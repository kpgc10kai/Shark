package com.shark.kty.shell;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import moe.shizuku.server.IRemoteProcess;

/**
 * IRemoteProcess implementation backed by a real local Process and pipe pairs.
 */
public final class RemoteProcessImpl extends IRemoteProcess.Stub {
    private static final String TAG = "SharkShell";
    private static final int BUFFER_SIZE = 8192;

    private final Process process;
    private final ParcelFileDescriptor[] stdinPipe;
    private final ParcelFileDescriptor[] stdoutPipe;
    private final ParcelFileDescriptor[] stderrPipe;

    private RemoteProcessImpl(Process process,
                              ParcelFileDescriptor[] stdinPipe,
                              ParcelFileDescriptor[] stdoutPipe,
                              ParcelFileDescriptor[] stderrPipe) {
        this.process = process;
        this.stdinPipe = stdinPipe;
        this.stdoutPipe = stdoutPipe;
        this.stderrPipe = stderrPipe;
    }

    public static RemoteProcessImpl start(String[] cmd, String[] env, String dir) {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        if (dir != null && !dir.isEmpty()) {
            builder.directory(new File(dir));
        }
        if (env != null) {
            builder.environment().clear();
            for (String entry : env) {
                int index = entry.indexOf('=');
                if (index > 0) {
                    builder.environment().put(entry.substring(0, index), entry.substring(index + 1));
                }
            }
        }
        try {
            Process process = builder.start();
            try {
                ParcelFileDescriptor[] stdin = ParcelFileDescriptor.createPipe();
                ParcelFileDescriptor[] stdout = ParcelFileDescriptor.createPipe();
                ParcelFileDescriptor[] stderr = ParcelFileDescriptor.createPipe();
                RemoteProcessImpl remote = new RemoteProcessImpl(process, stdin, stdout, stderr);
                pumpToPipe(process.getOutputStream(), stdin[0]);
                pumpFromPipe(process.getInputStream(), stdout[1]);
                pumpFromPipe(process.getErrorStream(), stderr[1]);
                return remote;
            } catch (Throwable t) {
                process.destroy();
                return null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "spawn failed: " + t);
            return null;
        }
    }

    /** Process stdin: pipe read end -> child's OutputStream. */
    private static void pumpToPipe(OutputStream output, ParcelFileDescriptor descriptor) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            try (ParcelFileDescriptor.AutoCloseInputStream input =
                         new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }, "shark-pump");
        thread.setDaemon(true);
        thread.start();
    }

    /** Process stdout/stderr: child's InputStream -> pipe write end. */
    private static void pumpFromPipe(InputStream input, ParcelFileDescriptor descriptor) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            try (ParcelFileDescriptor.AutoCloseOutputStream output =
                         new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }, "shark-pump");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public ParcelFileDescriptor getOutputStream() {
        return stdinPipe[1];
    }

    @Override
    public ParcelFileDescriptor getInputStream() {
        return stdoutPipe[0];
    }

    @Override
    public ParcelFileDescriptor getErrorStream() {
        return stderrPipe[0];
    }

    @Override
    public int waitFor() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public int exitValue() {
        try {
            return process.exitValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public boolean alive() {
        return process.isAlive();
    }

    @Override
    public boolean waitForTimeout(long timeout, String unit) {
        long multiplier = 1;
        if ("s".equals(unit)) {
            multiplier = 1000;
        } else if ("m".equals(unit)) {
            multiplier = 60000;
        }
        long deadline = System.currentTimeMillis() + timeout * multiplier;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !process.isAlive();
    }
}
