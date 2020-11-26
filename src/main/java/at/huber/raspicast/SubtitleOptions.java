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
import android.content.SharedPreferences;
import android.util.Log;

public class SubtitleOptions {
	
	String extSubtitlePath;
	private boolean ghostBoxes;
	private boolean left;
	private int subtitleSize; // in 1/1000 of screen size
	
	public SubtitleOptions(final Context con){
		SharedPreferences sp = con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE);
		ghostBoxes = sp.getBoolean(Constants.PREF_SUBTITLE_BOXES, true);
		left = sp.getBoolean(Constants.PREF_SUBTITLES_LEFT, true);
		subtitleSize = sp.getInt(Constants.PREF_SUBTITLE_SIZE, 55);
	}
	
	public String getSubtitleString(){
			String subtitleString="";
			subtitleString=(!ghostBoxes) ? subtitleString + " --no-ghost-box"
					: subtitleString;
			subtitleString=(!left) ? subtitleString + " --align center" : subtitleString;
			subtitleString=(subtitleSize != 55) ? subtitleString + " --font-size "
					+ subtitleSize : subtitleString;
			subtitleString=(extSubtitlePath != null) ? subtitleString + " --subtitles \""
					+ extSubtitlePath+"\"": subtitleString;
			Log.d("Subtitle String", subtitleString);
			return subtitleString;
	}

}
