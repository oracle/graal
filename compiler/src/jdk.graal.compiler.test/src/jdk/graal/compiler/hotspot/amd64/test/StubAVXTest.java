/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.amd64.test;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.BitSet;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.SnippetStub;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.jtt.LIRTest;
import jdk.graal.compiler.lir.jtt.LIRTestSpecification;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.graal.compiler.replacements.classfile.ClassfileBytecodeProvider;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class StubAVXTest extends LIRTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        Assume.assumeTrue("skipping AVX test", ((AMD64) getTarget().arch).getFeatures().contains(CPUFeature.AVX));
        if (getBackend() instanceof HotSpotBackend) {
            HotSpotBackend backend = (HotSpotBackend) getBackend();
            Assume.assumeTrue("skipping because of MaxVectorSize", backend.getRuntime().getVMConfig().maxVectorSize >= 32);
        }
    }

    private static final DataPointerConstant avxConstant = new ArrayDataPointerConstant(new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f}, 32);

    private static class LoadAVXConstant extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LoadAVXConstant> TYPE = LIRInstructionClass.create(LoadAVXConstant.class);

        @Def({REG}) AllocatableValue result;

        LoadAVXConstant(AllocatableValue result) {
            super(TYPE);
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.vmovdqu(ValueUtil.asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(avxConstant));
        }
    }

    private static final LIRTestSpecification loadAVXConstant = new LIRTestSpecification() {

        @Override
        public void generate(LIRGeneratorTool gen) {
            Variable ret = gen.newVariable(LIRKind.value(AMD64Kind.V256_SINGLE));
            gen.append(new LoadAVXConstant(ret));
            setResult(ret);
        }
    };

    @LIRIntrinsic
    public static Object loadAVXConstant(@SuppressWarnings("unused") LIRTestSpecification spec) {
        return null;
    }

    private static class CompareAVXRegister extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompareAVXRegister> TYPE = LIRInstructionClass.create(CompareAVXRegister.class);

        @Def({REG}) AllocatableValue result;
        @Use({REG}) AllocatableValue left;
        @Use({REG}) AllocatableValue right;
        @Temp({REG}) AllocatableValue temp;

        CompareAVXRegister(AllocatableValue result, AllocatableValue left, AllocatableValue right, AllocatableValue temp) {
            super(TYPE);
            this.result = result;
            this.left = left;
            this.right = right;
            this.temp = temp;
        }

        private static int getRXB(Register reg, Register rm) {
            int rxb = (reg.encoding & 0x08) >> 1;
            rxb |= (rm.encoding & 0x08) >> 3;
            return rxb;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register res = ValueUtil.asRegister(result);
            Register x = ValueUtil.asRegister(left);
            Register y = ValueUtil.asRegister(right);
            Register tmp = ValueUtil.asRegister(temp);

            // VEX.NDS.256.0F.WIG C2 /r ib(0)
            // VCMPPS tmp, x, y, EQ
            masm.emitByte(0xC4);                                   // VEX 3-byte
            masm.emitByte((~getRXB(tmp, y) & 0x7) << 5 | 0x01);    // RXB m-mmmmm (0F)
            masm.emitByte(((~x.encoding & 0x0f) << 3) | 0b1_00);   // W(0) vvvv L(1) pp(0)
            masm.emitByte(0xC2);
            masm.emitByte(0xC0 | ((tmp.encoding & 0x07) << 3) | (y.encoding & 0x07));
            masm.emitByte(0);

            // VEX.256.0F.WIG 50 /r
            // VMOVMSKPS res, tmp
            masm.emitByte(0xC4);                                   // VEX 3-byte
            masm.emitByte((~getRXB(res, tmp) & 0x7) << 5 | 0x01);  // RXB m-mmmmm (0F)
            masm.emitByte(0b0_1111_1_00);                          // W(0) vvvv L(1) pp(0)
            masm.emitByte(0x50);
            masm.emitByte(0xC0 | ((res.encoding & 0x07) << 3) | (tmp.encoding & 0x07));
        }
    }

    private static final LIRTestSpecification compareAVXRegister = new LIRTestSpecification() {

        @Override
        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1) {
            Variable ret = gen.newVariable(LIRKind.value(AMD64Kind.DWORD));
            gen.append(new CompareAVXRegister(ret, gen.asAllocatable(arg0), gen.asAllocatable(arg1), gen.newVariable(LIRKind.value(AMD64Kind.V256_QWORD))));
            setResult(ret);
        }
    };

    private static class TestStub extends SnippetStub {

        TestStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
            super("testStub", options, providers, linkage);
        }

        @Snippet
        static void testStub() {
        }

        @Override
        protected void registerSnippet() {
        }

        @Override
        protected StructuredGraph buildInitialGraph(DebugContext debug, CompilationIdentifier compilationId, Object[] args, BitSet nonNullParameters) {
            // Build the snippet graph directly since snippet registration is closed at this point.
            ReplacementsImpl d = (ReplacementsImpl) providers.getReplacements();
            MetaAccessProvider metaAccess = d.getProviders().getMetaAccess();
            BytecodeProvider bytecodes = new ClassfileBytecodeProvider(metaAccess, d.getProviders().getSnippetReflection(), ClassLoader.getSystemClassLoader());
            return d.makeGraph(debug, bytecodes, method, args, nonNullParameters, null, false, null);
        }
    }

    public static final ForeignCallSignature TEST_STUB = new ForeignCallSignature("test_stub", void.class);

    @LIRIntrinsic
    public static int compareAVXRegister(@SuppressWarnings("unused") LIRTestSpecification spec, Object left, Object right) {
        return left == right ? 0xff : 0;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, TestStub.class);
        r.register(new InvocationPlugin("testStub") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
                b.add(new ForeignCallNode(getProviders().getForeignCalls(), TEST_STUB));
                return true;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    public static int testStub() {
        Object preStub = loadAVXConstant(loadAVXConstant);

        // do something to potentially destroy the value
        TestStub.testStub();

        Object postStub = loadAVXConstant(loadAVXConstant);
        return compareAVXRegister(compareAVXRegister, preStub, postStub);
    }

    @Test
    public void test() {
        HotSpotProviders providers = (HotSpotProviders) getProviders();
        HotSpotForeignCallsProviderImpl foreignCalls = providers.getForeignCalls();
        HotSpotForeignCallLinkage linkage = foreignCalls.registerStubCall(TEST_STUB, HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO, NO_SIDE_EFFECT, COMPUTES_REGISTERS_KILLED);
        linkage.setCompiledStub(new TestStub(GraalCompilerTest.getInitialOptions(), providers, linkage));
        runTest("testStub");
    }
}
