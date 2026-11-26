import {
  IconBuildingHospital,
  IconCalendarEvent,
  IconChartBar,
  IconCreditCard,
  IconHistory,
  IconLayoutDashboard,
  IconPackage,
  IconPaw,
  IconPercentage,
  IconReceipt,
  IconSettings,
  IconStethoscope,
  IconUsers,
  IconVaccine,
  type Icon,
} from '@tabler/icons-react';

export interface NavItem {
  to: string;
  label: string;
  icon: Icon;
  /** Menüde görünmesi için gereken yetki (yoksa herkes görür). */
  permission?: string;
}

export const CLINIC_NAV: NavItem[] = [
  { to: '/', label: 'Genel bakış', icon: IconLayoutDashboard },
  { to: '/appointments', label: 'Randevular', icon: IconCalendarEvent, permission: 'appointments.manage' },
  { to: '/patients', label: 'Hastalar', icon: IconPaw, permission: 'clients.read' },
  { to: '/examinations', label: 'Muayeneler', icon: IconStethoscope, permission: 'clinical.read' },
  { to: '/vaccines', label: 'Aşılar', icon: IconVaccine, permission: 'clinical.read' },
  { to: '/inventory', label: 'Stok', icon: IconPackage, permission: 'inventory.read' },
  { to: '/sales', label: 'Satış ve fatura', icon: IconReceipt, permission: 'billing.manage' },
  { to: '/reports', label: 'Raporlar', icon: IconChartBar, permission: 'reports.read' },
  { to: '/staff', label: 'Personel ve yetkiler', icon: IconUsers, permission: 'users.manage' },
  { to: '/settings', label: 'Klinik ayarları', icon: IconSettings, permission: 'services.manage' },
  { to: '/subscription', label: 'Abonelik', icon: IconCreditCard, permission: 'subscriptions.manage' },
  { to: '/audit', label: 'Denetim izi', icon: IconHistory, permission: 'audit.read' },
];

export const PLATFORM_NAV: NavItem[] = [
  { to: '/platform', label: 'Gelir ve komisyon', icon: IconChartBar },
  { to: '/platform/tenants', label: 'Klinikler', icon: IconBuildingHospital },
  { to: '/platform/plans', label: 'Paketler', icon: IconCreditCard },
  { to: '/platform/commission-rules', label: 'Komisyon kuralları', icon: IconPercentage },
];
