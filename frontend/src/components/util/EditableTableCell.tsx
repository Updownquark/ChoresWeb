import React, { useState, useRef } from "react";
import { SxProps, TableCell, TableCellProps } from "@mui/material";
import ValidatedTextField from "./ValidatedTextField"; // Path to your component

interface EditableTableCellProps<T> extends Omit<TableCellProps, "onChange"> {
	value: T;
	onSave: (newValue: T) => void;
	parser: (value: string) => T;
	renderer?: (value: T) => string;
	validator?: (value: T) => string | null;
}

export function EditableTableCell<T>({
	value,
	onSave,
	parser,
	renderer,
	validator,
	...tableCellProps
}: EditableTableCellProps<T>) {
	const [isEditing, setIsEditing] = useState(false);
	const containerRef = useRef<HTMLTableCellElement>(null);

	const handleFocus = () => {
		setIsEditing(true);
	};

	const handleBlur = (e: React.FocusEvent) => {
		// Check if focus moved completely out of the TableCell context
		if (
			containerRef.current &&
			!containerRef.current.contains(e.relatedTarget)
		) {
			setIsEditing(false);
		}
	};

	const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
		// Blurring triggers the standard unfocus logic inside ValidatedTextField
		if (e.key === "Enter" || e.key === "Escape") {
			(e.target as HTMLInputElement).blur();
			setIsEditing(false);
		}
	};

	// Fallback string renderer matching your internal logic
	const displayValue = renderer ? renderer(value) : String(value);

	const sx: SxProps={

	};

	return (
		<TableCell
			{...tableCellProps}
			ref={containerRef}
			tabIndex={0} // Makes the cell container focusable
			onFocus={handleFocus}
			onBlur={handleBlur}
			sx={{
				cursor: "pointer",
				height: 30,
				padding: "2px 16px",
				verticalAlign: "middle",
				"&:focus": {
					outline: "none",
					backgroundColor: "action.hover",
				},
				...tableCellProps.sx,
			}}
		>
			{isEditing ? (
				<ValidatedTextField<T>
					value={value}
					onChange={(newValue) => {
						onSave(newValue);
						setIsEditing(false);
					}}
					parser={parser}
					renderer={renderer}
					validator={validator}
					autoFocus
					selectAllOnFocus
					onKeyDown={handleKeyDown}
					// Pass regular variants down to blend into the table cell seamlessly
					slotProps={{
						input: {
							disableUnderline: true,
							sx: { fontSize: "inherit" },
						},
					}}
					variant="standard"
					fullWidth
				/>
			) : (
				displayValue
			)}
		</TableCell>
	);
}
