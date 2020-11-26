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

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import at.huber.raspicast.utils.PixelTransformer;

public class RaspiListFragment extends ListFragment {


	private static final String STATE_ACTIVATED_POSITION = "activated_position";
	
	private Callbacks mCallbacks = sDummyCallbacks;
	private StreamListArrayAdapter<String> streamLists;
	private String streamListDirectory;
	private boolean isCreate=true;

	public int mActivatedPosition = ListView.INVALID_POSITION;

	public interface Callbacks {
		void onItemSelected(String id, int position);
	}

	private static Callbacks sDummyCallbacks = new Callbacks() {
		@Override
		public void onItemSelected(String id, int position) {
		}
	};

	public RaspiListFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		streamLists= new StreamListArrayAdapter<>(getActivity());
		setListAdapter(streamLists);
		updateDirectory();
	}

	private void readLists() {
		streamLists.clear();
		streamLists.add("\\" + getResources().getString(R.string.local_files));
		if (!RaspiListActivity.hasStoragePermission)
			return;
		String state = Environment.getExternalStorageState();
		streamLists.add("Cast");
		// A1 TV
		if (getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
				Activity.MODE_PRIVATE).getBoolean(Constants.PREF_SHOW_A1_TV, false) &&
				(getResources().getConfiguration().locale.getCountry().equals("AT") || getResources()
				.getConfiguration().locale.getCountry().equals("DE"))){
			boolean hasAt=false;
			for(String l : Resources.getSystem().getAssets().getLocales() ){
				if(l.equals("de_AT") || l.equals("de-AT")){
					hasAt=true;
				}
			}
			if((hasAt && getResources().getConfiguration().locale.getCountry().equals("AT"))){
				streamLists.add(Constants.A1_TV_STREAM_LIST_NAME);
			}
		}
		
		Set<String> dirsHomeScreen=getActivity().getSharedPreferences(Constants.PREF_FILE_NAME, 
				Activity.MODE_PRIVATE).getStringSet(Constants.PREF_DIRS_HOME_SCREEN, null);
		if(dirsHomeScreen!=null){
			for(String dir : dirsHomeScreen){
				streamLists.add("\\" + dir);
			}
		}
		
		
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			String m_str = Environment.getExternalStorageDirectory().getAbsolutePath();
			File dirs = new File(m_str + streamListDirectory);
			if (!dirs.exists()){
				Toast.makeText(getActivity(), R.string.error_dir_not_exist, Toast.LENGTH_LONG).show();
				dirs=new File(m_str);
				SharedPreferences.Editor editor = getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
						Activity.MODE_PRIVATE).edit();
				editor.putString(Constants.PREF_INPUT_DIR_NAME, "/");
				editor.apply();
			}
			File[] streamListFiles=dirs.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					if (!pathname.isDirectory() && pathname.length() <= Constants.MAX_FILE_SIZE 
							&& !pathname.getName().startsWith(".")){
						String fileName= pathname.getName();
						boolean isMediaFile=false, isImageFile=false, isNonPlaylistFile=false;
						if (fileName.lastIndexOf(".") != -1){
							fileName=fileName.substring(fileName.lastIndexOf("."));
							for(String extension : Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
								if (fileName.equalsIgnoreCase(extension)){
									isMediaFile=true;
									break;
								}
							}
							if (!isMediaFile){
								for(String extension : Constants.COMMON_IMAGE_FILE_EXTENSIONS){
									if (fileName.equalsIgnoreCase(extension)){
										isImageFile=true;
										break;
									}
								}
								if(!isImageFile){
									for(String extension : Constants.COMMON_NON_PLAYLIST_FILE_EXTENSIONS){
										if (fileName.equalsIgnoreCase(extension)){
											isNonPlaylistFile=true;
											break;
										}
									}
								}
							}
							
						}
						if(!isImageFile&&!isMediaFile && !isNonPlaylistFile){
							return true;
						}
					}
					return false;
				}
			});
			
			if(streamListFiles!=null){
				for (File streamListFile : streamListFiles) {
					streamLists.add(streamListFile.getName());
				}
			}
		} else {
			Toast.makeText(getActivity(), R.string.error_cant_read_files, Toast.LENGTH_LONG).show();
		}
	}
	
	public void setOnStartUp(){
		int lastSelectedItem=getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
				Activity.MODE_PRIVATE).getInt(Constants.PREF_LAST_TAB, 0);
		if (lastSelectedItem >= 0 && lastSelectedItem < streamLists.getCount()){
			getListView().performItemClick(getListView().getAdapter().getView(lastSelectedItem, null, null),
					lastSelectedItem,
					getListView().getAdapter().getItemId(lastSelectedItem));
		}
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {			
				String dir=streamLists.getItem(position);
				if(dir.startsWith("\\")&& position > 0){
					dir=dir.replace("\\", "");
					((OverflowMenuFragActivity)getActivity()).showDirOptions(dir, true);
				}
				return true;
			}
		});
		// Restore the previously serialized activated item position.
		if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
			setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		// Activities containing this fragment must implement its callbacks.
		if (!(activity instanceof Callbacks)) {
			throw new IllegalStateException("Activity must implement fragment's callbacks.");
		}
		mCallbacks = (Callbacks) activity;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		// Reset the active callbacks interface to the dummy implementation.
		mCallbacks = sDummyCallbacks;
	}

	@Override
	public void onListItemClick(ListView listView, View view, int position, long id) {
		super.onListItemClick(listView, view, position, id);

		// Notify the active callbacks interface (the activity, if the
		// fragment is attached to one) that an item has been selected.
		mCallbacks.onItemSelected(streamLists.getItem(position), position);
		if(RaspiListActivity.mTwoPane){
			Editor editor=getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
					Activity.MODE_PRIVATE).edit();
			editor.putInt(Constants.PREF_LAST_TAB, position);
			editor.apply();
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mActivatedPosition != ListView.INVALID_POSITION) {
			// Serialize and persist the activated item position.
			outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
		}
	}
	
	@Override
	public void onStart(){
		super.onStart();
		if(!isCreate){
			updateDirectory();
		}else{
			isCreate=false;
		}
	}
	
	public void updateDirectory(){
		streamListDirectory= getActivity().getSharedPreferences(Constants.PREF_FILE_NAME,
				Activity.MODE_PRIVATE).getString(Constants.PREF_INPUT_DIR_NAME, "/");
		readLists();
		streamLists.notifyDataSetChanged();
	}

	/**
	 * Turns on activate-on-click mode. When this mode is on, list items will be
	 * given the 'activated' state when touched.
	 */
	public void setActivateOnItemClick(boolean activateOnItemClick) {
		// When setting CHOICE_MODE_SINGLE, ListView will automatically
		// give items the 'activated' state when touched.
		getListView().setChoiceMode(
				activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
	}

	private void setActivatedPosition(int position) {
		if (position >= getListView().getChildCount()) {
			return;
		}
		if (position == ListView.INVALID_POSITION) {
			getListView().setItemChecked(mActivatedPosition, false);
		} else {
			getListView().setItemChecked(position, true);
		}

		mActivatedPosition = position;
	}

	private class StreamListArrayAdapter<T> extends ArrayAdapter<T> {

		StreamListArrayAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_activated_1, android.R.id.text1);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {

			TextView textView;
			if(convertView==null){
				textView = (TextView) super.getView(position, null, parent);
			}else{
				textView=(TextView)convertView;
			}
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
			if (RaspiListActivity.mTwoPane) {
				textView.setPadding(PixelTransformer.getPixelsFromDp(16.5f), PixelTransformer.getPixelsFromDp(14.3f), 
						PixelTransformer.getPixelsFromDp(7.5f), PixelTransformer.getPixelsFromDp(14.3f));
				textView.setMinHeight(PixelTransformer.getPixelsFromDp(75));
			} else {
				textView.setPadding(PixelTransformer.getPixelsFromDp(7f), 0, PixelTransformer.getPixelsFromDp(7f), 0);
				if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT)
					textView.setHeight(PixelTransformer.getPixelsFromHeight(0.13f));
				else
					textView.setHeight(PixelTransformer.getPixelsFromHeight(0.2f));
				//textView.setPadding(PixelTransformer.getPixelsFromDp(6.7f), PixelTransformer.getPixelsFromDp(17f), 
						//PixelTransformer.getPixelsFromDp(6.7f), PixelTransformer.getPixelsFromDp(17f));
			}
			int drawingId;
			if(position==1){
				textView.setText(streamLists.getItem(position));
				drawingId= (OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_cast_black_36dp:
					R.drawable.ic_cast_white_36dp;
			} else if (position == 0) {
				textView.setText(streamLists.getItem(position).replace("\\", ""));
				drawingId= (OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_raspi_data_light:
					R.drawable.ic_action_raspi_data_dark;
			} else if (streamLists.getItem(position).startsWith("\\")){
				String text=streamLists.getItem(position).replace("\\", "");
				if(text.contains("/")){
					text=text.substring(text.lastIndexOf('/')+1);
				}
				textView.setText(text);
				drawingId= (OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_dir_light:
					R.drawable.ic_action_dir_dark;
			}else{
				textView.setText(streamLists.getItem(position));
				drawingId=(OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_stream_list_light:
					R.drawable.ic_action_stream_list_dark;
			}
			textView.setCompoundDrawablesWithIntrinsicBounds(drawingId,0, 0, 0);
			if (RaspiListActivity.mTwoPane)
				textView.setCompoundDrawablePadding(PixelTransformer.getPixelsFromDp(8f));
			else
				textView.setCompoundDrawablePadding(PixelTransformer.getPixelsFromWidth(0.06f));
			textView.setMaxLines(2);
			textView.setEllipsize(TruncateAt.END);
			return textView;
		}
	}
}
