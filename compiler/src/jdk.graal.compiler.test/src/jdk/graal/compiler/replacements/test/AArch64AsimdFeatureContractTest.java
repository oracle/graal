/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import jdk.graal.compiler.core.aarch64.AArch64LIRKindTool;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotLIRGenerator;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CopySignNode;
import jdk.graal.compiler.nodes.calc.SignumNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.ConditionalInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.aarch64.AArch64GraphBuilderPlugins;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode;
import jdk.graal.compiler.replacements.nodes.KyberNode;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/** Tests feature admission without executing code for a synthetic target. */
public class AArch64AsimdFeatureContractTest extends GraalCompilerTest {
    private static final AArch64 WITHOUT_ASIMD = new AArch64(EnumSet.of(AArch64.CPUFeature.FP));
    private static final AArch64 WITH_ASIMD = new AArch64(EnumSet.of(AArch64.CPUFeature.FP, AArch64.CPUFeature.ASIMD));

    /** Records lowering decisions without emitting or executing target machine code. */
    private static final class RecordingLIRGenerator extends AArch64HotSpotLIRGenerator {
        private final TargetDescription target;
        private int nextVariable = 16;
        private int instructions;

        RecordingLIRGenerator(AArch64 arch, LIRGenerationResult result) {
            super(new AArch64LIRKindTool(), new AArch64ArithmeticLIRGenerator(null), null, null, null, null, result);
            target = new TargetDescription(arch, true, 16, 4096, true);
        }

        @Override
        public TargetDescription target() {
            return target;
        }

        @Override
        public Variable newVariable(ValueKind<?> kind) {
            return new Variable(kind, nextVariable++);
        }

        @Override
        public <I extends LIRInstruction> I append(I op) {
            instructions++;
            return op;
        }

        @Override
        public void emitMove(AllocatableValue dst, Value src) {
            // Fixed-register argument moves are irrelevant to feature admission.
        }
    }

    private RecordingLIRGenerator generator(AArch64 arch) {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        graph.start().setNext(graph.add(new ReturnNode(null)));
        LIR lir = new LIR(ControlFlowGraph.newBuilder(graph).connectBlocks(true).build(), null, getInitialOptions(), graph.getDebug());
        return new RecordingLIRGenerator(arch, new LIRGenerationResult(null, lir, null, null, null));
    }

    private void assertAsimdLowering(Consumer<RecordingLIRGenerator> action) {
        RecordingLIRGenerator without = generator(WITHOUT_ASIMD);
        GraalError error = Assert.assertThrows(GraalError.class, () -> action.accept(without));
        assertTrue(error.getMessage(), error.getMessage().contains("requires ASIMD"));
        Assert.assertEquals(0, without.instructions);
        RecordingLIRGenerator with = generator(WITH_ASIMD);
        action.accept(with);
        assertTrue("ASIMD target must retain intrinsic lowering", with.instructions > 0);
    }

    private static final Value POINTER = new Variable(LIRKind.value(AArch64Kind.QWORD), 0);
    private static final Value INT = new Variable(LIRKind.value(AArch64Kind.DWORD), 1);
    private static final Value FLOAT = new Variable(LIRKind.value(AArch64Kind.SINGLE), 2);
    private static final Value DOUBLE = new Variable(LIRKind.value(AArch64Kind.DOUBLE), 3);

    private InvocationPlugins standardPlugins() {
        InvocationPlugins plugins = new InvocationPlugins();
        StandardGraphBuilderPlugins.registerInvocationPlugins(getSnippetReflection(), plugins, true, false, true, true);
        return plugins;
    }

    private InvocationPlugins aarch64Plugins() {
        InvocationPlugins plugins = new InvocationPlugins();
        AArch64GraphBuilderPlugins.register(new Plugins(plugins), getInitialOptions());
        return plugins;
    }

    private void assertConditionalPlugins(InvocationPlugins plugins, String className, String methodName, int expectedCount) {
        int count = 0;
        for (InvocationPlugin plugin : plugins.getInvocationPlugins(true).get(className)) {
            if (plugin.name.equals(methodName)) {
                assertTrue(plugin.toString(), plugin instanceof ConditionalInvocationPlugin);
                ConditionalInvocationPlugin conditional = (ConditionalInvocationPlugin) plugin;
                assertFalse(plugin.toString(), conditional.isApplicable(WITHOUT_ASIMD));
                assertTrue(plugin.toString(), conditional.isApplicable(WITH_ASIMD));
                count++;
            }
        }
        Assert.assertEquals(expectedCount, count);
    }

    @Test
    public void testMath() {
        for (Value input : new Value[]{FLOAT, DOUBLE}) {
            assertAsimdLowering(gen -> gen.getArithmetic().emitMathCopySign(input, input));
            assertAsimdLowering(gen -> gen.getArithmetic().emitMathSignum(input));
        }
        assertFalse(CopySignNode.isSupported(WITHOUT_ASIMD));
        assertTrue(CopySignNode.isSupported(WITH_ASIMD));
        assertFalse(SignumNode.isSupported(WITHOUT_ASIMD));
        assertTrue(SignumNode.isSupported(WITH_ASIMD));
        assertConditionalPlugins(aarch64Plugins(), "Ljava/lang/Math;", "copySign", 2);
        assertConditionalPlugins(standardPlugins(), "Ljava/lang/Math;", "signum", 2);
    }

    @Test
    public void testBase64() {
        assertAsimdLowering(gen -> gen.emitBase64EncodeBlock(null, POINTER, INT, INT, POINTER, INT, INT));
        assertAsimdLowering(gen -> gen.emitBase64DecodeBlock(null, POINTER, INT, INT, POINTER, INT, INT, INT));
        assertFalse(Base64EncodeBlockNode.isSupported(WITHOUT_ASIMD));
        assertTrue(Base64EncodeBlockNode.isSupported(WITH_ASIMD));
        assertFalse(Base64DecodeBlockNode.isSupported(WITHOUT_ASIMD));
        assertTrue(Base64DecodeBlockNode.isSupported(WITH_ASIMD));
        InvocationPlugins plugins = standardPlugins();
        assertConditionalPlugins(plugins, "Ljava/util/Base64$Encoder;", "encodeBlock", 1);
        assertConditionalPlugins(plugins, "Ljava/util/Base64$Decoder;", "decodeBlock", 1);
    }

    private void assertSnippetAdmission(InvocationPlugins plugins, String className, String methodName) {
        int count = 0;
        for (InvocationPlugin plugin : plugins.getInvocationPlugins(true).get(className)) {
            if (plugin.name.equals(methodName)) {
                for (AArch64 arch : new AArch64[]{WITHOUT_ASIMD, WITH_ASIMD}) {
                    TargetDescription target = new TargetDescription(arch, true, 16, 4096, true);
                    LoweringProvider lowerer = (LoweringProvider) Proxy.newProxyInstance(LoweringProvider.class.getClassLoader(), new Class<?>[]{LoweringProvider.class}, (proxy, method, args) -> {
                        Assert.assertEquals("getTarget", method.getName());
                        return target;
                    });
                    boolean[] reachedSnippet = {false};
                    GraphBuilderContext context = (GraphBuilderContext) Proxy.newProxyInstance(GraphBuilderContext.class.getClassLoader(), new Class<?>[]{GraphBuilderContext.class}, (proxy, method,
                                    args) -> {
                        switch (method.getName()) {
                            case "getLowerer":
                                return lowerer;
                            case "isPluginEnabled":
                                reachedSnippet[0] = true;
                                // Stop before instantiating the snippet: only admission is under test.
                                return false;
                            default:
                                throw new AssertionError(method);
                        }
                    });
                    assertFalse(plugin.execute(context, null, null, new ValueNode[0]));
                    Assert.assertEquals(arch == WITH_ASIMD, reachedSnippet[0]);
                }
                count++;
            }
        }
        Assert.assertEquals(1, count);
    }

    @Test
    public void testArrayIndexOf() {
        assertAsimdLowering(gen -> gen.emitArrayIndexOf(Stride.S1, ArrayIndexOfVariant.MatchAny, null, POINTER, POINTER, INT, INT, INT));
        for (Stride stride : Stride.values()) {
            for (ArrayIndexOfVariant variant : ArrayIndexOfVariant.values()) {
                assertFalse(ArrayIndexOfNode.isSupported(WITHOUT_ASIMD, stride, variant));
                assertTrue(ArrayIndexOfNode.isSupported(WITH_ASIMD, stride, variant));
            }
        }
        InvocationPlugins plugins = aarch64Plugins();
        assertConditionalPlugins(plugins, "Ljava/lang/StringLatin1;", "indexOfChar", 1);
        assertConditionalPlugins(plugins, "Ljava/lang/StringUTF16;", "indexOfChar", 1);
        assertSnippetAdmission(plugins, "Ljava/lang/StringLatin1;", "indexOf");
        assertSnippetAdmission(plugins, "Ljava/lang/StringUTF16;", "indexOfUnsafe");
    }

    @Test
    public void testMLKEMAndMLDSA() {
        assertAsimdLowering(gen -> gen.emitKyberNtt(POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitKyberInverseNtt(POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitKyberNttMult(POINTER, POINTER, POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitKyberAddPoly2(POINTER, POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitKyberAddPoly3(POINTER, POINTER, POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitKyber12To16(POINTER, INT, POINTER, INT));
        assertAsimdLowering(gen -> gen.emitKyberBarrettReduce(POINTER));
        assertAsimdLowering(gen -> gen.emitDilithiumAlmostNtt(POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitDilithiumAlmostInverseNtt(POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitDilithiumNttMult(POINTER, POINTER, POINTER));
        assertAsimdLowering(gen -> gen.emitDilithiumMontMulByConstant(POINTER, INT));
        assertAsimdLowering(gen -> gen.emitDilithiumDecomposePoly(POINTER, POINTER, POINTER, INT, INT));
        assertFalse(KyberNode.isSupported(WITHOUT_ASIMD));
        assertTrue(KyberNode.isSupported(WITH_ASIMD));
        assertFalse(DilithiumNode.isSupported(WITHOUT_ASIMD));
        assertTrue(DilithiumNode.isSupported(WITH_ASIMD));
        InvocationPlugins plugins = standardPlugins();
        for (String name : new String[]{"implKyberNtt", "implKyberInverseNtt", "implKyberNttMult", "implKyberAddPoly", "implKyber12To16", "implKyberBarrettReduce"}) {
            assertConditionalPlugins(plugins, "Lcom/sun/crypto/provider/ML_KEM;", name, name.equals("implKyberAddPoly") ? 2 : 1);
        }
        for (String name : new String[]{"implDilithiumAlmostNtt", "implDilithiumAlmostInverseNtt", "implDilithiumNttMult", "implDilithiumMontMulByConstant", "implDilithiumDecomposePoly"}) {
            assertConditionalPlugins(plugins, "Lsun/security/provider/ML_DSA;", name, 1);
        }
    }
}
