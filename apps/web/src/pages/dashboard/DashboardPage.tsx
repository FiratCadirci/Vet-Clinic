import { Anchor, Badge, Button, Grid, Group, Paper, Progress, SimpleGrid, Skeleton, Stack, Table, Text, ThemeIcon } from '@mantine/core';
import {
  IconAlertTriangle,
  IconCalendarEvent,
  IconCalendarPlus,
  IconChartBar,
  IconPackage,
  IconReceipt,
  IconStethoscope,
  IconVaccine,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { AppointmentFormModal } from '../../components/clinical/AppointmentModals';
import { APPOINTMENT_TYPE_LABEL, statusKey } from '../../components/clinical/patientApi';
import { AppointmentStatusBadge, EmptyRow, EmptyText, ExamStatusBadge, Section, SkeletonRows } from '../../components/clinical/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { date, dateTime, money, time } from '../../lib/format';

// ---------------------------------------------------------------- GET /v1/dashboard
/** Seçili şubenin (X-Clinic-Id) genel bakışı; yetkisi olmayan bölüm null gelir. Para TL. */
interface Dashboard {
  date: string;
  clinicId: string;
  kpis: {
    examinations: number;
    appointments: Record<string, number>;
    occupancyRate: number;
    overdueVaccinations: number;
    sales: { count: number; total: number; paid: number };
  } | null;
  todayAppointments: {
    id: string;
    startsAt: string;
    endsAt: string;
    animalId: string;
    animalName: string;
    clientName: string;
    appointmentType: string;
    vetName: string | null;
    status: string;
  }[] | null;
  openEncounters: {
    id: string;
    animalId: string;
    animalName: string;
    vetName: string | null;
    status: string;
    startedAt: string;
  }[] | null;
  dueVaccinations: {
    vaccinationId: string;
    animalId: string;
    animalName: string;
    clientName: string | null;
    clientPhone: string | null;
    vaccineName: string;
    nextDueAt: string;
    overdue: boolean;
  }[] | null;
  stockAlerts: {
    lowStock: { productId: string; name: string; onHand: number; minimumStock: number }[];
    expiring: { lotId: string | null; productId: string }[];
  } | null;
}

/** Muayene durum kodu → ekran anahtarı (ExamStatusBadge). */
const EXAM_STATUS_KEY: Record<string, string> = {
  ADMISSION: 'intake',
  EXAMINATION: 'examination',
  DIAGNOSIS: 'diagnosis',
  PLAN: 'plan',
  CLOSED: 'closed',
};
const kurus = (tl: number) => Math.round(tl * 100);

// ---------------------------------------------------------------- KPI
function StatTile({ label, value, hint, icon, children }: { label: string; value: ReactNode; hint?: ReactNode; icon: ReactNode; children?: ReactNode }) {
  return (
    <Paper withBorder p="md">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={2}>
          <Text size="sm" c="dimmed">
            {label}
          </Text>
          <Text fz={26} fw={600} lh={1.2}>
            {value}
          </Text>
        </Stack>
        <ThemeIcon variant="light" color="gray" size="lg" radius="md">
          {icon}
        </ThemeIcon>
      </Group>
      {children}
      {hint && (
        <Text size="xs" c="dimmed" mt={6}>
          {hint}
        </Text>
      )}
    </Paper>
  );
}

function Kpis({ data, isLoading }: { data: Dashboard['kpis'] | undefined; isLoading: boolean }) {
  if (isLoading || !data) {
    return (
      <SimpleGrid cols={{ base: 1, xs: 2, md: 5 }} mb="md">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} h={104} />
        ))}
      </SimpleGrid>
    );
  }
  const a = data.appointments;
  const totalAppts = Object.values(a).reduce((s, n) => s + (n ?? 0), 0);
  const fmt = (n: number) => n.toLocaleString('tr-TR');

  return (
    <SimpleGrid cols={{ base: 1, xs: 2, md: 5 }} mb="md">
      <StatTile label="Muayene" value={fmt(data.examinations)} hint="Son 30 gün" icon={<IconStethoscope size={18} />} />
      <StatTile
        label="Randevu"
        value={fmt(totalAppts)}
        hint={`${fmt(a.COMPLETED ?? 0)} tamamlandı · ${fmt(a.NO_SHOW ?? 0)} gelmedi`}
        icon={<IconCalendarEvent size={18} />}
      />
      <StatTile label="Hekim doluluğu" value={`%${data.occupancyRate.toLocaleString('tr-TR')}`} icon={<IconChartBar size={18} />}>
        <Progress
          value={Math.min(100, data.occupancyRate)}
          color="teal.6"
          bg="teal.1"
          size="sm"
          mt="xs"
          aria-label="Doluluk oranı"
        />
      </StatTile>
      <StatTile
        label="Geciken aşı"
        value={fmt(data.overdueVaccinations)}
        hint={
          data.overdueVaccinations > 0 ? (
            <Group gap={4} component="span">
              <IconAlertTriangle size={12} color="var(--mantine-color-orange-6)" /> Takip gerekiyor
            </Group>
          ) : (
            'Geciken aşı yok'
          )
        }
        icon={<IconVaccine size={18} />}
      />
      <StatTile
        label="Satış"
        value={money(kurus(data.sales.total))}
        hint={`${fmt(data.sales.count)} fatura · tahsil: ${money(kurus(data.sales.paid))}`}
        icon={<IconReceipt size={18} />}
      />
    </SimpleGrid>
  );
}

// ---------------------------------------------------------------- bölümler
function TodayAppointments({ data, isLoading }: { data: Dashboard['todayAppointments'] | undefined; isLoading: boolean }) {
  const navigate = useNavigate();
  const active = data ?? [];

  return (
    <Section
      title={`Bugünkü randevular (${active.length})`}
      actions={
        <Anchor component={Link} to="/appointments" size="sm">
          Takvime git
        </Anchor>
      }
    >
      <Table.ScrollContainer minWidth={520}>
        <Table highlightOnHover verticalSpacing="xs">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Saat</Table.Th>
              <Table.Th>Hasta</Table.Th>
              <Table.Th>Hizmet</Table.Th>
              <Table.Th>Hekim</Table.Th>
              <Table.Th>Durum</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {isLoading ? (
              <SkeletonRows cols={5} rows={4} />
            ) : active.length === 0 ? (
              <EmptyRow cols={5}>Bugün için randevu yok</EmptyRow>
            ) : (
              active.map((a) => (
                <Table.Tr key={a.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/patients/${a.animalId}`)}>
                  <Table.Td fw={600}>{time(a.startsAt)}</Table.Td>
                  <Table.Td>
                    <Text size="sm">{a.animalName}</Text>
                    <Text size="xs" c="dimmed">
                      {a.clientName}
                    </Text>
                  </Table.Td>
                  <Table.Td>{APPOINTMENT_TYPE_LABEL[a.appointmentType] ?? a.appointmentType}</Table.Td>
                  <Table.Td>{a.vetName ?? 'Hekim atanmamış'}</Table.Td>
                  <Table.Td>
                    <AppointmentStatusBadge status={statusKey(a.status)} />
                  </Table.Td>
                </Table.Tr>
              ))
            )}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
    </Section>
  );
}

function OpenExams({ data, isLoading }: { data: Dashboard['openEncounters'] | undefined; isLoading: boolean }) {
  const open = data ?? [];
  return (
    <Section
      title={`Açık muayeneler (${open.length})`}
      actions={
        <Anchor component={Link} to="/examinations" size="sm">
          Tümü
        </Anchor>
      }
    >
      {isLoading ? (
        <Skeleton h={80} />
      ) : open.length === 0 ? (
        <EmptyText>Açık muayene yok</EmptyText>
      ) : (
        <Stack gap={6}>
          {open.slice(0, 6).map((e) => (
            <Group key={e.id} justify="space-between" wrap="nowrap">
              <div>
                <Anchor component={Link} to={`/examinations/${e.id}`} size="sm" fw={600}>
                  {e.animalName}
                </Anchor>
                <Text size="xs" c="dimmed">
                  {e.vetName ?? '—'} · {dateTime(e.startedAt)}
                </Text>
              </div>
              <ExamStatusBadge status={EXAM_STATUS_KEY[e.status] ?? e.status} />
            </Group>
          ))}
        </Stack>
      )}
    </Section>
  );
}

function DueVaccines({ data, isLoading }: { data: Dashboard['dueVaccinations'] | undefined; isLoading: boolean }) {
  const due = data ?? [];
  return (
    <Section
      title={`Yaklaşan / geciken aşılar (${due.length})`}
      actions={
        <Anchor component={Link} to="/vaccines" size="sm">
          Tümü
        </Anchor>
      }
    >
      {isLoading ? (
        <Skeleton h={80} />
      ) : due.length === 0 ? (
        <EmptyText>Önümüzdeki 14 günde aşı yok</EmptyText>
      ) : (
        <Stack gap={6}>
          {due.slice(0, 7).map((d) => (
            <Group key={d.vaccinationId} justify="space-between" wrap="nowrap">
              <div>
                <Anchor component={Link} to={`/patients/${d.animalId}`} size="sm" fw={600}>
                  {d.animalName}
                </Anchor>
                <Text size="xs" c="dimmed">
                  {d.vaccineName} · {d.clientName ?? ''} {d.clientPhone ?? ''}
                </Text>
              </div>
              <Badge color={d.overdue ? 'red' : 'yellow'} variant="light" size="sm" style={{ flexShrink: 0 }}>
                {`${d.overdue ? 'Gecikti · ' : ''}${date(d.nextDueAt)}`}
              </Badge>
            </Group>
          ))}
        </Stack>
      )}
    </Section>
  );
}

function StockWarnings({ data, isLoading }: { data: Dashboard['stockAlerts'] | undefined; isLoading: boolean }) {
  const low = data?.lowStock ?? [];
  const expiring = data?.expiring ?? [];
  return (
    <Section
      title="Stok uyarıları"
      actions={
        <Anchor component={Link} to="/inventory" size="sm">
          Stoğa git
        </Anchor>
      }
    >
      {isLoading ? (
        <Skeleton h={60} />
      ) : low.length === 0 && expiring.length === 0 ? (
        <EmptyText>Kritik stok yok</EmptyText>
      ) : (
        <Stack gap={6}>
          {low.slice(0, 5).map((s) => (
            <Group key={s.productId} justify="space-between" wrap="nowrap">
              <Group gap={6} wrap="nowrap">
                <IconPackage size={16} color="var(--mantine-color-orange-6)" />
                <Text size="sm">{s.name}</Text>
              </Group>
              <Badge color="orange" variant="light" size="sm">
                {s.onHand.toLocaleString('tr-TR')} / min {s.minimumStock.toLocaleString('tr-TR')}
              </Badge>
            </Group>
          ))}
          {expiring.length > 0 && (
            <Group gap={6}>
              <IconAlertTriangle size={14} color="var(--mantine-color-red-6)" />
              <Text size="xs" c="dimmed">
                {expiring.length} partinin son kullanma tarihi yaklaşıyor veya geçti
              </Text>
            </Group>
          )}
        </Stack>
      )}
    </Section>
  );
}

// ---------------------------------------------------------------- sayfa
export function DashboardPage() {
  const { user, can } = useAuth();
  const { branchId, branch } = useBranch();
  const navigate = useNavigate();
  const [apptOpen, setApptOpen] = useState(false);
  const dashboard = useQuery({
    queryKey: ['dashboard', { branchId }],
    queryFn: () => api.get<Dashboard>('/v1/dashboard'),
    enabled: Boolean(branchId),
  });

  if (!branchId) {
    return (
      <>
        <PageHeader title="Genel bakış" />
        <Paper withBorder p="xl">
          <Text c="dimmed">Şube bilgisi yükleniyor ya da hesabınıza atanmış şube yok.</Text>
        </Paper>
      </>
    );
  }

  const d = dashboard.data;
  const loading = dashboard.isLoading;
  const side = [
    can('clinical.read') && <OpenExams key="exams" data={d?.openEncounters} isLoading={loading} />,
    can('clients.read') && <DueVaccines key="vac" data={d?.dueVaccinations} isLoading={loading} />,
    can('inventory.read') && <StockWarnings key="stock" data={d?.stockAlerts} isLoading={loading} />,
  ].filter(Boolean);

  return (
    <>
      <PageHeader
        title="Genel bakış"
        description={`Merhaba ${user?.fullName ?? ''} · ${branch?.name ?? ''} · ${dayjs().format('D MMMM YYYY, dddd')}`}
        actions={
          <>
            {can('appointments.manage') && (
              <Button variant="default" leftSection={<IconCalendarPlus size={16} />} onClick={() => setApptOpen(true)}>
                Yeni randevu
              </Button>
            )}
            {can('clinical.write') && (
              <Button leftSection={<IconStethoscope size={16} />} onClick={() => navigate('/examinations?start=1')}>
                Muayene başlat
              </Button>
            )}
          </>
        }
      />
      {can('reports.read') && <Kpis data={d?.kpis} isLoading={loading} />}
      <Grid>
        {can('appointments.manage') && (
          <Grid.Col span={{ base: 12, lg: side.length ? 7 : 12 }}>
            <TodayAppointments data={d?.todayAppointments} isLoading={loading} />
          </Grid.Col>
        )}
        {side.length > 0 && (
          <Grid.Col span={{ base: 12, lg: can('appointments.manage') ? 5 : 12 }}>
            <Stack>{side}</Stack>
          </Grid.Col>
        )}
      </Grid>
      <AppointmentFormModal
        opened={apptOpen}
        onClose={() => {
          setApptOpen(false);
          void dashboard.refetch();
        }}
      />
    </>
  );
}
