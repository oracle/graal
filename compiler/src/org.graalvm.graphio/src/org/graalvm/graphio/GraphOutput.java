/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.graphio;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * Instance of output to dump informations about a compiler compilations.
 *
 * @param <G> the type of graph this instance handles
 * @param <M> the type of methods this instance handles
 */
public final class GraphOutput<G, M> implements Closeable {
    private final GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?> printer;

    private GraphOutput(GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?> p) {
        this.printer = p;
    }

    /**
     * Creates new builder to configure a future instance of {@link GraphOutput}.
     * 
     * @return the builder to configure
     */
    public static GraphOutput<?, ?>.Builder newBuilder() {
        final GraphOutput<Object, Object> temporary = new GraphOutput<>(null);
        return temporary.new Builder();
    }

    /**
     * Begins a compilation group.
     *
     * @param forGraph
     * @param name
     * @param shortName
     * @param method
     * @param bci
     * @param properties
     * @throws IOException
     */
    public void beginGroup(G forGraph, String name, String shortName, M method, int bci, Map<? extends Object, ? extends Object> properties) throws IOException {
        printer.beginGroup(forGraph, name, shortName, method, bci, properties);
    }

    /**
     * Prints a single graph.
     *
     * @param graph
     * @param properties
     * @param id
     * @param format
     * @param args
     * @throws IOException
     */
    public void print(G graph, Map<? extends Object, ? extends Object> properties, int id, String format, Object... args) throws IOException {
        printer.print(graph, properties, id, format, args);
    }

    /**
     * Ends compilation group.
     *
     * @throws IOException
     */
    public void endGroup() throws IOException {
        printer.endGroup();
    }

    /**
     * Closes the output. Closes allocated resources and associated output channel.
     */
    @Override
    public void close() {
        printer.close();
    }

    /**
     * Builder to create an instance of {@link GraphOutput}.
     */
    public final class Builder {
        Builder() {
        }
    }
}
