import axios from "axios";
import { LifeCycleService } from "./LifeCycleService";
import DemoBackend from "./Backend";
import { BACKEND_API_URL } from "../config/backend";
import JobService from "./JobService";
import MemberService from "./MemberService";
import AssignmentService from "./AssignmentService";

// Google Gemini helped me with this authorization code
const api = axios.create({
	baseURL: BACKEND_API_URL
});

// 2. Define a type for a function that can fetch the token dynamically
export type TokenProvider = () => string | null;

// 3. We create a placeholder reference that we will populate inside App.tsx
export const authContextHolder: { getToken?: TokenProvider; triggerLogin?: () => void } = {};

// Attach bearer token to outgoing requests
api.interceptors.request.use((config) => {
	if (authContextHolder.getToken) {
		const token = authContextHolder.getToken();
		if (token) {
			config.headers.Authorization = `Bearer ${token}`;
		}
	}
	return config;
});

//Redirect to the login page if an API call fails due to the token expiring
api.interceptors.response.use(
	(response) => response,
	(error) => {
		if (error.response && error.response.status == 401) {
			if (authContextHolder.triggerLogin) {
				authContextHolder.triggerLogin();
			}
		}
		return Promise.reject(error);
	}
);

export const lifeCycle = new LifeCycleService();
export const backend = new DemoBackend(api);
export const jobService=new JobService(api);
export const memberService=new MemberService(api);
export const assignmentService=new AssignmentService(api);
