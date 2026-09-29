import { Box, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@mui/material";
import Membership from "../values/Membership";
import { useEffect, useState } from "react";
import ValidatedTextField from "./util/ValidatedTextField";
import PointChangeRecord from "../values/PointChangeRecord";
import { historyService, jobService, resourcesService } from "../services/services";
import ArrowBackIos from "@mui/icons-material/ArrowBackIos";
import ArrowForwardIos from "@mui/icons-material/ArrowForwardIos";
import DeleteIcon from "@mui/icons-material/Delete";

interface PointHistoryViewProps{
	org: Membership;
	userId?: number;
	jobId?: number;
	resourceId?: number;
	visible: boolean;
}

const PointHistoryView: React.FC<PointHistoryViewProps>=({org, userId, jobId, resourceId, visible}): React.ReactNode=>{
	const [pageNumber, _setPageNumber] = useState(0);
	const [pageSize, _setPageSize]=useState(100);

	const [historySize, setHistorySize] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [history]=useState<PointChangeRecord[]>([]);
	const [loading, setLoading] = useState(true);
	const [refresh, setRefresh] = useState(0);
	const [selectedItems]=useState(new Set<number>());
	const [selectionAnchor, setSelectionAnchor]=useState(-1);
	const [selectionRefresh, setSelectionRefresh]=useState(0);

	useEffect(()=>{
		if(visible){
			historyService.getHistory(org, userId, jobId, resourceId, pageSize, pageNumber)//
				.then(setCurrentItems)
				.finally(()=>setLoading(false));
		}
	}, [visible, refresh, pageNumber, pageSize]);

	// Listen for changes
	useEffect(()=>{
		if(visible){
			historyService.getHistoryCount(org, userId, jobId, resourceId).then(newSize=>{
				setHistorySize(newSize);
				setPageCount(Math.ceil(newSize/pageSize));
				if(pageNumber!=0)
					setPageNumber(0);
				setRefresh(refresh+1);
			});
			return historyService.onChange(userId, jobId, resourceId, ()=>setRefresh(refresh+1));
		}
	}, [userId, jobId, resourceId, visible]);


	const setCurrentItems=(data: readonly PointChangeRecord[])=>{
		history.splice(0, history.length, ...data);
		const newItemIds: Set<number>=new Set();
		for(const item of data)
			newItemIds.add(item.id);
		const prevSelected: Set<number>=new Set(selectedItems);
		for(const itemId of prevSelected){
			if(!newItemIds.has(itemId))
				selectedItems.delete(itemId);
		}
	}

	const setPageNumber=(newPage: number)=>{
		setPageNumber(newPage);
		setLoading(true);
	};

	const setPageSize=(newSize: number)=>{
		setLoading(true);
		const newPageNumber=pageNumber*pageSize/newSize;
		setPageSize(newSize);
		setPageNumber(newPageNumber);
	};

	const selectionClick=(itemId: number, ctrl: boolean, shift: boolean)=>{
		if(!ctrl && !shift){
			if(selectedItems.size==1 && selectedItems.has(itemId))
				return;
			selectedItems.clear();
			selectedItems.add(itemId);
			setSelectionAnchor(itemId);
		} else if(ctrl){
			if(selectedItems.has(itemId))
				selectedItems.delete(itemId);
			else{
				selectedItems.add(itemId);
				setSelectionAnchor(itemId);
			}
		} else if(selectedItems.size==0){
			selectedItems.add(itemId);
			setSelectionAnchor(itemId);
		} else{
			let foundAnchor=false;
			for(const item of history){
				if(foundAnchor){
					selectedItems.add(item.id);
					if(item.id==itemId)
						break;
				} else if(item.id==selectionAnchor){
					foundAnchor=true;
					selectedItems.add(item.id);
				}
			}
		}
		setSelectionRefresh(selectionRefresh+1);
	}
	const revertSelectedItems=()=>{
		//TODO Confirm
		historyService.revertHistory(org, ...selectedItems);
	};

	const myDateFormat=new Intl.DateTimeFormat(Intl.NumberFormat().resolvedOptions().locale, {
		dateStyle: "short",
		timeStyle: "medium",
	});

	const printQuantity=(item: PointChangeRecord, unit: string): string => {
		if("$"== unit)
			return unit+item.quantity.toFixed(2);
		else
			return item.quantity.toString();
	}

	if(!visible)
		return null;

	const pager=<Box sx={{display: "flex", flexDirection: "row", alignItems: "center"}}>
		Page:&nbsp;
		<IconButton
			disabled={loading || pageNumber==0}
			onChange={(()=>setPageNumber(pageNumber-1))}>
			<ArrowBackIos />
		</IconButton>
		<ValidatedTextField
			sx={{width: "8ch"}}
			value={pageNumber}
			onChange={setPageNumber}
			disabled={loading}
			parser={parseInt}
			renderer={v=>(v+1).toString()}
			validator={page=>{
				if(page<=0)
					return "Page must be a positive number";
				else if(page>=pageCount)
					return "Only "+pageCount+" pages exist.";
				return null;
			}} />
		&nbsp;
		<IconButton
			disabled={loading || pageNumber+1>=pageCount}
			onChange={(()=>setPageNumber(pageNumber-1))}>
			<ArrowForwardIos />
		</IconButton>
		of {pageCount} ({historySize} items)
		&nbsp;&nbsp;&nbsp;&nbsp;Page Size:&nbsp;
		<ValidatedTextField
			sx={{width: "8ch"}}
			value={pageSize}
			onChange={setPageSize}
			disabled={loading}
			parser={parseInt}
			validator={size=>{
				if(size<=0)
					return "Page size must be a positive number";
				else if(size>1000)
					return "Page size cannot exceed 1000";
				return null;
			}} />
	</Box>;
	const revert=org.manager ?
		<Box sx={{display: "flex", flexDirection: "row"}}>
			<IconButton
				disabled={!selectedItems.size}
				onClick={revertSelectedItems}>
				<DeleteIcon />
			</IconButton>
		</Box>
		: null;
	const table=<TableContainer component={Paper}>
		<Table size="small">
			<TableHead>
				<TableRow>
					<TableCell>Date/Time</TableCell>
					<TableCell>Type</TableCell>
					<TableCell>Job/Resource</TableCell>
					<TableCell>Amount</TableCell>
					<TableCell>Points Before</TableCell>
					<TableCell>Point Change</TableCell>
					<TableCell>Points After</TableCell>
				</TableRow>
			</TableHead>
			<TableBody>
				{history.map(item=>{
					let changeSource: {name: string, unit?: string} | null = null;
					switch(item.changeSourceName){
						case "Job":
						case "Work":
							changeSource=jobService.getById(item.changeSourceId);
							break;
						case "Resource":
						case "Redeemed":
							changeSource=resourcesService.getById(item.changeSourceId);
							break;
					}
					const changeSourceName=changeSource?.name ?? item.changeSourceName;
					return <TableRow key={item.id} onClick={e=>selectionClick(item.id, e.ctrlKey, e.shiftKey)}>
						<TableCell>{myDateFormat.format(new Date(item.time))}</TableCell>
						<TableCell>{item.changeType}</TableCell>
						<TableCell>{changeSourceName}</TableCell>
						<TableCell>{printQuantity(item, changeSource?.unit)}</TableCell>
						<TableCell>{item.beforePoints}</TableCell>
						<TableCell>{item.pointChange}</TableCell>
						<TableCell>{item.beforePoints+item.pointChange}</TableCell>
					</TableRow>
				})}
			</TableBody>
		</Table>
	</TableContainer>;
	return <>{pager} {revert} {table}</>;
};

export default PointHistoryView;