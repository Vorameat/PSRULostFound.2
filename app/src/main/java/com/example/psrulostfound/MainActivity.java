package com.example.psrulostfound;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller หน้าแรก: แสดงประกาศที่ยังเปิดอยู่ (status = active)
 * กรองตามข้อความ / ประเภท (ของหาย-เจอของ) / หมวดหมู่ ได้พร้อมกัน
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private PostRepository repo;
    private ItemAdapter adapter;
    private ListenerRegistration registration;
    private boolean started = false;

    /** ข้อมูลทั้งหมดจาก Firestore (ต้นฉบับ) — ตัวกรองสร้างลิสต์ใหม่จากตรงนี้ทุกครั้ง */
    private final List<ItemModel> allItems = new ArrayList<>();
    private String query = "";
    private String typeFilter = null;       // null = ทั้งหมด
    private Category categoryFilter = null; // null = ทั้งหมด

    private TextView tvCount, tvEmptyTitle, tvEmptyHint;
    private View emptyState, progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UiUtil.applySystemBarPadding(findViewById(R.id.root));

        repo = new PostRepository();

        tvCount = findViewById(R.id.tvCount);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        emptyState = findViewById(R.id.emptyState);
        progress = findViewById(R.id.progress);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ItemAdapter(this::openDetail);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddPostActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        setupSearch();
        setupTypeFilter();
        setupCategoryChips();
    }

    // ---------------- Lifecycle: ผูก listener กับ onStart/onStop กันรั่ว ----------------

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        showLoading(true);
        repo.ensureSignedIn(
                () -> {
                    if (started && registration == null) startListening();
                },
                e -> {
                    showLoading(false);
                    Log.e(TAG, "sign-in failed", e);
                    Toast.makeText(this, "เชื่อมต่อระบบยืนยันตัวตนล้มเหลว", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void startListening() {
        registration = repo.listenPosts(ItemModel.STATUS_ACTIVE, "createdAt",
                items -> {
                    allItems.clear();
                    allItems.addAll(items);
                    showLoading(false);
                    applyFilters();
                },
                e -> {
                    // ถ้าขาด index Logcat จะมีลิงก์สร้าง index ให้กดได้
                    Log.e(TAG, "listen failed", e);
                    showLoading(false);
                    Toast.makeText(this, PostRepository.messageOf(e), Toast.LENGTH_LONG).show();
                });
    }

    // ---------------- ตัวกรอง ----------------

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void setupTypeFilter() {
        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleType);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnTypeLost) typeFilter = ItemModel.TYPE_LOST;
            else if (checkedId == R.id.btnTypeFound) typeFilter = ItemModel.TYPE_FOUND;
            else typeFilter = null;
            applyFilters();
        });
    }

    private void setupCategoryChips() {
        ChipGroup group = findViewById(R.id.chipGroupCategory);
        group.removeAllViews();

        Chip all = newChip(group, "ทั้งหมด", null);
        for (Category c : Category.values()) newChip(group, c.getLabel(), c);
        group.check(all.getId());

        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = g.findViewById(checkedIds.get(0));
            categoryFilter = chip != null ? (Category) chip.getTag() : null;
            applyFilters();
        });
    }

    private Chip newChip(ChipGroup group, String text, Category category) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, group, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(category);
        group.addView(chip);
        return chip;
    }

    /** สร้างลิสต์ใหม่จาก allItems ตามตัวกรองทั้งหมด แล้วส่งให้ adapter */
    private void applyFilters() {
        List<ItemModel> out = new ArrayList<>();
        for (ItemModel item : allItems) {
            if (typeFilter != null && !typeFilter.equals(item.resolveType())) continue;
            if (categoryFilter != null && categoryFilter != item.resolveCategory()) continue;
            if (!query.isEmpty() && !item.matches(query)) continue;
            out.add(item);
        }
        adapter.submitList(out);
        tvCount.setText(out.size() + " รายการ");

        boolean empty = out.isEmpty();
        emptyState.setVisibility(empty && progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        if (empty) {
            if (allItems.isEmpty()) {
                tvEmptyTitle.setText("ยังไม่มีประกาศ");
                tvEmptyHint.setText("กดปุ่ม \"แจ้งประกาศ\" เพื่อเป็นคนแรก");
            } else {
                tvEmptyTitle.setText("ไม่พบรายการที่ตรงกัน");
                tvEmptyHint.setText("ลองเปลี่ยนคำค้นหาหรือตัวกรอง");
            }
        }
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) emptyState.setVisibility(View.GONE);
    }

    private void openDetail(ItemModel item) {
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_DOCUMENT_ID, item.getId());
        startActivity(intent);
    }
}
