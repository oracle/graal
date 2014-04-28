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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.MatchPattern.Result;
import com.oracle.graal.nodes.*;

/**
 * A named {@link MatchPattern} along with a {@link MatchGenerator} that can be evaluated to replace
 * one or more {@link ValueNode}s with a single {@link Value}.
 */

public class MatchStatement {
    private final String name;
    private final MatchPattern pattern;
    private final Class<? extends MatchGenerator> generatorClass;

    public MatchStatement(String name, MatchPattern pattern) {
        this.name = name;
        this.pattern = pattern;
        this.generatorClass = null;
    }

    public MatchStatement(String name, MatchPattern pattern, Class<? extends MatchGenerator> generator) {
        this.name = name;
        this.pattern = pattern;
        this.generatorClass = generator;
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
    public boolean generate(NodeLIRBuilder builder, ValueNode node, List<ScheduledNode> nodes) {
        MatchContext context = new MatchContext(builder, this, node, nodes);
        Result result = pattern.match(node, context);
        if (result == Result.OK) {
            result = context.validate();
        }
        if (result == Result.OK) {
            MatchGenerator generator = null;
            try {
                generator = generatorClass.newInstance();
                // Transfer values into gen
                context.transferState(generator);
                ComplexMatchResult value = generator.match(builder);
                if (value != null) {
                    context.setResult(value);
                    return true;
                }
            } catch (InstantiationException | IllegalAccessException e) {
                throw new GraalInternalError(e);
            }
        } else {
            // This is fairly verbose for normal usage.
            // if (result.code != MatchResultCode.WRONG_CLASS) {
            // // Don't bother logging if it's just the wrong shape.
            // Debug.log("while matching %s|%s %s %s %s", context.getRoot().toString(Verbosity.Id),
            // context.getRoot().getClass().getSimpleName(), getName(), result, node.graph());
            // }
        }
        return false;
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
