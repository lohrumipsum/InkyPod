package com.lohrumipsum.inkypod;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class SubscriptionAdapter extends ListAdapter<Subscription, SubscriptionAdapter.SubscriptionViewHolder> {

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Subscription subscription);
    }

    public SubscriptionAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Subscription> DIFF_CALLBACK = new DiffUtil.ItemCallback<Subscription>() {
        @Override
        public boolean areItemsTheSame(@NonNull Subscription oldItem, @NonNull Subscription newItem) {
            return oldItem.feedUrl.equals(newItem.feedUrl);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Subscription oldItem, @NonNull Subscription newItem) {
            return oldItem.title.equals(newItem.title);
        }
    };


    @NonNull
    @Override
    public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_subscription, parent, false);
        return new SubscriptionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
        Subscription currentSubscription = getItem(position);
        holder.titleTextView.setText(currentSubscription.title);
    }

    class SubscriptionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;

        public SubscriptionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.subscription_title);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
        }
    }
}

