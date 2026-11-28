import { lazy, Suspense, type ReactNode } from 'react';
import { Center, Loader } from '@mantine/core';
import { createBrowserRouter } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { Placeholder } from './pages/Placeholder';

/** Sayfalar ihtiyaç anında yüklenir (ilk açılış daha hızlı). */
const AppointmentsPage = lazy(() => import('./pages/appointments/AppointmentsPage').then((m) => ({ default: m.AppointmentsPage })));
const AuditPage = lazy(() => import('./pages/audit/AuditPage').then((m) => ({ default: m.AuditPage })));
const AcceptInvitePage = lazy(() => import('./pages/auth/AcceptInvitePage').then((m) => ({ default: m.AcceptInvitePage })));
const SignupPage = lazy(() => import('./pages/auth/SignupPage').then((m) => ({ default: m.SignupPage })));
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const OwnerDetailPage = lazy(() => import('./pages/patients/OwnerDetailPage').then((m) => ({ default: m.OwnerDetailPage })));
const PatientDetailPage = lazy(() => import('./pages/patients/PatientDetailPage').then((m) => ({ default: m.PatientDetailPage })));
const PatientsPage = lazy(() => import('./pages/patients/PatientsPage').then((m) => ({ default: m.PatientsPage })));
const ReportsPage = lazy(() => import('./pages/reports/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const SaleDetailPage = lazy(() => import('./pages/sales/SaleDetailPage').then((m) => ({ default: m.SaleDetailPage })));
const SalesPage = lazy(() => import('./pages/sales/SalesPage').then((m) => ({ default: m.SalesPage })));
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })));
const StaffPage = lazy(() => import('./pages/staff/StaffPage').then((m) => ({ default: m.StaffPage })));

const page = (node: ReactNode) => (
  <Suspense
    fallback={
      <Center py="xl">
        <Loader />
      </Center>
    }
  >
    {node}
  </Suspense>
);

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: page(<SignupPage />) },
  { path: '/accept-invite', element: page(<AcceptInvitePage />) },
  { path: '/davet', element: page(<AcceptInvitePage />) },
  {
    element: <AppLayout area="clinic" />,
    children: [
      { path: '/', element: <Placeholder title="Genel bakış" /> },
      { path: '/appointments', element: page(<AppointmentsPage />) },
      { path: '/patients', element: page(<PatientsPage />) },
      { path: '/patients/:id', element: page(<PatientDetailPage />) },
      { path: '/owners/:id', element: page(<OwnerDetailPage />) },
      { path: '/examinations', element: <Placeholder title="Muayeneler" /> },
      { path: '/examinations/:id', element: <Placeholder title="Muayene" /> },
      { path: '/vaccines', element: <Placeholder title="Aşılar" /> },
      { path: '/inventory', element: page(<InventoryPage />) },
      { path: '/sales', element: page(<SalesPage />) },
      { path: '/sales/:id', element: page(<SaleDetailPage />) },
      { path: '/reports', element: page(<ReportsPage />) },
      { path: '/staff', element: page(<StaffPage />) },
      { path: '/settings', element: page(<SettingsPage />) },
      { path: '/subscription', element: <Placeholder title="Abonelik" /> },
      { path: '/audit', element: page(<AuditPage />) },
    ],
  },
  {
    element: <AppLayout area="platform" />,
    children: [
      { path: '/platform', element: <Placeholder title="Gelir ve komisyon" /> },
      { path: '/platform/tenants', element: <Placeholder title="Klinikler" /> },
      { path: '/platform/plans', element: <Placeholder title="Paketler" /> },
      { path: '/platform/commission-rules', element: <Placeholder title="Komisyon kuralları" /> },
    ],
  },
]);
