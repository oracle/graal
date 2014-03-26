/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.hotspot.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILControlFlow.CondMoveOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.DeoptimizeOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCall1ArgOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCall2ArgOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCallNoArgOp;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapCompressedOp;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapOp;
import com.oracle.graal.lir.hsail.HSAILMove.LoadCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.LoadOp;
import com.oracle.graal.lir.hsail.HSAILMove.StoreCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.StoreConstantOp;
import com.oracle.graal.lir.hsail.HSAILMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.util.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotLIRGenerator extends HSAILLIRGenerator implements HotSpotLIRGenerator {

    private final HotSpotVMConfig config;
    private final List<DeoptimizeOp> deopts = new ArrayList<>();
    private final ResolvedJavaMethod method;

    public HSAILHotSpotLIRGenerator(StructuredGraph graph, Providers providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        this.config = config;
        this.method = graph.method();
    }

    protected StructuredGraph prepareHostGraph() {
        if (deopts.isEmpty()) {
            return null;
        }
        StructuredGraph hostGraph = new StructuredGraph(method, -2);
        ParameterNode deoptId = hostGraph.unique(new ParameterNode(0, StampFactory.intValue()));
        ParameterNode hsailFrame = hostGraph.unique(new ParameterNode(1, StampFactory.forKind(getProviders().getCodeCache().getTarget().wordKind)));
        ParameterNode reasonAndAction = hostGraph.unique(new ParameterNode(2, StampFactory.intValue()));
        ParameterNode speculation = hostGraph.unique(new ParameterNode(3, StampFactory.object()));
        AbstractBeginNode[] branches = new AbstractBeginNode[deopts.size() + 1];
        int[] keys = new int[deopts.size()];
        int[] keySuccessors = new int[deopts.size() + 1];
        double[] keyProbabilities = new double[deopts.size() + 1];
        int i = 0;
        Collections.sort(deopts, new Comparator<DeoptimizeOp>() {
            public int compare(DeoptimizeOp o1, DeoptimizeOp o2) {
                return o1.getCodeBufferPos() - o2.getCodeBufferPos();
            }
        });
        for (DeoptimizeOp deopt : deopts) {
            keySuccessors[i] = i;
            keyProbabilities[i] = 1.0 / deopts.size();
            keys[i] = deopt.getCodeBufferPos();
            assert keys[i] >= 0;
            branches[i] = createHostDeoptBranch(deopt, hsailFrame, reasonAndAction, speculation);

            i++;
        }
        keyProbabilities[deopts.size()] = 0; // default
        keySuccessors[deopts.size()] = deopts.size();
        branches[deopts.size()] = createHostCrashBranch(hostGraph, deoptId);
        IntegerSwitchNode switchNode = hostGraph.add(new IntegerSwitchNode(deoptId, branches, keys, keyProbabilities, keySuccessors));
        StartNode start = hostGraph.start();
        start.setNext(switchNode);
        /*
         * printf.setNext(printf2); printf2.setNext(switchNode);
         */
        hostGraph.setGuardsStage(GuardsStage.AFTER_FSA);
        return hostGraph;
    }

    private static AbstractBeginNode createHostCrashBranch(StructuredGraph hostGraph, ValueNode deoptId) {
        VMErrorNode vmError = hostGraph.add(new VMErrorNode("Error in HSAIL deopt. DeoptId=%d", deoptId));
        // ConvertNode.convert(hostGraph, Kind.Long, deoptId)));
        vmError.setNext(hostGraph.add(new ReturnNode(ConstantNode.defaultForKind(hostGraph.method().getSignature().getReturnKind(), hostGraph))));
        return BeginNode.begin(vmError);
    }

    private AbstractBeginNode createHostDeoptBranch(DeoptimizeOp deopt, ParameterNode hsailFrame, ValueNode reasonAndAction, ValueNode speculation) {
        BeginNode branch = hsailFrame.graph().add(new BeginNode());
        DynamicDeoptimizeNode deoptimization = hsailFrame.graph().add(new DynamicDeoptimizeNode(reasonAndAction, speculation));
        deoptimization.setStateBefore(createFrameState(deopt.getFrameState().topFrame, hsailFrame));
        branch.setNext(deoptimization);
        return branch;
    }

    private FrameState createFrameState(BytecodeFrame lowLevelFrame, ParameterNode hsailFrame) {
        StructuredGraph hostGraph = hsailFrame.graph();
        ValueNode[] locals = new ValueNode[lowLevelFrame.numLocals];
        for (int i = 0; i < lowLevelFrame.numLocals; i++) {
            locals[i] = getNodeForValueFromFrame(lowLevelFrame.getLocalValue(i), hsailFrame, hostGraph);
        }
        List<ValueNode> stack = new ArrayList<>(lowLevelFrame.numStack);
        for (int i = 0; i < lowLevelFrame.numStack; i++) {
            stack.add(getNodeForValueFromFrame(lowLevelFrame.getStackValue(i), hsailFrame, hostGraph));
        }
        ValueNode[] locks = new ValueNode[lowLevelFrame.numLocks];
        MonitorIdNode[] monitorIds = new MonitorIdNode[lowLevelFrame.numLocks];
        for (int i = 0; i < lowLevelFrame.numLocks; i++) {
            HotSpotMonitorValue lockValue = (HotSpotMonitorValue) lowLevelFrame.getLockValue(i);
            locks[i] = getNodeForValueFromFrame(lockValue, hsailFrame, hostGraph);
            monitorIds[i] = getMonitorIdForHotSpotMonitorValueFromFrame(lockValue, hsailFrame, hostGraph);
        }
        FrameState frameState = hostGraph.add(new FrameState(lowLevelFrame.getMethod(), lowLevelFrame.getBCI(), locals, stack, locks, monitorIds, lowLevelFrame.rethrowException, false));
        if (lowLevelFrame.caller() != null) {
            frameState.setOuterFrameState(createFrameState(lowLevelFrame.caller(), hsailFrame));
        }
        return frameState;
    }

    @SuppressWarnings({"unused", "static-method"})
    private MonitorIdNode getMonitorIdForHotSpotMonitorValueFromFrame(HotSpotMonitorValue lockValue, ParameterNode hsailFrame, StructuredGraph hsailGraph) {
        if (lockValue.isEliminated()) {
            return null;
        }
        throw GraalInternalError.unimplemented();
    }

    private ValueNode getNodeForValueFromFrame(Value localValue, ParameterNode hsailFrame, StructuredGraph hostGraph) {
        ValueNode valueNode;
        if (localValue instanceof Constant) {
            valueNode = ConstantNode.forConstant((Constant) localValue, getProviders().getMetaAccess(), hostGraph);
        } else if (localValue instanceof VirtualObject) {
            throw GraalInternalError.unimplemented();
        } else if (localValue instanceof StackSlot) {
            throw GraalInternalError.unimplemented();
        } else if (localValue instanceof HotSpotMonitorValue) {
            HotSpotMonitorValue hotSpotMonitorValue = (HotSpotMonitorValue) localValue;
            return getNodeForValueFromFrame(hotSpotMonitorValue.getOwner(), hsailFrame, hostGraph);
        } else if (localValue instanceof RegisterValue) {
            RegisterValue registerValue = (RegisterValue) localValue;
            int regNumber = registerValue.getRegister().number;
            valueNode = getNodeForRegisterFromFrame(regNumber, localValue.getKind(), hsailFrame, hostGraph);
        } else if (Value.ILLEGAL.equals(localValue)) {
            valueNode = null;
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
        return valueNode;
    }

    private ValueNode getNodeForRegisterFromFrame(int regNumber, Kind valueKind, ParameterNode hsailFrame, StructuredGraph hostGraph) {
        ValueNode valueNode;
        LocationNode location;
        if (regNumber >= HSAIL.s0.number && regNumber <= HSAIL.s31.number) {
            int intSize = getProviders().getCodeCache().getTarget().arch.getSizeInBytes(Kind.Int);
            long offset = config.hsailFrameSaveAreaOffset + intSize * (regNumber - HSAIL.s0.number);
            location = ConstantLocationNode.create(FINAL_LOCATION, valueKind, offset, hostGraph);
        } else if (regNumber >= HSAIL.d0.number && regNumber <= HSAIL.d15.number) {
            int longSize = getProviders().getCodeCache().getTarget().arch.getSizeInBytes(Kind.Long);
            long offset = config.hsailFrameSaveAreaOffset + longSize * (regNumber - HSAIL.d0.number);
            LocationNode numSRegsLocation = ConstantLocationNode.create(FINAL_LOCATION, Kind.Byte, config.hsailFrameNumSRegOffset, hostGraph);
            ValueNode numSRegs = hostGraph.unique(new FloatingReadNode(hsailFrame, numSRegsLocation, null, StampFactory.forKind(Kind.Byte)));
            location = IndexedLocationNode.create(FINAL_LOCATION, valueKind, offset, numSRegs, hostGraph, 4);
        } else {
            throw GraalInternalError.shouldNotReachHere("unknown hsail register: " + regNumber);
        }
        valueNode = hostGraph.unique(new FloatingReadNode(hsailFrame, location, null, StampFactory.forKind(valueKind)));
        return valueNode;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    private int getLogMinObjectAlignment() {
        return config.logMinObjAlignment();
    }

    private int getNarrowOopShift() {
        return config.narrowOopShift;
    }

    private long getNarrowOopBase() {
        return config.narrowOopBase;
    }

    private int getLogKlassAlignment() {
        return config.logKlassAlignment;
    }

    private int getNarrowKlassShift() {
        return config.narrowKlassShift;
    }

    private long getNarrowKlassBase() {
        return config.narrowKlassBase;
    }

    private static boolean isCompressCandidate(Access access) {
        return access != null && access.isCompressible();
    }

    @Override
    public boolean canStoreConstant(Constant c, boolean isCompressed) {
        return true;
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        throw GraalInternalError.shouldNotReachHere("NYI");
    }

    /**
     * Appends either a {@link CompareAndSwapOp} or a {@link CompareAndSwapCompressedOp} depending
     * on whether the memory location of a given {@link LoweredCompareAndSwapNode} contains a
     * compressed oop. For the {@link CompareAndSwapCompressedOp} case, allocates a number of
     * scratch registers. The result {@link #operand(ValueNode) operand} for {@code node} complies
     * with the API for {@link Unsafe#compareAndSwapInt(Object, long, int, int)}.
     * 
     * @param address the memory location targeted by the operation
     */
    @Override
    public void visitCompareAndSwap(LoweredCompareAndSwapNode node, Value address) {
        Kind kind = node.getNewValue().getKind();
        assert kind == node.getExpectedValue().getKind();
        Variable expected = load(operand(node.getExpectedValue()));
        Variable newValue = load(operand(node.getNewValue()));
        HSAILAddressValue addressValue = asAddressValue(address);
        Variable casResult = newVariable(kind);
        if (config.useCompressedOops && node.isCompressible()) {
            // make 64-bit scratch variables for expected and new
            Variable scratchExpected64 = newVariable(Kind.Long);
            Variable scratchNewValue64 = newVariable(Kind.Long);
            // make 32-bit scratch variables for expected and new and result
            Variable scratchExpected32 = newVariable(Kind.Int);
            Variable scratchNewValue32 = newVariable(Kind.Int);
            Variable scratchCasResult32 = newVariable(Kind.Int);
            append(new CompareAndSwapCompressedOp(casResult, addressValue, expected, newValue, scratchExpected64, scratchNewValue64, scratchExpected32, scratchNewValue32, scratchCasResult32,
                            getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else {
            append(new CompareAndSwapOp(casResult, addressValue, expected, newValue));
        }
        Variable nodeResult = newVariable(node.getKind());
        append(new CondMoveOp(mapKindToCompareOp(kind), casResult, expected, nodeResult, Condition.EQ, Constant.INT_1, Constant.INT_0));
        setResult(node, nodeResult);
    }

    /**
     * Returns whether or not the input access should be (de)compressed.
     */
    private boolean isCompressedOperation(Kind kind, Access access) {
        return access != null && access.isCompressible() && ((kind == Kind.Long && config.useCompressedClassPointers) || (kind == Kind.Object && config.useCompressedOops));
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, Access access) {
        HSAILAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind.getStackKind());
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else {
            append(new LoadOp(kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, Access access) {
        HSAILAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        boolean isCompressed = isCompressedOperation(kind, access);
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c, isCompressed)) {
                if (isCompressed) {
                    if ((c.getKind() == Kind.Object) && c.isNull()) {
                        append(new StoreConstantOp(Kind.Int, storeAddress, Constant.forInt(0), state));
                    } else if (c.getKind() == Kind.Long) {
                        Constant value = compress(c, config.getKlassEncoding());
                        append(new StoreConstantOp(Kind.Int, storeAddress, value, state));
                    } else {
                        throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
                    }
                    return;
                } else {
                    append(new StoreConstantOp(kind, storeAddress, c, state));
                    return;
                }
            }
        }
        Variable input = load(inputVal);
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, DeoptimizingNode deopting) {
        emitDeoptimizeInner(actionAndReason, state(deopting), "emitDeoptimize");
    }

    /***
     * We need 64-bit and 32-bit scratch registers for the codegen $s0 can be live at this block.
     */
    private void emitDeoptimizeInner(Value actionAndReason, LIRFrameState lirFrameState, String emitName) {
        DeoptimizeOp deopt = new DeoptimizeOp(actionAndReason, lirFrameState, emitName, getMetaAccess());
        deopts.add(deopt);
        append(deopt);
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof CurrentJavaThreadNode) {
            throw new GraalInternalError("HSAILHotSpotLIRGenerator cannot handle node: " + node);
        } else {
            super.emitNode(node);
        }
    }

    /***
     * This is a very temporary solution to emitForeignCall. We don't really support foreign calls
     * yet, but we do want to generate dummy code for them. The ForeignCallXXXOps just end up
     * emitting a comment as to what Foreign call they would have made.
     */
    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        Variable result = newVariable(Kind.Object);  // linkage.getDescriptor().getResultType());

        // to make the LIRVerifier happy, we move any constants into registers
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = newVariable(arg.getKind());
            emitMove(loc, arg);
            argLocations[i] = loc;
        }

        // here we could check the callName if we wanted to only handle certain callnames
        String callName = linkage.getDescriptor().getName();
        switch (argLocations.length) {
            case 0:
                append(new ForeignCallNoArgOp(callName, result));
                break;
            case 1:
                append(new ForeignCall1ArgOp(callName, result, argLocations[0]));
                break;
            case 2:
                append(new ForeignCall2ArgOp(callName, result, argLocations[0], argLocations[1]));
                break;
            default:
                throw new InternalError("NYI emitForeignCall " + callName + ", " + argLocations.length + ", " + linkage);
        }
        return result;
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        // this version of emitForeignCall not used for now
    }

    /**
     * @return a compressed version of the incoming constant lifted from AMD64HotSpotLIRGenerator
     */
    protected static Constant compress(Constant c, CompressEncoding encoding) {
        if (c.getKind() == Kind.Long) {
            return Constant.forIntegerKind(Kind.Int, (int) (((c.asLong() - encoding.base) >> encoding.shift) & 0xffffffffL), c.getPrimitiveAnnotation());
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
