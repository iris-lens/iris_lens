package com.example.irislens.common;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.irislens.R;

public class WelcomeActivity extends BaseSwipeActivity {

    private static final String PREFS_NAME = "irislens_prefs";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";

    private TextView tvWelcome;
    private boolean mensajeBienvenidaReproducido = false;
    private boolean flujoInicializado = false;   // ← evita doble ejecución en onResume
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        currentFunctionalityIndex = Functionalities.WELCOME;
        tvWelcome = findViewById(R.id.tvWelcome);

        AppVoiceManager.getInstance(this).initializeTTS();

        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Si ya inicializamos el flujo en este ciclo de vida, no repetir
        if (flujoInicializado) return;

        // Esperar a que los permisos estén resueltos y el TTS listo antes de arrancar
        handler.postDelayed(this::iniciarFlujo, 400);
    }

    /**
     * Punto de entrada del flujo: verifica permisos y luego muestra disclaimer o bienvenida.
     * Se llama desde onResume con un pequeño delay para dar tiempo al TTS y a los permisos.
     */
    private void iniciarFlujo() {
        // Si la cámara aún no tiene permiso, esperar — BaseSwipeActivity lo pedirá
        // y onResume volverá a dispararse cuando el usuario responda
        if (!tieneCamaraPermiso()) return;

        // A partir de aquí el flujo ya está resuelto, no repetir
        flujoInicializado = true;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean disclaimerAceptado = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false);

        if (!disclaimerAceptado) {
            showDisclaimer(prefs);
        } else {
            welcome_message();
        }
    }

    /**
     * Verifica si el permiso de cámara ya fue concedido.
     */
    private boolean tieneCamaraPermiso() {
        return checkSelfPermission(android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Muestra el diálogo de aviso legal la primera vez que se abre la app.
     * Si el usuario acepta, guarda la preferencia y continúa.
     * Si rechaza, cierra la app.
     */
    private void showDisclaimer(SharedPreferences prefs) {
        String disclaimerTitle = getString(R.string.disclaimer_title);
        String disclaimerText = getString(R.string.disclaimer_text);

        // Leer el disclaimer por voz cuando el TTS esté listo
        handler.postDelayed(() -> {
            if (voiceManager != null && voiceManager.isTTSInitialized()) {
                voiceManager.speak(disclaimerTitle + ". " + disclaimerText);
            } else {
                handler.postDelayed(() -> {
                    if (voiceManager != null) {
                        voiceManager.speak(disclaimerTitle + ". " + disclaimerText);
                    }
                }, 800);
            }
        }, 600);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(disclaimerTitle)
                .setMessage(disclaimerText)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.disclaimer_accept), (d, which) -> {
                    if (voiceManager != null) voiceManager.stop();
                    prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply();
                    welcome_message();
                })
                .setNegativeButton(getString(R.string.disclaimer_exit), (d, which) -> {
                    if (voiceManager != null) voiceManager.stop();
                    finishAffinity();
                })
                .create();

        dialog.show();

        // Texto blanco
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        // Fondo azul
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        // Separación entre los botones
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).getLayoutParams();

        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setLayoutParams(params);
    }

    /**
     * Espera a que el TTS esté inicializado para reproducir el mensaje de bienvenida.
     */
    private void welcome_message() {
        handler.postDelayed(() -> {
            if (!mensajeBienvenidaReproducido) {
                if (voiceManager != null && voiceManager.isTTSInitialized()) {
                    voiceManager.speak("Iris Lens le da la bienvenida. " +
                            "Deslice hacia la izquierda o derecha para cambiar de funcionalidad. " +
                            "Use doble toque para detener la voz.");
                    mensajeBienvenidaReproducido = true;
                } else {
                    welcome_message();
                }
            }
        }, 600);
    }

    @Override
    protected void onDoubleTapDetected() {
        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Resetear para que onResume vuelva a evaluar si los permisos cambiaron
        flujoInicializado = false;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}