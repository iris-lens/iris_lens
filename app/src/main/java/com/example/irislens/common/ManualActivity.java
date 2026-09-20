package com.example.irislens.common;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.irislens.R;

public class ManualActivity extends BaseSwipeActivity {

    private Handler handler = new Handler();
    private TextView[] sections;
    private int currentSection = 0;
    private ScrollView scrollView;
    private boolean soundPlayed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);

        currentFunctionalityIndex = Functionalities.MANUAL;
        scrollView = findViewById(R.id.manualScroll);

        sections = new TextView[]{
                findViewById(R.id.section_welcome),
                findViewById(R.id.section_intro),
                findViewById(R.id.section_accessibility),
                findViewById(R.id.section_navigation),
                findViewById(R.id.section_interaction),
                findViewById(R.id.section_function),
                findViewById(R.id.section_medicines),
                findViewById(R.id.section_support)
        };

        // 🔍 Verificar que ninguna sección sea null
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) {
                android.util.Log.e("ManualActivity", "⚠️ sections[" + i + "] es NULL — revisar ID en XML");
            }
        }

        AppVoiceManager.getInstance(this).initializeTTS();

        if (voiceManager != null) {
            voiceManager.stop();
        }

        setupTouchReading();
        startReadingFrom(0);
    }

    // =========================
    // 🔑 MÉTODO CENTRAL
    // Todo arranque de lectura pasa por aquí
    // =========================

    private void startReadingFrom(int fromIndex) {
        handler.removeCallbacksAndMessages(null);
        soundPlayed = false;
        currentSection = fromIndex;

        if (voiceManager != null) {
            voiceManager.stop();
        }

        if (isTalkBackEnabled()) {
            handler.postDelayed(this::readNextSectionTalkBack, 800);
        } else {
            handler.postDelayed(() -> {
                if (voiceManager != null && voiceManager.isTTSInitialized()) {
                    readNextSection();
                } else {
                    // TTS todavía no listo, reintentar
                    handler.postDelayed(() -> startReadingFrom(currentSection), 400);
                }
            }, 600);
        }
    }

    // =========================
    // 🔊 LECTURA CON TALKBACK
    // Usa voiceManager (NO announceForAccessibility)
    // para tener control real del fin → sonido confiable
    // =========================

    private void readNextSectionTalkBack() {
        if (currentSection >= sections.length) {
            playEndSoundOnce();
            return;
        }

        TextView section = sections[currentSection];
        if (section == null) {
            // Sección inválida, saltar
            currentSection++;
            readNextSectionTalkBack();
            return;
        }

        highlightSection(section);

        String text = section.getText().toString();
        if (voiceManager != null) {
            voiceManager.speak(text);
        }

        waitSpeechFinishesTalkBack();
    }

    private void waitSpeechFinishesTalkBack() {
        handler.postDelayed(() -> {
            if (voiceManager != null && voiceManager.isSpeaking()) {
                waitSpeechFinishesTalkBack();
            } else {
                currentSection++;
                readNextSectionTalkBack();
            }
        }, 300);
    }

    // =========================
    // 🔊 LECTURA SIN TALKBACK
    // =========================

    private void readNextSection() {
        if (currentSection >= sections.length) {
            playEndSoundOnce();
            return;
        }

        TextView section = sections[currentSection];
        if (section == null) {
            // Sección inválida, saltar
            currentSection++;
            readNextSection();
            return;
        }

        highlightSection(section);

        String text = section.getText().toString();
        if (voiceManager != null) {
            voiceManager.speak(text);
        }

        waitUntilSpeechFinishes();
    }

    private void waitUntilSpeechFinishes() {
        handler.postDelayed(() -> {
            if (voiceManager != null && voiceManager.isSpeaking()) {
                waitUntilSpeechFinishes();
            } else {
                currentSection++;
                readNextSection();
            }
        }, 300);
    }

    // =========================
    // 👆 TAP EN PÁRRAFO
    // Lee desde ese párrafo hasta el final → suena al terminar
    // =========================

    private void setupTouchReading() {
        for (int i = 0; i < sections.length; i++) {
            int index = i;
            TextView tv = sections[i];

            if (tv == null) continue; // protección extra

            tv.setFocusable(true);
            tv.setFocusableInTouchMode(true);

            // TalkBack: resaltar al enfocar con 1 toque
            tv.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void sendAccessibilityEvent(View host, int eventType) {
                    super.sendAccessibilityEvent(host, eventType);
                    if (eventType ==
                            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                        highlightSection(tv);
                    }
                }
            });

            // Click / doble tap con TalkBack:
            // arranca lectura desde este párrafo hacia abajo
            tv.setOnClickListener(v -> startReadingFrom(index));
        }
    }

    // =========================
    // 🎯 UI
    // =========================

    private void highlightSection(TextView section) {
        for (TextView tv : sections) {
            if (tv != null) tv.setBackgroundColor(0x00000000);
        }
        section.setBackgroundColor(0x33FFFFFF);
        section.sendAccessibilityEvent(
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
        );
        scrollView.post(() ->
                scrollView.smoothScrollTo(0, section.getTop())
        );
    }

    // =========================
    // 🔊 SONIDO FINAL
    // =========================

    private void playEndSoundOnce() {
        if (soundPlayed) return;
        soundPlayed = true;
        handler.postDelayed(this::playEndSound, 200);
    }

    private void playEndSound() {
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.captura);
        if (mediaPlayer != null) {
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
        }
    }

    // =========================
    // 🔍 DETECCIÓN TALKBACK
    // =========================

    private boolean isTalkBackEnabled() {
        AccessibilityManager am =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null || !am.isEnabled()) return false;
        for (AccessibilityServiceInfo service :
                am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_SPOKEN)) {
            if (service.getId().contains("talkback")) return true;
        }
        return false;
    }

    // =========================
    // 🎯 GESTOS
    // =========================

    @Override
    protected void onDoubleTapDetected() {
        handler.removeCallbacksAndMessages(null);
        soundPlayed = false;
        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    // =========================
    // 🔄 CICLO DE VIDA
    // =========================

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}