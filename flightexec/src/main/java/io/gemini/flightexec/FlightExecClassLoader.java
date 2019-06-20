package io.gemini.flightexec;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 * gemini
 * io.gemini.flightexec.FlightExecClassLoader
 *
 * @author zhanghailin
 */
public class FlightExecClassLoader extends ClassLoader {

    private static ProtectionDomain PROTECTION_DOMAIN;

    static {
        PROTECTION_DOMAIN = AccessController.doPrivileged(
                (PrivilegedAction<ProtectionDomain>) FlightExecClassLoader.class::getProtectionDomain);
    }

    public FlightExecClassLoader() {
        super(Thread.currentThread().getContextClassLoader());
    }

    public Class<?> loadBytes(byte[] classBytes) {
        return defineClass(null, classBytes, 0, classBytes.length, PROTECTION_DOMAIN);
    }
}
