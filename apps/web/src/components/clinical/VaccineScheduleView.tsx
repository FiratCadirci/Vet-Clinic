import { Alert, Badge, Button, Group, ScrollArea, Stack, Table, Text, Tooltip } from '@mantine/core';
import { IconInfoCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { date } from '../../lib/format';
import { EmptyText, SkeletonBlock } from './ui';
import { getPetSchedule } from './vaccineApi';

const DOSE_COLOR = { done: 'green', planned: 'gray', overdue: 'red' } as const;
const DOSE_LABEL = { done: 'Yapıldı', planned: 'Planlı', overdue: 'Gecikti' } as const;

interface Props {
  petId: string;
  /** Verilirse her satırda "Uygula" düğmesi çıkar. */
  onVaccinate?: (item: { templateItemId?: string; vaccineName: string }) => void;
}

/** `GET /v1/animals/{id}/vaccine-schedule`: onaylı protokollere göre yapılan / sıradaki dozlar (zincir gerçek doz tarihinden). */
export function VaccineScheduleView({ petId, onVaccinate }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['vaccine-schedule', petId],
    queryFn: () => getPetSchedule(petId),
  });
  if (isLoading || !data) return <SkeletonBlock />;

  return (
    <Stack>
      {data.nextDue && (
        <Alert color={data.nextDue.overdue ? 'red' : 'teal'} variant="light" icon={<IconInfoCircle size={18} />}>
          Sıradaki aşı: <b>{data.nextDue.vaccineName}</b> ({data.nextDue.doseNumber}. doz) — {date(data.nextDue.dueAt)}
          {data.nextDue.overdue && ' · gecikmiş'}
        </Alert>
      )}
      {data.templatePendingApproval && (
        <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
          Bu tür için aşı takvimi şablonu hekim onayı bekliyor. Onaylanınca planlı dozlar burada görünür.
        </Alert>
      )}
      {!data.template && !data.templatePendingApproval && (
        <Text size="sm" c="dimmed">
          Bu tür için onaylı aşı takvimi yok. "Aşılar → Aşı takvimi şablonları" ekranından oluşturulabilir.
        </Text>
      )}
      {!data.birthDate && data.template && (
        <Text size="xs" c="dimmed">
          Doğum tarihi girilmediği için ilk dozların planlı tarihi hesaplanamıyor.
        </Text>
      )}

      {data.items.length > 0 && (
        <ScrollArea>
          <Table verticalSpacing="sm" miw={620}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Aşı</Table.Th>
                <Table.Th>Dozlar</Table.Th>
                <Table.Th>Sıradaki</Table.Th>
                {onVaccinate && <Table.Th />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data.items.map((item) => (
                <Table.Tr key={item.templateItemId}>
                  <Table.Td fw={500}>{item.vaccineName}</Table.Td>
                  <Table.Td>
                    <Group gap={4}>
                      {item.doses.map((d) => (
                        <Tooltip
                          key={d.doseNumber}
                          label={
                            d.status === 'done'
                              ? `Yapıldı: ${date(d.administeredAt)}`
                              : `${DOSE_LABEL[d.status]}: ${d.plannedAt ? date(d.plannedAt) : d.doseNumber > 1 ? 'önceki doz yapılınca' : 'tarih yok'}`
                          }
                        >
                          <Badge color={DOSE_COLOR[d.status]} variant={d.status === 'done' ? 'filled' : 'light'} size="sm">
                            {d.doseNumber}. doz
                          </Badge>
                        </Tooltip>
                      ))}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    {item.next ? (
                      <Group gap={6}>
                        <Text size="sm">
                          {item.next.booster ? 'Rapel' : `${item.next.doseNumber}. doz`} · {item.next.dueAt ? date(item.next.dueAt) : '—'}
                        </Text>
                        {item.next.overdue && (
                          <Badge color="red" size="xs">
                            Gecikti
                          </Badge>
                        )}
                      </Group>
                    ) : (
                      <Text size="sm" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                  {onVaccinate && (
                    <Table.Td ta="right">
                      <Button
                        size="xs"
                        variant="light"
                        onClick={() => onVaccinate({ templateItemId: item.templateItemId, vaccineName: item.vaccineName })}
                      >
                        Uygula
                      </Button>
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      )}

      {data.otherVaccinations.length > 0 && (
        <>
          <Text fw={600} size="sm">
            Takvim dışı aşılar
          </Text>
          <Table verticalSpacing="xs">
            <Table.Tbody>
              {data.otherVaccinations.map((v) => (
                <Table.Tr key={v.id}>
                  <Table.Td>{v.vaccineName}</Table.Td>
                  <Table.Td>{v.doseNumber}. doz</Table.Td>
                  <Table.Td>{date(v.administeredAt)}</Table.Td>
                  <Table.Td>{v.nextDueAt ? `Sonraki: ${date(v.nextDueAt)}` : '—'}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </>
      )}
      {data.items.length === 0 && data.otherVaccinations.length === 0 && data.template && <EmptyText>Aşı kaydı yok</EmptyText>}
    </Stack>
  );
}
