import { AxiosInstance } from "axios";
import TokenAuthService from "../util/TokenAuthService";

interface AbstractSyncServerEvent {
	type: string;
	sequence: number;
}

// Standardizing structural domain events directly from your Java payload models
export interface SyncDataEvent<T = any> extends AbstractSyncServerEvent {
	type: "addOrUpdate" | "remove";
	entityType: string;
	entity: T;
}

export interface SyncResetEvent<T = any> extends AbstractSyncServerEvent {
	type: "reset";
	entityType: string;
	entities: T[];
}

export interface SyncSubscriptionRevokedEvent extends AbstractSyncServerEvent {
	type: "subscriptionRevoked";
	subscription: string; // The full subId path: 'entityTypeName/uniqueId'
}

export type SyncEvent<T = any> =
	SyncDataEvent<T>
	| SyncResetEvent<T>
	| SyncSubscriptionRevokedEvent;

/** Public-facing listener for events that make sense to expose */
export type SyncEventListener<T = any> = (
	event: SyncDataEvent<T> | SyncResetEvent<T> | SyncSubscriptionRevokedEvent,
) => void;

interface InitEvent extends AbstractSyncServerEvent {
	type: "init";
	streamId: string;
	resume: boolean;
	initSubscriptions: readonly string [];
}

interface SyncSubscriptionChangedEvent extends AbstractSyncServerEvent {
	type: "subscriptionChanged";
	newSubscriptions: readonly string [];
	unsubscribed: readonly string [];
}

/** Inner  */
type SyncServerEvent<T = any>=
	SyncEvent<T>
	| InitEvent
	| SyncSubscriptionChangedEvent;

interface ActiveSubscription {
	id: string | null;
	filters: object;
	listener: SyncEventListener;
}

interface PendingModification {
	subscribeJsons: string;
	withInitialData: boolean;
}

type ConnectionStatusType = "success" | "attempt" | "failed";
export class ConnectionStatus {
	public readonly status: ConnectionStatusType;
	public readonly message: string;

	constructor(status: ConnectionStatusType, message: string){
		this.status=status;
		this.message=message;
	}
}

export default class SyncService {
	private _tokenService: TokenAuthService;
	private _api: AxiosInstance; // Injected Axios instance
	private _eventSource: EventSource | null = null;
	private _lastEventId: string | null = null;
	private _lastSequence: number = 0;
	private _isConnected: boolean = false;
	private _streamId: string | null = null;

	// Maps: entityType -> Set of internal tracked metadata details
	private _subscriptions: Map<string, Set<ActiveSubscription>> = new Map();

	private _connectionStatus: ConnectionStatus = new ConnectionStatus("attempt", "Not Connected");
	private readonly _connectionStatusListeners: ((status: ConnectionStatus)=>void) []=[];

	private _pendingModifications: PendingModification[] = [];

	constructor(tokenService: TokenAuthService, api: AxiosInstance) {
		this._tokenService = tokenService;
		this._api=api;
	}

	public getConnectionStatus(): ConnectionStatus {
		return this._connectionStatus;
	}

	/**
	 * Registers a dynamic filter subscription for a specific backend entity type.
	 * Asynchronously pushes mutations to the backend server and returns a teardown hook.
	 */
	public subscribe<T = any>(entityType: string, filters: object, listener: SyncEventListener<T>): () => void {
		if (!this._subscriptions.has(entityType)) {
			this._subscriptions.set(entityType, new Set());
		}

		const subRecord: ActiveSubscription = {
			id: null,
			filters: filters,
			listener: listener as SyncEventListener,
		};

		this._subscriptions.get(entityType)!.add(subRecord);

		const encodedSubscriptionJson = JSON.stringify([{
			subscribeEntityType: entityType,
			...filters,
		}]);

		this.sendSubscriptionModification(encodedSubscriptionJson, [], true);

		return () => {
			const entitySet = this._subscriptions.get(entityType);
			if (entitySet) {
				entitySet.delete(subRecord);
				if (entitySet.size === 0) {
					this._subscriptions.delete(entityType);
				}
			}

			if (subRecord.id) {
				this.sendSubscriptionModification("", [subRecord.id], false);
			}
		};
	}

	private gatherInitialSubscriptions(): string {
		const payloads: object[] = [];
		this._subscriptions.forEach((set, entityType) => {
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
		if (this._isConnected) {
			this.disconnect();
		}

		this.setConnectionStatus(new ConnectionStatus("attempt", "Connecting..."));

		let url = `${this._tokenService.baseUrl}/api/sync/init?token=${encodeURIComponent(this._tokenService.accessToken || "")}`;

		if (this._lastEventId) {
			url += `&lastEventId=${encodeURIComponent(this._lastEventId)}`;
		}

		const initialSubs = this.gatherInitialSubscriptions();
		url += `&subscriptions=${encodeURIComponent(initialSubs)}`;

		this._eventSource = new EventSource(url);
		this._isConnected = true;

		this._eventSource.onmessage = (event: MessageEvent) => this.processServerEvent(event);
		this._eventSource.onopen=()=>this.setConnectionStatus(new ConnectionStatus("success", "Connected"));
		this._eventSource.onerror = async (error) => {
			console.warn("Sync connection broken or authorization rejected. Attempting to reconnect...");
			this.disconnect();

			if (!this._tokenService.hasLocalToken()) {
				this.setConnectionStatus(new ConnectionStatus("failed", "Connection Failed"));
				console.error("Token lost. Relying on interceptors to re-authenticate or clear session context.");
				return;
			}

			try{
				this.setConnectionStatus(new ConnectionStatus("attempt", "Reconnecting..."));
				await this._tokenService.tryReconnect();
			} catch(authError){
				this.setConnectionStatus(new ConnectionStatus("failed", "Connection Failed"));
				console.error("Background re-auth failed.");
				setTimeout(() => this.init(), 5000);
			}
		};
	}

	private processServerEvent(event: MessageEvent){
		const myPrevEventId=this._lastEventId;

		try {
			const payload = JSON.parse(event.data) as SyncServerEvent;

			if("init" != payload.type && payload.sequence != this._lastSequence+1){
				// Event gap detected. Internal state will be corrupt.  Reconnect and re-init required.
				this.disconnect();
				this.init();
			}
			this._lastSequence=payload.sequence;
			if (event.lastEventId)
				this._lastEventId = event.lastEventId;

			switch (payload.type) {
				case "init":
					this._streamId = payload.streamId;
					// Clear out old connection IDs to allow pairing fresh session hashes
					this._subscriptions.forEach(set => {
						set.forEach(sub => (sub.id = null));
					});
					if (payload.resume) {
						console.log("Sync re-established");
					} else if(myPrevEventId){
						console.log("Sync outdated--reset required")
					} else {
						console.log("Sync initialized");
					}

					if (payload.initSubscriptions && Array.isArray(payload.initSubscriptions)) {
						this.populateSubscriptionIds(payload.initSubscriptions);
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
					if (payload.newSubscriptions && Array.isArray(payload.newSubscriptions)) {
						this.populateSubscriptionIds(payload.newSubscriptions);
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
	}

	private async sendSubscriptionModification(
		subscribeJsons: string,
		unsubscribeIds: string[],
		withInitialData: boolean,
	) {
		if (!this._streamId) {
			if (subscribeJsons.length) {
				// Buffer the modification parameters until the connection finishes the initialization handshake
				this._pendingModifications.push({ subscribeJsons, withInitialData });
			}
			return;
		}

		const params = new URLSearchParams();
		params.append("streamId", this._streamId);
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

			this.populateSubscriptionIds(response.data);
		} catch (error) {
			console.error("Failed to update subscription adjustments on server:", error);
		}
	}

	/**
	 * Flushes out accumulated modification layers sequentially
	 */
	private async flushPendingModifications() {
		if (this._pendingModifications.length === 0) return;

		const modsToProcess = [...this._pendingModifications];
		this._pendingModifications = [];

		for (const mod of modsToProcess) {
			await this.sendSubscriptionModification(mod.subscribeJsons, [], mod.withInitialData);
		}
	}

	private populateSubscriptionIds(serverIds: string[]) {
		serverIds.forEach(fullId => {
			const [entityType] = fullId.split("/");
			const targetSet = this._subscriptions.get(entityType);
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
		const targetSet = this._subscriptions.get(entityType);
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
			this._subscriptions.delete(entityType);
		}
	}

	private distributeToListeners(entityType: string, eventPayload: any) {
		const targetSet = this._subscriptions.get(entityType);
		if (targetSet) {
			targetSet.forEach(sub => sub.listener(eventPayload));
		}
	}

	public disconnect() {
		if (this._eventSource) {
			this._eventSource.close();
			this._eventSource = null;
		}
		this._streamId = null;
		this._isConnected = false;
		this._pendingModifications=[];
		for(const typeSubs of this._subscriptions.values()){
			for(const sub of typeSubs)
				sub.id=null;
		}
	}

	private setConnectionStatus(status: ConnectionStatus){
		this._connectionStatus=status;
		for(const listener of this._connectionStatusListeners)
			listener(status);
	}

	public onConnectionStatusChange(listener: (status: ConnectionStatus)=>void): (()=>void){
		this._connectionStatusListeners.push(listener);
		return ()=>{
			const index=this._connectionStatusListeners.indexOf(listener);
			if(index>=0)
				this._connectionStatusListeners.splice(index, 1);
		};
	}
}
