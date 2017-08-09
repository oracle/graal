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
package com.oracle.truffle.object;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;

@SuppressWarnings("deprecation")
class Debug {
    private static Collection<ShapeImpl> allShapes;

    static void trackShape(ShapeImpl newShape) {
        allShapes.add(newShape);
    }

    static void trackObject(DynamicObject obj) {
        com.oracle.truffle.object.debug.ShapeProfiler.getInstance().track(obj);
    }

    static Iterable<ShapeImpl> getAllShapes() {
        return allShapes;
    }

    static String dumpObject(DynamicObject object, int level, int levelStop) {
        List<Property> properties = object.getShape().getPropertyListInternal(true);
        StringBuilder sb = new StringBuilder(properties.size() * 10);
        sb.append("{\n");
        for (Property property : properties) {
            indent(sb, level + 1);

            sb.append(property.getKey());
            sb.append('[').append(property.getLocation()).append(']');
            Object value = property.get(object, false);
            if (value instanceof DynamicObject) {
                if (level < levelStop) {
                    value = dumpObject((DynamicObject) value, level + 1, levelStop);
                } else {
                    value = value.toString();
                }
            }
            sb.append(": ");
            sb.append(value);
            if (property != properties.get(properties.size() - 1)) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, level);
        sb.append("}");
        return sb.toString();
    }

    private static StringBuilder indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append(' ');
        }
        return sb;
    }

    static {
        if (ObjectStorageOptions.DumpShapes) {
            allShapes = new ConcurrentLinkedQueue<>();
        }

        if (ObjectStorageOptions.DumpShapes) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    try {
                        if (ObjectStorageOptions.DumpShapesDOT) {
                            dumpDOT();
                        }
                        if (ObjectStorageOptions.DumpShapesJSON) {
                            dumpJSON();
                        }
                        if (ObjectStorageOptions.DumpShapesIGV) {
                            dumpIGV();
                        }
                    } catch (FileNotFoundException | UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }

                private void dumpDOT() throws FileNotFoundException, UnsupportedEncodingException {
                    try (PrintWriter out = new PrintWriter(getOutputFile("dot"), "UTF-8")) {
                        com.oracle.truffle.object.debug.GraphvizShapeVisitor visitor = new com.oracle.truffle.object.debug.GraphvizShapeVisitor();
                        for (ShapeImpl shape : getAllShapes()) {
                            visitor.visitShape(shape);
                        }
                        out.println(visitor);
                    }
                }

                private void dumpJSON() throws FileNotFoundException, UnsupportedEncodingException {
                    try (PrintWriter out = new PrintWriter(getOutputFile("json"), "UTF-8")) {
                        out.println("{\"shapes\": [");
                        boolean first = true;
                        for (ShapeImpl shape : getAllShapes()) {
                            if (!first) {
                                out.println(",");
                            }
                            first = false;
                            out.print(new com.oracle.truffle.object.debug.JSONShapeVisitor().visitShape(shape));
                        }
                        if (!first) {
                            out.println();
                        }
                        out.println("]}");
                    }
                }

                private void dumpIGV() {
                    com.oracle.truffle.api.nodes.GraphPrintVisitor printer = new com.oracle.truffle.api.nodes.GraphPrintVisitor();
                    printer.beginGroup("shapes");
                    com.oracle.truffle.object.debug.IGVShapeVisitor visitor = new com.oracle.truffle.object.debug.IGVShapeVisitor(printer);
                    for (ShapeImpl shape : getAllShapes()) {
                        if (isRootShape(shape)) {
                            printer.beginGraph(DebugShapeVisitor.getId(shape));
                            visitor.visitShape(shape);
                            printer.endGraph();
                        }
                    }
                    printer.beginGraph("all shapes");
                    for (ShapeImpl shape : getAllShapes()) {
                        if (isRootShape(shape)) {
                            visitor.visitShape(shape);
                        }
                    }
                    printer.endGraph();
                    printer.endGroup();
                    printer.printToNetwork(false);
                }

                private boolean isRootShape(ShapeImpl shape) {
                    return shape.getParent() == null;
                }

                private File getOutputFile(String extension) {
                    return Paths.get(ObjectStorageOptions.DumpShapesPath, "shapes." + extension).toFile();
                }
            }));
        }
    }
}
