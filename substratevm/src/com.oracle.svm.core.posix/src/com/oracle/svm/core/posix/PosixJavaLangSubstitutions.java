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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Dirent.direntPointer;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Limits;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Time.timezone;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.UnistdNoTransitions;
import com.oracle.svm.core.posix.headers.Wait;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.VMError;

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
                SubstrateUtil.wrapAsByteBuffer(varBeg, varLength).get(var);
                result[2 * j] = var;

                byte[] val = new byte[valLength];
                SubstrateUtil.wrapAsByteBuffer(valBeg, valLength).get(val);
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
    @Delete static final Executor processReaperExecutor = null;

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

        int[] pipes = new int[8];
        Arrays.fill(pipes, -1);
        try (//
                        PinnedObject filePin = PinnedObject.create(file);
                        PinnedObject dirPin = PinnedObject.create(dir);
                        PinnedObject argBlockPin = PinnedObject.create(argBlock);
                        PinnedObject argvPin = PinnedObject.create(new CCharPointerPointer[argCount + 2]);
                        PinnedObject envBlockPin = PinnedObject.create(envBlock);
                        PinnedObject envpPin = PinnedObject.create(new CCharPointerPointer[envCount + 1]);
                        PinnedObject pipesPin = PinnedObject.create(pipes) //
        ) {
            CCharPointerPointer argv = argvPin.addressOfArrayElement(0);
            argv.write(0, filePin.addressOfArrayElement(0));
            Java_lang_UNIXProcess_Supplement.gatherCStringPointers(argBlockPin.addressOfArrayElement(0), argBlock.length, argv.addressOf(1), argCount + 1);

            CCharPointerPointer envp = WordFactory.nullPointer();
            if (envBlock != null) {
                envp = envpPin.addressOfArrayElement(0);
                Java_lang_UNIXProcess_Supplement.gatherCStringPointers(envBlockPin.addressOfArrayElement(0), envBlock.length, envp.addressOf(0), envCount + 1);
            }

            CIntPointer[] stdioPipes = new CIntPointer[3];
            for (int i = 0; i <= 2; i++) {
                if (fds[i] == -1) {
                    stdioPipes[i] = pipesPin.addressOfArrayElement(2 * i);
                    if (Unistd.pipe(stdioPipes[i]) < 0) {
                        throw new IOException("pipe() failed");
                    }
                }
            }

            CIntPointer failPipe = pipesPin.addressOfArrayElement(2 * stdioPipes.length);
            if (Unistd.pipe(failPipe) < 0) {
                throw new IOException("pipe() failed");
            }

            int[] childStdioFds = new int[3];
            childStdioFds[0] = (fds[0] != -1) ? fds[0] : stdioPipes[0].read(0);
            childStdioFds[1] = (fds[1] != -1) ? fds[1] : stdioPipes[1].read(1);
            childStdioFds[2] = (fds[2] != -1) ? fds[2] : stdioPipes[2].read(1);
            int childFailFd = failPipe.read(1);

            CCharPointer filep = filePin.addressOfArrayElement(0);
            CCharPointer dirp = (dir != null) ? dirPin.addressOfArrayElement(0) : WordFactory.nullPointer();
            int childPid = Java_lang_UNIXProcess_Supplement.doForkAndExec(filep, dirp, argv, envp, childStdioFds, childFailFd);
            if (childPid < 0) {
                throw new IOException("fork() failed");
            }

            // If we are here, we are the parent.

            // store fds of our pipe ends for caller, close unused fds (those of the child)
            fds[0] = fds[1] = fds[2] = -1;
            if (stdioPipes[0].isNonNull()) {
                fds[0] = stdioPipes[0].read(1);
                Unistd.close(stdioPipes[0].read(0));
            }
            if (stdioPipes[1].isNonNull()) {
                fds[1] = stdioPipes[1].read(0);
                Unistd.close(stdioPipes[1].read(1));
            }
            if (stdioPipes[2].isNonNull()) {
                fds[2] = stdioPipes[2].read(0);
                Unistd.close(stdioPipes[2].read(1));
            }
            Unistd.close(failPipe.read(1));

            // read status from child
            final int intSize = SizeOf.get(CIntPointer.class);
            CIntPointer pErrno = StackValue.get(intSize);
            SignedWord failBytes = Java_lang_UNIXProcess_Supplement.readEntirely(failPipe.read(0), pErrno, WordFactory.unsigned(intSize));
            Unistd.close(failPipe.read(0));
            if (failBytes.equal(0)) { // success: pipe closed during exec()
                return childPid;
            } else if (failBytes.equal(SizeOf.get(CIntPointer.class))) {
                int errbuflen = 256;
                try (PinnedObject errbuf = PinnedObject.create(new byte[errbuflen])) {
                    CCharPointer detailCstr = Errno.strerror_r(pErrno.read(), errbuf.addressOfArrayElement(0), WordFactory.unsigned(errbuflen));
                    String detail = CTypeConversion.toJavaString(detailCstr);
                    throw new IOException("error=" + pErrno.read() + ", " + detail);
                }
            } else {
                throw new IOException("unexpected data from child");
            }
        } catch (IOException e) {
            // NOTE: not a finally statement because when successful, some pipes need to stay open
            for (int fd : pipes) {
                if (fd != -1) {
                    Unistd.close(fd);
                }
            }
            throw e;
        }
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

        Thread reaperThread = Java_lang_UNIXProcess_Supplement.reaperFactory.newThread(new Runnable() {
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

@TargetClass(className = "java.lang.ProcessImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessImpl {

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings({"unused", "static-method"})
    private /* native */ int forkAndExec(
                    int mode,
                    byte[] helperpath,
                    byte[] prog,
                    byte[] argBlock,
                    int argc,
                    byte[] envBlock,
                    int envc,
                    byte[] dir,
                    int[] fds,
                    boolean redirectErrorStream)
                    throws IOException {
        throw VMError.unsupportedFeature("JDK9OrLater: Target_java_lang_ProcessImpl.forkAndExec");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    private static /* native */ void init() {
        throw VMError.unsupportedFeature("JDK9OrLater: Target_java_lang_ProcessImpl.init");
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Java_lang_UNIXProcess_Supplement {

    static final ThreadFactory reaperFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable reaper) {
            long stackSize = Boolean.getBoolean("jdk.lang.processReaperUseDefaultStackSize") ? 0 : 32768;
            Thread t = new Thread(null, reaper, "Process Reaper", stackSize);
            t.setDaemon(true);
            // A small attempt (probably futile) to avoid priority inversion
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        }
    };

    @SuppressWarnings("try")
    static int doForkAndExec(CCharPointer file, CCharPointer dir, CCharPointerPointer argv, CCharPointerPointer envp, int[] stdioFds, int failFd) {
        final int buflen = SizeOf.get(dirent.class) + Limits.PATH_MAX() + 1;
        final boolean haveProcFs = Platform.includedIn(Platform.LINUX.class);
        try (// Allocate any objects we need in the child after the fork() ahead of time here, since
             // only this thread will exist in the child and garbage collection is not possible.
                        PinnedObject bufferPin = PinnedObject.create(new byte[buflen]);
                        CCharPointerHolder procFdsPath = haveProcFs ? CTypeConversion.toCString("/proc/self/fd/") : null;
                        CCharPointerHolder searchPaths = CTypeConversion.toCString(System.getenv("PATH"));
                        CCharPointerHolder searchPathSeparator = CTypeConversion.toCString(":");
                        NoAllocationVerifier v = NoAllocationVerifier.factory("fragile state after fork()")) {

            CCharPointer procFdsPathPtr = (procFdsPath != null) ? procFdsPath.get() : WordFactory.nullPointer();
            return uninterruptibleForkAndExec(file, dir, argv, envp, stdioFds, failFd, bufferPin.addressOfArrayElement(0),
                            buflen, procFdsPathPtr, searchPaths.get(), searchPathSeparator.get());
        }
    }

    @Uninterruptible(reason = "fragile state after fork()")
    private static int uninterruptibleForkAndExec(CCharPointer file, CCharPointer dir, CCharPointerPointer argv,
                    CCharPointerPointer envp, int[] stdioFds, int initialFailFd, PointerBase buffer, int buflen,
                    CCharPointer procFdsPath, CCharPointer searchPaths, CCharPointer searchPathSeparator) {

        int childPid;
        childPid = UnistdNoTransitions.fork();
        if (childPid != 0) {
            return childPid;
        }

        // If we are here, we are the child process.
        int failFd = initialFailFd;
        try {
            // In case of an error, we "return" to end up in the finally block below and notify the
            // parent process of the failure.
            final int gotoFinally = -1;

            if (Java_lang_UNIXProcess_Supplement.dup2(stdioFds[0], 0) < 0) {
                return gotoFinally;
            }
            if (Java_lang_UNIXProcess_Supplement.dup2(stdioFds[1], 1) < 0) {
                return gotoFinally;
            }
            if (Java_lang_UNIXProcess_Supplement.dup2(stdioFds[2], 2) < 0) {
                return gotoFinally;
            }

            if (Java_lang_UNIXProcess_Supplement.dup2(failFd, 3) < 0) {
                return gotoFinally;
            }
            failFd = 3;

            // FD_CLOEXEC: close fail pipe on exec() to indicate success to parent
            if (Fcntl.fcntl_no_transition(failFd, Fcntl.F_SETFD(), Fcntl.FD_CLOEXEC()) < 0) {
                return gotoFinally;
            }

            /*
             * opendir() below allocates a file descriptor. We close failFd+1 so it should become
             * the descriptor allocated to opendir() and we can avoid closing it together with the
             * other descriptors.
             */
            final int maxFd = failFd + 1;
            if (UnistdNoTransitions.close(maxFd) < 0) {
                return gotoFinally;
            }

            if (procFdsPath.isNull()) {
                // We have no procfs, resort to close file descriptors by trial and error
                int maxOpenFds = (int) UnistdNoTransitions.sysconf(Unistd._SC_OPEN_MAX());
                for (int fd = maxFd + 1; fd < maxOpenFds; fd++) {
                    if (UnistdNoTransitions.close(fd) != 0 && Errno.errno() != Errno.EBADF()) {
                        return gotoFinally;
                    }
                }
            } else {
                DIR fddir = Dirent.opendir_no_transition(procFdsPath);
                if (fddir.isNull()) {
                    return gotoFinally;
                }
                dirent dirent = WordFactory.pointer(buffer.rawValue());
                direntPointer direntptr = StackValue.get(direntPointer.class);
                int status;
                while ((status = Dirent.readdir_r_no_transition(fddir, dirent, direntptr)) == 0 && direntptr.read().isNonNull()) {
                    CCharPointerPointer endptr = StackValue.get(CCharPointerPointer.class);
                    long fd = LibC.strtol(dirent.d_name(), endptr, 10);
                    if (fd > maxFd && endptr.read().isNonNull() && endptr.read().read() == '\0') {
                        UnistdNoTransitions.close((int) fd);
                    }
                }
                if (status != 0) {
                    Errno.set_errno(status); // readdir_r() does not set errno
                    return gotoFinally;
                }
                Dirent.closedir_no_transition(fddir);
            }

            if (dir.isNonNull()) {
                if (UnistdNoTransitions.chdir(dir) < 0) {
                    return gotoFinally;
                }
            }

            CCharPointerPointer actualEnvp = envp;
            if (actualEnvp.isNull()) {
                actualEnvp = LibCHelper.getEnviron();
            }

            if (LibC.strchr(file, '/').isNonNull()) {
                UnistdNoTransitions.execve(argv.read(0), argv, actualEnvp);
            } else {
                // Scan PATH for the file to execute. We cannot use execvpe()
                // because it is a GNU extension that is not universally available.
                final int fileStrlen = (int) LibC.strlen(file).rawValue();
                int stickyErrno = 0;

                final CCharPointerPointer saveptr = StackValue.get(CCharPointerPointer.class);
                saveptr.write(WordFactory.nullPointer());
                CCharPointer searchDir = LibC.strtok_r(searchPaths, searchPathSeparator, saveptr);
                while (searchDir.isNonNull()) {
                    CCharPointer bufptr = WordFactory.pointer(buffer.rawValue());
                    int len0 = (int) LibC.strlen(searchDir).rawValue();
                    if (len0 + fileStrlen + 2 > buflen) {
                        Errno.set_errno(Errno.ENAMETOOLONG());
                        continue;
                    }
                    if (len0 > 0) {
                        LibC.strcpy(bufptr, searchDir);
                        if (bufptr.read(len0 - 1) != '/') {
                            bufptr.write(len0, (byte) '/');
                            len0++;
                        }
                    }
                    LibC.strcpy(bufptr.addressOf(len0), file);

                    UnistdNoTransitions.execve(bufptr, argv, actualEnvp);

                    int e = Errno.errno();
                    if (e == Errno.EACCES()) {
                        stickyErrno = e; // as exec(): report EACCES unless we succeed later
                    } else if (e == Errno.ENOENT() || e == Errno.ENOTDIR() || e == Errno.ELOOP() || e == Errno.ESTALE() || e == Errno.ENODEV() || e == Errno.ETIMEDOUT()) {
                        // ignore
                    } else {
                        stickyErrno = e;
                        break; // bad
                    }

                    searchDir = LibC.strtok_r(WordFactory.nullPointer(), searchPathSeparator, saveptr);
                }

                if (stickyErrno != 0) {
                    Errno.set_errno(stickyErrno);
                }
            }

            // If we are here, exec certainly failed.
        } catch (Throwable t) {
            Errno.set_errno(Integer.MIN_VALUE);
        } finally {
            try {
                // notify parent of failure
                final int intSize = SizeOf.get(CIntPointer.class);
                CIntPointer pErrno = StackValue.get(intSize);
                pErrno.write(Errno.errno());
                Java_lang_UNIXProcess_Supplement.writeEntirely(failFd, pErrno, WordFactory.unsigned(intSize));
                UnistdNoTransitions.close(failFd);
            } finally {
                UnistdNoTransitions._exit(-1);
            }
        }
        throw VMError.shouldNotReachHere();
    }

    /**
     * For a sequence ("block") of zero-terminated strings, write pointers to the beginning of each
     * string into an array.
     */
    static void gatherCStringPointers(CCharPointer cstrblock, int nbytes, CCharPointerPointer ptrblock, int nptrs) {
        assert nptrs > 0;
        int i = 0;
        int k = 0;
        while (i < nbytes && k < nptrs - 1) {
            ptrblock.write(k, cstrblock.addressOf(i));
            k++;

            do {
                i++;
            } while (i < nbytes && cstrblock.read(i) != '\0');
            i++;
        }
        assert i == nbytes && k == nptrs - 1;
        ptrblock.write(k, WordFactory.nullPointer());
    }

    // Wrappers that retry operations that can be interrupted by a signal or partially completed

    @Uninterruptible(reason = "Called from uninterruptible code.")
    static int dup2(int fd, int fd2) {
        int result;
        do {
            Errno.set_errno(0);
            result = UnistdNoTransitions.dup2(fd, fd2);
        } while (result != fd2 && Errno.errno() == Errno.EINTR());
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    static SignedWord writeEntirely(int fd, PointerBase buf, UnsignedWord count) {
        Pointer ptr = WordFactory.pointer(buf.rawValue());
        final Pointer end = ptr.add(count);
        SignedWord written;
        do {
            Errno.set_errno(0);
            written = UnistdNoTransitions.write(fd, ptr, end.subtract(ptr));
            if (written.greaterThan(0)) {
                ptr = ptr.add((int) written.rawValue());
            }
        } while (ptr.notEqual(end) && (written.notEqual(-1) || Errno.errno() == Errno.EINTR()));
        if (ptr.notEqual(buf)) {
            return WordFactory.signed(ptr.rawValue() - buf.rawValue());
        }
        return written;
    }

    static SignedWord readEntirely(int fd, PointerBase buf, UnsignedWord count) {
        Pointer ptr = WordFactory.pointer(buf.rawValue());
        final Pointer end = ptr.add(count);
        SignedWord read;
        do {
            Errno.set_errno(0);
            read = Unistd.read(fd, ptr, end.subtract(ptr));
            if (read.equal(0)) {
                break; // EOF
            } else if (read.greaterThan(0)) {
                ptr = ptr.add((int) read.rawValue());
            }
        } while (ptr.notEqual(end) && (read.notEqual(-1) || Errno.errno() == Errno.EINTR()));
        if (ptr.notEqual(buf)) {
            return WordFactory.signed(ptr.rawValue() - buf.rawValue());
        }
        return read;
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
    @Alias static final Target_java_lang_ProcessBuilder_NullInputStream INSTANCE = null;
}

@TargetClass(className = "java.lang.ProcessBuilder", innerClass = "NullOutputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessBuilder_NullOutputStream {
    @Alias static final Target_java_lang_ProcessBuilder_NullOutputStream INSTANCE = null;
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
