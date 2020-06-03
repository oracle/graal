/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Mechanism for injecting special code into
 * {@linkplain HotSpotTruffleCompilerRuntime#getTruffleCallBoundaryMethods() call boundary methods}.
 */
public abstract class TruffleCallBoundaryInstrumentation extends CompilationResultBuilder {
    protected final GraalHotSpotVMConfig config;
    protected final HotSpotRegistersProvider registers;
    protected final MetaAccessProvider metaAccess;

    public TruffleCallBoundaryInstrumentation(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder,
                    FrameContext frameContext, OptionValues options, DebugContext debug, CompilationResult compilationResult, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, Register.None, null);
        this.metaAccess = metaAccess;
        this.config = config;
        this.registers = registers;
    }

    @Override
    public CompilationResult.CodeMark recordMark(CompilationResult.MarkId id) {
        CompilationResult.CodeMark mark = super.recordMark(id);
        if (id == HotSpotMarkId.VERIFIED_ENTRY) {
            ResolvedJavaType optimizedCallTargetType = TruffleCompilerRuntime.getRuntime().resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotOptimizedCallTarget");
            int installedCodeOffset = getFieldOffset("installedCode", optimizedCallTargetType);
            int entryPointOffset = getFieldOffset("entryPoint", metaAccess.lookupJavaType(InstalledCode.class));
            injectTailCallCode(installedCodeOffset, entryPointOffset);
        }
        return mark;
    }

    private static int getFieldOffset(String name, ResolvedJavaType declaringType) {
        for (ResolvedJavaField field : declaringType.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field.getOffset();
            }
        }
        throw new NoSuchFieldError(declaringType.toJavaName() + "." + name);
    }

    /**
     * Injects code into the verified entry point of that makes a tail-call to the target callee.
     *
     * @param entryPointOffset offset of the field {@code HotSpotOptimizedCallTarget.installedCode}
     * @param installedCodeOffset offset of the field {@code InstalledCode.entryPoint}
     */
    protected abstract void injectTailCallCode(int installedCodeOffset, int entryPointOffset);
}
