import EntitySetService from "./EntitySetService";
import * as Utils from "../util/Utils";
import Membership from "../values/Membership";
import { AxiosInstance } from "axios";

export default class OrganizationService extends EntitySetService<Membership>{
	constructor(api: AxiosInstance){
		super(api, "organization", "/api/orgs");
	}
	getId(org: Membership){
		return org.organization!.id;
	}

	compare(org1: Membership, org2: Membership){
		return Utils.compareNumberTolerant(org1.organization!.name, org2.organization!.name);
	}
};
