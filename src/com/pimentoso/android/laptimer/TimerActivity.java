package com.pimentoso.android.laptimer;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class TimerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback, OnClickListener {

	// elementi del layout
	SurfaceView mSurfaceView;
	SurfaceHolder mSurfaceHolder;
	Camera mCamera;

	TextView timerLabel;
	TextView statusLabel;
	TextView lap1Label;
	TextView lap2Label;
	TextView lap3Label;
	TextView lapBestLabel;
	Button startButton;

	// flags
	boolean isPreviewRunning = false;
	boolean isCalibrating = false;
	boolean isCalibrated = false;
	boolean isStarted = false;
	boolean isTimerRunning = false;
	boolean caughtPreviousFrame = false;

	// contatore dei frame per calibrazione
	int frame = 0;

	// offset dei pixel da controllare
	int[] pixelOffset = new int[3];

	// array di calibrazione
	int[][] calibrateRange = new int[3][20];

	// valori finali di calibrazione
	int[] calibrateValue = new int[3];

	// soglia di differenza di luminosità per catchare il frame
	public static int calibrateThreshold = 10;

	// millisecondi di ultimo catch
	long mLastCatchTime = 0;

	// tempo del giro migliore
	long bestLap = 0;

	// tempi dei giri
	// long[] laps = new long[3];
	ArrayList<Long> laps = new ArrayList<Long>();

	// contatore dei giri
	int lapCount = 0;

	Handler mHandler = new Handler();
	FPSCounter fps;
	long mStartTime = 0L;
	
	StringBuilder lapBuffer;
	StringBuilder timeBuffer;
	
	static byte[] frameBuffer1;
	static byte[] frameBuffer2;
	static byte[] frameBuffer3;

	private Runnable mUpdateTimeTask = new Runnable() {

		public void run() {

			long millis = SystemClock.uptimeMillis() - mStartTime;
			timerLabel.setText(convertTime(millis));
			mHandler.postAtTime(this, SystemClock.uptimeMillis() + 40);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		findViewById(R.id.button_start).setOnClickListener(this);
		findViewById(R.id.button_calibrate).setOnClickListener(this);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface_camera);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		String threshold = getPreferences(MODE_PRIVATE).getString("sensitivity", "15");
		calibrateThreshold = Integer.valueOf(threshold);

		timerLabel = (TextView) findViewById(R.id.text_timer);
		statusLabel = (TextView) findViewById(R.id.text_status);
		lap1Label = (TextView) findViewById(R.id.text_lap_1);
		lap2Label = (TextView) findViewById(R.id.text_lap_2);
		lap3Label = (TextView) findViewById(R.id.text_lap_3);
		lapBestLabel = (TextView) findViewById(R.id.text_lap_best);
		startButton = (Button) findViewById(R.id.button_start);

		statusLabel.setText(getString(R.string.label_status_init));
		startButton.setEnabled(false);
		
		fps = new FPSCounter();
	}

	@Override
	public void onStart() {

		super.onStart();

		// show help
		if (getPreferences(MODE_PRIVATE).getString("first_time", "1").equals("1")) {
			showAlertBox();
			getPreferences(MODE_PRIVATE).edit().putString("first_time", "0").commit();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {

		synchronized (this) {
			
			try {
				mCamera = Camera.open();
			}
			catch (RuntimeException e) {
				
				// camera service already in use: schianta
				new AlertDialog.Builder(this).setMessage(getString(R.string.error_camera_locked_text)).setTitle("Error").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int whichButton) {

						TimerActivity.this.finish();
					}
				}).show();

				return;
			}

			if (mCamera == null) {
				
				// camera not found: schianta
				new AlertDialog.Builder(this).setMessage(getString(R.string.error_camera_null_text)).setTitle("Error").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int whichButton) {

						TimerActivity.this.finish();
					}
				}).show();

				return;
			}

			Camera.Parameters parameters = mCamera.getParameters();

			Camera.Size mCameraSize = parameters.getPreviewSize();
			int bytesPerPixel = ImageFormat.getBitsPerPixel(parameters.getPreviewFormat());

			int bufferSize = (mCameraSize.width * mCameraSize.height * bytesPerPixel) >> 3;
			
			frameBuffer1 = new byte[bufferSize];
			frameBuffer2 = new byte[bufferSize];
			frameBuffer3 = new byte[bufferSize];

			mCamera.addCallbackBuffer(frameBuffer1);
			mCamera.addCallbackBuffer(frameBuffer2);
			mCamera.addCallbackBuffer(frameBuffer3);

			pixelOffset[0] = (int) (mCameraSize.width / 2) + (mCameraSize.width * (int) (mCameraSize.height * 0.1));
			pixelOffset[1] = (int) (mCameraSize.width / 2) + (mCameraSize.width * (int) (mCameraSize.height * 0.5));
			pixelOffset[2] = (int) (mCameraSize.width / 2) + (mCameraSize.width * (int) (mCameraSize.height * 0.9));

			mCamera.setDisplayOrientation(90);

			try {
				mCamera.setPreviewDisplay(arg0);
			}
			catch (IOException e) {
				Log.e("Camera", "Could not set preview display");
			}

			mCamera.setPreviewCallbackWithBuffer(this);
			mCamera.startPreview();
			isPreviewRunning = true;
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {

		synchronized (this) {
			try {
				if (mCamera != null) {
					mCamera.setPreviewCallback(null);
					mCamera.stopPreview();
					isPreviewRunning = false;
				}
			}
			catch (Exception e) {
				Log.e("Camera", e.getMessage());
			}
			finally {
				if (mCamera != null) {
					mCamera.release();
				}
			}
		}
	}

	@Override
	public void onPreviewFrame(byte[] yuv, Camera arg1) {

		int value0 = (int) yuv[pixelOffset[0]] & 0xFF;
		int value1 = (int) yuv[pixelOffset[1]] & 0xFF;
		int value2 = (int) yuv[pixelOffset[2]] & 0xFF;

		// sto calibrando...
		if (isCalibrating) {
			
			frame++;

			calibrateRange[0][frame - 1] = value0;
			calibrateRange[1][frame - 1] = value1;
			calibrateRange[2][frame - 1] = value2;

			if (frame >= 20) {
				// finito di calibrare
				isCalibrating = false;
				isCalibrated = true;
				startButton.setEnabled(true);
				statusLabel.setText(getString(R.string.label_status_ready));

				// calcolo la media
				int tot0 = 0, tot1 = 0, tot2 = 0;
				for (int i = 0; i < 20; i++) {
					tot0 += calibrateRange[0][i];
					tot1 += calibrateRange[1][i];
					tot2 += calibrateRange[2][i];
				}

				calibrateValue[0] = tot0 / 20;
				calibrateValue[1] = tot1 / 20;
				calibrateValue[2] = tot2 / 20;
			}
		}

		// sono in ascolto di variazioni
		else if (isStarted) {

			// catch del frame
			if (isCalibrated && 
					(value0 < calibrateValue[0] - calibrateThreshold || value0 > calibrateValue[0] + calibrateThreshold || 
						value1 < calibrateValue[1] - calibrateThreshold || value1 > calibrateValue[1] + calibrateThreshold ||
						value2 < calibrateValue[2] - calibrateThreshold || value2 > calibrateValue[2] + calibrateThreshold)) {
				
				// se ho catchato il frame precedente, ignoro
				if (!caughtPreviousFrame) {

					caughtPreviousFrame = true;

					if (isTimerRunning) {
						// calcolo dei lap
						long catchTime = SystemClock.uptimeMillis();
						lapCount++;

						long lapTime = catchTime - mLastCatchTime;
						laps.add(lapTime);

						if (lapCount == 1) {
							bestLap = lapTime;
						}
						else if (bestLap > lapTime) {
							bestLap = lapTime;
						}

						printLaps();

						mLastCatchTime = catchTime;
					}
					else {
						
						// devo far partire il timer
						isTimerRunning = true;
						mStartTime = SystemClock.uptimeMillis();
						mLastCatchTime = mStartTime;
						mHandler.removeCallbacks(mUpdateTimeTask);
						mHandler.postDelayed(mUpdateTimeTask, 50);
					}
				}
			}
			else {

				caughtPreviousFrame = false;
			}
		}

		mCamera.addCallbackBuffer(yuv);
		fps.logFrame();
	}

	@Override
	public void onClick(View v) {

		switch (v.getId()) {
			
			case R.id.button_start: {
				
				if (!isCalibrated || isCalibrating) {
					
					// non è calibrato
					break;
				}

				if (isStarted) {
					
					// ho stoppato
					startButton.setText("Start");
					statusLabel.setText(getString(R.string.label_status_ready));
					isStarted = false;
					isTimerRunning = false;
					mHandler.removeCallbacks(mUpdateTimeTask);
				}
				else {
					
					// ho startato
					startButton.setText("Stop");
					statusLabel.setText(getString(R.string.label_status_started));
					timerLabel.setText("0:00:0");
					isStarted = true;
					mStartTime = 0L;
					lapCount = 0;

					// resetto i laps
					laps = new ArrayList<Long>();
					bestLap = 0;

					printLaps();
				}

				break;
			}
			case R.id.button_calibrate: {
				
				if (isTimerRunning) {
					// devo stoppare prima di calibrare
					break;
				}

				// ho pigiato calibra, devo resettare tutto
				statusLabel.setText(getString(R.string.label_status_calibrating));
				timerLabel.setText("0:00:0");

				frame = 0;
				mStartTime = 0L;
				lapCount = 0;
				isStarted = false;
				isTimerRunning = false;
				isCalibrating = true;
				isCalibrated = false;

				// resetto i laps
				laps = new ArrayList<Long>();
				bestLap = 0;

				printLaps();

				break;
			}
		}
	}

	private String convertTime(long millis) {

		if (millis == 0) {
			return "0:00:0";
		}

		timeBuffer = new StringBuilder();
		int split = ((int) (millis / 100)) % 10;
		int seconds = (int) (millis / 1000);
		int minutes = seconds / 60;
		seconds = seconds % 60;

		if (seconds < 10) {
			timeBuffer.append(minutes).append(":0").append(seconds).append(":").append(split);
		}
		else {
			timeBuffer.append(minutes).append(":").append(seconds).append(":").append(split);
		}
		
		return timeBuffer.toString();
	}

	private void printLaps() {

		lapBuffer = new StringBuilder();
		lapBuffer.append("Lap ").append(lapCount).append(": ");
		
		try {
			lapBuffer.append(convertTime(laps.get(laps.size() - 1)));
		}
		catch (IndexOutOfBoundsException e) {
			lapBuffer.append(convertTime(0));
		}
		lap1Label.setText(lapBuffer.toString());
		
		lapBuffer = new StringBuilder();
		
		if (lapCount > 1) {
			lapBuffer.append("Lap ").append(lapCount - 1).append(": ");
			lap2Label.setVisibility(View.VISIBLE);
		}
		else {
			lapBuffer.append("Lap 0: ");
		}
		
		try {
			lapBuffer.append(convertTime(laps.get(laps.size() - 2)));
		}
		catch (IndexOutOfBoundsException e) {
			lapBuffer.append(convertTime(0));
		}
		lap2Label.setText(lapBuffer.toString());
		
		lapBuffer = new StringBuilder();
		
		if (lapCount > 2) {
			lapBuffer.append("Lap ").append(lapCount - 2).append(": ");
			lap3Label.setVisibility(View.VISIBLE);
		}
		else {
			lapBuffer.append("Lap 0: ");
		}
		
		try {
			lapBuffer.append(convertTime(laps.get(laps.size() - 3)));
		}
		catch (IndexOutOfBoundsException e) {
			lapBuffer.append(convertTime(0));
		}
		lap3Label.setText(lapBuffer.toString());
		
		lapBuffer = new StringBuilder();
		lapBuffer.append("Best lap: ").append(convertTime(bestLap));
		lapBestLabel.setText(lapBuffer.toString());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.menu_tutorial: {
				showAlertBox();
				return true;
			}
			case R.id.menu_sensitivity: {
				// devo stoppare tutto
				startButton.setText("Start");
				statusLabel.setText(getString(R.string.label_status_ready));
				isStarted = false;
				isTimerRunning = false;
				mHandler.removeCallbacks(mUpdateTimeTask);

				Intent i = new Intent(this, SensitivityDialogActivity.class);
				startActivity(i);
				return true;
			}
			case R.id.menu_email: {
				if (isStarted || isTimerRunning) {
					Toast.makeText(this, getString(R.string.error_timer_started), Toast.LENGTH_SHORT).show();
					return true;
				}
				if (laps == null || laps.size() == 0) {
					Toast.makeText(this, getString(R.string.error_laps_empty), Toast.LENGTH_SHORT).show();
					return true;
				}

				String emailBody = lapsToString();

				final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
				emailIntent.setType("plain/text");
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name));
				emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, emailBody);
				startActivity(Intent.createChooser(emailIntent, getString(R.string.menu_mail_laps)));

				return true;
			}
		}
		return false;
	}

	public void showAlertBox() {

		new AlertDialog.Builder(this).setMessage(getString(R.string.dialog_tutorial_text)).setTitle("Tutorial").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int whichButton) {

			}
		}).show();
	}

	private String lapsToString() {

		StringBuilder s = new StringBuilder();

		s.append("Mini 4WD Android Lap Timer data");
		s.append("\n\n");

		for (int i = 0; i < laps.size(); i++) {
			long lap = laps.get(i);
			s.append("Lap ").append(i + 1).append(": ").append(convertTime(lap)).append("\n");
		}

		s.append("\n");
		s.append("Best lap: " + convertTime(bestLap));

		return s.toString();
	}
}
