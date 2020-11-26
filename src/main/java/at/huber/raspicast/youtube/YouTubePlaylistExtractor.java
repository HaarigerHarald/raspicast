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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import at.huber.raspicast.Constants;

public class YouTubePlaylistExtractor extends AsyncTask<String, String, String[]> {
	
	private static final Pattern playlistIdPattern = 
			Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/playlist\\?list=(.+?)( |\\z)");
	
	private static final Pattern playlistVideoPattern = 
			Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)&list=(.+?)( |&|\\z)");
	
	private static final Pattern videoIdPattern = Pattern.compile("data-video-id=\"(.+?)\"");
	private final static String USER_AGENT="Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36";
	
	private Context context;
	private int maxPlaylistSize;
	
	public YouTubePlaylistExtractor(Context context){
		this.context=context;
		maxPlaylistSize=context.getSharedPreferences(Constants.PREF_FILE_NAME, 
				Activity.MODE_PRIVATE).getInt(Constants.PREF_MAX_PLAYLIST_SIZE, 25);
	}
	
	@Override
	protected void onPostExecute(String[] videoIds) {
		if(videoIds!=null){
			Log.d("Videonum:", ""+videoIds.length);
			boolean first=true;
			for(String videoId: videoIds){
				new RaspiYouTubeExtractor(context, true, first).execute(videoId);
				first=false;
			}
		}
	}

	@Override
	protected String[] doInBackground(String... params) {
		if(params.length==0)
			return null;
		Matcher mat = playlistIdPattern.matcher(params[0]);
		if(mat.find()){
			String playlistUrl=mat.group(0);
			if(playlistUrl.contains("://m.youtube")){
				playlistUrl.replace("://m.youtube", "://www.youtube");
			}
			if(playlistUrl.startsWith("http://")){
				playlistUrl=playlistUrl.replace("http://", "https://");
			}
			try{
				return getYoutubeIdsfromPlaylist(playlistUrl);
			}catch (IOException e){
				e.printStackTrace();
			}
		}else{
			mat = playlistVideoPattern.matcher(params[0]);
			if(mat.find()){
				String playlistUrl=mat.group(0);
				if(playlistUrl.contains("://m.youtube")){
					playlistUrl.replace("://m.youtube", "://www.youtube");
				}
				if(playlistUrl.startsWith("http://")){
					playlistUrl=playlistUrl.replace("http://", "https://");
				}
				try{
					return getYoutubeIdsfromPlaylist(playlistUrl);
				}catch (IOException e){
					e.printStackTrace();
				}
			}
			
		}
		return null;
	}
	
	private String[] getYoutubeIdsfromPlaylist(String playListUrl) throws IOException{
		Log.d("playlistUrl", playListUrl);
		ArrayList<String> youtubeVideoIds=new ArrayList<>();
		URL getUrl = new URL(playListUrl);
		HttpURLConnection urlConnection = (HttpURLConnection) getUrl.openConnection();
		urlConnection.setRequestProperty("User-Agent", USER_AGENT);
		BufferedReader reader=null;
		InputStream in=null;
		Matcher mat;
		try {
			in = new BufferedInputStream(urlConnection.getInputStream());
			reader = new BufferedReader(new InputStreamReader(in));
			String line;
			int i=0;
			while((line=reader.readLine())!=null){
				mat=videoIdPattern.matcher(line);
				if(mat.find()){
					youtubeVideoIds.add(mat.group(1));
					i++;
					if(i==maxPlaylistSize)
						break;
				}
			}
		 }finally {
			 if(in!=null&&reader!=null){
		    	in.close();
		    	reader.close();
			 }
		    	urlConnection.disconnect();
		 }
		return youtubeVideoIds.toArray(new String[youtubeVideoIds.size()]);
	}

}
