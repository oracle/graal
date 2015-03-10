/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodeinfo.*;

/**
 * Logic node that negates its argument.
 */
@NodeInfo
public final class LogicNegationNode extends LogicNode implements Canonicalizable.Unary<LogicNode> {

    public static final NodeClass<LogicNegationNode> TYPE = NodeClass.create(LogicNegationNode.class);
    @Input(InputType.Condition) LogicNode value;

    public LogicNegationNode(LogicNode value) {
        super(TYPE);
        this.value = value;
    }

    public static LogicNode create(LogicNode value) {
        LogicNode synonym = findSynonym(value);
        if (synonym != null) {
            return synonym;
        }
        return new LogicNegationNode(value);
    }

    private static LogicNode findSynonym(LogicNode value) {
        if (value instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) value;
            return LogicConstantNode.forBoolean(!logicConstantNode.getValue());
        } else if (value instanceof LogicNegationNode) {
            return ((LogicNegationNode) value).getValue();
        }
        return null;
    }

    public LogicNode getValue() {
        return value;
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool, LogicNode forValue) {
        LogicNode synonym = findSynonym(forValue);
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

}
