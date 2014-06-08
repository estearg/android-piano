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

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

	// Custom view
	PianoLayout pianoView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Show the view in full screen mode without title
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
		// Always portrait orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		// Set view
		pianoView = new PianoLayout(this.getApplicationContext());
		setContentView(pianoView);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		pianoView.destroy();
	}
	
	// Initialize options menu contents
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}
}
