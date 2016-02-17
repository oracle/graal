/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents the context of an instrumentation event.
 *
 * Instances of {@link EventContext} should be neither stored, cached nor hashed. The equality and
 * hashing behavior is undefined.
 *
 * @see ExecutionEventNodeFactory
 * @see ExecutionEventListener
 * @since 0.12
 */
public final class EventContext {

    private final ProbeNode probeNode;
    private final SourceSection sourceSection;

    EventContext(ProbeNode probeNode, SourceSection sourceSection) {
        this.sourceSection = sourceSection;
        this.probeNode = probeNode;
    }

    /**
     * Returns the {@link SourceSection} that is being instrumented. The returned source section is
     * final for each {@link EventContext} instance.
     *
     * <p>
     * <b>Performance note:</b> this is method may be invoked in compiled code and is guaranteed to
     * always return a compilation constant .
     * </p>
     *
     * @since 0.12
     */
    public SourceSection getInstrumentedSourceSection() {
        return sourceSection;
    }

    /**
     * Accessor to the instrumented node at which the event occurred. The returned AST must not be
     * mutated by the user.
     * <p>
     * <b>Performance note:</b> this is method may be invoked in compiled code and is guaranteed to
     * always return a compilation constant .
     * </p>
     *
     * @since 0.12
     */
    public Node getInstrumentedNode() {
        WrapperNode wrapper = probeNode.findWrapper();
        return wrapper != null ? wrapper.getDelegateNode() : null;
    }

    /**
     * Evaluates source of (potentially different) language using the current context. The names of
     * arguments are parameters for the resulting {#link CallTarget} that allow the
     * <code>source</code> to reference the actual parameters passed to
     * {@link CallTarget#call(java.lang.Object...)}.
     *
     * @param source the source to evaluate
     * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments that
     *            can be referenced from the source
     * @return the call target representing the parsed result
     * @throws IOException if the parsing or evaluation fails for some reason
     * @since 0.12
     */
    public CallTarget parseInContext(Source source, String... argumentNames) throws IOException {
        return InstrumentationHandler.ACCESSOR.parse(null, source, getInstrumentedNode(), argumentNames);
    }

    /**
     * Returns the first found parent {@link ExecutionEventNode event node} created from a given
     * {@link ExecutionEventNodeFactory factory}. If multiple
     * {@link Instrumenter#attachFactory(SourceSectionFilter, ExecutionEventNodeFactory) bindings}
     * were created with a single {@link ExecutionEventNodeFactory factory} instance then the first
     * ExecutionEventNode which is found is returned in the order of event binding attachment.
     *
     * @param factory a event node factory for which to return the first event node
     * @return the first event node found in the order of event binding attachment
     */
    @TruffleBoundary
    public ExecutionEventNode findParentEventNode(final ExecutionEventNodeFactory factory) {
        Node parent = getInstrumentedNode().getParent();
        while ((parent = parent.getParent()) != null) {
            ExecutionEventNode eventNode = findEventNode(factory, parent);
            if (eventNode != null) {
                return eventNode;
            }
        }
        return null;
    }

    /**
     * Returns all first-level child event nodes created from a given
     * {@link ExecutionEventNodeFactory factory}.
     *
     * @param factory an event node factory for which to return all first-level children
     * @return all first-level children that were created from a given factory
     */
    @TruffleBoundary
    public List<ExecutionEventNode> findChildEventNodes(final ExecutionEventNodeFactory factory) {
        final List<ExecutionEventNode> eventNodes = new ArrayList<>();
        Node instrumentedNode = getInstrumentedNode();
        // TODO ideally one could use a NodeListener instead of the recursive algortihm.
        // Unfortunately returning false in NodeVisitor#visit does not continue traversing all
        // parents children but stops visitation completely. Bug!?
        collectEventNodes(eventNodes, factory, instrumentedNode);
        return Collections.unmodifiableList(eventNodes);
    }

    private void collectEventNodes(List<ExecutionEventNode> eventNodes, ExecutionEventNodeFactory factory, Node node) {
        for (Node child : node.getChildren()) {
            ExecutionEventNode eventNode = findEventNode(factory, child);
            if (eventNode != null) {
                eventNodes.add(eventNode);
            } else if (child != null) {
                collectEventNodes(eventNodes, factory, child);
            }
        }
    }

    private static ExecutionEventNode findEventNode(ExecutionEventNodeFactory factory, Node node) {
        if (node instanceof WrapperNode) {
            return ((WrapperNode) node).getProbeNode().findEventNode(factory);
        }
        return null;
    }

    /*
     * TODO (chumer) a way to parse code in the current language and return something like a node
     * that is directly embeddable into the AST as a @Child.
     */
    /** @since 0.12 */
    @Override
    public String toString() {
        return "EventContext[source=" + getInstrumentedSourceSection() + "]";
    }

}
