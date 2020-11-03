/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.methodhandles;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

// Checkstyle: stop
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
// Checkstyle: resume

import java.util.Arrays;

import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.MethodHandle", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandle {

    @Alias
    native Target_java_lang_invoke_MemberName internalMemberName();

    @Alias
    native Target_java_lang_invoke_LambdaForm internalForm();

    @Substitute(polymorphicSignature = true)
    private Object invokeBasic(Object... args) {
        Target_java_lang_invoke_MemberName memberName = internalMemberName() != null ? internalMemberName() : internalForm().vmentry;
        if (memberName == null) { /* Interpretation mode */
            throw unsupportedFeature("Method handles requiring lambda form interpretation (e.g through a bindTo() call) are not supported. " +
                            "See https://github.com/oracle/graal/issues/2939.");
        }

        try {
            Method method = SubstrateUtil.cast(memberName.reflectAccess, Method.class);
            if (Modifier.isStatic(method.getModifiers())) {
                return method.invoke(null, args);
            } else {
                return method.invoke(args[0], Arrays.copyOfRange(args, 1, args.length));
            }
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    @SuppressWarnings("unused")
    @Substitute(polymorphicSignature = true)
    static Object linkToVirtual(Object... args) throws Throwable {
        throw unsupportedFeature("MethodHandle.linkToVirtual()");
    }

    @SuppressWarnings("unused")
    @Substitute(polymorphicSignature = true)
    static Object linkToStatic(Object... args) throws Throwable {
        throw unsupportedFeature("MethodHandle.linkToStatic()");
    }
}
