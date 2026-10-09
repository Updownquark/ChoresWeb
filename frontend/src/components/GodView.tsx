import React from "react";
import Membership from "../values/Membership";
import { AxiosInstance } from "axios";

export interface GodViewParams{
    org: Membership | null;
    api: AxiosInstance;
}

const GodView: React.FC<GodViewParams> = ({org, api}) =>{

};

export default GodView;
