import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ageFrom, date, dateTime, money, percent, time, toKurus } from './format'

describe('money (kuruş → TL)', () => {
  it.each([
    [123450, '₺1.234,50'],
    [0, '₺0,00'],
    [5, '₺0,05'],
    [-1230, '-₺12,30'],
    [100000000, '₺1.000.000,00'],
  ])('%i → %s', (kurus, text) => {
    expect(money(kurus)).toBe(text)
  })

  it('null/undefined sıfır gösterilir', () => {
    expect(money(null)).toBe('₺0,00')
    expect(money(undefined)).toBe('₺0,00')
  })
})

describe('toKurus (TL girişi → kuruş)', () => {
  it.each([
    [12.5, 1250],
    ['12,5', 1250],
    ['12.50', 1250],
    ['0,29', 29], // 0.29 * 100 = 28.999… → yuvarlanır
    [1.1, 110],
    ['1234', 123400],
    [0, 0],
  ])('%s → %i', (tl, kurus) => {
    expect(toKurus(tl)).toBe(kurus)
  })

  it('money ile gidiş-dönüş tutarlıdır', () => {
    for (const k of [1, 99, 1250, 999999]) expect(toKurus((k / 100).toString())).toBe(k)
  })

  it('sayı olmayan girdi NaN döner (form doğrulaması yakalar)', () => {
    expect(toKurus('abc')).toBeNaN()
  })
})

describe('percent (baz puan → yüzde)', () => {
  it.each([
    [2000, '%20'],
    [1000, '%10'],
    [150, '%1,5'],
    [25, '%0,25'],
    [0, '%0'],
  ])('%i → %s', (bp, text) => {
    expect(percent(bp)).toBe(text)
  })
})

describe('tarih biçimleri', () => {
  it('boş değer tire', () => {
    expect(date(null)).toBe('—')
    expect(dateTime(undefined)).toBe('—')
    expect(time('')).toBe('—')
  })

  it('yerel saatle gün.ay.yıl ve saat:dakika', () => {
    const d = new Date(2026, 10, 9, 14, 5)
    expect(date(d)).toBe('09.11.2026')
    expect(dateTime(d)).toBe('09.11.2026 14:05')
    expect(time(d)).toBe('14:05')
    expect(date('2026-01-02')).toBe('02.01.2026')
  })
})

describe('ageFrom', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 10, 12, 0))
  })
  afterEach(() => vi.useRealTimers())

  it('doğum tarihi yoksa tire', () => {
    expect(ageFrom(null)).toBe('—')
    expect(ageFrom(undefined)).toBe('—')
  })

  it('1 yaşından küçükse ay', () => {
    expect(ageFrom('2026-11-01')).toBe('0 ay')
    expect(ageFrom('2026-05-10')).toBe('6 ay')
    expect(ageFrom('2025-11-11')).toBe('11 ay') // 12. ay bir gün sonra doluyor
  })

  it('12 ay ve üzeri tam yaş (aşağı yuvarlanır)', () => {
    expect(ageFrom('2025-11-10')).toBe('1 yaş')
    expect(ageFrom('2024-05-10')).toBe('2 yaş') // 30 ay
    expect(ageFrom('2016-11-11')).toBe('9 yaş')
  })
})
