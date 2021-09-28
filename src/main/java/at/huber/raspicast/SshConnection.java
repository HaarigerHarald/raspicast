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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import at.huber.raspicast.dialogs.CertDialog;
import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.RaspiUtils;
import at.huber.raspicast.youtube.RaspiYouTubeExtractor;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.ConnectionInfo;
import ch.ethz.ssh2.KnownHosts;
import ch.ethz.ssh2.Session;

public class SshConnection {

	static String TEMP_FILES_LOCATION="/tmp";
	
	private static String ABORT_COMMAND="pkill omxplayer.bin;pkill -x omxplayer;rm " + TEMP_FILES_LOCATION + "/.r_info;rm " + TEMP_FILES_LOCATION + "/.linenum;";

	private final static String CMD_SEARCH_ABSOLUTE_PRE="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.SetPosition objpath:/not/used int64:";
	private final static String CMD_SEARCH_RELATIVE_PRE="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.Seek int64:";
	private final static String CMD_SEARCH_POST="000000 >/dev/null 2>&1";

	private static String CMD_LOUDER="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.Action int32:18>/dev/null 2>&1;echo -n +>>" + TEMP_FILES_LOCATION + "/.r_input 2>/dev/null";
	private static String CMD_QUIETER="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.Action int32:17>/dev/null 2>&1;echo -n ->>" + TEMP_FILES_LOCATION + "/.r_input 2>/dev/null;";

	private static String CMD_REPEAT="rm " + TEMP_FILES_LOCATION + "/.r_loop 2>/dev/null";
	private static String CMD_DO_NOT_REPEAT="echo> " + TEMP_FILES_LOCATION + "/.r_loop 2>/dev/null";

	private final static String CMD_CHANGE_AUDIO_STREAM_PRE="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.SelectAudio int32:";
	private final static String CMD_CHANGE_AUDIO_STREAM_POST=" >/dev/null 2>&1";
	private final static String CMD_SHOW_SUBTITLES_STREAM_PRE="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.ShowSubtitles&&dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.SelectSubtitle int32:";
	private final static String CMD_SHOW_SUBTITLES_STREAM_POST=" )>/dev/null 2>&1";
	private final static String CMD_HIDE_SUBTITLES="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.HideSubtitles>/dev/null 2>&1";

	private final static String CMD_PLAY_PAUSE="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.PlayPause>/dev/null 2>&1";
	private static String CMD_STOP=ABORT_COMMAND + "echo -n> " + TEMP_FILES_LOCATION + "/.r_input 2>/dev/null;pkill omxiv";
	private final static String CMD_FULL_STATUS="a=\"dbus-send --print-reply=literal --reply-timeout=800 --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2  org.\";b=\"mpris.MediaPlayer2.Player.\";c=\"freedesktop.DBus.Properties.\";${a}${c}Duration&&${a}${b}ListAudio&&${a}${b}ListSubtitles&&${a}${c}Position&&${a}${c}Duration&&${a}${c}PlaybackStatus)2>/dev/null";
	private final static String CMD_YT_STATUS="a=\"dbus-send --print-reply=literal --reply-timeout=800 --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2  org.\";c=\"freedesktop.DBus.Properties.\";${a}${c}Duration&&${a}${c}Position&&${a}${c}PlaybackStatus)2>/dev/null";
	private final static String CMD_REDUCED_STATUS="a=\"dbus-send --print-reply=literal --reply-timeout=800 --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.freedesktop.DBus.Properties.\";${a}Position&&${a}PlaybackStatus)2>/dev/null";
	
	private final static String CMD_ONLY_AUDIO_STATUS="dbus-send --print-reply=literal --reply-timeout=300 --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.ListAudio 2>/dev/null";
	
	private static final String[] THREAD_NAMES = { "SessionThread1", "SessionThread2", "SessionThread3", "SessionThread4" };

	private static Connection conn;

	public static volatile boolean started=false;// Is a Connection started
	private static volatile boolean isConnecting=false;// Is currently connecting
	private static AtomicInteger numWaiting = new AtomicInteger(0);// Number of threads waiting to execute a Command
	private final static Lock lock=new ReentrantLock(true);
	private static long lastVolumeControlMillis=0;
	private static long lastStartProgrammMillis=0;
	private static volatile boolean updateQueueRunning=false;
	private static volatile boolean requeuestFinalize=false;

	private static String sshHostname;
	private static int sshPort=-1;
	private static String sshUser;
	private static String sshPassword;
	private static String sshKeyFile;
	
	private static Thread readQueueThread;
	private static volatile boolean queueThreadcanInterrupt=false;
	
	private static boolean dbusFilesAssociatedWithUser=true;
	private static volatile boolean unreachable=false;
	
	/* Contains the "<source>\n<title>" of the video or "" if 
	 * nothing is playing local copy of TEMP_FILES_LOCATION/info */
	public volatile static String videoInfo="";
	public volatile static boolean loopStreams=false;
	public volatile static int liveOption=0;
	public volatile static String audioOuputDevice="hdmi";
	public volatile static String[] queueTitles=null;
	public volatile static int queueIndex=0;
	public volatile static String homeDir;
	public volatile static int volumeOffset=0;
	public volatile static boolean insecure=false;
	public volatile static boolean queueHasCast = false;


	public static void updateTmpFileLoc(){
		ABORT_COMMAND="pkill omxplayer.bin;pkill -x omxplayer;rm " + TEMP_FILES_LOCATION + "/.r_info;rm " + TEMP_FILES_LOCATION + "/.linenum;";

		CMD_LOUDER="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.Action int32:18>/dev/null 2>&1;echo -n +>>" + TEMP_FILES_LOCATION + "/.r_input 2>/dev/null";
		CMD_QUIETER="dbus-send --print-reply --dest=org.mpris.MediaPlayer2.omxplayer /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.Action int32:17>/dev/null 2>&1;echo -n ->>" + TEMP_FILES_LOCATION + "/.r_input 2>/dev/null";

		CMD_REPEAT="rm " + TEMP_FILES_LOCATION + "/.r_loop 2>/dev/null";
		CMD_DO_NOT_REPEAT="echo> " + TEMP_FILES_LOCATION + "/.r_loop 2>/dev/null";

		CMD_STOP=ABORT_COMMAND + "echo -n> " + TEMP_FILES_LOCATION
				+ "/.r_input 2>/dev/null;pkill omxiv";
	}
	
	public static void openSshConnection(final Context context, final String command,
			final boolean importantCommand, final boolean errorMessage) {
		if (!started && sshHostname != null && sshPort != -1 && sshUser != null && !isConnecting 
				&& !insecure){
			if(sshKeyFile != null && !RaspiListActivity.hasStoragePermission) {
				Toast.makeText(context, R.string.no_storage_permission, Toast.LENGTH_LONG).show();
				return;
			}
			if(context==null) return;
			isConnecting=true;
			new Thread("ConnnectThread") {
				@Override
				public void run() {
					lock.lock();
					try{
						if(conn!=null){
							conn.close();
						}
						TEMP_FILES_LOCATION = context.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
							getString(Constants.PREF_TEMP_DIR,"/dev/shm");
						updateTmpFileLoc();
						Log.d(SshConnection.class.getSimpleName(), "Starting connection");
						boolean isAuthenticated;
						conn=new Connection(sshHostname, sshPort);
						unreachable=false;
						ConnectionInfo conInfo=conn.connect(null, 0, 2700);			
						String fingerprint = KnownHosts.createHexFingerprint(conInfo.serverHostKeyAlgorithm, conInfo.serverHostKey);
						String oldFingerprint= context.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE).
								getString(Constants.PREF_SSH_FINGERPRINT, null);
						if(oldFingerprint!=null && !fingerprint.equals(oldFingerprint) && context instanceof FragmentActivity){
							new CertDialog(fingerprint).show(((FragmentActivity)context).getFragmentManager(), "fingerprint");
							insecure=true;
							return;
						}else if(oldFingerprint==null){
							Editor edit= context.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE).edit();
							edit.putString(Constants.PREF_SSH_FINGERPRINT, fingerprint);
							edit.apply();
						}
						if(sshKeyFile==null){
							if(sshPassword != null)
								isAuthenticated=conn.authenticateWithPassword(sshUser, sshPassword);
							else
								isAuthenticated=conn.authenticateWithNone(sshUser);
						}else{
							String keyFilePath= Environment.getExternalStorageDirectory().getAbsolutePath() +sshKeyFile;
							File keyFile= new File(keyFilePath);
							if (keyFile.isFile()) {
								isAuthenticated = conn.authenticateWithPublicKey(sshUser, keyFile, sshPassword);
							} else {
								isAuthenticated = false;
							}
						}
						if (isAuthenticated){
							initConnection();
							started=true;
							if (command != null){
								executeCommand(context, command, importantCommand);
							}
						}else{
							if(errorMessage){
								new Handler(Looper.getMainLooper()).post(new Runnable() {
									@Override
									public void run() {
										Toast.makeText(context, R.string.error_wrong_login,
												Toast.LENGTH_LONG).show();
									}
								});
							}
							conn.close();
						}
					}catch (Exception e){
						e.printStackTrace();
						if(conn!=null){
							try {
								conn.close();
							} catch (Exception ignored){}
						}
						if (e.getClass().equals(IOException.class) && errorMessage){
							new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(context, R.string.error_cant_connect,
											Toast.LENGTH_LONG).show();
								}
							});
						}
						if (e.getClass().equals(SocketTimeoutException.class)
								&& sshHostname != null && errorMessage){
							new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(
											context,
											context.getResources().getString(R.string.error_cant_reach)
													+ " " + sshHostname, Toast.LENGTH_LONG).show();
								}
							});
						}
						if (e.getClass().equals(java.net.ConnectException.class)){
							unreachable=true;
							try{
								Thread.sleep(1000);
							}catch (InterruptedException ignored){}
						}
					}finally{
						lock.unlock();
						isConnecting=false;
					}
				}
			}.start();
		}
	}

	public static OmxPlaystatus getPlaystatus(final Context con) throws InterruptedException{
		if (isConnecting || (conn != null && started)){
		    lock.lockInterruptibly();
		    Session sess=null;
			try{
			    VideoControlExecutor.canInterrupt=false;
				if (conn != null && conn.isAuthenticationComplete() && started){
					Log.d(SshConnection.class.getSimpleName(), "Syncing with raspi");
					sess=conn.openSession();
					sess.execCommand("cat " + TEMP_FILES_LOCATION + "/.r_info 2>/dev/null");
					BufferedReader br=new BufferedReader(new InputStreamReader(sess.getStdout()));
					StringBuilder strBuild=new StringBuilder("");
					String line;
					while ((line=br.readLine()) != null){
						strBuild.append(line);
						strBuild.append("\n");
					}
					br.close();
					sess.close();
					String tempVideoInfo=strBuild.toString();
					if (VideoControlExecutor.requestInterrupt){
						return null;
					}
					if (!tempVideoInfo.equals("") && started){
						OmxPlaystatus status=null;
						if(tempVideoInfo.startsWith("rtp://") || tempVideoInfo.startsWith("udp://")
								|| tempVideoInfo.startsWith("#?v=")){
							status=new OmxPlaystatus();
							status.isSeekAble=false;
							status.isPlaying=true;
							status.totalLengthMilliSeconds=4000;
							sess=conn.openSession();
							sess.execCommand(getDbus()+CMD_ONLY_AUDIO_STATUS);
							br=new BufferedReader(new InputStreamReader(sess.getStdout()));
							line=br.readLine();
							if (line != null && line.contains("[")){
								strBuild=new StringBuilder("");
								strBuild.append(line);
								if (!line.contains("]")){
									while (!line.contains("]")){
										line=br.readLine();
										if (line != null){
											strBuild.append(line);
										}else{
											break;
										}
									}
								}
								status.audioStreams= RaspiUtils.readStatusFromArrayString(strBuild
										.toString());
								status.activatedAudioStream= RaspiUtils
										.getActiveStream(status.audioStreams);
							}
							videoInfo=tempVideoInfo;
							br.close();
							sess.close();
							return status;
						}else if (!videoInfo.equals(tempVideoInfo)
								|| VideoControlExecutor.getPlaystatus().totalLengthMilliSeconds < 1000){
							status=new OmxPlaystatus();
							sess=conn.openSession();
							// YouTube videos are always seekable and don't have subtitles or multiple audio streams
							if(tempVideoInfo.startsWith("?v=")){
								sess.execCommand(getDbusExport()+CMD_YT_STATUS);
								status.isSeekAble=true;
								br=new BufferedReader(new InputStreamReader(sess.getStdout()));
								line=br.readLine();
								if (line != null){
									line=line.replaceAll("int64| ", "");
									status.totalLengthMilliSeconds=Long.parseLong(line) / 1000;
									line=br.readLine();
								}
								if (line != null){
									line=line.replace(" ", "");
									line=line.replace("int64", "");
									status.actPositionMilliSeconds=Long.parseLong(line) / 1000;
									line=br.readLine();	
								}
								if(line==null){
									status=null;
								}else{
									line=line.replace(" ", "");
									status.isPlaying = line.equals("Playing") || status.actPositionMilliSeconds == 0;
								}
							}else{
								sess.execCommand(getDbusExport()+CMD_FULL_STATUS);
								br=new BufferedReader(new InputStreamReader(sess.getStdout()));
								line=br.readLine();
								if (line != null){
									line=line.replaceAll("int64| ", "");
									status.totalLengthMilliSeconds=Long.parseLong(line) / 1000;
									line=br.readLine();
								}
								// Get Audio streams
								if (line != null && line.contains("[")){
									strBuild=new StringBuilder("");
									strBuild.append(line);
									if (!line.contains("]")){
										while (!line.contains("]")){
											line=br.readLine();
											if (line != null){
												strBuild.append(line);
											}else{
												break;
											}
										}
									}
									status.audioStreams= RaspiUtils.readStatusFromArrayString(strBuild
											.toString());
									status.activatedAudioStream= RaspiUtils
											.getActiveStream(status.audioStreams);
									line=br.readLine();
								}
								// Get subtitle streams
								if (line != null && line.contains("[")){
									strBuild=new StringBuilder("");
									strBuild.append(line);
									if (!line.contains("]")){
										while (!line.contains("]")){
											line=br.readLine();
											if (line != null){
												strBuild.append(line);
											}else{
												break;
											}
										}
									}
									status.subtitleStreams= RaspiUtils
											.readStatusFromArrayString(strBuild.toString());
									line=br.readLine();
								}
								if (line != null){
									line=line.replace(" ", "");
									line=line.replace("int64", "");
									status.actPositionMilliSeconds=Long.parseLong(line) / 1000;
									line=br.readLine();
									
								}
								if (line != null){
									line=line.replaceAll("int64| ", "");
									long duration2=(Long.parseLong(line) / 1000);
									if(duration2!=status.totalLengthMilliSeconds){
										status.totalLengthMilliSeconds=4000;
										status.actPositionMilliSeconds=-1;
										status.isSeekAble=false;
									}else{
										status.isSeekAble=true;
									}
									line=br.readLine();
								}
								if(line==null){
									status=null;
								}else{
									line=line.replace(" ", "");
									status.isPlaying = line.equals("Playing") || status.actPositionMilliSeconds == 0;
								}
							}
							br.close();
							sess.close();
						}else if(VideoControlExecutor.getPlaystatus().isSeekAble){
							status=new OmxPlaystatus();
							sess=conn.openSession();
							sess.execCommand(getDbusExport()+CMD_REDUCED_STATUS);
							br=new BufferedReader(new InputStreamReader(sess.getStdout()));
							line=br.readLine();
							if (line == null){
								status=null;
							}else{
								line=line.replace(" ", "");
								line=line.replace("int64", "");
								status.actPositionMilliSeconds=Long.parseLong(line) / 1000;
								line=br.readLine();
								if (line == null){
									status=null;
								}else{
									line=line.replace(" ", "");
									status.isPlaying = line.equals("Playing") || status.actPositionMilliSeconds == 0;
								}
							}
							br.close();
							sess.close();
						}
						if (status != null && status.totalLengthMilliSeconds > 1000){
							videoInfo=tempVideoInfo;
						}
						return status;
					}else{
						videoInfo="";
						return null;
					}
				}else{
					if (conn != null && !conn.isAuthenticationComplete()){
						started=false;
						openSshConnection(con, null, false, false);
					}
				}
			}catch (Exception ex){
				if (sess != null)
					sess.close();
				if (ex.getClass().equals(java.net.SocketException.class) || ex.getClass().equals(java.io.IOException.class)){
					if (!isConnecting && sshUser != null){
						started=false;
						openSshConnection(con, null, false, false);
					}
				}else{
					videoInfo="";
				}
			}finally{
				lock.unlock();
			}
		}else{
			if (!isConnecting && hasData()){
				started=false;
				openSshConnection(con, null, false, false);
			}
		}
		return null;
	}

	public static String[] listDirectories(String location, Context con) {
		if (isConnecting || (conn != null && started)){
			numWaiting.incrementAndGet();
			lock.lock();
			Session sess=null;
			try{
				numWaiting.decrementAndGet();
				if (conn != null && conn.isAuthenticationComplete()){
					sess=conn.openSession();
					if (location.startsWith("~")){
						if (location.equals("~")){
							sess.execCommand("pwd;ls -Lp1 --group-directories-first");
						}else{
							location=location.substring(location.indexOf("/") + 1);
							sess.execCommand("pwd;ls -Lp1 --group-directories-first \"" + location + "\"");
						}
					}else{
						sess.execCommand("pwd;ls -Lp1 --group-directories-first \"" + location + "\"");
					}
					BufferedReader br=new BufferedReader(new InputStreamReader(sess.getStdout()));
					List<String> files=new ArrayList<>();
					String line=br.readLine();
					if(line!=null){
						if(line.startsWith("/")){
							homeDir=line;
						}else{
							files.add(line);
						}
						while ((line=br.readLine()) != null){
							files.add(line);
						}
					}
					br.close();
					sess.close();
					return files.toArray(new String[files.size()]);
				}else{
					if (conn != null && !conn.isAuthenticationComplete() && !isConnecting){
						started=false;
						openSshConnection(con, null, false, false);
					}
				}
			}catch (Exception ex){
				if (sess != null)
					sess.close();
				if (ex.getClass().equals(java.net.SocketException.class) || ex.getClass().equals(java.io.IOException.class)){
					if (!isConnecting && hasData()){
						started=false;
						openSshConnection(con, null, false, false);
					}
				}
			}finally{
				lock.unlock();
			}
		}else{
			if (!isConnecting && hasData()){
				started=false;
				openSshConnection(con, null, false, false);
			}
		}
		return null;
	}

	public static void executeCommand(final Context con, final String command, final boolean important) {
		if (isConnecting || (conn != null && started)){
			if (numWaiting.get() > 1 && !important){
				return;
			} else if (numWaiting.get() > 3) {
				return;
			}
			new Thread(THREAD_NAMES[numWaiting.incrementAndGet() - 1]) {
				@Override
				public void run() {
					final int waitSeconds = (important) ? 3000 : 1500;
					try {
						if (!lock.tryLock(waitSeconds, TimeUnit.MILLISECONDS)) {
							return;
						}
					} catch (InterruptedException ie) {
						return;
					} finally {
						numWaiting.decrementAndGet();
					}
					Session sess = null;
					try {
						if (conn != null && conn.isAuthenticationComplete()) {
							sess = conn.openSession();
							sess.execCommand(command);
							sess.close();
						} else {
							if (conn != null && !conn.isAuthenticationComplete() && !isConnecting) {
								started = false;
								openSshConnection(con, "(" + command + ")&>/dev/null", important, false);
							}
						}
					} catch (Exception e) {
						if (sess != null)
							sess.close();
						if (!isConnecting && e.getClass().equals(java.net.SocketException.class) || e.getClass().equals(java.io.IOException.class)) {
							started = false;
							openSshConnection(con, command, important, false);
						}
					} finally {
						lock.unlock();
					}
				}
			}.start();
		} else {
			if (!isConnecting && hasData()) {
				started = false;
				openSshConnection(con, command, important, false);
			}
		}
	}

	public static void readCastStatus(final  Context con) {
		if (isConnecting || (conn != null && started)){
			if (numWaiting.get() > 3) {
				return;
			}
			new Thread(THREAD_NAMES[numWaiting.incrementAndGet() - 1]) {
				@Override
				public void run() {
					try {
						if (!lock.tryLock(3000, TimeUnit.MILLISECONDS)) {
							return;
						}
					} catch (InterruptedException ie) {
						return;
					} finally {
						numWaiting.decrementAndGet();
					}
					Session sess = null;
					try {
						if (conn != null && conn.isAuthenticationComplete()) {
							sess = conn.openSession();
							sess.execCommand("cat " + TEMP_FILES_LOCATION + "/.r_info 2>/dev/null");
							BufferedReader br=new BufferedReader(new InputStreamReader(sess.getStdout()));
							if (br.readLine() == null){
								HttpFileStreamer.closeFileStreamer(con, true);
							}
							br.close();
							sess.getStdout().close();
							sess.close();
						} else {
							if (conn != null && !conn.isAuthenticationComplete() && !isConnecting) {
								started = false;
								openSshConnection(con, null, false, false);
							}
						}
					} catch (Exception e) {
						if (sess != null)
							sess.close();
						if (!isConnecting && e.getClass().equals(java.net.SocketException.class) || e.getClass().equals(java.io.IOException.class)) {
							started = false;
							openSshConnection(con, null, false, false);
						}
					} finally {
						lock.unlock();
					}
				}
			}.start();
		} else {
			if (!isConnecting && hasData()) {
				started = false;
				openSshConnection(con, null, false, false);
			}
		}
	}

	public static void getQueueYoutubeIds(final Context con) {
		if ((isConnecting || (conn != null && started)) && !updateQueueRunning) {
			numWaiting.incrementAndGet();
			updateQueueRunning = true;
			new Thread("GetYoutubeIds") {
				@Override
				public void run() {
					try {
						try {
							if (!lock.tryLock(6, TimeUnit.SECONDS)) {
								return;
							}
						} catch (InterruptedException ie) {
							return;
						} finally {
							numWaiting.decrementAndGet();
						}
						ArrayList<String> ytIds = null;
						ArrayList<Integer> lineNums = null;
						Session sess = null;
						try {
							if (conn != null && conn.isAuthenticationComplete()) {
								Log.d("Update", "queue");
								sess = conn.openSession();
								sess.execCommand("sed -n 1~4p " + TEMP_FILES_LOCATION + "/.queue");
								BufferedReader br = new BufferedReader(new InputStreamReader(sess.getStdout()));
								ytIds = new ArrayList<>();
								lineNums = new ArrayList<>();
								String line;
								for (int i = 1; (line = br.readLine()) != null; i++) {
									if (line.startsWith("?v=")) {
										ytIds.add(line);
										lineNums.add(i);
									}
								}
								br.close();
								sess.close();
							} else {
								if (conn != null && !conn.isAuthenticationComplete() && !isConnecting && !unreachable) {
									started = false;
									openSshConnection(con, null, false, false);
									getQueueYoutubeIds(con);
								}
							}
						} catch (Exception e) {
							if (sess != null)
								sess.close();
							if (!unreachable && (!isConnecting && e.getClass().equals(java.net.SocketException.class) || e.getClass().equals(java.io.IOException.class))) {
								started = false;
								openSshConnection(con, null, false, false);
								getQueueYoutubeIds(con);
							}
							return;
						} finally {
							lock.unlock();
						}
						if (ytIds != null && ytIds.size() > 0 && lineNums.size() > 0) {
							if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
								Looper.prepare();
							}
							new RaspiYouTubeExtractor(con,
									RaspiUtils.convertIntegers(lineNums)).doInBackground(ytIds
									.toArray(new String[ytIds.size()]));

						} else if (ytIds != null) {
							queueUpdateDone(con);
						}
					} finally {
						updateQueueRunning = false;
						if (requeuestFinalize)
							finalizeConnection(true);
					}
				}
			}.start();
		} else if (!updateQueueRunning && !isConnecting && hasData() && !unreachable) {
			started = false;
			openSshConnection(con, null, false, false);
			getQueueYoutubeIds(con);
		}
	}
	
	private static void queueUpdateDone(Context con){
		BufferedWriter writer=null;
		File cacheFile=new File(con.getCacheDir().getAbsolutePath() + "/" + Constants.LAST_UPDATE_QUEUE_FILE);
		try{
			writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cacheFile), "UTF-8"));
			writer.write((System.currentTimeMillis()/1000)+"");
		}catch (Exception e){
			e.printStackTrace();
		}finally{
			if (writer != null){
				try{
					writer.close();
				}catch (IOException e){
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void updateYtUrls(final Context con,final String[] newUrls, final int[] linenums, boolean update, int fromIndex){
		if(newUrls==null|| linenums==null)
			return;
		if(update)
			queueUpdateDone(con);
		String blank;
		if(con.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE).getInt(Constants.PREF_YOUTUBE_QUALITY, 0)>1){
			blank="";
		}else{
			blank="-b";
		}
		
		StringBuilder stb=new StringBuilder("");
		stb.append("f=");
		stb.append(TEMP_FILES_LOCATION);
		stb.append("/.queue;");
		stb.append("sed -i");
		for(int i=fromIndex; i<fromIndex+5 && i<linenums.length&& i<newUrls.length; i++){
			stb.append(" -e '");
			stb.append(linenums[i]*4-1);
			stb.append("s/.*/");
			stb.append(blank);
			stb.append("/' -e '");
			stb.append(linenums[i]*4);
			stb.append("s/.*/");
			stb.append(newUrls[i].replace("/", "\\/").replace("&", "\\&"));
			stb.append("/'");
		}
		stb.append(" $f");
		executeCommand(con, stb.toString(), true);
	}
	
	public static void readQueue(final Context con, final int delay, final boolean override){
		if (isConnecting || (conn != null && started)){
			 if (numWaiting.get() > 3){
				return;
			}
			if(readQueueThread!=null && readQueueThread.isAlive()){
				if(queueThreadcanInterrupt && override)
					readQueueThread.interrupt();
				else if(!override)
					return;
			}
			readQueueThread=new Thread("ReadQueueThread") {
				
				@Override
				public void run() {
					if(delay>=0){
						try{
							queueThreadcanInterrupt=true;
							Thread.sleep(delay);
							if(con==null){
								queueThreadcanInterrupt=false;
								return;
							}
						}catch(InterruptedException ie){
							queueThreadcanInterrupt=false;
							return;
						}	
					}
					numWaiting.incrementAndGet();
					int tempQueueIndex;
					String[] tempQueueTitles;
					try{
						tempQueueIndex=queueIndex;
						tempQueueTitles=queueTitles;
						if (!lock.tryLock(7, TimeUnit.SECONDS)){
							return;
						}
					}catch (InterruptedException ie){
						return;
					}finally{
						queueThreadcanInterrupt=false;
						numWaiting.decrementAndGet();
					}
					
					Session sess=null;
					try{
						if (conn != null && conn.isAuthenticationComplete()){
							Log.d("Read", "queue");
							sess=conn.openSession();
							sess.execCommand("cat "+TEMP_FILES_LOCATION+"/.linenum 2>/dev/null;test $? -gt 0&&echo 0;sed -n 2~4p "+
									TEMP_FILES_LOCATION+"/.queue&&echo&&sed -n 1~4p "+ TEMP_FILES_LOCATION+ "/.queue|grep -c cast");
							BufferedReader br=new BufferedReader(new InputStreamReader(sess.getStdout()));
							List<String> queue=new ArrayList<>();
							String line;
							line=br.readLine();
							if(line==null){
								queueTitles=null;
								queueIndex=0;
							}else{
								try{
									queueIndex=(Integer.parseInt(line) + 3) / 4;
								}catch (NumberFormatException ne){
									queueIndex=0;
									queue.add(line);
								}
								while ((line=br.readLine()) != null && !line.equals("")){
									queue.add(line);
								}
								
								if(line != null){
									line = br.readLine();
									try{
										int numCast = Integer.parseInt(line);
										queueHasCast = numCast > 0;
									}catch(NumberFormatException ne){
										queueHasCast = false;
									}
								}else{
									queueHasCast = false;
								}
								
								queueTitles=queue.toArray(new String[queue.size()]);
							}
							br.close();
							sess.close();
							
							if(tempQueueTitles==null && queueTitles==null) return;
							boolean updateUI= (tempQueueIndex != queueIndex);
							
							if(!updateUI){
								if((tempQueueTitles==null && queueTitles!=null )|| (queueTitles==null && tempQueueTitles!=null)){
									updateUI=true;
								}else if(tempQueueTitles.length!=queueTitles.length){
									updateUI=true;
								}else{
									for(int i=0; i<tempQueueTitles.length; i++){
										if(!tempQueueTitles[i].equals(queueTitles[i])){
											updateUI=true;
											break;
										}
									}
								}
							}
							if(updateUI){
								Intent intent = new Intent(QueueFragActivity.QUEUE_UPDATE_ACTION);
								con.sendBroadcast(intent);
							}
						}else{
							if (conn != null && !conn.isAuthenticationComplete() && !isConnecting){
								started=false;
								openSshConnection(con, null, false, false);
							}
						}
					}catch (Exception e){
						e.printStackTrace();
						if (sess != null)
							sess.close();
						if (!isConnecting && e.getClass().equals(java.net.SocketException.class) || e.getClass().equals(java.io.IOException.class)){
							started=false;
							openSshConnection(con, null, false, false);
						}
					}finally{
						lock.unlock();
					}
				}
			};
			readQueueThread.start();
		}else{
			if (!isConnecting&& hasData()){
				started=false;
				openSshConnection(con, null, false, false);
			}
		}
	}
	
	public static void addToQueue(final String[] streams, final Context context, final String[] videoTitles,
			final String source, SubtitleOptions so, boolean autostart, boolean forceAudio){
		StringBuilder stb=new StringBuilder("");
		String subtitleString= (so!=null) ? so.getSubtitleString(): "";
		stb.append("f=\""+TEMP_FILES_LOCATION+"/.queue\";l=\""+TEMP_FILES_LOCATION+"/.linenum\";");
		
		for(int i=0; i<streams.length&& i<videoTitles.length; i++){
			if(i!=0)
				stb.append(";");
			String title;
			if (videoTitles[i] != null){
				title=videoTitles[i].replace("\"", "\\\"").replace("$", "\\$");
			}else{
				return;
			}
			String blank="-b";
			if(forceAudio)
				blank = "";
			else if (source != null && (source.equals("local") || source.equals("cast"))){
				for(String audioExtension : Constants.COMMON_AUDIO_FILE_EXTENSIONS){
					if (videoTitles[i].contains(".") && 
							videoTitles[i].substring(videoTitles[i].lastIndexOf(".")).equalsIgnoreCase(audioExtension)){
						blank="";
						break;
					}
				}
			}
			if(source.startsWith("?v=")&& context.getSharedPreferences(Constants.PREF_FILE_NAME, 
					Context.MODE_PRIVATE).getInt(Constants.PREF_YOUTUBE_QUALITY, 1)>1){
				blank="";
			}
			if(source.equals("local"))
				stb.append("echo -e \""+source+"\n" + title + "\n" +blank+"\n"+streams[i].replace("$", "\\$")+
						"\">>$f 2>/dev/null");
			else
				stb.append("echo -e \""+source+"\n" + title + "\n" +blank+"\n"+streams[i]+"\">>$f 2>/dev/null");
		}
		if(autostart){
			String addOptions= context.getSharedPreferences(Constants.PREF_FILE_NAME, 
					Context.MODE_PRIVATE).getString(Constants.PREF_OMXPLAYER_OPTIONS, "");
			if(!addOptions.equals("")){
				addOptions=" " + addOptions +" ";
			}
			stb.append(";test ! -e "+TEMP_FILES_LOCATION+"/.r_info&&(echo $(($(wc -l<$f)-"+ (streams.length*4-1)+"))"+
				" >$l;while true;do while [ $(cat $l) -le $(wc -l<$f) ];do sed -n \"$(cat $l),+1p\" $f>"
				+TEMP_FILES_LOCATION+"/.r_info;omxplayer"+addOptions+subtitleString+" -o "+audioOuputDevice+getLastVolumeString()
				+ "$(sed -n \"$(($(cat $l)+2))p\" $f) \"$(sed -n \"$(($(cat $l)+3))p\" $f)\">/dev/null 2>&1 </dev/null;"
				+ "test $? -ne 0&&exit;echo $(($(cat $l)+4)) >$l;done;echo 1 >$l;test -e " + TEMP_FILES_LOCATION+
				"/.r_loop&&break;done;rm "+ TEMP_FILES_LOCATION + "/.r_info;rm "+ TEMP_FILES_LOCATION + "/.linenum)");
		}
		executeCommand(context, stb.toString(), true);
	}
	
	public static void addToQueue(final String stream, final Context context, final String videoTitle,
			final String source, SubtitleOptions so, boolean autostart, boolean forceAudio){
		String[] streams, videoTitles;
		streams= new String[1];
		videoTitles= new String[1];
		streams[0]=stream;
		videoTitles[0]=videoTitle;
		addToQueue(streams, context, videoTitles, source, so, autostart, forceAudio);
	}
	
	public static void addToQueue(final String stream, final Context context, final String videoTitle,
			final String source, SubtitleOptions so, boolean forceAudio){
		boolean autostart= context.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE).
				getBoolean(Constants.PREF_AUTOSTART_QUEUE, true);
		addToQueue(stream, context, videoTitle, source, so, autostart, forceAudio);
	}
	
	public static void emptyQueue(final Context con){
		executeCommand(con, "test -e "+TEMP_FILES_LOCATION+"/.linenum&&("+ABORT_COMMAND+");rm "+TEMP_FILES_LOCATION+"/.queue", true);
	}
	
	public static void playQueue(final Context con, int lineNum, SubtitleOptions so){
		
		if(!queueHasCast)
			HttpFileStreamer.closeFileStreamer(con);
		String addOptions= con.getSharedPreferences(Constants.PREF_FILE_NAME, 
					Context.MODE_PRIVATE).getString(Constants.PREF_OMXPLAYER_OPTIONS, "");
		if(!addOptions.equals("")){
			addOptions=" " + addOptions +" ";
		}
		
		lineNum=(lineNum*4)-3;
		String killCommand=ABORT_COMMAND + "pkill omxiv&";
		String subtitleString= (so!=null) ? so.getSubtitleString(): "";
		executeCommand(con, killCommand+"f=\""+TEMP_FILES_LOCATION+"/.queue\";l=\""+TEMP_FILES_LOCATION+"/.linenum\";echo "+lineNum+
				" >$l;while true;do while [ $(cat $l) -le $(wc -l<$f) ];do sed -n \"$(cat $l),+1p\" $f>"
				+TEMP_FILES_LOCATION+"/.r_info;omxplayer"+addOptions+subtitleString+" -o "+audioOuputDevice+getLastVolumeString()
				+ "$(sed -n \"$(($(cat $l)+2))p\" $f) \"$(sed -n \"$(($(cat $l)+3))p\" $f)\">/dev/null 2>&1 </dev/null;"
				+ "test $? -ne 0&&exit;echo $(($(cat $l)+4)) >$l;done;echo 1 >$l;test -e " + TEMP_FILES_LOCATION+
				"/.r_loop&&break;done;rm "+ TEMP_FILES_LOCATION + "/.r_info;rm "+ TEMP_FILES_LOCATION + "/.linenum", true);
	}
	
	public static void removeFromQueue(final Context con, int lineNum){
		lineNum=(lineNum*4)-3;
		executeCommand(con, "l=\""+TEMP_FILES_LOCATION+"/.linenum\";" + "sed -i '"
				+ lineNum+",+3d' " + TEMP_FILES_LOCATION +"/.queue&&(test $(cat $l) -ge "+ lineNum+
				"&&echo $(($(cat $l)-4)) >$l&&test $(cat $l) -eq "+(lineNum-4)+"&&("+ABORT_COMMAND+
				"))", true);
	}

	public static void startProgramm(final String stream, final Context context, final String videoTitle,
			final String source, final SubtitleOptions subtitleOptions, final boolean audio) {
		
		if(!source.equals("cast"))
			HttpFileStreamer.closeFileStreamer(context);
		
		if(lastStartProgrammMillis+700>System.currentTimeMillis()){
			return;
		}
		String infoCommand="";
		if (videoTitle != null){
			String title=videoTitle.replace("\"", "\\\"").replace("$", "\\$");
			infoCommand="echo -e \"" + source + "\n" + title + "\">" + TEMP_FILES_LOCATION + "/.r_info&";
		}
		String blank= (audio) ? "" : "-b";
		String killCommand=ABORT_COMMAND + "pkill omxiv&";
		if (source.equals("local") || source.equals("cast")){
			for(String audioExtension : Constants.COMMON_AUDIO_FILE_EXTENSIONS){
				if (videoTitle.endsWith(audioExtension)){
					blank="";
					killCommand=ABORT_COMMAND;
					break;
				}
			}
		}
		if(source.startsWith("?v=")&& context.getSharedPreferences(Constants.PREF_FILE_NAME, 
				Context.MODE_PRIVATE).getInt(Constants.PREF_YOUTUBE_QUALITY, 1)>1){
			blank="";
		}
		String addOptions= context.getSharedPreferences(Constants.PREF_FILE_NAME, 
					Context.MODE_PRIVATE).getString(Constants.PREF_OMXPLAYER_OPTIONS, "");
		if(!addOptions.equals("")){
			addOptions=" " + addOptions +" ";
		}
		
		String subtitleString="";
		if (subtitleOptions != null){
			subtitleString=subtitleOptions.getSubtitleString();
		}
		String command;
		if (stream.startsWith("rtp://") || stream.startsWith("udp://")){
			String strLive = (liveOption<2) ? "--live ": "";
			command=killCommand + infoCommand + "omxplayer "+addOptions+strLive+"-b -o " + audioOuputDevice + getLastVolumeString()
					+ stream.replace("@", "") + ">/dev/null 2>&1 </dev/null&&rm " + TEMP_FILES_LOCATION + "/.r_info";
		}else{
			String videoFile;
			String strLive="";
			if(stream.startsWith("?youtube-dl")){
				killCommand+="pkill youtube-dl;";
				infoCommand="a=$(youtube-dl -eqg --no-check-certificate \""+stream.substring(stream.indexOf(" ")+1)+"\")&&";
				infoCommand += "b=$(echo \"$a\"|head -n1)&&c=$(echo \"$a\"|tail -n1)&&";
				infoCommand +="echo -e \""+stream+"\n$(echo \"$b\")\">" + TEMP_FILES_LOCATION + "/.r_info&&";
				videoFile= "\"$c\"";
			}else{
				videoFile="\"" + stream + "\"";
				if(liveOption==2&&!source.startsWith("?v=")&& !source.startsWith("local")){
					for(String streamType:Constants.SUPPORTED_STREAMS){
						if(stream.startsWith(streamType)){
							strLive="--live ";
							break;
						}
					}
				}
				if(source.equals("local"))
					videoFile = videoFile.replace("$", "\\$");
			}
			
			command=killCommand + infoCommand + "while true;do omxplayer "+addOptions+strLive + blank + " -o " + audioOuputDevice
					+ subtitleString + getLastVolumeString() + videoFile +">/dev/null 2>&1 </dev/null;"
					+ "test $? -ne 0&&break;test -e " + TEMP_FILES_LOCATION
					+ "/.r_loop&&rm " + TEMP_FILES_LOCATION + "/.r_info&&break;done";
		}
		VideoControlExecutor.resetActiveAudioStream();
		lastStartProgrammMillis=System.currentTimeMillis();
		executeCommand(context, command, true);
	}

	public static void showPicture(final Context con, final String path, final String... pictures) {
		boolean isVideoFilePlaying=true;
		if (videoInfo.contains("\n") && (videoInfo.startsWith("local") || videoInfo.startsWith("cast"))){
			String fileName=videoInfo.split("\n")[1];
			for(String audioExtension : Constants.COMMON_AUDIO_FILE_EXTENSIONS){
				if (fileName.endsWith(audioExtension)){
					isVideoFilePlaying=false;
					break;
				}
			}
		}
		String killCommand=(isVideoFilePlaying) ? ABORT_COMMAND : "";
		killCommand+="pkill omxiv;";
		int slideShowTimeout = con.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE).
				getInt(Constants.PREF_SLIDESHOW_DELAY, 2) + 1;
		StringBuilder pictureStB=new StringBuilder("");
		for(String pic : pictures){
			pictureStB.append("\"");
			pictureStB.append(pic);
			pictureStB.append("\" ");
		}
		String addOptions= con.getSharedPreferences(Constants.PREF_FILE_NAME, 
				Context.MODE_PRIVATE).getString(Constants.PREF_OMXIV_OPTIONS, "");
		if(!addOptions.equals("")){
			addOptions=" " + addOptions +" ";
		}
		String slideShowDelay= (pictures.length > 1) ? " -t "+slideShowTimeout: "";
		if(path.equals(""))
			executeCommand(con, killCommand + "omxiv -bk"+slideShowDelay+addOptions+
						" "+ pictureStB.toString(), true);
		else	
			executeCommand(con, killCommand + "(cd \""+path+"\"&&omxiv -bk"+slideShowDelay+addOptions+" "+ 
					pictureStB.toString() +")", true);
		
	}

	public static void changeSubtitles(final int subtitleIndex, final Context context) {
		String command=(subtitleIndex != -1) ? getDbusExport()+CMD_SHOW_SUBTITLES_STREAM_PRE + subtitleIndex
				+ CMD_SHOW_SUBTITLES_STREAM_POST : getDbus()+CMD_HIDE_SUBTITLES;
		executeCommand(context, command, true);
	}

	public static void changeLooping(final Context con) {
		String command=(loopStreams) ? CMD_REPEAT : CMD_DO_NOT_REPEAT;
		executeCommand(con, command, true);
	}

	public static void setPosition(final int seconds, final Context con, final boolean relative) {
		String command=(relative) ? CMD_SEARCH_RELATIVE_PRE : CMD_SEARCH_ABSOLUTE_PRE;
		command+=seconds + CMD_SEARCH_POST;
		executeCommand(con, getDbus()+command, !relative);
	}

	public static void stopCurrentProgramm(final Context context) {
		executeCommand(context, CMD_STOP, true);
	}

	public static void changeAudioIndex(final int audioIndex, final Context context) {
		executeCommand(context, getDbus()+CMD_CHANGE_AUDIO_STREAM_PRE + audioIndex + CMD_CHANGE_AUDIO_STREAM_POST,
				true);
	}

	public static void playPauseVideo(final Context con) {
		executeCommand(con, getDbus()+CMD_PLAY_PAUSE, false);
	}

	public static void changeVolume(final Context con, final boolean louder) {
		if(lastVolumeControlMillis+100<=System.currentTimeMillis()){
//			String command= "v=$(($(grep -o + " + TEMP_FILES_LOCATION
//			+ "/.r_input|wc -l)-$(grep -o - " + TEMP_FILES_LOCATION + "/.r_input|wc -l)));test $v -gt 7&&exit;test $v -lt -17&&exit;";
			String command=(louder) ? CMD_LOUDER : CMD_QUIETER;
			lastVolumeControlMillis=System.currentTimeMillis();
			executeCommand(con, getDbus() + command, false);
		}
	}
	
	public static void recordStream(final Context con, final int minutes, final String stream, final String title){
		executeCommand(con, "pkill ffmpeg;LD_LIBRARY_PATH=/usr/lib/omxplayer /usr/lib/omxplayer/ffmpeg -i "+stream+" -acodec copy -vcodec copy -t "+ (minutes*60)+" ~/"+title+".mp4 >/dev/null 2>&1 </dev/null", true);
	}

	private static void initConnection() throws IOException {
		Session sess=null;
		try{
			String loopCommand=(loopStreams) ? "rm " + TEMP_FILES_LOCATION + "/.r_loop" : "echo> "
					+ TEMP_FILES_LOCATION + "/.r_loop";
			String infoFileCommand="echo -n >" + TEMP_FILES_LOCATION + "/.r_input";
			sess=conn.openSession();
			//Look for needed files and read the omxplayer script for dbus file names.
			sess.execCommand("ls -a " + TEMP_FILES_LOCATION+ ";grep \"/tmp/omxplayerdbus\\\"\"</usr/bin/omxplayer");
			BufferedReader br=new BufferedReader(new InputStreamReader(sess.getStdout()));
			String line;
			String command=null;
			boolean raspiLoopOption=true, inputFileMissing=true;
			dbusFilesAssociatedWithUser=true;
			while ((line=br.readLine()) != null){
				if (line.contains(".r_input")){
					inputFileMissing=false;
				}else if (line.contains(".r_loop")) {
					raspiLoopOption = false;
				}else if(line.contains("/tmp/omxplayerdbus")){
					dbusFilesAssociatedWithUser=false;
					break;
				}
			}
			sess.getStdout().close();
			br.close();
			sess.close();
			if (raspiLoopOption != loopStreams)
				command= loopCommand;
			if (inputFileMissing)
				command=(command == null) ? infoFileCommand : command + "&&" + infoFileCommand;
			if (command != null){
				Log.d(SshConnection.class.getSimpleName(), "Creating missing files " + command);
				sess=conn.openSession();
				sess.execCommand(command + " 2>/dev/null");
				sess.close();
			}
		}catch (IOException ioe){
			sess.close();
			throw ioe;
		}
	}
	
	private static String getDbus(){
		if(dbusFilesAssociatedWithUser && sshUser!=null){
			return "x=\"cat /tmp/omxplayerdbus."+sshUser+"\";DBUS_SESSION_BUS_ADDRESS=$(${x}) DBUS_SESSION_BUS_PID=$(${x}.pid) ";
		}else{
			return "x=\"cat /tmp/omxplayerdbus\";DBUS_SESSION_BUS_ADDRESS=$(${x}) DBUS_SESSION_BUS_PID=$(${x}.pid) ";
		}
	}
	
	private static String getDbusExport(){
		if(dbusFilesAssociatedWithUser && sshHostname!=null){
			return "z=\"export DBUS_SESSION_BUS_\";x=\"cat /tmp/omxplayerdbus."+sshUser+"\";(${z}ADDRESS=$(${x}) ${z}PID=$(${x}.pid);";
		}else{
			return "z=\"export DBUS_SESSION_BUS_\";x=\"cat /tmp/omxplayerdbus\";(${z}ADDRESS=$(${x}) ${z}PID=$(${x}.pid);";
		}
	}
	
	private static String getLastVolumeString(){
		if(volumeOffset!=0){
			return " --vol $((300*($(grep -o + " + TEMP_FILES_LOCATION
						+ "/.r_input|wc -l)-$(grep -o - " + TEMP_FILES_LOCATION + "/.r_input|wc -l))+"+volumeOffset+")) ";
		}else{
			return  " --vol $((300*($(grep -o + " + TEMP_FILES_LOCATION
					+ "/.r_input|wc -l)-$(grep -o - " + TEMP_FILES_LOCATION + "/.r_input|wc -l)))) ";
		}
	}

	public static boolean hasData() {
		return !(sshHostname == null || sshUser == null || sshPort == -1);
	}

	public static void setCredentials(String hostname, int port, String user, String password, String keyFile) {
		sshHostname=hostname;
		sshPort=port;
		sshUser=user;
		sshPassword=password;
		started=false;
		sshKeyFile=keyFile;
	}
	
	public static void safelyKill(){
		started=false;
		isConnecting=false;
		sshUser=null;
		sshPassword=null;
		if (conn != null){
			new Thread(){
				public void run(){
					if(conn!=null){
						conn.close();
						conn=null;	
					}
					android.os.Process.sendSignal(android.os.Process.myPid(), 15);
				}
				
			}.start();
		}
	}
	
	public static void interceptFinalize(){
		SshConnection.requeuestFinalize=false;
	}

	public static void finalizeConnection(final boolean withLock) {
		if (!updateQueueRunning){
			if (!withLock){
				started=false;
				isConnecting=false;
				sshUser=null;
				sshPassword=null;
				requeuestFinalize=false;
				if (conn != null){
					new Thread() {
						public void run() {
							Log.d(SshConnection.class.getSimpleName(), "Closing connection");
							if (conn != null){
								conn.close();
								conn=null;
							}
						}

					}.start();
				}
			}else{
				new Thread() {
					public void run() {
						try{
							lock.lock();
							isConnecting=false;
							sshUser=null;
							sshPassword=null;
							requeuestFinalize=false;
							started=false;
							Log.d(SshConnection.class.getSimpleName(), "Closing connection");
							if (conn != null){
								conn.close();
								conn=null;
							}
						}catch (Exception e){
							e.printStackTrace();
						}finally{
							lock.unlock();
						}
					}
				}.start();
			}
		}else{
			requeuestFinalize=true;
		}
	}
}
