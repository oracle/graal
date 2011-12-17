/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.graph;

import java.util.*;

/**
 * This error represents a conditions that should never occur during normal operation.
 */
public class GraalInternalError extends Error {

    private Node node;
    private Graph graph;
    private final ArrayList<String> context = new ArrayList<String>();

    /**
     * This constructor creates a {@link GraalInternalError} with a message assembled via {@link String#format(String, Object...)}.
     * It always uses the ENGLISH locale in order to always generate the same output.
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be expanded into a [x, x, ...] representation.
     */
    public GraalInternalError(String msg, Object... args) {
        super(format(msg, args));
    }

    /**
     * This constructor creates a {@link GraalInternalError} for a given causing Throwable instance.
     * @param cause the original exception that contains additional information on this error
     */
    public GraalInternalError(Throwable cause) {
        super(cause);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(super.toString());
        for (String s : context) {
            str.append("\n\t\tat ").append(s);
        }
        return str.toString();
    }

    private static String format(String msg, Object... args) {
        if (args != null) {
            // expand Iterable parameters into a list representation
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Iterable<?>) {
                    ArrayList<Object> list = new ArrayList<Object>();
                    for (Object o : (Iterable<?>) args[i]) {
                        list.add(o);
                    }
                    args[i] = list.toString();
                }
            }
        }
        return String.format(Locale.ENGLISH, msg, args);
    }

    public GraalInternalError addContext(String context) {
        this.context.add(context);
        return this;
    }

    public GraalInternalError addContext(String name, Object obj) {
        return addContext(format("%s: %s", name, obj));
    }

    /**
     * Adds a graph to the context of this VerificationError. The first graph added via this method will be returned by {@link #graph()}.
     * @param graph the graph which is in a incorrect state, if the verification error was not caused by a specific node
     */
    public GraalInternalError addContext(Graph graph) {
        if (graph != this.graph) {
            addContext("graph", graph);
            if (this.graph == null) {
                this.graph = graph;
            }
        }
        return this;
    }

    /**
     * Adds a node to the context of this VerificationError. The first node added via this method will be returned by {@link #node()}.
     * @param node the node which is in a incorrect state, if the verification error was caused by a node
     */
    public GraalInternalError addContext(Node node) {
        if (node != this.node) {
            addContext("node", node);
            if (this.node == null) {
                this.node = node;
            }
        }
        return this;
    }

    public Node node() {
        return node;
    }

    public Graph graph() {
        return graph;
    }
}
