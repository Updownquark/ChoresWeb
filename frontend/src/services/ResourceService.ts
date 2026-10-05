import EntitySetService from "./EntitySetService";
import { AxiosInstance } from "axios";
import * as Utils from "../util/Utils";
import PointResource from "../values/PointResource";

class ResourceService extends EntitySetService<PointResource>{
	constructor(api: AxiosInstance){
		super(api, "resource", "/api/resources");
	}

	getId(rsrc: PointResource): number{
		return rsrc.id;
	}

	compare(rsrc1: PointResource, rsrc2: PointResource){
		return Utils.compareNumberTolerant(rsrc1.name, rsrc2.name);
	}
}

export default ResourceService;
