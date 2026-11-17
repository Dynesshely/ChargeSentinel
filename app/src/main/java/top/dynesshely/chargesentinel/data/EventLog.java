package top.dynesshely.chargesentinel.data;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 进程内环形日志：界面底部展示，同时写入 logcat（tag = ChargeSentinel）。 */
public final class EventLog {

    private static final String TAG = "ChargeSentinel";
    private static final int CAPACITY = 80;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private EventLog() {
    }

    public static void add(String message) {
        Log.i(TAG, message);
        String line = FORMAT.format(new Date()) + "  " + message;
        synchronized (LINES) {
            LINES.addFirst(line);
            while (LINES.size() > CAPACITY) {
                LINES.removeLast();
            }
        }
    }

    public static List<String> recent() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }
}
