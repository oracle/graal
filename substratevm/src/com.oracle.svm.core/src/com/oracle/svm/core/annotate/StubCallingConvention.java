/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Use a calling convention where all registers are callee-saved.
 *
 * The annotated method must be static, to avoid problems when a virtual method call could invoke
 * callees with different calling conventions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StubCallingConvention {

    class Utils {
        public static boolean hasStubCallingConvention(ResolvedJavaMethod method) {
            boolean result = false;
            if (CalleeSavedRegisters.supportedByPlatform()) {
                SubstrateForeignCallTarget foreignCallTargetAnnotation = GuardedAnnotationAccess.getAnnotation(method, SubstrateForeignCallTarget.class);
                if (foreignCallTargetAnnotation != null && foreignCallTargetAnnotation.stubCallingConvention()) {
                    result = true;
                } else {
                    result = GuardedAnnotationAccess.isAnnotationPresent(method, StubCallingConvention.class);
                }
            }
            if (result && !method.isStatic()) {
                throw UserError.abort("Method that uses stub calling convention must be static: %s", method);
            }
            return result;
        }
    }
}
