/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.graal.filters;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_CLASS;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.CLASS_ENDNODE;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;

import org.graalvm.visualizer.filter.AbstractFilter;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Connection.ConnectionStyle;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.InputSlot;

/**
 * Filter that colors usage and successor edges differently.
 */
public class GraalEdgeColorFilter extends AbstractFilter {

    private final HashMap<String, Color> usageColor = new HashMap<>();
    private Color otherUsageColor = Color.BLACK;

    public GraalEdgeColorFilter() {
    }

    @Override
    public String getName() {
        return "Graal Edge Color Filter"; // NOI18N
    }

    @Override
    public void apply(Diagram d) {
        Collection<Figure> figures = d.getFigures();
        for (Figure f : figures) {
            for (InputSlot is : f.getInputSlots()) {
                for (Connection c : is.getConnections()) {
                    String type = c.getType();
                    if ("Association".equals(type) && CLASS_ENDNODE.equals(c.getOutputSlot().getFigure().getProperties().get(PROPNAME_CLASS, String.class))) {
                        type = "Successor";
                    }

                    if (type != null) {
                        Color typeColor = usageColor.get(type);
                        if (typeColor == null) {
                            c.setColor(otherUsageColor);
                        } else {
                            c.setColor(typeColor);
                        }
                        if (c.getStyle() != ConnectionStyle.DASHED && "Successor".equals(type)) {
                            c.setStyle(ConnectionStyle.BOLD);
                        }
                    }
                }
            }
        }
    }

    public Color getUsageColor(String type) {
        return usageColor.get(type);
    }

    public void setUsageColor(String type, Color usageColor) {
        this.usageColor.put(type, usageColor);
    }

    public Color getOtherUsageColor() {
        return otherUsageColor;
    }

    public void setOtherUsageColor(Color otherUsageColor) {
        this.otherUsageColor = otherUsageColor;
    }
}
