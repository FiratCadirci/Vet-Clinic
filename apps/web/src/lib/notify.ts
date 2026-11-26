import { notifications } from '@mantine/notifications';

export const notifySuccess = (message: string) => notifications.show({ color: 'teal', message });
export const notifyError = (error: unknown) =>
  notifications.show({
    color: 'red',
    title: 'Hata',
    message: error instanceof Error ? error.message : String(error),
  });
