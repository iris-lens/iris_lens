package com.example.irislens.common;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;

public class PermissionManager {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private static final String PREFS = "permission_prefs";
    private static final String KEY_DENIED_COUNT = "camera_denied_count";

    /**
     * Solicita permiso de cámara
     */
    public void getPermissions(Activity activity) {

        // Ya tiene permiso
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(
                PREFS,
                Activity.MODE_PRIVATE
        );

        int deniedCount =
                prefs.getInt(KEY_DENIED_COUNT, 0);

        /**
         * Primeros intentos:
         * mostrar popup normal dentro de la app
         */
        if (deniedCount < 2) {

            activity.requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST
            );

            return;
        }

        /**
         * Android probablemente ya bloqueó el popup:
         * abrir configuraciones
         */
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts(
                        "package",
                        activity.getPackageName(),
                        null
                )
        );

        activity.startActivity(intent);
    }

    /**
     * Resultado del permiso
     */
    public void handlePermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults,
            Activity activity
    ) {

        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(
                PREFS,
                Activity.MODE_PRIVATE
        );

        // Permiso concedido
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Resetear contador
            prefs.edit()
                    .putInt(KEY_DENIED_COUNT, 0)
                    .apply();

            return;
        }

        // Incrementar cantidad de rechazos
        int deniedCount =
                prefs.getInt(KEY_DENIED_COUNT, 0);

        prefs.edit()
                .putInt(KEY_DENIED_COUNT, deniedCount + 1)
                .apply();

        // Cerrar app
        activity.finishAffinity();
    }
}