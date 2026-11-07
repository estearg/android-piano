/*
 * This file is part of Piano.
 * 
 * Piano is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Piano is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Piano.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Piano
 * Virtual piano based on Hexiano (https://gitorious.org/hexiano)
 * Original sounds from http://theremin.music.uiowa.edu/MISpiano.html
 */

package org.esteban.piano;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

	// Custom view
	private PianoLayout pianoView;
	// Preference variables
	protected String lowerOctavePosition;
	protected String damper; // boolean preferred, but no boolean array resource possible
	protected String octaves;
	protected String orientation;
	// Selected instrument (chosen on the home page): piano, organ, epiano, musicbox or harpsichord
	protected String instrument;
	// Write the note name on each key
	protected boolean showNotes;
	// Preference data interface
	static SharedPreferences sharedPreferences;
	// Flags to detect key presses
	private boolean upPressed;
	private boolean downPressed;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Load preferences. The following:
//		sharedPreferences = getSharedPreferences("Piano", Activity.MODE_PRIVATE); // if the file doesn't exist it'll be created when retrieving an editor and commiting changes
		// ...didn't call the on change listener. So:
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		// Save preferences into variables
		lowerOctavePosition = sharedPreferences.getString("pref_rows",
				this.getString(R.string.pref_rows_default_value));
		damper = sharedPreferences.getString("pref_damper",
				this.getString(R.string.pref_damper_default_value));
		octaves = sharedPreferences.getString("pref_octaves",
				this.getString(R.string.pref_octaves_default_value));
		orientation = sharedPreferences.getString("pref_orient",
				this.getString(R.string.pref_orient_default_value));

		showNotes = sharedPreferences.getBoolean("pref_show_notes", false);
		instrument = sharedPreferences.getString("pref_instrument", "piano");
		if (!(instrument.equals("organ") || instrument.equals("epiano")
				|| instrument.equals("musicbox") || instrument.equals("harpsichord"))) {
			instrument = "piano";
		}

		// Make volume button always control just the media volume
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		// Flags initialization
		upPressed = false;
		downPressed = false;
		
		// Show the view in full screen mode without title
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		// Set preferred orientation
		if (orientation.equals(this.getString(R.string.pref_orient_landscape_value))) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		if (orientation.equals(this.getString(R.string.pref_orient_portrait_value))) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
		// Set view
		pianoView = new PianoLayout(this.getApplicationContext());
		// Keep the keyboard out from under the system bars (Android 15 draws behind them)
		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		root.setFitsSystemWindows(true);
		root.addView(pianoView, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(root);
	}

	// Prefix of the sound resource names of the selected instrument (note0.wav, organ_note0.wav...)
	String notePrefix() {
		return instrument.equals("piano") ? "note" : instrument + "_note";
	}

	// The organ sound is a short loop that plays while the key is held
	boolean loopsWhileHeld() {
		return instrument.equals("organ");
	}

	// Silence everything when the app leaves the screen, so no looped note keeps sounding
	@Override
	protected void onPause() {
		super.onPause();
		pianoView.silence();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		pianoView.destroy();
	}

	// Implement the method that is called when a shared preference is changed, added or removed
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("pref_damper")) {
			damper = sharedPreferences.getString(key,
					this.getString(R.string.pref_damper_default_value));
		}
		if (key.equals("pref_rows")) {
			// See if the preference was really changed or just the dialog shown
			if (!(lowerOctavePosition.equals(sharedPreferences.getString(key,
					this.getString(R.string.pref_rows_default_value))))) {
				// Update variable
				lowerOctavePosition = sharedPreferences.getString(key,
					this.getString(R.string.pref_rows_default_value));
				// Swap rows
				pianoView.swapRows();
			}
		}
		if (key.equals("pref_octaves")) {
			// See if the preference was really changed or just the dialog shown
			if (!(octaves.equals(sharedPreferences.getString(key,
					this.getString(R.string.pref_octaves_default_value))))) {
				// Update variable
				octaves = sharedPreferences.getString(key,
					this.getString(R.string.pref_octaves_default_value));
				pianoView.buildNoteLabels();
				// Release old sounds and clear their identifications
				for (int id : pianoView.soundIds) {
					pianoView.pianoSounds.unload(id);
				}
				pianoView.soundIds.clear();
				// Load new sounds and save their identifications
				for (int i = 0; i < pianoView.numberOfNotes; i++) {
					int resourceId;
					if (octaves.equals(this.getString(R.string.pref_octaves_34_value))) {
						resourceId = this.getApplicationContext().getResources().
								getIdentifier(notePrefix()
										+ i,
										"raw", this.getApplicationContext().getPackageName());
						pianoView.soundIds.add(pianoView.pianoSounds.load(
								this.getApplicationContext(), resourceId, 1));
					}
					if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_45_value))) {
						resourceId = this.getApplicationContext().getResources().
								getIdentifier(notePrefix()
								+ Integer.toString(i + 12),
										"raw", this.getApplicationContext().getPackageName());
						pianoView.soundIds.add(pianoView.pianoSounds.load(
								this.getApplicationContext(), resourceId, 1));
					}
					if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_35_value))) {
						resourceId = this.getApplicationContext().getResources().
								getIdentifier(notePrefix()
								+ Integer.toString(i + (i / 12) * 12),
										"raw", this.getApplicationContext().getPackageName());
						pianoView.soundIds.add(pianoView.pianoSounds.load(
								this.getApplicationContext(), resourceId, 1));
					}
				}
			}
		}
		if (key.equals("pref_orient")) {
			// Update variable
			orientation = sharedPreferences.getString(key,
					this.getString(R.string.pref_orient_default_value));
			// Set preferred screen orientation
			if (orientation.equals(this.getString(R.string.pref_orient_landscape_value))) {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			}
			if (orientation.equals(this.getString(R.string.pref_orient_portrait_value))) {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
		}
	}

	// Respond to special key press combination when there is no hardware menu key to show the menu
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Set flags according to key presses and releases
		if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				upPressed = true;
			}
			if (event.getAction() == KeyEvent.ACTION_UP) {
				upPressed = false;
			}
		}
		if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				downPressed = true;
			}
			if (event.getAction() == KeyEvent.ACTION_UP) {
				downPressed = false;
			}
		}
		// Go back to the home page when both volume keys are pressed (the keyboard takes up the whole screen)
		if ((upPressed == true) && (downPressed == true)
// hasPermanentMenuKey is only available from API 14
//				&&
//				!(ViewConfiguration.get(this.getApplicationContext()).hasPermanentMenuKey())
				) {
			// reset flags
			upPressed = false;
			downPressed = false;
			// leave the piano; the home page is the previous screen
			finish();
			// return
			return true;
		}
		
		return super.dispatchKeyEvent(event);
	}

	public class PianoLayout extends View {
		// a Paint object is needed to be able to draw anything
		private Paint pianoPaint;
		private Paint textPaint;
		// label of each note (name + octave), by note number; rebuilt when the octaves change
		private String[] noteLabels;
		// view dimensions (fixed)
		private int pianoWidth, pianoHeight;
		// Path objects that define the shapes of the keys
		private Path symmetricWhiteKey, asymCFWhiteKey, asymEBWhiteKey, blackKey;
		// notes in the keyboard
		private int numberOfNotes;
		private int numberOfBlackKeys;
		private ArrayList<Integer> blackKeyNoteNumbers;
		// keys
		private ArrayList<Path> keys;
		// sounds
		private SoundPool pianoSounds;
		// sound identifications array, to associate a piano key with its sound
		private ArrayList<Integer> soundIds; // returned by sound pool load
		private int[] playIds; // returned by sound pool play (0 = none)
		// uptime (ms) at which each key's current note started; 0 = not sounding
		private long[] startTime;
		// volume of each note as a function of how many notes sound together (index = count)
		private float[] gainTable;
		// notes started within this time still count as sounding (piano notes decay quickly)
		private static final long OVERLAP_WINDOW_MS = 800;
		// a looped note is stopped for good this long after its loop was ended
		private static final long LOOP_END_STOP_MS = 400;
		private final Handler handler = new Handler(Looper.getMainLooper());
		// objects needed to draw outside of onDraw
		// bounding rectangles of the keys (precomputed, so touch handling allocates nothing)
		private RectF[] keyBounds;
		private boolean[] isBlack;
		// keys currently held down, one bit per note number
		private int pressedMask;

		// Constructor
		public PianoLayout(Context context) {
			super(context);

			// Initialization
			pianoPaint = new Paint();
			textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			textPaint.setTextAlign(Paint.Align.CENTER);
			textPaint.setFakeBoldText(true);
			pianoPaint.setStrokeWidth(2.0f); // stroke width used when Style is Stroke or StrokeAndFill, in pixels?
			symmetricWhiteKey = new Path();
			asymCFWhiteKey = new Path();
			asymEBWhiteKey = new Path();
			blackKey = new Path();
			keys = new ArrayList<Path>();
			numberOfNotes = 24; // two octaves
			numberOfBlackKeys = 10; // two octaves
			blackKeyNoteNumbers = new ArrayList<Integer>();
			keyBounds = new RectF[numberOfNotes];
			isBlack = new boolean[numberOfNotes];
			for (int i = 0; i < numberOfNotes; i++) {
				keyBounds[i] = new RectF();
			}
			if (Build.VERSION.SDK_INT >= 21) {
				pianoSounds = new SoundPool.Builder()
						.setMaxStreams(24)
						.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_GAME)
								.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
								.build())
						.build();
			} else {
				pianoSounds = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
			}
			soundIds = new ArrayList<Integer>();
			playIds = new int[numberOfNotes];
			startTime = new long[numberOfNotes];
			// With N overlapping notes the mix peaks at roughly N * peak * gain, so
			// gain ~ 1/N^0.85 keeps the sum below full scale (no clipping) without
			// making a single note quiet.
			gainTable = new float[numberOfNotes + 1];
			for (int n = 1; n <= numberOfNotes; n++) {
				gainTable[n] = (float) Math.pow(n, -0.85);
			}
			for (int i = 0; i < numberOfNotes; i++) {
				int resourceId;
				// Load the sound of each note, saving the identifications
				// (audio files saved as res/raw/note0.ogg etc.)
				if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_34_value))) {
					resourceId = context.getResources().getIdentifier(notePrefix()
							+ Integer.toString(i),
							"raw", context.getPackageName());
					soundIds.add(pianoSounds.load(context, resourceId, 1));
				}
				if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_45_value))) {
					resourceId = context.getResources().getIdentifier(notePrefix()
							+ Integer.toString(i + 12),
							"raw", context.getPackageName());
					soundIds.add(pianoSounds.load(context, resourceId, 1));
				}
				if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_35_value))) {
					resourceId = context.getResources().getIdentifier(notePrefix()
							+ Integer.toString(i + (i / 12) * 12),
							"raw", context.getPackageName());
					soundIds.add(pianoSounds.load(context, resourceId, 1));
				}
				// Create key objects
				keys.add(new Path());
				// Record the note numbers of the black keys
				switch (i % 12) {
				case 1:
				case 3:
				case 6:
				case 8:
				case 10:
					blackKeyNoteNumbers.add(Integer.valueOf(i));
					isBlack[i] = true;
					break;
				default:
					break;
				}
			}
		}

		// Draw the keyboard directly on the (hardware accelerated) canvas
		@Override
		protected void onDraw(Canvas canvas) {
			canvas.drawColor(Color.WHITE);
			for (int i = 0; i < numberOfNotes; i++) {
				boolean pressed = (pressedMask & (1 << i)) != 0;
				if (isBlack[i]) {
					pianoPaint.setStyle(Paint.Style.FILL);
					pianoPaint.setColor(pressed ? Color.LTGRAY : Color.BLACK);
				} else if (pressed) {
					pianoPaint.setStyle(Paint.Style.FILL);
					pianoPaint.setColor(Color.DKGRAY);
				} else {
					pianoPaint.setStyle(Paint.Style.STROKE);
					pianoPaint.setColor(Color.BLACK);
				}
				canvas.drawPath(keys.get(i), pianoPaint);
			}
			if (showNotes) {
				drawNoteNames(canvas);
			}
		}

		// Build the "name + octave" label of each note, for example C4 (Do4 in Turkish)
		void buildNoteLabels() {
			String[] names = getResources().getStringArray(R.array.note_names);
			if (noteLabels == null) {
				noteLabels = new String[numberOfNotes];
			}
			for (int i = 0; i < numberOfNotes; i++) {
				int octave;
				if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_45_value))) {
					octave = 4 + i / 12;
				} else if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_35_value))) {
					octave = 3 + 2 * (i / 12);
				} else {
					octave = 3 + i / 12;
				}
				noteLabels[i] = names[i % 12] + octave;
			}
			invalidate();
		}

		// Write each note's label at the bottom of its key
		private void drawNoteNames(Canvas canvas) {
			float whiteSize = pianoWidth / 7f * 0.22f;
			for (int i = 0; i < numberOfNotes; i++) {
				boolean pressed = (pressedMask & (1 << i)) != 0;
				RectF b = keyBounds[i];
				if (isBlack[i]) {
					textPaint.setTextSize(whiteSize * 0.8f);
					textPaint.setColor(pressed ? Color.BLACK : Color.WHITE);
					canvas.drawText(noteLabels[i], b.centerX(), b.bottom - pianoHeight * 0.02f, textPaint);
				} else {
					textPaint.setTextSize(whiteSize);
					textPaint.setColor(pressed ? Color.WHITE : Color.BLACK);
					canvas.drawText(noteLabels[i], b.centerX(), b.bottom - pianoHeight * 0.03f, textPaint);
				}
			}
		}

		// Find the key under a point, or -1. Black keys first, because the
		// bounding rectangles of white keys overlap with them.
		private int keyAt(float x, float y) {
			for (int i = 0; i < numberOfNotes; i++) {
				if (isBlack[i] && keyBounds[i].contains(x, y)) {
					return i;
				}
			}
			for (int i = 0; i < numberOfNotes; i++) {
				if (!isBlack[i] && keyBounds[i].contains(x, y)) {
					return i;
				}
			}
			return -1;
		}

		// Recompute the cached bounding rectangles from the key paths
		private void updateKeyBounds() {
			for (int i = 0; i < numberOfNotes; i++) {
				keys.get(i).computeBounds(keyBounds[i], true);
			}
		}

		// Exchange the two rows of keys
		void swapRows() {
			for (int i = 0; i < (numberOfNotes / 2); i++) {
				Collections.swap(keys, i, i + 12);
			}
			updateKeyBounds();
			invalidate();
		}

		// Number of notes currently sounding with real energy, not counting the keys in 'ignore'
		private int soundingNotes(long now, int ignore) {
			int count = 0;
			for (int i = 0; i < numberOfNotes; i++) {
				if ((ignore & (1 << i)) == 0 && startTime[i] != 0
						&& now - startTime[i] < OVERLAP_WINDOW_MS) {
					count++;
				}
			}
			return count;
		}

		// Let a looped note finish its current pass (it ends on a zero crossing, so
		// there is no click) and make sure it is stopped shortly after
		private void endLoopedNote(final int streamId) {
			pianoSounds.setLoop(streamId, 0);
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (pianoSounds != null) {
						pianoSounds.stop(streamId);
					}
				}
			}, LOOP_END_STOP_MS);
		}

		// Stop all sounds and release all keys
		void silence() {
			if (pianoSounds != null) {
				for (int i = 0; i < numberOfNotes; i++) {
					if (playIds[i] != 0) {
						pianoSounds.stop(playIds[i]);
						playIds[i] = 0;
					}
					startTime[i] = 0;
				}
			}
			if (pressedMask != 0) {
				pressedMask = 0;
				invalidate();
			}
		}

		private void playKey(int i, float volume, long now) {
			try {
				playIds[i] = pianoSounds.play(soundIds.get(i), volume, volume, 1, loopsWhileHeld() ? -1 : 0, 1.0f);
				startTime[i] = now;
			} catch (Exception e) {
				Log.e("PianoLayout.onTouchEvent", "Key " + i + " not playable!");
			}
		}

		// React when the user touches, stops touching, or touches in a new way,
		// the piano keyboard
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			int actionCode = event.getActionMasked();
			if (!(actionCode == MotionEvent.ACTION_DOWN ||
					actionCode == MotionEvent.ACTION_POINTER_DOWN ||
					actionCode == MotionEvent.ACTION_UP ||
					actionCode == MotionEvent.ACTION_POINTER_UP ||
					actionCode == MotionEvent.ACTION_MOVE ||
					actionCode == MotionEvent.ACTION_CANCEL)) {
				return false;
			}
			// Keys held down after this event. A cancelled gesture releases everything.
			int newMask = 0;
			if (actionCode != MotionEvent.ACTION_CANCEL) {
				// The pointer that just went up no longer presses anything
				int skipIndex = (actionCode == MotionEvent.ACTION_UP ||
						actionCode == MotionEvent.ACTION_POINTER_UP) ? event.getActionIndex() : -1;
				for (int p = 0; p < event.getPointerCount(); p++) {
					if (p == skipIndex) {
						continue;
					}
					int key = keyAt(event.getX(p), event.getY(p));
					if (key >= 0) {
						newMask |= 1 << key;
					}
				}
			}
			int justPressed = newMask & ~pressedMask;
			int justReleased = pressedMask & ~newMask;
			// Play the sound of each newly pressed key. Keys pressed together
			// (a chord) share the same volume, which depends on how many notes sound at once.
			if (justPressed != 0) {
				long now = SystemClock.uptimeMillis();
				int total = Math.min(numberOfNotes,
						soundingNotes(now, justPressed) + Integer.bitCount(justPressed));
				float volume = gainTable[total];
				for (int i = 0, bits = justPressed; bits != 0; i++, bits >>>= 1) {
					if ((bits & 1) != 0) {
						playKey(i, volume, now);
					}
				}
			}
			// Stop the sound of released keys
			if (justReleased != 0) {
				boolean loops = loopsWhileHeld();
				if (loops || damper.equals(
						MainActivity.this.getString(R.string.pref_damper_dampen_value))) {
					for (int i = 0; justReleased != 0; i++, justReleased >>>= 1) {
						if ((justReleased & 1) != 0 && playIds[i] != 0) {
							if (loops) {
								endLoopedNote(playIds[i]);
							} else {
								pianoSounds.stop(playIds[i]);
							}
							startTime[i] = 0; // no longer sounding
						}
					}
				}
			}
			// Redraw only when something changed
			if (newMask != pressedMask) {
				pressedMask = newMask;
				invalidate();
			}
			return true;
		}

		// Create shapes
		private void createShapes() {
			asymCFWhiteKey.reset();
			asymEBWhiteKey.reset();
			symmetricWhiteKey.reset();
			blackKey.reset();
			// Define the shapes
			// ___
			// |  |
			// |  |_ 
			// |    |
			// |____|
			//
			asymCFWhiteKey.moveTo(0.0f, 0.0f);
			asymCFWhiteKey.lineTo((float) pianoWidth * 17 / 168, 0.0f);
			asymCFWhiteKey.lineTo((float) pianoWidth * 17 / 168, (float) pianoHeight / 4);
			asymCFWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 4);
			asymCFWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
			asymCFWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
			asymCFWhiteKey.lineTo(0.0f, 0.0f);
			//    __
			//   |  |
			//  _|  | 
			// |    |
			// |____|
			//
			asymEBWhiteKey.moveTo((float) pianoWidth / 24, 0.0f);
			asymEBWhiteKey.lineTo((float) pianoWidth / 7, 0.0f);
			asymEBWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
			asymEBWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
			asymEBWhiteKey.lineTo(0.0f, (float) pianoHeight / 4);
			asymEBWhiteKey.lineTo((float) pianoWidth / 24, (float) pianoHeight / 4);
			asymEBWhiteKey.lineTo((float) pianoWidth / 24, 0.0f);
			asymEBWhiteKey.offset((float) pianoWidth * 2 / 7, 0.0f);
			//  __
			// |  |
			// |  |
			// |__|
			//
			blackKey.addRect(0.0f, 0.0f, (float) pianoWidth / 12, (float) pianoHeight / 4, Path.Direction.CW);
			blackKey.offset((float) pianoWidth * 17 / 168, 0.0f);
			//
			//    __
			//   |  |
			//  _|  |_
			// |      |
			// |______|
			//
			symmetricWhiteKey.moveTo((float) pianoWidth / 24, 0.0f);
			symmetricWhiteKey.lineTo((float) pianoWidth * 17 / 168, 0.0f);
			symmetricWhiteKey.lineTo((float) pianoWidth * 17 / 168, (float) pianoHeight / 4);
			symmetricWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 4);
			symmetricWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
			symmetricWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
			symmetricWhiteKey.lineTo(0.0f, (float) pianoHeight / 4);
			symmetricWhiteKey.lineTo((float) pianoWidth / 24, (float) pianoHeight / 4);
			symmetricWhiteKey.lineTo((float) pianoWidth / 24, 0.0f);
			symmetricWhiteKey.offset((float) pianoWidth / 7, 0.0f);
			// Save the shapes as keys
			for (int i = 0; i < (numberOfNotes / 2); i++) {
				if (blackKeyNoteNumbers.contains(i)) {
					// Black keys
					switch (i) {
					case 3: // D# = Eb
					case 8: // G# = Ab 
					case 10: // A# = Bb
						blackKey.offset((float) pianoWidth / 7, 0.0f);
						break;
					case 6: // F# = Gb
						blackKey.offset((float) pianoWidth * 2 / 7, 0.0f);
						break;
					default:
						break;
					}
					keys.get(i).set(blackKey);
					blackKey.offset(0.0f, (float) pianoHeight / 2);
					keys.get(i + 12).set(blackKey);
					blackKey.offset(0.0f, (float) -pianoHeight / 2);
				} else {
					// White keys
					if ((i == 0) || (i == 5)) {
						// CF key
						if (i == 5) { // F
							asymCFWhiteKey.offset((float) pianoWidth * 3 / 7, 0.0f);
						}
						keys.get(i).set(asymCFWhiteKey);
						asymCFWhiteKey.offset(0.0f, (float) pianoHeight / 2);
						keys.get(i + 12).set(asymCFWhiteKey);
						asymCFWhiteKey.offset(0.0f, (float) -pianoHeight / 2);
					}
					if ((i == 2) || (i == 7) || (i == 9)) {
						// symmetric key
						switch (i) {
						case 7: // G
							symmetricWhiteKey.offset((float) pianoWidth * 3 / 7, 0.0f);
							break;
						case 9: // A
							symmetricWhiteKey.offset((float) pianoWidth / 7, 0.0f);
							break;
						default:
							break;
						}
						keys.get(i).set(symmetricWhiteKey);
						symmetricWhiteKey.offset(0.0f, (float) pianoHeight / 2);
						keys.get(i + 12).set(symmetricWhiteKey);
						symmetricWhiteKey.offset(0.0f, (float) -pianoHeight / 2);					
					}
					if ((i == 4) || (i == 11)) {
						// EB key
						if (i == 11) { // B
							asymEBWhiteKey.offset((float) pianoWidth * 4 / 7, 0.0f);
						}
						keys.get(i).set(asymEBWhiteKey);
						asymEBWhiteKey.offset(0.0f, (float) pianoHeight / 2);
						keys.get(i + 12).set(asymEBWhiteKey);
						asymEBWhiteKey.offset(0.0f, (float) -pianoHeight / 2);
					}
				}
			}
			// Exchange rows depending on preference
			if (lowerOctavePosition.equals(
					MainActivity.this.getString(
							R.string.pref_rows_higher_octave_in_upper_row_value))) {
				// Swap rows
				swapRows();
			}
			updateKeyBounds();
		}

		// Free resources
		public void destroy() {
			handler.removeCallbacksAndMessages(null);
			if (pianoSounds != null) {
				pianoSounds.release(); // release the sound pool resources
			}
		}

		// Deal with view size changes
		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			pianoWidth = w;
			pianoHeight = h;
			this.createShapes();
			buildNoteLabels();
            // Inform the user about the key combination to access the menu
            // (because the keyboard takes up the whole screen)
            Toast.makeText(MainActivity.this.getApplicationContext(), MainActivity.this.getString(R.string.piano_hint),
                            Toast.LENGTH_LONG).show();
		}
	}
}
