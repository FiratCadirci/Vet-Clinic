import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Group,
  Modal,
  Paper,
  Progress,
  ScrollArea,
  Select,
  SimpleGrid,
  Skeleton,
  Stack,
  Table,
  Text,
  Title,
  Tooltip,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconCash,
  IconEdit,
  IconFileTypePdf,
  IconPackageOff,
  IconReceipt2,
  IconX,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { SaleItemsEditor } from '../../components/admin/SaleItemsEditor';
import {
  draftFromItem,
  draftsToPayload,
  useSaleCatalog,
  validateDrafts,
  type ItemDraft,
} from '../../components/admin/saleDrafts';
import { PAYMENT_METHOD, PAYMENT_STATUS, SALE_STATUS, toOptions } from '../../components/admin/labels';
import {
  cancelSale,
  getSale,
  getSaleCommissions,
  issueSale,
  openSalePdf,
  paySale,
  replaceSaleLines,
  type SaleView,
} from '../../components/admin/salesApi';
import { getOwnerDetail } from '../../components/clinical/patientApi';
import { Empty, ErrorAlert, MoneyInput, StatusBadge } from '../../components/admin/ui';
import { kurusToTl, num } from '../../components/admin/utils';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { PRODUCT_CATEGORY, SPECIES, dateTime, money, percent, toKurus } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';


export function SaleDetailPage() {
  const { id = '' } = useParams();
  const { can } = useAuth();
  const { branches } = useBranch();
  const qc = useQueryClient();
  const manage = can('billing.manage');
  const seeCommissions = can('commissions.read');
  const [editing, setEditing] = useState(false);
  const [paying, setPaying] = useState(false);
  const [stockError, setStockError] = useState<string | null>(null);

  const sale = useQuery({ queryKey: ['sale', id], queryFn: () => getSale(id) });
  const commissions = useQuery({
    queryKey: ['sale', id, 'commissions'],
    queryFn: () => getSaleCommissions(id),
    enabled: seeCommissions && Boolean(sale.data) && sale.data?.status !== 'draft',
  });
  const owner = useQuery({
    queryKey: ['owner', sale.data?.ownerId],
    queryFn: () => getOwnerDetail(sale.data!.ownerId!),
    enabled: Boolean(sale.data?.ownerId) && can('clients.read'),
  });

  const onSaleChanged = (s: SaleView) => {
    qc.setQueryData(['sale', id], s);
    void qc.invalidateQueries({ queryKey: ['sales'] });
    void qc.invalidateQueries({ queryKey: ['sale', id, 'commissions'] });
  };

  const issue = useMutation({
    mutationFn: () => issueSale(id),
    onSuccess: (s) => {
      setStockError(null);
      onSaleChanged(s);
      void qc.invalidateQueries({ queryKey: ['stock'] });
      void qc.invalidateQueries({ queryKey: ['stock-alerts'] });
      void qc.invalidateQueries({ queryKey: ['stock-movements'] });
      notifySuccess(`Fatura kesildi: ${s.number}`);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409 && e.code === 'INSUFFICIENT_STOCK') setStockError(e.message);
      else notifyError(e);
    },
  });

  const cancel = useMutation({
    mutationFn: () => cancelSale(id),
    onSuccess: (s) => {
      onSaleChanged(s);
      void qc.invalidateQueries({ queryKey: ['stock'] });
      void qc.invalidateQueries({ queryKey: ['stock-movements'] });
      notifySuccess('Satış iptal edildi');
    },
    onError: notifyError,
  });

  const pdf = useMutation({ mutationFn: () => openSalePdf(id), onError: notifyError });

  if (sale.isLoading) {
    return (
      <Stack>
        <Skeleton h={40} w={300} />
        <Skeleton h={220} />
        <Skeleton h={160} />
      </Stack>
    );
  }
  if (sale.error || !sale.data) {
    return (
      <Stack>
        <BackLink />
        {sale.error ? <ErrorAlert error={sale.error} /> : <Empty title="Satış bulunamadı" />}
      </Stack>
    );
  }

  const s = sale.data;
  const isDraft = s.status === 'draft';
  const isIssued = s.status === 'issued';
  const remaining = s.totalKurus - s.paidKurus;
  const pet = owner.data?.pets.find((p) => p.id === s.petId);
  const itemById = new Map(s.items.map((i) => [i.id, i]));
  const saleCommissions = commissions.data ?? [];

  const confirmCancel = () =>
    modals.openConfirmModal({
      title: 'Satışı iptal et',
      children: (
        <Text size="sm">
          Taslak satış iptal edilecek. Devam edilsin mi?
        </Text>
      ),
      labels: { confirm: 'İptal et', cancel: 'Vazgeç' },
      confirmProps: { color: 'red' },
      onConfirm: () => cancel.mutate(),
    });

  const confirmIssue = () =>
    modals.openConfirmModal({
      title: 'Faturayı kes',
      children: (
        <Text size="sm">
          {money(s.totalKurus)} tutarındaki satış faturalanacak. Ürün kalemleri şube stoğundan düşülecek ve fatura numarası
          verilecek. Bu işlemden sonra kalemler değiştirilemez.
        </Text>
      ),
      labels: { confirm: 'Faturayı kes', cancel: 'Vazgeç' },
      onConfirm: () => issue.mutate(),
    });

  return (
    <Stack gap="lg">
      <BackLink />
      <Group justify="space-between" align="flex-start" wrap="wrap">
        <Stack gap={4}>
          <Group gap="sm">
            <Title order={2}>{isDraft ? 'Taslak satış' : `Fatura ${s.number}`}</Title>
            <StatusBadge map={SALE_STATUS} value={s.status} />
            {isIssued && <StatusBadge map={PAYMENT_STATUS} value={s.paymentStatus} />}
          </Group>
          <Text size="sm" c="dimmed">
            {branches.find((b) => b.id === s.branchId)?.name ?? 'Şube'} · Oluşturma {dateTime(s.createdAt)}
            {s.issuedAt && ` · Kesim ${dateTime(s.issuedAt)}`}
            {s.channel === 'mobile' && ' · Mobil uygulama'}
          </Text>
        </Stack>
        <Group gap="sm">
          <Button
            variant="default"
            leftSection={<IconFileTypePdf size={16} />}
            loading={pdf.isPending}
            onClick={() => pdf.mutate()}
          >
            PDF
          </Button>
          {manage && isDraft && (
            <>
              <Button variant="default" leftSection={<IconEdit size={16} />} onClick={() => setEditing(true)}>
                Kalemleri düzenle
              </Button>
              <Button
                leftSection={<IconReceipt2 size={16} />}
                loading={issue.isPending}
                disabled={s.items.length === 0}
                onClick={confirmIssue}
              >
                Faturayı kes
              </Button>
            </>
          )}
          {manage && isIssued && s.paymentStatus !== 'paid' && (
            <Button color="teal" leftSection={<IconCash size={16} />} onClick={() => setPaying(true)}>
              Ödeme al
            </Button>
          )}
          {/* İptal yalnız taslakta: kesilmiş fatura için iade / alacak dekontu gerekir. */}
          {manage && isDraft && (
            <Tooltip label="Ödemesi alınmış satış iptal edilemez" disabled={s.paidKurus === 0}>
              <Button
                variant="light"
                color="red"
                leftSection={<IconX size={16} />}
                loading={cancel.isPending}
                disabled={s.paidKurus > 0}
                onClick={confirmCancel}
              >
                İptal et
              </Button>
            </Tooltip>
          )}
        </Group>
      </Group>

      {stockError && (
        <Alert
          color="orange"
          variant="light"
          icon={<IconPackageOff size={20} />}
          title="Fatura kesilemedi — yetersiz stok"
          withCloseButton
          onClose={() => setStockError(null)}
        >
          <Text size="sm">
            {stockError.replace('Yetersiz stok: ', '').replace(/. Stok sıfırın altına inemez.$/, '')} ürünü için şubede yeterli stok yok.
            Hiçbir değişiklik yapılmadı.
          </Text>
          <Group gap="sm" mt="xs">
            {can('inventory.write') && (
              <Anchor component={Link} to="/inventory" size="sm">
                Stok girişi yap
              </Anchor>
            )}
            {manage && (
              <Anchor component="button" size="sm" onClick={() => setEditing(true)}>
                Kalemleri düzenle
              </Anchor>
            )}
          </Group>
        </Alert>
      )}

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <Card withBorder radius="md">
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            Müşteri
          </Text>
          {s.ownerId ? (
            <>
              <Anchor component={Link} to={`/owners/${s.ownerId}`} fw={600}>
                {owner.data?.fullName ?? s.ownerName ?? 'Hayvan sahibi'}
              </Anchor>
              {owner.data && (
                <Text size="sm" c="dimmed">
                  {owner.data.phone}
                </Text>
              )}
            </>
          ) : (
            <Text fw={600}>Peşin müşteri</Text>
          )}
          {s.petId && (
            <Text size="sm" mt={4}>
              Hasta:{' '}
              <Anchor component={Link} to={`/patients/${s.petId}`}>
                {pet ? `${pet.name} (${SPECIES[pet.species] ?? pet.species})` : (s.petName ?? 'Hasta kaydı')}
              </Anchor>
            </Text>
          )}
          {s.examinationId && (
            <Text size="sm">
              <Anchor component={Link} to={`/examinations/${s.examinationId}`}>
                İlgili muayene
              </Anchor>
            </Text>
          )}
        </Card>
        <Card withBorder radius="md">
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            Genel toplam
          </Text>
          <Text fw={700} fz={26}>
            {money(s.totalKurus)}
          </Text>
          <Text size="sm" c="dimmed">
            KDV {money(s.taxKurus)} · İndirim {money(s.discountKurus)}
          </Text>
        </Card>
        <Card withBorder radius="md">
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            Tahsilat
          </Text>
          <Text fw={700} fz={26}>
            {money(s.paidKurus)}
          </Text>
          <Progress
            value={s.totalKurus ? (s.paidKurus / s.totalKurus) * 100 : 0}
            color={s.paymentStatus === 'paid' ? 'green' : 'yellow'}
            mt={6}
          />
          <Text size="sm" c="dimmed" mt={4}>
            {isIssued
              ? remaining > 0
                ? `Kalan: ${money(remaining)}`
                : 'Tamamı ödendi'
              : 'Ödeme yalnızca kesilmiş faturaya alınır'}
          </Text>
        </Card>
      </SimpleGrid>

      {/* Kalemler */}
      <Stack gap="xs">
        <Title order={4}>Kalemler</Title>
        <Paper withBorder radius="md">
          {s.items.length === 0 ? (
            <Empty title="Kalem yok" description="Faturayı kesmek için önce kalem ekleyin." />
          ) : (
            <ScrollArea type="auto">
              <Table miw={850} verticalSpacing="sm" tabularNums>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Kalem</Table.Th>
                    <Table.Th>Parti</Table.Th>
                    <Table.Th>Miktar</Table.Th>
                    <Table.Th>Birim fiyat</Table.Th>
                    <Table.Th>İndirim</Table.Th>
                    <Table.Th>KDV</Table.Th>
                    <Table.Th ta="right">Tutar (KDV dahil)</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {s.items.map((i) => (
                    <Table.Tr key={i.id}>
                      <Table.Td>
                        <Text size="sm" fw={500}>
                          {i.description}
                        </Text>
                        <Badge size="xs" variant="light" color={i.kind === 'product' ? 'indigo' : 'teal'}>
                          {i.kind === 'product' ? 'Ürün' : 'Hizmet'}
                        </Badge>
                      </Table.Td>
                      <Table.Td>{i.lotNo ?? '—'}</Table.Td>
                      <Table.Td>{i.quantity}</Table.Td>
                      <Table.Td>{money(i.unitPriceKurus)}</Table.Td>
                      <Table.Td>{i.discountKurus ? money(i.discountKurus) : '—'}</Table.Td>
                      <Table.Td>
                        {percent(i.taxRateBasisPoints)}
                        <Text size="xs" c="dimmed">
                          {money(i.taxKurus)}
                        </Text>
                      </Table.Td>
                      <Table.Td ta="right" fw={600}>
                        {money(i.lineTotalKurus)}
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </ScrollArea>
          )}
        </Paper>
        <Card withBorder radius="md" w={{ base: '100%', sm: 320 }} ml="auto">
          <Stack gap={6}>
            <Row label="Ara toplam (KDV hariç)" value={money(s.subtotalKurus)} />
            <Row label="İndirim" value={s.discountKurus ? `− ${money(s.discountKurus)}` : money(0)} />
            <Row label="KDV" value={money(s.taxKurus)} />
            <Row label="Genel toplam" value={money(s.totalKurus)} strong />
          </Stack>
        </Card>
      </Stack>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        {/* Ödemeler */}
        <Stack gap="xs">
          <Title order={4}>Ödemeler</Title>
          <Paper withBorder radius="md">
            {s.payments.length === 0 ? (
              <Empty title="Ödeme alınmadı" />
            ) : (
              <ScrollArea type="auto">
                <Table verticalSpacing="sm" tabularNums miw={400}>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Tarih</Table.Th>
                      <Table.Th>Yöntem</Table.Th>
                      <Table.Th ta="right">Tutar</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {s.payments.map((p) => (
                      <Table.Tr key={p.id}>
                        <Table.Td>{dateTime(p.createdAt)}</Table.Td>
                        <Table.Td>{PAYMENT_METHOD[p.method] ?? p.method}</Table.Td>
                        <Table.Td ta="right" fw={600}>
                          {money(p.amountKurus)}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </ScrollArea>
            )}
          </Paper>
        </Stack>

        {/* Komisyonlar (commissions.read: klinik/şube yöneticisi) */}
        {seeCommissions && (
        <Stack gap="xs">
          <Title order={4}>Platform komisyonu</Title>
          <Paper withBorder radius="md">
            {saleCommissions.length === 0 ? (
              <Empty
                title="Komisyon yok"
                description={isDraft ? 'Komisyon fatura kesildiğinde hesaplanır.' : 'Bu satışta komisyona tabi ürün yok.'}
              />
            ) : (
              <ScrollArea type="auto">
                <Table verticalSpacing="sm" tabularNums miw={450}>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Kalem</Table.Th>
                      <Table.Th>Matrah</Table.Th>
                      <Table.Th>Oran</Table.Th>
                      <Table.Th ta="right">Komisyon</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {saleCommissions.map((c) => (
                      <Table.Tr key={c.id}>
                        <Table.Td>{itemById.get(c.saleItemId)?.description ?? '—'}</Table.Td>
                        <Table.Td>{money(c.baseKurus)}</Table.Td>
                        <Table.Td>{percent(c.rateBasisPoints)}</Table.Td>
                        <Table.Td ta="right" fw={600}>
                          {money(c.amountKurus)}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </ScrollArea>
            )}
          </Paper>
          <Text size="xs" c="dimmed">
            Komisyon, platform üzerinden satılan uygun ({PRODUCT_CATEGORY.food.toLowerCase()} ve{' '}
            {PRODUCT_CATEGORY.drug.toLowerCase()}) ürünlerin KDV hariç net tutarı üzerinden hesaplanır.
          </Text>
        </Stack>
        )}
      </SimpleGrid>

      <Modal
        opened={editing}
        onClose={() => setEditing(false)}
        title="Kalemleri düzenle"
        size="85rem"
        closeOnClickOutside={false}
      >
        {editing && (
          <EditItems
            sale={s}
            onDone={(updated) => {
              setStockError(null);
              onSaleChanged(updated);
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        )}
      </Modal>
      <Modal opened={paying} onClose={() => setPaying(false)} title="Ödeme al">
        {paying && (
          <PaymentForm
            sale={s}
            onDone={(updated) => {
              onSaleChanged(updated);
              setPaying(false);
            }}
          />
        )}
      </Modal>
    </Stack>
  );
}

function BackLink() {
  return (
    <Anchor component={Link} to="/sales" size="sm">
      <Group gap={4}>
        <IconArrowLeft size={14} /> Satışlara dön
      </Group>
    </Anchor>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <Group justify="space-between">
      <Text size="sm" c={strong ? undefined : 'dimmed'} fw={strong ? 700 : 400}>
        {label}
      </Text>
      <Text size={strong ? 'lg' : 'sm'} fw={strong ? 700 : 500}>
        {value}
      </Text>
    </Group>
  );
}

function EditItems({ sale, onDone, onCancel }: { sale: SaleView; onDone: (s: SaleView) => void; onCancel: () => void }) {
  const { products } = useSaleCatalog(sale.branchId);
  const [items, setItems] = useState<ItemDraft[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Ürün listesi (parti takibi bilgisi) gelince taslaklar bir kez oluşturulur.
  const ready = products.length > 0 || !sale.items.some((i) => i.productId);
  const initial = useMemo(
    () => (ready ? sale.items.map((i) => draftFromItem(i, products)) : null),
    [ready, sale.items, products],
  );
  const drafts = items ?? initial;

  const save = useMutation({
    mutationFn: (d: ItemDraft[]) => replaceSaleLines(sale.id, draftsToPayload(d)),
    onSuccess: (s) => {
      notifySuccess('Kalemler güncellendi');
      onDone(s);
    },
    onError: notifyError,
  });

  if (!drafts) return <Skeleton h={200} />;

  return (
    <Stack>
      <SaleItemsEditor value={drafts} onChange={setItems} branchId={sale.branchId} />
      {error && (
        <Alert color="red" icon={<IconAlertTriangle size={16} />}>
          {error}
        </Alert>
      )}
      <Group justify="flex-end">
        <Button variant="default" onClick={onCancel}>
          Vazgeç
        </Button>
        <Button
          loading={save.isPending}
          onClick={() => {
            const problem = validateDrafts(drafts);
            setError(problem);
            if (!problem) save.mutate(drafts);
          }}
        >
          Kaydet
        </Button>
      </Group>
    </Stack>
  );
}

function PaymentForm({ sale, onDone }: { sale: SaleView; onDone: (s: SaleView) => void }) {
  const remaining = sale.totalKurus - sale.paidKurus;
  const form = useForm({
    initialValues: { amount: kurusToTl(remaining) as number | string, method: 'cash' },
    validate: {
      amount: (v) => {
        const k = toKurus(num(v));
        if (k <= 0) return 'Tutar sıfırdan büyük olmalı';
        if (k > remaining) return `Kalan tutarı (${money(remaining)}) aşamaz`;
        return null;
      },
    },
  });
  const pay = useMutation({
    mutationFn: (v: typeof form.values) =>
      paySale(sale.id, toKurus(num(v.amount)), v.method),
    onSuccess: (s) => {
      notifySuccess(s.paymentStatus === 'paid' ? 'Ödeme alındı, fatura kapandı' : 'Kısmi ödeme alındı');
      onDone(s);
    },
    onError: notifyError,
  });
  return (
    <form onSubmit={form.onSubmit((v) => pay.mutate(v))}>
      <Stack>
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            Kalan borç
          </Text>
          <Text fw={700}>{money(remaining)}</Text>
        </Group>
        <MoneyInput
          label="Tutar"
          required
          description="Kısmi ödeme için daha düşük tutar girebilirsiniz."
          {...form.getInputProps('amount')}
        />
        <Select label="Ödeme yöntemi" allowDeselect={false} data={toOptions(PAYMENT_METHOD)} {...form.getInputProps('method')} />
        <Group justify="flex-end">
          <Button type="submit" loading={pay.isPending} leftSection={<IconCash size={16} />}>
            Ödemeyi kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
