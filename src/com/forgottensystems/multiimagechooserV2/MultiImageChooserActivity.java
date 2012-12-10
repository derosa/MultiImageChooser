package com.forgottensystems.multiimagechooserV2;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class MultiImageChooserActivity extends FragmentActivity implements
		OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {
	private static final String TAG = "Collage";
	public static final String COL_WIDTH_KEY = "COL_WIDTH";
	public static final String FLURRY_EVENT_ADD_MULTIPLE_IMAGES = "Add multiple images";

	// El tamaño por defecto es 100 porque los thumbnails MICRO_KIND son de
	// 96x96
	private static final int DEFAULT_COLUMN_WIDTH = 120;

	public static final int NOLIMIT = -1;
	public static final String MAX_IMAGES_KEY = "MAX_IMAGES";

	private ImageAdapter ia;

	private Cursor imagecursor, actualimagecursor;
	private int image_column_index, actual_image_column_index;
	private int colWidth;

	private static final int CURSORLOADER_THUMBS = 0;
	private static final int CURSORLOADER_REAL = 1;

	private Set<String> fileNames = new HashSet<String>();

	private SparseBooleanArray checkStatus = new SparseBooleanArray();

	private Button acceptButton;
	private TextView freeLabel = null;
	private int maxImages;
	private boolean unlimitedImages = false;

	private GridView gridView;

	private ExecutorService executor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.multiselectorgrid);

		executor = Executors.newCachedThreadPool();
		fileNames.clear();

		maxImages = getIntent().getIntExtra(MAX_IMAGES_KEY, NOLIMIT);

		unlimitedImages = maxImages == NOLIMIT;
		if (!unlimitedImages) {
			freeLabel = (TextView) findViewById(R.id.label_images_left);
			freeLabel.setVisibility(View.VISIBLE);
			updateLabel();
		}

		acceptButton = (Button) findViewById(R.id.btn_select);
		acceptButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				selectClicked(v);
			}
		});

		colWidth = getIntent().getIntExtra(COL_WIDTH_KEY, DEFAULT_COLUMN_WIDTH);

		Display display = getWindowManager().getDefaultDisplay();
		@SuppressWarnings("deprecation")
		int width = display.getWidth();
		int testColWidth = width / 3;

		if (testColWidth > colWidth) {
			colWidth = width / 4;
		}

		int bgColor = getIntent().getIntExtra("BG_COLOR", Color.BLACK);

		gridView = (GridView) findViewById(R.id.gridview);
		gridView.setColumnWidth(colWidth);
		gridView.setOnItemClickListener(this);
		gridView.setBackgroundColor(bgColor);

		ia = new ImageAdapter(this);
		gridView.setAdapter(ia);

		LoaderManager.enableDebugLogging(false);
		getSupportLoaderManager().initLoader(CURSORLOADER_THUMBS, null, this);
		getSupportLoaderManager().initLoader(CURSORLOADER_REAL, null, this);

	}

	private void updateLabel() {
		if (freeLabel != null) {
			String text = String.format(getString(R.string.free_version_label),
					maxImages);
			freeLabel.setText(text);
			if (maxImages == 0) {
				freeLabel.setTextColor(Color.RED);
			} else {
				freeLabel.setTextColor(Color.WHITE);
			}
		}
	}

	public class ImageAdapter extends BaseAdapter {
		private final Matrix m = new Matrix();
		private Canvas canvas;
		private final Bitmap mPlaceHolderBitmap;

		public ImageAdapter(Context c) {
			Bitmap tmpHolderBitmap = BitmapFactory.decodeResource(
					getResources(), R.drawable.loading_icon);
			mPlaceHolderBitmap = Bitmap.createScaledBitmap(tmpHolderBitmap,
					colWidth, colWidth, false);
			if (tmpHolderBitmap != mPlaceHolderBitmap) {
				tmpHolderBitmap.recycle();
				tmpHolderBitmap = null;
			}
		}

		public int getCount() {
			if (imagecursor != null) {
				return imagecursor.getCount();
			} else {
				return 0;
			}
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView(int pos, View convertView, ViewGroup parent) {

			if (convertView == null) {
				convertView = new ImageView(MultiImageChooserActivity.this);
			}

			ImageView imageView = (ImageView) convertView;
			final int position = pos;

			imageView.setBackgroundColor(Color.TRANSPARENT);
			imageView.setImageBitmap(mPlaceHolderBitmap);

			if (!imagecursor.moveToPosition(position)) {
				return imageView;
			}

			if (image_column_index == -1) {
				return imageView;
			}

			final int id = imagecursor.getInt(image_column_index);

			imageView.setImageBitmap(mPlaceHolderBitmap);
			final WeakReference<ImageView> ivRef = new WeakReference<ImageView>(
					imageView);

			Runnable theRunnable = new Runnable() {

				private void setInvisible() {
					if (ivRef.get() == null) {
						return;
					} else {
						final ImageView iv = (ImageView) ivRef.get();
						if (iv == null) {
							return;
						} else {
							MultiImageChooserActivity.this
									.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											iv.setVisibility(View.GONE);
											iv.setClickable(false);
											iv.setEnabled(false);
										}
									});
						}
					}
				}

				@Override
				public void run() {
					Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
							getContentResolver(), id,
							MediaStore.Images.Thumbnails.MICRO_KIND, null);

					if (thumb == null) {
						// The original image no longer exists, hide the image
						// cell
						setInvisible();
						return;
					} else {
						final Bitmap mutable = Bitmap.createBitmap(colWidth,
								colWidth, thumb.getConfig());
						if (mutable == null) {
							setInvisible();
							return;
						}
						canvas = new Canvas(mutable);
						if (canvas == null) {
							setInvisible();
							return;
						}

						RectF src = new RectF(0, 0, thumb.getWidth(),
								thumb.getHeight());
						RectF dst = new RectF(0, 0, canvas.getWidth(),
								canvas.getHeight());
						m.reset();
						m.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
						canvas.drawBitmap(thumb, m, null);

						thumb.recycle();
						thumb = null;

						MultiImageChooserActivity.this
								.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										if (ivRef.get() == null) {
											return;
										} else {
											final ImageView iv = (ImageView) ivRef
													.get();
											if (iv == null) {
												return;
											} else {
												if (isChecked(position)) {
													iv.setBackgroundColor(Color.RED);
												}
												iv.setImageBitmap(mutable);
											}
										}
									}
								});
					}
				}
			};

			new Thread(theRunnable).start();

			return imageView;
		}
	}

	private String getImageName(int position) {
		actualimagecursor.moveToPosition(position);
		String name = null;

		try {
			name = actualimagecursor.getString(actual_image_column_index);
		} catch (Exception e) {
			return null;
		}
		return name;
	}

	private void setChecked(int position, boolean b) {
		checkStatus.put(position, b);
	}

	public boolean isChecked(int position) {
		boolean ret = checkStatus.get(position);
		return ret;
	}

	public void cancelClicked(View ignored) {
		setResult(RESULT_CANCELED);
		finish();
	}

	public void selectClicked(View ignored) {
		ArrayList<String> al = new ArrayList<String>();
		al.addAll(fileNames);
		Bundle res = new Bundle();
		res.putStringArrayList("MULTIPLEFILENAMES", al);
		if (imagecursor != null) {
			res.putInt("TOTALFILES", imagecursor.getCount());
		}
		Intent data = new Intent();
		data.putExtras(res);
		this.setResult(RESULT_OK, data);

		finish();
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position,
			long id) {
		String name = getImageName(position);

		if (name == null) {
			return;
		}
		boolean isChecked = !isChecked(position);
		// PhotoMix.Log("DAVID", "Posicion " + position + " isChecked: " +
		// isChecked);
		if (!unlimitedImages && maxImages == 0 && isChecked) {
			// PhotoMix.Log("DAVID", "Aquí no debería entrar...");
			isChecked = false;
		}

		if (isChecked) {
			// Solo se resta un slot si hemos introducido un
			// filename de verdad...
			if (fileNames.add(name)) {
				maxImages--;
				view.setBackgroundColor(Color.RED);
			}
		} else {
			if (fileNames.remove(name)) {
				// Solo incrementa los slots libres si hemos
				// "liberado" uno...
				maxImages++;
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}

		setChecked(position, isChecked);
		acceptButton.setEnabled(fileNames.size() != 0);
		updateLabel();

	}

	@Override
	public Loader<Cursor> onCreateLoader(int cursorID, Bundle arg1) {
		CursorLoader cl = null;

		ArrayList<String> img = new ArrayList<String>();
		switch (cursorID) {

		case CURSORLOADER_THUMBS:
			img.add(MediaStore.Images.Media._ID);
			break;
		case CURSORLOADER_REAL:
			img.add(MediaStore.Images.Thumbnails.DATA);
			break;
		default:
			break;
		}

		cl = new CursorLoader(MultiImageChooserActivity.this,
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				img.toArray(new String[img.size()]), null, null, null);
		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (cursor == null) {
			// NULL cursor. This usually means there's no image database yet....
			return;
		}

		switch (loader.getId()) {
		case CURSORLOADER_THUMBS:
			imagecursor = cursor;
			image_column_index = imagecursor
					.getColumnIndex(MediaStore.Images.Media._ID);
			ia.notifyDataSetChanged();
			break;
		case CURSORLOADER_REAL:
			actualimagecursor = cursor;
			actual_image_column_index = actualimagecursor
					.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			break;
		default:
			break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (loader.getId() == CURSORLOADER_THUMBS) {
			imagecursor = null;
		} else if (loader.getId() == CURSORLOADER_REAL) {
			actualimagecursor = null;
		}
	}
}