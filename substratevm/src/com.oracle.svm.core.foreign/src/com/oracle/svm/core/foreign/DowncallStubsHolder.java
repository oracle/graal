/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.InternalVMMethod;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/** Downcall stubs will be synthesized in this class. */
@InternalVMMethod
public final class DowncallStubsHolder {
    @Platforms(Platform.HOSTED_ONLY.class)
    public static ConstantPool getConstantPool(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(DowncallStubsHolder.class).getDeclaredConstructors()[0].getConstantPool();
    }

    /**
     * Generates the name used by a stub associated with the provided {@link NativeEntryPointInfo}.
     *
     * Naming scheme:
     * 
     * <pre>
     *  downcall_(<c argument>*)<c return type>_<digest of paramsMemoryAssignment>[_<returnMemoryAssignment>]>
     * </pre>
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String stubName(NativeEntryPointInfo nep) {
        StringBuilder builder = new StringBuilder("downcall_");
        for (var param : nep.methodType().parameterArray()) {
            builder.append(JavaKind.fromJavaClass(param).getTypeChar());
        }
        builder.append("_");
        builder.append(JavaKind.fromJavaClass(nep.methodType().returnType()).getTypeChar());

        if (nep.needsReturnBuffer()) {
            builder.append("_r");
        }
        if (nep.capturesCallState()) {
            builder.append("_c");
        }
        if (nep.skipsTransition()) {
            builder.append("_t");
        }

        StringBuilder assignmentsBuilder = new StringBuilder();
        for (var assignment : nep.parametersAssignment()) {
            assignmentsBuilder.append(assignment);
        }

        if (nep.returnsAssignment() != null) {
            assignmentsBuilder.append('_');
            for (var assignment : nep.returnsAssignment()) {
                assignmentsBuilder.append(assignment);
            }
        }

        builder.append('_');
        builder.append(SubstrateUtil.digest(assignmentsBuilder.toString()));

        return builder.toString();
    }

    private DowncallStubsHolder() {
    }
}
