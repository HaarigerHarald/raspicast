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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.sql.Date;

import at.huber.raspicast.utils.HttpFileStreamer;
import at.huber.raspicast.utils.PixelTransformer;
import at.huber.raspicast.utils.RaspiUtils;

public class ImageFragment extends Fragment {
	
	//private static final DateFormat M_DATE_FORMAT = new SimpleDateFormat(" MMM yy ", Locale.getDefault());
	
	private GridView gridview;
	private int columnWidth;
	private int numCol;
	private ImageAdapter<Image> imageAdapter;
	private CastFragment castFrag;
	private int lLine;
	
	private OnTouchListener imOnTouchList = new OnTouchListener() {
		
		private float startX;
		private float startY;

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			ImageView view=(ImageView) v;
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				startX=event.getX();
				startY=event.getY();
				view.setColorFilter(0x80888888, PorterDuff.Mode.SRC_ATOP);
				view.invalidate();
				break;
			case MotionEvent.ACTION_MOVE:
				if (!RaspiUtils.isAClick(startX, event.getX(), startY, event.getY())){
					view.clearColorFilter();
					view.invalidate();
				}
				break;
			case MotionEvent.ACTION_UP:
				float endX=event.getX();
				float endY=event.getY();
				if (RaspiUtils.isAClick(startX, endX, startY, endY)){
					gridview.performItemClick(v, (Integer) v.getTag(), 0);
					v.performClick();
				}

			case MotionEvent.ACTION_CANCEL:
				view.clearColorFilter();
				view.invalidate();
			}
			return true;
		}
	};

	public ImageFragment() {

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		castFrag = (CastFragment) getParentFragment();
		ContentResolver cr = getActivity().getContentResolver();
		String sortOrder=MediaStore.Images.Media.DATE_TAKEN + " DESC";
		String[] proj = { MediaStore.Images.Media.TITLE, MediaStore.Images.Media._ID,
				MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_TAKEN };

		Cursor cc=cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, sortOrder);

		imageAdapter=new ImageAdapter<>(getActivity());

		if (cc != null){

			if (cc.getCount() > 0){
				while (cc.moveToNext()){
					String title=cc.getString(0);
					long id=cc.getLong(1);
					String path=cc.getString(2);
					Date d = new Date(cc.getLong(3)*1000);
					Image im=new Image(title, path, id, d);
					imageAdapter.add(im);
				}

			}
			cc.close();

		}

	}
	

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		gridview=(GridView) inflater.inflate(R.layout.image_fragment, container, false);
		gridview.setAdapter(imageAdapter);

		int availableWidth=(int) ((RaspiListActivity.mTwoPane) ? PixelTransformer.getDispWidth() * 0.69
				: PixelTransformer.getDispWidth());

		availableWidth -= PixelTransformer.getPixelsFromDp(9);

		int baseWidth=(RaspiListActivity.mTwoPane) ? PixelTransformer.getPixelsFromDp(140):
			PixelTransformer.getPixelsFromDp(116);
		int tooMuch=availableWidth % baseWidth;
		if (tooMuch < baseWidth / 2){
			numCol=availableWidth / baseWidth;
			baseWidth+=tooMuch / numCol;
			columnWidth=baseWidth;
		}else{
			numCol=availableWidth / baseWidth + 1;
			baseWidth=availableWidth / numCol;
			columnWidth=baseWidth;
		}
		
		columnWidth-= PixelTransformer.getPixelsFromDp(3f);
		gridview.setNumColumns(numCol);
		gridview.setVerticalSpacing(PixelTransformer.getPixelsFromDp(3f));
		gridview.setHorizontalSpacing(PixelTransformer.getPixelsFromDp(3f));
		
		lLine = imageAdapter.getCount();
		if(lLine%numCol == 0)
			lLine-=numCol;
		else
			lLine-=lLine%numCol;

		gridview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				HttpFileStreamer.streamFile(imageAdapter.getItem(position).path, getActivity(),
						imageAdapter.getItem(position).title, false, true, false);
			}
		});

		return gridview;
	}
	
	private class Image {
		String title;
		String path;
		Date date;
		long id;

		Image(String title, String path, long id, Date date) {
			this.title=title;
			this.path=path;
			this.id=id;
			this.date=date;
		}
	}

	private class ImageAdapter<T> extends ArrayAdapter<T>{
		
//		private String[] sectionsArr = null;

		ImageAdapter(Context context) {
			super(context, 0);
		}
		
		
//		@Override
//		public Object[] getSections() {
//			if(imageAdapter.getCount()<=0)
//				return null;
//			if(sectionsArr!=null)
//				return sectionsArr;
//			
//			ArrayList<String> sections=new ArrayList<String>();
//			
//			Date ld= imageAdapter.getItem(0).date;
//			
//			sections.add(M_DATE_FORMAT.format(ld));
//			
//			for(int i=1; i < imageAdapter.getCount(); i++){
//				Date d = imageAdapter.getItem(i).date;
//				 
//				if(d.getMonth()!=ld.getMonth() || d.getYear()!= ld.getYear()){
//					ld=d;
//					sections.add(M_DATE_FORMAT.format(ld));
//				}
//					
//			}
//			sectionsArr=sections.toArray(new String[sections.size()]);
//			return sectionsArr;
//		}
//
//		@Override
//		public int getPositionForSection(int sectionIndex) {
//			for(int i=1; i < this.getCount(); i++){
//				Date d=imageAdapter.getItem(i).date;
//				if (M_DATE_FORMAT.format(d).equals(sectionsArr[sectionIndex]))
//					return i;
//			}
//			return 0;
//		}
//
//		@Override
//		public int getSectionForPosition(int position) {
//			String d= M_DATE_FORMAT.format(imageAdapter.getItem(position).date);
//			for(int i=0; i < sectionsArr.length; i++){
//				if (d.equals(sectionsArr[i])){
//					return i;
//				}
//			}
//			return 0;
//		}

		@NonNull
		public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
			final ImageView imageView;
			if (convertView == null){
				imageView=(ImageView) LayoutInflater.from(getContext()).inflate(R.layout.image_view, parent,
						false);
				imageView.setOnTouchListener(imOnTouchList);
				imageView.getLayoutParams().width=columnWidth;
			}else{
				imageView=(ImageView) convertView;
				long id = imageAdapter.getItem((int)imageView.getTag()).id;
				MediaStore.Images.Thumbnails.cancelThumbnailRequest(getContext().getContentResolver(), id);
				imageView.clearColorFilter();
			}
			
			imageView.setTag(position);
			
			if(position < numCol){
				imageView.setPadding(0, PixelTransformer.getPixelsFromDp(6f), 0, 0);
				imageView.getLayoutParams().height = columnWidth+ PixelTransformer.getPixelsFromDp(6f);
			}else if(position >= lLine){
				imageView.setPadding(0, 0, 0, PixelTransformer.getPixelsFromDp(6f));
				imageView.getLayoutParams().height = columnWidth+ PixelTransformer.getPixelsFromDp(6f);
			}else{
				imageView.setPadding(0, 0, 0, 0);
				imageView.getLayoutParams().height = columnWidth;
			}
			
			final long id=imageAdapter.getItem(position).id;
			
			Bitmap bm = castFrag.getBitmapFromMemCache("i"+id);
			if(bm !=null){
				imageView.setImageBitmap(bm);
			}else{
				imageView.setImageDrawable(null);
				imageView.setAlpha(0f);
				if(VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
					imageView.setHasTransientState(true);
				}
				AsyncImageLoader asyncImageLoader = new AsyncImageLoader();
				asyncImageLoader.id = id;
				asyncImageLoader.refImageView = new WeakReference<>(imageView);
				asyncImageLoader.refContext = new WeakReference<>(getContext());
				asyncImageLoader.execute();
			}
			return imageView;
		}
	}

	private static class AsyncImageLoader extends AsyncTask<Void, Void, Bitmap>
	{
		long id;
		WeakReference<ImageView> refImageView;
		WeakReference<Context> refContext;

		@Override
		protected void onPostExecute(Bitmap b) {
			if (b != null){
				final ImageView imageView = refImageView.get();
				if (imageView == null)
					return;
				if(VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN){
					ObjectAnimator oa = ObjectAnimator.ofFloat(imageView, View.ALPHA, 1);
					oa.setDuration(300);
					oa.addListener(new AnimatorListenerAdapter() {

						@SuppressLint("NewApi")
						@Override
						public void onAnimationEnd(Animator animation) {
							imageView.setHasTransientState(false);
						}
					});
					imageView.setImageBitmap(b);
					oa.start();
				}else{
					imageView.setImageBitmap(b);
				}
			}
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			Context context = refContext.get();
			if (context == null)
				return null;
			try {
				Bitmap b = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), id,
						MediaStore.Images.Thumbnails.MINI_KIND, null);
				Bitmap b2;
				if (b == null)
					return null;
				if (b.getHeight() > b.getWidth()) {
					b2 = Bitmap.createBitmap(b, 0, (b.getHeight() - b.getWidth()) / 2, b.getWidth(),
							b.getWidth());
					b.recycle();
				} else if (b.getHeight() < b.getWidth()) {
					b2 = Bitmap.createBitmap(b, (b.getWidth() - b.getHeight()) / 2, 0, b.getHeight(),
							b.getHeight());
					b.recycle();
				} else {
					CastFragment.addBitmapToMemoryCache("i" + id, b);
					return b;
				}

				CastFragment.addBitmapToMemoryCache("i" + id, b2);
				return b2;
			}
			catch (Exception e)
			{
				return null;
			}
		}
	}

}
