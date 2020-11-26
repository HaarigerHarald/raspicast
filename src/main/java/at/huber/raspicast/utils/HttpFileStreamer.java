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
package at.huber.raspicast.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;

import at.huber.raspicast.Constants;
import at.huber.raspicast.OverflowMenuFragActivity;
import at.huber.raspicast.SshConnection;
import at.huber.raspicast.SubtitleOptions;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class HttpFileStreamer{
	
	private static HttpFileServer httpFilesServer;
	private static boolean softClose = false;
	private static long lastStart;
	private static int port = 8080;
	
	public synchronized static boolean streamFile(String path, Context con, String videoTitle, final boolean audio, final boolean image, final boolean queue){
		String paths[] = new String[1];
		paths[0]= path;
		String titles[] = new String[1];
		titles[0] = videoTitle;
		
		return streamFile(paths, con, titles, audio, image, queue);
	}
	
	public synchronized static boolean streamFile(String paths[], Context con, String videoTitles[], final boolean audio, final boolean image, final boolean queue){
		try {

			port = con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
					getInt(Constants.PREF_STREAM_PORT, 8080);

			boolean autostart = true;
			if(queue){
				autostart= con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
					getBoolean(Constants.PREF_AUTOSTART_QUEUE, true);
			}

			if(autostart){
				startFileStreamer();
				if(!image){
					softClose=false;
					Intent startIntent = new Intent(con, FileStreamService.class);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						con.startForegroundService(startIntent);
					} else {
						con.startService(startIntent);
					}
				}else{
					Intent startIntent = new Intent(con, FileStreamService.class);
					con.stopService(startIntent);
					softClose=true;
				}
				lastStart = System.currentTimeMillis();
			}

			SubtitleOptions so = new SubtitleOptions(con);
			// Trying to prevent others from requesting any file
			for(int i=0; i<paths.length; i++){
				paths[i]=PasswordEncrypter.encrypt(paths[i]);
				paths[i]=URLEncoder.encode(paths[i], "UTF-8");
				paths[i]="http://$(echo $SSH_CONNECTION|awk '{print $1}'):"+port+"?path="+paths[i];
				Log.d("Path", paths[i]);
			}

			if(!image){
				if(!queue)
					SshConnection.startProgramm(paths[0], con, videoTitles[0] , "cast", so, audio);
				else{
					SshConnection.addToQueue(paths, con, videoTitles, "cast", so, autostart, audio);
				}
			}else
				SshConnection.showPicture(con, "", paths[0]);


			Intent intent = new Intent(OverflowMenuFragActivity.CALL_SYNC_ACTION);
			if (image){
				intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_DELAY, 400);
				intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_RETRIES, 0);
			}else{
				intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_DELAY, 1200);
				intent.putExtra(OverflowMenuFragActivity.CALL_SYNC_RETRIES, 2);
			}
			con.sendBroadcast(intent);

			if((SshConnection.queueIndex>0 || queue) && !image){
				SshConnection.readQueue(con, 300, true);
			}

			return true;
        } catch(IOException ioe) {
        	ioe.printStackTrace();
        }
		return false;
	}
	
	public synchronized static boolean startFileStreamerOutside(Context con){
		if(httpFilesServer == null)
			httpFilesServer = new HttpFileServer();

		if(!httpFilesServer.isAlive()){
			try{
				httpFilesServer.start();
			}catch (IOException e){
				return false;
			}
		}
		softClose = false;

		Intent startIntent = new Intent(con, FileStreamService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			con.startForegroundService(startIntent);
		} else {
			con.startService(startIntent);
		}

		lastStart = System.currentTimeMillis()+1000;
		return true;
	}
	
	private static void startFileStreamer() throws IOException{
		if(httpFilesServer==null)
			httpFilesServer=new HttpFileServer();
		
		if(!httpFilesServer.isAlive()){
			httpFilesServer.start();
		}
	}
	
	public synchronized static void closeFileStreamer(Context con, boolean noKillIm){
		if(noKillIm && System.currentTimeMillis() - lastStart < 1500)
			return;
		if(httpFilesServer!=null){
			httpFilesServer.stop();
			httpFilesServer.closeStream();
			httpFilesServer = null;
		}
		Intent startIntent = new Intent(con, FileStreamService.class);
		con.stopService(startIntent);
	}
	
	public synchronized static void closeFileStreamer(Context con){
		closeFileStreamer(con, false);
	}
	
	public synchronized static void softClose(){
		if(httpFilesServer!=null && softClose){
			httpFilesServer.stop();
			httpFilesServer.closeStream();
			httpFilesServer = null;
		}

	}
	
	private static class HttpFileServer extends NanoHTTPD{
		
		private String path;
		private File file;
		private long fileLength;
		BufferedInputStream fileInputStream;
		
		HttpFileServer(){
			super(port);
			
		}
		
		private void closeStream(){
			if (fileInputStream != null){
				try{
					fileInputStream.close();
				}catch (IOException e1){
					e1.printStackTrace();
				}
			}
		}
		
		@Override
		public Response serve(IHTTPSession session) {
			path = session.getParms().get("path");
			path=PasswordEncrypter.decrypt(path);
			file = new File(path);
			if(!file.exists()){
				 return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "");
			}
			fileLength=file.length();		
		    String mimeType = "video";
		    closeStream();
		    	
			String range;
			range = session.getHeaders().get("range");
			String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());
			String ifRange = session.getHeaders().get("if-range");
			boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));
			String ifNoneMatch = session.getHeaders().get("if-none-match");
            boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null && (ifNoneMatch.equals("*") || ifNoneMatch.equals(etag));
			try{
				if (range != null && headerIfRangeMissingOrMatching){
					if(headerIfNoneMatchPresentAndMatching){
						Response res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mimeType, "");
	                    res.addHeader("ETag", etag);
	                    return res;
					}else{
						return getPartialResponse(mimeType, range, etag);
					}
				}else {
					return getFullResponse(mimeType, etag);
				}
			}catch (IOException e){
				//e.printStackTrace();
			}
		    return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "");
		}

		private Response getFullResponse(String mimeType, String etag) throws IOException {
			fileInputStream = new BufferedInputStream(new FileInputStream(path));
			Response response= newFixedLengthResponse(Status.OK, mimeType, fileInputStream, fileLength);
		    response.addHeader("Content-Length", fileLength + "");
	        response.addHeader("Accept-Ranges", "bytes");
	        response.addHeader("ETag", etag);
	        return response;
		}

		private Response getPartialResponse(String mimeType, String rangeHeader, String etag) throws IOException {
		    String rangeValue = rangeHeader.trim().substring("bytes=".length());
		    long start, end;
		    if (rangeValue.startsWith("-")) {
		        end = fileLength - 1;
		        start = fileLength - 1
		                - Long.parseLong(rangeValue.substring("-".length()));
		    } else {
		        String[] range = rangeValue.split("-");
		        start = Long.parseLong(range[0]);
		        end = range.length > 1 ? Long.parseLong(range[1])
		                : fileLength - 1;
		    }
		    if (end > fileLength - 1) {
		        end = fileLength - 1;
		    }
		    if (start <= end) {
		        long contentLength = end - start + 1;		
		        fileInputStream = new BufferedInputStream(new FileInputStream(path));
			    fileInputStream.skip(start);
			    Response response = newFixedLengthResponse(Status.PARTIAL_CONTENT, mimeType, fileInputStream, contentLength);
		        response.addHeader("Content-Length", contentLength + "");
		        response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
		        response.addHeader("Accept-Ranges", "bytes");
		        response.addHeader("ETag", etag);
		        return response;
		    } else {
		    	Response res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
		    	res.addHeader("Content-Range", "bytes */" + fileLength);
                res.addHeader("ETag", etag);
		        return res;
		    }
		}
	}
}

