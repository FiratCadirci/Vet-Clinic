import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  MultiSelect,
  Paper,
  ScrollArea,
  SegmentedControl,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import {
  IconBan,
  IconCheck,
  IconEdit,
  IconLock,
  IconMail,
  IconShieldCheck,
  IconUserPlus,
  IconUsers,
  IconX,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { PageHeader } from '../../components/PageHeader';
import { ROLE_COLOR, ROLE_LABEL } from '../../components/admin/labels';
import {
  toStaffView,
  type Invitation,
  type InvitationStatus,
  type PermissionMatrixData,
  type RoleRow,
  type StaffView,
  type UserRow,
} from '../../components/admin/orgTypes';
import { DataTable, ErrorAlert, TableSkeleton } from '../../components/admin/ui';
import { ApiError, api, type Page } from '../../lib/api';
import { useAuth, type Role } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { date, dateTime } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

const ROLES: Role[] = ['clinic_admin', 'branch_manager', 'veterinarian', 'receptionist'];

const INVITE_STATUS: Record<InvitationStatus, { label: string; color: string }> = {
  PENDING: { label: 'Bekliyor', color: 'yellow' },
  ACCEPTED: { label: 'Kabul edildi', color: 'green' },
  REVOKED: { label: 'İptal edildi', color: 'red' },
  EXPIRED: { label: 'Süresi doldu', color: 'gray' },
};

export function StaffPage() {
  const [tab, setTab] = useState<string | null>('staff');
  return (
    <>
      <PageHeader title="Personel ve yetkiler" description="Personel rolleri, şube atamaları, davetler ve rol-yetki matrisi" />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="staff" leftSection={<IconUsers size={16} />}>
            Personel
          </Tabs.Tab>
          <Tabs.Tab value="invites" leftSection={<IconMail size={16} />}>
            Davetler
          </Tabs.Tab>
          <Tabs.Tab value="matrix" leftSection={<IconShieldCheck size={16} />}>
            Yetki matrisi
          </Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="staff">
          <StaffTab />
        </Tabs.Panel>
        <Tabs.Panel value="invites">
          <InvitesTab />
        </Tabs.Panel>
        <Tabs.Panel value="matrix">
          <MatrixTab />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}

/** Sistem rolleri (rol kodu → rol kimliği). */
function useRoles() {
  return useQuery({ queryKey: ['roles'], queryFn: () => api.get<RoleRow[]>('/v1/roles'), staleTime: Infinity });
}

/** Rol seçenekleri: klinik yöneticisi rolünü yalnız klinik yöneticisi verebilir. */
function roleOptions(roles: RoleRow[], isAdmin: boolean) {
  return ROLES.filter((r) => isAdmin || r !== 'clinic_admin')
    .map((code) => roles.find((r) => r.code === code))
    .filter((r): r is RoleRow => Boolean(r))
    .map((r) => ({ value: r.id, label: ROLE_LABEL[r.code] ?? r.name }));
}

/** Klinik yöneticisi gruptaki tüm şubelere, şube yöneticisi yalnız kendi şubelerine atama yapar. */
function useAssignableBranches(isAdmin: boolean) {
  const { branches, allBranches } = useBranch();
  return isAdmin ? allBranches : branches;
}

// ------------------------------------------------------------------ personel

function StaffTab() {
  const { user } = useAuth();
  const { allBranches } = useBranch();
  const isAdmin = user?.role === 'clinic_admin';
  const [editing, setEditing] = useState<StaffView | null>(null);
  const [showInactive, setShowInactive] = useState('active');
  const staff = useQuery({
    queryKey: ['users', 'staff-view'],
    queryFn: async () => (await api.get<Page<UserRow>>('/v1/users', { size: 200 })).content.map(toStaffView),
  });
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
  const rows = (staff.data ?? []).filter((s) => showInactive === 'all' || s.active);

  return (
    <Stack gap="sm">
      <SegmentedControl
        w="fit-content"
        value={showInactive}
        onChange={setShowInactive}
        data={[
          { value: 'active', label: 'Aktif personel' },
          { value: 'all', label: 'Tümü' },
        ]}
      />
      <DataTable
        head={['Ad soyad', 'Rol', 'Şubeler', 'Durum', 'Kayıt', '']}
        loading={staff.isLoading}
        error={staff.error}
        empty={rows.length === 0}
        emptyText="Personel bulunamadı"
        minWidth={820}
      >
        {rows.map((s) => {
          const locked = !isAdmin && s.role === 'clinic_admin';
          return (
            <Table.Tr key={s.id} opacity={s.active ? 1 : 0.6}>
              <Table.Td>
                <Text size="sm" fw={500} component="div">
                  {s.fullName}{' '}
                  {s.id === user?.id && (
                    <Badge size="xs" variant="outline">
                      Siz
                    </Badge>
                  )}
                </Text>
                <Text size="xs" c="dimmed">
                  {s.email}
                  {s.phone ? ` · ${s.phone}` : ''}
                </Text>
              </Table.Td>
              <Table.Td>
                {s.role ? (
                  <Badge color={ROLE_COLOR[s.role] ?? 'gray'} variant="light">
                    {ROLE_LABEL[s.role] ?? s.role}
                  </Badge>
                ) : (
                  <Text size="sm" c="dimmed">
                    —
                  </Text>
                )}
              </Table.Td>
              <Table.Td>
                {s.branchIds.length ? (
                  <Group gap={4}>
                    {s.branchIds.map((b) => (
                      <Badge key={b} size="sm" variant="default">
                        {branchName(b)}
                      </Badge>
                    ))}
                  </Group>
                ) : (
                  <Text size="sm" c="red">
                    Şube atanmamış
                  </Text>
                )}
              </Table.Td>
              <Table.Td>
                <Badge color={s.active ? 'green' : 'gray'} variant="light">
                  {s.active ? 'Aktif' : 'Pasif'}
                </Badge>
              </Table.Td>
              <Table.Td>{date(s.createdAt)}</Table.Td>
              <Table.Td>
                <Tooltip label={locked ? 'Klinik yöneticisini yalnızca klinik yöneticisi düzenleyebilir' : 'Düzenle'}>
                  <ActionIcon variant="subtle" disabled={locked} onClick={() => setEditing(s)} aria-label="Düzenle">
                    {locked ? <IconLock size={16} /> : <IconEdit size={16} />}
                  </ActionIcon>
                </Tooltip>
              </Table.Td>
            </Table.Tr>
          );
        })}
      </DataTable>
      <Modal opened={editing !== null} onClose={() => setEditing(null)} title="Personeli düzenle">
        {editing && <StaffForm member={editing} onDone={() => setEditing(null)} />}
      </Modal>
    </Stack>
  );
}

function StaffForm({ member, onDone }: { member: StaffView; onDone: () => void }) {
  const qc = useQueryClient();
  const { user } = useAuth();
  const roles = useRoles();
  const isAdmin = user?.role === 'clinic_admin';
  const isSelf = member.id === user?.id;
  const assignable = useAssignableBranches(isAdmin);
  // Şube yöneticisi yalnız kendi şubelerini görür/değiştirir; diğer atamalar sunucuda korunur.
  const myBranchIds = new Set(assignable.map((b) => b.id));
  const hiddenBranches = member.branchIds.filter((b) => !myBranchIds.has(b)).length;

  const form = useForm({
    initialValues: {
      roleId: member.roleId ?? '',
      branchIds: member.branchIds.filter((b) => myBranchIds.has(b)),
      active: member.active,
    },
    validate: {
      roleId: (v) => (v ? null : 'Rol seçin'),
      branchIds: (v) => (v.length === 0 && hiddenBranches === 0 ? 'Personel en az bir şubeye atanmalı' : null),
    },
  });

  const mutation = useMutation({
    mutationFn: async (v: typeof form.values) => {
      const roleChanged = v.roleId !== member.roleId;
      const ownBefore = member.branchIds.filter((b) => myBranchIds.has(b)).sort().join();
      if (!isSelf && v.branchIds.length && (roleChanged || ownBefore !== [...v.branchIds].sort().join())) {
        await api.put(`/v1/users/${member.id}/clinic-roles`, { roleId: v.roleId, clinicIds: v.branchIds });
      }
      if (v.active !== member.active) {
        await api.post(`/v1/users/${member.id}/${v.active ? 'activate' : 'deactivate'}`);
      }
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users', 'staff-view'] });
      void qc.invalidateQueries({ queryKey: ['clinic'] });
      notifySuccess('Personel güncellendi');
      onDone();
    },
    onError: notifyError,
  });

  const selectedRole = roles.data?.find((r) => r.id === form.values.roleId)?.code;

  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <div>
          <Text fw={600}>{member.fullName}</Text>
          <Text size="sm" c="dimmed">
            {member.email}
          </Text>
        </div>
        <Select
          label="Rol"
          allowDeselect={false}
          data={roleOptions(roles.data ?? [], isAdmin)}
          disabled={isSelf}
          description={isSelf ? 'Kendi rolünüzü değiştiremezsiniz.' : undefined}
          {...form.getInputProps('roleId')}
        />
        <MultiSelect
          label="Şubeler"
          placeholder="Şube seçin"
          disabled={isSelf}
          data={assignable.map((b) => ({ value: b.id, label: b.active ? b.name : `${b.name} (pasif)` }))}
          description={hiddenBranches ? `Yetkiniz dışındaki ${hiddenBranches} şube ataması korunur.` : undefined}
          {...form.getInputProps('branchIds')}
        />
        <Switch
          label="Aktif"
          description="Pasif personel sisteme giriş yapamaz. Aktifleştirme paket kullanıcı limitine tabidir."
          disabled={isSelf}
          {...form.getInputProps('active', { type: 'checkbox' })}
        />
        {selectedRole === 'veterinarian' && <VetProfile userId={member.id} />}
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

/** Muayene/aşı kaydı ve çalışma saati için hekim profili (lisans numarası) gerekir. */
function VetProfile({ userId }: { userId: string }) {
  const qc = useQueryClient();
  const [licenseNo, setLicenseNo] = useState('');
  const profile = useQuery({
    queryKey: ['vet-profile', userId],
    queryFn: async () => {
      try {
        return await api.get<{ id: string; licenseNo: string }>(`/v1/users/${userId}/veterinarian`);
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
  const create = useMutation({
    mutationFn: () => api.post(`/v1/users/${userId}/veterinarian`, { licenseNo: licenseNo.trim() }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['vet-profile', userId] });
      void qc.invalidateQueries({ queryKey: ['vets'] });
      notifySuccess('Hekim profili oluşturuldu');
    },
    onError: notifyError,
  });
  if (profile.isLoading || profile.error) return null;
  if (profile.data) {
    return (
      <Text size="sm" c="dimmed">
        Hekim lisans no: <b>{profile.data.licenseNo}</b>
      </Text>
    );
  }
  return (
    <Group align="flex-end" gap="xs">
      <TextInput
        label="Hekim lisans no"
        description="Muayene kaydı ve çalışma saatleri için gerekir."
        value={licenseNo}
        onChange={(e) => setLicenseNo(e.currentTarget.value)}
        style={{ flex: 1 }}
      />
      <Button variant="light" onClick={() => create.mutate()} loading={create.isPending} disabled={!licenseNo.trim()}>
        Profil oluştur
      </Button>
    </Group>
  );
}

// ------------------------------------------------------------------ davetler

function InvitesTab() {
  const qc = useQueryClient();
  const { user } = useAuth();
  const { allBranches } = useBranch();
  const isAdmin = user?.role === 'clinic_admin';
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<Invitation | null>(null);
  const [filter, setFilter] = useState('pending');
  const invites = useQuery({ queryKey: ['staff-invites'], queryFn: () => api.get<Invitation[]>('/v1/staff-invitations') });
  const rows = (invites.data ?? []).filter((i) => filter === 'all' || i.status === 'PENDING');
  const branchName = (id: string) => allBranches.find((b) => b.id === id)?.name ?? 'Diğer şube';
  const revoke = useMutation({
    mutationFn: (id: string) => api.post(`/v1/staff-invitations/${id}/revoke`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['staff-invites'] });
      notifySuccess('Davet iptal edildi');
    },
    onError: notifyError,
  });

  return (
    <Stack gap="sm">
      <Group justify="space-between">
        <SegmentedControl
          value={filter}
          onChange={setFilter}
          data={[
            { value: 'pending', label: 'Bekleyen' },
            { value: 'all', label: 'Tümü' },
          ]}
        />
        <Button leftSection={<IconUserPlus size={16} />} onClick={() => setCreating(true)}>
          Personel davet et
        </Button>
      </Group>
      <DataTable
        head={['E-posta', 'Rol', 'Şubeler', 'Gönderim', 'Son geçerlilik', 'Durum', '']}
        loading={invites.isLoading}
        error={invites.error}
        empty={rows.length === 0}
        emptyText={filter === 'pending' ? 'Bekleyen davet yok' : 'Davet yok'}
        minWidth={860}
      >
        {rows.map((i) => {
          const st = INVITE_STATUS[i.status];
          return (
            <Table.Tr key={i.id}>
              <Table.Td fw={500}>{i.email}</Table.Td>
              <Table.Td>
                <Badge color={ROLE_COLOR[i.roleCode] ?? 'gray'} variant="light">
                  {ROLE_LABEL[i.roleCode] ?? i.roleName}
                </Badge>
              </Table.Td>
              <Table.Td>{i.clinicIds.map(branchName).join(', ')}</Table.Td>
              <Table.Td>{dateTime(i.createdAt)}</Table.Td>
              <Table.Td>{dateTime(i.expiresAt)}</Table.Td>
              <Table.Td>
                <Badge color={st.color} variant="light">
                  {st.label}
                </Badge>
              </Table.Td>
              <Table.Td>
                {i.status === 'PENDING' && (isAdmin || i.roleCode !== 'clinic_admin') && (
                  <Tooltip label="Daveti iptal et">
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      loading={revoke.isPending && revoke.variables === i.id}
                      onClick={() => revoke.mutate(i.id)}
                      aria-label="Daveti iptal et"
                    >
                      <IconBan size={16} />
                    </ActionIcon>
                  </Tooltip>
                )}
              </Table.Td>
            </Table.Tr>
          );
        })}
      </DataTable>
      <Text size="xs" c="dimmed">
        Davet bağlantısı e-postayla gönderilir, 7 gün geçerlidir ve tek kullanımlıktır. Aynı adrese yeniden davet gönderilirse eski
        bağlantı geçersiz olur.
      </Text>

      <Modal
        opened={creating || created !== null}
        onClose={() => {
          setCreating(false);
          setCreated(null);
        }}
        title={created ? 'Davet gönderildi' : 'Personel davet et'}
      >
        {created ? (
          <Stack>
            <Alert color="teal" icon={<IconCheck size={16} />}>
              <b>{created.email}</b> için {(ROLE_LABEL[created.roleCode] ?? created.roleName).toLowerCase()} daveti oluşturuldu ve
              davet bağlantısı bu adrese e-postayla gönderildi. Bağlantı {dateTime(created.expiresAt)} tarihine kadar geçerlidir.
            </Alert>
            <Group justify="flex-end">
              <Button onClick={() => setCreated(null)}>Tamam</Button>
            </Group>
          </Stack>
        ) : (
          creating && (
            <InviteForm
              onCreated={(inv) => {
                setCreating(false);
                setCreated(inv);
              }}
              onCancel={() => setCreating(false)}
            />
          )
        )}
      </Modal>
    </Stack>
  );
}

function InviteForm({ onCreated, onCancel }: { onCreated: (i: Invitation) => void; onCancel: () => void }) {
  const qc = useQueryClient();
  const { user } = useAuth();
  const { branchId } = useBranch();
  const roles = useRoles();
  const isAdmin = user?.role === 'clinic_admin';
  const assignable = useAssignableBranches(isAdmin);
  const vetRoleId = roles.data?.find((r) => r.code === 'veterinarian')?.id ?? '';
  const form = useForm({
    initialValues: { email: '', roleId: '', branchIds: branchId ? [branchId] : ([] as string[]) },
    validate: {
      email: (v) => (/^\S+@\S+\.\S+$/.test(v.trim()) ? null : 'Geçerli bir e-posta girin'),
      roleId: (v) => (v || vetRoleId ? null : 'Rol seçin'),
      branchIds: (v) => (v.length === 0 ? 'En az bir şube seçin' : null),
    },
  });
  const mutation = useMutation({
    mutationFn: (v: typeof form.values) =>
      api.post<Invitation>('/v1/staff-invitations', {
        email: v.email.trim(),
        roleId: v.roleId || vetRoleId,
        clinicIds: v.branchIds,
      }),
    onSuccess: (inv) => {
      void qc.invalidateQueries({ queryKey: ['staff-invites'] });
      onCreated(inv);
    },
    onError: notifyError,
  });
  return (
    <form onSubmit={form.onSubmit((v) => mutation.mutate(v))}>
      <Stack>
        <TextInput label="E-posta" type="email" required {...form.getInputProps('email')} />
        <Select
          label="Rol"
          allowDeselect={false}
          data={roleOptions(roles.data ?? [], isAdmin)}
          {...form.getInputProps('roleId')}
          value={form.values.roleId || vetRoleId || null}
        />
        <MultiSelect
          label="Şubeler"
          required
          data={assignable.filter((b) => b.active).map((b) => ({ value: b.id, label: b.name }))}
          {...form.getInputProps('branchIds')}
        />
        <Text size="xs" c="dimmed">
          Davet oluşturulurken ve kabul edilirken paket kullanıcı limiti kontrol edilir.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel}>
            Vazgeç
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Davet oluştur
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

// ------------------------------------------------------------------ yetki matrisi

function MatrixTab() {
  const { user } = useAuth();
  const matrix = useQuery({
    queryKey: ['permission-matrix'],
    queryFn: () => api.get<PermissionMatrixData>('/v1/permission-matrix'),
    staleTime: Infinity,
  });
  const grid = useMemo(
    () => new Map((matrix.data?.roles ?? []).map((r) => [r.code, new Set(r.permissions)])),
    [matrix.data],
  );
  if (matrix.isLoading) return <TableSkeleton rows={10} cols={5} />;
  if (matrix.error || !matrix.data) return <ErrorAlert error={matrix.error} />;
  const { permissions, roles } = matrix.data;

  return (
    <Stack gap="sm">
      <Text size="sm" c="dimmed">
        Roller ve yetkileri sistem genelinde sabittir. Personelin hangi şubelerde çalışacağı "Personel" sekmesinden ayarlanır.
      </Text>
      <Paper withBorder radius="md">
        <ScrollArea type="auto">
          <Table miw={760} verticalSpacing="xs" striped highlightOnHover withColumnBorders stickyHeader>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Yetki</Table.Th>
                {roles.map((r) => (
                  <Table.Th key={r.id} ta="center" w={140}>
                    <Badge color={ROLE_COLOR[r.code] ?? 'gray'} variant={user?.role === r.code ? 'filled' : 'light'}>
                      {r.name}
                    </Badge>
                  </Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {permissions.map((p) => (
                <Table.Tr key={p.code}>
                  <Table.Td>
                    <Text size="sm">{p.description}</Text>
                    <Text size="xs" c="dimmed" ff="monospace">
                      {p.code}
                    </Text>
                  </Table.Td>
                  {roles.map((r) => (
                    <Table.Td key={r.id} ta="center">
                      {grid.get(r.code)?.has(p.code) ? (
                        <IconCheck size={18} color="var(--mantine-color-teal-7)" aria-label="Var" />
                      ) : (
                        <IconX size={16} color="var(--mantine-color-gray-4)" aria-label="Yok" />
                      )}
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Paper>
    </Stack>
  );
}
