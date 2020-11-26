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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.huber.raspicast.Constants;
import at.huber.raspicast.R;

public class RaspiUtils {

	public static final int PATH_INDEX = 0;
	public static final int TITLE_INDEX = 1;
	public static final int ARTIST_INDEX = 2;

	private static final Pattern patArrayItem = Pattern.compile("[0-9]:.*?:.*?:.*?:([a-z]*? | )");
	
	public static String[] readStatusFromArrayString(String arrayString){
		Matcher mat= patArrayItem.matcher(arrayString);
		ArrayList<String> audioStreams=new ArrayList<>();
		while(mat.find()){
			audioStreams.add(mat.group());
		}
		if(audioStreams.isEmpty()){
			return null;
		}
		return audioStreams.toArray(new String[audioStreams.size()]);
	}
	
	public static void brandAlertDialog(Dialog dial, Context con){
		TypedValue typedValue=new TypedValue();
		con.getTheme().resolveAttribute(R.attr.highlight_color, typedValue, true);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			Button btn=((AlertDialog)dial).getButton(AlertDialog.BUTTON_POSITIVE);
			if(btn!=null)
				btn.setTextColor(typedValue.data);
			btn=((AlertDialog)dial).getButton(AlertDialog.BUTTON_NEUTRAL);
			if(btn!=null)
				btn.setTextColor(typedValue.data);
			btn=((AlertDialog)dial).getButton(AlertDialog.BUTTON_NEGATIVE);
			if(btn!=null)
				btn.setTextColor(typedValue.data);
		}
		try{
			int dividerId=dial.getContext().getResources()
					.getIdentifier("android:id/titleDivider", null, null);
			View divider=dial.findViewById(dividerId);
			divider.setBackgroundColor(typedValue.data);
			int textViewId=dial.getContext().getResources()
					.getIdentifier("android:id/alertTitle", null, null);
			TextView tv= dial.findViewById(textViewId);
			tv.setTextColor(typedValue.data);
		}catch (NullPointerException npe){
			// Ignore something has changed
		}
	}
	
	public static void brandDialog(Dialog dial, Context con){
		TypedValue typedValue = new TypedValue();
		con.getTheme().resolveAttribute(R.attr.highlight_color, typedValue, true);
		try{
			int dividerId=dial.getContext().getResources()
					.getIdentifier("android:id/titleDivider", null, null);
			View divider=dial.findViewById(dividerId);
			divider.setBackgroundColor(typedValue.data);
			int textViewId=dial.getContext().getResources().getIdentifier("android:id/title", null, null);
			TextView tv = dial.findViewById(textViewId);
			tv.setTextColor(typedValue.data);
		}catch(NullPointerException npe){
			//Ignore something has changed
		}
	}


	public static String[] getRealPathFromURI(Context context, Uri contentUri, boolean isAudio) {
		if (contentUri == null)
			return null;

		try {
			String path = getPath(context, contentUri);
			if (path != null) {
				String[] pathTitle = new String[3];
				pathTitle[PATH_INDEX] = path;
				MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
				File mFile = new File(path);
				Uri uri = Uri.fromFile(mFile);
				try {
					mediaMetadataRetriever.setDataSource(context, uri);
					pathTitle[TITLE_INDEX] = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
					if (isAudio)
						pathTitle[ARTIST_INDEX] = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
				} catch (java.lang.RuntimeException ignored) {
				}

				if (pathTitle[TITLE_INDEX] == null)
					pathTitle[TITLE_INDEX] = mFile.getName().substring(0, mFile.getName().lastIndexOf('.'));
				if (pathTitle[ARTIST_INDEX] == null)
					pathTitle[ARTIST_INDEX] = Constants.UNKNOWN_DATA;
				return pathTitle;
			}
		} catch (Exception ignored)
		{ /* Da fuck? */ }

		return null;
	}
	
	public static int getActiveStream(String[] streams){
		int activeStream=-1;
		if(streams!=null){
			for(int i=0; i < streams.length; i++){
				if (streams[i].contains("active")){
					activeStream=i;
					break;
				}
			}
		}
		return activeStream;
	}
	
	public static int[] convertIntegers(List<Integer> integers)
	{
	    int[] ret = new int[integers.size()];
	    for (int i=0; i < ret.length; i++)
	    {
	        ret[i] = integers.get(i);
	    }
	    return ret;
	}
	
	public static boolean isAClick(float startX, float endX, float startY, float endY) {
	    float differenceX = Math.abs(startX - endX);
	    float differenceY = Math.abs(startY - endY);
	    int diff = PixelTransformer.getPixelsFromDp(10);
		return !(differenceX > diff || differenceY > diff);
	}
	
	
	public static void appendLogFile(String text) {
		String extStorage=Environment.getExternalStorageDirectory().getAbsolutePath();
		File logFile=new File(extStorage+"/raspicast.log");
		if (!logFile.exists()){
			try{
				logFile.createNewFile();
			}catch (IOException e){
				e.printStackTrace();
			}
		}
		try{
			BufferedWriter buf=new BufferedWriter(new FileWriter(logFile, true));
			buf.append(text);
			buf.newLine();
			buf.close();
		}catch (IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	@SuppressLint("NewApi")
	private static String getPath(final Context context, final Uri uri) {

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				} else {
					String filePath = null;
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						File external[] = context.getExternalMediaDirs();
						if (external.length > 1) {
							filePath = external[1].getAbsolutePath();
							filePath =  filePath.substring(0, filePath.indexOf("Android")) + split[1];
						}
					} else {
						filePath = "/storage/" + type + "/" + split[1];
					}
					return filePath;
				}
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {
			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	private static String getDataColumn(Context context, Uri uri, String selection,
									   String[] selectionArgs) {

		Cursor cursor = null;
		final String column = MediaStore.Files.FileColumns.DATA;
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		}
		finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}


	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	private static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	private static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	private static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}
}
