package net.fastfourier.plotbot.plotbot;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;


public class DrawingActivity extends Activity {
    public static final Bus drawBus = new Bus();

    private static final UUID BT_SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int ENABLE_BT_REQUEST = 99;


    private BluetoothAdapter bluetoothAdapter;

    private ProgressDialog dialog;
    private BluetoothDevice drawBot;
    private BluetoothSocket drawSock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new DrawingFragment())
                    .commit();
        }
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null){
            Log.e("DrawingActivity", "NO BLUETOOTH AVAILABLE");
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableIntent, ENABLE_BT_REQUEST);
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        drawBus.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        drawBus.unregister(this);
        disconnectBluetooth();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Subscribe
    public void startDraw(DrawData data){
        clearDialog();
        dialog = ProgressDialog.show(this, "Drawing...", "Please wait.", true, false);
        new DrawTask().execute(data);
    }

    private BluetoothSocket connectBluetooth() {
        if(drawSock != null && drawSock.isConnected()){
            return drawSock;
        }
        try {
            drawBot = bluetoothAdapter.getRemoteDevice("20:13:02:19:15:28");
            drawSock = drawBot.createRfcommSocketToServiceRecord(BT_SSP_UUID);
            drawSock.connect();
            return drawSock;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void disconnectBluetooth(){
        if(drawSock != null){
            try {
                drawSock.close();
                drawSock = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        drawBot = null;
    }

    private void clearDialog(){
        if(dialog != null){
            dialog.dismiss();
            dialog = null;
        }
    }

    public static class DrawData{
        public final Point[][] lines;

        public DrawData(Point[][] lineData) {
            this.lines = lineData;
        }
    }

    private class DrawTask extends AsyncTask<DrawData, Void, Boolean>{

        @Override
        protected Boolean doInBackground(DrawData... params) {
            BluetoothSocket drawSocket = connectBluetooth();
            if(drawSocket != null && drawSocket.isConnected()){
                try {
                    InputStream in = drawSocket.getInputStream();
                    OutputStream out = drawSocket.getOutputStream();
                    ByteBuffer outData = ByteBuffer.allocate(1024);
                    outData.rewind();

                    //generate draw commands
                    DrawLib.generateDrawScript(outData, params[0]);

                    //write to socket
                    out.write(outData.array(), 0, outData.position());
                    out.flush();
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            clearDialog();
            if(success){
                Toast.makeText(DrawingActivity.this, "SUCCESS!", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(DrawingActivity.this, "Failed!", Toast.LENGTH_LONG).show();
            }
        }
    }

    public static class DrawingFragment extends Fragment {
        private DrawView drawArea;

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            inflater.inflate(R.menu.drawing, menu);
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            super.onPrepareOptionsMenu(menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()){
                case R.id.action_clear:
                    drawArea.clearOverlay();
                    return true;
                case R.id.action_save:
                    drawBus.post(new DrawData(drawArea.getLineData()));
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            setHasOptionsMenu(true);
            View rootView = inflater.inflate(R.layout.fragment_drawing, container, false);
            drawArea = (DrawView) rootView.findViewById(R.id.draw_area);
            return rootView;
        }
    }
}
