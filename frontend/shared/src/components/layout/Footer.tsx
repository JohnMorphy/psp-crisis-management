import importAdminBoundaries from "../../hooks/importAdminBoundaries";
import { useApi } from "../../services/ApiContext";
import { useMapStore } from "../../store/mapStore";

function Footer() {

    const api = useApi();
    const { isPanelCollapsed, togglePanel } = useMapStore();

    return (
        <>
            <footer className="flex items-center gap-4 px-4 py-2 bg-gray-800 border-t border-gray-700 text-sm text-gray-400 shrink-0">
                <button
                onClick={togglePanel}
                className="hover:text-white transition-colors"
                >
                {isPanelCollapsed ? '▶ Rozwiń panel' : '◀ Zwiń panel'}
                </button>
                <button
                onClick={() => importAdminBoundaries(api)}
                className="hover:text-white transition-colors"
                >
                    🗺 Import danych terytorialnych
                </button>
                <button className="hover:text-white transition-colors">🗺 Reset widoku</button>
                <button className="hover:text-white transition-colors">⊕ Kalkulatory</button>
            </footer>
        </>
    )
}

export default Footer;
