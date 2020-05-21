package it.runningexamples.recamera2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

//TODO rivedere e pulire codice
//TODO effetti preview android 10

public class MainActivity extends AppCompatActivity{
    private static final CaptureRequest.Key<Integer> EFFECT = CaptureRequest.CONTROL_EFFECT_MODE;
    private static final CaptureRequest.Key<Integer> AWB = CaptureRequest.CONTROL_AWB_MODE;
    private static final CaptureRequest.Key<Integer> NOISE = CaptureRequest.NOISE_REDUCTION_MODE;
    private static final CaptureRequest.Key<Integer> FLASH = CaptureRequest.FLASH_MODE;
    private static final int PERMISSION_ALL = 1;
    private static final String CAMERA_FRONT = "1";
    private static final String CAMERA_BACK = "0";
    String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    //Variabili hardware fotocamera
    private String cameraId; //ID numerico della fotocamera hardware
    protected CameraDevice cameraDevice; //Oggetto che rappresenta una fotocamera hardware, con tutte le sue informazioni
    protected CameraCaptureSession captureSession; //Sessione usata per passare una CaptureRequest al CameraDevice
    protected CameraCharacteristics characteristics; //Oggetto che contiene tutte le informazioni del cameraDevice, come ad esempio il cameraId
    protected CaptureRequest.Builder previewRequestBuilder; //Inizializza i campi di una CaptureRequest in uno dei template definiti nel CameraDevice. Questo è la request per chiedere di mostrare la preview della camera
    protected CaptureRequest.Builder pictureRequestBuilder;
    protected CameraManager manager; //Gestisce tutti i cameraDevice e permette di ottenere i cameraCharacteristics di ognuno
    protected ImageReader imageReader; //Visualizza le foto una volta scattate


    //Variabili immagine di output
    private static final int width = 1920;
    private static final int height = 1080;

    private Size imageDimension;
    private File folder,file;
    private TextureView textureView;
    TextureListener textureListener;
    public Holder holder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Controlla i permessi all'avvio.
        if (!CameraTools.hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            textureListener = new TextureListener();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "camera2photos");
        holder = new Holder();
        cameraId = CAMERA_BACK;// apre camera frontale all'avvio
    }

    public class Holder implements View.OnClickListener,PopupMenu.OnMenuItemClickListener{
        MediaPlayer shutterSound;
        Animation anim_button,anim_photo;
        int menuClicked;
        private ImageButton takePictureButton, btnFlip, btnGallery;
        private Button btnFlash, btnAwb, btnEffects, btnNoise;
        private PopupMenu menuEffects,menuNoise,menuColor,menuFlash;

        Holder(){
            anim_photo = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.photo_button_click);
            anim_button = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.bar_button_click);
            shutterSound = MediaPlayer.create(getApplicationContext(),R.raw.shutter);

            textureView = findViewById(R.id.texture);
            takePictureButton = findViewById(R.id.btn_takepicture);
            btnGallery = findViewById(R.id.btn_gallery);
            btnFlash = findViewById(R.id.btnFlash);
            btnNoise = findViewById(R.id.btnNoiseReduction);
            btnFlip = findViewById(R.id.btn_Flip);
            btnEffects = findViewById(R.id.btnEffects);
            btnAwb = findViewById(R.id.btnAwb);
            textureView.setSurfaceTextureListener(textureListener);
            menuEffects = createPopup(btnEffects,R.menu.effectmenu_popup);
            menuColor = createPopup(btnAwb,R.menu.awb_popup);
            menuNoise = createPopup(btnNoise,R.menu.noisereduction_popup);
            menuFlash = createPopup(btnFlash,R.menu.flashmenu_popup);

            btnAwb.setOnClickListener(this);
            btnEffects.setOnClickListener(this);
            btnNoise.setOnClickListener(this);
            btnFlip.setOnClickListener(this);
            btnGallery.setOnClickListener(this);
            takePictureButton.setOnClickListener(this);
            btnFlash.setOnClickListener(this);
            menuEffects.setOnMenuItemClickListener(this);
            menuColor.setOnMenuItemClickListener(this);
            menuNoise.setOnMenuItemClickListener(this);
            menuFlash.setOnMenuItemClickListener(this);
        }

        @Override
        public void onClick(View v) {

            if (v.getId() == R.id.btn_takepicture) {
                shutterSound.start();
                takePictureButton.startAnimation(anim_photo);
                takePicture();
            }
            if (v.getId() == R.id.btn_Flip) {
                switchCamera();
                btnFlip.startAnimation(anim_button);
            }
            if (v.getId() == R.id.btn_gallery) {
                btnGallery.startAnimation(anim_button);
                File[] allFiles = folder.listFiles();
                if (allFiles == null){
                    Toast.makeText(MainActivity.this, "Non hai scattato nessuna foto", Toast.LENGTH_SHORT).show();
                }else {
                    new SingleMediaScanner(MainActivity.this, allFiles[folder.listFiles().length - 1]);
                }
            }
            if (v.getId() == R.id.btnFlash) {
                btnFlash.startAnimation(anim_button);
                menuFlash.show();
                menuClicked = R.id.btnFlash;
            }
            if (v.getId() == R.id.btnAwb) {
                btnAwb.startAnimation(anim_button);
                menuColor.show();
                menuClicked = R.id.btnAwb;

            }
            if (v.getId() == R.id.btnEffects) {
                btnEffects.startAnimation(anim_button);
                menuEffects.show();
                menuClicked = R.id.btnEffects;
            }
            if (v.getId() == R.id.btnNoiseReduction) {
                btnNoise.startAnimation(anim_button);
                menuNoise.show();
                menuClicked = R.id.btnNoiseReduction;
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()){
                case R.id.negative:
                    btnEffects.setBackgroundResource(R.drawable.effects_active);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE);
                    return true;
                case R.id.aqua:
                    btnEffects.setBackgroundResource(R.drawable.effects_active);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_AQUA);
                    return true;
                case R.id.solarize:
                    btnEffects.setBackgroundResource(R.drawable.effects_active);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE);
                    return true;
                case R.id.sepia:
                    btnEffects.setBackgroundResource(R.drawable.effects_active);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_SEPIA);
                    return true;
                case R.id.posterize:
                    btnEffects.setBackgroundResource(R.drawable.effects_active);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE);
                    return true;
                case R.id.effectOff:
                    btnEffects.setBackgroundResource(R.drawable.effects);
                    setCameraPreference(EFFECT,CameraMetadata.CONTROL_EFFECT_MODE_OFF);
                    return true;
                case R.id.autoAwb:
                    btnAwb.setBackgroundResource(R.drawable.awb);
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_AUTO);
                    return true;
                case R.id.incandescentAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.fluorescentAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.daylightAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.cloudyAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.twilightAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_TWILIGHT);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.shadeAwb:
                    setCameraPreference(AWB,CameraMetadata.CONTROL_AWB_MODE_SHADE);
                    btnAwb.setBackgroundResource(R.drawable.awb_active);
                    return true;
                case R.id.offNoise:
                    setCameraPreference(NOISE,CameraMetadata.NOISE_REDUCTION_MODE_OFF);
                    btnNoise.setBackgroundResource(R.drawable.noise);
                    return true;
                case R.id.fastNoise:
                    setCameraPreference(NOISE,CameraMetadata.NOISE_REDUCTION_MODE_FAST);
                    btnNoise.setBackgroundResource(R.drawable.noise_active);
                    return true;
                case R.id.highNoise:
                    setCameraPreference(NOISE,CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                    btnNoise.setBackgroundResource(R.drawable.noise_active);
                    return true;
                case R.id.minNoise:
                    setCameraPreference(NOISE,CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL);
                    btnNoise.setBackgroundResource(R.drawable.noise_active);
                    return true;
                case R.id.zeroNoise:
                    setCameraPreference(NOISE,CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
                    btnNoise.setBackgroundResource(R.drawable.noise_active);
                    return true;
                case R.id.noFlash:
                    setCameraPreference(FLASH,CameraMetadata.FLASH_MODE_OFF);
                    btnFlash.setBackgroundResource(R.drawable.flash);
                    return true;
                case R.id.yesFlash:
                    pictureRequestBuilder.set(FLASH,CameraMetadata.FLASH_MODE_SINGLE);
                    btnFlash.setBackgroundResource(R.drawable.flash_active);
                    return true;
                case R.id.torchFlash:
                    setCameraPreference(FLASH,CameraMetadata.FLASH_MODE_TORCH);
                    btnFlash.setBackgroundResource(R.drawable.flash_active);
                    return true;
            }
            return false;
        }
    }


    //Inizializzo i callback e l'imageListener
    private final CameraStateCallback cameraStateCallback = new CameraStateCallback();
    SessionStateCallback sessionStateCallback = new SessionStateCallback();
    CaptureCallback captureCallback = new CaptureCallback();
    ImageListener imageListener = new ImageListener();


    public void switchCamera() {
        Animation animSwitch = AnimationUtils.loadAnimation(this,R.anim.switch_camera);
        resetButtons();
        if (cameraId.equals(CAMERA_FRONT)) {
            cameraId = CAMERA_BACK;
            closeCamera();
            openCamera();
            textureView.startAnimation(animSwitch);

        } else if (cameraId.equals(CAMERA_BACK)) {
            cameraId = CAMERA_FRONT;
            closeCamera();
            openCamera();
            textureView.startAnimation(animSwitch);

        }
    }

    protected void takePicture() { //Metodo che scatta e salva una foto            // throws CameraAccessException imposta da getOrientantion()
        if (null == cameraDevice) {
            return;
        }
        try {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            pictureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraTools.getJpegOrientation(characteristics, rotation));
            file = CameraTools.createFilePhoto(folder); //Chiama il metodo per creare il file dove salvare la foto
            imageReader.setOnImageAvailableListener(imageListener, null);
            captureSession.capture(pictureRequestBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() { //Crea la surface dove verrà mostrata l'anteprima tramite updatePreview, e crea la CaptureSession dove passare le richieste.
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture(); //Prende la SurfaceTexture della textureView (dove viene visualizzata l'anteprima nel .xml)
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);
            Surface readerSurface = imageReader.getSurface();
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, readerSurface), sessionStateCallback, null);
            previewRequestBuilder.addTarget(previewSurface);
            pictureRequestBuilder.addTarget(readerSurface);
        } catch (CameraAccessException e) {
            finish();
            e.printStackTrace();
        }
    }


    /*  Il controllo per il permesso CAMERA non viene fatto esplicitamente
        su manager.openCamera e questo causa un Warning. Poiché la nostra app
        viene chiusa immediatamente se non si danno i permessi, non è necessario controllarli
        nuovamente, per cui ignoro il warning.
     */
    @SuppressLint("MissingPermission")
    private void openCamera() {
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            characteristics = manager.getCameraCharacteristics(cameraId); //Ottengo le caratteristiche della fotocamera attuale tramite il suo cameraId
            //Ottiene una StreamConfigurationMap dalle carachteristics della camera. Contiene tutte le configurazioni di streaming disponibili supportate dal cameraDevice;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(ImageFormat.JPEG)[1];
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2); //Instanzia l'imageReader per leggere e mostrare le foto scattate
            manager.openCamera(cameraId, cameraStateCallback, null); //lancia il metodo openCamera che apre la connessione con il cameraDevice avente id cameraId
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    protected void updatePreview() {
        //Metodo usato per aggiornare la preview a seguito di un cambiamento, inviando una nuova setRepeatingRequest al CameraDevice.
        try {
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null); //Richiede l'acquisizione ripetuta infinita di immagini da questa sessione. Permette di aggiornare continuamente l'immagine vista sulla surface.
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    public PopupMenu createPopup(View v, int menu) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(menu, popup.getMenu());
        return popup;
    }

    private void setCameraPreference(CaptureRequest.Key<Integer> key,int value){
        previewRequestBuilder.set(key,value);
        pictureRequestBuilder.set(key,value);
        updatePreview();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ALL:
                if (grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED)) {
                    Toast.makeText(MainActivity.this, getString(R.string.givePermission), Toast.LENGTH_LONG).show();
                    finish();
                }
        }
        textureListener = new TextureListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetButtons();
        if (textureView.isAvailable() && CameraTools.hasPermissions(MainActivity.this, PERMISSIONS)) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    protected void resetButtons(){
        holder.btnEffects.setBackgroundResource(R.drawable.effects);
        holder.btnAwb.setBackgroundResource(R.drawable.awb);
        holder.btnFlash.setBackgroundResource(R.drawable.flash);
        holder.btnNoise.setBackgroundResource(R.drawable.noise);
    }

    class TextureListener implements TextureView.SurfaceTextureListener {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {openCamera();}
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {return false;}
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    }

    class ImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Callback che viene chiamata quando è disponibile una nuova immagine nell'imageReader, ovvero quando viene scattata una foto
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                CameraTools.save(bytes,file); //chiama il metodo di basso livello che salva i bytes nel File creato
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, getString(R.string.save) + file, Toast.LENGTH_SHORT).show();
        }
    }

    class SessionStateCallback extends CameraCaptureSession.StateCallback {
        //Callback usata per ottenere lo stato della CameraCaptureSession

        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (null == cameraDevice) {
                return;
            }
            //Se il cameraDevice è pronto sessione è pronta, viene mostrata l'anteprima a schermo tramite il metodo updatePreview();
            captureSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            Toast.makeText(MainActivity.this, getString(R.string.configuration), Toast.LENGTH_SHORT).show();
        }
    }

    class CameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(CameraDevice camera) { //Chiamato quando viene aperta la Camera
            cameraDevice = camera;
            try {
                previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); //Creo il builder della CaptureRequest da passare alla sessione per mostrare la preview della camera
                pictureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            createCameraPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {}
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {}
    }

    public class SingleMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient { //Mostra una foto scattata tramite la galleria
        private MediaScannerConnection mMs;
        private File mFile;

        public SingleMediaScanner(Context context, File f) {
            mFile = f;
            mMs = new MediaScannerConnection(context, this);
            mMs.connect();
        }

        public void onMediaScannerConnected() {
            mMs.scanFile(mFile.getAbsolutePath(), null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            startActivity(intent);
            mMs.disconnect();
        }

    }

}


