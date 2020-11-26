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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PixelTransformer;

public class VideoControlExecutor {

	private static final int MAX_PROGRESS=10000;
	private static final int SYNC_DELAY_MILLIS= 200;//Depends on network load, constant maybe not optimal
	private static final DecimalFormat MINUTE_FORMAT=new DecimalFormat("00");
	private static final DecimalFormat SECONDS_FORMAT=new DecimalFormat("00");

	private final Activity calledActivity;
	private final LinearLayout mainLayout;
	private final LinearLayout videoControlLayout;
	private final TextView txtTitle;
	private final TextView txtActPos;
	private final TextView txtLength;
	private final SeekBar videoSeekbar;
	private final ScheduledExecutorService exec;
	
	private volatile boolean isShuttingDown=false;

	private boolean isTracking=false;
	private boolean isTranslatingOut=false;
	private boolean hideExtensions;
	private boolean queueNeedsUpdate=false;
	
	public static volatile boolean requestInterrupt=false;
	public static volatile boolean canInterrupt=false;
	public static boolean isRunning = false;

	private volatile byte index=0;

	private volatile static boolean isWaitingForInterrupt=false;
	private static Thread syncThrd;
	protected static long lastTimeMillis=-1;
	
	private static long lastSeekTime=-1; //For an omxplayer bug caused by a to small delay between two seeks
	private static byte nextSyncSeconds=10;

	private final static OmxPlaystatus playstatus=new OmxPlaystatus();
	public static boolean syncOnStart=true;

	VideoControlExecutor(Activity act, LinearLayout mainLayout) {
		isRunning = true;
		calledActivity=act;
		requestInterrupt=false;
		this.mainLayout=mainLayout;
		if (mainLayout.getChildCount() <= 1){
			act.getLayoutInflater().inflate(R.layout.video_control, mainLayout, true);
		}
		videoControlLayout=(LinearLayout) mainLayout.getChildAt(1);
		if (lastTimeMillis > 0){
			synchronized (playstatus) {
				if (playstatus.isPlaying
						&& (System.currentTimeMillis() - lastTimeMillis) > 0) {
					long currentActPos = playstatus.actPositionMilliSeconds + (System.currentTimeMillis() - lastTimeMillis);
					currentActPos -= 1000 * (currentActPos / playstatus.totalLengthMilliSeconds);
					if (currentActPos > playstatus.totalLengthMilliSeconds) {
						playstatus.activatedAudioStream = 0;
					}
					playstatus.actPositionMilliSeconds = (currentActPos % playstatus.totalLengthMilliSeconds);
					lastTimeMillis = System.currentTimeMillis();
				}
			}
		}
		videoControlLayout.setOnTouchListener(new OnSwipeTouchListener(calledActivity) {
			@Override
			public void onSwipeLeft() {
				synchronized (playstatus) {
					if (playstatus.isSeekAble) {
						String[] videoInfos = SshConnection.videoInfo.split("\n");
						if (videoInfos.length >= 2 && (playstatus.actPositionMilliSeconds - 12000) > 0
								&& !isTracking) {
							playstatus.actPositionMilliSeconds -= 10000;
							lastTimeMillis = System.currentTimeMillis();
							SshConnection.setPosition(-10, calledActivity.getApplicationContext(), true);
							syncWithRaspi(1600, 1, false);
						}
					}
				}
			}

			@Override
			public void onSwipeRight() {
				synchronized (playstatus) {
					if (playstatus.isSeekAble) {
						String[] videoInfos = SshConnection.videoInfo.split("\n");
						if (videoInfos.length >= 2
								&& (playstatus.actPositionMilliSeconds + 12000) < playstatus.totalLengthMilliSeconds
								&& !isTracking) {
							playstatus.actPositionMilliSeconds += 10000;
							lastTimeMillis = System.currentTimeMillis();
							SshConnection.setPosition(10, calledActivity.getApplicationContext(), true);
							syncWithRaspi(1600, 1, false);
						}
					}
				}
			}
		});
		hideExtensions=calledActivity.getSharedPreferences(Constants.PREF_FILE_NAME,
				Activity.MODE_PRIVATE).getBoolean(Constants.PREF_HIDE_MEDIA_EXTIONSIONS, false);
		txtActPos= videoControlLayout.findViewById(R.id.txtActPos);
		txtLength= videoControlLayout.findViewById(R.id.txtLength);
		videoSeekbar= videoControlLayout.findViewById(R.id.seekBarVideo);
		videoSeekbar.setMax(MAX_PROGRESS);
		txtTitle= videoControlLayout.findViewById(R.id.txtTitle);
		LinearLayout.LayoutParams llp = (LayoutParams) videoSeekbar.getLayoutParams();
		llp.bottomMargin=PixelTransformer.getPixelsFromHeight(0.02f);
		videoSeekbar.setLayoutParams(llp);
		llp = (LayoutParams) txtTitle.getLayoutParams();
		llp.topMargin=PixelTransformer.getPixelsFromHeight(0.01f)+PixelTransformer.getPixelsFromDp(5);
		llp.bottomMargin=PixelTransformer.getPixelsFromHeight(0.01f);
		txtTitle.setLayoutParams(llp);
		if (!RaspiListActivity.mTwoPane){
			txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
		}else{
			txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
		}
		exec=Executors.newSingleThreadScheduledExecutor();
		setSeekBar();
		if (SshConnection.videoInfo.equals("")){
			videoControlLayout.setVisibility(View.GONE);
		}else{
			videoControlLayout.setVisibility(View.VISIBLE);
			synchronized (playstatus)
			{
				if (SshConnection.videoInfo.contains("\n") && playstatus.totalLengthMilliSeconds>=1000) {
					String actHour = "";
					if (playstatus.actPositionMilliSeconds > 3600000) {
						actHour = playstatus.actPositionMilliSeconds / 3600000 + ":";
					}
					String actPos = actHour
							+ MINUTE_FORMAT.format((playstatus.actPositionMilliSeconds / 60000) % 60) + ":"
							+ SECONDS_FORMAT.format((playstatus.actPositionMilliSeconds % 60000) / 1000);
					int progress = (int) ((MAX_PROGRESS * playstatus.actPositionMilliSeconds) / playstatus.totalLengthMilliSeconds);
					txtActPos.setText(actPos);
					String textTitle = SshConnection.videoInfo.split("\n")[1].replace("_", "\u200B_\u200B");
					if (hideExtensions && SshConnection.videoInfo.startsWith("local") && textTitle.contains(".")) {
						txtTitle.setText(textTitle.substring(0, textTitle.lastIndexOf(".")));
					} else {
						txtTitle.setText(textTitle);
					}
					videoSeekbar.setProgress(progress);
					if (!playstatus.isSeekAble) {
						videoSeekbar.setVisibility(View.GONE);
						txtLength.setVisibility(View.GONE);
						txtActPos.setVisibility(View.GONE);
					} else {
						videoSeekbar.setVisibility(View.VISIBLE);
						txtLength.setVisibility(View.VISIBLE);
						txtActPos.setVisibility(View.VISIBLE);
					}
				}
			}
		}
		if (syncOnStart){
			if(SshConnection.queueTitles==null){
				SshConnection.readQueue(calledActivity, 100, true);
			}else{
				SshConnection.readQueue(calledActivity, 500, false);
			}
			syncWithRaspi(50, 1, true);
		}
		checkForQueueUpdate();
		runExecutor();
	}

	private void runExecutor() {
		exec.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				String[] videoInfos=SshConnection.videoInfo.split("\n");
				synchronized (playstatus) {
					if (videoInfos.length >= 2 && playstatus.isSeekAble) {
						if (playstatus.isPlaying) {
							if (lastTimeMillis >= 0) {
								playstatus.actPositionMilliSeconds += (System.currentTimeMillis() - lastTimeMillis);
							}
						}
						lastTimeMillis = System.currentTimeMillis();
						setMediaInfo(videoInfos[1]);
					} else if (videoInfos.length < 2) {
						hideVideoControl();
					}
					if ((index >= nextSyncSeconds || (playstatus.actPositionMilliSeconds > playstatus.totalLengthMilliSeconds) && playstatus.isSeekAble)
							&& !isTracking) {
						if (playstatus.actPositionMilliSeconds > playstatus.totalLengthMilliSeconds)
							playstatus.activatedAudioStream = 0;
						syncWithRaspi(0, -1, false);
					} else {
						index++;
					}
				}
				if(index==3&&queueNeedsUpdate){
					checkForQueueUpdate();
				}
			}
		}, 0, 1, TimeUnit.SECONDS);
	}

	private void setSeekBar() {
		videoSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				isTracking=true;
				synchronized (playstatus) {
					if (SshConnection.videoInfo != null) {
						String[] videoInfos = SshConnection.videoInfo.split("\n");
						if (videoInfos.length >= 2 && (System.currentTimeMillis() - lastSeekTime) > 800) {
							lastSeekTime = System.currentTimeMillis();
							int startSeconds = (int) ((playstatus.totalLengthMilliSeconds * seekBar.getProgress() / MAX_PROGRESS)) / 1000;
							if (startSeconds >= (playstatus.totalLengthMilliSeconds / 1000)) {
								startSeconds = 0;
							}
							SshConnection.setPosition(startSeconds, calledActivity.getApplicationContext(), false);
							playstatus.actPositionMilliSeconds = (startSeconds * 1000);
							lastTimeMillis = System.currentTimeMillis();
							syncWithRaspi(1600, 2, false);
						}
					}
				}
				isTracking=false;
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				isTracking=true;
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser){
					if (SshConnection.videoInfo != null){
						String[] videoInfos=SshConnection.videoInfo.split("\n");
						if (videoInfos.length >= 2){
							synchronized (playstatus) {
								int actSeconds = (int) ((playstatus.totalLengthMilliSeconds * progress / MAX_PROGRESS)) / 1000;

								String actHour = "";
								if (actSeconds > 3600) {
									actHour = actSeconds / 3600 + ":";
								}
								txtActPos.setText(actHour + MINUTE_FORMAT.format((actSeconds / 60) % 60) + ":"
										+ SECONDS_FORMAT.format(actSeconds % 60));
							}
						}
					}
				}

			}
		});
	}

	private void setActionBar(final boolean playing, final boolean seekAble) {
		if (calledActivity.isFinishing()){
			return;
		}
		calledActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((OverflowMenuFragActivity) calledActivity).changePlayPauseIcon(playing, seekAble);
			}
		});
	}

	private void hideVideoControl() {		
		if (!isShuttingDown && mainLayout.getChildCount() > 1 && videoControlLayout.isShown() && !isTranslatingOut){
			HttpFileStreamer.closeFileStreamer(calledActivity.getApplicationContext(), true);
			playstatus.actPositionMilliSeconds=-1;
			playstatus.totalLengthMilliSeconds=-1;
			playstatus.isPlaying=false;
			calledActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if(isShuttingDown) return;
					isTranslatingOut=true;
					((OverflowMenuFragActivity) calledActivity).changePlayPauseIcon(false, false);
					int toYDelta= (playstatus.isSeekAble) ? 400 : 200;
					TranslateAnimation slide=new TranslateAnimation(0, 0, 0, toYDelta);
					slide.setDuration(300);
					videoControlLayout.startAnimation(slide);
					slide.setAnimationListener(new AnimationListener() {

						@Override
						public void onAnimationStart(Animation animation) {
							videoControlLayout.setVisibility(View.GONE);
						}

						@Override
						public void onAnimationRepeat(Animation animation) {
							// I don't need it.
						}

						@Override
						public void onAnimationEnd(Animation animation) {
							isTranslatingOut=false;
						}
					});
				}
			});
		}
	}

	public void syncWithRaspi(final int delay, final int retries, final boolean onlyRetryOnNull) {
		index=0;
		if (isWaitingForInterrupt || isShuttingDown){
			return;
		}
		if (syncThrd != null && syncThrd.isAlive()){
			if (retries < 0){
				return;
			}
			if (canInterrupt){
				syncThrd.interrupt();
			}else{
				requestInterrupt=true;
			}
			isWaitingForInterrupt=true;
			new Thread() {
				public void run() {
					while (syncThrd.isAlive()&& !isShuttingDown){
						try{
							Thread.sleep(5);
						}catch (InterruptedException e){
							isWaitingForInterrupt=false;
							return;
						}
					}
					isWaitingForInterrupt=false;
					if(!isShuttingDown){
						syncThrd=new Thread("SyncThread") {
							@Override
							public void run() {
								if(isShuttingDown) return;
								doSyncing(delay, retries, onlyRetryOnNull);
							}
						};
						syncThrd.start();
					}
				}
			}.start();
		}else{
			if(!isShuttingDown){
				syncThrd=new Thread("SyncThread") {
					@Override
					public void run() {
						if(isShuttingDown) return;
						doSyncing(delay, retries, onlyRetryOnNull);
					}
				};
				syncThrd.start();
			}
		}
	}

	private void doSyncing(final int delay, final int retries, final boolean onlyRetryOnNull) {
		OmxPlaystatus tempStatus;
		try{
			canInterrupt=true;
			if (delay > 0){
				Thread.sleep(delay);
			}
			if (isShuttingDown){
				return;
			}
			tempStatus=SshConnection.getPlaystatus(calledActivity.getApplicationContext());
			if (requestInterrupt || isShuttingDown){
				if(tempStatus==null ||tempStatus.totalLengthMilliSeconds==-1){
					requestInterrupt=false;
					return;
				}
			}
		}catch (InterruptedException ie){
			canInterrupt=false;
			return;
		}
		index=0;
		String[] videoInfos=SshConnection.videoInfo.split("\n");
		synchronized (playstatus) {
			if (tempStatus != null && videoInfos.length >= 2) {
				if (tempStatus.totalLengthMilliSeconds == -1) {
					tempStatus.totalLengthMilliSeconds = playstatus.totalLengthMilliSeconds;
					tempStatus.audioStreams = playstatus.audioStreams;
					tempStatus.activatedAudioStream = playstatus.activatedAudioStream;
					tempStatus.subtitleStreams = playstatus.subtitleStreams;
					tempStatus.isSeekAble = playstatus.isSeekAble;
					if (tempStatus.isSeekAble) {
						long latency = playstatus.actPositionMilliSeconds - tempStatus.actPositionMilliSeconds;
						if (tempStatus.isPlaying) {
							latency -= SYNC_DELAY_MILLIS;
							latency += (System.currentTimeMillis() - lastTimeMillis);
						}
						if (Math.abs(latency) >= 2500) {
							nextSyncSeconds = 3;
						} else {
							nextSyncSeconds = 10;
						}
					} else {
						nextSyncSeconds = 10;
					}
				} else if (tempStatus.isSeekAble) {
					nextSyncSeconds = 3;
				}
				if (tempStatus.isPlaying && tempStatus.isSeekAble) {
					tempStatus.actPositionMilliSeconds += SYNC_DELAY_MILLIS;
				}
				lastTimeMillis = System.currentTimeMillis();
				if (tempStatus.isPlaying != playstatus.isPlaying || tempStatus.audioStreams != playstatus.audioStreams
						|| tempStatus.subtitleStreams != playstatus.subtitleStreams || playstatus.isSeekAble != tempStatus.isSeekAble) {
					playstatus.audioStreams = tempStatus.audioStreams;
					if (!isShuttingDown)
						setActionBar(tempStatus.isPlaying, tempStatus.isSeekAble);
				}
				if ((tempStatus.actPositionMilliSeconds - playstatus.actPositionMilliSeconds) > 3000
						|| (tempStatus.actPositionMilliSeconds - playstatus.actPositionMilliSeconds) < -3000
						|| playstatus.actPositionMilliSeconds == -1) {
					copyPlayStatus(tempStatus);
					setMediaInfo(videoInfos[1]);
				} else {
					copyPlayStatus(tempStatus);
				}
			} else {
				if (videoInfos.length < 2) {
					hideVideoControl();
				} else if (playstatus.isSeekAble) {
					index = (byte) (nextSyncSeconds - 1);
				}
			}
		}
		if(retries==-1){
			SshConnection.readQueue(calledActivity, 0, false);
		}
		if (retries > 0 && !requestInterrupt && !isShuttingDown && (!onlyRetryOnNull || tempStatus==null)){
			syncWithRaspi(750, retries - 1, onlyRetryOnNull);
		}
		requestInterrupt=false;
	}

	private void copyPlayStatus(OmxPlaystatus tempStatus)
	{
		playstatus.activatedAudioStream = tempStatus.activatedAudioStream;
		playstatus.actPositionMilliSeconds = tempStatus.actPositionMilliSeconds;
		playstatus.audioStreams = tempStatus.audioStreams;
		playstatus.isPlaying = tempStatus.isPlaying;
		playstatus.isSeekAble = tempStatus.isSeekAble;
		playstatus.subtitleStreams = tempStatus.subtitleStreams;
		playstatus.totalLengthMilliSeconds = tempStatus.totalLengthMilliSeconds;
	}

	private void setMediaInfo(String title) {
		if (playstatus.totalLengthMilliSeconds < 1000|| isShuttingDown) return;
		String actHour="";
		if (playstatus.actPositionMilliSeconds > 3600000){
			actHour=playstatus.actPositionMilliSeconds / 3600000 + ":";
		}
		final String actPos=actHour + MINUTE_FORMAT.format((playstatus.actPositionMilliSeconds / 60000) % 60)
				+ ":" + SECONDS_FORMAT.format((playstatus.actPositionMilliSeconds % 60000) / 1000);
		String totalHour="";
		if (playstatus.totalLengthMilliSeconds > 3600000){
			totalHour=playstatus.totalLengthMilliSeconds / 3600000 + ":";
		}
		final String totalLength=totalHour
				+ MINUTE_FORMAT.format((playstatus.totalLengthMilliSeconds / 60000) % 60) + ":"
				+ SECONDS_FORMAT.format(((playstatus.totalLengthMilliSeconds) % 60000) / 1000);
		final int progress = (int) ((MAX_PROGRESS*playstatus.actPositionMilliSeconds)/playstatus.totalLengthMilliSeconds);
		title=title.replace("_", "\u200B_\u200B");
		final String textTitle;
		if(hideExtensions&& SshConnection.videoInfo.startsWith("local")&& title.contains(".")){
			textTitle=title.substring(0, title.lastIndexOf("."));
		}else{
			textTitle=title;
		}

		final boolean isSeekAble = playstatus.isSeekAble;

		calledActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(!isShuttingDown){
					if (!isSeekAble){
						videoSeekbar.setVisibility(View.GONE);
						txtLength.setVisibility(View.GONE);
						txtTitle.setText(textTitle);
						txtActPos.setVisibility(View.GONE);
					}else if (!isTracking){
						videoSeekbar.setVisibility(View.VISIBLE);
						txtLength.setVisibility(View.VISIBLE);
						txtActPos.setVisibility(View.VISIBLE);
						txtLength.setText(totalLength);
						txtTitle.setText(textTitle);
						txtActPos.setText(actPos);
						videoSeekbar.setProgress(progress);
					}
					if (!videoControlLayout.isShown()){
						videoControlLayout.setVisibility(View.VISIBLE);
						int fromYDelta= (isSeekAble) ? 300 : 150;
						TranslateAnimation slide=new TranslateAnimation(0, 0, fromYDelta, 0);
						slide.setDuration(300);
						videoControlLayout.startAnimation(slide);
					}
				}
			}
		});
	}
	

	public void killThread() {
		exec.shutdownNow();
		isRunning = false;
		isShuttingDown=true;
		if (syncThrd != null && syncThrd.isAlive()){
			if (canInterrupt){
				syncThrd.interrupt();
			}else{
				requestInterrupt=true;
			}
		}
		syncOnStart=true;
	}
	
	private void checkForQueueUpdate(){
		File cacheFile=new File(calledActivity.getCacheDir().getAbsolutePath() + "/" + Constants.LAST_UPDATE_QUEUE_FILE);
		BufferedReader reader=null;
		long lastQueueUpdate;
		if(cacheFile.exists()){
			try{
				reader=new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), "UTF-8"));
				lastQueueUpdate=Long.parseLong(reader.readLine());
			}catch (Exception e){
				lastQueueUpdate=0;
			}finally{
				try{
					if(reader != null)
						reader.close();
				}catch (IOException e){
					e.printStackTrace();
				}
			}
		}else{
			lastQueueUpdate=0;
		}
			
		if((System.currentTimeMillis()/1000)-lastQueueUpdate>12600){
			queueNeedsUpdate=true;
			SshConnection.getQueueYoutubeIds(calledActivity.getApplicationContext());
		}else{
			queueNeedsUpdate=false;
		}
	}

	public static OmxPlaystatus getPlaystatus()
	{
		synchronized (playstatus)
		{
			return playstatus;
		}
	}

	public static void resetActiveAudioStream()
	{
		synchronized (playstatus)
		{
			playstatus.activatedAudioStream = 0;
		}
	}

	public static void incrementActPosition(final int incrementMs)
	{
		synchronized (playstatus)
		{
			playstatus.actPositionMilliSeconds += incrementMs;
		}
	}

	public static void setActiveAudioStream(final int streamIndex)
	{
		synchronized (playstatus)
		{
			playstatus.activatedAudioStream = streamIndex;
		}
	}

}
