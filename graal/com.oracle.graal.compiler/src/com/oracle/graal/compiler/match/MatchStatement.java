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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.MatchPattern.MatchResultCode;
import com.oracle.graal.compiler.match.MatchPattern.Result;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;

/**
 * A named {@link MatchPattern} along with a {@link MatchGenerator} that can be evaluated to replace
 * one or more {@link ValueNode}s with a single {@link Value}.
 */

public class MatchStatement {
    private static final DebugMetric MatchStatementSuccess = Debug.metric("MatchStatementSuccess");

    /**
     * A printable name for this statement. Usually it's just the name of the method doing the
     * emission.
     */
    private final String name;

    /**
     * The actual match pattern.
     */
    private final MatchPattern pattern;

    /**
     * The method in the {@link NodeLIRBuilder} subclass that will actually do the code emission.
     */
    private Method generatorMethod;

    /**
     * The name of arguments in the order they are expected to be passed to the generator method.
     */
    private String[] arguments;

    public MatchStatement(String name, MatchPattern pattern, Method generator, String[] arguments) {
        this.name = name;
        this.pattern = pattern;
        this.generatorMethod = generator;
        this.arguments = arguments;
    }

    /**
     * Attempt to match the current statement against a Node.
     *
     * @param builder the current builder instance.
     * @param node the node to be matched
     * @param nodes the nodes in the current block
     * @return true if the statement matched something and set a {@link ComplexMatchResult} to be
     *         evaluated by the NodeLIRBuilder.
     */
    public boolean generate(NodeLIRBuilder builder, int index, ValueNode node, List<ScheduledNode> nodes) {
        assert index == nodes.indexOf(node);
        // Check that the basic shape matches
        Result result = pattern.matchShape(node, this);
        if (result != Result.OK) {
            return false;
        }
        // Now ensure that the other safety constraints are matched.
        MatchContext context = new MatchContext(builder, this, index, node, nodes);
        result = pattern.matchUsage(node, context);
        if (result == Result.OK) {
            try {
                // Invoke the generator method and set the result if it's non null.
                ComplexMatchResult value = (ComplexMatchResult) generatorMethod.invoke(builder, buildArgList(context));
                if (value != null) {
                    context.setResult(value);
                    MatchStatementSuccess.increment();
                    return true;
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new GraalInternalError(e);
            }
        } else {
            if (LogVerbose.getValue() && result.code != MatchResultCode.WRONG_CLASS) {
                Debug.log("while matching %s|%s %s %s", context.getRoot().toString(Verbosity.Id), context.getRoot().getClass().getSimpleName(), getName(), result);
            }
        }
        return false;
    }

    /**
     * @param context
     * @return the ValueNodes captured by the match rule in the order expected by the
     *         generatorMethod
     */
    private Object[] buildArgList(MatchContext context) {
        Object[] result = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            if ("root".equals(arguments[i])) {
                result[i] = context.getRoot();
            } else {
                result[i] = context.namedNode(arguments[i]);
                if (result[i] == null) {
                    throw new GraalGraphInternalError("Can't find named node %s", arguments[i]);
                }
            }
        }
        return result;
    }

    public String formatMatch(ValueNode root) {
        return pattern.formatMatch(root);
    }

    public MatchPattern getPattern() {
        return pattern;
    }

    public String getName() {
        return name;
    }
}
