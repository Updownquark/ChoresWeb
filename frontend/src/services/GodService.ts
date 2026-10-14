import { AxiosInstance } from "axios";
import User from "../values/User";
import { syncService } from "./services";
import { ApplicationInfo } from "../values/ApplicationInfo";
import { SyncDataEvent } from "./SyncService";

interface Listener{
    globalDataChanged: ()=>void;
    userSetChanged: (event: SyncDataEvent<User>)=>void;
    orgSetChanged: ()=>void;
}

export interface BulkQueryResult<T> {
	resultCount: number;
	pageResults: T[];
}

interface BulkUserMod{
    god?: boolean;
    globalAdmin?: boolean;
    blocked?: boolean;
}

export default class GodService{
    private readonly _api: AxiosInstance;
    private _isGod: boolean=false;
    private _isInitialized: boolean=false;
    private _app: ApplicationInfo | undefined;
    private _godListeners: (()=>void) | null = null;
    private _listeners=new Set<Listener>();

    constructor(api: AxiosInstance){
        this._api=api;
    }

    public init(me: User){
        if(this._isInitialized)
            return;
        this._setGod(me.god);
        syncService.subscribe("applicationInfo", {}, event=>{
            switch(event.type){
                case "reset":
                    this._setApplicationInfo(event.entities[0] as ApplicationInfo);
                    break;
                case "addOrUpdate":
                    this._setApplicationInfo(event.entity as ApplicationInfo);
                    break;
            }
        });
        syncService.subscribe("user", {id: me.id}, event=>{
            switch(event.type){
                case "addOrUpdate":
                    const user=event.entity as User;
                    if(user.id==me.id)
                        this._setGod(user.god);
            }
        })
        this._isInitialized=true;
    }

    private _setApplicationInfo(app: ApplicationInfo){
        this._app=app;
        this._listeners.forEach(listener=>listener.globalDataChanged());
    }

    private _setGod(god: boolean){
        if(this._isGod==god)
            return;
        this._isGod=god;
        if(god){
            console.log("Activating God");
            const usersListener=syncService.subscribe("user", {}, event=>{
                switch(event.type){
                    case "addOrUpdate":
                    case "remove":
                        this._listeners.forEach(listener=>listener.userSetChanged(event));
                }
            });
            const orgsListener=syncService.subscribe("organization", {}, event=>{
                switch(event.type){
                    case "addOrUpdate":
                    case "remove":
                        this._listeners.forEach(listener=>listener.orgSetChanged());
                }
            });
            this._godListeners=()=>{
                console.log("Deactivating God");
                usersListener();
                orgsListener();
            }
        } else if(this._godListeners){
            this._godListeners();
            this._godListeners=null;
        }
        this._listeners.forEach(listener=>listener.globalDataChanged());
    }

    public get app(): ApplicationInfo | undefined{
        return this._app;
    }

    public get god(): boolean {
        return this._isGod;
    }

    public get orgCount(): Promise<number>{
        return this._api.get<number>("/api/god/org-count").then(response=>response.data);
    }

    public get userCount(): Promise<number>{
        return this._api.get<number>("/api/god/user-count").then(response=>response.data);
    }

    public getUsers(query: string | null, pageSize: number, pageNumber: number): Promise<BulkQueryResult<User>>{
        return this._api.get<BulkQueryResult<User>>("/api/god/users", {
			params: {
				query: query ? query : undefined,
				pageSize: pageSize,
				pageNumber: pageNumber,
			},
		}).then(response=>response.data);
    }

    public modifyUsers(
        query: string | null,
        selectedUsers: Set<number>,
        block: boolean | undefined,
        globalAdmin: boolean | undefined,
        god: boolean | undefined){
        let endpoint="/api/god/users";
        let mod: BulkUserMod = {};
        if(typeof block === "boolean")
            mod.blocked=block;

        let params=undefined;
        if(selectedUsers.size)
            params={selectedUserIds: [...selectedUsers].join(",")};
        else if(query?.length)
            params={query: query}
        this._api.post(endpoint, {
            blocked: block,
            globalAdmin: globalAdmin,
            god: god,
        }, {params: params});
    }

    public deleteUsers(
        query: string | null,
        selectedUsers: Set<number>){
        let endpoint="/api/god/users";
        let params=undefined;
        if(selectedUsers.size)
            params={selectedUserIds: [...selectedUsers].join(",")};
        else if(query?.length)
            params={query: query}
        this._api.delete(endpoint, {params: params});
    }

    public onChange(listener: Listener): (()=>void){
        this._listeners.add(listener);
        return ()=>{
            this._listeners.delete(listener);
        };
    }
}
