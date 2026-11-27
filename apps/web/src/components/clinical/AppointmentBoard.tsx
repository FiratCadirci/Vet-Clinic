import { Paper, ScrollArea, Skeleton, Stack, Text, UnstyledButton } from '@mantine/core';
import type { ReactNode } from 'react';
import { APPOINTMENT_STATUS, time } from '../../lib/format';
import type { Appointment } from './types';
import { AppointmentStatusBadge } from './ui';

export interface BoardColumn {
  key: string;
  title: ReactNode;
  highlight?: boolean;
  items: Appointment[];
}

function AppointmentCard({ a, showVet, onClick }: { a: Appointment; showVet: boolean; onClick: () => void }) {
  const color = APPOINTMENT_STATUS[a.status]?.color ?? 'gray';
  const inactive = a.status === 'cancelled' || a.status === 'no_show';
  return (
    <UnstyledButton onClick={onClick} w="100%">
      <Paper
        withBorder
        p={8}
        radius="sm"
        style={{
          borderLeft: `4px solid var(--mantine-color-${color}-6)`,
          opacity: inactive ? 0.55 : 1,
        }}
      >
        <Text size="xs" fw={700}>
          {time(a.startsAt)}–{time(a.endsAt)}
        </Text>
        <Text size="sm" fw={600} lineClamp={1} td={inactive ? 'line-through' : undefined}>
          {a.petName}
        </Text>
        <Text size="xs" c="dimmed" lineClamp={1}>
          {a.serviceName}
          {showVet ? ` · ${a.vetName}` : ''}
        </Text>
        {a.status !== 'confirmed' && (
          <div style={{ marginTop: 4 }}>
            <AppointmentStatusBadge status={a.status} />
          </div>
        )}
      </Paper>
    </UnstyledButton>
  );
}

/** Sütunlu randevu panosu: gün görünümünde hekim başına, hafta görünümünde gün başına bir sütun. */
export function AppointmentBoard({
  columns,
  loading,
  showVet,
  minColumnWidth,
  onSelect,
}: {
  columns: BoardColumn[];
  loading: boolean;
  showVet: boolean;
  minColumnWidth: number;
  onSelect: (a: Appointment) => void;
}) {
  if (!columns.length && !loading) {
    return (
      <Text c="dimmed" ta="center" py="xl">
        Bu şubede gösterilecek hekim yok.
      </Text>
    );
  }
  return (
    <ScrollArea type="auto" offsetScrollbars>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${Math.max(columns.length, 1)}, minmax(${minColumnWidth}px, 1fr))`,
          gap: 'var(--mantine-spacing-sm)',
        }}
      >
        {columns.map((c) => (
          <Paper
            key={c.key}
            withBorder
            p="xs"
            bg={c.highlight ? 'teal.0' : 'white'}
            style={{ minHeight: 260, borderColor: c.highlight ? 'var(--mantine-color-teal-4)' : undefined }}
          >
            <Text fw={600} size="sm" mb="xs" ta="center">
              {c.title}
            </Text>
            <Stack gap={6}>
              {loading ? (
                <>
                  <Skeleton h={64} />
                  <Skeleton h={64} />
                </>
              ) : c.items.length === 0 ? (
                <Text size="xs" c="dimmed" ta="center" py="md">
                  Randevu yok
                </Text>
              ) : (
                c.items.map((a) => <AppointmentCard key={a.id} a={a} showVet={showVet} onClick={() => onSelect(a)} />)
              )}
            </Stack>
          </Paper>
        ))}
      </div>
    </ScrollArea>
  );
}

