/** Yönetim ekranlarına özgü Türkçe etiketler ve rozet renkleri. */

export const ROLE_LABEL: Record<string, string> = {
  clinic_admin: 'Klinik yöneticisi',
  branch_manager: 'Şube yöneticisi',
  veterinarian: 'Veteriner hekim',
  receptionist: 'Resepsiyon',
};

export const ROLE_COLOR: Record<string, string> = {
  clinic_admin: 'grape',
  branch_manager: 'indigo',
  veterinarian: 'teal',
  receptionist: 'cyan',
};

/** Stok hareket türleri: anahtarlar vet.inventory_transactions.transaction_type değerleridir (stok ve rapor ekranları). */
export const MOVEMENT_TYPE: Record<string, { label: string; color: string }> = {
  RECEIPT: { label: 'Alım', color: 'teal' },
  DISPENSE: { label: 'Satış / kullanım', color: 'blue' },
  ADJUSTMENT: { label: 'Sayım düzeltme', color: 'yellow' },
  TRANSFER_OUT: { label: 'Transfer (çıkış)', color: 'grape' },
  TRANSFER_IN: { label: 'Transfer (giriş)', color: 'grape' },
  CUSTOMER_RETURN: { label: 'Müşteri iadesi', color: 'lime' },
  SUPPLIER_RETURN: { label: 'Tedarikçiye iade', color: 'orange' },
  WASTE: { label: 'Fire / imha', color: 'red' },
  OPENING_BALANCE: { label: 'Açılış bakiyesi', color: 'gray' },
};

/** vet.products.product_type değerleri (rapor ekranı). */
export const PRODUCT_TYPE: Record<string, string> = {
  MEDICATION: 'İlaç',
  VACCINE: 'Aşı',
  FOOD: 'Mama',
  CONSUMABLE: 'Sarf malzeme',
  SUPPLEMENT: 'Takviye',
  EQUIPMENT: 'Ekipman',
  OTHER: 'Diğer',
};

export const SALE_STATUS: Record<string, { label: string; color: string }> = {
  draft: { label: 'Taslak', color: 'gray' },
  issued: { label: 'Kesildi', color: 'blue' },
  cancelled: { label: 'İptal', color: 'red' },
};

export const PAYMENT_STATUS: Record<string, { label: string; color: string }> = {
  unpaid: { label: 'Ödenmedi', color: 'red' },
  partial: { label: 'Kısmi ödendi', color: 'yellow' },
  paid: { label: 'Ödendi', color: 'green' },
};

export const PAYMENT_METHOD: Record<string, string> = {
  cash: 'Nakit',
  card: 'Kredi kartı',
  transfer: 'Havale/EFT',
  other: 'Diğer',
};

export const SUBSCRIPTION_STATUS: Record<string, { label: string; color: string }> = {
  trial: { label: 'Deneme', color: 'cyan' },
  active: { label: 'Aktif', color: 'green' },
  past_due: { label: 'Ödeme gecikmiş', color: 'orange' },
  cancelled: { label: 'İptal edildi', color: 'red' },
};

export const PERIOD: Record<string, string> = { monthly: 'Aylık', yearly: 'Yıllık' };

export const WEEKDAYS: { value: number; label: string }[] = [
  { value: 1, label: 'Pazartesi' },
  { value: 2, label: 'Salı' },
  { value: 3, label: 'Çarşamba' },
  { value: 4, label: 'Perşembe' },
  { value: 5, label: 'Cuma' },
  { value: 6, label: 'Cumartesi' },
  { value: 7, label: 'Pazar' },
];

/**
 * Denetim izindeki kayıt türleri (vet.audit_logs.entity_type, büyük harfe çevrilerek eşlenir). Filtre de büyük/küçük
 * harf duyarsızdır.
 */
export const AUDIT_ENTITY: Record<string, string> = {
  ANIMAL: 'Hasta',
  CLIENT: 'Hayvan sahibi',
  ENCOUNTER: 'Muayene',
  ENCOUNTER_TREATMENT: 'Muayene tedavisi',
  CLINICAL_DOCUMENT: 'Muayene eki',
  APPOINTMENT: 'Randevu',
  VACCINE_RECORD: 'Aşı uygulaması',
  VACCINE_PROTOCOL: 'Aşı şablonu',
  PRODUCT: 'Ürün',
  INVENTORY_TRANSACTION: 'Stok hareketi',
  INVOICE: 'Fatura',
  PAYMENT: 'Tahsilat',
  APP_USER: 'Personel',
  STAFF_INVITATION: 'Personel daveti',
  CLINIC: 'Şube',
  ORGANIZATION: 'Klinik profili',
  SERVICE_CATALOG: 'Hizmet',
  VETERINARIAN: 'Çalışma saatleri',
};

/** Denetim izindeki işlem kodları (backend AuditRecorder ve görüntüleme denetimi). */
export const AUDIT_ACTION: Record<string, { label: string; color: string }> = {
  ANIMAL_VIEW: { label: 'Hasta görüntüleme', color: 'gray' },
  ANIMAL_HISTORY_VIEW: { label: 'Hasta geçmişi görüntüleme', color: 'gray' },
  CLIENT_VIEW: { label: 'Sahip görüntüleme', color: 'gray' },
  ENCOUNTER_VIEW: { label: 'Muayene görüntüleme', color: 'gray' },
  CLINICAL_DOCUMENT_DOWNLOAD: { label: 'Ek indirme', color: 'grape' },
  APPOINTMENT_CREATE: { label: 'Randevu oluşturma', color: 'teal' },
  APPOINTMENT_RESCHEDULE: { label: 'Randevu değişikliği', color: 'blue' },
  APPOINTMENT_CONFIRMED: { label: 'Randevu onayı', color: 'blue' },
  APPOINTMENT_CHECKED_IN: { label: 'Randevu kabulü', color: 'blue' },
  APPOINTMENT_IN_PROGRESS: { label: 'Randevu muayenede', color: 'blue' },
  APPOINTMENT_COMPLETED: { label: 'Randevu tamamlandı', color: 'blue' },
  APPOINTMENT_NO_SHOW: { label: 'Randevu: gelmedi', color: 'orange' },
  APPOINTMENT_CANCELLED: { label: 'Randevu iptali', color: 'red' },
  ENCOUNTER_CLOSE: { label: 'Muayene kapatma', color: 'blue' },
  ENCOUNTER_DELETE: { label: 'Muayene silme', color: 'red' },
  TREATMENT_DELETE: { label: 'Tedavi silme', color: 'red' },
  ATTACHMENT_DELETE: { label: 'Ek silme', color: 'red' },
  VACCINE_RECORD_CREATE: { label: 'Aşı kaydı', color: 'teal' },
  VACCINE_RECORD_DELETE: { label: 'Aşı kaydı silme', color: 'red' },
  VACCINE_PROTOCOL_APPROVE: { label: 'Aşı şablonu onayı', color: 'blue' },
  INVOICE_CREATE: { label: 'Fatura oluşturma', color: 'teal' },
  INVOICE_ISSUE: { label: 'Fatura kesimi', color: 'blue' },
  INVOICE_VOID: { label: 'Fatura iptali', color: 'red' },
  PAYMENT_RECEIVE: { label: 'Tahsilat', color: 'teal' },
  PRODUCT_CREATE: { label: 'Ürün oluşturma', color: 'teal' },
  PRODUCT_UPDATE: { label: 'Ürün güncelleme', color: 'blue' },
  PRODUCT_DELETE: { label: 'Ürün silme', color: 'red' },
  STOCK_RECEIPT: { label: 'Stok girişi', color: 'teal' },
  STOCK_DISPENSE: { label: 'Stok çıkışı', color: 'blue' },
  STOCK_ADJUSTMENT: { label: 'Sayım düzeltme', color: 'yellow' },
  STOCK_TRANSFER: { label: 'Stok transferi', color: 'grape' },
  SERVICE_CREATE: { label: 'Hizmet oluşturma', color: 'teal' },
  SERVICE_UPDATE: { label: 'Hizmet güncelleme', color: 'blue' },
  SERVICE_DELETE: { label: 'Hizmet silme', color: 'red' },
  BRANCH_CREATE: { label: 'Şube oluşturma', color: 'teal' },
  BRANCH_UPDATE: { label: 'Şube güncelleme', color: 'blue' },
  BRANCH_DEACTIVATE: { label: 'Şube kapatma', color: 'red' },
  BRANCH_ACTIVATE: { label: 'Şube açma', color: 'teal' },
  ORGANIZATION_UPDATE: { label: 'Klinik profili güncelleme', color: 'blue' },
  STAFF_INVITATION_CREATE: { label: 'Personel daveti', color: 'teal' },
  STAFF_INVITATION_REVOKE: { label: 'Davet iptali', color: 'red' },
  USER_ROLES_REPLACE: { label: 'Rol atama', color: 'blue' },
  WORKING_HOURS_REPLACE: { label: 'Çalışma saati değişikliği', color: 'blue' },
};

export const toOptions = (map: Record<string, string | { label: string }>) =>
  Object.entries(map).map(([value, v]) => ({ value, label: typeof v === 'string' ? v : v.label }));

/**
 * Platform komisyonunun uygulandığı ürün kategorileri (backend: `billing/commission/commission-policy.ts`
 * `COMMISSION_CATEGORIES`). Komisyona tabi olmak klinik ayarı değil platform kuralıdır: bu kategorideki ürün
 * satışlarına, platformun aktif komisyon kuralı varsa komisyon uygulanır (deneme süresindeki klinikler hariç).
 */
export const COMMISSION_CATEGORIES = ['food', 'drug'] as const;
export const isCommissionCategory = (c: string) => (COMMISSION_CATEGORIES as readonly string[]).includes(c);
export const COMMISSION_HINT =
  'Komisyonu platform belirler: mama ve ilaç satışlarına platformun aktif komisyon kuralı uygulanır (deneme süresinde alınmaz). Klinik bunu değiştiremez.';
