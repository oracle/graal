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

import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;

/**
 * A simple recursive pattern matcher for a DAG of nodes.
 */

public class MatchPattern {

    enum MatchResultCode {
        OK,
        WRONG_CLASS,
        NAMED_VALUE_MISMATCH,
        TOO_MANY_USERS,
        NOT_IN_BLOCK,
        NOT_SAFE,
        ALREADY_USED,
    }

    /**
     * A descriptive result for match failures. This can be helpful for debugging why a match
     * doesn't work as expected.
     */
    static class Result {
        final MatchResultCode code;
        final ScheduledNode node;
        final MatchPattern matcher;

        Result(MatchResultCode result, ScheduledNode node, MatchPattern matcher) {
            this.code = result;
            this.node = node;
            this.matcher = matcher;
        }

        static final Result OK = new Result(MatchResultCode.OK, null, null);

        static Result WRONG_CLASS(ValueNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.WRONG_CLASS, node, matcher);
        }

        static Result NAMED_VALUE_MISMATCH(ValueNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.NAMED_VALUE_MISMATCH, node, matcher);
        }

        static Result TOO_MANY_USERS(ValueNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.TOO_MANY_USERS, node, matcher);
        }

        static Result NOT_IN_BLOCK(ScheduledNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.NOT_IN_BLOCK, node, matcher);
        }

        static Result NOT_SAFE(ScheduledNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.NOT_SAFE, node, matcher);
        }

        static Result ALREADY_USED(ValueNode node, MatchPattern matcher) {
            return new Result(MatchResultCode.ALREADY_USED, node, matcher);
        }

        @Override
        public String toString() {
            if (code == MatchResultCode.OK) {
                return "OK";
            }
            return code + " " + node.toString(Verbosity.Id) + "|" + node.getClass().getSimpleName() + " " + matcher;
        }
    }

    /**
     * The expected type of the node. It must match exactly.
     */
    private final Class<? extends ValueNode> nodeClass;
    /**
     * An optional name for this node. A name can occur multiple times in a match and that name must
     * always refer to the same node of the match will fail.
     */
    private final String name;
    /**
     * An optional pattern for the first input.
     */
    private final MatchPattern first;
    /**
     * An optional pattern for the second input.
     */
    private final MatchPattern second;
    /**
     * Helper class to visit the inputs.
     */
    private final MatchNodeAdapter adapter;
    /**
     * Can there only be one user of the node. Constant nodes can be matched even if there are other
     * users.
     */
    private final boolean singleUser;

    public MatchPattern(String name, boolean singleUser) {
        this(null, name, null, null, null, singleUser);
    }

    public MatchPattern(Class<? extends ValueNode> nodeClass, String name, boolean singleUser) {
        this(nodeClass, name, null, null, null, singleUser);
    }

    public MatchPattern(Class<? extends ValueNode> nodeClass, String name, MatchPattern first, MatchNodeAdapter adapter, boolean singleUser) {
        this(nodeClass, name, first, null, adapter, singleUser);
    }

    public MatchPattern(Class<? extends ValueNode> nodeClass, String name, MatchPattern first, MatchPattern second, MatchNodeAdapter adapter, boolean singleUser) {
        this.nodeClass = nodeClass;
        this.name = name;
        this.singleUser = singleUser;
        this.first = first;
        this.second = second;
        this.adapter = adapter;
    }

    Class<? extends ValueNode> nodeClass() {
        return nodeClass;
    }

    Result match(ValueNode node, MatchContext context) {
        return matchTree(node, context, true);
    }

    private Result matchTree(ValueNode node, MatchContext context, boolean atRoot) {
        Result result = Result.OK;
        if (nodeClass != null && node.getClass() != nodeClass) {
            return Result.WRONG_CLASS(node, this);
        }
        if (singleUser && !atRoot) {
            result = context.consume(node);
            if (result != Result.OK) {
                return result;
            }
        }

        if (name != null) {
            result = context.captureNamedValue(name, nodeClass, node);
        }

        if (first != null) {
            result = first.matchTree(adapter.getFirstInput(node), context, false);
            if (result == Result.OK && second != null) {
                result = second.matchTree(adapter.getSecondInput(node), context, false);
            }
        }

        return result;
    }

    /**
     * For a node starting at root, produce a String showing the inputs that matched against this
     * rule. It's assumed that a match has already succeeded against this rule, otherwise the
     * printing may produce exceptions.
     */
    public String formatMatch(ValueNode root) {
        String result = String.format("%s", root);
        if (first == null && second == null) {
            return result;
        } else {
            return "(" + result + (first != null ? " " + first.formatMatch(adapter.getFirstInput(root)) : "") + (second != null ? " " + second.formatMatch(adapter.getSecondInput(root)) : "") + ")";
        }
    }

    @Override
    public String toString() {
        if (nodeClass == null) {
            return name;
        } else {
            String pre = first != null || second != null ? "(" : "";
            String post = first != null || second != null ? ")" : "";
            String nodeName = nodeClass.getSimpleName();
            return pre + nodeName + (name != null ? "=\"" + name + "\"" : "") + (first != null ? (" " + first.toString()) : "") + (second != null ? (" " + second.toString()) : "") + post;
        }
    }
}
