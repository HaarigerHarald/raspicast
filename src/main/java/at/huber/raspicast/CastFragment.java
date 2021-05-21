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

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.collection.LruCache;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;

public class CastFragment extends Fragment{
	
	protected enum Mode{
		VIDEOS(0), MUSIC(1), ALBUMS(2);
		
		private final int value;
		
		Mode(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return value;
	    }
	}

	private static final int PAGES[] = { R.string.videos, R.string.music, R.string.albums, R.string.images };

	private static final LruCache<String, Bitmap> mMemoryCache;
    
    static{
    	final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

	    // Use 1/8th of the available memory for this memory cache.
	    final int cacheSize = maxMemory / 8;

	    mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
	        @Override
	        protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
	            return bitmap.getByteCount() / 1024;
	        }
	    };
    }
    
    public CastFragment(){	
    }
    
    protected static void addBitmapToMemoryCache(String key, Bitmap bitmap) {
    	synchronized (mMemoryCache) {
    	     if (mMemoryCache.get(key) == null) {
    	    	 mMemoryCache.put(key, bitmap);
    	     
    	}}
	}

	protected Bitmap getBitmapFromMemCache(String key) {
	    return mMemoryCache.get(key);
	}
	
	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState){
        ((QueueFragActivity)getActivity()).configFloatingActionButton();
        super.onViewCreated(view, savedInstanceState);
	}
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.cast_fragment, container, false);

		ViewPager mPager = rootView.findViewById(R.id.pager);
		PagerAdapter mPagerAdapter = new ScreenSlidePagerAdapter(getChildFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        
        PagerSlidingTabStrip tabs = rootView.findViewById(R.id.tabs);
        tabs.setViewPager(mPager);
//        if(RaspiListActivity.lightTheme){
//        	tabs.setTextColorStateListResource(R.color.tab_text);
//        }
        return rootView;
    }


    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
        	switch(position){
        	case 0:
        		return new LocalVideoFragment();
        	case 1:
        		return new LocalMusicFragment();
        	case 2:
        		return new AlbumFragment();
        	case 3:
        		return new ImageFragment();
        	}
			return null;
        }

        @Override
        public int getCount() {
            return PAGES.length;
        }
        
        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getString(PAGES[position]);
        }
    }

}
