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

import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.util.Digest;
import com.oracle.svm.core.jdk.InternalVMMethod;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/** Upcall stubs will be synthesized in this class. */
@InternalVMMethod
public final class UpcallStubsHolder {
    @Platforms(Platform.HOSTED_ONLY.class)
    public static ConstantPool getConstantPool(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(UpcallStubsHolder.class).getDeclaredConstructors()[0].getConstantPool();
    }

    /**
     * Generates the name used by a stub associated with the provided {@link JavaEntryPointInfo}.
     *
     * Naming scheme:
     *
     * <pre>
     *  upcall<High|Low>_(<c argument>*)<c return type>_<digest of paramsMemoryAssignment>[_<returnMemoryAssignment>]>
     * </pre>
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String stubName(JavaEntryPointInfo jep, boolean highLevel, boolean direct) {
        MethodType type = jep.handleType();

        StringBuilder builder = new StringBuilder();
        if (direct) {
            builder.append("direct_");
        }
        builder.append("upcall");
        builder.append(highLevel ? "High" : "Low");
        builder.append("_");
        for (var param : type.parameterArray()) {
            builder.append(JavaKind.fromJavaClass(param).getTypeChar());
        }
        builder.append("_");
        builder.append(JavaKind.fromJavaClass(type.returnType()).getTypeChar());

        if (jep.buffersReturn()) {
            builder.append("_r");
        }

        StringBuilder assignmentsBuilder = new StringBuilder();
        for (var assignment : jep.parametersAssignment()) {
            assignmentsBuilder.append(assignment);
        }

        assignmentsBuilder.append('_');
        for (var assignment : jep.returnAssignment()) {
            assignmentsBuilder.append(assignment);
        }

        builder.append('_');
        builder.append(Digest.digest(assignmentsBuilder.toString()));

        return builder.toString();
    }

    private UpcallStubsHolder() {
    }
}
