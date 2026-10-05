import { AxiosInstance } from "axios";
import * as Utils from "../util/Utils";
import { syncService } from "./services";

export interface EntityChangeEvent<E>{
	added?: E;
	removed?: E;
	changed?: E;
}

export type EntityChangeListener<E>=(events: readonly EntityChangeEvent<E>[])=>void;

abstract class EntitySetService<E>{
	private readonly _api: AxiosInstance;
	private readonly _tableName: string;
	private readonly _apiPath: string;
	private readonly _entities: E[]=[];
	private readonly _entitiesById=new Map<string | number, E>();
	private _immutableEntities: readonly E[]=[];
	private _changeListener: (()=>void) | null = null;
	private readonly _listeners: EntityChangeListener<E> [] = [];

	constructor(api: AxiosInstance, tableName: string, apiPath: string){
		this._api=api;
		this._tableName=tableName;
		this._apiPath=apiPath;
	}

	public async init(orgId?: number){
		this.disconnect();

		let byOrgPath=this._apiPath;
		if(orgId)
			byOrgPath+="/by-org/"+orgId;
		const entities=(await this._api.get<E[]>(byOrgPath)).data;
		entities.sort((e1, e2)=>this.compare(e1, e2));
		let i=0;
		const initialAdds:EntityChangeEvent<E>[] =[];
		for(const entity of entities){
			this.added(i++, entity);
			initialAdds.push({added: entity});
		}
		this._immutableEntities=[...entities];
		if(initialAdds.length)
			this.fireListeners(initialAdds);

		this._changeListener=syncService.subscribe(this._tableName, event=>{
			console.log(this._tableName, event.exists ? "add/update" : "delete", event.entityId);
			if(event.exists)
				this.addOrUpdate(event.entityId);
			else{
				const entity=this._entitiesById.get(event.entityId);
				if(entity){
					const index=Utils.binarySearch(this._entities, e=>this.compare(entity, e));
					this.deleted(index, entity)
					this._immutableEntities=[...this._entities];
					this.fireListeners([{removed: entity}]);
				}
			}
		});
	}

	private async addOrUpdate(id: number){
		const entity=(await this._api.get<E>(this._apiPath+"/"+id)).data;
		const prev=this._entitiesById.get(id);
		if(prev){
			const index=Utils.binarySearch(this._entities, e=>this.compare(prev, e));
			if(entity){
				console.log("Updating "+this._tableName, entity);
				this.updated(index, entity);
				this._immutableEntities=[...this._entities];
				this.fireListeners([{changed: entity}])
			} else{
				this.deleted(index, prev);
				this._immutableEntities=[...this._entities];
				this.fireListeners([{removed: prev}])
			}
		} else if(entity){
			let index=Utils.binarySearch(this._entities, e=>this.compare(entity, e));
			if(index<0)
				index=-index-1;
			else{
				while(index<this._entities.length && this.compare(entity, this._entities[index])==0)
					index++;
			}
			this.added(index, entity);
			this._immutableEntities=[...this._entities];
			this.fireListeners([{added: entity}]);
		}
	}

	public disconnect(){
		this.clear();
		if(this._changeListener){
			this._changeListener();
			this._changeListener=null;
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
		const entities=[...this._entities];
		entities.reverse();
		this._entities.splice(0, this._entities.length);
		this._entitiesById.clear();
		this._immutableEntities=[];
		for(const entity of entities)
			this.fireListeners([{removed: entity}]);
	}

	abstract getId(entity: E): string | number;

	abstract compare(e1: E, e2: E): number;

	public getAll(): readonly E []{
		return this._immutableEntities;
	}

	public getById(id: string | number): E | null {
		return this._entitiesById.get(id) ?? null;
	}

	public onChange(listener: EntityChangeListener<E>): ()=>void {
		this._listeners.push(listener);
		return ()=>{
			const index=this._listeners.indexOf(listener);
			if(index>=0)
				this._listeners.splice(index, 1);
		};
	}

	private fireListeners(events: readonly EntityChangeEvent<E>[]) {
		for(const listener of this._listeners)
			listener(events);
	}
}

export default EntitySetService;
