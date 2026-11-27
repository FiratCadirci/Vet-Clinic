import { Alert, Anchor, Group, SegmentedControl, Stack, Text, ThemeIcon, Timeline } from '@mantine/core';
import { IconCalendarEvent, IconLock, IconStethoscope, IconVaccine } from '@tabler/icons-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { date, dateTime } from '../../lib/format';
import type { PetHistory } from './types';
import { AppointmentStatusBadge, EmptyText, ExamStatusBadge } from './ui';

type Kind = 'exam' | 'vaccine' | 'appointment';
interface Event {
  key: string;
  kind: Kind;
  at: string;
  title: ReactNode;
  body: ReactNode;
}

const ICON: Record<Kind, { icon: ReactNode; color: string }> = {
  exam: { icon: <IconStethoscope size={14} />, color: 'teal' },
  vaccine: { icon: <IconVaccine size={14} />, color: 'grape' },
  appointment: { icon: <IconCalendarEvent size={14} />, color: 'blue' },
};

function toEvents(h: PetHistory): Event[] {
  const events: Event[] = [
    ...h.examinations.map<Event>((e) => ({
      key: `e-${e.id}`,
      kind: 'exam',
      at: e.startedAt,
      title: (
        <Group gap="xs">
          <Anchor component={Link} to={`/examinations/${e.id}`} fw={600} size="sm">
            Muayene
          </Anchor>
          <ExamStatusBadge status={e.status} />
        </Group>
      ),
      body: (
        <>
          <Text size="xs" c="dimmed">
            {e.vetName} · {e.branchName}
          </Text>
          {e.complaint && <Text size="sm">Şikayet: {e.complaint}</Text>}
          {e.diagnosis && <Text size="sm">Tanı: {e.diagnosis}</Text>}
          {e.treatmentPlan && <Text size="sm">Tedavi: {e.treatmentPlan}</Text>}
        </>
      ),
    })),
    ...h.vaccinations.map<Event>((v) => ({
      key: `v-${v.id}`,
      kind: 'vaccine',
      at: v.administeredAt,
      title: (
        <Text fw={600} size="sm">
          {v.vaccineName}
          {v.doseNumber > 0 ? ` — ${v.doseNumber}. doz` : ''}
        </Text>
      ),
      body: (
        <Text size="xs" c="dimmed">
          {v.branchName}
          {v.lotNo ? ` · Lot: ${v.lotNo}` : ''}
          {v.nextDueAt ? ` · Sonraki doz: ${date(v.nextDueAt)}` : ''}
        </Text>
      ),
    })),
    ...h.appointments.map<Event>((a) => ({
      key: `a-${a.id}`,
      kind: 'appointment',
      at: a.startsAt,
      title: (
        <Group gap="xs">
          <Text fw={600} size="sm">
            Randevu
          </Text>
          <AppointmentStatusBadge status={a.status} />
        </Group>
      ),
      body: (
        <Text size="xs" c="dimmed">
          {a.branchName}
        </Text>
      ),
    })),
  ];
  return events.sort((a, b) => b.at.localeCompare(a.at));
}

/** Hastanın kronolojik sağlık geçmişi (muayene, aşı, randevu). */
export function PetHistoryTimeline({ history }: { history: PetHistory }) {
  const [filter, setFilter] = useState<'all' | Kind>('all');
  const events = toEvents(history).filter((e) => filter === 'all' || e.kind === filter);

  return (
    <Stack>
      {history.hiddenFromOtherBranches > 0 && (
        <Alert color="gray" icon={<IconLock size={18} />} title="Diğer şubelerdeki kayıtlar gizli">
          {history.clinicalAccess === false ? (
            <>
              Bu hastanın {hiddenText(history)} var. Muayene ve aşı kayıtlarını görme yetkiniz olmadığı için bu
              kayıtlar listelenmiyor (yetki klinik yöneticisi tarafından verilebilir).
            </>
          ) : (
            <>
              Bu hastanın başka şubelerde {hiddenText(history)} var. Tüm şubelerin geçmişini görme
              yetkiniz olmadığı için bu kayıtlar listelenmiyor (yetki klinik yöneticisi tarafından verilebilir).
            </>
          )}
        </Alert>
      )}
      <SegmentedControl
        size="xs"
        value={filter}
        onChange={(v) => setFilter(v as typeof filter)}
        data={[
          { value: 'all', label: 'Tümü' },
          { value: 'exam', label: `Muayene (${history.examinations.length})` },
          { value: 'vaccine', label: `Aşı (${history.vaccinations.length})` },
          { value: 'appointment', label: `Randevu (${history.appointments.length})` },
        ]}
        style={{ alignSelf: 'flex-start' }}
      />
      {events.length === 0 ? (
        <EmptyText>Kayıt yok</EmptyText>
      ) : (
        <Timeline bulletSize={26} lineWidth={2}>
          {events.map((e) => (
            <Timeline.Item
              key={e.key}
              bullet={
                <ThemeIcon size={26} radius="xl" color={ICON[e.kind].color} variant="light">
                  {ICON[e.kind].icon}
                </ThemeIcon>
              }
              title={e.title}
            >
              <Text size="xs" c="dimmed" mb={2}>
                {dateTime(e.at)}
              </Text>
              {e.body}
            </Timeline.Item>
          ))}
        </Timeline>
      )}
    </Stack>
  );
}

/** "2 muayene, 1 aşı" gibi gizli kayıt dökümü. */
function hiddenText(h: PetHistory): string {
  const t = h.hiddenByType;
  if (!t) return `${h.hiddenFromOtherBranches} kayıt`;
  const parts = [
    t.examinations && `${t.examinations} muayene`,
    t.vaccinations && `${t.vaccinations} aşı`,
    t.appointments && `${t.appointments} randevu`,
  ].filter(Boolean);
  return `${parts.join(', ')} kaydı`;
}
