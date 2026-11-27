import { Alert, Anchor, Button, Center, Loader, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconMailOpened } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import type { InvitationPreview } from '../../components/admin/orgTypes';
import { useAuth, type TenantAuthResponse } from '../../lib/auth';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';

/** Davet bağlantısı: `${WEB_URL}/accept-invite?token=...` (e-postayla gelir). */
export function AcceptInvitePage() {
  const { user, acceptTenantSession, logout } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const tokenFromUrl = params.get('token') ?? '';
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const form = useForm({
    initialValues: { token: tokenFromUrl, fullName: '', password: '', passwordConfirm: '' },
    validate: {
      token: (v) => (v.trim().length >= 10 ? null : 'Geçerli bir davet kodu girin'),
      fullName: (v) => (v.trim().length >= 2 ? null : 'Ad soyad zorunlu'),
      password: (v) => (v.length >= 10 ? null : 'Parola en az 10 karakter olmalı'),
      passwordConfirm: (v, values) => (v === values.password ? null : 'Parolalar eşleşmiyor'),
    },
  });

  const token = form.values.token.trim();
  // Davet önizlemesi: klinik adı, e-posta ve rol. Süresi dolmuş / iptal edilmiş davette sunucu açıklayıcı hata döner.
  const preview = useQuery({
    queryKey: ['invite-preview', token],
    queryFn: () => api.post<InvitationPreview>('/auth/invitations/preview', { token }),
    enabled: token.length >= 10,
    retry: false,
  });

  const submit = form.onSubmit(async (v) => {
    setBusy(true);
    setError(null);
    try {
      const s = await api.post<TenantAuthResponse>('/auth/accept-invite', {
        token: v.token.trim(),
        fullName: v.fullName.trim(),
        password: v.password,
      });
      if (!s.accessToken) throw new Error('Hesap oluşturuldu ancak oturum açılamadı; lütfen giriş yapın.');
      acceptTenantSession(s, preview.data?.email);
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  });

  return (
    <Center mih="100vh" bg="teal.0" p="md">
      <Paper w="100%" maw={420} p="xl" radius="md" shadow="md">
        <form onSubmit={submit}>
          <Stack>
            <Stack gap={4} align="center">
              <IconMailOpened size={40} color="var(--mantine-color-teal-6)" />
              <Title order={2}>Ekibe katılın</Title>
              <Text c="dimmed" size="sm" ta="center">
                Klinik davetinizi kabul etmek için hesabınızı oluşturun. E-posta adresiniz davetten alınır.
              </Text>
            </Stack>
            {user && (
              <Alert color="yellow">
                Şu an <b>{user.email}</b> olarak oturum açıksınız. Daveti kabul ettiğinizde yeni hesapla devam edilir.{' '}
                <Anchor component="button" type="button" size="sm" onClick={logout}>
                  Çıkış yap
                </Anchor>
              </Alert>
            )}
            {preview.isLoading && (
              <Center>
                <Loader size="sm" />
              </Center>
            )}
            {preview.data && (
              <Alert color="teal" variant="light">
                <b>{preview.data.organizationName}</b> sizi <b>{preview.data.roleName.toLowerCase()}</b> olarak davet etti.
                <br />
                {preview.data.email} · {dateTime(preview.data.expiresAt)} tarihine kadar geçerli
              </Alert>
            )}
            {preview.error && <Alert color="red">{preview.error.message}</Alert>}
            {error && <Alert color="red">{error}</Alert>}
            {!tokenFromUrl && <TextInput label="Davet kodu" required {...form.getInputProps('token')} />}
            <TextInput label="Ad soyad" required autoComplete="name" {...form.getInputProps('fullName')} />
            <PasswordInput
              label="Parola"
              required
              autoComplete="new-password"
              description="En az 10 karakter"
              {...form.getInputProps('password')}
            />
            <PasswordInput
              label="Parola (tekrar)"
              required
              autoComplete="new-password"
              {...form.getInputProps('passwordConfirm')}
            />
            <Button type="submit" loading={busy} fullWidth disabled={Boolean(preview.error)}>
              Daveti kabul et
            </Button>
            <Text size="sm" ta="center">
              <Anchor component={Link} to="/login">
                Giriş sayfasına dön
              </Anchor>
            </Text>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
