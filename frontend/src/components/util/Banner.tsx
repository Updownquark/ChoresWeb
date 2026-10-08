import { Box, Dialog, DialogTitle, MenuItem, Switch, Typography, useColorScheme, useMediaQuery } from "@mui/material";
import React, { useEffect, useState } from "react";
import SettingsMenu from "./SettingsMenu";
import Membership from "../../values/Membership";
import { globalLogoutTrigger } from "./CustomLogin";

interface BannerProps{
	title: string;
	org?: Membership | null;
	exitOrg: ()=>void;
}

const Banner: React.FC<BannerProps> = ({title, org, exitOrg}) =>{
	const [showAbout, setShowAbout]=useState(false);
	const {mode, setMode, systemMode} = useColorScheme();

	const activeMode = mode==="system" ? systemMode : mode;

	let close: (()=>void) | null= null;

	return <Box sx={{
		width: "100%",
		display: "flex",
		flexDirection: "row",
		justifyContent: "space-between",
		}}>
		<Typography variant="h2" component="h2" sx={{
			width: "100%",
			textAlign: "center"
			}}>
			{title}
		</Typography>
		<SettingsMenu close={(doClose)=>close=doClose}>
			<MenuItem>
				Display: Light
				<Switch
					checked={activeMode==="dark"}
					onChange={e=>setMode(e.target.checked ? "dark" : "light")} />
				Dark
			</MenuItem>
			<MenuItem onClick={()=>{
				setShowAbout(true);
				close!();
			}}>About Chore Champ</MenuItem>
			{org ? <MenuItem onClick={()=>{
				exitOrg();
				close!();
			}}>Exit {org!.organization!.name}</MenuItem> : null}
			<MenuItem onClick={()=>{
				if(globalLogoutTrigger) globalLogoutTrigger();
				close!();
			}}>Logout</MenuItem>
		</SettingsMenu>

		<Dialog open={showAbout} onClose={()=>setShowAbout(false)}>
			<DialogTitle title="About Chore Champ">
				<Box sx={{display: "flex", flexDirection: "column"}}>
					<Typography>An app for managing chores</Typography>
					<Typography>Version 0.0.1</Typography>
					<Typography>By Andrew Butler</Typography>
					<Typography>Profiled with
						<a href="" >
							<img src="https://www.ej-technologies.com/images/product_banners/jprofiler_small.png" />
						</a>
					</Typography>
				</Box>
			</DialogTitle>
		</Dialog>
	</Box>
};

export default Banner;
