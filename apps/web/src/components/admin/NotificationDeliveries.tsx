import {
  Badge,
  Group,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Tooltip,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api, type Page } from "../../lib/api";
import { dateTime } from "../../lib/format";
import { DataTable, DateRangeInput, StatusBadge } from "./ui";
import { dateOnlyQuery, useDateRange } from "./utils";

/** GET /v1/notifications/deliveries (sayfalı) ve /deliveries/summary (kanal × durum sayıları). */
interface Delivery {
  id: string;
  createdAt: string;
  sentAt: string | null;
  channel: "IN_APP" | "EMAIL" | "PUSH";
  status: "PENDING" | "SENT" | "FAILED" | "SKIPPED";
  type: string;
  title: string;
  recipientName: string | null;
  recipientEmail: string | null;
  error: string | null;
}
interface SummaryRow {
  channel: Delivery["channel"];
  status: Delivery["status"];
  count: number;
}

const CHANNEL: Record<string, { label: string; color: string }> = {
  IN_APP: { label: "Uygulama içi", color: "teal" },
  EMAIL: { label: "E-posta", color: "blue" },
  PUSH: { label: "Push", color: "grape" },
};
const STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: "Gönderiliyor", color: "yellow" },
  SENT: { label: "Gönderildi", color: "green" },
  FAILED: { label: "Başarısız", color: "red" },
  SKIPPED: { label: "Atlandı (tercih kapalı)", color: "gray" },
};
const TYPE: Record<string, string> = {
  APPOINTMENT_CONFIRMED: "Randevu onayı",
  APPOINTMENT_CANCELLED: "Randevu iptali",
  APPOINTMENT_RESCHEDULED: "Randevu değişikliği",
  VACCINE_RECORDED: "Aşı kaydı",
  VACCINE_DUE: "Aşı hatırlatması",
  INVOICE_ISSUED: "Fatura",
  STOCK_LOW: "Kritik stok",
  STAFF_INVITED: "Personel daveti",
};

/** Modül I: kliniğin bildirim gönderim kayıtları (hangi olay, kime, hangi kanal, sonuç). */
export function NotificationDeliveries() {
  const [channel, setChannel] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [type, setType] = useState<string | null>(null);
  const [range, setRange] = useDateRange(7);
  const filters = {
    channel: channel ?? undefined,
    status: status ?? undefined,
    type: type ?? undefined,
    ...dateOnlyQuery(range),
  };
  const q = useQuery({
    queryKey: ["notification-deliveries", filters],
    queryFn: () =>
      api.get<Page<Delivery>>("/v1/notifications/deliveries", { ...filters, size: 200 }),
  });
  const summary = useQuery({
    queryKey: ["notification-deliveries", "summary", filters],
    queryFn: () => api.get<SummaryRow[]>("/v1/notifications/deliveries/summary", filters),
  });

  const total = (s: Delivery["status"]) =>
    (summary.data ?? [])
      .filter((r) => r.status === s)
      .reduce((a, r) => a + r.count, 0);

  return (
    <Stack gap="sm">
      <Group align="flex-end" gap="sm" wrap="wrap">
        <Select
          label="Kanal"
          placeholder="Tümü"
          clearable
          data={Object.entries(CHANNEL).map(([value, v]) => ({
            value,
            label: v.label,
          }))}
          value={channel}
          onChange={setChannel}
          w={160}
        />
        <Select
          label="Durum"
          placeholder="Tümü"
          clearable
          data={Object.entries(STATUS).map(([value, v]) => ({
            value,
            label: v.label,
          }))}
          value={status}
          onChange={setStatus}
          w={160}
        />
        <Select
          label="Olay"
          placeholder="Tümü"
          clearable
          data={Object.entries(TYPE).map(([value, label]) => ({
            value,
            label,
          }))}
          value={type}
          onChange={setType}
          w={200}
        />
        <DateRangeInput value={range} onChange={setRange} />
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        {(["SENT", "FAILED", "PENDING"] as const).map((s) => (
          <Paper key={s} withBorder p="sm" radius="md">
            <Text size="xs" c="dimmed">
              {STATUS[s].label}
            </Text>
            <Text fw={700} size="xl" c={STATUS[s].color}>
              {total(s)}
            </Text>
          </Paper>
        ))}
      </SimpleGrid>

      <DataTable
        head={["Zaman", "Olay", "Kanal", "Alıcı", "Başlık", "Durum"]}
        loading={q.isLoading}
        error={q.error}
        empty={!q.data?.content.length}
        emptyText="Bu filtrelerle gönderim kaydı yok"
        minWidth={1000}
      >
        {q.data?.content.map((d) => (
          <Table.Tr key={d.id}>
            <Table.Td style={{ whiteSpace: "nowrap" }}>
              {dateTime(d.createdAt)}
            </Table.Td>
            <Table.Td>{TYPE[d.type] ?? d.type}</Table.Td>
            <Table.Td>
              <StatusBadge map={CHANNEL} value={d.channel} />
            </Table.Td>
            <Table.Td>
              <Text size="sm">{d.recipientName ?? "—"}</Text>
              {d.recipientEmail && (
                <Text size="xs" c="dimmed">
                  {d.recipientEmail}
                </Text>
              )}
            </Table.Td>
            <Table.Td>
              <Text size="sm" lineClamp={1}>
                {d.title}
              </Text>
            </Table.Td>
            <Table.Td>
              {d.status === "FAILED" && d.error ? (
                <Tooltip label={d.error} multiline w={300}>
                  <Badge color="red" variant="light">
                    {STATUS.FAILED.label}
                  </Badge>
                </Tooltip>
              ) : (
                <StatusBadge map={STATUS} value={d.status} />
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </DataTable>
    </Stack>
  );
}
