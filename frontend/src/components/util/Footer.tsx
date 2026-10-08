import { Box, Tooltip } from "@mui/material";
import React, { useSyncExternalStore } from "react"
import { syncService } from "../../services/services";

const Footer: React.FC = ()=>{
    const status=useSyncExternalStore(//
        listener=>syncService.onConnectionStatusChange(listener),//
        ()=>syncService.getConnectionStatus());
    
    let color: string;
    switch(status.status){
        case "success":
            color="green";
            break;
        case "attempt":
            color="orange";
            break;
        case "failed":
            color="red";
            break;
        default:
            color="gray";
            break;
    }

    return <Box component="footer" sx={{
        width: "100%",
        px: 2,
        py: 6,
        mt: "auto",
        display: "flex",
        flexDirection: "row",
        justifyContent: "flex-end",
    }}>
        <Tooltip title={status.message}>
            <div style= {{
                width: 20,
                height: 20,
                borderRadius: "50%",
                backgroundColor: color
            }} />
        </Tooltip>
    </Box>
};

export default Footer;
