/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import java.io.*;

public abstract class GraphEvent {

    private Exception exceptionContext;

    public static class NodeEvent extends GraphEvent {

        public static enum Type {
            ADDED, DELETED, CHANGED
        }

        public final Node node;
        public final Type type;
        private final String nodeString;

        public NodeEvent(Node n, Type type) {
            this.node = n;
            this.type = type;
            nodeString = n.toString();
        }

        @Override
        public StackTraceElement[] print(StackTraceElement[] last, PrintStream stream) {
            stream.println(type.toString() + ", " + nodeString);
            return super.print(last, stream);
        }
    }

    public static class EdgeEvent extends GraphEvent {

        public static enum Type {
            INPUT, SUCC
        }

        public final Node node;
        public final int index;
        public final Node newValue;
        public final Type type;

        public EdgeEvent(Node node, int index, Node newValue, Type type) {
            this.node = node;
            this.index = index;
            this.newValue = newValue;
            this.type = type;
        }
    }

    public GraphEvent() {
        exceptionContext = new Exception();
    }

    public StackTraceElement[] print(StackTraceElement[] last, PrintStream stream) {
        StackTraceElement[] stackTrace = exceptionContext.getStackTrace();

        boolean atTop = true;
        for (int i = 0; i < stackTrace.length; ++i) {
            StackTraceElement elem = stackTrace[i];
            int toBottom = stackTrace.length - i;
            if (atTop) {
                if (!elem.getClassName().startsWith("com.oracle.graal.graph.Graph") && !elem.getClassName().startsWith("com.oracle.graal.graph.Node")) {
                    atTop = false;
                } else {
                    continue;
                }
            } else {
                if (last.length >= toBottom && last[last.length - toBottom].equals(elem)) {
                    continue;
                }
            }
            stream.println(String.format("%s.%s(%s:%d)", elem.getClassName(), elem.getMethodName(), elem.getFileName(), elem.getLineNumber()));
        }
        return stackTrace;
    }
}
