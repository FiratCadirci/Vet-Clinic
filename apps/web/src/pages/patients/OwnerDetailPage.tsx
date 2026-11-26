import { Alert, Anchor, Badge, Button, Grid, Group, ScrollArea, SimpleGrid, Table, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowLeft, IconDeviceMobile, IconDeviceMobileOff, IconEdit, IconPlus } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { PageHeader } from '../../components/PageHeader';
import { OwnerFormModal } from '../../components/clinical/OwnerFormModal';
import { PetFormModal } from '../../components/clinical/PetFormModal';
import { EmptyRow, InfoRow, Section, SkeletonBlock, SpeciesBadge } from '../../components/clinical/ui';
import { getOwnerDetail } from '../../components/clinical/patientApi';
import { useAuth } from '../../lib/auth';
import { SEX, ageFrom, date } from '../../lib/format';

export function OwnerDetailPage() {
  const { id = '' } = useParams();
  const { can } = useAuth();
  const navigate = useNavigate();
  const [editOpen, edit] = useDisclosure();
  const [petOpen, petModal] = useDisclosure();
  const { data: owner, isError, error } = useQuery({
    queryKey: ['owner', id],
    queryFn: () => getOwnerDetail(id),
  });

  if (isError) {
    return (
      <Alert color="red" title="Kayıt açılamadı">
        {error.message}
      </Alert>
    );
  }
  if (!owner) return <SkeletonBlock h={240} />;

  return (
    <>
      <Anchor component={Link} to="/patients?tab=owners" size="sm" c="dimmed">
        <Group gap={4}>
          <IconArrowLeft size={14} /> Sahipler
        </Group>
      </Anchor>
      <PageHeader
        title={owner.fullName}
        description="Hayvan sahibi"
        actions={
          can('clients.write') && (
            <>
              <Button variant="default" leftSection={<IconEdit size={16} />} onClick={edit.open}>
                Düzenle
              </Button>
              <Button leftSection={<IconPlus size={16} />} onClick={petModal.open}>
                Hasta ekle
              </Button>
            </>
          )
        }
      />
      <Grid>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Section
            title="İletişim"
            actions={
              owner.userId ? (
                <Badge color="teal" variant="light" leftSection={<IconDeviceMobile size={12} />}>
                  Mobil hesap bağlı
                </Badge>
              ) : (
                <Badge color="gray" variant="light" leftSection={<IconDeviceMobileOff size={12} />}>
                  Mobil hesap yok
                </Badge>
              )
            }
          >
            <SimpleGrid cols={2} spacing="sm">
              <InfoRow label="Telefon">{owner.phone}</InfoRow>
              <InfoRow label="E-posta">{owner.email}</InfoRow>
              <InfoRow label="Kayıt tarihi">{date(owner.createdAt)}</InfoRow>
            </SimpleGrid>
            <InfoRow label="Adres">{owner.address}</InfoRow>
            {!owner.userId && (
              <Text size="xs" c="dimmed">
                Sahip mobil uygulamada aynı e-posta ve telefonla kayıt olduğunda hesabı bu karta bağlanır.
              </Text>
            )}
          </Section>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Section title={`Hastalar (${owner.pets.length})`}>
            <ScrollArea>
              <Table highlightOnHover verticalSpacing="sm" miw={480}>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Ad</Table.Th>
                    <Table.Th>Tür / ırk</Table.Th>
                    <Table.Th>Cinsiyet</Table.Th>
                    <Table.Th>Yaş</Table.Th>
                    <Table.Th>Mikroçip</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {owner.pets.length === 0 ? (
                    <EmptyRow cols={5}>Bu sahibe kayıtlı (görebildiğiniz) hasta yok</EmptyRow>
                  ) : (
                    owner.pets.map((p) => (
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
                        <Table.Td>{SEX[p.sex]}</Table.Td>
                        <Table.Td>{ageFrom(p.birthDate)}</Table.Td>
                        <Table.Td>{p.microchipNo ?? '—'}</Table.Td>
                      </Table.Tr>
                    ))
                  )}
                </Table.Tbody>
              </Table>
            </ScrollArea>
          </Section>
        </Grid.Col>
      </Grid>

      <OwnerFormModal opened={editOpen} onClose={edit.close} owner={owner} />
      <PetFormModal
        opened={petOpen}
        onClose={petModal.close}
        owner={{ id: owner.id, fullName: owner.fullName, phone: owner.phone }}
        onSaved={(p) => navigate(`/patients/${p.id}`)}
      />
    </>
  );
}
