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
package jdk.graal.compiler.libgraal;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.Supplier;

import jdk.graal.compiler.hotspot.libgraal.RunTime;
import jdk.graal.compiler.word.Word;
import jdk.graal.nativeimage.LibGraalRuntime;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

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
                throw new IllegalArgumentException("is this case really needed? Unsupported class loader for lookup: " + name + " " + classLoader);
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
    /**
     * Used to avoid complaints by javac about casts that appear to violate the Java type system
     * rules but are safe in the context of SVM substitutions.
     */
    @SuppressWarnings({"unused", "unchecked"})
    public static <T> T cast(Object obj, Class<T> toType) {
        return (T) obj;
    }

    @TargetClass(className = "jdk.graal.compiler.serviceprovider.VMSupport", classLoader = LibGraalClassLoaderSupplier.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_graal_compiler_serviceprovider_VMSupport {

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
                JNI.JNIEnv env = Word.unsigned(RunTime.getJNIEnv());
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
        public static void invokeShutdownCallback(String cbClassName, String cbMethodName) {
            JNI.JNIEnv env = Word.unsigned(RunTime.getJNIEnv());
            JNI.JClass cbClass = JNIUtil.findClass(env, JNIUtil.getSystemClassLoader(env),
                            JNIUtil.getBinaryName(cbClassName), true);
            JNI.JMethodID cbMethod = JNIUtil.findMethod(env, cbClass, true, cbMethodName, "()V");
            env.getFunctions().getCallStaticVoidMethodA().call(env, cbClass, cbMethod, StackValue.get(0));
            JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
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
            Thread thread = cast(this, Thread.class);
            if (!RunTime.attachCurrentThread(thread.isDaemon(), null)) {
                throw new InternalError("Couldn't attach to HotSpot runtime");
            }
        }

        @Substitute
        @SuppressWarnings("static-method")
        void afterRun() {
            RunTime.detachCurrentThread(false);
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
        if (!ImageSingletons.contains(LibGraalFeature.class)) {
            return null;
        }
        ClassLoader loader = ImageSingletons.lookup(LibGraalFeature.class).loader;
        if (loader == null) {
            LibGraalRuntime.fatalError("loader is null");
        }
        return loader;
    }
}
