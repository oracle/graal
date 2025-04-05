/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.gc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.memory.address.AddressNode;

@NodeInfo(cycles = CYCLES_8, size = SIZE_4)
public class SerialWriteBarrierNode extends ObjectWriteBarrierNode {
    public static final NodeClass<SerialWriteBarrierNode> TYPE = NodeClass.create(SerialWriteBarrierNode.class);

    /**
     * Denote the status of the object that is the destination of the write corresponding to a
     * {@code SerialWriteBarrierNode}. During barrier expansion, we can take advantage of this
     * information to elide or emit a more efficient sequence for a {@code SerialWriteBarrierNode}.
     */
    public enum BaseStatus {
        /**
         * There is no extra information for this base, the code should be emitted taking all
         * possibilities into consideration.
         */
        DEFAULT,

        /**
         * The object is created inside this compilation unit and there is no loop or call between
         * the allocation and the barrier. Therefore, the base object is likely young and the
         * backend can outline the path in which the object is in the old generation for better code
         * size and performance.
         */
        NO_LOOP_OR_CALL,

        /**
         * The object is created inside this compilation unit and there is no loop or safepoint
         * between the allocation and the barrier. Therefore, if the backend can ensure that a newly
         * allocated object is always young or the remember cards corresponding to it are all
         * dirtied, the object still has that property at the write barrier and the barrier can be
         * elided completely.
         */
        NO_LOOP_OR_SAFEPOINT;

        /**
         * Whether this base is likely young. This corresponds to NO_LOOP_OR_CALL or
         * NO_LOOP_OR_SAFEPOINT above.
         */
        public boolean likelyYoung() {
            return this != DEFAULT;
        }
    }

    private boolean eliminated;

    private BaseStatus baseStatus;

    public SerialWriteBarrierNode(AddressNode address, boolean precise) {
        this(TYPE, address, precise);
    }

    protected SerialWriteBarrierNode(NodeClass<? extends SerialWriteBarrierNode> c, AddressNode address, boolean precise) {
        super(c, address, null, precise);
        this.baseStatus = BaseStatus.DEFAULT;
    }

    public void setEliminated() {
        eliminated = true;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setBaseStatus(BaseStatus status) {
        baseStatus = status;
    }

    public BaseStatus getBaseStatus() {
        return baseStatus;
    }

    @Override
    public Kind getKind() {
        return Kind.POST_BARRIER;
    }
}
