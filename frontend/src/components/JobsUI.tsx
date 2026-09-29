import React, { useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { jobService } from "../services/services";
import Job from "../values/Job";
import { Box, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { EditableTableCell } from "./util/EditableTableCell";
import PointHistoryView from "./PointHistoryView";

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const jobs = useSyncExternalStore(
		listener=>jobService.onChange(listener),
		()=>jobService.getAll()); // Freelance work can use inactive jobs
	const [editJob, setEditJob] = useState<Job | null>(null);

	const addJob=()=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
		});
	};

	const deleteJob=()=>{
		//TODO Confirm
		jobService.modify("DELETE", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: editJob!.id,
		}).then(()=>setEditJob(null));
	};
	const renameJob=(job: Job, newName: string)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			name: newName,
		})
	};
	const setJobPoints=(job: Job, newPoints: number)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			value: newPoints,
		})
	};
	const myDateFormat=new Intl.DateTimeFormat(Intl.NumberFormat().resolvedOptions().locale, {
		dateStyle: "short",
		timeStyle: "medium",
	});
	const setJobMinLevel=(job: Job, minLevel: number)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			minLevel: minLevel,
		})
	};
	const setJobMaxLevel=(job: Job, maxLevel: number)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			maxLevel: maxLevel,
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
	const setJobInclusionLabels=(job: Job, labels: readonly string[] | null)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			inclusionLabels: labels,
		})
	};
	const setJobExclusionLabels=(job: Job, labels: readonly string[] | null)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			exclusionLabels: labels,
		})
	};
	const setJobActive=(job: Job, active: boolean)=>{
		jobService.modify("PUT", "/api/jobs", {
			orgId: org.organization!.id,
			jobId: job.id,
			active: active,
		})
	};

	return <Box sx={{display: visible ? "flex" : "none", flexDirection: "column", width: "100%", height: "100%"}}>
		{org.manager ?
			/* Add/Remove Jobs */
			<Box sx={{display: visible ? "flex" : "none", flexDirection: "row"}}>
				<Tooltip title="Add a new worker">
					<IconButton onClick={addJob}>
						<AddIcon />
					</IconButton>
				</Tooltip>
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
		<TableContainer component={Paper} sx={{display : visible ? "flex" : "none"}}>
			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell><b>Name</b></TableCell>
						<TableCell><b>Points</b></TableCell>
						<TableCell><b>Last Done</b></TableCell>
						<TableCell><b>Min Level</b></TableCell>
						<TableCell><b>Max Level</b></TableCell>
						<TableCell><b>Inclusion Labels</b></TableCell>
						<TableCell><b>Exclusion Labels</b></TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{jobs.map(job=>{
						const editing=job.id==editJob?.id;
						const nameEditable=org.manager;
						const cellStyle= {backgroundColor: editing ? "lightblue" : ""};
						return <TableRow key={job.id} onClick={e=>{
							setEditJob(job);
						}}>
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
										if(v<=0)
											return "Job value must be positive";
										else if(v>1000)
											return "Job value cannot exceed 1000";
										return null;
									}} />
								:
								<TableCell sx={cellStyle}>{job.value}</TableCell>
							}
							<TableCell>{job.lastDone ? myDateFormat.format(new Date(job.lastDone!)) : "Never"}</TableCell>
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.minLevel}
									onSave={newLevel=>setJobMinLevel(job, newLevel)}
									parser={parseInt} />
								: <TableCell sx={cellStyle}>{job.minLevel}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.maxLevel}
									onSave={newLevel=>setJobMaxLevel(job, newLevel)}
									parser={parseInt} />
								: <TableCell sx={cellStyle}>{job.maxLevel}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.inclusionLabels}
									onSave={newLabels=>setJobInclusionLabels(job, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={cellStyle}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={job.inclusionLabels}
									onSave={newLabels=>setJobExclusionLabels(job, newLabels)}
									parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={cellStyle}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							}
						</TableRow>;
					})}
				</TableBody>
			</Table>
		</TableContainer>
		<PointHistoryView org={org} jobId={editJob?.id} visible={visible && !!editJob} />
	</Box>
};

export default JobsUI;
