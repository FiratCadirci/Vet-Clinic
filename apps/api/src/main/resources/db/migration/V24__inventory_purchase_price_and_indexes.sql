-- Stok modülü (FG-F1..F5): ürün kartında alış fiyatı ve şube bazlı hareket sorguları için indeks.
-- Stok bakiyesi, negatif stok engeli ve hareket immutability kuralları V1'deki tetikleyicilerde kalır.

-- FG-F1: ürün kartı alış fiyatı (KDV hariç, TL). Parti bazlı gerçek maliyet inventory_lots.unit_cost'ta tutulur;
-- bu alan yeni girişlerde varsayılan birim maliyettir.
ALTER TABLE vet.products
    ADD COLUMN purchase_price numeric(14,2) NOT NULL DEFAULT 0 CHECK (purchase_price >= 0);

-- Hareket geçmişi ve şube stok sorguları lokasyon + tarih ile filtrelenir (FOG-8).
CREATE INDEX ix_inventory_tx_location_occurred
    ON vet.inventory_transactions (organization_id, location_id, occurred_at DESC);

CREATE INDEX ix_inventory_lines_transaction
    ON vet.inventory_transaction_lines (organization_id, transaction_id);
