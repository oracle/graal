/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Source information of a particular source location in the profiler.
 */
final class SourceLocation {

    private final SourceSection sourceSection;
    private final String rootName;
    private final Set<Class<?>> tags;
    private final Node instrumentedNode;

    SourceLocation(Instrumenter instrumenter, EventContext context) {
        this.tags = instrumenter.queryTags(context.getInstrumentedNode());
        this.sourceSection = context.getInstrumentedSourceSection();
        this.instrumentedNode = context.getInstrumentedNode();
        this.rootName = extractRootName(instrumentedNode);
    }

    SourceLocation(Instrumenter instrumenter, Node node) {
        this.tags = instrumenter.queryTags(node);
        this.sourceSection = node.getSourceSection();
        this.instrumentedNode = node;
        rootName = extractRootName(instrumentedNode);
    }

    private static String extractRootName(Node instrumentedNode) {
        RootNode rootNode = instrumentedNode.getRootNode();
        if (rootNode != null) {
            if (rootNode.getName() == null) {
                return rootNode.toString();
            } else {
                return rootNode.getName();
            }
        } else {
            return "<Unknown>";
        }
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public String getRootName() {
        return rootName;
    }

    public Set<Class<?>> getTags() {
        return tags;
    }

    public Node getInstrumentedNode() {
        return instrumentedNode;
    }

    @Override
    public int hashCode() {
        return sourceSection.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SourceLocation)) {
            return false;
        }
        SourceLocation other = (SourceLocation) obj;
        return Objects.equals(sourceSection, other.sourceSection) && Objects.equals(rootName, other.rootName);
    }

}
