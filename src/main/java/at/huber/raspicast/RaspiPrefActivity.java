/*
 * Copyright (C) 2020  Benjamin Huber
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package at.huber.raspicast;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;

import at.huber.raspicast.dialogs.DirectoryChooseDialog;
import at.huber.raspicast.dialogs.PrefDialog;
import at.huber.raspicast.dialogs.PrefDialogEdit;

public class RaspiPrefActivity extends PreferenceActivity implements DirectoryChooseDialog.Callbacks {

	private static boolean launchYtHttp;
	private static int launchYtQuality;
	private SharedPreferences mSharedPreferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (!RaspiListActivity.killOnExit){
			OverflowMenuFragActivity.lightTheme=getSharedPreferences(Constants.PREF_FILE_NAME,
					MODE_PRIVATE).getBoolean(Constants.PREF_LIGHT_THEME,
					Constants.LIGHT_THEME_DEFAULT);
		}
		if (OverflowMenuFragActivity.lightTheme){
			setTheme(R.style.RaspicastTheme_HoloLight);
		}
		super.onCreate(savedInstanceState);
		PreferenceManager prefMgr=getPreferenceManager();
		prefMgr.setSharedPreferencesName(Constants.PREF_FILE_NAME);
		prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setTitle(R.string.advanced_options);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			getActionBar().setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
		}
		
		mSharedPreferences=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE);
		addPreferencesFromResource(R.xml.preferences);
		

		Preference prefYtQuality= findPreference(Constants.PREF_YOUTUBE_QUALITY);
		final String[] qualities= { "HD", "SD", "Audio only" };
		launchYtQuality=mSharedPreferences.getInt(Constants.PREF_YOUTUBE_QUALITY, 0);
		launchYtHttp=mSharedPreferences.getBoolean(Constants.PREF_YOUTUBE_HTTP, false);
		prefYtQuality.setSummary(qualities[launchYtQuality]);
		prefYtQuality.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				new PrefDialog(qualities, preference.getKey(), false).show(getFragmentManager(), "test");
				return true;
			}
		});
		
		Preference prefMaxPlaylistSize= findPreference(Constants.PREF_MAX_PLAYLIST_SIZE);
		prefMaxPlaylistSize.setSummary(mSharedPreferences.getInt(Constants.PREF_MAX_PLAYLIST_SIZE, 25) + "");
		prefMaxPlaylistSize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				if (!strValue.equals("")){
					int value=Integer.parseInt((String) newValue);
					if (value > 0 && value <= 100){
						Editor edit=mSharedPreferences.edit();
						edit.putInt(preference.getKey(), value);
						edit.apply();
						preference.setSummary(newValue + "");
						return true;
					}
				}
				return false;
			}
		});
		
		final Preference prefCustomCMD= findPreference(Constants.PREF_CUSTOM_CMD);
		String customCMDTx = mSharedPreferences.getString(Constants.PREF_CUSTOM_CMD, null);
		if(customCMDTx==null || customCMDTx.equals(""))
			prefCustomCMD.setSummary("Long click to edit");
		else
			prefCustomCMD.setSummary(customCMDTx);
		
		prefCustomCMD.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				String cmd = mSharedPreferences.getString(Constants.PREF_CUSTOM_CMD, null);
				if(cmd != null && !cmd.equals(""))
					SshConnection.executeCommand(getApplicationContext(), cmd, true);
				return true;
			}
		});
		
		final Preference prefCustomCMD2= findPreference(Constants.PREF_CUSTOM_CMD2);
		String customCMDTx2 = mSharedPreferences.getString(Constants.PREF_CUSTOM_CMD2, null);
		if(customCMDTx2==null || customCMDTx2.equals(""))
			prefCustomCMD2.setSummary("Long click to edit");
		else
			prefCustomCMD2.setSummary(customCMDTx2);
		
		prefCustomCMD2.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				String cmd = mSharedPreferences.getString(Constants.PREF_CUSTOM_CMD2, null);
				if(cmd != null && !cmd.equals(""))
					SshConnection.executeCommand(getApplicationContext(), cmd, true);
				return true;
			}
		});
		
		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				if(position == 0){
					new PrefDialogEdit(Constants.PREF_CUSTOM_CMD).show(getFragmentManager(), "edit");
					return true;
				}else if(position == 1){
					new PrefDialogEdit(Constants.PREF_CUSTOM_CMD2).show(getFragmentManager(), "edit");
					return true;
				}
				return false;
			}
		});
		
		
		final Preference prefPlaylistDir= findPreference(Constants.PREF_INPUT_DIR_NAME);
		final String strPLaylistDir=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE)
				.getString(Constants.PREF_INPUT_DIR_NAME, "/");
		prefPlaylistDir.setSummary(Html.fromHtml(strPLaylistDir.replace("/", "<big>/</big>\u200B")));
		final DirectoryChooseDialog dial=new DirectoryChooseDialog(strPLaylistDir);
		prefPlaylistDir.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				dial.show(getFragmentManager(), "playlistDir");
				return true;
			}
		});

		final boolean isFromAT;
		if (getResources().getConfiguration().locale.getCountry().equals("AT")
				|| getResources().getConfiguration().locale.getCountry().equals("DE")){
			boolean hasAt=false;
			for(String l : Resources.getSystem().getAssets().getLocales()){
				if (l.equals("de_AT")|| l.equals("de-AT")){
					hasAt=true;
				}
			}
			isFromAT = (hasAt && getResources().getConfiguration().locale.getCountry().equals("AT"));
		}else{
			isFromAT=false;
		}
		if (!isFromAT){
			Preference prefA1= findPreference(Constants.PREF_SHOW_A1_TV);
			PreferenceCategory mCategory=(PreferenceCategory) findPreference("general");
			mCategory.removePreference(prefA1);
		}

		Preference prefLive= findPreference(Constants.PREF_LIVE_OPTIONS);
		
		final String[] liveOptions= new String[3];
		liveOptions[0]=getResources().getString(R.string.for_rtp_udp);
		liveOptions[1]=getResources().getString(R.string.for_all_streams);
		liveOptions[2]=getResources().getString(R.string.never);
		prefLive.setSummary(liveOptions[mSharedPreferences.getInt(Constants.PREF_LIVE_OPTIONS, 0)]);
		prefLive.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				new PrefDialog(liveOptions, preference.getKey(), false).show(getFragmentManager(), "test");
				return true;
			}
		});
		Preference prefTheme= findPreference(Constants.PREF_LIGHT_THEME);
		prefTheme.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				RaspiListActivity.killOnExit = ((Boolean) newValue) != RaspiListActivity.lightTheme;
				return true;
			}
		});

		Preference prefPlayOption= findPreference(Constants.PREF_DEFAULT_PLAY_OPTION);
		final String[] playOpt= new String[2];
		playOpt[0]=getResources().getString(R.string.play);
		playOpt[1]=getResources().getString(R.string.queue);
		prefPlayOption.setSummary(playOpt[mSharedPreferences.getInt(Constants.PREF_DEFAULT_PLAY_OPTION,
				0)]);
		prefPlayOption.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				new PrefDialog(playOpt, preference.getKey(), false).show(getFragmentManager(), "test");
				return true;
			}
		});
		Preference prefAudioOffset= findPreference(Constants.PREF_AUDIO_OFFSET);
		prefAudioOffset.setSummary(mSharedPreferences.getInt(Constants.PREF_AUDIO_OFFSET, 0) + " dB");
		prefAudioOffset.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				if (!value.equals("") && !value.equals("-")) {
					Editor edit = mSharedPreferences.edit();
					edit.putInt(preference.getKey(), Integer.parseInt(value));
					edit.apply();
					preference.setSummary(newValue + " dB");
					SshConnection.volumeOffset = Integer.parseInt(value) * 100;
					return true;
				}
				return false;
			}
		});
		
		Preference prefOmxOptions= findPreference(Constants.PREF_OMXPLAYER_OPTIONS);
		String omxOptSummary = mSharedPreferences.getString(Constants.PREF_OMXPLAYER_OPTIONS, "");
		if(omxOptSummary.equals(""))
			omxOptSummary="May break playback";
		prefOmxOptions.setSummary(omxOptSummary);
		prefOmxOptions.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				Editor edit=mSharedPreferences.edit();
				edit.putString(preference.getKey(), strValue);
				edit.apply();
				if(strValue.equals(""))
					preference.setSummary("May break playback");
				else
					preference.setSummary(strValue);
				return true;
			}
		});

		final Preference prefAlsaDevice= findPreference(Constants.PREF_ALSA_DEVICE);
		final String alsaDevice = mSharedPreferences.getString(Constants.PREF_ALSA_DEVICE, "");
		if(alsaDevice.equals(""))
			prefAlsaDevice.setSummary("default");
		else
			prefAlsaDevice.setSummary(alsaDevice);
		prefAlsaDevice.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				Editor edit=mSharedPreferences.edit();
				edit.putString(preference.getKey(), strValue);
				edit.apply();
				if(strValue.equals("")){
					preference.setSummary("default");
					if (SshConnection.audioOuputDevice.startsWith("alsa"))
						SshConnection.audioOuputDevice = "alsa";
				}else {
					preference.setSummary(strValue);
					if (SshConnection.audioOuputDevice.startsWith("alsa"))
						SshConnection.audioOuputDevice = "alsa:" + strValue;
				}
				return true;
			}
		});
		
		Preference prefOmxivOptions= findPreference(Constants.PREF_OMXIV_OPTIONS);
		prefOmxivOptions.setSummary(mSharedPreferences.getString(Constants.PREF_OMXIV_OPTIONS,""));
		prefOmxivOptions.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {		
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				Editor edit=mSharedPreferences.edit();
				edit.putString(preference.getKey(), strValue);
				edit.apply();
				preference.setSummary(strValue);
				return true;
			}
		});
		
		Preference prefTempDir= findPreference(Constants.PREF_TEMP_DIR);
		prefTempDir.setSummary(mSharedPreferences.getString(Constants.PREF_TEMP_DIR,"/dev/shm"));
		prefTempDir.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {		
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				Editor edit=mSharedPreferences.edit();
				edit.putString(preference.getKey(), strValue);
				edit.apply();
				SshConnection.TEMP_FILES_LOCATION = strValue;
				SshConnection.updateTmpFileLoc();
				SshConnection.changeLooping(getApplicationContext());
				SshConnection.stopCurrentProgramm(getApplicationContext());
				preference.setSummary(strValue);
				return true;
			}
		});

		Preference prefSubtitleSize= findPreference(Constants.PREF_SUBTITLE_SIZE);
		prefSubtitleSize.setSummary(mSharedPreferences.getInt(Constants.PREF_SUBTITLE_SIZE, 55) + "");
		prefSubtitleSize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				if (!strValue.equals("")){
					int value=Integer.parseInt((String) newValue);
					if (value > 0 && value < 1000){
						Editor edit=mSharedPreferences.edit();
						edit.putInt(preference.getKey(), value);
						edit.apply();
						preference.setSummary(newValue + "");
						return true;
					}
				}
				return false;
			}
		});

		Preference prefStreamPort= findPreference(Constants.PREF_STREAM_PORT);
		prefStreamPort.setSummary(mSharedPreferences.getInt(Constants.PREF_STREAM_PORT, 8080) + "");
		prefStreamPort.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String strValue=(String) newValue;
				if (!strValue.equals("")){
					int value=Integer.parseInt((String) newValue);
					if (value > 0 && value < 64000){
						Editor edit=mSharedPreferences.edit();
						edit.putInt(preference.getKey(), value);
						edit.apply();
						preference.setSummary(newValue + "");
						return true;
					}
				}
				return false;
			}
		});

		Preference prefAlignment= findPreference(Constants.PREF_SUBTITLES_LEFT);
		final String[] subtitleAlign=new String[2];
		subtitleAlign[0]=getResources().getString(R.string.left);
		subtitleAlign[1]=getResources().getString(R.string.centered);
		if (mSharedPreferences.getBoolean(Constants.PREF_SUBTITLES_LEFT, true)){
			prefAlignment.setSummary(subtitleAlign[0]);
		}else{
			prefAlignment.setSummary(subtitleAlign[1]);
		}

		prefAlignment.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				new PrefDialog(subtitleAlign, preference.getKey(), true).show(getFragmentManager(), "test");
				return true;
			}
		});

		Preference slideShowDelayPref= findPreference(Constants.PREF_SLIDESHOW_DELAY);
		final int slideOffset=1;
		final String slideShowDelayTit=getResources().getString(R.string.slide_show_delay);
		slideShowDelayPref.setTitle(slideShowDelayTit + " "
				+ (mSharedPreferences.getInt(Constants.PREF_SLIDESHOW_DELAY, 2)+slideOffset) + " s");
		slideShowDelayPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int value=(Integer) newValue;
				value+=slideOffset;
				preference.setTitle(slideShowDelayTit + " " + value + " s");
				return true;
			}
		});
		
		CheckBoxPreference showQueueShare= (CheckBoxPreference) findPreference(Constants.PREF_SHOW_QUEUE_SHARE);
		showQueueShare.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				PackageManager pm = getPackageManager();
				pm.setComponentEnabledSetting(new ComponentName(RaspiPrefActivity.this, 
						"at.huber.raspicast.QueueShare"),   
						(Boolean)newValue? PackageManager.COMPONENT_ENABLED_STATE_ENABLED: 
							PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
					
				return true;
			}
		});
	}

	@Override
	public void onDestroy() {
		int ytQualityEnd=mSharedPreferences.getInt(Constants.PREF_YOUTUBE_QUALITY, 0);
		boolean ytHttpEnd=mSharedPreferences.getBoolean(Constants.PREF_YOUTUBE_HTTP, false);
		if (ytQualityEnd != launchYtQuality || ytHttpEnd != launchYtHttp){
			SshConnection.getQueueYoutubeIds(getApplicationContext());
		}
		SshConnection.liveOption=mSharedPreferences.getInt(Constants.PREF_LIVE_OPTIONS, 0);
		super.onDestroy();
	}



	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// Respond to the action bar's Up/Home button
		case android.R.id.home:
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onSelection(String playlistDir) {
		final Preference prefPlaylistDir= findPreference(Constants.PREF_INPUT_DIR_NAME);
		String playlistDirText=playlistDir.replace("/", "<big>/</big>\u200B");
		prefPlaylistDir.setSummary(Html.fromHtml(playlistDirText));
		Editor edit=mSharedPreferences.edit();
		edit.putString(Constants.PREF_INPUT_DIR_NAME, playlistDir);
		edit.apply();
		
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (action == KeyEvent.ACTION_DOWN) {
				SshConnection.changeVolume(getApplicationContext(), true);
			}
			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (action == KeyEvent.ACTION_DOWN) {
				SshConnection.changeVolume(getApplicationContext(), false);
			}
			return true;
		default:
			return super.dispatchKeyEvent(event);
		}
	}
	
	
	
	
}
