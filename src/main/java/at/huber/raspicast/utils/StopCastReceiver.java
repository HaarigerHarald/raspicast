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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import at.huber.raspicast.Constants;
import at.huber.raspicast.SshConnection;

public class StopCastReceiver extends BroadcastReceiver { 

	@Override
	public void onReceive(final Context con, Intent intent) {
		HttpFileStreamer.closeFileStreamer(con);
		if(!(SshConnection.videoInfo.startsWith("cast") || SshConnection.videoInfo.startsWith(""))){
			return;
		}
		if(!SshConnection.started){
			final String hostname=con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE)
					.getString(Constants.PREF_HOSTNAME, null);
			final int port=con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE)
					.getInt(Constants.PREF_PORT, 22);
			final String user=con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE)
					.getString(Constants.PREF_USER, null);
			SshConnection.setCredentials(
					hostname,
					port,
					user,
					PasswordEncrypter.decrypt(con.getSharedPreferences(Constants.PREF_FILE_NAME,
							Activity.MODE_PRIVATE).getString(Constants.PREF_PASSWORD, null)),
					con.getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).getString(
							Constants.PREF_KEYFILE_PATH, null));
			
			
			SshConnection.openSshConnection(con, null, true, false);
			new Handler().postDelayed(new Runnable() {
				
				@Override
				public void run() {
					SshConnection.stopCurrentProgramm(con);
				}
			}, 100);
			new Handler().postDelayed(new Runnable() {
				
				@Override
				public void run() {
					SshConnection.finalizeConnection(true);	
				}
			}, 200);
		}else{
			SshConnection.stopCurrentProgramm(con);
		}
	}
}
