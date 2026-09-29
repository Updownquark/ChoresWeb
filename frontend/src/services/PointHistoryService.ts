import { AxiosInstance } from "axios";
import Membership from "../values/Membership";
import { memberService } from "./services";
import PointChangeRecord from "../values/PointChangeRecord";
import * as Utils from "../util/Utils";
import { LifeCycleService } from "./LifeCycleService";

interface HistoryChanges{
	lastChangeTime: number;
	users: readonly number[];
	jobs: readonly number [];
}

class HistoryChangeListener{
	userId: number | undefined;
	jobId: number | undefined;
	resourceId: number | undefined;
	listener: ()=>void;

	constructor(userId: number | undefined, jobId: number | undefined, resourceId: number | undefined, listener: ()=>void){
		this.userId=userId;
		this.jobId=jobId;
		this.resourceId=resourceId;
		this.listener=listener;
	}
}

class PointHistoryService{
	private readonly _api: AxiosInstance;
	private _lastChangeTime: number=0;
	private _check: (()=>Promise<void>) | null = null;
	private readonly _listeners: HistoryChangeListener []=[];
	private _heartBeatListener: (()=>void) | null = null;

	constructor(api: AxiosInstance){
		this._api=api;
	}

	public async init(orgId: number, lifeCycle: LifeCycleService){
		this.disconnect();

		this._check=async ()=>{
			if(!this._lastChangeTime)
				return; //Not initialized yet
			this.applyChanges((await this._api.get<HistoryChanges>("/api/history/changes", {
				params: {
					orgId: orgId,
					lastKnownChange: this._lastChangeTime
				}
			})).data);
		};
		this._heartBeatListener= lifeCycle.onHeartBeat(this._check);
	}

	public async getHistoryCount(org: Membership, userId: number | undefined, jobId: number | undefined, resourceId: number | undefined): Promise<number>{
		return (await this._api.get<number>("/api/history/size", {
			params: {
				orgId: org.organization!.id,
				userId: userId,
				jobId: jobId,
				resourceId: resourceId,
			}
		})).data;
	}

	public async getHistory(org: Membership, userId: number | undefined, jobId: number | undefined, resourceId: number | undefined, pageSize: number, pageNumber: number): Promise<readonly PointChangeRecord[]>{
		return (await this._api.get<PointChangeRecord[]>("/api/history", {
			params: {
				orgId: org.organization!.id,
				userId: userId,
				jobId: jobId,
				resourceId: resourceId,
				pageSize: pageSize,
				pageNumber: pageNumber,
			}
		})).data;
	}

	public onChange(userId: number | undefined, jobId: number | undefined, resourceId: number | undefined, listener: ()=>void): ()=>void {
		const hcl=new HistoryChangeListener(userId, jobId, resourceId, listener);
		this._listeners.push(hcl);
		return ()=>{
			const index=this._listeners.indexOf(hcl);
			if(index>=0)
				this._listeners.splice(index, 1);
		};
	}

	public async check(){
		if(this._check)
			this._check();
	}

	public revertHistory(org: Membership, ...itemIds: number[]){
		memberService.modify("DELETE", "/api/history", {
			orgId: org.organization!.id,
			items: itemIds,
		});
		this.check();
	}

	private applyChanges(changes: HistoryChanges){
		this._lastChangeTime=changes.lastChangeTime;
		for(const listener of this._listeners){
			let applies=false;
			if(listener.userId && Utils.binarySearch(changes.users, u=>listener.userId!-u)>=0)
				applies=true;
			if(!applies && listener.jobId && Utils.binarySearch(changes.jobs, j=>listener.jobId!-j)>=0)
				applies=true;
			if(applies)
				listener.listener();
		}
	}

	public disconnect(){
		this._lastChangeTime=0;
		if(this._heartBeatListener){
			this._heartBeatListener();
			this._heartBeatListener=null;
		}
	}
}

export default PointHistoryService;
