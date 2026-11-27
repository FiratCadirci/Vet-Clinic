import { Anchor, Button, Group, Modal, SimpleGrid, Stack, Text } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconCalendarTime, IconCheck, IconStethoscope, IconUserOff, IconX } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useBranch } from '../../lib/branch';
import { useAuth } from '../../lib/auth';
import { SPECIES, dateTime, time } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { invalidateAppointments } from './hooks';
import { appointmentAction } from './patientApi';
import type { Appointment } from './types';
import { AppointmentStatusBadge, InfoRow } from './ui';

interface Props {
  appointment: Appointment | null;
  onClose: () => void;
  onReschedule: (a: Appointment) => void;
  onCancel: (a: Appointment) => void;
}

/** Randevu ayrıntısı ve durum makinesine uygun işlemler (onay, erteleme, iptal, gelmedi, muayene başlat). */
export function AppointmentDetailModal({ appointment: a, onClose, onReschedule, onCancel }: Props) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { can } = useAuth();
  const manage = can('appointments.manage');
  const { allBranches } = useBranch();
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';

  const action = useMutation({
    mutationFn: ({ id, kind }: { id: string; kind: 'confirm' | 'no-show' }) => appointmentAction(id, kind, branchName),
    onSuccess: (updated, { kind }) => {
      invalidateAppointments(qc, updated.petId);
      notifySuccess(kind === 'confirm' ? 'Randevu onaylandı' : 'Randevu "gelmedi" olarak işaretlendi');
      onClose();
    },
    onError: notifyError,
  });

  // Personelin verdiği randevu (BOOKED) onaylı randevu gibi yönetilir; onay bekleyen talep REQUESTED'dır.
  const active = a && (a.status === 'requested' || a.status === 'booked' || a.status === 'confirmed');
  const confirmable = a && (a.status === 'requested' || a.status === 'booked');
  const attended = a && (a.status === 'booked' || a.status === 'confirmed');
  const [openedAt] = useState(() => Date.now());
  const started = a && new Date(a.startsAt).getTime() <= openedAt;

  return (
    <Modal opened={!!a} onClose={onClose} title="Randevu" size="lg">
      {a && (
        <Stack>
          <Group justify="space-between">
            <div>
              <Anchor component={Link} to={`/patients/${a.petId}`} fw={700} size="lg">
                {a.petName}
              </Anchor>
              <Text size="sm" c="dimmed">
                {a.species ? `${SPECIES[a.species]} · ` : ''}
                {a.serviceName}
              </Text>
            </div>
            <AppointmentStatusBadge status={a.status} />
          </Group>
          <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
            <InfoRow label="Zaman">
              {dateTime(a.startsAt)} – {time(a.endsAt)}
            </InfoRow>
            <InfoRow label="Hekim">{a.vetName}</InfoRow>
            <InfoRow label="Sahip">
              <Anchor component={Link} to={`/owners/${a.ownerId}`} size="sm">
                {a.ownerName}
              </Anchor>
              {a.ownerPhone ? ` · ${a.ownerPhone}` : ''}
            </InfoRow>
            <InfoRow label="Şube">{a.branchName}</InfoRow>
            {a.note && <InfoRow label="Not">{a.note}</InfoRow>}
            {a.cancelReason && <InfoRow label="İptal nedeni">{a.cancelReason}</InfoRow>}
          </SimpleGrid>
          {a.status === 'requested' && (
            <Text size="xs" c="yellow.8">
              Bu randevu mobil uygulamadan talep edildi; onaylandığında hayvan sahibine bildirim gider.
            </Text>
          )}

          {manage && active && (
            <Group justify="flex-end" gap="xs">
              <Button variant="light" color="red" leftSection={<IconX size={16} />} onClick={() => onCancel(a)}>
                İptal et
              </Button>
              <Button variant="default" leftSection={<IconCalendarTime size={16} />} onClick={() => onReschedule(a)}>
                Ertele
              </Button>
              {attended && started && (
                <Button
                  variant="default"
                  leftSection={<IconUserOff size={16} />}
                  loading={action.isPending && action.variables?.kind === 'no-show'}
                  onClick={() =>
                    modals.openConfirmModal({
                      title: 'Gelmedi olarak işaretle',
                      children: <Text size="sm">Hasta randevuya gelmedi olarak işaretlenecek.</Text>,
                      labels: { confirm: 'İşaretle', cancel: 'Vazgeç' },
                      confirmProps: { color: 'red' },
                      onConfirm: () => action.mutate({ id: a.id, kind: 'no-show' }),
                    })
                  }
                >
                  Gelmedi
                </Button>
              )}
              {confirmable && (
                <Button
                  color="blue"
                  leftSection={<IconCheck size={16} />}
                  loading={action.isPending && action.variables?.kind === 'confirm'}
                  onClick={() => action.mutate({ id: a.id, kind: 'confirm' })}
                >
                  Onayla
                </Button>
              )}
              {a.status === 'confirmed' && can('clinical.write') && (
                <Button
                  leftSection={<IconStethoscope size={16} />}
                  onClick={() => {
                    onClose();
                    navigate(`/examinations?start=1&appointmentId=${a.id}`);
                  }}
                >
                  Muayene başlat
                </Button>
              )}
            </Group>
          )}
        </Stack>
      )}
    </Modal>
  );
}
