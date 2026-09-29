import axios, { AxiosInstance, InternalAxiosRequestConfig } from "axios";

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
	_retry?: boolean;
}

export default class TokenAuthService {
	private api: AxiosInstance;
	private accessToken: string | null = null;
	private isRefreshing = false;
	private refreshSubscribers: ((token: string) => void)[] = [];

	public onAuthenticationFailure: (() => void) | null = null;

	constructor(baseURL: string) {
		this.api = axios.create({
			baseURL,
			withCredentials: true,
		});

		// PERSISTENCE OPTIMIZATION: Restore access token from storage on application startup
		this.accessToken = sessionStorage.getItem("chores_access_token");

		this.setupInterceptors();
	}

	/**
	 * Updates the active internal application token and mirrors it to sessionStorage.
	 */
	public setAccessToken(token: string | null): void {
		this.accessToken = token;
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
		return !!this.accessToken;
	}

	public getClient(): AxiosInstance {
		return this.api;
	}

	/**
	 * Clears the application session on the server and strips all tokens from local storage.
	 */
	public async logout(): Promise<void> {
		try {
			// Notify the backend to destroy the HttpOnly cookie safely
			await this.api.post("/api/auth/logout");
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

	private setupInterceptors(): void {
		this.api.interceptors.request.use(
			(config) => {
				if (this.accessToken && config.headers) {
					config.headers["Authorization"] = `Bearer ${this.accessToken}`;
				}
				return config;
			},
			(error) => Promise.reject(error),
		);

		this.api.interceptors.response.use(
			(response) => response,
			async (error) => {
				const originalRequest = error.config as CustomAxiosRequestConfig;

				if (
					error.response?.status === 401 &&
					originalRequest &&
					!originalRequest._retry
				) {
					originalRequest._retry = true;

					if (this.isRefreshing) {
						return new Promise((resolve) => {
							this.subscribeTokenRefresh((newToken: string) => {
								if (originalRequest.headers) {
									originalRequest.headers["Authorization"] =
										`Bearer ${newToken}`;
								}
								resolve(this.api(originalRequest));
							});
						});
					}

					this.isRefreshing = true;

					try {
						const refreshResponse = await axios.post<{ accessToken: string }>(
							`${this.api.defaults.baseURL}/api/auth/refresh`,
							{},
							{ withCredentials: true },
						);

						const { accessToken: newAccessToken } = refreshResponse.data;

						// Automatically commits to sessionStorage via our wrapper method
						this.setAccessToken(newAccessToken);
						this.isRefreshing = false;
						this.onTokenRefreshed(newAccessToken);

						if (originalRequest.headers) {
							originalRequest.headers["Authorization"] =
								`Bearer ${newAccessToken}`;
						}
						return this.api(originalRequest);
					} catch (refreshError) {
						this.isRefreshing = false;
						this.refreshSubscribers = [];

						// Wipes memory and cleans out sessionStorage automatically
						this.setAccessToken(null);

						if (this.onAuthenticationFailure) {
							this.onAuthenticationFailure();
						}

						return Promise.reject(refreshError);
					}
				}
				return Promise.reject(error);
			},
		);
	}

	private subscribeTokenRefresh(cb: (token: string) => void): void {
		this.refreshSubscribers.push(cb);
	}

	private onTokenRefreshed(token: string): void {
		this.refreshSubscribers.forEach((cb) => cb(token));
		this.refreshSubscribers = [];
	}
}
