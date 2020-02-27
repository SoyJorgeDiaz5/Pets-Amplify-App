package com.co.soyjorgediaz5.petsamplifyapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.amplify.generated.graphql.ListPetsQuery;
import com.amazonaws.amplify.generated.graphql.OnCreatePetSubscription;
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import javax.annotation.Nonnull;

public class MainActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private AdapterPets mAdapterPets;

    private ArrayList<ListPetsQuery.Item> mPets;
    private final String TAG = MainActivity.class.getSimpleName();

    private AppSyncSubscriptionCall subscriptionWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecyclerView = findViewById(R.id.recycler_view);

        // use a linear layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        //specify and adapter
        mAdapterPets = new AdapterPets(this);
        mRecyclerView.setAdapter(mAdapterPets);

        ClientFactory.init(this);

        FloatingActionButton fab = findViewById(R.id.btn_addPet);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent addPetIntent = new Intent(MainActivity.this, AddPetActivity.class);
                MainActivity.this.startActivity(addPetIntent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Query list data when we return to the screen
        query();
        subscribe();
    }

    @Override
    protected void onStop() {
        super.onStop();

        subscriptionWatcher.cancel();
    }

    private void query() {
        ClientFactory.appSyncClient().query(ListPetsQuery.builder().build())
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueue(queryCallback);
    }

    private GraphQLCall.Callback<ListPetsQuery.Data> queryCallback = new GraphQLCall.Callback<ListPetsQuery.Data>() {
        @Override
        public void onResponse(@Nonnull Response<ListPetsQuery.Data> response) {
            mPets = new ArrayList<>(response.data().listPets().items());
            Log.i(TAG, "Retrieved list items" + mPets.toString());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapterPets.setItems(mPets);
                    mAdapterPets.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, e.toString());
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void subscribe(){
        OnCreatePetSubscription subscription = OnCreatePetSubscription.builder().build();
        subscriptionWatcher = ClientFactory.appSyncClient().subscribe(subscription);
        subscriptionWatcher.execute(subCallback);
    }

    private AppSyncSubscriptionCall.Callback subCallback = new AppSyncSubscriptionCall.Callback() {
        @Override
        public void onResponse(@Nonnull Response response) {
            Log.i("Response", "Received subscription notification: " + response.data().toString());

            // Update UI with the newly added item
            OnCreatePetSubscription.OnCreatePet data = (
                    (OnCreatePetSubscription.Data) response.data()).onCreatePet();
            final ListPetsQuery.Item addedItem = new ListPetsQuery.Item(
                    data.__typename(), data.id(), data.name(), data.description());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mPets.add(addedItem);
                    mAdapterPets.notifyItemInserted(mPets.size()-1);
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
        }

        @Override
        public void onCompleted() {
            Log.i("Completed", "Subscription completed");
        }
    };
}
