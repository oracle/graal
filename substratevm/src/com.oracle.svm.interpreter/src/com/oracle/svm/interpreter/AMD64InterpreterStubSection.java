/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.EST_NO_ENTRY;

import java.util.Collection;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs;
import com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64InterpreterStubSection extends InterpreterStubSection {
    public AMD64InterpreterStubSection() {
        this.target = ConfigurationValues.getTarget();
        this.registerConfig = new SubstrateAMD64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NATIVE_TO_JAVA, null, target, true);
        this.valueKindFactory = javaKind -> LIRKind.fromJavaKind(target.arch, javaKind);
    }

    @Override
    protected byte[] generateEnterStubs(Collection<InterpreterResolvedJavaMethod> methods) {
        AMD64MacroAssembler masm = new AMD64MacroAssembler(target);

        if (SubstrateControlFlowIntegrity.enabled()) {
            VMError.unimplemented("GR-63035: Add CFI support for interpreter stubs");
        }

        Label interpEnterStub = new Label();
        masm.bind(interpEnterStub);

        masm.setCodePatchingAnnotationConsumer(this::recordEnterStubForPatching);
        masm.jmp();

        /* emit enter trampolines for each method that potentially runs in the interpreter */
        for (InterpreterResolvedJavaMethod method : methods) {
            VMError.guarantee(method.getEnterStubOffset() != EST_NO_ENTRY);
            InterpreterUtil.log("[svm_interp] Adding stub for %s", method);
            recordEnterTrampoline(method, masm.position());

            /* pass the method index, the reference is obtained in enterInterpreterStub */
            masm.movq(AMD64InterpreterStubs.TRAMPOLINE_ARGUMENT, method.getEnterStubOffset());

            masm.jmp(interpEnterStub);
        }

        return masm.close(true);
    }

    @Override
    public int getVTableStubSize() {
        int branchTargetAlignment = ConfigurationValues.getTarget().wordSize * 2;
        int stubSize = 10;

        return NumUtil.roundUp(stubSize, branchTargetAlignment);
    }

    @Override
    protected byte[] generateVTableEnterStubs(int maxVTableIndex) {
        AMD64MacroAssembler masm = new AMD64MacroAssembler(target);

        if (SubstrateControlFlowIntegrity.enabled()) {
            VMError.unimplemented("GR-63035: Add CFI support for interpreter stubs");
        }

        Label interpEnterStub = new Label();
        masm.bind(interpEnterStub);

        masm.setCodePatchingAnnotationConsumer(this::recordEnterStubForPatching);
        masm.jmp();

        masm.align(getVTableStubSize());
        recordVTableStubBaseOffset(masm.position());

        for (int vTableIndex = 0; vTableIndex < maxVTableIndex; vTableIndex++) {
            int expectedStubEnd = masm.position() + getVTableStubSize();

            /* pass current vTable index as hidden argument */
            masm.moveInt(AMD64InterpreterStubs.TRAMPOLINE_ARGUMENT, vTableIndex);

            masm.jmp(interpEnterStub);

            masm.align(getVTableStubSize());
            assert masm.position() == expectedStubEnd;
        }

        return masm.close(true);
    }

    private int resolverPatchOffset;
    private int resolverKindAddend;
    private ObjectFile.RelocationKind resolverPatchRelocationKind = null;

    private void recordEnterStubForPatching(Assembler.CodeAnnotation a) {
        if (resolverPatchRelocationKind != null) {
            return;
        }

        assert a instanceof AMD64BaseAssembler.OperandDataAnnotation;
        AMD64BaseAssembler.OperandDataAnnotation annotation = (AMD64BaseAssembler.OperandDataAnnotation) a;
        resolverPatchOffset = annotation.operandPosition;
        resolverKindAddend = -annotation.operandSize;
        resolverPatchRelocationKind = ObjectFile.RelocationKind.getPCRelative(annotation.operandSize);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected void markEnterStubPatch(ObjectFile.ProgbitsSectionImpl pltBuffer, ResolvedJavaMethod enterStub) {
        pltBuffer.markRelocationSite(resolverPatchOffset, resolverPatchRelocationKind, NativeImage.localSymbolNameForMethod(enterStub), resolverKindAddend);
    }
}
