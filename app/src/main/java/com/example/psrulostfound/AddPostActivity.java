package com.example.psrulostfound;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Controller หน้าแจ้งประกาศ
 * - ตรวจข้อมูลเบื้องต้นฝั่งแอป (UX) แต่การตรวจจริงและการเก็บ PIN (hash) ทำที่ Cloud Function createPost
 */
public class AddPostActivity extends AppCompatActivity {

    private PostRepository repo;

    private static final String LOCATION_OTHER = "อื่นๆ (ระบุเอง)";

    private TextInputLayout tilTitle, tilLocation, tilLocationOther, tilPhone, tilPin, tilImageUrl;
    private TextInputEditText etTitle, etLocationOther, etDescription, etImageUrl, etReporterName, etPhone, etPin;
    private MaterialAutoCompleteTextView actLocation;
    private String selectedLocation = null;
    private View tvTypeError, tvCategoryError;
    private MaterialButton btnSubmit;

    private String selectedType = null;
    private Category selectedCategory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_post);
        UiUtil.applySystemBarPadding(findViewById(R.id.root));

        repo = new PostRepository();

        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        tilTitle = findViewById(R.id.tilTitle);
        tilLocation = findViewById(R.id.tilLocation);
        tilLocationOther = findViewById(R.id.tilLocationOther);
        tilPhone = findViewById(R.id.tilPhone);
        tilPin = findViewById(R.id.tilPin);
        tilImageUrl = findViewById(R.id.tilImageUrl);
        etTitle = findViewById(R.id.etTitle);
        actLocation = findViewById(R.id.actLocation);
        etLocationOther = findViewById(R.id.etLocationOther);
        etDescription = findViewById(R.id.etDescription);
        etImageUrl = findViewById(R.id.etImageUrl);
        etReporterName = findViewById(R.id.etReporterName);
        etPhone = findViewById(R.id.etPhone);
        etPin = findViewById(R.id.etPinCode);
        tvTypeError = findViewById(R.id.tvTypeError);
        tvCategoryError = findViewById(R.id.tvCategoryError);
        btnSubmit = findViewById(R.id.btnSubmit);

        setupTypeToggle();
        setupCategoryChips();
        setupLocationDropdown();
        btnSubmit.setOnClickListener(v -> submit());
    }

    private void setupTypeToggle() {
        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleType);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                selectedType = checkedId == R.id.btnTypeFound ? ItemModel.TYPE_FOUND : ItemModel.TYPE_LOST;
                tvTypeError.setVisibility(View.GONE);
            } else if (group.getCheckedButtonId() == View.NO_ID) {
                selectedType = null;
            }
        });
    }

    /** ช่องเลือกสถานที่: รายการจาก res/values/locations_psru.xml + "อื่นๆ (ระบุเอง)" ต่อท้าย */
    private void setupLocationDropdown() {
        String[] base = getResources().getStringArray(R.array.psru_locations);
        String[] options = new String[base.length + 1];
        System.arraycopy(base, 0, options, 0, base.length);
        options[base.length] = LOCATION_OTHER;

        actLocation.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, options));
        actLocation.setOnItemClickListener((parent, view, position, id) -> {
            selectedLocation = options[position];
            tilLocation.setError(null);
            boolean other = LOCATION_OTHER.equals(selectedLocation);
            tilLocationOther.setVisibility(other ? View.VISIBLE : View.GONE);
            if (other) etLocationOther.requestFocus();
        });
    }

    private void setupCategoryChips() {
        ChipGroup group = findViewById(R.id.chipGroupCategory);
        group.removeAllViews();
        for (Category c : Category.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, group, false);
            chip.setId(View.generateViewId());
            chip.setText(c.getLabel());
            chip.setTag(c);
            group.addView(chip);
        }
        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = null;
            } else {
                Chip chip = g.findViewById(checkedIds.get(0));
                selectedCategory = chip != null ? (Category) chip.getTag() : null;
                tvCategoryError.setVisibility(View.GONE);
            }
        });
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private boolean validate(String title, String location, String phone, String imageUrl, String pin) {
        boolean ok = true;

        if (selectedType == null) { tvTypeError.setVisibility(View.VISIBLE); ok = false; }
        if (selectedCategory == null) { tvCategoryError.setVisibility(View.VISIBLE); ok = false; }

        tilTitle.setError(title.length() < 2 ? "กรุณากรอกชื่อสิ่งของ" : null);
        if (selectedLocation == null) {
            tilLocation.setError("กรุณาเลือกสถานที่");
        } else {
            tilLocation.setError(null);
        }
        tilLocationOther.setError(LOCATION_OTHER.equals(selectedLocation) && location.isEmpty()
                ? "กรุณาระบุสถานที่" : null);
        tilPhone.setError(phone.length() < 5 ? "กรุณากรอกเบอร์โทรหรือ LINE ID" : null);
        tilImageUrl.setError(!imageUrl.isEmpty() && !imageUrl.toLowerCase().startsWith("https://")
                ? "ลิงก์ต้องขึ้นต้นด้วย https://" : null);
        String pinError = null;
        if (!pin.matches("\\d{4}")) {
            pinError = "กรุณาตั้ง PIN เป็นตัวเลข 4 หลัก";
        } else if (pin.matches("(\\d)\\1{3}") || "01234567890".contains(pin) || "09876543210".contains(pin)) {
            pinError = "PIN เดาง่ายเกินไป กรุณาเลือกเลขอื่น";
        }
        tilPin.setError(pinError);

        if (tilTitle.getError() != null || tilLocation.getError() != null
                || tilLocationOther.getError() != null || tilPhone.getError() != null
                || tilImageUrl.getError() != null || tilPin.getError() != null) {
            ok = false;
        }
        return ok;
    }

    private void submit() {
        String title = text(etTitle);
        String location = LOCATION_OTHER.equals(selectedLocation) ? text(etLocationOther)
                : (selectedLocation != null ? selectedLocation : "");
        String description = text(etDescription);
        String imageUrl = text(etImageUrl);
        String reporter = text(etReporterName);
        String phone = text(etPhone);
        String pin = text(etPin);

        if (!validate(title, location, phone, imageUrl, pin)) return;

        setSubmitting(true);
        repo.ensureSignedIn(
                () -> repo.createPost(selectedType, selectedCategory.getKey(), title, description,
                        location, phone, reporter, imageUrl, pin,
                        id -> {
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(this, "ลงประกาศเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show();
                            finish();
                        },
                        e -> onSubmitFailed(e)),
                this::onSubmitFailed);
    }

    private void onSubmitFailed(Exception e) {
        if (isFinishing() || isDestroyed()) return;
        setSubmitting(false);
        Toast.makeText(this, PostRepository.messageOf(e), Toast.LENGTH_LONG).show();
    }

    private void setSubmitting(boolean submitting) {
        btnSubmit.setEnabled(!submitting);
        btnSubmit.setText(submitting ? "กำลังลงประกาศ..." : "ลงประกาศ");
    }
}
