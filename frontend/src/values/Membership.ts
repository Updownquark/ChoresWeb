import Organization from "./Organization";
import User from "./User";

interface Membership {
	readonly member: User | null;
	readonly organization: Organization | null;
	readonly manager: boolean;
	readonly worker: boolean;
	readonly level: number;
	readonly points: number;
	readonly labels: readonly string [];
}

export default Membership;
