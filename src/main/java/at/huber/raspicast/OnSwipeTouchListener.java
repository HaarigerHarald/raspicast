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

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import at.huber.raspicast.utils.PixelTransformer;

public class OnSwipeTouchListener implements OnTouchListener {
	
	private static final int SWIPE_DISTANCE_THRESHOLD_DP = 60;
    private static final int SWIPE_VELOCITY_THRESHOLD_DP = 180;
    
    private static int swipe_distance_threshold;
    private static int swipe_velocity_threshold;

    private final GestureDetector gestureDetector;

    OnSwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
        swipe_distance_threshold=PixelTransformer.getPixelsFromDp(SWIPE_DISTANCE_THRESHOLD_DP);
        swipe_velocity_threshold=PixelTransformer.getPixelsFromDp(SWIPE_VELOCITY_THRESHOLD_DP);
    }

    public void onSwipeLeft() {
    }

    public void onSwipeRight() {
    }

    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends SimpleOnGestureListener {

        
        
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float distanceX = e2.getX() - e1.getX();
            float distanceY = e2.getY() - e1.getY();
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > swipe_distance_threshold && Math.abs(velocityX) > swipe_velocity_threshold) {
                if (distanceX > 0)
                    onSwipeRight();
                else
                    onSwipeLeft();
                return true;
            }
            return false;
        }
    }
}
