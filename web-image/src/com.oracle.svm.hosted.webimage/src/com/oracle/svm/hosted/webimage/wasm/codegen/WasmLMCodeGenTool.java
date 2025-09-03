/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicSet;

import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Relocation;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmVisitor;
import com.oracle.svm.hosted.webimage.wasm.nodes.WebImageWasmVMThreadLocalSTHolderNode;
import com.oracle.svm.hosted.webimage.wasm.stack.StackClearer;
import com.oracle.svm.hosted.webimage.wasm.stack.StackFrameSizeConstant;
import com.oracle.svm.hosted.webimage.wasm.stack.VirtualStackSlotConstant;
import com.oracle.svm.webimage.hightiercodegen.variables.VariableAllocation;
import com.oracle.svm.webimage.wasm.stack.WebImageWasmFrameMap;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.iterators.NodePredicates;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.framemap.FrameMapBuilderTool;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlot;
import jdk.graal.compiler.lir.framemap.VirtualStackSlotRange;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Proxy;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.VMConstant;

public class WasmLMCodeGenTool extends WasmCodeGenTool {

    private final FrameMapBuilderTool frameMapBuilder;

    /**
     * Current pseudo instruction pointer (IP) relative to the IP at the start of the method.
     * <p>
     * They will later be patched to unique absolute IPs once the number of IPs in each method is
     * known and the method's start IPs are determined.
     *
     * @see WebImageWasmFrameMap
     */
    private int ip = 0;

    /**
     * Set of nodes that have to be spilled to the shadow stack.
     */
    private final EconomicSet<ValueNode> spilledNodes = EconomicSet.create(16);

    protected WasmLMCodeGenTool(CoreProviders provider, VariableAllocation variableAllocation, WebImageWasmCompilationResult compilationResult, WebImageWasmProviders wasmProviders,
                    WasmBlockContext topContext, FrameMapBuilderTool frameMapBuilder, StructuredGraph graph) {
        super(provider, variableAllocation, compilationResult, wasmProviders, topContext, graph);
        this.frameMapBuilder = frameMapBuilder;
    }

    /**
     * Performs all necessary actions for performing a function call (record call, allocate and set
     * IP in callee's stack frame).
     * <p>
     * Only call this method when generating top-level instructions (this means any node that lowers
     * to a Wasm call must be top-level).
     */
    @Override
    public Instruction.AbstractCall getCall(InvokeTarget target, boolean direct, Instruction.AbstractCall callInstruction) {
        int posBefore = getIp();
        int posAfter = allocateIp();

        if (direct) {
            assert callInstruction instanceof Instruction.Call : callInstruction;
        } else {
            assert callInstruction instanceof Instruction.CallIndirect : callInstruction;
        }

        recordCall(posBefore, posAfter, target, direct);
        genIPSetter();
        return callInstruction;
    }

    @Override
    public void prepareForMethod(StructuredGraph g) {
        /*
         * Currently just spills every node that represents an object onto the stack as live.
         *
         * As a small optimization, it ignores constants, since those are always in the image heap
         * (ConstantNode, WebImageWasmVMThreadLocalSTHolderNode), and nodes that just hold the value
         * of other nodes (proxies and compression nodes), since those are spilled by their original
         * node.
         */
        for (ValueNode node : g.getNodes().filter(ValueNode.class).filter(NodePredicates.isNotA(ConstantNode.class).nor(WebImageWasmVMThreadLocalSTHolderNode.class))) {
            if (node instanceof Proxy && node instanceof CompressionNode) {
                continue;
            }

            if (node.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
                spilledNodes.add(node);
            }
        }

        super.prepareForMethod(g);
    }

    @Override
    public void lowerPreamble() {
        genInst(getKnownIds().stackPointer.setter(getStackRelativeAddress(new StackFrameSizeConstant(), false)), "Allocate stack frame");
        if (!spilledNodes.isEmpty()) {
            /*
             * Fills the stack frame with zeros to ensure spill slots contain a null pointer before
             * they are filled since GC may currently access a stack slot before the spill happens.
             *
             * TODO GR-43486 remove once we have proper liveness tracking (in that case only stack
             * slots that aren't guaranteed to be filled before a call that considers them live need
             * to be cleared).
             */
            genInst(new Relocation(StackClearer.INSTANCE), "Clear stack frame");
        }

        // Set the IP stack slot to its initial value
        genIPSetter("Store initial pseudo instruction pointer");

        // Preserve stack pointer if necessary.
        if (nodeLowerer().stackPointerHolder != null) {
            genInst(nodeLowerer().stackPointerHolder.setter(getKnownIds().stackPointer.getter()), "Preserve stack pointer");
        }
    }

    /**
     * Returns the current instruction pointer (IP).
     * <p>
     * This is the IP of the previous instruction with an IP.
     */
    public int getIp() {
        return ip;
    }

    /**
     * Bumps the instruction pointer (IP).
     *
     * @return The IP after the allocated IP.
     */
    public int allocateIp() {
        ip++;
        return getIp();
    }

    public void genIPSetter() {
        genIPSetter(null);
    }

    /**
     * Generates code to set the pseudo-instruction pointer in future callee's stack frame.
     */
    public void genIPSetter(Object comment) {
        genInst(new Instruction.Store(WasmPrimitiveType.i32, new Relocation(new ConstantReference(new AbsoluteIPConstant(method, getIp()))),
                        getStackRelativeAddress(Instruction.Const.forInt(WebImageWasmFrameMap.getIPSize()), false)),
                        comment == null ? "Set pseudo instruction pointer in callee's stack frame" : comment);
    }

    /**
     * Constructs an address relative to the stack pointer.
     *
     * @param addend Value added/subtracted from the stack pointer.
     * @param add Whether to add or subtract from the stack pointer
     */
    public Instruction getStackRelativeAddress(Instruction addend, boolean add) {
        Instruction.Binary.Op op = add ? Instruction.Binary.Op.I32Add : Instruction.Binary.Op.I32Sub;
        return op.create(getKnownIds().stackPointer.getter(), addend);
    }

    /**
     * Constructs an address relative to the stack pointer using a relocation (see
     * {@link #getStackRelativeAddress(Instruction, boolean)}).
     */
    public Instruction getStackRelativeAddress(VMConstant addend, boolean add) {
        return getStackRelativeAddress(Relocation.forConstant(addend), add);
    }

    public Instruction getStackSlotAddress(VirtualStackSlot slot) {
        return Relocation.forConstant(new VirtualStackSlotConstant(slot));
    }

    /**
     * The caller stack pointer is just the current stack pointer plus the frame size.
     */
    public Instruction getCallerStackPointer() {
        return getStackRelativeAddress(new StackFrameSizeConstant(), true);
    }

    public VirtualStackSlot allocateStackMemory(int sizeInBytes, int alignmentInBytes) {
        return frameMapBuilder.allocateStackMemory(sizeInBytes, alignmentInBytes);
    }

    public VirtualStackSlot allocateSpillSlot() {
        return frameMapBuilder.allocateSpillSlot(LIRKind.Illegal);
    }

    public boolean isSpilled(ValueNode n) {
        return spilledNodes.contains(n);
    }

    /**
     * Computes stack layout for the method and resolves all stack-related relocations.
     *
     * @see WebImageWasmFrameMap
     */
    private void stackLayout() {
        List<VirtualStackSlot> stackSlots = frameMapBuilder.getStackSlots();

        /*
         * Maps a virtual stack slot id to its concrete stack slot relative to the stack pointer
         * (after it has been decremented)
         */
        final StackSlot[] slotToOffset = new StackSlot[stackSlots.size()];

        for (VirtualStackSlot slot : stackSlots) {
            int id = slot.getId();
            if (slot instanceof VirtualStackSlotRange range) {
                slotToOffset[id] = frameMapBuilder.getFrameMap().allocateStackMemory(range.getSizeInBytes(), range.getAlignmentInBytes());
            } else if (slot instanceof SimpleVirtualStackSlot simpleVirtualStackSlot) {
                slotToOffset[id] = frameMapBuilder.getFrameMap().allocateSpillSlot(simpleVirtualStackSlot.getValueKind());
                compilationResult.addLiveStackSlot(slotToOffset[id]);
            } else {
                throw GraalError.shouldNotReachHere(slot.toString());
            }
        }

        frameMapBuilder.getFrameMap().finish();
        int frameSize = frameMapBuilder.getFrameMap().totalFrameSize();

        /*
         * Processes all stack-related method-local relocations (frame size, stack slot offset).
         */
        new WasmVisitor() {
            @Override
            public void visitRelocation(Relocation relocation) {
                super.visitRelocation(relocation);

                if (relocation.target instanceof ConstantReference constantReference) {
                    VMConstant constant = constantReference.getConstant();
                    if (constant instanceof StackFrameSizeConstant) {
                        relocation.setValue(Instruction.Const.forInt(frameSize));
                    } else if (constant instanceof VirtualStackSlotConstant virtualStackSlot) {
                        StackSlot stackSlot = slotToOffset[virtualStackSlot.stackSlot().getId()];
                        int offset = stackSlot.getOffset(frameSize);

                        Instruction stackPointer = getKnownIds().stackPointer.getter();
                        Instruction value = offset == 0 ? stackPointer : Instruction.Binary.Op.I32Add.create(stackPointer, Instruction.Const.forInt(offset));
                        relocation.setComment(virtualStackSlot.stackSlot());
                        relocation.setValue(value);
                    }
                } else if (relocation.target instanceof StackClearer) {
                    int sizeToClear = frameSize - WebImageWasmFrameMap.frameSetupSize();

                    List<Instruction> instructions = new ArrayList<>();

                    /*
                     * Depending on the number of bytes that need to be cleared, generate
                     * specialized instructions because memory.fill severely underperforms for small
                     * regions compared to one or two store instructions.
                     */

                    if (sizeToClear == 0) {
                        instructions.add(new Instruction.Nop());
                    } else if (sizeToClear == 4) {
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i32, Instruction.Const.forInt(0), getKnownIds().stackPointer.getter()));
                    } else if (sizeToClear == 8) {
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i64, Instruction.Const.forLong(0), getKnownIds().stackPointer.getter()));
                    } else if (sizeToClear == 12) {
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i64, 0, Instruction.Const.forLong(0), getKnownIds().stackPointer.getter()));
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i32, 8, Instruction.Const.forInt(0), getKnownIds().stackPointer.getter()));
                    } else if (sizeToClear == 16) {
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i64, Instruction.Const.forLong(0), getKnownIds().stackPointer.getter()));
                        instructions.add(new Instruction.Store(WasmPrimitiveType.i64, 8, Instruction.Const.forLong(0), getKnownIds().stackPointer.getter()));
                    } else {
                        instructions.add(new Instruction.MemoryFill(getKnownIds().stackPointer.getter(),
                                        Instruction.Const.forInt(0),
                                        Instruction.Const.forInt(sizeToClear)));
                    }

                    if (instructions.size() == 1) {
                        relocation.setValue(instructions.get(0));
                    } else {
                        Instruction.Block block = new Instruction.Block(null);
                        block.instructions.addAll(instructions);
                        relocation.setValue(block);
                    }
                }
            }
        }.visitFunction(compilationResult.getFunction());

        compilationResult.setTotalFrameSize(frameSize);
    }

    @Override
    public void finish() {
        super.finish();
        stackLayout();

        compilationResult.setTargetCode(new byte[0], getIp() + 1);
    }
}
