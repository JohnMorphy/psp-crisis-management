import { ReactElement } from "react";

export function preview(): ReactElement {
    return (
        <div style={{
            width: "100%",
            minHeight: 320,
            background: "#111827",
            borderRadius: 6,
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            fontFamily: "sans-serif",
        }}>
            {/* Header */}
            <div style={{
                background: "#1f2937",
                padding: "8px 14px",
                display: "flex",
                alignItems: "center",
                gap: 8,
                borderBottom: "1px solid #374151",
            }}>
                <span style={{ fontSize: 14, color: "#f9fafb", fontWeight: 600 }}>
                    Dashboard Jednostek Ochrony Ludności
                </span>
                <span style={{
                    marginLeft: "auto",
                    fontSize: 11,
                    color: "#22c55e",
                    border: "1px solid #22c55e",
                    borderRadius: 3,
                    padding: "1px 6px",
                }}>● ONLINE</span>
            </div>

            {/* Body */}
            <div style={{ display: "flex", flex: 1 }}>
                {/* Map area */}
                <div style={{
                    flex: "0 0 70%",
                    background: "#1a2332",
                    position: "relative",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                }}>
                    {/* Grid lines suggesting a map */}
                    <svg width="100%" height="100%" style={{ position: "absolute", inset: 0, opacity: 0.15 }}>
                        {[20, 40, 60, 80].map(p => (
                            <line key={`h${p}`} x1="0" y1={`${p}%`} x2="100%" y2={`${p}%`} stroke="#60a5fa" strokeWidth="1" />
                        ))}
                        {[20, 40, 60, 80].map(p => (
                            <line key={`v${p}`} x1={`${p}%`} y1="0" x2={`${p}%`} y2="100%" stroke="#60a5fa" strokeWidth="1" />
                        ))}
                    </svg>
                    {/* Mock pins */}
                    {[
                        { x: "35%", y: "42%", color: "#ef4444" },
                        { x: "52%", y: "35%", color: "#3b82f6" },
                        { x: "61%", y: "55%", color: "#3b82f6" },
                        { x: "28%", y: "60%", color: "#22c55e" },
                        { x: "70%", y: "40%", color: "#f59e0b" },
                    ].map((pin, i) => (
                        <div key={i} style={{
                            position: "absolute",
                            left: pin.x,
                            top: pin.y,
                            width: 10,
                            height: 10,
                            borderRadius: "50%",
                            background: pin.color,
                            border: "2px solid white",
                            boxShadow: `0 0 6px ${pin.color}`,
                        }} />
                    ))}
                    <span style={{ color: "#4b5563", fontSize: 12, position: "relative" }}>
                        OpenStreetMap · React-Leaflet
                    </span>
                </div>

                {/* Side panel */}
                <div style={{
                    flex: "0 0 30%",
                    background: "#1f2937",
                    borderLeft: "1px solid #374151",
                    display: "flex",
                    flexDirection: "column",
                    gap: 6,
                    padding: 8,
                }}>
                    {[
                        { label: "Warstwy GIS", color: "#3b82f6" },
                        { label: "Alerty IMGW", color: "#ef4444" },
                        { label: "Jednostki w zasięgu", color: "#22c55e" },
                    ].map(panel => (
                        <div key={panel.label} style={{
                            background: "#111827",
                            borderRadius: 4,
                            padding: "6px 10px",
                            borderLeft: `3px solid ${panel.color}`,
                        }}>
                            <span style={{ fontSize: 11, color: "#9ca3af" }}>{panel.label}</span>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

export function getPreviewCss(): string {
    return "";
}
