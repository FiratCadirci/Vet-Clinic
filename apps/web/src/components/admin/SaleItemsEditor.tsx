import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Divider,
  Group,
  NumberInput,
  Paper,
  ScrollArea,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconBarcode, IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { listSaleProducts } from './salesApi';
import { money, percent } from '../../lib/format';
import { notifyError } from '../../lib/notify';
import { saleTotals, type PricedLine } from './pricing';
import { draftFromProduct, draftFromService, newKey, priceDraft, useSaleCatalog, type ItemDraft } from './saleDrafts';
import type { Product } from './types';
import { Empty, MoneyInput, PercentInput } from './ui';
import { num } from './utils';

export function SaleTotalsCard({ lines }: { lines: PricedLine[] }) {
  const t = saleTotals(lines);
  const row = (label: string, value: string, strong = false) => (
    <Group justify="space-between">
      <Text size="sm" c={strong ? undefined : 'dimmed'} fw={strong ? 700 : 400}>
        {label}
      </Text>
      <Text size={strong ? 'lg' : 'sm'} fw={strong ? 700 : 500} style={{ fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Text>
    </Group>
  );
  return (
    <Card withBorder radius="md" w={{ base: '100%', sm: 320 }} ml="auto">
      <Stack gap={6}>
        {row('Ara toplam (KDV hariç)', money(t.subtotalKurus))}
        {row('İndirim', t.discountKurus ? `− ${money(t.discountKurus)}` : money(0))}
        {row('KDV', money(t.taxKurus))}
        <Divider my={4} />
        {row('Genel toplam', money(t.totalKurus), true)}
      </Stack>
    </Card>
  );
}

export function SaleItemsEditor({
  value,
  onChange,
  branchId,
}: {
  value: ItemDraft[];
  onChange: (next: ItemDraft[]) => void;
  branchId: string | null;
}) {
  const { services, products, stock } = useSaleCatalog(branchId);
  const [barcode, setBarcode] = useState('');
  const [productSearch, setProductSearch] = useState('');
  const [debouncedSearch] = useDebouncedValue(productSearch, 300);
  const [extraProducts, setExtraProducts] = useState<Product[]>([]);

  // 200'den fazla ürünü olan kliniklerde arama sunucuda yapılır.
  const searched = useQuery({
    queryKey: ['products', { active: true, search: debouncedSearch }],
    queryFn: () => listSaleProducts({ search: debouncedSearch, size: 50 }),
    enabled: debouncedSearch.trim().length >= 2 && products.length >= 200,
  });

  const allProducts = useMemo(() => {
    const map = new Map<string, Product>();
    for (const p of [...products, ...(searched.data ?? []), ...extraProducts]) map.set(p.id, p);
    return [...map.values()];
  }, [products, searched.data, extraProducts]);

  const stockOf = (productId: string | null) => stock.find((s) => s.productId === productId);
  const update = (key: string, patch: Partial<ItemDraft>) => onChange(value.map((d) => (d.key === key ? { ...d, ...patch } : d)));
  const remove = (key: string) => onChange(value.filter((d) => d.key !== key));

  const addProduct = (p: Product) => {
    // Aynı ürün (partisiz) zaten varsa miktarını artır.
    const existing = value.find((d) => d.kind === 'product' && d.productId === p.id && !d.lotId);
    if (existing) update(existing.key, { quantity: num(existing.quantity) + 1 });
    else onChange([...value, draftFromProduct(p)]);
  };

  const scanBarcode = async () => {
    const code = barcode.trim();
    if (!code) return;
    try {
      let p = allProducts.find((x) => x.barcode === code);
      if (!p) {
        const found = (await listSaleProducts({ search: code, size: 20 })).filter((x) => x.barcode === code);
        p = found[0];
        if (p) setExtraProducts((x) => [...x, p!]);
      }
      if (!p) notifyError(new Error(`Barkod bulunamadı: ${code}`));
      else addProduct(p);
      setBarcode('');
    } catch (e) {
      notifyError(e);
    }
  };

  const lines = value.map(priceDraft);

  return (
    <Stack gap="sm">
      <Group align="flex-end" gap="sm" wrap="wrap">
        <Select
          label="Hizmet ekle"
          placeholder="Hizmet seçin"
          searchable
          w={{ base: '100%', sm: 240 }}
          data={services.map((s) => ({ value: s.id, label: `${s.name} · ${money(s.priceKurus)}` }))}
          value={null}
          onChange={(id) => {
            const s = services.find((x) => x.id === id);
            if (s) onChange([...value, draftFromService(s)]);
          }}
          nothingFoundMessage="Hizmet bulunamadı"
        />
        <Select
          label="Ürün ekle"
          placeholder="Ürün adı veya barkod"
          searchable
          w={{ base: '100%', sm: 280 }}
          data={allProducts.map((p) => ({ value: p.id, label: `${p.name}${p.barcode ? ` (${p.barcode})` : ''}` }))}
          value={null}
          searchValue={productSearch}
          onSearchChange={setProductSearch}
          onChange={(id) => {
            const p = allProducts.find((x) => x.id === id);
            if (p) addProduct(p);
            setProductSearch('');
          }}
          nothingFoundMessage="Ürün bulunamadı"
        />
        <TextInput
          label="Barkod okut"
          placeholder="Barkod + Enter"
          leftSection={<IconBarcode size={16} />}
          w={{ base: '100%', sm: 200 }}
          value={barcode}
          onChange={(e) => setBarcode(e.currentTarget.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              void scanBarcode();
            }
          }}
        />
        <Button
          variant="light"
          leftSection={<IconPlus size={16} />}
          onClick={() =>
            onChange([
              ...value,
              {
                key: newKey(),
                kind: 'service',
                productId: null,
                serviceId: null,
                description: '',
                quantity: 1,
                unitPrice: 0,
                discount: 0,
                taxPercent: 20,
                lotId: null,
                lotNo: null,
                trackLots: false,
                unit: null,
              },
            ])
          }
        >
          Serbest kalem
        </Button>
      </Group>

      <Paper withBorder radius="md">
        {value.length === 0 ? (
          <Empty title="Henüz kalem eklenmedi" description="Hizmet veya ürün seçerek ya da barkod okutarak kalem ekleyin." />
        ) : (
          <ScrollArea type="auto">
            <Table miw={960} verticalSpacing="xs" tabularNums>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Kalem</Table.Th>
                  <Table.Th w={170}>Parti</Table.Th>
                  <Table.Th w={90}>Miktar</Table.Th>
                  <Table.Th w={130}>Birim fiyat</Table.Th>
                  <Table.Th w={120}>İndirim</Table.Th>
                  <Table.Th w={90}>KDV</Table.Th>
                  <Table.Th w={120} ta="right">
                    Tutar
                  </Table.Th>
                  <Table.Th w={40} />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {value.map((d, i) => {
                  const line = lines[i];
                  const st = d.kind === 'product' ? stockOf(d.productId) : undefined;
                  const available = d.lotId ? (st?.lots.find((l) => l.lotId === d.lotId)?.quantity ?? 0) : (st?.total ?? 0);
                  const short = d.kind === 'product' && line.quantity > available;
                  const free = d.kind === 'service' && !d.serviceId;
                  return (
                    <Table.Tr key={d.key}>
                      <Table.Td>
                        <Stack gap={2}>
                          {free ? (
                            <TextInput
                              size="xs"
                              placeholder="Açıklama (ör. Muayene ücreti)"
                              value={d.description}
                              onChange={(e) => update(d.key, { description: e.currentTarget.value })}
                              error={!d.description.trim()}
                            />
                          ) : (
                            <Text size="sm" fw={500}>
                              {d.description}
                            </Text>
                          )}
                          <Group gap={4}>
                            <Badge size="xs" variant="light" color={d.kind === 'product' ? 'indigo' : 'teal'}>
                              {d.kind === 'product' ? 'Ürün' : free ? 'Serbest hizmet' : 'Hizmet'}
                            </Badge>
                            {d.kind === 'product' && (
                              <Text size="xs" c={short ? 'red' : 'dimmed'}>
                                Stok: {available} {d.unit ?? ''}
                              </Text>
                            )}
                          </Group>
                        </Stack>
                      </Table.Td>
                      <Table.Td>
                        {d.kind === 'product' && d.trackLots ? (
                          <Select
                            size="xs"
                            placeholder="Otomatik (SKT sırası)"
                            clearable
                            data={(st?.lots ?? []).map((l) => ({
                              value: l.lotId,
                              label: `${l.lotNo} · ${l.quantity} ad.${l.expiryDate ? ` · SKT ${l.expiryDate.split('-').reverse().join('.')}` : ''}`,
                            }))}
                            value={d.lotId}
                            onChange={(v) => update(d.key, { lotId: v, lotNo: st?.lots.find((l) => l.lotId === v)?.lotNo ?? null })}
                          />
                        ) : (
                          <Text size="xs" c="dimmed">
                            —
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <NumberInput
                          size="xs"
                          min={1}
                          allowDecimal={false}
                          allowNegative={false}
                          value={d.quantity}
                          onChange={(v) => update(d.key, { quantity: v })}
                          error={num(d.quantity) < 1}
                        />
                      </Table.Td>
                      <Table.Td>
                        <MoneyInput size="xs" value={d.unitPrice} onChange={(v) => update(d.key, { unitPrice: v })} />
                      </Table.Td>
                      <Table.Td>
                        <MoneyInput
                          size="xs"
                          value={d.discount}
                          onChange={(v) => update(d.key, { discount: v })}
                          error={line.invalid}
                        />
                      </Table.Td>
                      <Table.Td>
                        {free ? (
                          <PercentInput size="xs" value={d.taxPercent} onChange={(v) => update(d.key, { taxPercent: v })} />
                        ) : (
                          <Text size="sm">{percent(line.taxRateBasisPoints)}</Text>
                        )}
                      </Table.Td>
                      <Table.Td ta="right">
                        <Text size="sm" fw={600}>
                          {money(line.lineTotalKurus)}
                        </Text>
                        <Text size="xs" c="dimmed">
                          KDV {money(line.taxKurus)}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Tooltip label="Kalemi kaldır">
                          <ActionIcon variant="subtle" color="red" onClick={() => remove(d.key)} aria-label="Kalemi kaldır">
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Tooltip>
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        )}
      </Paper>

      <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm">
        <Text size="xs" c="dimmed" maw={420}>
          Fiyatlar KDV hariçtir; KDV her satırda ayrı hesaplanıp yuvarlanır. Ürün KDV oranı ürün kartından gelir. Parti seçilmezse
          fatura kesilirken son kullanma tarihi en yakın partiden düşülür.
        </Text>
        <SaleTotalsCard lines={lines} />
      </Group>
    </Stack>
  );
}
