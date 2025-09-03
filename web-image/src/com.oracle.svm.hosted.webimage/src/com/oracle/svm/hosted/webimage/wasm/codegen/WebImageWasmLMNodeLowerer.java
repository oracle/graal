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

import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Binary;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Call;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Load;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.MemoryCopy;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.MemoryFill;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.MemoryGrow;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.MemorySize;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Nop;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Store;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Throw;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unreachable;
import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.MEM_CALLOC;
import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.MEM_FREE;
import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.MEM_MALLOC;
import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.MEM_REALLOC;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i64;

import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFixedNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFloatingNode;
import com.oracle.svm.core.graal.nodes.WriteStackPointerNode;
import com.oracle.svm.core.graal.stackvalue.LoweredStackValueNode;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.wasm.WasmImports;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmReservedRegisters;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmVMThreadLocalSTSupport;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmIRWalker.Requirements;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmAddressNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryCopyNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryFillNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryGrowNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemorySizeNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmPrintNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WebImageWasmVMThreadLocalSTHolderNode;
import com.oracle.svm.hosted.webimage.wasm.snippets.WasmImportForeignCallDescriptor;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.wasm.types.WasmLMUtil;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DirectCallTargetNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoweredCallTargetNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.replacements.DimensionsNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.ZeroMemoryNode;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WebImageWasmLMNodeLowerer extends WebImageWasmNodeLowerer {
    public WebImageWasmLMNodeLowerer(WasmCodeGenTool codeGenTool) {
        super(codeGenTool);
    }

    @Override
    protected WasmLMCodeGenTool masm() {
        return (WasmLMCodeGenTool) super.masm();
    }

    /**
     * For nodes that are spilled to the stack, the spilling instructions are also generated.
     *
     * @see WebImageWasmNodeLowerer#lowerVariableStore(ValueNode, Instruction)
     */
    @Override
    public ResolvedVar lowerVariableStore(ValueNode n, Instruction value) {
        assert masm().declared(n) : n;
        assert util.hasValue(n) : n;
        ResolvedVar variable = masm().getAllocatedVariable(n);
        WasmValType type = util.typeForNode(n);
        WasmId.Local local = masm().idFactory.forVariable(variable, type);

        if (masm().isSpilled(n)) {
            VirtualStackSlot slot = masm().allocateSpillSlot();
            /*
             * Spilled nodes are always stored in variables and written to a stack slot.
             *
             * The produced instructions look like this:
             *
             * @formatter:off
             * (i32.add (global.get $stackPointer) (i32.const 0xNN)) (; Calculate stack slot address ;)
             * (local.tee $l1 (...)) (; Computation of object pointer ;)
             * (i32.store)
             * @formatter:on
             *
             * After the local.tee instruction the stack has the object's pointer at the top followed by the address of the spill stack slot and the object pointer is stored to the local variable.
             * The i32.store instruction then pops those two values to spill the value to the stack.
             */
            masm().genInst(masm().getStackSlotAddress(slot));
            masm().genInst(local.tee(value), n);
            masm().genInst(new Store(WasmLMUtil.POINTER_TYPE, new Nop(), new Nop()), "Spilling live object to stack");
        } else {
            return super.lowerVariableStore(n, value);
        }

        return variable;
    }

    /**
     * Lowers the given node to an {@link Instruction} that produces no value.
     * <p>
     * This method is for nodes that do not produce a value in the WASM lowering.
     *
     * @return The {@link Instruction} for the node or null if the node does nothing.
     */
    @Override
    protected Instruction lowerTopLevelStatement(Node n, Requirements reqs) {
        /*
         * We never want to emit a value-producing node at the top level. They always have to be
         * emitted as a parameter of another node.
         */
        assert !util.hasValue(n) : n;

        if (n instanceof ReturnNode returnNode) {
            return lowerReturn(returnNode);
        } else if (n instanceof BlackholeNode blackhole) {
            return lowerDrop(blackhole.getValue());
        } else if (n instanceof ReachabilityFenceNode reachabilityFence) {
            return lowerReachabilityFence(reachabilityFence);
        } else if (n instanceof LoweredDeadEndNode) {
            /*
             * Technically, this node is already unreachable and could not emit any code. But for
             * early detection of bugs for which this node is actually reached, an explicit WASM
             * unreachable instruction is emitted. The overhead is only one byte per instruction.
             */
            return new Unreachable();
        } else if (n instanceof WasmTrapNode) {
            return new Unreachable();
        } else if (n instanceof MemoryAnchorNode) {
            // Nothing to emit, since this node is used for structural purposes only.
            return null;
        } else if (n instanceof UnwindNode unwind) {
            return lowerUnwind(unwind);
        } else if (n instanceof AssertionNode assertion) {
            return lowerAssert(assertion);
        } else if (n instanceof WriteStackPointerNode writeStackPointer) {
            // TODO GR-42105 stop using wrap
            return masm().getKnownIds().stackPointer.setter(Unary.Op.I32Wrap64.create(lowerExpression(writeStackPointer.getValue())));
        } else if (n instanceof WasmPrintNode printNode) {
            return lowerPrint(printNode);
        } else {
            /*
             * There are some nodes that can be both top-level statements and values (e.g. invokes
             * with and without return values) and for these we need to delegate to dispatch.
             */
            assert n instanceof ValueNode : n;
            return dispatch((ValueNode) n, reqs);
        }
    }

    /**
     * Generates code to return from the current function.
     * <p>
     * If the function has a stack frame and a return value, the return value must be computed
     * before the stack pointer is reset. Otherwise, the nodes inlined into the return execute with
     * the wrong stack pointer and may cause problems.
     * <p>
     * Because of that, the value producing node is emitted directly into the block and the return
     * instruction is given a nop as input. This will place it on the stack and the return
     * instruction will then read the return value from the stack. The modification of the stack
     * pointer does not overwrite the return value or pollute the stack. This would look something
     * like this:
     *
     * <pre>
     * {@code
     * (i32.const 0xfeff) (; Return value ;)
     * (global.set $stackPointer
     *      (i32.add
     *          (global.get $stackPointer)
     *          (i32.const ...)
     *      )
     * )
     * (return (nop))
     * }
     * </pre>
     */
    @Override
    protected Instruction lowerReturn(ReturnNode returnNode) {
        ValueNode resultNode = returnNode.result();
        boolean isVoid = resultNode == null;
        Instruction result = isVoid ? null : lowerExpression(resultNode);

        if (!isVoid) {
            masm().genInst(result, resultNode);
            result = new Nop();
            result.setComment("Placeholder, return value is read from stack");
        }
        masm().genInst(masm().getKnownIds().stackPointer.setter(masm().getCallerStackPointer()), "Deallocate stack frame");

        return new Instruction.Return(result);
    }

    private Instruction lowerPrint(WasmPrintNode printNode) {
        Instruction fd = lowerExpression(printNode.getFd());
        /*
         * TODO GR-42105 stop using wrap
         */
        Instruction address = Unary.Op.I32Wrap64.create(lowerExpression(printNode.input()));
        Instruction length = Unary.Op.I32Wrap64.create(lowerExpression(printNode.getLength()));
        ImportDescriptor.Function printImport = switch (printNode.elementSize) {
            case 1 -> WasmImports.printBytes;
            case 2 -> WasmImports.printChars;
            default -> throw GraalError.shouldNotReachHere("Print node with element size " + printNode.elementSize);
        };
        return new Call(masm().idFactory.forFunctionImport(printImport), fd, address, length);
    }

    private Instruction lowerUnwind(UnwindNode n) {
        return new Throw(masm().getKnownIds().getJavaThrowableTag(), lowerExpression(n.exception()));
    }

    @Override
    protected Instruction dispatch(ValueNode n, Requirements reqs) {
        if (n instanceof LoweredStackValueNode loweredStackValue) {
            return lowerStackValue(loweredStackValue);
        } else if (n instanceof DimensionsNode dimensions) {
            return lowerDimensions(dimensions);
        } else if (n instanceof ReadCallerStackPointerNode) {
            // TODO GR-42105 stop using extend
            return Unary.Op.I64ExtendI32U.create(masm().getCallerStackPointer());
        } else if (n instanceof InvokeNode invoke) {
            return lowerInvoke(invoke);
        } else if (n instanceof InvokeWithExceptionNode invoke) {
            return lowerInvoke(invoke);
        } else if (n instanceof ForeignCall foreignCall) {
            return lowerForeignCall(foreignCall);
        } else if (n instanceof LogicNode logic) {
            return lowerLogicNode(logic, reqs);
        } else if (n instanceof FixedAccessNode fixedAccess) {
            return lowerFixedAccess(fixedAccess);
        } else if (n instanceof WasmAddressNode addressNode) {
            return lowerWasmAddress(addressNode);
        } else if (n instanceof CGlobalDataLoadAddressNode globalData) {
            return lowerGlobalData(globalData);
        } else if (n instanceof ConstantNode constant) {
            return lowerConstant(constant);
        } else if (n instanceof ParameterNode param) {
            return lowerParam(param);
        } else if (n instanceof BinaryNode binary) {
            return lowerBinary(binary);
        } else if (n instanceof CompressionNode compression) {
            return lowerCompression(compression, reqs);
        } else if (n instanceof UnaryNode unary) {
            return lowerUnary(unary);
        } else if (n instanceof ConditionalNode conditional) {
            return lowerConditional(conditional);
        } else if (n instanceof PiNode pi) {
            return lowerPi(pi, reqs);
        } else if (n instanceof IntegerDivRemNode divRem) {
            return lowerDivRem(divRem);
        } else if (n instanceof FixedValueAnchorNode fixedValueAnchor) {
            return lowerExpression(fixedValueAnchor.object());
        } else if (n instanceof PublishWritesNode publishWrites) {
            return lowerExpression(publishWrites.getOriginalNode());
        } else if (n instanceof WordCastNode wordCast) {
            return lowerWordCast(wordCast);
        } else if (n instanceof FloatingWordCastNode floatingWordCast) {
            return lowerFloatingWordCast(floatingWordCast);
        } else if (n instanceof ReadExceptionObjectNode readExceptionObject) {
            return lowerReadException(readExceptionObject);
        } else if (n instanceof ReadReservedRegisterFloatingNode readReservedRegister) {
            return lowerReadReservedRegister(readReservedRegister.getRegister());
        } else if (n instanceof ReadReservedRegisterFixedNode readReservedRegister) {
            return lowerReadReservedRegister(readReservedRegister.getRegister());
        } else if (n instanceof WebImageWasmVMThreadLocalSTHolderNode threadLocalHolder) {
            return lowerThreadLocalHolder(threadLocalHolder);
        } else if (n instanceof WasmMemorySizeNode) {
            return new MemorySize();
        } else if (n instanceof WasmMemoryGrowNode memoryGrow) {
            /*
             * TODO GR-42105 stop using extend and wrap
             */
            return Unary.Op.I64ExtendI32U.create(new MemoryGrow(Unary.Op.I32Wrap64.create(lowerExpression(memoryGrow.getNumPages()))));
        } else if (n instanceof WasmMemoryCopyNode memoryCopy) {
            return new MemoryCopy(
                            lowerExpression(memoryCopy.getTarget()),
                            lowerExpression(memoryCopy.getSource()),
                            lowerExpression(memoryCopy.getSize()));
        } else if (n instanceof WasmMemoryFillNode memoryFill) {
            return new MemoryFill(
                            lowerExpression(memoryFill.getStart()),
                            lowerExpression(memoryFill.getFillValue()),
                            lowerExpression(memoryFill.getSize()));
        } else if (n instanceof JSBody jsBody) {
            return lowerJSBody(jsBody);
        } else if (n instanceof JSCallNode jsCall) {
            return lowerJSCall(jsCall);
        } else {
            assert !isForbiddenNode(n) : reportForbiddenNode(n);
            throw GraalError.shouldNotReachHere("Tried to lower unknown node: " + n);
        }
    }

    @Override
    protected Instruction lowerWasmImportForeignCall(WasmImportForeignCallDescriptor descriptor, Instructions args) {
        throw GraalError.unimplemented("No foreign calls to imports should exist here: " + descriptor);
    }

    @Override
    protected Instruction lowerIsNull(IsNullNode n) {
        return Unary.Op.I32Eqz.create(lowerExpression(n.getValue()));
    }

    protected Instruction lowerPi(PiNode pi, Requirements reqs) {
        // In the WasmLM backend, objects are untyped (i32) and don't require explicit casting
        return lowerExpression(pi.getOriginalNode(), reqs);
    }

    protected <T extends FixedNode & Invoke> Instruction lowerInvoke(T node) {
        HostedMethod targetMethod = (HostedMethod) node.getTargetMethod();
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) node.callTarget();
        Instructions params = new Instructions();
        callTarget.arguments().forEach(param -> params.add(lowerExpression(param)));

        if (callTarget instanceof IndirectCallTargetNode indirectCallTarget) {
            WasmPrimitiveType addressType = util.typeForNode(indirectCallTarget.computedAddress()).asPrimitive();
            Instruction index = lowerExpression(indirectCallTarget.computedAddress());
            /*
             * The computed address can have different kind of stamps that are represented as either
             * i32 or i64 in wasm. For example the stamp could be an i64 integer stamp (represented
             * as i64) or a method pointer stamp (represented as i32). If the computed address is
             * represented as an i64, it has to first be truncated to i32.
             */
            if (addressType == i64) {
                index = Unary.Op.I32Wrap64.create(index);
            }
            TypeUse typeUse;

            if (targetMethod == null) {
                /*
                 * If there is no target method, reconstruct the TypeUse from the call target node.
                 */
                assert !indirectCallTarget.invokeKind().hasReceiver() : "Calls to an address cannot have receivers: " + indirectCallTarget;
                MetaAccessProvider metaAccess = masm().getProviders().getMetaAccess();
                ResolvedJavaType returnType = indirectCallTarget.returnStamp().getTrustedStamp().javaType(metaAccess);
                typeUse = WebImageWasmBackend.signatureToTypeUse(masm().wasmProviders, indirectCallTarget.signature(), returnType);
            } else {
                typeUse = WebImageWasmBackend.methodToTypeUse(masm().wasmProviders, targetMethod);
            }
            FunctionTypeDescriptor typeDescriptor = FunctionTypeDescriptor.createSimple(typeUse);
            return masm().getCall(targetMethod, false, new Instruction.CallIndirect(masm().getKnownIds().functionTable, index, masm().idFactory.newFuncType(typeDescriptor), typeUse, params));
        } else if (callTarget instanceof DirectCallTargetNode) {
            return masm().getCall(targetMethod, true, new Call(masm().idFactory.forMethod(targetMethod), params));
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(callTarget);
        }
    }

    private Instruction lowerReadReservedRegister(Register register) {
        if (register.equals(WebImageWasmReservedRegisters.FRAME_REGISTER)) {
            // TODO GR-42105 stop using extend
            return Unary.Op.I64ExtendI32U.create(masm().getKnownIds().stackPointer.getter());
        } else {
            throw GraalError.unimplemented("Cannot read register: " + register); // ExcludeFromJacocoGeneratedReport
        }
    }

    private Instruction lowerWasmAddressBase(WasmAddressNode n) {
        ValueNode baseNode = n.getBase();

        Instruction base = lowerExpression(baseNode);

        /*
         * The base can be either a word-size node (pointer or integer, which are currently 64-bit)
         * or an object (which is 32-bit). For 64-bit inputs, truncation to 32-bit is needed.
         */
        if (baseNode.getStackKind() == JavaKind.Long) {
            // TODO GR-42105 stop using wrap
            base = Unary.Op.I32Wrap64.create(base);
        }

        return base;
    }

    private Instruction lowerWasmAddress(WasmAddressNode n) {
        ValueNode offsetNode = n.getIndex();
        return Binary.Op.I32Add.create(lowerWasmAddressBase(n), lowerExpression(offsetNode));
    }

    private Instruction lowerGlobalData(CGlobalDataLoadAddressNode n) {
        return masm().getRelocation(new CGlobalDataReference(n.getDataInfo()));
    }

    private Instruction lowerFixedAccess(FixedAccessNode n) {
        assert !n.getUsedAsNullCheck() : "Accesses cannot be used as null checksAccesses cannot be used as null checks";

        WasmAddressNode addressNode = (WasmAddressNode) n.getAddress();

        if (n instanceof ReadNode || n instanceof WriteNode) {
            Instruction address;
            Instruction offsetInstr;

            if (addressNode.hasConstantOffset()) {
                address = lowerWasmAddressBase(addressNode);
                offsetInstr = lowerConstant(addressNode.getConstantOffset());
            } else {
                address = lowerExpression(addressNode);
                offsetInstr = Const.forInt(0);
            }

            if (n instanceof ReadNode read) {
                JavaKind stackKind = read.getStackKind();
                int accessBits = stackKind == JavaKind.Object ? WasmLMUtil.POINTER_KIND.getBitCount() : read.getAccessBits();
                MemoryExtendKind extendKind = read.getExtendKind();

                /*
                 * By default, zero-extension is used since that's how smaller types are stored in
                 * an i32
                 */
                boolean signExtend = false;
                if (extendKind != MemoryExtendKind.DEFAULT) {
                    assert extendKind.getExtendedBitSize() == stackKind.getBitCount() : extendKind + ", " + stackKind;
                    signExtend = extendKind.isSignExtend();
                }

                WasmValType stackType = util.mapType(stackKind);
                assert stackType instanceof WasmPrimitiveType : "Attempt to read non-primitive value from memory: " + n;
                return new Load((WasmPrimitiveType) stackType, offsetInstr, address, accessBits, signExtend);
            } else {
                WriteNode write = (WriteNode) n;
                ValueNode value = write.value();
                JavaKind writeKind = value.getStackKind();

                JavaKind memoryKind = util.memoryKind(value.stamp(NodeView.DEFAULT));
                WasmValType stackType = util.mapType(writeKind);
                assert stackType instanceof WasmPrimitiveType : "Attempt to write non-primitive value to memory: " + n;
                return new Store((WasmPrimitiveType) stackType, offsetInstr, lowerExpression(write.value()), address, memoryKind.getBitCount());
            }
        } else if (n instanceof ZeroMemoryNode zeroMemory) {
            Instruction length = lowerExpression(zeroMemory.getLength());

            // TODO GR-42105 stop using wrap
            return new MemoryFill(lowerExpression(addressNode), Const.forInt(0), Unary.Op.I32Wrap64.create(length));
        } else {
            throw GraalError.shouldNotReachHere(n.toString());
        }
    }

    private Instruction lowerThreadLocalHolder(WebImageWasmVMThreadLocalSTHolderNode threadLocalHolder) {
        WebImageWasmVMThreadLocalSTSupport threadLocalSupport = ImageSingletons.lookup(WebImageWasmVMThreadLocalSTSupport.class);

        boolean isObject = threadLocalHolder.getThreadLocalInfo().isObject;
        Object holder = isObject ? threadLocalSupport.objectThreadLocals : threadLocalSupport.primitiveThreadLocals;
        return masm().getConstantRelocation(masm().getProviders().getSnippetReflection().forObject(holder));
    }

    private Instruction lowerStackValue(LoweredStackValueNode stackValue) {
        LoweredStackValueNode.StackSlotHolder stackSlotHolder = stackValue.getStackSlotHolder();
        int sizeInBytes = stackValue.getSizeInBytes();
        int alignmentInBytes = stackValue.getAlignmentInBytes();

        assert stackSlotHolder != null : "node not processed by StackValuePhase";
        assert sizeInBytes > 0 : "Stack slot with size zero";

        VirtualStackSlot slot = stackSlotHolder.getSlot();
        if (slot == null) {
            slot = masm().allocateStackMemory(sizeInBytes, alignmentInBytes);
            stackSlotHolder.setSlot(slot);
        }

        // TODO GR-42105 stop using extend
        return Unary.Op.I64ExtendI32U.create(masm().getStackSlotAddress(slot));
    }

    private Instruction lowerDimensions(DimensionsNode dimensions) {
        int sizeInBytes = dimensions.getRank() * Integer.BYTES;
        VirtualStackSlot array = masm().allocateStackMemory(sizeInBytes, Integer.BYTES);
        // TODO GR-42105 stop using extend
        return Unary.Op.I64ExtendI32U.create(masm().getStackSlotAddress(array));
    }

    /**
     * Functions in {@link JSCallNode} which pass objects that have been adapted to work with the
     * WasmLM backend.
     */
    private static final Set<JSSystemFunction> SUPPORTED_OBJECT_JS_CALLS = Set.of(JSCallNode.GEN_CALL_STACK, JSCallNode.GET_CURRENT_WORKING_DIRECTORY);
    /**
     * Functions in {@link JSCallNode} which are not supported in the WasmLM backend.
     */
    private static final Set<JSSystemFunction> FORBIDDEN_JS_CALLS = Set.of(MEM_MALLOC, MEM_CALLOC, MEM_REALLOC, MEM_FREE);

    private Instruction lowerJSCall(JSCallNode n) {
        JSSystemFunction func = n.getFunctionDefinition();

        boolean passesObject = false;

        for (ValueNode node : n.getArguments()) {
            if (node.stamp(NodeView.DEFAULT).isPointerStamp()) {
                passesObject = true;
                break;
            }
        }

        if (n.stamp(NodeView.DEFAULT).isPointerStamp()) {
            passesObject = true;
        }

        if (passesObject && !SUPPORTED_OBJECT_JS_CALLS.contains(func)) {
            // TODO GR-42437 Passing objects is not yet supported. Consider failing if this happen
            // and make sure no JSCallNode passing objects is created in the WASM backend.
            return getStub(n);
        }

        // TODO GR-42437 Support all calls
        if (FORBIDDEN_JS_CALLS.contains(func)) {
            return getStub(n);
        }

        Instructions params = new Instructions();
        n.getArguments().forEach(param -> params.add(lowerExpression(param)));

        return new Call(masm().wasmProviders.getJSCounterparts().idForJSFunction(masm().wasmProviders, func), params);
    }

    private Instruction lowerJSBody(JSBody n) {
        // TODO GR-42437 Support for @JS and @JavaScriptBody
        return getStub(n.asNode());
    }

}
