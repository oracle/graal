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

package org.graalvm.visualizer.data.services.impl;

import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.services.GraphClassifier;
import org.openide.util.lookup.ServiceProvider;
import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author sdedic, odouda
 */
@ServiceProvider(service = GraphClassifier.class)
public class DefaultGraphClassifier implements GraphClassifier {
    @Override
    public String classifyGraphType(Properties properties) {
        String g = properties.get("graph", String.class); // NOI18N
        if (g != null) {
            if (g.startsWith(STRUCTURED_GRAPH)) {
                return STRUCTURED_GRAPH;
            } else if (g.contains("inline")) { // NOI18N
                return CALL_GRAPH;
            }
        }
        return null;
    }

    @Override
    public Collection<String> knownGraphTypes() {
        return Arrays.asList(STRUCTURED_GRAPH, CALL_GRAPH, InputGraph.DEFAULT_TYPE);
    }
}
