/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.posix;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Time.timezone;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.VMError;

@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
@AutomaticFeature
class PosixJavaLangSubstituteFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.lang.UNIXProcess"), "required for substitutions");
        } else {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.lang.ProcessImpl"), "required for substitutions");
            Class<?> processHandleImplClass = access.findClassByName("java.lang.ProcessHandleImpl");
            VMError.guarantee(processHandleImplClass != null);
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(processHandleImplClass, "for substitutions");
        }
    }
}

@TargetClass(className = "java.lang.ProcessEnvironment")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessEnvironment {

    /*
     * On Substrate VM, we initialize the environment variables lazily on first access. We do not
     * want to delay startup by initializing a lot of internal Java state (which might not even be
     * used by an application).
     */

    @Alias @InjectAccessors(value = EnvironmentAccessor.class) //
    private static HashMap<Target_java_lang_ProcessEnvironment_Variable, Target_java_lang_ProcessEnvironment_Value> theEnvironment;

    @Alias @InjectAccessors(value = EnvironmentAccessor.class) //
    private static Map<String, String> theUnmodifiableEnvironment;

    static class EnvironmentAccessor {
        private static HashMap<Target_java_lang_ProcessEnvironment_Variable, Target_java_lang_ProcessEnvironment_Value> theEnvironment;
        private static Map<String, String> theUnmodifiableEnvironment;

        static HashMap<Target_java_lang_ProcessEnvironment_Variable, Target_java_lang_ProcessEnvironment_Value> getTheEnvironment() {
            ensureInitialized();
            return theEnvironment;
        }

        static Map<String, String> getTheUnmodifiableEnvironment() {
            ensureInitialized();
            return theUnmodifiableEnvironment;
        }

        @SuppressWarnings("unchecked")
        static void ensureInitialized() {
            if (theUnmodifiableEnvironment == null) {
                // We cache the C environment. This means that subsequent calls
                // to putenv/setenv from C will not be visible from Java code.
                byte[][] environ = environ();
                HashMap<Target_java_lang_ProcessEnvironment_Variable, Target_java_lang_ProcessEnvironment_Value> env = new HashMap<>(environ.length / 2 + 3);
                // Read environment variables back to front,
                // so that earlier variables override later ones.
                for (int i = environ.length - 1; i > 0; i -= 2) {
                    byte[] var = environ[i - 1];
                    byte[] val = environ[i];
                    env.put(Target_java_lang_ProcessEnvironment_Variable.valueOf(var),
                                    Target_java_lang_ProcessEnvironment_Value.valueOf(val));
                }

                theEnvironment = env;
                theUnmodifiableEnvironment = SubstrateUtil.cast(new Target_java_lang_ProcessEnvironment_StringEnvironment(env), Map.class);
            }
        }
    }

    // This code is derived from the C implementation of the JDK.
    @Substitute
    static byte[][] environ() {
        CCharPointerPointer environ = LibCHelper.getEnviron();

        int count = 0;
        for (int i = 0; environ.read(i).isNonNull(); i++) {
            /* Ignore corrupted environment variables */
            if (SubstrateUtil.strchr(environ.read(i), '=').isNonNull()) {
                count++;
            }
        }

        byte[][] result = new byte[count * 2][];
        int j = 0;
        for (int i = 0; environ.read(i).isNonNull(); i++) {
            CCharPointer varBeg = environ.read(i);
            CCharPointer varEnd = SubstrateUtil.strchr(varBeg, '=');
            /* Ignore corrupted environment variables */
            if (varEnd.isNonNull()) {
                CCharPointer valBeg = varEnd.addressOf(1);
                int varLength = (int) PointerUtils.absoluteDifference(varEnd, varBeg).rawValue();
                int valLength = (int) SubstrateUtil.strlen(valBeg).rawValue();

                byte[] var = new byte[varLength];
                CTypeConversion.asByteBuffer(varBeg, varLength).get(var);
                result[2 * j] = var;

                byte[] val = new byte[valLength];
                CTypeConversion.asByteBuffer(valBeg, valLength).get(val);
                result[2 * j + 1] = val;

                j++;
            }
        }
        assert j == count;

        return result;
    }
}

@TargetClass(className = "java.lang.ProcessEnvironment", innerClass = "StringEnvironment")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessEnvironment_StringEnvironment {
    @Alias
    @SuppressWarnings("unused")
    Target_java_lang_ProcessEnvironment_StringEnvironment(
                    Map<Target_java_lang_ProcessEnvironment_Variable, Target_java_lang_ProcessEnvironment_Value> m) {
    }

    @Alias
    public native byte[] toEnvironmentBlock(int[] envc);
}

@TargetClass(className = "java.lang.ProcessEnvironment", innerClass = "Variable")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessEnvironment_Variable {
    @Alias
    public static native Target_java_lang_ProcessEnvironment_Variable valueOf(byte[] bytes);
}

@TargetClass(className = "java.lang.ProcessEnvironment", innerClass = "Value")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessEnvironment_Value {
    @Alias
    public static native Target_java_lang_ProcessEnvironment_Value valueOf(byte[] bytes);
}

@Platforms(Platform.HOSTED_ONLY.class)
class ProcessNameProvider implements Function<TargetClass, String> {

    @Override
    public String apply(TargetClass annotation) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            return "java.lang.UNIXProcess";
        } else {
            return "java.lang.ProcessImpl";
        }
    }
}

@TargetClass(classNameProvider = ProcessNameProvider.class)
@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
final class Target_java_lang_UNIXProcess {

    // The reaper thread pool and thread groups (currently) confuse the analysis, so we launch
    // reaper threads individually (with the only difference being that threads are not recycled)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})//
    @TargetElement(onlyWith = JDK8OrEarlier.class)//
    @Delete static Executor processReaperExecutor;

    @Alias int pid;
    @Alias OutputStream stdin;
    @Alias InputStream stdout;
    @Alias InputStream stderr;
    @Alias int exitcode;
    @Alias boolean hasExited;

    /*
     * NOTE: This implementation uses simple fork() and exec() calls. However, OpenJDK uses
     * posix_spawn() on some platforms, specifically on Solaris to avoid swap exhaustion when memory
     * is reserved conservatively for the fork'ed process. That implementation is more complex and
     * requires a helper tool to cleanly launch the actual target executable.
     */

    @Substitute
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @SuppressWarnings({"unused", "static-method"})
    int forkAndExec(int mode, byte[] helperpath,
                    byte[] file,
                    byte[] argBlock, int argCount,
                    byte[] envBlock, int envCount,
                    byte[] dir,
                    int[] fds,
                    boolean redirectErrorStream)
                    throws IOException {
        return Java_lang_Process_Supplement.forkAndExec(mode, helperpath, file, argBlock, argCount, envBlock, envCount, dir, fds, redirectErrorStream);
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    void initStreams(int[] fds) {
        UNIXProcess_Support.doInitStreams(this, fds, false);
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    void initStreams(int[] fds, boolean forceNullOutputStream) {
        UNIXProcess_Support.doInitStreams(this, fds, forceNullOutputStream);
    }

    @Substitute
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings({"static-method"})
    int waitForProcessExit(int ppid) {
        return PosixUtils.waitForProcessExit(ppid);
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static void destroyProcess(int ppid, boolean force) {
        int sig = force ? Signal.SignalEnum.SIGKILL.getCValue() : Signal.SignalEnum.SIGTERM.getCValue();
        Signal.kill(ppid, sig);
    }
}

final class UNIXProcess_Support {
    static void doInitStreams(Target_java_lang_UNIXProcess proc, int[] fds, boolean forceNullOutputStream) {
        Object in = Target_java_lang_ProcessBuilder_NullOutputStream.INSTANCE;
        if (fds[0] != -1 && !forceNullOutputStream) {
            in = new Target_java_lang_UNIXProcess_ProcessPipeOutputStream(fds[0]);
        }
        proc.stdin = SubstrateUtil.cast(in, OutputStream.class);

        Object out = Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE;
        if (fds[1] != -1 && !forceNullOutputStream) {
            out = new Target_java_lang_UNIXProcess_ProcessPipeInputStream(fds[1]);
        }
        proc.stdout = SubstrateUtil.cast(out, InputStream.class);

        Object err = Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE;
        if (fds[2] != -1 && !forceNullOutputStream) {
            err = new Target_java_lang_UNIXProcess_ProcessPipeInputStream(fds[2]);
        }
        proc.stderr = SubstrateUtil.cast(err, InputStream.class);

        Thread reaperThread = Java_lang_Process_Supplement.reaperFactory.newThread(new Runnable() {
            @Override
            public void run() {
                int status = PosixUtils.waitForProcessExit(proc.pid);
                // Checkstyle: stop
                // We need to use synchronized to synchronize with non-substituted UNIXProcess code
                synchronized (proc) {
                    // Checkstyle: resume
                    proc.exitcode = status;
                    proc.hasExited = true;
                    proc.notifyAll();
                }
                if ((Object) proc.stdout != Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE) {
                    SubstrateUtil.cast(proc.stdout, Target_java_lang_UNIXProcess_ProcessPipeInputStream.class)
                                    .processExited();
                }
                if ((Object) proc.stderr != Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE) {
                    SubstrateUtil.cast(proc.stderr, Target_java_lang_UNIXProcess_ProcessPipeInputStream.class)
                                    .processExited();
                }
                if ((Object) proc.stdin != Target_java_lang_ProcessBuilder_NullOutputStream.INSTANCE) {
                    SubstrateUtil.cast(proc.stdin, Target_java_lang_UNIXProcess_ProcessPipeOutputStream.class)
                                    .processExited();
                }
            }
        });
        reaperThread.start();
    }
}

@TargetClass(classNameProvider = ProcessNameProvider.class, innerClass = "ProcessPipeInputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_UNIXProcess_ProcessPipeInputStream {
    @Alias
    Target_java_lang_UNIXProcess_ProcessPipeInputStream(@SuppressWarnings("unused") int fd) {
    }

    @Alias
    native void processExited();
}

@TargetClass(classNameProvider = ProcessNameProvider.class, innerClass = "ProcessPipeOutputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_UNIXProcess_ProcessPipeOutputStream {
    @Alias
    Target_java_lang_UNIXProcess_ProcessPipeOutputStream(@SuppressWarnings("unused") int fd) {
    }

    @Alias
    native void processExited();
}

@TargetClass(className = "java.lang.ProcessBuilder", innerClass = "NullInputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessBuilder_NullInputStream {
    @Alias static Target_java_lang_ProcessBuilder_NullInputStream INSTANCE;
}

@TargetClass(className = "java.lang.ProcessBuilder", innerClass = "NullOutputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessBuilder_NullOutputStream {
    @Alias static Target_java_lang_ProcessBuilder_NullOutputStream INSTANCE;
}

@TargetClass(java.lang.System.class)
@Platforms({Platform.LINUX.class, InternalPlatform.LINUX_JNI.class, Platform.DARWIN.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_lang_System {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static volatile Console cons;

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static long currentTimeMillis() {
        timeval timeval = StackValue.get(timeval.class);
        timezone timezone = WordFactory.nullPointer();
        Time.gettimeofday(timeval, timezone);
        return timeval.tv_sec() * 1_000L + timeval.tv_usec() / 1_000L;
    }
}

@TargetClass(className = "java.lang.Shutdown")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_Shutdown {

    @Substitute
    static void halt0(int status) {
        LibC.exit(status);
    }
}

@TargetClass(java.lang.Runtime.class)
@Platforms({Platform.DARWIN.class})
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    @Substitute
    private int availableProcessors() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return (int) Unistd.sysconf(Unistd._SC_NPROCESSORS_ONLN());
        } else {
            return 1;
        }
    }
}

/** Dummy class to have a class with the file's name. */
@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
public final class PosixJavaLangSubstitutions {

    /** Private constructor: No instances. */
    private PosixJavaLangSubstitutions() {
    }

    @Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
    public static boolean initIDs() {
        // The JDK uses posix_spawn on the Mac to launch executables.
        // This requires a separate process "jspawnhelper" which we
        // don't want to have to rely on. Force the use of FORK on
        // Linux and Mac.
        System.setProperty("jdk.lang.Process.launchMechanism", "FORK");
        return true;
    }
}
