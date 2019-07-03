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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateForeignCallLinkage implements ForeignCallLinkage {

    private final SubstrateForeignCallDescriptor descriptor;
    private final CallingConvention outgoingCallingConvention;
    private final CallingConvention incomingCallingConvention;
    private final ResolvedJavaMethod method;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateForeignCallLinkage(Providers providers, SubstrateForeignCallDescriptor descriptor) {
        this.descriptor = descriptor;
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        CodeCacheProvider codeCache = providers.getCodeCache();
        if (codeCache != null) {
            RegisterConfig registerConfig = codeCache.getRegisterConfig();
            Architecture arch = codeCache.getTarget().arch;
            ValueKindFactory<LIRKind> valueKindFactory = new ValueKindFactory<LIRKind>() {
                @Override
                public LIRKind getValueKind(JavaKind javaKind) {
                    return LIRKind.fromJavaKind(arch, javaKind);
                }
            };
            JavaType resType = metaAccess.lookupJavaType(descriptor.getResultType());
            JavaType[] argTypes = metaAccess.lookupJavaTypes(descriptor.getArgumentTypes());
            this.outgoingCallingConvention = registerConfig.getCallingConvention(SubstrateCallingConventionType.JavaCall, resType, argTypes, valueKindFactory);
            this.incomingCallingConvention = registerConfig.getCallingConvention(SubstrateCallingConventionType.JavaCallee, resType, argTypes, valueKindFactory);
        } else {
            this.outgoingCallingConvention = null;
            this.incomingCallingConvention = null;
        }
        this.method = descriptor.findMethod(metaAccess);
    }

    @Override
    public CallingConvention getOutgoingCallingConvention() {
        return outgoingCallingConvention;
    }

    @Override
    public CallingConvention getIncomingCallingConvention() {
        return incomingCallingConvention;
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
        return "RuntimeCall<" + descriptor.getDeclaringClass().getSimpleName() + "." + descriptor.getName() + ">";
    }
}
