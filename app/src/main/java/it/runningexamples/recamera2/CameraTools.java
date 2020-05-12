/** Questa classe contiene tutti i metodi usati nella MainActivity che non fanno direttamente utilizzo di parametri contenuti nell'activity.
 *  Viene utilizzata per snellire il codice */


package it.runningexamples.recamera2;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CameraTools {

    // JPEG_ORIENTATION -> Se LENS_FACING_FRONT l'immagine jpeg deve essere ruotata rispetto all'orientamento della fotocamera.
    // Dipende dalle caratteristiche del dispositivo. https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_ORIENTATION
    public static int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;
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

    public static boolean hasPermissions(Context context, String[] permissions) {
        boolean garanted = true;
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    garanted = false;
                }
            }
        }
        return garanted;
    }

    public static File createFilePhoto(File folder) {
        // Crea il File dove salvare la foto scattata
        if (!folder.exists()) {
            folder.mkdirs(); //crea la directory delle foto se questa non esiste
        }
        int serialNum = folder.listFiles().length;
        String path = Environment.getExternalStorageDirectory() + "/camera2photos/pic" + serialNum;
        File file = new File(path + ".jpg");
        int num = 1;
        while (file.exists()) {
            String N = "(" + num + ")";
            file = new File(path + N + ".jpg");
            num++;
        }
        return file;
    }

    public static void save(byte[] bytes, File file) throws IOException {
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
}
