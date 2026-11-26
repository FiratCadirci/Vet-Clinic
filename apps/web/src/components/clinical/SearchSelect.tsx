import { Loader, Select, type SelectProps } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { listOwners, listPets } from './patientApi';
import { SPECIES } from '../../lib/format';
import type { OwnerListItem, PetListItem } from './types';

export interface Option<T> {
  value: string;
  label: string;
  item?: T;
}

type BaseProps = Omit<SelectProps, 'data' | 'value' | 'onChange' | 'searchable' | 'searchValue' | 'onSearchChange' | 'filter'>;

interface SearchSelectProps<T> extends BaseProps {
  /** Sunucu tarafı arama (boş metin: ilk kayıtlar). */
  search: (term: string) => Promise<T[]>;
  queryKey: string;
  toOption: (item: T) => Option<T>;
  value: string | null;
  onChange: (value: string | null, item?: T) => void;
  /** Önceden seçili kayıt (ör. hasta sayfasından açılınca). */
  initialOption?: Option<T>;
}

/** Sunucu tarafında arama yapan seçim kutusu. */
function SearchSelect<T>({ search: fetchItems, queryKey, toOption, value, onChange, initialOption, ...rest }: SearchSelectProps<T>) {
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Option<T> | undefined>(initialOption);
  const term = search === selected?.label ? '' : search.trim();
  const [debounced] = useDebouncedValue(term, 300);

  const { data = [], isFetching } = useQuery({
    queryKey: [queryKey, { search: debounced, limit: 20 }],
    queryFn: () => fetchItems(debounced),
  });

  const options = useMemo(() => {
    const list = data.map(toOption);
    const current = selected && selected.value === value ? selected : initialOption?.value === value ? initialOption : undefined;
    if (current && !list.some((o) => o.value === current.value)) list.unshift(current);
    return list;
  }, [data, toOption, selected, initialOption, value]);

  return (
    <Select
      searchable
      clearable
      nothingFoundMessage={isFetching ? 'Aranıyor…' : 'Kayıt bulunamadı'}
      rightSection={isFetching ? <Loader size={14} /> : undefined}
      {...rest}
      data={options.map((o) => ({ value: o.value, label: o.label }))}
      filter={({ options: opts }) => opts}
      value={value}
      searchValue={search}
      onSearchChange={setSearch}
      onChange={(v) => {
        const opt = options.find((o) => o.value === v);
        setSelected(opt);
        onChange(v, opt?.item);
      }}
    />
  );
}

const petOption = (p: PetListItem): Option<PetListItem> => ({
  value: p.id,
  label: `${p.name} (${SPECIES[p.species] ?? p.species}) — ${p.ownerName}`,
  item: p,
});

const ownerOption = (o: OwnerListItem): Option<OwnerListItem> => ({
  value: o.id,
  label: `${o.fullName} — ${o.phone}`,
  item: o,
});

type EntityProps<T> = BaseProps & {
  value: string | null;
  onChange: (value: string | null, item?: T) => void;
  initialOption?: Option<T>;
};

export function PetSelect(props: EntityProps<PetListItem>) {
  return (
    <SearchSelect<PetListItem>
      label="Hasta"
      placeholder="Ad, mikroçip, sahip adı veya telefon"
      {...props}
      search={(term) => listPets(term || undefined, 0, 20)}
      queryKey="pets"
      toOption={petOption}
    />
  );
}

export function OwnerSelect(props: EntityProps<OwnerListItem>) {
  return (
    <SearchSelect<OwnerListItem>
      label="Hayvan sahibi"
      placeholder="Ad, telefon veya e-posta"
      {...props}
      search={(term) => listOwners(term || undefined, 0, 20)}
      queryKey="owners"
      toOption={ownerOption}
    />
  );
}
