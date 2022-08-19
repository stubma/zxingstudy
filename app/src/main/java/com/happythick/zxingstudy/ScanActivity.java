package com.happythick.zxingstudy;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class ScanActivity extends AppCompatActivity implements BarcodeCallback {
	// request code
	private static final int REQ_SELECT_PIC = 1000;
	private static final int REQ_SELECT_PIC_KITKAT = 1001;

	// views
	private BarcodeView mBarcodeView;
	private ImageView mBoxTL;
	private ImageView mBoxTR;
	private ImageView mBoxBL;
	private ImageView mBoxBR;
	private CropRectView mCropView;
	private ImageView mScanLineView;
	private Button mToggleTorchButton;

	// for scan line animation
	private ObjectAnimator mScanLineAnimator;

	// barcode scanned, it can be issue id, or a http url which contains issue id
	// the url format must be http://weshow.ipub360.net:40000/app?issueid=xxx
	private String mBarcode;

	// ui flag
	private boolean mTorchOn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// set content
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);

		// find views
		mBoxTL = (ImageView)findViewById(R.id.box_tl);
		mBoxTR = (ImageView)findViewById(R.id.box_tr);
		mBoxBL = (ImageView)findViewById(R.id.box_bl);
		mBoxBR = (ImageView)findViewById(R.id.box_br);
		mCropView = (CropRectView)findViewById(R.id.crop);
		mScanLineView = (ImageView)findViewById(R.id.scanline);
		mToggleTorchButton = (Button)findViewById(R.id.toggle_torch);

		// set toolbar
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		toolbar.setNavigationIcon(R.drawable.btn_back);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// finish
				finish();
			}
		});

		// album button
		findViewById(R.id.album).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("image/jpeg");
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
					startActivityForResult(intent, REQ_SELECT_PIC_KITKAT);
				} else {
					startActivityForResult(intent, REQ_SELECT_PIC);
				}
			}
		});

		// scanner view
		mBarcodeView = (BarcodeView)findViewById(R.id.zxing_barcode_surface);
		mBarcodeView.decodeSingle(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// resume barcode
		mBarcodeView.resume();

		// start moving scan line
		if(mScanLineAnimator != null && !mScanLineAnimator.isStarted()) {
			mScanLineAnimator.start();
		}
	}

	@Override
	protected void onPause() {
		// pause barcode
		mBarcodeView.pause();

		// stop moving scan line
		mScanLineAnimator.end();

		// call super
		super.onPause();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		// re-layout box corner
		int cropSize = mCropView.getWidth() * 2 / 3;
		int cornerSize = mBoxBL.getWidth();
		int delta = (cropSize - cornerSize) / 2;
		int extraOffset = cropSize / 4;
		mBoxTL.setTranslationX(-delta);
		mBoxTL.setTranslationY(-delta - extraOffset);
		mBoxTR.setTranslationX(delta);
		mBoxTR.setTranslationY(-delta - extraOffset);
		mBoxBL.setTranslationX(-delta);
		mBoxBL.setTranslationY(delta - extraOffset);
		mBoxBR.setTranslationX(delta);
		mBoxBR.setTranslationY(delta - extraOffset);

		// create scan line animator
		if(mScanLineAnimator == null) {
			mScanLineAnimator = ObjectAnimator.ofFloat(mScanLineView, "translationY", -cropSize / 2 - extraOffset, cropSize / 2 - extraOffset);
			mScanLineAnimator.setDuration(3000);
			mScanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
		}
		if(!mScanLineAnimator.isStarted()) {
			mScanLineAnimator.start();
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch(requestCode) {
			case REQ_SELECT_PIC_KITKAT:
			case REQ_SELECT_PIC:
				if(resultCode == RESULT_OK) {
					// show progress
					ProgressHUD progressHUD = ProgressHUD.show(this, getString(R.string.processing), true, false, null);

					// first get bitmap
					Uri imageFileUri = intent.getData();
					Bitmap bitmap = null;
					try {
						bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageFileUri);
					} catch(IOException e) {
					}

					// decode
					if(bitmap == null) {
						// if bitmap is null, show error dialog
						progressHUD.dismiss();
						new AlertDialog.Builder(this)
							.setTitle(R.string.title_error)
							.setMessage(R.string.message_cannot_scan_barcode)
							.setPositiveButton(android.R.string.yes, null)
							.setIcon(android.R.drawable.ic_dialog_alert)
							.show();
					} else {
						// if not null, try to decode it
						try {
							// decode
							Map<DecodeHintType, String> hints = new HashMap<>();
							hints.put(DecodeHintType.CHARACTER_SET, "utf-8");
							int width = bitmap.getWidth();
							int height = bitmap.getHeight();
							int[] pixels = new int[width * height];
							bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
							RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
							BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(source));
							QRCodeReader reader = new QRCodeReader();
							Result result = reader.decode(bb, hints);

							// get barcode
							mBarcode = result.getText();
							if(BuildConfig.DEBUG) {
								Log.d("weshow", "get scanned bar string: " + mBarcode);
							}

							// if barcode is a http url, get uuid from query string
							if(mBarcode.indexOf("http") != -1) {
								Uri uri = Uri.parse(mBarcode);
								String issueId = uri.getQueryParameter("issueid");
								if(issueId != null) {
									mBarcode = issueId;
								}
							}

							// remove this progress
							progressHUD.dismiss();
						} catch (Throwable e) {
							// if bitmap is null, show error dialog
							progressHUD.dismiss();
							new AlertDialog.Builder(this)
								.setTitle(R.string.title_error)
								.setMessage(e.getLocalizedMessage())
								.setPositiveButton(android.R.string.yes, null)
								.setIcon(android.R.drawable.ic_dialog_alert)
								.show();
						} finally {
							bitmap.recycle();
						}
					}
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, intent);
				break;
		}
	}

	public void onToggleTorchClicked(View v) {
		if(mTorchOn) {
			mBarcodeView.setTorch(false);
			mTorchOn = false;
			mToggleTorchButton.setText(R.string.lights_on);
		} else {
			mBarcodeView.setTorch(true);
			mTorchOn = true;
			mToggleTorchButton.setText(R.string.lights_off);
		}
	}

	@Override
	public void barcodeResult(BarcodeResult result) {
		// get barcode
		mBarcode = result.getText();
		if(BuildConfig.DEBUG) {
			Log.d("weshow", "get scanned bar string: " + mBarcode);
		}

		// if barcode is a http url, get uuid from query string
		if(mBarcode.indexOf("http") != -1) {
			Uri uri = Uri.parse(mBarcode);
			String issueId = uri.getQueryParameter("issueid");
			if(issueId != null) {
				mBarcode = issueId;
			}
		}
	}

	@Override
	public void possibleResultPoints(List<ResultPoint> resultPoints) {
	}
}
