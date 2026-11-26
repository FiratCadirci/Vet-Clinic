import { Button, Group, Modal, SimpleGrid, Stack, Text, TextInput, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createOwner, duplicateCandidates, updateOwner, type OwnerInput } from './patientApi';
import { notifyError, notifySuccess } from '../../lib/notify';
import type { Owner } from './types';

const orNull = (v: string) => (v.trim() === '' ? null : v.trim());

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Verilirse düzenleme modu. */
  owner?: Owner;
  onSaved?: (owner: Owner) => void;
}

export function OwnerFormModal({ opened, onClose, owner, onSaved }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={owner ? 'Sahip bilgilerini düzenle' : 'Yeni hayvan sahibi'} size="lg">
      <OwnerForm owner={owner} onClose={onClose} onSaved={onSaved} />
    </Modal>
  );
}

function OwnerForm({ owner, onClose, onSaved }: Omit<Props, 'opened'>) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      fullName: owner?.fullName ?? '',
      phone: owner?.phone ?? '',
      email: owner?.email ?? '',
      address: owner?.address ?? '',
    },
    validate: {
      fullName: (v) => (v.trim().length < 2 ? 'Ad soyad en az 2 karakter olmalı' : null),
      phone: (v) => (v.replace(/\D/g, '').length < 7 ? 'Geçerli bir telefon girin' : null),
      email: (v) => (v.trim() && !/^\S+@\S+\.\S+$/.test(v.trim()) ? 'Geçerli bir e-posta girin' : null),
    },
  });

  const save = useMutation({
    mutationFn: ({ v, confirmDuplicate }: { v: typeof form.values; confirmDuplicate?: boolean }) => {
      const body: OwnerInput = {
        fullName: v.fullName.trim(),
        phone: v.phone.trim(),
        email: orNull(v.email),
        address: orNull(v.address),
      };
      return owner ? updateOwner(owner.id, body) : createOwner(body, confirmDuplicate);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['owners'] });
      qc.invalidateQueries({ queryKey: ['owner', saved.id] });
      qc.invalidateQueries({ queryKey: ['pet'] });
      qc.invalidateQueries({ queryKey: ['pets'] });
      notifySuccess(owner ? 'Sahip bilgileri güncellendi' : 'Hayvan sahibi kaydedildi');
      onSaved?.(saved);
      onClose();
    },
    onError: (err, { v }) => {
      // Aynı telefon / e-posta başka kayıtta varsa önce kullanıcıya sorulur (mükerrer kayıt kontrolü).
      const similar = duplicateCandidates(err);
      if (!similar) return notifyError(err);
      modals.openConfirmModal({
        title: 'Benzer kayıt bulundu',
        children: <Text size="sm">Aynı telefon veya e-postayla kayıtlı sahip: {similar.join(', ')}. Yine de yeni kayıt oluşturulsun mu?</Text>,
        labels: { confirm: 'Yine de oluştur', cancel: 'Vazgeç' },
        onConfirm: () => save.mutate({ v, confirmDuplicate: true }),
      });
    },
  });

  return (
    <form onSubmit={form.onSubmit((v) => save.mutate({ v }))}>
      <Stack>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput label="Ad soyad" withAsterisk data-autofocus {...form.getInputProps('fullName')} />
          <TextInput label="Telefon" withAsterisk placeholder="05xx xxx xx xx" {...form.getInputProps('phone')} />
          <TextInput
            label="E-posta"
            description="Mobil uygulama hesabıyla eşleştirmede kullanılır"
            {...form.getInputProps('email')}
          />
        </SimpleGrid>
        <Textarea label="Adres" autosize minRows={2} {...form.getInputProps('address')} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" loading={save.isPending}>
            Kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
