import { Chip, Group, Input, Skeleton, Text } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { AvailabilityDto } from './patientApi';

interface Props {
  branchId: string | null;
  vetId: string | null;
  serviceId: string | null;
  /** "YYYY-MM-DD" (klinik yerel tarihi). */
  date: string | null;
  /** Seçili slotun başlangıcı (ISO). */
  value: string | null;
  onChange: (startsAt: string | null) => void;
  error?: string;
  /** Ertelemede randevunun kendi saati boş sayılır; hizmet verilmezse süre bu randevunun süresidir. */
  excludeAppointmentId?: string;
}

/** `GET /v1/appointments/availability` boş slotlarını tıklanabilir çipler olarak gösterir. */
export function SlotPicker({ branchId, vetId, serviceId, date, value, onChange, error, excludeAppointmentId }: Props) {
  const ready = !!(branchId && vetId && (serviceId || excludeAppointmentId) && date);
  const { data, isLoading, isError } = useQuery({
    queryKey: ['appointments', 'availability', { branchId, vetId, serviceId, date, excludeAppointmentId }],
    queryFn: () =>
      api.get<AvailabilityDto>('/v1/appointments/availability', {
        clinicId: branchId,
        veterinarianId: vetId,
        serviceId: serviceId || undefined,
        date,
        excludeAppointmentId,
      }),
    enabled: ready,
    staleTime: 0,
  });

  let content;
  if (!ready) content = <Text size="sm" c="dimmed">Boş saatleri görmek için hizmet, hekim ve tarih seçin.</Text>;
  else if (isLoading)
    content = (
      <Group gap="xs">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} h={28} w={64} radius="xl" />
        ))}
      </Group>
    );
  else if (isError) content = <Text size="sm" c="red">Boş saatler alınamadı.</Text>;
  else if (!data?.slots.length)
    content = <Text size="sm" c="dimmed">Bu gün için boş saat yok (hekim çalışmıyor ya da tüm saatler dolu).</Text>;
  else
    content = (
      <Chip.Group multiple={false} value={value} onChange={(v) => onChange(v || null)}>
        <Group gap="xs">
          {data.slots.map((s) => (
            <Chip key={s.startsAt} value={s.startsAt} size="sm" variant="outline">
              {s.time}
            </Chip>
          ))}
        </Group>
      </Chip.Group>
    );

  return (
    <Input.Wrapper
      label="Boş saatler"
      withAsterisk
      description={data ? `Süre: ${data.durationMinutes} dk` : undefined}
      error={error}
    >
      <div style={{ marginTop: 6 }}>{content}</div>
    </Input.Wrapper>
  );
}
