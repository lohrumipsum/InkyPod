package com.lohrumipsum.inkypod;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SubscriptionsFragment extends Fragment {

    private PodcastViewModel podcastViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_subscriptions, container, false);

        podcastViewModel = new ViewModelProvider(requireActivity()).get(PodcastViewModel.class);

        // --- Subscriptions List Setup ---
        RecyclerView subscriptionsRecyclerView = view.findViewById(R.id.subscription_recycler_view);
        subscriptionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        subscriptionsRecyclerView.setHasFixedSize(true);

        final SubscriptionAdapter subscriptionAdapter = new SubscriptionAdapter(subscription -> {
            Intent intent = new Intent(getActivity(), SubscriptionDetailActivity.class);
            intent.putExtra(SubscriptionDetailActivity.EXTRA_FEED_URL, subscription.feedUrl);
            intent.putExtra(SubscriptionDetailActivity.EXTRA_FEED_TITLE, subscription.title);
            startActivity(intent);
        });
        subscriptionsRecyclerView.setAdapter(subscriptionAdapter);
        podcastViewModel.getAllSubscriptions().observe(getViewLifecycleOwner(), subscriptionAdapter::submitList);

        // --- Search Results List Setup ---
        RecyclerView searchResultsRecyclerView = view.findViewById(R.id.search_results_recycler_view);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResultsRecyclerView.setHasFixedSize(true);
        
        final SearchResultAdapter searchResultAdapter = new SearchResultAdapter(searchResult -> {
            podcastViewModel.subscribe(searchResult.feedUrl);
            // Clear search results after subscribing
            searchResultsRecyclerView.setAdapter(null);
            Toast.makeText(getContext(), "Subscribed to " + searchResult.title, Toast.LENGTH_SHORT).show();
        });
        searchResultsRecyclerView.setAdapter(searchResultAdapter);
        podcastViewModel.getSearchResults().observe(getViewLifecycleOwner(), searchResultAdapter::submitList);


        // --- UI Widgets and Click Listeners ---
        EditText rssUrlEditText = view.findViewById(R.id.rss_url_edit_text);
        Button subscribeButton = view.findViewById(R.id.subscribe_button);
        EditText searchTermEditText = view.findViewById(R.id.search_term_edit_text);
        Button searchButton = view.findViewById(R.id.search_button);


        subscribeButton.setOnClickListener(v -> {
            String url = rssUrlEditText.getText().toString();
            if (!url.isEmpty()) {
                podcastViewModel.subscribe(url);
                rssUrlEditText.setText("");
            }
        });
        
        searchButton.setOnClickListener(v -> {
            String term = searchTermEditText.getText().toString();
            if (!term.isEmpty()) {
                podcastViewModel.searchPodcasts(term);
                // Clear the other input field
                rssUrlEditText.setText("");
            }
        });

        return view;
    }
}

