package top.dynesshely.chargesentinel.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 电量采样与状态事件的本地存储（framework SQLite，无第三方依赖）。
 *
 * 采样策略由调用方决定（服务每 5 分钟或电量变化时写一条），事件只在状态切换时写入。
 * 默认保留 30 天，旧数据在服务运行期间定期清理。
 */
public final class HistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "charge_history.db";
    private static final int DB_VERSION = 1;

    private static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private static volatile HistoryStore instance;

    public static HistoryStore get(Context context) {
        if (instance == null) {
            synchronized (HistoryStore.class) {
                if (instance == null) {
                    instance = new HistoryStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private HistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE samples ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "level INTEGER NOT NULL,"
                + "plugged INTEGER NOT NULL,"
                + "suspended INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_samples_ts ON samples(ts)");
        db.execSQL("CREATE TABLE events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "type TEXT NOT NULL,"
                + "level INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS samples");
        db.execSQL("DROP TABLE IF EXISTS events");
        onCreate(db);
    }

    public void recordSample(long timestamp, int level, boolean plugged, boolean suspended) {
        ContentValues values = new ContentValues();
        values.put("ts", timestamp);
        values.put("level", level);
        values.put("plugged", plugged ? 1 : 0);
        values.put("suspended", suspended ? 1 : 0);
        getWritableDatabase().insert("samples", null, values);
    }

    public void recordEvent(long timestamp, String type, int level) {
        ContentValues values = new ContentValues();
        values.put("ts", timestamp);
        values.put("type", type);
        values.put("level", level);
        getWritableDatabase().insert("events", null, values);
    }

    public List<HistoryPoint> samplesSince(long since) {
        List<HistoryPoint> points = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "samples", new String[]{"ts", "level", "plugged", "suspended"},
                "ts >= ?", new String[]{String.valueOf(since)}, null, null, "ts ASC")) {
            while (cursor.moveToNext()) {
                points.add(new HistoryPoint(
                        cursor.getLong(0), cursor.getInt(1), cursor.getInt(2) != 0, cursor.getInt(3) != 0));
            }
        }
        return points;
    }

    public List<HistoryEvent> eventsSince(long since) {
        List<HistoryEvent> events = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "events", new String[]{"ts", "type", "level"},
                "ts >= ?", new String[]{String.valueOf(since)}, null, null, "ts ASC")) {
            while (cursor.moveToNext()) {
                events.add(new HistoryEvent(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)));
            }
        }
        return events;
    }

    public void prune(long before) {
        getWritableDatabase().delete("samples", "ts < ?", new String[]{String.valueOf(before)});
        getWritableDatabase().delete("events", "ts < ?", new String[]{String.valueOf(before)});
    }

    public void pruneOldData() {
        prune(System.currentTimeMillis() - RETENTION_MS);
    }
}
