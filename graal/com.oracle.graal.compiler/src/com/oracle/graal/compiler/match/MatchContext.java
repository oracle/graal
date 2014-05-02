/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.match;

import static com.oracle.graal.compiler.GraalDebugConfig.*;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.MatchPattern.Result;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Container for state captured during a match.
 */
public class MatchContext {

    private final ValueNode root;

    private final List<ScheduledNode> nodes;

    private final MatchStatement rule;

    private Map<String, NamedNode> namedNodes;

    private ArrayList<ValueNode> consumed;

    private int startIndex;

    private int endIndex;

    private final NodeLIRBuilder builder;

    private static class NamedNode {
        final Class<? extends ValueNode> type;
        final ValueNode value;

        NamedNode(Class<? extends ValueNode> type, ValueNode value) {
            this.type = type;
            this.value = value;
        }
    }

    public MatchContext(NodeLIRBuilder builder, MatchStatement rule, int index, ValueNode node, List<ScheduledNode> nodes) {
        this.builder = builder;
        this.rule = rule;
        this.root = node;
        this.nodes = nodes;
        assert index == nodes.indexOf(node);
        // The root should be the last index since all the inputs must be scheduled before it.
        startIndex = endIndex = index;
    }

    public ValueNode getRoot() {
        return root;
    }

    public Result captureNamedValue(String name, Class<? extends ValueNode> type, ValueNode value) {
        if (namedNodes == null) {
            namedNodes = new HashMap<>(2);
        }
        NamedNode current = namedNodes.get(name);
        if (current == null) {
            current = new NamedNode(type, value);
            namedNodes.put(name, current);
            return Result.OK;
        } else {
            if (current.value != value || current.type != type) {
                return Result.NAMED_VALUE_MISMATCH(value, rule.getPattern());
            }
            return Result.OK;
        }
    }

    public Result validate() {
        // Ensure that there's no unsafe work in between these operations.
        for (int i = startIndex; i <= endIndex; i++) {
            ScheduledNode node = nodes.get(i);
            if (node instanceof ConstantNode || node instanceof LocationNode || node instanceof VirtualObjectNode || node instanceof ParameterNode) {
                // these can be evaluated lazily so don't worry about them. This should probably be
                // captured by some interface that indicates that their generate method is empty.
                continue;
            } else if (consumed == null || !consumed.contains(node) && node != root) {
                if (LogVerbose.getValue()) {
                    Debug.log("unexpected node %s", node);
                    for (int j = startIndex; j <= endIndex; j++) {
                        ScheduledNode theNode = nodes.get(j);
                        Debug.log("%s(%s) %1s", (consumed.contains(theNode) || theNode == root) ? "*" : " ", theNode.usages().count(), theNode);
                    }
                }
                return Result.NOT_SAFE(node, rule.getPattern());
            }
        }
        return Result.OK;
    }

    /**
     * Mark the interior nodes with INTERIOR_MATCH and set the Value of the root to be the result.
     * During final LIR generation it will be evaluated to produce the actual LIR value.
     *
     * @param result
     */
    public void setResult(ComplexMatchResult result) {
        ComplexMatchValue value = new ComplexMatchValue(result);
        Debug.log("matched %s %s", rule.getName(), rule.getPattern());
        Debug.log("with nodes %s", rule.formatMatch(root));
        if (consumed != null) {
            for (ValueNode node : consumed) {
                // All the interior nodes should be skipped during the normal doRoot calls in
                // NodeLIRBuilder so mark them as interior matches. The root of the match will get a
                // closure which will be evaluated to produce the final LIR.
                builder.setMatchResult(node, ComplexMatchValue.INTERIOR_MATCH);
            }
        }
        builder.setMatchResult(root, value);
    }

    /**
     * Mark a node as consumed by the match. Consumed nodes will never be evaluated.
     *
     * @return Result.OK if the node can be safely consumed.
     */
    public Result consume(ValueNode node) {
        assert node.usages().count() <= 1 : "should have already been checked";

        if (builder.hasOperand(node)) {
            return Result.ALREADY_USED(node, rule.getPattern());
        }

        int index = nodes.indexOf(node);
        if (index == -1) {
            return Result.NOT_IN_BLOCK(node, rule.getPattern());
        }
        startIndex = Math.min(startIndex, index);
        if (consumed == null) {
            consumed = new ArrayList<>(2);
        }
        consumed.add(node);
        return Result.OK;
    }

    /**
     * Return the named node. It's an error if the
     *
     * @param name the name of a node in the match rule
     * @return the matched node
     * @throws GraalInternalError is the named node doesn't exist.
     */
    public ValueNode namedNode(String name) {
        if (namedNodes != null) {
            NamedNode value = namedNodes.get(name);
            if (value != null) {
                return value.value;
            }
        }
        throw new GraalInternalError("missing node %s", name);
    }

    @Override
    public String toString() {
        return String.format("%s %s (%d, %d) consumed %s", rule, root, startIndex, endIndex, consumed != null ? Arrays.toString(consumed.toArray()) : "");
    }
}
