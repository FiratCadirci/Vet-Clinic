import { lazy, Suspense, type ReactNode } from 'react';
import { Center, Loader } from '@mantine/core';
import { createBrowserRouter } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { Placeholder } from './pages/Placeholder';

/** Sayfalar ihtiyaç anında yüklenir (ilk açılış daha hızlı). */
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const OwnerDetailPage = lazy(() => import('./pages/patients/OwnerDetailPage').then((m) => ({ default: m.OwnerDetailPage })));
const PatientsPage = lazy(() => import('./pages/patients/PatientsPage').then((m) => ({ default: m.PatientsPage })));

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
  { path: '/signup', element: <Placeholder title="Klinik hesabı oluştur" /> },
  { path: '/accept-invite', element: <Placeholder title="Davet" /> },
  { path: '/davet', element: <Placeholder title="Davet" /> },
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
      { path: '/staff', element: <Placeholder title="Personel ve yetkiler" /> },
      { path: '/settings', element: <Placeholder title="Klinik ayarları" /> },
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
