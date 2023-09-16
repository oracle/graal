/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.nodes;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.replacements.nodes.BasicObjectCloneNode;

@NodeInfo
public class AnalysisObjectCloneNode extends BasicObjectCloneNode {

    public static final NodeClass<AnalysisObjectCloneNode> TYPE = NodeClass.create(AnalysisObjectCloneNode.class);

    public AnalysisObjectCloneNode(MacroParams p) {
        this(p, null);
    }

    private AnalysisObjectCloneNode(MacroParams p, FrameState stateAfter) {
        super(TYPE, p, stateAfter);
    }

    @Override
    protected AnalysisObjectCloneNode duplicateWithNewStamp(ObjectStamp newStamp) {
        return new AnalysisObjectCloneNode(copyParamsWithImprovedStamp(newStamp), stateAfter());
    }
}
