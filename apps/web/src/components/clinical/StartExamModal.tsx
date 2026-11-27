import { Button, Group, Modal, NumberInput, Select, SimpleGrid, Skeleton, Stack, Text, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { dateTime } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { flowFor, startExam } from './examApi';
import { EXAM_KIND, FIELD_LABELS } from './labels';
import { getPetDetail, listAppointments, toAppointment, type AppointmentDto } from './patientApi';
import { PetSelect, type Option } from './SearchSelect';
import type { Appointment, ExamKind, PetListItem } from './types';

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Önceden seçili hasta. */
  petId?: string;
  /** Randevudan başlatılıyorsa (hasta randevudan gelir). */
  appointmentId?: string;
}

/** Muayeneden başlatılabilen randevu durumları (onaylı veya hasta geldi). */
const STARTABLE = ['confirmed', 'checked_in'];

export function StartExamModal({ opened, onClose, petId, appointmentId }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title="Muayene başlat" size="lg">
      <Prefill petId={petId} appointmentId={appointmentId} onClose={onClose} />
    </Modal>
  );
}

function useBranchName() {
  const { allBranches } = useBranch();
  return (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
}

/** Önceden seçili hasta/randevu bilgisini yükler, sonra formu açar. */
function Prefill({ petId, appointmentId, onClose }: Omit<Props, 'opened'>) {
  const branchName = useBranchName();
  const appt = useQuery({
    queryKey: ['appointment', appointmentId],
    queryFn: async () => toAppointment(await api.get<AppointmentDto>(`/v1/appointments/${appointmentId}`), branchName),
    enabled: !!appointmentId,
  });
  const pet = useQuery({
    queryKey: ['pet', petId],
    queryFn: () => getPetDetail(petId!),
    enabled: !!petId && !appointmentId,
  });
  if ((appointmentId && appt.isLoading) || (petId && !appointmentId && pet.isLoading)) {
    return (
      <Stack>
        <Skeleton h={36} />
        <Skeleton h={36} />
        <Skeleton h={80} />
      </Stack>
    );
  }
  let initialPet: Option<PetListItem> | undefined;
  if (appt.data) initialPet = { value: appt.data.petId, label: `${appt.data.petName} — ${appt.data.ownerName}` };
  else if (pet.data) initialPet = { value: pet.data.id, label: `${pet.data.name} — ${pet.data.owner?.fullName ?? ''}` };
  return <StartExamForm initialPet={initialPet} appointment={appt.data} onClose={onClose} />;
}

function StartExamForm({
  initialPet,
  appointment,
  onClose,
}: {
  initialPet?: Option<PetListItem>;
  appointment?: Appointment;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { branchId, branch } = useBranch();
  const branchName = useBranchName();

  const form = useForm({
    initialValues: {
      petId: initialPet?.value ?? (null as string | null),
      appointmentId: appointment?.id ?? (null as string | null),
      kind: (appointment?.serviceName.toLocaleLowerCase('tr').includes('aşı') ? 'vaccination' : 'general') as ExamKind,
      complaint: '',
      anamnesis: '',
      weightKg: '' as number | string,
      temperatureC: '' as number | string,
    },
    validate: {
      petId: (v) => (v ? null : 'Hasta seçin'),
    },
  });

  // Kabul adımı muayene açılırken tamamlanır: türün kabul alanları (ve zorunlulukları) burada girilir.
  const flow = useQuery({ queryKey: ['encounter-flow', form.values.kind], queryFn: () => flowFor(form.values.kind) });
  const intake = flow.data?.steps.find((s) => s.step === 'intake');
  const required = intake?.required ?? [];
  const has = (f: 'complaint' | 'anamnesis' | 'weightKg' | 'temperatureC') => intake?.fields.includes(f) ?? f === 'complaint';

  // Hastanın bu şubedeki muayeneye alınabilir randevuları (isteğe bağlı bağlantı için).
  const appts = useQuery({
    queryKey: ['appointments', { branchId, petId: form.values.petId }],
    queryFn: () => listAppointments({ clinicId: branchId, animalId: form.values.petId!, size: 100 }, branchName),
    enabled: !!branchId && !!form.values.petId,
  });
  const activeAppts = (appts.data ?? []).filter((a) => STARTABLE.includes(a.status));
  if (appointment && !activeAppts.some((a) => a.id === appointment.id)) activeAppts.unshift(appointment);

  const start = useMutation({
    mutationFn: (v: typeof form.values) =>
      startExam({
        petId: v.petId!,
        appointmentId: v.appointmentId,
        kind: v.kind,
        complaint: v.complaint.trim() || null,
        anamnesis: v.anamnesis.trim() || null,
        weightKg: v.weightKg === '' ? null : Number(v.weightKg),
        temperatureC: v.temperatureC === '' ? null : Number(v.temperatureC),
      }),
    onSuccess: (exam, v) => {
      qc.invalidateQueries({ queryKey: ['examinations'] });
      qc.invalidateQueries({ queryKey: ['appointments'] });
      if (v.petId) qc.invalidateQueries({ queryKey: ['pet-history', v.petId] });
      for (const w of exam.warnings ?? []) {
        notifications.show({ color: 'yellow', title: 'Uyarı', message: w, autoClose: 10_000, icon: <IconAlertTriangle size={16} /> });
      }
      notifySuccess('Muayene başlatıldı');
      onClose();
      navigate(`/examinations/${exam.id}`);
    },
    onError: notifyError,
  });

  const submit = form.onSubmit((v) => {
    if (!branchId) {
      void notifyError('Önce üst menüden şube seçin');
      return;
    }
    const values = v as unknown as Record<string, unknown>;
    // Randevudan başlatılırken şikâyet boşsa backend randevu nedenini kullanır.
    const reason = activeAppts.find((a) => a.id === v.appointmentId)?.note;
    const missing = required.find((f) => !(f === 'complaint' && reason) && String(values[f] ?? '').trim() === '');
    if (missing) {
      form.setFieldError(missing, `${FIELD_LABELS[missing]} zorunlu`);
      return;
    }
    start.mutate(v);
  });

  return (
    <form onSubmit={submit}>
      <Stack>
        <Text size="sm" c="dimmed">
          Şube: <b>{branch?.name ?? '—'}</b>
        </Text>
        <PetSelect
          withAsterisk
          disabled={!!appointment}
          initialOption={initialPet}
          value={form.values.petId}
          onChange={(v) => {
            form.setFieldValue('petId', v);
            form.setFieldValue('appointmentId', null);
          }}
          error={form.errors.petId}
        />
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <Select
            label="Muayene türü"
            description="Türe göre adımlar ve zorunlu alanlar değişir"
            allowDeselect={false}
            data={Object.entries(EXAM_KIND).map(([value, k]) => ({ value, label: k.label }))}
            {...form.getInputProps('kind')}
          />
          {/* Muayene, giriş yapan hekimin profiliyle açılır. */}
          <Select label="Hekim" data={[{ value: 'me', label: user?.fullName ?? '' }]} value="me" disabled />
        </SimpleGrid>
        <Select
          label="Bağlı randevu"
          description="Muayene kapatılınca randevu 'tamamlandı' olur"
          placeholder={activeAppts.length ? 'Randevu seçin (isteğe bağlı)' : 'Aktif randevu yok'}
          clearable
          disabled={!form.values.petId || !!appointment}
          data={activeAppts.map((a) => ({ value: a.id, label: `${dateTime(a.startsAt)} · ${a.serviceName} · ${a.vetName}` }))}
          {...form.getInputProps('appointmentId')}
        />
        {(has('weightKg') || has('temperatureC')) && (
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            {has('weightKg') && (
              <NumberInput
                label={FIELD_LABELS.weightKg}
                withAsterisk={required.includes('weightKg')}
                decimalSeparator=","
                decimalScale={2}
                min={0.01}
                max={2000}
                {...form.getInputProps('weightKg')}
              />
            )}
            {has('temperatureC') && (
              <NumberInput
                label={FIELD_LABELS.temperatureC}
                withAsterisk={required.includes('temperatureC')}
                decimalSeparator=","
                decimalScale={1}
                min={25}
                max={46}
                {...form.getInputProps('temperatureC')}
              />
            )}
          </SimpleGrid>
        )}
        <Textarea
          label="Şikayet"
          withAsterisk={required.includes('complaint')}
          autosize
          minRows={2}
          placeholder={appointment?.note ? `Boşsa randevu nedeni: ${appointment.note}` : undefined}
          {...form.getInputProps('complaint')}
        />
        {has('anamnesis') && (
          <Textarea label={FIELD_LABELS.anamnesis} withAsterisk={required.includes('anamnesis')} autosize minRows={2} {...form.getInputProps('anamnesis')} />
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" loading={start.isPending}>
            Başlat
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
