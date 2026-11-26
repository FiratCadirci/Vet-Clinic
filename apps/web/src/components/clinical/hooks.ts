import { useQuery, type QueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { getBookingOptions } from './patientApi';

export interface VetOption {
  id: string;
  fullName: string;
}

/** Randevu formu seçenekleri: şubenin aktif hizmetleri (süreleriyle) ve hekimleri. */
export function useBookingOptions(branchId: string | null) {
  const { can } = useAuth();
  return useQuery({
    queryKey: ['booking-options', branchId],
    queryFn: () => getBookingOptions(branchId!),
    enabled: !!branchId && can('appointments.manage'),
    staleTime: 60_000,
  });
}

/** Şubede rolü veya çalışma saati olan aktif veteriner hekimler (`GET /v1/appointments/booking-options`). */
export function useVets(branchId: string | null) {
  const options = useBookingOptions(branchId);
  const vets = useMemo<VetOption[]>(() => options.data?.veterinarians ?? [], [options.data]);
  return { vets, isLoading: options.isLoading };
}

/** Hasta ile ilgili verinin değiştiği her mutasyondan sonra çağrılır. */
export function invalidatePatientData(qc: QueryClient, petId?: string) {
  qc.invalidateQueries({ queryKey: ['pets'] });
  qc.invalidateQueries({ queryKey: ['owners'] });
  qc.invalidateQueries({ queryKey: ['owner'] });
  if (petId) {
    qc.invalidateQueries({ queryKey: ['pet', petId] });
    qc.invalidateQueries({ queryKey: ['pet-history', petId] });
    qc.invalidateQueries({ queryKey: ['vaccine-schedule', petId] });
  }
}

export function invalidateAppointments(qc: QueryClient, petId?: string) {
  qc.invalidateQueries({ queryKey: ['appointments'] });
  qc.invalidateQueries({ queryKey: ['reports'] });
  if (petId) qc.invalidateQueries({ queryKey: ['pet-history', petId] });
}

/** Ek dosyayı yetki başlığıyla indirir (dosya adı korunur). */
export async function downloadFile(path: string, fileName: string) {
  // api istemcisi yetki, şube başlığı ve token yenilemeyi yönetir; JSON olmayan yanıt Blob döner.
  const url = URL.createObjectURL(await api.get<Blob>(path));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
