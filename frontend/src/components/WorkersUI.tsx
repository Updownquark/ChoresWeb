import React, { useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { memberService } from "../services/services";
import AddIcon from "@mui/icons-material/Add";
import { Box, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@mui/material";
import { EditableTableCell } from "./util/EditableTableCell";

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const workers=useSyncExternalStore(
		listener=>memberService.onChange(listener),
		()=>memberService.getWorkers());
	const [editWorkerId, setEditWorkerId] = useState(-1);

	const parseLabels=(labelStr: string)=> {
		if(!labelStr || labelStr.length==0)
			return null;
		const labels=labelStr.split(",");
		for(let i=0;i<labels.length;i++)
			labels[i]=labels[i].trim();
		return labels;
	}
	
	return <Box sx={{display: "flex", flexDirection: "column", width: "100%", height: "100%"}}>
		<TableContainer component={Paper} sx={{display : visible ? "flex" : "none"}}>
			<Table size="small">
				<TableHead>
					<TableRow>
						<TableCell><h4>Name</h4></TableCell>
						<TableCell><h4>Points</h4></TableCell>
						<TableCell><h4>Level</h4></TableCell>
						<TableCell><h4>Labels</h4></TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{workers.map(worker=>{
						const editing=worker.member!.id==editWorkerId;
						const nameEditable=worker.manager || worker.member!.id==org.member?.id;
						return <TableRow key={worker.member!.id} onClick={e=>{
							setEditWorkerId(worker.member!.id);
						}}>
							{nameEditable ? <EditableTableCell
								sx={{backgroundColor: editing ? "lightblue" : ""}}
								value={worker.name}
								onSave={newName=>{
									memberService.modify("POST", "/api/members/modify", {
										orgId: org.organization!.id,
										userId: worker.member!.id,
										name: newName
									})
								}} parser={s=>s} validator={newName=>{
									if(!newName || newName.length==0)
										return "Name cannot be empty";
									else if(newName.length>100)
										return "Name cannot exceed 100 characters";
									for(const w of workers){
										if(w!=worker && w.name==newName)
											return "Another worker named '"+newName+"' exists";
									}
									return null;
								}} />
								: <TableCell sx={{backgroundColor: editing ? "lightblue" : ""}}>{worker.name}</TableCell>
							}
							<TableCell sx={{backgroundColor: editing ? "lightblue" : ""}}>{worker.points}</TableCell>
							{org.manager ?
								<EditableTableCell
									sx={{backgroundColor: editing ? "lightblue" : ""}}
									value={worker.level}
									onSave={newLevel=>{
										memberService.modify("POST", "/api/members/modify", {
											orgId: org.organization!.id,
											userId: worker.member!.id,
											level: newLevel
										})
									}} parser={parseInt} />
								: <TableCell sx={{backgroundColor: editing ? "lightblue" : ""}}>{worker.level}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={{backgroundColor: editing ? "lightblue" : ""}}
									value={worker.labels}
									onSave={newLabels=>{
									}} parser={parseLabels}
									renderer={labels=>labels ? labels.join(",") : ""} />
								: <TableCell sx={{backgroundColor: editing ? "lightblue" : ""}}>worker.labels ? worker.labels.join(",") : ""</TableCell>
							}
						</TableRow>;
					})}
				</TableBody>
			</Table>
		</TableContainer>
	</Box>
};

export default JobsUI;
