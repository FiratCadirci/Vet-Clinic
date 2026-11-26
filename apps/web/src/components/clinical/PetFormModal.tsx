import { Button, Group, Modal, Select, SimpleGrid, Stack, Switch, TextInput, Textarea } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useBranch } from '../../lib/branch';
import { notifyError, notifySuccess } from '../../lib/notify';
import { invalidatePatientData } from './hooks';
import { SEX_OPTIONS } from './labels';
import { createPet, listBreeds, listSpecies, updatePet } from './patientApi';
import { OwnerSelect, type Option } from './SearchSelect';
import type { OwnerListItem, Pet, Sex } from './types';

const orNull = (v: string) => (v.trim() === '' ? null : v.trim());

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Verilirse düzenleme modu (sahip, şube ve tür değiştirilemez). */
  pet?: Pet;
  /** Yeni hasta için önceden seçili sahip. */
  owner?: { id: string; fullName: string; phone: string };
  onSaved?: (pet: Pet) => void;
}

export function PetFormModal({ opened, onClose, pet, owner, onSaved }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={pet ? `${pet.name} — düzenle` : 'Yeni hasta'} size="lg">
      <PetForm pet={pet} owner={owner} onClose={onClose} onSaved={onSaved} />
    </Modal>
  );
}

function PetForm({ pet, owner, onClose, onSaved }: Omit<Props, 'opened'>) {
  const qc = useQueryClient();
  const { branches, branchId } = useBranch();
  const [today] = useState(() => new Date());
  const species = useQuery({ queryKey: ['species'], queryFn: listSpecies, staleTime: Infinity });
  const form = useForm({
    initialValues: {
      ownerId: owner?.id ?? (null as string | null),
      branchId: branchId as string | null,
      name: pet?.name ?? '',
      speciesId: (pet?.speciesId ?? null) as string | null,
      breedId: (pet?.breedId ?? null) as string | null,
      birthDate: (pet?.birthDate ?? null) as string | null,
      sex: (pet?.sex ?? 'unknown') as Sex,
      microchipNo: pet?.microchipNo ?? '',
      allergies: pet?.allergies ?? '',
      healthNotes: pet?.healthNotes ?? '',
      deceased: pet?.deceased ?? false,
    },
    validate: {
      ownerId: (v) => (!pet && !v ? 'Hayvan sahibi seçin' : null),
      branchId: (v) => (!pet && !v ? 'Şube seçin' : null),
      name: (v) => (v.trim() ? null : 'Hasta adı zorunlu'),
      speciesId: (v) => (v ? null : 'Tür seçin'),
      birthDate: (v) => (v && dayjs(v).isAfter(dayjs()) ? 'Doğum tarihi gelecekte olamaz' : null),
    },
  });

  // Yeni hasta formunda varsayılan tür köpek.
  const dog = species.data?.find((x) => x.code === 'DOG')?.id ?? null;
  const speciesId = form.values.speciesId ?? dog;
  const breeds = useQuery({
    queryKey: ['breeds', speciesId],
    queryFn: () => listBreeds(speciesId!),
    enabled: !!speciesId,
    staleTime: Infinity,
  });

  const save = useMutation({
    mutationFn: (v: typeof form.values) => {
      const input = {
        name: v.name.trim(),
        speciesId: v.speciesId ?? dog ?? '',
        breedId: v.breedId,
        birthDate: v.birthDate ? dayjs(v.birthDate).format('YYYY-MM-DD') : null,
        sex: v.sex,
        microchipNo: orNull(v.microchipNo),
        allergies: orNull(v.allergies),
        healthNotes: orNull(v.healthNotes),
        deceased: v.deceased,
      };
      return pet ? updatePet(pet, input) : createPet(v.ownerId!, input);
    },
    onSuccess: (saved) => {
      invalidatePatientData(qc, saved.id);
      notifySuccess(pet ? 'Hasta bilgileri güncellendi' : 'Hasta kaydedildi');
      onSaved?.(saved);
      onClose();
    },
    onError: (err) => {
      // Kısmi kayıt olabilir (ör. hasta kaydedildi, mikroçip reddedildi); listeler yenilenir.
      invalidatePatientData(qc, pet?.id);
      notifyError(err);
    },
  });

  const ownerInitial: Option<OwnerListItem> | undefined = owner
    ? { value: owner.id, label: `${owner.fullName} — ${owner.phone}` }
    : undefined;

  return (
    <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
      <Stack>
        {!pet && (
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <OwnerSelect
              withAsterisk
              disabled={!!owner}
              initialOption={ownerInitial}
              value={form.values.ownerId}
              onChange={(v) => form.setFieldValue('ownerId', v)}
              error={form.errors.ownerId}
            />
            <Select
              label="Kayıt şubesi"
              withAsterisk
              data={branches.map((b) => ({ value: b.id, label: b.name }))}
              allowDeselect={false}
              disabled
              description="Hasta, üst menüde seçili şubeye kaydedilir"
              {...form.getInputProps('branchId')}
            />
          </SimpleGrid>
        )}
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput label="Hasta adı" withAsterisk data-autofocus {...form.getInputProps('name')} />
          <Select
            label="Tür"
            data={(species.data ?? []).map((x) => ({ value: x.id, label: x.name }))}
            allowDeselect={false}
            disabled={!!pet}
            {...form.getInputProps('speciesId')}
            value={speciesId}
            onChange={(v) => {
              form.setFieldValue('speciesId', v);
              form.setFieldValue('breedId', null);
            }}
          />
          <Select
            label="Irk"
            searchable
            clearable
            data={(breeds.data ?? []).map((b) => ({ value: b.id, label: b.name }))}
            {...form.getInputProps('breedId')}
          />
          <Select label="Cinsiyet" data={SEX_OPTIONS} allowDeselect={false} {...form.getInputProps('sex')} />
          <DateInput
            label="Doğum tarihi"
            valueFormat="DD.MM.YYYY"
            maxDate={today}
            clearable
            {...form.getInputProps('birthDate')}
          />
          <TextInput
            label="Mikroçip no"
            disabled={!!pet?.microchipNo}
            {...form.getInputProps('microchipNo')}
          />
        </SimpleGrid>
        <Textarea label="Alerjiler" autosize minRows={2} {...form.getInputProps('allergies')} />
        <Textarea label="Sağlık notları" autosize minRows={2} {...form.getInputProps('healthNotes')} />
        {pet && (
          <Switch
            label="Vefat etti"
            description="Vefat etmiş hasta için randevu ve aşı kaydı açılamaz"
            color="red"
            disabled={pet.deceased}
            {...form.getInputProps('deceased', { type: 'checkbox' })}
          />
        )}
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
