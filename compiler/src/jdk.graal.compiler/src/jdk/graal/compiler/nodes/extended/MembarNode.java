/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.Map;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.MemoryBarriers;

/**
 * Creates a memory barrier.
 */
@NodeInfo(nameTemplate = "Membar#{p#location/s}", allowedUsageTypes = Memory, cycles = CYCLES_2, size = SIZE_2)
public final class MembarNode extends FixedWithNextNode implements LIRLowerable, SingleMemoryKill {

    /**
     * Describes how in the generated code ordering must be constrained. Note compiler optimization
     * passes currently do not look at a Membar's {@link FenceKind}; instead, optimization
     * constraints are enforced solely according to this node's {@link #location}.
     *
     * For the purposes of this documentation, memory accesses are divided into stores and loads. A
     * memory access which does both (e.g., compare-and-swap) is considered both a load and store.
     */
    public enum FenceKind {
        /**
         * No ordering constraints are imposed on the generated code. This fence is only exists at
         * the compilation optimization level.
         */
        NONE(0),
        /*
         * All stores before this fence are guaranteed to have completed <b>before</b> any
         * subsequent load may execute.
         */
        STORE_LOAD(MemoryBarriers.STORE_LOAD),
        /*
         * All stores before this fence are guaranteed to have completed <b>before</b> any
         * subsequent store may execute.
         */
        STORE_STORE(MemoryBarriers.STORE_STORE),
        /*
         * All loads before this fence are guaranteed to have completed <b>before</b> any subsequent
         * memory access may execute.
         */
        LOAD_ACQUIRE(MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE),
        /*
         * All memory accesses before this fence are guaranteed to have completed <b>before</b> any
         * subsequent store may execute.
         */
        STORE_RELEASE(MemoryBarriers.LOAD_STORE | MemoryBarriers.STORE_STORE),
        /**
         * We must ensure all allocation initializations before this fence have completed before the
         * object can become visible to a different thread.
         *
         * Currently within generated code this is equivalent to a {@link #STORE_STORE} fence.
         */
        ALLOCATION_INIT(MemoryBarriers.STORE_STORE),
        /**
         * Note this fence can only be placed at a constructor exit. We must ensure all writes to
         * values reachable by final fields (of the object currently being constructed) before this
         * fence have completed before the final field can be visible to a different thread.
         *
         * Currently within generated code this is equivalent to a {@link #STORE_STORE} fence.
         */
        CONSTRUCTOR_FREEZE(MemoryBarriers.STORE_STORE),
        /*
         * All memory accesses before this fence are guaranteed to have completed <b>before</b> any
         * subsequent memory access may execute.
         */
        FULL(MemoryBarriers.LOAD_LOAD | MemoryBarriers.STORE_LOAD | MemoryBarriers.LOAD_STORE | MemoryBarriers.STORE_STORE);

        private final int barriers;

        /**
         * @return true iff this fence should be used at the end of object initialization to make a
         *         newly allocated object visible to a different thread.
         */
        public boolean isInit() {
            return this == ALLOCATION_INIT;
        }

        FenceKind(int barriers) {
            this.barriers = barriers;
        }
    }

    public static final NodeClass<MembarNode> TYPE = NodeClass.create(MembarNode.class);
    protected final FenceKind fence;
    protected final LocationIdentity location;

    public MembarNode(FenceKind fence) {
        this(fence, LocationIdentity.any());
    }

    public MembarNode(FenceKind fence, LocationIdentity location) {
        super(TYPE, StampFactory.forVoid());
        assert fence.isInit() == location.isInit() : Assertions.errorMessage(fence, location);
        this.fence = fence;
        this.location = location;
    }

    /**
     * Creates a new {@link MembarNode} to be placed after the initialization of one or more newly
     * allocated objects.
     */
    public static MembarNode forInitialization() {
        return new MembarNode(FenceKind.ALLOCATION_INIT, LocationIdentity.init());
    }

    public FenceKind getFenceKind() {
        return fence;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return location;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        map.put("barriersString", MemoryBarriers.barriersString(fence.barriers));
        return super.getDebugProperties(map);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitMembar(fence.barriers);
    }

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter FenceKind fence);

    @NodeIntrinsic
    public static native void memoryBarrier(@ConstantNodeParameter FenceKind fence, @ConstantNodeParameter LocationIdentity location);
}
