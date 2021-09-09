/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateForeignCallLinkage implements ForeignCallLinkage {

    private final SubstrateForeignCallsProvider provider;
    private final SubstrateForeignCallDescriptor descriptor;
    private final ResolvedJavaMethod method;

    private CallingConvention outgoingCallingConvention;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateForeignCallLinkage(SubstrateForeignCallsProvider provider, SubstrateForeignCallDescriptor descriptor) {
        this.provider = provider;
        this.descriptor = descriptor;
        this.method = descriptor.findMethod(provider.metaAccess);
    }

    @Override
    public CallingConvention getOutgoingCallingConvention() {
        if (outgoingCallingConvention == null) {
            JavaType resType = provider.metaAccess.lookupJavaType(descriptor.getResultType());
            JavaType[] argTypes = provider.metaAccess.lookupJavaTypes(descriptor.getArgumentTypes());
            SubstrateCallingConventionKind callingConventionKind = ((SharedMethod) method).getCallingConventionKind();
            outgoingCallingConvention = provider.registerConfig.getCallingConvention(callingConventionKind.toType(true), resType, argTypes, provider);
        }
        return outgoingCallingConvention;
    }

    @Override
    public CallingConvention getIncomingCallingConvention() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public long getMaxCallTargetOffset() {
        return -1;
    }

    @Override
    public boolean destroysRegisters() {
        return true;
    }

    @Override
    public boolean needsDebugInfo() {
        return descriptor.needsDebugInfo();
    }

    @Override
    public SubstrateForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public Value[] getTemporaries() {
        return AllocatableValue.NONE;
    }

    @Override
    public String toString() {
        return "RuntimeCall<" + ClassUtil.getUnqualifiedName(descriptor.getDeclaringClass()) + "." + descriptor.getName() + ">";
    }
}
