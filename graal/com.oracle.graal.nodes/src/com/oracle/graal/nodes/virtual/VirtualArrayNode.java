/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(nameTemplate = "VirtualArray {p#componentType/s}[{p#length}]")
public class VirtualArrayNode extends VirtualObjectNode {

    private final ResolvedJavaType componentType;
    private final int length;

    public VirtualArrayNode(int virtualId, ResolvedJavaType componentType, int length) {
        super(virtualId);
        this.componentType = componentType;
        this.length = length;
    }

    @Override
    public ResolvedJavaType type() {
        return componentType.arrayOf();
    }

    public ResolvedJavaType componentType() {
        return componentType;
    }

    @Override
    public int entryCount() {
        return length;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nothing to do...
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + " " + componentType.name() + "[" + length + "]";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Object fieldName(int index) {
        return "[" + index + "]";
    }
}
