/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIEnvironmentPointer;
import com.oracle.svm.core.jni.headers.JNIErrors;
import com.oracle.svm.core.jni.headers.JNIJavaVMInitArgs;
import com.oracle.svm.core.jni.headers.JNIJavaVMOption;
import com.oracle.svm.core.jni.headers.JNIJavaVMPointer;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jni.headers.JNIVersion;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.interpreter.InterpreterOptions;
import com.oracle.svm.interpreter.debug.DebuggerEvents;
import com.oracle.svm.jdwp.bridge.ArgFilesOption;
import com.oracle.svm.jdwp.bridge.DebugOptions.Options;
import com.oracle.svm.jdwp.bridge.JDWPEventHandlerBridge;
import com.oracle.svm.jdwp.bridge.Logger;
import com.oracle.svm.jdwp.bridge.NativeToHSJDWPEventHandlerBridge;
import com.oracle.svm.jdwp.bridge.ResidentJDWPFeatureEnabled;
import com.oracle.svm.jdwp.bridge.jniutils.JNI;
import com.oracle.svm.jdwp.bridge.jniutils.JNIMethodScope;
import com.oracle.svm.jdwp.bridge.nativebridge.NativeObjectHandles;
import com.oracle.svm.jdwp.resident.impl.ResidentJDWP;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Signal;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class DebuggingOnDemandHandler implements Signal.Handler {

    private final Options options;
    private static volatile Thread suspendThread;

    private static final int MAX_DEBUGGER_VM_OPTIONS = 32;

    public DebuggingOnDemandHandler(Options options) {
        this.options = options;
    }

    boolean first = true;

    private static JNI.JavaVM debuggerServerJavaVM; // Java VM running the debugger server

    private static final FastThreadLocalWord<JNI.JNIEnv> jniEnvPerThread = FastThreadLocalFactory.createWord("jniEnvPerThread");

    public static JNI.JNIEnv currentThreadJniEnv() {
        JNI.JNIEnv currentJniEnv = jniEnvPerThread.get();
        if (currentJniEnv.isNull()) {
            JNI.JNIEnvPointer attachedJniEnvPointer = StackValue.get(JNI.JNIEnvPointer.class);
            // Enter the debugger server (HotSpot or isolate) as a daemon thread.
            debuggerServerJavaVM.getFunctions().getAttachCurrentThreadAsDaemon().call(debuggerServerJavaVM, attachedJniEnvPointer, Word.nullPointer());
            currentJniEnv = attachedJniEnvPointer.readJNIEnv();
            jniEnvPerThread.set(currentJniEnv);
        }
        return currentJniEnv;
    }

    @Override
    public void handle(Signal sig) {
        if (!first) {
            ResidentJDWP.LOGGER.log("[DebuggingOnDemand] received USR2 signal, but JDWP server already spawned?");
            return;
        }
        first = false;
        ResidentJDWP.LOGGER.log("[DebuggingOnDemand] received USR2 signal, spawning JDWP server");
        spawnJDWPThread();
    }

    private static class JDWPServerThread extends Thread {
        private final Path libraryPath;
        private final long initialThreadId;
        private final Options options;

        JDWPServerThread(Path libraryPath, long initialThreadId, Options options) {
            super("jdwp-server");
            this.libraryPath = libraryPath;
            this.initialThreadId = initialThreadId;
            this.options = options;
        }

        @Override
        public void run() {
            DebuggingOnDemandHandler.spawnJDWPServer(libraryPath, initialThreadId, options);
            ThreadStartDeathSupport.get().setDebuggerThreadServer(null);
        }
    }

    @SuppressFBWarnings(value = {"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"}, justification = "Intentional.")
    public void spawnJDWPThread() {
        long initialThreadId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(Thread.currentThread());

        // Try to find native library before spawning the JDWP server thread, to fail early in the
        // case it cannot be found.
        Path libraryPath = DebuggingOnDemandHandler.findLibraryPath(options);
        assert Files.isRegularFile(libraryPath);

        Thread jdwpServerThread = new JDWPServerThread(libraryPath, initialThreadId, options);
        ThreadStartDeathSupport.get().setDebuggerThreadServer(jdwpServerThread);
        jdwpServerThread.setDaemon(true);
        jdwpServerThread.start();
        if (options.suspend()) {
            suspendThread = Thread.currentThread();
            synchronized (DebuggingOnDemandHandler.class) {
                try {
                    DebuggingOnDemandHandler.class.wait();
                } catch (InterruptedException e) {
                    ResidentJDWP.LOGGER.log("[DebuggingOnDemand] Interrupted initial suspend.");
                }
            }
        }
    }

    public interface JNICreateJavaVMPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIJavaVMPointer jvmptr, JNIEnvironmentPointer env, JNIJavaVMInitArgs args);
    }

    public interface CallStaticVoidMethodFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JNIEnvironment env, JNIObjectHandle objOrClass, JNIMethodId methodId);
    }

    public interface CallStaticObjectMethodFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JNI.JObject invoke(JNIEnvironment env, JNIObjectHandle objOrClass, JNIMethodId methodId);
    }

    @SuppressWarnings("try")
    public static void spawnJDWPServer(Path libraryPath, long initialThreadId, Options jdwpOptions) {
        assert libraryPath != null;
        ResidentJDWP.LOGGER = new Logger(jdwpOptions.tracing(), "[ResidentJDWP]", System.err);

        String debuggerArgs = jdwpOptions.vmOptions();
        List<String> vmOptions = new ArrayList<>();
        if (debuggerArgs != null && !debuggerArgs.isBlank()) {
            ArgFilesOption parser = new ArgFilesOption();
            for (String arg : debuggerArgs.split("(\\R|\\s)+")) {
                vmOptions.addAll(parser.process(arg));
            }
        } else {
            if ("jvm".equals(jdwpOptions.mode())) {
                throw new IllegalArgumentException(
                                "The JDWP 'vm.options' option is not set or empty. It must contain at least the -Djava.class.path for the debugger, note that it also supports @argFiles arguments.");
            }
        }

        if (vmOptions.size() > MAX_DEBUGGER_VM_OPTIONS) {
            throw VMError.shouldNotReachHere("'vm.options' contains more than " + MAX_DEBUGGER_VM_OPTIONS + " elements (including @argFile expansion)");
        }

        try (CTypeConversion.CCharPointerHolder declaringClass = CTypeConversion.toCString("com/oracle/svm/jdwp/server/JDWPServer");
                        CTypeConversion.CCharPointerHolder hsMethodName = CTypeConversion.toCString("createInstance");
                        CTypeConversion.CCharPointerHolder hsSignature = CTypeConversion.toCString("()Lcom/oracle/svm/jdwp/bridge/JDWPEventHandlerBridge;");
                        CTypeConversion.CCharPointerPointerHolder hsVMOptions = CTypeConversion.toCStrings(vmOptions.toArray(new String[0]))) {

            ResidentJDWP.LOGGER.log("Loading libraryPath=" + libraryPath);
            ResidentJDWP.LOGGER.log("jdwpOptions=" + jdwpOptions.jdwpOptions());
            ResidentJDWP.LOGGER.log("additionalOptions=" + jdwpOptions.additionalOptions());

            JNIJavaVMInitArgs args = StackValue.get(JNIJavaVMInitArgs.class);

            JNIJavaVMOption options;
            args.setNOptions(vmOptions.size());
            // The number of elements must still be constant for the stack allocation.
            options = StackValue.get(MAX_DEBUGGER_VM_OPTIONS, JNIJavaVMOption.class);

            for (int i = 0; i < vmOptions.size(); i++) {
                CCharPointer option = hsVMOptions.get().read(i);
                options.addressOf(i).setOptionString(option);
            }

            args.setOptions(options);
            args.setIgnoreUnrecognized(false);

            args.setVersion(JNIVersion.JNI_VERSION_10());
            args.setIgnoreUnrecognized(true);

            JNIJavaVMPointer jvmptr = StackValue.get(JNIJavaVMPointer.class);
            JNIEnvironmentPointer dbgEnv = StackValue.get(JNIEnvironmentPointer.class);

            PlatformNativeLibrarySupport.NativeLibrary library = PlatformNativeLibrarySupport.singleton().createLibrary(libraryPath.toString(), false);
            library.load();

            DebuggingOnDemandHandler.JNICreateJavaVMPointer symCreateJavaVM = (DebuggingOnDemandHandler.JNICreateJavaVMPointer) library.findSymbol("JNI_CreateJavaVM");

            int result = symCreateJavaVM.call(jvmptr, dbgEnv, args);

            if (result != JNIErrors.JNI_OK()) {
                Log.log().string("CreateJavaVM failed: ").signed(result).newline();
                LibC.exit(LibC.EXIT_CODE_ABORT);
            }

            debuggerServerJavaVM = (JNI.JavaVM) jvmptr.read();

            JNIObjectHandle jhJDWPServerClass = dbgEnv.read().getFunctions().getFindClass().invoke(dbgEnv.read(), declaringClass.get());
            abortOnJNIException(dbgEnv);

            JNIMethodId jdwpCreateInstance = dbgEnv.read().getFunctions().getGetStaticMethodID().invoke(dbgEnv.read(), jhJDWPServerClass, hsMethodName.get(), hsSignature.get());
            abortOnJNIException(dbgEnv);

            DebuggingOnDemandHandler.CallStaticObjectMethodFunctionPointer entryPointFunctionPointer = (DebuggingOnDemandHandler.CallStaticObjectMethodFunctionPointer) dbgEnv.read().getFunctions()
                            .getCallStaticObjectMethod();
            JNI.JObject handle = entryPointFunctionPointer.invoke(dbgEnv.read(), jhJDWPServerClass, jdwpCreateInstance);
            abortOnJNIException(dbgEnv);

            JDWPEventHandlerBridge jdwpEventHandler = NativeToHSJDWPEventHandlerBridge.createNativeToHS((JNI.JNIEnv) dbgEnv.read(), handle);
            abortOnJNIException(dbgEnv);

            long isolate = CurrentIsolate.getIsolate().rawValue();

            // Ensures that the callbacks To HotSpot have a JNIEnv available via JNIMethodScope.
            DebuggerEvents.singleton().setEventHandler(new EnterHSEventHandler(jdwpEventHandler));

            ThreadStartDeathSupport.get().setListener(new ThreadStartDeathSupport.Listener() {
                @Override
                public void threadStarted() {
                    IsolateThread isolateThread = CurrentIsolate.getCurrentThread();
                    Thread thread = ThreadStartDeathSupport.get().filterAppThread(isolateThread);
                    if (thread == null) {
                        return;
                    }
                    long threadId = JDWPBridgeImpl.getIds().toId(thread);
                    ResidentJDWP.LOGGER.log("[StartDeathSupport] threadStarted[" + threadId + "](" + thread.getName() + ")");
                    try (JNIMethodScope ignored = new JNIMethodScope("JDWPServer::onThreadStart", currentThreadJniEnv())) {
                        jdwpEventHandler.onThreadStart(threadId);
                    }
                }

                @Override
                @Uninterruptible(reason = "Used in ThreadListenerSupport.", calleeMustBe = false)
                public void threadDied() {
                    IsolateThread isolateThread = CurrentIsolate.getCurrentThread();
                    // We need to inform the server through JNI, interruptible code is called
                    threadDied(isolateThread);
                }

                private void threadDied(IsolateThread isolateThread) {
                    Thread thread = ThreadStartDeathSupport.get().filterAppThread(isolateThread);
                    if (thread == null) {
                        return;
                    }
                    long threadId = JDWPBridgeImpl.getIds().toId(thread);
                    ResidentJDWP.LOGGER.log("[StartDeathSupport] threadDied[" + threadId + "](" + thread.getName() + ")");
                    try (JNIMethodScope ignored = new JNIMethodScope("JDWPServer::onThreadDeath", currentThreadJniEnv())) {
                        jdwpEventHandler.onThreadDeath(threadId);
                    }
                }

                @Override
                public void vmDied() {
                    ResidentJDWP.LOGGER.log("[StartDeathSupport] vmDied()");
                    try (JNIMethodScope ignored = new JNIMethodScope("JDWPServer::onVMDeath", currentThreadJniEnv())) {
                        jdwpEventHandler.onVMDeath();
                    }
                }

            });

            assert DebuggerEvents.singleton().getEventHandler() != null;
            Path metadataPath = DebuggerSupport.getMetadataFilePath();
            String metadataHashString = DebuggerSupport.getMetadataHashString();
            try (JNIMethodScope ignored = new JNIMethodScope("JDWPServer::spawnServer", currentThreadJniEnv())) {
                long jdwpBridgeHandle = createJDWPBridgeHandle();
                jdwpEventHandler.spawnServer(
                                jdwpOptions.jdwpOptions(),
                                jdwpOptions.additionalOptions(),
                                isolate,
                                initialThreadId,
                                jdwpBridgeHandle,
                                metadataHashString,
                                metadataPath.toString(),
                                jdwpOptions.tracing());
            }
        }
    }

    /**
     * Finds a Java home from environment variables.
     *
     * @return path to Java home, or {@code null} if cannot be found
     */
    static Path findJavaHome() {
        String javaHome = System.getenv("JDWP_SERVER_JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getenv("GRAALVM_HOME");
            if (javaHome == null) {
                javaHome = System.getenv("JAVA_HOME");
            }
        }
        return (javaHome != null)
                        ? Path.of(javaHome)
                        : null;
    }

    /**
     * Finds the given {@link System#mapLibraryName(String) library name} (file name), in the given
     * search paths. The search paths are inspected in the given order, search paths can point to
     * regular files or directories.
     *
     * <p>
     * A file search path is valid, if it exists and have the same file name as the specified
     * library name. A directory search is valid, if it exists contains the specified library file
     * name.
     *
     * @param libraryName file name of the library, platform-dependent, as given by
     *            {@link System#mapLibraryName(String)}
     * @param throwIfNotFound flag to throw {@link IllegalArgumentException} if the library is not
     *            found
     * @param searchPaths paths to search, files of directories
     * @return the path of the library, guaranteed to be an existing regular file, or null if the
     *         library was not found (and {@code throwIfNotFound} is false).
     *
     * @throws IllegalArgumentException if {@code throwIfNotFound} and the library was not found in
     *             the given search paths
     */
    private static Path findLibrary(String libraryName, boolean throwIfNotFound, List<Path> searchPaths) {
        for (Path path : searchPaths) {
            Path fileName = path.getFileName();
            if (Files.isRegularFile(path) && fileName != null && libraryName.equals(fileName.toString())) {
                return path;
            } else if (Files.isDirectory(path)) {
                Path libraryPath = path.resolve(libraryName);
                if (Files.isRegularFile(libraryPath)) {
                    return libraryPath;
                }
            }
        }

        if (throwIfNotFound) {
            throw new IllegalArgumentException(libraryName + " not found in search path: " + searchPaths);
        }

        return null;
    }

    /**
     * Returns the platform-dependent path (Linux/Mac/Windows) to the native libraries directory in
     * a Java home.
     *
     * @return path to the native libraries directory, or {@code null} if the Java home is
     *         {@code null}
     */
    private static Path librariesInJavaHome(Path javaHome) {
        if (javaHome == null) {
            return null;
        }
        return javaHome.resolve(OS.WINDOWS.isCurrent() ? "bin" : "lib");
    }

    /**
     * Returns the platform-dependent path (Linux/Mac/Windows) to {@code lib:jvm} library within a
     * Java home. A Java home may contain several implementations of {@code lib:jvm} e.g.
     * {@code "server" | "client" | "truffle"}, use {@code jvmSubDirectory} to select which one.
     *
     * @return path to the lib:jvm shared library, or {@code null} if the Java home is {@code null}
     */
    private static Path jvmLibraryInJavaHome(Path javaHome, String jvmSubDirectory) {
        Path libraries = librariesInJavaHome(javaHome);
        if (libraries == null) {
            return null;
        }
        return libraries.resolve(jvmSubDirectory);
    }

    /**
     * Returns the platform-dependent path (Linux/Mac/Windows) to {@code lib:jvm} library within a
     * Java home. Picks the {@ocde lib:jvm} "server" configuration.
     *
     * @return path to the lib:jvm shared library, or {@code null} if the Java home is {@code null}
     */
    private static Path jvmLibraryInJavaHome(Path javaHome) {
        return jvmLibraryInJavaHome(javaHome, "server");
    }

    private static List<Path> filterValidPaths(Path... paths) {
        List<Path> validPaths = new ArrayList<>();
        for (Path path : paths) {
            if (path != null && Files.exists(path)) {
                validPaths.add(path);
            }
        }
        return validPaths;
    }

    /**
     * Finds the native library path used launch the JDWP server. Supports both
     * {@link Options#mode() native or jvm modes}.
     *
     * <p>
     * In {@link Options#mode() native mode}, the search paths are as follows:
     * <ol>
     * <li>The specified {@link Options#libraryPath() library path}
     * <li>The native executable directory, <strong>NOT the current working directory</strong></li>
     * <li>Assumes {@link Options#libraryPath() library path} is a Java home, search for
     * {@code lib:svmjdwp} there.
     * <li>Finds a Java home from environment variables, search for {@code lib:svmjdwp} there.
     * </ol>
     *
     * <p>
     * In {@link Options#mode() jvm mode}, the search paths are as follows:
     * <ol>
     * <li>The specified {@link Options#libraryPath() library path}
     * <li>Assumes {@link Options#libraryPath() library path} is a Java home, search for
     * {@code lib:jvm} there.
     * <li>Finds a Java home from environment variables, search for {@code lib:jvm} there.
     * </ol>
     *
     * @throws IllegalArgumentException if the native library cannot be found
     *
     * @return path the to native library used to run the JDWP server, {@code lib:svmjdwp} or
     *         {@code lib:jvm} depending on the {@link Options#mode()}
     */
    private static Path findLibraryPath(Options jdwpOptions) {
        Path libraryPath = null;
        if (jdwpOptions.libraryPath() != null) {
            libraryPath = Path.of(jdwpOptions.libraryPath());
        }

        String libraryName;
        List<Path> searchPaths;
        if ("jvm".equals(jdwpOptions.mode())) {
            libraryName = System.mapLibraryName("jvm");
            searchPaths = filterValidPaths(
                            libraryPath,
                            jvmLibraryInJavaHome(libraryPath),
                            jvmLibraryInJavaHome(findJavaHome()));
        } else {
            assert "native".equals(jdwpOptions.mode());
            libraryName = System.mapLibraryName("svmjdwp");
            searchPaths = filterValidPaths(
                            libraryPath,
                            Path.of(ProcessProperties.getExecutableName()).getParent(),
                            librariesInJavaHome(libraryPath),
                            librariesInJavaHome(findJavaHome()));
        }

        return findLibrary(libraryName, true, searchPaths);
    }

    private static long createJDWPBridgeHandle() {
        VMError.guarantee(JDWPOptions.JDWP.getValue());
        VMError.guarantee(InterpreterOptions.DebuggerWithInterpreter.getValue());
        return NativeObjectHandles.create(new JDWPBridgeImpl());
    }

    static long toPrimitiveOrId(JavaKind kind, Object object) {
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean((boolean) object).getRawValue();
            case Byte -> JavaConstant.forByte((byte) object).getRawValue();
            case Short -> JavaConstant.forShort((short) object).getRawValue();
            case Char -> JavaConstant.forChar((char) object).getRawValue();
            case Int -> JavaConstant.forInt((int) object).getRawValue();
            case Float -> JavaConstant.forFloat((float) object).getRawValue();
            case Long -> JavaConstant.forLong((long) object).getRawValue();
            case Double -> JavaConstant.forDouble((double) object).getRawValue();
            case Object -> JDWPBridgeImpl.getIds().toId(object);
            case Void -> {
                assert object == null;
                yield 0L; // null
            }
            case Illegal -> throw VMError.shouldNotReachHere("illegal return kind");
        };
    }

    private static void abortOnJNIException(JNIEnvironmentPointer dbgEnv) {
        if (dbgEnv.read().getFunctions().getExceptionCheck().invoke(dbgEnv.read())) {
            dbgEnv.read().getFunctions().getExceptionDescribe().invoke(dbgEnv.read());
            LibC.exit(LibC.EXIT_CODE_ABORT);
        }
    }

    public static boolean suspendDoneOnShellSide() {
        Thread t = suspendThread;
        suspendThread = null;
        if (t != null) {
            t.interrupt();
            return true;
        } else {
            return false;
        }
    }

    /*
     * GR-55105: This shouldn't be needed. Only inner classes are processed by
     * com.oracle.svm.hosted.ImageClassLoader#findSystemElements for some reason.
     */
    @SuppressWarnings("unused")
    private static final class EntryPointHolder {
        @Uninterruptible(reason = "Dummy symbol to make HotSpot's native method linking to look for symbols in the main executable. Required to run the JDWP server (debugger) on HotSpot")
        @CEntryPoint(name = "JNI_OnLoad_DEFAULT_NAMESPACE", include = ResidentJDWPFeatureEnabled.class)
        @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
        @SuppressWarnings("unused")
        public static int onLoadDefaultNamespace(JNI.JavaVM vm, PointerBase reserved) {
            return JNIVersion.JNI_VERSION_10();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_JDWPJNIConfig_attachCurrentThread", //
                        builtin = CEntryPoint.Builtin.ATTACH_THREAD, //
                        include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings("unused")
        public static native IsolateThread attachCurrentThread(JNI.JNIEnv jniEnv, JNI.JClass clazz, Isolate isolate);
    }
}
