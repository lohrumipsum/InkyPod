package com.lohrumipsum.inkypod;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SubscriptionsFragment extends Fragment {

    private PodcastViewModel podcastViewModel;
    private static final String TAG = "InkyPodDebug";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_subscriptions, container, false);

        podcastViewModel = new ViewModelProvider(requireActivity()).get(PodcastViewModel.class);

        // Setup for current subscriptions list
        RecyclerView subscriptionsRecyclerView = view.findViewById(R.id.subscription_recycler_view);
        subscriptionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final SubscriptionAdapter subscriptionAdapter = new SubscriptionAdapter(subscription -> {
            Intent intent = new Intent(getActivity(), SubscriptionDetailActivity.class);
            intent.putExtra(SubscriptionDetailActivity.EXTRA_FEED_URL, subscription.feedUrl);
            intent.putExtra(SubscriptionDetailActivity.EXTRA_FEED_TITLE, subscription.title);
            startActivity(intent);
        });
        subscriptionsRecyclerView.setAdapter(subscriptionAdapter);

        podcastViewModel.getAllSubscriptions().observe(getViewLifecycleOwner(), subscriptionAdapter::submitList);

        // Setup for search results list
        RecyclerView searchResultsRecyclerView = view.findViewById(R.id.search_results_recycler_view);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final SearchResultAdapter searchResultAdapter = new SearchResultAdapter(searchResult -> {
            Log.d(TAG, "Subscribing to: " + searchResult.title + " at " + searchResult.feedUrl);
            podcastViewModel.subscribe(searchResult.feedUrl);
            
            // Clear the search results and hide the keyboard
            podcastViewModel.clearSearchResults();
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getView() != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        });
        searchResultsRecyclerView.setAdapter(searchResultAdapter);

        podcastViewModel.getSearchResults().observe(getViewLifecycleOwner(), searchResults -> {
            if (searchResults != null) {
                searchResultsRecyclerView.setVisibility(View.VISIBLE);
                searchResultAdapter.submitList(searchResults);
            } else {
                searchResultsRecyclerView.setVisibility(View.GONE);
            }
        });

        // Setup for manual RSS subscription
        EditText rssUrlEditText = view.findViewById(R.id.rss_url_edit_text);
        Button subscribeButton = view.findViewById(R.id.subscribe_button);
        subscribeButton.setOnClickListener(v -> {
            String url = rssUrlEditText.getText().toString();
            if (!url.isEmpty()) {
                podcastViewModel.subscribe(url);
                rssUrlEditText.setText("");
            }
        });

        // Setup for search button
        EditText searchTermEditText = view.findViewById(R.id.search_term_edit_text);
        Button searchButton = view.findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> {
            String term = searchTermEditText.getText().toString();
            if (!term.isEmpty()) {
                podcastViewModel.searchPodcasts(term);
            }
        });
        
        // Setup for refresh button
        Button refreshButton = view.findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> podcastViewModel.refreshAllFeeds());

        return view;
    }
}

