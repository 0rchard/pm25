package com.ruyiso.pm25;

import java.text.DecimalFormat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener, IBluetoothServiceEventReceiver{
	private static final DecimalFormat df = new DecimalFormat("0.00");
	
	private SensorManager sensorManager;
	
	private TextView pm25_textView;
	private TextView pm10_textView;
	
	float lastPm25 = 0;
	float lastPm10 = 0;
	
	private PowerManager.WakeLock wakeLock;
	
	private Button button_inquiry;
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
		
		pm25_textView = (TextView) findViewById(R.id.textViewPm25);
		pm10_textView = (TextView) findViewById(R.id.textViewPm10);
		
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "do_not_turn_off");
		
		BluetoothService.initialize(getApplicationContext(), this);
		
		button_inquiry = (Button) findViewById(R.id.button_inquiry);
		button_inquiry.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				inquiryPM25();
			}
		});
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if (!BluetoothService.requestEnableBluetooth(this)) {
			bluetoothEnabled();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		wakeLock.acquire();
		BluetoothService.registerBroadcastReceiver(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		wakeLock.release();
		BluetoothService.unregisterBroadcastReceiver(this);
		BluetoothService.disconnect();
	}
	
	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		return;
	}
	
	@Override
	public void bluetoothEnabling() {
		((TextView) findViewById(R.id.textViewState)).setText(R.string.value_enabling);
	}
	
	@Override
	public void bluetoothEnabled() {
		Toast.makeText(this, R.string.bluetooth_enabled, Toast.LENGTH_SHORT).show();
		
		((TextView) findViewById(R.id.textViewState)).setText(R.string.value_enabled);
		
		startSearchDeviceIntent();
	}
	
	public void inquiryPM25() {
		if (BluetoothService.isConnected()) {
			BluetoothService.sendToTarget("t");
			String message = BluetoothService.receiveFromTarget();
			pm25_textView.setText(message);
			BluetoothService.sendToTarget("ok");
		}
		else
		{
			pm25_textView.setText(R.string.value_na);
		}
	}
	
	private void startSearchDeviceIntent() {
		Intent serverIntent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(serverIntent, IntentRequestCodes.BT_SELECT_DEVICE);
	}

	@Override
	public void bluetoothDisabling() {
		// TODO Auto-generated method stub
		((TextView) findViewById(R.id.textViewState)).setText(R.string.value_disabling);
	}

	@Override
	public void bluetoothDisabled() {
		// TODO Auto-generated method stub
		Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
		
		((TextView) findViewById(R.id.textViewState)).setText(R.string.value_disabled);
		((TextView) findViewById(R.id.textViewTarget)).setText(R.string.value_na);
		
		((TextView) findViewById(R.id.textViewPm10)).setText(R.string.value_na);
		((TextView) findViewById(R.id.textViewPm25)).setText(R.string.value_na);
	}

	@Override
	public void connectedTo(String name, String address) {
		// TODO Auto-generated method stub
		((TextView) findViewById(R.id.textViewTarget)).setText(name + " (" + address + ")");
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case IntentRequestCodes.BT_REQUEST_ENABLE: {
			if (BluetoothService.bluetoothEnabled()) {
				bluetoothEnabled();
			}
			break;
		}
		case IntentRequestCodes.BT_SELECT_DEVICE: {
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(DeviceListActivity.EXTRAC_DEVICE_ADDRESS);
				BluetoothService.connectToDevice(address);
			}
		}
		default: {
			super.onActivityResult(requestCode, resultCode, data);
			break;
		}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (BluetoothService.bluetoothAvailable()) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.option_menu, menu);
			return true;
		}
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.scan:
			if (!BluetoothService.bluetoothEnabled()) {
				BluetoothService.requestEnableBluetooth(this);
				return true;
			}
			startSearchDeviceIntent();
			return true;
		}
		return false;
	}
}
