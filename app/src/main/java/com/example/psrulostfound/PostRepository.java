package com.example.psrulostfound;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ชั้นเข้าถึงข้อมูลทั้งหมด (Activity ไม่แตะ Firestore ตรงๆ)
 *
 * ระบบ PIN (ใช้ Firestore Security Rules อย่างเดียว ไม่ต้องมี Cloud Functions):
 *  - ตอนสร้างประกาศ: สุ่ม salt (เก็บใน post) แล้วเก็บ SHA-256(salt:pin) ไว้ใน posts/{id}/private/auth
 *    ซึ่ง Rules ห้ามไคลเอนต์ "อ่าน" ทุกกรณี
 *  - ตอนปิดรายการ: แอปคำนวณ hash จาก PIN ที่กรอกแล้วส่งเป็น claimHash
 *    Rules เทียบกับ pinHash ฝั่งเซิร์ฟเวอร์ ถ้าไม่ตรงจะปฏิเสธการเขียน (PERMISSION_DENIED)
 */
public class PostRepository {

    private static final String TAG = "PostRepository";
    private static final String COLLECTION = "posts";

    public interface OnSuccess<T> { void accept(T value); }
    public interface OnError { void accept(Exception e); }

    /** ข้อผิดพลาดที่ผู้ใช้แก้เองได้ (เช่น PIN ผิด) — ข้อความแสดงผู้ใช้ได้เลย */
    public static class InputException extends Exception {
        public InputException(String message) { super(message); }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // ---------------- Auth ----------------

    public void ensureSignedIn(@NonNull Runnable onReady, @NonNull OnError onError) {
        if (auth.getCurrentUser() != null) {
            onReady.run();
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                onReady.run();
            } else {
                onError.accept(task.getException() != null
                        ? task.getException() : new Exception("sign-in failed"));
            }
        });
    }

    @Nullable
    public String currentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    // ---------------- Read ----------------

    /** ต้องมี composite index (status + orderField) ดู firestore.indexes.json */
    public ListenerRegistration listenPosts(@NonNull String status, @NonNull String orderField,
                                            @NonNull OnSuccess<List<ItemModel>> onSuccess,
                                            @NonNull OnError onError) {
        return db.collection(COLLECTION)
                .whereEqualTo("status", status)
                .orderBy(orderField, Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        onError.accept(error);
                        return;
                    }
                    if (snap == null) return;
                    List<ItemModel> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        ItemModel item = doc.toObject(ItemModel.class);
                        item.setId(doc.getId());
                        list.add(item);
                    }
                    onSuccess.accept(list);
                });
    }

    public void getPost(@NonNull String id, @NonNull OnSuccess<ItemModel> onSuccess,
                        @NonNull OnError onError) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        onError.accept(new Exception("ไม่พบประกาศนี้ (อาจถูกลบไปแล้ว)"));
                        return;
                    }
                    ItemModel item = doc.toObject(ItemModel.class);
                    if (item == null) {
                        onError.accept(new Exception("อ่านข้อมูลประกาศไม่สำเร็จ"));
                        return;
                    }
                    item.setId(doc.getId());
                    onSuccess.accept(item);
                })
                .addOnFailureListener(onError::accept);
    }

    // ---------------- Write ----------------

    /** เขียนประกาศ + PIN hash พร้อมกันแบบ batch (สำเร็จทั้งคู่หรือล้มเหลวทั้งคู่) */
    public void createPost(@NonNull String type, @NonNull String category, @NonNull String title,
                           @NonNull String description, @NonNull String location,
                           @NonNull String phone, @NonNull String reporterName,
                           @NonNull String imageUrl, @NonNull String pin,
                           @NonNull OnSuccess<String> onSuccess, @NonNull OnError onError) {
        String uid = currentUid();
        if (uid == null) {
            onError.accept(new Exception("ยังไม่ได้เข้าสู่ระบบ กรุณาลองใหม่อีกครั้ง"));
            return;
        }

        String salt = newSalt();
        DocumentReference ref = db.collection(COLLECTION).document();

        Map<String, Object> post = new HashMap<>();
        post.put("type", type);
        post.put("category", category);
        post.put("title", title);
        post.put("description", description);
        post.put("location", location);
        post.put("phone", phone);
        post.put("reporterName", reporterName);
        post.put("imageUrl", imageUrl);
        post.put("status", ItemModel.STATUS_ACTIVE);
        post.put("userId", uid);
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("pinSalt", salt);

        Map<String, Object> secret = new HashMap<>();
        secret.put("pinHash", sha256Hex(salt + ":" + pin));

        WriteBatch batch = db.batch();
        batch.set(ref, post);
        batch.set(ref.collection("private").document("auth"), secret);
        batch.commit()
                .addOnSuccessListener(v -> onSuccess.accept(ref.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "createPost failed", e);
                    onError.accept(e);
                });
    }

    /** pin = null สำหรับเจ้าของประกาศ (Rules ตรวจ uid เอง) */
    public void claimPost(@NonNull String postId, @Nullable String pin,
                          @NonNull Runnable onSuccess, @NonNull OnError onError) {
        String uid = currentUid();
        if (uid == null) {
            onError.accept(new Exception("ยังไม่ได้เข้าสู่ระบบ กรุณาลองใหม่อีกครั้ง"));
            return;
        }
        DocumentReference ref = db.collection(COLLECTION).document(postId);

        if (pin == null) {
            doClaim(ref, uid, null, onSuccess, onError);
            return;
        }
        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                onError.accept(new Exception("ไม่พบประกาศนี้ (อาจถูกลบไปแล้ว)"));
                return;
            }
            String salt = doc.getString("pinSalt");
            if (salt == null) {
                onError.accept(new Exception(
                        "ประกาศนี้เป็นข้อมูลเก่าที่ไม่มี PIN สำหรับตรวจสอบ กรุณาให้เจ้าของประกาศปิดรายการ"));
                return;
            }
            doClaim(ref, uid, sha256Hex(salt + ":" + pin), onSuccess, onError);
        }).addOnFailureListener(onError::accept);
    }

    private void doClaim(DocumentReference ref, String uid, @Nullable String claimHash,
                         Runnable onSuccess, OnError onError) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", ItemModel.STATUS_CLAIMED);
        update.put("claimedAt", FieldValue.serverTimestamp());
        update.put("claimedBy", uid);
        if (claimHash != null) update.put("claimHash", claimHash);

        ref.update(update)
                .addOnSuccessListener(v -> onSuccess.run())
                .addOnFailureListener(e -> {
                    Log.w(TAG, "claimPost failed", e);
                    if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                            == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // Rules ปฏิเสธ = PIN ไม่ตรง (หรือรายการถูกปิดไปแล้ว)
                        onError.accept(new InputException(
                                "PIN ไม่ถูกต้อง หรือรายการนี้ถูกปิดไปแล้ว"));
                    } else {
                        onError.accept(e);
                    }
                });
    }

    // ---------------- Hash helpers ----------------

    private static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return toHex(b);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // ---------------- Error helpers ----------------

    private static final String GENERIC = "เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง";
    private static final String OFFLINE = "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ต";

    public static String messageOf(@Nullable Exception e) {
        if (e instanceof InputException) return e.getMessage();
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) return OFFLINE;
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                // query ต้องใช้ composite index — ลิงก์สร้าง index อยู่ใน Logcat
                return "ยังไม่ได้สร้าง index ใน Firestore (ดูลิงก์ใน Logcat หรือ deploy firestore.indexes.json)";
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "ไม่มีสิทธิ์ทำรายการนี้ (ตรวจสอบ Firestore Rules ว่าเผยแพร่แล้วหรือยัง)";
            }
            return GENERIC;
        }
        if (e instanceof IOException) return OFFLINE;
        if (e != null && e.getMessage() != null && e.getClass() == Exception.class) {
            return e.getMessage(); // ข้อความที่เราสร้างเองใน Repository
        }
        return GENERIC;
    }

    /** ความผิดพลาดที่ผู้ใช้แก้ได้เอง — dialog ควรเปิดค้างไว้ */
    public static boolean isInputError(@Nullable Exception e) {
        return e instanceof InputException;
    }
}
