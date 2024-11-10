/**
 * StarIME - A custom input method service for Android that handles various input events
 * through broadcast receivers.
 *
 * This IME service provides the following functionality:
 * - Text input via broadcast messages
 * - Key event simulation with meta key support
 * - Editor action handling (search, done, etc.)
 * - Text clearing capability
 *
 * The service uses broadcast receivers to handle different types of input events
 * and communicates with the Android input system through InputConnection.
 */
package com.android.starime;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.View;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.RequiresApi;

import java.util.Objects;

public class StarIME extends InputMethodService {
    // Broadcast action constants for different input operations
    private static final String IME_INPUT_TEXT = "STAR_INPUT_TEXT";
    private static final String IME_EDITOR_CODE = "STAR_EDITOR_CODE"; 
    private static final String IME_CLEAR_TEXT = "STAR_CLEAR_TEXT";
    private BroadcastReceiver mReceiver = null;

    /**
     * Creates and initializes the input view and registers the broadcast receiver.
     * This method is called when the IME is first displayed.
     *
     * @return The created input view instance
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public View onCreateInputView() {
        @SuppressLint("InflateParams") View mInputView = getLayoutInflater().inflate(R.layout.view, null);

        if (mReceiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IME_INPUT_TEXT);
            filter.addAction(IME_EDITOR_CODE);
            filter.addAction(IME_CLEAR_TEXT);
            mReceiver = new AdbReceiver();
            registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        return mInputView;
    }

    /**
     * Performs cleanup by unregistering the broadcast receiver when the IME is destroyed.
     * This prevents memory leaks and ensures proper resource management.
     */
    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * Inner class that handles various broadcast events for input processing.
     * Acts as a central handler for all incoming broadcast messages.
     */
    private class AdbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) {
                return;
            }

            String action = intent.getAction();
            switch (Objects.requireNonNull(action)) {
                case IME_INPUT_TEXT:
                    handleMessage(intent, ic);
                    break;
                case IME_EDITOR_CODE:
                    handleEditorCode(intent, ic);
                    break;
                case IME_CLEAR_TEXT:
                    handleClearText(ic);
                    break;
                default:
                    break;
            }
        }

        /**
         * Processes text input and meta key combinations received via broadcast.
         * Supports both simple text input and complex key combinations with meta states.
         *
         * @param intent Intent containing the text to input and/or key codes to simulate
         * @param ic Current input connection to send events to
         */
        private void handleMessage(Intent intent, InputConnection ic) {
            String msg = intent.getStringExtra("text");
            if (msg != null) {
                ic.commitText(msg, 1);
            }

            String metaCodes = intent.getStringExtra("code");
            if (metaCodes != null) {
                String[] codes = metaCodes.split(",");
                for (int i = 0; i < codes.length - 1; i += 2) {
                    KeyEvent keDown;
                    KeyEvent keUp;
                    if (codes[i].contains("+")) {
                        String[] arrCode = codes[i].split("\\+");
                        int metaState = Integer.parseInt(arrCode[0]) | Integer.parseInt(arrCode[1]);
                        keDown = new KeyEvent(
                                0, 0, KeyEvent.ACTION_DOWN, Integer.parseInt(codes[i + 1]), 0,
                                metaState, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                        keUp = new KeyEvent(
                                0, 0, KeyEvent.ACTION_UP, Integer.parseInt(codes[i + 1]), 0,
                                metaState, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                    } else {
                        keDown = new KeyEvent(
                                0, 0, KeyEvent.ACTION_DOWN, Integer.parseInt(codes[i + 1]), 0,
                                Integer.parseInt(codes[i]), 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                        keUp = new KeyEvent(
                                0, 0, KeyEvent.ACTION_UP, Integer.parseInt(codes[i + 1]), 0,
                                Integer.parseInt(codes[i]), 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                    }
                    ic.sendKeyEvent(keDown);
                    ic.sendKeyEvent(keUp);
                }
            }
        }

        /**
         * Processes editor action codes (e.g., search, done, next) received via broadcast.
         * Performs the specified editor action on the current input connection.
         *
         * @param intent Intent containing the editor action code to perform
         * @param ic Current input connection to perform the action on
         */
        private void handleEditorCode(Intent intent, InputConnection ic) {
            int code = intent.getIntExtra("code", -1);
            if (code != -1) {
                ic.performEditorAction(code);
            }
        }

        /**
         * Handles text clearing requests by removing all text from the current input field.
         * Uses the InputConnection to determine text boundaries and perform deletion.
         *
         * @param ic Current input connection to perform text clearing on
         */
        private void handleClearText(InputConnection ic) {
            CharSequence curPos = ic.getExtractedText(new ExtractedTextRequest(), 0).text;
            if (curPos != null) {
                CharSequence beforePos = ic.getTextBeforeCursor(curPos.length(), 0);
                CharSequence afterPos = ic.getTextAfterCursor(curPos.length(), 0);
                if (beforePos != null && afterPos != null) {
                    ic.deleteSurroundingText(beforePos.length(), afterPos.length());
                }
            }
        }
    }
}
