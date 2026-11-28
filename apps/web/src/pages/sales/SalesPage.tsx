import { Alert, Button, Group, Modal, Select, Stack, Table, Text } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPlus, IconReceipt } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { SaleItemsEditor } from '../../components/admin/SaleItemsEditor';
import { draftsToPayload, validateDrafts, type ItemDraft } from '../../components/admin/saleDrafts';
import { PAYMENT_STATUS, SALE_STATUS, toOptions } from '../../components/admin/labels';
import { createSale, listSales } from '../../components/admin/salesApi';
import type { SaleStatus } from '../../components/admin/types';
import { DataTable, DateRangeInput, PagerFooter, StatusBadge } from '../../components/admin/ui';
import { dateOnlyQuery, useDateRange, usePager } from '../../components/admin/utils';
import { getOwnerDetail, listOwners } from '../../components/clinical/patientApi';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { SPECIES, dateTime, money } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

export function SalesPage() {
  const { can } = useAuth();
  const { branches, branchId } = useBranch();
  const navigate = useNavigate();
  const [building, setBuilding] = useState(false);
  const [branchFilter, setBranchFilter] = useState<string | null>(branchId);
  const [status, setStatus] = useState<string | null>(null);
  const [range, setRange] = useDateRange(30);
  const pager = usePager(50);

  const params = {
    branchId: branchFilter ?? undefined,
    status: (status ?? undefined) as SaleStatus | undefined,
    ...dateOnlyQuery(range),
    limit: pager.limit,
    offset: pager.offset,
  };
  const sales = useQuery({ queryKey: ['sales', params], queryFn: () => listSales(params) });
  const branchName = (id: string) => branches.find((b) => b.id === id)?.name ?? '—';
  const filtersChanged =
    <T,>(set: (v: T) => void) =>
    (v: T) => {
      set(v);
      pager.reset();
    };

  return (
    <>
      <PageHeader
        title="Satış ve fatura"
        description="Satışlar, faturalar ve tahsilatlar"
        actions={
          can('billing.manage') && (
            <Button leftSection={<IconPlus size={16} />} onClick={() => setBuilding(true)} disabled={!branchId}>
              Yeni satış
            </Button>
          )
        }
      />
      <Stack gap="sm">
        <Group align="flex-end" gap="sm" wrap="wrap">
          <Select
            label="Şube"
            placeholder="Tüm şubelerim"
            clearable
            data={branches.map((b) => ({ value: b.id, label: b.name }))}
            value={branchFilter}
            onChange={filtersChanged(setBranchFilter)}
            w={200}
          />
          <Select
            label="Durum"
            placeholder="Tümü"
            clearable
            data={toOptions(SALE_STATUS)}
            value={status}
            onChange={filtersChanged(setStatus)}
            w={160}
          />
          <DateRangeInput value={range} onChange={filtersChanged(setRange)} />
        </Group>
        <DataTable
          head={['Fatura no', 'Tarih', 'Şube', 'Müşteri', 'Toplam', 'Ödenen', 'Durum', 'Ödeme']}
          loading={sales.isLoading}
          error={sales.error}
          empty={!sales.data?.length}
          emptyText="Bu filtrelerle satış bulunamadı"
          minWidth={900}
          footer={<PagerFooter pager={pager} count={sales.data?.length ?? 0} />}
        >
          {sales.data?.map((s) => (
            <Table.Tr key={s.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/sales/${s.id}`)}>
              <Table.Td>
                <Group gap={6} wrap="nowrap">
                  <IconReceipt size={16} color="var(--mantine-color-gray-6)" />
                  <Text size="sm" fw={600}>
                    {s.status === 'draft' ? 'Taslak' : s.number}
                  </Text>
                  {s.channel === 'mobile' && (
                    <Text size="xs" c="dimmed">
                      (mobil)
                    </Text>
                  )}
                </Group>
              </Table.Td>
              <Table.Td>{dateTime(s.issuedAt ?? s.createdAt)}</Table.Td>
              <Table.Td>{branchName(s.branchId)}</Table.Td>
              <Table.Td>
                {s.ownerName ?? (
                  <Text c="dimmed" size="sm">
                    Peşin müşteri
                  </Text>
                )}
              </Table.Td>
              <Table.Td fw={600}>{money(s.totalKurus)}</Table.Td>
              <Table.Td>{money(s.paidKurus)}</Table.Td>
              <Table.Td>
                <StatusBadge map={SALE_STATUS} value={s.status} />
              </Table.Td>
              <Table.Td>{s.status === 'issued' ? <StatusBadge map={PAYMENT_STATUS} value={s.paymentStatus} /> : '—'}</Table.Td>
            </Table.Tr>
          ))}
        </DataTable>
      </Stack>

      <Modal opened={building} onClose={() => setBuilding(false)} title="Yeni satış" size="85rem" closeOnClickOutside={false}>
        {building && branchId && <SaleBuilder branchId={branchId} onCancel={() => setBuilding(false)} />}
      </Modal>
    </>
  );
}

function SaleBuilder({ branchId, onCancel }: { branchId: string; onCancel: () => void }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { branch } = useBranch();
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [petId, setPetId] = useState<string | null>(null);
  const [ownerSearch, setOwnerSearch] = useState('');
  const [debounced] = useDebouncedValue(ownerSearch, 300);
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [error, setError] = useState<string | null>(null);

  const owners = useQuery({
    queryKey: ['owners', { search: debounced }],
    queryFn: () => listOwners(debounced || undefined, 0, 20),
  });
  const pets = useQuery({
    queryKey: ['owner', ownerId, 'pets'],
    queryFn: () => getOwnerDetail(ownerId!).then((o) => o.pets),
    enabled: Boolean(ownerId),
  });

  const ownerOptions = (owners.data ?? []).map((o) => ({ value: o.id, label: `${o.fullName} · ${o.phone}` }));
  if (ownerId && ownerLabel && !ownerOptions.some((o) => o.value === ownerId))
    ownerOptions.unshift({ value: ownerId, label: ownerLabel });

  const save = useMutation({
    // Şube X-Clinic-Id başlığından gelir (seçili şube); faturada müşteri zorunludur.
    mutationFn: () => createSale({ clientId: ownerId!, animalId: petId, lines: draftsToPayload(items) }),
    onSuccess: (sale) => {
      void qc.invalidateQueries({ queryKey: ['sales'] });
      notifySuccess('Satış taslak olarak kaydedildi');
      navigate(`/sales/${sale.id}`);
    },
    onError: notifyError,
  });

  const submit = () => {
    const problem = !ownerId
      ? 'Hayvan sahibi seçin'
      : items.length === 0
        ? 'En az bir kalem ekleyin'
        : validateDrafts(items);
    setError(problem);
    if (!problem) save.mutate();
  };

  return (
    <Stack>
      <Text size="sm" c="dimmed">
        Şube: <b>{branch?.name}</b> · Satış taslak olarak kaydedilir; stok düşümü ve fatura numarası "Faturayı kes" adımında
        oluşur.
      </Text>
      <Group align="flex-end" gap="sm" wrap="wrap">
        <Select
          label="Hayvan sahibi"
          placeholder="Ad, telefon veya e-posta ile ara"
          required
          searchable
          clearable
          w={{ base: '100%', sm: 340 }}
          data={ownerOptions}
          value={ownerId}
          searchValue={ownerSearch}
          onSearchChange={setOwnerSearch}
          filter={({ options }) => options}
          onChange={(v, opt) => {
            setOwnerId(v);
            setOwnerLabel(opt?.label ?? null);
            setPetId(null);
          }}
          nothingFoundMessage={owners.isFetching ? 'Aranıyor…' : 'Sahip bulunamadı'}
        />
        <Select
          label="Hasta (isteğe bağlı)"
          placeholder={ownerId ? 'Hasta seçin' : 'Önce sahip seçin'}
          clearable
          disabled={!ownerId}
          w={{ base: '100%', sm: 260 }}
          data={(pets.data ?? []).map((p) => ({ value: p.id, label: `${p.name} (${SPECIES[p.species] ?? p.species})` }))}
          value={petId}
          onChange={setPetId}
        />
      </Group>

      <SaleItemsEditor value={items} onChange={setItems} branchId={branchId} />

      {error && <Alert color="red">{error}</Alert>}
      <Group justify="flex-end">
        <Button variant="default" onClick={onCancel}>
          Vazgeç
        </Button>
        <Button onClick={submit} loading={save.isPending}>
          Taslak olarak kaydet
        </Button>
      </Group>
    </Stack>
  );
}
