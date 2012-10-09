package com.forgottensystems.multiimagechooserV2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.multiselectorgrid);
		fileNames.clear();

		maxImages = getIntent().getIntExtra(MAX_IMAGES_KEY, NOLIMIT);

		unlimitedImages = maxImages == NOLIMIT;
		if (!unlimitedImages) {
			freeLabel = (TextView) findViewById(R.id.label_images_left);
			freeLabel.setVisibility(View.VISIBLE);
			updateLabel();
		}

		acceptButton = (Button) findViewById(R.id.btn_select);

		colWidth = getIntent().getIntExtra(COL_WIDTH_KEY, DEFAULT_COLUMN_WIDTH);
		int bgColor = getIntent().getIntExtra("BG_COLOR", Color.BLACK);

		gridView = (GridView) findViewById(R.id.gridview);
		gridView.setColumnWidth(colWidth);
		gridView.setOnItemClickListener(this);
		gridView.setBackgroundColor(bgColor);

		ia = new ImageAdapter(this);
		gridView.setAdapter(ia);

		LoaderManager.enableDebugLogging(true);
		getSupportLoaderManager().initLoader(CURSORLOADER_THUMBS, null, this);
		getSupportLoaderManager().initLoader(CURSORLOADER_REAL, null, this);
		
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		double sdAvailSize = (double)stat.getAvailableBlocks()
		                   * (double)stat.getBlockSize();
		//One binary gigabyte equals 1,073,741,824 bytes.
		double gigaAvailable = sdAvailSize / 1073741824 * 1024;

		Log.d(TAG, "Free space in MB: " + gigaAvailable);
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
		// private final int BASE_SIZE = 128;
		private final Matrix m = new Matrix();
		private Canvas canvas;

		public ImageAdapter(Context c) {
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
		public View getView(int position, View convertView, ViewGroup parent) {

			ImageView imageView = null;

			if (convertView == null) {
				imageView = new ImageView(MultiImageChooserActivity.this);
			} else {
				imageView = (ImageView) convertView;
			}
			imageView.setBackgroundColor(Color.TRANSPARENT);
			imageView.setImageBitmap(null);

			if (!imagecursor.moveToPosition(position)) {
				Log.d("Collage", "moveToPosition was false");
				return imageView;
			}

			if (image_column_index == -1) {
				Log.d("Collage", "image_column_index == -1!");
				return imageView;
			}

			int id = imagecursor.getInt(image_column_index);

			Bitmap eso = MediaStore.Images.Thumbnails.getThumbnail(
					getContentResolver(), id,
					MediaStore.Images.Thumbnails.MICRO_KIND, null);

			if (eso == null) {
				// Ya no existe la imagen original de la miniatura, se oculta
				// esta vista:
				imageView.setVisibility(View.GONE);
				imageView.setClickable(false);
				imageView.setEnabled(false);
				return imageView;
			}

			Bitmap mutable = Bitmap.createBitmap(colWidth, colWidth,
					eso.getConfig());

			canvas = new Canvas(mutable);

			RectF src = new RectF(0, 0, eso.getWidth(), eso.getHeight());
			RectF dst = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
			m.reset();
			m.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
			canvas.drawBitmap(eso, m, null);

			eso.recycle();
			eso = null;

			if (isChecked(position)) {
				imageView.setBackgroundColor(Color.RED);
			}

			imageView.setImageBitmap(mutable);

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
		Log.d(TAG, "Images: " + al.toString());

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
		// Log.d("DAVID", "Posicion " + position + " isChecked: " + isChecked);
		if (!unlimitedImages && maxImages == 0 && isChecked) {
			// Log.d("DAVID", "Aquí no debería entrar...");
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
		Log.d(TAG, "onCreateLoader: " + cursorID);
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

		Log.d(TAG, "Schema: " + MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

		cl = new CursorLoader(MultiImageChooserActivity.this,
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				img.toArray(new String[img.size()]), null, null, null);
		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		Log.d(TAG, "onLoadFinished: " + loader.getId());
		Log.d(TAG,
				"Is the SD card mounted? "
						+ Environment.MEDIA_MOUNTED.equals(Environment
								.getExternalStorageState()));
		if (cursor != null) {
			Log.d(TAG,
					"Cursor: " + cursor.toString() + "; items: "
							+ cursor.getCount());
		} else {
			Log.d(TAG,
					"NULL cursor. This usually means there's no image database yet....");
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
		Log.d(TAG, "onLoaderReset: " + loader.getId());
		if (loader.getId() == CURSORLOADER_THUMBS) {
			imagecursor = null;
		} else if (loader.getId() == CURSORLOADER_REAL) {
			actualimagecursor = null;
		}
	}
}