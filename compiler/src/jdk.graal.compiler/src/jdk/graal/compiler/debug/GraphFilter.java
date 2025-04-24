/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A filter that can be applied to {@linkplain jdk.graal.compiler.nodes.StructuredGraph graphs}.
 */
public final class GraphFilter {

    private final String value;
    private final MethodFilter methodFilter;

    /// Creates a graph filter.
    /// A graph is matched by this filter if any of the following is true:
    /// * `value` is null
    /// * [StructuredGraph#name] is non-null and contains `value` as a substring
    /// * [StructuredGraph#method()] is non-null and is [matched][MethodFilter#matches] by the
    /// [MethodFilter] parsed from `value``
    public GraphFilter(String value) {
        this.value = value;
        this.methodFilter = value == null ? MethodFilter.matchAll() : MethodFilter.parse(value);
    }

    @Override
    public String toString() {
        return value + ":" + methodFilter;
    }

    /// Determines if this filter matches `graph` based on the rules described in [GraphFilter].
    public boolean matches(StructuredGraph graph) {
        if (graph.name != null) {
            if (value == null || graph.name.contains(value)) {
                return true;
            }
        }
        ResolvedJavaMethod method = graph.method();
        if (method != null) {
            return methodFilter.matches(method);
        }
        return false;
    }

    /// If this filter matches `graph` based on the rules described in [GraphFilter],
    /// returns a label for `graph` based on its non-null name or non-null method.
    /// Otherwise, returns `null`.
    public String matchedLabel(StructuredGraph graph) {
        if (matches(graph)) {
            return graph.name != null ? graph.name : graph.method().format("%H.%n(%p)");
        }
        return null;
    }
}
