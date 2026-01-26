package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.cosmo.headers.Signal;
import com.oracle.svm.core.posix.cosmo.headers.Time;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.PlatformTimeUtils;
import com.oracle.svm.core.util.TimeUtils;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.impl.InternalPlatform;

import java.io.Console;
import java.util.Objects;

/* dummy */
public class CosmoSubstitutions {
}

@TargetClass(value = java.lang.System.class, onlyWith = CosmoLibCSupplier.class)
final class Target_java_lang_System_Cosmo {

    @Substitute
    @Uninterruptible(reason = "Does basic math after a simple system call")
    private static long nanoTime() {
        Time.timespec tp = StackValue.get(Time.timespec.class);
        int status = Time.NoTransitions.clock_gettime(Time.CLOCK_MONOTONIC(), tp);
        CosmoUtils.checkStatusIs0(status, "System.nanoTime(): clock_gettime(CLOCK_MONOTONIC) failed.");
        return tp.tv_sec() * TimeUtils.nanosPerSecond + tp.tv_nsec();
    }

    @Substitute
    public static String mapLibraryName(String libname) {
        Objects.requireNonNull(libname);
        return "lib" + libname + ".so";
    }

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static volatile Console cons;

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long currentTimeMillis() {
        Time.timespec ts = StackValue.get(Time.timespec.class);
        int status = CosmoUtils.clock_gettime(Time.CLOCK_MONOTONIC(), ts);
        CosmoUtils.checkStatusIs0(status, "System.currentTimeMillis(): clock_gettime(CLOCK_MONOTONIC) failed.");
        return ts.tv_sec() * TimeUtils.millisPerSecond + ts.tv_nsec() / TimeUtils.nanosPerMilli;
    }
}

@TargetClass(className = "jdk.internal.misc.VM", onlyWith = CosmoLibCSupplier.class)
final class Target_jdk_internal_misc_VM {
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+3/src/hotspot/share/prims/jvm.cpp#L258-L291")
    public static long getNanoTimeAdjustment(long offsetInSeconds) {
        long maxDiffSecs = 0x0100000000L;
        long minDiffSecs = -maxDiffSecs;

        PlatformTimeUtils.SecondsNanos time = PlatformTimeUtils.singleton().javaTimeSystemUTC();

        long diff = time.seconds() - offsetInSeconds;
        if (diff >= maxDiffSecs || diff <= minDiffSecs) {
            return -1;
        }
        return (diff * 1000000000) + time.nanos();
    }
}

@TargetClass(className = "jdk.internal.misc.Signal", onlyWith = CosmoLibCSupplier.class)
final class Target_jdk_internal_misc_Signal {
    @Substitute
    private static int findSignal0(String signalName) {
        return CosmoSignalHandlerSupport.singleton().findSignal(signalName);
    }

    @Substitute
    private static void raise0(int signalNumber) {
        Signal.raise(signalNumber);
    }
}

@TargetClass(className = "java.io.UnixFileSystem", onlyWith = CosmoLibCSupplier.class)
final class Target_java_io_UnixFileSystem_JNI {
    @Alias
    static native void initIDs();
}