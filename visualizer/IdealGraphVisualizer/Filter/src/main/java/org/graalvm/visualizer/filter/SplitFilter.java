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

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.InputSlot;
import org.graalvm.visualizer.graph.OutputSlot;
import org.graalvm.visualizer.graph.Selector;

import java.util.List;

public class SplitFilter extends AbstractFilter {

    private final String name;
    private final Selector selector;
    private final String propertyName;

    public SplitFilter(String name, Selector selector, String propertyName) {
        this.name = name;
        this.selector = selector;
        this.propertyName = propertyName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram d) {
        List<Figure> list = selector.selected(d);

        for (Figure f : list) {
            checkCancelled();
            for (InputSlot is : f.getInputSlots()) {
                for (Connection c : is.getConnections()) {
                    OutputSlot os = c.getOutputSlot();
                    InputNode n = f.getSource().first();
                    if (n != null) {
                        os.getSource().addSourceNodes(f.getSource());
                        os.setAssociatedNode(n);
                        os.setColor(f.getColor());
                    }

                    String s = Figure.resolveString(propertyName, f.getProperties());
                    if (s != null) {
                        os.setShortName(s);
                    }

                }
            }
            for (OutputSlot os : f.getOutputSlots()) {
                for (Connection c : os.getConnections()) {
                    InputSlot is = c.getInputSlot();
                    InputNode n = f.getSource().first();
                    if (n != null) {
                        is.getSource().addSourceNodes(f.getSource());
                        is.setAssociatedNode(n);
                        is.setColor(f.getColor());
                    }

                    String s = Figure.resolveString(propertyName, f.getProperties());
                    if (s != null) {
                        is.setShortName(s);
                    }
                }
            }

            d.removeFigure(f);
        }
    }
}
