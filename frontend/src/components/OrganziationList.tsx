import { List, ListItemText } from "@mui/material";
import Membership from "../values/Membership";

interface OrgListParams{
	orgs: readonly Membership [];
	setOrg: (membership: number)=>void;
}

const OrganizationList: React.FC<OrgListParams>=({orgs, setOrg})=>{
	return <List>
		{orgs.map(org=>{
			const orgId=org.organization!.id;
			return <ListItemText
				key="orgId"
				primary={org.organization!.name}
				onClick={e=>setOrg(orgId)}/>;
		})}
	</List>
};

export default OrganizationList;
