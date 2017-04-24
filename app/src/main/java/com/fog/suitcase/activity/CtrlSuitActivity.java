package com.fog.suitcase.activity;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.fog.suitcase.Beans.AppStateBean;
import com.fog.suitcase.R;
import com.fog.suitcase.helper.COMPARA;
import com.google.gson.Gson;
import com.junkchen.blelib.BleService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

/**
 * Created by SIN on 2017/4/13.
 */

public class CtrlSuitActivity extends AppCompatActivity {
    private String TAG = "---bleinfo---";
    public static final int SERVICE_BIND = 1;
    public static final int _UPDATETV = 2;

    // 更新经纬度
    public static final int _UP_LONG = 4;
    public static final int _UP_LATI = 8;
    public static final int _UP_SWITCH = 5;
    public static final int _UP_ALERT = 6;
    public static final int _UP_DEVID = 7;

    private Context context;

    private String mac;
    private BleService mBleService;

    private boolean mIsBind;//是否已经绑定

    private TextView ble_status;
    private ImageView arrow_back;
    //    private TextView update_loc;
    private TextView longitude_tvid;
    private TextView latitude_tvid;
    private TextView deviceid_tvid;
    private Switch lock_switch;
    private Switch lock_alert;

    /**
     * 蓝牙服务的列表
     */
    private List<String[]> characteristicList;
    // 服务列表
    private BluetoothGatt mGatt;

    // 各种服务的UUID
    private BluetoothGattCharacteristic bgc_longitude = null;
    private BluetoothGattCharacteristic bgc_latitude = null;
    private BluetoothGattCharacteristic bgc_switch = null;
    private BluetoothGattCharacteristic bgc_alert = null;
    private BluetoothGattCharacteristic bgc_devid = null;

    private String _LONGITUDE_TMP = "";
    private String _LATITUDE_TMP = "";
    private String _LATCH_SWITCH_TMP = "false";
    private String _CASE_LOST_TMP = "false";
    private String _DEVICE_TMP = "047863A00214";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_suit);

        context = CtrlSuitActivity.this;

        initView();

        Intent i = getIntent();
        mac = i.getStringExtra("mac");
        _DEVICE_TMP = mac.replace(":", "");

        // 绑定服务
        doBindService();
    }

    private void initView() {
//        update_loc = (TextView) findViewById(R.id.update_loc);
        ble_status = (TextView) findViewById(R.id.ble_status1);

        deviceid_tvid = (TextView) findViewById(R.id.deviceid_tvid);
        longitude_tvid = (TextView) findViewById(R.id.longitude_tvid);
        latitude_tvid = (TextView) findViewById(R.id.latitude_tvid);
        arrow_back = (ImageView) findViewById(R.id.arrow_back);
        lock_switch = (Switch) findViewById(R.id.lock_switch);
        lock_alert = (Switch) findViewById(R.id.lock_alert);

        characteristicList = new ArrayList<>();

//        update_loc.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (null != bgc_location)
//                    mGatt.readCharacteristic(bgc_location);
//            }
//        });

        arrow_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        lock_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                byte[] k;
                if (isChecked) {
                    k = new byte[]{0x01};
                } else {
                    k = new byte[]{0x00};
                }
                if (null != bgc_switch)
                    sendCommand(k, bgc_switch);
            }
        });

        lock_alert.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                byte[] k;
                if (isChecked) {
                    k = new byte[]{0x01};
                } else {
                    k = new byte[]{0x00};
                }
                if (null != bgc_alert)
                    sendCommand(k, bgc_alert);
            }
        });

        deviceid_tvid.setText(_DEVICE_TMP);
    }

    private void connectble() {
        if (null != mBleService)
            mBleService.connect(mac);
    }

    private void sendCommand(byte[] data, BluetoothGattCharacteristic characteristic) {
        //将指令放置进特征中
        characteristic.setValue(data);
        //设置回复形式
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        //开始写数据
        mGatt.writeCharacteristic(characteristic);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            if (mBleService != null) mHandler.sendEmptyMessage(SERVICE_BIND);
            if (mBleService.initialize()) {
                if (mBleService.enableBluetooth(true)) {
                    connectble();
                }
            } else {
                Toast.makeText(context, "Not support Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService = null;
            mIsBind = false;
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SERVICE_BIND:
                    ble_status.setText(getStatus(SERVICE_BIND));
                    setBleServiceListener();
                    break;
                case _UPDATETV:
                    ble_status.setText(getStatus(Integer.parseInt(msg.obj.toString())));
                    if (msg.obj.toString().equals("0"))
                        connectble();
                    break;
                case _UP_LONG:
                    longitude_tvid.setText(msg.obj.toString());
                    break;
                case _UP_LATI:
                    latitude_tvid.setText(msg.obj.toString());
                    break;
            }
        }
    };

    /**
     * 获取显示的状态
     *
     * @param code
     * @return
     */
    private int getStatus(int code) {
        int status = R.string.connecting;
        switch (code) {
            case STATE_DISCONNECTED:
                status = R.string.tryagain;
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        ble_status.setText(R.string.connecting);
                    }
                }, 2000);
                break;
            case STATE_CONNECTED:
                status = R.string.state_connected;
                break;
            case STATE_CONNECTING:
                status = R.string.connecting;
                break;
        }
        return status;
    }

    private void setBleServiceListener() {
        mBleService.setOnServicesDiscoveredListener(new BleService.OnServicesDiscoveredListener() {
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    mGatt = gatt;
                    for (BluetoothGattService service : gatt.getServices()) {

                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        for (int i = 0; i < characteristics.size(); i++) {

                            String charUuid = characteristics.get(i).getUuid().toString();

                            switch (charUuid) {
                                case COMPARA._LONGITUDE:
                                    bgc_longitude = characteristics.get(i);
                                    readCharacter();
                                    break;
                                case COMPARA._LATITUDE:
                                    bgc_latitude = characteristics.get(i);
                                    readCharacter();
                                    break;
                                case COMPARA._SWITCH:
                                    bgc_switch = characteristics.get(i);
                                    break;
                                case COMPARA._ALERT:
                                    bgc_alert = characteristics.get(i);
                                    break;
                                case COMPARA._DEVID:
                                    bgc_devid = characteristics.get(i);
                                    break;
                            }
                        }
                    }
                }
            }
        });

        /**
         * Ble连接回调
         * newstate: 0 disconnect; 2 connected
         */
        mBleService.setOnConnectListener(new BleService.OnConnectionStateChangeListener() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Message msg = new Message();
                msg.what = _UPDATETV;
                msg.obj = newState;

                mHandler.sendMessage(msg);
            }
        });

        mBleService.setOnReadRemoteRssiListener(new BleService.OnReadRemoteRssiListener() {
            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
//                Log.i(TAG, "onReadRemoteRssi: rssi = " + rssi);
            }
        });

        mBleService.setOnDataAvailableListener(new BleService.OnDataAvailableListener() {

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                try {
                    String res = new String(characteristic.getValue(), "UTF-8");

                    Log.d(TAG, "onCharacteristicRead ---> " + res);

                    switch (characteristic.getUuid().toString()) {
//                        case COMPARA._LONGITUDE:
//                            send2Handler(_UP_LOCATION, res);
//                            break;
                        case COMPARA._SWITCH:
                            send2Handler(_UP_SWITCH, res);
                            break;
                        case COMPARA._ALERT:
                            send2Handler(_UP_ALERT, res);
                            break;
                        case COMPARA._DEVID:
                            send2Handler(_UP_DEVID, res);
                            break;
                    }

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                try {
                    Log.d(TAG, "onCharacteristicChanged ---> " + characteristic.getUuid().toString().equals(COMPARA._LONGITUDE) + " === ");
                    switch (characteristic.getUuid().toString()) {
                        case COMPARA._LONGITUDE:
                            _LONGITUDE_TMP = new String(characteristic.getValue(), "UTF-8");
                            send2Handler(_UP_LONG, _LONGITUDE_TMP);
                            break;
                        case COMPARA._LATITUDE:
                            _LATITUDE_TMP = new String(characteristic.getValue(), "UTF-8");
                            send2Handler(_UP_LATI, _LATITUDE_TMP);
                            break;
                        case COMPARA._SWITCH:
                            _LATCH_SWITCH_TMP = new String(characteristic.getValue(), "UTF-8");
                            break;
                        case COMPARA._DEVID:
                            _DEVICE_TMP = new String(characteristic.getValue(), "UTF-8");
                            break;
                    }
                    upData();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor characteristic, int status) {
                try {
                    Log.d(TAG, "onDescriptorRead ---> " + new String(characteristic.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });

        mBleService.setOnReadRemoteRssiListener(new BleService.OnReadRemoteRssiListener() {
            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                Log.d(TAG, "onReadRemoteRssi ---> " + status);
            }
        });
    }

    /**
     * 开启订阅和改变的监听
     */
    private void readCharacter() {
        if (null != bgc_longitude) {
            mGatt.readCharacteristic(bgc_longitude);
            mGatt.setCharacteristicNotification(bgc_longitude, true);
        }
        if (null != bgc_latitude) {
            mGatt.readCharacteristic(bgc_latitude);
            mGatt.setCharacteristicNotification(bgc_latitude, true);
        }
        if (null != bgc_switch) {
            mGatt.readCharacteristic(bgc_switch);
            mGatt.setCharacteristicNotification(bgc_switch, true);
        }
        if (null != bgc_alert) {
            mGatt.readCharacteristic(bgc_alert);
            mGatt.setCharacteristicNotification(bgc_alert, true);
        }
    }

    private void upData() {
        //创建okHttpClient对象
        OkHttpClient mOkHttpClient = new OkHttpClient();

        FormBody body = new FormBody.Builder()
                .add("device", _DEVICE_TMP)
                .add("longitude", _LONGITUDE_TMP)
                .add("latitude", _LATITUDE_TMP)
                .add("latch_switch", _LATCH_SWITCH_TMP)
                .add("case_lost", _CASE_LOST_TMP)
                .build();

        //创建一个Request
        Request request = new Request.Builder()
                .url(COMPARA._HOST)
                .post(body)
                .build();

        //new call
        Call call = mOkHttpClient.newCall(request);

        //请求加入调度
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String htmlStr = response.body().string();
                Log.d(TAG, "gson -- htmlStr -- " + htmlStr);

                if (htmlStr.indexOf("code") > -1) {
                    if (0 == getResCode(htmlStr)) {
                        Log.d(TAG, "gson -- success");
                    }
                }
            }
        });
    }

    private int getResCode(String message) {

        Gson gson = new Gson();
        AppStateBean asb = null;
        try {
            asb = gson.fromJson(message, AppStateBean.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        Log.d(TAG, asb.getMeta().getMessage());
//        Log.d(TAG, asb.getMeta().getCode() +"");
//        Log.d(TAG, asb.getData().getDevice());

        return null != asb ? 1 : asb.getMeta().getCode();
    }

    /**
     * 通知handler来更新页面
     *
     * @param tag
     * @param message
     */
    private void send2Handler(int tag, String message) {
        Message msg = new Message();
        msg.what = tag;
        msg.obj = message;
        mHandler.sendMessage(msg);
    }

    /**
     * 绑定服务
     */
    private void doBindService() {
        Intent serviceIntent = new Intent(this, BleService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        mIsBind = true;
    }

    /**
     * 解绑服务
     */
    private void doUnBindService() {
        if (mIsBind) {
            unbindService(serviceConnection);
            mBleService.disconnect();

            mBleService = null;
            mIsBind = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnBindService();
    }
}
