import Membership from "../values/Membership";
import EntitySetService from "./EntitySetService";
import * as Utils from "../util/Utils";

class MemberService extends EntitySetService<Membership>{
	private _workers: readonly Membership []=[];
	private readonly _membersByUserId= new Map<number, Membership>();

	constructor(){
		super("membership");
		this.onChange(()=>{
			this._workers=this.getAll().filter(m=>m.worker);
		});
	}

	getId(member: Membership): number{
		return member.id;
	}

	compare(member1: Membership, member2: Membership): number{
		let comp=member2.level-member1.level;
		if(comp==0)
			comp= Utils.compareNumberTolerant(member1.member!.email, member2.member!.email);
		return comp;
	}

	public getWorkers(): readonly Membership[]{
		return this._workers;
	}

	public getByUserId(id: number): Membership | undefined{
		return this._membersByUserId.get(id);
	}

	protected added(index: number, entity: Membership): void {
		super.added(index, entity);
		this._membersByUserId.set(entity.member!.id, entity);
	}

	protected updated(index: number, entity: Membership): void {
		super.updated(index, entity);
		this._membersByUserId.set(entity.member!.id, entity);
	}

	protected deleted(index: number, entity: Membership): void {
		super.deleted(index, entity);
		this._membersByUserId.delete(entity.member!.id);
	}
};

export default MemberService;