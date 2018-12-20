/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.posix.headers.Time.gettimeofday;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Time.timezone;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.Wait;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.PointerUtils;

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
                theUnmodifiableEnvironment = KnownIntrinsics.unsafeCast(new Target_java_lang_ProcessEnvironment_StringEnvironment(env), Map.class);
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
            if (LibC.strchr(environ.read(i), '=').isNonNull()) {
                count++;
            }
        }

        byte[][] result = new byte[count * 2][];
        int j = 0;
        for (int i = 0; environ.read(i).isNonNull(); i++) {
            CCharPointer varBeg = environ.read(i);
            CCharPointer varEnd = LibC.strchr(varBeg, '=');
            /* Ignore corrupted environment variables */
            if (varEnd.isNonNull()) {
                CCharPointer valBeg = varEnd.addressOf(1);
                int varLength = (int) PointerUtils.absoluteDifference(varEnd, varBeg).rawValue();
                int valLength = (int) LibC.strlen(valBeg).rawValue();

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

@TargetClass(className = "java.lang.UNIXProcess", onlyWith = JDK8OrEarlier.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_UNIXProcess {

    // The reaper thread pool and thread groups (currently) confuse the analysis, so we launch
    // reaper threads individually (with the only difference being that threads are not recycled)
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
    void initStreams(int[] fds) {
        Object in = Target_java_lang_ProcessBuilder_NullOutputStream.INSTANCE;
        if (fds[0] != -1) {
            in = new Target_java_lang_UNIXProcess_ProcessPipeOutputStream(fds[0]);
        }
        stdin = KnownIntrinsics.unsafeCast(in, OutputStream.class);

        Object out = Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE;
        if (fds[1] != -1) {
            out = new Target_java_lang_UNIXProcess_ProcessPipeInputStream(fds[1]);
        }
        stdout = KnownIntrinsics.unsafeCast(out, InputStream.class);

        Object err = Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE;
        if (fds[2] != -1) {
            err = new Target_java_lang_UNIXProcess_ProcessPipeInputStream(fds[2]);
        }
        stderr = KnownIntrinsics.unsafeCast(err, InputStream.class);

        Thread reaperThread = Java_lang_Process_Supplement.reaperFactory.newThread(new Runnable() {
            @Override
            public void run() {
                int status = waitForProcessExit(pid);
                // Checkstyle: stop
                // We need to use synchronized to synchronize with non-substituted UNIXProcess code
                synchronized (Target_java_lang_UNIXProcess.this) {
                    // Checkstyle: resume
                    Target_java_lang_UNIXProcess.this.exitcode = status;
                    Target_java_lang_UNIXProcess.this.hasExited = true;
                    Target_java_lang_UNIXProcess.this.notifyAll();
                }
                if ((Object) stdout != Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE) {
                    KnownIntrinsics.unsafeCast(stdout, Target_java_lang_UNIXProcess_ProcessPipeInputStream.class)
                                    .processExited();
                }
                if ((Object) stderr != Target_java_lang_ProcessBuilder_NullInputStream.INSTANCE) {
                    KnownIntrinsics.unsafeCast(stderr, Target_java_lang_UNIXProcess_ProcessPipeInputStream.class)
                                    .processExited();
                }
                if ((Object) stdin != Target_java_lang_ProcessBuilder_NullOutputStream.INSTANCE) {
                    KnownIntrinsics.unsafeCast(stdin, Target_java_lang_UNIXProcess_ProcessPipeOutputStream.class)
                                    .processExited();
                }
            }
        });
        reaperThread.start();
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    int waitForProcessExit(int ppid) {
        CIntPointer statusptr = StackValue.get(CIntPointer.class);
        while (Wait.waitpid(ppid, statusptr, 0) < 0) {
            if (Errno.errno() == Errno.ECHILD()) {
                return 0;
            } else if (Errno.errno() != Errno.EINTR()) {
                return -1;
            }
        }

        int status = statusptr.read();
        if (Wait.WIFEXITED(status)) {
            return Wait.WEXITSTATUS(status);
        } else if (Wait.WIFSIGNALED(status)) {
            // Exited because of signal: return 0x80 + signal number like shells do
            return 0x80 + Wait.WTERMSIG(status);
        }
        return status;
    }

    @Substitute
    static void destroyProcess(int ppid, boolean force) {
        int sig = force ? Signal.SignalEnum.SIGKILL.getCValue() : Signal.SignalEnum.SIGTERM.getCValue();
        Signal.kill(ppid, sig);
    }
}

@TargetClass(className = "java.lang.UNIXProcess", innerClass = "ProcessPipeInputStream", onlyWith = JDK8OrEarlier.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_UNIXProcess_ProcessPipeInputStream {
    @Alias
    Target_java_lang_UNIXProcess_ProcessPipeInputStream(@SuppressWarnings("unused") int fd) {
    }

    @Alias
    native void processExited();
}

@TargetClass(className = "java.lang.UNIXProcess", innerClass = "ProcessPipeOutputStream", onlyWith = JDK8OrEarlier.class)
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
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_System {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static volatile Console cons;

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static long currentTimeMillis() {
        timeval timeval = StackValue.get(timeval.class);
        timezone timezone = WordFactory.nullPointer();
        gettimeofday(timeval, timezone);
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
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
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
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public final class PosixJavaLangSubstitutions {

    /** Private constructor: No instances. */
    private PosixJavaLangSubstitutions() {
    }
}
