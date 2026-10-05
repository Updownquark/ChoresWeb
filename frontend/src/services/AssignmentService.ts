import { AxiosInstance } from "axios";
import Assignment from "../values/Assignment";
import EntitySetService from "./EntitySetService";

class AssignmentService extends EntitySetService<Assignment>{
	private static readonly EMPTY_ASSN_MAP: ReadonlyMap<number, Assignment> = new Map();

	private readonly  _assignmentsByUser = new Map<number, Map<number, Assignment>>();
	private readonly  _assignmentsByJob = new Map<number, Map<number, Assignment>>();

	constructor(api: AxiosInstance){
		super(api, "assignment", "/api/assignments");
	}

	public getUserAssignments(userId: number): ReadonlyMap<number, Assignment>{
		const assns=this._assignmentsByUser.get(userId);
		return assns ? assns : AssignmentService.EMPTY_ASSN_MAP;
	}

	public getJobAssignments(jobId: number): ReadonlyMap<number, Assignment>{
		const assns=this._assignmentsByJob.get(jobId);
		return assns ? assns : AssignmentService.EMPTY_ASSN_MAP;
	}

	getId(assn: Assignment){
		return assn.userId+"/"+assn.jobId;
	}

	compare(assn1: Assignment, assn2: Assignment){
		let comp=assn2.userId-assn1.userId;
		if(comp==0)
			comp=assn2.jobId-assn1.jobId;
		return comp;
	}

	added(index: number, assn: Assignment){
		super.added(index, assn);
		this.install(assn);
	}

	private install(assn: Assignment){
		let temp=this._assignmentsByUser.get(assn.userId);
		if(!temp){
			temp=new Map<number, Assignment>();
			this._assignmentsByUser.set(assn.userId, temp);
		}
		temp.set(assn.jobId, assn);

		temp=this._assignmentsByJob.get(assn.jobId);
		if(!temp){
			temp=new Map<number, Assignment>();
			this._assignmentsByJob.set(assn.jobId, temp);
		}
		temp.set(assn.userId, assn);
	}

	updated(index: number, assn: Assignment){
		super.updated(index, assn);
		this.install(assn);
	}

	deleted(index: number, assn: Assignment){
		super.deleted(index, assn);
		let temp=this._assignmentsByUser.get(assn.userId);
		if(temp){
			temp.delete(assn.jobId);
			if(temp.size==0)
				this._assignmentsByUser.delete(assn.userId);
		}
		temp=this._assignmentsByJob.get(assn.jobId);
		if(temp){
			temp.delete(assn.userId);
			if(temp.size==0)
				this._assignmentsByJob.delete(assn.jobId);
		}
	}

	clear(){
		this._assignmentsByUser.clear();
		this._assignmentsByJob.clear();
		super.clear();
	}
}

export default AssignmentService;