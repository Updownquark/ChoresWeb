import { useState, useEffect, useSyncExternalStore } from "react";
import { GoogleLogin, GoogleOAuthProvider } from "@react-oauth/google";
import { CLIENT_ID } from "./config/backend";
import { myTheme } from "./theme";
import { StyledEngineProvider, ThemeProvider, useColorScheme } from "@mui/material/styles";
import { Button, Container, CssBaseline, Typography, useMediaQuery } from "@mui/material";
import User from "./values/User";
import Membership from "./values/Membership";
import OrganizationList from "./components/OrganizationList";
import OrganizationUI from "./components/OrganizationUI";
import {
	authService,
	api,
	lifeCycle,
	jobService,
	memberService,
	assignmentService,
	resourcesService,
	debug,
	syncService,
	orgsService,
	historyService,
} from "./services/services";
import { CustomLogin } from "./components/util/CustomLogin";
import { LifeCycleStage } from "./services/LifeCycleService";
import Banner from "./components/util/Banner";
import Footer from "./components/util/Footer";

const appName = "Chore Champ";

let _me: User | null = null;
let _org: Membership | null = null;
export function me(): User | null {
	return _me;
}
export function org(): Membership | null {
	return _org;
}

const subscribeToOrgs = orgsService.onChange.bind(orgsService);
const getOrgsSnapshot = orgsService.getAll.bind(orgsService);

function ChoreChampApp() {
	const [me, setMe] = useState<User | null>(null);
	const orgs = useSyncExternalStore(subscribeToOrgs, getOrgsSnapshot);
	const [org, _setOrg] = useState<Membership | null>(null);

	useEffect(() => {
		syncService.init();
	}, []);

	useEffect(() => {
		if (org) {
		} else if (!_me?.globalAdmin && orgs.length == 1) {
			setOrg(orgs[0]);
		} else {
			const orgIdStr = sessionStorage.getItem("selectedOrg");
			if (orgIdStr) {
				const orgId = parseInt(orgIdStr);
				for (const org of orgs) {
					if (org.organization!.id == orgId) {
						setOrg(org);
						break;
					}
				}
			}
		}
	}, [orgs]);

	const setOrg = (org: Membership | null) => {
		if (org && org.organization) {
			sessionStorage.setItem("selectedOrg", org.organization!.id.toString());
			document.title = appName + ": " + org.organization!.name;
			const filters = { organization: org.organization!.id };
			jobService.init(filters);
			memberService.init(filters);
			assignmentService.init(filters);
			resourcesService.init(filters);
			historyService.setOrg(org.organization!.id);
			if (!debug && lifeCycle.getStage() == LifeCycleStage.PreInit) lifeCycle.start();
		} else {
			document.title = appName;
			sessionStorage.removeItem("selectedOrg");
			jobService.disconnect();
			memberService.disconnect();
			assignmentService.disconnect();
			resourcesService.disconnect();
		}
		_setOrg(org);
	};

	useEffect(() => {
		fetchMe();
	}, []);

	// 6. Test the secure API instance
	const fetchMe = async () => {
		try {
			const response = await api.get<User>("/api/me");
			_me = response.data;
			setMe(_me);
			if (_me.id != -1)
				fetchOrgs();
		} catch (error) {
			console.error("API Error:", error);
			setMe(null);
		}
	};

	const fetchOrgs = async () => {
		try {
			orgsService.init({ member: _me!.id });
		} catch (error) {
			console.error("API Error:", error);
			setMe(null);
		}
	};

	const exitOrg = () => setOrg(null);

	const layoutStyles={
		width: "100%",
		height: "100vh",
		mt: 4,
		display: "flex",
		flexDirection: "column",
		alignItems: "center"
	};
	if (orgsService.status=="loading") {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Loading..."
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				Loading Your Organizations
			</Container>
		);
	} else if (!me) {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Chore Champ Unavailable"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				The application is not accessible
			</Container>
		);
	} else if (me.id == -1) {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Unrecognized User"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				You need to be added to an organization by an organization admin to access this application.
				<Footer />
			</Container>
		);
	} else if (org) {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Chore Champ"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				<OrganizationUI
					org={org}
					api={api}
				/>
				<Footer />
			</Container>
		);
	} else if (me.globalAdmin) {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Select the Organization to View"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				{orgs ? (
					<OrganizationList
						orgs={orgs}
						setOrg={orgId => {
							api.get<Membership>("/api/orgs/" + orgId).then(response => setOrg(response.data));
						}}
					/>
				) : null}
				<Button
					onClick={e => {
						api.post<Membership>("/api/orgs/add").then(response => setOrg(response.data));
					}}>
					Create Organization
				</Button>
				<Footer />
			</Container>
		);
	} else if (orgs == null || orgs.length == 0) {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="No Organization Memberships"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				You need to be added to an organization by an organization admin to access this application.
				<Footer />
			</Container>
		);
	} else {
		return (
			<Container sx={{...layoutStyles}}>
				<Banner
					title="Select the Organization to View"
					org={org}
					exitOrg={exitOrg}
					api={api}
				/>
				<OrganizationList
					orgs={orgs}
					setOrg={orgId => {
						api.get<Membership>("/api/orgs/" + orgId).then(response => setOrg(response.data));
					}}
				/>
				<Footer />
			</Container>
		);
	}
}

function App() {
	return (
		<ThemeProvider theme={myTheme}>
			<StyledEngineProvider injectFirst>
				<CssBaseline />
				<GoogleOAuthProvider clientId={CLIENT_ID}>
					<CustomLogin
						authService={authService}
						init={({ onAuthSuccess, onAuthError, onLogout }) => (
							<div style={{ textAlign: "center", marginTop: "50px" }}>
								<h1>Chore Champ Login</h1>
								<GoogleLogin
									onSuccess={response => {
										if (response.credential) onAuthSuccess(response.credential);
										else onAuthError("No credential payload found");
									}}
									onError={() => onAuthError("Authentication failed")}
									useOneTap
								/>
							</div>
						)}
						onFail={
							<h1>Chore Champ Login</h1>
						}>
						<ChoreChampApp />
					</CustomLogin>
				</GoogleOAuthProvider>
			</StyledEngineProvider>
		</ThemeProvider>
	);
}

export default App;
