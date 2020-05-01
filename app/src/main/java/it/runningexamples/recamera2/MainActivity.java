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
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

//TODO sistemare immagine capovolta fotocamera frontale
//TODO Naming convention
//TODO dimension.xml (?)
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int PERMISSION_ALL = 1;
    private static final String CAMERA_FRONT = "1";
    private static final String CAMERA_BACK = "0";
    String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private static final String TAG = "AndroidCameraApi";
    private static final String TAG2 = "Permessi";
    private ImageButton takePictureButton,btnFlip,btnSettings;
    private TextureView textureView;
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
    private static final int width = 640;
    private static final int height = 480;

    private Size imageDimension;
    private File file; //File dove andrà salvata la foto scattata
    private boolean flashMode; //??

    TextureListener textureListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Controlla i permessi all'avvio.
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            textureListener = new TextureListener();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = findViewById(R.id.btn_takepicture);
        btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(this);
        takePictureButton.setOnClickListener(this);
        btnFlip = findViewById(R.id.btn_Flip);
        btnFlip.setOnClickListener(this);
        cameraId = CAMERA_BACK;         // apre camera frontale all'avvio
    }

    //Inizializzo i callback e l'imageListener
    private final CameraStateCallback cameraStateCallback = new CameraStateCallback();
    SessionStateCallback sessionStateCallback = new SessionStateCallback();
    CaptureCallback captureCallback = new CaptureCallback();
    ImageListener imageListener = new ImageListener();


   public void switchCamera() {
        if (cameraId.equals(CAMERA_FRONT)) {
            cameraId = CAMERA_BACK;
            closeCamera();
            openCamera();

        } else if (cameraId.equals(CAMERA_BACK)) {
            cameraId = CAMERA_FRONT;
            closeCamera();
            openCamera();
        }
    }

    // JPEG_ORIENTATION -> Se LENS_FACING_FRONT l'immagine jpeg deve essere ruotata rispetto all'orientamento della fotocamera.
    // Dipende dalle caratteristiche del dispositivo. https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_ORIENTATION
    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // se si tratta della fotocamera frontale -> ruota
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calcolo orientamento rispetto alla fotocamera
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    protected void takePicture() { //Metodo che scatta e salva una foto            // throws CameraAccessException imposta da getOrientantion()
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE); //Instanzio un camera manager, castando come Camera Manager il servizio CAMERA di android

        try {
            characteristics = manager.getCameraCharacteristics(cameraDevice.getId()); //Prendo le caratteristiche della camera tramite il suo ID (??)
            pictureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            pictureRequestBuilder.addTarget(imageReader.getSurface());
            pictureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO); //Non voglio controllare i metadati
            pictureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(characteristics, rotation));
            createFilePhoto(); //Chiama il metodo per creare il file dove salvare la foto
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
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface,readerSurface), sessionStateCallback, null);
            Log.v ("CFG","Created Capture Session");
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); //Creo il builder della CaptureRequest da passare alla sessione per mostrare la preview della camera
            previewRequestBuilder.addTarget(previewSurface);
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
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            characteristics = manager.getCameraCharacteristics(cameraId); //Ottengo le caratteristiche della fotocamera attuale tramite il suo cameraId
            //Ottiene una StreamConfigurationMap dalle carachteristics della camera. contiene tutte le configurazioni di streaming disponibili supportate dal cameraDevice;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(ImageFormat.JPEG)[1];
            Log.d(TAG, "imageDimension "+ imageDimension);
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1); //Instanzia l'imageReader per leggere e mostrare le foto scattate
            manager.openCamera(cameraId, cameraStateCallback, null); //lancia il metodo openCamera che apre la connessione con il cameraDevice avente id cameraId
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    protected void updatePreview() {
        //Aggiorna costantemente la Preview, fornendo l'anteprima istante per istante della ripresa
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE); //Builder della richiesta di preview

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
        Log.e(TAG, "onResume");
        if (textureView.isAvailable() && hasPermissions(MainActivity.this, PERMISSIONS)) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_takepicture) {
            Animation anim_zoomIn = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.zoom_in);
            Animation anim_zoomOut = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.zoom_out);
            takePictureButton.startAnimation(anim_zoomIn);
            takePictureButton.startAnimation(anim_zoomOut);
            takePicture();
        }
        if (v.getId() == R.id.btn_Flip) {
            switchCamera();
        }
        if (v.getId() == R.id.btn_settings){
            startActivity(new Intent(MainActivity.this,ActivityPop.class));
        }
    }



    public static boolean hasPermissions(Context context, String[] permissions) {
        boolean garanted = true;
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                Log.d(TAG2, "Check "+permission);
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG2, permission + " Not garanted");
                    garanted = false;
                    Log.d(TAG2, "Garanted = "+garanted);
                }
            }
        }
        return garanted;
    }

    private void createFilePhoto() {
        // Crea il File dove salvare la foto scattata
        File folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "camera2photos");
        if (!folder.exists()) {
            folder.mkdirs(); //crea la directory delle foto se questa non esiste
        }
        String lastPic = Integer.toString(folder.listFiles().length);
        String path = Environment.getExternalStorageDirectory() + "/camera2photos/pic" + lastPic;
        file = new File(path + ".jpg");
        int num = 1;
        while (file.exists()) {
            String N = "(" + num + ")";
            file = new File(path + N + ".jpg");
            num++;
        }
    }

    private void save(byte[] bytes) throws IOException {
        // Metodo di basso livello che salva il file attraverso uno Stream di bytes
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } finally {
            if (null != output) {
                output.close();
            }
        }
    }

    class TextureListener implements TextureView.SurfaceTextureListener {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

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
                save(bytes); //chiama il metodo di basso livello che salva i bytes nel File creato
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
            createCameraPreview();
        }
    }

    class SessionStateCallback extends CameraCaptureSession.StateCallback {
        //Callback usata per ottenere lo stato della CameraCaptureSession

        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.v ("CFG","ONCONFIGURED");
            //Se la camera è già chiusa retrurn
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
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {}
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {}
    }
}


