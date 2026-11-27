import { lazy, Suspense, type ReactNode } from 'react';
import { Center, Loader } from '@mantine/core';
import { createBrowserRouter } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { Placeholder } from './pages/Placeholder';

/** Sayfalar ihtiyaç anında yüklenir (ilk açılış daha hızlı). */
const AcceptInvitePage = lazy(() => import('./pages/auth/AcceptInvitePage').then((m) => ({ default: m.AcceptInvitePage })));
const SignupPage = lazy(() => import('./pages/auth/SignupPage').then((m) => ({ default: m.SignupPage })));
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const OwnerDetailPage = lazy(() => import('./pages/patients/OwnerDetailPage').then((m) => ({ default: m.OwnerDetailPage })));
const PatientsPage = lazy(() => import('./pages/patients/PatientsPage').then((m) => ({ default: m.PatientsPage })));
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
      { path: '/appointments', element: <Placeholder title="Randevular" /> },
      { path: '/patients', element: page(<PatientsPage />) },
      { path: '/patients/:id', element: <Placeholder title="Hasta" /> },
      { path: '/owners/:id', element: page(<OwnerDetailPage />) },
      { path: '/examinations', element: <Placeholder title="Muayeneler" /> },
      { path: '/examinations/:id', element: <Placeholder title="Muayene" /> },
      { path: '/vaccines', element: <Placeholder title="Aşılar" /> },
      { path: '/inventory', element: page(<InventoryPage />) },
      { path: '/sales', element: <Placeholder title="Satış ve fatura" /> },
      { path: '/sales/:id', element: <Placeholder title="Satış" /> },
      { path: '/reports', element: <Placeholder title="Raporlar" /> },
      { path: '/staff', element: page(<StaffPage />) },
      { path: '/settings', element: page(<SettingsPage />) },
      { path: '/subscription', element: <Placeholder title="Abonelik" /> },
      { path: '/audit', element: <Placeholder title="Denetim izi" /> },
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
