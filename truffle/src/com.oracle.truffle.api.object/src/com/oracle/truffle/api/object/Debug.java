/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.object;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

class Debug {

    private static Collection<Shape> allShapes;

    static void trackShape(Shape newShape) {
        allShapes.add(newShape);
    }

    static void trackObject(DynamicObject obj) {
        assert ObjectStorageOptions.Profile;
        ShapeProfiler.getInstance().track(obj);
    }

    static Iterable<Shape> getAllShapes() {
        return allShapes;
    }

    private static void dumpDOT() throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter out = new PrintWriter(getOutputFile("dot"), "UTF-8")) {
            GraphvizShapeVisitor visitor = new GraphvizShapeVisitor();
            for (Shape shape : getAllShapes()) {
                visitor.visitShape(shape);
            }
            out.println(visitor);
        }
    }

    private static File getOutputFile(String extension) {
        return Paths.get(ObjectStorageOptions.DumpShapesPath, "shapes." + extension).toFile();
    }

    static String getId(Shape shape) {
        return Integer.toHexString(shape.hashCode());
    }

    interface DebugShapeVisitor<R> {
        default R visitShape(Shape shape) {
            Map<Transition, Shape> snapshot = new LinkedHashMap<>();
            shape.forEachTransition(snapshot::put);
            return visitShape(shape, Collections.unmodifiableMap(snapshot));
        }

        R visitShape(Shape shape, Map<? extends Transition, ? extends Shape> transitions);
    }

    static class GraphvizShapeVisitor implements DebugShapeVisitor<GraphvizShapeVisitor> {
        private final Set<Shape> drawn;
        private final StringBuilder sb = new StringBuilder();

        GraphvizShapeVisitor() {
            this.drawn = new HashSet<>();
        }

        @Override
        public GraphvizShapeVisitor visitShape(Shape shape, Map<? extends Transition, ? extends Shape> transitions) {
            if (!drawn.add(shape)) {
                return this;
            }

            if (shape.isLeaf() && shape.getLastProperty() == null) {
                // There are many leaf root shapes - don't draw them
                return this;
            }

            String prefix = "s";
            sb.append(prefix).append(getId(shape));
            sb.append(" [label=\"");
            sb.append(getId(shape));
            sb.append(":");
            if (shape.getLastProperty() != null) {
                for (Property property : shape.getPropertyListInternal(true)) {
                    sb.append("\\n");
                    sb.append(escapeString(property.toString()));
                }
            } else {
                sb.append("\\nROOT");
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

    static {
        if (ObjectStorageOptions.DumpShapes) {
            allShapes = new ConcurrentLinkedQueue<>();
        }

        if (ObjectStorageOptions.DumpShapes) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (ObjectStorageOptions.DumpShapesDOT) {
                        dumpDOT();
                    }
                } catch (FileNotFoundException | UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }
}
