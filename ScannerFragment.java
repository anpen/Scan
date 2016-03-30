package com.frank.ble.scanner;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import com.frank.ble.communication.BluetoothCrashResolver;
import com.frankace.smartpen.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

public class ScannerFragment extends DialogFragment {
	private final static String TAG = "ScannerFragment";

	private final static String PARAM_UUID = "param_uuid";
	private final static long SCAN_DURATION = 1 * 10000;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeScanner mBluetoothLeScanner;
	private OnDeviceSelectedListener mListener;
	private DeviceListAdapter mAdapter;
	private final Handler mHandler = new Handler();
	private Button mScanButton;
	
	private BluetoothCrashResolver bluetoothCrashResolver;
	private Activity mActivity;
	
	private ParcelUuid mUuid;

	private boolean mIsScanning = false;
	
	public static ScannerFragment getInstance(final UUID uuid) {
		final ScannerFragment fragment = new ScannerFragment();

		final Bundle args = new Bundle();
		if (uuid != null)
			args.putParcelable(PARAM_UUID, new ParcelUuid(uuid));
		fragment.setArguments(args);
		return fragment;
	}
	
	/**
	 * Interface required to be implemented by activity.
	 */
	public static interface OnDeviceSelectedListener {
		/**
		 * Fired when user selected the device.
		 * 
		 * @param device
		 *            the device to connect to
		 * @param name
		 *            the device name. Unfortunately on some devices {@link BluetoothDevice#getName()} always returns <code>null</code>, f.e. Sony Xperia Z1 (C6903) with Android 4.3. The name has to
		 *            be parsed manually form the Advertisement packet.
		 */
		public void onDeviceSelected(final BluetoothDevice device, final String name);

		/**
		 * Fired when scanner dialog has been cancelled without selecting a device.
		 */
		public void onDialogCanceled();
	}
	
	/**
	 * This will make sure that {@link OnDeviceSelectedListener} interface is implemented by activity.
	 */
	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		mActivity = activity;
		try {
			this.mListener = (OnDeviceSelectedListener) activity;
		} catch (final ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement OnDeviceSelectedListener");
		}
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Bundle args = getArguments();
		if (args.containsKey(PARAM_UUID)) {
			mUuid = args.getParcelable(PARAM_UUID);
		}

		final BluetoothManager manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = manager.getAdapter();
		mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		
		bluetoothCrashResolver = new BluetoothCrashResolver(mActivity.getApplicationContext()); 
		bluetoothCrashResolver.start();
	}
	
	@Override
	public void onDestroyView() {
		stopScan();
		super.onDestroyView();
	}
	
	   @Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			final View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_device_selection, null);
			final ListView listview = (ListView) dialogView.findViewById(android.R.id.list);
			listview.setEmptyView(dialogView.findViewById(android.R.id.empty));
			listview.setAdapter(mAdapter = new DeviceListAdapter(getActivity()));

			builder.setTitle(R.string.scanner_title);
			final AlertDialog dialog = builder.setView(dialogView).create();
			listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
					stopScan();
					dialog.dismiss();
					final MyScanResult d = (MyScanResult) mAdapter.getItem(position);
					mListener.onDeviceSelected(d.getDevice(), d.getDevice().getName());
				}
			});

			mScanButton = (Button) dialogView.findViewById(R.id.action_cancel);
			mScanButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (v.getId() == R.id.action_cancel) {
						if (mIsScanning) {
							dialog.cancel();
						} else {
							startScan();
						}
					}
				}
			});
			
//			addBondedDevices();
			if (savedInstanceState == null)
				startScan();
			return dialog;
		}

		@Override
		public void onCancel(DialogInterface dialog) {
			super.onCancel(dialog);

			mListener.onDialogCanceled();
		}
		
		/**
		 * Scan for 5 seconds and then stop scanning when a BluetoothLE device is found then mLEScanCallback is activated This will perform regular scan for custom BLE Service UUID and then filter out.
		 * using class ScannerServiceParser
		 */
		private void startScan() {
			mAdapter.clearDevices();
			mScanButton.setText(R.string.scanner_action_cancel);
//			mBluetoothAdapter.startLeScan(mLeScanCallback);
			ScanFilter mScanFilterTest = new ScanFilter.Builder().build();
			ArrayList<ScanFilter> mScanFilter = new ArrayList<ScanFilter>();
			mScanFilter.add(mScanFilterTest);
			ScanSettings mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).setReportDelay(0).build();
			mBluetoothLeScanner.startScan(mScanFilter, mScanSettings, mScanCallBack);
			mIsScanning = true;
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mIsScanning) {
						stopScan();
					}
				}
			}, SCAN_DURATION);
		}

		/**
		 * Stop scan if user tap Cancel button
		 */
		private void stopScan() {
			if (mIsScanning) {
				mScanButton.setText(R.string.scanner_action_scan);
//				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				mBluetoothLeScanner.stopScan(mScanCallBack);
				mIsScanning = false;
			}
		}
		
		private ScanCallback mScanCallBack = new ScanCallback() {
			public void onScanResult(int callbackType, ScanResult result) {
				mAdapter.update(new MyScanResult(result.getDevice(), result.getRssi(), null));
				bluetoothCrashResolver.notifyScannedDevice(result.getDevice(), mLeScanCallback);
			};
		};
		
		// Device scan callback.
	    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
	        @Override
	        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
	        	
	        	mHandler.post(new Runnable(){

					@Override
					public void run() {
						// TODO Auto-generated method stub
						mAdapter.update(new MyScanResult(device, rssi, scanRecord));
						bluetoothCrashResolver.notifyScannedDevice(device, mLeScanCallback);
					}
	        		
	        	});
	        }
	    };

		private void addBondedDevices() {
			final Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
			mAdapter.addBondedDevices(devices);
		}
}
