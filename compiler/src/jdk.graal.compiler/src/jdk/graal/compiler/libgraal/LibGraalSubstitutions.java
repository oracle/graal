/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.io.PrintStream;
import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.function.Supplier;

import jdk.graal.compiler.word.Word;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.JDKLatest;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hotspot.LibGraalJNIMethodScope;

class LibGraalJVMCISubstitutions {

    @TargetClass(className = "jdk.vm.ci.services.Services", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_services_Services {
        /**
         * Ensures field returns false if seen by the analysis.
         */
        // Checkstyle: stop
        @Alias //
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
        public static boolean IS_BUILDING_NATIVE_IMAGE = false;
        // Checkstyle: resume

        /*
         * Static final boolean field Services.IS_IN_NATIVE_IMAGE is used in many places in the
         * JVMCI codebase to switch between the different implementations needed for regular use (a
         * built-in module jdk.graal.compiler in the JVM) or as part of libgraal.
         */
        // Checkstyle: stop
        @Alias //
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
        public static boolean IS_IN_NATIVE_IMAGE = true;
        // Checkstyle: resume

        /*
         * Reset Services.savedProperties to null so that we cannot get the hosted savedProperties
         * into the libgraal image-heap. This also guarantees that at libgraal-runtime the
         * savedProperties are initialized with the system properties state of the JVM that uses
         * libgraal.
         */
        @Alias //
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
        private static Map<String, String> savedProperties;
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.Cleaner", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_hotspot_Cleaner {
        /*
         * Ensure the ReferenceQueue<Object> instance in Cleaner.queue that is in libgraal is not
         * tainted by any use of the Cleaner class at image build-time.
         */
        @Alias //
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, isFinal = true, declClass = ReferenceQueue.class)//
        private static ReferenceQueue<Object> queue;

        /*
         * Make package-private clean() accessible so that it can be called from
         * LibGraalEntryPoints.doReferenceHandling().
         */
        @Alias
        static native void clean();
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.CompilerToVM", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = {LibGraalFeature.IsEnabled.class, JDKLatest.class})
    static final class Target_jdk_vm_ci_hotspot_CompilerToVM {
        /*
         * For libgraal the implementation of CompilerToVM.lookupType needs to take into account
         * that the passed-in classloader can also be an instance of LibGraalClassLoader. Checking
         * if that classLoader is the same as the one of the HotSpotResolvedJavaType class itself
         * (which is the LibGraalClassLoader) takes care of that.
         */
        @Substitute
        Target_jdk_vm_ci_hotspot_HotSpotResolvedJavaType lookupType(ClassLoader classLoader, String name) throws NoClassDefFoundError {
            int accessingClassLoader;
            if (classLoader == null) {
                accessingClassLoader = 0;
            } else if (classLoader == ClassLoader.getPlatformClassLoader()) {
                accessingClassLoader = 1;
            } else if (classLoader == ClassLoader.getSystemClassLoader()) {
                accessingClassLoader = 2;
            } else if (classLoader == getClass().getClassLoader()) {
                accessingClassLoader = 2;
            } else {
                throw new IllegalArgumentException("Unsupported class loader for lookup: " + classLoader);
            }
            return lookupType(name, null, 0L, accessingClassLoader, true);
        }

        @Alias
        native Target_jdk_vm_ci_hotspot_HotSpotResolvedJavaType lookupType(String name, Target_jdk_vm_ci_hotspot_HotSpotResolvedObjectTypeImpl accessingClass,
                        long accessingKlassPointer, int accessingClassLoader, boolean resolve) throws NoClassDefFoundError;
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.HotSpotResolvedJavaType", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_hotspot_HotSpotResolvedJavaType {
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_hotspot_HotSpotResolvedObjectTypeImpl {
    }
}

public class LibGraalSubstitutions {

    private static final String GLOBAL_ATOMIC_LONG = "jdk.graal.compiler.serviceprovider.GlobalAtomicLong";

    @TargetClass(className = GLOBAL_ATOMIC_LONG, classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_serviceprovider_GlobalAtomicLong {

        @Inject//
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = GlobalAtomicLongAddressProvider.class) //
        private CGlobalData<Pointer> addressSupplier;

        @Delete private long address;

        /*
         * Graal class GlobalAtomicLong uses a java.lang.ref.Cleaner in static field
         * GlobalAtomicLong.cleaner to ensure all native memory allocated by GlobalAtomicLong
         * instances get freed when the instances are garbage collected.
         *
         * Native image does not support java.lang.ref.Cleaner. Instead, we can use a
         * CGlobalData<Pointer> per GlobalAtomicLong instance if we can guarantee that no instances
         * are created at libgraal-runtime. I.e. all GlobalAtomicLongs in the libgraal-image are
         * image-heap objects.
         */
        @Delete private static Cleaner cleaner;

        /**
         * Delete the constructor to ensure instances of {@code GlobalAtomicLong} cannot be created
         * at runtime.
         */
        @Substitute
        @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
        @SuppressWarnings({"unused", "static-method"})
        public void constructor(long initialValue) {
            throw VMError.unsupportedFeature("Cannot create " + GLOBAL_ATOMIC_LONG + " objects in native image runtime");
        }

        @Substitute
        private long getAddress() {
            return addressSupplier.get().rawValue();
        }
    }

    /*
     * The challenge with using CGlobalData<Pointer> for GlobalAtomicLongs is that we do have to
     * make sure they get initialized with the correct initialValue for a given GlobalAtomicLong
     * instance at image build-time. This FieldValueTransformer ensures that.
     */
    @Platforms(HOSTED_ONLY.class)
    private static final class GlobalAtomicLongAddressProvider implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            long initialValue;
            try {
                initialValue = (long) ImageSingletons.lookup(LibGraalFeature.class).handleGlobalAtomicLongGetInitialValue.invoke(receiver);
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
            return CGlobalDataFactory.createWord(Word.unsigned(initialValue), null, true);
        }
    }

    @TargetClass(className = "jdk.graal.compiler.serviceprovider.VMSupport", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_serviceprovider_VMSupport {

        @Substitute
        public static long getIsolateAddress() {
            return CurrentIsolate.getIsolate().rawValue();
        }

        @Substitute
        public static long getIsolateID() {
            return ImageSingletons.lookup(IsolateSupport.class).getIsolateID();
        }

        /**
         * Performs the following actions around a libgraal compilation:
         * <ul>
         * <li>before: opens a JNIMethodScope to allow Graal compilations of Truffle host methods to
         * call methods on the TruffleCompilerRuntime.</li>
         * <li>after: closes the above JNIMethodScope</li>
         * <li>after: triggers GC weak reference processing as SVM does not use a separate thread
         * for this in libgraal</li>
         * </ul>
         */
        static class LibGraalCompilationRequestScope implements AutoCloseable {
            final JNIMethodScope scope;

            LibGraalCompilationRequestScope() {
                JNI.JNIEnv env = LibGraalEntryPoints.getJNIEnv();
                /*
                 * This scope is required to allow Graal compilations of host methods to call
                 * methods in the TruffleCompilerRuntime. This is, for example, required to find out
                 * about Truffle-specific method annotations.
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
                     * libgraal doesn't use a dedicated reference handler thread, so we trigger the
                     * reference handling manually when a compilation finishes.
                     */
                    LibGraalEntryPoints.doReferenceHandling();
                }
            }
        }

        @Substitute
        public static AutoCloseable getCompilationRequestScope() {
            return new LibGraalCompilationRequestScope();
        }

        @Substitute
        public static void fatalError(String message, int delayMS) {
            LogHandler handler = ImageSingletons.lookup(LogHandler.class);
            if (handler instanceof FunctionPointerLogHandler) {
                try {
                    Thread.sleep(delayMS);
                } catch (InterruptedException e) {
                    // ignore
                }
                VMError.shouldNotReachHere(message);
            }
        }

        @Substitute
        public static void startupLibGraal() {
            VMRuntime.initialize();
        }

        @Substitute
        public static void shutdownLibGraal() {
            VMRuntime.shutdown();
        }

        @Substitute
        public static void invokeShutdownCallback(String cbClassName, String cbMethodName) {
            JNI.JNIEnv env = LibGraalEntryPoints.getJNIEnv();
            JNI.JClass cbClass = JNIUtil.findClass(env, JNIUtil.getSystemClassLoader(env),
                            JNIUtil.getBinaryName(cbClassName), true);
            JNI.JMethodID cbMethod = JNIUtil.findMethod(env, cbClass, true, cbMethodName, "()V");
            env.getFunctions().getCallStaticVoidMethodA().call(env, cbClass, cbMethod, StackValue.get(0));
            JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
        }

        @Substitute
        public static void notifyLowMemoryPoint(boolean hintFullGC, boolean forceFullGC) {
            if (forceFullGC) {
                Heap.getHeap().getGC().collectCompletely(GCCause.JavaLangSystemGC);
            } else {
                Heap.getHeap().getGC().collectionHint(hintFullGC);
            }
            LibGraalEntryPoints.doReferenceHandling();
        }
    }

    @TargetClass(className = "jdk.graal.compiler.phases.BasePhase", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_phases_BasePhase {

        /*
         * Redirect method to image build-time pre-computed statistics in LibGraalCompilerSupport.
         */
        @Substitute
        static Target_jdk_graal_compiler_phases_BasePhase_BasePhaseStatistics getBasePhaseStatistics(Class<?> clazz) {
            Object result = LibGraalCompilerSupport.get().getBasePhaseStatistics().get(clazz);
            if (result == null) {
                throw VMError.shouldNotReachHere(String.format("Missing statistics for phase class: %s%n", clazz.getName()));
            }
            return SubstrateUtil.cast(result, Target_jdk_graal_compiler_phases_BasePhase_BasePhaseStatistics.class);
        }
    }

    @TargetClass(className = "jdk.graal.compiler.phases.BasePhase", innerClass = "BasePhaseStatistics", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_phases_BasePhase_BasePhaseStatistics {
    }

    @TargetClass(className = "jdk.graal.compiler.lir.phases.LIRPhase", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_lir_phases_LIRPhase {

        /*
         * Redirect method to image build-time pre-computed statistics in LibGraalCompilerSupport.
         */
        @Substitute
        static Target_jdk_graal_compiler_lir_phases_LIRPhase_LIRPhaseStatistics getLIRPhaseStatistics(Class<?> clazz) {
            Object result = LibGraalCompilerSupport.get().getLirPhaseStatistics().get(clazz);
            if (result == null) {
                throw VMError.shouldNotReachHere(String.format("Missing statistics for phase class: %s%n", clazz.getName()));
            }
            return SubstrateUtil.cast(result, Target_jdk_graal_compiler_lir_phases_LIRPhase_LIRPhaseStatistics.class);
        }
    }

    @TargetClass(className = "jdk.graal.compiler.lir.phases.LIRPhase", innerClass = "LIRPhaseStatistics", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_lir_phases_LIRPhase_LIRPhaseStatistics {
    }

    @TargetClass(className = "jdk.graal.compiler.graph.NodeClass", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_graph_NodeClass {

        @Alias //
        private String shortName;

        /*
         * All node-classes in libgraal are in the image-heap and were already fully initialized at
         * build-time. The shortName accessor method can be reduced to a simple field access.
         */
        @Substitute
        public String shortName() {
            assert shortName != null;
            return shortName;
        }
    }

    @TargetClass(className = "jdk.graal.compiler.hotspot.HotSpotGraalOptionValues", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_hotspot_HotSpotGraalOptionValues {

        @Substitute
        private static void notifyLibgraalOptions(Map<String, String> vmOptionSettings) {
            LibGraalEntryPoints.initializeOptions(vmOptionSettings);
        }

        @Substitute
        private static void printLibgraalProperties(PrintStream out, String prefix) {
            LibGraalEntryPoints.printOptions(out, prefix);
        }
    }

    @TargetClass(className = "jdk.graal.compiler.core.GraalServiceThread", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_core_GraalServiceThread {
        @Substitute()
        void beforeRun() {
            Thread thread = SubstrateUtil.cast(this, Thread.class);
            if (!LibGraalEntryPoints.attachCurrentThread(thread.isDaemon(), null)) {
                throw new InternalError("Couldn't attach to HotSpot runtime");
            }
        }

        @Substitute
        @SuppressWarnings("static-method")
        void afterRun() {
            LibGraalEntryPoints.detachCurrentThread(false);
        }
    }
}

/*
 * This supplier is used by all LibGraalSubstitutions and ensures that the substitution target
 * classes are classes from the LibGraalClassLoader (instead of hosted Graal & JVMCI classes).
 */
@Platforms(HOSTED_ONLY.class)
class LibGraalClassLoaderSupplier implements Supplier<ClassLoader> {
    @Override
    public ClassLoader get() {
        ClassLoader loader = ImageSingletons.lookup(LibGraalFeature.class).loader;
        VMError.guarantee(loader != null);
        return loader;
    }
}
