/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;

@TargetClass(className = "java.lang.invoke.LambdaForm")
public final class Target_java_lang_invoke_LambdaForm {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_lang_invoke_MemberName vmentry;

    @Alias
    native String lambdaName();

    @Substitute
    void compileToBytecode() {
        /*
         * Those lambda form types are required to be precompiled to bytecode during method handles
         * bootstrapping to avoid a cyclic dependency. No further initialization is required since
         * these forms are executed as intrinsics (@see MethodHandleIntrinsic)
         */
        if (lambdaName().equals("zero") || lambdaName().equals("identity")) {
            vmentry = new Target_java_lang_invoke_MemberName();
        }
    }

    /*
     * We do not want invokers for lambda forms to be generated at runtime.
     */
    @Substitute
    @SuppressWarnings("static-method")
    private boolean forceInterpretation() {
        return true;
    }

    @Alias
    native Object interpretWithArguments(Object... argumentValues) throws Throwable;
}

@TargetClass(className = "java.lang.invoke.LambdaForm", innerClass = "NamedFunction")
final class Target_java_lang_invoke_LambdaForm_NamedFunction {
    @Alias
    native Target_java_lang_invoke_MethodHandle resolvedHandle();

    /*
     * Avoid triggering the generation of an optimized invoker.
     */
    @Substitute
    Object invokeWithArguments(Object... arguments) throws Throwable {
        return resolvedHandle().invokeBasic(arguments);
    }
}
