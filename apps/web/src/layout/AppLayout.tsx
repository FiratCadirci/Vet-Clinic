import { AppShell, Avatar, Badge, Burger, Group, Menu, NavLink, ScrollArea, Select, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconLogout, IconPaw } from '@tabler/icons-react';
import { Navigate, Outlet, NavLink as RouterNavLink, useLocation } from 'react-router';
import { useAuth } from '../lib/auth';
import { ROLE_LABEL } from '../lib/session';
import { useBranch } from '../lib/branch';
import { CLINIC_NAV, PLATFORM_NAV } from './nav';

export function AppLayout({ area }: { area: 'clinic' | 'platform' }) {
  const { user, logout, can } = useAuth();
  const { branches, branchId, setBranchId } = useBranch();
  const [opened, { toggle, close }] = useDisclosure();
  const location = useLocation();

  if (!user || user.mustChangePassword) return <Navigate to="/login" replace state={{ from: location }} />;
  if (area === 'clinic' && user.kind === 'platform_admin') return <Navigate to="/platform" replace />;
  if (area === 'platform' && user.kind !== 'platform_admin') return <Navigate to="/" replace />;

  const items = (area === 'clinic' ? CLINIC_NAV : PLATFORM_NAV).filter((i) => !i.permission || can(i.permission));

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 250, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="xs" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <IconPaw size={26} color="var(--mantine-color-teal-6)" />
            <Text fw={700} size="lg">
              VetClinic
            </Text>
            {area === 'platform' && <Badge variant="light">Platform yönetimi</Badge>}
          </Group>
          <Group gap="sm" wrap="nowrap">
            {area === 'clinic' && branches.length > 0 && (
              <Select
                aria-label="Şube"
                size="sm"
                w={200}
                data={branches.map((b) => ({ value: b.id, label: b.name }))}
                value={branchId}
                onChange={(v) => v && setBranchId(v)}
                allowDeselect={false}
              />
            )}
            <Menu position="bottom-end" withinPortal>
              <Menu.Target>
                <UnstyledButton>
                  <Group gap="xs" wrap="nowrap">
                    <Avatar color="teal" radius="xl" size="sm">
                      {user.fullName.slice(0, 1)}
                    </Avatar>
                    <div style={{ lineHeight: 1.1 }}>
                      <Text size="sm" fw={500}>
                        {user.fullName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {user.role ? ROLE_LABEL[user.role] : 'Platform yöneticisi'}
                      </Text>
                    </div>
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user.email}</Menu.Label>
                <Menu.Item leftSection={<IconLogout size={16} />} onClick={logout}>
                  Çıkış yap
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          {items.map((item) => (
            <NavLink
              key={item.to}
              component={RouterNavLink}
              to={item.to}
              end={item.to === '/' || item.to === '/platform'}
              label={item.label}
              leftSection={<item.icon size={18} stroke={1.6} />}
              onClick={close}
              style={{ borderRadius: 8 }}
            />
          ))}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main bg="gray.0">
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
