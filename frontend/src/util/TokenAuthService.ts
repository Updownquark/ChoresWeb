import axios, { AxiosInstance, InternalAxiosRequestConfig } from "axios";

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
	_retry?: boolean;
}

export default class TokenAuthService {
	private readonly _baseUrl: string;
	private _api: AxiosInstance;
	private _accessToken: string | null = null;
	private _isRefreshing = false;
	private _refreshSubscribers: ((token: string) => void)[] = [];

	public onAuthenticationFailure: (() => void) | null = null;

	constructor(baseURL: string) {
		this._baseUrl=baseURL;
		this._api = axios.create({
			baseURL,
			withCredentials: true,
		});

		// PERSISTENCE OPTIMIZATION: Restore access token from storage on application startup
		this._accessToken = sessionStorage.getItem("chores_access_token");

		this.setupInterceptors();
	}

	public get baseUrl(): string{
		return this._baseUrl;
	}

	public get accessToken(): string | null {
		return this._accessToken;
	}

	/**
	 * Updates the active internal application token and mirrors it to sessionStorage.
	 */
	public setAccessToken(token: string | null): void {
		this._accessToken = token;
		if (token) {
			sessionStorage.setItem("chores_access_token", token);
		} else {
			sessionStorage.removeItem("chores_access_token");
		}
	}

	/**
	 * Helper utility to quickly inspect if an internal token is locally cached.
	 */
	public hasLocalToken(): boolean {
		return !!this._accessToken;
	}

	public getClient(): AxiosInstance {
		return this._api;
	}

	/**
	 * Clears the application session on the server and strips all tokens from local storage.
	 */
	public async logout(): Promise<void> {
		try {
			// Notify the backend to destroy the HttpOnly cookie safely
			await this._api.post("/api/auth/logout");
		} catch (err) {
			console.error("Server-side logout cookie clearance failed:", err);
		} finally {
			// Always wipe local client memory and sessionStorage, even if the network call fails
			this.setAccessToken(null);

			// Trigger the UI unmount fallback hook if it's currently listening
			if (this.onAuthenticationFailure) {
				this.onAuthenticationFailure();
			}
		}
	}

	public async tryReconnect(): Promise<string | null>{
		this._isRefreshing = true;

		try {
			const refreshResponse = await axios.post<{ accessToken: string }>(
				`${this._api.defaults.baseURL}/api/auth/refresh`,
				{},
				{ withCredentials: true },
			);

			const { accessToken: newAccessToken } = refreshResponse.data;

			// Automatically commits to sessionStorage via our wrapper method
			this.setAccessToken(newAccessToken);
			this._isRefreshing = false;
			this.onTokenRefreshed(newAccessToken);

			return newAccessToken;
		} catch (refreshError) {
			this._isRefreshing = false;

			if(axios.isAxiosError(refreshError) && refreshError.response){
				// Wipes memory and cleans out sessionStorage automatically
				this._refreshSubscribers = [];
				this.setAccessToken(null);

				if (this.onAuthenticationFailure) {
					this.onAuthenticationFailure();
				}
			}

			return Promise.reject(refreshError);
		}
	}

	private setupInterceptors(): void {
		this._api.interceptors.request.use(
			(config) => {
				if (this._accessToken && config.headers) {
					config.headers["Authorization"] = `Bearer ${this._accessToken}`;
				}
				return config;
			},
			(error) => Promise.reject(error),
		);

		this._api.interceptors.response.use(
			(response) => response,
			async (error) => {
				this.tryReconnect();
				const originalRequest = error.config as CustomAxiosRequestConfig;

				if (
					error.response?.status === 401 &&
					originalRequest &&
					!originalRequest._retry
				) {
					originalRequest._retry = true;

					if (this._isRefreshing) {
						return new Promise((resolve) => {
							this.subscribeTokenRefresh((newToken: string) => {
								if (originalRequest.headers) {
									originalRequest.headers["Authorization"] =
										`Bearer ${newToken}`;
								}
								resolve(this._api(originalRequest));
							});
						});
					}

					const newAccessToken=await this.tryReconnect();
					if(newAccessToken){
						if (originalRequest.headers) {
							originalRequest.headers["Authorization"] =`Bearer ${newAccessToken}`;
						}
						return this._api(originalRequest);
					}
				}
				return Promise.reject(error);
			},
		);
	}
	

	private subscribeTokenRefresh(cb: (token: string) => void): void {
		this._refreshSubscribers.push(cb);
	}

	private onTokenRefreshed(token: string): void {
		this._refreshSubscribers.forEach((cb) => cb(token));
		this._refreshSubscribers = [];
	}
}
