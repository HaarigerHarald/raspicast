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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PixelTransformer;
import at.huber.raspicast.utils.RaspiUtils;

public abstract class QueueFragActivity extends FragmentActivity{
	
	protected static int defaultPlayOption;
	private FrameLayout queueSlideFrame;
	private FrameLayout contentFrame;
	private ListView queueList;
	private static QueueAdapter queueAdapter;
	private DrawerLayout mDrawerLayout;
	private int markedPlayingIndex=-1;
	private int highlightColor;
	private boolean hideFileExtensions=false;
	private FloatingActionButton fab;
	private boolean openDrawerWithSwipe = true;
	private boolean drawerSliding = false;
	private boolean miniQueueButton = false;
	
	private static final ScaleAnimation  scaleAnimation = new ScaleAnimation(0, 1f, 0, 1f, Animation.RELATIVE_TO_SELF, (float)0.5,Animation.RELATIVE_TO_SELF, (float)0.5);
	static {
		scaleAnimation.setDuration(250);
	}
	
	protected boolean closeAppAfterQueue=false;
	
	public static final String QUEUE_UPDATE_ACTION = "at.huber.raspicast.queue_update";
	
	private BroadcastReceiver queueUpdateReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        if(intent.getAction().equals(QUEUE_UPDATE_ACTION)) {
	            updateQueue(false);
	        }
	    }	
	};
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(closeAppAfterQueue)
			return;
		if(!RaspiListActivity.killOnExit){
			OverflowMenuFragActivity.lightTheme = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE)
					.getBoolean(Constants.PREF_LIGHT_THEME, Constants.LIGHT_THEME_DEFAULT);
		}
		if (OverflowMenuFragActivity.lightTheme) {
			setTheme(R.style.RaspicastTheme_HoloLight);
		}
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    		getActionBar().setElevation(0);
		}else{
			getTheme().applyStyle(R.style.NoConOverlay, true);
		}
		
        super.setContentView(R.layout.main_slide_menu);
        contentFrame= findViewById(R.id.content_frame);
		
		DisplayMetrics metrics = getResources().getDisplayMetrics(); 
		PixelTransformer.setDensity(metrics.density, metrics.heightPixels, metrics.widthPixels);
		mDrawerLayout = findViewById(R.id.drawer_layout);
		mDrawerLayout.setDrawerShadow(R.drawable.shadowright, GravityCompat.END);
		mDrawerLayout.setDrawerListener(new DrawerListener() {
			
			@Override
			public void onDrawerStateChanged(int arg0) {}
			
			@Override
			public void onDrawerSlide(@NonNull View arg0, float arg1) {
				drawerSliding = true;
			}
			
			@Override
			public void onDrawerOpened(@NonNull View arg0) {
				drawerSliding = false;
				if(!openDrawerWithSwipe)
					mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);	
			}
			
			@Override
			public void onDrawerClosed(@NonNull View arg0) {
				drawerSliding = false;
				if(!openDrawerWithSwipe)
					mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			}
		});

		queueSlideFrame= findViewById(R.id.slide_queue_menu);
		LinearLayout mainView = (LinearLayout) getLayoutInflater().inflate(R.layout.queue_list, queueSlideFrame,false);
		queueSlideFrame.addView(mainView);
		
		queueList=(ListView)mainView.getChildAt(0);
		final FrameLayout emptyFl= mainView.findViewById(R.id.fl_empty);
		
		TextView emptyTxt= mainView.findViewById(R.id.txt_empty);
		emptyTxt.setOnTouchListener(new OnTouchListener() {
            private float startX;
            private float startY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    emptyFl.setPressed(true);
                    break;
                case MotionEvent.ACTION_MOVE: {
                	if(!RaspiUtils.isAClick(startX, event.getX(), startY, event.getY())){
                		emptyFl.setPressed(false);
                	}
                	break;
                } 	
                case MotionEvent.ACTION_UP: {
                    float endX = event.getX();
                    float endY = event.getY();
                    emptyFl.setPressed(false);
                    if (RaspiUtils.isAClick(startX, endX, startY, endY)) {
                    	v.performClick();
                    	SshConnection.emptyQueue(getApplicationContext());
                    	if(SshConnection.queueIndex>0){
                    		SshConnection.readQueue(QueueFragActivity.this, 400, true);	
                    	}else{
                    		SshConnection.readQueue(QueueFragActivity.this, 250, true);
                    	}
        				if(SshConnection.queueIndex>0 && ((OverflowMenuFragActivity)QueueFragActivity.this).
        						videoControlThread!=null){
        					((OverflowMenuFragActivity)QueueFragActivity.this).
        					videoControlThread.syncWithRaspi(1000, 1, false);
        					HttpFileStreamer.closeFileStreamer(getApplicationContext());
        				}	
                    }
                    break;
                }
                }
                return true;
            }
		});
		queueList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if(SshConnection.queueHasCast)
					HttpFileStreamer.startFileStreamerOutside(getApplicationContext());
				SubtitleOptions so = new SubtitleOptions(QueueFragActivity.this);
				SshConnection.playQueue(getApplicationContext(), position+1, so);
				if(((OverflowMenuFragActivity)QueueFragActivity.this).videoControlThread!=null)
					((OverflowMenuFragActivity)QueueFragActivity.this).
					videoControlThread.syncWithRaspi(1200, 3, false);
				SshConnection.readQueue(QueueFragActivity.this, 1000, true);
			}
		});
		queueList.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				SshConnection.removeFromQueue(getApplicationContext(), position+1);
				if(position+1==SshConnection.queueIndex){
					if(((OverflowMenuFragActivity)QueueFragActivity.this).videoControlThread!=null)
						((OverflowMenuFragActivity)QueueFragActivity.this).videoControlThread.syncWithRaspi(300, 1, false);
					SshConnection.readQueue(QueueFragActivity.this, 400, true);
				}else{
					SshConnection.readQueue(QueueFragActivity.this, 280, true);
				}
				
				return true;
			}
		});
		
		if(OverflowMenuFragActivity.lightTheme)
			highlightColor=0xFFD50000;
		else{	
			TypedValue typedValue = new TypedValue();
			getTheme().resolveAttribute(R.attr.highlight_color, typedValue, true);
			highlightColor=typedValue.data;
		}
		if(queueAdapter==null){
			queueAdapter = new QueueAdapter(QueueFragActivity.this);
			if(SshConnection.queueTitles!=null){
				updateQueue(false);
			}
		}
		queueList.setAdapter(queueAdapter);	
		
	}
	
	@Override
	public void onPause(){
		unregisterReceiver(queueUpdateReceiver);
		super.onPause();
	}
	
	
	@Override
	public void onResume(){
		super.onResume();
		defaultPlayOption=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getInt(Constants.PREF_DEFAULT_PLAY_OPTION, 0);
		hideFileExtensions=getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getBoolean(Constants.PREF_HIDE_MEDIA_EXTIONSIONS, false);
		openDrawerWithSwipe = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getBoolean(Constants.PREF_OPEN_QUEUE_SWIPE, true);
		miniQueueButton = getSharedPreferences(Constants.PREF_FILE_NAME, MODE_PRIVATE).
				getBoolean(Constants.PREF_MINI_QUEUE_BUTTON, false);

		if(SshConnection.queueTitles!=null){
			updateQueue(true);
		}
		
		if(openDrawerWithSwipe){
			if(queueAdapter==null|| queueAdapter.isEmpty()){
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			}else{
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
			}
		}else if(!mDrawerLayout.isDrawerOpen(queueSlideFrame)|| queueAdapter==null
				|| queueAdapter.isEmpty()){
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
		}
		
		registerReceiver(queueUpdateReceiver, new IntentFilter(QUEUE_UPDATE_ACTION));
		
		if(fab != null){
			if(miniQueueButton)
				fab.setSize(FloatingActionButton.SIZE_MINI);
			else
				fab.setSize(FloatingActionButton.SIZE_NORMAL);
			
			if(isDrawerLocked()){
				fab.setVisibility(View.GONE);
			}else{
				fab.setVisibility(View.VISIBLE);
			}
		}
	}
	
	public void updateQueue(boolean background){
		queueAdapter.clear();
		if(SshConnection.queueTitles!=null){
			for(String queueItem : SshConnection.queueTitles){
				queueAdapter.add(queueItem);
			}
			if (queueAdapter.isEmpty()){
				markedPlayingIndex=-1;
				updateQueueLock(true, background);
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
				if(mDrawerLayout.isDrawerOpen(queueSlideFrame)){
					mDrawerLayout.closeDrawers();
				}
			}else if(queueList!=null){
				updateQueueLock(false, background);
				if(openDrawerWithSwipe)
					mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
				
				if(markedPlayingIndex!=SshConnection.queueIndex){
					int child, position, firstPosition;
					TextView wantedView;
					if (markedPlayingIndex > 0){
						position=markedPlayingIndex - 1;
						firstPosition=queueList.getFirstVisiblePosition() - queueList.getHeaderViewsCount();
						child=position - firstPosition;

						if (child >= 0 && child < queueList.getChildCount()){
							wantedView=(TextView) queueList.getChildAt(child);
							if(OverflowMenuFragActivity.lightTheme){
								wantedView.setTextColor(Color.BLACK);
							}else{
								wantedView.setTextColor(0xFFF0F0F0);
							}
						}
					}
					position = SshConnection.queueIndex-1;
					firstPosition=queueList.getFirstVisiblePosition() - queueList.getHeaderViewsCount(); 
					child=position - firstPosition;
					if (child >= 0 && child < queueList.getChildCount()){
						wantedView=(TextView) queueList.getChildAt(child);
						wantedView.setTextColor(highlightColor);
						markedPlayingIndex=SshConnection.queueIndex;
					}else{
						markedPlayingIndex=-1;
					}
				}
			}
		}else{
			markedPlayingIndex=-1;
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			if(mDrawerLayout.isDrawerOpen(queueSlideFrame)){
				mDrawerLayout.closeDrawers();
			}
			updateQueueLock(true, background);
		}
		Log.d("Calling", "notify");
		queueAdapter.notifyDataSetChanged();
	}
	
	protected boolean isDrawerLocked() {
		return mDrawerLayout == null || queueSlideFrame == null ||
				queueAdapter == null || queueAdapter.isEmpty();
	}
	
	protected void openCloseQueue(){
		if(mDrawerLayout.isDrawerOpen(queueSlideFrame)){
			mDrawerLayout.closeDrawers();
		}else{
			mDrawerLayout.openDrawer(queueSlideFrame);
		}
	}
	
	protected void updateQueueLock(boolean locked, boolean background){
		if(fab == null)
			return;
		
		if(locked){
			fab.setVisibility(View.GONE);
		}else{
			if(!fab.isShown()){
				fab.setVisibility(View.VISIBLE);
				if(!background)
					fab.startAnimation(scaleAnimation);
			}
		}
	}
	
	private class QueueAdapter extends ArrayAdapter<String> {

		QueueAdapter(Context context) {
			super(context, 0);
		}

		@NonNull
		public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(R.layout.queue_list_item, parent, false);
			}
			if(position==SshConnection.queueIndex-1){
				markedPlayingIndex=SshConnection.queueIndex;
				((TextView)convertView).setTextColor(highlightColor);
			}else{
				if(OverflowMenuFragActivity.lightTheme){
					((TextView)convertView).setTextColor(0xF8222222);
				}else{
					((TextView)convertView).setTextColor(0xFFF0F0F0);
				}
			}
			if(hideFileExtensions){
				String item=getItem(position);
				if(item.contains(".")){
					for(String mediaExt: Constants.COMMON_MULTIMEDIA_FILE_EXTENSIONS){
						if(item.substring(item.lastIndexOf(".")).equalsIgnoreCase(mediaExt)){
							item=item.substring(0, item.lastIndexOf("."));
							break;
						}
					}
				}
				((TextView)convertView).setText(item);
			}else{
				((TextView)convertView).setText(getItem(position));
			}
			return convertView;
		}
	}
	
	public void configFloatingActionButton(){
		fab = contentFrame.findViewById(R.id.fab);
		if(fab == null){
			return;
		}
		fab.setColorPressed(lighten(fab.getColorNormal(), 1.3f));
		if(miniQueueButton)
			fab.setSize(FloatingActionButton.SIZE_MINI);
		
		if(!isDrawerLocked())
			fab.setVisibility(View.VISIBLE);
		fab.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				openCloseQueue();
			}
		});
	}
	
	public boolean isQueueOpen() {
		return !drawerSliding && mDrawerLayout.isDrawerOpen(queueSlideFrame);
	}
	
	@Override
	public void onBackPressed(){
		if(isQueueOpen()){
			mDrawerLayout.closeDrawers();
		}else{
			super.onBackPressed();
		}
	}
	
	
	@Override
	public void setContentView(int id){
		View content=getLayoutInflater().inflate(id, contentFrame,false);
		contentFrame.addView(content);
	}
	
	private int lighten(int color, float factor) {
	    int a = Color.alpha( color );
	    int r = Color.red( color );
	    int g = Color.green( color );
	    int b = Color.blue( color );

	    return Color.argb( a,
	            Math.min( (int)(r * factor), 255 ),
	            Math.min( (int)(g * factor), 255 ),
	            Math.min( (int)(b * factor), 255 ) );
	}
		

}
