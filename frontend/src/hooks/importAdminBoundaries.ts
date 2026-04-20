import api from "../services/api";

export default function importAdminBoundaries() {
    return api.post("/api/admin-boundaries/import");
}