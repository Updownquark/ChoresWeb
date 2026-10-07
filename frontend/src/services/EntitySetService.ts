import * as Utils from "../util/Utils";
import { syncService } from "./services";

export interface EntityChangeEvent<E>{
	added: readonly E [];
	removed: readonly E [];
	changed: readonly E [];
}

export type EntityChangeListener<E>=(events: EntityChangeEvent<E>)=>void;

abstract class EntitySetService<E>{
	private readonly _tableName: string;
	private readonly _entities: E[]=[];
	private readonly _entitiesById=new Map<string | number, E>();
	private _immutableEntities: readonly E[]=[];
	private _changeListener: (()=>void) | null = null;
	private readonly _listeners: EntityChangeListener<E> [] = [];

	constructor(tableName: string){
		this._tableName=tableName;
	}

	public async init(filters: object){
		this.disconnect();

		this._changeListener = syncService.subscribe<E>(this._tableName, filters, (event) => {
			switch (event.type) {
				case "reset":
					this.handleReset(event.entities);
					break;

				case "addOrUpdate":
					this.handleAddOrUpdate(event.entity);
					break;

				case "remove":
					this.handleRemove(event.entity);
					break;

				case "subscriptionRevoked":
					console.warn(`Local cache invalidated for ${this._tableName}. Subscription revoked by server security rules.`);
					this.clear();
					break;

				default:
					break;
			}
		});
	}

	private handleAddOrUpdate(entity: E){
		const id=this.getId(entity);
		const prev=this._entitiesById.get(id);
		if(prev){
			this.updateEntity(prev, entity);
			this._immutableEntities=[...this._entities];
			this.fireListeners({added: [], removed: [], changed: [entity]})
		} else{
			let index=this.indexOf(entity);
			index=-index-1; // The entity should not exist in the list yet
			this.added(index, entity);
			this._immutableEntities=[...this._entities];
			this.fireListeners({added: [entity], removed: [], changed: []});
		}
	}

	private updateEntity(prev: E, entity: E){
		const oldIndex=this.indexOf(prev);
		if(this.compare(prev, entity)==0)
			this.updated(oldIndex, entity);
		else{
			this.deleted(oldIndex, prev);
			let newIndex=this.indexOf(entity);
			newIndex=-newIndex-1;
			this.added(newIndex, entity);
		}
	}

	private handleRemove(entity: E){
		const id=this.getId(entity);
		const prev=this._entitiesById.get(id);
		if(prev){
			const index=this.indexOf(prev);
			this.deleted(index, prev);
			this._immutableEntities=[...this._entities];
			this.fireListeners({added: [], removed: [prev], changed: []})
		}
	}

	private indexOf(entity: E){
		return Utils.binarySearch(this._entities, e=>this.compareWithId(entity, e));
	}

	private compareWithId(e1: E, e2: E){
		// First, follow the custom sorting
		let comp=this.compare(e1, e2);
		if(comp!==0)
			return comp;
		const id1=this.getId(e1);
		const id2=this.getId(e2);
		if (typeof id1 === "number") {
			return (id1 as number) - (id2 as number);
		} else {
			return (id1 as string).localeCompare(id2 as string);
		}
		return comp;
	}

	private handleReset(entities: E []){
		const purged=new Set<string | number>(this._entitiesById.keys());
		const added: E [] = [];
		const removed: E [] = [];
		const changed: E [] = [];
		for(const entity of entities){
			const id=this.getId(entity);
			const prev=this._entitiesById.get(id);
			if(prev){
				purged.delete(id);
				this.updateEntity(prev, entity);
				changed.push(entity);
			} else {
				let index=this.indexOf(entity);
				index=-index-1;
				this.added(index, entity);
				added.push(entity);
			}
		}
		for(const id of purged){
			const entity=this._entitiesById.get(id)!;
			let index=this.indexOf(entity);
			this.deleted(index, entity);
			removed.push(entity);
		}

		if(added.length || removed.length || changed.length){
			this._immutableEntities=[...this._entities];
			this.fireListeners({added: added, removed: removed, changed: changed});
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
		this.fireListeners({added: [], removed: entities, changed: []});
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

	private fireListeners(event: EntityChangeEvent<E>) {
		for(const listener of this._listeners)
			listener(event);
	}
}

export default EntitySetService;
