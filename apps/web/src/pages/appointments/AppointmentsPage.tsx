import { ActionIcon, Alert, Anchor, Button, Group, Paper, SegmentedControl, Select, Stack, Text } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconBellRinging, IconChevronLeft, IconChevronRight, IconPlus } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { PageHeader } from '../../components/PageHeader';
import { AppointmentBoard, type BoardColumn } from '../../components/clinical/AppointmentBoard';
import { AppointmentDetailModal } from '../../components/clinical/AppointmentDetailModal';
import { AppointmentFormModal, CancelAppointmentModal, RescheduleModal } from '../../components/clinical/AppointmentModals';
import { useVets } from '../../components/clinical/hooks';
import type { Appointment } from '../../components/clinical/types';
import { listAppointments } from '../../components/clinical/patientApi';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { APPOINTMENT_STATUS, dateTime } from '../../lib/format';

type View = 'day' | 'week';

/** Pazartesi başlangıçlı hafta. */
const weekStart = (d: Dayjs) => d.subtract((d.day() + 6) % 7, 'day').startOf('day');
const byStart = (a: Appointment, b: Appointment) => a.startsAt.localeCompare(b.startsAt);

/** Mobil uygulamadan gelen, onay bekleyen (gelecekteki) talepler. */
function PendingRequests({ branchId, onSelect }: { branchId: string; onSelect: (a: Appointment) => void }) {
  const [now] = useState(() => dayjs().startOf('hour').format());
  const { allBranches } = useBranch();
  const { data = [] } = useQuery({
    queryKey: ['appointments', { branchId, status: 'requested', from: now }],
    queryFn: () =>
      listAppointments({ clinicId: branchId, status: 'requested', from: now, size: 50 }, (id) =>
        allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube',
      ),
  });
  if (!data.length) return null;
  return (
    <Alert color="yellow" icon={<IconBellRinging size={18} />} title={`${data.length} randevu talebi onay bekliyor`} mb="md">
      <Group gap="md">
        {data.slice(0, 6).map((a) => (
          <Anchor key={a.id} size="sm" onClick={() => onSelect(a)}>
            {dateTime(a.startsAt)} · {a.petName} ({a.vetName})
          </Anchor>
        ))}
        {data.length > 6 && <Text size="sm">ve {data.length - 6} talep daha</Text>}
      </Group>
    </Alert>
  );
}

export function AppointmentsPage() {
  const { can } = useAuth();
  const { branchId, branch, allBranches } = useBranch();
  const { vets } = useVets(branchId);
  const [view, setView] = useState<View>('day');
  const [day, setDay] = useState(() => dayjs().format('YYYY-MM-DD'));
  const [vetFilter, setVetFilter] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<Appointment | null>(null);
  const [rescheduling, setRescheduling] = useState<Appointment | null>(null);
  const [cancelling, setCancelling] = useState<Appointment | null>(null);

  const from = view === 'day' ? dayjs(day).startOf('day') : weekStart(dayjs(day));
  const to = from.add(view === 'day' ? 1 : 7, 'day');
  const fromIso = from.format();

  const { data = [], isLoading } = useQuery({
    queryKey: ['appointments', { branchId, from: from.format(), to: to.format(), vetFilter, statusFilter }],
    queryFn: () =>
      listAppointments(
        { clinicId: branchId, from: from.format(), to: to.format(), vetId: vetFilter, status: statusFilter },
        (id) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube',
      ),
    enabled: !!branchId,
    placeholderData: keepPreviousData,
  });

  const columns: BoardColumn[] = (() => {
    const sorted = [...data].sort(byStart);
    if (view === 'week') {
      return Array.from({ length: 7 }, (_, i) => {
        const d = dayjs(fromIso).add(i, 'day');
        const key = d.format('YYYY-MM-DD');
        return {
          key,
          title: d.format('dd D MMM'),
          highlight: d.isSame(dayjs(), 'day'),
          items: sorted.filter((a) => dayjs(a.startsAt).format('YYYY-MM-DD') === key),
        };
      });
    }
    // Gün görünümü: hekim başına sütun (çalışma saati olan hekimler + o gün randevusu olanlar).
    const names = new Map(vets.map((v) => [v.id, v.fullName]));
    for (const a of sorted) if (!names.has(a.vetId)) names.set(a.vetId, a.vetName);
    return [...names]
      .filter(([id]) => !vetFilter || id === vetFilter)
      .map(([id, name]) => ({ key: id, title: name, items: sorted.filter((a) => a.vetId === id) }));
  })();

  const shift = (dir: 1 | -1) => setDay(dayjs(day).add(dir, view === 'day' ? 'day' : 'week').format('YYYY-MM-DD'));
  const rangeLabel =
    view === 'day' ? dayjs(day).format('D MMMM YYYY, dddd') : `${from.format('D MMM')} – ${to.subtract(1, 'day').format('D MMM YYYY')}`;
  const activeCount = data.filter((a) => ['requested', 'booked', 'confirmed', 'checked_in', 'in_progress'].includes(a.status)).length;

  const vetOptions = (() => {
    const names = new Map(vets.map((v) => [v.id, v.fullName]));
    for (const a of data) if (!names.has(a.vetId)) names.set(a.vetId, a.vetName);
    return [...names].map(([value, label]) => ({ value, label }));
  })();

  return (
    <>
      <PageHeader
        title="Randevular"
        description={branch ? `${branch.name} randevu takvimi` : undefined}
        actions={
          can('appointments.manage') && (
            <Button leftSection={<IconPlus size={16} />} onClick={() => setCreateOpen(true)}>
              Yeni randevu
            </Button>
          )
        }
      />
      {branchId && <PendingRequests branchId={branchId} onSelect={setSelected} />}

      <Paper withBorder p="md">
        <Stack>
          <Group justify="space-between" wrap="wrap" gap="sm">
            <Group gap="xs" wrap="nowrap">
              <ActionIcon variant="default" size="lg" onClick={() => shift(-1)} aria-label="Önceki">
                <IconChevronLeft size={18} />
              </ActionIcon>
              <Button variant="default" onClick={() => setDay(dayjs().format('YYYY-MM-DD'))}>
                Bugün
              </Button>
              <ActionIcon variant="default" size="lg" onClick={() => shift(1)} aria-label="Sonraki">
                <IconChevronRight size={18} />
              </ActionIcon>
              <DatePickerInput
                value={day}
                onChange={(v) => v && setDay(dayjs(v).format('YYYY-MM-DD'))}
                valueFormat="DD.MM.YYYY"
                w={130}
                aria-label="Tarih"
              />
              <Text fw={600} visibleFrom="md">
                {rangeLabel}
              </Text>
            </Group>
            <Group gap="xs" wrap="wrap">
              <Select
                placeholder="Tüm hekimler"
                clearable
                w={180}
                data={vetOptions}
                value={vetFilter}
                onChange={setVetFilter}
              />
              <Select
                placeholder="Tüm durumlar"
                clearable
                w={160}
                data={Object.entries(APPOINTMENT_STATUS).map(([value, s]) => ({ value, label: s.label }))}
                value={statusFilter}
                onChange={setStatusFilter}
              />
              <SegmentedControl
                value={view}
                onChange={(v) => setView(v as View)}
                data={[
                  { value: 'day', label: 'Gün' },
                  { value: 'week', label: 'Hafta' },
                ]}
              />
            </Group>
          </Group>
          <Text size="sm" c="dimmed">
            {data.length} randevu · {activeCount} aktif
          </Text>
          <AppointmentBoard
            columns={columns}
            loading={isLoading}
            showVet={view === 'week'}
            minColumnWidth={view === 'week' ? 160 : 230}
            onSelect={setSelected}
          />
        </Stack>
      </Paper>

      <AppointmentFormModal
        opened={createOpen}
        onClose={() => setCreateOpen(false)}
        date={day}
        vetId={vetFilter}
      />
      <AppointmentDetailModal
        appointment={selected}
        onClose={() => setSelected(null)}
        onReschedule={(a) => {
          setSelected(null);
          setRescheduling(a);
        }}
        onCancel={(a) => {
          setSelected(null);
          setCancelling(a);
        }}
      />
      <RescheduleModal appointment={rescheduling} onClose={() => setRescheduling(null)} />
      <CancelAppointmentModal appointment={cancelling} onClose={() => setCancelling(null)} />
    </>
  );
}
