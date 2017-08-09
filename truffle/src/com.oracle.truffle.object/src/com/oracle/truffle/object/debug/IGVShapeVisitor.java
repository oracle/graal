/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.Transition;

@SuppressWarnings("all")
@Deprecated
public class IGVShapeVisitor extends com.oracle.truffle.object.DebugShapeVisitor<IGVShapeVisitor> {
    private final com.oracle.truffle.api.nodes.GraphPrintVisitor graphPrinter;

    public IGVShapeVisitor(com.oracle.truffle.api.nodes.GraphPrintVisitor printer) {
        this.graphPrinter = printer;
    }

    @Override
    public IGVShapeVisitor visitShape(final Shape shape, final Map<? extends Transition, ? extends Shape> transitions) {
        graphPrinter.visit(shape, new com.oracle.truffle.api.nodes.GraphPrintVisitor.GraphPrintHandler() {
            public void visit(Object node, com.oracle.truffle.api.nodes.GraphPrintVisitor.GraphPrintAdapter printer) {
                if (!printer.visited(node)) {
                    Shape s = (Shape) node;
                    printer.createElementForNode(s);
                    String name = s.getLastProperty() == null ? "ROOT" : s.getLastProperty().toString();
                    printer.setNodeProperty(s, "name", name);
                    printer.setNodeProperty(s, "valid", s.isValid());
                    printer.setNodeProperty(s, "identityHashCode", Integer.toHexString(System.identityHashCode(s)));
                    printer.setNodeProperty(s, "objectType", s.getObjectType());

                    for (Entry<? extends Transition, ? extends Shape> entry : transitions.entrySet()) {
                        Shape dst = entry.getValue();
                        IGVShapeVisitor.this.visitShape((dst));
                        assert printer.visited(dst);
                        printer.connectNodes(s, dst, entry.getKey().toString());
                    }
                }
            }
        });

        return this;
    }
}
