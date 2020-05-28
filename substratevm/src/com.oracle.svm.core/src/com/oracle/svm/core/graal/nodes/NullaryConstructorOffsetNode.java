/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;

/**
 * The vtable offset is only available after static analysis. Parsing graphs that are put into the
 * image happens during static analysis, so we need a separate node that is replaced with a constant
 * once the vtable information is available.
 */
@NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
public final class NullaryConstructorOffsetNode extends FloatingNode implements Canonicalizable {
    public static final NodeClass<NullaryConstructorOffsetNode> TYPE = NodeClass.create(NullaryConstructorOffsetNode.class);

    public static final int NULLARY_CONSTRUCTOR_OFFSET = 0;

    private final RuntimeConfiguration runtimeConfig;

    public NullaryConstructorOffsetNode(RuntimeConfiguration runtimeConfig) {
        super(TYPE, FrameAccess.getWordStamp());
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (runtimeConfig.isFullyInitialized()) {
            final int vtableEntryOffset = runtimeConfig.getVTableOffset(NULLARY_CONSTRUCTOR_OFFSET);
            return ConstantNode.forIntegerKind(FrameAccess.getWordKind(), vtableEntryOffset);
        }
        return this;
    }
}
