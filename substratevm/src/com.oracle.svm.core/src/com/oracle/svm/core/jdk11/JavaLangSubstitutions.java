/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.jdk11;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.jdk.Target_java_lang_Package;

@TargetClass(value = jdk.internal.loader.ClassLoaders.class)
final class Target_jdk_internal_loader_ClassLoaders {
    @Alias
    static native Target_jdk_internal_loader_BuiltinClassLoader bootLoader();

    @Alias
    public static native ClassLoader platformClassLoader();
}

@TargetClass(value = jdk.internal.loader.BootLoader.class)
final class Target_jdk_internal_loader_BootLoader {

    @Substitute
    static Package getDefinedPackage(String name) {
        if (name != null) {
            Target_java_lang_Package pkg = new Target_java_lang_Package(name, null, null, null,
                            null, null, null, null, null);
            return SubstrateUtil.cast(pkg, Package.class);
        } else {
            return null;
        }
    }

    @Substitute
    public static Stream<Package> packages() {
        Target_jdk_internal_loader_BuiltinClassLoader bootClassLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader systemClassLoader = SubstrateUtil.cast(bootClassLoader, Target_java_lang_ClassLoader.class);
        return systemClassLoader.packages();
    }

    @Delete("only used by #packages()")
    private static native String[] getSystemPackageNames();

    @Substitute
    private static Class<?> loadClassOrNull(String name) {
        return ClassForNameSupport.forNameOrNull(name, null);
    }

    @SuppressWarnings("unused")
    @Substitute
    private static Class<?> loadClass(Target_java_lang_Module module, String name) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        return ClassForNameSupport.forNameOrNull(name, null);
    }

    @Substitute
    private static boolean hasClassPath() {
        return true;
    }

    /**
     * All ClassLoaderValue are reset at run time for now. See also
     * {@link Target_java_lang_ClassLoader#classLoaderValueMap} for resetting of individual class
     * loaders.
     */
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    static ConcurrentHashMap<?, ?> CLASS_LOADER_VALUE_MAP;
    // Checkstyle: resume
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangSubstitutions {

}
