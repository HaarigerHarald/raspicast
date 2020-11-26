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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import at.huber.raspicast.utils.PixelTransformer;

public class RaspiDetailFragment extends Fragment {

	protected static String currentDirectory="~";

	protected final static HashMap<String, Parcelable> lastCurrentStrListViewStates=new HashMap<>();

	public boolean localData=false;
	
	private static final Animation blendInAnimation=new AlphaAnimation(0.0f, 1.0f);
	static {
		blendInAnimation.setDuration(300);
	}
	
	private int additionalBotPad=0;
	private File streamListFile;
	private ArrayList<String> streamListArray;
	private DetailListArrayAdapter<String> streamListAdapter;
	private ListView strListView;
	private ProgressBar pb;
	private TextView txtCurrentDir;
	private volatile boolean isUpdating=false;
	private Handler hand;
	private boolean subtitleChoosing=false;
	private String nextFileToPlay;
	private String nextTitle;
	private SubtitleOptions nextSubtitleOptions;
	private SwipeRefreshLayout swipeRefreshLayout;
	private boolean hideExtensions=false;
	private Runnable run= new Runnable() {	
		@Override
		public void run() {
			if (isUpdating && getActivity()!=null && !getActivity().isFinishing()){
				if(pb!=null){
					strListView.setVisibility(View.GONE);
					pb.setVisibility(View.VISIBLE);
				}
			}	
		}
	};

	
	public RaspiDetailFragment() {
	}
	
	protected void setExternalSubtitleChoosing(String fileToPlay, String title, SubtitleOptions so){
		subtitleChoosing=true;
		nextFileToPlay=fileToPlay;
		nextTitle=title;
		nextSubtitleOptions=so;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		additionalBotPad = PixelTransformer.getPixelsFromDp(2);
		String streamListDirectory = getArguments().getString(Constants.PLAYLIST_DIR);
		int listPosition= getArguments().getInt(Constants.LIST_POSTION);
		if (getArguments().getString(Constants.STREAMING_FILE).startsWith("\\")){
			localData=true;
			if(listPosition!=0){
				String dir=getArguments().getString(Constants.STREAMING_FILE);
				dir=dir.replace("\\", "");
				currentDirectory=dir;
			}
			streamListAdapter=new DetailListArrayAdapter<>(getActivity(),
					R.layout.file_browse_list_item);
			updateLocalData();
		}else{
			if (getArguments().containsKey(Constants.STREAMING_FILE)){
				InputStream a1TvInputStream=null;
				if (getArguments().get(Constants.STREAMING_FILE).equals(
						Constants.A1_TV_STREAM_LIST_NAME) && 
						getActivity().getSharedPreferences(Constants.PREF_FILE_NAME, Activity.MODE_PRIVATE).
						getBoolean(Constants.PREF_SHOW_A1_TV, false)){
					a1TvInputStream=getResources().openRawResource(R.raw.a1_tv);
				}else{
					String filepath=Environment.getExternalStorageDirectory().getAbsolutePath()
							+ streamListDirectory + "//" + getArguments().get(Constants.STREAMING_FILE);
					streamListFile=new File(filepath);
					if (!streamListFile.canRead() || streamListFile.length() > Constants.MAX_FILE_SIZE){
						Toast.makeText(getActivity(), R.string.error_cant_read_file, Toast.LENGTH_LONG)
								.show();
						return;
					}
				}
				streamListArray=new ArrayList<>();
				streamListAdapter=new DetailListArrayAdapter<>(getActivity(),
						android.R.layout.simple_list_item_1);
				BufferedReader reader=null;
				FileInputStream fis=null;

				try{
					if (a1TvInputStream == null){
						fis=new FileInputStream(streamListFile);
						reader=new BufferedReader(new InputStreamReader(fis, "UTF-8"));
					}else{
						reader=new BufferedReader(new InputStreamReader(a1TvInputStream, "UTF-8"));
					}
					String firstLine;
					while ((firstLine=reader.readLine()) != null && firstLine.equals(""));
					if (firstLine == null){
						Toast.makeText(getActivity(), R.string.error_empty_file, Toast.LENGTH_LONG)
								.show();
						return;
					}
					if (!firstLine.startsWith("[playlist]"))
						readM3UList(reader, firstLine);
					else
						readPlsList(reader);
					reader.close();
					if (a1TvInputStream == null)
						fis.close();
				}catch (Exception ex){
					ex.printStackTrace();
					Toast.makeText(getActivity(), R.string.error_reading_file, Toast.LENGTH_LONG).show();
				}finally{

					try{
						if (reader != null)
							reader.close();
						if (fis != null)
							fis.close();
					}catch (IOException e){
						e.printStackTrace();
					}
				}
			}
		}
	}

	private void readM3UList(BufferedReader reader, String firstLine) throws IOException {
		String previousLine = null;
		String line=firstLine;
		String lineWithoutSpace;
		do{
			lineWithoutSpace=line.replace(" ", "");
			if (lineWithoutSpace.startsWith("#")){
				if (lineWithoutSpace.toUpperCase(getResources().getConfiguration().locale).startsWith(
						"#EXTINF")
						&& line.contains(","))
					previousLine=line;
			}else{
				boolean isSupported=false;
				for(String stream : Constants.SUPPORTED_STREAMS){
					if (lineWithoutSpace.startsWith(stream)){
						isSupported=true;
						break;
					}
				}
				if (isSupported){
					if (previousLine != null){
						streamListAdapter.add(previousLine.substring(previousLine.lastIndexOf(",") + 1));
						streamListArray.add(lineWithoutSpace);
					}else{
						streamListAdapter.add(lineWithoutSpace);
						streamListArray.add(lineWithoutSpace);
					}
					previousLine=null;
				}
			}
		}while ((line=reader.readLine()) != null);
	}

	private void readPlsList(BufferedReader reader) throws IOException {
		StringBuilder buildr=new StringBuilder();
		String line;
		String title;
		String file;
		int numEntries=0;
		while ((line=reader.readLine()) != null){
			buildr.append(line);
			buildr.append("\n");
			if (line.contains("NumberOfEntries")){
				line=line.replace(" ", "");
				line=line.substring(line.indexOf("=") + 1, line.length());
				numEntries=Integer.parseInt(line);
			}
		}
		String plsList=buildr.toString();
		for(int i=1; i <= numEntries; i++){
			title=null;
			file=null;
			if (plsList.contains("File" + i)){
				file=plsList.substring(plsList.indexOf("File" + i));
				file=file.substring(0, file.indexOf("\n"));
				file=file.substring(file.indexOf("=") + 1);
			}
			if (plsList.contains("Title" + i)){
				title=plsList.substring(plsList.indexOf("Title" + i));
				title=title.substring(0, title.indexOf("\n"));
				title=title.substring(title.indexOf("=") + 1);
			}
			if (file != null){
				boolean isSupported=false;
				for(String stream : Constants.SUPPORTED_STREAMS){
					if (file.startsWith(stream)){
						isSupported=true;
						break;
					}
				}
				if (isSupported){
					if (title != null){
						streamListAdapter.add(title);
						streamListArray.add(file);
					}else{
						streamListAdapter.add(file);
						streamListArray.add(file);
					}
				}
			}
		}
	}

	public void updateLocalData() {
		isUpdating=true;
		Log.d("currentDirectory:", currentDirectory);
		if(SshConnection.homeDir!=null &&currentDirectory.startsWith(SshConnection.homeDir)){
			currentDirectory=currentDirectory.replaceFirst(SshConnection.homeDir, "~");	
		}
		String text=currentDirectory.replace("/","\u200A<big>/</big>\u200A");
		if(txtCurrentDir!=null){
			txtCurrentDir.setText(Html.fromHtml(text));
		}
		if(hand!=null){
			hand.removeCallbacks(run);
		}else{
			hand=new Handler();
		}
		hand.postDelayed(run, 500);
		new Thread("listDirThread") {
			@Override
			public void run() {
				String[] dirs=null;
				for(int i=0; i<2&& dirs==null&& getActivity()!=null && !getActivity().isFinishing(); i++){
					dirs=SshConnection.listDirectories(currentDirectory, getActivity());
					try{
						Thread.sleep(10);
					}catch (InterruptedException e){
						break;
					}
				}
				if (dirs != null && getActivity() != null && !getActivity().isFinishing()){
					final String[] tempList=dirs;
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (getActivity()==null || getActivity().isFinishing()) return;
								isUpdating=false;
								streamListAdapter.clear();
								if (tempList.length > 0 && !tempList[0].equals("")){
									for(String file : tempList){
										if (file.startsWith(" ")){
											file=file.substring(1);
										}
										streamListAdapter.add(file);
									}
								}
								stopScroll(strListView);
								streamListAdapter.notifyDataSetChanged();
								if (strListView != null && !strListView.isShown()){
									strListView.setVisibility(View.VISIBLE);
//									// TODO
//									if(RaspiListActivity.mTwoPane || pb.isShown())
									strListView.startAnimation(blendInAnimation);
								}
								if (pb != null && pb.isShown()){	
									pb.setVisibility(View.GONE);
								}
								if(swipeRefreshLayout!=null && swipeRefreshLayout.isRefreshing()){
									new Handler().postDelayed(new Runnable() {
										public void run() {
											swipeRefreshLayout.setRefreshing(false);
										}
									}, 400);
								}else if(strListView != null){
									strListView.setSelection(0);
									if (lastCurrentStrListViewStates.containsKey(currentDirectory)){
										strListView.onRestoreInstanceState(lastCurrentStrListViewStates
												.get(currentDirectory));
									}
								}
								
							}
						});
				}else{
					if (getActivity() != null && !getActivity().isFinishing() && currentDirectory.contains("/")
							&& !currentDirectory.equals("/")){
						currentDirectory=currentDirectory.substring(0, currentDirectory.lastIndexOf("/"));
					}
				}
			}
		}.start();
	}
	
	private void stopScroll(AbsListView view)
	{
	    try
	    {
	        Field field = android.widget.AbsListView.class.getDeclaredField("mFlingRunnable");
	        field.setAccessible(true);
	        Object flingRunnable = field.get(view);
	        if (flingRunnable != null)
	        {
	            Method method = Class.forName("android.widget.AbsListView$FlingRunnable").getDeclaredMethod("endFling");
	            method.setAccessible(true);
	            method.invoke(flingRunnable);
	        }
	    }
	    catch (Exception ignored) {}
	}
	
	private String getFilePath(String item){
		String fileToPlay;
		String path;
		if (currentDirectory.startsWith("/")){
			path= currentDirectory;
			fileToPlay=path + "/" + item;
		}else{
			if (currentDirectory.equals("~")){
				fileToPlay=item;
			}else{
				path=currentDirectory.substring(currentDirectory.indexOf("/") + 1);
				fileToPlay=path+ "/" + item;
			}
		}
		return fileToPlay;
	}
	
	@Override
	public void onPause(){
		super.onPause();
		if(!isUpdating){
			lastCurrentStrListViewStates.put(currentDirectory,
				strListView.onSaveInstanceState());
		}
	}
	
	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState){
        ((QueueFragActivity)getActivity()).configFloatingActionButton();
        super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View rootView=inflater.inflate(R.layout.fragment_raspicast_detail, container, false);
		if (streamListAdapter != null){
			strListView = rootView.findViewById(R.id.raspicast_detail);
			strListView.setAdapter(streamListAdapter);
			swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
			if (!localData){
				strListView.setVisibility(View.VISIBLE);
				swipeRefreshLayout.setEnabled(false);
				strListView.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						Log.d("Playing", streamListArray.get(position));
						SubtitleOptions so = new SubtitleOptions(getActivity());
						SshConnection.startProgramm(streamListArray.get(position), getActivity().getApplicationContext(), streamListAdapter.getItem(position),streamListArray.get(position) ,
								so, false);
						((OverflowMenuFragActivity)getActivity()).videoControlThread.
							syncWithRaspi(1000, 3, false);
						if(SshConnection.queueIndex>0)
							SshConnection.readQueue(getContext(), 400, true);
					}
				});
			}else{
				 if(RaspiListActivity.lightTheme){
					 swipeRefreshLayout.setColorSchemeResources(R.color.highlight_color_light);
				 }else{
					 swipeRefreshLayout.setColorSchemeResources(R.color.highlight_color_dark);
				 }
				 swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
				      @Override
				      public void onRefresh() {
				    	  updateLocalData();
				      }
				    
				});
				txtCurrentDir = rootView.findViewById(R.id.txt_current_dir);
				String text=currentDirectory.replace("/","\u200A<big>/</big>\u200A");
				txtCurrentDir.setText(Html.fromHtml(text));
				if(RaspiListActivity.mTwoPane){
					txtCurrentDir.setPadding(PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(10f), 
							PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(10f));
				}
				
				txtCurrentDir.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						if(!currentDirectory.equals("/")){
							lastCurrentStrListViewStates.remove(currentDirectory);
							if (currentDirectory.equals("~")){
								currentDirectory="/home";
							}else{
								if (currentDirectory.contains("/")){
									currentDirectory=currentDirectory.substring(0,
											currentDirectory.lastIndexOf("/"));
								}else{
									currentDirectory="/";
								}
							}
							if (currentDirectory.equals("")){
								currentDirectory="/";
							}
							updateLocalData();
						}	
					}
				});
				txtCurrentDir.setVisibility(View.VISIBLE);
				View v=rootView.findViewById(R.id.divider);
				v.setVisibility(View.VISIBLE);
				pb = rootView.findViewById(R.id.pgr_loading);
				strListView.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						String item = streamListAdapter.getItem(position);
						if (item.endsWith("/")){
							if(!isUpdating){
								lastCurrentStrListViewStates.put(currentDirectory,
										strListView.onSaveInstanceState());
								if (currentDirectory.equals("/")){
									currentDirectory += item.replace("/", "");
								}else{
									currentDirectory += "/" + item.replace("/", "");
								}
								updateLocalData();
							}
						}else{
							String fileToPlay=getFilePath(item);
							boolean isMediaFile=false;
							boolean isImageFile=false;
							if (item.lastIndexOf(".") != -1){
								String thisFileExtension=item.substring(item.lastIndexOf("."));
								for(String extension : Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
									if (thisFileExtension.equalsIgnoreCase(extension)){
										isMediaFile=true;
										break;
									}
								}
								if (!isMediaFile){
									for(String extension : Constants.COMMON_IMAGE_FILE_EXTENSIONS){
										if (thisFileExtension.equalsIgnoreCase(extension)){
											isImageFile=true;
											break;
										}
									}
								}
							}
							if (isMediaFile){
								Log.d("File To PLay:", fileToPlay);
								SubtitleOptions so = new SubtitleOptions(getActivity());
								if(QueueFragActivity.defaultPlayOption==0){
									SshConnection.startProgramm(fileToPlay, getActivity().getApplicationContext(), item, "local", so, false);
									((OverflowMenuFragActivity)getActivity()).videoControlThread.
										syncWithRaspi(1200, 2, false);
								}else if(QueueFragActivity.defaultPlayOption==1){
									SshConnection.addToQueue(fileToPlay, getActivity().getApplicationContext(), item, "local", so, false);
								}					
								SshConnection.readQueue(getContext(), 300, true);
							}else if (isImageFile){
								String path = (fileToPlay.contains("/")) ? fileToPlay.substring(0, fileToPlay.lastIndexOf("/")):"";
								SshConnection.showPicture(getActivity().getApplicationContext(), path, item);
								((OverflowMenuFragActivity)getActivity()).videoControlThread.
									syncWithRaspi(300, 0, false);
								if(SshConnection.queueIndex>0){
									SshConnection.readQueue(getContext(), 300, true);
								}
							}else if(subtitleChoosing){
								if(nextSubtitleOptions!=null)
									nextSubtitleOptions.extSubtitlePath=fileToPlay;
								SshConnection.startProgramm(nextFileToPlay, getActivity().getApplicationContext(), nextTitle, "local", nextSubtitleOptions, false);
								((OverflowMenuFragActivity)getActivity()).videoControlThread.syncWithRaspi(
										1200, 2, false);
							}
							subtitleChoosing=false;
						}
					}
				});
				strListView.setOnItemLongClickListener(new OnItemLongClickListener() {

					@Override
					public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
						subtitleChoosing=false;
						String item=streamListAdapter.getItem(position);
						if(item.endsWith("/")){
							item=item.substring(0, item.length()-1);
							((OverflowMenuFragActivity)getActivity()).showDirOptions(currentDirectory+"/"+item, false);
							
							return true;
						}
						boolean isMediaFile=false;
						boolean isImageFile=false;
						if (item.lastIndexOf(".") != -1){
							String thisFileExtension=item.substring(item.lastIndexOf("."));
							for(String extension : Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
								if (thisFileExtension.equalsIgnoreCase(extension)){
									isMediaFile=true;
									break;
								}
							}
							if (!isMediaFile){
								for(String extension : Constants.COMMON_IMAGE_FILE_EXTENSIONS){
									if (thisFileExtension.equalsIgnoreCase(extension)){
										isImageFile=true;
										break;
									}
								}
							}
						}
						if(isImageFile){
							List<String> pictures= new ArrayList<>();
							pictures.add(item);
							for(int i=(position+1)%streamListAdapter.getCount(); (i%streamListAdapter.getCount())!=position; i++){
								item=streamListAdapter.getItem(i%streamListAdapter.getCount());
								if (item.lastIndexOf(".") != -1){
									String thisFileExtension=item.substring(item.lastIndexOf("."));
										for(String extension : Constants.COMMON_IMAGE_FILE_EXTENSIONS){
											if (thisFileExtension.equalsIgnoreCase(extension)){
												pictures.add(item);
												break;
											}
										}
								}
							}
							String path;
							if (currentDirectory.startsWith("/")){
								path= currentDirectory;
							}else{
								if (currentDirectory.equals("~")){
									path="";
								}else{
									path=currentDirectory.substring(currentDirectory.indexOf("/") + 1);
								}
							}
							((OverflowMenuFragActivity)getActivity()).showSlideShowOptions(path,pictures.toArray(new String[pictures.size()]) );
						}else{
							String fileToPlay=getFilePath(item);
							SubtitleOptions so = new SubtitleOptions(getActivity());
							((OverflowMenuFragActivity)getActivity()).showPlayOptions(fileToPlay, item, so, streamListAdapter );
						}
						return true;
					}
				});
			}
		}

		return rootView;
	}
	
	@Override
	public void onResume(){
		super.onResume();
		hideExtensions=getActivity().getSharedPreferences(Constants.PREF_FILE_NAME, 
				Activity.MODE_PRIVATE).getBoolean(Constants.PREF_HIDE_MEDIA_EXTIONSIONS, false);
	}

	private class DetailListArrayAdapter<T> extends ArrayAdapter<T> {

		DetailListArrayAdapter(Context context, int resource) {
			super(context, resource);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			View view;
			if (localData){
				TextView textView;
				if (convertView == null){
					textView=(TextView) super.getView(position, null, parent);
					if(!RaspiListActivity.mTwoPane){
						textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
					}else{
						textView.setPadding(PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(12.5f), 
								PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(12.5f));
					}
					textView.setMinHeight(PixelTransformer.getPixelsFromDp(57));
				}else{
					textView=(TextView) convertView;
				}
				int drawableId;
				if (streamListAdapter.getItem(position).endsWith("/")){
					textView.setText(streamListAdapter.getItem(position).replace("/", ""));
					drawableId=(OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_dir_light
							: R.drawable.ic_action_dir_dark;
				}else{
					String item=streamListAdapter.getItem(position);
					String text=item.replace("_", "\u200B_\u200B");
					textView.setText(text);
					boolean isMediaFile=false;
					boolean isImageFile=false;
					if (item.contains(".")){
						item=item.substring(item.lastIndexOf("."));
						for(String extension : Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
							if (item.equalsIgnoreCase(extension)){
								isMediaFile=true;
								break;
							}
						}
						if (!isMediaFile){
							for(String extension : Constants.COMMON_IMAGE_FILE_EXTENSIONS){
								if (item.equalsIgnoreCase(extension)){
									isImageFile=true;
									break;
								}
							}
						}
					}

					if (isMediaFile){
						if(hideExtensions){
							text=text.substring(0, text.lastIndexOf("."));
							textView.setText(text);
						}
						drawableId=(OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_media_light_old
								: R.drawable.ic_action_media_dark_old;
					}else if(isImageFile){
						drawableId=(OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_picture_light
								: R.drawable.ic_action_picture_dark;
					}else{
						drawableId=(OverflowMenuFragActivity.lightTheme) ? R.drawable.ic_action_data_light
								: R.drawable.ic_action_data_dark;
					}
				}
				textView.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);
				view=textView;
			}else{
				TextView tv=(TextView) super.getView(position, convertView, parent);
				if(RaspiListActivity.mTwoPane){
					tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
					tv.setPadding(PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(15f), 
							PixelTransformer.getPixelsFromDp(9f), PixelTransformer.getPixelsFromDp(15f));
				}else{
					tv.setPadding(PixelTransformer.getPixelsFromDp(13.3f), PixelTransformer.getPixelsFromDp(11.3f),
							PixelTransformer.getPixelsFromDp(13.3f), PixelTransformer.getPixelsFromDp(11.3f));
				}
				tv.setMaxLines(2);
				view=tv;
			}
			return view;
		}
	}

}
