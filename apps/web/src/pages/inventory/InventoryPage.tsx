import {
  ActionIcon,
  Alert,
  Autocomplete,
  Badge,
  Button,
  Checkbox,
  Group,
  Menu,
  Modal,
  NumberInput,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  TextInput,
  Textarea,
} from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import {
  IconAdjustments,
  IconAlertTriangle,
  IconArrowsExchange,
  IconBell,
  IconDots,
  IconEdit,
  IconHistory,
  IconPackage,
  IconPackageImport,
  IconPlus,
  IconSearch,
  IconStack2,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { PageHeader } from '../../components/PageHeader';
import { COMMISSION_HINT, MOVEMENT_TYPE, isCommissionCategory, toOptions } from '../../components/admin/labels';
import { DataTable, DateRangeInput, Empty, MoneyInput, PagerFooter, PercentInput, StatusBadge } from '../../components/admin/ui';
import { dateOnlyQuery, num, usePager, type Range } from '../../components/admin/utils';
import { api, type Page } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { date, dateTime, money, percent } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

type OpType = 'receive' | 'adjust' | 'transfer';

// ------------------------------------------------------------------ API tipleri (Java: product / inventory DTO'ları)
// Fiyatlar TL (KDV hariç), KDV yüzde, miktarlar ürün biriminde (BigDecimal → number).

/** vet.products.product_type değerleri. */
const PRODUCT_TYPE: Record<string, string> = {
  MEDICATION: 'İlaç',
  VACCINE: 'Aşı',
  FOOD: 'Mama',
  CONSUMABLE: 'Sarf malzeme',
  SUPPLEMENT: 'Takviye',
  EQUIPMENT: 'Ekipman',
  OTHER: 'Diğer',
};
/** Komisyon kuralları ilaç ve mama kategorilerine uygulanır (FG-K4). */
const commissionType = (t: string) => isCommissionCategory(t === 'MEDICATION' ? 'drug' : t === 'FOOD' ? 'food' : t);
const tlMoney = (v: number | null | undefined) => money(Math.round((v ?? 0) * 100));

interface Product {
  id: string;
  sku: string;
  barcode: string | null;
  name: string;
  productType: string;
  baseUnit: string;
  stockItem: boolean;
  tracksLot: boolean;
  minimumStock: number;
  purchasePrice: number;
  salePrice: number | null;
  taxRate: number;
  active: boolean;
  createdAt: string;
}

interface StockLot {
  lotId: string | null;
  lotNumber: string | null;
  expiresAt: string | null;
  quantity: number;
  expired: boolean;
  expiringSoon: boolean;
}

interface StockItem {
  clinicId: string;
  productId: string;
  name: string;
  productType: string;
  barcode: string | null;
  baseUnit: string;
  minimumStock: number;
  tracksLot: boolean;
  onHand: number;
  low: boolean;
  expiringSoon: boolean;
  lots: StockLot[];
}

interface StockAlerts {
  expiryDays: number;
  lowStock: { clinicId: string; productId: string; name: string; onHand: number; minimumStock: number }[];
  expiring: {
    clinicId: string;
    productId: string;
    name: string;
    lotNumber: string | null;
    expiresAt: string;
    quantity: number;
    expired: boolean;
    daysLeft: number;
  }[];
}

interface StockMovement {
  id: string;
  transactionNo: string;
  transactionType: string;
  clinicId: string;
  productName: string;
  lotNumber: string | null;
  quantity: number;
  referenceType: string | null;
  notes: string | null;
  createdByName: string | null;
  occurredAt: string;
}

interface MovementResult {
  transactionId: string;
  stock: { onHand: number; low: boolean };
}

const useActiveProducts = () =>
  useQuery({
    queryKey: ['products', { active: true, size: 200 }],
    queryFn: () => api.get<Page<Product>>('/v1/products', { active: true, size: 200 }).then((p) => p.content),
  });

/** Seçili şubenin (X-Clinic-Id) stoğu. */
const useBranchStock = (branchId: string) =>
  useQuery({ queryKey: ['stock', branchId], queryFn: () => api.get<StockItem[]>('/v1/inventory/stock') });

function invalidateStock(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ['stock'] });
  void qc.invalidateQueries({ queryKey: ['stock-alerts'] });
  void qc.invalidateQueries({ queryKey: ['stock-movements'] });
}

export function InventoryPage() {
  const { can } = useAuth();
  const { branchId, branch } = useBranch();
  const manage = can('inventory.write');
  const [tab, setTab] = useState<string | null>('stock');
  const [op, setOp] = useState<{ type: OpType; productId?: string; lotNo?: string } | null>(null);

  const alerts = useQuery({
    queryKey: ['stock-alerts', branchId],
    queryFn: () => api.get<StockAlerts>('/v1/inventory/alerts'),
    enabled: Boolean(branchId),
  });
  const alertCount = (alerts.data?.lowStock.length ?? 0) + (alerts.data?.expiring.length ?? 0);

  if (!branchId) {
    return (
      <>
        <PageHeader title="Stok" />
        <Empty title="Şube bulunamadı" description="Stok ekranını kullanmak için en az bir şubeye atanmış olmalısınız." />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Stok"
        description={`${branch?.name ?? ''} stok durumu, ürünler ve stok hareketleri`}
        actions={
          manage && (
            <>
              <Button leftSection={<IconPackageImport size={16} />} onClick={() => setOp({ type: 'receive' })}>
                Stok girişi
              </Button>
              <Button variant="default" leftSection={<IconAdjustments size={16} />} onClick={() => setOp({ type: 'adjust' })}>
                Sayım düzeltme
              </Button>
              <Button
                variant="default"
                leftSection={<IconArrowsExchange size={16} />}
                onClick={() => setOp({ type: 'transfer' })}
              >
                Transfer
              </Button>
            </>
          )
        }
      />

      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="stock" leftSection={<IconStack2 size={16} />}>
            Şube stoğu
          </Tabs.Tab>
          <Tabs.Tab value="products" leftSection={<IconPackage size={16} />}>
            Ürünler
          </Tabs.Tab>
          <Tabs.Tab value="movements" leftSection={<IconHistory size={16} />}>
            Hareketler
          </Tabs.Tab>
          <Tabs.Tab
            value="alerts"
            leftSection={<IconBell size={16} />}
            rightSection={
              alertCount > 0 ? (
                <Badge size="xs" color="red" circle>
                  {alertCount}
                </Badge>
              ) : undefined
            }
          >
            Uyarılar
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="stock">
          <StockTab branchId={branchId} manage={manage} onOp={(type, productId, lotNo) => setOp({ type, productId, lotNo })} />
        </Tabs.Panel>
        <Tabs.Panel value="products">
          <ProductsTab manage={manage} />
        </Tabs.Panel>
        <Tabs.Panel value="movements">
          <MovementsTab />
        </Tabs.Panel>
        <Tabs.Panel value="alerts">
          <AlertsTab />
        </Tabs.Panel>
      </Tabs>

      <Modal
        opened={op !== null}
        onClose={() => setOp(null)}
        title={op?.type === 'receive' ? 'Stok girişi' : op?.type === 'adjust' ? 'Sayım düzeltme' : 'Şubeler arası transfer'}
        size="lg"
      >
        {op && (
          <StockOpForm
            key={`${op.type}-${op.productId}`}
            type={op.type}
            branchId={branchId}
            initialProductId={op.productId}
            initialLot={op.lotNo}
            onDone={() => setOp(null)}
          />
        )}
      </Modal>
    </>
  );
}

// ================================================================== şube stoğu

function LotList({ row }: { row: StockItem }) {
  if (!row.lots.length)
    return (
      <Text size="xs" c="dimmed">
        {row.tracksLot ? 'Partili stok yok' : '—'}
      </Text>
    );
  return (
    <Stack gap={2}>
      {row.lots.map((l) => (
        <Text key={l.lotId ?? 'nolot'} size="xs" c={l.expired ? 'red' : l.expiringSoon ? 'orange.8' : undefined}>
          {l.lotNumber ? <b>{l.lotNumber}</b> : 'Partisiz'} · {l.quantity} {row.baseUnit}
          {l.expiresAt && ` · SKT ${date(l.expiresAt)}`}
          {l.expired ? ' (süresi geçti)' : l.expiringSoon ? ' (yaklaşıyor)' : ''}
        </Text>
      ))}
    </Stack>
  );
}

function StockTab({
  branchId,
  manage,
  onOp,
}: {
  branchId: string;
  manage: boolean;
  onOp: (t: OpType, productId: string, lotNo?: string) => void;
}) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const stock = useBranchStock(branchId);

  const rows = useMemo(() => {
    const q = search.trim().toLocaleLowerCase('tr-TR');
    return (stock.data ?? []).filter(
      (r) =>
        (!q || r.name.toLocaleLowerCase('tr-TR').includes(q) || (r.barcode ?? '').includes(q)) &&
        (filter === 'all' || (filter === 'low' && r.low) || (filter === 'expiring' && r.expiringSoon)),
    );
  }, [stock.data, search, filter]);

  const lowCount = stock.data?.filter((r) => r.low).length ?? 0;
  const expCount = stock.data?.filter((r) => r.expiringSoon).length ?? 0;

  return (
    <Stack gap="sm">
      <Group justify="space-between" wrap="wrap">
        <TextInput
          placeholder="Ürün adı veya barkod ara"
          leftSection={<IconSearch size={16} />}
          value={search}
          onChange={(e) => setSearch(e.currentTarget.value)}
          w={{ base: '100%', sm: 300 }}
        />
        <SegmentedControl
          value={filter}
          onChange={setFilter}
          data={[
            { value: 'all', label: 'Tümü' },
            { value: 'low', label: `Düşük stok (${lowCount})` },
            { value: 'expiring', label: `SKT yaklaşan (${expCount})` },
          ]}
        />
      </Group>
      <DataTable
        head={['Ürün', 'Kategori', 'Miktar', 'Minimum', 'Partiler / SKT', 'Durum', '']}
        loading={stock.isLoading}
        error={stock.error}
        empty={rows.length === 0}
        emptyText={stock.data?.length ? 'Filtreye uyan ürün yok' : 'Bu şubede stok kaydı yok'}
        minWidth={900}
      >
        {rows.map((r) => (
          <Table.Tr key={r.productId}>
            <Table.Td>
              <Text size="sm" fw={500}>
                {r.name}
              </Text>
              {r.barcode && (
                <Text size="xs" c="dimmed">
                  {r.barcode}
                </Text>
              )}
            </Table.Td>
            <Table.Td>{PRODUCT_TYPE[r.productType] ?? r.productType}</Table.Td>
            <Table.Td>
              <Text fw={600} c={r.low ? 'red' : undefined}>
                {r.onHand} {r.baseUnit}
              </Text>
            </Table.Td>
            <Table.Td>{r.minimumStock}</Table.Td>
            <Table.Td>
              <LotList row={r} />
            </Table.Td>
            <Table.Td>
              <Group gap={4}>
                {r.low && (
                  <Badge color="red" variant="light">
                    Düşük stok
                  </Badge>
                )}
                {r.lots.some((l) => l.expired) ? (
                  <Badge color="red" variant="filled">
                    SKT geçmiş
                  </Badge>
                ) : (
                  r.expiringSoon && (
                    <Badge color="orange" variant="light">
                      SKT yaklaşıyor
                    </Badge>
                  )
                )}
                {!r.low && !r.expiringSoon && (
                  <Badge color="green" variant="light">
                    Yeterli
                  </Badge>
                )}
              </Group>
            </Table.Td>
            <Table.Td>
              {manage && (
                <Menu position="bottom-end" withinPortal>
                  <Menu.Target>
                    <ActionIcon variant="subtle" color="gray" aria-label="İşlemler">
                      <IconDots size={16} />
                    </ActionIcon>
                  </Menu.Target>
                  <Menu.Dropdown>
                    <Menu.Item leftSection={<IconPackageImport size={14} />} onClick={() => onOp('receive', r.productId)}>
                      Stok girişi
                    </Menu.Item>
                    <Menu.Item leftSection={<IconAdjustments size={14} />} onClick={() => onOp('adjust', r.productId)}>
                      Sayım düzeltme
                    </Menu.Item>
                    <Menu.Item
                      leftSection={<IconArrowsExchange size={14} />}
                      onClick={() => onOp('transfer', r.productId)}
                      disabled={r.onHand <= 0}
                    >
                      Transfer
                    </Menu.Item>
                  </Menu.Dropdown>
                </Menu>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
      <Text size="xs" c="dimmed">
        Listede bu şubede stok kaydı olan veya minimum stok tanımlı aktif ürünler yer alır. SKT'si 30 gün içinde olan partiler
        "yaklaşan" sayılır.
      </Text>
    </Stack>
  );
}

// ================================================================== stok işlemleri

function StockOpForm({
  type,
  branchId,
  initialProductId,
  initialLot,
  onDone,
}: {
  type: OpType;
  branchId: string;
  initialProductId?: string;
  initialLot?: string;
  onDone: () => void;
}) {
  const qc = useQueryClient();
  const { branches } = useBranch();
  const products = useActiveProducts();
  const stock = useBranchStock(branchId);
  const branchName = branches.find((b) => b.id === branchId)?.name;

  const lotsOf = (productId: string | null) => stock.data?.find((r) => r.productId === productId)?.lots ?? [];
  const form = useForm({
    initialValues: {
      productId: initialProductId ?? (null as string | null),
      quantity: 1 as number | string,
      delta: '' as number | string,
      lotNo: initialLot ?? '',
      expiryDate: null as string | null,
      note: '',
      reason: '',
      toBranchId: null as string | null,
    },
    validate: (v) => {
      const p = products.data?.find((x) => x.id === v.productId);
      const errors: Record<string, string> = {};
      if (!v.productId) errors.productId = 'Ürün seçin';
      if (type !== 'adjust' && (!Number.isInteger(num(v.quantity)) || num(v.quantity) < 1))
        errors.quantity = 'Miktar en az 1 olmalı';
      if (type === 'adjust') {
        if (!Number.isInteger(num(v.delta)) || num(v.delta) === 0) errors.delta = 'Fark sıfırdan farklı bir tam sayı olmalı';
        if (v.reason.trim().length < 3) errors.reason = 'Gerekçe en az 3 karakter olmalı';
      }
      if (p?.tracksLot && type === 'receive') {
        if (!v.lotNo.trim()) errors.lotNo = 'Parti takipli üründe parti numarası zorunlu';
        if (!v.expiryDate) errors.expiryDate = 'Parti takipli üründe son kullanma tarihi zorunlu';
      }
      if (p?.tracksLot && type === 'adjust' && !v.lotNo.trim()) errors.lotNo = 'Parti takipli üründe parti numarası zorunlu';
      if (
        p?.tracksLot &&
        type === 'adjust' &&
        num(v.delta) > 0 &&
        v.lotNo.trim() &&
        !lotsOf(v.productId).some((l) => l.lotNumber === v.lotNo.trim()) &&
        !v.expiryDate
      )
        errors.expiryDate = 'Yeni parti için son kullanma tarihi zorunlu';
      if (type === 'transfer' && !v.toBranchId) errors.toBranchId = 'Hedef şubeyi seçin';
      return errors;
    },
  });

  const product = products.data?.find((p) => p.id === form.values.productId);
  const row = stock.data?.find((r) => r.productId === form.values.productId);
  const lots = row?.lots ?? [];
  const existingLot = lots.find((l) => l.lotNumber === form.values.lotNo.trim());

  // Şube = X-Clinic-Id (seçili şube); kaynak şube istek gövdesinde gönderilmez.
  const mutation = useMutation({
    mutationFn: async (v: typeof form.values) => {
      const lotNumber = product?.tracksLot ? v.lotNo.trim() || null : null;
      if (type === 'receive') {
        const r = await api.post<MovementResult>('/v1/inventory/receipts', {
          productId: v.productId,
          quantity: num(v.quantity),
          lotNumber,
          expiresAt: product?.tracksLot ? v.expiryDate : null,
          notes: v.note.trim() || null,
        });
        return r.stock;
      }
      if (type === 'adjust') {
        const r = await api.post<MovementResult>('/v1/inventory/adjustments', {
          productId: v.productId,
          lotNumber,
          quantityDelta: num(v.delta),
          reason: v.reason.trim(),
          expiresAt: product?.tracksLot && num(v.delta) > 0 && !existingLot ? v.expiryDate : null,
        });
        return r.stock;
      }
      const r = await api.post<{ source: MovementResult; target: MovementResult }>('/v1/inventory/transfers', {
        targetClinicId: v.toBranchId,
        productId: v.productId,
        quantity: num(v.quantity),
        lotNumber,
        notes: v.note.trim() || null,
      });
      return r.source.stock;
    },
    onSuccess: (r) => {
      invalidateStock(qc);
      notifySuccess(
        type === 'receive' ? 'Stok girişi kaydedildi' : type === 'adjust' ? 'Sayım düzeltmesi kaydedildi' : 'Transfer tamamlandı',
      );
      if (r.low)
        notifications.show({
          color: 'yellow',
          title: 'Düşük stok',
          message: `${product?.name} stoğu minimum seviyede (${r.onHand})`,
        });
      onDone();
    },
    onError: notifyError,
  });

  const productOptions = (products.data ?? []).map((p) => ({
    value: p.id,
    label: `${p.name}${p.barcode ? ` (${p.barcode})` : ''}`,
  }));
  const otherBranches = branches.filter((b) => b.id !== branchId && b.active).map((b) => ({ value: b.id, label: b.name }));

  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <Text size="sm" c="dimmed">
          Şube: <b>{branchName}</b>
        </Text>
        <Select
          label="Ürün"
          placeholder="Ürün seçin"
          searchable
          required
          data={productOptions}
          nothingFoundMessage="Ürün bulunamadı"
          {...form.getInputProps('productId')}
          onChange={(v) => {
            form.setFieldValue('productId', v);
            form.setFieldValue('lotNo', '');
          }}
        />
        {product && (
          <Group gap="xs">
            <Badge variant="light">
              Mevcut: {row?.onHand ?? 0} {product.baseUnit}
            </Badge>
            {product.tracksLot && (
              <Badge variant="light" color="grape">
                Parti takipli
              </Badge>
            )}
            <Badge variant="light" color="gray">
              Minimum: {product.minimumStock}
            </Badge>
          </Group>
        )}

        {type === 'transfer' && (
          <>
            {otherBranches.length === 0 && (
              <Alert color="yellow" icon={<IconAlertTriangle size={16} />}>
                Transfer yapabileceğiniz başka aktif şube yok.
              </Alert>
            )}
            <Select
              label="Hedef şube"
              placeholder="Şube seçin"
              required
              data={otherBranches}
              {...form.getInputProps('toBranchId')}
            />
          </>
        )}

        {/* Parti alanı */}
        {product?.tracksLot && type === 'receive' && (
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <Autocomplete
              label="Parti numarası"
              required
              data={lots.flatMap((l) => (l.lotNumber ? [l.lotNumber] : []))}
              placeholder="ör. L2026-11"
              {...form.getInputProps('lotNo')}
            />
            <DatePickerInput
              label="Son kullanma tarihi"
              required
              valueFormat="DD.MM.YYYY"
              placeholder="Tarih seçin"
              minDate={dayjs().format('YYYY-MM-DD')}
              {...form.getInputProps('expiryDate')}
            />
          </SimpleGrid>
        )}
        {product?.tracksLot && type === 'adjust' && (
          <Autocomplete
            label="Parti numarası"
            required
            description="Mevcut bir partiyi seçin; sayım fazlası için yeni parti numarası da yazabilirsiniz."
            data={lots.flatMap((l) => (l.lotNumber ? [{ value: l.lotNumber, label: `${l.lotNumber} (${l.quantity} ad.)` }] : []))}
            {...form.getInputProps('lotNo')}
          />
        )}
        {product?.tracksLot && type === 'transfer' && (
          <Select
            label="Parti"
            placeholder="Otomatik (SKT'si en yakın partiden)"
            clearable
            data={lots.flatMap((l) =>
              l.lotNumber
                ? [
                    {
                      value: l.lotNumber,
                      label: `${l.lotNumber} · ${l.quantity} ad.${l.expiresAt ? ` · SKT ${date(l.expiresAt)}` : ''}`,
                    },
                  ]
                : [],
            )}
            value={form.values.lotNo || null}
            onChange={(v) => form.setFieldValue('lotNo', v ?? '')}
          />
        )}

        {type === 'adjust' ? (
          <>
            <NumberInput
              label="Sayım farkı"
              description="Fazla için pozitif (ör. 3), eksik için negatif (ör. -2) girin."
              required
              allowDecimal={false}
              {...form.getInputProps('delta')}
            />
            {product?.tracksLot && num(form.values.delta) > 0 && form.values.lotNo.trim() && !existingLot && (
              <DatePickerInput
                label="Son kullanma tarihi (yeni parti)"
                required
                valueFormat="DD.MM.YYYY"
                clearable
                {...form.getInputProps('expiryDate')}
              />
            )}
            <Textarea
              label="Gerekçe"
              required
              placeholder="ör. Ay sonu sayımı, kırık ambalaj"
              autosize
              minRows={2}
              {...form.getInputProps('reason')}
            />
          </>
        ) : (
          <>
            <NumberInput
              label="Miktar"
              required
              min={1}
              allowDecimal={false}
              allowNegative={false}
              {...form.getInputProps('quantity')}
            />
            <TextInput
              label="Not"
              placeholder={type === 'receive' ? 'ör. Tedarikçi irsaliye no' : 'ör. Acil ihtiyaç'}
              {...form.getInputProps('note')}
            />
          </>
        )}

        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onDone}>
            Vazgeç
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={type === 'transfer' && otherBranches.length === 0}>
            Kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

// ================================================================== ürünler

function ProductsTab({ manage }: { manage: boolean }) {
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [category, setCategory] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('true');
  const [editing, setEditing] = useState<Product | 'new' | null>(null);

  const params = {
    search: debounced.trim() || undefined,
    productType: category ?? undefined,
    active: status === 'all' ? undefined : status,
    size: 200,
  };
  const products = useQuery({
    queryKey: ['products', params],
    queryFn: () => api.get<Page<Product>>('/v1/products', params).then((p) => p.content),
  });

  return (
    <Stack gap="sm">
      <Group justify="space-between" wrap="wrap">
        <Group gap="sm" wrap="wrap">
          <TextInput
            placeholder="Ad veya barkod ara"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            w={{ base: '100%', sm: 260 }}
          />
          <Select
            placeholder="Kategori"
            clearable
            data={toOptions(PRODUCT_TYPE)}
            value={category}
            onChange={setCategory}
            w={170}
          />
          <SegmentedControl
            value={status}
            onChange={setStatus}
            data={[
              { value: 'true', label: 'Aktif' },
              { value: 'false', label: 'Pasif' },
              { value: 'all', label: 'Tümü' },
            ]}
          />
        </Group>
        {manage && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
            Yeni ürün
          </Button>
        )}
      </Group>
      <DataTable
        head={['Ürün', 'Kategori', 'Alış', 'Satış (KDV hariç)', 'KDV', 'Min. stok', 'Özellikler', 'Durum', '']}
        loading={products.isLoading}
        error={products.error}
        empty={!products.data?.length}
        emptyText="Ürün bulunamadı"
        minWidth={1000}
      >
        {products.data?.map((p) => (
          <Table.Tr key={p.id}>
            <Table.Td>
              <Text size="sm" fw={500}>
                {p.name}
              </Text>
              <Text size="xs" c="dimmed">
                {p.barcode ?? 'Barkod yok'} · {p.baseUnit}
              </Text>
            </Table.Td>
            <Table.Td>{PRODUCT_TYPE[p.productType] ?? p.productType}</Table.Td>
            <Table.Td>{tlMoney(p.purchasePrice)}</Table.Td>
            <Table.Td fw={500}>{tlMoney(p.salePrice)}</Table.Td>
            <Table.Td>{percent(Math.round(p.taxRate * 100))}</Table.Td>
            <Table.Td>{p.minimumStock}</Table.Td>
            <Table.Td>
              <Group gap={4}>
                {p.tracksLot && (
                  <Badge size="sm" variant="light" color="grape">
                    Parti/SKT
                  </Badge>
                )}
                {commissionType(p.productType) && (
                  <Badge size="sm" variant="light" color="indigo" title={COMMISSION_HINT}>
                    Komisyona tabi (platform kuralı)
                  </Badge>
                )}
              </Group>
            </Table.Td>
            <Table.Td>
              <Badge variant="light" color={p.active ? 'green' : 'gray'}>
                {p.active ? 'Aktif' : 'Pasif'}
              </Badge>
            </Table.Td>
            <Table.Td>
              {manage && (
                <ActionIcon variant="subtle" onClick={() => setEditing(p)} aria-label="Düzenle">
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
        title={editing === 'new' ? 'Yeni ürün' : 'Ürünü düzenle'}
        size="lg"
      >
        {editing && <ProductForm product={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Modal>
    </Stack>
  );
}

function ProductForm({ product, onDone }: { product: Product | null; onDone: () => void }) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      name: product?.name ?? '',
      category: product?.productType ?? 'MEDICATION',
      barcode: product?.barcode ?? '',
      unit: product?.baseUnit ?? 'adet',
      purchasePrice: (product?.purchasePrice ?? 0) as number | string,
      salePrice: (product?.salePrice ?? '') as number | string,
      taxPercent: (product?.taxRate ?? 20) as number | string,
      minStock: (product?.minimumStock ?? 0) as number | string,
      trackLots: product?.tracksLot ?? false,
      active: product?.active ?? true,
    },
    validate: {
      name: (v) => (v.trim() ? null : 'Ürün adı zorunlu'),
      unit: (v) => (v.trim() ? null : 'Birim zorunlu'),
      salePrice: (v) => (v === '' ? 'Satış fiyatı zorunlu' : null),
      taxPercent: (v) => (num(v) < 0 || num(v) > 100 ? 'KDV %0–100 arası olmalı' : null),
    },
  });

  const mutation = useMutation({
    mutationFn: (v: typeof form.values) => {
      const body = {
        name: v.name.trim(),
        productType: v.category,
        baseUnit: v.unit.trim(),
        purchasePrice: num(v.purchasePrice),
        salePrice: num(v.salePrice),
        taxRate: num(v.taxPercent),
        minimumStock: Math.trunc(num(v.minStock)),
        tracksLot: v.trackLots,
      };
      // Güncellemede boş barkod ("") barkodu siler; yeni üründe barkodsuz kayıt için null.
      return product
        ? api.patch<Product>(`/v1/products/${product.id}`, { ...body, barcode: v.barcode.trim(), active: v.active })
        : api.post<Product>('/v1/products', { ...body, barcode: v.barcode.trim() || null });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['products'] });
      void qc.invalidateQueries({ queryKey: ['stock'] });
      notifySuccess(product ? 'Ürün güncellendi' : 'Ürün oluşturuldu');
      onDone();
    },
    onError: notifyError,
  });


  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <TextInput label="Ürün adı" required {...form.getInputProps('name')} />
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <Select
            label="Kategori"
            required
            allowDeselect={false}
            data={toOptions(PRODUCT_TYPE)}
            {...form.getInputProps('category')}
          />
          <TextInput label="Barkod" placeholder="İsteğe bağlı" {...form.getInputProps('barcode')} />
          <TextInput label="Birim" required placeholder="adet, kutu, kg…" {...form.getInputProps('unit')} />
        </SimpleGrid>
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <MoneyInput label="Alış fiyatı" {...form.getInputProps('purchasePrice')} />
          <MoneyInput label="Satış fiyatı (KDV hariç)" required {...form.getInputProps('salePrice')} />
          <PercentInput label="KDV oranı" {...form.getInputProps('taxPercent')} />
        </SimpleGrid>
        <NumberInput
          label="Minimum stok"
          description="Şube stoğu bu değere inince düşük stok uyarısı verilir."
          min={0}
          allowDecimal={false}
          allowNegative={false}
          {...form.getInputProps('minStock')}
        />
        <Checkbox
          label="Parti (lot) ve son kullanma tarihi takibi"
          description="İlaç ve aşı gibi SKT'li ürünlerde stok girişinde parti no ve SKT zorunlu olur."
          {...form.getInputProps('trackLots', { type: 'checkbox' })}
        />
        <Group gap="xs" wrap="nowrap" align="flex-start">
          {commissionType(form.values.category) ? (
            <Badge variant="light" color="indigo" style={{ flexShrink: 0 }}>
              Komisyona tabi (platform kuralı)
            </Badge>
          ) : (
            <Badge variant="light" color="gray" style={{ flexShrink: 0 }}>
              Komisyonsuz kategori
            </Badge>
          )}
          <Text size="xs" c="dimmed">
            {COMMISSION_HINT}
          </Text>
        </Group>
        {product && (
          <Switch
            label="Aktif (pasif ürün satışa ve stok işlemine kapalıdır)"
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

// ================================================================== hareketler

function MovementsTab() {
  const { branches, branchId } = useBranch();
  const products = useActiveProducts();
  const [branchFilter, setBranchFilter] = useState<string | null>(branchId);
  const [productId, setProductId] = useState<string | null>(null);
  const [range, setRange] = useState<Range>([null, null]);
  const pager = usePager(50);

  const params = {
    clinicId: branchFilter ?? undefined,
    productId: productId ?? undefined,
    ...dateOnlyQuery(range),
    size: pager.limit,
    page: pager.page,
  };
  const movements = useQuery({
    queryKey: ['stock-movements', params],
    queryFn: () => api.get<Page<StockMovement>>('/v1/inventory/movements', params).then((p) => p.content),
  });
  const branchName = (id: string) => branches.find((b) => b.id === id)?.name ?? '—';

  return (
    <Stack gap="sm">
      <Group align="flex-end" gap="sm" wrap="wrap">
        <Select
          label="Şube"
          placeholder="Tüm şubelerim"
          clearable
          data={branches.map((b) => ({ value: b.id, label: b.name }))}
          value={branchFilter}
          onChange={(v) => {
            setBranchFilter(v);
            pager.reset();
          }}
          w={200}
        />
        <Select
          label="Ürün"
          placeholder="Tüm ürünler"
          clearable
          searchable
          data={(products.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          value={productId}
          onChange={(v) => {
            setProductId(v);
            pager.reset();
          }}
          w={240}
        />
        <DateRangeInput
          value={range}
          onChange={(v) => {
            setRange(v);
            pager.reset();
          }}
        />
      </Group>
      <DataTable
        head={['Tarih', 'Şube', 'Ürün', 'Parti', 'Tür', 'Miktar', 'Referans', 'Kullanıcı']}
        loading={movements.isLoading}
        error={movements.error}
        empty={!movements.data?.length}
        emptyText="Stok hareketi bulunamadı"
        minWidth={950}
        footer={<PagerFooter pager={pager} count={movements.data?.length ?? 0} />}
      >
        {movements.data?.map((m) => (
          <Table.Tr key={m.id}>
            <Table.Td>{dateTime(m.occurredAt)}</Table.Td>
            <Table.Td>{branchName(m.clinicId)}</Table.Td>
            <Table.Td>{m.productName}</Table.Td>
            <Table.Td>{m.lotNumber || '—'}</Table.Td>
            <Table.Td>
              <StatusBadge map={MOVEMENT_TYPE} value={m.transactionType} />
            </Table.Td>
            <Table.Td>
              <Text fw={600} c={m.quantity > 0 ? 'teal.8' : 'red.8'}>
                {m.quantity > 0 ? `+${m.quantity}` : m.quantity}
              </Text>
            </Table.Td>
            <Table.Td>
              <Text size="sm" lineClamp={2}>
                {m.notes ?? (m.referenceType === 'INVOICE' ? 'Fatura' : m.transactionNo)}
              </Text>
            </Table.Td>
            <Table.Td>{m.createdByName ?? '—'}</Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
    </Stack>
  );
}

// ================================================================== uyarılar

function AlertsTab() {
  const { branchId, branches } = useBranch();
  const [scope, setScope] = useState('branch');
  const alerts = useQuery({
    queryKey: ['stock-alerts', scope === 'branch' ? branchId : 'all'],
    queryFn: () => api.get<StockAlerts>('/v1/inventory/alerts', { allClinics: scope === 'all' }),
  });
  const branchName = (id: string) => branches.find((b) => b.id === id)?.name ?? '—';

  return (
    <Stack gap="lg">
      <SegmentedControl
        w="fit-content"
        value={scope}
        onChange={setScope}
        data={[
          { value: 'branch', label: 'Seçili şube' },
          { value: 'all', label: 'Tüm şubelerim' },
        ]}
      />
      <Stack gap="xs">
        <Group gap="xs">
          <IconAlertTriangle size={18} color="var(--mantine-color-red-6)" />
          <Text fw={600}>Düşük stok</Text>
          <Badge color="red" variant="light">
            {alerts.data?.lowStock.length ?? 0}
          </Badge>
        </Group>
        <DataTable
          head={['Ürün', 'Şube', 'Mevcut', 'Minimum']}
          loading={alerts.isLoading}
          error={alerts.error}
          empty={!alerts.data?.lowStock.length}
          emptyText="Minimumun altında ürün yok"
          minWidth={500}
        >
          {alerts.data?.lowStock.map((a) => (
            <Table.Tr key={`${a.clinicId}-${a.productId}`}>
              <Table.Td fw={500}>{a.name}</Table.Td>
              <Table.Td>{branchName(a.clinicId)}</Table.Td>
              <Table.Td>
                <Text c="red" fw={600}>
                  {a.onHand}
                </Text>
              </Table.Td>
              <Table.Td>{a.minimumStock}</Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Stack>
      <Stack gap="xs">
        <Group gap="xs">
          <IconAlertTriangle size={18} color="var(--mantine-color-orange-6)" />
          <Text fw={600}>Son kullanma tarihi yaklaşan / geçen partiler</Text>
          <Badge color="orange" variant="light">
            {alerts.data?.expiring.length ?? 0}
          </Badge>
        </Group>
        <DataTable
          head={['Ürün', 'Şube', 'Parti', 'Miktar', 'SKT', 'Durum']}
          loading={alerts.isLoading}
          empty={!alerts.data?.expiring.length}
          emptyText="SKT'si yaklaşan parti yok"
          minWidth={650}
        >
          {alerts.data?.expiring.map((a) => (
            <Table.Tr key={`${a.clinicId}-${a.productId}-${a.lotNumber}`}>
              <Table.Td fw={500}>{a.name}</Table.Td>
              <Table.Td>{branchName(a.clinicId)}</Table.Td>
              <Table.Td>{a.lotNumber || '—'}</Table.Td>
              <Table.Td>{a.quantity}</Table.Td>
              <Table.Td>{date(a.expiresAt)}</Table.Td>
              <Table.Td>
                {a.expired ? (
                  <Badge color="red">Süresi geçti</Badge>
                ) : (
                  <Badge color="orange" variant="light">
                    {a.daysLeft} gün kaldı
                  </Badge>
                )}
              </Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Stack>
    </Stack>
  );
}
