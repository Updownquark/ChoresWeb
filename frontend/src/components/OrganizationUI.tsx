import { AxiosInstance } from "axios";
import Membership from "../values/Membership";
import { Box, IconButton, Tab, Tabs, Tooltip } from "@mui/material";
import { useRef, useState } from "react";
import ValidatedTextField from "./util/ValidatedTextField";
import CancelIcon from "@mui/icons-material/Cancel";
import EditIcon from "@mui/icons-material/Edit";
import AssignmentsUI from "./AssignmentsUI";
import JobsUI from "./JobsUI";
import WorkersUI from "./WorkersUI";
import ResourcesUI from "./ResourcesUI";
import FileUploadIcon from "@mui/icons-material/FileUpload";

interface OrgUIParams{
	org: Membership;
	api: AxiosInstance;
}

const OrganizationUI: React.FC<OrgUIParams> =({org, api})=>{
	const getStoredSelectedTab=(): number =>{
		const selectedTabStr=sessionStorage.getItem("mainTab");
		return selectedTabStr ? parseInt(selectedTabStr) : 0;
	}

	const [orgName, setOrgName] = useState(org.organization!.name);
	const [editingName, setEditingName]=useState(false);
	const [selectedTab, _setSelectedTab]=useState(getStoredSelectedTab());

	const fileInputRef = useRef<HTMLInputElement>(null);

	const setSelectedTab=(tab: number)=>{
		_setSelectedTab(tab);
		sessionStorage.setItem("mainTab", tab.toString());
	}

	const uploadBackupData=async (event: React.ChangeEvent<HTMLInputElement>)=>{
		const files=event.target.files;
		if(!files || files.length!=1)
			return;
		const formData=new FormData();
		formData.append("file", files[0]);
		api.post("/api/upload/upload", formData, {
			headers: {
				"Content-Type": "multipart/form-data"
			},
			params: {
				orgId: org.organization!.id
			}
		});
	}

	return <Box sx={{display: "flex", flexDirection: "column", alignItems: "flex-start"}}>
		<Box sx={{display:"flex", flexDirection: "row", alignItems: "center"}}>
			{editingName ?
				<>
					<ValidatedTextField value={orgName} parser={s=>s} onChange={newName=>{
						api.post("/api/orgs/set-name", {
							id: org.organization!.id,
							name: newName
						}).then(()=>{
							setOrgName(newName);
							setEditingName(false);
						});
					}} validator={name=>{
						if(!name || name.length==0)
							return "Name cannot be empty";
						else if(name.length>200)
							return "Name cannot exceed 200 characters";
						else
							return null;
					}} onBlur={e=>setEditingName(false)}
					autoFocus selectAllOnFocus />
					&nbsp;&nbsp;
					<Tooltip title="Cancel name editing">
						<IconButton onClick={e=>setEditingName(false)}>
							<CancelIcon />
						</IconButton>
					</Tooltip>
				</>
			:	<>
					<h3>{orgName}</h3>
					&nbsp;&nbsp;
					<IconButton onClick={e=>setEditingName(true)}>
						<EditIcon />
					</IconButton>
					{/* A delete button should go here when I'm up to getting confirmation working */}
					{org.manager ? <>
						&nbsp;&nbsp;
						<input type="file" ref={fileInputRef} onChange={uploadBackupData} style={{display: "none"}} />
						<Tooltip title="Upload existing ChoreChamp data">
							<IconButton onClick={e=>fileInputRef.current?.click()}>
								<FileUploadIcon />
							</IconButton>
						</Tooltip>
						</>
						: null
					}
			</>
		}</Box>
		<Tabs value={selectedTab} onChange={(e, newValue)=>setSelectedTab(newValue)}>
			<Tab label="Assignments" />
			<Tab label="Workers" />
			<Tab label="Jobs" />
			<Tab label="Resources" />
		</Tabs>
		<AssignmentsUI org={org} api={api} visible={selectedTab==0} />
		<WorkersUI org={org} api={api} visible={selectedTab==1} />
		<JobsUI org={org} api={api} visible={selectedTab==2} />
		<ResourcesUI org={org} api={api} visible={selectedTab==3} />
	</Box>
};

export default OrganizationUI;
