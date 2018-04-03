package info.plux.pluxapi.sampleapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.view.View;
import android.widget.*;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;

import info.plux.pluxapi.Communication;
import info.plux.pluxapi.bitalino.*;
import info.plux.pluxapi.bitalino.bth.OnBITalinoDataAvailable;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Calendar;
import java.util.Locale;

import static android.provider.Telephony.Carriers.PORT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static info.plux.pluxapi.Constants.*;

public class DeviceActivity extends FragmentActivity implements OnBITalinoDataAvailable, View.OnClickListener {
    private final String TAG = this.getClass().getSimpleName();

    public final static String EXTRA_DEVICE = "info.plux.pluxapi.sampleapp.DeviceActivity.EXTRA_DEVICE";
    public final static String FRAME = "info.plux.pluxapi.sampleapp.DeviceActivity.Frame";

    private BluetoothDevice bluetoothDevice;

    private BITalinoCommunication bitalino;
    private boolean isBITalino2 = false;


    private Handler handler;

    private States currentState = States.DISCONNECTED;

    private boolean isUpdateReceiverRegistered = false;

    /*
     * UI elements
     */
    private TextView nameTextView;
    private TextView addressTextView;
    private TextView elapsedTextView;
    private TextView stateTextView;

    private Button connectButton;
    private Button disconnectButton;
    private Button startButton;
    private Button stopButton;
    private Button configurationButton;

    private LinearLayout bitalinoLinearLayout;
//    private Button stateButton;
//    private RadioButton digital1RadioButton;
//    private RadioButton digital2RadioButton;
//    private RadioButton digital3RadioButton;
//    private RadioButton digital4RadioButton;
//    private Button triggerButton;

    private Button ipAdressButton;
    private Button port_numberButton;
    private Button test_connectionButton;
    private EditText ipAdressText;
    private EditText port_numberText;
    private SeekBar batteryThresholdSeekBar;
    private Button batteryThresholdButton;
    private SeekBar pwmSeekBar;
    private Button pwmButton;
    private TextView resultsTextView;
    private RadioButton radio10Button;
    private RadioButton radio100Button;
    private int sampleRate;

    private boolean isDigital1RadioButtonChecked = false;
    private boolean isDigital2RadioButtonChecked = false;

    private float alpha = 0.25f;

    //MEU CODIGO
    private List saidaTexto;
    private String ipString;
    private String portString;
    private InetAddress serverAddr;
    private Socket socket;
    private PrintWriter outStream;
    private SimpleDateFormat sdf;
    private Calendar cal;
    private boolean connectPressed;
    private boolean startPressed;

    private EditText playerText;
    private String player;

    private EditText sessionText;
    private String sessionString;

    private String debugStartButton = "startButton";
    private String debugStopButton = "stopButton";
    private CheckBox casualButton;
    private LineChart chart;

    /*
     * Test with 2 device
     */
//    private BITalinoCommunication bitalino2;
//    private String identifierBITalino2 = "20:16:07:18:15:94";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getIntent().hasExtra(EXTRA_DEVICE)){
            bluetoothDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);
        }

        setContentView(R.layout.activity_main);
        ipString = "10.111.111.105";
        portString = "65001";
        connectPressed = false;
        startPressed = false;
        initView();
        setUIElements();
        radio100Button.setChecked(true);
        radio10Button.setChecked(false);
        saidaTexto = new LinkedList();

        ArrayList<Entry> yValues = new ArrayList<>();

        handler = new Handler(getMainLooper()){
          @Override
          public void handleMessage(Message msg) {
              Bundle bundle = msg.getData();
              BITalinoFrame frame = bundle.getParcelable(FRAME);

              Log.d(TAG, frame.toString());

              if(frame != null){ //BITalino
                  resultsTextView.setText(frame.toString());
                  //MEU CODIGO
                  cal = Calendar.getInstance();
                  sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
                  saidaTexto.add(frame.toString() + "\n");
              }
          }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(updateReceiver, makeUpdateIntentFilter());
        isUpdateReceiverRegistered = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(isUpdateReceiverRegistered) {
            unregisterReceiver(updateReceiver);
            isUpdateReceiverRegistered = false;
        }

        if(bitalino != null){
            bitalino.closeReceivers();
            try {
                bitalino.disconnect();
            } catch (BITalinoException e) {
                e.printStackTrace();
            }
        }

//        if(bitalino2 != null){
//            bitalino2.closeReceivers();
//            try {
//                bitalino2.disconnect();
//            } catch (BITalinoException e) {
//                e.printStackTrace();
//            }
//        }
    }

    /*
     * UI elements
     */
    private void initView(){
        nameTextView = (TextView) findViewById(R.id.device_name_text_view);
        addressTextView = (TextView) findViewById(R.id.mac_address_text_view);
        elapsedTextView = (TextView) findViewById(R.id.elapsed_time_Text_view);
        stateTextView = (TextView) findViewById(R.id.state_text_view);

        connectButton = (Button) findViewById(R.id.connect_button);
        disconnectButton = (Button) findViewById(R.id.disconnect_button);
        startButton = (Button) findViewById(R.id.start_button);
        stopButton = (Button) findViewById(R.id.stop_button);
        configurationButton = (Button) findViewById(R.id.configurations);

        //bitalino ui elements
        //bitalinoLinearLayout = (LinearLayout) findViewById(R.id.bitalino_linear_layout);
//        stateButton = (Button) findViewById(R.id.state_button);
//        digital1RadioButton = (RadioButton) findViewById(R.id.digital_1_radio_button);
//        digital2RadioButton = (RadioButton) findViewById(R.id.digital_2_radio_button);
//        digital3RadioButton = (RadioButton) findViewById(R.id.digital_3_radio_button);
//        digital4RadioButton = (RadioButton) findViewById(R.id.digital_4_radio_button);
//        triggerButton = (Button) findViewById(R.id.trigger_button);

        //Botões de set
        ipAdressButton = (Button) findViewById(R.id.set_ip_button);
        port_numberButton = (Button) findViewById(R.id.set_port_button);
        test_connectionButton = (Button) findViewById(R.id.test_connection);
        radio10Button = (RadioButton) findViewById(R.id.radio10);
        radio100Button = (RadioButton) findViewById(R.id.radio100);

        //Caixas de texto
        ipAdressText = (EditText) findViewById(R.id.ip_text);
        ipAdressText.setText(ipString);
        ipAdressText.setHint(ipString);
        port_numberText = (EditText) findViewById(R.id.port_text);
        port_numberText.setHint(portString);
        playerText = (EditText) findViewById(R.id.playerNumber);
        sessionText = (EditText) findViewById(R.id.sessionNumber);
        playerText.setFocusableInTouchMode(true);
        //batteryThresholdSeekBar = (SeekBar) findViewById(R.id.battery_threshold_seek_bar);
        //batteryThresholdButton = (Button) findViewById(R.id.battery_threshold_button);
        //pwmSeekBar = (SeekBar) findViewById(R.id.pwm_seek_bar);
        //pwmButton = (Button) findViewById(R.id.pwm_button);
        resultsTextView = (TextView) findViewById(R.id.results_text_view);
        casualButton = (CheckBox) findViewById(R.id.casual);
        chart = (LineChart) findViewById(R.id.chart);
    }

    private void setUIElements(){
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);

        if(bluetoothDevice.getName() == null){
            nameTextView.setText("BITalino");
        }
        else {
            nameTextView.setText(bluetoothDevice.getName());
        }
        addressTextView.setText(bluetoothDevice.getAddress());
        stateTextView.setText(currentState.name());

        Communication communication = Communication.getById(bluetoothDevice.getType());
        Log.d(TAG, "Communication: " + communication.name());
        if(communication.equals(Communication.DUAL)){
            communication = Communication.BLE;
        }

        bitalino = new BITalinoCommunicationFactory().getCommunication(communication,this, this);

//        bitalino2 = new BITalinoCommunicationFactory().getCommunication(communication,this, this);

        connectButton.setOnClickListener(this);
        disconnectButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        configurationButton.setOnClickListener(this);
//        stateButton.setOnClickListener(this);
//        digital1RadioButton.setOnClickListener(this);
//        digital2RadioButton.setOnClickListener(this);
//        digital3RadioButton.setOnClickListener(this);
//        digital4RadioButton.setOnClickListener(this);
//        triggerButton.setOnClickListener(this);

        ipAdressButton.setOnClickListener(this);
        port_numberButton.setOnClickListener(this);
        test_connectionButton.setOnClickListener(this);
        radio10Button.setOnClickListener(this);
        radio100Button.setOnClickListener(this);
        casualButton.setOnClickListener(this);
        /*não estão em uso*/
        //batteryThresholdButton.setOnClickListener(this);
        //pwmButton.setOnClickListener(this);
    }

    /*
     * Local Broadcast
     */
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(ACTION_STATE_CHANGED.equals(action)){
                String identifier = intent.getStringExtra(IDENTIFIER);
                States state = States.getStates(intent.getIntExtra(EXTRA_STATE_CHANGED, 0));

                Log.i(TAG, identifier + " -> " + state.name());

                stateTextView.setText(state.name());

                switch (state){
                    case NO_CONNECTION:
                        break;
                    case LISTEN:
                        break;
                    case CONNECTING:
                        break;
                    case CONNECTED:
                        Log.d(debugStartButton, "State changed: CONNECTED");
                        connectPressed = true;
                        playerText.setFocusableInTouchMode(true);
                        sessionText.setFocusableInTouchMode(true);
                        disconnectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                        startButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                        test_connectionButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                        disconnectButton.setVisibility(View.VISIBLE);
                        startButton.setVisibility(View.VISIBLE);

                        stopButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        configurationButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        configurationButton.setVisibility(View.GONE);
                        connectButton.setVisibility(View.GONE);
                        stopButton.setVisibility(View.GONE);
                        resultsTextView.setVisibility(View.GONE);

                        break;
                    case ACQUISITION_TRYING:
                        break;
                    case ACQUISITION_OK:
                        Log.d(debugStartButton, "State changed: ACQUISITION_OK");
                        chart.setVisibility(View.VISIBLE);
                        startPressed = true;
                        playerText.setFocusableInTouchMode(false);
                        sessionText.setFocusableInTouchMode(false);


                        test_connectionButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        disconnectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        startButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        test_connectionButton.setVisibility(View.GONE);
                        disconnectButton.setVisibility(View.GONE);
                        startButton.setVisibility(View.GONE);

                        stopButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                        stopButton.setVisibility(View.VISIBLE);
                        break;
                    case ACQUISITION_STOPPING:
                        break;
                    case DISCONNECTED:
                        configurationButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                        configurationButton.setVisibility(View.VISIBLE);
                        connectButton.setVisibility(View.VISIBLE);
                        test_connectionButton.setVisibility(View.VISIBLE);

                        disconnectButton.setVisibility(View.GONE);
                        startButton.setVisibility(View.GONE);
                        chart.setVisibility(View.GONE);
                        break;
                    case ENDED:
                        break;

                }
            }
            else if(ACTION_DATA_AVAILABLE.equals(action)){
                Log.d(debugStartButton, "Data Available");
                if(intent.hasExtra(EXTRA_DATA)){
                    Parcelable parcelable = intent.getParcelableExtra(EXTRA_DATA);
                    if(parcelable.getClass().equals(BITalinoFrame.class)){ //BITalino
                        String rawData = parcelable.toString();
                        Log.d(debugStartButton, rawData);
                        String aux = rawData;
                        String dataVector = aux.substring( aux.indexOf('[')+1 , aux.indexOf(']') );
                        if(rawData.contains("-1") || rawData.contains("0, 0, 0, 0, 0, 0")){
                            Log.d(debugStartButton, "Erro no recebimento de dados!");
                            if(!resultsTextView.getText().toString().contains("error")) {
                                String message = "Ackisition error. Trying to solve...";
                                resultsTextView.setText(message);
                            }
                            try {
                                bitalino.stop();
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }

                            try {
                                bitalino.start(new int[]{0, 1, 2, 3, 4, 5}, sampleRate);
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }
                        }else {
                            String output = rawData.substring(rawData.indexOf(' ') + 1);
                            resultsTextView.setText(output);
                            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
                            String ready = format.format(Calendar.getInstance().getTime()) + "," + dataVector;
                            saidaTexto.add(ready);

                            if (socket != null && socket.isConnected()) {
                                //This will send current time to server
                                Log.d(debugStartButton, ready);
                                outStream.println(ready);
                            }
                        }
                    }
                }
            }
            else if(ACTION_COMMAND_REPLY.equals(action)){
                //String identifier = intent.getStringExtra(IDENTIFIER);
                Log.d(debugStartButton, "Command Reply");
                if(intent.hasExtra(EXTRA_COMMAND_REPLY) && (intent.getParcelableExtra(EXTRA_COMMAND_REPLY) != null)){
                    Parcelable parcelable = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);
                    if(parcelable.getClass().equals(BITalinoState.class)){ //BITalino
                        Log.d(debugStartButton, parcelable.toString());
                        String aux = parcelable.toString();
                        String dataVector = aux.substring( aux.indexOf('[')+1 , aux.indexOf(']') );
                        if(dataVector.equals("0, 0, 0, 0, 0, 0")){
                            Log.d(debugStartButton, "Erro no recebimento de dados!");
                        }else {
                            resultsTextView.setText(parcelable.toString());
                            //MEU CODIGO

                            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

                            String ready = format.format(Calendar.getInstance().getTime()) + "," + dataVector;
                            saidaTexto.add(ready);

                            if (socket != null && socket.isConnected()) {
                                //This will send current time to server
                                outStream.println(ready);
                            }
                        }

                    }
                    else if(parcelable.getClass().equals(BITalinoDescription.class)){ //BITalino
                        isBITalino2 = ((BITalinoDescription)parcelable).isBITalino2();
                        resultsTextView.setText("isBITalino2: " + isBITalino2 + "; FwVersion: " + String.valueOf(((BITalinoDescription)parcelable).getFwVersion()));
                        //MEU CODIGO
                        saidaTexto.add(new String("isBITalino2: " + isBITalino2 + "; FwVersion: " + String.valueOf(((BITalinoDescription)parcelable).getFwVersion()) + "\n"));
                        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
                        String strAux = format.format(Calendar.getInstance().getTime());
                        saidaTexto.add("Celphone_Time: " + strAux );


//                        if(identifier.equals(identifierBITalino2) && bitalino2 != null){
//                            try {
//                                bitalino2.start(new int[]{0,1,2,3,4,5}, 1);
//                            } catch (BITalinoException e) {
//                                e.printStackTrace();
//                            }
//                        }
                    }
                }
            }
        }
    };

    private IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_STATE_CHANGED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ACTION_EVENT_AVAILABLE);
        intentFilter.addAction(ACTION_DEVICE_READY);
        intentFilter.addAction(ACTION_COMMAND_REPLY);
        return intentFilter;
    }

    /*
     * Callbacks
     */

    @Override
    public void onBITalinoDataAvailable(BITalinoFrame bitalinoFrame) {
        Message message = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, bitalinoFrame);
        message.setData(bundle);
        handler.sendMessage(message);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 5) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Create the file.
                String path = Environment.getExternalStorageDirectory().getPath();
                Toast.makeText(getBaseContext(), "Arquivo salvo em: " + path + "/bitalinoOutput.txt", Toast.LENGTH_SHORT).show();
                //File file = new File("/sdcard/bitalinoOutput.txt");
                File file = new File(path + "/bitalinoOutput.txt");

                // Save your stream, don't forget to flush() it before closing it.
                try
                {
                    file.createNewFile();
                    FileOutputStream fOut = new FileOutputStream(file);
                    OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                    for(int i = 0; i < saidaTexto.size();) {
                        myOutWriter.append(saidaTexto.get(0).toString());
                        saidaTexto.remove(0);
                    }

                    myOutWriter.close();
                    fOut.close();
                    Toast.makeText(getBaseContext(), "Terminou de escrever!", Toast.LENGTH_SHORT).show();
                }
                catch (IOException e) {
                    Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("Exception", "File write failed: " + e.toString());
                }
            }
        }
    }

            @Override
            public void onClick(View view) {
                switch (view.getId()){
                    case R.id.casual:
                        break;
                    case R.id.connect_button:
                        if(!connectPressed) {
                            //Conecta bluetooth
                            try {
                                bitalino.connect(bluetoothDevice.getAddress());
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }
                            connectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                            //stopButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                            //conecta com pc via wireless
                            if (socket == null) {
                                new Thread() {
                                    public void run() {
                                        try {
                                            socket = new Socket(ipString, Integer.parseInt(portString));
                                            outStream = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                                            outStream.println("Connect");//sdf.format(Calendar.getInstance().getTime()));//This will send “Hi” to server
                                        } catch (java.net.UnknownHostException e) {
                                            Toast.makeText(getBaseContext(), "Erro em InetAdress() " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        } catch (java.io.IOException e) {
                                            Toast.makeText(getBaseContext(), "Erro em Socket(): " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }.start();
                            } else {
                                outStream.println("Connect");
                            }
                        }


//                try {
//                    bitalino2.connect(identifierBITalino2);
//                } catch (BITalinoException e) {
//                    e.printStackTrace();
//                }
                        break;
                    case R.id.disconnect_button:
                        if(connectPressed && !startPressed) {
                            connectPressed = false;
                            try {
                                bitalino.disconnect();
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }
                            if (socket != null && socket.isConnected()) {
                                try {
                                    Toast.makeText(getBaseContext(), "Desconectado do pc.", Toast.LENGTH_SHORT).show();
                                    socket.close();
                                    socket = null;
                                    outStream = null;
                                } catch (IOException e) {
                                    Toast.makeText(getBaseContext(), "Erro ao fechar Socket(): " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                            connectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                            disconnectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                            startButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));

                            //stopButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        }


//                try {
//                    bitalino2.disconnect();
//                } catch (BITalinoException e) {
//                    e.printStackTrace();
//                }
                        break;
                    case R.id.start_button:
                        Log.d(debugStartButton, "pressed");
                        if(connectPressed && !startPressed) {
                            Log.d("Start button", "connected pressed");
                            try {
                                player = playerText.getText().toString();
                                sessionString = sessionText.getText().toString();
                                Log.d(debugStartButton, "trying");
                                if (socket != null && socket.isConnected()) {
                                    Log.d(debugStartButton, "socket connected");

                                    //This will send current time to server
                                    outStream.println("Start:"+player+":"+sessionString+":"+(casualButton.isChecked()?1:0));
                                    outStream.println("Celphone_Time: " +
                                            Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" +
                                            Calendar.getInstance().get(Calendar.MINUTE) + ":" +
                                            Calendar.getInstance().get(Calendar.SECOND) + "." +
                                            Calendar.getInstance().get(Calendar.MILLISECOND));
                                }
                                if(radio100Button.isChecked()) {
                                    Log.d(debugStartButton, "100Hz");
                                    sampleRate = 100;
                                    bitalino.start(new int[]{0, 1, 2, 3, 4, 5}, sampleRate);
                                    Log.d(debugStartButton, "sampled");
                                }else if(radio10Button.isChecked()){
                                    Log.d(debugStartButton, "10Hz");
                                    sampleRate = 10;
                                    bitalino.start(new int[]{0, 1, 2, 3, 4, 5}, sampleRate);
                                    Log.d(debugStartButton, "sampled");
                                }
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }
                            //connectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        }
                        break;
                    case R.id.stop_button:
                        Log.d(debugStopButton, "stop pressed");
                        if(connectPressed && startPressed) {

                            int num = Integer.parseInt(sessionString.substring( sessionString.indexOf('_')+1 ));
                            String result = "";
                            if(num < 4) {
                                num += 1;
                                result = sessionString.substring(0, sessionString.indexOf('_') + 1) + num;
                                sessionText.setText(result);
                            }else {
                                result = sessionString.substring(0, sessionString.indexOf('_') + 1) + 1;
                                sessionText.setText(result);
                                num = Integer.parseInt(player.substring(player.indexOf('_') + 1));
                                num += 1;
                                result = player.substring(0, player.indexOf('_') + 1) + num;
                                playerText.setText(result);
                            }
                            startPressed = false;
                            try {
                                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                //MEU CODIGo

                                AlertDialog alert = builder.create();
                                String permission = "android.permission-group.STORAGE";
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);

                                //JA ESTAVA
                                alert.show();
                                bitalino.stop();
                            } catch (BITalinoException e) {
                                e.printStackTrace();
                            }
                            if (socket != null && socket.isConnected()) {
                                //This will send current time to server
                                outStream.println("Finished");
                            }
                            //connectButton.setTextColor(ResourcesCompat.getColor(getResources(), R.color.buttonUnactive, null));
                        }
                        break;
                    case R.id.configurations:
                        if(!startPressed && !connectPressed) {
                            if (configurationButton.getText().toString().contains("Open")) {
                                configurationButton.setText("Close Configurations");
                                radio10Button.setVisibility(VISIBLE);
                                radio100Button.setVisibility(VISIBLE);
                                ipAdressText.setVisibility(VISIBLE);
                                ipAdressButton.setVisibility(VISIBLE);
                                port_numberText.setVisibility(VISIBLE);
                                port_numberButton.setVisibility(VISIBLE);

                                connectButton.setVisibility(GONE);
                                disconnectButton.setVisibility(GONE);
                                startButton.setVisibility(GONE);
                                stopButton.setVisibility(GONE);
                            } else if (configurationButton.getText().toString().contains("Close")) {
                                configurationButton.setText("Open Configurations");
                                radio10Button.setVisibility(GONE);
                                radio100Button.setVisibility(GONE);
                                ipAdressText.setVisibility(GONE);
                                ipAdressButton.setVisibility(GONE);
                                port_numberText.setVisibility(GONE);
                                port_numberButton.setVisibility(GONE);

                                connectButton.setVisibility(VISIBLE);
                                //disconnectButton.setVisibility(VISIBLE);
                                //startButton.setVisibility(VISIBLE);
                                //stopButton.setVisibility(VISIBLE);
                            }
                        }
                        break;
                    case R.id.radio10:
                        radio100Button.setChecked(false);
                        radio10Button.setChecked(true);
                        break;
                    case R.id.radio100:
                        radio10Button.setChecked(false);
                        radio100Button.setChecked(true);
                        break;
//                    case R.id.state_button:
//                        try {
//                            bitalino.state();
//                        } catch (BITalinoException e) {
//                            e.printStackTrace();
//                        }
//                        break;
//                    case R.id.trigger_button:
//                        int[] digitalChannels;
//                        if(isBITalino2){
//                            digitalChannels = new int[2];
//                        }
//                        else{
//                            digitalChannels = new int[4];
//                        }
//
//                        digitalChannels[0] = (digital1RadioButton.isChecked()) ? 1 : 0;
//                        digitalChannels[1] = (digital2RadioButton.isChecked()) ? 1 : 0;
//
//                        if(!isBITalino2){
//                            digitalChannels[2] = (digital3RadioButton.isChecked()) ? 1 : 0;
//                            digitalChannels[3] = (digital4RadioButton.isChecked()) ? 1 : 0;
//                        }
//
//                        try {
//                            bitalino.trigger(digitalChannels);
//                        } catch (BITalinoException e) {
//                            e.printStackTrace();
//                        }
//                        break;
//                    case R.id.digital_1_radio_button:
//                        if(isDigital1RadioButtonChecked){
//                            digital1RadioButton.setChecked(false);
//                        }
//                        else{
//                            digital1RadioButton.setChecked(true);
//                        }
//                        isDigital1RadioButtonChecked = digital1RadioButton.isChecked();
//                        break;
//                    case R.id.digital_2_radio_button:
//                        if(isDigital2RadioButtonChecked){
//                            digital2RadioButton.setChecked(false);
//                        }
//                        else{
//                            digital2RadioButton.setChecked(true);
//                        }
//                        isDigital2RadioButtonChecked = digital2RadioButton.isChecked();
//                        break;
//                    case R.id.digital_3_radio_button:
//                        if(digital3RadioButton.isChecked()){
//                            digital3RadioButton.setChecked(false);
//                        }
//                        else{
//                            digital3RadioButton.setChecked(true);
//                        }
//                        break;
//                    case R.id.digital_4_radio_button:
//                        if(digital4RadioButton.isChecked()){
//                            digital4RadioButton.setChecked(false);
//                        }
//                        else{
//                            digital4RadioButton.setChecked(true);
//                        }
//                        break;
                    case R.id.ip_text:
                        ipAdressText.setCursorVisible(true);
                        break;
                    case R.id.port_text:
                        port_numberText.setCursorVisible(true);
                        break;
                    case R.id.set_ip_button:
                        ipString = ipAdressText.getText().toString();
                        Toast.makeText(getBaseContext(), "IP: " + ipString , Toast.LENGTH_SHORT).show();
                        /*try {
                            bitalino.battery(batteryThresholdSeekBar.getProgress());
                        } catch (BITalinoException e) {
                            e.printStackTrace();
                        }*/
                        break;
                    case R.id.set_port_button:
                        portString = port_numberText.getText().toString();
                        Toast.makeText(getBaseContext(), "PORT: " + portString , Toast.LENGTH_SHORT).show();

                        /*try {
                            bitalino.pwm(pwmSeekBar.getProgress());
                        } catch (BITalinoException e) {
                            e.printStackTrace();
                        }*/
                        break;
                    case R.id.test_connection:
                        if(!startPressed) {
                            Toast.makeText(getBaseContext(), "Testando conexão com " + ipString + " " + portString, Toast.LENGTH_SHORT).show();
                            if (socket == null) {
                                new Thread() {
                                    public void run() {
                                        try {
                                            socket = new Socket(ipString, Integer.parseInt(portString));
                                        } catch (UnknownHostException e) {
                                            Toast.makeText(getBaseContext(), "Erro de conexao: verifique o IP e a porta " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        } catch (java.io.IOException e) {
                                            Toast.makeText(getBaseContext(), "Erro de conexao: verifique o IP e a porta " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                        try {
                                            outStream = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                                            outStream.println("Hello pc!");//sdf.format(Calendar.getInstance().getTime()));//This will send “Hi” to server
                                        } catch (java.io.IOException e) {
                                            socket = null;
                                            outStream = null;
                                            Toast.makeText(getBaseContext(), "Erro de conexao: verifique o IP e a porta " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }.start();
                            } else {
                                try {
                                    outStream.println("Hello pc!");
                                } catch (Exception e) {
                                    Toast.makeText(getBaseContext(), "Desconectado do servidor", Toast.LENGTH_SHORT);
                                    socket = null;
                                    outStream = null;
                                }
                            }
                        }
                }
            }

    public class MyClientTask extends AsyncTask<Void, Void, Void> {
        String dstAddress;
        int dstPort;
        String response;

        MyClientTask(String addr, int port) {
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                socket = new Socket(dstAddress, dstPort);
                InputStream inputStream = socket.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];

                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }

                socket.close();
                //response = byteArrayOutputStream.toString("UTF-8");
                response = "Oi, eu sou Goku!";


            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            //textResponse.setText(response);
            outStream.println(response);
            super.onPostExecute(result);
        }
    }

}
