/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A {@link PiNode} that also provides an array length in addition to a more refined stamp. A usage
 * that reads the array length, such as an {@link ArrayLengthNode}, can be canonicalized based on
 * this information.
 */
public final class PiArrayNode extends PiNode implements ArrayLengthProvider {

    @Input private ValueNode length;

    public ValueNode length() {
        return length;
    }

    public PiArrayNode(ValueNode object, ValueNode length, Stamp stamp) {
        super(object, stamp);
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!(object() instanceof ArrayLengthProvider) || length() != ((ArrayLengthProvider) object()).length()) {
            return this;
        }
        return super.canonical(tool);
    }

    @NodeIntrinsic
    public static native <T> T piArrayCast(Object object, int length, @ConstantNodeParameter Stamp stamp);
}
