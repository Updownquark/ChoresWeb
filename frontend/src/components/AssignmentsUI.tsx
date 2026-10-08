import React, { useEffect, useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { assignmentService,  jobService, memberService } from "../services/services";
import { Box, Button, Dialog, DialogTitle, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
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
	const [isConfirmingSubmit, setConfirmingSubmit] = useState(false);
	const [isConfirmingClear, setConfirmingClear] = useState(false);
	
	useEffect(()=>assignmentService.onChange(()=>assignmentsChanged(prev=>prev+1)), []);

	return <>
	<Dialog open={isConfirmingSubmit}>
		<DialogTitle sx={{textAlign: "center"}}>Submit Work?</DialogTitle>
		<Box sx={{
			display: "flex",
			flexDirection: "column",
			alignItems: "stretch",
			paddingLeft: 2,
			paddingRight: 2,
			paddingBottom: 1,
			}}>
			<Typography sx={{textAlign: "center"}}>Submit these assignments as work done?</Typography>
			<Box sx={{
				display: "flex",
				flexDirection: "row",
				justifyContent: "space-evenly",
				width: "100%"
				}}>
				<Button onClick={e=>{
					e.preventDefault();
					setConfirmingSubmit(false);
					api.post("/api/assignments/submit", null, {
						params: {orgId: org.organization!.id}
					});
				}}>OK</Button>
				<Button onClick={e=>{
					e.preventDefault();
					setConfirmingSubmit(false);
				}}>Cancel</Button>
			</Box>
		</Box>
	</Dialog>
	<Dialog open={isConfirmingClear}>
		<DialogTitle>Clear Assignments</DialogTitle>
		Clear all assignments?
		<Box sx={{display: "flex", flexDirection: "row", justifyItems: "space-evenly"}}>
			<Button onClick={e=>{
				e.preventDefault();
				setConfirmingClear(false);
				api.delete("/api/assignments/all", {
					params: {orgId: org.organization!.id}
				});
			}}>OK</Button>
			<Button onClick={e=>{
				e.preventDefault();
				setConfirmingClear(false);
			}}>Cancel</Button>
		</Box>
	</Dialog>
	<TableContainer component={Paper} sx={{display : visible ? "" : "none"}}>
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
						<TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>{job.name}</TableCell>
						<TableCell sx={{whiteSpace: "nowrap", width: "1%"}}>{job.value}</TableCell>
						{workers.map(worker=>{
							const assn=assignments.get(worker.member!.id);
							return <EditableTableCell
								key={worker.member!.id}
								sx={{border: "1px solid black"}}
								value={assn?.completed}
								onSave={newV=>{
									api.put("/api/assignments", {
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
			<Button onClick={()=>setConfirmingSubmit(true)}>Submit</Button>
			<Button onClick={()=>setConfirmingClear(true)}>Clear All</Button>
		</Box>
	</TableContainer>
	</>;
};

export default AssignmentsUI;
