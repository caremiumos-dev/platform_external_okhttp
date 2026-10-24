package com.squareup.okhttp;

import com.quicinc.tcmiface.DpmTcmIface;
import com.squareup.okhttp.internal.Platform;

import java.lang.reflect.Method;

/**
 * XTC/QTI addition: registers this connection pool with the vendor TCM client
 * (loaded reflectively from {@code /system/framework/tcmclient.jar}) so that
 * idle connections can be closed on demand.
 *
 * <p><b>Condemnation.</b> This reaches into a closed vendor component via
 * reflection and lets it drive the process's network connection pool; the
 * failure to load the client is swallowed silently. Reproduced for source
 * fidelity only.
 */
public class TcmIdleTimerMonitor implements DpmTcmIface {
    private ConnectionPool connectionPool;
    Object result;

    private static Object tcmClient = null;
    private static Method mTcmRegisterMethod = null;
    private static final Object lockObj = new Object();

    public TcmIdleTimerMonitor(ConnectionPool connectionPool) {
        this.result = null;
        synchronized (lockObj) {
            try {
                this.connectionPool = connectionPool;
                try {
                    if (mTcmRegisterMethod == null || tcmClient == null) {
                        Class<?> cls = new dalvik.system.PathClassLoader(
                                "/system/framework/tcmclient.jar",
                                ClassLoader.getSystemClassLoader())
                                .loadClass("com.qti.tcmclient.DpmTcmClient");
                        tcmClient = cls.getDeclaredMethod("getInstance", new Class[0])
                                .invoke(null, new Object[0]);
                        mTcmRegisterMethod = cls.getDeclaredMethod("registerTcmMonitor",
                                DpmTcmIface.class);
                    }
                    if (mTcmRegisterMethod != null && tcmClient != null) {
                        this.result = mTcmRegisterMethod.invoke(tcmClient, this);
                    }
                } catch (ClassNotFoundException e) {
                    // vendor client absent
                } catch (Exception e2) {
                    Platform.get().logW("tcmclient load failed: " + e2);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    @Override
    public void OnCloseIdleConn() {
        this.connectionPool.closeIdleConnections();
    }
}