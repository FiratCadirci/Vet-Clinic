import { Alert, Button, Group, Modal, Select, SimpleGrid, Stack, Text, Textarea } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { ApiError } from '../../lib/api';
import { useBranch } from '../../lib/branch';
import { dateTime, money } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { invalidateAppointments, useBookingOptions, useVets } from './hooks';
import { cancelAppointment, createAppointment, rescheduleAppointment } from './patientApi';
import { PetSelect, type Option } from './SearchSelect';
import { SlotPicker } from './SlotPicker';
import type { Appointment, PetListItem } from './types';

const today = () => dayjs().format('YYYY-MM-DD');

/** 409 (çakışma) ise satır içi gösterilecek mesaj; diğer hatalar bildirim olarak. */
function useConflict() {
  const [conflict, setConflict] = useState<string | null>(null);
  const handle = (err: unknown, refresh: () => void) => {
    if (err instanceof ApiError && err.status === 409) {
      setConflict(err.message);
      refresh();
    } else notifyError(err);
  };
  return { conflict, setConflict, handle };
}

function ConflictAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <Alert color="orange" icon={<IconAlertTriangle size={18} />} title="Saat artık uygun değil">
      {message.replace(/\.+$/, '')}. Boş saatler yenilendi, lütfen başka bir saat seçin.
    </Alert>
  );
}

// ---------------------------------------------------------------- yeni randevu
interface CreateProps {
  opened: boolean;
  onClose: () => void;
  /** Hasta sayfasından açılınca önceden seçili hasta. */
  pet?: { id: string; label: string };
  /** Takvimde seçili gün. */
  date?: string;
  vetId?: string | null;
}

export function AppointmentFormModal({ opened, onClose, ...rest }: CreateProps) {
  return (
    <Modal opened={opened} onClose={onClose} title="Yeni randevu" size="lg">
      <AppointmentForm onClose={onClose} {...rest} />
    </Modal>
  );
}

function AppointmentForm({ onClose, pet, date, vetId }: Omit<CreateProps, 'opened'>) {
  const qc = useQueryClient();
  const { branchId, branch, allBranches } = useBranch();
  const options = useBookingOptions(branchId);
  const services = { data: options.data?.services.filter((s) => s.durationMinutes), isLoading: options.isLoading };
  const { vets, isLoading: vetsLoading } = useVets(branchId);
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
  const { conflict, setConflict, handle } = useConflict();

  const form = useForm({
    initialValues: {
      petId: pet?.id ?? (null as string | null),
      serviceId: null as string | null,
      vetId: vetId ?? (null as string | null),
      date: date && date >= today() ? date : today(),
      startsAt: null as string | null,
      note: '',
    },
    validate: {
      petId: (v) => (v ? null : 'Hasta seçin'),
      serviceId: (v) => (v ? null : 'Hizmet seçin'),
      vetId: (v) => (v ? null : 'Hekim seçin'),
      date: (v) => (v ? null : 'Tarih seçin'),
      startsAt: (v) => (v ? null : 'Boş saatlerden birini seçin'),
    },
  });
  const resetSlot = () => form.setFieldValue('startsAt', null);

  const create = useMutation({
    mutationFn: (v: typeof form.values) => {
      const chosen = services.data?.find((s) => s.id === v.serviceId);
      return createAppointment(
        {
          animalId: v.petId!,
          veterinarianId: v.vetId!,
          startsAt: v.startsAt!,
          durationMinutes: chosen?.durationMinutes ?? 30,
          // Randevu kaydında hizmet alanı yok; seçilen hizmet ziyaret nedenine yazılır.
          reason: [chosen?.name, v.note.trim()].filter(Boolean).join(' — ') || null,
        },
        branchName,
      );
    },
    onSuccess: (a) => {
      invalidateAppointments(qc, a.petId);
      notifySuccess(`Randevu oluşturuldu: ${dateTime(a.startsAt)}`);
      onClose();
    },
    onError: (err) =>
      handle(err, () => {
        resetSlot();
        qc.invalidateQueries({ queryKey: ['appointments', 'availability'] });
      }),
  });

  const initialPet: Option<PetListItem> | undefined = pet ? { value: pet.id, label: pet.label } : undefined;
  const service = services.data?.find((s) => s.id === form.values.serviceId);

  return (
    <form
      onSubmit={form.onSubmit((v) => {
        if (!branchId) { void notifyError('Önce üst menüden şube seçin'); return; }
        setConflict(null);
        create.mutate(v);
      })}
    >
      <Stack>
        <Text size="sm" c="dimmed">
          Şube: <b>{branch?.name ?? '—'}</b> · Personelin oluşturduğu randevu doğrudan onaylanır.
        </Text>
        <PetSelect
          withAsterisk
          disabled={!!pet}
          initialOption={initialPet}
          value={form.values.petId}
          onChange={(v) => form.setFieldValue('petId', v)}
          error={form.errors.petId}
        />
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <Select
            label="Hizmet"
            withAsterisk
            placeholder={services.isLoading ? 'Yükleniyor…' : 'Hizmet seçin'}
            data={(services.data ?? []).map((s) => ({ value: s.id, label: `${s.name} (${s.durationMinutes} dk)` }))}
            {...form.getInputProps('serviceId')}
            onChange={(v) => {
              form.setFieldValue('serviceId', v);
              resetSlot();
            }}
            description={service ? `Ücret: ${money(Math.round(service.price * 100))}` : undefined}
          />
          <Select
            label="Hekim"
            withAsterisk
            placeholder={vetsLoading ? 'Yükleniyor…' : 'Hekim seçin'}
            nothingFoundMessage="Bu şubede çalışma saati tanımlı hekim yok"
            data={vets.map((v) => ({ value: v.id, label: v.fullName }))}
            {...form.getInputProps('vetId')}
            onChange={(v) => {
              form.setFieldValue('vetId', v);
              resetSlot();
            }}
          />
        </SimpleGrid>
        <DateInput
          label="Tarih"
          withAsterisk
          valueFormat="DD MMMM YYYY, dddd"
          minDate={today()}
          {...form.getInputProps('date')}
          onChange={(v) => {
            form.setFieldValue('date', v ?? today());
            resetSlot();
          }}
        />
        <SlotPicker
          branchId={branchId}
          vetId={form.values.vetId}
          serviceId={form.values.serviceId}
          date={form.values.date}
          value={form.values.startsAt}
          onChange={(v) => {
            setConflict(null);
            form.setFieldValue('startsAt', v);
          }}
          error={form.errors.startsAt as string | undefined}
        />
        <ConflictAlert message={conflict} />
        <Textarea label="Not" autosize minRows={2} {...form.getInputProps('note')} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" loading={create.isPending}>
            Randevu oluştur
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

// ---------------------------------------------------------------- erteleme
export function RescheduleModal({ appointment, onClose }: { appointment: Appointment | null; onClose: () => void }) {
  return (
    <Modal opened={!!appointment} onClose={onClose} title="Randevuyu ertele" size="lg">
      {appointment && <RescheduleForm appointment={appointment} onClose={onClose} />}
    </Modal>
  );
}

function RescheduleForm({ appointment, onClose }: { appointment: Appointment; onClose: () => void }) {
  const qc = useQueryClient();
  const { allBranches } = useBranch();
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
  const { vets } = useVets(appointment.branchId);
  const { conflict, setConflict, handle } = useConflict();
  const [vetId, setVetId] = useState<string | null>(appointment.vetId);
  const [date, setDate] = useState<string>(() => {
    const d = dayjs(appointment.startsAt).format('YYYY-MM-DD');
    return d < today() ? today() : d;
  });
  const [startsAt, setStartsAt] = useState<string | null>(null);

  const vetOptions = vets.some((v) => v.id === appointment.vetId)
    ? vets
    : appointment.vetId
      ? [{ id: appointment.vetId, fullName: appointment.vetName }, ...vets]
      : vets;

  const save = useMutation({
    mutationFn: () => rescheduleAppointment(appointment, startsAt!, vetId, branchName),
    onSuccess: (a) => {
      invalidateAppointments(qc, a.petId);
      notifySuccess(`Randevu ertelendi: ${dateTime(a.startsAt)}`);
      onClose();
    },
    onError: (err) =>
      handle(err, () => {
        setStartsAt(null);
        qc.invalidateQueries({ queryKey: ['appointments', 'availability'] });
      }),
  });

  return (
    <Stack>
      <Text size="sm">
        <b>{appointment.petName}</b> · {appointment.serviceName} · şu an: {dateTime(appointment.startsAt)} ({appointment.vetName})
      </Text>
      <SimpleGrid cols={{ base: 1, sm: 2 }}>
        <Select
          label="Hekim"
          allowDeselect={false}
          data={vetOptions.map((v) => ({ value: v.id, label: v.fullName }))}
          value={vetId}
          onChange={(v) => {
            setVetId(v);
            setStartsAt(null);
          }}
        />
        <DateInput
          label="Yeni tarih"
          valueFormat="DD MMMM YYYY, dddd"
          minDate={today()}
          value={date}
          onChange={(v) => {
            setDate(v ?? today());
            setStartsAt(null);
          }}
        />
      </SimpleGrid>
      <SlotPicker
        branchId={appointment.branchId}
        vetId={vetId}
        serviceId={null}
        date={date}
        excludeAppointmentId={appointment.id}
        value={startsAt}
        onChange={(v) => {
          setConflict(null);
          setStartsAt(v);
        }}
      />
      <ConflictAlert message={conflict} />
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>
          Vazgeç
        </Button>
        <Button disabled={!startsAt} loading={save.isPending} onClick={() => save.mutate()}>
          Ertele
        </Button>
      </Group>
    </Stack>
  );
}

// ---------------------------------------------------------------- iptal
export function CancelAppointmentModal({ appointment, onClose }: { appointment: Appointment | null; onClose: () => void }) {
  const qc = useQueryClient();
  const { allBranches } = useBranch();
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
  const form = useForm({
    initialValues: { reason: '' },
    validate: { reason: (v) => (v.trim().length < 2 ? 'İptal nedeni girin' : null) },
  });
  const cancel = useMutation({
    mutationFn: (reason: string) => cancelAppointment(appointment!.id, reason, branchName),
    onSuccess: (a) => {
      invalidateAppointments(qc, a.petId);
      notifySuccess('Randevu iptal edildi');
      form.reset();
      onClose();
    },
    onError: notifyError,
  });
  return (
    <Modal opened={!!appointment} onClose={onClose} title="Randevuyu iptal et">
      {appointment && (
        <form onSubmit={form.onSubmit((v) => cancel.mutate(v.reason.trim()))}>
          <Stack>
            <Text size="sm">
              <b>{appointment.petName}</b> · {dateTime(appointment.startsAt)} · {appointment.vetName}
            </Text>
            <Textarea
              label="İptal nedeni"
              withAsterisk
              description="Hayvan sahibine bildirimde gösterilir"
              autosize
              minRows={2}
              data-autofocus
              {...form.getInputProps('reason')}
            />
            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                Vazgeç
              </Button>
              <Button type="submit" color="red" loading={cancel.isPending}>
                İptal et
              </Button>
            </Group>
          </Stack>
        </form>
      )}
    </Modal>
  );
}
