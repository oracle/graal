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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.RiType.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

// TODO (chaeubl) this should be a FloatingNode but Lowering is not possible in that case
public final class ReadHubNode extends FixedWithNextNode implements Lowerable, Canonicalizable {
    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public ReadHubNode(ValueNode object) {
        super(StampFactory.objectNonNull());
        this.object = object;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        RiRuntime runtime = tool.runtime();
        if (runtime != null) {
            ObjectStamp stamp = object.objectStamp();

            RiResolvedType exactType;
            if (stamp.isExactType()) {
                exactType = stamp.type();
            } else if (stamp.type() != null && tool.assumptions() != null) {
                exactType = stamp.type().uniqueConcreteSubtype();
                if (exactType != null) {
                    tool.assumptions().recordConcreteSubtype(stamp.type(), exactType);
                }
            } else {
                exactType = null;
            }

            if (exactType != null) {
                return ConstantNode.forCiConstant(exactType.getEncoding(Representation.ObjectHub), runtime, graph());
            }
        }
        return this;
    }
}
