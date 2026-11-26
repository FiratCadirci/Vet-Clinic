import { Paper, Text } from '@mantine/core';
import { PageHeader } from '../components/PageHeader';

export function Placeholder({ title }: { title: string }) {
  return (
    <>
      <PageHeader title={title} />
      <Paper p="xl" withBorder>
        <Text c="dimmed">Bu ekran yapım aşamasında.</Text>
      </Paper>
    </>
  );
}
