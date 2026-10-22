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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import jdk.graal.compiler.lir.amd64.AMD64Unary;
import jdk.graal.compiler.lir.StandardOp.CallOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.ElectronicCodeBookAESNode;
import jdk.graal.compiler.replacements.nodes.AESNode.CryptMode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.IntegerPolynomialAssignNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CharsetName;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.OptimizeExtendsPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.replacements.nodes.Adler32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;
import jdk.graal.compiler.replacements.nodes.IntrinsicMethodNodeInterface;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.InstalledCode;
import org.graalvm.word.LocationIdentity;

/** Compiles intrinsic nodes with nonzero upper argument bits without executing dummy addresses. */
public class IntrinsicArgumentsTest extends MatchRuleTest {
    private boolean omitOptimizeExtends;

    /** Intrinsic variants with independent expected zero-extension counts for each architecture. */
    private enum IntrinsicCase {
        INDEX_OF(2),
        COPY_WITH_FIXED_STRIDES(1),
        COPY_WITH_DYNAMIC_STRIDES(2),
        COMPARE_WITH_FIXED_STRIDES(1),
        COMPARE_WITH_DYNAMIC_STRIDES(2),
        VECTORIZED_MISMATCH(1),
        BIG_INTEGER_LEFT_SHIFT(2),
        BIG_INTEGER_RIGHT_SHIFT(2),
        CBC_AES_ENCRYPT(1),
        CBC_AES_DECRYPT(1),
        ECB_AES_ENCRYPT(1),
        ECB_AES_DECRYPT(1),
        BIG_INTEGER_SQUARE_TO_LEN(2),
        INTEGER_POLYNOMIAL_ASSIGN(2),
        BASE64_ENCODE(2),
        BASE64_DECODE(2),
        SHA1_MULTI_BLOCK(2),
        SHA256_MULTI_BLOCK(2),
        SHA3_MULTI_BLOCK(2),
        SHA512_MULTI_BLOCK(2),
        ENCODE_ARRAY(1),
        COUNT_POSITIVES(0),
        ADLER32(0, 2),
        EQUALS_WITH_FIXED_STRIDES(0),
        EQUALS_WITH_DYNAMIC_STRIDES(2, 0);

        private final int expectedAMD64ZeroExtensions;
        private final int expectedAArch64ZeroExtensions;

        IntrinsicCase(int expectedZeroExtensions) {
            this(expectedZeroExtensions, expectedZeroExtensions);
        }

        IntrinsicCase(int expectedAMD64ZeroExtensions, int expectedAArch64ZeroExtensions) {
            this.expectedAMD64ZeroExtensions = expectedAMD64ZeroExtensions;
            this.expectedAArch64ZeroExtensions = expectedAArch64ZeroExtensions;
        }

        int expectedZeroExtensions(Architecture arch) {
            if (arch instanceof AMD64) {
                return expectedAMD64ZeroExtensions;
            }
            GraalError.guarantee(arch instanceof AArch64, "Missing zero-extension expectations for %s", arch);
            return expectedAArch64ZeroExtensions;
        }
    }

    /** Forces direct assembly emission to test zero-extension independently of calls to stubs. */
    @NodeInfo
    public static final class DirectIndexOfNode extends FixedWithNextNode implements IntrinsicMethodNodeInterface {
        public static final NodeClass<DirectIndexOfNode> TYPE = NodeClass.create(DirectIndexOfNode.class);
        @Input private ValueNode array;
        @Input private ValueNode offset;
        @Input private ValueNode length;
        @Input private ValueNode fromIndex;
        @Input private ValueNode searchValue;

        public DirectIndexOfNode(ValueNode array, ValueNode offset, ValueNode length, ValueNode fromIndex, ValueNode searchValue) {
            super(TYPE, StampFactory.forKind(JavaKind.Int));
            this.array = array;
            this.offset = offset;
            this.length = length;
            this.fromIndex = fromIndex;
            this.searchValue = searchValue;
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return ArrayIndexOfForeignCalls.STUB_INDEX_OF_1_S1;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{array, offset, length, fromIndex, searchValue};
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            emitIntrinsic(prepareArguments(gen));
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            /* Mirrors ArrayIndexOfNode.emitIntrinsic without selecting a stub call. */
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayIndexOf(Stride.S1, ArrayIndexOfVariant.MatchAny, null,
                            gen.operand(array), gen.operand(offset), gen.operand(length), gen.operand(fromIndex), new Value[]{gen.operand(searchValue)}));
        }
    }

    /** Argument preparation must remain correct when optional extension optimization is absent. */
    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        if (omitOptimizeExtends) {
            while (suites.getLowTier().removePhase(OptimizeExtendsPhase.class)) {
                /* Remove both modes. */
            }
        }
        return suites;
    }

    /** Verifies that argument preparation does not add conversions to the graph. */
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        int found = 0;
        for (var node : graph.getNodes()) {
            if (node instanceof IntrinsicMethodNodeInterface intrinsic) {
                found++;
                ValueNode[] args = intrinsic.getForeignCallArguments();
                Class<?>[] types = intrinsic.getForeignCallDescriptor().getArgumentTypes();
                for (int i = 0; i < args.length; i++) {
                    if (types[i] == int.class && !args[i].isJavaConstant()) {
                        Assert.assertTrue("Intrinsic arguments must use their original narrows",
                                        args[i] instanceof NarrowNode narrow && narrow.getValue().getStackKind() == JavaKind.Long);
                    }
                }
            }
        }
        Assert.assertEquals("The real intrinsic must remain in the graph", 1, found);
    }

    public static int indexOf(byte[] array, long length, long fromIndex) {
        return array.length + (int) (length + fromIndex);
    }

    /** Executes installed code only after verifying zero extension for both int arguments in LIR. */
    @Test
    public void testIndexOf() throws Exception {
        checkIndexOf(false);
    }

    @Test
    public void testDirectIndexOf() throws Exception {
        checkIndexOf(true);
    }

    /** Verifies installed code for both direct emission and calls to the production index-of stub. */
    private void checkIndexOf(boolean direct) throws Exception {
        Assume.assumeTrue(ArrayIndexOfNode.isSupported(getTarget().arch, Stride.S1, ArrayIndexOfVariant.MatchAny));
        StructuredGraph graph = parseEager("indexOf", StructuredGraph.AllowAssumptions.NO);
        ValueNode length = graph.addOrUnique(new NarrowNode(graph.getParameter(1), 32));
        ValueNode fromIndex = graph.addOrUnique(new NarrowNode(graph.getParameter(2), 32));
        ValueNode offset = ConstantNode.forLong(getMetaAccess().getArrayBaseOffset(JavaKind.Byte), graph);
        ValueNode searchValue = ConstantNode.forInt(7, graph);
        FixedWithNextNode intrinsic;
        if (direct) {
            intrinsic = graph.add(new DirectIndexOfNode(graph.getParameter(0), offset, length, fromIndex, searchValue));
        } else {
            intrinsic = graph.add(new ArrayIndexOfNode(Stride.S1, ArrayIndexOfVariant.MatchAny, graph.getParameter(0), offset, length, fromIndex, searchValue));
        }
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        graph.addBeforeFixed(ret, intrinsic);
        ret.replaceFirstInput(ret.result(), intrinsic);
        int expected = IntrinsicCase.INDEX_OF.expectedZeroExtensions(getTarget().arch);
        InstalledCode code = getCode(getResolvedJavaMethod("indexOf"), graph, true);
        try {
            checkZeroExtensions(direct ? "direct index-of" : "index-of stub", expected);
            boolean callsStub = false;
            for (var block : getLIR().getControlFlowGraph().getBlocks()) {
                for (var instruction : getLIR().getLIRforBlock(block)) {
                    callsStub |= instruction instanceof CallOp;
                }
            }
            Assert.assertEquals("The requested emission path must be used", !direct, callsStub);
            byte[] array = {7, 0, 7, 0};
            for (long highBits : new long[]{0x0000000100000000L, 0xffffffff00000000L}) {
                Assert.assertEquals(0, code.executeVarargs(array, highBits | 4, highBits));
                Assert.assertEquals(2, code.executeVarargs(array, highBits | 4, highBits | 1));
            }
        } finally {
            code.invalidate();
        }
    }

    public static int arguments(long first, long second) {
        return (int) (first + second);
    }

    /** Checks the expected number of explicit zero-extensions for each intrinsic variant. */
    @Test
    public void testArguments() {
        Architecture arch = getTarget().arch;
        for (IntrinsicCase testCase : IntrinsicCase.values()) {
            StructuredGraph graph = parseEager("arguments", StructuredGraph.AllowAssumptions.NO);
            ValueNode first = graph.addOrUnique(new NarrowNode(graph.getParameter(0), 32));
            ValueNode second = graph.addOrUnique(new NarrowNode(graph.getParameter(1), 32));
            ValueNode pointer = ConstantNode.forLong(0, graph);
            ValueNode array = ConstantNode.forConstant(JavaConstant.NULL_POINTER, getMetaAccess(), graph);
            ValueNode zero = ConstantNode.forInt(0, graph);
            FixedWithNextNode intrinsic = switch (testCase) {
                case INDEX_OF -> ArrayIndexOfNode.isSupported(arch, Stride.S1, ArrayIndexOfVariant.MatchAny)
                                ? new ArrayIndexOfNode(Stride.S1, ArrayIndexOfVariant.MatchAny, array, pointer, first, second, ConstantNode.forInt(7, graph))
                                : null;
                case COPY_WITH_FIXED_STRIDES -> new ArrayCopyWithConversionsNode(array, pointer, array, pointer, first, Stride.S2, Stride.S1);
                case COPY_WITH_DYNAMIC_STRIDES -> new ArrayCopyWithConversionsNode(array, pointer, array, pointer, first, second);
                case COMPARE_WITH_FIXED_STRIDES -> new ArrayRegionCompareToNode(array, pointer, array, pointer, first, Stride.S2, Stride.S1, LocationIdentity.any());
                case COMPARE_WITH_DYNAMIC_STRIDES -> new ArrayRegionCompareToNode(array, pointer, array, pointer, first, second, LocationIdentity.any());
                case VECTORIZED_MISMATCH -> VectorizedMismatchNode.isSupported(arch) ? new VectorizedMismatchNode(pointer, pointer, first, zero) : null;
                case BIG_INTEGER_LEFT_SHIFT -> BigIntegerLeftShiftWorkerNode.isSupported(arch)
                                ? new BigIntegerLeftShiftWorkerNode(pointer, pointer, first, zero, second)
                                : null;
                case BIG_INTEGER_RIGHT_SHIFT -> BigIntegerRightShiftWorkerNode.isSupported(arch)
                                ? new BigIntegerRightShiftWorkerNode(pointer, pointer, first, zero, second)
                                : null;
                case CBC_AES_ENCRYPT -> CipherBlockChainingAESNode.isSupported(arch)
                                ? new CipherBlockChainingAESNode(pointer, pointer, pointer, pointer, first, CryptMode.ENCRYPT)
                                : null;
                case CBC_AES_DECRYPT -> CipherBlockChainingAESNode.isSupported(arch)
                                ? new CipherBlockChainingAESNode(pointer, pointer, pointer, pointer, first, CryptMode.DECRYPT)
                                : null;
                case ECB_AES_ENCRYPT -> ElectronicCodeBookAESNode.isSupported(arch)
                                ? new ElectronicCodeBookAESNode(pointer, pointer, pointer, first, CryptMode.ENCRYPT)
                                : null;
                case ECB_AES_DECRYPT -> ElectronicCodeBookAESNode.isSupported(arch)
                                ? new ElectronicCodeBookAESNode(pointer, pointer, pointer, first, CryptMode.DECRYPT)
                                : null;
                case BIG_INTEGER_SQUARE_TO_LEN -> new BigIntegerSquareToLenNode(pointer, first, pointer, second);
                case INTEGER_POLYNOMIAL_ASSIGN -> IntegerPolynomialAssignNode.isSupported(arch)
                                ? new IntegerPolynomialAssignNode(first, pointer, pointer, second)
                                : null;
                case BASE64_ENCODE -> Base64EncodeBlockNode.isSupported(arch)
                                ? new Base64EncodeBlockNode(pointer, first, second, pointer, first, zero)
                                : null;
                case BASE64_DECODE -> Base64DecodeBlockNode.isSupported(arch)
                                ? new Base64DecodeBlockNode(pointer, first, second, pointer, first, zero, zero)
                                : null;
                case SHA1_MULTI_BLOCK -> MessageDigestNode.SHA1Node.isSupported(arch)
                                ? new MessageDigestNode.SHA1MultiBlockNode(pointer, pointer, first, second)
                                : null;
                case SHA256_MULTI_BLOCK -> MessageDigestNode.SHA256Node.isSupported(arch)
                                ? new MessageDigestNode.SHA256MultiBlockNode(pointer, pointer, first, second)
                                : null;
                case SHA3_MULTI_BLOCK -> MessageDigestNode.SHA3Node.isSupported(arch)
                                ? new MessageDigestNode.SHA3MultiBlockNode(pointer, pointer, first, first, second)
                                : null;
                case SHA512_MULTI_BLOCK -> MessageDigestNode.SHA512Node.isSupported(arch)
                                ? new MessageDigestNode.SHA512MultiBlockNode(pointer, pointer, first, second)
                                : null;
                case ENCODE_ARRAY -> new EncodeArrayNode(pointer, pointer, first, CharsetName.ISO_8859_1);
                case COUNT_POSITIVES -> new CountPositivesNode(pointer, first);
                case ADLER32 -> Adler32UpdateBytesNode.isSupported(arch) ? new Adler32UpdateBytesNode(first, pointer, second) : null;
                case EQUALS_WITH_FIXED_STRIDES -> new ArrayRegionEqualsNode(array, pointer, array, pointer, first, Stride.S1, Stride.S1);
                case EQUALS_WITH_DYNAMIC_STRIDES -> new ArrayRegionEqualsNode(array, pointer, array, pointer, first, second);
            };
            if (intrinsic == null) {
                continue;
            }
            graph.add(intrinsic);
            if (intrinsic instanceof StateSplit split && split.hasSideEffect()) {
                split.setStateAfter(graph.start().stateAfter());
            }
            ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
            graph.addBeforeFixed(ret, intrinsic);
            if (intrinsic.getStackKind() == JavaKind.Int) {
                ret.replaceFirstInput(ret.result(), intrinsic);
            }
            int expected = testCase.expectedZeroExtensions(arch);
            InstalledCode code = getCode(getResolvedJavaMethod("arguments"), graph, true);
            try {
                checkZeroExtensions(testCase.name(), expected);
            } finally {
                code.invalidate();
            }
        }
    }

    @Test
    public void testWithoutOptimizeExtends() {
        omitOptimizeExtends = true;
        try {
            testArguments();
        } finally {
            omitOptimizeExtends = false;
        }
    }

    /** Checks operations that survive allocation before any intrinsic memory access can execute. */
    private void checkZeroExtensions(String intrinsic, int expected) {
        int zeroExtensions = 0;
        for (var block : getLIR().getControlFlowGraph().getBlocks()) {
            for (var instruction : getLIR().getLIRforBlock(block)) {
                if (instruction instanceof AMD64Unary.RMOp && instruction.name().equals("MOV") ||
                                instruction instanceof AArch64ArithmeticOp.BinaryConstOp && instruction.name().equals("AND")) {
                    zeroExtensions++;
                }
            }
        }
        Assert.assertEquals(intrinsic + ": explicit zero-extension instructions", expected, zeroExtensions);
    }
}
