import { useState, useEffect, useRef } from "react";
import { TextField, TextFieldProps as MuiTextFieldProps } from "@mui/material";

interface TextFieldProps<T> extends Omit<MuiTextFieldProps, 'value' | 'onChange'> {
	value: T;
	onChange: (value: T) => void;
	parser: (value: string) => T;
	renderer?: (value: T) => string;
	validator?: (value: T) => string | null;
	onBlur?: (event: React.FocusEvent) => void;
	autoFocus?: boolean;
	selectAllOnFocus?: boolean;
	onKeyDown?: (event: React.KeyboardEvent<HTMLInputElement>) => void;
}
function render<T>(value: T, renderer?: (value: T) => string): string {
	if (renderer) {
		return renderer(value);
	} else {
		return String(value);
	}
}

function ValidatedTextField<T>({
	value,
	onChange,
	parser,
	renderer,
	validator,
	onBlur,
	autoFocus,
	selectAllOnFocus,
	onKeyDown,
	...restProps
}: TextFieldProps<T>) {
	const [draftValue, setDraftValue] = useState<T>(value);
	const [draftText, setDraftText] = useState(render(value, renderer));
	const [isDirty, setIsDirty] = useState(false);
	const [isValid, setValid] = useState<string | null>(null);

	const stateRef = useRef({ draftValue, draftText, isDirty, isValid, value, onChange });

	useEffect(() => {
		stateRef.current = { draftValue, draftText, isDirty, isValid, value, onChange };
	}, [draftValue, draftText, isDirty, isValid, value, onChange]);

	useEffect(() => {
		setDraftValue(value);
		setDraftText(render(value, renderer));
		setDirty(false);
		setValid(null);
	}, [value, renderer]);

	useEffect(() => {
		return () => {
			//Cleanup on unmount
			const {
				isDirty: currentDirty,
				isValid: currentValid,
				draftValue: currentDraft,
				onChange: currentOnChange,
			} = stateRef.current;
			if (currentDirty && currentValid === null) {
				currentOnChange(currentDraft); // Commit on unmount
			}
		};
	}, []);

	const setDirty = (dirty: boolean) => {
		stateRef.current.isDirty=false;
		setIsDirty(dirty);
	};

	const commit = () => {
		if (stateRef.current.isDirty && stateRef.current.isValid == null) {
			stateRef.current.onChange(stateRef.current.draftValue);
			setDirty(false);
		}
	};

	const revert = () => {
		if (stateRef.current.isDirty) {
			setDraftValue(stateRef.current.value);
			setDraftText(render(stateRef.current.value, renderer));
			setDirty(false);
			setValid(null);
		}
	};

	const unfocus = (e: React.FocusEvent) => {
		if (stateRef.current.isDirty) {
			if (stateRef.current.isValid == null) commit();
			else revert();
		}
		if (onBlur) onBlur(e);
	};

	const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
		const newValue = e.target.value;
		setDraftText(newValue);
		var parsedValue: T;
		try {
			parsedValue = parser(newValue);
		} catch(e) {
			if(typeof e === "string")
				setValid(e as string);
			else
				setValid("Invalid input");
			return;
		}
		setDraftValue(parsedValue);
		setDirty(true);
		if (validator) {
			setValid(validator(parsedValue));
		} else setValid(null);
	};

	return (
		<TextField
			{...restProps}
			value={draftText}
			onChange={handleInputChange}
			onFocus={(e) => {
				if (selectAllOnFocus) e.target.select();
			}}
			onBlur={unfocus}
			autoFocus={autoFocus}
			onKeyDown={(e) => {
				if (e.key === "Enter") {
					commit();
				} else if (e.key === "Escape") {
					revert();
					(e.target as HTMLInputElement).blur();
				}
				if (onKeyDown) onKeyDown(e as React.KeyboardEvent<HTMLInputElement>);
			}}
			error={isValid != null}
			helperText={isValid}
		/>
	);
}

export default ValidatedTextField;
