import { ReactElement } from "react";
import { HelloWorldSample } from "./components/HelloWorldSample";
import { GisMapPreviewProps } from "../typings/GisMapProps";

export function preview({ sampleText }: GisMapPreviewProps): ReactElement {
    return <HelloWorldSample sampleText={sampleText} />;
}

export function getPreviewCss(): string {
    return require("./ui/GisMap.css");
}
