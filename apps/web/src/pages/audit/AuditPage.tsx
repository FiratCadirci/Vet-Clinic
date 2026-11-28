import {
  Group,
  Select,
  Stack,
  Table,
  Tabs,
  Text,
  TextInput,
} from "@mantine/core";
import { useDebouncedValue } from "@mantine/hooks";
import { IconSearch } from "@tabler/icons-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../components/PageHeader";
import { NotificationDeliveries } from "../../components/admin/NotificationDeliveries";
import {
  AUDIT_ACTION,
  AUDIT_ENTITY,
  toOptions,
} from "../../components/admin/labels";
import type { UserRow } from "../../components/admin/orgTypes";
import type { AuditLog } from "../../components/admin/types";
import {
  DataTable,
  DateRangeInput,
  StatusBadge,
} from "../../components/admin/ui";
import { isoRangeQuery, useDateRange } from "../../components/admin/utils";
import { api, type Page } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import { dateTime } from "../../lib/format";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function AuditPage() {
  const { can } = useAuth();
  const [entity, setEntity] = useState<string | null>(null);
  const [action, setAction] = useState<string | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  const [entityId, setEntityId] = useState("");
  const [debouncedEntityId] = useDebouncedValue(entityId.trim(), 400);
  const [range, setRange] = useDateRange(7);
  const [limit, setLimit] = useState<string>("100");

  const users = useQuery({
    queryKey: ["users", "audit-filter"],
    queryFn: async () =>
      (await api.get<Page<UserRow>>("/v1/users", { size: 200 })).content,
    enabled: can("users.manage"),
  });
  // Yarım yazılmış kimlikle istek atılmaz (backend geçersiz UUID'ye 400 döner).
  const validEntityId = UUID_RE.test(debouncedEntityId) ? debouncedEntityId : undefined;
  const params = {
    entityType: entity ?? undefined,
    action: action ?? undefined,
    entityId: validEntityId,
    userId: userId ?? undefined,
    ...isoRangeQuery(range),
    limit: Number(limit),
  };
  const logs = useQuery({
    queryKey: ["audit-logs", params],
    queryFn: () => api.get<AuditLog[]>("/v1/audit-logs", params),
  });

  return (
    <>
      <PageHeader
        title="Denetim izi"
        description="Kritik değişiklikler, hasta kaydı görüntülemeleri ve bildirim gönderim kayıtları"
      />
      <Tabs defaultValue="audit" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="audit">İşlem kayıtları</Tabs.Tab>
          <Tabs.Tab value="deliveries">Bildirim gönderim kayıtları</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="deliveries">
          <NotificationDeliveries />
        </Tabs.Panel>
        <Tabs.Panel value="audit">
          <Stack gap="sm">
            <Group align="flex-end" gap="sm" wrap="wrap">
              <Select
                label="Kayıt türü"
                placeholder="Tümü"
                clearable
                searchable
                data={toOptions(AUDIT_ENTITY)}
                value={entity}
                onChange={setEntity}
                w={200}
              />
              <Select
                label="İşlem"
                placeholder="Tümü"
                clearable
                searchable
                data={toOptions(AUDIT_ACTION)}
                value={action}
                onChange={setAction}
                w={220}
              />
              <Select
                label="Kullanıcı"
                placeholder="Tümü"
                clearable
                searchable
                data={(users.data ?? []).map((u) => ({
                  value: u.id,
                  label: u.displayName,
                }))}
                value={userId}
                onChange={setUserId}
                w={220}
                disabled={!can("users.manage")}
              />
              <TextInput
                label="Kayıt kimliği"
                placeholder="UUID"
                leftSection={<IconSearch size={16} />}
                value={entityId}
                onChange={(e) => setEntityId(e.currentTarget.value)}
                error={
                  debouncedEntityId && !validEntityId
                    ? "Geçerli bir kimlik girin"
                    : undefined
                }
                w={260}
              />
              <DateRangeInput value={range} onChange={setRange} />
              <Select
                label="Kayıt sayısı"
                allowDeselect={false}
                data={["50", "100", "250", "500"]}
                value={limit}
                onChange={(v) => v && setLimit(v)}
                w={110}
              />
            </Group>
            <DataTable
              head={[
                "Zaman",
                "Kullanıcı",
                "İşlem",
                "Kayıt türü",
                "Kayıt",
                "Şube",
                "IP",
              ]}
              loading={logs.isLoading}
              error={logs.error}
              empty={!logs.data?.length}
              emptyText="Bu filtrelerle kayıt yok"
              minWidth={1100}
            >
              {logs.data?.map((l) => (
                <Table.Tr key={l.id}>
                  <Table.Td style={{ whiteSpace: "nowrap" }}>
                    {dateTime(l.occurredAt)}
                  </Table.Td>
                  <Table.Td>
                    {l.userName ?? (
                      <Text c="dimmed" size="sm">
                        Sistem
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <StatusBadge map={AUDIT_ACTION} value={l.action} />
                  </Table.Td>
                  <Table.Td>
                    {AUDIT_ENTITY[l.entityType.toUpperCase()] ?? l.entityType}
                  </Table.Td>
                  <Table.Td>
                    {l.entityId ? (
                      <Text
                        size="xs"
                        ff="monospace"
                        style={{ cursor: "pointer" }}
                        title="Bu kayda göre süz"
                        onClick={() => setEntityId(l.entityId!)}
                      >
                        {`${l.entityId.slice(0, 8)}…`}
                      </Text>
                    ) : (
                      "—"
                    )}
                  </Table.Td>
                  <Table.Td>{l.clinicName ?? "—"}</Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {l.ipAddress ?? "—"}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ))}
            </DataTable>
            {logs.data && logs.data.length >= Number(limit) && (
              <Text size="xs" c="dimmed">
                En yeni {limit} kayıt gösteriliyor. Daha eski kayıtlar için
                tarih aralığını daraltın.
              </Text>
            )}
          </Stack>
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
