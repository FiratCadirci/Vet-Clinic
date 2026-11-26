import { Alert, Anchor, Button, Center, Paper, PasswordInput, Select, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconPaw } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { AmbiguousOrganizationError } from '../lib/session';

export function LoginPage() {
  const { user, login, reload, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [organizations, setOrganizations] = useState<{ code: number; name: string }[]>([]);
  const form = useForm({ initialValues: { email: '', password: '', organizationCode: '' } });
  const pwd = useForm({
    initialValues: { newPassword: '', repeat: '' },
    validate: {
      newPassword: (v) => (v.length < 10 ? 'En az 10 karakter olmalı' : null),
      repeat: (v, all) => (v !== all.newPassword ? 'Parolalar aynı değil' : null),
    },
  });

  if (user && !user.mustChangePassword) return <Navigate to={user.kind === 'platform_admin' ? '/platform' : '/'} replace />;

  const submit = form.onSubmit(async (values) => {
    setBusy(true);
    setError(null);
    try {
      const code = values.organizationCode ? Number(values.organizationCode) : undefined;
      const me = await login(values.email, values.password, code);
      if (!me.mustChangePassword) navigate(me.kind === 'platform_admin' ? '/platform' : '/', { replace: true });
    } catch (e) {
      if (e instanceof AmbiguousOrganizationError) setOrganizations(e.organizations);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  });

  const changePassword = pwd.onSubmit(async (values) => {
    setBusy(true);
    setError(null);
    try {
      await api.post('/auth/change-password', { currentPassword: form.values.password, newPassword: values.newPassword });
      await reload();
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  });

  return (
    <Center mih="100vh" bg="teal.0">
      <Paper w={380} p="xl" radius="md" shadow="md">
        <Stack gap={4} align="center" mb="md">
          <IconPaw size={40} color="var(--mantine-color-teal-6)" />
          <Title order={2}>VetClinic</Title>
          <Text c="dimmed" size="sm">
            Klinik paneline giriş
          </Text>
        </Stack>
        {user?.mustChangePassword ? (
          <form onSubmit={changePassword}>
            <Stack>
              <Alert color="yellow">İlk girişte parolanızı değiştirmeniz gerekir.</Alert>
              {error && <Alert color="red">{error}</Alert>}
              <PasswordInput label="Yeni parola" required autoComplete="new-password" {...pwd.getInputProps('newPassword')} />
              <PasswordInput label="Yeni parola (tekrar)" required autoComplete="new-password" {...pwd.getInputProps('repeat')} />
              <Button type="submit" loading={busy} fullWidth>
                Parolayı değiştir
              </Button>
              <Anchor component="button" type="button" size="sm" onClick={logout}>
                Çıkış yap
              </Anchor>
            </Stack>
          </form>
        ) : (
          <form onSubmit={submit}>
            <Stack>
              {error && <Alert color="red">{error}</Alert>}
              <TextInput label="E-posta" type="email" required autoComplete="username" {...form.getInputProps('email')} />
              <PasswordInput label="Parola" required autoComplete="current-password" {...form.getInputProps('password')} />
              {organizations.length > 0 && (
                <Select
                  label="Klinik"
                  required
                  data={organizations.map((o) => ({ value: String(o.code), label: o.name }))}
                  {...form.getInputProps('organizationCode')}
                />
              )}
              <Button type="submit" loading={busy} fullWidth>
                Giriş yap
              </Button>
              <Text size="sm" ta="center">
                Kliniğiniz yok mu? <Anchor component={Link} to="/signup">Klinik hesabı oluşturun</Anchor>
              </Text>
            </Stack>
          </form>
        )}
      </Paper>
    </Center>
  );
}
