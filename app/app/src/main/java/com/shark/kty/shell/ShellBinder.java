package com.shark.kty.shell;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IShizukuServiceConnection;

/**
 * Shizuku-compatible server binder, running inside the privileged shell process.
 */
public final class ShellBinder extends IShizukuService.Stub {
    public static final String TAG = "SharkShell";
    private static final int SERVER_VERSION = 13;
    private static final int PROBE_MAGIC = 0x53484b;
    public static final int TRANSACTION_PROBE = 0x5348;
    private static final Pattern PROP_KEY = Pattern.compile("[A-Za-z0-9_.-]+");

    private final int appUid;

    public ShellBinder(int appUid) {
        this.appUid = appUid;
    }

    private boolean isAllowedCaller() {
        int uid = Binder.getCallingUid();
        return uid == Process.myUid() || (appUid > 0 && uid == appUid);
    }

    private static String readCommand(String... command) {
        try {
            java.lang.Process process = new ProcessBuilder(command)
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
            return builder.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readCurrentContext() {
        try (FileInputStream input = new FileInputStream("/proc/self/attr/current")) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read > 0) {
                return new String(buffer, 0, read, StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable ignored) {
        }
        String fallback = readCommand("id", "-Z").trim();
        return fallback.isEmpty() ? "u:r:shell:s0" : fallback;
    }

    @Override
    public int getVersion() {
        return SERVER_VERSION;
    }

    @Override
    public int getUid() {
        return Process.myUid();
    }

    @Override
    public int checkPermission(String permission) {
        return isAllowedCaller() ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED;
    }

    @Override
    public boolean checkSelfPermission() {
        return isAllowedCaller();
    }

    @Override
    public boolean shouldShowRequestPermissionRationale() {
        return false;
    }

    @Override
    public void requestPermission(int requestCode) {
    }

    @Override
    public IRemoteProcess newProcess(String[] cmd, String[] env, String dir) {
        return RemoteProcessImpl.start(cmd, env, dir);
    }

    @Override
    public String getSELinuxContext() {
        return readCurrentContext();
    }

    @Override
    public String getSystemProperty(String key, String defaultValue) {
        if (key == null || !PROP_KEY.matcher(key).matches()) {
            return defaultValue;
        }
        String value = readCommand("getprop", key).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    @Override
    public void setSystemProperty(String key, String value) {
        if (key == null || value == null || !PROP_KEY.matcher(key).matches()) {
            return;
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 ||
                value.indexOf('\0') >= 0) {
            return;
        }
        try {
            java.lang.Process process = new ProcessBuilder("setprop", key, value).start();
            process.waitFor();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int addUserService(IShizukuServiceConnection connection, Bundle options) {
        return 0;
    }

    @Override
    public int removeUserService(IShizukuServiceConnection connection, Bundle options) {
        return 0;
    }

    @Override
    public void attachUserService(IBinder binder, Bundle options) {
    }

    @Override
    public void attachApplication(IShizukuApplication application, Bundle options) {
        if (application == null) {
            return;
        }
        Bundle reply = new Bundle();
        reply.putInt("shizuku:attach-reply-uid", Process.myUid());
        reply.putInt("shizuku:attach-reply-version", SERVER_VERSION);
        reply.putInt("shizuku:attach-reply-patch-version", 0);
        reply.putString("shizuku:attach-reply-secontext", getSELinuxContext());
        reply.putBoolean("shizuku:attach-reply-permission-granted", true);
        reply.putBoolean("shizuku:attach-reply-should-show-request-permission-rationale", false);
        try {
            application.bindApplication(reply);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void dispatchPackageChanged(Intent intent) {
    }

    @Override
    public boolean isHidden(int uid) {
        return false;
    }

    @Override
    public void dispatchPermissionConfirmationResult(int requestUid, int requestCode,
                                                    int result, Bundle data) {
    }

    @Override
    public int getFlagsForUid(int uid, int flags) {
        return 0;
    }

    @Override
    public void updateFlagsForUid(int uid, int mask, int flags) {
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == TRANSACTION_PROBE) {
            if (reply != null) {
                reply.writeInt(PROBE_MAGIC);
            }
            return true;
        }
        if (!isAllowedCaller()) {
            return false;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
