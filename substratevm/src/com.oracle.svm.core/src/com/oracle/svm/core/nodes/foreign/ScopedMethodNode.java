/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes.foreign;

import com.oracle.svm.core.nodes.ClusterNode;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Describes a {@code jdk.internal.misc.ScopedMemoryAccess$Scoped} function scope. Used to mark the
 * beginning and end of such a function. Enables the compiler to perform certain verification for
 * exactly the scope of the {@code Scope} annotated method even in the presence of inlining.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, allowedUsageTypes = InputType.Association, nameTemplate = "Invoke#{p#type/s}")
public class ScopedMethodNode extends FixedWithNextNode implements ClusterNode {
    public static final NodeClass<ScopedMethodNode> TYPE = NodeClass.create(ScopedMethodNode.class);

    @Node.OptionalInput(InputType.Association) ScopedMethodNode start;

    public enum Type {
        START,
        END
    }

    private final Type type;

    public ScopedMethodNode() {
        super(TYPE, StampFactory.forVoid());
        this.type = Type.START;
    }

    public ScopedMethodNode(ScopedMethodNode start) {
        super(TYPE, StampFactory.forVoid());
        this.type = Type.END;
        this.start = start;
    }

    public ScopedMethodNode getStart() {
        return start;
    }

    public Type getType() {
        return type;
    }

    @Override
    public void delete() {
        if (type == Type.START) {
            replaceAtUsages(null);
        }
        GraphUtil.unlinkFixedNode(this);
        this.safeDelete();
    }

    @Override
    public String toString(Verbosity verbosity) {
        String s = super.toString(verbosity);
        if (verbosity == Verbosity.Long) {
            s += "#" + type;
        }
        return s;
    }
}
