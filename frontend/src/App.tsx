import { useState, useEffect } from "react";
import { GoogleLogin, GoogleOAuthProvider } from "@react-oauth/google";
import { CLIENT_ID } from "./config/backend";
import { myTheme } from "./theme";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { Button, Container, CssBaseline, Typography } from "@mui/material";
import User from "./values/User";
import Membership from "./values/Membership";
import OrganizationList from "./components/OrganziationList";
import OrganizationUI from "./components/OrganizationUI";
import { authService, api, lifeCycle, jobService, memberService, assignmentService, resourcesService, historyService, debug} from "./services/services";
import { CustomLogin } from "./components/util/CustomLogin";
import { LifeCycleStage } from "./services/LifeCycleService";

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

function ChoreChampApp() {
	const [me, setMe] = useState<User | null>(null);
	const [loading, setLoading] = useState<LoadingStage | null>(null);
	const [orgs, setOrgs] = useState<readonly Membership[] | null>();
	const [org, _setOrg] = useState<Membership | null>(null);

	const setOrg=(org: Membership | null)=>{
		if(org && org.organization){
			sessionStorage.setItem("selectedOrg", org.organization!.id.toString());
			document.title=appName+": "+org.organization!.name;
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
			resourcesService.init(
				"/api/resources/by-org/"+org.organization!.id,
				"/api/resources/changes/"+org.organization!.id,
				lifeCycle);
			historyService.init(org.organization!.id, lifeCycle);
			if(!debug && lifeCycle.getStage()==LifeCycleStage.PreInit)
				lifeCycle.start();
		} else
			document.title=appName;
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
		let targetLoadingStage: LoadingStage | null = null;
		try {
			const response = await api.get<Membership[]>("/api/orgs");
			const _orgs = response.data;
			setOrgs(_orgs);
			if (!_me?.canCreateOrgs && _orgs.length == 1){
				setOrg(org);
			} else {
				const orgIdStr=sessionStorage.getItem("selectedOrg");
				if(orgIdStr){
					const orgId=parseInt(orgIdStr);
					for(const org of _orgs){
						if(org.organization!.id==orgId){
							setOrg(org);
							break;
						}
					}
				}
			}
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

	if (loading) {
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
								<h1>ChoreChamp Login Failed, please try again.</h1>
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
