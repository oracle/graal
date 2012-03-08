/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.virtual;

import java.util.*;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

public class VirtualObjectFieldNode extends ValueNode implements LIRLowerable {

    @Input private VirtualObjectNode object;
    @Input private ValueNode lastState;
    @Input private ValueNode input;

    private int index;

    public VirtualObjectNode object() {
        return object;
    }

    public ValueNode lastState() {
        return lastState;
    }

    public ValueNode input() {
        return input;
    }

    public VirtualObjectFieldNode(VirtualObjectNode object, ValueNode lastState, ValueNode input, int index) {
        super(StampFactory.illegal());
        this.index = index;
        this.object = object;
        this.lastState = lastState;
        this.input = input;
    }

    public int index() {
        return index;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nothing to do...
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("index", index);
        return properties;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && object().fields() != null) {
            return super.toString(Verbosity.Name) + " " + object().fields()[index].name();
        } else {
            return super.toString(verbosity);
        }
    }
}
