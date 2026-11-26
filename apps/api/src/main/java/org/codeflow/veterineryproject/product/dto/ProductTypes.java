package org.codeflow.veterineryproject.product.dto;

/** vet.products.product_type CHECK kısıtındaki değerler (ilaç, aşı, mama, sarf, takviye, ekipman, diğer). */
public final class ProductTypes {

    public static final String PATTERN = "MEDICATION|VACCINE|FOOD|CONSUMABLE|SUPPLEMENT|EQUIPMENT|OTHER";

    private ProductTypes() {
    }
}
