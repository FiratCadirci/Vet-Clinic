import { Badge, Center, Paper, Skeleton, Stack, Table, Text, Title, type PaperProps } from '@mantine/core';
import type { ReactNode } from 'react';
import { APPOINTMENT_STATUS, EXAM_STATUS, SPECIES } from '../../lib/format';
import { EXAM_KIND, EXAM_STATUS_COLOR } from './labels';
import type { ExamKind } from './types';

export function AppointmentStatusBadge({ status }: { status: string }) {
  const s = APPOINTMENT_STATUS[status] ?? { label: status, color: 'gray' };
  return (
    <Badge color={s.color} variant="light">
      {s.label}
    </Badge>
  );
}

export function ExamStatusBadge({ status }: { status: string }) {
  return (
    <Badge color={EXAM_STATUS_COLOR[status] ?? 'gray'} variant="light">
      {EXAM_STATUS[status] ?? status}
    </Badge>
  );
}

export function ExamKindBadge({ kind }: { kind: ExamKind }) {
  const k = EXAM_KIND[kind];
  return (
    <Badge color={k.color} variant="outline">
      {k.label}
    </Badge>
  );
}

export function SpeciesBadge({ species }: { species: string }) {
  return (
    <Badge color="gray" variant="light" size="sm">
      {SPECIES[species] ?? species}
    </Badge>
  );
}

/** Başlıklı kart. */
export function Section({ title, actions, children, ...rest }: { title: ReactNode; actions?: ReactNode; children: ReactNode } & PaperProps) {
  return (
    <Paper withBorder p="md" {...rest}>
      <Stack gap="sm">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <Title order={4}>{title}</Title>
          {actions}
        </div>
        {children}
      </Stack>
    </Paper>
  );
}

export function EmptyText({ children }: { children: ReactNode }) {
  return (
    <Center py="lg">
      <Text c="dimmed" size="sm" ta="center">
        {children}
      </Text>
    </Center>
  );
}

/** Tablo yüklenirken satır iskeletleri. */
export function SkeletonRows({ cols, rows = 5 }: { cols: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }, (_, r) => (
        <Table.Tr key={r}>
          {Array.from({ length: cols }, (_, c) => (
            <Table.Td key={c}>
              <Skeleton h={14} />
            </Table.Td>
          ))}
        </Table.Tr>
      ))}
    </>
  );
}

/** Tabloda boş durum satırı. */
export function EmptyRow({ cols, children }: { cols: number; children: ReactNode }) {
  return (
    <Table.Tr>
      <Table.Td colSpan={cols}>
        <EmptyText>{children}</EmptyText>
      </Table.Td>
    </Table.Tr>
  );
}

export function SkeletonBlock({ h = 120 }: { h?: number }) {
  return (
    <Stack gap="sm">
      <Skeleton h={20} w="40%" />
      <Skeleton h={h} />
    </Stack>
  );
}

/** Bilgi listesi satırı: etiket + değer. */
export function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      {/* div: değer rozet gibi blok öğe içerebilir (<p> içinde <div> geçersiz HTML) */}
      <Text size="sm" component="div">
        {children || '—'}
      </Text>
    </div>
  );
}
