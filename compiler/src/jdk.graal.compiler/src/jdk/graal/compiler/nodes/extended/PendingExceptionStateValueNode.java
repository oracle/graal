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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Value proxy inserted while parsing exception dispatch for threaded bytecode-handler calls whose
 * result is stored back to a local. It gives the exception path a distinct {@code copyFromReturn}
 * value early enough for normal exception-handler merge creation to build the right phis. The SVM
 * outline phase later replaces the proxy with either a {@code PendingExceptionStateHolder} read,
 * if the throwing predecessor is a generated stub, or the original input value for ordinary Java
 * invokes.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0, allowedUsageTypes = {InputType.Anchor})
public final class PendingExceptionStateValueNode extends FixedValueAnchorNode {
    public static final NodeClass<PendingExceptionStateValueNode> TYPE = NodeClass.create(PendingExceptionStateValueNode.class);

    public enum Source {
        /** The parser saw a direct handler stub call, so the callee publishes pending state. */
        STUB,
        /**
         * The parser saw a switch-extension call; inlining and outline-phase processing must inspect
         * the eventual predecessor.
         */
        INFER
    }

    private final int slotIndex;
    private final JavaKind kind;
    private final Source source;

    public PendingExceptionStateValueNode(ValueNode value, int slotIndex, JavaKind kind, Source source) {
        super(TYPE, value);
        this.slotIndex = slotIndex;
        this.kind = kind;
        this.source = source;
        setStamp(value.stamp(NodeView.DEFAULT).unrestricted());
    }

    public int slotIndex() {
        return slotIndex;
    }

    public JavaKind kind() {
        return kind;
    }

    public Source source() {
        return source;
    }

    public ValueNode value() {
        return object();
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(value().stamp(NodeView.DEFAULT).unrestricted());
    }
}
