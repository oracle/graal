/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.*;

/**
 * This error represents a conditions that should never occur during normal operation.
 */
public class GraalGraphInternalError extends GraalInternalError {

    private static final long serialVersionUID = 8776065085829593278L;
    private Node node;
    private Graph graph;

    /**
     * This constructor creates a {@link GraalGraphInternalError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    public GraalGraphInternalError(String msg, Object... args) {
        super(msg, args);
    }

    /**
     * This constructor creates a {@link GraalGraphInternalError} for a given causing Throwable
     * instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public GraalGraphInternalError(Throwable cause) {
        super(cause);
    }

    /**
     * This constructor creates a {@link GraalGraphInternalError} from a given GraalInternalError
     * instance.
     *
     * @param e the original GraalInternalError
     */
    protected GraalGraphInternalError(GraalInternalError e) {
        super(e);
        if (e instanceof GraalGraphInternalError) {
            node = ((GraalGraphInternalError) e).node;
            graph = ((GraalGraphInternalError) e).graph;
        }
    }

    /**
     * Adds a graph to the context of this VerificationError. The first graph added via this method
     * will be returned by {@link #graph()}.
     *
     * @param newGraph the graph which is in a incorrect state, if the verification error was not
     *            caused by a specific node
     */
    GraalGraphInternalError addContext(Graph newGraph) {
        if (newGraph != this.graph) {
            addContext("graph", newGraph);
            if (this.graph == null) {
                this.graph = newGraph;
            }
        }
        return this;
    }

    /**
     * Adds a node to the context of this VerificationError. The first node added via this method
     * will be returned by {@link #node()}.
     *
     * @param newNode the node which is in a incorrect state, if the verification error was caused
     *            by a node
     */
    public GraalGraphInternalError addContext(Node newNode) {
        if (newNode != this.node) {
            addContext("node", newNode);
            if (this.node == null) {
                this.node = newNode;
            }
        }
        return this;
    }

    /**
     * Adds a graph to the context of the given GraalInternalError.
     *
     * @param e the previous error
     * @param newGraph the graph which is in a incorrect state, if the verification error was not
     *            caused by a specific node
     */
    public static GraalGraphInternalError addContext(GraalInternalError e, Graph newGraph) {
        GraalGraphInternalError graphError;
        if (e instanceof GraalGraphInternalError) {
            graphError = (GraalGraphInternalError) e;
        } else {
            graphError = new GraalGraphInternalError(e);
        }
        return graphError.addContext(newGraph);
    }

    /**
     * Adds a node to the context of the given GraalInternalError.
     *
     * @param e the previous error
     * @param newNode the node which is in a incorrect state, if the verification error was caused
     *            by a node
     */
    public static GraalGraphInternalError addContext(GraalInternalError e, Node newNode) {
        GraalGraphInternalError graphError;
        if (e instanceof GraalGraphInternalError) {
            graphError = (GraalGraphInternalError) e;
        } else {
            graphError = new GraalGraphInternalError(e);
        }
        return graphError.addContext(newNode);
    }

    public Node node() {
        return node;
    }

    public Graph graph() {
        return graph;
    }
}
