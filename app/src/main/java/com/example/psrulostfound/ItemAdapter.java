package com.example.psrulostfound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.Objects;

/**
 * View: แสดงการ์ดประกาศ
 * ใช้ ListAdapter + DiffUtil → ส่งลิสต์ใหม่ด้วย submitList() ทุกครั้ง
 * (แก้บั๊กเดิมที่ filterList() สลับ reference ของลิสต์จนข้อมูลหลังค้นหาไม่อัปเดต)
 */
public class ItemAdapter extends ListAdapter<ItemModel, ItemAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ItemModel item);
    }

    private final OnItemClickListener listener;

    public ItemAdapter(@NonNull OnItemClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ItemModel> DIFF = new DiffUtil.ItemCallback<ItemModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull ItemModel a, @NonNull ItemModel b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ItemModel a, @NonNull ItemModel b) {
            return Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getLocation(), b.getLocation())
                    && Objects.equals(a.getImageUrl(), b.getImageUrl())
                    && Objects.equals(a.getStatus(), b.getStatus())
                    && Objects.equals(a.getType(), b.getType())
                    && Objects.equals(a.getCategory(), b.getCategory())
                    && Objects.equals(a.getCreatedAt(), b.getCreatedAt())
                    && Objects.equals(a.getClaimedAt(), b.getClaimedAt());
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ItemModel item = getItem(position);

        h.viewStripe.setBackgroundColor(UiUtil.accentColor(h.itemView.getContext(), item));
        UiUtil.bindTag(h.tvTypeTag, item);
        h.tvCategory.setText(item.resolveCategory().getLabel());
        h.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");
        h.tvLocation.setText(item.getLocation() != null ? item.getLocation() : "-");

        CharSequence time;
        if (item.claimed() && item.getClaimedAt() != null) {
            time = "ปิดรายการเมื่อ " + UiUtil.relativeTime(item.getClaimedAt());
        } else {
            time = UiUtil.relativeTime(item.getCreatedAt());
        }
        h.tvTime.setText(time);
        h.tvTime.setVisibility(time.length() == 0 ? View.GONE : View.VISIBLE);

        String url = item.getImageUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(h.imgItem)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .into(h.imgItem);
        } else {
            Glide.with(h.imgItem).clear(h.imgItem);
            h.imgItem.setImageResource(R.drawable.bg_image_placeholder);
        }

        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewStripe;
        final ImageView imgItem;
        final TextView tvTypeTag, tvCategory, tvTitle, tvLocation, tvTime;

        ViewHolder(@NonNull View v) {
            super(v);
            viewStripe = v.findViewById(R.id.viewStripe);
            imgItem = v.findViewById(R.id.imgItem);
            tvTypeTag = v.findViewById(R.id.tvTypeTag);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvLocation = v.findViewById(R.id.tvLocation);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }
}
