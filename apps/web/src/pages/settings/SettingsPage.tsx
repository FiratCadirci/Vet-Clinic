import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  Grid,
  Group,
  Modal,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
  Skeleton,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  TextInput,
  Textarea,
  Tooltip,
} from '@mantine/core';
import { TimeInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import {
  IconBuilding,
  IconBuildingStore,
  IconClock,
  IconCopy,
  IconEdit,
  IconInfoCircle,
  IconPlus,
  IconStethoscope,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { SUBSCRIPTION_STATUS, WEEKDAYS } from '../../components/admin/labels';
import type {
  CatalogService,
  OrganizationProfile,
  VetOption,
  WorkingHourRow,
} from '../../components/admin/orgTypes';
import { DataTable, Empty, ErrorAlert, MoneyInput, PercentInput, StatusBadge } from '../../components/admin/ui';
import { num } from '../../components/admin/utils';
import { api, sessionStore, setActiveClinic } from '../../lib/api';
import { useAuth, type TenantAuthResponse } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { date, money, percent } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

/** TL tutarı → format.money'nin beklediği kuruş. */
const tl = (value: number) => money(Math.round(value * 100));
const blank = (v: string) => v.trim() || null;

/** Backend ClinicResponse (şube) ham alanları. */
interface BranchRow {
  id: string;
  code: string;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  district: string | null;
  addressLine: string | null;
  postalCode: string | null;
  isActive: boolean;
}

/** Profil sorgusu: klinik ve şube sekmeleri ortak kullanır (paket limiti/kullanımı dahil). */
const useOrganization = () =>
  useQuery({ queryKey: ['organization'], queryFn: () => api.get<OrganizationProfile>('/v1/organization') });

export function SettingsPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState<string | null>('clinic');
  return (
    <>
      <PageHeader title="Klinik ayarları" description="Klinik profili, şubeler, hizmetler ve hekim çalışma saatleri" />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="clinic" leftSection={<IconBuilding size={16} />}>
            Klinik profili
          </Tabs.Tab>
          <Tabs.Tab value="branches" leftSection={<IconBuildingStore size={16} />}>
            Şubeler
          </Tabs.Tab>
          <Tabs.Tab value="services" leftSection={<IconStethoscope size={16} />}>
            Hizmetler
          </Tabs.Tab>
          {(can('schedule.manage') || can('appointments.manage')) && (
            <Tabs.Tab value="hours" leftSection={<IconClock size={16} />}>
              Çalışma saatleri
            </Tabs.Tab>
          )}
        </Tabs.List>
        <Tabs.Panel value="clinic">
          <ClinicTab />
        </Tabs.Panel>
        <Tabs.Panel value="branches">
          <BranchesTab />
        </Tabs.Panel>
        <Tabs.Panel value="services">
          <ServicesTab />
        </Tabs.Panel>
        <Tabs.Panel value="hours">
          <WorkingHoursTab />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}

// ------------------------------------------------------------------ klinik profili

function ClinicTab() {
  const clinic = useOrganization();
  if (clinic.isLoading) return <Skeleton h={320} />;
  if (clinic.error || !clinic.data) return <ErrorAlert error={clinic.error} />;
  return <ClinicForm clinic={clinic.data} />;
}

function ClinicForm({ clinic }: { clinic: OrganizationProfile }) {
  const { can } = useAuth();
  const qc = useQueryClient();
  const editable = can('branches.manage');
  const form = useForm({
    initialValues: {
      name: clinic.name,
      phone: clinic.phone ?? '',
      email: clinic.email ?? '',
      address: clinic.addressLine ?? '',
      taxNumber: clinic.taxNumber ?? '',
    },
    validate: {
      name: (v) => (v.trim().length >= 2 ? null : 'Klinik adı en az 2 karakter olmalı'),
      phone: (v) => (!v.trim() || v.trim().length >= 7 ? null : 'Telefon en az 7 karakter olmalı'),
      email: (v) => (!v.trim() || /^\S+@\S+\.\S+$/.test(v.trim()) ? null : 'Geçerli bir e-posta girin'),
    },
  });

  const save = useMutation({
    mutationFn: (v: typeof form.values) =>
      api.put<OrganizationProfile>('/v1/organization', {
        name: v.name.trim(),
        legalName: clinic.legalName,
        taxOffice: clinic.taxOffice,
        phone: blank(v.phone),
        email: blank(v.email),
        addressLine: blank(v.address),
        taxNumber: blank(v.taxNumber),
      }),
    onSuccess: (c, v) => {
      qc.setQueryData(['organization'], c);
      form.resetDirty(v);
      notifySuccess('Klinik profili güncellendi');
    },
    onError: notifyError,
  });

  const sub = clinic.subscription;

  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 8 }}>
        <Paper withBorder radius="md" p="lg">
          <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
            <Stack>
              <TextInput label="Klinik adı" required disabled={!editable} {...form.getInputProps('name')} />
              <SimpleGrid cols={{ base: 1, sm: 2 }}>
                <TextInput label="Telefon" disabled={!editable} {...form.getInputProps('phone')} />
                <TextInput label="E-posta" type="email" disabled={!editable} {...form.getInputProps('email')} />
              </SimpleGrid>
              <Textarea label="Adres" autosize minRows={2} disabled={!editable} {...form.getInputProps('address')} />
              <TextInput label="Vergi numarası" disabled={!editable} {...form.getInputProps('taxNumber')} />
              <Text size="xs" c="dimmed">
                Bu bilgiler fatura (PDF) başlığında görünür. Klinik kodu (girişte kullanılır): <b>{clinic.code}</b>
              </Text>
              {editable ? (
                <Group justify="flex-end">
                  <Button type="submit" loading={save.isPending} disabled={!form.isDirty()}>
                    Kaydet
                  </Button>
                </Group>
              ) : (
                <Alert color="gray" icon={<IconInfoCircle size={16} />}>
                  Klinik profilini yalnızca klinik yöneticisi değiştirebilir.
                </Alert>
              )}
            </Stack>
          </form>
        </Paper>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 4 }}>
        <Card withBorder radius="md" padding="lg">
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            Abonelik
          </Text>
          {sub ? (
            <Stack gap={6} mt="xs">
              <Group justify="space-between">
                <Text fw={700} size="lg">
                  {sub.planName}
                </Text>
                <StatusBadge map={SUBSCRIPTION_STATUS} value={sub.status.toLowerCase()} />
              </Group>
              <Text size="sm">Yenileme: {date(sub.currentPeriodEnd)}</Text>
              <Text size="sm" c="dimmed">
                En fazla {sub.maxBranches} şube · {sub.maxUsers} kullanıcı
              </Text>
              {can('subscriptions.manage') && (
                <Button component={Link} to="/subscription" variant="light" mt="xs">
                  Aboneliği yönet
                </Button>
              )}
            </Stack>
          ) : (
            <Text size="sm" mt="xs">
              Aktif abonelik yok.
            </Text>
          )}
        </Card>
      </Grid.Col>
    </Grid>
  );
}

// ------------------------------------------------------------------ şubeler

function BranchesTab() {
  const { can } = useAuth();
  const manage = can('branches.manage');
  const clinic = useOrganization();
  // Kliniğin (aynı gruptaki) tüm şubeleri, pasifler dahil; düzenleme formu ham alanları kullanır.
  const branches = useQuery({ queryKey: ['branches', 'raw'], queryFn: () => api.get<BranchRow[]>('/v1/clinics') });
  const [editing, setEditing] = useState<BranchRow | 'new' | null>(null);
  const rows = branches.data ?? [];
  const limit = clinic.data?.subscription?.maxBranches;
  const used = clinic.data?.usage.activeBranches ?? rows.filter((b) => b.isActive).length;
  const atLimit = limit !== undefined && used >= limit;

  return (
    <Stack gap="sm">
      <Group justify="space-between">
        <Text size="sm" c="dimmed">
          {limit !== undefined
            ? `Paketiniz en fazla ${limit} aktif şube içerir (${used} kullanılıyor).`
            : `${used} aktif şube`}
        </Text>
        {manage && (
          <Tooltip label="Paket şube limitine ulaşıldı" disabled={!atLimit}>
            <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')} disabled={atLimit}>
              Yeni şube
            </Button>
          </Tooltip>
        )}
      </Group>
      <DataTable
        head={['Şube', 'Adres', 'Telefon', 'Durum', '']}
        loading={branches.isLoading}
        error={branches.error}
        empty={rows.length === 0}
        emptyText="Şube yok"
        minWidth={600}
      >
        {rows.map((b) => (
          <Table.Tr key={b.id} opacity={b.isActive ? 1 : 0.6}>
            <Table.Td fw={500}>{b.name}</Table.Td>
            <Table.Td>
              <Text size="sm" lineClamp={2}>
                {[b.addressLine, b.district, b.city].filter(Boolean).join(', ') || '—'}
              </Text>
            </Table.Td>
            <Table.Td>{b.phone ?? '—'}</Table.Td>
            <Table.Td>
              <Badge color={b.isActive ? 'green' : 'gray'} variant="light">
                {b.isActive ? 'Aktif' : 'Pasif'}
              </Badge>
            </Table.Td>
            <Table.Td>
              {manage && (
                <ActionIcon variant="subtle" onClick={() => setEditing(b)} aria-label="Düzenle">
                  <IconEdit size={16} />
                </ActionIcon>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Yeni şube' : 'Şubeyi düzenle'}
      >
        {editing && <BranchForm branch={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Modal>
    </Stack>
  );
}

function BranchForm({ branch, onDone }: { branch: BranchRow | null; onDone: () => void }) {
  const qc = useQueryClient();
  const { user, acceptTenantSession } = useAuth();
  const form = useForm({
    initialValues: {
      name: branch?.name ?? '',
      address: branch?.addressLine ?? '',
      phone: branch?.phone ?? '',
      active: branch?.isActive ?? true,
    },
    validate: {
      name: (v) => (v.trim().length >= 2 ? null : 'Şube adı en az 2 karakter olmalı'),
      phone: (v) => (!v.trim() || v.trim().length >= 7 ? null : 'Telefon en az 7 karakter olmalı'),
    },
  });

  /** Yeni şubeye otomatik atanan yönetici, şubeyi seçebilmek için yeni token alır (refresh). */
  const refreshSession = async () => {
    const s = sessionStore.get();
    if (!s?.refreshToken) return;
    const t = await api.post<TenantAuthResponse>('/auth/refresh', { refreshToken: s.refreshToken });
    acceptTenantSession(t, s.email ?? user?.email);
  };

  const mutation = useMutation({
    mutationFn: async (v: typeof form.values) => {
      const body = {
        name: v.name.trim(),
        addressLine: blank(v.address),
        phone: blank(v.phone),
        email: branch?.email ?? null,
        city: branch?.city ?? null,
        district: branch?.district ?? null,
        postalCode: branch?.postalCode ?? null,
      };
      if (!branch) {
        await api.post('/v1/clinics', body);
        await refreshSession();
        return;
      }
      await api.put(`/v1/clinics/${branch.id}`, body);
      if (v.active !== branch.isActive) await api.post(`/v1/clinics/${branch.id}/${v.active ? 'activate' : 'deactivate'}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['branches'] });
      void qc.invalidateQueries({ queryKey: ['organization'] });
      notifySuccess(branch ? 'Şube güncellendi' : 'Şube oluşturuldu');
      onDone();
    },
    onError: (e) => {
      void qc.invalidateQueries({ queryKey: ['branches'] });
      notifyError(e);
    },
  });
  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <TextInput label="Şube adı" required {...form.getInputProps('name')} />
        <Textarea label="Adres" autosize minRows={2} {...form.getInputProps('address')} />
        <TextInput label="Telefon" {...form.getInputProps('phone')} />
        {branch && (
          <Switch
            label="Aktif"
            description="Pasif şubeye randevu ve transfer yapılamaz. Yeniden açmak paket şube limitine tabidir."
            {...form.getInputProps('active', { type: 'checkbox' })}
          />
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onDone}>
            Vazgeç
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

// ------------------------------------------------------------------ hizmetler

function ServicesTab() {
  const { can } = useAuth();
  const manage = can('services.manage');
  const [editing, setEditing] = useState<CatalogService | 'new' | null>(null);
  const services = useQuery({
    queryKey: ['services', { includeInactive: true }],
    queryFn: () => api.get<CatalogService[]>('/v1/services', { includeInactive: true }),
  });

  return (
    <Stack gap="sm">
      <Group justify="space-between">
        <Text size="sm" c="dimmed">
          Randevu süreleri ve satışta kullanılan hizmet fiyatları (KDV hariç).
        </Text>
        {manage && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
            Yeni hizmet
          </Button>
        )}
      </Group>
      <DataTable
        head={['Hizmet', 'Süre', 'Fiyat (KDV hariç)', 'KDV', 'KDV dahil', 'Durum', '']}
        loading={services.isLoading}
        error={services.error}
        empty={!services.data?.length}
        emptyText="Henüz hizmet tanımlanmadı"
        minWidth={750}
      >
        {services.data?.map((s) => (
          <Table.Tr key={s.id} opacity={s.active ? 1 : 0.6}>
            <Table.Td fw={500}>{s.name}</Table.Td>
            <Table.Td>{s.durationMinutes} dk</Table.Td>
            <Table.Td>{tl(s.price)}</Table.Td>
            <Table.Td>{percent(Math.round(s.taxRate * 100))}</Table.Td>
            <Table.Td fw={600}>{tl(s.price + (s.price * s.taxRate) / 100)}</Table.Td>
            <Table.Td>
              <Badge color={s.active ? 'green' : 'gray'} variant="light">
                {s.active ? 'Aktif' : 'Pasif'}
              </Badge>
            </Table.Td>
            <Table.Td>
              {manage && (
                <ActionIcon variant="subtle" onClick={() => setEditing(s)} aria-label="Düzenle">
                  <IconEdit size={16} />
                </ActionIcon>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Yeni hizmet' : 'Hizmeti düzenle'}
      >
        {editing && <ServiceForm service={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Modal>
    </Stack>
  );
}

function ServiceForm({ service, onDone }: { service: CatalogService | null; onDone: () => void }) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      name: service?.name ?? '',
      durationMinutes: (service?.durationMinutes ?? 30) as number | string,
      price: (service ? service.price : '') as number | string,
      taxPercent: (service?.taxRate ?? 20) as number | string,
      active: service?.active ?? true,
    },
    validate: {
      name: (v) => (v.trim().length >= 2 ? null : 'Hizmet adı en az 2 karakter olmalı'),
      durationMinutes: (v) => (num(v) >= 5 && num(v) <= 480 ? null : 'Süre 5–480 dakika arası olmalı'),
      price: (v) => (v === '' ? 'Fiyat zorunlu' : null),
    },
  });
  const mutation = useMutation({
    mutationFn: (v: typeof form.values) => {
      const body = {
        name: v.name.trim(),
        category: service?.category ?? null,
        durationMinutes: Math.trunc(num(v.durationMinutes)),
        price: Math.round(num(v.price) * 100) / 100,
        taxRate: Math.round(num(v.taxPercent) * 100) / 100,
        active: v.active,
      };
      return service ? api.put(`/v1/services/${service.id}`, body) : api.post('/v1/services', body);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['services'] });
      notifySuccess(service ? 'Hizmet güncellendi' : 'Hizmet oluşturuldu');
      onDone();
    },
    onError: notifyError,
  });
  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <TextInput label="Hizmet adı" required placeholder="ör. Genel muayene" {...form.getInputProps('name')} />
        <NumberInput
          label="Süre (dakika)"
          required
          min={5}
          max={480}
          step={5}
          allowDecimal={false}
          {...form.getInputProps('durationMinutes')}
        />
        <SimpleGrid cols={2}>
          <MoneyInput label="Fiyat (KDV hariç)" required {...form.getInputProps('price')} />
          <PercentInput label="KDV oranı" {...form.getInputProps('taxPercent')} />
        </SimpleGrid>
        <Switch label="Aktif" {...form.getInputProps('active', { type: 'checkbox' })} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onDone}>
            Vazgeç
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

// ------------------------------------------------------------------ çalışma saatleri

interface Interval {
  key: number;
  /** Arayüz günü: 1 = Pazartesi … 7 = Pazar (WEEKDAYS). */
  weekday: number;
  startTime: string;
  endTime: string;
}
let ivSeq = 0;

/** Backend günü (0 = Pazar … 6 = Cumartesi) ↔ arayüz günü (1 = Pazartesi … 7 = Pazar). */
const toWeekday = (dow: number) => (dow === 0 ? 7 : dow);
const toDayOfWeek = (weekday: number) => weekday % 7;

function WorkingHoursTab() {
  const { can } = useAuth();
  const { branches, branchId: currentBranch } = useBranch();
  const [branchId, setBranchId] = useState<string | null>(currentBranch);
  const canManage = can('schedule.manage');

  // Şubede görevli, hekim profili olan personel.
  const vetsQuery = useQuery({
    queryKey: ['vets', branchId],
    queryFn: () => api.get<VetOption[]>('/v1/working-hours/veterinarians', { clinicId: branchId }),
    enabled: Boolean(branchId),
  });
  const branchHours = useQuery({
    queryKey: ['working-hours', branchId],
    queryFn: () => api.get<WorkingHourRow[]>('/v1/working-hours', { clinicId: branchId }),
    enabled: Boolean(branchId),
  });

  const vets = useMemo(
    () => (vetsQuery.data ?? []).map((v) => ({ value: v.veterinarianId, label: v.displayName })),
    [vetsQuery.data],
  );

  const [pickedVet, setVetId] = useState<string | null>(null);
  // Seçim listede yoksa (şube değişti / ilk açılış) ilk hekim seçilir.
  const vetId = pickedVet && vets.some((v) => v.value === pickedVet) ? pickedVet : (vets[0]?.value ?? null);

  const vetHours = useMemo(
    () => (branchHours.data ?? []).filter((h) => h.veterinarianId === vetId),
    [branchHours.data, vetId],
  );

  const summary = (id: string) => {
    const rows = (branchHours.data ?? []).filter((h) => h.veterinarianId === id);
    if (!rows.length) return 'Tanımsız';
    const days = new Set(rows.map((r) => r.dayOfWeek)).size;
    return `${days} gün`;
  };

  return (
    <Stack gap="md">
      <Group align="flex-end" gap="sm" wrap="wrap">
        <Select
          label="Şube"
          allowDeselect={false}
          data={branches.map((b) => ({ value: b.id, label: b.name }))}
          value={branchId}
          onChange={(v) => {
            setBranchId(v);
            setVetId(null);
          }}
          w={220}
        />
        <Select
          label="Hekim"
          placeholder={vets.length ? 'Hekim seçin' : 'Bu şubede hekim yok'}
          allowDeselect={false}
          data={vets.map((v) => ({ ...v, label: `${v.label} · ${summary(v.value)}` }))}
          value={vetId}
          onChange={setVetId}
          w={280}
          disabled={!vets.length}
        />
      </Group>
      {!vets.length && !vetsQuery.isLoading && !branchHours.isLoading ? (
        <Paper withBorder radius="md">
          <Empty
            title="Bu şubeye atanmış hekim yok"
            description="Personel ekranından şubeye veteriner hekim atayın ve hekim profilini (lisans no) oluşturun."
          />
        </Paper>
      ) : (
        branchId &&
        vetId && (
          <HoursEditor
            key={`${branchId}:${vetId}`}
            branchId={branchId}
            vetId={vetId}
            initial={vetHours}
            loading={branchHours.isLoading}
            editable={canManage}
          />
        )
      )}
    </Stack>
  );
}

function HoursEditor({
  branchId,
  vetId,
  initial,
  loading,
  editable,
}: {
  branchId: string;
  vetId: string;
  initial: WorkingHourRow[];
  loading: boolean;
  editable: boolean;
}) {
  const qc = useQueryClient();
  const { branchId: activeBranch } = useBranch();
  const [rows, setRows] = useState<Interval[] | null>(null);
  const fromServer = useMemo(
    () =>
      initial.map((h) => ({
        key: ivSeq++,
        weekday: toWeekday(h.dayOfWeek),
        startTime: h.startTime.slice(0, 5),
        endTime: h.endTime.slice(0, 5),
      })),
    [initial],
  );
  const list = rows ?? fromServer;

  const problems = useMemo(() => {
    const p = new Map<number, string>();
    for (const r of list) {
      if (!/^\d{2}:\d{2}$/.test(r.startTime) || !/^\d{2}:\d{2}$/.test(r.endTime)) p.set(r.key, 'Saatleri girin');
      else if (r.startTime >= r.endTime) p.set(r.key, 'Başlangıç bitişten önce olmalı');
      else if (list.some((o) => o.key !== r.key && o.weekday === r.weekday && o.startTime < r.endTime && r.startTime < o.endTime))
        p.set(r.key, 'Aralıklar çakışıyor');
    }
    return p;
  }, [list]);

  const save = useMutation({
    mutationFn: () => {
      // Hedef şube X-Clinic-Id başlığıyla seçilir: istek başlığı çağrı anında okunur, ardından seçili şube geri yüklenir.
      setActiveClinic(branchId);
      const request = api.put<WorkingHourRow[]>('/v1/working-hours', {
        veterinarianId: vetId,
        hours: list.map(({ weekday, startTime, endTime }) => ({ dayOfWeek: toDayOfWeek(weekday), startTime, endTime })),
      });
      setActiveClinic(activeBranch);
      return request;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['working-hours'] });
      void qc.invalidateQueries({ queryKey: ['appointments', 'availability'] });
      setRows(null);
      notifySuccess('Çalışma saatleri kaydedildi');
    },
    onError: notifyError,
  });

  const set = (next: Interval[]) => setRows(next.sort((a, b) => a.weekday - b.weekday || a.startTime.localeCompare(b.startTime)));
  const add = (weekday: number) => {
    const last = list.filter((r) => r.weekday === weekday).at(-1);
    set([...list, { key: ivSeq++, weekday, startTime: last ? last.endTime : '09:00', endTime: last ? '20:00' : '18:00' }]);
  };
  const copyToWeekdays = (weekday: number) => {
    const src = list.filter((r) => r.weekday === weekday);
    set([
      ...list.filter((r) => r.weekday === weekday || r.weekday > 5),
      ...[1, 2, 3, 4, 5].filter((d) => d !== weekday).flatMap((d) => src.map((s) => ({ ...s, key: ivSeq++, weekday: d }))),
    ]);
  };

  if (loading) return <Skeleton h={360} />;

  return (
    <Stack gap="sm">
      <Paper withBorder radius="md">
        <Table verticalSpacing="sm" miw={0}>
          <Table.Tbody>
            {WEEKDAYS.map((day) => {
              const intervals = list.filter((r) => r.weekday === day.value);
              return (
                <Table.Tr key={day.value}>
                  <Table.Td w={130} valign="top" pt="md">
                    <Text fw={500}>{day.label}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Stack gap="xs">
                      {intervals.length === 0 && (
                        <Text size="sm" c="dimmed" pt={6}>
                          Çalışmıyor
                        </Text>
                      )}
                      {intervals.map((r) => (
                        <Group key={r.key} gap="xs" wrap="wrap" align="flex-start">
                          <TimeInput
                            aria-label="Başlangıç"
                            w={110}
                            value={r.startTime}
                            disabled={!editable}
                            onChange={(e) =>
                              set(list.map((x) => (x.key === r.key ? { ...x, startTime: e.currentTarget.value } : x)))
                            }
                            error={Boolean(problems.get(r.key))}
                          />
                          <Text pt={6}>–</Text>
                          <TimeInput
                            aria-label="Bitiş"
                            w={110}
                            value={r.endTime}
                            disabled={!editable}
                            onChange={(e) =>
                              set(list.map((x) => (x.key === r.key ? { ...x, endTime: e.currentTarget.value } : x)))
                            }
                            error={Boolean(problems.get(r.key))}
                          />
                          {editable && (
                            <ActionIcon
                              variant="subtle"
                              color="red"
                              mt={4}
                              onClick={() => set(list.filter((x) => x.key !== r.key))}
                              aria-label="Aralığı sil"
                            >
                              <IconTrash size={16} />
                            </ActionIcon>
                          )}
                          {problems.get(r.key) && (
                            <Text size="xs" c="red" pt={8}>
                              {problems.get(r.key)}
                            </Text>
                          )}
                        </Group>
                      ))}
                    </Stack>
                  </Table.Td>
                  <Table.Td w={1} valign="top" style={{ whiteSpace: 'nowrap' }}>
                    {editable && (
                      <Group gap={4} wrap="nowrap">
                        <Button size="xs" variant="subtle" leftSection={<IconPlus size={14} />} onClick={() => add(day.value)}>
                          Aralık
                        </Button>
                        {intervals.length > 0 && day.value <= 5 && (
                          <Tooltip label="Bu günün saatlerini diğer hafta içi günlere kopyala">
                            <ActionIcon
                              variant="subtle"
                              color="gray"
                              onClick={() => copyToWeekdays(day.value)}
                              aria-label="Hafta içine kopyala"
                            >
                              <IconCopy size={16} />
                            </ActionIcon>
                          </Tooltip>
                        )}
                      </Group>
                    )}
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      </Paper>
      {editable ? (
        <Group justify="space-between">
          <Text size="xs" c="dimmed">
            Hekim aynı saatlerde başka bir şubede çalışıyor olamaz. Öğle arası için güne iki aralık ekleyin.
          </Text>
          <Group gap="sm">
            {rows && (
              <Button variant="default" onClick={() => setRows(null)}>
                Değişiklikleri geri al
              </Button>
            )}
            <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!rows || problems.size > 0}>
              Kaydet
            </Button>
          </Group>
        </Group>
      ) : (
        <Alert color="gray" icon={<IconInfoCircle size={16} />}>
          Çalışma saatlerini değiştirme yetkiniz yok.
        </Alert>
      )}
    </Stack>
  );
}
