
interface Job{
	id: number;
	name: string;
	value: number;
	minLevel: number;
	maxLevel: number;
	inclusionLabels: readonly string [];
	exclusionLabels: readonly string [];
	priority: number;
	active: boolean;
	deleted: boolean;
}

export default Job;
