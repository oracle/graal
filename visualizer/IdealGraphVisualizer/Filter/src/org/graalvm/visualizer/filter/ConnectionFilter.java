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
package org.graalvm.visualizer.filter;

import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Selector;
import org.graalvm.visualizer.graph.OutputSlot;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.data.Properties;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConnectionFilter extends AbstractFilter {

    private final List<ConnectionStyleRule> connectionStyleRules;
    private final String name;

    public ConnectionFilter(String name) {
        this.name = name;
        connectionStyleRules = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {
        for (ConnectionStyleRule rule : connectionStyleRules) {
            Collection<Figure> figures;
            if (rule.getSelector() != null) {
                figures = rule.getSelector().selected(diagram);
            } else {
                figures = diagram.getFigures();
            }

            for (Figure f : figures) {
                for (OutputSlot os : f.getOutputSlots()) {
                    for (Connection c : os.getConnections()) {
                        if (figures.contains(c.getInputSlot().getFigure())) {
                            c.setStyle(rule.getLineStyle());
                            c.setColor(rule.getLineColor());
                        }
                    }
                }
            }
        }
    }

    public void addRule(ConnectionStyleRule r) {
        connectionStyleRules.add(r);
    }

    public static class ConnectionStyleRule {

        private final Color lineColor;
        private final Connection.ConnectionStyle lineStyle;
        private final Selector selector;

        public ConnectionStyleRule(Selector selector, Color lineColor, Connection.ConnectionStyle lineStyle) {
            this.selector = selector;
            this.lineColor = lineColor;
            this.lineStyle = lineStyle;
        }

        public Selector getSelector() {
            return selector;
        }

        public Color getLineColor() {
            return lineColor;
        }

        public Connection.ConnectionStyle getLineStyle() {
            return lineStyle;
        }
    }
}
