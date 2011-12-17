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
import com.sun.cri.ri.*;


public class VirtualObjectNode extends ValueNode implements LIRLowerable {

    @Data private RiType type;
    private EscapeField[] fields;
    private int fieldsCount;

    public VirtualObjectNode(RiType type, EscapeField[] fields) {
        super(StampFactory.illegal());
        this.type = type;
        this.fields = fields;
        this.fieldsCount = fields.length;
    }

    public VirtualObjectNode(RiType type, int fieldCount) {
        super(StampFactory.illegal());
        this.type = type;
        this.fieldsCount = fieldCount;
    }

    public RiType type() {
        return type;
    }

    public EscapeField[] fields() {
        return fields;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nothing to do...
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("type", type);
        return properties;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + " " + type.name();
        } else {
            return super.toString(verbosity);
        }
    }

    public int fieldsCount() {
        return fieldsCount;
    }
}
