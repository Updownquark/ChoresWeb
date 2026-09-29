import React, { useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { resourcesService } from "../services/services";
import { Box, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import PointResource from "../values/PointResource";
import { EditableTableCell } from "./util/EditableTableCell";
import PointHistoryView from "./PointHistoryView";

const JobsUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const resources = useSyncExternalStore(
		listener=>resourcesService.onChange(listener),
		()=>resourcesService.getAll()); // Freelance work can use inactive jobs
	const [editResource, setEditJob] = useState<PointResource | null>(null);

	const addResource=()=>{
		resourcesService.modify("PUT", "/api/resources", {
			orgId: org.organization!.id,
		});
	};

	const deleteResource=()=>{
		//TODO Confirm
		resourcesService.modify("DELETE", "/api/resources", {
			orgId: org.organization!.id,
			resourceId: editResource!.id,
		}).then(()=>setEditJob(null));
	};
	const renameResource=(rsrc: PointResource, newName: string)=>{
		resourcesService.modify("PUT", "/api/resources", {
			orgId: org.organization!.id,
			resourceId: rsrc.id,
			name: newName,
		})
	};
	const setResourceRate=(rsrc: PointResource, newRate: number)=>{
		resourcesService.modify("PUT", "/api/resources", {
			orgId: org.organization!.id,
			resourceId: rsrc.id,
			rate: newRate,
		})
	};
	const setResourceUnit=(rsrc: PointResource, unit: string | null)=>{
		resourcesService.modify("PUT", "/api/resources", {
			orgId: org.organization!.id,
			resourceId: rsrc.id,
			unit: unit,
		})
	};

	return <Box sx={{display: visible ? "flex" : "none", flexDirection: "column", width: "100%", height: "100%"}}>
		{org.manager ?
			/* Add/Remove Jobs */
			<Box sx={{display: visible ? "flex" : "none", flexDirection: "row"}}>
				<Tooltip title="Add a new worker">
					<IconButton onClick={addResource}>
						<AddIcon />
					</IconButton>
				</Tooltip>
				<Tooltip title={editResource==null ? "Select a worker to delete" : "Delete the selected worker"}>
					<span>
						<IconButton disabled={editResource==null} onClick={deleteResource}>
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
						<TableCell><b>Rate</b></TableCell>
						<TableCell><b>Unit</b></TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{resources.map(rsrc=>{
						const editing=rsrc.id==editResource?.id;
						const nameEditable=org.manager;
						const cellStyle= {backgroundColor: editing ? "lightblue" : ""};
						return <TableRow key={rsrc.id} onClick={e=>{
							setEditJob(rsrc);
						}}>
							{nameEditable ? <EditableTableCell
								sx={cellStyle}
								value={rsrc.name}
								onSave={newName=>renameResource(rsrc, newName)}
								parser={s=>s}
								validator={newName=>{
									if(!newName || newName.length==0)
										return "Name cannot be empty";
									else if(newName.length>100)
										return "Name cannot exceed 100 characters";
									for(const j of resources){
										if(j.id!=rsrc.id && j.name==newName)
											return "Another worker named '"+newName+"' exists";
									}
									return null;
								}} />
								: <TableCell sx={cellStyle}>{rsrc.name}</TableCell>
							}
							{org.manager ?
								<EditableTableCell
									sx={cellStyle}
									value={rsrc.rate}
									onSave={newValue=>setResourceRate(rsrc, newValue)}
									parser={parseInt}
									validator={v=>{
										if(Math.abs(v)<=0.0)
											return "Resource rate cannot be zero";
										else if(Math.abs(v)>1000)
											return "Resource rate cannot exceed 1000";
										return null;
									}} />
								:
								<TableCell sx={cellStyle}>{rsrc.rate}</TableCell>
							}
							{org.manager ?
								<EditableTableCell<string | null>
									sx={cellStyle}
									value={rsrc.unit}
									onSave={newUnit=>setResourceUnit(rsrc, newUnit)}
									parser={s=>s?.length ? s : null}
									validator={newUnit=>{
										if(newUnit && newUnit.length>16)
											return "Name cannot exceed 16 characters";
										return null;
									}} />
								:
								<TableCell sx={cellStyle}>{rsrc.unit?? ""}</TableCell>
							}
						</TableRow>;
					})}
				</TableBody>
			</Table>
		</TableContainer>
		<PointHistoryView org={org} resourceId={editResource?.id} visible={visible && !!editResource} />
	</Box>
};

export default JobsUI;
