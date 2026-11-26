package com.shark.kty.shell;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import moe.shizuku.api.BinderContainer;
import rikka.hidden.compat.ActivityManagerApis;

/**
 * Hands the shell binder to the app's own ShizukuProvider.
 */
public final class BinderDelivery {
    private static final String TAG = "SharkShell";
    private static final String EXTRA_BINDER =
            "moe.shizuku.privileged.api.intent.extra.BINDER";
    private static final int MAX_ATTEMPTS = 30;
    private static final long RETRY_DELAY_MS = 300;

    private BinderDelivery() {
    }

    public static boolean send(IBinder binder, String packageName) {
        String authority = packageName + ".shizuku";
        IContentProvider provider = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS && provider == null; attempt++) {
            try {
                IContentProvider candidate = ActivityManagerApis
                        .getContentProviderExternal(authority, 0, null, authority);
                if (candidate != null && candidate.asBinder().pingBinder()) {
                    provider = candidate;
                    break;
                }
            } catch (Throwable t) {
                Log.w(TAG, "provider lookup failed: " + t);
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        if (provider == null) {
            Log.e(TAG, "provider " + authority + " not reachable");
            return false;
        }

        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_BINDER, new BinderContainer(binder));
        try {
            Bundle result = provider.call(
                    new AttributionSource.Builder(Process.myUid())
                            .setPackageName(packageName)
                            .build(),
                    authority,
                    "sendBinder",
                    null,
                    extras);
            Log.i(TAG, "binder delivered (" + (result != null) + ")");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "sendBinder failed: " + t);
            return false;
        } finally {
            try {
                ActivityManagerApis.removeContentProviderExternal(authority, null);
            } catch (Throwable ignored) {
            }
        }
    }
}
