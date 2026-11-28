import { AreaChart, BarChart } from '@mantine/charts';
import { Anchor, Badge, Group, Paper, Progress, Select, SimpleGrid, Skeleton, Stack, Table, Tabs, Text } from '@mantine/core';
import { IconCalendarStats, IconCash, IconChartBar, IconPercentage, IconStethoscope, IconVaccine } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { SERIES_COLORS, dayLabel, shortTl } from '../../components/admin/charts';
import { MOVEMENT_TYPE, PRODUCT_TYPE } from '../../components/admin/labels';
import { DataTable, DateRangeInput, ErrorAlert, Section, StatCard } from '../../components/admin/ui';
import { dateOnlyQuery, isoRangeQuery, useDateRange, type Range } from '../../components/admin/utils';
import { statusKey } from '../../components/clinical/patientApi';
import { api, type Page } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { APPOINTMENT_STATUS, date, money } from '../../lib/format';

/** GET /v1/reports/* yanıtları (FG-L1). Para alanları TL (KDV dahil), oranlar yüzde. */
interface Overview {
  examinations: number;
  /** Durum kodu (BOOKED, COMPLETED ...) → randevu sayısı. */
  appointments: Record<string, number>;
  occupancyRate: number;
  overdueVaccinations: number;
  sales: { count: number; total: number; paid: number };
}
interface ExamRow {
  day: string;
  clinicId: string;
  veterinarianId: string | null;
  vetName: string | null;
  encounterType: string;
  count: number;
}
interface OccupancyRow {
  clinicId: string;
  veterinarianId: string;
  vetName: string;
  availableMinutes: number;
  bookedMinutes: number;
  occupancyRate: number;
}
interface OverdueRow {
  vaccinationId: string;
  animalId: string;
  animalName: string;
  speciesName: string;
  vaccineName: string;
  nextDueAt: string;
  clinicId: string;
  daysOverdue: number;
}
interface StockReportRow {
  productId: string;
  productName: string;
  productType: string;
  transactionType: string;
  quantity: number;
  movements: number;
}
interface SalesReport {
  byItem: {
    lineType: 'SERVICE' | 'PRODUCT' | 'OTHER';
    productId: string | null;
    serviceId: string | null;
    description: string;
    category: string | null;
    quantity: number;
    total: number;
  }[];
  byDay: { day: string; count: number; total: number }[];
}
interface BranchPerf {
  clinicId: string;
  clinicName: string;
  examinations: number;
  appointments: number;
  revenue: number;
  occupancyRate: number;
}
/** GET /v1/commissions satırı (commissions.read). */
interface CommissionRecord {
  id: string;
  clinicId: string;
  invoiceId: string;
  invoiceNo: string;
  description: string;
  productType: string | null;
  quantity: number;
  eligibleAmount: number;
  ratePercent: number;
  commissionAmount: number;
  issuedAt: string | null;
  calculatedAt: string;
}

/** TL tutarı → kuruş (money/shortTl kuruş bekler). */
const kurus = (tl: number | null | undefined) => Math.round((tl ?? 0) * 100);

interface Filters {
  range: Range;
  branchId: string | null;
}

function useReport<T>(name: string, f: Filters) {
  const params = { ...isoRangeQuery(f.range), clinicId: f.branchId ?? undefined };
  return useQuery({ queryKey: ['reports', name, params], queryFn: () => api.get<T>(`/v1/reports/${name}`, params) });
}

/** Aralıktaki tüm günler (grafikte boş günler sıfır görünsün). */
function daysOf([from, to]: Range): string[] {
  if (!from || !to) return [];
  const out: string[] = [];
  for (let d = dayjs(from); !d.isAfter(dayjs(to), 'day') && out.length < 400; d = d.add(1, 'day'))
    out.push(d.format('YYYY-MM-DD'));
  return out;
}

const hours = (min: number) => `${Math.round((min / 60) * 10) / 10} sa`;

export function ReportsPage() {
  const { branches } = useBranch();
  const { can } = useAuth();
  const [range, setRange] = useDateRange(30);
  const [branchId, setBranchId] = useState<string | null>(null);
  const [tab, setTab] = useState<string | null>('overview');

  const valid = Boolean(range[0] && range[1]) && dayjs(range[1]).diff(dayjs(range[0]), 'day') <= 365;
  const filters: Filters = { range, branchId };

  return (
    <>
      <PageHeader title="Raporlar" description="Muayene, randevu doluluğu, aşı, stok, satış ve komisyon raporları" />
      <Paper withBorder radius="md" p="sm" mb="md">
        <Group align="flex-end" gap="sm" wrap="wrap">
          <DateRangeInput value={range} onChange={setRange} clearable={false} />
          <Select
            label="Şube"
            placeholder="Tüm şubelerim"
            clearable
            data={branches.map((b) => ({ value: b.id, label: b.name }))}
            value={branchId}
            onChange={setBranchId}
            w={220}
          />
          {!valid && (
            <Text size="sm" c="red">
              Başlangıç ve bitiş tarihi seçin (en fazla 1 yıl).
            </Text>
          )}
        </Group>
      </Paper>

      {valid && (
        <Tabs value={tab} onChange={setTab} keepMounted={false}>
          <Tabs.List mb="md">
            <Tabs.Tab value="overview">Genel bakış</Tabs.Tab>
            <Tabs.Tab value="exams">Muayene ve doluluk</Tabs.Tab>
            <Tabs.Tab value="vaccines">Gecikmiş aşılar</Tabs.Tab>
            <Tabs.Tab value="stock">Stok hareketleri</Tabs.Tab>
            <Tabs.Tab value="sales">Satışlar</Tabs.Tab>
            {can('commissions.read') && <Tabs.Tab value="commissions">Komisyon dökümü</Tabs.Tab>}
          </Tabs.List>
          <Tabs.Panel value="overview">
            <OverviewTab f={filters} />
          </Tabs.Panel>
          <Tabs.Panel value="exams">
            <ExamsTab f={filters} />
          </Tabs.Panel>
          <Tabs.Panel value="vaccines">
            <VaccinesTab f={filters} />
          </Tabs.Panel>
          <Tabs.Panel value="stock">
            <StockTab f={filters} />
          </Tabs.Panel>
          <Tabs.Panel value="sales">
            <SalesTab f={filters} />
          </Tabs.Panel>
          <Tabs.Panel value="commissions">
            <CommissionsTab f={filters} />
          </Tabs.Panel>
        </Tabs>
      )}
    </>
  );
}

// ------------------------------------------------------------------ genel bakış

function OverviewTab({ f }: { f: Filters }) {
  const overview = useReport<Overview>('overview', f);
  const perf = useReport<BranchPerf[]>('branches', f);
  const o = overview.data;
  const apptTotal = o ? Object.values(o.appointments).reduce((s, n) => s + n, 0) : 0;

  return (
    <Stack gap="lg">
      {overview.error ? (
        <ErrorAlert error={overview.error} />
      ) : !o ? (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 5 }}>
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} h={100} />
          ))}
        </SimpleGrid>
      ) : (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 5 }}>
          <StatCard label="Muayene" value={o.examinations} icon={<IconStethoscope size={20} />} />
          <StatCard
            label="Randevu"
            value={apptTotal}
            hint={`${o.appointments.COMPLETED ?? 0} tamamlandı · ${o.appointments.NO_SHOW ?? 0} gelmedi`}
            icon={<IconCalendarStats size={20} />}
            color="indigo"
          />
          <StatCard
            label="Randevu doluluğu"
            value={`%${o.occupancyRate.toLocaleString('tr-TR')}`}
            icon={<IconPercentage size={20} />}
            color="cyan"
          />
          <StatCard
            label="Gecikmiş aşı"
            value={o.overdueVaccinations}
            hint="Şu an itibarıyla"
            icon={<IconVaccine size={20} />}
            color="orange"
          />
          <StatCard
            label="Ciro (kesilen)"
            value={money(kurus(o.sales.total))}
            hint={`${o.sales.count} fatura · tahsil ${money(kurus(o.sales.paid))}`}
            icon={<IconCash size={20} />}
            color="green"
          />
        </SimpleGrid>
      )}

      {o && apptTotal > 0 && (
        <Group gap="xs">
          {Object.entries(o.appointments).map(([code, n]) => {
            const status = APPOINTMENT_STATUS[statusKey(code)];
            return (
              <Badge key={code} variant="light" color={status?.color ?? 'gray'}>
                {status?.label ?? code}: {n}
              </Badge>
            );
          })}
        </Group>
      )}

      <Section title="Şube performansı" description="Seçili aralıkta şube bazında muayene, randevu, ciro ve doluluk">
        {perf.data && perf.data.length > 1 && (
          <Paper withBorder radius="md" p="md">
            <BarChart
              h={240}
              data={perf.data.map((b) => ({ şube: b.clinicName, ciro: kurus(b.revenue) }))}
              dataKey="şube"
              series={[{ name: 'ciro', label: 'Ciro', color: SERIES_COLORS[0] }]}
              valueFormatter={(v) => money(v)}
              yAxisProps={{ tickFormatter: (v: number) => shortTl(v), width: 80 }}
              gridAxis="y"
              barProps={{ radius: [4, 4, 0, 0] }}
              maxBarWidth={48}
            />
          </Paper>
        )}
        <DataTable
          head={['Şube', 'Muayene', 'Randevu', 'Ciro', 'Doluluk']}
          loading={perf.isLoading}
          error={perf.error}
          empty={!perf.data?.length}
          minWidth={600}
        >
          {perf.data?.map((b) => (
            <Table.Tr key={b.clinicId}>
              <Table.Td fw={500}>{b.clinicName}</Table.Td>
              <Table.Td>{b.examinations}</Table.Td>
              <Table.Td>{b.appointments}</Table.Td>
              <Table.Td fw={600}>{money(kurus(b.revenue))}</Table.Td>
              <Table.Td w={220}>
                <OccupancyBar rate={b.occupancyRate} />
              </Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Section>
    </Stack>
  );
}

function OccupancyBar({ rate }: { rate: number }) {
  return (
    <Group gap="xs" wrap="nowrap">
      <Progress value={Math.min(rate, 100)} color={rate >= 85 ? 'orange' : 'teal'} style={{ flex: 1 }} />
      <Text size="sm" w={52} ta="right">
        %{rate.toLocaleString('tr-TR')}
      </Text>
    </Group>
  );
}

// ------------------------------------------------------------------ muayene + doluluk

function ExamsTab({ f }: { f: Filters }) {
  const { branches } = useBranch();
  const exams = useReport<ExamRow[]>('examinations', f);
  const occ = useReport<OccupancyRow[]>('occupancy', f);
  const branchName = (id: string) => branches.find((b) => b.id === id)?.name ?? '—';

  const perDay = useMemo(() => {
    const map = new Map<string, number>();
    for (const r of exams.data ?? []) map.set(r.day, (map.get(r.day) ?? 0) + r.count);
    return daysOf(f.range).map((d) => ({ gün: dayLabel(d), muayene: map.get(d) ?? 0 }));
  }, [exams.data, f.range]);

  const perVet = useMemo(() => {
    const map = new Map<string, { vetName: string; branchId: string; n: number }>();
    for (const r of exams.data ?? []) {
      const key = `${r.clinicId}:${r.veterinarianId}`;
      const cur = map.get(key) ?? { vetName: r.vetName ?? '—', branchId: r.clinicId, n: 0 };
      cur.n += r.count;
      map.set(key, cur);
    }
    return [...map.values()].sort((a, b) => b.n - a.n);
  }, [exams.data]);
  const total = perVet.reduce((s, r) => s + r.n, 0);

  return (
    <Stack gap="lg">
      <Section title="Günlük muayene sayısı" description={`Toplam ${total} muayene`}>
        <Paper withBorder radius="md" p="md">
          {exams.isLoading ? (
            <Skeleton h={260} />
          ) : exams.error ? (
            <ErrorAlert error={exams.error} />
          ) : (
            <AreaChart
              h={260}
              data={perDay}
              dataKey="gün"
              series={[{ name: 'muayene', label: 'Muayene', color: SERIES_COLORS[0] }]}
              curveType="monotone"
              gridAxis="y"
              withDots={perDay.length <= 31}
              strokeWidth={2}
              fillOpacity={0.15}
              yAxisProps={{ allowDecimals: false }}
            />
          )}
        </Paper>
      </Section>

      <Section title="Hekim bazında muayene">
        <DataTable
          head={['Hekim', 'Şube', 'Muayene', 'Pay']}
          loading={exams.isLoading}
          empty={perVet.length === 0}
          minWidth={500}
        >
          {perVet.map((r) => (
            <Table.Tr key={`${r.branchId}-${r.vetName}`}>
              <Table.Td fw={500}>{r.vetName}</Table.Td>
              <Table.Td>{branchName(r.branchId)}</Table.Td>
              <Table.Td>{r.n}</Table.Td>
              <Table.Td>%{total ? Math.round((r.n / total) * 100) : 0}</Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Section>

      <Section
        title="Randevu doluluk oranı"
        description="Dolu süre / hekim çalışma saatlerinden gelen müsait süre (hekim ve şube bazında)"
      >
        <DataTable
          head={['Hekim', 'Şube', 'Müsait', 'Dolu', 'Doluluk']}
          loading={occ.isLoading}
          error={occ.error}
          empty={!occ.data?.length}
          emptyText="Çalışma saati tanımlı hekim yok"
          minWidth={650}
        >
          {occ.data
            ?.slice()
            .sort((a, b) => b.occupancyRate - a.occupancyRate)
            .map((r) => (
              <Table.Tr key={`${r.clinicId}-${r.veterinarianId}`}>
                <Table.Td fw={500}>{r.vetName}</Table.Td>
                <Table.Td>{branchName(r.clinicId)}</Table.Td>
                <Table.Td>{hours(r.availableMinutes)}</Table.Td>
                <Table.Td>{hours(r.bookedMinutes)}</Table.Td>
                <Table.Td w={240}>
                  <OccupancyBar rate={r.occupancyRate} />
                </Table.Td>
              </Table.Tr>
            ))}
        </DataTable>
      </Section>
    </Stack>
  );
}

// ------------------------------------------------------------------ aşılar

function VaccinesTab({ f }: { f: Filters }) {
  const { branches } = useBranch();
  const overdue = useReport<OverdueRow[]>('vaccines/overdue', f);
  return (
    <Section
      title="Gecikmiş aşılar"
      description="Sonraki doz tarihi geçmiş ve henüz yapılmamış aşılar (tarih aralığından bağımsız, güncel durum)"
    >
      <DataTable
        head={['Hasta', 'Tür', 'Aşı', 'Planlanan tarih', 'Gecikme', 'Şube']}
        loading={overdue.isLoading}
        error={overdue.error}
        empty={!overdue.data?.length}
        emptyText="Gecikmiş aşı yok"
        minWidth={700}
      >
        {overdue.data?.map((r) => (
          <Table.Tr key={r.vaccinationId}>
            <Table.Td>
              <Anchor component={Link} to={`/patients/${r.animalId}`} fw={500} size="sm">
                {r.animalName}
              </Anchor>
            </Table.Td>
            <Table.Td>{r.speciesName}</Table.Td>
            <Table.Td>{r.vaccineName}</Table.Td>
            <Table.Td>{date(r.nextDueAt)}</Table.Td>
            <Table.Td>
              <Badge color={r.daysOverdue > 30 ? 'red' : 'orange'} variant="light">
                {r.daysOverdue} gün
              </Badge>
            </Table.Td>
            <Table.Td>{branches.find((b) => b.id === r.clinicId)?.name ?? '—'}</Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
    </Section>
  );
}

// ------------------------------------------------------------------ stok

/** Sütun sırası: etiket tablosundaki sıra; yalnız aralıkta görülen hareket türleri sütun olur. */
const TYPE_ORDER = Object.keys(MOVEMENT_TYPE);

function StockTab({ f }: { f: Filters }) {
  const stock = useReport<StockReportRow[]>('stock-movements', f);
  const { pivot, types } = useMemo(() => {
    const map = new Map<string, { name: string; category: string; byType: Record<string, number>; net: number; count: number }>();
    const seen = new Set<string>();
    for (const r of stock.data ?? []) {
      const cur = map.get(r.productId) ?? { name: r.productName, category: r.productType, byType: {}, net: 0, count: 0 };
      cur.byType[r.transactionType] = (cur.byType[r.transactionType] ?? 0) + r.quantity;
      cur.net += r.quantity;
      cur.count += r.movements;
      map.set(r.productId, cur);
      seen.add(r.transactionType);
    }
    const order = (t: string) => (TYPE_ORDER.indexOf(t) < 0 ? TYPE_ORDER.length : TYPE_ORDER.indexOf(t));
    return { pivot: [...map.entries()], types: [...seen].sort((a, b) => order(a) - order(b)) };
  }, [stock.data]);

  const signed = (n: number | undefined) =>
    n === undefined || n === 0 ? (
      <Text c="dimmed" size="sm">
        —
      </Text>
    ) : (
      <Text size="sm" fw={500} c={n > 0 ? 'teal.8' : 'red.8'}>
        {n > 0 ? `+${n.toLocaleString('tr-TR')}` : n.toLocaleString('tr-TR')}
      </Text>
    );

  return (
    <Section
      title="Stok hareketleri"
      description="Ürün ve hareket türüne göre net miktarlar (giriş +, çıkış −). Transferde çıkış ve giriş ayrı şubelerde sayılır."
    >
      <DataTable
        head={['Ürün', 'Kategori', ...types.map((t) => MOVEMENT_TYPE[t]?.label ?? t), 'Net', 'Hareket']}
        loading={stock.isLoading}
        error={stock.error}
        empty={pivot.length === 0}
        emptyText="Bu aralıkta stok hareketi yok"
        minWidth={900}
      >
        {pivot.map(([id, r]) => (
          <Table.Tr key={id}>
            <Table.Td fw={500}>{r.name}</Table.Td>
            <Table.Td>{PRODUCT_TYPE[r.category] ?? r.category}</Table.Td>
            {types.map((t) => (
              <Table.Td key={t}>{signed(r.byType[t])}</Table.Td>
            ))}
            <Table.Td>{signed(r.net)}</Table.Td>
            <Table.Td>{r.count}</Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
    </Section>
  );
}

// ------------------------------------------------------------------ satışlar

const LINE_TYPE: Record<string, { label: string; color: string }> = {
  PRODUCT: { label: 'Ürün', color: 'indigo' },
  SERVICE: { label: 'Hizmet', color: 'teal' },
  OTHER: { label: 'Diğer', color: 'gray' },
};

function SalesTab({ f }: { f: Filters }) {
  const sales = useReport<SalesReport>('sales', f);
  const perDay = useMemo(() => {
    const map = new Map((sales.data?.byDay ?? []).map((d) => [d.day, d]));
    return daysOf(f.range).map((d) => ({ gün: dayLabel(d), ciro: kurus(map.get(d)?.total) }));
  }, [sales.data, f.range]);
  const total = sales.data?.byDay.reduce((s, d) => s + kurus(d.total), 0) ?? 0;
  const count = sales.data?.byDay.reduce((s, d) => s + d.count, 0) ?? 0;

  return (
    <Stack gap="lg">
      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <StatCard label="Ciro (KDV dahil)" value={money(total)} icon={<IconCash size={20} />} color="green" />
        <StatCard label="Fatura" value={count} icon={<IconChartBar size={20} />} />
        <StatCard
          label="Ortalama fatura"
          value={money(count ? Math.round(total / count) : 0)}
          icon={<IconCash size={20} />}
          color="indigo"
        />
      </SimpleGrid>
      <Section title="Günlük ciro" description="Kesilmiş faturalar, kesim tarihine göre">
        <Paper withBorder radius="md" p="md">
          {sales.isLoading ? (
            <Skeleton h={260} />
          ) : sales.error ? (
            <ErrorAlert error={sales.error} />
          ) : (
            <BarChart
              h={260}
              data={perDay}
              dataKey="gün"
              series={[{ name: 'ciro', label: 'Ciro', color: SERIES_COLORS[0] }]}
              valueFormatter={(v) => money(v)}
              yAxisProps={{ tickFormatter: (v: number) => shortTl(v), width: 80 }}
              gridAxis="y"
              barProps={{ radius: [4, 4, 0, 0] }}
              maxBarWidth={32}
            />
          )}
        </Paper>
      </Section>
      <Section title="Kalem bazında satışlar">
        <DataTable
          head={['Kalem', 'Tür', 'Miktar', 'Tutar (KDV dahil)', 'Pay']}
          loading={sales.isLoading}
          empty={!sales.data?.byItem.length}
          emptyText="Bu aralıkta satış yok"
          minWidth={600}
        >
          {sales.data?.byItem.map((r, i) => {
            const kind = LINE_TYPE[r.lineType] ?? LINE_TYPE.OTHER;
            const lineTotal = kurus(r.total);
            return (
              <Table.Tr key={`${r.lineType}-${r.productId ?? r.serviceId ?? ''}-${r.description}-${i}`}>
                <Table.Td fw={500}>{r.description}</Table.Td>
                <Table.Td>
                  <Badge size="sm" variant="light" color={kind.color}>
                    {r.category ? (PRODUCT_TYPE[r.category] ?? kind.label) : kind.label}
                  </Badge>
                </Table.Td>
                <Table.Td>{r.quantity.toLocaleString('tr-TR')}</Table.Td>
                <Table.Td fw={600}>{money(lineTotal)}</Table.Td>
                <Table.Td>%{total ? Math.round((lineTotal / total) * 100) : 0}</Table.Td>
              </Table.Tr>
            );
          })}
        </DataTable>
      </Section>
    </Stack>
  );
}

// ------------------------------------------------------------------ komisyon

/** Komisyon dökümü için sayfa sayısı sınırı (200 × 10 kayıt). */
const COMMISSION_PAGE_SIZE = 200;
const COMMISSION_MAX_PAGES = 10;

async function listCommissions(params: { from?: string; to?: string; clinicId?: string }) {
  const out: CommissionRecord[] = [];
  for (let page = 0; page < COMMISSION_MAX_PAGES; page++) {
    const res = await api.get<Page<CommissionRecord>>('/v1/commissions', { ...params, page, size: COMMISSION_PAGE_SIZE });
    out.push(...res.content);
    if (res.last) break;
  }
  return out;
}

function CommissionsTab({ f }: { f: Filters }) {
  const { branches } = useBranch();
  const params = { ...dateOnlyQuery(f.range), clinicId: f.branchId ?? undefined };
  const statement = useQuery({
    queryKey: ['commissions', params],
    queryFn: () => listCommissions(params),
  });
  const lines = statement.data ?? [];
  const base = lines.reduce((s, l) => s + kurus(l.eligibleAmount), 0);
  const amount = lines.reduce((s, l) => s + kurus(l.commissionAmount), 0);

  return (
    <Stack gap="lg">
      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <StatCard label="Komisyon matrahı" value={money(base)} hint="KDV hariç net satış" icon={<IconCash size={20} />} />
        <StatCard label="Platforma ödenecek" value={money(amount)} icon={<IconPercentage size={20} />} color="orange" />
        <StatCard label="Komisyonlu kalem" value={lines.length} icon={<IconChartBar size={20} />} color="indigo" />
      </SimpleGrid>
      <Section title="Komisyon dökümü" description="Platform üzerinden satılan uygun mama/ilaç ürünlerinden kesilen komisyonlar">
        <DataTable
          head={['Tarih', 'Fatura', 'Şube', 'Kalem', 'Kategori', 'Miktar', 'Matrah', 'Oran', 'Komisyon']}
          loading={statement.isLoading}
          error={statement.error}
          empty={lines.length === 0}
          emptyText="Bu aralıkta komisyon yok"
          minWidth={1000}
        >
          {lines.map((l) => (
            <Table.Tr key={l.id}>
              <Table.Td>{date(l.issuedAt ?? l.calculatedAt)}</Table.Td>
              <Table.Td>
                <Anchor component={Link} to={`/sales/${l.invoiceId}`} size="sm">
                  {l.invoiceNo}
                </Anchor>
              </Table.Td>
              <Table.Td>{branches.find((b) => b.id === l.clinicId)?.name ?? '—'}</Table.Td>
              <Table.Td>{l.description}</Table.Td>
              <Table.Td>{l.productType ? (PRODUCT_TYPE[l.productType] ?? l.productType) : '—'}</Table.Td>
              <Table.Td>{l.quantity.toLocaleString('tr-TR')}</Table.Td>
              <Table.Td>{money(kurus(l.eligibleAmount))}</Table.Td>
              <Table.Td>%{l.ratePercent.toLocaleString('tr-TR')}</Table.Td>
              <Table.Td fw={600}>{money(kurus(l.commissionAmount))}</Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Section>
    </Stack>
  );
}
