package org.codeflow.veterineryproject.commission.policy;

/**
 * STRATEGY: fatura kesiminde platform komisyonunun nasıl uygulanacağı (FG-K4).
 *
 * <p>Komisyon tutarı ve uygunluğu (mama/ilaç + aktif platform kuralı) tek kaynak olarak DB tetikleyicisinde
 * ({@code vet.post_invoice_inventory_and_commission}, V28) hesaplanır; stok düşümüyle aynı UPDATE'te çalıştığı
 * için kesim atomiktir. Strateji, o tetikleyiciye hangi yöntemin uygulanacağını faturaya yazılan {@link #code()}
 * ile bildirir; kod faturada kalır, hangi faturaya neden komisyon yazılmadığı sonradan izlenebilir.
 * Yeni yöntem = yeni strateji sınıfı + kod + tetikleyicide dal; fatura akışı (BillingService) değişmez.
 */
public interface CommissionPolicy {

    /** invoices.commission_policy değeri. */
    String code();

    /** Bu yöntemle kesilen faturada komisyon kaydı oluşur mu. */
    boolean chargesCommission();
}
