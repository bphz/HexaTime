/*
 * Copyright (C) 2014 Priyesh Patel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyesh.hexatime;

import java.util.Calendar;
import java.util.Date;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class HexatimeService extends WallpaperService{

	public static final String SHARED_PREFS_NAME="hexatime_settings";

	public int wallpaperUpdateInterval;
	public int day, hour, twelveHour, min, sec;
	public Calendar cal;
	private SharedPreferences mPrefs = null;

	private int fontStyleValue = 1; 
	private Typeface fontStyle;

	private int clockSizeValue = 1;
	private int clockSize;

	private float clockHorizontalAlignment;
	private float clockVerticalAlignment;

	private boolean showNumberSignValue;
	private int colorCodeNotationValue;

	private String clockStringFormat;

	private int separatorStyleValue = 1;
	private String separatorStyle;

	private int clockVisibilityValue = 0;

	private int timeFormatValue = 1;
	private int colorRangeValue = 0;
	private int amountToDim;

	private boolean enableImageOverlayValue;
	private int imageOverlayValue;
	private int imageOverlay;
	private int imageOverlayOpacity;
	private int imageOverlayScale;

	private boolean enableSetCustomColorValue;
	private String customColor;

	@Override
	public Engine onCreateEngine() {
		return new HexatimeEngine(this);
	}

	private class HexatimeEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

		private boolean mVisible = false;
		private final Handler mHandler = new Handler();

		private Canvas c;
		private Paint hexClock, bg, dimLayer;
		
		private Bitmap initialOverlay;
		private BitmapDrawable overlayDrawable;
		private Bitmap finalOverlay;
		private int canvasWidth = 500, canvasHeight = 500;
		private int canvasDensity = 480;

		private final Runnable mUpdateDisplay = new Runnable() {

			@Override
			public void run() {
				draw();
			}};

			HexatimeEngine(WallpaperService ws) {
				mPrefs = HexatimeService.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
				mPrefs.registerOnSharedPreferenceChangeListener(this);
				onSharedPreferenceChanged(mPrefs, null);
			}
			
			public void onStart() {
				Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
				Point size = new Point();
				display.getSize(size);
				canvasWidth = size.x;
				canvasHeight = size.y;
				updateImageOverlay();
			}

			private void draw() {
				cal = Calendar.getInstance();
				day = cal.get(Calendar.DAY_OF_YEAR) - 1;
				hour = cal.get(Calendar.HOUR_OF_DAY);
				twelveHour = cal.get(Calendar.HOUR);
				if (twelveHour == 0){ twelveHour = 12; }
				min = cal.get(Calendar.MINUTE);
				sec = cal.get(Calendar.SECOND);

				SurfaceHolder holder = getSurfaceHolder();
				c = null;
				try {

					// Color Code Production
					String hexTime = null, hexValue;
					double tempTime = (twelveHour * 3600) + (min * 60) + sec;
					if(colorRangeValue == 1) {
						hexValue = String.format("%6S", Integer.toHexString((int)tempTime)).replace(" ", "0");
					}
					else {
						hexValue = String.format("%02X%02X%02X", twelveHour, min, sec);
					}
					
					// Time Format
					if (timeFormatValue == 0){
						hexTime = String.format(clockStringFormat, twelveHour, min, sec); 
					}						
					else {
						hexTime = String.format(clockStringFormat, hour, min, sec);
					}
					
					// Toggle number sign visibility
					if (showNumberSignValue){
						hexTime = "#" + hexTime;					
					}
					
					hexClock = new Paint();
					bg = new Paint();

					hexClock.setTextSize(clockSize);
					hexClock.setTypeface(fontStyle);
					hexClock.setColor(Color.WHITE);
					hexClock.setAntiAlias(true);
					
					// Calculate clock offset
					float d = hexClock.measureText(hexTime, 0, hexTime.length());
					int horizontalClockOffset = (int) d / 2;
					
					c = holder.lockCanvas();
					
					if (c != null) {
						canvasWidth = c.getWidth();
						canvasHeight = c.getHeight();
						canvasDensity = c.getDensity();

						// Colored Background Drawing

						float hue = (float)(hour * 60 + min) * 360f / 1440f;
						float sat = 0.9f;
						float val = 0.9f;
						
						int defaultColor = Color.HSVToColor(new float[] {hue, sat, val - amountToDim * 0.5f / 255f});
						
						if (!enableSetCustomColorValue) {
							c.drawColor(defaultColor); 
						} else {
							c.drawColor(Color.parseColor("#" + customColor));
						}

						// Image Overlay
						if (enableImageOverlayValue) {
							long t = System.nanoTime();
							c.drawBitmap(finalOverlay, 0, 0, new Paint());
							Log.i("HexaTime", "Took: " + (System.nanoTime() - t) + " nanos to render overlay");
						}						

						// Clock Visibility
						if (clockVisibilityValue == 0){
							c.drawText(hexTime, clockHorizontalAlignment - horizontalClockOffset, clockVerticalAlignment, hexClock);
						}
						else if (clockVisibilityValue == 1) {
							KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
							boolean lockscreenShowing = km.inKeyguardRestrictedInputMode();
							if(!lockscreenShowing){  
								c.drawText(hexTime, clockHorizontalAlignment - horizontalClockOffset, clockVerticalAlignment, hexClock);
							}
						}
						else if (clockVisibilityValue == 2) {
							// Don't draw the clock...ever
						}
					}
				} finally {
					if (c != null)
						holder.unlockCanvasAndPost(c);
				}
				mHandler.removeCallbacks(mUpdateDisplay);
				if (mVisible) {
					mHandler.postDelayed(mUpdateDisplay, wallpaperUpdateInterval);
				}
			}

			@Override
			public void onVisibilityChanged(boolean visible) {
				mVisible = visible;
				if (visible) {
					draw();
				} else {
					mHandler.removeCallbacks(mUpdateDisplay);
				}
			}

			@Override
			public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
				draw();
			}

			@Override
			public void onSurfaceDestroyed(SurfaceHolder holder) {
				super.onSurfaceDestroyed(holder);
				mVisible = false;
				mHandler.removeCallbacks(mUpdateDisplay);
			}

			@Override
			public void onDestroy() {
				super.onDestroy();
				mVisible = false;
				mHandler.removeCallbacks(mUpdateDisplay);
			}

			@Override
			public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
				if(key != null){
					if(key.equals("FONT_STYLE")){
						changeFontStyle(prefs.getString("FONT_STYLE", "1"));
					}
					else if(key.equals("CLOCK_SIZE")){
						changeClockSize(prefs.getString("CLOCK_SIZE", "1"));
					}
					else if(key.equals("CLOCK_VERTICAL_ALIGNMENT")){
						changeClockVerticalAlignment(prefs.getFloat("CLOCK_VERTICAL_ALIGNMENT", 0.5f));
					}
					else if(key.equals("CLOCK_HORIZONTAL_ALIGNMENT")){
						changeClockHorizontalAlignment(prefs.getFloat("CLOCK_HORIZONTAL_ALIGNMENT", 0.5f));
					}
					else if(key.equals("CLOCK_VISIBILITY")){
						changeClockVisibility(prefs.getString("CLOCK_VISIBILITY", "0"));
					}
					else if(key.equals("TIME_FORMAT")){
						changeTimeFormat(prefs.getString("TIME_FORMAT", "1"));
					}
					else if(key.equals("COLOR_RANGE")){
						changeColorRange(prefs.getString("COLOR_RANGE", "0"));
					}
					else if(key.equals("SHOW_NUMBER_SIGN")){
						showNumberSign(prefs.getBoolean("SHOW_NUMBER_SIGN", true));
					}
					else if(key.equals("COLOR_CODE_NOTATION")){
						changeColorCodeNotation(prefs.getString("COLOR_CODE_NOTATION", "0"));
					}
					else if(key.equals("SEPARATOR_STYLE")){
						changeSeparatorStyle(prefs.getString("SEPARATOR_STYLE", "5"));
					}
					else if(key.equals("DIM_BACKGROUND")){
						changeDimBackground(prefs.getFloat("DIM_BACKGROUND", 0.0f));
					}
					else if(key.equals("ENABLE_IMAGE_OVERLAY")){
						enableImageOverlay(prefs.getBoolean("ENABLE_IMAGE_OVERLAY", false));
					}
					else if(key.equals("IMAGE_OVERLAY")){
						changeImageOverlay(prefs.getString("IMAGE_OVERLAY", "0"));
					}
					else if(key.equals("IMAGE_OVERLAY_OPACITY")){
						changeImageOverlayOpacity(prefs.getFloat("IMAGE_OVERLAY_OPACITY", 0.5f));
					}
					else if(key.equals("IMAGE_OVERLAY_SCALE")){
						changeImageOverlayScale(prefs.getFloat("IMAGE_OVERLAY_SCALE", 0.5f));
					}
					else if(key.equals("ENABLE_SET_CUSTOM_COLOR")){
						enableSetCustomColor(prefs.getBoolean("ENABLE_SET_CUSTOM_COLOR", false));
					}
					else if(key.equals("SET_CUSTOM_COLOR")){
						getCustomColor(prefs.getString("SET_CUSTOM_COLOR", "#000000"));
					}
					else if(key.equals("REDUCE_WALLPAPER_UPDATES")){
						reduceWallpaperUpdates(prefs.getBoolean("REDUCE_WALLPAPER_UPDATES", false));
					}

				}
				else {	                        
					changeFontStyle(prefs.getString("FONT_STYLE", "1"));
					changeClockSize(prefs.getString("CLOCK_SIZE", "1"));
					changeClockVerticalAlignment(prefs.getFloat("CLOCK_VERTICAL_ALIGNMENT", 0.5f));
					changeClockHorizontalAlignment(prefs.getFloat("CLOCK_HORIZONTAL_ALIGNMENT", 0.5f));
					changeClockVisibility(prefs.getString("CLOCK_VISIBILITY", "0"));
					changeTimeFormat(prefs.getString("TIME_FORMAT", "1"));
					changeColorRange(prefs.getString("COLOR_RANGE", "0"));
					showNumberSign(prefs.getBoolean("SHOW_NUMBER_SIGN", true));
					changeColorCodeNotation(prefs.getString("COLOR_CODE_NOTATION", "0"));
					changeSeparatorStyle(prefs.getString("SEPARATOR_STYLE", "5"));
					changeDimBackground(prefs.getFloat("DIM_BACKGROUND", 0.0f)); 
					enableImageOverlay(prefs.getBoolean("ENABLE_IMAGE_OVERLAY", false));
					changeImageOverlay(prefs.getString("IMAGE_OVERLAY", "0"));
					changeImageOverlayOpacity(prefs.getFloat("IMAGE_OVERLAY_OPACITY", 0.5f));
					changeImageOverlayScale(prefs.getFloat("IMAGE_OVERLAY_SCALE", 0.5f));
					enableSetCustomColor(prefs.getBoolean("ENABLE_SET_CUSTOM_COLOR", false));
					getCustomColor(prefs.getString("SET_CUSTOM_COLOR", "#000000"));
					reduceWallpaperUpdates(prefs.getBoolean("REDUCE_WALLPAPER_UPDATES", false));
				}
				return;
			}

			private void changeFontStyle(String value){
				fontStyleValue = Integer.parseInt(value);
				if(fontStyleValue == 0){ 
					fontStyle = Typeface.createFromAsset(getAssets(), "Lato.ttf");
				}
				else if (fontStyleValue == 1){
					fontStyle = Typeface.createFromAsset(getAssets(), "LatoLight.ttf");                    
				}
				else if (fontStyleValue == 2){
					fontStyle = Typeface.createFromAsset(getAssets(), "Roboto.ttf");                    
				}
				else if (fontStyleValue == 3){
					fontStyle = Typeface.createFromAsset(getAssets(), "RobotoLight.ttf");                    
				}
				else if (fontStyleValue == 4){
					fontStyle = Typeface.createFromAsset(getAssets(), "RobotoSlab.ttf");                    
				}
				else if (fontStyleValue == 5){
					fontStyle = Typeface.createFromAsset(getAssets(), "RobotoSlabLight.ttf");                    
				}
			}

			private void changeClockSize(String value){
				clockSizeValue = Integer.parseInt(value);
				if(clockSizeValue == 0){
					clockSize = (getResources().getDimensionPixelSize(R.dimen.clockFontSizeSmall));
				}
				else if (clockSizeValue == 1){
					clockSize = (getResources().getDimensionPixelSize(R.dimen.clockFontSizeMed));
				}
				else if (clockSizeValue == 2){
					clockSize = (getResources().getDimensionPixelSize(R.dimen.clockFontSizeLarge));
				}
			}

			private void changeClockVerticalAlignment(float value){
				WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
				Display display = wm.getDefaultDisplay();
				Point size = new Point();
				display.getSize(size);
				int h = size.y;				
				clockVerticalAlignment = h - (h * value);
			}

			private void changeClockHorizontalAlignment(float value){
				WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
				Display display = wm.getDefaultDisplay();
				Point size = new Point();
				display.getSize(size);
				int w = size.x;
				clockHorizontalAlignment = (w * value);
			}

			private void showNumberSign(boolean value){
				showNumberSignValue = value;
			}

			private void changeColorCodeNotation(String value){
				colorCodeNotationValue = Integer.parseInt(value);
			}

			private void changeSeparatorStyle(String value){
				separatorStyleValue = Integer.parseInt(value);
				String singleDigit = (colorCodeNotationValue == 1) ? "%02X" : "%02d";
				if(separatorStyleValue == 0){ 
					separatorStyle = ":";
				}
				else if (separatorStyleValue == 1){
					separatorStyle = " ";
				}
				else if (separatorStyleValue == 2){
					separatorStyle = ".";
				}
				else if (separatorStyleValue == 3){
					separatorStyle = "|";
				}
				else if (separatorStyleValue == 4){
					separatorStyle = "/";
				}
				else if (separatorStyleValue == 5){
					separatorStyle = "";
				}
				clockStringFormat = singleDigit + separatorStyle + singleDigit + separatorStyle + singleDigit;
			}

			private void changeClockVisibility(String value){
				clockVisibilityValue = Integer.parseInt(value);
			}

			private void changeTimeFormat(String value){
				timeFormatValue = Integer.parseInt(value);
			}

			private void changeColorRange(String value){
				colorRangeValue = Integer.parseInt(value);
			}

			private void changeDimBackground(float value){
				amountToDim = (int) (value * 255);
			}

			private void enableImageOverlay(boolean value){
				enableImageOverlayValue = value;
			}

			private void changeImageOverlay(String value){
				imageOverlayValue = Integer.parseInt(value);
				if (imageOverlayValue == 0){
					imageOverlay = R.drawable.hex;
				}
				else if (imageOverlayValue == 1){
					imageOverlay = R.drawable.grid;
				}
				else if (imageOverlayValue == 2){
					imageOverlay = R.drawable.dots;
				}
				else if (imageOverlayValue == 3){
					imageOverlay = R.drawable.circles;
				}
				updateImageOverlay();
			}
			
			private void updateImageOverlay() {
				if (imageOverlayScale <= 0) imageOverlayScale = 1;
				if (initialOverlay != null) initialOverlay.recycle();
				initialOverlay = BitmapFactory.decodeResource(getResources(), imageOverlay);
				Bitmap overlayScaled = Bitmap.createScaledBitmap(initialOverlay, imageOverlayScale, imageOverlayScale, false);
				overlayDrawable = new BitmapDrawable (overlayScaled); 
				overlayDrawable.setTileModeX(Shader.TileMode.REPEAT); 
				overlayDrawable.setTileModeY(Shader.TileMode.REPEAT);
				overlayDrawable.setAlpha(imageOverlayOpacity);
				overlayDrawable.setBounds(0, 0, canvasWidth, canvasHeight);
				overlayDrawable.setDither(false);
				overlayDrawable.setTargetDensity(canvasDensity);
				overlayDrawable.setAntiAlias(false);
				if (finalOverlay != null) finalOverlay.recycle();
				finalOverlay = Bitmap.createBitmap(canvasWidth, canvasHeight, initialOverlay.getConfig());
				Canvas c = new Canvas(finalOverlay);
				Paint p = new Paint();
				overlayDrawable.draw(c);
			}

			private void changeImageOverlayOpacity(float value){
				imageOverlayOpacity = (int) (value * 255);
				updateImageOverlay();
			}

			private void changeImageOverlayScale(float value){
				imageOverlayScale = (int) (500 * value);
				if (imageOverlayScale == 0) {
					imageOverlayScale = 1;
				}
				updateImageOverlay();
			}

			private void enableSetCustomColor(boolean value){
				enableSetCustomColorValue = value;
			}

			private void getCustomColor(String value){
				customColor = value;
			}

			private void reduceWallpaperUpdates(boolean value){
				boolean wallpaperUpdateIntervalValue = value;
				if (wallpaperUpdateIntervalValue){
					wallpaperUpdateInterval = 10000;
				}
				else {
					wallpaperUpdateInterval = 1000;
				}
			}
	}
}
