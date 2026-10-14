import React, { useEffect, useState } from "react";
import Membership from "../values/Membership";
import { AxiosInstance } from "axios";
import { debug, godService, lifeCycle, syncService } from "../services/services";
import User from "../values/User";
import {
	Box,
	Button,
	Checkbox,
	Dialog,
	DialogContent,
	DialogTitle,
	Divider,
	Grid,
	IconButton,
	keyframes,
	MenuItem,
	Paper,
	Select,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableHead,
	TableRow,
	Tooltip,
	Typography,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import DeleteIcon from '@mui/icons-material/Delete';
import ClearIcon from '@mui/icons-material/Clear';
import FirstPageIcon from '@mui/icons-material/FirstPage';
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import LastPageIcon from '@mui/icons-material/LastPage';
import ValidatedTextField from "./util/ValidatedTextField";

export interface GodViewParams {
	me: User | null;
	api: AxiosInstance;
}

interface BulkUserMod{
    god?: boolean;
    globalAdmin?: boolean;
    block?: boolean;
}

interface SpringProblemDetail{
    type?: string;
    title?: string;
    statis?: number;
    detail?: string;
    [key: string]: any;
}

interface UserTableState{
    query: string | null;
    pageNumber: number;
    // dirty: boolean;
}

const spin = keyframes`
    0% {
        transform: rotate(0deg);
    }
    100% {
        transform: rotate(360deg);
    }
`;
const spinAnimation=`${spin} 1.5s linear infinite`;

const DATE_FORMAT=new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "numeric",
});
const NUMBER_FORMAT=new Intl.NumberFormat();

const selectedUsers=new Set<number>();

let hackNonClosureUsers: User [] = [];

const GodView: React.FC<GodViewParams> = ({ me, api }) => {
    const [open, setOpen] = useState(false);
	const [orgCount, setOrgCount] = useState(0);
	const [orgCountDirty, setOrgCountDirty] = useState(true);
	const [userQuery, setUserQuery] = useState<string | null>(null);
    const [prevTableState, setPrevTableState] = useState<UserTableState | null>(null);
    const [queryError, setQueryError] = useState<string | null>(null);
	const [userPageSize, setUserPageSize] = useState(100);
	const [userPageNumber, setUserPageNumber] = useState(0);
    const [userTableRefreshTime, setUserTableRefreshTime] = useState<Date | null>(null);
	// const [userTableDirty, _setUserTableDirty] = useState(true);
    const [userTableRefreshing, setUserTableRefreshing] = useState(false);
	const [god, setGod] = useState(me?.god);

	const [totalUserCount, setTotalUserCount] = useState(0);
	const [filteredUserCount, setFilteredUserCount] = useState(0);
	const [userTableTitle, setUserTableTitle] = useState("");
    const [users, setUsers] = useState<User []>([]);
    const [, _setSelectionChanged] = useState(0);

    const [confirmOpen, setConfirmOpen] = useState(false);
    const [newOpenState, setNewOpenState] = useState(false);
    const [confirmUserAction, setConfirmUserAction] = useState(false);
    const [userActionMod, setUserActionMod] = useState<BulkUserMod | null>(null);

    const selectionChanged=()=>_setSelectionChanged(v=>v+1);
    // const setUserTableDirty=(dirty: boolean)=>{
    //     _setUserTableDirty(dirty);
    // }
    const maxPageNumber=(filteredUserCount-1)/userPageSize+1;

    useEffect(()=>{
        hackNonClosureUsers=users;
    }, [users]);

	const refreshOrgCount = () => {
        godService.orgCount.then(orgCount => {
			setOrgCount(orgCount);
			setOrgCountDirty(false);
		});
	};
	const refreshUserTable = (withCount: boolean) => {
        setUserTableRefreshing(true);
        if(withCount){
            godService.userCount.then(userCount => {
                setTotalUserCount(userCount);
                if(userCount<=userPageSize*userPageNumber){
                    setUserPageNumber(0);
                    // setUserTableDirty(true);
                }
            });
        }
		godService.getUsers(userQuery, userPageSize, userPageNumber).then(result => {
            setFilteredUserCount(result.resultCount);
            setUsers(result.pageResults);
            // setUserTableDirty(false);
            setPrevTableState(null);
            setUserTableRefreshTime(new Date());
        }).catch(error=>{
            if(error.code=="ERR_BAD_REQUEST" && prevTableState){
                setUserQuery(prevTableState.query);
                setUserPageNumber(prevTableState.pageNumber);
                // setUserTableDirty(prevTableState.dirty);
                setQueryError((error.response?.data as SpringProblemDetail).detail ?? "Unspecified query error");
            }
        }).finally(()=>{
            setUserTableRefreshing(false);
        });
	};
	useEffect(() => {
		if (god){
            refreshOrgCount();
            refreshUserTable(true);
        }
	}, [god]);
    useEffect(()=>{
        refreshUserTable(false);
    }, [userQuery, userPageSize, userPageNumber]);

	useEffect(() => {
        godService.init(me!);
        setOpen(godService.app?.openToWorld ?? false);
        const godListener=godService.onChange({
            globalDataChanged: ()=>{
                setGod(godService.god);
                setOpen(godService.app?.openToWorld ?? false);
            },
            userSetChanged: event=>{
                for(let i=0;i<hackNonClosureUsers.length;i++){
                    if(hackNonClosureUsers[i].id==event.entity.id){
                        if(event.type=="remove"){
                            selectedUsers.delete(event.entity.id);
                            hackNonClosureUsers.splice(i, 1);
                        } else
                            hackNonClosureUsers[i]=event.entity;
                        selectionChanged(); //Just a hack to make React re-render
                        break;
                    }
                }
                // setUserTableDirty(true);
            },
            orgSetChanged: ()=>{
                setOrgCountDirty(true);
            }
        });
		const heartBeat=lifeCycle.onHeartBeat(() => {
			if (god
                && orgCountDirty
                && syncService.getConnectionStatus().status == "success") {
				refreshOrgCount();
			}
		});
        return ()=>{
            godListener();
            heartBeat();
        };
	}, []);

	useEffect(() => {
		let tableTitle;
        if(filteredUserCount==0)
            tableTitle="No Users Matched ("+NUMBER_FORMAT.format(totalUserCount)+" Total)";
        else {
            tableTitle = "Displaying ";
            if(userPageSize>=filteredUserCount)
                tableTitle+="All of ";
            else {
                const min=userPageNumber*userPageSize+1;
                const max=Math.min(min+userPageSize-1, filteredUserCount);
                if(min==max)
                    tableTitle+="#"+NUMBER_FORMAT.format(min);
                else
                    tableTitle+="#"+NUMBER_FORMAT.format(min)+" - "+NUMBER_FORMAT.format(max);
                tableTitle+=" of ";
            }
            tableTitle+=NUMBER_FORMAT.format(filteredUserCount);
            if(userQuery?.length) {
                tableTitle+=" Matched of "+NUMBER_FORMAT.format(totalUserCount)+" Total";
            }

            tableTitle+=" User";
            if(filteredUserCount!=1)
                tableTitle+="s";
        }
		setUserTableTitle(tableTitle);
	}, [totalUserCount, filteredUserCount, userPageNumber, userPageSize, userQuery]);

    const setOpenToWorld=(newOpen: boolean)=>{
        if(newOpen==open)
            return;
        setNewOpenState(newOpen);
        setConfirmOpen(true);
    }

    const changeOpenState=(open: boolean)=>{
        api.put("/api/god/open-to-world/"+open);
        setConfirmOpen(false);
    }

    const selectFullPage=()=>{
        for(const user of users)
            selectedUsers.add(user.id);
        selectionChanged();
    }

    const deselectFullPage=()=>{
        for(const user of users)
            selectedUsers.delete(user.id);
        selectionChanged();
    }

    const clearSelection=()=>{
        selectedUsers.clear();
        selectionChanged();
    }

    const tryNewQuery=(newQuery: string | null)=>{
        if(newQuery && !newQuery.length)
            newQuery=null;
        if(newQuery == userQuery)
            return;

        // const prevDirty=userTableDirty;
        setUserQuery(newQuery);
        if(!newQuery) return; // Error is not possible

        setPrevTableState({
            query: userQuery,
            pageNumber: userPageNumber,
            //  dirty: prevDirty,
        });
        setUserQuery(newQuery);
        setUserPageNumber(0);
        // setUserTableDirty(true);
    }

    useEffect(()=>{
        if(queryError)
            setTimeout(()=>setQueryError(null), 10000);
    }, [queryError]);

    const applicableUsersText=selectedUsers.size
        ? (selectedUsers.size+" selected User"+(selectedUsers.size==1 ? "" : "s"))
        : (filteredUserCount+" matching User"+(filteredUserCount==1 ? "" : "s"));

    const userModifications=[
        "Block", "Unblock",
        "Grant Creator", "Revoke Creator",
        "Grant God", "Revoke God"];
    const userModDescrips=[
        "Blocked users cannot access the application at all",
        "Creators can create their own organizations",
        "God users can access this view to manage users and the application in general"
    ]

    const modifyUsers=(mod: number)=>{
        const body: BulkUserMod={}
        switch(Math.floor(mod/2)){
            case 0:
                body.block=mod%2==0;
                break;
            case 1:
                body.globalAdmin=mod%2==0;
                break;
            case 2:
                body.god=mod%2==0;
                break;
            default:
                console.error("Unrecognized user mod: ", mod);
                return;
        }
        setUserActionMod(body);
        setConfirmUserAction(true);
    }

    const doModifyUsers=(mod: BulkUserMod)=>{
        godService.modifyUsers(userQuery, selectedUsers, mod.block, mod.globalAdmin, mod.god);
        setUserActionMod(null);
        setConfirmUserAction(false);
    }

    const deleteUsers=()=>{
        setUserActionMod(null);
        setConfirmUserAction(true);
    }

    const doDeleteUsers=()=>{
        godService.deleteUsers(userQuery, selectedUsers);
        setConfirmUserAction(false);
    };

	return (
        <Box sx={{
            width: "100%",
            height: "100%",
            display: "flex",
            flexDirection: "column",
            justifyContent: "stretch",
            alignContent: "stretch"}}>
            <Dialog open={confirmOpen}>
                <DialogTitle>Confirm Open Change</DialogTitle>
                <DialogContent sx={{width: "100%"}}>
                    {newOpenState
                        ? <span>Open the application to the world?<br />
                        This will allow new users to create their own organizations.<br />
                        This will <b>NOT</b> affect existing users.</span>
                        : <span>Close the application off from the world?<br />
                        This will cause new users to the application to be unable to create their own organizations.<br />
                        New users may still be granted access to organizations by a manager.<br />
                        This will <b>NOT</b> affect existing users.
                        </span>
                    }
                    <Box sx={{
                        display: "flex",
                        flexDirection: "row",
                        width: "100%",
                        justifyContent: "space-evenly"
                        }}>
                        <Button onClick={()=>changeOpenState(newOpenState)}>OK</Button>
                        <Button onClick={()=>setConfirmOpen(false)}>Cancel</Button>
                    </Box>
                </DialogContent>
            </Dialog>
            <Dialog open={!!confirmUserAction}>
                <DialogTitle>{
                    (userActionMod ? "Modify " : "Delete ")
                    +applicableUsersText+"?"
                }</DialogTitle>
                <DialogContent>
                    <Box sx={{display: "flex", flexDirection: "row", justifyContent: "space-evenly"}}>
                        <Button onClick={()=>{
                            if(userActionMod)
                                doModifyUsers(userActionMod);
                            else
                                doDeleteUsers();
                        }}>OK</Button>
                        <Button onClick={()=>{
                            setUserActionMod(null);
                            setConfirmUserAction(false);
                        }}>Cancel</Button>
                    </Box>
                </DialogContent>
            </Dialog>

            <Box sx={{display: "flex", flexDirection: "row", justifyContent: "start"}}>
                <Grid container>
                    <Grid size={10}>Organizations:</Grid>
                    <Grid size={2}>
                        <Box sx={{textAlign: "center"}}>
                            {orgCount}
                        </Box>
                    </Grid>

                    <Grid size={10}>Open To World:</Grid>
                    <Grid size={2}>
                        <Box sx={{textAlign: "center"}}>
                            <Tooltip title="Whether this application is open to the world, such that new users are able to create their own organizations">
                                <Checkbox
                                    sx={{p:0}}
                                    checked={open}
                                    onChange={e=>setOpenToWorld(!open)} />
                            </Tooltip>
                        </Box>
                    </Grid>
                </Grid>
            </Box>

            <Divider textAlign="center">
                <Typography variant="h5">Users</Typography>
            </Divider>

            <span style={{
                display: queryError ? undefined : "none",
                 color:"red",
                  fontSize: "small"}}>
                {queryError}
            </span>

            <Box sx={{
                display: "flex",
                flexDirection: "row",
                width: "100%",
                justifyContent: "stretch",
                alignItems: "center"}}>
                <Tooltip
                    title={
                        <Typography>
                            Enter text or dates to filter the table rows.
                            <br />
                            Use <b>XXX-XXX</b> to enter a range of dates.
                            <br />
                            Use <b>abc*efg</b> to match any text with "efg" occurring after "abc".
                            <br />
                            Use <b>sort:column1,column2</b> to sort the table rows.
                            <br />
                            All filters are AND-ed.
                        </Typography>
                    }>
                    <ValidatedTextField
                        variant="filled"
                        sx={{ width: "100%" }}
                        label="Filter users (e.g. email:*@gmail.com)"
                        value={userQuery ?? ""}
                        onChange={tryNewQuery}
                        parser={s=>s}
                        autoFocus
                    />
                </Tooltip>
                <Tooltip title="Clear the filter to display all users in the table">
                    <span>
                        <IconButton onClick={()=>setUserQuery(null)}>
                            <ClearIcon />
                        </IconButton>
                    </span>
                </Tooltip>
            </Box>

            <Box sx={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <Box sx={{display: "flex", flexDirection: "row", alignItems: "center", p: 1}}>
                    <b>{selectedUsers.size} User{selectedUsers.size==1 ? "" : "s"} Selected</b>
                    &nbsp;
                    <Tooltip title="Select all Users displayed in current results">
                        <span>
                            <Button
                                disabled={users.length==0}
                                onClick={()=>selectFullPage()}>Select Page</Button>
                        </span>
                    </Tooltip>
                    <Tooltip title="De-select all Users displayed in current results">
                        <span>
                            <Button
                                disabled={users.length==0 || selectedUsers.size==0}
                                onClick={()=>deselectFullPage()}>Deselect Page</Button>
                        </span>
                    </Tooltip>
                    <Tooltip title="Clear the entire selection">
                        <span>
                            <Button
                                disabled={users.length==0 || selectedUsers.size==0}
                                onClick={()=>clearSelection()}>Clear Selection</Button>
                        </span>
                    </Tooltip>
                </Box>

                <Box sx={{display: "flex", flexDirection: "row", alignItems: "center"}}>
                    <Tooltip title={"Change the permissions for the "+applicableUsersText}>
                        <Select value={-1} onChange={e=>{
                            e.preventDefault();
                            modifyUsers(e.target.value);
                        }}>
                            <MenuItem value={-1}>Modify Users</MenuItem>
                            {userModifications.map((mod, index)=>{
                                return <MenuItem key={index} value={index}>
                                    <Typography>{mod}</Typography>
                                </MenuItem>
                            })}
                        </Select>
                    </Tooltip>
                    <Tooltip title={"Delete the "+applicableUsersText}>
                        <IconButton onClick={()=>deleteUsers()}>
                            <DeleteIcon />
                        </IconButton>
                    </Tooltip>
                </Box>
            </Box>

            <Box sx={{
                display: "grid",
                // 3 columns: Left (flexible), Center (auto size), Right (flexible)
                gridTemplateColumns: '1fr auto 1fr',
                alignItems: "center"}}>
                <Typography variant="h5">&nbsp;{userTableTitle}</Typography>
                <Box sx={{
                    display: "flex",
                    flexDirection: "row",
                    alignItems: "center",
                    gap: 0}}>
                    <IconButton
                        disabled={userPageNumber==0}
                        onClick={()=>setUserPageNumber(0)}>
                        <FirstPageIcon />
                    </IconButton>
                    <IconButton
                        disabled={userPageNumber==0}
                        onClick={()=>setUserPageNumber(p=>p-1)}>
                        <NavigateBeforeIcon />
                    </IconButton>
                    <IconButton
                        disabled={userPageNumber+1==maxPageNumber}
                        onClick={()=>setUserPageNumber(p=>p+1)}>
                        <NavigateNextIcon />
                    </IconButton>
                    <IconButton
                        disabled={userPageNumber+1==maxPageNumber}
                        onClick={()=>setUserPageNumber(maxPageNumber-1)}>
                        <LastPageIcon />
                    </IconButton>
                    Page Size:&nbsp;
                    <ValidatedTextField
                        size="small"
                        sx={{width: "5em"}}
                        value={userPageSize}
                        onChange={setUserPageSize}
                        parser={parseInt}
                        validator={size=>{
                            if(size<=0)
                                return "Page size must be positive";
                            else if(size>1000)
                                return "Page size cannot exceed 1000";
                            else
                                return null;
                        }} />
                </Box>
                <Box sx={{
                    display: "flex",
                    flexDirection: "row",
                    justifyContent: "flex-end",
                    alignItems: "flex-end",
                    gap:1 }}>
                    <Typography sx={{display: userTableRefreshTime ? undefined : "none"}}>
                        Updated {DATE_FORMAT.format(userTableRefreshTime!)}
                    </Typography>
                    <Tooltip title="Click to Refresh the Users table">
                        <span>
                            <IconButton
                                disabled={userTableRefreshing}
                                onClick={() => refreshUserTable(true)}>
                                <RefreshIcon sx={{ animation: userTableRefreshing ? spinAnimation : undefined }} />
                            </IconButton>
                        </span>
                    </Tooltip>
                </Box>
            </Box>

            <TableContainer
                component={Paper}
                sx={{ width: "100%" }}>
                <Table
                    size="small"
                    sx={{ width: "100%" }}>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
                                <Tooltip title="Whether the user is selected for individual modification">
                                    <Typography variant="h6">Selected</Typography>
                                </Tooltip>
                            </TableCell>
                            {debug ? <TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
                                <Tooltip title="The user's internal database ID">
                                    <Typography variant="h6">ID</Typography>
                                </Tooltip>
                            </TableCell>
                            : null}
                            <TableCell>
                                <Tooltip title="The user's email address by which they may log in to this app">
                                    <Typography variant="h6">Email</Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell>
                                <Tooltip title="The user's default name when they are added to an organization">
                                    <Typography variant="h6">Preferred Name</Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
                                <Tooltip title="Whether the user has Creator permission.  Creators can create their own organizations.">
                                    <Typography variant="h6">Creator</Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
                                <Tooltip title="Whether the user has God permission.  God users have access to this interface for managing users and the application in general.">
                                    <Typography variant="h6">God</Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
                                <Tooltip title="Whether the user has been blocked form accessing the application">
                                    <Typography variant="h6">Blocked</Typography>
                                </Tooltip>
                            </TableCell>
                            <TableCell>
                                <Tooltip title="The time of the user's most recent login to the app">
                                    <Typography variant="h6">Last Active</Typography>
                                </Tooltip>
                            </TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {users.map(user=>
                            <TableRow key={user.id}>
                                <TableCell>
                                    <Checkbox
                                        checked={selectedUsers.has(user.id)}
                                        onChange={e=>{
                                            if(e.target.checked)
                                                selectedUsers.add(user.id);
                                            else
                                                selectedUsers.delete(user.id);
                                            selectionChanged();
                                        }} />
                                </TableCell>
                                {debug ? <TableCell>{user.id}</TableCell> : null}
                                <TableCell>{user.email}</TableCell>
                                <TableCell>{user.name}</TableCell>
                                <TableCell>
                                    <Checkbox
										sx={{paddingTop: 0, paddingBottom: 0, alignItems: "center"}}
                                        checked={user.globalAdmin} />
                                </TableCell>
                                <TableCell>
                                    <Checkbox
										sx={{paddingTop: 0, paddingBottom: 0}}
                                        checked={user.god} />
                                </TableCell>
                                <TableCell>
                                    <Checkbox
										sx={{paddingTop: 0, paddingBottom: 0}}
                                        checked={user.blocked} />
                                </TableCell>
                                <TableCell>{
                                    user.lastActive
                                    ? DATE_FORMAT.format(user.lastActive)
                                    : "Never"
                                }</TableCell>
                            </TableRow>)
                        }
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
	);
};

export default GodView;
