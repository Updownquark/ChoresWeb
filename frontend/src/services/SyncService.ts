import { AxiosInstance } from "axios";
import TokenAuthService from "../util/TokenAuthService";

// Standardizing structural domain events directly from your Java payload models
export interface SyncDataEvent<T = any> {
	type: "addOrUpdate" | "remove";
	entityType: string;
	entity: T;
}

export interface SyncResetEvent<T = any> {
	type: "reset";
	entityType: string;
	entities: T[];
}

export interface SyncSubscriptionRevokedEvent {
	type: "subscriptionRevoked";
	subscription: string; // The full subId path: 'entityTypeName/uniqueId'
}

export type SyncEventListener<T = any> = (
	event: SyncDataEvent<T> | SyncResetEvent<T> | SyncSubscriptionRevokedEvent,
) => void;

interface ActiveSubscription {
	id: string | null;
	filters: object;
	listener: SyncEventListener;
}

interface PendingModification {
	subscribeJsons: string;
	withInitialData: boolean;
}

export default class SyncService {
	private _tokenService: TokenAuthService;
	private _api: AxiosInstance; // Injected Axios instance
	private eventSource: EventSource | null = null;
	private lastEventId: string | null = null;
	private isConnected: boolean = false;
	private streamId: string | null = null;

	// Maps: entityType -> Set of internal tracked metadata details
	private subscriptions: Map<string, Set<ActiveSubscription>> = new Map();

	private pendingModifications: PendingModification[] = [];

	constructor(tokenService: TokenAuthService, api: AxiosInstance) {
		this._tokenService = tokenService;
		this._api=api;
	}

	/**
	 * Registers a dynamic filter subscription for a specific backend entity type.
	 * Asynchronously pushes mutations to the backend server and returns a teardown hook.
	 */
	public subscribe<T = any>(entityType: string, filters: object, listener: SyncEventListener<T>): () => void {
		if (!this.subscriptions.has(entityType)) {
			this.subscriptions.set(entityType, new Set());
		}

		const subRecord: ActiveSubscription = {
			id: null,
			filters: filters,
			listener: listener as SyncEventListener,
		};

		this.subscriptions.get(entityType)!.add(subRecord);

		const encodedSubscriptionJson = JSON.stringify([{
			subscribeEntityType: entityType,
			...filters,
		}]);

		this.sendSubscriptionModification(encodedSubscriptionJson, [], true);

		return () => {
			const entitySet = this.subscriptions.get(entityType);
			if (entitySet) {
				entitySet.delete(subRecord);
				if (entitySet.size === 0) {
					this.subscriptions.delete(entityType);
				}
			}

			if (subRecord.id) {
				this.sendSubscriptionModification("", [subRecord.id], false);
			}
		};
	}

	private gatherInitialSubscriptions(): string {
		const payloads: object[] = [];
		this.subscriptions.forEach((set, entityType) => {
			set.forEach(sub => {
				payloads.push({
					subscribeEntityType: entityType,
					...sub.filters,
				},
				);
			});
		});
		return JSON.stringify(payloads);
	}

	/**
	 * Starts the long-lived HTTP SSE stream connection to the Spring Boot backend.
	 */
	public init() {
		if (this.isConnected) {
			this.disconnect();
		}

		let url = `${this._tokenService.baseUrl}/api/sync/init?token=${encodeURIComponent(this._tokenService.accessToken || "")}`;

		if (this.lastEventId) {
			url += `&lastEventId=${encodeURIComponent(this.lastEventId)}`;
		}

		const initialSubs = this.gatherInitialSubscriptions();
		url += `&subscriptions=${encodeURIComponent(initialSubs)}`;

		this.eventSource = new EventSource(url);
		this.isConnected = true;

		this.eventSource.onmessage = (event: MessageEvent) => {
			const hadLast=!!event.lastEventId;
			if (event.lastEventId) {
				this.lastEventId = event.lastEventId;
			}

			try {
				const payload = JSON.parse(event.data);

				switch (payload.type) {
					case "init":
						this.streamId = payload.streamId;
						// Clear out old connection IDs to allow pairing fresh session hashes
						this.subscriptions.forEach(set => {
							set.forEach(sub => (sub.id = null));
						});
						if (payload.resume) {
							console.log("Sync re-established");
						} else if(hadLast){
							console.log("Sync outdated--reset required")
						} else {
							console.log("Sync initialized");
						}

						if (payload.initSubscriptions && Array.isArray(payload.initSubscriptions)) {
							this.pairSubscriptionIds(payload.initSubscriptions);
						}
						this.flushPendingModifications();
						break;

					case "reset":
					case "addOrUpdate":
					case "remove":
						console.log(payload.type, payload.entityType, payload)
						this.distributeToListeners(payload.entityType, payload);
						break;

					case "subscriptionChanged":
						if (payload.subscriptions && Array.isArray(payload.subscriptions)) {
							this.pairSubscriptionIds(payload.subscriptions);
						}
						break;

					case "subscriptionRevoked":
						console.log(
							`Subscription ${payload.subscription} was revoked by the server security validation.`,
						);
						this.handleServerRevocation(payload.subscription, payload);
						break;

					default:
						break;
				}
			} catch (err) {
				console.error("Failed to parse incoming sync event payload matrix:", err);
				console.error("JSON was ", event.data);
			}
		};

		this.eventSource.onerror = () => {
			console.warn("Sync connection broken. Cleaning line states...");
			this.disconnect();

			if (!this._tokenService.hasLocalToken()) {
				console.error("Token lost. Relying on interceptors to re-authenticate or clear session context.");
				return;
			}

			setTimeout(() => this.init(), 3000);
		};
	}

	private async sendSubscriptionModification(
		subscribeJsons: string,
		unsubscribeIds: string[],
		withInitialData: boolean,
	) {
		if (!this.streamId) {
			if (subscribeJsons.length) {
				// Buffer the modification parameters until the connection finishes the initialization handshake
				this.pendingModifications.push({ subscribeJsons, withInitialData });
			}
			return;
		}

		const params = new URLSearchParams();
		params.append("streamId", this.streamId);
		params.append("withInitialData", String(withInitialData));

		if(subscribeJsons)
			params.append("subscribe", subscribeJsons);
		unsubscribeIds.forEach(id => params.append("unsubscribe", id));

		try {
			const response = await this._api.post<string[]>("/api/sync/modify", params, {
				headers: {
					"Content-Type": "application/x-www-form-urlencoded",
				}
			});

			this.pairSubscriptionIds(response.data);
		} catch (error) {
			console.error("Failed to update subscription adjustments on server:", error);
		}
	}

	/**
	 * Flushes out accumulated modification layers sequentially
	 */
	private async flushPendingModifications() {
		if (this.pendingModifications.length === 0) return;

		const modsToProcess = [...this.pendingModifications];
		this.pendingModifications = [];

		for (const mod of modsToProcess) {
			await this.sendSubscriptionModification(mod.subscribeJsons, [], mod.withInitialData);
		}
	}

	private pairSubscriptionIds(serverIds: string[]) {
		serverIds.forEach(fullId => {
			const [entityType] = fullId.split("/");
			const targetSet = this.subscriptions.get(entityType);
			if (!targetSet) return;

			for (const sub of targetSet) {
				if (sub.id === null) {
					sub.id = fullId;
					break;
				}
			}
		});
	}

	/**
	 * Cleans up subscriptions rejected or revoked by server filters dynamically,
	 * and distributes the official 'subscriptionRevoked' payload to the listener.
	 */
	private handleServerRevocation(revokedId: string, payload: SyncSubscriptionRevokedEvent) {
		const [entityType] = revokedId.split("/");
		const targetSet = this.subscriptions.get(entityType);
		if (!targetSet) return;

		for (const sub of targetSet) {
			if (sub.id === revokedId) {
				// Alert the target listener directly using the backend's original payload payload contract
				sub.listener(payload);

				targetSet.delete(sub);
				break;
			}
		}
		if (targetSet.size === 0) {
			this.subscriptions.delete(entityType);
		}
	}

	private distributeToListeners(entityType: string, eventPayload: any) {
		const targetSet = this.subscriptions.get(entityType);
		if (targetSet) {
			targetSet.forEach(sub => sub.listener(eventPayload));
		}
	}

	public disconnect() {
		if (this.eventSource) {
			this.eventSource.close();
			this.eventSource = null;
		}
		this.streamId = null;
		this.isConnected = false;
		this.pendingModifications=[];
	}
}
