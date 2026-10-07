import EntitySetService from "./EntitySetService";
import * as Utils from "../util/Utils";
import Membership from "../values/Membership";

export default class OrganizationService extends EntitySetService<Membership>{
	constructor(){
		super("membership");
	}
	getId(org: Membership){
		return org.organization!.id;
	}

	compare(org1: Membership, org2: Membership){
		return Utils.compareNumberTolerant(org1.organization!.name, org2.organization!.name);
	}
};
