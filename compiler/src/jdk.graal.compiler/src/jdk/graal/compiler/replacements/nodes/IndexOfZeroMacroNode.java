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
package jdk.graal.compiler.replacements.nodes;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

// JaCoCo Exclude

/**
 * Stub-call node for implementations of libc's {@code strlen} function.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class IndexOfZeroMacroNode extends MacroWithExceptionNode implements Canonicalizable {

    public static final NodeClass<IndexOfZeroMacroNode> TYPE = NodeClass.create(IndexOfZeroMacroNode.class);

    private final Stride stride;

    public IndexOfZeroMacroNode(MacroNode.MacroParams p, Stride stride) {
        super(TYPE, p);
        this.stride = stride;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        /*
         * This node has architecture-specific feature checks. However, since all logic besides the
         * feature check is shared, we have deliberately decided avoid code duplication and not to
         * make architecture-specific nodes.
         */
        Architecture arch = tool.getLowerer().getTarget().arch;
        boolean intrinsifiable = (arch instanceof AMD64 && ((AMD64) arch).getFeatures().containsAll(IndexOfZeroNode.minFeaturesAMD64())) || (arch instanceof AArch64);
        if (intrinsifiable) {
            return new IndexOfZeroNode(stride, getArgument(1));
        }
        return this;
    }
}
