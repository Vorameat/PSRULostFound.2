package com.example.psrulostfound;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;

/** ตัวช่วยฝั่ง View ที่ใช้ซ้ำหลายหน้า */
public final class UiUtil {

    private UiUtil() { }

    /**
     * เว้นขอบให้พ้น status bar / navigation bar / คีย์บอร์ด
     * (targetSdk 35+ บังคับ edge-to-edge จึงต้องจัดการเอง)
     */
    public static void applySystemBarPadding(View root) {
        final int l = root.getPaddingLeft();
        final int t = root.getPaddingTop();
        final int r = root.getPaddingRight();
        final int b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static int accentColor(Context c, ItemModel item) {
        if (item.claimed()) return ContextCompat.getColor(c, R.color.psru_claimed);
        return ContextCompat.getColor(c, ItemModel.TYPE_FOUND.equals(item.resolveType())
                ? R.color.psru_found : R.color.psru_lost);
    }

    public static int containerColor(Context c, ItemModel item) {
        if (item.claimed()) return ContextCompat.getColor(c, R.color.psru_claimed_container);
        return ContextCompat.getColor(c, ItemModel.TYPE_FOUND.equals(item.resolveType())
                ? R.color.psru_found_container : R.color.psru_lost_container);
    }

    public static String tagLabel(ItemModel item) {
        if (item.claimed()) return "ปิดรายการแล้ว";
        return ItemModel.TYPE_FOUND.equals(item.resolveType()) ? "เจอของ" : "ของหาย";
    }

    /** ป้ายสีขนาดเล็ก (pill) บอกประเภท */
    public static void bindTag(TextView tv, ItemModel item) {
        tv.setText(tagLabel(item));
        tv.setTextColor(accentColor(tv.getContext(), item));
        tv.setBackgroundTintList(ColorStateList.valueOf(containerColor(tv.getContext(), item)));
    }

    /** เช่น "5 นาทีที่ผ่านมา" — คืนค่าว่างถ้าไม่มีเวลา */
    public static CharSequence relativeTime(Timestamp ts) {
        if (ts == null) return "";
        return DateUtils.getRelativeTimeSpanString(
                ts.toDate().getTime(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }
}
