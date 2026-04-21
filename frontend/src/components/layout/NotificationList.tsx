import { useNotificationStore, NotificationType } from "../../store/notificationStore"

const bgByType: Record<NotificationType, string> = {
    success: 'bg-green-900 border-green-600',
    error:   'bg-red-900 border-red-600',
    info:    'bg-blue-900 border-blue-600',
    warning: 'bg-yellow-900 border-yellow-600',
}

const titleByType: Record<NotificationType, string> = {
    success: 'text-green-300',
    error:   'text-red-300',
    info:    'text-blue-300',
    warning: 'text-yellow-300',
}

export default function NotificationList() {
    const { notifications, removeNotification } = useNotificationStore();

    return (
        <div className="absolute top-5 self-center flex flex-col gap-2 z-[1001] w-80 pointer-events-none">
            {notifications.map((notification) => (
                <div
                    key={notification.id}
                    className={`flex opacity-75 items-start justify-between gap-3 px-4 py-3 rounded-lg border ${bgByType[notification.status]} pointer-events-auto shadow-lg`}
                >
                    <div className="flex flex-col gap-0.5 min-w-0">
                        <span className={`font-semibold text-sm ${titleByType[notification.status]}`}>
                            {notification.title}
                        </span>
                        <span className="text-gray-300 text-sm">
                            {notification.message}
                        </span>
                    </div>
                    <button
                        onClick={() => removeNotification(notification.id)}
                        className="text-gray-400 hover:text-white transition-colors shrink-0 text-lg leading-none mt-0.5"
                    >
                        ×
                    </button>
                </div>
            ))}
        </div>
    )
}