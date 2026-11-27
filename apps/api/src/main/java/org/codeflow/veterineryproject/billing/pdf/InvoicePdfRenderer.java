package org.codeflow.veterineryproject.billing.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceResponse;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.LineResponse;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository.PdfParty;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Fatura / Fiş taslağı" PDF'i (FG-G5). Türkçe karakterler (ğ, ş, ı, İ...) için DejaVu Sans gömülür
 * (resources/fonts, Bitstream Vera/DejaVu lisansı); PDF'in yerleşik Helvetica'sı bunları basamaz.
 * Veritabanı bilmez: fatura yanıtı ve başlık bilgileri servisten gelir.
 */
@Component
public class InvoicePdfRenderer {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", TR);
    private static final Color RULE = new Color(0x99, 0x99, 0x99);
    private static final Color MUTED = new Color(0x55, 0x55, 0x55);
    private static final Map<String, String> STATUS = Map.of(
            "DRAFT", "Taslak", "ISSUED", "Kesildi · Ödenmedi", "PARTIALLY_PAID", "Kesildi · Kısmi ödendi",
            "PAID", "Kesildi · Ödendi", "OVERDUE", "Kesildi · Gecikmiş", "VOID", "İptal");
    private static final float[] COLUMNS = {36, 8, 13, 11, 7, 11, 14};

    private final BaseFont regular;
    private final BaseFont bold;

    public InvoicePdfRenderer() {
        this.regular = font("fonts/DejaVuSans.ttf");
        this.bold = font("fonts/DejaVuSans-Bold.ttf");
    }

    public byte[] render(InvoiceResponse invoice, PdfParty party) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            InvoiceSummary s = invoice.invoice();
            doc.addTitle("Fatura " + number(s));
            doc.addAuthor(party.organizationName());
            doc.open();
            doc.add(header(s, party));
            doc.add(customer(s, party));
            doc.add(lines(invoice.lines()));
            doc.add(totals(invoice));
            Paragraph note = new Paragraph("Bu belge bilgi amaçlı bir taslaktır; resmi e-Fatura / e-Arşiv belgesi "
                    + "yerine geçmez. Birim fiyatlar KDV hariçtir.", new Font(regular, 7.5f, Font.NORMAL, MUTED));
            note.setSpacingBefore(24);
            doc.add(note);
            doc.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF oluşturulamadı.", ex);
        }
        return out.toByteArray();
    }

    private PdfPTable header(InvoiceSummary s, PdfParty p) {
        PdfPTable t = table(new float[]{60, 40});
        PdfPCell left = cell(Element.ALIGN_LEFT);
        left.addElement(new Paragraph(orDefault(p.legalName(), p.organizationName()), new Font(bold, 16)));
        List<String> info = new ArrayList<>();
        info.add(p.clinicName());
        addIfPresent(info, p.clinicAddress());
        addIfPresent(info, joinPresent(" · ", p.clinicPhone(), p.clinicEmail()));
        if (p.taxNumber() != null) {
            info.add("VKN: " + p.taxNumber() + (p.taxOffice() == null ? "" : " (" + p.taxOffice() + ")"));
        }
        info.forEach(line -> left.addElement(new Paragraph(line, small())));
        t.addCell(left);

        PdfPCell right = cell(Element.ALIGN_RIGHT);
        right.addElement(right(new Paragraph("Fatura / Fiş taslağı", new Font(bold, 14))));
        OffsetDateTime date = s.issuedAt() != null ? s.issuedAt() : s.createdAt();
        right.addElement(right(new Paragraph("No: " + number(s), small())));
        if (date != null) {
            right.addElement(right(new Paragraph("Tarih: " + DATE_TIME.format(date.atZoneSameInstant(ApiDateTime.ZONE)), small())));
        }
        right.addElement(right(new Paragraph("Durum: " + STATUS.getOrDefault(s.status(), s.status()), small())));
        t.addCell(right);
        t.setSpacingAfter(16);
        return t;
    }

    private PdfPTable customer(InvoiceSummary s, PdfParty p) {
        PdfPTable t = table(new float[]{100});
        PdfPCell c = cell(Element.ALIGN_LEFT);
        c.addElement(new Paragraph("Sayın", new Font(bold, 10)));
        c.addElement(new Paragraph(p.clientName(), small()));
        if (p.clientPhone() != null) {
            c.addElement(new Paragraph("Tel: " + p.clientPhone(), small()));
        }
        if (p.clientAddress() != null) {
            c.addElement(new Paragraph(p.clientAddress(), small()));
        }
        if (s.animalName() != null) {
            c.addElement(new Paragraph("Hasta: " + s.animalName(), small()));
        }
        t.addCell(c);
        t.setSpacingAfter(12);
        return t;
    }

    private PdfPTable lines(List<LineResponse> lines) {
        PdfPTable t = new PdfPTable(COLUMNS);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        String[] titles = {"Açıklama", "Miktar", "Birim fiyat", "İndirim", "KDV", "KDV tutarı", "Tutar"};
        for (int i = 0; i < titles.length; i++) {
            t.addCell(row(titles[i], i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, true));
        }
        for (LineResponse l : lines) {
            String description = l.lotNumber() == null ? l.description() : l.description() + " (Parti: " + l.lotNumber() + ")";
            t.addCell(row(description, Element.ALIGN_LEFT, false));
            t.addCell(row(quantity(l.quantity()), Element.ALIGN_RIGHT, false));
            t.addCell(row(money(l.unitPrice()), Element.ALIGN_RIGHT, false));
            t.addCell(row(l.discountAmount().signum() == 0 ? "-" : money(l.discountAmount()), Element.ALIGN_RIGHT, false));
            t.addCell(row("%" + quantity(l.taxRate()), Element.ALIGN_RIGHT, false));
            t.addCell(row(money(l.taxAmount()), Element.ALIGN_RIGHT, false));
            t.addCell(row(money(l.lineTotal()), Element.ALIGN_RIGHT, false));
        }
        t.setSpacingAfter(12);
        return t;
    }

    private PdfPTable totals(InvoiceResponse inv) {
        PdfPTable t = table(new float[]{50, 30, 20});
        InvoiceSummary s = inv.invoice();
        totalRow(t, "Ara toplam (KDV hariç)", money(inv.grossTotal()), false);
        totalRow(t, "İndirim", inv.discountTotal().signum() == 0 ? money(inv.discountTotal())
                : "− " + money(inv.discountTotal()), false);
        totalRow(t, "KDV", money(inv.taxTotal()), false);
        totalRow(t, "Genel toplam", money(s.grandTotal()), true);
        totalRow(t, "Ödenen", money(s.paidTotal()), false);
        totalRow(t, "Kalan", money(s.balance()), false);
        return t;
    }

    private void totalRow(PdfPTable t, String label, String value, boolean strong) {
        Font f = strong ? new Font(bold, 11) : small();
        t.addCell(cell(Element.ALIGN_LEFT));
        PdfPCell l = cell(Element.ALIGN_RIGHT);
        l.setPhrase(new Phrase(label, f));
        t.addCell(l);
        PdfPCell v = cell(Element.ALIGN_RIGHT);
        v.setPhrase(new Phrase(value, f));
        t.addCell(v);
    }

    private PdfPCell row(String text, int align, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(text, header ? new Font(bold, 8.5f) : new Font(regular, 8.5f)));
        c.setHorizontalAlignment(align);
        c.setBorder(header ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
        c.setBorderColor(RULE);
        c.setPaddingBottom(4);
        return c;
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell cell(int align) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static Paragraph right(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Font small() {
        return new Font(regular, 9);
    }

    private static String number(InvoiceSummary s) {
        return "DRAFT".equals(s.status()) || "VOID".equals(s.status()) ? "Taslak" : s.invoiceNo();
    }

    static String money(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(TR));
        return f.format(value == null ? BigDecimal.ZERO : value) + " TL";
    }

    private static String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String joinPresent(String separator, String... values) {
        return String.join(separator, java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
    }

    private static BaseFont font(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException ex) {
            throw new UncheckedIOException("Yazı tipi yüklenemedi: " + path, ex);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Yazı tipi yüklenemedi: " + path, ex);
        }
    }
}
