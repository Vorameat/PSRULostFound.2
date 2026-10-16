package com.example.psrulostfound;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Locale;

/**
 * Model ของประกาศ (POJO สำหรับอ่านจาก Firestore)
 * หมายเหตุ: ไม่มี pinCode แล้ว — PIN (hash) อยู่ใน posts/{id}/private/auth ซึ่งไคลเอนต์อ่านไม่ได้
 */
@IgnoreExtraProperties
public class ItemModel {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLAIMED = "claimed";
    public static final String TYPE_LOST = "lost";
    public static final String TYPE_FOUND = "found";

    private String id;
    private String title;
    private String description;
    private String location;
    private String phone;
    private String reporterName;
    private String type;
    private String category;
    private String imageUrl;
    private String status;
    private String userId;
    private Timestamp createdAt;
    private Timestamp claimedAt;

    // จำเป็นสำหรับ Firestore
    public ItemModel() { }

    // id มาจาก doc.getId() ไม่ใช่ฟิลด์ในเอกสาร
    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Timestamp claimedAt) { this.claimedAt = claimedAt; }

    // ---------- helper (ชื่อไม่ขึ้นต้นด้วย get/set/is จึงไม่ถูกนับเป็นฟิลด์ของ Firestore) ----------

    public boolean claimed() {
        return STATUS_CLAIMED.equalsIgnoreCase(status);
    }

    /** แปลงค่า type เป็น lost/found (ประกาศเก่าเป็นข้อความอิสระ เช่น "เก็บของได้") */
    public String resolveType() {
        if (TYPE_FOUND.equals(type)) return TYPE_FOUND;
        if (TYPE_LOST.equals(type)) return TYPE_LOST;
        if (type != null) {
            String t = type.toLowerCase(Locale.ROOT);
            if (t.contains("found") || t.contains("เก็บ") || t.contains("พบ") || t.contains("เจอ")) {
                return TYPE_FOUND;
            }
        }
        return TYPE_LOST;
    }

    public Category resolveCategory() {
        return Category.fromKey(category);
    }

    /** ใช้กับช่องค้นหา (lowerQuery ต้องเป็นตัวพิมพ์เล็กแล้ว) */
    public boolean matches(String lowerQuery) {
        return contains(title, lowerQuery)
                || contains(location, lowerQuery)
                || contains(description, lowerQuery)
                || contains(resolveCategory().getLabel(), lowerQuery);
    }

    private static boolean contains(String text, String lowerQuery) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }
}
