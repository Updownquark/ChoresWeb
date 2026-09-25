import { AxiosInstance } from "axios";
import { LifeCycleService } from "./LifeCycleService";
import * as Utils from "../util/Utils";

interface EntityChangeSet<E>{
	lastTime: number,
	changes: readonly E[];
}

abstract class EntitySetService<E>{
	private readonly _api: AxiosInstance;
	private _initDataApiPath: string | null = null;
	private readonly _entities: E[]=[];
	private readonly _entitiesById=new Map<string | number, E>();
	private _immutableEntities: readonly E[]=[];
	private _lastChangeTime: number=0;
	private _heartBeatListener: (()=>void) | null = null;
	private _check: (()=>Promise<void>) | null = null;
	private readonly _listeners: (()=>void) [] = [];

	constructor(api: AxiosInstance){
		this._api=api;
	}

	public async init(initDataApiPath: string, changesApiPath: string, lifeCycle: LifeCycleService){
		this._initDataApiPath=initDataApiPath;
		this.disconnect();

		this.applyChanges((await this._api.get<EntityChangeSet<E>>(this._initDataApiPath)).data, true);

		this._check=async ()=>{
			if(!this._lastChangeTime)
				return; //Not initialized yet
			this.applyChanges((await this._api.get<EntityChangeSet<E>>(changesApiPath, {
				params: {
					lastKnownChange: this._lastChangeTime
				}
			})).data, false);
		};
		this._heartBeatListener= lifeCycle.onHeartBeat(this._check);
	}

	private async applyChanges(changes: EntityChangeSet<E>, fullSet: boolean){
		if(!changes){ // Out-of-date.  Need to re-initialize.
			this._lastChangeTime=0;
			changes=(await this._api.get<EntityChangeSet<E>>(this._initDataApiPath!)).data;
			fullSet=true;
		}
		this._lastChangeTime=changes.lastTime;
		if(changes.changes.length==0){
			if(fullSet && this._entities.length>0){
				this.clear();
			}
			return;
		}
		const toDelete: Set<string | number> | null= fullSet ? new Set<string | number>() : null;
		for(const change of changes.changes){
			const id=this.getId(change);
			const prev=this._entitiesById.get(id);
			const deleted=this.isDeleted(change);
			if(prev){ //Updated or deleted entity
				const index=Utils.binarySearch(this._entities, e=>this.compare(prev, e));
				if(toDelete)
					toDelete.delete(id);
				if(deleted)
					this.deleted(index, prev);
				else
					this.updated(index, change);
			} else if(!deleted) { //New entity
				let index=Utils.binarySearch(this._entities, e=>this.compare(change, e));
				if(index<0)
					index=-index-1;
				else{
					while(index<this._entities.length && this.compare(change, this._entities[index])==0)
						index++;
				}
				this.added(index, change);
			}
		}
		if(toDelete){
			for(const id of toDelete){
				const entity=this._entitiesById.get(id);
				if(entity){
					const index=Utils.binarySearch(this._entities, e=>this.compare(entity, e));
					this.deleted(index, entity);
				}
			}
		}
		this._immutableEntities=this._entities;
		this.fireListeners();
	}

	public disconnect(){
		this._lastChangeTime=0;
		this.clear();
		if(this._heartBeatListener){
			this._heartBeatListener();
			this._heartBeatListener=null;
		}
	}

	protected added(index: number, entity: E){
		this._entities.splice(index, 0, entity);
		this._entitiesById.set(this.getId(entity), entity);
	}

	protected updated(index: number, entity: E){
		this._entities[index]=entity;
		this._entitiesById.set(this.getId(entity), entity);
	}

	protected deleted(index: number, entity: E){
		this._entities.splice(index, 1);
		this._entitiesById.delete(this.getId(entity));
	}

	protected clear(){
		this._entities.splice(0, this._entities.length);
		this._entitiesById.clear();
		this._immutableEntities=[];
		this.fireListeners();
	}

	abstract getId(entity: E): string | number;

	abstract compare(e1: E, e2: E): number;

	abstract isDeleted(entity: E): boolean;

	public getAll(): readonly E []{
		return this._immutableEntities;
	}

	public getById(id: string | number): E | undefined {
		return this._entitiesById.get(id);
	}

	public async check(){
		if(this._check)
			return this._check();
	}

	public async modify(method: string, path: string, body?: object, params: object = {}){
		this.applyChanges((await this._api.request<EntityChangeSet<E>>({
			method: method.toLowerCase(),
			url: path,
			data: body,
			params: {
				...params,
				lastKnownChange: this._lastChangeTime
			}
		})).data, false);
	}

	public onChange(listener: ()=>void): ()=>void {
		this._listeners.push(listener);
		return ()=>{
			const index=this._listeners.indexOf(listener);
			if(index>=0)
				this._listeners.splice(index, 1);
		};
	}

	private fireListeners() {
		for(const listener of this._listeners)
			listener();
	}
}

export default EntitySetService;
