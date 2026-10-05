import { IconButton, Menu } from "@mui/material";
import React, { useEffect, useState } from "react";
import SettingsIcon from "@mui/icons-material/Settings";

interface SettingsMenuProps{
	children: React.ReactNode;
	close: (doClose: ()=>void)=>void;
}

const SettingsMenu: React.FC<SettingsMenuProps> = ({ children, close }) => {
	const [settingsAnchor, setSettingsAnchor] = useState<HTMLButtonElement | null>(null);
	// const settingsOpen = Boolean(settingsAnchor);
	const openSettings = (e: React.MouseEvent<HTMLButtonElement>) => setSettingsAnchor(e.currentTarget);
	const closeSettings = () => setSettingsAnchor(null);

	useEffect(()=>{
		close(()=>setSettingsAnchor(null));
	}, [close]);

	return (
		<>
			<IconButton sx={{border: "none !important"}} onClick={openSettings}>
				<SettingsIcon />
			</IconButton>
			<Menu
				open={Boolean(settingsAnchor)}
				anchorEl={settingsAnchor}
				onClose={closeSettings}
				anchorOrigin={{
					vertical: "top",
					horizontal: "right",
				}}>
				{children}
			</Menu>
		</>
	);
};

export default SettingsMenu;
