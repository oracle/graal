/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.graphio.parsing.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Classifies graphs into types. Some metadata is precomputed when a graph is loaded, such as node
 * changes between graphs. Such data is only meaningful to compute for graphs of the same type -
 * different stages of the same graph in time.
 *
 * @author sdedic
 * @author crefice
 */
public class GraphClassifier {
    public static final String STRUCTURED_GRAPH = "StructuredGraph";
    public static final String CALL_GRAPH = "CallGraph";
    public static final String DEFAULT_TYPE = "defaultType";

    /**
     * Static, shared instance of {@link GraphClassifier}.
     */
    public static final GraphClassifier DEFAULT_CLASSIFIER = new GraphClassifier();

    /**
     * Computes a graph's type from its properties. The type is represented as a String to allow
     * subclasses to use custom graph types.
     *
     * @param properties graph properties
     * @return graph type or {@link #DEFAULT_TYPE} if undetermined.
     */
    public String classifyGraphType(Properties properties) {
        String g = properties.get("graph", String.class);
        if (g != null) {
            if (g.startsWith(STRUCTURED_GRAPH)) {
                return STRUCTURED_GRAPH;
            } else if (g.contains("inline")) {
                return CALL_GRAPH;
            }
        }
        return DEFAULT_TYPE;
    }

    private static final Set<String> KNOWN_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(STRUCTURED_GRAPH, CALL_GRAPH, DEFAULT_TYPE)));

    /**
     * Returns a set of all the graph types that {@link #classifyGraphType} can return.
     */
    public Set<String> knownGraphTypes() {
        return KNOWN_TYPES;
    }
}
