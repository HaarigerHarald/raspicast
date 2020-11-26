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
package at.huber.raspicast.utils;

public class PixelTransformer {
	
	private static float density;
	private static int displayHeight;
	private static int displayWidth;
	
	public static void setDensity(float dens, int height, int width){
		density=dens;
		displayHeight=height;
		displayWidth=width;
	}
	
	public static int getDispWidth(){
		return displayWidth;
	}
	
	public static int getPixelsFromDp(float dp){
		float pixels= density*dp;
		return (int) (pixels+ 0.5f);
	}
	
	public static int getPixelsFromHeight(float percentage){
		return (int)(((float)displayHeight)*percentage);
	}
	
	public static int getPixelsFromWidth(float percentage){
		return (int)(((float)displayWidth)*percentage);
	}
	
	

}
