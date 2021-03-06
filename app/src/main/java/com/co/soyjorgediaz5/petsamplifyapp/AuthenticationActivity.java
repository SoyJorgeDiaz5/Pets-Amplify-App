package com.co.soyjorgediaz5.petsamplifyapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.SignInUIOptions;
import com.amazonaws.mobile.client.UserStateDetails;

public class AuthenticationActivity extends AppCompatActivity {

    private final String TAG = AuthenticationActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);
        checkUserState();
    }

    private void checkUserState(){
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                Log.i(TAG, result.getUserState().toString());
                switch (result.getUserState()){
                    case SIGNED_IN:
                        Intent intent = new Intent(AuthenticationActivity.this, MainActivity.class);
                        startActivity(intent);
                        AuthenticationActivity.this.finish();
                        break;

                    case SIGNED_OUT:
                        Log.d(TAG, "CASE SIGNED_OUT");
                        showSignIn();
                        break;

                    case SIGNED_OUT_USER_POOLS_TOKENS_INVALID:
                        Log.i("userState", "need to login again");
                        break;
                    case SIGNED_OUT_FEDERATED_TOKENS_INVALID:
                        Log.i("userState", "user logged in via federation, but currently needs new tokens");
                        break;

                    default:
                        AWSMobileClient.getInstance().signOut();
                        showSignIn();
                        break;
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, e.toString());
            }
        });
    }

    private void showSignIn() {
        try {
            AWSMobileClient.getInstance().showSignIn(this,
                    SignInUIOptions.builder().nextActivity(MainActivity.class).build());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
