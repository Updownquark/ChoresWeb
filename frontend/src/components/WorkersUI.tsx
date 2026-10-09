import React, { useEffect, useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { debug, api, historyService, jobService, memberService, resourcesService } from "../services/services";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Dialog, DialogTitle, IconButton, MenuItem, Paper, Select, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, Tooltip, Typography } from "@mui/material";
import { EditableTableCell } from "./util/EditableTableCell";
import ValidatedTextField from "./util/ValidatedTextField";
import Job from "../values/Job";
import Membership from "../values/Membership";
import PointHistoryView from "./PointHistoryView";
import PointResource from "../values/PointResource";

interface ApiResourceUsage{
	resourceId: number;
	points: number;
	notes?: string;
}

const subscribeToWorkers=memberService.onChange.bind(memberService);
const getWorkersSnapshot=memberService.getWorkers.bind(memberService);

const subscribeToJobs = jobService.onChange.bind(jobService);
const getJobsSnapshot = jobService.getAll.bind(jobService); //Freelance work can be any job

const subscribeToResources = resourcesService.onChange.bind(resourcesService);
const getResourcesSnapshot = resourcesService.getAll.bind(resourcesService);

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const getInitialEditWorker=(): Membership | null => {
		const selectedWorkerStr=sessionStorage.getItem("selectedWorker");
		if(selectedWorkerStr && memberService.getAll().length){
			return memberService.getById(parseInt(selectedWorkerStr));
		}
		return null;
	}

	const workers=useSyncExternalStore(subscribeToWorkers, getWorkersSnapshot);
	const [editWorker, _setEditWorker] = useState<Membership | null>(getInitialEditWorker());
	const jobs = useSyncExternalStore(subscribeToJobs, getJobsSnapshot);
	const resources = useSyncExternalStore(subscribeToResources, getResourcesSnapshot);

	const [enteringNewWorkerEmail, setEnteringNewWorkerEmail] = useState(false);
	const [newWorkerEmail, setNewWorkerEmail] = useState("");

	const [confirmingDelete, setConfirmingDelete] = useState(false);

	const [freeLancePoints, setFreeLancePoints] = useState(1);
	const [freeLanceJob, setFreeLanceJob]=useState<Job | null>(null);
	const [freeLanceNotes, setFreeLanceNotes]=useState<string | null>(null);

	const [selectedTab, setSelectedTab] = useState(0);
	const [pointUsage]=useState(new Map<number, Map<number, number>>());
	const [pointUsageRefresh, setPointUsageRefresh] = useState(0);

	const [showHistory, setShowHistory] = useState(false);

	useEffect(()=>{
		if(!visible){ //Hide history when the Workers tab is de-selected
			setSelectedTab(0);
			setShowHistory(false);
		}
	}, [visible]);

	useEffect(()=>{
		const worker=getInitialEditWorker();
		if(worker?.member?.id!=editWorker?.member?.id)
			_setEditWorker(worker);
	}, [workers]);

	const setEditWorker=(worker: Membership | null)=>{
		_setEditWorker(worker);
		if(worker)
			sessionStorage.setItem("selectedWorker", worker.id.toString());
		else
			sessionStorage.removeItem("selectedWorker");
	}

	const isValidEmail=(email: string)=>{
		if(!email)
			return "Email address is required";
		const at=email.indexOf("@");
		if(at<=0)
			return "Not an email address";
		const dot=email.lastIndexOf(".");
		if(dot<at || dot==email.length-1)
			return "Not an email address";
		for(const worker of workers){
			if(worker.member!.email.toLowerCase()==email.toLowerCase())
				return `Worker '${worker.name} has this email address`;
		}
		return null;
	}

	const addWorkerEmailValid=isValidEmail(newWorkerEmail);

	const addWorker=()=>{
		setNewWorkerEmail("");
		setEnteringNewWorkerEmail(true);
	};

	const doAddWorker=(email: string)=>{
		setEnteringNewWorkerEmail(false);
		api.post<Membership>("/api/members/add", {
			orgId: org.organization!.id,
			userEmail: email,
		}).then(response=>{
			if(response.data)
				setEditWorker(response.data);
		});
	};
	const deleteWorker=()=>{
		setConfirmingDelete(true);
	}

	const doDeleteWorker=()=>{
		setConfirmingDelete(false);
		api.delete("/api/members", {
			data: {
				orgId: org.organization!.id,
				userId: editWorker!.member!.id,
			}
		}).then(()=>setEditWorker(null));
	};
	const renameWorker=(worker: Membership, newName: string)=>{
		api.post("/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			name: newName,
		});
	};
	const setWorkerLevel=(worker: Membership, newLevel: number)=>{
		api.post("/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			level: newLevel,
		});
	};
	const parseLabels=(labelStr: string): readonly string[] | null => {
		if(!labelStr || labelStr.length==0)
			return null;
		const labels=labelStr.split(",");
		for(let i=0;i<labels.length;i++)
			labels[i]=labels[i].trim();
		return labels;
	}
	const setWorkerLabels=(worker: Membership, newLabels: readonly string[] | null)=>{
		api.post("/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			labels: newLabels,
		});
	};
	const reportWork=()=>{
		api.post("/api/assignments/report", {
			orgId: org.organization!.id,
			userId: editWorker!.member!.id,
			jobId: freeLanceJob!.id,
			completed: freeLancePoints,
			notes: freeLanceNotes,
		});
	};
	const usePoints=(rsrc: PointResource, points?: number)=>{
		let userUsage=pointUsage.get(editWorker!.member!.id);
		if(!userUsage){
			if(!points)
				return;
			userUsage=new Map();
			pointUsage.set(editWorker!.member!.id, userUsage);
		}
		if(!points){
			userUsage.delete(rsrc.id);
			return;
		}
		userUsage.set(rsrc.id, points);
		setPointUsageRefresh(pointUsageRefresh+1);
	};
	const pointUsageNegative=()=>{
		if(!editWorker)
			return false;
		let points=editWorker.points;
		if(points<0)
			return true;
		let userUsage=pointUsage.get(editWorker!.member!.id);
		if(!userUsage?.size)
			return false;
		for(const usage of userUsage.values()){
			points-=usage;
			if(points<0)
				return true;
		}
		return false;
	};
	const getPointUsageTooltip=()=>{
		if(!editWorker)
			return "";

		let points=editWorker.points;
		let userUsage=pointUsage.get(editWorker!.member!.id);
		if(!userUsage?.size)
			return "Enter point or amount values for a resource to redeem points for";
		for(const usage of userUsage.values())
			points-=usage;
		return "Redeem the selected points for the selected resources.\n"
			+ editWorker.name
			+ " will have "
			+ points
			+ " point"+(points==1 ? "" : "s")
			+ " afterward.";
	}
	const commitPointUsage=()=>{
		if(!editWorker)
			return;
		let userUsage=pointUsage.get(editWorker!.member!.id);
		if(!userUsage?.size)
			return;
		const usage:ApiResourceUsage[]=[];
		for(const [rsrcId, points] of userUsage.entries()){
			usage.push({
				resourceId: rsrcId,
				points: points,
			});
		}

		api.post("/api/resources/redeem", {
			orgId: org.organization!.id,
			userId: editWorker!.member!.id,
			usage: usage,
		});
	};
	
	return <Box sx={{
		display: visible ? "flex" : "none",
		flexDirection: "column",
		width: "100%",
		height: "100%",}}
			className="section">
		<Dialog open={enteringNewWorkerEmail} onClose={()=>setEnteringNewWorkerEmail(false)}>
			<DialogTitle>New Worker Email</DialogTitle>
			<Box sx={{
				display: "flex",
				flexDirection: "column",
				alignItems: "start",
				marginLeft: 2,
				marginRight: 2,
				marginBottom: 5,
				}}>
				<Typography>Enter the email address for the new worker</Typography>
				<TextField
					sx={{width: "100%"}}
					value={newWorkerEmail}
					onChange={e=>setNewWorkerEmail(e.target.value)}
					onKeyDown={e=>{
						if(e.key=="Enter" && !addWorkerEmailValid){
							e.preventDefault(); //Prevents side-effects and allows the dialog to close
							doAddWorker(newWorkerEmail);
						}
					}}
					autoFocus
					label="Enter worker email address" />
				<Box sx={{width: "100%", display: "flex", flexDirection: "row", justifyContent: "center"}}>
					<Tooltip title={addWorkerEmailValid ? addWorkerEmailValid : "Create a new worker"}>
						<span>
							<Button
								variant="contained"
								onClick={e=>doAddWorker(newWorkerEmail)}
								disabled={!!addWorkerEmailValid}>
								Add Worker
							</Button>
						</span>
					</Tooltip>
				</Box>
			</Box>
		</Dialog>
		<Dialog open={confirmingDelete} onClose={()=>setConfirmingDelete(false)}>
			<DialogTitle>Delete Worker?</DialogTitle>
			<Box sx={{
				display: "flex",
				flexDirection: "column",
				alignItems: "start",
				marginLeft: 2,
				marginRight: 2,
				marginBottom: 5,
				}}>
				<Typography>
					Are you sure you want to delete worker '{editWorker?.name}'?<br />
					This cannot be undone.
				</Typography>
				<Box sx={{width: "100%", display: "flex", flexDirection: "row", justifyContent: "center"}}>
					<Button
						variant="contained"
						onClick={e=>doDeleteWorker()}>
						Delete Worker
					</Button>
				</Box>
			</Box>
		</Dialog>
		{org.manager ?
			/* Add/Remove Workers */
			<Box sx={{display: "flex", flexDirection: "row"}}>
				&nbsp;&nbsp;
				<Tooltip title="Add a new worker">
					<IconButton onClick={addWorker}>
						<AddIcon />
					</IconButton>
				</Tooltip>
				&nbsp;&nbsp;
				<Tooltip title={editWorker==null ? "Select a worker to delete" : "Delete the selected worker"}>
					<span>
						<IconButton disabled={editWorker==null} onClick={deleteWorker}>
							<DeleteIcon />
						</IconButton>
					</span>
				</Tooltip>
			</Box>
			: null
		}

		{/* Worker Table */}
		<TableContainer component={Paper}>
			<Table size="small">
				<TableHead>
					<TableRow>
						{debug ? <TableCell><b>ID</b></TableCell> : null}
						<TableCell><b>Name</b></TableCell>
						<TableCell><b>Points</b></TableCell>
						<TableCell><b>Level</b></TableCell>
						{/* Labels were used by a previous version--they're not useful anymore
						<TableCell><b>Labels</b></TableCell>
						*/}
					</TableRow>
				</TableHead>
				<TableBody>
					{workers.map(worker=>{
						const editing=worker.member?.id==editWorker?.member?.id;
						const nameEditable=org.manager || worker.member!.id==org.member?.id;
						return <TableRow
							key={worker.member!.id}
							className={editing ? "selected" : ""}
							onClick={e=>{
								if(e.ctrlKey && editWorker!.member!.id==worker.member!.id)
									setEditWorker(null);
								else
									setEditWorker(worker);
							}}>
							{debug ? <TableCell>{worker.member?.id+" ("+worker.id+")"}</TableCell> : null}
							{nameEditable ? <EditableTableCell
								value={worker.name}
								onSave={newName=>renameWorker(worker, newName)}
								parser={s=>s}
								validator={newName=>{
									if(!newName || newName.length==0)
										return "Name cannot be empty";
									else if(newName.length>100)
										return "Name cannot exceed 100 characters";
									for(const w of workers){
										if(w.member!.id!=worker.member!.id && w.name==newName)
											return "Another worker named '"+newName+"' exists";
									}
									return null;
								}} />
								: <TableCell>{worker.name}</TableCell>
							}
							<TableCell>{worker.points}</TableCell>
							{org.manager ?
								<EditableTableCell
									value={worker.level}
									onSave={newLevel=>setWorkerLevel(worker, newLevel)}
									parser={parseInt} />
								: <TableCell>{worker.level}</TableCell>
							}
							{/* Labels were used by a previous version--they're not useful anymore
							org.manager ?
								<EditableTableCell
									value={worker.labels}
									onSave={newLabels=>setWorkerLabels(worker, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell>worker.labels ? worker.labels.join(",") : ""</TableCell>
							*/}
						</TableRow>;
					})}
				</TableBody>
			</Table>
		</TableContainer>

		<Box sx={{display: editWorker ? "flex" : "none", flexDirection: "column", width: "100%"}}>
			{org.manager ?
				/* Freelance Work Reporting */
				<Box sx={{display: visible ? "flex" : "none", flexDirection: "row", alignItems: "center"}}>
					FreeLance:&nbsp;
					<ValidatedTextField
						value={freeLancePoints}
						onChange={setFreeLancePoints}
						parser={parseInt}
						validator={v=>{
							if(v<=0)
								return "Freelance points must be positive";
							return null;
						}} />
					<Box sx={{display: freeLanceJob ? "none" : "flex", flexDirection: "row"}}>
						&nbsp;/&nbsp; {freeLanceJob?.value}
					</Box>
					&nbsp;for&nbsp;
					<Select
						value={freeLanceJob?.id ?? ""}
						onChange={e=>setFreeLanceJob(jobService.getById(e.target.value))}>
						{jobs.map(job=><MenuItem
							key={job.id}
							value={job.id}
							sx={{color: job.active ? "" : "darkgray"}}>
							{job.name}
						</MenuItem>)}
					</Select>
					<ValidatedTextField
						label="Optional notes"
						value={freeLanceNotes ?? ""}
						onChange={setFreeLanceNotes}
						parser={s=>s}
						validator={notes=>{
							if(notes && notes.length>100)
								return "Notes cannot exceed 100 characters";
							return null;
						}}
						 />
					<Button disabled={!freeLanceJob} onClick={reportWork}>Report Work</Button>
				</Box>
				: null
			}

			{org.manager ?
				<>
					<Tabs value={selectedTab} onChange={(e, newValue)=>setSelectedTab(newValue)}>
						<Tab label="Point Usage" />
						<Tab label="History" />
					</Tabs>
					{/* Point Redemption for Resources */}
					<TableContainer component={Paper} sx={{display: selectedTab==0 ? null : "none"}}>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell><b>Resource</b></TableCell>
									<TableCell><b>Redeem Points</b></TableCell>
									<TableCell><b>For Amount</b></TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{resources.map(rsrc=>{
									const usagePoints=editWorker ? pointUsage.get(editWorker!.member!.id)?.get(rsrc.id) : undefined;
									const usageAmount=usagePoints ? usagePoints*rsrc.rate : undefined;
									return <TableRow key={rsrc.id}>
										<TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>
											{rsrc.name}
										</TableCell>
										<EditableTableCell
											value={usagePoints}
											onSave={newValue=>usePoints(rsrc, newValue)}
											renderer={v=>v ? v.toString() : ""}
											parser={s=>{
												if(!s?.length)
													return undefined;
												let value;
												try{
													value=parseFloat(s);
												} catch {
													throw "Point usage must be a number";
												}
												if(value % 1.0 != 0.0)
													throw "Point usage must be an integer"
												return value;
											}}
											/>
										<EditableTableCell
											value={usageAmount}
											onSave={newValue=>usePoints(rsrc, newValue ? newValue/rsrc.rate : undefined)}
											renderer={v=>v ? v.toString() : ""}
											parser={s=>{
												if(!s?.length)
													return undefined;
												let value;
												try{
													value=parseFloat(s);
												} catch {
													throw "Point usage must be a number";
												}
												return value;
											}}
											/>
									</TableRow>
								})}
							</TableBody>
						</Table>
					</TableContainer>
					<Box sx={{
						display: selectedTab==0 ? "flex" : "none",
						flexDirection: "column",
						width: "100%",
						justifyItems: "center"}}>
						<Tooltip title={getPointUsageTooltip()}>
							<span>
								<Button
									sx={{color: pointUsageNegative() ? "red" : "black"}}
									disabled={!pointUsage.size}
									onClick={commitPointUsage}>
									Redeem Points
								</Button>
							</span>
						</Tooltip>
					</Box>
					<PointHistoryView
						org={org}
						userId={editWorker?.member?.id}
						visible={visible && selectedTab==1 && !!editWorker} />
				</>
				:
				<Accordion
					sx={{dislay: editWorker ? "" : "none"}}
					expanded={showHistory}
					onChange={(e: React.SyntheticEvent, expanded: boolean)=>setShowHistory(expanded)}>
					<AccordionSummary expandIcon={<ExpandMoreIcon />}>
						<Typography component="span">Worker History</Typography>
					</AccordionSummary>
					<AccordionDetails>
						 <PointHistoryView
						 	org={org}
							userId={editWorker?.member?.id}
							visible={visible && showHistory && !!editWorker} />
					</AccordionDetails>
				</Accordion>
			}
		</Box>
	</Box>
};

export default JobsUI;
