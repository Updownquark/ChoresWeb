import React, { useState, useEffect } from "react";
import {
	GoogleOAuthProvider,
	GoogleLogin,
	CredentialResponse,
} from "@react-oauth/google";
import { BACKEND_API_URL, CLIENT_ID } from "./config/backend";
import axios from "axios";
import { myTheme } from "./theme";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { Button, Container, CssBaseline, Typography } from "@mui/material";
import User from "./values/User";
import Membership from "./values/Membership";
import OrganizationList from "./components/OrganziationList";
import OrganizationUI from "./components/OrganizationUI";
import { authContextHolder, lifeCycle, jobService, memberService, assignmentService} from "./services/services";

authContextHolder.getToken=()=>sessionStorage.getItem("google_id_token");

export const api = axios.create({
	baseURL: BACKEND_API_URL,
	headers: {
		"Content-Type": "application/json",
	},
});

// Request Interceptor: Automatically injects the cached token if it exists
api.interceptors.request.use(
	(config) => {
		const token = sessionStorage.getItem("google_id_token");
		if (token && config.headers) {
			config.headers.Authorization = `Bearer ${token}`;
		}
		return config;
	},
	(error) => {
		return Promise.reject(error);
	},
);

const LoadingStage = {
	Me: "Me",
	Orgs: "Orgs",
} as const;
type LoadingStage = (typeof LoadingStage)[keyof typeof LoadingStage];

let _me: User | null = null;
let _org: Membership | null = null;
export function me(): User | null {
	return _me;
}
export function org(): Membership | null {
	return _org;
}

function ChoreChampApp() {
	const [token, setToken] = useState<string | null>(null);
	const [me, setMe] = useState<User | null>(null);
	const [loading, setLoading] = useState<LoadingStage | null>(null);
	const [orgs, setOrgs] = useState<readonly Membership[] | null>();
	const [org, _setOrg] = useState<Membership | null>(null);

	const setOrg=(org: Membership | null)=>{
		if(org && org.organization){
			jobService.init(
				"/api/jobs/by-org/"+org.organization!.id,
				"/api/jobs/changes/"+org.organization!.id,
				lifeCycle);
			memberService.init(
				"/api/members/by-org/"+org.organization!.id,
				"/api/members/changes/"+org.organization!.id,
				lifeCycle);
			assignmentService.init(
				"/api/assignments/by-org/"+org.organization!.id,
				"/api/assignments/changes/"+org.organization!.id,
				lifeCycle);
		}
		_setOrg(org);
	};

	// Check sessionStorage for an existing cached token on startup
	useEffect(() => {
		const cachedToken = sessionStorage.getItem("google_id_token");
		if (cachedToken) {
			setToken(cachedToken);
			fetchMe();
		}
		// lifeCycle.start();
	}, []);

	const handleLoginSuccess = (credentialResponse: CredentialResponse) => {
		const jwtToken = credentialResponse.credential;
		if (jwtToken) {
			sessionStorage.setItem("google_id_token", jwtToken); // Cache it
			setToken(jwtToken); // Update state to reveal dashboard
			fetchMe();
		}
	};

	const handleLoginError = () => {
		console.error("Google Sign-In failed");
		alert("Failed to log in with Google. Please try again.");
	};

	// Clear cache and state on logout
	const handleLogout = () => {
		sessionStorage.removeItem("google_id_token");
		setToken(null);
		setMe(null);
	};

	// 6. Test the secure API instance
	const fetchMe = async () => {
		setLoading(LoadingStage.Me);
		let targetLoadingStage: LoadingStage | null = null;
		try {
			const response = await api.get<User>("/api/me");
			_me = response.data;
			//TODO Start the lifecycle
			setMe(_me);
			if (_me.id != -1) {
				targetLoadingStage = LoadingStage.Orgs;
				fetchOrgs();
			}
		} catch (error) {
			console.error("API Error:", error);
			setMe(null);
		} finally {
			setLoading(targetLoadingStage);
		}
	};

	const fetchOrgs = async () => {
		let targetLoadingStage: LoadingStage | null = null;
		try {
			const response = await api.get<Membership[]>("/api/orgs");
			const _orgs = response.data;
			//TODO Start the lifecycle
			setOrgs(_orgs);
			if (!_me?.canCreateOrgs && _orgs.length == 1) setOrg(org);
		} catch (error) {
			console.error("API Error:", error);
			setMe(null);
		} finally {
			setLoading(targetLoadingStage);
		}
	};

	let loadingStatusMessage: string;
	switch (loading) {
		case LoadingStage.Me:
			loadingStatusMessage = "Your information is being loaded";
			break;
		case LoadingStage.Orgs:
			loadingStatusMessage = "Loading your organization(s)";
			break;
		default:
			loadingStatusMessage = "Unrecognized loading status: " + loading;
			break;
	}

	if (!token) {
		return (
			<Container sx={{width: "100%", mt: 4 }}>
				<Typography variant="body1" sx={{ mb: 2 }}>
					Please log in to access the application dashboard.
				</Typography>
				<GoogleLogin
					onSuccess={handleLoginSuccess}
					onError={handleLoginError}
				/>
			</Container>
		);
	} else if (loading) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Loading...
				</Typography>
				{loadingStatusMessage}
			</Container>
		);
	} else if (!me) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Internal Server Error
				</Typography>
				An error has occurred on the server and the application cannot be
				accessed
			</Container>
		);
	} else if (me.id == -1) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Unrecognized User
				</Typography>
				You need to be added to an organization by an organization admin to
				access this application.
			</Container>
		);
	} else if(org){
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Chore Champ
				</Typography>
				<OrganizationUI org={org} api={api} />
			</Container>
		);
	} else if (me.canCreateOrgs) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Select the Organization to View
				</Typography>
				{orgs ? (
					<OrganizationList
						orgs={orgs}
						setOrg={(orgId) => {
							api
								.get<Membership>("/api/orgs/" + orgId)
								.then((response) => setOrg(response.data));
						}}
					/>
				) : null}
				<Button
					onClick={(e) => {
						api
							.post<Membership>("/api/orgs/add")
							.then((response) => setOrg(response.data));
					}}
				>
					Create Organization
				</Button>
			</Container>
		);
	} else if (orgs == null || orgs.length == 0) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					No Organization Memberships
				</Typography>
				You need to be added to an organization by an organization admin to
				access this application.
			</Container>
		);
	} else {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Typography variant="h4" component="h1" gutterBottom>
					Select the Organization to View
				</Typography>
				<OrganizationList
					orgs={orgs}
					setOrg={(orgId) => {
						api
							.get<Membership>("/api/orgs/" + orgId)
							.then((response) => setOrg(response.data));
					}}
				/>
			</Container>
		);
	}
}

function App() {
	return (
		<StyledEngineProvider injectFirst>
			<ThemeProvider theme={myTheme}>
				<CssBaseline />
				<GoogleOAuthProvider clientId={CLIENT_ID}>
					<ChoreChampApp />
				</GoogleOAuthProvider>
			</ThemeProvider>
		</StyledEngineProvider>
	);
}

export default App;
