
interface PointChangeRecord{
	id: number;
	workerId: number;
	changeType: string;
	changeSourceId: number;
	changeSourceName: string;
	time: number;
	beforePoints: number;
	pointChange: number;
	quantity: number;
	valueOrRate: number;
	notes: string;
}

export default PointChangeRecord;