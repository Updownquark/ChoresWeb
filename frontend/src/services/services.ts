import LifeCycleService from "./LifeCycleService";
import DemoBackend from "./Backend";
import { BACKEND_API_URL } from "../config/backend";
import JobService from "./JobService";
import MemberService from "./MemberService";
import AssignmentService from "./AssignmentService";
import ResourceService from "./ResourceService";
import PointHistoryService from "./PointHistoryService";
import TokenAuthService from "../util/TokenAuthService";

export const debug=true;

export const authService=new TokenAuthService(BACKEND_API_URL);

export const api=authService.getClient();

export const lifeCycle = new LifeCycleService();
export const backend = new DemoBackend(api);
export const jobService=new JobService(api);
export const memberService=new MemberService(api);
export const assignmentService=new AssignmentService(api);
export const resourcesService=new ResourceService(api);
export const historyService=new PointHistoryService(api);
