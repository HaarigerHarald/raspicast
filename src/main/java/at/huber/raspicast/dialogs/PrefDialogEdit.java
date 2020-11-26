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
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import at.huber.raspicast.Constants;
import at.huber.raspicast.R;

public class PrefDialogEdit extends DialogFragment{
	
	private SharedPreferences mSharedPreferences;
	
	String prefName;

	@SuppressLint("ValidFragment")
	public PrefDialogEdit(String pref){
		this.prefName=pref;
	}
	
	public PrefDialogEdit(){
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState){
		outState.putString("prefName", prefName);
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstances){
		mSharedPreferences = getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
				Activity.MODE_PRIVATE);
		AlertDialog.Builder alert=new AlertDialog.Builder(getActivity());
		if(savedInstances!=null){
			prefName=savedInstances.getString("prefName");
		}
		final Preference pref=((PreferenceActivity)getActivity()).findPreference(prefName);
		alert.setTitle(pref.getTitle());
		LinearLayout ll = (LinearLayout)getActivity().getLayoutInflater().
				inflate(R.layout.pref_dial_edit_txt, null);
		final EditText input = ll.findViewById(R.id.pref_dial_edit_txt);
		
		input.setText(mSharedPreferences.getString(prefName, ""));
		input.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		alert.setView(ll);
		final Dialog dialog;
		
		alert.setPositiveButton(R.string.btn_ok, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Editor edit=mSharedPreferences.edit();
				edit.putString(prefName, input.getText().toString());
				if(input.getText().toString().equals(""))
					pref.setSummary("Long click to edit");
				else
					pref.setSummary(input.getText().toString());
				edit.apply();
				dialog.dismiss();
				
			}
		});
		alert.setNegativeButton(R.string.btn_cancel, null);
		dialog=alert.create();
		return dialog;
	}
	
	
}