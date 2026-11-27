import { Alert, Anchor, Badge, Button, Grid, Group, Paper, SimpleGrid, Stack, Tabs, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconCalendarPlus,
  IconDeviceMobile,
  IconEdit,
  IconHistory,
  IconStethoscope,
  IconVaccine,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { AppointmentFormModal } from '../../components/clinical/AppointmentModals';
import { PetFormModal } from '../../components/clinical/PetFormModal';
import { PetHistoryTimeline } from '../../components/clinical/PetHistoryTimeline';
import { StartExamModal } from '../../components/clinical/StartExamModal';
import { getPetDetail, getPetHistory } from '../../components/clinical/patientApi';
import type { PetDetail } from '../../components/clinical/types';
import { InfoRow, Section, SkeletonBlock, SpeciesBadge } from '../../components/clinical/ui';
import { VaccinationModal } from '../../components/clinical/VaccinationModal';
import { VaccineScheduleView } from '../../components/clinical/VaccineScheduleView';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { SEX, ageFrom, date } from '../../lib/format';

function PetCard({ pet }: { pet: PetDetail }) {
  const { allBranches } = useBranch();
  return (
    <Section title="Hasta bilgileri">
      <SimpleGrid cols={2} spacing="sm">
        <InfoRow label="Tür">
          <SpeciesBadge species={pet.species} />
        </InfoRow>
        <InfoRow label="Irk">{pet.breed}</InfoRow>
        <InfoRow label="Cinsiyet">{SEX[pet.sex]}</InfoRow>
        <InfoRow label="Doğum tarihi">{pet.birthDate ? `${date(pet.birthDate)} (${ageFrom(pet.birthDate)})` : null}</InfoRow>
        <InfoRow label="Mikroçip">{pet.microchipNo}</InfoRow>
        <InfoRow label="Kayıt şubesi">{allBranches.find((b) => b.id === pet.branchId)?.name ?? 'Diğer şube'}</InfoRow>
      </SimpleGrid>
      {pet.allergies && (
        <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />} title="Alerjiler" p="xs">
          {pet.allergies}
        </Alert>
      )}
      {pet.healthNotes && <InfoRow label="Sağlık notları">{pet.healthNotes}</InfoRow>}
    </Section>
  );
}

function OwnerCard({ owner }: { owner: NonNullable<PetDetail['owner']> }) {
  return (
    <Section
      title="Sahip"
      actions={
        owner.userId && (
          <Badge color="teal" variant="light" leftSection={<IconDeviceMobile size={12} />}>
            Mobil hesap bağlı
          </Badge>
        )
      }
    >
      <Anchor component={Link} to={`/owners/${owner.id}`} fw={600}>
        {owner.fullName}
      </Anchor>
      <SimpleGrid cols={2} spacing="sm">
        <InfoRow label="Telefon">{owner.phone}</InfoRow>
        <InfoRow label="E-posta">{owner.email}</InfoRow>
      </SimpleGrid>
      {owner.address && <InfoRow label="Adres">{owner.address}</InfoRow>}
    </Section>
  );
}

export function PatientDetailPage() {
  const { id = '' } = useParams();
  const { can } = useAuth();
  const [editOpen, edit] = useDisclosure();
  const [examOpen, exam] = useDisclosure();
  const [apptOpen, appt] = useDisclosure();
  const [vaccine, setVaccine] = useState<{ templateItemId?: string; vaccineName?: string } | null>(null);

  const pet = useQuery({ queryKey: ['pet', id], queryFn: () => getPetDetail(id) });
  const history = useQuery({
    queryKey: ['pet-history', id],
    queryFn: () => getPetHistory(pet.data!),
    enabled: !!pet.data,
  });

  if (pet.isError) {
    return (
      <Alert color="red" title="Hasta açılamadı">
        {pet.error.message}
        <br />
        <Anchor component={Link} to="/patients">
          Hasta listesine dön
        </Anchor>
      </Alert>
    );
  }
  if (!pet.data) return <SkeletonBlock h={300} />;
  const p = pet.data;
  const active = !p.deceased;

  return (
    <>
      <Anchor component={Link} to="/patients" size="sm" c="dimmed">
        <Group gap={4}>
          <IconArrowLeft size={14} /> Hastalar
        </Group>
      </Anchor>
      <PageHeader
        title={p.name}
        description={p.owner ? `Sahibi: ${p.owner.fullName}` : undefined}
        actions={
          <>
            {can('clients.write') && (
              <Button variant="default" leftSection={<IconEdit size={16} />} onClick={edit.open}>
                Düzenle
              </Button>
            )}
            {can('appointments.manage') && active && (
              <Button variant="default" leftSection={<IconCalendarPlus size={16} />} onClick={appt.open}>
                Yeni randevu
              </Button>
            )}
            {can('clinical.write') && active && (
              <Button variant="light" color="grape" leftSection={<IconVaccine size={16} />} onClick={() => setVaccine({})}>
                Aşı uygula
              </Button>
            )}
            {can('clinical.write') && active && (
              <Button leftSection={<IconStethoscope size={16} />} onClick={exam.open}>
                Muayene başlat
              </Button>
            )}
          </>
        }
      />
      {p.deceased && (
        <Alert color="gray" mb="md">
          Bu hasta vefat etmiş olarak kayıtlı; yeni randevu, muayene veya aşı kaydı açılamaz.
        </Alert>
      )}

      <Grid>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Stack>
            <PetCard pet={p} />
            {p.owner && <OwnerCard owner={p.owner} />}
          </Stack>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Paper withBorder p="md">
            <Tabs defaultValue="history">
              <Tabs.List mb="md">
                <Tabs.Tab value="history" leftSection={<IconHistory size={16} />}>
                  Sağlık geçmişi
                </Tabs.Tab>
                <Tabs.Tab value="vaccines" leftSection={<IconVaccine size={16} />}>
                  Aşı takvimi
                </Tabs.Tab>
              </Tabs.List>
              <Tabs.Panel value="history">
                {history.data ? (
                  <PetHistoryTimeline history={history.data} />
                ) : history.isError ? (
                  <Text c="red" size="sm">
                    {history.error.message}
                  </Text>
                ) : (
                  <SkeletonBlock />
                )}
              </Tabs.Panel>
              <Tabs.Panel value="vaccines">
                <VaccineScheduleView
                  petId={p.id}
                  onVaccinate={can('clinical.write') && active ? (item) => setVaccine(item) : undefined}
                />
              </Tabs.Panel>
            </Tabs>
          </Paper>
        </Grid.Col>
      </Grid>

      <PetFormModal opened={editOpen} onClose={edit.close} pet={p} />
      <StartExamModal opened={examOpen} onClose={exam.close} petId={p.id} />
      <AppointmentFormModal
        opened={apptOpen}
        onClose={appt.close}
        pet={{ id: p.id, label: `${p.name} — ${p.owner?.fullName ?? ''}` }}
      />
      <VaccinationModal
        opened={!!vaccine}
        onClose={() => setVaccine(null)}
        pet={{ id: p.id, name: p.name, species: p.species }}
        templateItemId={vaccine?.templateItemId}
        vaccineName={vaccine?.vaccineName}
      />
    </>
  );
}
