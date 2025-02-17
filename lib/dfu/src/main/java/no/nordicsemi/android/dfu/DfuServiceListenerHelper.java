/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.dfu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import no.nordicsemi.android.dfu.internal.scanner.BootloaderScannerFactory;
import no.nordicsemi.android.error.GattError;

/**
 * A helper class that should be used to register listeners for DFU Service broadcast events.
 * The {@link DfuProgressListener} should be registered to listen for DFU status updates and errors,
 * while the {@link DfuLogListener} listener receives the log updates.
 * Listeners may be registered for a specified device (given with device address) or for any device.
 * Keep in mind, that while updating the SoftDevice using the buttonless update the device may
 * change its address in the bootloader mode.
 * <p>
 * Use {@link #registerProgressListener(Context, DfuProgressListener)} or
 * {@link #registerLogListener(Context, DfuLogListener)} to register your listeners.
 * Remember about unregistering them when your context is destroyed.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DfuServiceListenerHelper {
	private static LogBroadcastReceiver mLogBroadcastReceiver;
	private static ProgressBroadcastsReceiver mProgressBroadcastReceiver;

	private static class LogBroadcastReceiver extends BroadcastReceiver {
		private final Map<String, DfuLogListener> mListeners = new HashMap<>();
		private DfuLogListener mGlobalLogListener;

		private void setLogListener(final DfuLogListener globalLogListener) {
			this.mGlobalLogListener = globalLogListener;
		}

		private void setLogListener(final String deviceAddress, final DfuLogListener listener) {
			// When using the buttonless update and updating the SoftDevice the application will
			// be removed to make space for the new SoftDevice.
			// The new bootloader will afterwards advertise with the address incremented by 1.
			// We need to make sure that the listener will receive also events from this device.
			mListeners.put(deviceAddress, listener);
			mListeners.put(BootloaderScannerFactory.getIncrementedAddress(deviceAddress), listener); // assuming the address is a valid BLE address
		}

		private boolean removeLogListener(final DfuLogListener listener) {
			if (mGlobalLogListener == listener)
				mGlobalLogListener = null;

			// We do it 2 times as the listener was added for 2 addresses
			for (final Map.Entry<String, DfuLogListener> entry : mListeners.entrySet()) {
				if (entry.getValue() == listener) {
					mListeners.remove(entry.getKey());
					break;
				}
			}
			for (final Map.Entry<String, DfuLogListener> entry : mListeners.entrySet()) {
				if (entry.getValue() == listener) {
					mListeners.remove(entry.getKey());
					break;
				}
			}

			return mGlobalLogListener == null && mListeners.isEmpty();
		}

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String address = intent.getStringExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS);

			// Find proper listeners
			final DfuLogListener globalListener = mGlobalLogListener;
			final DfuLogListener deviceListener = mListeners.get(address);

			if (globalListener == null && deviceListener == null)
				return;

			final int level = intent.getIntExtra(DfuBaseService.EXTRA_LOG_LEVEL, 0);
			final String message = intent.getStringExtra(DfuBaseService.EXTRA_LOG_MESSAGE);

			if (globalListener != null)
				globalListener.onLogEvent(address, level, message);
			if (deviceListener != null)
				deviceListener.onLogEvent(address, level, message);
		}
	}

	private static class ProgressBroadcastsReceiver extends BroadcastReceiver {
		private final Map<String, DfuProgressListener> mListeners = new HashMap<>();
		private DfuProgressListener mGlobalProgressListener;

		private void setProgressListener(final DfuProgressListener globalProgressListener) {
			this.mGlobalProgressListener = globalProgressListener;
		}

		private void setProgressListener(final String deviceAddress, final DfuProgressListener listener) {
			// When using the buttonless update and updating the SoftDevice the application will be removed to make space for the new SoftDevice.
			// The new bootloader will afterwards advertise with the address incremented by 1. We need to make sure that the listener will receive also events from this device.
			mListeners.put(deviceAddress, listener);
			mListeners.put(BootloaderScannerFactory.getIncrementedAddress(deviceAddress), listener); // assuming the address is a valid BLE address
		}

		private boolean removeProgressListener(final DfuProgressListener listener) {
			if (mGlobalProgressListener == listener)
				mGlobalProgressListener = null;

			// We do it 2 times as the listener was added for 2 addresses
			for (final Map.Entry<String, DfuProgressListener> entry : mListeners.entrySet()) {
				if (entry.getValue() == listener) {
					mListeners.remove(entry.getKey());
					break;
				}
			}
			for (final Map.Entry<String, DfuProgressListener> entry : mListeners.entrySet()) {
				if (entry.getValue() == listener) {
					mListeners.remove(entry.getKey());
					break;
				}
			}

			return mGlobalProgressListener == null && mListeners.isEmpty();
		}

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String address = intent.getStringExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS);
			if (address == null)
				return;

			// Find proper listeners
			final DfuProgressListener globalListener = mGlobalProgressListener;
			final DfuProgressListener deviceListener = mListeners.get(address);

			if (globalListener == null && deviceListener == null)
				return;

			final String action = intent.getAction();
			if (action == null)
				return;

			switch (action) {
				case DfuBaseService.BROADCAST_PROGRESS -> {
					final int progress = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
					final float speed = intent.getFloatExtra(DfuBaseService.EXTRA_SPEED_B_PER_MS, 0.0f);
					final float avgSpeed = intent.getFloatExtra(DfuBaseService.EXTRA_AVG_SPEED_B_PER_MS, 0.0f);
					final int currentPart = intent.getIntExtra(DfuBaseService.EXTRA_PART_CURRENT, 0);
					final int partsTotal = intent.getIntExtra(DfuBaseService.EXTRA_PARTS_TOTAL, 0);

					switch (progress) {
						case DfuBaseService.PROGRESS_CONNECTING -> {
							if (globalListener != null)
								globalListener.onDeviceConnecting(address);
							if (deviceListener != null)
								deviceListener.onDeviceConnecting(address);
						}
						case DfuBaseService.PROGRESS_STARTING -> {
							if (globalListener != null) {
								globalListener.onDeviceConnected(address);
								globalListener.onDfuProcessStarting(address);
							}
							if (deviceListener != null) {
								deviceListener.onDeviceConnected(address);
								deviceListener.onDfuProcessStarting(address);
							}
						}
						case DfuBaseService.PROGRESS_ENABLING_DFU_MODE -> {
							if (globalListener != null)
								globalListener.onEnablingDfuMode(address);
							if (deviceListener != null)
								deviceListener.onEnablingDfuMode(address);
						}
						case DfuBaseService.PROGRESS_VALIDATING -> {
							if (globalListener != null)
								globalListener.onFirmwareValidating(address);
							if (deviceListener != null)
								deviceListener.onFirmwareValidating(address);
						}
						case DfuBaseService.PROGRESS_DISCONNECTING -> {
							if (globalListener != null)
								globalListener.onDeviceDisconnecting(address);
							if (deviceListener != null)
								deviceListener.onDeviceDisconnecting(address);
						}
						case DfuBaseService.PROGRESS_COMPLETED -> {
							if (globalListener != null) {
								globalListener.onDeviceDisconnected(address);
								globalListener.onDfuCompleted(address);
							}
							if (deviceListener != null) {
								deviceListener.onDeviceDisconnected(address);
								deviceListener.onDfuCompleted(address);
							}
						}
						case DfuBaseService.PROGRESS_ABORTED -> {
							if (globalListener != null) {
								globalListener.onDeviceDisconnected(address);
								globalListener.onDfuAborted(address);
							}
							if (deviceListener != null) {
								deviceListener.onDeviceDisconnected(address);
								deviceListener.onDfuAborted(address);
							}
						}
						default -> {
							if (progress == 0) {
								if (globalListener != null)
									globalListener.onDfuProcessStarted(address);
								if (deviceListener != null)
									deviceListener.onDfuProcessStarted(address);
							}
							if (globalListener != null)
								globalListener.onProgressChanged(address, progress, speed, avgSpeed, currentPart, partsTotal);
							if (deviceListener != null)
								deviceListener.onProgressChanged(address, progress, speed, avgSpeed, currentPart, partsTotal);
						}
					}

				}
				case DfuBaseService.BROADCAST_ERROR -> {
					final int error = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
					final int errorType = intent.getIntExtra(DfuBaseService.EXTRA_ERROR_TYPE, 0);

					if (globalListener != null)
						globalListener.onDeviceDisconnected(address);
					if (deviceListener != null)
						deviceListener.onDeviceDisconnected(address);
					switch (errorType) {
						case DfuBaseService.ERROR_TYPE_COMMUNICATION_STATE -> {
							if (globalListener != null)
								globalListener.onError(address, error, errorType, GattError.parseConnectionError(error));
							if (deviceListener != null)
								deviceListener.onError(address, error, errorType, GattError.parseConnectionError(error));
						}
						case DfuBaseService.ERROR_TYPE_DFU_REMOTE -> {
							if (globalListener != null)
								globalListener.onError(address, error, errorType, GattError.parseDfuRemoteError(error));
							if (deviceListener != null)
								deviceListener.onError(address, error, errorType, GattError.parseDfuRemoteError(error));
						}
						default -> {
							if (globalListener != null)
								globalListener.onError(address, error, errorType, GattError.parse(error));
							if (deviceListener != null)
								deviceListener.onError(address, error, errorType, GattError.parse(error));
						}
					}
				}
			}
		}
	}

	/**
	 * Registers the {@link DfuProgressListener}.
     * Registered listener will receive the progress events from the DFU service.
	 *
	 * @param context  the application context.
	 * @param listener the listener to register.
	 */
	public static void registerProgressListener(@NonNull final Context context, @NonNull final DfuProgressListener listener) {
		if (mProgressBroadcastReceiver == null) {
			mProgressBroadcastReceiver = new ProgressBroadcastsReceiver();

			final IntentFilter filter = new IntentFilter();
			filter.addAction(DfuBaseService.BROADCAST_PROGRESS);
			filter.addAction(DfuBaseService.BROADCAST_ERROR);
			//noinspection deprecation
			LocalBroadcastManager.getInstance(context).registerReceiver(mProgressBroadcastReceiver, filter);
		}
		mProgressBroadcastReceiver.setProgressListener(listener);
	}

	/**
	 * Registers the {@link DfuProgressListener}. Registered listener will receive the progress
     * events from the DFU service.
	 *
	 * @param context       the application context.
	 * @param listener      the listener to register.
	 * @param deviceAddress the address of the device to receive updates from (or null if any device).
	 */
	public static void registerProgressListener(@NonNull final Context context,
                                                @NonNull final DfuProgressListener listener, @NonNull final String deviceAddress) {
		if (mProgressBroadcastReceiver == null) {
			mProgressBroadcastReceiver = new ProgressBroadcastsReceiver();

			final IntentFilter filter = new IntentFilter();
			filter.addAction(DfuBaseService.BROADCAST_PROGRESS);
			filter.addAction(DfuBaseService.BROADCAST_ERROR);
			//noinspection deprecation
			LocalBroadcastManager.getInstance(context).registerReceiver(mProgressBroadcastReceiver, filter);
		}
		mProgressBroadcastReceiver.setProgressListener(deviceAddress, listener);
	}

	/**
	 * Unregisters the previously registered progress listener.
	 *
	 * @param context  the application context.
	 * @param listener the listener to unregister.
	 */
	public static void unregisterProgressListener(@NonNull final Context context, @NonNull final DfuProgressListener listener) {
		if (mProgressBroadcastReceiver != null) {
			final boolean empty = mProgressBroadcastReceiver.removeProgressListener(listener);

			if (empty) {
				//noinspection deprecation
				LocalBroadcastManager.getInstance(context).unregisterReceiver(mProgressBroadcastReceiver);
				mProgressBroadcastReceiver = null;
			}
		}
	}

	/**
	 * Registers the {@link DfuLogListener}. Registered listener will receive the log events from the DFU service.
	 *
	 * @param context  the application context.
	 * @param listener the listener to register.
	 */
	public static void registerLogListener(@NonNull final Context context, @NonNull final DfuLogListener listener) {
		if (mLogBroadcastReceiver == null) {
			mLogBroadcastReceiver = new LogBroadcastReceiver();

			final IntentFilter filter = new IntentFilter();
			filter.addAction(DfuBaseService.BROADCAST_LOG);
			//noinspection deprecation
			LocalBroadcastManager.getInstance(context).registerReceiver(mLogBroadcastReceiver, filter);
		}
		mLogBroadcastReceiver.setLogListener(listener);
	}

	/**
	 * Registers the {@link DfuLogListener}. Registered listener will receive the log events from
     * the DFU service.
	 *
	 * @param context       the application context.
	 * @param listener      the listener to register.
	 * @param deviceAddress the address of the device to receive updates from (or null if any device).
	 */
	public static void registerLogListener(@NonNull final Context context,
                                           @NonNull final DfuLogListener listener, @NonNull final String deviceAddress) {
		if (mLogBroadcastReceiver == null) {
			mLogBroadcastReceiver = new LogBroadcastReceiver();

			final IntentFilter filter = new IntentFilter();
			filter.addAction(DfuBaseService.BROADCAST_LOG);
			//noinspection deprecation
			LocalBroadcastManager.getInstance(context).registerReceiver(mLogBroadcastReceiver, filter);
		}
		mLogBroadcastReceiver.setLogListener(deviceAddress, listener);
	}

	/**
	 * Unregisters the previously registered log listener.
	 *
	 * @param context  the application context.
	 * @param listener the listener to unregister.
	 */
	public static void unregisterLogListener(@NonNull final Context context, @NonNull final DfuLogListener listener) {
		if (mLogBroadcastReceiver != null) {
			final boolean empty = mLogBroadcastReceiver.removeLogListener(listener);

			if (empty) {
				//noinspection deprecation
				LocalBroadcastManager.getInstance(context).unregisterReceiver(mLogBroadcastReceiver);
				mLogBroadcastReceiver = null;
			}
		}
	}
}
