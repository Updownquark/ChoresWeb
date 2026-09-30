import React, { useEffect, useState, useSyncExternalStore } from "react";
import ChoresTabParams from "./ChoresTabParams";
import { debug, resourcesService } from "../services/services";
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Dialog, DialogTitle, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import PointResource from "../values/PointResource";
import { EditableTableCell } from "./util/EditableTableCell";
import PointHistoryView from "./PointHistoryView";

const subscribeToResources = resourcesService.onChange.bind(resourcesService);
const getResourcesSnapshot = resourcesService.getAll.bind(resourcesService);

const ResourcesUI: React.FC<ChoresTabParams> = ({api, org, visible})=>{
	const getInitialEditResource=(): PointResource | null => {
		const selectedRsrcStr=sessionStorage.getItem("selectedResource");
		if(selectedRsrcStr){
			return resourcesService.getById(parseInt(selectedRsrcStr));
		}
		return null;
	}

	const resources = useSyncExternalStore(subscribeToResources, getResourcesSnapshot);
	const [editResource, _setEditResource] = useState<PointResource | null>(getInitialEditResource());

	const [confirmingDelete, setConfirmingDelete] = useState(false);

	const [showHistory, setShowHistory] = useState(false);

	useEffect(()=>{
		if(!visible) //Hide history when the Resources tab is de-selected
			setShowHistory(false);
	}, [visible]);

	useEffect(()=>{
		const rsrc=getInitialEditResource();
		if(rsrc?.id!=editResource?.id)
			_setEditResource(rsrc);
	}, [resources]);

	const setEditResource=(rsrc: PointResource | null)=>{
		_setEditResource(rsrc);
		if(rsrc)
			sessionStorage.setItem("selectedResource", rsrc.id.toString());
		else
			sessionStorage.removeItem("selectedResource");
	}
	
	const addResource=()=>{
		resourcesService.modify("PUT", "/api/resources", {
			orgId: org.organization!.id,
		}).then(event=>{
			if(event.added)
				setEditResource(event.added.values().next().value!);
		});
	};

	const deleteResource=()=>{
		setConfirmingDelete(true);
	}

	const doDeleteResource=()=>{
		setConfirmingDelete(false);
		resourcesService.modify("DELETE", "/api/resources/"+editResource!.id).then(()=>setEditResource(null));
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

	return <Box sx={{
			display: visible ? "flex" : "none",
			flexDirection: "column",
			width: "100%",
			height: "100%"}}
			className="section">
		<Dialog open={confirmingDelete} onClose={()=>setConfirmingDelete(false)}>
			<DialogTitle>Delete Resource?</DialogTitle>
			<Box sx={{
				display: "flex",
				flexDirection: "column",
				alignItems: "start",
				marginLeft: 2,
				marginRight: 2,
				marginBottom: 5,
				}}>
				<Typography>
					Are you sure you want to delete resource '{editResource?.name}'?<br />
					This cannot be undone.
				</Typography>
				<Box sx={{width: "100%", display: "flex", flexDirection: "row", justifyContent: "center"}}>
					<Button
						variant="contained"
						onClick={e=>doDeleteResource()}>
						Delete Resource
					</Button>
				</Box>
			</Box>
		</Dialog>
		{org.manager ?
			/* Add/Remove Jobs */
			<Box sx={{display: "flex", flexDirection: "row"}}>
				&nbsp;&nbsp;
				<Tooltip title="Add a new worker">
					<IconButton onClick={addResource}>
						<AddIcon />
					</IconButton>
				</Tooltip>
				&nbsp;&nbsp;
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
		<TableContainer component={Paper}>
			<Table size="small">
				<TableHead>
					<TableRow>
						{debug ? <TableCell><b>ID</b></TableCell> : null}
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
							setEditResource(rsrc);
						}}>
							{debug ? <TableCell>{rsrc.id}</TableCell> : null}
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
		<Accordion
			sx={{display: editResource ? "" : "none"}}
			expanded={showHistory}
			onChange={(e: React.SyntheticEvent, expanded: boolean)=>setShowHistory(expanded)}>
			<AccordionSummary expandIcon={<ExpandMoreIcon />}>
				<Typography component="span">Resource History</Typography>
			</AccordionSummary>
			<AccordionDetails>
				<PointHistoryView
					org={org}
					resourceId={editResource?.id}
					visible={visible && showHistory && !!editResource} />
			</AccordionDetails>
		</Accordion>
	</Box>
};

export default ResourcesUI;
