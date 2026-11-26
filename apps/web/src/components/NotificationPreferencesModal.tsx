import { Loader, Modal, Stack, Switch, Table, Text } from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { notifyError } from '../lib/notify';

/** GET/PUT /v1/notifications/preferences: tür × kanal açık/kapalı (kayıt yoksa açık). */
interface Preference {
  type: string;
  label: string;
  channels: { channel: Channel; enabled: boolean }[];
}
type Channel = 'IN_APP' | 'EMAIL' | 'PUSH';

const CHANNELS: { value: Channel; label: string }[] = [
  { value: 'IN_APP', label: 'Uygulama içi' },
  { value: 'EMAIL', label: 'E-posta' },
  { value: 'PUSH', label: 'Push' },
];

/** Bildirim zilinden açılan küçük tercih penceresi (FG-I2). Değişiklik anında kaydedilir. */
export function NotificationPreferencesModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => api.get<Preference[]>('/v1/notifications/preferences'),
    enabled: opened,
  });
  const save = useMutation({
    mutationFn: (p: { type: string; channel: Channel; enabled: boolean }) =>
      api.put<Preference[]>('/v1/notifications/preferences', { preferences: [p] }),
    onSuccess: (data) => qc.setQueryData(['notification-preferences'], data),
    onError: notifyError,
  });
  const prefs = q.data ?? [];
  const used = CHANNELS.filter((c) => prefs.some((p) => p.channels.some((pc) => pc.channel === c.value)));

  return (
    <Modal opened={opened} onClose={onClose} title="Bildirim tercihleri" size="lg">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          Hangi olaylarda hangi kanaldan bildirim almak istediğinizi seçin.
        </Text>
        {q.isLoading ? (
          <Loader size="sm" />
        ) : prefs.length === 0 ? (
          <Text size="sm" c="dimmed">
            Ayarlanabilir bildirim yok
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Olay</Table.Th>
                {used.map((c) => (
                  <Table.Th key={c.value}>{c.label}</Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {prefs.map((p) => (
                <Table.Tr key={p.type}>
                  <Table.Td>{p.label}</Table.Td>
                  {used.map((c) => {
                    const current = p.channels.find((pc) => pc.channel === c.value);
                    return (
                      <Table.Td key={c.value}>
                        {current ? (
                          <Switch
                            aria-label={`${p.label} – ${c.label}`}
                            checked={current.enabled}
                            disabled={save.isPending}
                            onChange={(e) => save.mutate({ type: p.type, channel: c.value, enabled: e.currentTarget.checked })}
                          />
                        ) : (
                          <Text size="xs" c="dimmed">
                            —
                          </Text>
                        )}
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Stack>
    </Modal>
  );
}
