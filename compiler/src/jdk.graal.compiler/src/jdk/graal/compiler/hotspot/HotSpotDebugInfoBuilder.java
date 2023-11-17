/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.gen.DebugInfoBuilder;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.spi.NodeValueMap;
import jdk.graal.compiler.nodes.spi.NodeWithState;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Extends {@link DebugInfoBuilder} to allocate the extra debug information required for locks.
 */
public class HotSpotDebugInfoBuilder extends DebugInfoBuilder {

    private final HotSpotLockStack lockStack;

    private int maxInterpreterFrameSize;

    private HotSpotCodeCacheProvider codeCacheProvider;

    public HotSpotDebugInfoBuilder(NodeValueMap nodeValueMap, HotSpotLockStack lockStack, HotSpotLIRGenerator gen) {
        super(nodeValueMap, gen.getProviders().getMetaAccessExtensionProvider(), gen.getResult().getLIR().getDebug());
        this.lockStack = lockStack;
        this.codeCacheProvider = gen.getProviders().getCodeCache();
    }

    public HotSpotLockStack lockStack() {
        return lockStack;
    }

    public int maxInterpreterFrameSize() {
        return maxInterpreterFrameSize;
    }

    @Override
    protected JavaValue computeLockValue(FrameState state, int lockIndex) {
        int lockDepth = lockIndex;
        if (state.outerFrameState() != null) {
            lockDepth += state.outerFrameState().nestedLockDepth();
        }
        VirtualStackSlot slot = lockStack.makeLockSlot(lockDepth);
        ValueNode lock = state.lockAt(lockIndex);
        JavaValue object = toJavaValue(lock);
        boolean eliminated = object instanceof VirtualObject || state.monitorIdAt(lockIndex).isEliminated();
        assert state.monitorIdAt(lockIndex).getLockDepth() == lockDepth : Assertions.errorMessage(state, lockIndex, state.monitorIdAt(lockIndex), lockDepth);
        return new StackLockValue(object, slot, eliminated);
    }

    @Override
    protected void verifyFrameState(NodeWithState node, FrameState topState) {
        if (node instanceof FullInfopointNode) {
            return;
        }

        FrameState current = topState;
        while (current != null) {
            assert current.getMethod() instanceof HotSpotResolvedJavaMethod : current;
            current = current.outerFrameState();
        }

        if (node instanceof ForeignCall) {
            ForeignCall call = (ForeignCall) node;
            ForeignCallDescriptor descriptor = call.getDescriptor();
            if (DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls.containsValue(descriptor.getSignature())) {
                // This case is special because we can't use rethrowException. This path must be
                // marked as reexecutable so it doesn't fail some internal asserts even though the
                // actual deopt is done with Unpack_exception which overrides the reexectuable flag
                GraalError.guarantee(topState.getStackState() == FrameState.StackState.BeforePop && topState.bci >= 0 && topState.stackSize() == 0, "invalid state %s for bytecode exception %s",
                                topState, descriptor.getSignature());
                GraalError.guarantee(!topState.getStackState().duringCall, "must be not duringCall to set reexecute bit");
                return;
            }
        }
        // There are many properties of FrameStates which could be validated though it's complicated
        // by some of the idiomatic ways that they are used. This check specifically tries to catch
        // cases where a FrameState that's constructed for reexecution has an incorrect stack depth
        // at invokes.
        if (topState.bci >= 0) {
            ResolvedJavaMethod m = topState.getMethod();
            int opcode = m.getCode()[topState.bci] & 0xff;
            int stackEffect = Bytecodes.stackEffectOf(opcode);
            switch (topState.getStackState()) {
                case BeforePop:
                    if (opcode == Bytecodes.INVOKEVIRTUAL || opcode == Bytecodes.INVOKEINTERFACE) {
                        GraalError.guarantee(topState.stackSize() > 0, "expected non-empty stack: %s", topState);
                    } else {
                        if (stackEffect < 0) {
                            GraalError.guarantee(topState.stackSize() >= -stackEffect, "opcode %d (%s) stack effect %d: expected at least %d stack depth : %s", opcode, Bytecodes.nameOf(opcode),
                                            stackEffect, -stackEffect, topState);
                        }
                    }
                    break;
                case AfterPop:
                    GraalError.guarantee(!shouldReexecute(opcode), "hotspot says this must deopt with reexecute: %s %s", Bytecodes.nameOf(opcode), topState);
                    break;
                case Rethrow:
                    GraalError.guarantee(topState.stackSize() == 1, "rethrow frame states should have a single value on the top of stack: %s", topState);
                    break;
            }
            if (node instanceof DeoptimizeNode) {
                // The FrameState must either have the arguments to the current bytecode on the top
                // of
                // stack or it must be a rethrow
                GraalError.guarantee(topState.getStackState() != FrameState.StackState.AfterPop || stackEffect >= 0, "must be executable state %S", topState);
            }
        }
    }

    /**
     * Returns true for bytecodes that are required to be reexecuted according to HotSpot. Copied
     * {@code AbstractInterpreter::bytecode_should_reexecute} in
     * src/hotspot/share/interpreter/abstractInterpreter.cpp.
     */
    boolean shouldReexecute(int code) {
        switch (code) {
            case Bytecodes.LOOKUPSWITCH:
            case Bytecodes.TABLESWITCH:
            case Bytecodes.LCMP:
            case Bytecodes.FCMPL:
            case Bytecodes.FCMPG:
            case Bytecodes.DCMPL:
            case Bytecodes.DCMPG:
            case Bytecodes.IFNULL:
            case Bytecodes.IFNONNULL:
            case Bytecodes.GOTO:
            case Bytecodes.GOTO_W:
            case Bytecodes.IFEQ:
            case Bytecodes.IFNE:
            case Bytecodes.IFLT:
            case Bytecodes.IFGE:
            case Bytecodes.IFGT:
            case Bytecodes.IFLE:
            case Bytecodes.IF_ICMPEQ:
            case Bytecodes.IF_ICMPNE:
            case Bytecodes.IF_ICMPLT:
            case Bytecodes.IF_ICMPGE:
            case Bytecodes.IF_ICMPGT:
            case Bytecodes.IF_ICMPLE:
            case Bytecodes.IF_ACMPEQ:
            case Bytecodes.IF_ACMPNE:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.AASTORE:
            case Bytecodes.ATHROW:
                return true;
        }
        return false;
    }

    @Override
    protected BytecodeFrame computeFrameForState(NodeWithState node, FrameState state) {
        if (isPlaceholderBci(state.bci) && state.bci != BytecodeFrame.BEFORE_BCI) {
            raiseInvalidFrameStateError(state);
        }
        BytecodeFrame result = super.computeFrameForState(node, state);
        maxInterpreterFrameSize = Math.max(maxInterpreterFrameSize, codeCacheProvider.interpreterFrameSize(result));
        return result;
    }

    protected void raiseInvalidFrameStateError(FrameState state) throws GraalGraphError {
        // This is a hard error since an incorrect state could crash hotspot
        NodeSourcePosition sourcePosition = state.getNodeSourcePosition();
        List<String> context = new ArrayList<>();
        ResolvedJavaMethod replacementMethodWithProblematicSideEffect = null;
        if (sourcePosition != null) {
            NodeSourcePosition pos = sourcePosition;
            while (pos != null) {
                StringBuilder sb = new StringBuilder("parsing ");
                ResolvedJavaMethod method = pos.getMethod();
                MetaUtil.appendLocation(sb, method, pos.getBCI());
                if (pos.isSubstitution()) {
                    replacementMethodWithProblematicSideEffect = method;
                }
                context.add(sb.toString());
                pos = pos.getCaller();
            }
        }
        String message = "Invalid frame state " + state;
        if (replacementMethodWithProblematicSideEffect != null) {
            message += " associated with a side effect in " + replacementMethodWithProblematicSideEffect.format("%H.%n(%p)") + " at a position that cannot be deoptimized to";
        }
        GraalGraphError error = new GraalGraphError(message);
        for (String c : context) {
            error.addContext(c);
        }
        throw error;
    }
}
