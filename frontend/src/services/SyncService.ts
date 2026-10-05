import TokenAuthService from "../util/TokenAuthService";

export interface ChoresApplicationEvent {
	tableName: string;
	entityId: number;
	exists: boolean;
	eventTime: number;
}

export type SyncEventListener = (event: ChoresApplicationEvent) => void;

export default class SyncService {
	private _tokenService: TokenAuthService;
	private eventSource: EventSource | null = null;
	private lastSyncTime: number = 0;
	private isConnected: boolean = false;

	// Maps: tableName -> Set of callback listener functions
	private listeners: Map<string, Set<SyncEventListener>> = new Map();
	// Global hooks for system-level mutations (like FORCE_REFETCH or security overrides)
	private onGlobalRefetch: (() => void) | null = null;

	constructor(tokenService: TokenAuthService) {
		this._tokenService = tokenService;
	}

	/**
	 * Registers a callback listener function for a specific database table name.
	 * Returns a cleanup function to easily unregister on component unmount.
	 */
	public subscribe(tableName: string, listener: SyncEventListener): () => void {
		if (!this.listeners.has(tableName)) {
			this.listeners.set(tableName, new Set());
		}

		this.listeners.get(tableName)!.add(listener);

		// Return an explicit teardown callback function
		return () => {
			const tableSet = this.listeners.get(tableName);
			if (tableSet) {
				tableSet.delete(listener);
				if (tableSet.size === 0) {
					this.listeners.delete(tableName);
				}
			}
		};
	}

	/**
	 * Configures a system-level callback to run when a FORCE_REFETCH is triggered.
	 */
	public registerGlobalRefetch(callback: () => void) {
		this.onGlobalRefetch = callback;
	}

	/**
	 * Starts the long-lived HTTP SSE stream connection to the Spring Boot backend.
	 */
	public connect(orgId?: number) {
		if (this.isConnected){
			this.disconnect();
			return;
		}

		const establish = () => {
			let url = `${this._tokenService.baseUrl}/api/sync`;
			if(orgId)
				url+=`/org-changes/${orgId}`;
			else
				url+="/orgs";
			url+=`?token=${this._tokenService.accessToken}`
			if(this.lastSyncTime)
				url+=`&lastSync=${this.lastSyncTime}`;
			this.eventSource = new EventSource(url);
			this.isConnected = true;

			// 1. Process Handshake Event
			this.eventSource.addEventListener("init", (event: MessageEvent) => {
				const handshake = JSON.parse(event.data);

				if (!handshake.upToDate) {
					console.log(
						"Sync Timeline reset or initial load. Triggering cache refresh.",
					);
					this.lastSyncTime = handshake.lastChangeTime;
					if (this.onGlobalRefetch) this.onGlobalRefetch();
				} else {
					console.log("Sync line re-established seamlessly.");
				}
			});

			// 2. Process Operational Data Changes
			this.eventSource.addEventListener(
				"data-change",
				(event: MessageEvent) => {
					const changeEvent: ChoresApplicationEvent = JSON.parse(event.data);

					// Advance our chronological high-water mark timeline marker instantly
					this.lastSyncTime = changeEvent.eventTime;

					// Route the event to targeted table listeners
					// Disseminate to specific tables matching your preferred pattern
					const tableListeners = this.listeners.get(changeEvent.tableName);
					if (tableListeners) {
						tableListeners.forEach((listener) => listener(changeEvent));
					}
				},
			);

			this.eventSource.onerror = () => {
				console.warn(
					"Sync connection broken. Scheduling native reconnect sequence...",
				);
				this.disconnect();
				// Native EventSource will automatically loop and reconnect via its built-in retry.
				// It will pick up the updated this.lastSyncTime tracking ref automatically.

				// CRITICAL ROTATION PROTECTION: If our token expired while we were connected,
				// we check if TokenAuthService is currently running a refresh or has failed.
				if (!this._tokenService.hasLocalToken()) {
					console.error(
						"Token lost. Relying on Axios routing intercepts to auto-heal session.",
					);
					return;
				}

				// Reconnect with exponential backoff or native retry loop
				setTimeout(() => this.connect(orgId), 3000);
			};
		};

		establish();
	}

	public disconnect() {
		if (this.eventSource) {
			this.eventSource.close();
			this.eventSource = null;
		}
		this.isConnected = false;
	}
}
