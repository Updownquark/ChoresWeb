import { AxiosInstance } from "axios";
import Membership from "../values/Membership";
import EntitySetService from "./EntitySetService";
import * as Utils from "../util/Utils";

class MemberService extends EntitySetService<Membership>{
	private _workers: readonly Membership []=[];

	constructor(api: AxiosInstance){
		super(api);
		this.onChange(()=>{
			this._workers=this.getAll().filter(m=>m.worker);
		});
	}

	getId(member: Membership): number{
		return member.member!.id;
	}

	compare(member1: Membership, member2: Membership): number{
		let comp=member2.level-member1.level;
		if(comp==0)
			comp= Utils.compareNumberTolerant(member1.member!.email, member2.member!.email);
		return comp;
	}

	isDeleted(member: Membership){
		return member.deleted;
	}
	public getWorkers(): readonly Membership[]{
		return this._workers;
	}
};

export default MemberService;