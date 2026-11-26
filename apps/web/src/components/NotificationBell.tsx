import { ActionIcon, Group, Indicator, Popover, ScrollArea, Stack, Text, UnstyledButton } from '@mantine/core';
import { IconBell, IconSettings } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../lib/api';
import { dateTime } from '../lib/format';
import { NotificationPreferencesModal } from './NotificationPreferencesModal';

/** GET /v1/notifications: oturumdaki kullanıcının uygulama içi bildirimleri (yeniden eskiye). */
interface Notification {
  id: string;
  type: string;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
}

export function NotificationBell() {
  const qc = useQueryClient();
  const [prefsOpen, setPrefsOpen] = useState(false);
  const { data } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<Page<Notification>>('/v1/notifications', { size: 20 }),
    refetchInterval: 60_000,
    retry: false,
  });
  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api.get<{ unreadCount: number }>('/v1/notifications/unread-count'),
    refetchInterval: 60_000,
    retry: false,
  });
  const readAll = useMutation({
    mutationFn: () => api.post('/v1/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
  const items = data?.content ?? [];
  const unread = unreadData?.unreadCount ?? items.filter((n) => !n.readAt).length;

  return (
    <>
      <Popover width={340} position="bottom-end" shadow="md">
        <Popover.Target>
          <Indicator disabled={!unread} label={unread} size={16} color="red">
            <ActionIcon variant="subtle" color="gray" size="lg" aria-label="Bildirimler">
              <IconBell size={20} />
            </ActionIcon>
          </Indicator>
        </Popover.Target>
        <Popover.Dropdown>
          <Stack gap="xs">
            <Group justify="space-between">
              <Text fw={600}>Bildirimler</Text>
              <ActionIcon
                variant="subtle"
                color="gray"
                size="sm"
                aria-label="Bildirim tercihleri"
                title="Bildirim tercihleri"
                onClick={() => setPrefsOpen(true)}
              >
                <IconSettings size={16} />
              </ActionIcon>
            </Group>
            {items.length === 0 && (
              <Text size="sm" c="dimmed">
                Bildirim yok
              </Text>
            )}
            <ScrollArea.Autosize mah={360}>
              <Stack gap={8}>
                {items.map((n) => (
                  <div key={n.id} style={{ opacity: n.readAt ? 0.6 : 1 }}>
                    <Text size="sm" fw={500}>
                      {n.title}
                    </Text>
                    <Text size="xs">{n.body}</Text>
                    <Text size="xs" c="dimmed">
                      {dateTime(n.createdAt)}
                    </Text>
                  </div>
                ))}
              </Stack>
            </ScrollArea.Autosize>
            {unread > 0 && (
              <UnstyledButton onClick={() => readAll.mutate()}>
                <Text size="xs" c="teal">
                  Tümünü okundu işaretle
                </Text>
              </UnstyledButton>
            )}
          </Stack>
        </Popover.Dropdown>
      </Popover>
      <NotificationPreferencesModal opened={prefsOpen} onClose={() => setPrefsOpen(false)} />
    </>
  );
}
