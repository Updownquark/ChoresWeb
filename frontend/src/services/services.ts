import LifeCycleService from "./LifeCycleService";
import DemoBackend from "./Backend";
import { BACKEND_API_URL, CHORES_DEBUG } from "../config/backend";
import JobService from "./JobService";
import MemberService from "./MemberService";
import AssignmentService from "./AssignmentService";
import ResourceService from "./ResourceService";
import PointHistoryService from "./PointHistoryService";
import TokenAuthService from "../util/TokenAuthService";
import SyncService from "./SyncService";
import OrganizationService from "./OrganizationService";

var d: boolean=false;
switch(typeof CHORES_DEBUG){
    case "boolean":
        d=CHORES_DEBUG as boolean;
        break;
    case "string":
        d="true" == CHORES_DEBUG;
        break;
    default:
        d=false;
}
export const debug=d;

export const authService=new TokenAuthService(BACKEND_API_URL);

export const api=authService.getClient();

export const lifeCycle = new LifeCycleService();
export const backend = new DemoBackend(api);
export const syncService=new SyncService(authService, api)
export const orgsService=new OrganizationService();
export const jobService=new JobService();
export const memberService=new MemberService();
export const assignmentService=new AssignmentService();
export const resourcesService=new ResourceService();
export const historyService=new PointHistoryService(api);
