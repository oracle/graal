/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

class LibGraalSubstitutions {

    @TargetClass(className = "jdk.vm.ci.services.Services", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_services_Services {
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
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.Cleaner", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_hotspot_Cleaner {

        /*
         * Make package-private clean() accessible so that it can be called from
         * LibGraalEntryPoints.doReferenceHandling().
         */
        @Alias
        public static native void clean();
    }

    /*
     * There are no String-based class-lookups happening at libgraal runtime. Thus, we can safely
     * prune all classloading-logic out of the image.
     */
    @TargetClass(value = java.lang.Class.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_lang_Class {
        @Substitute
        public static Class<?> forName(String name, boolean initialize, ClassLoader loader)
                        throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Substitute
        private static Class<?> forName(String className, Class<?> caller)
                        throws ClassNotFoundException {
            throw new ClassNotFoundException(className);
        }

        @Substitute
        public static Class<?> forName(Module module, String name) {
            return null;
        }
    }

    @TargetClass(value = java.lang.Module.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_lang_Module {
        @Substitute
        public Set<String> getPackages() {
            return Collections.emptySet();
        }
    }

    @TargetClass(value = java.lang.ClassLoader.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_lang_ClassLoader {
        @Substitute
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Substitute
        static Class<?> findBootstrapClassOrNull(String name) {
            return null;
        }
    }
}
