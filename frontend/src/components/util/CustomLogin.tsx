import React, { ReactNode, useEffect, useState } from "react";
import TokenAuthService from "../../util/TokenAuthService";
import axios from "axios";

interface AuthContextProps {
	onAuthSuccess: (credentialString: string) => Promise<void>;
	onAuthError: (errorMessage?: string) => void;
	onLogout: () => Promise<void>;
}

interface CustomLoginProps {
	authService: TokenAuthService;
	init: (auth: AuthContextProps) => ReactNode;
	onFail?: ReactNode;
	children: ReactNode;
}

export let globalLogoutTrigger: (() => Promise<void>) | null = null;

export const CustomLogin: React.FC<CustomLoginProps> = ({
	authService,
	init,
	onFail,
	children,
}) => {
	const [isAuthenticated, setIsAuthenticated] = useState<boolean>(
		authService.hasLocalToken(),
	);
	const [hasFailed, setHasFailed] = useState<boolean>(false);
	const [isInitializing, setIsInitializing] = useState<boolean>(
		!authService.hasLocalToken(),
	);

	const handleLogout = async () => {
		await authService.logout();
		setIsAuthenticated(false);
	};

	globalLogoutTrigger = handleLogout;

	useEffect(() => {
		const abortController = new AbortController();
		let isCurrentMountTrackActive = true;

		authService.onAuthenticationFailure = () => {
			if (!isCurrentMountTrackActive) return;
			setIsAuthenticated(false);
			setHasFailed(true);
		};

		if (!authService.hasLocalToken()) {
			const silentCheck = async () => {
				try {
					const response = await axios.post<{ accessToken: string }>(
						`${authService.getClient().defaults.baseURL}/api/auth/refresh`,
						{},
						{ 
							withCredentials: true,
							signal: abortController.signal // Aborts the call if unmounted instantly
						},
					);
					
					if (isCurrentMountTrackActive) {
						authService.setAccessToken(response.data.accessToken);
						setIsAuthenticated(true);
					}
				} catch (err) {
					if (axios.isCancel(err)) {
						return; // Silently absorb Strict Mode development cancellations
					}
					if (isCurrentMountTrackActive) {
						setIsAuthenticated(false);
					}
				} finally {
					if (isCurrentMountTrackActive) {
						setIsInitializing(false);
					}
				}
			};

			silentCheck();
		} else {
			setIsInitializing(false);
		}

		return () => {
			isCurrentMountTrackActive = false;
			abortController.abort();
			authService.onAuthenticationFailure = null;
		};
	}, [authService]);

	const handleAuthSuccess = async (credentialString: string) => {
		try {
			setHasFailed(false);

			// Execute the initialization handshake via your unified authClient instance
			const response = await authService.getClient().post<{ accessToken: string }>(
				"/api/auth/init",
				{},
				{
					headers: { 'Authorization': `Bearer ${credentialString}` },
					withCredentials: true,
				}
			);

			authService.setAccessToken(response.data.accessToken);
			setIsAuthenticated(true);
		} catch (err) {
			console.error("Server backend handshake rejected:", err);
			setHasFailed(true);
		}
	};

	const handleAuthError = (errorMessage?: string) => {
		console.error("Identity collection error:", errorMessage);
		setHasFailed(true);
	};

	if (isInitializing) {
		return null; 
	}

	if (isAuthenticated) {
		return <>{children}</>;
	}

	const authController: AuthContextProps = {
		onAuthSuccess: handleAuthSuccess,
		onAuthError: handleAuthError,
		onLogout: handleLogout,
	};

	return (
		<div className="auth-wrapper">
			<div
				className="login-container"
				style={{ textAlign: "center", marginTop: "50px" }}
			>
				{hasFailed && <div style={{ marginBottom: "20px" }}>{onFail}</div>}
				{init(authController)}
			</div>
		</div>
	);
};
