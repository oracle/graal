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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.nodes.spi.NodeWithState;

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
        assert state.monitorIdAt(lockIndex).getLockDepth() == lockDepth;
        return new StackLockValue(object, slot, eliminated);
    }

    @Override
    protected boolean verifyFrameState(NodeWithState node, FrameState topState) {
        if (node instanceof FullInfopointNode) {
            return true;
        }
        if (node instanceof ForeignCall) {
            ForeignCall call = (ForeignCall) node;
            ForeignCallDescriptor descriptor = call.getDescriptor();
            if (DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls.containsValue(descriptor.getSignature())) {
                return true;
            }
        }
        FrameState current = topState;
        while (current != null) {
            assert current.getMethod() instanceof HotSpotResolvedJavaMethod : current;
            current = current.outerFrameState();
        }
        // There are many properties of FrameStates which could be validated though it's complicated
        // by some of the idiomatic ways that they are used. This check specifically tries to catch
        // cases where a FrameState that's constructed for reexecution has an incorrect stack depth
        // at invokes.
        if (topState.bci >= 0 && !topState.duringCall() && !topState.rethrowException()) {
            ResolvedJavaMethod m = topState.getMethod();
            int opcode = m.getCode()[topState.bci] & 0xff;
            if (opcode == Bytecodes.INVOKEVIRTUAL || opcode == Bytecodes.INVOKEINTERFACE) {
                assert topState.stackSize() > 0 : "expected non-empty stack: " + topState;
            } else {
                int stackEffect = Bytecodes.stackEffectOf(opcode);
                if (stackEffect < 0) {
                    assert topState.stackSize() >= -stackEffect : "opcode " + opcode + " (" + Bytecodes.nameOf(opcode) + ") stack effect " + stackEffect + ": expected at least " + (-stackEffect) +
                                    " stack depth : " + topState;
                }
            }
        }
        if (node instanceof DeoptimizeNode) {
            assert !topState.duringCall() : topState.toString(Verbosity.Debugger);
        }
        return true;
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
