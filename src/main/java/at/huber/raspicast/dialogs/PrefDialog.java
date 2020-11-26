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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import at.huber.raspicast.Constants;
import at.huber.raspicast.R;
import at.huber.raspicast.utils.RaspiUtils;

public class PrefDialog extends DialogFragment{
	
	private SharedPreferences mSharedPreferences;
	
	String names[];
	String prefName;
	boolean intToBool;

	@SuppressLint("ValidFragment")
	public PrefDialog(final String names[], String pref, final boolean intToBool){
		this.names=names;
		this.prefName=pref;
		this.intToBool=intToBool;
	}
	
	public PrefDialog(){
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState){
		outState.putStringArray("names", names);
		outState.putBoolean("intToBool", intToBool);
		outState.putString("prefName", prefName);
	}

	@SuppressWarnings("ResourceType")
	@SuppressLint("ALL")
	@Override
	public Dialog onCreateDialog(Bundle savedInstances){
		AlertDialog.Builder alert=new AlertDialog.Builder(getActivity());
		if(savedInstances!=null){
			names=savedInstances.getStringArray("names");
			intToBool=savedInstances.getBoolean("intToBool");
			prefName=savedInstances.getString("prefName");
		}
		mSharedPreferences = getActivity().getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE);
		final Preference pref=((PreferenceActivity)getActivity()).findPreference(prefName);
		alert.setTitle(pref.getTitle());
		final Dialog dialog;
		
		ScrollView scrlView=(ScrollView) getActivity().getLayoutInflater().inflate(R.layout.stream_chooser, null);
		final RadioGroup rg= scrlView.findViewById(R.id.rg_stream_chooser);
		for(int i=0; i < names.length; i++){
			RadioButton rb=(RadioButton) getActivity().getLayoutInflater().inflate(R.layout.stream_chooser_radiobtn, rg,
					false);
			rb.setText(names[i]);
			rb.setId(i);
			rg.addView(rb);
			if (i + 1 < names.length){
				View divider= getActivity().getLayoutInflater().inflate(R.layout.stream_chooser_divider, rg, false);
				rg.addView(divider);
			}
		}
		if (intToBool){
			if (mSharedPreferences.getBoolean(prefName, true))
				rg.check(0);
			else
				rg.check(1);
		}else{
			rg.check(mSharedPreferences.getInt(prefName, 0));
		}
		
		alert.setNegativeButton(R.string.btn_cancel, null);
		alert.setView(scrlView);
		dialog=alert.create();
		rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				Editor edit=mSharedPreferences.edit();
				if (intToBool){
					if (checkedId == 0)
						edit.putBoolean(prefName, true);
					else
						edit.putBoolean(prefName, false);
				}else
					edit.putInt(prefName, checkedId);

				pref.setSummary(names[checkedId]);
				edit.apply();
				dialog.dismiss();
			}
		});
		dialog.setOnShowListener(new OnShowListener() {
			
			@Override
			public void onShow(DialogInterface d) {
				RaspiUtils.brandAlertDialog(dialog, getActivity());
			}
		});
		return dialog;
	}
	
	
}