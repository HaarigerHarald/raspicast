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
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;

import at.huber.raspicast.CastFragment.Mode;
import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PixelTransformer;

public class LocalMediaFragment extends Fragment {

	protected static final String MODE_PASS="mode";

	private MediaListAdapter<Media> mediaListAdapter;
	private Mode mode;
	private CastFragment castFrag;

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putInt(MODE_PASS, mode.getValue());
	}

	public LocalMediaFragment() {

	}

	@SuppressLint("ValidFragment")
	public LocalMediaFragment(Mode mode) {
		this.mode=mode;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null){
			int modeNum=savedInstanceState.getInt(MODE_PASS, 0);
			mode=Mode.values()[modeNum];
		}
		
		castFrag = (CastFragment) getParentFragment();

		mediaListAdapter=new MediaListAdapter<>(getActivity());
		if (mode == CastFragment.Mode.MUSIC){
			ContentResolver cr=getActivity().getContentResolver();

			Uri uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String selection=MediaStore.Audio.Media.IS_MUSIC + " != 0";
			String sortOrder=MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
			String[] proj = { Audio.Media.TITLE, Audio.Media.ARTIST, Audio.Media.DATA};
			Cursor cur=cr.query(uri, proj, selection, null, sortOrder);

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
						mediaListAdapter.add(new Media(title, artist, path, -1));
					}

				}
				cur.close();
			}
		}else if (mode == CastFragment.Mode.VIDEOS){
			ContentResolver cr = getActivity().getContentResolver();

			Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
			String[] proj = {MediaStore.Video.Media._ID,  MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA};

			String sortOrder = MediaStore.Video.Media.TITLE + " COLLATE NOCASE ASC";
			Cursor cur = cr.query(uri, proj, null, null, sortOrder);

			if (cur != null){
				if (cur.getCount() > 0){
					while (cur.moveToNext()){
						long id=cur.getLong(0);
						String title=cur.getString(1);
						if(title == null)
							title = Constants.UNKNOWN_DATA;

						String path=cur.getString(2);
						mediaListAdapter.add(new Media(title, null, path, id));
					}

				}
				cur.close();
			}

		}

	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup rootView=(ViewGroup) inflater.inflate(R.layout.local_media, container, false);
		ListView lvLocalMedia = rootView.findViewById(R.id.lv_local_media);
		lvLocalMedia.setAdapter(mediaListAdapter);
		lvLocalMedia.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				boolean queue = (QueueFragActivity.defaultPlayOption == 1);
				Media cast = mediaListAdapter.getItem(position);
				if (mode == Mode.MUSIC) {
					if (cast.artist.equals(MediaStore.UNKNOWN_STRING))
						HttpFileStreamer.streamFile(cast.path, getActivity(),
								cast.title, true, false, queue);
					else
						HttpFileStreamer.streamFile(cast.path, getActivity()
								, cast.artist + " - " + cast.title, true, false, queue);
				} else
					HttpFileStreamer.streamFile(cast.path, getActivity()
							, cast.title, false, false, queue);
			}
		});
		
		lvLocalMedia.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				Media cast = mediaListAdapter.getItem(position);
				if (mode == Mode.MUSIC) {
					if (cast.artist.equals(MediaStore.UNKNOWN_STRING))
						((OverflowMenuFragActivity) getActivity()).showCastOptions(cast.path, cast.title);
					else
						((OverflowMenuFragActivity) getActivity()).showCastOptions(cast.path, cast.artist + " - " + cast.title);
				} else
					((OverflowMenuFragActivity) getActivity()).showCastOptions(cast.path, cast.title);
				return true;
			}
		});
		FragmentManager manager=getActivity().getSupportFragmentManager();
		FragmentTransaction trans=manager.beginTransaction();
		trans.disallowAddToBackStack();
		trans.commit();
		manager.popBackStack();
		return rootView;
	}

	private class Media {
		String title;
		String artist;
		String path;
		long id;

		public Media(String title, String artist, String path, long id) {
			this.title=title;
			this.artist=artist;
			this.path=path;
			this.id=id;
		}
	}

	private class MediaListAdapter<T> extends ArrayAdapter<T> implements SectionIndexer {

		private Character[] sectionsArr;

		MediaListAdapter(Context context) {
			super(context, 0);
		}

		@NonNull
		@Override
		public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
			TextView textView;
			if (convertView == null){
				if (mode == CastFragment.Mode.MUSIC){
					textView = (TextView)LayoutInflater.from(getContext()).inflate(R.layout.cast_list_item, parent,
							false);
					textView.setMaxLines(4);
					textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
					convertView=textView;
				}else{
					convertView=LayoutInflater.from(getContext()).inflate(R.layout.video_cast_item, parent,
							false);
					
					textView = convertView.findViewById(R.id.tv_video_cast);
					
					textView.setMaxLines(4);
					textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
					textView.setPadding(PixelTransformer.getPixelsFromDp(8f), 0, 0, 0);
				}
			}else{
				if (mode == CastFragment.Mode.MUSIC)
					textView=(TextView) convertView;
				else
					textView= convertView.findViewById(R.id.tv_video_cast);
			}
			
			if (mode == Mode.MUSIC){
				String text;
				if(mediaListAdapter.getItem(position).artist.equals(MediaStore.UNKNOWN_STRING))
					text="<b>" + mediaListAdapter.getItem(position).title + "</b>";
				else
					text="<b>" + mediaListAdapter.getItem(position).title + "</b><br/>"
							+ mediaListAdapter.getItem(position).artist;
				textView.setText(Html.fromHtml(text));
			}else{
				textView.setText(mediaListAdapter.getItem(position).title);
				final long id=mediaListAdapter.getItem(position).id;

				final ImageView imView= convertView.findViewById(R.id.iv_video_cast);
				
				Bitmap bm =  castFrag.getBitmapFromMemCache("v"+id);
				if(bm != null){
					imView.setImageBitmap(bm);
				}else{
					new AsyncTask<Void, Void, Bitmap>() {
	
						@Override
						protected void onPostExecute(Bitmap b) {
							if (b != null && imView != null){
								imView.setImageBitmap(b);
								castFrag.addBitmapToMemoryCache("v"+id, b);
							}
							
						}
	
						@Override
						protected Bitmap doInBackground(Void... params) {
							return MediaStore.Video.Thumbnails.getThumbnail(getContext().getContentResolver(),
									id, MediaStore.Video.Thumbnails.MICRO_KIND, null);
						}
					}.execute();
				}
			}
			return convertView;

		}

		@Override
		public Object[] getSections() {
			if(mediaListAdapter.getCount()<=0)
				return null;
			if(sectionsArr!=null)
				return sectionsArr;
			
			ArrayList<Character> sections=new ArrayList<>();
			
			if (mediaListAdapter.getItem(0).title.isEmpty() ||
					!Character.isLetter(mediaListAdapter.getItem(0).title.charAt(0))){
				sections.add('#');
			}

			int firstAlphIndex=0;
			while (firstAlphIndex < mediaListAdapter.getCount() && (
					mediaListAdapter.getItem(firstAlphIndex).title.isEmpty() ||
					!Character.isLetter(mediaListAdapter.getItem(firstAlphIndex).title.charAt(0)))){
				firstAlphIndex++;
			}
			if(firstAlphIndex < mediaListAdapter.getCount()){
				
				sections.add(Character.toUpperCase(mediaListAdapter.getItem(firstAlphIndex).title.charAt(0)));
				for(int i=firstAlphIndex; i < mediaListAdapter.getCount(); i++){
					if (Character.toUpperCase(mediaListAdapter.getItem(i).title.charAt(0)) != sections
							.get(sections.size() - 1)){
						sections.add(Character.toUpperCase(mediaListAdapter.getItem(i).title.charAt(0)));
					}
				}
			}
			sectionsArr= sections.toArray(new Character[sections.size()]);
			return sectionsArr;
		}

		@Override
		public int getPositionForSection(int sectionIndex) {
			if(sectionsArr == null || sectionIndex >= sectionsArr.length)
				return 0;
				
			for(int i=1; i < mediaListAdapter.getCount(); i++){
				String item=mediaListAdapter.getItem(i).title.toUpperCase();
				if (item.charAt(0) == sectionsArr[sectionIndex])
					return i;
			}
			return 0;
		}

		@Override
		public int getSectionForPosition(int position) {
			if(sectionsArr == null)
				return 0;
			
			String item=mediaListAdapter.getItem(position).title.toUpperCase();
			if (item.isEmpty())
				return 0;

			for(int i=0; i < sectionsArr.length; i++){
				if (item.charAt(0) == sectionsArr[i]){
					return i;
				}
			}
			return 0;
		}
	}

}
