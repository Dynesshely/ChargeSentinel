package top.dynesshely.chargesentinel;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 进程内的环形日志，供界面底部展示，同时也写入 logcat（tag=ChargeSentinel）。 */
public final class Logs {

    private static final String TAG = "ChargeSentinel";
    private static final int MAX = 80;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private Logs() {
    }

    public static void add(String msg) {
        Log.i(TAG, msg);
        String line = FMT.format(new Date()) + "  " + msg;
        synchronized (LINES) {
            LINES.addFirst(line);
            while (LINES.size() > MAX) {
                LINES.removeLast();
            }
        }
    }

    public static List<String> snapshot() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }
}
