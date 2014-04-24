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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
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
    private final ArrayList<ValueNode> consumed = new ArrayList<>();
    private final List<ScheduledNode> nodes;
    private final ValueNode root;
    private List<String> names;
    private List<Class<? extends ValueNode>> types;
    private List<ValueNode> values;
    private final MatchStatement rule;
    private int startIndex;
    private int endIndex;
    private final NodeLIRBuilder builder;

    public MatchContext(NodeLIRBuilder builder, MatchStatement rule, ValueNode node, List<ScheduledNode> nodes) {
        this.builder = builder;
        this.rule = rule;
        this.root = node;
        this.nodes = nodes;
        // The root should be the last index since all the inputs must be scheduled before.
        startIndex = endIndex = nodes.indexOf(node);
    }

    public ValueNode getRoot() {
        return root;
    }

    public Result captureNamedValue(String name, Class<? extends ValueNode> type, ValueNode value) {
        if (names == null) {
            names = new ArrayList<>(2);
            values = new ArrayList<>(2);
            types = new ArrayList<>(2);
        }
        int index = names.indexOf(name);
        if (index == -1) {
            names.add(name);
            values.add(value);
            types.add(type);
            return Result.OK;
        } else {
            if (values.get(index) != value) {
                return Result.NAMED_VALUE_MISMATCH(value, rule.getPattern());
            }
            return Result.OK;
        }
    }

    public Result validate() {
        // Ensure that there's no unsafe work in between these operations.
        for (int i = startIndex; i <= endIndex; i++) {
            ScheduledNode node = getNodes().get(i);
            if (node instanceof ConstantNode || node instanceof LocationNode || node instanceof VirtualObjectNode || node instanceof ParameterNode) {
                // these can be evaluated lazily so don't worry about them. This should probably be
                // captured by some interface that indicates that their generate method is empty.
                continue;
            } else if (!consumed.contains(node) && node != root) {
                // This is too verbose for normal logging.
                // Debug.log("unexpected node %s", node);
                // for (int j = startIndex; j <= endIndex; j++) {
                // ScheduledNode theNode = getNodes().get(j);
                // Debug.log("%s(%s) %1s", (consumed.contains(theNode) || theNode == root) ? "*" :
                // " ",
                // theNode.usages().count(), theNode);
                // }
                return Result.NOT_SAFE(node, rule.getPattern());
            }
        }
        return Result.OK;
    }

    /**
     * Transfers the captured value into the MatchGenerator instance. The reflective information
     * should really be generated and checking during construction of the MatchStatement but this is
     * ok for now.
     */
    public void transferState(MatchGenerator generator) {
        try {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                try {
                    Field field = generator.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(generator, values.get(i));
                } catch (NoSuchFieldException e) {
                    // Doesn't exist so the generator doesn't care about the value.
                }
            }
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new GraalInternalError(e);
        }
        try {
            Field field = generator.getClass().getDeclaredField("root");
            field.setAccessible(true);
            field.set(generator, getRoot());
        } catch (NoSuchFieldException e) {
            // Doesn't exist
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException e) {
            throw new GraalInternalError(e);
        }
    }

    public void setResult(ComplexMatchResult result) {
        setResult(new ComplexMatchValue(result));
    }

    /**
     * Mark the interior nodes with INTERIOR_MATCH and set the Value of the root to be the result.
     * During final LIR generation it will be evaluated to produce the actual LIR value.
     *
     * @param result
     */
    public void setResult(ComplexMatchValue result) {
        Debug.log("matched %s %s", rule.getName(), rule.getPattern());
        // Debug.log("%s", rule.formatMatch(root));
        for (ValueNode node : consumed) {
            // All the interior nodes should be skipped during the normal doRoot calls in
            // NodeLIRBuilder so mark them as interior matches. The root of the match will get a
            // closure which will be evaluated to produce the final LIR.
            getBuilder().setMatchResult(node, Value.INTERIOR_MATCH);
        }
        getBuilder().setMatchResult(root, result);
    }

    /**
     * Mark a node as consumed by the match. Consumed nodes will never be evaluated.
     *
     * @return Result.OK if the node can be safely consumed.
     */
    public Result consume(ValueNode node) {
        if (node.usages().count() != 1) {
            return Result.TOO_MANY_USERS(node, rule.getPattern());
        }

        if (getBuilder().hasOperand(node)) {
            return Result.ALREADY_USED(node, rule.getPattern());
        }

        int index = getNodes().indexOf(node);
        if (index == -1) {
            return Result.NOT_IN_BLOCK(node, rule.getPattern());
        }
        startIndex = Math.min(startIndex, index);
        consumed.add(node);
        return Result.OK;
    }

    private NodeLIRBuilder getBuilder() {
        return builder;
    }

    private List<ScheduledNode> getNodes() {
        return nodes;
    }
}
