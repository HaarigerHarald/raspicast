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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.huber.raspicast.utils.FileStreamService;
import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PasswordEncrypter;
import at.huber.raspicast.utils.RaspiUtils;
import at.huber.raspicast.youtube.RaspiYouTubeExtractor;
import at.huber.raspicast.youtube.YouTubePlaylistExtractor;

public class RaspiListActivity extends OverflowMenuFragActivity implements RaspiListFragment.Callbacks {

	private static final int REQ_CODE_ASK_PERMISSION_STORAGE = 1678;

	public static boolean mTwoPane;
	public static boolean hasStoragePermission = false;
	protected static boolean killOnExit=false;
	
	private static final Pattern castHttpLinkPattern = Pattern.compile("((http://|https://)(.+?))(\\s|\\z)");
	private static final String playlistRegex=".*(http|https)://(www\\.|m.|)youtube\\.com/playlist\\?list=(.+?)( |\\z).*";
	private static final String playlistWatchRegex = ".*(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)&list=(.+?)( |&|\\z).*";
	protected boolean addToQueue=false;

	private static Intent intent;
	private static boolean isCreated;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		VideoControlExecutor.syncOnStart=true;
		SshConnection.interceptFinalize();
		if(savedInstanceState==null)
			SshConnection.insecure=false;
		if(!closeAppAfterQueue){

			intent = null;
			isCreated = savedInstanceState == null;

			if (ContextCompat.checkSelfPermission(this,
					Manifest.permission.READ_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED) {

				intent = getIntent();

				ActivityCompat.requestPermissions(this,
						new String[]{"android.permission.READ_EXTERNAL_STORAGE"},
						REQ_CODE_ASK_PERMISSION_STORAGE);

			} else {
				hasStoragePermission = true;
				getShareAndInit(savedInstanceState == null, getIntent());
			}

			setContentView(R.layout.activity_raspicast_list);
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
				getActionBar().setDisplayShowHomeEnabled(true);
			}
	
			if (findViewById(R.id.raspicast_detail_container) != null) {
				mTwoPane = true;
	

				((RaspiListFragment) getSupportFragmentManager().findFragmentById(R.id.raspicast_list))
						.setActivateOnItemClick(true);
				if(SshConnection.hasData() && savedInstanceState == null)
					((RaspiListFragment) getSupportFragmentManager().findFragmentById(R.id.raspicast_list)).setOnStartUp();
				
			}else{
				configFloatingActionButton();
			}
		}
		 
	}

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQ_CODE_ASK_PERMISSION_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    hasStoragePermission = true;
                    ((RaspiListFragment) getSupportFragmentManager().findFragmentById(R.id.raspicast_list)).updateDirectory();
                } else {
                    hasStoragePermission = false;
                }
				getShareAndInit(isCreated, intent);
                return;
            }
        }
    }
	
	@Override
	protected void onNewIntent(Intent intent){
		getShareAndInit(true, intent);
	}
	
	protected void getShareAndInit(boolean isCreated, Intent intent){
		if (isCreated && ((Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) ||
				Intent.ACTION_VIEW.equals(intent.getAction()))) {
			String link=null;
			if(Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())){
				link=intent.getStringExtra(Intent.EXTRA_TEXT);
			}else if(Intent.ACTION_VIEW.equals(getIntent().getAction())){
				link=intent.getDataString();
			}else if( (Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) )
					&& (intent.getType().startsWith("video/") ||
					intent.getType().startsWith("image/") || intent.getType().startsWith("audio/"))
					&& hasStoragePermission){

				boolean isAudio = intent.getType().startsWith("audio/");
				boolean isImage = intent.getType().startsWith("image/");

				Uri sendUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
				initializeSshConnection(null, false);
				String pathTitle[] = RaspiUtils.getRealPathFromURI(this, sendUri, isAudio);
				if(pathTitle != null) {
					if (isAudio) {
						if (!pathTitle[RaspiUtils.ARTIST_INDEX].equals(Constants.UNKNOWN_DATA))
							HttpFileStreamer.streamFile(pathTitle[RaspiUtils.PATH_INDEX], this,
									pathTitle[RaspiUtils.ARTIST_INDEX] + " - " + pathTitle[RaspiUtils.TITLE_INDEX],
									true, isImage, false);
						else
							HttpFileStreamer.streamFile(pathTitle[RaspiUtils.PATH_INDEX], this,
									pathTitle[RaspiUtils.TITLE_INDEX], true, isImage, false);
					} else {
						HttpFileStreamer.streamFile(pathTitle[RaspiUtils.PATH_INDEX], this,
								pathTitle[RaspiUtils.TITLE_INDEX], false, isImage, false);
					}
				}else{
					Toast.makeText(this, R.string.error_uri, Toast.LENGTH_LONG).show();
				}
			}

			if (link != null && (link.contains("://youtu.be/") || 
					link.contains("youtube.com/"))){
				initializeSshConnection(link,true);
			}else if(link!=null){
				boolean supportedStream=false;

				for(String streamType : Constants.SUPPORTED_STREAMS){
					if (link.startsWith(streamType)){
						if (streamType.equals("http://") || streamType.equals("https://")){
							for(String fileExt: Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
								if(link.endsWith(fileExt) || link.endsWith(fileExt.toUpperCase(Locale.US))){
									supportedStream=true;
								}
							}
						}else{
							supportedStream=true;
						}
						break;
					}
				}
				if(supportedStream){
					initializeSshConnection(link,false);
				}else{
					Matcher mat = castHttpLinkPattern.matcher(link);
					if(mat.find()){
						Log.d("link to cast:", mat.group(1));
						initializeSshConnection("?youtube-dl "+mat.group(1), false);
					}else{
						Toast.makeText(this, R.string.error_no_yt_link, Toast.LENGTH_LONG).show();
						Log.d("Link:", link);
						finish();
					}
				}
			}
		} else {
			initializeSshConnection(null, false);
		}
	}


	@Override
	public void onItemSelected(String id, int position) {
		if(position==1){
			if (mTwoPane) {
				Fragment fragment = new CastFragment();
				getSupportFragmentManager().beginTransaction().replace(R.id.raspicast_detail_container, fragment)
						.commit();

			} else {
				Intent detailIntent = new Intent(this, RaspiDetailActivity.class);
				detailIntent.setFlags(detailIntent.getFlags()|Intent.FLAG_ACTIVITY_SINGLE_TOP);
				detailIntent.putExtra(Constants.LIST_POSTION, position);
				startActivity(detailIntent);
				overridePendingTransition(R.anim.fade_in, R.anim.no_fade);
			}
		}else if (mTwoPane) {
			Bundle arguments = new Bundle();
			arguments.putString(Constants.STREAMING_FILE, id);
			arguments.putInt(Constants.LIST_POSTION, position);
			String playListDir=getSharedPreferences(Constants.PREF_FILE_NAME,MODE_PRIVATE).getString(Constants.PREF_INPUT_DIR_NAME, "/");
			arguments.putString(Constants.PLAYLIST_DIR, playListDir);
			Fragment fragment = new RaspiDetailFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction().replace(R.id.raspicast_detail_container, fragment)
					.commit();

		} else {
			
			Intent detailIntent = new Intent(this, RaspiDetailActivity.class);
			detailIntent.setFlags(detailIntent.getFlags()|Intent.FLAG_ACTIVITY_SINGLE_TOP);
			detailIntent.putExtra(Constants.STREAMING_FILE, id);
			detailIntent.putExtra(Constants.LIST_POSTION, position);
			String playListDir=getSharedPreferences(Constants.PREF_FILE_NAME,MODE_PRIVATE).getString(Constants.PREF_INPUT_DIR_NAME, "/");
			detailIntent.putExtra(Constants.PLAYLIST_DIR, playListDir);
			startActivity(detailIntent);
			overridePendingTransition(R.anim.fade_in, R.anim.no_fade);
		}
	}

	@Override
	public void onDestroy() {
		if (isFinishing() && (isTaskRoot() || killOnExit)) {
			HttpFileStreamer.softClose();
			if(killOnExit){
				killOnExit=false;
				HttpFileStreamer.closeFileStreamer(getApplicationContext());
				super.onDestroy();
				SshConnection.safelyKill();
				return;
			}else if(!closeAppAfterQueue){
				if(!FileStreamService.isFileStreamServiceRunning(this))
					SshConnection.finalizeConnection(false);
			}
		}
		super.onDestroy();
	}

	private void initializeSshConnection(final String streamToPlay, boolean isYoutubeLink) {
		if (!SshConnection.hasData()) {
			SshConnection.audioOuputDevice = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE)
					.getString(Constants.PREF_AUDIO_OUTPUT, "hdmi");
			if(SshConnection.audioOuputDevice.startsWith("alsa")) {
				String alsaDevice = getSharedPreferences(Constants.PREF_FILE_NAME,
						MODE_PRIVATE).getString(Constants.PREF_ALSA_DEVICE, "");
				if (!alsaDevice.equals(""))
					SshConnection.audioOuputDevice = "alsa:" + alsaDevice;
			}

			final String hostname=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE)
					.getString(Constants.PREF_HOSTNAME, null);
			final int port=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getInt(
					Constants.PREF_PORT, 22);
			final String user=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
					Constants.PREF_USER, null);
			SshConnection.setCredentials(
					hostname, port, user,
					PasswordEncrypter.decrypt(getSharedPreferences(Constants.PREF_FILE_NAME,
							MODE_PRIVATE).getString(Constants.PREF_PASSWORD, null)),
					getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).getString(
							Constants.PREF_KEYFILE_PATH, null));
			if(!SshConnection.hasData()){
				if(isYoutubeLink)
					setSshSettings(streamToPlay, addToQueue);
				else
					setSshSettings(null, addToQueue);
			}else{
				SshConnection.openSshConnection(this, null, false, true);
				if (streamToPlay != null){
					if (!closeAppAfterQueue)
						VideoControlExecutor.syncOnStart=false;
					if (isYoutubeLink){
						Log.d("youtubeLink", streamToPlay);
						if (streamToPlay.matches(playlistRegex))
							new YouTubePlaylistExtractor(getApplicationContext()).execute(streamToPlay);
						else if (streamToPlay.matches(playlistWatchRegex))
							new YouTubePlaylistExtractor(getApplicationContext()).execute(streamToPlay);
						else
							new RaspiYouTubeExtractor(getApplicationContext(), addToQueue).execute(streamToPlay);
					}else{
						if (!addToQueue)
							SshConnection.startProgramm(streamToPlay, getApplicationContext(), streamToPlay, streamToPlay, null, false);
						else
							SshConnection.addToQueue(streamToPlay, getApplicationContext(), streamToPlay, streamToPlay, null, false);
					}
	
				}
			}
		} else {
			if (streamToPlay != null) {
				if(!closeAppAfterQueue)
					VideoControlExecutor.syncOnStart = false;
				if(isYoutubeLink){
					if(streamToPlay.matches(playlistRegex))
						new YouTubePlaylistExtractor(getApplicationContext()).execute(streamToPlay);
					else if(streamToPlay.matches(playlistWatchRegex))
						new YouTubePlaylistExtractor(getApplicationContext()).execute(streamToPlay);
					else
						new RaspiYouTubeExtractor(getApplicationContext(), addToQueue).execute(streamToPlay);
				}else{
					if(!addToQueue || streamToPlay.startsWith("?youtube-dl"))
						SshConnection.startProgramm(streamToPlay, getApplicationContext(), streamToPlay, streamToPlay, null, false);
					else
						SshConnection.addToQueue(streamToPlay, getApplicationContext(), streamToPlay, streamToPlay, null, false);
				}
			}
		}
	}
}
