/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.virtual;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This class encapsulated the virtual state of an escape analyzed object.
 */
public final class VirtualObjectState extends VirtualState implements Node.IterableNodeType, LIRLowerable {

    @Input private VirtualObjectNode object;
    @Input private NodeInputList<ValueNode> fieldValues;

    public VirtualObjectNode object() {
        return object;
    }

    public NodeInputList<ValueNode> fieldValues() {
        return fieldValues;
    }

    public VirtualObjectState(VirtualObjectNode object, ValueNode[] fieldValues) {
        assert object.fieldsCount() == fieldValues.length;
        this.object = object;
        this.fieldValues = new NodeInputList<>(this, fieldValues);
    }

    private VirtualObjectState(VirtualObjectNode object, List<ValueNode> fieldValues) {
        assert object.fieldsCount() == fieldValues.size();
        this.object = object;
        this.fieldValues = new NodeInputList<>(this, fieldValues);
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        // Nothing to do, virtual object states are processed as part of the handling of StateSplit nodes.
    }

    @Override
    public VirtualObjectState duplicateWithVirtualState() {
        return graph().add(new VirtualObjectState(object, fieldValues));
    }

    @Override
    public void applyToNonVirtual(NodeClosure< ? super ValueNode> closure) {
        for (ValueNode value : fieldValues) {
            closure.apply(value);
        }
    }
}
