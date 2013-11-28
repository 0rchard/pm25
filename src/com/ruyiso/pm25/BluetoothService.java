package com.ruyiso.pm25;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.app.Notification.Action;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public final class BluetoothService {
	private static final String TAG = "BluetoothService";
	private static final UUID uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	private static Context applicationContext;
	
	private static boolean initialized;
	
	private static final IntentFilter broadcastIntentFilter = new IntentFilter();
	
	private static BroadcastReceiver broadcastReceiver;
	
	private BluetoothService() {}
	
	private static BluetoothAdapter btAdapter;
	
	private static IBluetoothServiceEventReceiver eventReceiver;
	
	private final static Handler eventReceiverHandler = new Handler();
	
	private static BluetoothDevice connectedDevice;
	
	private static BluetoothSocket connectedSocket;
	
	private static BufferedOutputStream outputStream;
	
	private static BufferedInputStream inputStream;
	
	public static synchronized boolean initialize(Context applicationContext, IBluetoothServiceEventReceiver eventReceiver) {
		BluetoothService.eventReceiver = eventReceiver;
		
		if (initialized) return true;
		
		BluetoothService.applicationContext = applicationContext;
		
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (btAdapter == null) {
			Toast.makeText(applicationContext, R.string.no_bluetooth_modem, Toast.LENGTH_LONG).show();
			return false;
		}
		
		initialized = true;
		return true;
	}
	
	public static synchronized boolean bluetoothAvailable() {
		return btAdapter != null;
	}
	
	public static synchronized boolean bluetoothEnabled() {
		return btAdapter != null && btAdapter.isEnabled();
	}
	
	public static synchronized boolean requestEnableBluetooth(Activity activity) {
		if (bluetoothEnabled()) return false;
		
		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		activity.startActivityForResult(enableIntent, IntentRequestCodes.BT_REQUEST_ENABLE);
		return true;
	}
	
	public static synchronized void registerBroadcastReceiver(final Activity activity) {
		if (broadcastReceiver == null) {
			broadcastReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					int currentState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
					int lastState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
					
					Log.v(TAG, "Bluetooth state change received: " + lastState + " --> " + currentState);
					switch(currentState) {
					case BluetoothAdapter.STATE_TURNING_ON:
						onBluetoothEnabling();
						break;
					case BluetoothAdapter.STATE_ON:
						onBluetoothEnabled();
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						onBluetoothDisabling();
						break;
					case BluetoothAdapter.STATE_OFF:
						onBluetoothDisabled();
						break;
					}
					
				}				
			};
			
			broadcastIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		}
		
		activity.registerReceiver(broadcastReceiver, broadcastIntentFilter);
	}
	
	public static synchronized void unregisterBroadcastReceiver(Activity activity) {
		if (broadcastReceiver == null) return;
		
		activity.unregisterReceiver(broadcastReceiver);
	}
	
	private static synchronized void onBluetoothEnabling() {
		assert eventReceiver != null;
		
		eventReceiverHandler.post(new Runnable() {
			@Override
			public void run() {
				eventReceiver.bluetoothEnabling();
			}
		});
		
	}
	
	private static synchronized void onBluetoothDisabling() {
		assert eventReceiver != null;
		
		eventReceiverHandler.post(new Runnable() {
			@Override
			public void run() {
				eventReceiver.bluetoothDisabling();
			}
		});
	}
	
	private static synchronized void onBluetoothEnabled() {
		assert eventReceiver != null;
		
		eventReceiverHandler.post(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				eventReceiver.bluetoothEnabled();
			}
		});
	}
	
	private static synchronized void onBluetoothDisabled() {
		assert eventReceiver != null;
		
		eventReceiverHandler.post(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				eventReceiver.bluetoothDisabled();
			}
		});
	}
	
	public static synchronized void connectToDevice(final String macAddress) {
		assert eventReceiver != null;
		
		disconnect();
		
		BluetoothDevice device = btAdapter.getRemoteDevice(macAddress);
		Log.i(TAG, "Bluetooth-Slave: " + device.getName() + "; " + device.getAddress());
		connectedDevice = device;
		
		final String deviceName = device.getName();
		
		eventReceiverHandler.post(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				eventReceiver.connectedTo(deviceName == null? "unknowName" : deviceName, macAddress);
			}
		});
		
		try {
			BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuidSpp);
			connectedSocket = socket;
			if (socket == null) {
				Log.e(TAG, "unable to get bluetooth socket");
				return;
			}
			
			if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
			
			try {
				Log.i(TAG, "Connecting Socket to " + device.getName());
				socket.connect();
			} catch (IOException e) {
				Log.e(TAG, "connecting socket err", e);
				return;
			}
			
			try {
				InputStream realInputStream = socket.getInputStream();
				if (realInputStream == null) {
					Log.e(TAG, "socket getInputStream err");
					return;
				}
				inputStream = new BufferedInputStream(realInputStream);
			} catch (IOException e) {
				Log.e(TAG, "socket getInputStream exception");
				return;
			}
			
			try {
				OutputStream realOutputStream = socket.getOutputStream();
				if (realOutputStream == null) {
					Log.e(TAG, "socket getOutputStream err");
					return;
				}
				outputStream = new BufferedOutputStream(realOutputStream);
			} catch (IOException e) {
				Log.e(TAG, "socket getOutputStream exception");
				return;
			}
			
			// sync message sending
			sendSyncMessage();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			Log.e(TAG, "null reference pointer", e);
		}
	}
	
	public static synchronized void disconnect() {
		
		if (outputStream != null) {
			try {
				outputStream.flush();
				outputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				
			}
		}
		outputStream = null;
		
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				
			}
		}
		inputStream = null;
		
		if (connectedSocket != null) {
			try {
				connectedSocket.getOutputStream().close();
				connectedSocket.getInputStream().close();
				connectedSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				
			}
		}
		connectedSocket = null;
		
		connectedDevice = null;
	}
	
	private static synchronized void sendSyncMessage() {
		assert outputStream != null;
		
		String syncMessage = "SYNC from " + btAdapter.getName() + " " + btAdapter.getAddress() + "\r\n";
		try {
			outputStream.write(syncMessage.getBytes());
			outputStream.flush();
		} catch (IOException e) {
			Log.e(TAG, "sendSyncMessage err", e);
		}
	}
	
	public static synchronized boolean isConnected() {
		return connectedSocket != null && outputStream != null;
	}
	
	public static synchronized void sendToTarget(String message) {
		try {
			outputStream.write(message.getBytes());
			outputStream.write('\r');
			outputStream.write('\n');
			outputStream.flush();
		} catch (IOException e) {
			
		} catch (NullPointerException e) {
			
		}
	}
	
	public static synchronized String receiveFromTarget() {
		byte[] buffer = {0x01, 0x01, 0x02, (byte) 0xe4, 0x71, 0x2b, 0x5f, 0x30};
		String message;
		try {
			inputStream.read(buffer);
			message = buffer.toString();
		} catch (IOException e) {
			message = "error";
		} catch (NullPointerException e) {
			message = "error";
		}
		return message;
	}
}
