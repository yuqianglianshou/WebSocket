package com.lq.lan.ui;

import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** 聊天式消息列表：收到的靠左、发出的靠右、系统事件居中 */
public final class LogAdapter extends ListAdapter<LogEntry, RecyclerView.ViewHolder> {

    private static final int VIEW_BUBBLE = 0;
    private static final int VIEW_SYSTEM = 1;

    public LogAdapter() {
        super(new DiffUtil.ItemCallback<LogEntry>() {
            @Override
            public boolean areItemsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                return true; // 条目不可变
            }
        });
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        LogEntry.Type t = getItem(position).type;
        return t == LogEntry.Type.IN || t == LogEntry.Type.OUT ? VIEW_BUBBLE : VIEW_SYSTEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        return viewType == VIEW_BUBBLE
                ? new BubbleHolder(inf.inflate(R.layout.lan_item_bubble, parent, false))
                : new SystemHolder(inf.inflate(R.layout.lan_item_system, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        LogEntry e = getItem(position);
        if (holder instanceof BubbleHolder) ((BubbleHolder) holder).bind(e);
        else ((SystemHolder) holder).bind(e);
    }

    private static CharSequence time(long t) {
        return DateFormat.format("HH:mm:ss", t);
    }

    static final class BubbleHolder extends RecyclerView.ViewHolder {
        final LinearLayout column;
        final TextView meta;
        final MaterialCardView card;
        final TextView text;

        BubbleHolder(View v) {
            super(v);
            column = v.findViewById(R.id.lan_bubble_column);
            meta = v.findViewById(R.id.lan_bubble_meta);
            card = v.findViewById(R.id.lan_bubble_card);
            text = v.findViewById(R.id.lan_bubble_text);
        }

        void bind(LogEntry e) {
            boolean out = e.type == LogEntry.Type.OUT;
            column.setGravity(out ? Gravity.END : Gravity.START);
            int bg = MaterialColors.getColor(card, out
                    ? com.google.android.material.R.attr.colorPrimaryContainer
                    : com.google.android.material.R.attr.colorSurfaceContainerHigh);
            int fg = MaterialColors.getColor(card, out
                    ? com.google.android.material.R.attr.colorOnPrimaryContainer
                    : com.google.android.material.R.attr.colorOnSurface);
            card.setCardBackgroundColor(bg);
            text.setTextColor(fg);
            text.setText(e.text);
            CharSequence t = time(e.time);
            meta.setText(e.title == null ? t : meta.getContext().getString(R.string.lan_meta_format, e.title, t));
        }
    }

    static final class SystemHolder extends RecyclerView.ViewHolder {
        final TextView text;

        SystemHolder(View v) {
            super(v);
            text = (TextView) v;
        }

        void bind(LogEntry e) {
            int color = MaterialColors.getColor(text, e.type == LogEntry.Type.ERROR
                    ? androidx.appcompat.R.attr.colorError
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            text.setTextColor(color);
            text.setText(text.getContext().getString(R.string.lan_system_format, time(e.time), e.text));
        }
    }
}
