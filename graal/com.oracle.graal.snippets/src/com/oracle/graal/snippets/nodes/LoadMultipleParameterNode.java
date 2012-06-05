/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets.nodes;

import java.lang.reflect.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.Snippet.Parameter;

/**
 * Implements the semantics of a snippet {@link Parameter} whose {@link Parameter#multiple()} element is {@code true}.
 */
public final class LoadMultipleParameterNode extends FixedWithNextNode implements Canonicalizable {

    @Input private ValueNode index;

    private final LocalNode[] locals;

    public ValueNode index() {
        return index;
    }

    public LoadMultipleParameterNode(ConstantNode array, int localIndex, ValueNode index, Stamp stamp) {
        super(stamp);
        int length = Array.getLength(array.asConstant().asObject());
        this.index = index;
        locals = new LocalNode[length];
        for (int i = 0; i < length; i++) {
            int idx = localIndex << 16 | i;
            LocalNode local = array.graph().unique(new LocalNode(idx, stamp()));
            locals[i] = local;
        }
    }

    public LocalNode getLocal(int idx) {
        assert idx < locals.length;
        return locals[idx];
    }

    public int getLocalCount() {
        return locals.length;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert index.isConstant();
        return getLocal(index().asConstant().asInt());
    }
}
