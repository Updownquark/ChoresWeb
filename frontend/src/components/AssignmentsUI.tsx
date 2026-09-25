import React, { useEffect, useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { assignmentService, jobService, memberService } from "../services/services";
import { Box, Button, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip } from "@mui/material";
import { EditableTableCell } from "./util/EditableTableCell";

const AssignmentsUI: React.FC<ChoresTabParams>=({api, org, visible})=>{
	const jobs = useSyncExternalStore(
		listener=>jobService.onChange(listener),
		()=>jobService.getActiveJobs());
	const workers=useSyncExternalStore(
		listener=>memberService.onChange(listener),
		()=>memberService.getWorkers());

	// Re-render when assignments change
	const [, assignmentsChanged] =useState(0);
	useEffect(()=>assignmentService.onChange(()=>assignmentsChanged(prev=>prev+1)), []);

	return <TableContainer component={Paper} sx={{display : visible ? "" : "none"}}>
		<Table sx={{width:"100%"}} size="small">
			<TableHead>
				<TableRow>
					<TableCell>Job</TableCell>
					<TableCell>Points</TableCell>
					{workers.map(worker=>{
						return <TableCell key={worker.member!.id} sx={{fontWeight: "bold"}}>{worker.name+" ("+worker.points+")"}</TableCell>;
					})}
				</TableRow>
			</TableHead>
			<TableBody>
				{jobs.map(job=>{
					const assignments=assignmentService.getJobAssignments(job.id);
					return <TableRow key={job.id}>
						<TableCell>{job.name}</TableCell>
						<TableCell>{job.value}</TableCell>
						{workers.map(worker=>{
							const assn=assignments.get(worker.member!.id);
							return <EditableTableCell
								key={worker.member!.id}
								sx={{border: "1px solid black"}}
								value={assn?.completed}
								onSave={newV=>{
									assignmentService.modify("PUT", "/api/assignments", {
										orgId: org.organization!.id,
										userId: worker.member!.id,
										jobId: job.id,
										completed: newV
									});
								}} renderer={v=>{
									if(v)
										return v.toString();
									else
										return "";
								}} parser={parseInt} validator={newV=>{
									if(newV!<0)
										return "Completed amount must be >=0";
									return null;
								}} />
						})}
					</TableRow>;
				})}
			</TableBody>
		</Table>
		<Box sx={{display: "flex", flexDirection: "row", alignItems: "center", justifyContent: "space-evenly"}}>
			<Button onClick={e=>{
				assignmentService.modify("POST", "/api/assignments/submit", {
					orgId: org.organization!.id
				});
				memberService.check();
			}}>Submit</Button>
			<Button onClick={e=>assignmentService.modify("DELETE", "/api/assignments/all", {
				orgId: org.organization!.id
			})}>Clear All</Button>
		</Box>
	</TableContainer>;
};

export default AssignmentsUI;
