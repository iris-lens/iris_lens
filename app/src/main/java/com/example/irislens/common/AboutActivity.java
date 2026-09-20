package com.example.irislens.common;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.irislens.R;

public class AboutActivity extends BaseSwipeActivity {

    private Handler handler = new Handler();
    private TextView[] sections;
    private int currentSection = 0;
    private ScrollView scrollView;
    private boolean soundPlayed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        currentFunctionalityIndex = Functionalities.ABOUT;

        scrollView = findViewById(R.id.aboutScroll);

        sections = new TextView[]{
                findViewById(R.id.section_title),
                findViewById(R.id.section_version),
                findViewById(R.id.section_description),
                findViewById(R.id.section_source_title),
                findViewById(R.id.section_source),
                findViewById(R.id.section_github),
                findViewById(R.id.section_contact_title),
                findViewById(R.id.section_contact),
                findViewById(R.id.section_email),
                findViewById(R.id.section_thanks)
        };

        AppVoiceManager.getInstance(this).initializeTTS();

        if (voiceManager != null) {
            voiceManager.stop();
        }

        setupTouchReading();
        startReadingFrom(0);
    }

    private void startReadingFrom(int fromIndex) {
        handler.removeCallbacksAndMessages(null);
        soundPlayed = false;
        currentSection = fromIndex;

        if (voiceManager != null) {
            voiceManager.stop();
        }

        handler.postDelayed(() -> {
            if (voiceManager != null && voiceManager.isTTSInitialized()) {
                readNextSection();
            } else {
                handler.postDelayed(() -> startReadingFrom(currentSection), 400);
            }
        }, 600);
    }

    private void readNextSection() {
        if (currentSection >= sections.length) {
            playEndSoundOnce();
            return;
        }

        TextView section = sections[currentSection];

        if (section == null) {
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

    private void setupTouchReading() {
        for (int i = 0; i < sections.length; i++) {
            int index = i;
            TextView tv = sections[i];

            if (tv == null) continue;

            tv.setFocusable(true);
            tv.setFocusableInTouchMode(true);

            tv.setOnClickListener(v -> startReadingFrom(index));

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
        }
    }

    private void highlightSection(TextView section) {
        for (TextView tv : sections) {
            if (tv != null) tv.setBackgroundColor(0x00000000);
        }

        section.setBackgroundColor(0x33FFFFFF);

        scrollView.post(() ->
                scrollView.smoothScrollTo(0, section.getTop())
        );
    }

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

    @Override
    protected void onDoubleTapDetected() {
        handler.removeCallbacksAndMessages(null);

        soundPlayed = false;

        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

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