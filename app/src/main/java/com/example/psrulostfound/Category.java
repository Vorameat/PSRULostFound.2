package com.example.psrulostfound;

/**
 * หมวดหมู่ของ — เก็บใน Firestore เป็น key (ภาษาอังกฤษ) แสดงผลเป็น label ภาษาไทย
 * key ต้องตรงกับ CATEGORIES ใน functions/index.js
 */
public enum Category {
    DOCUMENTS("documents", "บัตร / เอกสาร"),
    WALLET("wallet", "กระเป๋า / กระเป๋าสตางค์"),
    ELECTRONICS("electronics", "อุปกรณ์อิเล็กทรอนิกส์"),
    KEYS("keys", "กุญแจ"),
    BOOKS("books", "หนังสือ / เครื่องเขียน"),
    CLOTHING("clothing", "เสื้อผ้า / เครื่องประดับ"),
    OTHER("other", "อื่นๆ");

    private final String key;
    private final String label;

    Category(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() { return key; }

    public String getLabel() { return label; }

    /** ประกาศเก่าที่ไม่มีหมวดหมู่ (หรือค่าที่ไม่รู้จัก) จะถูกจัดเป็น "อื่นๆ" */
    public static Category fromKey(String key) {
        if (key != null) {
            for (Category c : values()) {
                if (c.key.equals(key)) return c;
            }
        }
        return OTHER;
    }
}
