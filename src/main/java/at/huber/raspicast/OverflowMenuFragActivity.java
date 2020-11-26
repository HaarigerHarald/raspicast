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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.huber.raspicast.dialogs.DirectoryChooseDialog;
import at.huber.raspicast.dialogs.LicensesFragment;
import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PasswordEncrypter;
import at.huber.raspicast.utils.RaspiUtils;
import at.huber.raspicast.youtube.RaspiYouTubeExtractor;

public abstract class OverflowMenuFragActivity extends QueueFragActivity 
		implements DirectoryChooseDialog.Callbacks{
	
	public static final String CALL_SYNC_ACTION = "at.huber.raspicast.call_sync";
	public static final String CALL_SYNC_DELAY = "call_sync_delay";
	public static final String CALL_SYNC_RETRIES = "call_sync_retries";

	public static boolean lightTheme=false;
	private static Dialog dialog;

	VideoControlExecutor videoControlThread;

	private boolean purposedDialogDismiss = false;
	private MenuItem playPauseMenuItem;
	private MenuItem miForward;
	private MenuItem miBackward;
	private MenuItem miSubtitles;
	private MenuItem miAudioStream;
	private MenuItem miRepeat;
	
	private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        if(intent.getAction().equals(CALL_SYNC_ACTION)) {
	            int delay = intent.getExtras().getInt(CALL_SYNC_DELAY);
	            int retries = intent.getExtras().getInt(CALL_SYNC_RETRIES);
	            if(videoControlThread !=null){
	            	videoControlThread.syncWithRaspi(delay, retries, false);
	            }
	        }
	    }	
	};

	private OnDismissListener myOnDismissListener = new OnDismissListener() {
		@Override
		public void onDismiss(DialogInterface d) {
			if (!purposedDialogDismiss)
				dialog = null;
		}
	};
	private OnKeyListener myOnKeyListener = new OnKeyListener() {
		
		@Override
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
			int action=event.getAction();
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (action == KeyEvent.ACTION_DOWN){
					SshConnection.changeVolume(getApplicationContext(), true);
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (action == KeyEvent.ACTION_DOWN){
					SshConnection.changeVolume(getApplicationContext(), false);
				}
				return true;
			default:
				return false;
			}
		}
	};

	private TextWatcher noSpacesTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			// I don't need this.
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			// I don't need this.
		}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();
			int oldLength = text.length();
			text = text.replace(" ", "");
			if (oldLength != text.length()) {
				s.replace(0, s.length(), text);
			}
		}
	};
	private TextView txtKeyFile;
	private static String keyFile;
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		SharedPreferences.Editor editor;
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case android.R.id.home:
	        super.onOptionsItemSelected(item);
	        finish();
			overridePendingTransition(R.anim.no_fade,R.anim.fade_out);
	        return true;
		case R.id.action_stop:
			SshConnection.stopCurrentProgramm(getApplicationContext());
			videoControlThread.syncWithRaspi(300, 1, false);
			SshConnection.readQueue(OverflowMenuFragActivity.this, 400, true);
			HttpFileStreamer.closeFileStreamer(getApplicationContext());
			return true;
		case R.id.action_start:
			if (SshConnection.videoInfo.contains("\n") && VideoControlExecutor.getPlaystatus().isSeekAble) {
				playAndPausVideo();
			} else {
				playStreamManually();
			}
			return true;
		case R.id.action_audio_output:
			showAudioOutputDialog();
			return true;
		case R.id.action_forward:
			if((VideoControlExecutor.getPlaystatus().actPositionMilliSeconds+12000) <
					VideoControlExecutor.getPlaystatus().totalLengthMilliSeconds){
				VideoControlExecutor.incrementActPosition(1000);
				SshConnection.setPosition(10, getApplicationContext(), true);
				VideoControlExecutor.lastTimeMillis=System.currentTimeMillis();
				videoControlThread.syncWithRaspi(1500, 1, false);
			}
			return true;
		case R.id.action_backward:
			if((VideoControlExecutor.getPlaystatus().actPositionMilliSeconds-12000)>0){
				VideoControlExecutor.incrementActPosition(-1000);
				SshConnection.setPosition(-10, getApplicationContext(), true);
				VideoControlExecutor.lastTimeMillis=System.currentTimeMillis();
				videoControlThread.syncWithRaspi(1500, 1, false);
			}
			return true;
		case R.id.action_audio_streams:
			showAudioStreamDialog();
			return true;
		case R.id.action_ssh_settings:
			setSshSettings(null, false);
			return true;
		case R.id.action_repeat_icon:
		case R.id.action_repeat:
			boolean loopStream = !SshConnection.loopStreams;
			item.setChecked(loopStream);
			SshConnection.loopStreams = loopStream;
			SshConnection.changeLooping(getApplicationContext());
			if(miRepeat!=null){
			if(SshConnection.loopStreams){
				if(lightTheme){
					miRepeat.setIcon(R.drawable.ic_action_repeat_enabled_light);
				}else{
					miRepeat.setIcon(R.drawable.ic_action_repeat_mat_dark);
				}
			}else{
				if(lightTheme){
					miRepeat.setIcon(R.drawable.ic_action_repeat_dark);
				}else{
					miRepeat.setIcon(R.drawable.ic_action_repeat_dark);
				}
			}
			}
			editor = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).edit();
			editor.putBoolean(Constants.PREF_LOOP_STREAM, loopStream);
			editor.apply();
			return true;
		case R.id.action_subtitles:
			if(VideoControlExecutor.getPlaystatus().subtitleStreams!=null)
				showSubtitleDialog();
			return true;
		case R.id.action_advanced_options:
			 Intent intent = new Intent(this,RaspiPrefActivity.class);
			 startActivity(intent);
			return true;
		case R.id.action_about_help:
			showHelpAndAboutDialog();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	

	private void showAudioOutputDialog() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.audio_output);

		final RadioGroup rg = (RadioGroup) getLayoutInflater().inflate(R.layout.audio_output, null);
		String checked = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
				Constants.PREF_AUDIO_OUTPUT, "hdmi");
		switch (checked) {
			case "hdmi":
				rg.check(R.id.rd_hdmi);
				break;
			case "local":
				rg.check(R.id.rd_local);
				break;
			case "both":
				rg.check(R.id.rd_both);
				break;
			case "alsa":
				rg.check(R.id.rd_alsa);
				break;
		}
		final String alsaDevice = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
				Constants.PREF_ALSA_DEVICE, "");

		if(!alsaDevice.equals("")){
			RadioButton rdAlsa = rg.findViewById(R.id.rd_alsa);
			rdAlsa.setText("alsa:"+alsaDevice);
		}

		alert.setView(rg);
		alert.setNegativeButton(R.string.btn_cancel, null);
		final Dialog diag = alert.create();
		rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				SharedPreferences.Editor editor = getSharedPreferences(Constants.PREF_FILE_NAME,
						MODE_PRIVATE).edit();
				switch (rg.getCheckedRadioButtonId()) {
					case R.id.rd_hdmi:
						editor.putString(Constants.PREF_AUDIO_OUTPUT, "hdmi");
						SshConnection.audioOuputDevice = "hdmi";
						break;
					case R.id.rd_local:
						editor.putString(Constants.PREF_AUDIO_OUTPUT, "local");
						SshConnection.audioOuputDevice = "local";
						break;
					case R.id.rd_both:
						editor.putString(Constants.PREF_AUDIO_OUTPUT, "both");
						SshConnection.audioOuputDevice = "both";
						break;
					case R.id.rd_alsa:
						editor.putString(Constants.PREF_AUDIO_OUTPUT, "alsa");
						if(!alsaDevice.equals(""))
							SshConnection.audioOuputDevice = "alsa:"+alsaDevice;
						else
							SshConnection.audioOuputDevice = "alsa";
						break;
				}
				editor.apply();
				diag.dismiss();
			}
		});
		dialog = diag;
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();
		RaspiUtils.brandAlertDialog(dialog, this);

	}

	@SuppressWarnings("ResourceType")
	@SuppressLint("ALL")
	private void showSubtitleDialog(){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(R.string.subtitles);

		ScrollView scrlView = (ScrollView) getLayoutInflater().inflate(R.layout.stream_chooser, null);
		final RadioGroup rg = scrlView.findViewById(R.id.rg_stream_chooser);
		RadioButton rb= (RadioButton) getLayoutInflater().inflate(R.layout.stream_chooser_radiobtn, rg, false);
		rb.setText(R.string.subtitles_off);
		rb.setId(Constants.SUBTITLE_OFF_ID);
		rg.addView(rb);
		rg.addView(getLayoutInflater().inflate(R.layout.stream_chooser_divider, rg, false));
		for(int i=0; i<VideoControlExecutor.getPlaystatus().subtitleStreams.length; i++){
			Pattern patTitel= Pattern.compile(":((.+?):(.*?)):");
			Matcher mat= patTitel.matcher(VideoControlExecutor.getPlaystatus().subtitleStreams[i]);
			if(mat.find()){
				rb= (RadioButton) getLayoutInflater().inflate(R.layout.stream_chooser_radiobtn, rg, false);
				String title=mat.group(1);
				String lang;
				if(title.endsWith(":")){
					title=title.replace(":", "");
					lang=title;
					title=null;
					rb.setText(lang);
				}else{
					lang=title.substring(0, title.indexOf(":"));
					title=title.substring(title.indexOf(":")+1, title.length());
					rb.setText(lang+" ("+ title+")");
				}
				for(int x=0;x<Constants.LANGUAGE_ABBREVIATION.length;x++){
					if(Constants.LANGUAGE_ABBREVIATION[x].equalsIgnoreCase(lang)){
						if(title!=null)
							rb.setText(Constants.LANGUAGE_FULL[x]+ " ("+title+")");
						else
							rb.setText(Constants.LANGUAGE_FULL[x]);
						break;
					}
				}
				
				rb.setId(i);
				rg.addView(rb);
				if(i+1<VideoControlExecutor.getPlaystatus().subtitleStreams.length){
					View divider= getLayoutInflater().inflate(R.layout.stream_chooser_divider, rg, false);
					rg.addView(divider);
				}
			}
			
		}
		//rg.check(RaspicastConst.SUBTITLE_OFF_ID);
		alert.setView(scrlView);
		alert.setNegativeButton(R.string.btn_cancel, null);
		final Dialog diag = alert.create();
		rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

			@SuppressWarnings("ResourceType")
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if(rg.getCheckedRadioButtonId()==Constants.SUBTITLE_OFF_ID){
					SshConnection.changeSubtitles(-1, getApplicationContext());
				}else{
					SshConnection.changeSubtitles(rg.getCheckedRadioButtonId(), getApplicationContext());
				}
				diag.dismiss();
			}
		});
		dialog = diag;
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();
		RaspiUtils.brandAlertDialog(dialog, this);
	}
	
	private void showAudioStreamDialog(){
		if(VideoControlExecutor.getPlaystatus().audioStreams!=null){
			AlertDialog.Builder alert=new AlertDialog.Builder(this);
			alert.setTitle(R.string.audio_streams);

			ScrollView scrlView=(ScrollView) getLayoutInflater().inflate(R.layout.stream_chooser,
					null);
			final RadioGroup rg=(RadioGroup) scrlView.findViewById(R.id.rg_stream_chooser);
			for(int i=0; i < VideoControlExecutor.getPlaystatus().audioStreams.length; i++){
				RadioButton rb=(RadioButton) getLayoutInflater().inflate(R.layout.stream_chooser_radiobtn,
						rg, false);
				String title=VideoControlExecutor.getPlaystatus().audioStreams[i];
				title=title.substring(title.indexOf(":") + 1);
				title=title.substring(0, title.indexOf(":"));
				rb.setText(title);
				for(int x=0;x<Constants.LANGUAGE_ABBREVIATION.length;x++){
					if(Constants.LANGUAGE_ABBREVIATION[x].equalsIgnoreCase(title)){
						rb.setText(Constants.LANGUAGE_FULL[x]);
						break;
					}
				}
				rb.setId(i);
				rg.addView(rb);
				if(i+1<VideoControlExecutor.getPlaystatus().audioStreams.length){
					View divider= getLayoutInflater().inflate(R.layout.stream_chooser_divider, rg, false);
					rg.addView(divider);
				}
			}
			rg.check(VideoControlExecutor.getPlaystatus().activatedAudioStream);
			alert.setNegativeButton(R.string.btn_cancel, null);
			alert.setView(scrlView);
			final Dialog diag =alert.create();
			rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(RadioGroup group, int checkedId) {
					VideoControlExecutor.setActiveAudioStream(rg.getCheckedRadioButtonId());
					SshConnection.changeAudioIndex(rg.getCheckedRadioButtonId(), getApplicationContext());
					diag.dismiss();
				}
			});
			dialog = diag;
			dialog.setOnDismissListener(myOnDismissListener);
			dialog.setOnKeyListener(myOnKeyListener);
			dialog.show();
			RaspiUtils.brandAlertDialog(dialog, this);
		}
	}

	private void showHelpAndAboutDialog() {
		final Dialog helpDialog = new Dialog(this);
		helpDialog.setOnDismissListener(myOnDismissListener);
		helpDialog.setTitle(R.string.about_help);
		helpDialog.setContentView(R.layout.help_and_about);
		final TextView txtVersion= helpDialog.findViewById(R.id.txt_version);
		String versionText= "Version: ";
		try{
			versionText+=getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}catch (NameNotFoundException e){
			versionText+="unknown";
		}
		txtVersion.setText(versionText);
		final TextView txtLicenses= helpDialog.findViewById(R.id.txt_licenses);
		txtLicenses.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				LicensesFragment.displayLicensesFragment(getSupportFragmentManager(), true);
				
			}
		});
		TextView txtHelp= helpDialog.findViewById(R.id.txt_help);
		txtHelp.setMovementMethod(LinkMovementMethod.getInstance());
		if(getResources().getConfiguration().locale.getCountry().equals("AT")){
			final CharSequence a1TVHelp="\nMit dieser App ist es außerdem möglich alle unverschlüsselten Sender des A1 TV Programms abzuspielen, dazu sollte der Pi allerdings nicht via Wlan im Netz hängen, sondern wie die Settop-Box via Lan.\n";
			txtHelp.setText(TextUtils.concat(getResources().getText(R.string.help), a1TVHelp));
		}	
		TextView txtBugreport= helpDialog.findViewById(R.id.txt_bug_report);
		txtBugreport.setMovementMethod(LinkMovementMethod.getInstance());
		helpDialog.findViewById(R.id.btnOk).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				helpDialog.dismiss();
			}
		});
		helpDialog.setOnKeyListener(myOnKeyListener);
		RaspiUtils.brandDialog(helpDialog, this);
		dialog = helpDialog;
		helpDialog.show();
	}

	protected void setSshSettings(final String streamToPlay, final boolean addToQueue) {
		final Dialog sshDialog = new Dialog(this);
		sshDialog.setOnDismissListener(myOnDismissListener);
		sshDialog.setContentView(R.layout.ssh_setup_layout);
		sshDialog.setTitle(R.string.ssh_setup);

		final EditText editTxtHostname = sshDialog.findViewById(R.id.editTxtHostname);
		final EditText editTxtPort = sshDialog.findViewById(R.id.editTxtPort);
		final EditText editTxtUser = sshDialog.findViewById(R.id.editTxtUser);
		final EditText editTxtPassword = sshDialog.findViewById(R.id.editTxtPassword);
		Button btnOK = sshDialog.findViewById(R.id.btnOk);
		Button btnCancel = sshDialog.findViewById(R.id.btnCancel);

		final String oldHostname = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
				Constants.PREF_HOSTNAME, "");
		editTxtHostname.setText(oldHostname);
		editTxtPort.setText(""
				+ getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getInt(
						Constants.PREF_PORT, 22));
		editTxtUser.setText(getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
				Constants.PREF_USER, "pi"));
		editTxtPassword.setText(PasswordEncrypter.decrypt(getSharedPreferences(Constants.PREF_FILE_NAME,
				MODE_PRIVATE).getString(Constants.PREF_PASSWORD, "")));

		editTxtHostname.addTextChangedListener(noSpacesTextWatcher);
		editTxtUser.addTextChangedListener(noSpacesTextWatcher);
		txtKeyFile= sshDialog.findViewById(R.id.txt_key_file);
		final CheckBox chkBxPubKey= sshDialog.findViewById(R.id.chkBx_PubKey);
		chkBxPubKey.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked) {
					if (RaspiListActivity.hasStoragePermission)
						txtKeyFile.setVisibility(View.VISIBLE);
					else
						Toast.makeText(OverflowMenuFragActivity.this,
								R.string.no_storage_permission, Toast.LENGTH_LONG).show();
				} else
					txtKeyFile.setVisibility(View.GONE);
			}
		});
 		final DirectoryChooseDialog dial= new DirectoryChooseDialog();

 		keyFile=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getString(Constants.PREF_KEYFILE_PATH, null);
		if(keyFile!=null){
			chkBxPubKey.setChecked(true);
			txtKeyFile.setVisibility(View.VISIBLE);
		}
		TypedValue typedValue = new TypedValue();
		getTheme().resolveAttribute(R.attr.highlight_color, typedValue, true);
		String hexColor= String.format("#%06X", (0xFFFFFF & typedValue.data));
		String keyFileText = getResources().getString(R.string.key_file);
		keyFileText+="\u2009<font color="+hexColor+">";
		if(keyFile!=null){
			keyFileText+=keyFile;
		}	
		keyFileText +="</font>";
		txtKeyFile.setText(Html.fromHtml(keyFileText));
		
		txtKeyFile.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dial.show(getFragmentManager(), "keyFileChoose");
			}
		});

		btnCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sshDialog.cancel();
			}
		});

		btnOK.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor editor = getSharedPreferences(Constants.PREF_FILE_NAME,
						MODE_PRIVATE).edit();
				if(!chkBxPubKey.isChecked()){
					keyFile=null;
				}
				editor.putString(Constants.PREF_KEYFILE_PATH, keyFile);
		
				if(!editTxtHostname.getText().toString().equals(oldHostname))
					editor.remove(Constants.PREF_SSH_FINGERPRINT);
				editor.putString(Constants.PREF_HOSTNAME, editTxtHostname.getText().toString());
				editor.putInt(Constants.PREF_PORT, Integer.parseInt(editTxtPort.getText().toString()));
				editor.putString(Constants.PREF_USER, editTxtUser.getText().toString());
				editor.putString(Constants.PREF_PASSWORD, PasswordEncrypter.encrypt(editTxtPassword.getText().toString()));
				editor.apply();
				SshConnection.setCredentials(editTxtHostname.getText().toString(),
						Integer.parseInt(editTxtPort.getText().toString()), editTxtUser.getText().toString(),
						editTxtPassword.getText().toString(), keyFile);
				SshConnection.insecure = false;
				SshConnection.openSshConnection(OverflowMenuFragActivity.this , null, false, true);
				if(RaspiListActivity.mTwoPane){
					((RaspiListFragment) getSupportFragmentManager().findFragmentById(R.id.raspicast_list)).setOnStartUp();
				}

				if (streamToPlay != null) {
					new RaspiYouTubeExtractor(getApplicationContext(), addToQueue).execute(streamToPlay);
				} else {
					if(videoControlThread!=null){
						videoControlThread.syncWithRaspi(100, 0, false);
					}
				}
				sshDialog.dismiss();
			}
		});
		sshDialog.setOnKeyListener(myOnKeyListener);
		sshDialog.setOwnerActivity(this);
		RaspiUtils.brandDialog(sshDialog, this);
		dialog = sshDialog;
		sshDialog.show();
	}

	private void playStreamManually() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.play_stream);
		// Set an EditText view to get user input
		final EditText input = (EditText) getLayoutInflater().inflate(R.layout.play_stream, null);
		input.setHint(R.string.play_stream_hint);
		input.setText(getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
				Constants.PREF_LAST_PLAYED_STREAM_MAN_NAME, ""));
		input.addTextChangedListener(noSpacesTextWatcher);
		input.selectAll();
		alert.setView(input);
		alert.setNeutralButton(R.string.queue, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String value = input.getText().toString();
				boolean isSupported=false;
				for(String stream :Constants.SUPPORTED_STREAMS){
					if(value.startsWith(stream)){
						isSupported=true;
						break;
					}
				}
				if (isSupported) {
					if (value.contains("http://youtu.be/") || value.contains("youtube.com/watch?v=")){
						new RaspiYouTubeExtractor(OverflowMenuFragActivity.this, false).execute(value);
					} else if(value.startsWith("rtp://")|| value.startsWith("udp://") ){
						SubtitleOptions so = new SubtitleOptions(OverflowMenuFragActivity.this);
						SshConnection.startProgramm(value, getApplicationContext(), value, value, so, false);
						if(videoControlThread!=null)
							videoControlThread.syncWithRaspi(1200, 2, false);
						if(SshConnection.queueIndex>0){
							SshConnection.readQueue(getApplicationContext(), 250, true);
						}
						
					}else{
						SubtitleOptions so = new SubtitleOptions(OverflowMenuFragActivity.this);
						SshConnection.addToQueue(value, getApplicationContext(), value, value, so, false);
						SshConnection.readQueue(OverflowMenuFragActivity.this, 300, true);
						if(getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
								getBoolean(Constants.PREF_AUTOSTART_QUEUE, true) && videoControlThread!=null
								&& SshConnection.videoInfo.equals(""))
							videoControlThread.syncWithRaspi(1200, 2, false);
					}
					SharedPreferences.Editor editor = getSharedPreferences(Constants.PREF_FILE_NAME,
							MODE_PRIVATE).edit();
					editor.putString(Constants.PREF_LAST_PLAYED_STREAM_MAN_NAME, value);
					editor.apply();
				} else {
					Toast.makeText(getApplicationContext(), R.string.error_no_stream, Toast.LENGTH_LONG)
							.show();
					dialog.cancel();
				}
				
			}
		});
		alert.setPositiveButton(R.string.play, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				boolean isSupported=false;
				for(String stream :Constants.SUPPORTED_STREAMS){
					if(value.startsWith(stream)){
						isSupported=true;
						break;
					}
				}
				if (isSupported) {
					if (value.contains("http://youtu.be/") || value.contains("youtube.com/watch?v=")){
						new RaspiYouTubeExtractor(OverflowMenuFragActivity.this, false).execute(value);
					} else {
						SubtitleOptions so = new SubtitleOptions(OverflowMenuFragActivity.this);
						SshConnection.startProgramm(value, getApplicationContext(), value, value, so, false);
						if(videoControlThread!=null)
							videoControlThread.syncWithRaspi(1200, 2, false);
						if(SshConnection.queueIndex>0){
							SshConnection.readQueue(getApplicationContext(), 250, true);
						}
					}
					SharedPreferences.Editor editor = getSharedPreferences(Constants.PREF_FILE_NAME,
							MODE_PRIVATE).edit();
					editor.putString(Constants.PREF_LAST_PLAYED_STREAM_MAN_NAME, value);
					editor.apply();
				} else {
					Toast.makeText(getApplicationContext(), R.string.error_no_stream, Toast.LENGTH_LONG)
							.show();
					dialog.cancel();
				}
			}
		});
		alert.setNegativeButton(R.string.btn_cancel, null);
		final Dialog diag = alert.create();
		input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					diag.getWindow().setSoftInputMode(
							WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
			}
		});
		dialog = diag;
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.show();
		RaspiUtils.brandAlertDialog(dialog, this);
	}

	private void playAndPausVideo() {
		SshConnection.playPauseVideo(this);
		if(videoControlThread!=null){
			int syncDelay= (SshConnection.videoInfo.startsWith("local")) ? 80 : 100;
			videoControlThread.syncWithRaspi(syncDelay, 2, true);
		}
	}

	public void changePlayPauseIcon(boolean playing, boolean seekAble) {
		if (playPauseMenuItem != null) {
			if (playing && seekAble) {
				playPauseMenuItem.setTitle(R.string.pause);
				if (lightTheme) {
					playPauseMenuItem.setIcon(R.drawable.ic_action_pause_dark);
				} else {
					playPauseMenuItem.setIcon(R.drawable.ic_action_pause_dark);
				}
			} else {
				if(seekAble){
					playPauseMenuItem.setTitle(R.string.play);
				}else{
					playPauseMenuItem.setTitle(R.string.play_stream);
				}
				if (lightTheme) {
					playPauseMenuItem.setIcon(R.drawable.ic_action_play_dark);
				} else {
					playPauseMenuItem.setIcon(R.drawable.ic_action_play_dark);
				}
			}
			if (!SshConnection.videoInfo.contains("\n")) {
				playPauseMenuItem.setTitle(R.string.play_stream);
				if(miBackward!=null && miForward!=null){
					miForward.setVisible(false);
					miBackward.setVisible(false);
				}
				if(miAudioStream!=null && miSubtitles!=null){
					miAudioStream.setVisible(false);
					miSubtitles.setVisible(false);
				}
			}else{
				if(miBackward!=null && miForward!=null){
					if(seekAble){
						miForward.setVisible(true);
						miBackward.setVisible(true);
					}else{
						miForward.setVisible(false);
						miBackward.setVisible(false);
					}
				}
				if(miAudioStream!=null && miSubtitles!=null && VideoControlExecutor.getPlaystatus().audioStreams!=null){
					if(VideoControlExecutor.getPlaystatus().audioStreams.length>1){
						miAudioStream.setVisible(true);
					}else{
						miAudioStream.setVisible(false);
					}
					//boolean showSubtitles=getSharedPreferences(RaspicastConst.PREF_FILE_NAME, MODE_PRIVATE).getBoolean(RaspicastConst.PREF_SUBTITLES, false);
					if(VideoControlExecutor.getPlaystatus().subtitleStreams!=null){
						miSubtitles.setVisible(true);
					}else{
						miSubtitles.setVisible(false);
					}
				}
			}
		}
	}
	
	protected void showSlideShowOptions(final String path, final String[] pictures){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.options);
		String[] items= { getResources().getString(R.string.show), getResources().getString(R.string.slide_show_start)};
		alert.setItems(items, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				if(which==0){
					SshConnection.showPicture(getApplicationContext(), path, pictures[0]);
					videoControlThread.syncWithRaspi(300, 0, false);
				}else if(which == 1){
					SshConnection.showPicture(getApplicationContext(), path, pictures);
					videoControlThread.syncWithRaspi(300, 0, false);
				}		
			}
		});
		dialog=alert.create();
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();	
		RaspiUtils.brandAlertDialog(dialog, this);
	}
	
	protected void showCastOptions(final String fileToPlay, final String title){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		String[] items;
		items=new String[2];

		items[0]=getResources().getString(R.string.play);
		items[1]=getResources().getString(R.string.add_to_queue);
		alert.setTitle(R.string.options);
		alert.setItems(items, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				switch(which){
				case 0:
					HttpFileStreamer.streamFile(fileToPlay, getApplicationContext(), title,
							true, false, false);
					break;
				case 1:
					HttpFileStreamer.streamFile(fileToPlay, getApplicationContext(), title,
							true, false, true);
					break;
				}
			}
		});
		dialog=alert.create();
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();	
		RaspiUtils.brandAlertDialog(dialog, this);
	}
	
	protected void showPlayOptions(final String fileToPlay, final String title, final SubtitleOptions so, final ArrayAdapter<String> fileArrayAdapter){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		String[] items;
		items=new String[4];
		items[2]=getResources().getString(R.string.dir_to_queue);
		items[3]=getResources().getString(R.string.external_subtitles);

		items[0]=getResources().getString(R.string.play);
		items[1]=getResources().getString(R.string.add_to_queue);
		alert.setTitle(R.string.options);
		alert.setItems(items, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				switch(which){
					case 0:
						SshConnection.startProgramm(fileToPlay, getApplicationContext(), title, "local", so, false);
						videoControlThread.syncWithRaspi(1200, 2, false);
						SshConnection.readQueue(OverflowMenuFragActivity.this, 100, true);
						break;
					case 1:
						SshConnection.addToQueue(fileToPlay, getApplicationContext(), title, "local",so, false);
						SshConnection.readQueue(OverflowMenuFragActivity.this, 100, true);
						if(getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
								getBoolean(Constants.PREF_AUTOSTART_QUEUE, true) && videoControlThread!=null
								&& SshConnection.videoInfo.equals(""))
							videoControlThread.syncWithRaspi(1200, 2, false);
						break;
					case 2:
							String dir=(fileToPlay.contains("/")) ? fileToPlay.substring(0,fileToPlay.lastIndexOf("/")+1) :"";
							String file= (fileToPlay.contains("/")) ? fileToPlay.substring(fileToPlay.lastIndexOf("/")+1): fileToPlay;
							int startPostion=0;
							for(int i=0; i<fileArrayAdapter.getCount();i++){
								if(file.equals(fileArrayAdapter.getItem(i))) {
									startPostion=i;
									break;
								}
							}
							ArrayList<String> filesToAdd=new ArrayList<>();
							for(int i=0; i<fileArrayAdapter.getCount();i++){
								file=fileArrayAdapter.getItem((i+startPostion)%fileArrayAdapter.getCount());
								if(file == null || !file.contains(".") || file.endsWith("/"))
									continue;
								for(String mediaExt: Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
									if(file.substring(file.lastIndexOf(".")).equalsIgnoreCase(mediaExt)){
										filesToAdd.add(file);
										break;
									}
								}
							}
							String[] fileTitles= filesToAdd.toArray(new String[filesToAdd.size()]);
							String[] filePaths= new String[fileTitles.length];
							for(int i=0; i<filePaths.length; i++){
								filePaths[i]=dir+fileTitles[i];
							}
							boolean autostart= getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
									getBoolean(Constants.PREF_AUTOSTART_QUEUE, true);
							SshConnection.addToQueue(filePaths, getApplicationContext(), fileTitles, "local", so, autostart, false);
							SshConnection.readQueue(OverflowMenuFragActivity.this, 100, true);
							if(autostart && videoControlThread!=null && SshConnection.videoInfo.equals(""))
								videoControlThread.syncWithRaspi(1200, 2, false);
							break;
					case 3:
						RaspiDetailFragment rdf=(RaspiDetailFragment)getSupportFragmentManager().findFragmentById(R.id.raspicast_detail_container);
						rdf.setExternalSubtitleChoosing(fileToPlay, title, so);
						break;
				}
			}
		});
		dialog=alert.create();
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();	
		RaspiUtils.brandAlertDialog(dialog, this);
	}
	
	protected void showRecordDialog(final String stream, final String title){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.record_title);
		LinearLayout ll = (LinearLayout) getLayoutInflater().inflate(R.layout.record_layout, null);
		final String recordBaseText= getResources().getString(R.string.record_text);
		final TextView txtRecord= ll.findViewById(R.id.txt_record);
		txtRecord.setText(recordBaseText+ " 30\u200Amin");
		final SeekBar skBarRecord= ll.findViewById(R.id.skBar_record);
		skBarRecord.setProgress(30);
		skBarRecord.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				txtRecord.setText(recordBaseText+" "+progress+"\u200Amin");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar){}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		
		alert.setPositiveButton(R.string.record_title, new Dialog.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SshConnection.recordStream(getApplicationContext(), skBarRecord.getProgress(), stream, title);
			}
		});
		alert.setNegativeButton(R.string.btn_cancel, null);
		alert.setView(ll);
		dialog=alert.create();
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();
		RaspiUtils.brandAlertDialog(dialog, this);
	}
	
	protected void showDirOptions(final String dir,final boolean remove){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		String[] items= new String[1];
		if(!remove)
			items[0]=getResources().getString(R.string.dirs_home_screen);
		else
			items[0]=getResources().getString(R.string.dirs_home_screen_rem);
		alert.setTitle(R.string.dir_options);
		alert.setItems(items, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				SharedPreferences sp= getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE);
				Set<String> dirs = new HashSet<>(sp.getStringSet(Constants.PREF_DIRS_HOME_SCREEN, new HashSet<String>()));
				Editor edit=sp.edit();
				if(!remove){
					dirs.add(dir);
				}else{
					dirs.remove(dir);
				}	
				edit.putStringSet(Constants.PREF_DIRS_HOME_SCREEN, dirs);
				edit.apply();
				if(RaspiListActivity.mTwoPane || remove){
					((RaspiListFragment) getSupportFragmentManager().
							findFragmentById(R.id.raspicast_list)).updateDirectory();
				}
			}
		});
		dialog = alert.create();
		dialog.setOnDismissListener(myOnDismissListener);
		dialog.setOnKeyListener(myOnKeyListener);
		dialog.show();	
		RaspiUtils.brandAlertDialog(dialog, this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(closeAppAfterQueue)
			return;
		SshConnection.loopStreams = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getBoolean(Constants.PREF_LOOP_STREAM, false);
		SshConnection.liveOption = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getInt(Constants.PREF_LIVE_OPTIONS, 0);
		SshConnection.volumeOffset = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getInt(Constants.PREF_AUDIO_OFFSET, 0) * 100;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater=getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		MenuItem miLoopStream=menu.findItem(R.id.action_repeat);
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
				|| RaspiListActivity.mTwoPane){
			miForward=menu.findItem(R.id.action_forward);
			miBackward=menu.findItem(R.id.action_backward);

			miRepeat=menu.findItem(R.id.action_repeat_icon);
			miRepeat.setVisible(true);
			if (SshConnection.loopStreams){
				if (lightTheme){
					miRepeat.setIcon(R.drawable.ic_action_repeat_enabled_light);
				}else{
					miRepeat.setIcon(R.drawable.ic_action_repeat_mat_dark);
				}
			}
			menu.findItem(R.id.action_repeat).setVisible(false);
		}
		miAudioStream=menu.findItem(R.id.action_audio_streams);
			
		miSubtitles=menu.findItem(R.id.action_subtitles);
		miLoopStream.setChecked(SshConnection.loopStreams);
		playPauseMenuItem=menu.findItem(R.id.action_start);
		if (SshConnection.videoInfo != null && SshConnection.videoInfo.contains("\n")){
			changePlayPauseIcon(VideoControlExecutor.getPlaystatus().isPlaying,
					VideoControlExecutor.getPlaystatus().isSeekAble);
		}else{
			changePlayPauseIcon(false, VideoControlExecutor.getPlaystatus().isSeekAble);
		}
		return super.onCreateOptionsMenu(menu);
	}
	

	@Override
	protected void onStart() {
		super.onStart();
		
		if (dialog != null && !this.isFinishing()) {
			dialog.setOwnerActivity(this);
			dialog.setOnDismissListener(myOnDismissListener);
			this.txtKeyFile = dialog.findViewById(R.id.txt_key_file);
			dialog.show();
		}
	}

	@Override
	public void onStop() {
		if (dialog != null && dialog.isShowing()) {
			purposedDialogDismiss = true;
			dialog.dismiss();
		} else {
			dialog = null;
		}
		super.onStop();
	}

	@Override
	public void onResume() {
		super.onResume();
		purposedDialogDismiss = false;
		LinearLayout ll = findViewById(R.id.mainLayout);
		videoControlThread = new VideoControlExecutor(this, ll);
		if (SshConnection.videoInfo != null && SshConnection.videoInfo.contains("\n")) {
			changePlayPauseIcon(VideoControlExecutor.getPlaystatus().isPlaying,VideoControlExecutor.getPlaystatus().isSeekAble );
		} else {
			changePlayPauseIcon(false, VideoControlExecutor.getPlaystatus().isSeekAble);
		}
		registerReceiver(syncReceiver, new IntentFilter(CALL_SYNC_ACTION));
	}

	@Override
	public void onPause() {
		videoControlThread.killThread();
		unregisterReceiver(syncReceiver);
		super.onPause();
	}

	@SuppressLint("RestrictedApi")
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


	@Override
	public void onSelection(String file) {
		keyFile=file;
		TypedValue typedValue = new TypedValue();
		getTheme().resolveAttribute(R.attr.highlight_color, typedValue, true);
		String keyFileText = getResources().getString(R.string.key_file);
		String hexColor= String.format("#%06X", (0xFFFFFF & typedValue.data));
		keyFileText+="\u2009<font color="+hexColor+">";
		if(keyFile!=null){
			keyFileText+=keyFile;
		}
		keyFileText+="</font>";
		txtKeyFile.setText(Html.fromHtml(keyFileText));
	}

	
}
