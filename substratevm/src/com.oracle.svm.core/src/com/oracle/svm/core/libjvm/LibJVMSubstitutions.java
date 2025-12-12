/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.libjvm;

import java.lang.reflect.Method;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.misc.MethodFinder;

class LibJVMSubstitutions {

    /**
     * Workaround for GR-71358. See also {@link LibJVMMainMethodWrappers}
     */
    @TargetClass(value = MethodFinder.class, onlyWith = LibJVMMainMethodWrappers.Enabled.class)
    static final class Target_jdk_internal_misc_MethodFinder {

        @Alias //
        static JavaLangAccess JLA;

        @Substitute
        public static Method findMainMethod(Class<?> cls) {
            Method mainMethod = JLA.findMethod(cls, true, "main", String[].class);

            if (mainMethod == null) {
                // if not public method, try to lookup a non-public one
                mainMethod = JLA.findMethod(cls, false, "main", String[].class);
            }

            if (mainMethod == null || !isValidMainMethod(mainMethod)) {
                mainMethod = JLA.findMethod(cls, false, "main");
            }

            if (mainMethod == null || !isValidMainMethod(mainMethod)) {
                return null;
            }

            LibJVMMainMethodWrappers.singleton().setValidMainClass(mainMethod.getDeclaringClass());
            return mainMethod;
        }

        @Alias
        static native boolean isValidMainMethod(Method mainMethodCandidate);
    }
}
