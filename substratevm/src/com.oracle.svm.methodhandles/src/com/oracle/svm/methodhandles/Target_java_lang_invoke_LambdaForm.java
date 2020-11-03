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

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(className = "java.lang.invoke.LambdaForm", onlyWith = MethodHandlesSupported.class)
public final class Target_java_lang_invoke_LambdaForm {
    /*
     * Setting this value to -1 forces interpretation of method handles instead of compiling them to
     * bytecode at runtime.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = LambdaFormInvocationCounterComputer.class) private int invocationCounter;

    @Alias Target_java_lang_invoke_MemberName vmentry;

    @Alias
    native String lambdaName();

    @Substitute
    void compileToBytecode() {
        if (lambdaName().equals("invoke")) {
            return;
        }

        /*
         * Those lambda form types are required to be precompiled to bytecode during method handles
         * bootstrapping. They are only used during interpretation of lambda forms however, so we
         * simply emit a dummy MemberName for now.
         */
        if (lambdaName().equals("zero") || lambdaName().equals("identity")) {
            vmentry = new Target_java_lang_invoke_MemberName();
        }
    }
}

final class LambdaFormInvocationCounterComputer implements RecomputeFieldValue.CustomFieldValueComputer {

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return -1;
    }
}
