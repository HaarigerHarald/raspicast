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
package at.huber.raspicast.youtube;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.SparseArray;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.huber.raspicast.Constants;
import at.huber.raspicast.OverflowMenuFragActivity;
import at.huber.raspicast.SshConnection;
import at.huber.raspicast.SubtitleOptions;
import at.huber.raspicast.utils.RaspiUtils;
import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

public class RaspiYouTubeExtractor extends YouTubeExtractor {
	
	private final static boolean FILE_LOGGING=false;
	
	private static final Pattern patYouTubePageLink=Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)");
	private static final Pattern patYouTubeShortLink=Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)");
	
	private Context con;
	
	private String youtubeSource;
	private boolean addToQueue=false;
	private boolean first=false;
	private int[] lineNums;
	private int youtubeQuality=0;
	
	public RaspiYouTubeExtractor(Context con, int[] lineNums) {
		this(con, false);
		this.lineNums=lineNums;
	}
	
	public RaspiYouTubeExtractor(Context con, boolean addToQueue) {
		this(con, addToQueue, true);
	}

	RaspiYouTubeExtractor(Context con, boolean addToQueue, boolean first) {
		super(con);
		this.con = con;
		youtubeQuality=con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).getInt(
				Constants.PREF_YOUTUBE_QUALITY, 0);
		setDefaultHttpProtocol(con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).getBoolean(
				Constants.PREF_YOUTUBE_HTTP, false));
		setIncludeWebM(false);
		if(youtubeQuality>=2)
			setParseDashManifest(true);
		this.first=first;
		this.addToQueue=addToQueue;
	}

	@Override
	public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
		if (ytFiles != null) {
			String streamingUrl;
			boolean audio = false;
			if(vMeta.isLiveStream()) {
				if(youtubeQuality == 0 && ytFiles.get(95) != null){
					streamingUrl = ytFiles.get(95).getUrl();
				}else if(ytFiles.get(93) != null){
					streamingUrl = ytFiles.get(93).getUrl();
				}else{
					streamingUrl = ytFiles.get(ytFiles.keyAt(0)).getUrl();
				}
				youtubeSource = "#" + youtubeSource;
				addToQueue = false;
			}else {
				if (youtubeQuality == 2 && ytFiles.get(140) != null) {
					streamingUrl = ytFiles.get(140).getUrl();
					audio = true;
				} else if (youtubeQuality == 0 && ytFiles.get(22) != null) {
					streamingUrl = ytFiles.get(22).getUrl();
				} else if (ytFiles.get(18) != null) {
					streamingUrl = ytFiles.get(18).getUrl();
				} else if (ytFiles.get(36) != null) {
					streamingUrl = ytFiles.get(36).getUrl();
				} else {
					streamingUrl = ytFiles.get(ytFiles.keyAt(0)).getUrl();
				}
			}
			SubtitleOptions so=null;
			if (con != null)
				so=new SubtitleOptions(con);
			if (!addToQueue){
				SshConnection.startProgramm(streamingUrl, con, vMeta.getTitle(), youtubeSource, so, audio);
			}else{
				SshConnection.addToQueue(streamingUrl, con, vMeta.getTitle(), youtubeSource, so, first, audio);
			}
			SshConnection.readQueue(con, 500, true);

			Intent intent=new Intent(OverflowMenuFragActivity.CALL_SYNC_ACTION);
			intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_DELAY, 1500);
			intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_RETRIES, 3);
			con.sendBroadcast(intent);
		}
	}
	
	@Override
	public SparseArray<YtFile> doInBackground(String... params) {
		String youtubeID= null;
		SparseArray<YtFile> videos= null;
		long startTime=System.currentTimeMillis();
		String urls[]=new String[params.length];
		extract: for(int i=0; i<params.length;i++){
			if (i%5 == 0 && i > 0){
				SshConnection.updateYtUrls(con, urls, lineNums, false, i-5);
			}
			for(int x=0; x<i;x++){
				if(params[i].equals(params[x])){
					urls[i]=urls[x];
					continue extract;
				}
			}
			String ytUrl=params[i];
			Matcher mat=patYouTubePageLink.matcher(ytUrl);
			if(ytUrl.startsWith("?v=")){
				ytUrl=ytUrl.substring(3);
				if(ytUrl.endsWith(" cip")){
					ytUrl=ytUrl.substring(0,ytUrl.indexOf(" "));
				}
			}
			if (mat.find()){
				youtubeID=mat.group(3);
			}else{
				mat=patYouTubeShortLink.matcher(ytUrl);
				if(mat.find()){
					youtubeID=mat.group(3);
				}else if(ytUrl.matches("\\p{Graph}+?")){
					youtubeID=ytUrl;
				}
			}
			
			if(youtubeID==null){
				Log.d(getClass().getSimpleName(), "Youtubelink:"+ytUrl);
				return null;
			}
			youtubeSource="?v="+youtubeID;
			try {
				if(FILE_LOGGING)
					RaspiUtils.appendLogFile("YoutubeID:" + youtubeID);
				videos = super.doInBackground(youtubeID);
				if (videos != null) {
					YtFile video;
					if(youtubeQuality == 2 && videos.get(140) != null) {
						video=videos.get(140);
					}else if(youtubeQuality == 0 && videos.get(22) != null){
						video=videos.get(22);
					}else if(videos.get(18) != null){
						video=videos.get(18);
					}else if(videos.get(36) != null){
						video=videos.get(36);
					}else{
						video=videos.get(videos.keyAt(0));
					}

					urls[i]=video.getUrl();
				}else{
					return null;
				}
			} catch (Exception e) {
				if(FILE_LOGGING)
					RaspiUtils.appendLogFile("Exception Class:" + e.getClass().getName());
				if(e.getMessage()!=null){
					Log.d(getClass().getSimpleName(), e.getMessage());
					if(FILE_LOGGING)
						RaspiUtils.appendLogFile("Exception message:" + e.getMessage());
				}
				return null;
			}
		}
		Log.d(getClass().getSimpleName(), "Duration: "+(System.currentTimeMillis()-startTime)+"ms");
		if(lineNums!=null){
			if(urls.length%5==0){
				SshConnection.updateYtUrls(con, urls, lineNums, true, urls.length-5);
			}else{
				SshConnection.updateYtUrls(con, urls, lineNums, true, urls.length-(urls.length%5));
			}
			return null;
		}else{
			return videos;
		}
	}	

}
