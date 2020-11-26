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
package at.huber.raspicast.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import at.huber.raspicast.R;
import at.huber.raspicast.Constants;
import at.huber.raspicast.SshConnection;
import at.huber.raspicast.utils.RaspiUtils;

public class CertDialog extends DialogFragment{
	
	private String fingerprint;
	
	public CertDialog(){
		
	}

	@SuppressLint("ValidFragment")
	public CertDialog(String fingerprint){
		this.fingerprint=fingerprint;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState){
		outState.putString("fingerprint", fingerprint);
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstances){
		
		if(savedInstances!=null){
			fingerprint=savedInstances.getString("fingerprint");
		}
		AlertDialog.Builder builder;
		builder=new AlertDialog.Builder(getActivity());
		final Dialog dialog;
		builder.setTitle("Security Warning");
		builder.setMessage("SSH fingerprint differs: "+fingerprint);
		builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Editor edit=getActivity().getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).edit();
				edit.putString(Constants.PREF_SSH_FINGERPRINT, fingerprint);
				edit.commit();
				SshConnection.insecure=false;
				SshConnection.openSshConnection(getActivity(), null, false, true);
				dialog.dismiss();
			}

		});
		builder.setNegativeButton(R.string.btn_cancel, new Dialog.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SshConnection.finalizeConnection(false);
				dialog.dismiss();
			}
		});
		
		dialog=builder.create();
		dialog.setOnShowListener(new OnShowListener() {
			
			@Override
			public void onShow(DialogInterface d) {
				RaspiUtils.brandAlertDialog(dialog, getActivity());
			}
		});
		return dialog;
	}
}