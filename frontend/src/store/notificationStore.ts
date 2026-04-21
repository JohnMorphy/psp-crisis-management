import { create } from 'zustand'

export const NotificationType = {
  SUCCESS: 'success',
  ERROR: 'error',
  INFO: 'info',
  WARNING: 'warning',
} as const

export type NotificationType =
  (typeof NotificationType)[keyof typeof NotificationType]

export type Notification = {
    id: string,
    title: string,
    message: string,
    status: NotificationType,
    createdAt: Date,
    duration?: number // ms
}

type notificationStore = {
    notifications: Notification[],
    addNotification: (title: string, message: string, status: NotificationType, duration?: number) => void,   
    removeNotification: (id: string) => void,
    clearNotifications: () => void
}

export const useNotificationStore = create<notificationStore>()((set, get) => ({
        notifications: [],
        addNotification: (title: string, message: string, status: NotificationType, duration?: number) => {
            const notification = {
                id: crypto.randomUUID(),
                title,
                message,
                status,
                createdAt: new Date(),
                duration
            }
                
            set((state) => ({
            notifications: [...state.notifications, notification],
            }))

            if (notification.duration) {
                setTimeout(() => {
                    get().removeNotification(notification.id)
                }, notification.duration)
            }
        },
        removeNotification: (id) => {
            set((state) => ({
            notifications: state.notifications.filter((n) => n.id !== id),
            }))
        },

        clearNotifications: () => {
            set({ notifications: [] })
        },

        
    })
);
