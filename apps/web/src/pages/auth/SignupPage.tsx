import {
  Alert,
  Anchor,
  Button,
  Center,
  Divider,
  Paper,
  PasswordInput,
  Radio,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Textarea,
  Title,
  Group,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconInfoCircle, IconPaw } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import type { PlanOption } from '../../components/admin/orgTypes';
import { api } from '../../lib/api';
import { useAuth, type TenantAuthResponse } from '../../lib/auth';
import { money } from '../../lib/format';

const EMAIL = /^\S+@\S+\.\S+$/;

export function SignupPage() {
  const { user, acceptTenantSession } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Paket listesi oturumsuz okunur; liste gelmezse deneme en uygun aylık paketle başlar.
  const plans = useQuery({
    queryKey: ['plans', 'public'],
    queryFn: async () => (await api.get<PlanOption[]>('/auth/signup/plans')).filter((p) => p.billingPeriod === 'MONTHLY'),
    retry: false,
  });

  const form = useForm({
    initialValues: {
      clinicName: '',
      branchName: 'Merkez Şube',
      phone: '',
      email: '',
      address: '',
      taxNumber: '',
      adminFullName: '',
      adminEmail: '',
      password: '',
      passwordConfirm: '',
      planCode: '',
    },
    validate: {
      clinicName: (v) => (v.trim().length >= 2 ? null : 'Klinik adı en az 2 karakter olmalı'),
      branchName: (v) => (v.trim().length >= 2 ? null : 'Şube adı en az 2 karakter olmalı'),
      phone: (v) => (!v.trim() || v.trim().length >= 7 ? null : 'Telefon en az 7 karakter olmalı'),
      email: (v) => (!v.trim() || EMAIL.test(v.trim()) ? null : 'Geçerli bir e-posta girin'),
      adminFullName: (v) => (v.trim().length >= 2 ? null : 'Ad soyad zorunlu'),
      adminEmail: (v) => (EMAIL.test(v.trim()) ? null : 'Geçerli bir e-posta girin'),
      password: (v) => (v.length >= 10 ? null : 'Parola en az 10 karakter olmalı'),
      passwordConfirm: (v, values) => (v === values.password ? null : 'Parolalar eşleşmiyor'),
    },
  });

  if (user) return <Navigate to={user.kind === 'platform_admin' ? '/platform' : '/'} replace />;

  const submit = form.onSubmit(async (v) => {
    setBusy(true);
    setError(null);
    try {
      const s = await api.post<TenantAuthResponse>('/auth/signup', {
        clinicName: v.clinicName.trim(),
        branchName: v.branchName.trim(),
        phone: v.phone.trim() || null,
        email: v.email.trim() || null,
        address: v.address.trim() || null,
        taxNumber: v.taxNumber.trim() || null,
        adminFullName: v.adminFullName.trim(),
        adminEmail: v.adminEmail.trim(),
        password: v.password,
        ...(v.planCode.trim() && { planCode: v.planCode.trim() }),
      });
      if (!s.accessToken) throw new Error('Hesap oluşturuldu ancak oturum açılamadı; lütfen giriş yapın.');
      acceptTenantSession(s, v.adminEmail.trim().toLowerCase());
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  });

  return (
    <Center mih="100vh" bg="teal.0" py="xl" px="md">
      <Paper w="100%" maw={640} p="xl" radius="md" shadow="md">
        <form onSubmit={submit}>
          <Stack>
            <Stack gap={4} align="center">
              <IconPaw size={40} color="var(--mantine-color-teal-6)" />
              <Title order={2}>Klinik hesabı oluşturun</Title>
              <Text c="dimmed" size="sm" ta="center">
                14 gün ücretsiz deneyin. Kredi kartı gerekmez.
              </Text>
            </Stack>
            {error && <Alert color="red">{error}</Alert>}

            <Divider label="Klinik bilgileri" labelPosition="left" />
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <TextInput
                label="Klinik adı"
                required
                placeholder="ör. Pati Veteriner Kliniği"
                {...form.getInputProps('clinicName')}
              />
              <TextInput label="İlk şube adı" required {...form.getInputProps('branchName')} />
              <TextInput label="Telefon" placeholder="0212 000 00 00" {...form.getInputProps('phone')} />
              <TextInput label="Klinik e-postası" type="email" {...form.getInputProps('email')} />
            </SimpleGrid>
            <Textarea label="Adres" autosize minRows={2} {...form.getInputProps('address')} />
            <TextInput label="Vergi numarası" {...form.getInputProps('taxNumber')} />

            <Divider label="Yönetici hesabı" labelPosition="left" />
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <TextInput label="Ad soyad" required {...form.getInputProps('adminFullName')} />
              <TextInput
                label="E-posta (giriş için)"
                type="email"
                required
                autoComplete="username"
                {...form.getInputProps('adminEmail')}
              />
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
            </SimpleGrid>

            <Divider label="Paket" labelPosition="left" />
            {plans.data?.length ? (
              <Radio.Group {...form.getInputProps('planCode')}>
                <Stack gap="xs">
                  {plans.data.map((p) => (
                    <Radio.Card key={p.id} value={p.code} p="sm" radius="md">
                      <Group wrap="nowrap" align="flex-start">
                        <Radio.Indicator />
                        <div>
                          <Text fw={600}>{p.name}</Text>
                          <Text size="sm" c="dimmed">
                            {money(Math.round(p.price * 100))} / ay · {p.maxBranches} şube · {p.maxUsers} kullanıcı
                          </Text>
                        </div>
                      </Group>
                    </Radio.Card>
                  ))}
                </Stack>
              </Radio.Group>
            ) : (
              <Alert color="teal" variant="light" icon={<IconInfoCircle size={16} />}>
                Deneme süresi en uygun başlangıç paketiyle başlar. Paketinizi giriş yaptıktan sonra <b>Abonelik</b> ekranından
                istediğiniz zaman değiştirebilirsiniz.
              </Alert>
            )}

            <Button type="submit" loading={busy} fullWidth size="md">
              Hesabı oluştur ve başla
            </Button>
            <Text size="sm" ta="center">
              Zaten hesabınız var mı?{' '}
              <Anchor component={Link} to="/login">
                Giriş yapın
              </Anchor>
            </Text>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
