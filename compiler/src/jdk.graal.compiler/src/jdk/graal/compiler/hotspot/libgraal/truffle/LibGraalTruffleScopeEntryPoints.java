package jdk.graal.compiler.hotspot.libgraal.truffle;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.PointerBase;

import jdk.graal.compiler.libgraal.LibGraalFeature;
import jdk.graal.compiler.serviceprovider.IsolateUtil;

/**
 * Truffle specific {@link CEntryPoint} implementations.
 */
final class LibGraalTruffleScopeEntryPoints {

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateThreadIn", builtin = CEntryPoint.Builtin.GET_CURRENT_THREAD, include = LibGraalFeature.IsEnabled.class)
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_attachThreadTo", builtin = CEntryPoint.Builtin.ATTACH_THREAD, include = LibGraalFeature.IsEnabled.class)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext long isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_detachThreadFrom", builtin = CEntryPoint.Builtin.DETACH_THREAD, include = LibGraalFeature.IsEnabled.class)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateThreadContext long isolateThread);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateId", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings("unused")
    public static long getIsolateId(PointerBase env, PointerBase jclass, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        return IsolateUtil.getIsolateID();
    }
}
