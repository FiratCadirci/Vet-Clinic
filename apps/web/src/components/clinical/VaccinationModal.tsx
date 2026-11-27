import {
  Alert,
  Button,
  Checkbox,
  Group,
  Modal,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { DateInput, DateTimePicker } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { IconInfoCircle } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useAuth } from '../../lib/auth';
import { useBranch } from '../../lib/branch';
import { SPECIES } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { listExams } from './examApi';
import { invalidatePatientData } from './hooks';
import { speciesKey } from './patientApi';
import { PetSelect } from './SearchSelect';
import type { Species } from './types';
import { getPetSchedule, listActiveProtocols, recordVaccination, useProductOptions } from './vaccineApi';

export interface VaccinationTarget {
  id: string;
  name: string;
  species: Species;
}

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Verilmezse modal içinde hasta seçilir. */
  pet?: VaccinationTarget;
  /** Aşı kaydı bir muayeneye bağlıdır; verilmezse hastanın bu şubedeki açık muayenelerinden seçilir. */
  examinationId?: string;
  /** Takvimden önceden seçilecek kalem (protokol) veya eşleştirilecek aşı adı. */
  templateItemId?: string;
  vaccineName?: string;
  onSaved?: () => void;
}

/** "Aşı uygula": onaylı takvim kalemi (protokol dozu) veya serbest aşı adı, parti no ve isteğe bağlı stoktan düşülecek aşı ürünü. */
export function VaccinationModal({ opened, onClose, ...rest }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title="Aşı uygula" size="lg">
      <VaccinationForm onClose={onClose} {...rest} />
    </Modal>
  );
}

/** Türün onaylı protokolleri (aşı kaydında yalnız bunlar kullanılabilir) ve onay bekleyen taslak var mı. */
function useApprovedProtocols(species: Species | undefined) {
  const list = useQuery({
    queryKey: ['vaccine-protocols', 'active'],
    queryFn: () => listActiveProtocols(),
    enabled: !!species,
  });
  const own = (list.data ?? []).filter((p) => speciesKey(p.speciesCode) === species);
  return {
    protocols: own.filter((p) => p.status === 'APPROVED'),
    pending: own.some((p) => p.status === 'DRAFT'),
    isLoading: list.isLoading,
  };
}

function VaccinationForm({ onClose, pet: fixedPet, examinationId, templateItemId, vaccineName, onSaved }: Omit<Props, 'opened'>) {
  const qc = useQueryClient();
  const { can } = useAuth();
  const { branchId, branch } = useBranch();
  const [pickedPet, setPickedPet] = useState<VaccinationTarget | undefined>();
  const pet = fixedPet ?? pickedPet;
  const { protocols, pending, isLoading } = useApprovedProtocols(pet?.species);
  const products = useProductOptions('VACCINE');
  const [modeState, setMode] = useState<'template' | 'free' | null>(null);
  const [now] = useState(() => new Date());

  // Muayene seçimi: hastanın bu şubede açık muayeneleri.
  const exams = useQuery({
    queryKey: ['examinations', { animalId: pet?.id, open: true }],
    queryFn: () => listExams({ animalId: pet!.id, size: 50 }),
    enabled: !!pet && !examinationId,
  });
  const openExams = (exams.data ?? []).filter((e) => e.status !== 'closed' && e.branchId === branchId);
  const schedule = useQuery({
    queryKey: ['vaccine-schedule', pet?.id],
    queryFn: () => getPetSchedule(pet!.id),
    enabled: !!pet,
  });

  const matched =
    protocols.find((p) => p.id === templateItemId) ??
    protocols.find((p) => vaccineName && p.name.toLocaleLowerCase('tr') === vaccineName.toLocaleLowerCase('tr'));
  const mode = protocols.length ? (modeState ?? (vaccineName && !matched ? 'free' : 'template')) : 'free';

  const form = useForm({
    initialValues: {
      encounterId: null as string | null,
      templateItemId: null as string | null,
      doseId: null as string | null,
      vaccineName: vaccineName ?? '',
      lotNo: '',
      serialNo: '',
      administeredAt: null as string | null,
      productId: null as string | null,
      nextDueAt: null as string | null,
      noNextDose: false,
    },
  });
  const encounterId = examinationId ?? form.values.encounterId ?? (openExams.length === 1 ? openExams[0].id : null);
  const itemId = form.values.templateItemId ?? matched?.id ?? null;
  const protocol = protocols.find((p) => p.id === itemId);
  // Varsayılan doz: takvimdeki sıradaki doz (rapelde son doz; rapel aralığı son dozdan hesaplanır).
  const next = schedule.data?.items.find((i) => i.templateItemId === itemId)?.next;
  const defaultDose = protocol?.doses.find((d) => d.doseNumber === Math.min(next?.doseNumber ?? 1, protocol.doses.length));
  const doseId = form.values.doseId ?? defaultDose?.id ?? null;
  const selectedProduct = products.data?.find((p) => p.id === form.values.productId);

  const save = useMutation({
    mutationFn: () => {
      const v = form.values;
      const nextDueAt = mode === 'free' && v.noNextDose ? null : v.nextDueAt ? dayjs(v.nextDueAt).format('YYYY-MM-DD') : undefined;
      return recordVaccination({
        encounterId: encounterId!,
        protocolDoseId: mode === 'template' ? doseId : null,
        vaccineName: mode === 'free' ? v.vaccineName.trim() : null,
        batchNumber: v.lotNo.trim() || null,
        certificateNo: v.serialNo.trim() || null,
        administeredAt: v.administeredAt ? dayjs(v.administeredAt).format() : undefined,
        productId: v.productId,
        nextDueAt,
      });
    },
    onSuccess: (res) => {
      invalidatePatientData(qc, pet!.id);
      qc.invalidateQueries({ queryKey: ['vaccinations'] });
      qc.invalidateQueries({ queryKey: ['stock'] });
      qc.invalidateQueries({ queryKey: ['inventory'] });
      qc.invalidateQueries({ queryKey: ['reports'] });
      qc.invalidateQueries({ queryKey: ['examination', res.encounterId] });
      notifySuccess(res.vaccineProductId && res.batchNumber ? `Aşı kaydedildi. Stoktan düşülen lot: ${res.batchNumber}` : 'Aşı kaydedildi');
      onSaved?.();
      onClose();
    },
    onError: notifyError,
  });

  const submit = form.onSubmit(() => {
    if (!pet) { void form.setFieldError('templateItemId', 'Hasta seçin'); return; }
    if (!branchId) { void notifyError('Önce üst menüden şube seçin'); return; }
    if (!encounterId) { void form.setFieldError('encounterId', 'Muayene seçin'); return; }
    if (mode === 'template' && !itemId) { void form.setFieldError('templateItemId', 'Takvimden aşı seçin'); return; }
    if (mode === 'template' && !doseId) { void form.setFieldError('doseId', 'Doz seçin'); return; }
    if (mode === 'free' && form.values.vaccineName.trim().length < 1 && !form.values.productId) {
      void form.setFieldError('vaccineName', 'Aşı adı girin');
      return;
    }
    save.mutate();
  });

  return (
    <form onSubmit={submit}>
      <Stack>
        {fixedPet ? (
          <Text size="sm">
            Hasta: <b>{fixedPet.name}</b> ({SPECIES[fixedPet.species]}) · Şube: <b>{branch?.name ?? '—'}</b>
          </Text>
        ) : (
          <PetSelect
            withAsterisk
            value={pickedPet?.id ?? null}
            onChange={(_, p) => {
              setPickedPet(p ? { id: p.id, name: p.name, species: p.species } : undefined);
              form.setFieldValue('templateItemId', null);
              form.setFieldValue('doseId', null);
              form.setFieldValue('encounterId', null);
              setMode(null);
            }}
          />
        )}

        {pet && !examinationId && !exams.isLoading && openExams.length === 0 && (
          <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
            Aşı kaydı bir muayeneye bağlanır. Bu hasta için bu şubede açık muayene yok; önce "Aşı ziyareti" türünde muayene başlatın.
          </Alert>
        )}
        {pet && !examinationId && openExams.length > 0 && (
          <Select
            label="Muayene"
            withAsterisk
            data={openExams.map((e) => ({ value: e.id, label: `${dayjs(e.startedAt).format('DD.MM.YYYY HH:mm')} · ${e.complaint ?? 'Muayene'}` }))}
            value={encounterId}
            onChange={(v) => form.setFieldValue('encounterId', v)}
            error={form.errors.encounterId}
          />
        )}

        {pet && pending && (
          <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
            {SPECIES[pet.species]} aşı takviminde hekim onayı bekleyen kalemler var; onaylanana kadar yalnız onaylı kalemler veya serbest aşı adı kullanılabilir.
          </Alert>
        )}
        {pet && !isLoading && protocols.length === 0 && !pending && (
          <Text size="xs" c="dimmed">
            Bu tür için aşı takvimi şablonu yok; aşı adını serbest girin.
          </Text>
        )}

        {protocols.length > 0 && (
          <SegmentedControl
            value={mode}
            onChange={(v) => setMode(v as 'template' | 'free')}
            data={[
              { value: 'template', label: 'Takvimden seç' },
              { value: 'free', label: 'Takvim dışı aşı' },
            ]}
          />
        )}

        {mode === 'template' ? (
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <Select
              label="Takvimdeki aşı"
              withAsterisk
              data={protocols.map((p) => ({
                value: p.id,
                label: `${p.name} (${p.doses.length} doz${p.boosterIntervalDays ? `, rapel ${p.boosterIntervalDays} gün` : ''})`,
              }))}
              value={itemId}
              onChange={(v) => {
                form.setFieldValue('templateItemId', v);
                form.setFieldValue('doseId', null);
              }}
              error={form.errors.templateItemId}
            />
            <Select
              label="Doz"
              description={next?.booster ? 'Seri tamamlandı: rapel' : 'Varsayılan: takvimdeki sıradaki doz'}
              withAsterisk
              disabled={!protocol}
              data={(protocol?.doses ?? []).map((d) => ({ value: d.id, label: `${d.doseNumber}. doz` }))}
              value={doseId}
              onChange={(v) => form.setFieldValue('doseId', v)}
              error={form.errors.doseId}
            />
          </SimpleGrid>
        ) : (
          <TextInput
            label="Aşı adı"
            withAsterisk={!form.values.productId}
            description={form.values.productId ? 'Boşsa ürün adı kullanılır' : undefined}
            {...form.getInputProps('vaccineName')}
            error={form.errors.vaccineName ?? form.errors.templateItemId}
          />
        )}

        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <DateTimePicker
            label="Uygulama zamanı"
            description="Boşsa şimdi"
            valueFormat="DD.MM.YYYY HH:mm"
            maxDate={now}
            clearable
            {...form.getInputProps('administeredAt')}
          />
          <TextInput
            label="Parti (lot) no"
            description={selectedProduct?.trackLots ? 'Lot takipli üründe stoktan düşülen lot yazılır' : undefined}
            disabled={!!selectedProduct?.trackLots}
            {...form.getInputProps('lotNo')}
          />
          <TextInput label="Seri / sertifika no" {...form.getInputProps('serialNo')} />
        </SimpleGrid>

        {can('inventory.read') && (
          <Select
            label="Stoktan düşülecek aşı ürünü"
            description={
              selectedProduct?.trackLots
                ? 'Parti takipli ürün: son kullanma tarihi en yakın partiden 1 adet düşülür (FEFO)'
                : 'Seçilirse şube stoğundan 1 adet düşülür; stok yetersizse aşı kaydedilmez'
            }
            placeholder={products.isLoading ? 'Yükleniyor…' : 'Stoktan düşme (isteğe bağlı)'}
            clearable
            searchable
            data={(products.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
            {...form.getInputProps('productId')}
          />
        )}

        <SimpleGrid cols={{ base: 1, sm: 2 }} style={{ alignItems: 'end' }}>
          <DateInput
            label="Sonraki doz tarihi"
            description={mode === 'template' ? 'Boşsa takvimden hesaplanır' : 'Boşsa hatırlatma oluşmaz'}
            valueFormat="DD.MM.YYYY"
            minDate={now}
            clearable
            disabled={mode === 'free' && form.values.noNextDose}
            {...form.getInputProps('nextDueAt')}
          />
          {mode === 'free' && <Checkbox label="Sonraki doz yok" mb={8} {...form.getInputProps('noNextDose', { type: 'checkbox' })} />}
        </SimpleGrid>

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" loading={save.isPending} disabled={!pet || !encounterId}>
            Aşıyı kaydet
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
