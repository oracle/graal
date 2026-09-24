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
package jdk.graal.compiler.nodes.virtual;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Represents a node in the graph that establishes an aliasing relationship between the value stored
 * in the {@link #field} of {@link #receiver} and the {@link #aliasValue}. It allows read
 * elimination to replace future {@link #field} accesses with the {@link #aliasValue}.
 *
 * <p>
 * The producer of an immutable alias must ensure that the field of the aliased receiver does not
 * change while the alias or derived immutable reads can be used. The Java {@code final} modifier
 * alone does not establish this contract. Initializing the same field on another instance is allowed.
 *
 * <p>
 * An immutable alias survives generic memory kills and remains available at a merge if it is
 * present on every predecessor. For a specific field kill, read elimination and partial escape
 * analysis conservatively invalidate both mutable and immutable cache entries for that field,
 * regardless of the receiver. Initializing another instance can therefore discard an alias without
 * requiring receiver alias analysis or rejecting the compilation.
 *
 * <p>
 * This invalidation is more conservative than dominator-based global value numbering (DGVN), which
 * uses {@link LocationIdentity#overlaps(LocationIdentity)}. A mutable field identity does not overlap
 * its immutable counterpart, so DGVN can retain an immutable read across the same kill. The extra
 * invalidation in read elimination is conservative cache bookkeeping, not support for writes to the
 * aliased receiver's immutable field.
 *
 * <p>
 * In particular, unsafe or ordered writes that report only {@link LocationIdentity#any()} can leave
 * immutable aliases cached, and DGVN still trusts derived immutable reads. If a future producer
 * permits such writes to the aliased receiver, stale values could be reused. Supporting that case
 * requires revisiting the immutable-location contract across compiler phases; specific-field
 * invalidation in read elimination alone is insufficient. Invoke invalidation below is likewise an
 * optional optimization, not a substitute for this contract.
 *
 * <p>
 * An optional optimization after inlining inserts field-alias reloads on normal invoke
 * continuations to replace spill/reload pairs with field loads. The immutable values remain valid
 * across calls even without this optimization. Read elimination invalidates the incoming alias
 * there and caches the reload under the same immutable
 * identity, allowing it to merge with an incoming alias on a path that bypasses the call. Invokes also
 * invalidate cached aliases on exceptional continuations. Floating reads must be disabled for these
 * graphs so that reloads remain fixed after calls.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class FieldAliasNode extends FixedWithNextNode implements MemoryAccess, Virtualizable, Lowerable {

    public static final NodeClass<FieldAliasNode> TYPE = NodeClass.create(FieldAliasNode.class);

    @Input ValueNode receiver;
    @Input ValueNode aliasValue;
    private final ResolvedJavaField field;
    private final FieldLocationIdentity location;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public FieldAliasNode(ValueNode receiver, ResolvedJavaField field, ValueNode aliasValue, boolean immutable) {
        super(TYPE, StampFactory.forVoid());
        assert !immutable || field.isFinal() : "immutable fields must also be final";
        this.receiver = receiver;
        this.field = field;
        this.aliasValue = aliasValue;
        this.location = new FieldLocationIdentity(field, immutable);
    }

    public ResolvedJavaField getField() {
        return field;
    }

    public ValueNode getReceiver() {
        return receiver;
    }

    public ValueNode getAlias() {
        return aliasValue;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        this.lastLocationAccess = lla;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (tool.getAlias(getReceiver()) instanceof VirtualInstanceNode virtualObject) {
            int fieldIndex = virtualObject.fieldIndex(getField());
            if (fieldIndex != -1) {
                // Injects aliasing relationship
                tool.setVirtualEntry(virtualObject, fieldIndex, getAlias());
            }
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        // We assume the aliasing relationship is injected into read elimination optimizations
        // before the first lowering.
        graph().removeFixed(this);
    }
}
