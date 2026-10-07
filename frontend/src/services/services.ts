import LifeCycleService from "./LifeCycleService";
import DemoBackend from "./Backend";
import { BACKEND_API_URL } from "../config/backend";
import JobService from "./JobService";
import MemberService from "./MemberService";
import AssignmentService from "./AssignmentService";
import ResourceService from "./ResourceService";
import PointHistoryService from "./PointHistoryService";
import TokenAuthService from "../util/TokenAuthService";
import SyncService from "./SyncService";
import OrganizationService from "./OrganizationService";

export const debug=true;

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
