/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.OrderedMemoryAccess;
import org.graalvm.compiler.nodes.spi.Lowerable;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The base class of all instructions that access fields.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public abstract class AccessFieldNode extends FixedWithNextNode implements Lowerable, OrderedMemoryAccess, MemoryAccess {

    public static final NodeClass<AccessFieldNode> TYPE = NodeClass.create(AccessFieldNode.class);
    @OptionalInput ValueNode object;
    protected final FieldLocationIdentity location;
    protected final ResolvedJavaField field;
    protected final MemoryOrderMode memoryOrder;

    public ValueNode object() {
        return object;
    }

    /**
     * Constructs a new access field object.
     *
     * @param object the instruction producing the receiver object
     * @param field the compiler interface representation of the field
     * @param memoryOrder specifies the memory ordering requirements of the access. This overrides
     *            the field volatile modifier.
     */
    public AccessFieldNode(NodeClass<? extends AccessFieldNode> c, Stamp stamp, ValueNode object, ResolvedJavaField field, MemoryOrderMode memoryOrder, boolean immutable) {
        super(c, stamp);
        assert !immutable || field.isFinal() : "immutable fields must also be final";
        assert !immutable || !field.isStatic() : "immutable fields must also be non-static";
        this.object = object;
        this.field = field;
        this.memoryOrder = memoryOrder;
        this.location = new FieldLocationIdentity(field, immutable);
    }

    /**
     * Constructs a new access field object.
     *
     * @param object the instruction producing the receiver object
     * @param field the compiler interface representation of the field
     */
    public AccessFieldNode(NodeClass<? extends AccessFieldNode> c, Stamp stamp, ValueNode object, ResolvedJavaField field) {
        this(c, stamp, object, field, MemoryOrderMode.getMemoryOrder(field), false);
    }

    @Override
    public FieldLocationIdentity getLocationIdentity() {
        return location;
    }

    /**
     * Gets the compiler interface field for this field access.
     *
     * @return the compiler interface field for this field access
     */
    public ResolvedJavaField field() {
        return field;
    }

    /**
     * Checks whether this field access is an access to a static field.
     *
     * @return {@code true} if this field access is to a static field
     */
    public boolean isStatic() {
        return field.isStatic();
    }

    /**
     * Note the field access semantics are coupled to the access and not to the field. e.g. it's
     * possible to access volatile fields using non-volatile semantics via VarHandles.
     */
    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && field != null) {
            return super.toString(verbosity) + "#" + field.getName();
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public boolean verify() {
        assertTrue((object == null) == isStatic(), "static field must not have object, instance field must have object");
        return super.verify();
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        if (ordersMemoryAccesses()) {
            return SIZE_2;
        }
        return super.dynamicNodeSizeEstimate();
    }
}
