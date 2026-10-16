package com.example.psrulostfound;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;

/** Controller หน้าประวัติ: ประกาศที่ปิดแล้ว (status = claimed) เรียงตามเวลาที่ปิด */
public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    private PostRepository repo;
    private ItemAdapter adapter;
    private ListenerRegistration registration;
    private boolean started = false;
    private View emptyState, progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        UiUtil.applySystemBarPadding(findViewById(R.id.root));

        repo = new PostRepository();
        emptyState = findViewById(R.id.emptyState);
        progress = findViewById(R.id.progress);

        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ItemAdapter(item -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra(ItemDetailActivity.EXTRA_DOCUMENT_ID, item.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        progress.setVisibility(View.VISIBLE);
        repo.ensureSignedIn(
                () -> {
                    if (started && registration == null) startListening();
                },
                e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, PostRepository.messageOf(e), Toast.LENGTH_SHORT).show();
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
        registration = repo.listenPosts(ItemModel.STATUS_CLAIMED, "claimedAt",
                items -> {
                    progress.setVisibility(View.GONE);
                    adapter.submitList(items);
                    emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                },
                e -> {
                    Log.e(TAG, "listen failed", e);
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, PostRepository.messageOf(e), Toast.LENGTH_LONG).show();
                });
    }
}
