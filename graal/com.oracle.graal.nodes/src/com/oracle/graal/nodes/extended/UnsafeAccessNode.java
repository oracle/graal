/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public abstract class UnsafeAccessNode extends FixedWithNextNode implements Canonicalizable {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    private final int displacement;
    private final Kind accessKind;

    public UnsafeAccessNode(Stamp stamp, ValueNode object, int displacement, ValueNode offset, Kind accessKind) {
        super(stamp);
        assert accessKind != null;
        this.object = object;
        this.displacement = displacement;
        this.offset = offset;
        this.accessKind = accessKind;
    }

    public ValueNode object() {
        return object;
    }

    public int displacement() {
        return displacement;
    }

    public ValueNode offset() {
        return offset;
    }

    public Kind accessKind() {
        return accessKind;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (offset().isConstant()) {
            long constantOffset = offset().asConstant().asLong();

            // Try to canonicalize to a field access.
            ResolvedJavaType receiverType = ObjectStamp.typeOrNull(object());
            if (receiverType != null) {
                ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(displacement() + constantOffset);
                // No need for checking that the receiver is non-null. The field access includes
                // the null check and if a field is found, the offset is so small that this is
                // never a valid access of an arbitrary address.
                if (field != null && field.getKind() == this.accessKind()) {
                    return cloneAsFieldAccess(field);
                }
            }

            if (constantOffset != 0 && Integer.MAX_VALUE - displacement() >= constantOffset) {
                int intDisplacement = (int) (constantOffset + displacement());
                if (constantOffset == intDisplacement) {
                    return cloneWithZeroOffset(intDisplacement);
                }
            }
        }
        return this;
    }

    protected abstract ValueNode cloneAsFieldAccess(ResolvedJavaField field);

    protected abstract ValueNode cloneWithZeroOffset(int intDisplacement);
}
