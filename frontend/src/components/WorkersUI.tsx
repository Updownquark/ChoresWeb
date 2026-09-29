import React, { useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { historyService, jobService, memberService, resourcesService } from "../services/services";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { Box, Button, IconButton, MenuItem, Paper, Select, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, Tooltip } from "@mui/material";
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

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const workers=useSyncExternalStore(
		listener=>memberService.onChange(listener),
		()=>memberService.getWorkers());
	const [editWorker, setEditWorker] = useState<Membership | null>(null);
	const jobs = useSyncExternalStore(
		listener=>jobService.onChange(listener),
		()=>jobService.getAll()); // Freelance work can use inactive jobs
	const resources = useSyncExternalStore(
		listener=>resourcesService.onChange(listener),
		()=>resourcesService.getAll());

	const [freeLancePoints, setFreeLancePoints] = useState(1);
	const [freeLanceJob, setFreeLanceJob]=useState<Job | null>(null);
	const [freeLanceNotes, setFreeLanceNotes]=useState<string | null>(null);

	const [selectedTab, setSelectedTab] = useState(0);
	const [pointUsage]=useState(new Map<number, Map<number, number>>());
	const [pointUsageRefresh, setPointUsageRefresh] = useState(0);

	const addWorker=()=>{
		const email=getAddWorkerEmail();
		memberService.modify("POST", "/api/members/add", {
			ordId: org.organization!.id,
			userEmail: email,
		}).then(()=>{
			for(const worker of workers){
				if(worker.member!.email==email){
					setEditWorker(worker);
					break;
				}
			}
		});
	};
	const getAddWorkerEmail=(): string=>{
		return ""; //TODO
	}
	const deleteWorker=()=>{
		//TODO Confirm
		memberService.modify("DELETE", "/api/members", {
			orgId: org.organization!.id,
			userId: editWorker!.member!.id,
		}).then(()=>setEditWorker(null));
	};
	const renameWorker=(worker: Membership, newName: string)=>{
		memberService.modify("POST", "/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			name: newName,
		})
	};
	const setWorkerLevel=(worker: Membership, newLevel: number)=>{
		memberService.modify("POST", "/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			level: newLevel,
		})
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
		memberService.modify("POST", "/api/members/modify", {
			orgId: org.organization!.id,
			userId: worker.member!.id,
			labels: newLabels,
		})
	};
	const reportWork=()=>{
		memberService.modify("POST", "/api/assignments/report", {
			orgId: org.organization!.id,
			userId: editWorker!.member!.id,
			jobId: freeLanceJob!.id,
			completed: freeLancePoints,
		});
		historyService.check();
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

		memberService.modify("POST", "/api/resources/redeem", {
			orgId: org.organization!.id,
			userId: editWorker!.member!.id,
			usage: usage,
		})
		historyService.check();
	};
	
	return <Box sx={{display: "flex", flexDirection: "column", width: "100%", height: "100%"}}>
		{org.manager ?
			/* Add/Remove Workers */
			<Box sx={{display: visible ? "flex" : "none", flexDirection: "row"}}>
				<Tooltip title="Add a new worker">
					<IconButton onClick={addWorker}>
						<AddIcon />
					</IconButton>
				</Tooltip>
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
		<TableContainer component={Paper} sx={{display : visible ? "flex" : "none"}}>
			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell><b>Name</b></TableCell>
						<TableCell><b>Points</b></TableCell>
						<TableCell><b>Level</b></TableCell>
						<TableCell><b>Labels</b></TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{workers.map(worker=>{
						const editing=worker.member?.id==editWorker?.member?.id;
						const nameEditable=worker.manager || worker.member!.id==org.member?.id;
						const cellStyle= {backgroundColor: editing ? "lightblue" : ""};
						return <TableRow key={worker.member!.id} onClick={e=>{
							setEditWorker(worker);
						}}>
							{nameEditable ? <EditableTableCell
								sx={cellStyle}
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
								: <TableCell sx={cellStyle}>{worker.name}</TableCell>
							}
							<TableCell sx={cellStyle}>{worker.points}</TableCell>
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={worker.level}
									onSave={newLevel=>setWorkerLevel(worker, newLevel)}
									parser={parseInt} />
								: <TableCell sx={cellStyle}>{worker.level}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={worker.labels}
									onSave={newLabels=>setWorkerLabels(worker, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={cellStyle}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							}
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
							sx={{color: job.active ? "black" : "darkgray"}}>
							{job.name}
						</MenuItem>)}
					</Select>
					<TextField value={freeLanceNotes ?? ""} onChange={e=>setFreeLanceNotes(e.target.value)} />
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
										<TableCell>{rsrc.name}</TableCell>
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
					<Box sx={{display: selectedTab==0 ? "flex" : "none", flexDirection: "column", justifyItems: "center"}}>
						<Tooltip title={getPointUsageTooltip()}>
							<span>
								<Button
									sx={{color: pointUsageNegative() ? "red" : "black"}}
									disabled={!pointUsage.size}
									onClick={commitPointUsage}>Redeem Points</Button>
							</span>
						</Tooltip>
					</Box>
					<PointHistoryView org={org} userId={editWorker?.member?.id} visible={selectedTab==1 && Boolean(editWorker)} />
				</>
				: null /*<PointHistoryView org={org} userId={editWorker?.member?.id} visible={Boolean(editWorker)} />*/
			}
		</Box>
	</Box>
};

export default JobsUI;
