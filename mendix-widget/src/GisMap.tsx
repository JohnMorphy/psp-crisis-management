
import { useEffect } from "react";

export function GisMap() {
  useEffect(() => {
    const WIDGET_BASE = "widgets/3qcode/gismap/assets";

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = `${WIDGET_BASE}/index.css`;
    document.head.appendChild(link);

    const script = document.createElement("script");
    script.src = `${WIDGET_BASE}/index.js`;
    script.type = "module";
    document.body.appendChild(script);

    return () => {
      document.head.removeChild(link);
      document.body.removeChild(script);
    };
  }, []);

  return <div id="root" style={{ width: "100%", height: "100%" }} />;
}
