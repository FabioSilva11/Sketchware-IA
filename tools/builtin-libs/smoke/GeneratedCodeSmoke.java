package com.example.smoke;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerUtils;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Mirrors the code Sketchware's generators (Lx, Jx, ManageEvent, BlocksHandler, ExtraBlocks)
 * emit for each component, so tools/builtin-libs/verify_generated_code.py can check that it
 * still compiles against the bundled libraries.
 */
public class GeneratedCodeSmoke extends AppCompatActivity {
    private static final int REQ_CD_GOOGLE = 101;

    private FirebaseDatabase _firebase = FirebaseDatabase.getInstance();
    private FirebaseStorage _firebase_storage = FirebaseStorage.getInstance();
    private DatabaseReference db = _firebase.getReference("users");
    private ChildEventListener _db_child_listener;
    private StorageReference storage = _firebase_storage.getReference("files");
    private OnCompleteListener<Uri> _storage_upload_success_listener;
    private OnSuccessListener<FileDownloadTask.TaskSnapshot> _storage_download_success_listener;
    private OnSuccessListener _storage_delete_success_listener;
    private OnProgressListener _storage_upload_progress_listener;
    private OnProgressListener _storage_download_progress_listener;
    private OnFailureListener _storage_failure_listener;

    private FirebaseAuth auth;
    private OnCompleteListener<AuthResult> _auth_sign_in_listener;
    private OnCompleteListener<Void> auth_updateEmailListener;
    private OnCompleteListener<Void> auth_updatePasswordListener;
    private OnCompleteListener<Void> auth_emailVerificationSentListener;
    private OnCompleteListener<Void> auth_deleteUserListener;
    private OnCompleteListener<Void> auth_updateProfileListener;
    private OnCompleteListener<AuthResult> auth_phoneAuthListener;
    private OnCompleteListener<AuthResult> auth_googleSignInListener;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks phone;
    private PhoneAuthProvider.ForceResendingToken phone_resendToken;
    private GoogleSignInClient google;

    private OnCompleteListener<String> fcm_onCompleteListener;

    private InterstitialAd interstitial;
    private InterstitialAdLoadCallback _interstitial_interstitial_ad_load_callback;
    private FullScreenContentCallback _interstitial_full_screen_content_callback;
    private RewardedAd rewarded;
    private OnUserEarnedRewardListener _rewarded_on_user_earned_reward_listener;
    private RewardedAdLoadCallback _rewarded_rewarded_ad_load_callback;
    private String _ad_unit_id;
    private String _reward_ad_unit_id;

    private FusedLocationProviderClient location;
    private LocationRequest _location_location_request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(500L).build();
    private LocationCallback _location_location_callback;

    private WorkManager work;
    private BiometricPrompt biometric;
    private BiometricManager _biometric_manager;

    private AdView adview;
    private MapView mapview;
    private YouTubePlayerView youtube;
    private LottieAnimationView lottie;
    private CircleImageView avatar;
    private ImageView image;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private BottomNavigationView bottomnav;
    private FloatingActionButton fab;
    private TextInputLayout input;
    private SignInButton signin;

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        initialize(_savedInstanceState);
        FirebaseApp.initializeApp(this);
        MobileAds.initialize(this);
        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                .setTestDeviceIds(Arrays.asList("TEST")).build());
        initializeLogic();
    }

    private void initialize(Bundle _savedInstanceState) {
        auth = FirebaseAuth.getInstance();
        work = WorkManager.getInstance(getApplicationContext());
        _biometric_manager = BiometricManager.from(this);
        location = LocationServices.getFusedLocationProviderClient(this);

        _db_child_listener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot _param1, String _param2) {
                GenericTypeIndicator<HashMap<String, Object>> _ind = new GenericTypeIndicator<HashMap<String, Object>>() {
                };
                final HashMap<String, Object> _childValue = _param1.getValue(_ind);
            }

            @Override
            public void onChildChanged(DataSnapshot _param1, String _param2) {
            }

            @Override
            public void onChildMoved(DataSnapshot _param1, String _param2) {
            }

            @Override
            public void onChildRemoved(DataSnapshot _param1) {
            }

            @Override
            public void onCancelled(DatabaseError _param1) {
                final int _errorCode = _param1.getCode();
                final String _errorMessage = _param1.getMessage();
            }
        };
        db.addChildEventListener(_db_child_listener);
        db.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot _dataSnapshot) {
                for (DataSnapshot _data : _dataSnapshot.getChildren()) {
                    HashMap<String, Object> _map = _data.getValue(new GenericTypeIndicator<HashMap<String, Object>>() {
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError _databaseError) {
            }
        });

        _storage_upload_progress_listener = new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot _param1) {
                double _progressValue = (100.0 * _param1.getBytesTransferred()) / _param1.getTotalByteCount();
            }
        };
        _storage_download_progress_listener = new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onProgress(FileDownloadTask.TaskSnapshot _param1) {
                double _progressValue = (100.0 * _param1.getBytesTransferred()) / _param1.getTotalByteCount();
            }
        };
        _storage_upload_success_listener = new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(Task<Uri> _param1) {
                final String _downloadUrl = _param1.getResult().toString();
            }
        };
        _storage_download_success_listener = new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot _param1) {
                final long _totalByteCount = _param1.getTotalByteCount();
            }
        };
        _storage_delete_success_listener = new OnSuccessListener() {
            @Override
            public void onSuccess(Object _param1) {
            }
        };
        _storage_failure_listener = new OnFailureListener() {
            @Override
            public void onFailure(Exception _param1) {
                final String _message = _param1.getMessage();
            }
        };

        _auth_sign_in_listener = new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(Task<AuthResult> _param1) {
                final boolean _success = _param1.isSuccessful();
                final String _errorMessage = _param1.getException() != null ? _param1.getException().getMessage() : "";
            }
        };
        auth_updateEmailListener = new OnCompleteListener<Void>() {
            @Override
            public void onComplete(Task<Void> _param1) {
            }
        };
        auth_googleSignInListener = new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(Task<AuthResult> task) {
                final boolean _success = task.isSuccessful();
            }
        };
        phone = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential _credential) {
            }

            @Override
            public void onVerificationFailed(FirebaseException _e) {
            }

            @Override
            public void onCodeSent(String _verificationId, PhoneAuthProvider.ForceResendingToken _token) {
                phone_resendToken = _token;
            }
        };

        fcm_onCompleteListener = new OnCompleteListener<String>() {
            @Override
            public void onComplete(Task<String> task) {
                final boolean _success = task.isSuccessful();
                final String _token = task.isSuccessful() ? task.getResult() : "";
                final String _errorMessage = task.getException() != null ? task.getException().getMessage() : "";
            }
        };

        _interstitial_interstitial_ad_load_callback = new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd _param1) {
                interstitial = _param1;
                interstitial.setFullScreenContentCallback(_interstitial_full_screen_content_callback);
            }

            @Override
            public void onAdFailedToLoad(LoadAdError _param1) {
                interstitial = null;
                final int _errorCode = _param1.getCode();
                final String _errorMessage = _param1.getMessage();
            }
        };
        _interstitial_full_screen_content_callback = new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError _adError) {
            }

            @Override
            public void onAdShowedFullScreenContent() {
            }
        };
        _rewarded_rewarded_ad_load_callback = new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd _param1) {
                rewarded = _param1;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError _param1) {
                rewarded = null;
            }
        };
        _rewarded_on_user_earned_reward_listener = new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(RewardItem _param1) {
                final int _rewardAmount = _param1.getAmount();
                final String _rewardType = _param1.getType();
            }
        };

        _location_location_callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult _param1) {
                if (_param1 == null || _param1.getLastLocation() == null) return;
                Location _location = _param1.getLastLocation();
                final double _lat = _location.getLatitude();
            }
        };

        Executor _executor = ContextCompat.getMainExecutor(this);
        biometric = new BiometricPrompt(this, _executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult _result) {
            }

            @Override
            public void onAuthenticationError(int _errorCode, @NonNull CharSequence _errString) {
            }
        });

        adview.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdFailedToLoad(LoadAdError _param1) {
            }
        });

        bottomnav.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(android.view.MenuItem item) {
                return true;
            }
        });

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
            }
        });
    }

    private void initializeLogic() {
        // Firebase Auth
        auth.signInWithEmailAndPassword("a@b.c", "x").addOnCompleteListener(this, _auth_sign_in_listener);
        auth.createUserWithEmailAndPassword("a@b.c", "x").addOnCompleteListener(this, _auth_sign_in_listener);
        auth.sendPasswordResetEmail("a@b.c");
        auth.signInAnonymously().addOnCompleteListener(this, _auth_sign_in_listener);
        FirebaseUser _user = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseAuth.getInstance().signOut();
        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+5511999999999")
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(phone)
                .build());

        // Google sign-in through the Google login component (GoogleSignInClient)
        GoogleSignInOptions _gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("client-id").requestEmail().build();
        google = GoogleSignIn.getClient(this, _gso);
        startActivityForResult(google.getSignInIntent(), REQ_CD_GOOGLE);

        // Firebase Cloud Messaging
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(fcm_onCompleteListener);
        FirebaseMessaging.getInstance().subscribeToTopic("all");

        // Firebase Storage blocks (Fx)
        storage.child("a.png").putFile(Uri.fromFile(new File("/sdcard/a.png"))).addOnFailureListener(_storage_failure_listener).addOnProgressListener(_storage_upload_progress_listener).continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<UploadTask.TaskSnapshot> task) throws Exception {
                return storage.child("a.png").getDownloadUrl();
            }
        }).addOnCompleteListener(_storage_upload_success_listener);
        _firebase_storage.getReferenceFromUrl("gs://x/a.png").getFile(new File("/sdcard/a.png")).addOnSuccessListener(_storage_download_success_listener).addOnFailureListener(_storage_failure_listener).addOnProgressListener(_storage_download_progress_listener);
        _firebase_storage.getReferenceFromUrl("gs://x/a.png").delete().addOnSuccessListener(_storage_delete_success_listener).addOnFailureListener(_storage_failure_listener);

        // Firebase Database blocks
        HashMap<String, Object> _map = new HashMap<>();
        db.child(db.push().getKey()).updateChildren(_map);
        db.child("key").removeValue();

        // AdMob blocks
        {
            AdRequest adRequest = new AdRequest.Builder().build();
            InterstitialAd.load(GeneratedCodeSmoke.this, _ad_unit_id, adRequest, _interstitial_interstitial_ad_load_callback);
        }
        if (interstitial != null) {
            interstitial.show(GeneratedCodeSmoke.this);
        }
        RewardedAd.load(GeneratedCodeSmoke.this, _reward_ad_unit_id, new AdRequest.Builder().build(), _rewarded_rewarded_ad_load_callback);
        if (rewarded != null) {
            rewarded.show(GeneratedCodeSmoke.this, _rewarded_on_user_earned_reward_listener);
        }
        {
            int _adWidth = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
            adview.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, _adWidth));
            adview.loadAd(new AdRequest.Builder().build());
        }
        {
            ConsentRequestParameters _consentParams = new ConsentRequestParameters.Builder().build();
            ConsentInformation _consentInfo = UserMessagingPlatform.getConsentInformation(this);
            _consentInfo.requestConsentInfoUpdate(this, _consentParams, new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                @Override
                public void onConsentInfoUpdateSuccess() {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(GeneratedCodeSmoke.this, new ConsentForm.OnConsentFormDismissedListener() {
                        @Override
                        public void onConsentFormDismissed(FormError _formError) {
                        }
                    });
                }
            }, new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                @Override
                public void onConsentInfoUpdateFailure(FormError _requestError) {
                }
            });
        }

        // FusedLocationManager blocks
        _location_location_request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Math.max(1000L, (long) 2000)).setMinUpdateIntervalMillis(Math.max(500L, (long) 1000)).build();
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            location.requestLocationUpdates(_location_location_request, _location_location_callback, Looper.getMainLooper());
        }
        location.removeLocationUpdates(_location_location_callback);

        // WorkManager blocks
        try {
            Class<? extends ListenableWorker> _workerClass = (Class<? extends ListenableWorker>) Class.forName("x.Worker");
            OneTimeWorkRequest _workRequest = new OneTimeWorkRequest.Builder(_workerClass).setInitialDelay((long) 1, TimeUnit.MINUTES).addTag("t").build();
            work.enqueue(_workRequest);
            PeriodicWorkRequest _periodic = new PeriodicWorkRequest.Builder(_workerClass, Math.max(15L, (long) 15), TimeUnit.MINUTES).addTag("t").build();
            work.enqueue(_periodic);
        } catch (ClassNotFoundException _e) {
        }

        // BiometricManager blocks
        if (_biometric_manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            biometric.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("t").setSubtitle("s").setNegativeButtonText("c").build());
        }

        // YouTube player block
        youtube.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                String videoId = "id";
                youTubePlayer.cueVideo(videoId, 0);
            }
        });
        FullscreenListener _fullscreenListener = null;

        // Glide, Lottie, Gson
        Glide.with(getApplicationContext()).load(Uri.parse("https://x/y.png")).into(image);

        // Blocks Fx builds in Java
        String _email = (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getEmail() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "");
        String _uid = (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
        if (google != null) startActivityForResult(google.getSignInIntent(), REQ_CD_GOOGLE);
        lottie.setAnimation("a.json");
        lottie.playAnimation();
        ArrayList<HashMap<String, Object>> _list = new Gson().fromJson("[]", new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());
    }

    // Generated by Lx for FusedLocationManager components
    private boolean _location_location_updates_started;

    private void _location_start_location_updates() {
        if (location == null || _location_location_callback == null || _location_location_updates_started) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1000);
            return;
        }
        location.requestLocationUpdates(_location_location_request, _location_location_callback, Looper.getMainLooper());
        _location_location_updates_started = true;
    }

    private void _location_stop_location_updates() {
        if (location != null && _location_location_callback != null && _location_location_updates_started) {
            location.removeLocationUpdates(_location_location_callback);
            _location_location_updates_started = false;
        }
    }

    @Override
    protected void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
        super.onActivityResult(_requestCode, _resultCode, _data);
        switch (_requestCode) {
            case REQ_CD_GOOGLE:
                if (_resultCode == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> _task = GoogleSignIn.getSignedInAccountFromIntent(_data);
                    try {
                        GoogleSignInAccount _account = _task.getResult(ApiException.class);
                        AuthCredential _credential = GoogleAuthProvider.getCredential(_account.getIdToken(), null);
                        auth.signInWithCredential(_credential).addOnCompleteListener(auth_googleSignInListener);
                    } catch (ApiException _e) {
                    }
                }
                break;
            default:
                break;
        }
    }
}
