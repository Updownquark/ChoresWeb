import EntitySetService from "./EntitySetService";
import Job from "../values/Job";
import * as Utils from "../util/Utils";

class JobService extends EntitySetService<Job>{
	private _activeJobs: readonly Job [] = [];

	constructor(){
		super("job");
		this.onChange(()=>{
			this._activeJobs=this.getAll().filter(job=>job.active);
		});
	}

	getId(job: Job): number{
		return job.id;
	}

	compare(job1: Job, job2: Job){
		return Utils.compareNumberTolerant(job1.name, job2.name);
	}

	public getActiveJobs(): readonly Job[]{
		return this._activeJobs;
	}
}

export default JobService;
