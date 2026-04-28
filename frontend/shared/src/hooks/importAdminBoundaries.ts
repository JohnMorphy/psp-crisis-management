import { type AxiosInstance } from "axios";
import { useNotificationStore, NotificationType } from "../store/notificationStore";

// TODO (task 1.12 — WebSocket): Replace this client-side lock with server-pushed status.
// Backend LiveFeedService will push to /topic/system:
//   ADMIN_IMPORT_STARTED   → addNotification(INFO)
//   ADMIN_IMPORT_COMPLETED → addNotification(SUCCESS)
//   ADMIN_IMPORT_FAILED    → addNotification(ERROR)
// useWebSocket hook dispatches those messages, removing the need for optimistic state here.

let isRunning = false;

export default async function importAdminBoundaries(api: AxiosInstance) {
    const { addNotification } = useNotificationStore.getState();

    if (isRunning) {
        addNotification(
            "Import w toku",
            "Import danych terytorialnych już trwa. Poczekaj na zakończenie.",
            NotificationType.WARNING,
            5000
        );
        return;
    } else {
        addNotification(
            "Import rozpoczęty",
            "Pobieranie danych administracyjnych z serwera...",
            NotificationType.INFO,
            5000
        );
    }

    isRunning = true;


    try {
        const response = await api.post("/api/admin-boundaries/import");
        addNotification(
            "Import zakończony",
            "Dane terytorialne zostały pomyślnie zaimportowane.",
            NotificationType.SUCCESS,
            5000
        );
        return response;
    } catch {
        addNotification(
            "Błąd importu",
            "Nie udało się zaimportować danych. Sprawdź połączenie z serwerem.",
            NotificationType.ERROR,
            8000
        );
    } finally {
        isRunning = false;
    }
}
