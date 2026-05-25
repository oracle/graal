/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.MuslLibC;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.shared.util.SubstrateUtil;

/*
 * Substituting the entire class because the original class has fields capturing
 * state from the image generator that must not leak into the image heap.
 */
@Substitute
@TargetClass(value = jdk.internal.loader.NativeLibraries.class, onlyWith = ClassRegistries.IgnoresClassLoader.class)
final class Target_jdk_internal_loader_NativeLibraries {

    // needs to be explicitly deleted (GR-58623)
    @Delete @SuppressWarnings("unused") static final boolean $assertionsDisabled = false;

    @Substitute
    public static Target_jdk_internal_loader_NativeLibraries newInstance(@SuppressWarnings("unused") ClassLoader loader) {
        return new Target_jdk_internal_loader_NativeLibraries();
    }

    @Substitute
    @SuppressWarnings("static-method")
    public long find(String name) {
        return NativeLibrarySupport.singleton().findSymbol(name).rawValue();
    }
}

/*
 * When class lookup is class-loader aware, keep the JDK implementation and only refresh the hosted
 * collections that would otherwise leak into the image heap.
 */
@TargetClass(value = jdk.internal.loader.NativeLibraries.class, onlyWith = ClassRegistries.RespectsClassLoader.class)
final class Target_jdk_internal_loader_NativeLibraries_RespectsClassLoader {

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class, isFinal = true) //
    Map<String, jdk.internal.loader.NativeLibrary> libraries;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = NewLoadedLibraryNamesComputer.class, isFinal = true) //
    static Set<String> loadedLibraryNames;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class, isFinal = true) //
    static Map<String, ReentrantLock> nativeLibraryLockMap;
}

@TargetClass(value = jdk.internal.loader.NativeLibraries.class, onlyWith = StaticMuslWithClassLoaderAwareNativeLibraries.class)
final class Target_jdk_internal_loader_NativeLibraries_StaticMusl {

    @Substitute
    private static boolean load(Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl impl, String name, boolean isBuiltin, boolean throwExceptionIfFail) {
        if (isBuiltin) {
            try {
                impl.handle = 0;
                impl.jniVersion = NativeLibrarySupport.singleton().loadBuiltinLibrary(name);
                return true;
            } catch (UnsatisfiedLinkError e) {
                if (throwExceptionIfFail) {
                    throw e;
                }
                return false;
            }
        }
        return originalLoad(impl, name, false, throwExceptionIfFail);
    }

    @Alias
    @TargetElement(name = "load")
    private static native boolean originalLoad(Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl impl, String name, boolean isBuiltin, boolean throwExceptionIfFail);
}

@TargetClass(value = jdk.internal.loader.NativeLibraries.class, innerClass = "NativeLibraryImpl", onlyWith = StaticMuslWithClassLoaderAwareNativeLibraries.class)
final class Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl {
    @Alias long handle;
    @Alias int jniVersion;
}

final class StaticMuslWithClassLoaderAwareNativeLibraries implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return SubstrateOptions.StaticExecutable.getValue() && LibCBase.targetLibCIs(MuslLibC.class) && ClassRegistries.respectClassLoader();
    }
}

@TargetClass(value = jdk.internal.loader.NativeLibraries.class, innerClass = "NativeLibraryContext")
final class Target_jdk_internal_loader_NativeLibraries_NativeLibraryContext {

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class) //
    static Map<Thread, Deque<jdk.internal.loader.NativeLibrary>> nativeLibraryThreadContext;
}

final class NewLoadedLibraryNamesComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return ConcurrentHashMap.<String> newKeySet();
    }
}

@Substitute
@TargetClass(value = jdk.internal.loader.NativeLibraries.class, innerClass = "LibraryPaths", onlyWith = ClassRegistries.RespectsClassLoader.class)
final class Target_jdk_internal_loader_NativeLibraries_LibraryPaths {
    // Checkstyle: stop
    @Substitute static String[] USER_PATHS;
    @Substitute static String[] SYS_PATHS;
    // Checkstyle: resume

    static {
        if (!SubstrateUtil.HOSTED) {
            USER_PATHS = Target_jdk_internal_loader_ClassLoaderHelper.parsePath(Target_jdk_internal_util_StaticProperty.javaLibraryPath());
            String[] sysPath = Target_jdk_internal_loader_ClassLoaderHelper.parsePath(Target_jdk_internal_util_StaticProperty.sunBootLibraryPath());
            /*
             * Keep the image directory as an SVM-specific fallback for shared-library images whose
             * local runtime libraries are not part of the JDK boot library directory.
             */
            String imageDirectory = NativeLibraries.getImageDirectory();
            if (imageDirectory != null) {
                sysPath = Arrays.copyOf(sysPath, sysPath.length + 1);
                sysPath[sysPath.length - 1] = imageDirectory;
            }
            SYS_PATHS = sysPath;
        }
    }
}
