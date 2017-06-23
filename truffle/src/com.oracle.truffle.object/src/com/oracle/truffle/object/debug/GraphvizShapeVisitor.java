/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.debug;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.Transition;

@SuppressWarnings("deprecation")
@Deprecated
public class GraphvizShapeVisitor extends com.oracle.truffle.object.DebugShapeVisitor<GraphvizShapeVisitor> {
    private final Set<Shape> drawn;
    private final StringBuilder sb = new StringBuilder();

    public GraphvizShapeVisitor() {
        this.drawn = new HashSet<>();
    }

    @Override
    public GraphvizShapeVisitor visitShape(Shape shape, Map<? extends Transition, ? extends Shape> transitions) {
        if (!drawn.add(shape)) {
            return this;
        }

        String prefix = "s";
        sb.append(prefix).append(getId(shape));
        sb.append(" [label=\"");
        if (shape.getLastProperty() != null) {
            sb.append(escapeString(shape.getLastProperty().toString()));
        } else {
            sb.append("ROOT");
        }
        sb.append("\"");
        sb.append(", shape=\"rectangle\"");
        if (!shape.isValid()) {
            sb.append(", color=\"red\", style=dotted");
        }
        sb.append("];");

        for (Entry<? extends Transition, ? extends Shape> entry : transitions.entrySet()) {
            Shape dst = entry.getValue();
            this.visitShape(dst);
            assert drawn.contains(dst);

            sb.append(prefix).append(getId(shape)).append("->").append(prefix).append(getId(dst));
            sb.append(" [label=\"").append(escapeString(entry.getKey().toString())).append("\"]");
            sb.append(";");
        }

        return this;
    }

    private static String escapeString(String str) {
        return str.replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\\\"");
    }

    @Override
    public String toString() {
        return new StringBuilder("digraph{").append(sb).append("}").toString();
    }
}
