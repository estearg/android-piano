/*
 * Front page of the app: the title, the choice of instrument sound, the buttons that
 * lead to the piano and the settings, and a banner ad at the bottom.
 */
package org.esteban.piano;

import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RadioGroup;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

public class HomeActivity extends Activity {

	// Instruments, in the same order as their buttons in the layout
	private static final String[] INSTRUMENTS = { "piano", "organ", "epiano", "musicbox", "harpsichord" };
	private static final int[] BUTTON_IDS = { R.id.instr_piano, R.id.instr_organ, R.id.instr_epiano,
			R.id.instr_musicbox, R.id.instr_harpsichord };
	// Preview note: C4 is note12 in every instrument
	private static final int PREVIEW_NOTE = 12;

	private AdView adView;
	private SoundPool preview;
	private final HashMap<String, Integer> previewIds = new HashMap<String, Integer>();
	private int pendingPreview = 0; // sample waiting to be played when it finishes loading
	private int previewStream = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		RadioGroup group = (RadioGroup) findViewById(R.id.instrument_group);
		String current = prefs.getString("pref_instrument", "piano");
		for (int i = 0; i < INSTRUMENTS.length; i++) {
			if (INSTRUMENTS[i].equals(current)) {
				group.check(BUTTON_IDS[i]);
			}
		}
		if (group.getCheckedRadioButtonId() == -1) {
			group.check(R.id.instr_piano);
		}
		group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup g, int checkedId) {
				for (int i = 0; i < BUTTON_IDS.length; i++) {
					if (BUTTON_IDS[i] == checkedId) {
						prefs.edit().putString("pref_instrument", INSTRUMENTS[i]).apply();
						playPreview(INSTRUMENTS[i]);
					}
				}
			}
		});

		findViewById(R.id.button_play).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(HomeActivity.this, MainActivity.class));
			}
		});
		findViewById(R.id.button_settings).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
			}
		});

		setUpBannerAd();
	}

	// Load the banner at the bottom of the page. It is shown only once an ad has loaded.
	private void setUpBannerAd() {
		MobileAds.initialize(this, new OnInitializationCompleteListener() {
			@Override
			public void onInitializationComplete(InitializationStatus status) {
			}
		});
		final FrameLayout container = getAdContainer();
		adView = new AdView(this);
		adView.setAdUnitId(getString(R.string.admob_banner_unit_id));
		adView.setAdSize(AdSize.BANNER);
		adView.setAdListener(new AdListener() {
			@Override
			public void onAdLoaded() {
				container.setVisibility(View.VISIBLE);
			}

			@Override
			public void onAdFailedToLoad(LoadAdError error) {
				container.setVisibility(View.GONE);
			}
		});
		container.addView(adView);
		adView.loadAd(new AdRequest.Builder().build());
	}

	// Play a note of the instrument so the user hears what it sounds like
	private void playPreview(String instrument) {
		if (preview == null) {
			if (Build.VERSION.SDK_INT >= 21) {
				preview = new SoundPool.Builder()
						.setMaxStreams(2)
						.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_GAME)
								.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
								.build())
						.build();
			} else {
				preview = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
			}
			preview.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
				@Override
				public void onLoadComplete(SoundPool pool, int sampleId, int status) {
					if (status == 0 && sampleId == pendingPreview) {
						startPreview(sampleId);
					}
				}
			});
		}
		if (previewStream != 0) {
			preview.stop(previewStream);
			previewStream = 0;
		}
		Integer id = previewIds.get(instrument);
		if (id == null) {
			String prefix = instrument.equals("piano") ? "note" : instrument + "_note";
			int res = getResources().getIdentifier(prefix + PREVIEW_NOTE, "raw", getPackageName());
			id = preview.load(this, res, 1);
			previewIds.put(instrument, id);
			pendingPreview = id; // played by the load listener
		} else {
			pendingPreview = id;
			startPreview(id);
		}
	}

	private void startPreview(int sampleId) {
		// the organ sound is a short loop: repeat it a few times for the preview
		Integer organId = previewIds.get("organ");
		boolean looped = organId != null && organId == sampleId;
		previewStream = preview.play(sampleId, 1.0f, 1.0f, 1, looped ? 3 : 0, 1.0f);
	}

	protected FrameLayout getAdContainer() {
		return (FrameLayout) findViewById(R.id.ad_container);
	}

	@Override
	protected void onPause() {
		if (adView != null) {
			adView.pause();
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (adView != null) {
			adView.resume();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (preview != null && previewStream != 0) {
			preview.stop(previewStream);
			previewStream = 0;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (adView != null) {
			adView.destroy();
			adView = null;
		}
		if (preview != null) {
			preview.release();
			preview = null;
		}
	}
}
