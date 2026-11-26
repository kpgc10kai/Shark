package com.shark.kty.shell;

import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * app_process entry point. Runs as the shell user and publishes ShellBinder to
 * the app through its ShizukuProvider, then re-sends it when the app touches
 * the request file.
 */
public final class ShellServer {
    public static final String TAG = "SharkShell";
    public static final String PACKAGE = "com.shark.kty";
    public static final String EXTERNAL_DIR = "/sdcard/Android/data/com.shark.kty/files";
    public static final String REQUEST_FILE = "binder-request";

    private ShellServer() {
    }

    public static void main(String[] args) {
        Looper.prepareMainLooper();
        int appUid = findAppUid();
        Log.i(TAG, "server starting uid=" + Process.myUid() + " appUid=" + appUid);
        startWatchdog();
        ShellBinder binder = new ShellBinder(appUid);
        BinderDelivery.send(binder, PACKAGE);
        watchRequests(binder);
        Looper.loop();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * Starts a detached shell watchdog once. It survives this process and
     * restarts ShellServer after a kill without needing the app or a backend.
     * The child server is marked with SHARK_WATCHDOG=1 so it does not spawn a
     * second watchdog.
     */
    private static void startWatchdog() {
        if (System.getenv("SHARK_WATCHDOG") != null) {
            return;
        }
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isEmpty()) {
            return;
        }
        String script =
                "LOCK=/data/local/tmp/shark-watchdog.lock\n" +
                "if ! mkdir \"$LOCK\" 2>/dev/null; then\n" +
                "  if [ -f \"$LOCK/pid\" ] && kill -0 \"$(cat \"$LOCK/pid\")\" 2>/dev/null; then exit 0; fi\n" +
                "  rm -rf \"$LOCK\"; mkdir \"$LOCK\" || exit 0\n" +
                "fi\n" +
                "echo $$ > \"$LOCK/pid\"\n" +
                "trap 'rm -rf \"$LOCK\"' EXIT\n" +
                "while true; do\n" +
                "  if ! pidof shark_shell >/dev/null 2>&1; then\n" +
                "    env SHARK_WATCHDOG=1 CLASSPATH=" + shellQuote(classPath) +
                " app_process /system/bin --nice-name=shark_shell " +
                "com.shark.kty.shell.ShellServer </dev/null >/dev/null 2>&1\n" +
                "  fi\n" +
                "  sleep 2\n" +
                "done\n";
        try {
            new ProcessBuilder("setsid", "sh", "-c", script).start();
            Log.i(TAG, "watchdog started");
        } catch (Throwable t) {
            Log.w(TAG, "watchdog start failed: " + t);
        }
    }

    private static int findAppUid() {
        try {
            java.lang.Process process = new ProcessBuilder(
                    "pm", "list", "packages", "-U", PACKAGE)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            process.waitFor();
            Matcher matcher = Pattern.compile("uid:(\\d+)").matcher(builder);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Throwable t) {
            Log.w(TAG, "findAppUid failed: " + t);
        }
        return -1;
    }

    private static void watchRequests(ShellBinder binder) {
        Thread thread = new Thread(() -> {
            File request = new File(EXTERNAL_DIR, REQUEST_FILE);
            long lastModified = 0;
            while (true) {
                try {
                    long current = request.lastModified();
                    if (request.exists() && current != lastModified) {
                        boolean delivered = false;
                        try {
                            delivered = BinderDelivery.send(binder, PACKAGE);
                        } catch (Throwable ignored) {
                        }
                        if (delivered) {
                            lastModified = current;
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                }
            }
        }, "shark-request");
        thread.setDaemon(true);
        thread.start();
    }
}
