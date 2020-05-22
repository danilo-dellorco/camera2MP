/* Questa classe contiene tutti i metodi usati nella MainActivity che non fanno direttamente utilizzo di parametri contenuti nell'activity.
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
    public static int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Se cameraDevice in uso è la camera frontale, l'immagine jpeg deve essere ruotata rispetto all'orientamento della fotocamera.
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calcolo orientamento rispetto alla fotocamera
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    //Controllo permessi
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

    // Creazione path file di salvataggio
    public static File createFilePhoto(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();            //crea la directory delle foto se questa non esiste
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
