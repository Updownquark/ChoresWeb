import Organization from "./Organization";
import User from "./User";

interface Membership {
	readonly id: number;
	readonly member: User;
	readonly organization: Organization;
	readonly name: string;
	readonly manager: boolean;
	readonly worker: boolean;
	readonly level: number;
	readonly points: number;
	readonly labels: readonly string [];
}

export default Membership;
