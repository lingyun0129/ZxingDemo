package com.karics.library.zxing.android;

import com.example.zxingdemo.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.karics.library.zxing.android.BeepManager;
import com.karics.library.zxing.android.CaptureActivityHandler;
import com.karics.library.zxing.android.FinishListener;
import com.karics.library.zxing.android.InactivityTimer;
import com.karics.library.zxing.android.IntentSource;
import com.karics.library.zxing.camera.CameraManager;
import com.karics.library.zxing.view.ViewfinderView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * 杩欎釜activity鎵撳紑鐩告満锛屽湪鍚庡彴绾跨▼鍋氬父瑙勭殑鎵弿锛涘畠缁樺埗浜嗕竴涓粨鏋渧iew鏉ュ府鍔╂纭湴鏄剧ず鏉″舰鐮侊紝鍦ㄦ壂鎻忕殑鏃跺�欐樉绀哄弽棣堜俊鎭紝
 * 鐒跺悗鍦ㄦ壂鎻忔垚鍔熺殑鏃跺�欒鐩栨壂鎻忕粨鏋�
 * 
 */
public final class CaptureActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();
	
	// 鐩告満鎺у埗
	private boolean isOpen = false;
	Parameters params;
    private Camera camera;
	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private IntentSource source;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	// 鐢甸噺鎺у埗
	private InactivityTimer inactivityTimer;
	// 澹伴煶銆侀渿鍔ㄦ帶鍒�
	private BeepManager beepManager;

	private ImageButton imageButton_back;
	
	private ImageButton imageButton_flashlight;

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	/**
	 * OnCreate涓垵濮嬪寲涓�浜涜緟鍔╃被锛屽InactivityTimer锛堜紤鐪狅級銆丅eep锛堝０闊筹級浠ュ強AmbientLight锛堥棯鍏夌伅锛�
	 */
	//上次记录的时间戳
    long lastRecordTime = System.currentTimeMillis();
 
    //上次记录的索引
    int darkIndex = 0;
    //一个历史记录的数组，255是代表亮度最大值
    long[] darkList = new long[]{255, 255, 255, 255};
    //扫描间隔
    int waitScanTime = 300;
 
    //亮度低的阀值
    int darkValue = 60;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		// 淇濇寔Activity澶勪簬鍞ら啋鐘舵��
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);

		hasSurface = false;

		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

		imageButton_back = (ImageButton) findViewById(R.id.capture_imageview_back);
		imageButton_back.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		imageButton_flashlight = (ImageButton) findViewById(R.id.capture_imageview_flashlight);
		imageButton_flashlight.setOnClickListener(new View.OnClickListener() 
		{
			@Override
			public void onClick(View v) 
			{
				if(!isOpen)
				{
					openLight();
				}
				else
				{
					closeLight();
				}
			}
		});		
	}
	
	private void openLight()
	{
		//camera = CameraManager.getCamera();
        params = camera.getParameters();
        params.setFlashMode(Parameters.FLASH_MODE_TORCH);
        camera.setParameters(params);
        camera.startPreview(); // 开始亮灯
        isOpen = true;
	}
	
	private void closeLight()
	{
		params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
		camera.setParameters(params); // 关掉亮灯
        isOpen = false;
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		// CameraManager蹇呴』鍦ㄨ繖閲屽垵濮嬪寲锛岃�屼笉鏄湪onCreate()涓��
		// 杩欐槸蹇呴』鐨勶紝鍥犱负褰撴垜浠涓�娆¤繘鍏ユ椂闇�瑕佹樉绀哄府鍔╅〉锛屾垜浠苟涓嶆兂鎵撳紑Camera,娴嬮噺灞忓箷澶у皬
		// 褰撴壂鎻忔鐨勫昂瀵镐笉姝ｇ‘鏃朵細鍑虹幇bug
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		handler = null;

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// activity鍦╬aused鏃朵絾涓嶄細stopped,鍥犳surface浠嶆棫瀛樺湪锛�
			// surfaceCreated()涓嶄細璋冪敤锛屽洜姝ゅ湪杩欓噷鍒濆鍖朿amera
			initCamera(surfaceHolder);
		} else {
			// 閲嶇疆callback锛岀瓑寰卻urfaceCreated()鏉ュ垵濮嬪寲camera
			surfaceHolder.addCallback(this);
		}

		beepManager.updatePrefs();
		inactivityTimer.onResume();

		source = IntentSource.NONE;
		decodeFormats = null;
		characterSet = null;
		
	}

	@Override
	protected void onPause() {
		if(camera!=null){
			camera.setPreviewCallback(null);
			camera=null;
		}
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
		camera = CameraManager.getCamera();
		if(camera!=null)
			camera.setPreviewCallback(new Camera.PreviewCallback() {
	            @Override
	            public void onPreviewFrame(byte[] data, Camera camera) {
	                long currentTime = System.currentTimeMillis();
	                if (currentTime - lastRecordTime < waitScanTime) {
	                    return;
	                }
	                lastRecordTime = currentTime;
	 
	                int width = camera.getParameters().getPreviewSize().width;
	                int height = camera.getParameters().getPreviewSize().height;
	                //像素点的总亮度
	                long pixelLightCount = 0L;
	                //像素点的总数
	                long pixeCount = width * height;
	                //采集步长，因为没有必要每个像素点都采集，可以跨一段采集一个，减少计算负担，必须大于等于1。
	                int step = 10;
	                //data.length - allCount * 1.5f的目的是判断图像格式是不是YUV420格式，只有是这种格式才相等
	                //因为int整形与float浮点直接比较会出问题，所以这么比
	                if (Math.abs(data.length - pixeCount * 1.5f) < 0.00001f) {
	                    for (int i = 0; i < pixeCount; i += step) {
	                        //如果直接加是不行的，因为data[i]记录的是色值并不是数值，byte的范围是+127到—128，
	                        // 而亮度FFFFFF是11111111是-127，所以这里需要先转为无符号unsigned long参考Byte.toUnsignedLong()
	                        pixelLightCount += ((long) data[i]) & 0xffL;
	                    }
	                    //平均亮度
	                    long cameraLight = pixelLightCount / (pixeCount / step);
	                    //更新历史记录
	                    int lightSize = darkList.length;
	                    darkList[darkIndex = darkIndex % lightSize] = cameraLight;
	                    darkIndex++;
	                    boolean isDarkEnv = true;
	                    //判断在时间范围waitScanTime * lightSize内是不是亮度过暗
	                    for (int i = 0; i < lightSize; i++) {
	                        if (darkList[i] > darkValue) {
	                            isDarkEnv = false;
	                        }
	                    }
	                    Log.e(TAG, "摄像头环境亮度为 ： " + cameraLight);
	                    if (!isFinishing()) {
	                        //亮度过暗就提醒
	                        if (isDarkEnv) {
	                        	imageButton_flashlight.setVisibility(View.VISIBLE);
	                        } else {
	                        	if(!isOpen)
	                        	imageButton_flashlight.setVisibility(View.GONE);
	                        }
	                    }
	                }
	            }
	        });
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * 鎵弿鎴愬姛锛屽鐞嗗弽棣堜俊鎭�
	 * 
	 * @param rawResult
	 * @param barcode
	 * @param scaleFactor
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
		inactivityTimer.onActivity();

		boolean fromLiveScan = barcode != null;
		//杩欓噷澶勭悊瑙ｇ爜瀹屾垚鍚庣殑缁撴灉锛屾澶勫皢鍙傛暟鍥炰紶鍒癆ctivity澶勭悊
		if (fromLiveScan) {
			beepManager.playBeepSoundAndVibrate();

//			Toast.makeText(this, "鎵弿鎴愬姛", Toast.LENGTH_SHORT).show();

			Intent intent = getIntent();
			intent.putExtra("codedContent", rawResult.getText());
			intent.putExtra("codedBitmap", barcode);
			setResult(RESULT_OK, intent);
			finish();
		}

	}

	/**
	 * 鍒濆鍖朇amera
	 * 
	 * @param surfaceHolder
	 */
	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			return;
		}
		try {
			// 鎵撳紑Camera纭欢璁惧
			cameraManager.openDriver(surfaceHolder);
			// 鍒涘缓涓�涓猦andler鏉ユ墦寮�棰勮锛屽苟鎶涘嚭涓�涓繍琛屾椂寮傚父
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats,
						decodeHints, characterSet, cameraManager);
			}
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	/**
	 * 鏄剧ず搴曞眰閿欒淇℃伅骞堕��鍑哄簲鐢�
	 */
	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

}
