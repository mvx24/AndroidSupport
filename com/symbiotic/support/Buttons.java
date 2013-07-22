package com.symbiotic.support;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.view.View;

public final class Buttons
{
	public static void setPressedEffectTouchListener(View button)
	{
		// Taken from: http://stackoverflow.com/questions/7175873/click-effect-on-button-in-android
		button.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event)
			{
				switch(event.getAction())
				{
					case MotionEvent.ACTION_DOWN:
						v.getBackground().setColorFilter(Color.argb(128, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
						v.invalidate();
						break;
					case MotionEvent.ACTION_UP:
						v.getBackground().clearColorFilter();
						v.invalidate();
						break;
				}
				return false;
			}
		});
	}
}
