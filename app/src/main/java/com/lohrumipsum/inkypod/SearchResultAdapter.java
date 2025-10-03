package com.lohrumipsum.inkypod;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class SearchResultAdapter extends ListAdapter<SearchResult, SearchResultAdapter.SearchResultViewHolder> {

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onSubscribeClick(SearchResult searchResult);
    }

    public SearchResultAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SearchResult> DIFF_CALLBACK = new DiffUtil.ItemCallback<SearchResult>() {
        @Override
        public boolean areItemsTheSame(@NonNull SearchResult oldItem, @NonNull SearchResult newItem) {
            return oldItem.feedUrl.equals(newItem.feedUrl);
        }

        @Override
        public boolean areContentsTheSame(@NonNull SearchResult oldItem, @NonNull SearchResult newItem) {
            return oldItem.title.equals(newItem.title) &&
                   oldItem.author.equals(newItem.author) &&
                   oldItem.description.equals(newItem.description);
        }
    };

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_search_result, parent, false);
        return new SearchResultViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        SearchResult current = getItem(position);
        holder.bind(current, listener);
    }

    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView authorTextView;
        private final TextView descriptionTextView;
        private final Button subscribeButton;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.search_result_title);
            authorTextView = itemView.findViewById(R.id.search_result_author);
            descriptionTextView = itemView.findViewById(R.id.search_result_description);
            subscribeButton = itemView.findViewById(R.id.subscribe_button_search);
        }

        public void bind(final SearchResult searchResult, final OnItemClickListener listener) {
            titleTextView.setText(searchResult.title);
            authorTextView.setText(searchResult.author);
            descriptionTextView.setText(searchResult.description);
            subscribeButton.setOnClickListener(v -> listener.onSubscribeClick(searchResult));
        }
    }
}

