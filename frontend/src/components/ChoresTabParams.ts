import { AxiosInstance } from "axios";
import Membership from "../values/Membership";

interface ChoresTabParams{
	api: AxiosInstance;
	org: Membership;
	visible: boolean;
}

export default ChoresTabParams;
