/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.libgraal.LibGraalRuntime;
import org.graalvm.nativeimage.libgraal.hosted.GlobalData;
import org.graalvm.nativeimage.libgraal.hosted.LibGraalLoader;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.libgraal.truffle.HSTruffleCompilerRuntime;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.hotspot.CompilerThreadCanCallJavaScope;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * Implementation of {@link LibGraalSupport} that is only loaded by the libgraal class loader when
 * building libgraal. Only in the libgraal class loader context as libgraal build-time as well as at
 * libgraal runtime is the {@link LibGraalSupport#INSTANCE} non-null.
 */
public final class LibGraalSupportImpl implements LibGraalSupport {

    private final LibGraalLoader libGraalLoader = (LibGraalLoader) getClass().getClassLoader();

    private static HSTruffleCompilerRuntime truffleRuntime;

    public static void registerTruffleCompilerRuntime(HSTruffleCompilerRuntime runtime) {
        GraalError.guarantee(truffleRuntime == null, "cannot register more than one Truffle runtime");
        truffleRuntime = runtime;
    }

    @Override
    public AutoCloseable openCompilationRequestScope() {
        return new LibGraalCompilationRequestScope();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public Supplier<Long> createGlobal(long initialValue) {
        return GlobalData.createGlobal(initialValue);
    }

    /**
     * Performs the following actions around a libgraal compilation:
     * <ul>
     * <li>before: opens a JNIMethodScope to allow Graal compilations of Truffle host methods to
     * call methods on the TruffleCompilerRuntime.</li>
     * <li>after: closes the above JNIMethodScope</li>
     * <li>after: triggers GC weak reference processing as SVM does not use a separate thread for
     * this in libgraal</li>
     * </ul>
     */
    static class LibGraalCompilationRequestScope implements AutoCloseable {
        final JNIMethodScope scope;

        LibGraalCompilationRequestScope() {
            JNI.JNIEnv env = Word.unsigned(getJNIEnv());
            /*
             * This scope is required to allow Graal compilations of host methods to call methods in
             * the TruffleCompilerRuntime. This is, for example, required to find out about
             * Truffle-specific method annotations.
             */
            scope = LibGraalJNIMethodScope.open("<called from VM>", env, false);
        }

        @Override
        public void close() {
            try {
                if (scope != null) {
                    scope.close();
                }
            } finally {
                /*
                 * libgraal doesn't use a dedicated reference handler thread, so trigger the
                 * reference handling manually when a compilation finishes.
                 */
                LibGraalSupportImpl.doReferenceHandling();
            }
        }
    }

    private static long jniEnvironmentOffset = Integer.MAX_VALUE;

    private static long getJniEnvironmentOffset() {
        if (jniEnvironmentOffset == Integer.MAX_VALUE) {
            HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
            HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(store);
            jniEnvironmentOffset = config.getFieldOffset("JavaThread::_jni_environment", Integer.class, "JNIEnv");
        }
        return jniEnvironmentOffset;
    }

    /**
     * Gets the JNIEnv value for the current HotSpot thread.
     */
    private static long getJNIEnv() {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        long offset = getJniEnvironmentOffset();
        long javaThreadAddr = jvmciRuntime.getCurrentJavaThread();
        return javaThreadAddr + offset;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public Map<String, String> getClassModuleMap() {
        return libGraalLoader.getClassModuleMap();
    }

    @Override
    public void processReferences() {
        LibGraalRuntime.processReferences();
    }

    @Override
    public void dumpHeap(String outputFile, boolean live) throws IOException, UnsupportedOperationException {
        VMRuntime.dumpHeap(outputFile, live);
    }

    @Override
    public long getIsolateAddress() {
        return CurrentIsolate.getIsolate().rawValue();
    }

    @Override
    public long getIsolateID() {
        return LibGraalRuntime.getIsolateID();
    }

    /**
     * The set of libgraal options seen on the command line.
     */
    static EconomicSet<String> explicitOptions = EconomicSet.create();

    @Override
    public void notifyOptions(EconomicMap<String, String> settings) {
        MapCursor<String, String> cursor = settings.getEntries();
        while (cursor.advance()) {
            String name = cursor.getKey();
            String stringValue = cursor.getValue();
            Object value;
            if (name.startsWith("X") && stringValue.isEmpty()) {
                name = name.substring(1);
                value = stringValue;
            } else {
                RuntimeOptions.Descriptor desc = RuntimeOptions.getDescriptor(name);
                if (desc == null) {
                    throw new IllegalArgumentException("Could not find option " + name);
                }
                value = desc.convertValue(stringValue);
                explicitOptions.add(name);
            }
            try {
                RuntimeOptions.set(name, value);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    @Override
    public void printOptions(PrintStream out, String namePrefix) {
        Comparator<RuntimeOptions.Descriptor> comparator = Comparator.comparing(RuntimeOptions.Descriptor::name);
        RuntimeOptions.listDescriptors().stream().sorted(comparator).forEach(d -> {
            String assign = explicitOptions.contains(d.name()) ? ":=" : "=";
            OptionValues.printHelp(out, namePrefix,
                            d.name(),
                            RuntimeOptions.get(d.name()),
                            d.valueType(),
                            assign,
                            "[community edition]",
                            List.of(d.help()));
        });
    }

    @Override
    public void initialize() {
        VMRuntime.initialize();
    }

    @Override
    @SuppressWarnings("try")
    public void shutdown(String callbackClassName, String callbackMethodName) {
        try (CompilerThreadCanCallJavaScope ignore = new CompilerThreadCanCallJavaScope(true)) {
            if (callbackClassName != null) {
                JNI.JNIEnv env = Word.unsigned(getJNIEnv());
                JNI.JClass cbClass = JNIUtil.findClass(env, JNIUtil.getSystemClassLoader(env),
                                JNIUtil.getBinaryName(callbackClassName), true);
                JNI.JMethodID cbMethod = JNIUtil.findMethod(env, cbClass, true, callbackMethodName, "()V");
                env.getFunctions().getCallStaticVoidMethodA().call(env, cbClass, cbMethod, StackValue.get(0));
                JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
            }
            if (truffleRuntime != null) {
                JNI.JNIEnv env = Word.unsigned(getJNIEnv());
                truffleRuntime.notifyShutdown(env);
            }
        }
        VMRuntime.shutdown();

    }

    @Override
    public void notifyLowMemoryPoint(boolean suggestFullGC) {
        LibGraalRuntime.notifyLowMemoryPoint(suggestFullGC);
    }

    /**
     * Since reference handling is synchronous in libgraal, explicitly perform it here and then run
     * any code which is expecting to process a reference queue to let it clean up.
     */
    public static void doReferenceHandling() {
        LibGraalRuntime.processReferences();

        /*
         * Thanks to JDK-8346781, this can be replaced with jdk.vm.ci.hotspot.Cleaner.clean() once
         * JDK 21 support is no longer necessary.
         */
        LibGraalSubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.clean();
    }

    @Override
    public void fatalError(String crashMessage) {
        LibGraalRuntime.fatalError(crashMessage);
    }
}
