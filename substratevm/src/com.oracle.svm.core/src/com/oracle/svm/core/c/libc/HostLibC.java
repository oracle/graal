package com.oracle.svm.core.c.libc;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Iterator;
import java.util.ServiceLoader;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract class HostLibC {

    private static final LibCBase INSTANCE;

    static {
        Iterator<HostLibC> loader = ServiceLoader.load(HostLibC.class).iterator();
        INSTANCE = loader.hasNext() ? loader.next().create() : new NoLibC();
    }

    public static LibCBase get() {
        return INSTANCE;
    }

    public abstract LibCBase create();

    public static String getName() {
        return is(NoLibC.class) ? null : get().getName();
    }

    public static boolean is(Class<? extends LibCBase> libcClass) {
        return get().getClass() == libcClass;
    }
}
