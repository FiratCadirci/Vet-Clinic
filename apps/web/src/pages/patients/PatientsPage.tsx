import { Badge, Button, Group, Paper, ScrollArea, Table, Tabs, Text, TextInput } from '@mantine/core';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconDeviceMobile, IconPaw, IconPlus, IconSearch, IconUserPlus, IconUsers } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { OwnerFormModal } from '../../components/clinical/OwnerFormModal';
import { PetFormModal } from '../../components/clinical/PetFormModal';
import { EmptyRow, SkeletonRows, SpeciesBadge } from '../../components/clinical/ui';
import { listOwners, listPets } from '../../components/clinical/patientApi';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { ageFrom } from '../../lib/format';

const PAGE = 50;

function Pager({ page, count, onChange }: { page: number; count: number; onChange: (p: number) => void }) {
  if (page === 0 && count < PAGE) return null;
  return (
    <Group justify="flex-end" mt="sm" gap="xs">
      <Text size="sm" c="dimmed">
        Sayfa {page + 1}
      </Text>
      <Button size="xs" variant="default" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Önceki
      </Button>
      <Button size="xs" variant="default" disabled={count < PAGE} onClick={() => onChange(page + 1)}>
        Sonraki
      </Button>
    </Group>
  );
}

function PetsTable({ search }: { search: string }) {
  const navigate = useNavigate();
  const { allBranches } = useBranch();
  const [page, setPage] = useState(0);
  const [prevSearch, setPrevSearch] = useState(search);
  if (prevSearch !== search) {
    setPrevSearch(search);
    setPage(0);
  }
  const { data = [], isLoading } = useQuery({
    queryKey: ['pets', { search, offset: page * PAGE, limit: PAGE }],
    queryFn: () => listPets(search || undefined, page, PAGE),
    placeholderData: keepPreviousData,
  });
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';

  return (
    <>
      <ScrollArea>
        <Table highlightOnHover verticalSpacing="sm" miw={760}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Hasta</Table.Th>
              <Table.Th>Tür / ırk</Table.Th>
              <Table.Th>Yaş</Table.Th>
              <Table.Th>Sahip</Table.Th>
              <Table.Th>Telefon</Table.Th>
              <Table.Th>Mikroçip</Table.Th>
              <Table.Th>Kayıt şubesi</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {isLoading ? (
              <SkeletonRows cols={7} />
            ) : data.length === 0 ? (
              <EmptyRow cols={7}>{search ? 'Aramayla eşleşen hasta yok' : 'Henüz hasta kaydı yok'}</EmptyRow>
            ) : (
              data.map((p) => (
                <Table.Tr key={p.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/patients/${p.id}`)}>
                  <Table.Td fw={600}>{p.name}</Table.Td>
                  <Table.Td>
                    <Group gap={6} wrap="nowrap">
                      <SpeciesBadge species={p.species} />
                      <Text size="sm" c="dimmed">
                        {p.breed ?? ''}
                      </Text>
                    </Group>
                  </Table.Td>
                  <Table.Td>{ageFrom(p.birthDate)}</Table.Td>
                  <Table.Td
                    onClick={(e) => {
                      e.stopPropagation();
                      navigate(`/owners/${p.ownerId}`);
                    }}
                  >
                    <Text size="sm" c="teal" style={{ cursor: 'pointer' }}>
                      {p.ownerName}
                    </Text>
                  </Table.Td>
                  <Table.Td>{p.ownerPhone}</Table.Td>
                  <Table.Td>{p.microchipNo ?? '—'}</Table.Td>
                  <Table.Td>{branchName(p.branchId)}</Table.Td>
                </Table.Tr>
              ))
            )}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      <Pager page={page} count={data.length} onChange={setPage} />
    </>
  );
}

function OwnersTable({ search }: { search: string }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [prevSearch, setPrevSearch] = useState(search);
  if (prevSearch !== search) {
    setPrevSearch(search);
    setPage(0);
  }
  const { data = [], isLoading } = useQuery({
    queryKey: ['owners', { search, offset: page * PAGE, limit: PAGE }],
    queryFn: () => listOwners(search || undefined, page, PAGE),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <ScrollArea>
        <Table highlightOnHover verticalSpacing="sm" miw={640}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Ad soyad</Table.Th>
              <Table.Th>Telefon</Table.Th>
              <Table.Th>E-posta</Table.Th>
              <Table.Th>Hasta sayısı</Table.Th>
              <Table.Th>Mobil uygulama</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {isLoading ? (
              <SkeletonRows cols={5} />
            ) : data.length === 0 ? (
              <EmptyRow cols={5}>{search ? 'Aramayla eşleşen sahip yok' : 'Henüz hayvan sahibi kaydı yok'}</EmptyRow>
            ) : (
              data.map((o) => (
                <Table.Tr key={o.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/owners/${o.id}`)}>
                  <Table.Td fw={600}>{o.fullName}</Table.Td>
                  <Table.Td>{o.phone}</Table.Td>
                  <Table.Td>{o.email ?? '—'}</Table.Td>
                  <Table.Td>{o.petCount}</Table.Td>
                  <Table.Td>
                    {o.hasMobileAccount ? (
                      <Badge color="teal" variant="light" leftSection={<IconDeviceMobile size={12} />}>
                        Bağlı
                      </Badge>
                    ) : (
                      <Text size="sm" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))
            )}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      <Pager page={page} count={data.length} onChange={setPage} />
    </>
  );
}

export function PatientsPage() {
  const { can } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'owners' ? 'owners' : 'pets';
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search.trim(), 300);
  const [ownerOpen, ownerModal] = useDisclosure();
  const [petOpen, petModal] = useDisclosure();

  return (
    <>
      <PageHeader
        title="Hastalar"
        description="Hasta ve hayvan sahibi kayıtları"
        actions={
          can('clients.write') && (
            <>
              <Button variant="default" leftSection={<IconUserPlus size={16} />} onClick={ownerModal.open}>
                Yeni sahip
              </Button>
              <Button leftSection={<IconPlus size={16} />} onClick={petModal.open}>
                Yeni hasta
              </Button>
            </>
          )
        }
      />
      <Paper withBorder p="md">
        <Tabs value={tab} onChange={(v) => setParams(v === 'owners' ? { tab: 'owners' } : {}, { replace: true })}>
          <Group justify="space-between" align="flex-end" mb="md" wrap="wrap">
            <Tabs.List>
              <Tabs.Tab value="pets" leftSection={<IconPaw size={16} />}>
                Hastalar
              </Tabs.Tab>
              <Tabs.Tab value="owners" leftSection={<IconUsers size={16} />}>
                Sahipler
              </Tabs.Tab>
            </Tabs.List>
            <TextInput
              w={{ base: '100%', sm: 320 }}
              leftSection={<IconSearch size={16} />}
              placeholder={tab === 'pets' ? 'Ad, mikroçip, sahip adı veya telefon' : 'Ad, telefon veya e-posta'}
              value={search}
              onChange={(e) => setSearch(e.currentTarget.value)}
            />
          </Group>
          <Tabs.Panel value="pets">
            <PetsTable search={debounced} />
          </Tabs.Panel>
          <Tabs.Panel value="owners">
            <OwnersTable search={debounced} />
          </Tabs.Panel>
        </Tabs>
      </Paper>

      <OwnerFormModal opened={ownerOpen} onClose={ownerModal.close} onSaved={(o) => navigate(`/owners/${o.id}`)} />
      <PetFormModal opened={petOpen} onClose={petModal.close} onSaved={(p) => navigate(`/patients/${p.id}`)} />
    </>
  );
}
