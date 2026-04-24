
import { useEffect } from "react";

export function GisMap() {
  useEffect(() => {
    const script = document.createElement("script");
    script.src = "widgets/mywidget/assets/assets/index.js"; 
    script.type = "module";
    document.body.appendChild(script);
  }, []);

  return <div id="root"></div>;
}