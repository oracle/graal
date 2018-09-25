package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class JniEnv {
    static {
        String lib = System.getProperty("nespresso.library");
        if (lib == null) {
            load("nespresso");
        } else {
            load(lib);
        }
        // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes)
        // at runtime
    }

    private static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    private static long load(String name) {
        TruffleObject lib = null;
        EspressoContext context = EspressoLanguage.getCurrentContext();
        try {
            lib = loadLibrary(name);
        } catch (UnsatisfiedLinkError e) {
            throw context.getMeta().throwEx(UnsatisfiedLinkError.class);
        }
        return context.addNativeLibrary(lib);
    }

    // TODO(peterssen): JNIEnv omitted?
    public static int GetArrayLength(long jniEnv, Object array) {
        // TODO(peterssen): Find out how to throw NullPointerException.
        // This method is called from TruffleNFI.
        return EspressoLanguage.getCurrentContext().getVm().arrayLength(array);
    }

    public static native long createEnv();

    public static native void disposeEnv(long nativeAddress);
}
