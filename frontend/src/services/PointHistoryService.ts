import { AxiosInstance } from "axios";
import Membership from "../values/Membership";
import PointChangeRecord from "../values/PointChangeRecord";
import { syncService } from "./services";

class PointHistoryService{
	private readonly _api: AxiosInstance;
	private _orgId: number | null = null;

	constructor(api: AxiosInstance){
		this._api=api;
	}

	public setOrg(orgId: number){
		this._orgId=orgId;
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

	public revertHistory(org: Membership, ...itemIds: number[]){
		this._api.delete("/api/history", {
			data: {
				orgId: org.organization!.id,
				items: itemIds,
			}
		});
	}

	public onChange(listener: ()=>void): (()=>void) {
		return syncService.subscribe("history", {organization: this._orgId}, listener);
	}
}

export default PointHistoryService;
