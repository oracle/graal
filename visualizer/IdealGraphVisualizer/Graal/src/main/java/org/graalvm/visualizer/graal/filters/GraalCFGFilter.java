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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.*;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.CLASS_ENDNODE;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.visualizer.filter.AbstractFilter;
import org.graalvm.visualizer.graph.*;

import jdk.graal.compiler.graphio.parsing.model.Properties;

public class GraalCFGFilter extends AbstractFilter {

    @Override
    public String getName() {
        return "Graal CFG Filter"; // NOI18N
    }

    @Override
    public void apply(Diagram d) {
        Set<Connection> connectionsToRemove = new HashSet<>();

        for (Figure f : d.getFigures()) {
            Properties p = f.getProperties();
            int predCount = -1;
            String predCountString = p.getString(PROPNAME_PREDECESSOR_COUNT, null);
            if (predCountString != null) {
                try {
                    predCount = Integer.parseInt(predCountString);
                } catch (NumberFormatException ex) {
                    // expected
                }
            }
            if (predCount == -1) {
                if (Boolean.parseBoolean(p.getString(PROPNAME_HAS_PREDECESSOR, null))) {
                    predCount = 1;
                } else {
                    predCount = 0;
                }
            }
            for (InputSlot is : f.getInputSlots()) {
                if (is.getPosition() >= predCount && !CLASS_ENDNODE.equals(is.getProperties().get(PROPNAME_CLASS, String.class))) {
                    for (Connection c : is.getConnections()) {
                        if (!CLASS_ENDNODE.equals(c.getOutputSlot().getFigure().getProperties().get(PROPNAME_CLASS, String.class))) {
                            connectionsToRemove.add(c);
                        }
                    }
                }
            }
        }

        for (Connection c : connectionsToRemove) {
            c.remove();
        }

        Set<Figure> figuresToRemove = new HashSet<>();
        next:
        for (Figure f : d.getFigures()) {
            for (InputSlot is : f.getInputSlots()) {
                if (!is.getConnections().isEmpty()) {
                    continue next;
                }
            }
            for (OutputSlot os : f.getOutputSlots()) {
                if (!os.getConnections().isEmpty()) {
                    continue next;
                }
            }
            figuresToRemove.add(f);
        }
        d.removeAllFigures(figuresToRemove);
    }
}
