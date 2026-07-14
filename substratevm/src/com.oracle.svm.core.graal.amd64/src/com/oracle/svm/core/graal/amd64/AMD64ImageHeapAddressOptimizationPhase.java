/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.ListIterator;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.amd64.AMD64AddressLowering;
import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.graal.compiler.core.match.MatchRule;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.FinalSchedulePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.Value;

/**
 * Optimizes the machine code for operations that access image heap constants using the addressing
 * mode of the x86 instruction set.
 *
 * The image heap starts at offset 0 relative to the {@link Isolates#getHeapBase heap base} and is
 * limited to 2 GByte. Therefore, references to image heap objects can be expressed as addresses
 * that have the heap base register as the base, and a 32-bit immediate operand (and possibly an
 * index register when accessing an image heap array).
 *
 * This phase folds the {@link ConstantNode} base of a {@link AMD64AddressNode} together into a new
 * node. Why do this in a separate phase and not integrated with other phases or during lowering?
 * <ol>
 * <li>The {@link AMD64AddressLowering} is already quite complicated. It modifies
 * {@link AMD64AddressNode} nodes after they have been created. So there is no good place to create
 * {@link AMD64ImageHeapAddressNode} already during address lowering.</li>
 * <li>Using the {@link MatchRule} matching during LIR generation to combine
 * {@link AMD64AddressNode} and its base {@link ConstantNode} does not work because it is likely
 * that the address and constant nodes are in different blocks, so they would not be matched.</li>
 * <li>During LIR generation for {@link AMD64AddressNode}, it is already too late to do an
 * optimization because the {@link ConstantNode} has already been materialized in a register. We
 * cannot have {@link LIRGeneratorTool#canInlineConstant} return true for all object constants
 * because usages of object constants outside of addresses still need to be materialized in a
 * register.</li>
 * </ol>
 *
 * Even if an image heap object constant cannot be folded into an address operation, the object can
 * be materialized with a single {@code lea} instruction instead of two (loading the compressed
 * constant and then decompressing it). {@link LoadUncompressedImageHeapConstantOp} performs that
 * optimization on the LIR level.
 */
class AMD64ImageHeapAddressOptimizationPhase extends BasePhase<CoreProviders> {

    public static class Options {
        @Option(help = "Optimize address operations that involve image heap constants", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> OptInlineImageHeapConstants = new HostedOptionKey<>(true);
    }

    static boolean phaseEnabled() {
        return Options.OptInlineImageHeapConstants.getValue() && !SubstrateOptions.useLLVMBackend();
    }

    static boolean canOptimize(CompressibleConstant constant, ConstantReflectionProvider constantReflection) {
        return phaseEnabled() && ((SharedConstantReflectionProvider) constantReflection).canRepresentAsImageHeapOffset(constant);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();

        for (AMD64AddressNode address : graph.getNodes().filter(AMD64AddressNode.class)) {
            ValueNode baseNode = address.getBase();
            if (baseNode != null && baseNode.isJavaConstant() &&
                            baseNode.asJavaConstant() instanceof CompressibleConstant baseConstant &&
                            baseConstant.isNonNull()) {

                if (AMD64MemoryMaskingAndFencing.isEnabled() && address.getIndex() != null) {
                    /*
                     * If the MemoryMaskingAndFencing mitigation is enabled, then we cannot allow
                     * the optimization if index is not null. Indeed, if the index is not-null then
                     * the final address will be [r14 + reg * scale + displacement] re-introducing a
                     * non-masked register-based address.
                     */
                    continue;
                }

                if (canOptimize(baseConstant, constantReflection)) {
                    AMD64ImageHeapAddressNode replacement = new AMD64ImageHeapAddressNode(baseConstant, address.getIndex(), address.getScale(), address.getDisplacement());
                    address.replaceAndDelete(graph.unique(replacement));
                    if (baseNode.hasNoUsages()) {
                        baseNode.safeDelete();
                    }
                }
            }
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
class AMD64ImageHeapAddressFeature implements InternalFeature {
    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted, boolean fallback) {
        if (!fallback && AMD64ImageHeapAddressOptimizationPhase.phaseEnabled() && !(hosted && SubstrateOptions.useEconomyCompilerConfig())) {
            /*
             * Since this phase does not open up any new optimization potential, it should be done
             * as late as possible, i.e., just before the final schedule.
             */
            ListIterator<BasePhase<? super LowTierContext>> position = suites.getLowTier().findPhase(FinalSchedulePhase.class);
            position.previous();
            position.add(new AMD64ImageHeapAddressOptimizationPhase());
        }
    }
}

@NodeInfo
class AMD64ImageHeapAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<AMD64ImageHeapAddressNode> TYPE = NodeClass.create(AMD64ImageHeapAddressNode.class);

    private final CompressibleConstant base;
    @OptionalInput private ValueNode index;
    private final Stride stride;
    private final int displacement;

    protected AMD64ImageHeapAddressNode(CompressibleConstant base, ValueNode index, Stride stride, int displacement) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.stride = stride;
        this.displacement = displacement;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();

        RegisterValue heapBase = ReservedRegisters.singleton().getHeapBaseRegister().asValue();
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));
        LIRKind kind = tool.getLIRKind(stamp(NodeView.DEFAULT));

        gen.setResult(this, new AMD64AddressValue(kind, heapBase, indexValue, stride,
                        displacement + SubstrateAMD64Backend.addressDisplacement(base, constantReflection),
                        SubstrateAMD64Backend.addressDisplacementAnnotation(base)));
    }

    @Override
    public ValueNode getBase() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ValueNode getIndex() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public long getMaxConstantDisplacement() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }
}

final class LoadUncompressedImageHeapConstantOp extends AMD64LIRInstruction implements StandardOp.LoadConstantOp {
    public static final LIRInstructionClass<LoadUncompressedImageHeapConstantOp> TYPE = LIRInstructionClass.create(LoadUncompressedImageHeapConstantOp.class);

    @Def({REG, HINT}) private AllocatableValue result;
    private final CompressibleConstant constant;

    LoadUncompressedImageHeapConstantOp(AllocatableValue result, CompressibleConstant constant) {
        super(TYPE);
        this.result = result;
        this.constant = constant;
    }

    @Override
    public Constant getConstant() {
        return constant;
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        /* WARNING: must NOT have side effects. Preserve the flags register! */
        assert !constant.isCompressed();

        ConstantReflectionProvider constantReflection = crb.getConstantReflection();
        Register heapBase = ReservedRegisters.singleton().getHeapBaseRegister();
        masm.leaq(asRegister(result), new AMD64Address(heapBase, Register.None, Stride.S1,
                        SubstrateAMD64Backend.addressDisplacement(constant, constantReflection),
                        SubstrateAMD64Backend.addressDisplacementAnnotation(constant)));
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
