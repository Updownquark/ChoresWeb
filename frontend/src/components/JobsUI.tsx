import React, { useEffect, useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { debug, jobService } from "../services/services";
import Job from "../values/Job";
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Checkbox, Dialog, DialogTitle, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { EditableTableCell } from "./util/EditableTableCell";
import PointHistoryView from "./PointHistoryView";

const subscribeToJobs = (listener: ()=>void)=>jobService.onChange(e=>{
	listener();
});
const getJobsSnapshot = jobService.getAll.bind(jobService); // Freelance work can use inactive jobs

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const getInitialEditJob=(): Job | null => {
		const selectedJobStr=sessionStorage.getItem("selectedJob");
		if(selectedJobStr){
			return jobService.getById(parseInt(selectedJobStr));
		}
		return null;
	}

	const jobs = useSyncExternalStore(subscribeToJobs, getJobsSnapshot);
	const [editJob, _setEditJob] = useState<Job | null>(getInitialEditJob());

	const [confirmingDelete, setConfirmingDelete] = useState(false);

	const [showHistory, setShowHistory] = useState(false);

	useEffect(()=>{
		if(!visible) //Hide history when the Jobs tab is de-selected
			setShowHistory(false);
	}, [visible]);

	useEffect(()=>{
		const job=getInitialEditJob();
		if(job?.id!=editJob?.id)
			_setEditJob(job);
	}, [jobs]);

	const setEditJob=(job: Job | null)=>{
		_setEditJob(job);
		if(job)
			sessionStorage.setItem("selectedJob", job.id.toString());
		else
			sessionStorage.removeItem("selectedJob");
	}
	
	const addJob=()=>{
		api.put<Job>("/api/jobs", {
			orgId: org.organization!.id,
		}).then(event=>{
			if(event.data)
				setEditJob(event.data);
		});
	};

	const deleteJob=()=>{
		setConfirmingDelete(true);
	}

	const doDeleteJob=()=>{
		setConfirmingDelete(false);
		api.delete("/api/jobs/"+editJob!.id).then(()=>setEditJob(null));
	};
	const renameJob=(job: Job, newName: string)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			name: newName,
		});
	};
	const setJobPoints=(job: Job, newPoints: number)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			value: newPoints,
		});
	};
	const myDateFormat=new Intl.DateTimeFormat(Intl.NumberFormat().resolvedOptions().locale, {
		dateStyle: "short",
		timeStyle: "medium",
	});
	const setJobMinLevel=(job: Job, minLevel: number)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			minLevel: minLevel,
		});
	};
	const setJobMaxLevel=(job: Job, maxLevel: number)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			maxLevel: maxLevel,
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
	const setJobInclusionLabels=(job: Job, labels: readonly string[] | null)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			inclusionLabels: labels,
		});
	};
	const setJobExclusionLabels=(job: Job, labels: readonly string[] | null)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			exclusionLabels: labels,
		});
	};
	const setJobActive=(job: Job, active: boolean)=>{
		api.put("/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			active: active,
		});
	};

	return <Box sx={{
			display: visible ? "flex" : "none",
			flexDirection: "column",
			width: "100%",
			height: "100%"}}
			className="section">
		<Dialog open={confirmingDelete} onClose={()=>setConfirmingDelete(false)}>
			<DialogTitle>Delete Job?</DialogTitle>
			<Box sx={{
				display: "flex",
				flexDirection: "column",
				alignItems: "start",
				marginLeft: 2,
				marginRight: 2,
				marginBottom: 5,
				}}>
				<Typography>
					Are you sure you want to delete job '{editJob?.name}'?<br />
					This cannot be undone.
				</Typography>
				<Box sx={{width: "100%", display: "flex", flexDirection: "row", justifyContent: "center"}}>
					<Button
						variant="contained"
						onClick={e=>doDeleteJob()}>
						Delete Job
					</Button>
				</Box>
			</Box>
		</Dialog>
		{org.manager ?
			/* Add/Remove Jobs */
			<Box sx={{display: "flex", flexDirection: "row"}}>
				&nbsp;&nbsp;
				<Tooltip title="Add a new worker">
					<IconButton onClick={addJob}>
						<AddIcon />
					</IconButton>
				</Tooltip>
				&nbsp;&nbsp;
				<Tooltip title={editJob==null ? "Select a worker to delete" : "Delete the selected worker"}>
					<span>
						<IconButton disabled={editJob==null} onClick={deleteJob}>
							<DeleteIcon />
						</IconButton>
					</span>
				</Tooltip>
			</Box>
			: null
		}

		{/* Job Table */}
		<TableContainer component={Paper}>
			<Table size="small">
				<TableHead>
					<TableRow>
						{debug ? <TableCell><b>ID</b></TableCell> : null}
						<TableCell><b>Name</b></TableCell>
						<TableCell><b>Points</b></TableCell>
						<TableCell><b>Last Done</b></TableCell>
						{/*
						These fields were for an older version of this app (not even for the web),
						in which there was an auto-assignment feature that would attempt to assign
						chores to workers automatically.
						These fields were to instruct that feature.
						That feature ended up being more trouble than it was worth, so these fields are no longer useful.
						<TableCell><b>Min Level</b></TableCell>
						<TableCell><b>Max Level</b></TableCell>
						<TableCell><b>Inclusion Labels</b></TableCell>
						<TableCell><b>Exclusion Labels</b></TableCell>*/}
						<TableCell><b>Active</b></TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{jobs.map(job=>{
						const editing=job.id==editJob?.id;
						const nameEditable=org.manager;
						const cellStyle= {backgroundColor: editing ? "lightblue" : ""};
						return <TableRow
							key={job.id}
							className={editing ? "selected" : null}
							onClick={e=>{
								if(e.ctrlKey && editJob?.id==job.id)
									setEditJob(null);
								else
									setEditJob(job);
							}}>
							{debug ? <TableCell>{job.id}</TableCell> : null}
							{nameEditable ? <EditableTableCell
								sx={cellStyle}
								value={job.name}
								onSave={newName=>renameJob(job, newName)}
								parser={s=>s}
								validator={newName=>{
									if(!newName || newName.length==0)
										return "Name cannot be empty";
									else if(newName.length>100)
										return "Name cannot exceed 100 characters";
									for(const j of jobs){
										if(j.id!=job.id && j.name==newName)
											return "Another worker named '"+newName+"' exists";
									}
									return null;
								}} />
								: <TableCell sx={cellStyle}>{job.name}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.value}
									onSave={newValue=>setJobPoints(job, newValue)}
									parser={parseInt}
									validator={v=>{
										if(v<0)
											return "Job value cannot be negative";
										else if(v>1000)
											return "Job value cannot exceed 1000";
										return null;
									}} />
								:
								<TableCell sx={cellStyle}>{job.value}</TableCell>
							}
							<TableCell sx={{
								...cellStyle,
								whiteSpace: "nowrap",
								width: "1%",
								}}>{job.lastDone ? myDateFormat.format(new Date(job.lastDone!)) : "Never"}</TableCell>
							{/*org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.minLevel}
									onSave={newLevel=>setJobMinLevel(job, newLevel)}
									parser={parseInt} />
								: <TableCell sx={cellStyle}>{job.minLevel}</TableCell>
							*/}
							{/*org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.maxLevel}
									onSave={newLevel=>setJobMaxLevel(job, newLevel)}
									parser={parseInt} />
								: <TableCell sx={cellStyle}>{job.maxLevel}</TableCell>
							*/}
							{/*org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.inclusionLabels}
									onSave={newLabels=>setJobInclusionLabels(job, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={cellStyle}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							*/}
							{/*org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.exclusionLabels}
									onSave={newLabels=>setJobExclusionLabels(job, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={cellStyle}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							*/}
							<TableCell sx={{
								paddingTop: 0,
								paddingBottom: 0,
								whiteSpace: "nowrap",
								width: "1%"
								}}>
								{org.manager ?
									<Checkbox
									 	sx={{paddingTop: 0, paddingBottom: 0}}
										checked={job.active}
										onChange={e=>setJobActive(job, e.target.checked)} />
									:
									<Checkbox checked={job.active} />
								}
							</TableCell>
						</TableRow>;
					})}
				</TableBody>
			</Table>
		</TableContainer>
		<Accordion
			sx={{display: editJob ? "" : "none"}}
			expanded={showHistory}
			onChange={(e: React.SyntheticEvent, expanded: boolean)=>setShowHistory(expanded)}>
			<AccordionSummary expandIcon={<ExpandMoreIcon />}>
				<Typography component="span">Job History</Typography>
			</AccordionSummary>
			<AccordionDetails>
				<PointHistoryView
					org={org}
					jobId={editJob?.id}
					visible={visible && showHistory && !!editJob} />
			</AccordionDetails>
		</Accordion>
	</Box>
};

export default JobsUI;
