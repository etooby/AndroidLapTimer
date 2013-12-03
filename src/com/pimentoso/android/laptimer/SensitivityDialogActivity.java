package com.pimentoso.android.laptimer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class SensitivityDialogActivity extends Activity implements OnClickListener, OnSeekBarChangeListener
{
	private SeekBar bar;
	private TextView barValue;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sensitivity);
		findViewById(R.id.button_sensitivity_close).setOnClickListener(this);
		
		LayoutParams params = getWindow().getAttributes();
		params.width = LayoutParams.FILL_PARENT;
		getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
		
		String currentValue = getPreferences(MODE_PRIVATE).getString("sensitivity", "15");
		
		bar = (SeekBar) findViewById(R.id.seekbar_sensitivity);
		bar.setOnSeekBarChangeListener(this);
		
		barValue = (TextView) findViewById(R.id.seekbar_sensitivity_value);
		barValue.setText(currentValue);
		
		bar.setProgress(Integer.valueOf(currentValue));
		
		// settare il valore nel timer a (25-barValue)
		// barra 20 = 5
		// barra 15 = 10
		// barra 10 = 15
		// barra 5 = 20
		// barra 0 = 25
	}

	@Override
	public void onClick(View v)
	{
		String finalValue = barValue.getText().toString();
		getPreferences(MODE_PRIVATE).edit().putString("sensitivity", finalValue).commit();
		TimerActivity.calibrateThreshold = 25 - Integer.valueOf(finalValue);
		this.finish();
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2)
	{
		String t = String.valueOf(arg1);
		barValue.setText(t);
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0)
	{
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0)
	{		
	}
}