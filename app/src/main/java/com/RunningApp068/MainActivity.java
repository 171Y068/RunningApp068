package com.RunningApp068;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //LocationService.javaを呼び出す時に使う
    public LocationService locationService;

    //activity_main.xmlのMapView(map)を呼び出す時に使う
    private MapView mapView;

    //GoogleMapのビューに関する設定をする時に使う(?)
    private GoogleMap map;

    //地図にマーカー(現在地)を置く(https://developers.google.com/maps/documentation/android-sdk/marker)
    private Marker userPositionMarker;

    //地図上にサークルを描画し、それに関係する呼び出しをする時に使う
    private Circle locationAccuracyCircle;

    //GoogleMapライブラリ独自のBitMapクラス。マーカーのカスタマイズ(?)
    private BitmapDescriptor userPositionMarkerBitmapDescriptor;

    //線を描画する
    private Polyline runningPathPolyline;
    private PolylineOptions polylineOptions;

    //線のカスタマイズ
    private int polylineWidth = 30;

    //距離
    private List<LatLng> mRunList = new ArrayList<LatLng>();
    private double mMeter = 0.0;
    private double disMeter = 0.0;

    private double elapsedTime = 0.0;
    private double mSpeed = 0.0;

    //始点の緯度
    private double startLatitude;
    //始点の経度
    private double startLongitude;
    //終点の緯度
    private double endLatitude;
    //終点の経度
    private double endLongitude;
    //距離
    private double sumDistance;

    boolean isStarted = false;

    //距離のリセット用
    boolean starter = false;
    boolean first;

    Chronometer timer;

    private long startTime;

    private TextView timerText;

    private volatile boolean stopRun = false;

    private SimpleDateFormat dataFormat =
            new SimpleDateFormat("mm:ss.SS", Locale.JAPAN);

    //自動ズームの時に使うboolean変数
    boolean zoomable = true;

    //自動ズームを止めるタイマー
    Timer zoomBlockingTimer;

    //自動ズームの判定に使うboolean変数(true=自動ズーム,false=何もしない)
    boolean didInitialZoom;

    //ハンドラ変数 別スレッドからUI部品操作を用いる際に、よく使われる(http://d.hatena.ne.jp/sankumee/20120329/1333021847)
    private Handler handlerOnUIThread;

    //レシーバー
    private BroadcastReceiver locationUpdateReceiver;
    private BroadcastReceiver predictedLocationReceiver;

    //activity_main.xmlのボタン2つ(スタートボタン、ストップボタン)
    private ImageButton startButton;
    private ImageButton stopButton;

/*
    private LocationManager mLocationManager;
    private TextView LatT = (TextView)findViewById(R.id.LatiText);
    private TextView LngT = (TextView)findViewById(R.id.LongText);
    private TextView AltT = (TextView)findViewById(R.id.AltiText);
*/

    //Fillter
    private Circle predictionRange;
    BitmapDescriptor oldLocationMarkerBitmapDescriptor;
    BitmapDescriptor noAccuracyLocationMarkerBitmapDescriptor;
    BitmapDescriptor inaccurateLocationMarkerBitmapDescriptor;
    BitmapDescriptor kalmanNGLocationMarkerBitmapDescriptor;
    ArrayList<Marker> malMarkers = new ArrayList<>();
    final Handler handler = new Handler();


    @Override
    //onCreate Activityが生存している間ずっと、必要な処理の初期化を行う。初期化は全てここに書く。
    protected void onCreate(Bundle savedInstanceState) {
        //参考(https://qiita.com/yyyske/items/c6e342a9008bebef75bd)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timer = (Chronometer) findViewById(R.id.chronometer);


        //位置情報を管理している LocationManager のインスタンスを生成する
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        String locationProvider = null;

        // GPSが利用可能になっているかどうかをチェック
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationProvider = LocationManager.GPS_PROVIDER;
        }
        // GPSプロバイダーが有効になっていない場合は基地局情報が利用可能になっているかをチェック
        else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationProvider = LocationManager.NETWORK_PROVIDER;
        }
        // いずれも利用可能でない場合は、GPSを設定する画面に遷移する
        else {
            //アラートダイアログの表示
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.GpsCheck1);
            builder.setMessage(R.string.GpsCheck2);
            builder.setPositiveButton(R.string.GpsCheck3,new DialogInterface.OnClickListener(){
                //OKが押された時の処理
                @Override
                public void onClick(DialogInterface dialog, int which){
                    //設定の位置情報へ移動
                    Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    //新しいタスクとして開く
                    settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(settingsIntent);
                }
                //キャンセルが押された時(なにもしない)
            }).setNegativeButton(R.string.Cancel,null).show();
        }

        //Intentを使ってLocationServiceのインスタンスを作り実行
        final Intent locationService = new Intent(this.getApplication(), LocationService.class);
        this.getApplication().startService(locationService);
        this.getApplication().bindService(locationService, serviceConnection, Context.BIND_AUTO_CREATE);



        //MapViewのインスタンスを取得
        mapView = (MapView) this.findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);



        /* GoogleMapオブジェクトを取得(呼ぶと非同期的にOnMapReadyCallbackのonMapReadyというメソッドが呼ばれる) */
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                map = googleMap;

                //ズームコントロールを非表示
                map.getUiSettings().setZoomControlsEnabled(false);

                //自分の位置をMapに表示(これだと標準の青丸でアイコンのカスタマイズが不可)
                map.setMyLocationEnabled(false);

                //コンパスの機能をオン
                map.getUiSettings().setCompassEnabled(true);

                //現在地に飛ぶボタンを表示
                map.getUiSettings().setMyLocationButtonEnabled(true);

                /* マップの位置やズームレベルが変化した時に呼ばれるリスナー */
                map.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {

                    @Override
                    //マップのフォーカスが変わるたびにこのメソッドが呼ばれる
                    public void onCameraMoveStarted(int reason) {
                        /* reasonが下のif文の時はユーザーが触ったことによってマップが動いたということ */
                        if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
                            /*この時だけタイマーを作成して10秒間だけzoomableフラグをfalseにする */
                            //ログ出力
                            Log.d(TAG, "onCameraMoveStarted after user's zoom action");

                            zoomable = false;
                            if (zoomBlockingTimer != null) {
                                zoomBlockingTimer.cancel();
                            }

                            handlerOnUIThread = new Handler();

                            TimerTask task = new TimerTask() {
                                @Override
                                public void run() {
                                    handlerOnUIThread.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            zoomBlockingTimer = null;
                                            zoomable = true;

                                        }
                                    });
                                }
                            };
                            zoomBlockingTimer = new Timer();
                            zoomBlockingTimer.schedule(task, 10 * 1000);
                            //ログ出力(10秒後に自動ズームが有効になることを知らせる)
                            Log.d(TAG, "start blocking auto zoom for 10 seconds");
                        }
                    }
                });
            }
        });/* ----------  mapView.getMapAsync  ---------- */



        /* ブロードキャストを受け取る処理
            LocationServiceからの情報をMainActivity側でリアルタイムに受信できるようにする */
        //locationUpdateReceiver(BroadcastReceiverクラスのメンバー)の初期化
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            //onRecieveメソッドをオーバーライド
            public void onReceive(Context context, Intent intent) {
                //オブジェクトの一時的保管領域(http://s-takumi.hatenablog.com/entry/2014/06/01/173236)
                //newLocationの宣言
                Location newLocation = intent.getParcelableExtra("location");

                //メソッドdrawLocationAccuracyCircleを呼び出す
                //位置情報の精度を表す円をユーザーの周りに描画
                drawLocationAccuracyCircle(newLocation);

                //メソッドdrawUserPositionMarkerを呼び出す
                //ユーザーの位置を描画
                drawUserPositionMarker(newLocation);

                //LocationServiceのisLoggingがtrueの時(?)
                if (MainActivity.this.locationService.isLogging) {
                    //メソッドaddPolylineを呼び出す(移動経路を線として描画)
                    addPolyline();

                }
                //メソッドzoomMapToを呼び出す
                zoomMapTo(newLocation);

                setData(newLocation);

                getDistance();

                //メソッドdrawLocationsを呼び出す(フィルタの視覚化)
                drawMalLocations();

            }
        };/* ----------  locationUpdateReceiver  ---------- */


        predictedLocationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //オブジェクトの一時的保管領域(http://s-takumi.hatenablog.com/entry/2014/06/01/173236)
                Location predictedLocation = intent.getParcelableExtra("location");

                //メソッドdrawPredictionRangeを呼び出す
                drawPredictionRange(predictedLocation);

            }
        };/* ----------  predictedLocationReceiver  ---------- */


        LocalBroadcastManager.getInstance(this).registerReceiver(
                locationUpdateReceiver,
                new IntentFilter("LocationUpdated"));

        LocalBroadcastManager.getInstance(this).registerReceiver(
                predictedLocationReceiver,
                new IntentFilter("PredictLocation"));


        //スタートボタンの画像指定
        startButton = (ImageButton) this.findViewById(R.id.start_button);

        //ストップボタンの画像指定
        stopButton = (ImageButton) this.findViewById(R.id.stop_button);

        //(アプリ起動時に)ストップボタンの非表示
        stopButton.setVisibility(View.INVISIBLE);


        //スタートボタンがクリックされた時の処理
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.Start)
                        .setMessage(R.string.RunStart)
                        .setNegativeButton(R.string.Cancel, null)
                        .setPositiveButton(R.string.Start, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //スタートボタンの非表示
                                startButton.setVisibility(View.INVISIBLE);

                                //ストップボタンの表示
                                stopButton.setVisibility(View.VISIBLE);

                                //メソッドclearPolylineの呼び出し
                                clearPolyline();

                                //メソッドclearMalMarkersの呼び出し
                                clearMalMarkers();

                                //マーカーのクリア(テスト)
                                malMarkers.clear();

                                //オリジナル関数
                                mRunList.clear();
                                disMeter = 0.0;

                                starter = true;

                                isStarted = true;


                                timer.setBase(SystemClock.elapsedRealtime());
                                timer.start();

                                first = true;
                                //sumDistance();

                                //LocationServiceのstartLoggingの呼び出し
                                MainActivity.this.locationService.startLogging();
                            }
                        })
                        .show();

            }
        });


        //ストップボタンがクリックされた時の処理
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.End)
                        .setMessage(R.string.RunEnd)
                        //キャンセルを押した時(なにもしない)
                        .setNegativeButton(R.string.Cancel, null)
                        .setPositiveButton(R.string.End, new DialogInterface.OnClickListener() {
                            @Override
                            //OKを押した時
                            public void onClick(DialogInterface dialog, int which) {
                                //スタートボタンの表示
                                startButton.setVisibility(View.VISIBLE);

                                //ストップボタンの非表示
                                stopButton.setVisibility(View.INVISIBLE);

                                starter = false;
                                //LocationServiceのstopLoggingの呼び出し
                                MainActivity.this.locationService.stopLogging();

                                first = false;

                                timer.stop();

                                isStarted = false;
                            }
                        })
                        //表示
                        .show();



            }
        });



        oldLocationMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.old_location_marker);
        noAccuracyLocationMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.no_accuracy_location_marker);
        inaccurateLocationMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.inaccurate_location_marker);
        kalmanNGLocationMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.kalman_ng_location_marker);

    }/* ----------  onCreate()  ---------- */




    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // これは、サービスとの接続が確立されたときに呼び出され、
            // サービスと対話するために使用できるサービスオブジェクトを提供します。
            // 独自のプロセスで実行されていることがわかっている明示的なサービスにバインドしているため、
            // IBinderを具象クラスにキャストして直接アクセスできます。

            //String型変数 name の宣言(className 上)
            String name = className.getClassName();

            //endsWith(文字列中に検索文字列が後方にある)(https://akira-watson.com/android/string-class.html)
            if (name.endsWith("LocationService")) {
                //LocationServiceからLocationServiceBinderメソッドを受け取る
                locationService = ((LocationService.LocationServiceBinder) service).getService();

                //LocationServiceからstartUpdatingLocationメソッドを呼び出す
                locationService.startUpdatingLocation();

            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // これは、サービスとの接続が予期せず切断された場合、
            // つまりプロセスがクラッシュした場合に呼び出されます。
            // 同じプロセスで実行されているため、これは起こりません。
            if (className.getClassName().equals("LocationService")) {
                //LocationServiceからstopUpdatingLocationメソッドを呼び出す
                locationService.stopUpdatingLocation();

                //locationServiceをnullにする
                locationService = null;
            }
        }
    };

    /* MapViewのライフサイクルメソッドのオーバーライド */
    /* Activityライフサイクル(http://ougiitirou.web.fc2.com/AndroidMain/activitySycle.html) */
    @Override
    //onPause()
    //システムが別のアクティビティを開始しようとしているときに呼び出される。
    public void onPause() {
        super.onPause();

        if (this.mapView != null) {
            this.mapView.onPause();
        }
    }

    //onResume()
    //アクティビティがユーザーとの対話を開始する直前に呼び出される。
    @Override
    public void onResume() {
        super.onResume();


        if (this.mapView != null) {
            this.mapView.onResume();
        }

    }

    //onDestroy()
    //アクティビティが破棄される前に呼び出される。
    @Override
    public void onDestroy() {
        if (this.mapView != null) {
            this.mapView.onDestroy();
        }

        try {
            if (locationUpdateReceiver != null) {
                unregisterReceiver(locationUpdateReceiver);
            }

            if (predictedLocationReceiver != null) {
                unregisterReceiver(predictedLocationReceiver);
            }
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }

        super.onDestroy();

    }

    //onStart()
    //アクティビティがユーザーから見えるようになる直前に呼び出される。
    @Override
    public void onStart() {
        super.onStart();

        if (this.mapView != null) {
            this.mapView.onStart();
        }

    }

    //onStop()
    //アクティビティがユーザーから見えなくなったときに呼び出される。
    @Override
    public void onStop() {
        super.onStop();

        if (this.mapView != null) {
            this.mapView.onStop();
        }

    }

    //onLowMemory
    //使用できるメモリが少なくなった時に呼び出される。
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (this.mapView != null) {
            this.mapView.onLowMemory();
        }
    }

    //onSaveInstanceState()
    //参考(https://qiita.com/yyyske/items/c6e342a9008bebef75bd)
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (this.mapView != null) {
            this.mapView.onSaveInstanceState(outState);
        }
    }

    /* 数値データの取得、表示を行う */
    private void setData(Location location){
        //LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if(starter == true) {
            //距離
            sumDistance = 0.00;
            starter = false;
        }

            TextView LatText = (TextView)findViewById(R.id.LatiText);
            startLatitude = location.getLatitude();
            String str1 = "緯度：" + startLatitude;
            LatText.setText(str1);

            TextView LngText = (TextView)findViewById(R.id.LongText);
            startLongitude = location.getLongitude();
            String str2 = "経度：" + startLongitude;
            LngText.setText(str2);

            TextView AltText = (TextView)findViewById(R.id.AltiText);
            String str3 = "高度：" + location.getAltitude();
            AltText.setText(str3);

            TextView SpdText = (TextView)findViewById(R.id.SpedText);
            String str4 = "速度：" + location.getSpeed();
            SpdText.setText(str4);

    }





    /* 移動距離を出す */
    private void getDistance() {
        ArrayList<Location> locationList = locationService.locationList;

        if(first == true){
            endLatitude = startLatitude;
            endLongitude = startLongitude;

            first = false;

        }


        //結果を格納するための配列を生成
        float[] results = new float[3];
            //距離計算
            Location.distanceBetween(startLatitude, startLongitude,endLatitude,endLongitude,results);
            //結果をsumDistanceに追加
            sumDistance += results[0];
            //mをkmに変換
            double distance = sumDistance / 1000;
            TextView disText = (TextView)findViewById(R.id.disText);
            //結果を表示
            if(isStarted == true)
            disText.setText(String.format("%.2fkm", distance));

            //始点だったものを終点に
            endLatitude = startLatitude;
            endLongitude = startLongitude;

    }/*---------- getDistance ----------*/


    /* ユーザーをマップ上で自動追跡する機能をつけて常にユーザーがマップの中心にいるようにする */
    /* 位置情報が更新される度に呼ぶようにすることでマップが最新の位置(ユーザーの現在地)にアニメーション付きで移動＆ズームする */
    private void zoomMapTo(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        //アプリ起動後に一度でもユーザーの現在地にマップをズームさせたかのフラグ(didInitalZoom)をチェック
        if (this.didInitialZoom == false) {

            try {

                //まだ一度もチェックしていない場合はマップは世界全体を表示になるのでアニメーションなしで現在地へ移動(ズームレベル17.5)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.5f));

                //didInitialZoom をTrueにする
                this.didInitialZoom = true;

                return;

            } catch (Exception e) {
                //例外処理
                //参考(http://www.kab-studio.biz/Programing/JavaA2Z/Word/00000340.html)
                e.printStackTrace();
            }

            //Toast.makeText(this.getActivity(), "Inital zoom in process", Toast.LENGTH_LONG).show();
        }

        /* 初回以降の処理
        * ユーザーが触ってから10秒間はfalseになる。trueの場合だけ自動ズームする。
        * アニメーション中はzoomableフラグをfalseにしている　*/

        //フラグ(zoomable)を確認
        if (zoomable) {

            try {
                //zoomableをfalseにする
                zoomable = false;

                //現在地にマップをアニメーション付きで移動(ズームレベルは変更しない)
                map.animateCamera(CameraUpdateFactory.newLatLng(latLng),
                        new GoogleMap.CancelableCallback() {

                    //onFinish()
                    //アクティビティを終了させたい時に呼び出す。
                    @Override
                    public void onFinish() {
                        zoomable = true;
                    }

                    @Override
                    public void onCancel() {
                        zoomable = true;
                    }
                });
            } catch (Exception e) {
                //例外処理
                e.printStackTrace();
            }
        }
    }/* ----------  zoomMapTo()  ---------- */


    /* ユーザーの位置に赤い丸を表示 */
    private void drawUserPositionMarker(Location location){
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if(this.userPositionMarkerBitmapDescriptor == null){

            /* 赤い丸のイメージからBitmapDescriptorクラスのオブジェクトを生成し、これを使ってGoogleMapオブジェクトの
               addMarkerメソッドを呼び出してマーカーを地図上に表示させる */
            userPositionMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.user_position_point);
        }

        if (userPositionMarker == null) {

            //マーカー表示
            userPositionMarker = map.addMarker(new MarkerOptions()
                    .position(latLng)
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .icon(this.userPositionMarkerBitmapDescriptor));

        } else {
            //2回目以降この関数が呼ばれた場合はマーカーの位置をsetPositionメソッドを使って更新
            userPositionMarker.setPosition(latLng);
        }
    }/* ----------  drawUserPositionMarker  ---------- */


    /* 位置情報の精度を表す円をユーザーの周りに描画する */
    private void drawLocationAccuracyCircle(Location location){

        //Location.getAccuracy()  精度
        if(location.getAccuracy() < 0){
            return;
        }

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        //locationAccuracyCircleの中身がnullのとき
        if (this.locationAccuracyCircle == null) {

            //サークル表示
            this.locationAccuracyCircle = map.addCircle(new CircleOptions()
                    //現在地を指定
                    .center(latLng)
                    //サークルの色
                    // argb(透明度,赤,緑,青)
                    .fillColor(Color.argb(64, 175, 238, 238))
                    //サークル外周円
                    .strokeColor(Color.argb(64, 30, 144, 255))
                    //外周円Width(太さ)
                    .strokeWidth(2f)
                    //半径にaccuracy(精度の大きさをメートルで表示)を設定
                    .radius(location.getAccuracy()));

        } else {
            //サークルを中央に配置する
            this.locationAccuracyCircle.setCenter(latLng);
        }
    }/* ----------  drawLocationAccuracyCircle  ---------- */


    /* 移動経路の描画 LocationServiceがログした位置情報が全て格納されている。
         この中の最新の位置情報と一つ前の位置情報を取り出し、その2点の間に直線を書いていく */
    private void addPolyline() {
        ArrayList<Location> locationList = locationService.locationList;

        //runningPathPolylineという変数の中身が空の場合
        if (runningPathPolyline == null) {

            //ログした位置情報が1個より多い場合
            if (locationList.size() > 1){

                //fromLocation -2
                //toLocation -1
                Location fromLocation = locationList.get(locationList.size() - 2);
                Location toLocation = locationList.get(locationList.size() - 1);

                LatLng from = new LatLng(((fromLocation.getLatitude())),
                        ((fromLocation.getLongitude())));

                LatLng to = new LatLng(((toLocation.getLatitude())),
                        ((toLocation.getLongitude())));

                /* 新規にPolylineオブジェクトをMapに追加し、
                生成されたPolylineオブジェクトをrunningPathPolylineという変数に格納しておく */
                this.runningPathPolyline = map.addPolyline(new PolylineOptions()
                        .add(from, to)
                        .width(polylineWidth).color(Color.parseColor("#801B60FE")).geodesic(true));

            }
        }

        //runningPathPolylineという変数の中身が存在する場合
        else {

            /* 最初に描画した際に生成したPolylineオブジェクト(runningPathPolyline)に新たに現在の位置を追加する */
            Location toLocation = locationList.get(locationList.size() - 1);

            LatLng to = new LatLng(((toLocation.getLatitude())),
                    ((toLocation.getLongitude())));

            List<LatLng> points = runningPathPolyline.getPoints();
            points.add(to);

            runningPathPolyline.setPoints(points);

        }
    }/* ----------  addPolyline()  ---------- */

    /* 移動経路のクリア */
    private void clearPolyline() {
        if (runningPathPolyline != null) {
            runningPathPolyline.remove();
            runningPathPolyline = null;
        }
    }


    /* フィルタの視覚化 */
    private void drawMalLocations(){
        //メソッドdrawMalMarkersの呼び出し
        drawMalMarkers(locationService.oldLocationList, oldLocationMarkerBitmapDescriptor);
        drawMalMarkers(locationService.noAccuracyLocationList, noAccuracyLocationMarkerBitmapDescriptor);
        drawMalMarkers(locationService.inaccurateLocationList, inaccurateLocationMarkerBitmapDescriptor);
        drawMalMarkers(locationService.kalmanNGLocationList, kalmanNGLocationMarkerBitmapDescriptor);
    }

    private void drawMalMarkers(ArrayList<Location> locationList, BitmapDescriptor descriptor){
        for(Location location : locationList){
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            Marker marker = map.addMarker(new MarkerOptions()
                    .position(latLng)
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .icon(descriptor));

            malMarkers.add(marker);
        }
    }

    /* Circleカラー */
    private void drawPredictionRange(Location location){
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (this.predictionRange == null) {
            this.predictionRange = map.addCircle(new CircleOptions()
                    .center(latLng)
                    .fillColor(Color.argb(50, 30, 207, 0))
                    .strokeColor(Color.argb(128, 30, 207, 0))
                    .strokeWidth(1.0f)
                    .radius(30)); //30 meters of the prediction range
        } else {
            this.predictionRange.setCenter(latLng);
        }

        this.predictionRange.setVisible(true);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.predictionRange.setVisible(false);
            }
        }, 2000);
    }

    public void clearMalMarkers(){
        for (Marker marker : malMarkers){
            marker.remove();
        }
    }


}
