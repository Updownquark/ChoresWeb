import { Box, Dialog, DialogContent, DialogTitle, Grid, IconButton, MenuItem, Switch, TextField, Tooltip, Typography, useColorScheme } from "@mui/material";
import React, { useState } from "react";
import SettingsMenu from "./SettingsMenu";
import Membership from "../../values/Membership";
import { globalLogoutTrigger } from "./CustomLogin";
import EditIcon from "@mui/icons-material/Edit";
import CheckIcon from '@mui/icons-material/Check';
import { AxiosInstance } from "axios";

interface BannerProps{
	title: string;
	org?: Membership | null;
	exitOrg: ()=>void;
	api: AxiosInstance;
}

const Banner: React.FC<BannerProps> = ({title, org, exitOrg, api}) =>{
	const [showAbout, setShowAbout]=useState(false);
	const [showProfile, setShowProfile]=useState(false);
	const [editingUserName, setEditingUserName]=useState(false);
	const [editName, setEditName] = useState("");
	const {mode, setMode, systemMode} = useColorScheme();

	const activeMode = mode==="system" ? systemMode : mode;

	let close: (()=>void) | null= null;

	const editNameValid = editName
		&& editName.length>1
		&& editName.trim()==editName;

	const doSetName = ()=>{
		api.post("/api/users", {
			id: org!.member.id,
			name: editName
		});
	}

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
			{org
				? <MenuItem onClick={()=>setShowProfile(true)}>View User Profile</MenuItem>
				: null
			}
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
			<DialogTitle>About Chore Champ</DialogTitle>
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
		</Dialog>

		<Dialog
			fullWidth
			maxWidth="sm"
			slotProps={{
				paper: {
					sx: {
						width: "500px",
						maxWidth: "90%",
						mx: "auto"
					}
				}
			}}
			sx={{textAlign: "center"}}
			open={showProfile}
			onClose={()=>setShowProfile(false)}>
			<DialogTitle>Your Profile</DialogTitle>
			<DialogContent>
				<Grid
					container
					spacing={2}
					sx={{width: "100%", mt: 0.5}}>
					<Grid columns={2}><b>Email:</b></Grid>
					<Grid columns={9}>
						{editingUserName
						? <TextField
							sx={{width: "100%"}}
							value={editName}
							onChange={e=>setEditName(e.target.value)}
							onKeyDown={e=>{
								if(e.key=="Enter" && !editNameValid){
									e.preventDefault(); //Prevents side-effects and allows the dialog to close
									doSetName();
								} else if(e.key=="Escape")
									setEditingUserName(false);
							}}
							autoFocus
							label="Enter your preferred user name" />
						: org?.member.email
						}
					</Grid>
					<Grid columns={1}>
						<Tooltip title="Change your preferred user name">
							<span>
								<IconButton onClick={()=>{
									if(editingUserName){
										if(!editNameValid)
											doSetName();
									} else {
										setEditName(org!.member.name);
										setEditingUserName(true);
									}
								}}>
									{editingUserName ? <CheckIcon /> : <EditIcon />}
								</IconButton>
							</span>
						</Tooltip>
					</Grid>
				</Grid>
			</DialogContent>
		</Dialog>
	</Box>
};

export default Banner;
