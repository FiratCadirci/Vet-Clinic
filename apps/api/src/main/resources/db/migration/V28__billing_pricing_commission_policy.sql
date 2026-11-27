-- Satış ve faturalama (FG-G1..G5, FG-K4).
-- 1) Satır fiyatlama kuralı web önizlemesi ve Java LinePricing ile birebir aynı olacak şekilde netleştirilir:
--    brüt = round(miktar × birim, 2); indirim = discount_amount (TL, brütü aşamaz); net = brüt − indirim;
--    KDV = round(net × oran / 100, 2) satır bazında; satır toplamı = net + KDV.
--    Eski ifade KDV'yi ve toplamı yuvarlanmamış net üzerinden hesapladığı için satır toplamı net + KDV'den
--    1 kuruş sapabiliyordu; ekran ise indirimi tutar olarak (TL) alıyor.
-- 2) Komisyon politikası (Strategy, Java'da seçilir) kesimde faturaya yazılır; tetikleyici bu koda göre
--    komisyon kaydı üretir ya da üretmez (deneme aboneliği). Uygunluk: ürün tipi mama/ilaç + aktif platform kuralı.
-- 3) Fatura kesildi bildirimi için müşteri iletişim bilgisi (SECURITY DEFINER, organizasyon kapsamlı).

-- ---------------------------------------------------------------------------
-- 1. invoice_lines: tutar olarak indirim + fiyatlama ifadeleri
-- ---------------------------------------------------------------------------
ALTER TABLE vet.invoice_lines
    ADD COLUMN discount_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0);

-- Mevcut oranlı indirimler tutara çevrilir (taslak koruması bu tek seferlik dönüşümde devre dışı).
ALTER TABLE vet.invoice_lines DISABLE TRIGGER trg_invoice_lines_draft_only;
UPDATE vet.invoice_lines
SET discount_amount = round(round(quantity * unit_price, 2) * discount_rate / 100, 2)
WHERE discount_rate > 0;
ALTER TABLE vet.invoice_lines ENABLE TRIGGER trg_invoice_lines_draft_only;

ALTER TABLE vet.invoice_lines
    ALTER COLUMN net_amount SET EXPRESSION AS (round(quantity * unit_price, 2) - discount_amount),
    ALTER COLUMN tax_amount SET EXPRESSION AS (round((round(quantity * unit_price, 2) - discount_amount) * tax_rate / 100, 2)),
    ALTER COLUMN line_total SET EXPRESSION AS (
        (round(quantity * unit_price, 2) - discount_amount)
        + round((round(quantity * unit_price, 2) - discount_amount) * tax_rate / 100, 2));

ALTER TABLE vet.invoice_lines
    ADD CONSTRAINT ck_invoice_lines_discount_le_gross CHECK (discount_amount <= round(quantity * unit_price, 2));

-- ---------------------------------------------------------------------------
-- 2. invoices: kesimde uygulanan komisyon politikası (izlenebilirlik)
-- ---------------------------------------------------------------------------
ALTER TABLE vet.invoices
    ADD COLUMN commission_policy varchar(30)
        CHECK (commission_policy IS NULL OR commission_policy IN ('PLATFORM_RULE', 'NO_COMMISSION'));

COMMENT ON COLUMN vet.invoices.commission_policy IS
    'Kesimde seçilen komisyon stratejisi: PLATFORM_RULE (platform kuralı) veya NO_COMMISSION (deneme aboneliği). NULL = PLATFORM_RULE.';

-- V1 fonksiyonunun aynısı; yalnız komisyon bölümü politika koduna ve mama/ilaç uygunluğuna bağlandı.
CREATE OR REPLACE FUNCTION vet.post_invoice_inventory_and_commission()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    sale_tx_id uuid;
    sale_tx_no varchar(60);
    has_stock_items boolean;
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('ISSUED','VOID') THEN
            RAISE EXCEPTION 'DRAFT faturadan yalnızca ISSUED veya VOID durumuna geçilebilir.';
        END IF;
    ELSIF OLD.status = 'ISSUED' THEN
        IF NEW.status NOT IN ('PARTIALLY_PAID','PAID','OVERDUE') THEN
            RAISE EXCEPTION 'Düzenlenmiş fatura geriye alınamaz veya doğrudan VOID yapılamaz; iade/alacak dekontu kullanın.';
        END IF;
    ELSIF OLD.status = 'PARTIALLY_PAID' THEN
        IF NEW.status NOT IN ('PAID','OVERDUE') THEN
            RAISE EXCEPTION 'Kısmen ödenmiş faturanın geçişi PAID veya OVERDUE olmalıdır.';
        END IF;
    ELSIF OLD.status = 'OVERDUE' THEN
        IF NEW.status NOT IN ('PARTIALLY_PAID','PAID') THEN
            RAISE EXCEPTION 'Gecikmiş fatura yalnızca ödeme sonrası duruma geçebilir.';
        END IF;
    ELSE
        RAISE EXCEPTION 'PAID veya VOID faturanın durumu değiştirilemez.';
    END IF;

    IF NOT (OLD.status = 'DRAFT' AND NEW.status = 'ISSUED') THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM vet.invoice_lines il
        WHERE il.organization_id = NEW.organization_id AND il.invoice_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Satırı bulunmayan fatura düzenlenemez.';
    END IF;

    NEW.issued_at := COALESCE(NEW.issued_at, now());

    -- Proforma ve alacak dekontu satış değildir; stok/iade hareketi ayrı ve açık bir işlem olmalıdır.
    IF NEW.invoice_type <> 'SALES' THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM vet.invoice_lines il
        JOIN vet.products p
          ON p.organization_id = il.organization_id AND p.id = il.product_id
        WHERE il.organization_id = NEW.organization_id
          AND il.invoice_id = NEW.id
          AND il.line_type = 'PRODUCT'
          AND p.is_stock_item
    ) INTO has_stock_items;

    IF has_stock_items AND NEW.inventory_location_id IS NULL THEN
        RAISE EXCEPTION 'Stok kalemi içeren faturanın inventory_location_id alanı belirtilmelidir.';
    END IF;

    IF has_stock_items THEN
        IF NOT EXISTS (
            SELECT 1 FROM vet.inventory_locations loc
            WHERE loc.organization_id = NEW.organization_id
              AND loc.id = NEW.inventory_location_id
              AND loc.clinic_id = NEW.clinic_id
              AND loc.is_active
        ) THEN
            RAISE EXCEPTION 'Faturadaki stok lokasyonu aktif olmalı ve faturanın şubesine ait olmalıdır.';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM vet.invoice_lines il
            JOIN vet.products p
              ON p.organization_id = il.organization_id AND p.id = il.product_id
            LEFT JOIN vet.inventory_lots lot
              ON lot.organization_id = il.organization_id
             AND lot.product_id = il.product_id
             AND lot.id = il.lot_id
            WHERE il.organization_id = NEW.organization_id
              AND il.invoice_id = NEW.id
              AND il.line_type = 'PRODUCT'
              AND p.is_stock_item
              AND ((p.tracks_lot AND il.lot_id IS NULL)
                   OR (il.lot_id IS NOT NULL AND (lot.id IS NULL OR (lot.expires_at IS NOT NULL AND lot.expires_at < CURRENT_DATE))))
        ) THEN
            RAISE EXCEPTION 'Lot takibi zorunlu olabilir veya lot geçersiz/süresi geçmiş olabilir; ürün lot bilgilerini kontrol edin.';
        END IF;

        sale_tx_no := 'SALE-' || replace(NEW.id::text, '-', '');
        INSERT INTO vet.inventory_transactions (
            organization_id, location_id, transaction_no, transaction_type, status,
            occurred_at, reference_type, reference_id, notes, created_by
        ) VALUES (
            NEW.organization_id, NEW.inventory_location_id, sale_tx_no, 'DISPENSE', 'DRAFT',
            COALESCE(NEW.issued_at, now()), 'INVOICE', NEW.id,
            'Fatura düzenleme sırasında otomatik oluşturulan stok çıkışı.', NEW.created_by
        ) RETURNING id INTO sale_tx_id;

        INSERT INTO vet.inventory_transaction_lines (
            organization_id, transaction_id, product_id, lot_id,
            direction, quantity, unit_cost, line_note
        )
        SELECT il.organization_id, sale_tx_id, il.product_id, il.lot_id,
               'OUT', il.quantity,
               COALESCE(lot.unit_cost, 0),
               'Fatura ' || NEW.invoice_no
        FROM vet.invoice_lines il
        JOIN vet.products p
          ON p.organization_id = il.organization_id AND p.id = il.product_id
        LEFT JOIN vet.inventory_lots lot
          ON lot.organization_id = il.organization_id
         AND lot.product_id = il.product_id
         AND lot.id = il.lot_id
        WHERE il.organization_id = NEW.organization_id
          AND il.invoice_id = NEW.id
          AND il.line_type = 'PRODUCT'
          AND p.is_stock_item;

        UPDATE vet.inventory_transactions
        SET status = 'POSTED', posted_by = NEW.created_by, posted_at = now()
        WHERE organization_id = NEW.organization_id AND id = sale_tx_id;
    END IF;

    -- Deneme aboneliğinde (NO_COMMISSION) komisyon kaydı yazılmaz. Politika kodu yoksa platform kuralı uygulanır:
    -- klinik kodu yazmadan komisyondan kaçamaz.
    IF COALESCE(NEW.commission_policy, 'PLATFORM_RULE') = 'NO_COMMISSION' THEN
        RETURN NEW;
    END IF;

    -- Komisyon, satış anındaki fatura satırı net tutarı üzerinden hesaplanır (KDV hariç).
    -- Uygunluk platform tarafından belirlenir: ürün tipi mama (FOOD) veya ilaç (MEDICATION) VE o tipte aktif,
    -- oranı sıfırdan büyük platform kuralı. Ürün kartında komisyonu kapatan bir alan yoktur.
    INSERT INTO vet.commission_records (
        organization_id, clinic_id, invoice_id, invoice_line_id, commission_rule_id,
        eligible_amount, rate_percent, commission_amount, status
    )
    SELECT NEW.organization_id, NEW.clinic_id, NEW.id, il.id, cr.id,
           il.net_amount, cr.rate_percent,
           round(il.net_amount * cr.rate_percent / 100, 2), 'PENDING'
    FROM vet.invoice_lines il
    JOIN vet.products p
      ON p.organization_id = il.organization_id AND p.id = il.product_id
    JOIN LATERAL (
        SELECT rule.id, rule.rate_percent
        FROM vet.commission_rules rule
        WHERE rule.product_type = p.product_type
          AND rule.is_active
          AND rule.valid_from <= NEW.issued_at
          AND (rule.valid_to IS NULL OR rule.valid_to > NEW.issued_at)
        ORDER BY rule.valid_from DESC, rule.created_at DESC
        LIMIT 1
    ) cr ON true
    WHERE il.organization_id = NEW.organization_id
      AND il.invoice_id = NEW.id
      AND il.line_type = 'PRODUCT'
      AND p.product_type IN ('FOOD', 'MEDICATION')
      AND cr.rate_percent > 0
    ON CONFLICT (organization_id, invoice_line_id, commission_rule_id) DO NOTHING;

    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 3. Fatura kesildi bildirimi: faturanın müşterisi (mobil hesabı ve e-postası), numara, tutar, şube.
--    Kesen personelin şube görünürlüğünden bağımsızdır; yalnız app.organization_id organizasyonunu okur.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.notification_invoice(p_invoice_id uuid)
RETURNS TABLE (
    client_id     uuid,
    client_name   text,
    owner_user_id uuid,
    owner_email   text,
    animal_id     uuid,
    invoice_no    text,
    grand_total   numeric,
    currency      text,
    clinic_name   text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = vet, pg_temp
AS $$
    SELECT c.id,
           c.display_name::text,
           c.app_user_id,
           COALESCE(
               (SELECT cc.contact_value FROM vet.client_contacts cc
                 WHERE cc.organization_id = c.organization_id AND cc.client_id = c.id AND cc.contact_type = 'EMAIL'
                 ORDER BY cc.is_primary DESC, cc.created_at LIMIT 1),
               (SELECT u.email FROM vet.app_users u
                 WHERE u.organization_id = c.organization_id AND u.id = c.app_user_id AND u.is_active)
           )::text,
           i.animal_id,
           i.invoice_no::text,
           COALESCE((SELECT sum(l.line_total) FROM vet.invoice_lines l
                      WHERE l.organization_id = i.organization_id AND l.invoice_id = i.id), 0),
           i.currency::text,
           cl.name::text
      FROM vet.invoices i
      JOIN vet.clients c ON c.organization_id = i.organization_id AND c.id = i.client_id
      JOIN vet.clinics cl ON cl.organization_id = i.organization_id AND cl.id = i.clinic_id
     WHERE i.organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
       AND i.id = p_invoice_id
       AND i.status <> 'DRAFT'
       AND c.is_active;
$$;

REVOKE ALL ON FUNCTION vet.notification_invoice(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION vet.notification_invoice(uuid) TO vet_app;
