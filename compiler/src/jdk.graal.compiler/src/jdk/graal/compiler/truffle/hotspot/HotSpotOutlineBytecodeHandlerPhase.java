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
package jdk.graal.compiler.truffle.hotspot;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.HotSpotTruffleBytecodeHandlerStub;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.ForeignCallWithExceptionNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.graal.compiler.truffle.host.OutlineBytecodeHandlerPhase;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A HotSpot-specific implementation of the {@link OutlineBytecodeHandlerPhase}. This phase is
 * responsible for outlining Truffle bytecode handlers into separate stubs, which can be called from
 * the enclosing method using foreign calls.
 */
public class HotSpotOutlineBytecodeHandlerPhase extends OutlineBytecodeHandlerPhase {

    public static void install(HighTier highTier) {
        HotSpotOutlineBytecodeHandlerPhase phase = new HotSpotOutlineBytecodeHandlerPhase();
        HostInliningPhase.insertBeforeInlining(highTier, phase);
    }

    /**
     * Maps a ResolvedJavaType to its corresponding Java Class for use in a foreign call signature.
     * For primitive types, the corresponding Java Class is returned. For reference types,
     * Object.class is returned as the precise type is not required.
     */
    private static Class<?> toJavaClass(ResolvedJavaType type) {
        return type.isPrimitive() ? type.getJavaKind().toJavaClass() : Object.class;
    }

    private static Class<?>[] toJavaClasses(List<ResolvedJavaType> types) {
        ArrayList<Class<?>> argumentClasses = new ArrayList<>();
        for (ResolvedJavaType type : types) {
            argumentClasses.add(toJavaClass(type));
        }
        return argumentClasses.toArray(new Class<?>[0]);
    }

    /**
     * Replaces an invoke node with a foreign call to the outlined handler stub.
     */
    @Override
    protected FixedNode replaceInvoke(HighTierContext context, TruffleBytecodeHandlerCallsite callsite, Invoke invoke, ValueNode[] arguments) {
        StructuredGraph graph = invoke.asNode().graph();
        HotSpotHostForeignCallsProvider foreignCalls = (HotSpotHostForeignCallsProvider) context.getForeignCalls();
        ForeignCallSignature foreignCallSignature = new ForeignCallSignature(callsite.getStubName(), toJavaClass(callsite.getReturnType()), toJavaClasses(callsite.getArgumentTypes()));

        HotSpotForeignCallLinkage linkage;
        if (foreignCalls.isRegistered(foreignCallSignature)) {
            linkage = foreignCalls.lookupForeignCall(foreignCallSignature);
        } else {
            // Instantiate the stub if not exists
            linkage = foreignCalls.registerStubCall(foreignCallSignature, SAFEPOINT, HAS_SIDE_EFFECT, COMPUTES_REGISTERS_KILLED, LocationIdentity.ANY_LOCATION);
            HotSpotTruffleBytecodeHandlerStub stub = new HotSpotTruffleBytecodeHandlerStub(graph.getOptions(), (HotSpotProviders) context.getProviders(), linkage, callsite);
            HotSpotHostForeignCallsProvider.link(stub);
        }

        if (invoke instanceof InvokeNode invokeNode) {
            // Replace InvokeNode with ForeignCallNode
            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(foreignCalls, foreignCallSignature, arguments));
            foreignCallNode.setBci(callsite.getBci());
            foreignCallNode.setStateAfter(invoke.stateAfter());

            graph.replaceFixed(invokeNode, foreignCallNode);
            return foreignCallNode;
        } else {
            // Replace InvokeWithExceptionNode with ForeignCallWithExceptionNode
            InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
            ForeignCallWithExceptionNode foreignCallWithExceptionNode = graph.add(new ForeignCallWithExceptionNode(linkage.getDescriptor(), arguments));
            foreignCallWithExceptionNode.setBci(callsite.getBci());
            foreignCallWithExceptionNode.setStateAfter(invoke.stateAfter());

            AbstractBeginNode next = invokeWithExceptionNode.next();
            AbstractBeginNode exceptionEdge = invokeWithExceptionNode.exceptionEdge();

            invokeWithExceptionNode.setNext(null);
            invokeWithExceptionNode.setExceptionEdge(null);

            foreignCallWithExceptionNode.setNext(next);
            foreignCallWithExceptionNode.setExceptionEdge(exceptionEdge);
            return foreignCallWithExceptionNode;
        }
    }
}
