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

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import at.huber.raspicast.OverflowMenuFragActivity;
import at.huber.raspicast.R;
import at.huber.raspicast.RaspiListActivity;
import at.huber.raspicast.Constants;
import at.huber.raspicast.utils.PixelTransformer;
import at.huber.raspicast.utils.RaspiUtils;

public class DirectoryChooseDialog extends DialogFragment {

	public String currentDirectory="/";
	private Callbacks mCallbacks;
	public String keyFile;
	private Button btnOk;
	private boolean lightTheme;
	private ListView mainListView;
	private TextView navigationView;
	private directoryChooseArrayAdapter<String> dirChooseAdapter;
	private HashMap<String, Parcelable> lastCurrentStrListViewStates=new HashMap<>();
	private String startPlayListDir;
	private boolean okCalled=false;
	private boolean keyFileChoose=false;
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mCallbacks = (Callbacks) activity;

	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstances){
		final Dialog d= new Dialog(getActivity());
		if(savedInstances!=null){
			currentDirectory=savedInstances.getString("playlistDir");
			if(currentDirectory==null){
				keyFileChoose=true;
				currentDirectory="/";
				setUp(d);
			}else{
				keyFileChoose=false;
				startPlayListDir=savedInstances.getString("startDir");
				setUp(d);
			}
			
		}else{
			setUp(d);
		}
		if(keyFileChoose){
			d.setTitle(R.string.key_choose_title);
			btnOk.setVisibility(View.GONE);
			mainListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		}else{
			d.setTitle(R.string.title_choose_input);
		}
		btnOk.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				d.dismiss();
				if(!keyFileChoose){
					okCalled();
					mCallbacks.onSelection(currentDirectory);
				}else{
					mCallbacks.onSelection(keyFile);
				}	
			}
		});
		return d;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState){
		if(!keyFileChoose) {
			outState.putString("playlistDir", currentDirectory);
			outState.putString("startDir", startPlayListDir);
		}
	}
	
	public interface Callbacks {
		void onSelection(String file);
	}

	@SuppressLint("ValidFragment")
	public DirectoryChooseDialog(String playlistDirectory) {
		this.lightTheme=OverflowMenuFragActivity.lightTheme;
		currentDirectory=playlistDirectory;
		startPlayListDir=playlistDirectory;
	}
	
	public DirectoryChooseDialog(){
		this.lightTheme=OverflowMenuFragActivity.lightTheme;
		startPlayListDir=currentDirectory;
		keyFileChoose=true;
	}
	
	
	private void setUp(final Dialog d){
		d.setContentView(R.layout.directory_chooser);
		setCancelable(true);
		mainListView= d.findViewById(R.id.lv_directory_choose);
		navigationView= d.findViewById(R.id.txt_navigation_bar);
		if(lightTheme)
			navigationView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_up_light, 0, 0, 0);
		dirChooseAdapter=new directoryChooseArrayAdapter<>(getActivity(), R.layout.file_browse_list_item);
		mainListView.setAdapter(dirChooseAdapter);
		if(RaspiListActivity.mTwoPane){
			navigationView.setPadding(PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(10f), 
					PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(10f));
		}
		navigationView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!currentDirectory.equals("/")){
					lastCurrentStrListViewStates.remove(currentDirectory);
					if (currentDirectory.equals("~")){
						currentDirectory="/home";
					}else{
						if (currentDirectory.contains("/")){
							currentDirectory=currentDirectory.substring(0, currentDirectory.lastIndexOf("/"));
						}else{
							currentDirectory="/";
						}
					}
					if (currentDirectory.equals("")){
						currentDirectory="/";
					}
					updateDirectoryChooseAdapter();
				}
			}
		});
		

		mainListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String item=dirChooseAdapter.getItem(position);
				if (item.endsWith("/")){
					lastCurrentStrListViewStates.put(currentDirectory, mainListView.onSaveInstanceState());
					if (currentDirectory.equals("/")){
						currentDirectory+=item.replace("/", "");
					}else{
						currentDirectory+="/" + item.replace("/", "");
					}
					updateDirectoryChooseAdapter();
				}else if(keyFileChoose){
					keyFile=currentDirectory+"/"+item;
					btnOk.performClick();
				}
			}
		});
		Button btnCancel= d.findViewById(R.id.btnCancel);
		btnCancel.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dismiss();	
			}
		});
		btnOk = d.findViewById(R.id.btnOk);
		d.setOnShowListener(new OnShowListener() {
			
			@Override
			public void onShow(DialogInterface dialog) {
				RaspiUtils.brandDialog(d, getActivity());
				d.getWindow().setLayout(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			}
		});
	}
	
	@Override
	public void onStart(){
		okCalled = false;
		super.onStart();
		if(keyFileChoose){
			keyFile=null;
		}
		updateDirectoryChooseAdapter();		
	}
	
	@Override
	public void onStop(){
		super.onStop();
		if(okCalled){
			startPlayListDir=currentDirectory;
		}else{
			currentDirectory=startPlayListDir;
		}
	}
	
	private void okCalled(){
		okCalled=true;
	}
	

	private void updateDirectoryChooseAdapter() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
			dirChooseAdapter.clear();
			String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
			File dirs = new File(dir + currentDirectory);
			File[] fileList = dirs.listFiles();
			Arrays.sort(fileList, new SortFileName());
			Arrays.sort(fileList, new SortFolder());
			for(File file : fileList){
				if(!file.getName().startsWith(".")){
					if (file.isDirectory()) {
						if(!file.getName().equalsIgnoreCase("LOST.DIR")){
							dirChooseAdapter.add(file.getName() + "/");
						}
					} else {
						if(file.length() < Constants.MAX_FILE_SIZE) {
							String fileName= file.getName();
							boolean isMediaFile=false, isImageFile=false, isNonPlaylistFile=false;
							if (fileName.lastIndexOf(".") != -1) {
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
								dirChooseAdapter.add(file.getName());
							}
						}
					}
				}
			}
			dirChooseAdapter.notifyDataSetChanged();
			if (lastCurrentStrListViewStates.containsKey(currentDirectory)){
				mainListView.onRestoreInstanceState(lastCurrentStrListViewStates.get(currentDirectory));
			}else{
				mainListView.setSelection(0);
			}
			String text=currentDirectory.replace("/","\u200A<big>/</big>\u200A");
			navigationView.setText(Html.fromHtml(text));
		}else{
			Toast.makeText(getActivity(), R.string.error_cant_read_files, Toast.LENGTH_LONG).show();
			this.dismiss();
		}
	}

	private class directoryChooseArrayAdapter<T> extends ArrayAdapter<T> {

		directoryChooseArrayAdapter(Context context, int resource) {
			super(context, resource);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			TextView textView;
			if (convertView == null){
				textView=(TextView) super.getView(position, null, parent);
				if(RaspiListActivity.mTwoPane){
					textView.setPadding(PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(13.5f), 
							PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(13.5f));
				}
			}else{
				textView=(TextView) convertView;
			}
			int drawableId;
			if (dirChooseAdapter.getItem(position).endsWith("/")){
				textView.setText(dirChooseAdapter.getItem(position).replace("/", ""));
				drawableId=(lightTheme) ? R.drawable.ic_action_dir_light : R.drawable.ic_action_dir_dark;
			}else{
				String item=dirChooseAdapter.getItem(position);
				textView.setText(item);
				if(keyFileChoose)
					drawableId=(lightTheme) ?  R.drawable.ic_action_key_light :
						R.drawable.ic_action_key_dark;
				else 
					drawableId=(lightTheme) ? R.drawable.ic_action_stream_list_light
						: R.drawable.ic_action_stream_list_dark;
				
				if(keyFileChoose && keyFile!=null && keyFile.equals(currentDirectory+item)){
					if(lightTheme)
						textView.setBackgroundResource(R.drawable.apptheme_activated_background_holo_light);
					else
						textView.setBackgroundResource(R.drawable.apptheme_activated_background_holo_dark);
				}
			}
			textView.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);
			return textView;
		}
	}
	
	//sorts based on the files name
	private class SortFileName implements Comparator<File> {
	    @Override
	    public int compare(File f1, File f2) {
	          return f1.getName().compareTo(f2.getName());
	    }
	}

	//sorts based on a file or folder. folders will be listed first
	private class SortFolder implements Comparator<File> {
	    @Override
	    public int compare(File f1, File f2) {
	         if (f1.isDirectory() == f2.isDirectory())
	            return 0;
	         else if (f1.isDirectory() && !f2.isDirectory())
	            return -1;
	         else
	            return 1;
	          }
	}

}
