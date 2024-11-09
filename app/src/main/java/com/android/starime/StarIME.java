package com.android.starime;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.Base64;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;

public class StarIME extends InputMethodService {
    private static final String IME_MESSAGE = "STAR_INPUT_TEXT";
    private static final String IME_CHARS = "STAR_INPUT_CHARS";
    private static final String IME_KEYCODE = "STAR_INPUT_CODE";
    private static final String IME_EDITOR_CODE = "STAR_EDITOR_CODE";
    private static final String IME_MESSAGE_B64 = "STAR_INPUT_B64";
    private static final String IME_CLEAR_TEXT = "STAR_CLEAR_TEXT";
    private BroadcastReceiver mReceiver = null;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public View onCreateInputView() {
        @SuppressLint("InflateParams") View mInputView = getLayoutInflater().inflate(R.layout.view, null);

        if (mReceiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IME_MESSAGE);
            filter.addAction(IME_CHARS);
            filter.addAction(IME_KEYCODE);
            filter.addAction(IME_EDITOR_CODE);
            filter.addAction(IME_MESSAGE_B64);
            filter.addAction(IME_CLEAR_TEXT);
            mReceiver = new AdbReceiver();
            registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        return mInputView;
    }

    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    private class AdbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) {
                return;
            }

            String action = intent.getAction();
            if (IME_MESSAGE.equals(action)) {
                handleMessage(intent, ic);
            } else if (IME_CHARS.equals(action)) {
                handleChars(intent, ic);
            } else if (IME_KEYCODE.equals(action)) {
                handleKeyCode(intent, ic);
            } else if (IME_EDITOR_CODE.equals(action)) {
                handleEditorCode(intent, ic);
            } else if (IME_MESSAGE_B64.equals(action)) {
                handleBase64Message(intent, ic);
            } else if (IME_CLEAR_TEXT.equals(action)) {
                handleClearText(ic);
            }
        }

        private void handleMessage(Intent intent, InputConnection ic) {
            String msg = intent.getStringExtra("msg");
            if (msg != null) {
                ic.commitText(msg, 1);
            }

            String metaCodes = intent.getStringExtra("code");
            if (metaCodes != null) {
                String[] codes = metaCodes.split(",");
                for (int i = 0; i < codes.length - 1; i += 2) {
                    KeyEvent ke;
                    if (codes[i].contains("+")) {
                        String[] arrCode = codes[i].split("\\+");
                        ke = new KeyEvent(
                                0, 0, KeyEvent.ACTION_DOWN, Integer.parseInt(codes[i + 1]), 0,
                                Integer.parseInt(arrCode[0]) | Integer.parseInt(arrCode[1]),
                                0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                    } else {
                        ke = new KeyEvent(
                                0, 0, KeyEvent.ACTION_DOWN, Integer.parseInt(codes[i + 1]), 0,
                                Integer.parseInt(codes[i]), 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                InputDevice.SOURCE_KEYBOARD
                        );
                    }
                    ic.sendKeyEvent(ke);
                }
            }
        }

        private void handleChars(Intent intent, InputConnection ic) {
            int[] chars = intent.getIntArrayExtra("chars");
            if (chars != null) {
                String msg = new String(chars, 0, chars.length);
                ic.commitText(msg, 1);
            }
        }

        private void handleKeyCode(Intent intent, InputConnection ic) {
            int code = intent.getIntExtra("code", -1);
            if (code != -1) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            }
        }

        private void handleEditorCode(Intent intent, InputConnection ic) {
            int code = intent.getIntExtra("code", -1);
            if (code != -1) {
                ic.performEditorAction(code);
            }
        }

        private void handleBase64Message(Intent intent, InputConnection ic) {
            String data = intent.getStringExtra("msg");
            if (data != null) {
                byte[] b64 = Base64.decode(data, Base64.DEFAULT);
                String msg = new String(b64, StandardCharsets.UTF_8);
                ic.commitText(msg, 1);
            }
        }

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
