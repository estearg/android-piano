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

package org.esteban.piano;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class PianoLayout extends View {

	// TODO Presently there is only a fixed number of keys (24), arranged
	// in two sections, spanning octaves 3 and 4 (i.e. range = C3..B4)
	// TODO Change orientation when width > height
	// TODO Use the touch pressure values to choose between sound pools "pp", "mf" and "ff" 
	
	// list of formerly pressed keys
	private static Set<Integer> old_pressed;
	// a Paint object is needed to be able to draw anything
	private Paint pianoPaint;
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
	private ArrayList<Integer> soundIds;
	// objects needed to draw outside of onDraw
	private Bitmap pianoBitmap;
	private Canvas pianoCanvas;
	// list of keys that must be shown as pressed
	private ArrayList<Integer> justPressedKeys;
	
	// Constructor
	public PianoLayout(Context context) {
		super(context);
		
		// Initialization
		pianoPaint = new Paint();
		pianoPaint.setStrokeWidth(2.0f); // stroke width used when Style is Stroke or StrokeAndFill, in pixels?
		symmetricWhiteKey = new Path();
		asymCFWhiteKey = new Path();
		asymEBWhiteKey = new Path();
		blackKey = new Path();
		keys = new ArrayList<Path>();
		numberOfNotes = 24; // two octaves
		numberOfBlackKeys = 10; // two octaves
		blackKeyNoteNumbers = new ArrayList<Integer>();
		old_pressed = new HashSet<Integer>();
		justPressedKeys = new ArrayList<Integer>();
		pianoSounds = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
		soundIds = new ArrayList<Integer>();
		for (int i = 0; i < numberOfNotes; i++) {
			// Load the sound of each note, saving the identifications
			int resourceId = context.getResources().getIdentifier("note" + i,
					"raw", context.getPackageName()); // audio files saved as res/raw/note0.ogg etc.
			soundIds.add(pianoSounds.load(context, resourceId, 1));
			// Create key objects and record the note numbers of the black keys
			keys.add(new Path());
			switch (i % 12) {
			case 1:
			case 3:
			case 6:
			case 8:
			case 10:
				blackKeyNoteNumbers.add(Integer.valueOf(i));
			}
		}
		// Get view dimensions (fixed)
		Display display = ((WindowManager) 
				context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		DisplayMetrics dm = new DisplayMetrics();
		display.getMetrics(dm);
		pianoWidth = dm.widthPixels;
		pianoHeight = dm.heightPixels;
		// Create key shapes and assign them to the keys array
		createShapes();
		// Create canvas and its associated bitmap
		pianoCanvas = new Canvas();
		pianoBitmap = Bitmap.createBitmap(pianoWidth, pianoHeight, Bitmap.Config.ARGB_8888);
		pianoCanvas.setBitmap(pianoBitmap);
	}

	// Draw on canvas, from bitmap
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// draw the keyboard on the bitmap
		drawOnBitmap();
		// draw the bitmap to the real canvas c
		canvas.drawBitmap(pianoBitmap, 0, 0, null);
	}

	// React when the user touches, stops touching, or touches in a new way,
	// the piano keyboard
	@SuppressLint("UseSparseArrays")
	public boolean onTouchEvent(MotionEvent event){
		// React only to some actions, ignoring the others
		int action = event.getAction();
		int actionCode = action & MotionEvent.ACTION_MASK;
		if (!(
				actionCode == MotionEvent.ACTION_DOWN ||
				actionCode == MotionEvent.ACTION_POINTER_DOWN ||
				actionCode == MotionEvent.ACTION_UP ||
				actionCode == MotionEvent.ACTION_POINTER_UP ||
				actionCode == MotionEvent.ACTION_MOVE
				)) {
			return false;
		}
		// Use of maps to keep track of:
		//       all affected keys:  pressed_map
		//       pressed keys:       new_pressed
		Set<Integer> new_pressed = new HashSet<Integer>();
		HashMap<Integer, Float> pressed_map = new HashMap<Integer, Float>();
		// For every pointer, see which key the point belongs to
		for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
			// Get coordinates of the point for the current pointer
			int x = (int)event.getX(pointerIndex);
			int y = (int)event.getY(pointerIndex);
			int keyWasFound = 0; // flag to do something with a key
			int noteFound = 0; // variable to store the note number of the affected key
			// Check black keys first, because white keys' bounding rectangles overlap with
			// black keys
			int blackKeyFound = 0; // flag to skip white keys check
			// Check black keys
			for (int i = 0; i < numberOfBlackKeys; i++) {
				RectF bounds = new RectF();
				keys.get(blackKeyNoteNumbers.get(i)).computeBounds(bounds, true);
				if (bounds.contains((float) x, (float) y)) {
					blackKeyFound = 1;
					keyWasFound = 1;
					noteFound = blackKeyNoteNumbers.get(i);
				}
			}
			// Check white keys, if necessary
			for (int i = 0; (i < numberOfNotes) && (blackKeyFound == 0); i++) {
				if (blackKeyNoteNumbers.contains(i)) {
					continue; // skip black keys -already checked
				}
				RectF bounds = new RectF();
				keys.get(i).computeBounds(bounds, true);
				if (bounds.contains((float) x, (float) y)) {
					keyWasFound = 1;
					noteFound = i;
				}
			}
			// Save found key
			if (keyWasFound == 1) {
				// save note number and pressure in all affected keys map
				if (pressed_map.containsKey(noteFound)) {
					pressed_map.put(noteFound,
							Math.max(event.getPressure(pointerIndex),
									pressed_map.get(noteFound)));
				} else {
					pressed_map.put(noteFound, event.getPressure(pointerIndex));
				}
				// if appropriate, save note number in pressed keys map
				if (!(pointerIndex == event.getActionIndex() && (
						actionCode == MotionEvent.ACTION_UP ||
						actionCode == MotionEvent.ACTION_POINTER_UP ))) {
					new_pressed.add(noteFound);
				}
			}
		}
		// Map of newly pressed keys (pressed keys that weren't already pressed)
		Set<Integer> just_pressed = new HashSet<Integer>(new_pressed);
		just_pressed.removeAll(old_pressed);
		// Play the sound of each newly pressed key
		Iterator<Integer> it = just_pressed.iterator();
		justPressedKeys.clear(); // empty the list of just pressed keys (used to draw them)
		while (it.hasNext()) {
			int i = it.next();
			justPressedKeys.add(i); // add the key (note number) to the list so that it can be shown as pressed
			try {
				pianoSounds.play(soundIds.get(i), 1.0f, 1.0f, 1, 0, 1.0f);
			} catch (Exception e) {
				Log.e("PianoLayout.onTouchEvent", "Key " + i + " not playable!");
			}
		}
		// Stop the sound of released keys
//		Set<Integer> just_released = new HashSet<Integer>(old_pressed);
//		just_released.removeAll(new_pressed);
//		it = just_released.iterator();
//		while (it.hasNext()) {
//			int i = it.next();
//			pianoSounds.stop(soundIds.get(i));
//		}
		// Update map of pressed keys
		old_pressed = new_pressed;
		// Force a call to onDraw() to give visual feedback to the user
		this.invalidate();

		return true;
	}

	// Create shapes
	private void createShapes() {
		// Define the shapes
		//
		// ________________
		// |      _________|
		// |_____|
		//
		asymCFWhiteKey.lineTo((float) pianoWidth / 2, 0.0f);
		asymCFWhiteKey.lineTo((float) pianoWidth / 2, (float) pianoHeight * 17 / 168);
		asymCFWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight * 17 / 168);
		asymCFWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight / 7);
		asymCFWhiteKey.lineTo(0.0f, (float) pianoHeight / 7);
		asymCFWhiteKey.lineTo(0.0f, 0.0f);
		//
		// ______
		// |     |_________
		// |_______________|
		//
		asymEBWhiteKey.lineTo((float) pianoWidth / 4, 0.0f);
		asymEBWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight / 24);
		asymEBWhiteKey.lineTo((float) pianoWidth / 2, (float) pianoHeight / 24);
		asymEBWhiteKey.lineTo((float) pianoWidth / 2, (float) pianoHeight / 7);
		asymEBWhiteKey.lineTo(0.0f, (float) pianoHeight / 7);
		asymEBWhiteKey.lineTo(0.0f, 0.0f);
		asymEBWhiteKey.offset(0.0f, (float) pianoHeight * 2 / 7);
		// __________
		// |_________|
		//
		blackKey.addRect(0.0f, 0.0f, (float) pianoWidth / 4, (float) pianoHeight / 12, Path.Direction.CW);
		blackKey.offset((float) pianoWidth / 4, (float) pianoHeight * 17 / 168);
		//
		// ______
		// |     |_________
		// |      _________|
		// |_____|
		//
		symmetricWhiteKey.lineTo((float) pianoWidth / 4, 0.0f);
		symmetricWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight / 24);
		symmetricWhiteKey.lineTo((float) pianoWidth / 2, (float) pianoHeight / 24);
		symmetricWhiteKey.lineTo((float) pianoWidth / 2, (float) pianoHeight * 17 / 168);
		symmetricWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight * 17 / 168);
		symmetricWhiteKey.lineTo((float) pianoWidth / 4, (float) pianoHeight / 7);
		symmetricWhiteKey.lineTo(0.0f, (float) pianoHeight / 7);
		symmetricWhiteKey.lineTo(0.0f, 0.0f);
		symmetricWhiteKey.offset(0.0f, (float) pianoHeight / 7);
		// Save the shapes as keys
		for (int i = 0; i < (numberOfNotes / 2); i++) {
			if (blackKeyNoteNumbers.contains(i)) {
				// Black keys
				switch (i) {
				case 3: // D# = Eb
				case 8: // G# = Ab 
				case 10: // A# = Bb
					blackKey.offset(0.0f, (float) pianoHeight / 7);
					break;
				case 6: // F# = Gb
					blackKey.offset(0.0f, (float) pianoHeight * 2 / 7);
					break;
				}
				keys.get(i).set(blackKey);
				blackKey.offset((float) pianoWidth / 2, 0.0f);
				keys.get(i + 12).set(blackKey);
				blackKey.offset((float) -pianoWidth / 2, 0.0f);
			} else {
				// White keys
				if ((i == 0) || (i == 5)) {
					// CF key
					if (i == 5) { // F
						asymCFWhiteKey.offset(0.0f, (float) pianoHeight * 3 / 7);
					}
					keys.get(i).set(asymCFWhiteKey);
					asymCFWhiteKey.offset((float) pianoWidth / 2, 0.0f);
					keys.get(i + 12).set(asymCFWhiteKey);
					asymCFWhiteKey.offset((float) -pianoWidth / 2, 0.0f);
				}
				if ((i == 2) || (i == 7) || (i == 9)) {
					// symmetric key
					switch (i) {
					case 7: // G
						symmetricWhiteKey.offset(0.0f, (float) pianoHeight * 3 / 7);
						break;
					case 9: // A
						symmetricWhiteKey.offset(0.0f, (float) pianoHeight / 7);
						break;
					}
					keys.get(i).set(symmetricWhiteKey);
					symmetricWhiteKey.offset((float) pianoWidth / 2, 0.0f);
					keys.get(i + 12).set(symmetricWhiteKey);
					symmetricWhiteKey.offset((float) -pianoWidth / 2, 0.0f);					
				}
				if ((i == 4) || (i == 11)) {
					// EB key
					if (i == 11) { // B
						asymEBWhiteKey.offset(0.0f, (float) pianoHeight * 4 / 7);
					}
					keys.get(i).set(asymEBWhiteKey);
					asymEBWhiteKey.offset((float) pianoWidth / 2, 0.0f);
					keys.get(i + 12).set(asymEBWhiteKey);
					asymEBWhiteKey.offset((float) -pianoWidth / 2, 0.0f);
				}
			}
		}
	}
	
	// Draw on bitmap
	protected void drawOnBitmap() {
		// Erase the canvas
		pianoCanvas.drawColor(Color.WHITE);
		// Draw the keys
		for (int i = 0; i < numberOfNotes; i++) {
			if (blackKeyNoteNumbers.contains(i)) {
				// Black keys
				pianoPaint.setStyle(Paint.Style.FILL); // all filled; ignore all stroke-related settings in the paint
				if (justPressedKeys.contains(i)) {
					pianoPaint.setColor(Color.LTGRAY);
				} else {
					pianoPaint.setColor(Color.BLACK);
				}
			} else {
				// White keys
				if (justPressedKeys.contains(i)) {
					pianoPaint.setStyle(Paint.Style.FILL); // all filled; ignore all stroke-related settings in the paint
					pianoPaint.setColor(Color.DKGRAY);
				} else {
					pianoPaint.setStyle(Paint.Style.STROKE); // stroked
					pianoPaint.setColor(Color.BLACK);
				}
			}
			pianoCanvas.drawPath(keys.get(i), pianoPaint);
		}
	}

	// Release resources
	public void destroy() {
		if (pianoBitmap != null) {
			pianoBitmap.recycle(); // mark the bitmap as dead
		}
		if (pianoSounds != null) {
			pianoSounds.release(); // release the sound pool resources
		}
	}
}
