import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  Group,
  NumberInput,
  Paper,
  ScrollArea,
  Skeleton,
  Stack,
  Table,
  Text,
  ThemeIcon,
  Title,
  type NumberInputProps,
} from '@mantine/core';
import { DatePickerInput, type DatePickerInputProps } from '@mantine/dates';
import { IconAlertTriangle, IconCalendar, IconInbox } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import type { Range, usePager } from './utils';

// ------------------------------------------------------------------ para / oran girişleri

/** TL girişi (form değeri TL cinsinden sayı; kayıtta `toKurus` ile kuruşa çevrilir). */
export function MoneyInput(props: NumberInputProps) {
  return (
    <NumberInput
      decimalSeparator=","
      thousandSeparator="."
      decimalScale={2}
      min={0}
      hideControls
      rightSection={
        <Text size="sm" c="dimmed">
          ₺
        </Text>
      }
      {...props}
    />
  );
}

/** Yüzde girişi (form değeri yüzde; kayıtta ×100 ile baz puana çevrilir). */
export function PercentInput(props: NumberInputProps) {
  return (
    <NumberInput
      decimalSeparator=","
      decimalScale={2}
      min={0}
      max={100}
      hideControls
      leftSection={
        <Text size="sm" c="dimmed">
          %
        </Text>
      }
      {...props}
    />
  );
}

// ------------------------------------------------------------------ tablo yardımcıları

export function TableSkeleton({ rows = 5, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <Stack gap="xs" p="md">
      {Array.from({ length: rows }, (_, i) => (
        <Group key={i} gap="md" wrap="nowrap">
          {Array.from({ length: cols }, (_, j) => (
            <Skeleton key={j} h={18} style={{ flex: 1 }} />
          ))}
        </Group>
      ))}
    </Stack>
  );
}

export function Empty({
  title = 'Kayıt bulunamadı',
  description,
  icon,
  children,
}: {
  title?: string;
  description?: ReactNode;
  icon?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <EmptyState py="xl" title={title} description={description} icon={icon ?? <IconInbox size={28} />} withIndicatorBackground>
      {children && <EmptyState.Actions>{children}</EmptyState.Actions>}
    </EmptyState>
  );
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <Alert color="red" icon={<IconAlertTriangle size={18} />} title="Veriler yüklenemedi" m="md">
      {error instanceof Error ? error.message : String(error)}
    </Alert>
  );
}

/**
 * Kaydırılabilir tablo kartı: yükleniyor → iskelet, hata → uyarı, boş → boş durum.
 * `head` başlık hücreleri; `children` satırlar (Table.Tr).
 */
export function DataTable({
  head,
  children,
  loading,
  error,
  empty,
  emptyText,
  minWidth = 700,
  footer,
}: {
  head: ReactNode[];
  children: ReactNode;
  loading?: boolean;
  error?: unknown;
  empty?: boolean;
  emptyText?: string;
  minWidth?: number;
  footer?: ReactNode;
}) {
  return (
    <Paper withBorder radius="md">
      {loading ? (
        <TableSkeleton cols={Math.min(head.length, 6)} />
      ) : error ? (
        <ErrorAlert error={error} />
      ) : empty ? (
        <Empty title={emptyText ?? 'Kayıt bulunamadı'} />
      ) : (
        <ScrollArea type="auto">
          <Table miw={minWidth} verticalSpacing="sm" highlightOnHover tabularNums>
            <Table.Thead>
              <Table.Tr>
                {head.map((h, i) => (
                  <Table.Th key={i}>{h}</Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>{children}</Table.Tbody>
          </Table>
        </ScrollArea>
      )}
      {footer}
    </Paper>
  );
}

export function StatusBadge({
  map,
  value,
}: {
  map: Record<string, { label: string; color: string }>;
  value: string | null | undefined;
}) {
  if (!value) return <Text c="dimmed">—</Text>;
  const s = map[value] ?? { label: value, color: 'gray' };
  return (
    <Badge color={s.color} variant="light">
      {s.label}
    </Badge>
  );
}

// ------------------------------------------------------------------ kartlar

export function StatCard({
  label,
  value,
  hint,
  icon,
  color = 'teal',
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  color?: string;
}) {
  return (
    <Card withBorder radius="md" padding="md">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={4}>
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            {label}
          </Text>
          <Text fw={700} fz={24} lh={1.2}>
            {value}
          </Text>
          {hint && (
            <Text size="xs" c="dimmed">
              {hint}
            </Text>
          )}
        </Stack>
        {icon && (
          <ThemeIcon variant="light" color={color} size="lg" radius="md">
            {icon}
          </ThemeIcon>
        )}
      </Group>
    </Card>
  );
}

export function Section({
  title,
  description,
  actions,
  children,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Stack gap="sm">
      <Group justify="space-between" align="flex-end">
        <Stack gap={0}>
          <Title order={4}>{title}</Title>
          {description && (
            <Text size="sm" c="dimmed">
              {description}
            </Text>
          )}
        </Stack>
        {actions}
      </Group>
      {children}
    </Stack>
  );
}

// ------------------------------------------------------------------ tarih aralığı

export function DateRangeInput({
  value,
  onChange,
  ...rest
}: { value: Range; onChange: (v: Range) => void } & Omit<DatePickerInputProps<'range'>, 'type' | 'value' | 'onChange'>) {
  const today = dayjs();
  return (
    <DatePickerInput
      type="range"
      label="Tarih aralığı"
      placeholder="Tarih seçin"
      valueFormat="DD.MM.YYYY"
      leftSection={<IconCalendar size={16} />}
      clearable
      allowSingleDateInRange
      w={260}
      presets={[
        { value: [today.format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Bugün' },
        { value: [today.subtract(6, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Son 7 gün' },
        { value: [today.subtract(29, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Son 30 gün' },
        { value: [today.startOf('month').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Bu ay' },
        { value: [today.subtract(89, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Son 90 gün' },
        { value: [today.startOf('year').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')], label: 'Bu yıl' },
      ]}
      value={value}
      onChange={(v) => onChange([v[0], v[1]])}
      {...rest}
    />
  );
}

export function PagerFooter({ pager, count }: { pager: ReturnType<typeof usePager>; count: number }) {
  if (pager.page === 0 && count < pager.limit) return null;
  return (
    <Group justify="space-between" p="sm" style={{ borderTop: '1px solid var(--mantine-color-gray-3)' }}>
      <Text size="sm" c="dimmed">
        Sayfa {pager.page + 1}
      </Text>
      <Group gap="xs">
        <Button size="xs" variant="default" disabled={pager.page === 0} onClick={() => pager.setPage(pager.page - 1)}>
          Önceki
        </Button>
        <Button size="xs" variant="default" disabled={count < pager.limit} onClick={() => pager.setPage(pager.page + 1)}>
          Sonraki
        </Button>
      </Group>
    </Group>
  );
}
