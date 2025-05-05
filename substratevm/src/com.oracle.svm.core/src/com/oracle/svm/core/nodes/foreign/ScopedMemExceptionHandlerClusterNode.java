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
package com.oracle.svm.core.nodes.foreign;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.nodes.ClusterNode;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.ScopedMemoryAccess.ScopedAccessError;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
public abstract class ScopedMemExceptionHandlerClusterNode extends FixedWithNextNode implements ClusterNode {
    public static final NodeClass<ScopedMemExceptionHandlerClusterNode> TYPE = NodeClass.create(ScopedMemExceptionHandlerClusterNode.class);

    public ScopedMemExceptionHandlerClusterNode(NodeClass<? extends FixedWithNextNode> c, Stamp stamp) {
        super(c, stamp);
    }

    /**
     * See {@link ClusterNode} for details.
     *
     * Mark the beginning of the non exception path of the exception cluster for a
     * {@link ScopedMemoryAccess} checking the validity of a memory session.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
    public static class RegularPathNode extends ScopedMemExceptionHandlerClusterNode implements LIRLowerable {

        public static final NodeClass<RegularPathNode> TYPE = NodeClass.create(RegularPathNode.class);
        @Node.OptionalInput ValueNode scope;

        public RegularPathNode(ValueNode scope) {
            super(TYPE, StampFactory.forVoid());
            this.scope = scope;
        }

        public ValueNode getScope() {
            return scope;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            // nothing to do
        }

        @NodeIntrinsic
        public static native void endClusterNormalPath(long scope);

        @Override
        public void delete() {
            GraphUtil.unlinkFixedNode(this);
            this.safeDelete();
        }
    }

    /**
     * See {@link ClusterNode} for details.
     *
     * Mark the inputs of an exception cluster for a {@link ScopedMemoryAccess} checking the
     * validity of a memory session.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
    public static class ExceptionInputNode extends ScopedMemExceptionHandlerClusterNode implements LIRLowerable {

        public static final NodeClass<ExceptionInputNode> TYPE = NodeClass.create(ExceptionInputNode.class);

        /**
         * A link to the {@link MemoryArenaValidInScopeNode} that allows easy access to the scoped
         * access dominating this exception handler.
         */
        @Node.OptionalInput ValueNode scope;
        /**
         * A link to the {@code MemorySessionImpl} associated with the scoped access.
         */
        @Node.OptionalInput ValueNode input;

        public ExceptionInputNode(ValueNode scope, ValueNode input) {
            super(TYPE, input.stamp(NodeView.DEFAULT));
            this.input = input;
            this.scope = scope;
        }

        public ValueNode getInput() {
            return input;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            // nothing to do
        }

        @NodeIntrinsic
        public static native MemorySessionImpl clusterInputValue(long scope, MemorySessionImpl value);

        @Override
        public void delete() {
            this.replaceAtUsages(input);
            GraphUtil.unlinkFixedNode(this);
            this.safeDelete();
        }
    }

    /**
     * See {@link ClusterNode} for details.
     *
     * Mark the beginning of the exception handler of the exception cluster for a
     * {@link ScopedMemoryAccess} checking the validity of a memory session.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN, allowedUsageTypes = InputType.Memory)
    public static class ExceptionPathNode extends ScopedMemExceptionHandlerClusterNode implements LIRLowerable, SingleMemoryKill, MemoryAccess {

        public static final NodeClass<ExceptionPathNode> TYPE = NodeClass.create(ExceptionPathNode.class);

        @Node.OptionalInput ValueNode exception;
        @Node.OptionalInput ValueNode scope;

        public ExceptionPathNode(ValueNode exception, ValueNode scope) {
            super(TYPE, exception.stamp(NodeView.DEFAULT));
            this.exception = exception;
            this.scope = scope;
        }

        public ValueNode getException() {
            return exception;
        }

        public ValueNode getScope() {
            return scope;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            // nothing to do
        }

        @NodeIntrinsic
        public static native ScopedAccessError endClusterExceptionPath(Throwable t, long scopeNode);

        @Override
        public void delete() {
            if (hasUsagesOfType(InputType.Memory) && lastLocationAccess == null) {
                /*
                 * We might not know the next dominating kill of ANY. Do not bother finding it, add
                 * an artificial kill, we are in the exception path, perf is not relevant here but
                 * graph shape for correctness is.
                 */
                StructuredGraph g = graph();
                SideEffectNode sf = g.addWithoutUnique(new SideEffectNode());
                g.addBeforeFixed(this, sf);
                replaceAtUsages(sf, Memory);
            }
            replaceAtUsages(exception);
            GraphUtil.unlinkFixedNode(this);
            this.safeDelete();
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            return LocationIdentity.ANY_LOCATION;
        }

        @Override
        public LocationIdentity getLocationIdentity() {
            return getKilledLocationIdentity();
        }

        @OptionalInput(Memory) MemoryKill lastLocationAccess;

        @Override
        public MemoryKill getLastLocationAccess() {
            return lastLocationAccess;
        }

        @Override
        public void setLastLocationAccess(MemoryKill lla) {
            updateUsagesInterface(lastLocationAccess, lla);
            lastLocationAccess = lla;
        }

    }

    /**
     * See {@link ClusterNode} for details.
     *
     * Mark the beginning of an exception cluster for a {@link ScopedMemoryAccess} checking the
     * validity of a memory session.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
    public static class ClusterBeginNode extends ScopedMemExceptionHandlerClusterNode implements LIRLowerable {

        public static final NodeClass<ClusterBeginNode> TYPE = NodeClass.create(ClusterBeginNode.class);

        @Node.OptionalInput ValueNode scope;

        public ClusterBeginNode(ValueNode scope) {
            super(TYPE, StampFactory.forVoid());
            this.scope = scope;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            // nothing to do
        }

        @NodeIntrinsic
        public static native void beginExceptionCluster(long scope);

        @Override
        public void delete() {
            GraphUtil.unlinkFixedNode(this);
            this.safeDelete();
        }
    }
}
