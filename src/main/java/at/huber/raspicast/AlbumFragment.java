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

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import at.huber.raspicast.utils.HttpFileStreamer;

public class AlbumFragment extends Fragment {

	private MediaListAdapter<Media> mediaListAdapter;
	private String[] sectionsArr = null;
	private int hexColor;

	public AlbumFragment() {
	}
	
	private static int darken(int color, float factor) {
	    int a = Color.alpha( color );
	    int r = Color.red( color );
	    int g = Color.green( color );
	    int b = Color.blue( color );

	    return Color.argb( a,
	            Math.max( (int)(r * factor), 0 ),
	            Math.max( (int)(g * factor), 0 ),
	            Math.max( (int)(b * factor), 0 ) );
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(OverflowMenuFragActivity.lightTheme){
			hexColor = getActivity().getResources().getColor(R.color.highlight_color_light);
		}else{	
			hexColor = getActivity().getResources().getColor(R.color.highlight_color_dark);
		}
		
		hexColor = darken(hexColor, 0.8f);
		
		mediaListAdapter=new MediaListAdapter<>(getActivity());

		ContentResolver cr=getActivity().getContentResolver();

		Uri uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection=MediaStore.Audio.Media.IS_MUSIC + " != 0";
		String sortOrder=MediaStore.Audio.Media.ALBUM + " COLLATE NOCASE ASC, "+ MediaStore.Audio.Media.TRACK + " ASC";
		String[] proj = { Audio.Media.TITLE, Audio.Media.ARTIST, Audio.Media.DATA, Audio.Media.ALBUM, Audio.Media.TRACK};
		Cursor cur=cr.query(uri, proj, selection, null, sortOrder);
		
		ArrayList<String> albumSections = new ArrayList<>();

		String lastAlbum ="";
		int lastAlbumPos = 0;
		if (cur != null){

			if (cur.getCount() > 0){

				while (cur.moveToNext()){
					String title=cur.getString(0);
					if(title == null)
						title = Constants.UNKNOWN_DATA;
					String artist=cur.getString(1);
					if(artist == null)
						artist = Constants.UNKNOWN_DATA;
					String path=cur.getString(2);
					String album=cur.getString(3);
					if(album == null)
						album = Constants.UNKNOWN_DATA;
					if(!lastAlbum.equals(album)){
						lastAlbum=album;
						albumSections.add(album);
						lastAlbumPos = mediaListAdapter.getCount();
						mediaListAdapter.add(new Media(album, artist));
					}
					int track = cur.getInt(4);
					mediaListAdapter.add(new Media(title, artist, path, track, album));
					String albArtist = mediaListAdapter.getItem(lastAlbumPos).artist;
					if(!albArtist.equals("") && !albArtist.equals(artist)){
						mediaListAdapter.remove(mediaListAdapter.getItem(lastAlbumPos));
						mediaListAdapter.insert(new Media(lastAlbum, ""), lastAlbumPos);
					}
				}
				sectionsArr= albumSections.toArray(new String[albumSections.size()]);

			}
			cur.close();
		}

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup rootView=(ViewGroup) inflater.inflate(R.layout.local_media, container, false);
		ListView lvLocalMedia = (ListView) rootView.findViewById(R.id.lv_local_media);
		lvLocalMedia.setAdapter(mediaListAdapter);
		lvLocalMedia.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				boolean queue = (QueueFragActivity.defaultPlayOption == 1);
				Media cast = mediaListAdapter.getItem(position);
				if (!cast.isAlbumTitle) {
					if (cast.artist.equals(MediaStore.UNKNOWN_STRING))
						HttpFileStreamer.streamFile(cast.path, getActivity(), cast.title, true, false, queue);
					else
						HttpFileStreamer.streamFile(cast.path, getActivity(), cast.artist + " - " + cast.title,
								true, false, queue);
				} else {
					ArrayList<String> titles = new ArrayList<>();
					ArrayList<String> paths = new ArrayList<>();
					for (int i = position + 1; i < mediaListAdapter.getCount(); i++) {
						Media qItem = mediaListAdapter.getItem(i);
						if (!qItem.isAlbumTitle) {
							titles.add(qItem.artist + " - " + qItem.title);
							paths.add(qItem.path);
						} else {
							break;
						}
					}
					HttpFileStreamer.streamFile(paths.toArray(new String[paths.size()]), getActivity(),
							titles.toArray(new String[titles.size()]), true, false, true);

				}
			}
		});
		
		lvLocalMedia.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				Media cast = mediaListAdapter.getItem(position);
				if (!cast.isAlbumTitle) {
					if (cast.artist.equals(MediaStore.UNKNOWN_STRING))
						((OverflowMenuFragActivity) getActivity()).showCastOptions(cast.path, cast.title);
					else
						((OverflowMenuFragActivity) getActivity()).showCastOptions(cast.path, cast.artist + " - " + cast.title);
				}
				return true;
			}
		});
		
		FragmentManager manager = getActivity().getSupportFragmentManager();
		FragmentTransaction trans = manager.beginTransaction();
		trans.disallowAddToBackStack();
		trans.commit();
		manager.popBackStack();
		return rootView;
	}

	private class Media {
		String title;
		String artist;
		String path;
		int track = -1;
		String album;
		boolean isAlbumTitle;

		public Media(String title, String artist, String path, int track, String album) {
			this.title=title;
			this.artist=artist;
			this.path=path;
			this.track=track;
			this.album=album;
			isAlbumTitle=false;
		}
		
		public Media(String album, String artist){
			this.album=album;
			this.artist=artist;
			isAlbumTitle=true;
		}
		
	}

	private class MediaListAdapter<T> extends ArrayAdapter<T> implements SectionIndexer {
		
		private Character[] sectionArrFirstChar = null;

		MediaListAdapter(Context context) {
			super(context, 0);
		}

		@NonNull
		@Override
		public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
			TextView textView;
			if (convertView == null){

				textView=(TextView) LayoutInflater.from(getContext()).inflate(R.layout.cast_list_item,
						parent, false);
				textView.setMaxLines(4);
				LinearLayout ll = new LinearLayout(getContext());
				ll.addView(textView);
				convertView=ll;

			}else{
				textView= (TextView) ((LinearLayout)convertView).getChildAt(0);
			}
			
			boolean albumTitle = mediaListAdapter.getItem(position).isAlbumTitle;
			String text;
			if(RaspiListActivity.lightTheme)
				textView.setTextColor(0xff111111);
			
			convertView.setBackgroundColor(0x00000000);
			
			if(albumTitle){
				text="<b>" + mediaListAdapter.getItem(position).album+ "</b><br/>"
						+ mediaListAdapter.getItem(position).artist;
				if(RaspiListActivity.lightTheme)
					textView.setTextColor(0xffffffff);
				
				convertView.setBackgroundColor(hexColor);
				
			}else if(mediaListAdapter.getItem(position).track>0)
				text= mediaListAdapter.getItem(position).title;
			else{
				text=mediaListAdapter.getItem(position).title;
			}
			
			textView.setText(Html.fromHtml(text));
			
			if(albumTitle){
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			}else{
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			}

			return convertView;

		}

		@Override
		public Object[] getSections() {
			if(sectionsArr == null)
				return null;
			
			if(sectionArrFirstChar==null){
				sectionArrFirstChar= new Character[sectionsArr.length];
				for(int i=0; i<sectionsArr.length; i++)
					sectionArrFirstChar[i] = Character.toUpperCase(sectionsArr[i].charAt(0));
			}
			return sectionArrFirstChar;
		}

		@Override
		public int getPositionForSection(int sectionIndex) {
			if(sectionsArr == null || sectionIndex >= sectionsArr.length)
				return 0;
			
			for(int i=1; i < this.getCount(); i++){
				String item=mediaListAdapter.getItem(i).album;
				if (item.equals(sectionsArr[sectionIndex]))
					return i-1;
			}
			return 0;
		}

		@Override
		public int getSectionForPosition(int position) {
			if(sectionsArr == null)
				return 0;
			
			String item=mediaListAdapter.getItem(position).album;
			for(int i=0; i < sectionsArr.length; i++){
				if (item.equals(sectionsArr[i])){
					return i;
				}
			}
			return 0;
		}

	}

}
