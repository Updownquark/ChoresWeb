import { useState, useEffect, useSyncExternalStore } from "react";
import { GoogleLogin, GoogleOAuthProvider } from "@react-oauth/google";
import { CLIENT_ID } from "./config/backend";
import { myTheme } from "./theme";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { Button, Container, CssBaseline, Typography } from "@mui/material";
import User from "./values/User";
import Membership from "./values/Membership";
import OrganizationList from "./components/OrganziationList";
import OrganizationUI from "./components/OrganizationUI";
import { authService, api, lifeCycle, jobService, memberService, assignmentService, resourcesService, debug, syncService, orgsService} from "./services/services";
import { CustomLogin } from "./components/util/CustomLogin";
import { LifeCycleStage } from "./services/LifeCycleService";
import Banner from "./components/util/Banner";

const appName="Chore Champ";

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

const subscribeToOrgs=orgsService.onChange.bind(orgsService);
const getOrgsSnapshot=orgsService.getAll.bind(orgsService);

function ChoreChampApp() {
	const [me, setMe] = useState<User | null>(null);
	const [loading, setLoading] = useState<LoadingStage | null>(null);
	const orgs=useSyncExternalStore(subscribeToOrgs, getOrgsSnapshot);
	const [org, _setOrg] = useState<Membership | null>(null);

	useEffect(()=>{
		if(org)
			return;
		if (!_me?.canCreateOrgs && orgs.length == 1){
			setOrg(orgs[0]);
		} else {
			const orgIdStr=sessionStorage.getItem("selectedOrg");
			if(orgIdStr){
				const orgId=parseInt(orgIdStr);
				for(const org of orgs){
					if(org.organization!.id==orgId){
						setOrg(org);
						break;
					}
				}
			}
		}
		if(loading)
			setLoading(null);
	}, [orgs]);

	const setOrg=(org: Membership | null)=>{
		if(org && org.organization){
			sessionStorage.setItem("selectedOrg", org.organization!.id.toString());
			document.title=appName+": "+org.organization!.name;
			jobService.init(org.organization!.id);
			memberService.init(org.organization!.id);
			assignmentService.init(org.organization!.id);
			resourcesService.init(org.organization!.id);
			syncService.connect(org.organization!.id);
			if(!debug && lifeCycle.getStage()==LifeCycleStage.PreInit)
				lifeCycle.start();
		} else{
			document.title=appName;
			sessionStorage.removeItem("selectedOrg");
		}
		_setOrg(org);
	};

	useEffect(()=>{
		fetchMe();
	}, []);

	// 6. Test the secure API instance
	const fetchMe = async () => {
		setLoading(LoadingStage.Me);
		let targetLoadingStage: LoadingStage | null = null;
		try {
			const response = await api.get<User>("/api/me");
			_me = response.data;
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
		setLoading(LoadingStage.Orgs);
		try {
			orgsService.init();
		} catch (error) {
			console.error("API Error:", error);
			setMe(null);
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

	const exitOrg=()=>setOrg(null);

	if (loading) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Loading..." org={org} exitOrg={exitOrg} />
				{loadingStatusMessage}
			</Container>
		);
	} else if (!me) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Internal Server Error" org={org} exitOrg={exitOrg} />
				An error has occurred on the server and the application cannot be
				accessed
			</Container>
		);
	} else if (me.id == -1) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Unrecognized User" org={org} exitOrg={exitOrg} />
				You need to be added to an organization by an organization admin to
				access this application.
			</Container>
		);
	} else if(org){
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Chore Champ" org={org} exitOrg={exitOrg} />
				<OrganizationUI org={org} api={api} />
			</Container>
		);
	} else if (me.canCreateOrgs) {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Select the Organization to View" org={org} exitOrg={exitOrg} />
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
				<Banner title="No Organization Memberships" org={org} exitOrg={exitOrg} />
				You need to be added to an organization by an organization admin to
				access this application.
			</Container>
		);
	} else {
		return (
			<Container sx={{ width: "100%", mt: 4 }}>
				<Banner title="Select the Organization to View" org={org} exitOrg={exitOrg} />
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
					<CustomLogin
						authService={authService}
						init={({ onAuthSuccess, onAuthError, onLogout}) =>(
							<div style={{textAlign: "center", marginTop: "50px"}}>
								<h1>ChoreChamp Login</h1>
								<GoogleLogin
									onSuccess={(response)=>{
										if(response.credential)
											onAuthSuccess(response.credential);
										else
											onAuthError("No credential payload found");
									}}
									onError={()=>onAuthError("Authentication failed")}
									useOneTap
								/>
							</div>
						)}
						onFail={
							<div style={{ color: "red", textAlign: "center"}}>
								<h1>Chore Champ is unavailable, please try again later.</h1>
							</div>
						}>
						<ChoreChampApp />
					</CustomLogin>
				</GoogleOAuthProvider>
			</ThemeProvider>
		</StyledEngineProvider>
	);
}

export default App;
