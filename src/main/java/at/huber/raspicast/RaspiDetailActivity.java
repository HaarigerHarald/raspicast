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

import android.os.Bundle;
import androidx.fragment.app.Fragment;

public class RaspiDetailActivity extends OverflowMenuFragActivity {
	
	Fragment fragment;
	private boolean isCast=false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_raspicast_detail);

		if (savedInstanceState == null) {
			int position=getIntent().getIntExtra(Constants.LIST_POSTION, 0);
			if(position==1){
				fragment= new CastFragment();
				isCast=true;
			}else{
				Bundle arguments = new Bundle();
				arguments.putString(Constants.PLAYLIST_DIR, getIntent().getStringExtra(Constants.PLAYLIST_DIR));
				arguments.putString(Constants.STREAMING_FILE,
						getIntent().getStringExtra(Constants.STREAMING_FILE));
				arguments.putInt(Constants.LIST_POSTION, position);
				fragment = new RaspiDetailFragment();
				fragment.setArguments(arguments);
			}
			getSupportFragmentManager().beginTransaction()
					.add(R.id.raspicast_detail_container, fragment).commit();
		}
	}
	
	
	@Override
	public void onBackPressed(){
		if(isQueueOpen()){
			openCloseQueue();
			return;
		}
		if(fragment!=null && !isCast &&  ((RaspiDetailFragment)fragment).localData){
			if(RaspiDetailFragment.currentDirectory.contains("/")&& !RaspiDetailFragment.currentDirectory.equals("/")){
				RaspiDetailFragment.lastCurrentStrListViewStates.remove(RaspiDetailFragment.currentDirectory);
				RaspiDetailFragment.currentDirectory = RaspiDetailFragment.currentDirectory.substring(0,
						RaspiDetailFragment.currentDirectory.lastIndexOf("/"));
				if(RaspiDetailFragment.currentDirectory.equals("")){
					RaspiDetailFragment.currentDirectory="/";
				}
				((RaspiDetailFragment)fragment).updateLocalData();
			}else{
				finish();
				overridePendingTransition(R.anim.no_fade,R.anim.fade_out);
			}
		}else{
			finish();
			overridePendingTransition(R.anim.no_fade,R.anim.fade_out);
		}	
	}	
	
}
