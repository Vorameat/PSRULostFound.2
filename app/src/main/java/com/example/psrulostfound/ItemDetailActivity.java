package com.example.psrulostfound;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Controller หน้ารายละเอียด + ปิดรายการ
 * รับแค่ documentId แล้วโหลดข้อมูลจาก Firestore (ไม่ส่ง PIN/ข้อมูลผ่าน Intent อีกต่อไป)
 *
 * การปิดรายการ:
 *  - เจ้าของประกาศ  → ยืนยันแล้วปิดได้เลย (เซิร์ฟเวอร์เช็ก uid เอง)
 *  - คนอื่น          → กรอก PIN; เซิร์ฟเวอร์ตรวจ hash, จำกัดจำนวนครั้ง และล็อกเมื่อกรอกผิดเกินกำหนด
 */
public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DOCUMENT_ID = "documentId";

    private PostRepository repo;
    private String documentId;
    private ItemModel item;

    private View scrollContent, progress, bottomBar;
    private ImageView imgItem;
    private TextView tvTypeTag, tvCategory, tvTitle, tvTime, tvDescription, tvLocation,
            tvReporterName, tvPhone, tvClaimedNote;
    private MaterialButton btnClaim, btnCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);
        UiUtil.applySystemBarPadding(findViewById(R.id.root));

        repo = new PostRepository();
        documentId = getIntent().getStringExtra(EXTRA_DOCUMENT_ID);

        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        scrollContent = findViewById(R.id.scrollContent);
        progress = findViewById(R.id.progress);
        bottomBar = findViewById(R.id.bottomBar);
        imgItem = findViewById(R.id.imgItem);
        tvTypeTag = findViewById(R.id.tvTypeTag);
        tvCategory = findViewById(R.id.tvCategory);
        tvTitle = findViewById(R.id.tvTitle);
        tvTime = findViewById(R.id.tvTime);
        tvDescription = findViewById(R.id.tvDescription);
        tvLocation = findViewById(R.id.tvLocation);
        tvReporterName = findViewById(R.id.tvReporterName);
        tvPhone = findViewById(R.id.tvPhone);
        tvClaimedNote = findViewById(R.id.tvClaimedNote);
        btnClaim = findViewById(R.id.btnClaim);
        btnCall = findViewById(R.id.btnCall);

        btnClaim.setOnClickListener(v -> onClaimClicked());

        if (documentId == null || documentId.isEmpty()) {
            Toast.makeText(this, "ไม่พบรหัสประกาศ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadItem();
    }

    private void loadItem() {
        progress.setVisibility(View.VISIBLE);
        repo.ensureSignedIn(
                () -> repo.getPost(documentId,
                        loaded -> {
                            if (isFinishing() || isDestroyed()) return;
                            item = loaded;
                            bind(loaded);
                        },
                        this::onLoadFailed),
                this::onLoadFailed);
    }

    private void onLoadFailed(Exception e) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, PostRepository.messageOf(e), Toast.LENGTH_LONG).show();
        finish();
    }

    private void bind(ItemModel it) {
        progress.setVisibility(View.GONE);
        scrollContent.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        UiUtil.bindTag(tvTypeTag, it);
        tvCategory.setText(it.resolveCategory().getLabel());
        tvTitle.setText(it.getTitle() != null ? it.getTitle() : "");
        tvLocation.setText(it.getLocation() != null ? it.getLocation() : "-");

        CharSequence time = UiUtil.relativeTime(it.getCreatedAt());
        tvTime.setText(time.length() == 0 ? "" : "ลงประกาศ " + time);
        tvTime.setVisibility(time.length() == 0 ? View.GONE : View.VISIBLE);

        String desc = it.getDescription();
        tvDescription.setVisibility(desc == null || desc.isEmpty() ? View.GONE : View.VISIBLE);
        tvDescription.setText(desc != null ? desc : "");

        String reporter = it.getReporterName();
        tvReporterName.setText(reporter == null || reporter.isEmpty() ? "ไม่ระบุชื่อ" : reporter);

        final String phone = it.getPhone() != null ? it.getPhone() : "";
        tvPhone.setText(phone.isEmpty() ? "-" : phone);
        // แสดงปุ่มโทรเฉพาะเมื่อดูเป็นเบอร์โทรจริง (ไม่ใช่ LINE ID)
        final String dialable = phone.replaceAll("[^0-9+]", "");
        boolean looksLikePhone = phone.matches("[0-9+\\-\\s()]+") && dialable.length() >= 8;
        btnCall.setVisibility(looksLikePhone ? View.VISIBLE : View.GONE);
        btnCall.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialable))));

        String url = it.getImageUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(url).centerCrop()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .into(imgItem);
        } else {
            imgItem.setImageResource(R.drawable.bg_image_placeholder);
        }

        if (it.claimed()) {
            btnClaim.setVisibility(View.GONE);
            tvClaimedNote.setVisibility(View.VISIBLE);
        } else {
            btnClaim.setVisibility(View.VISIBLE);
            tvClaimedNote.setVisibility(View.GONE);
        }
    }

    // ---------------- ปิดรายการ ----------------

    private boolean isOwner() {
        String uid = repo.currentUid();
        return uid != null && item != null && uid.equals(item.getUserId());
    }

    private void onClaimClicked() {
        if (item == null) return;
        if (isOwner()) showOwnerConfirmDialog();
        else showPinDialog();
    }

    private void showOwnerConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("ปิดรายการนี้?")
                .setMessage("คุณเป็นเจ้าของประกาศ จึงปิดรายการได้โดยไม่ต้องกรอก PIN")
                .setPositiveButton("ปิดรายการ", (d, w) -> claim(null, null))
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private void showPinDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_pin, null);
        TextInputLayout tilPin = content.findViewById(R.id.tilPin);
        TextInputEditText etPin = content.findViewById(R.id.etPin);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("ยืนยันด้วย PIN")
                .setView(content)
                // ใส่ listener เป็น null ก่อน แล้วผูกใหม่หลัง show() เพื่อไม่ให้ dialog ปิดเองเมื่อ PIN ผิด
                .setPositiveButton("ยืนยัน", null)
                .setNegativeButton("ยกเลิก", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        etPin.requestFocus();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = etPin.getText() != null ? etPin.getText().toString().trim() : "";
            if (!pin.matches("\\d{4}")) {
                tilPin.setError("กรุณากรอกตัวเลข 4 หลัก");
                return;
            }
            tilPin.setError(null);
            claim(pin, new ClaimUi(dialog, tilPin));
        });
    }

    /** ตัวช่วยจัดการหน้าตา dialog ระหว่างรอผลจากเซิร์ฟเวอร์ */
    private static class ClaimUi {
        final AlertDialog dialog;
        final TextInputLayout tilPin;

        ClaimUi(AlertDialog dialog, TextInputLayout tilPin) {
            this.dialog = dialog;
            this.tilPin = tilPin;
        }

        void setBusy(boolean busy) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
            tilPin.setEnabled(!busy);
        }
    }

    private void claim(String pin, ClaimUi ui) {
        if (ui != null) ui.setBusy(true);
        btnClaim.setEnabled(false);

        repo.claimPost(documentId, pin,
                () -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (ui != null) ui.dialog.dismiss();
                    Toast.makeText(this, "ปิดรายการเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show();
                    finish();
                },
                e -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnClaim.setEnabled(true);
                    String msg = PostRepository.messageOf(e);
                    if (ui != null && PostRepository.isInputError(e)) {
                        // PIN ผิด / ถูกล็อก: เปิด dialog ค้างไว้และแสดงข้อความที่ช่องกรอก
                        ui.setBusy(false);
                        ui.tilPin.setError(msg);
                    } else {
                        if (ui != null) ui.dialog.dismiss();
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
