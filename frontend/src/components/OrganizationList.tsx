import { Box, Button, Dialog, DialogTitle, IconButton, List, ListItem, ListItemText } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import Membership from "../values/Membership";
import { useState } from "react";
import Organization from "../values/Organization";
import { api } from "../services/services";

interface OrgListParams{
	orgs: readonly Membership [];
	setOrg: (membership: number)=>void;
}

const OrganizationList: React.FC<OrgListParams>=({orgs, setOrg})=>{
	const [deleteOrg, setDeleteOrg] = useState<Organization | null>(null);

	return <List sx={{width: "100%"}}>
		{orgs.map(org=>{
			const orgId=org.organization!.id;

			return <ListItem
				key="orgId"
				secondaryAction={
                    <IconButton edge="end" aria-label="delete" onClick={()=>setDeleteOrg(org.organization)}>
                      <DeleteIcon />
                    </IconButton>
                  }>
				<ListItemText
					primary={org.organization!.name}
					sx={{cursor: "pointer"}}
					onClick={()=>setOrg(orgId)}
					 />
			</ListItem>
		})}
		<Dialog open={!!deleteOrg}>
			<DialogTitle>Delete Organization?</DialogTitle>
			<Box sx={{
				display: "flex",
				flexDirection: "column",
				paddingLeft: 2,
				paddingRight: 2,
				paddingBottom: 1,
				}}>
				<Box>Permanently delete '{deleteOrg?.name}'?</Box>
				<Box>This cannot be undone.</Box>
				<Box sx={{display: "flex", flexDirection: "row", justifyContent: "space-evenly"}}>
					<Button onClick={()=>{
						api.delete("/api/orgs/"+deleteOrg!.id);
						setDeleteOrg(null);
					}}>OK</Button>
					<Button onClick={()=>setDeleteOrg(null)}>Cancel</Button>
				</Box>
			</Box>
		</Dialog>
	</List>
};

export default OrganizationList;
