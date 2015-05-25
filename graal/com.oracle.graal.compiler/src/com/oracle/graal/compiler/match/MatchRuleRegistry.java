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
import java.util.Map.Entry;

import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.runtime.*;

public class MatchRuleRegistry {

    /**
     * Convert a list of field names into {@link com.oracle.graal.graph.Position} objects that can
     * be used to read them during a match. The names should already have been confirmed to exist in
     * the type.
     *
     * @param nodeClass
     * @param names
     * @return an array of Position objects corresponding to the named fields.
     */
    public static Position[] findPositions(NodeClass<? extends Node> nodeClass, String[] names) {
        Position[] result = new Position[names.length];
        for (int i = 0; i < names.length; i++) {
            Edges edges = nodeClass.getInputEdges();
            for (int e = 0; e < edges.getDirectCount(); e++) {
                if (names[i].equals(edges.getName(e))) {
                    result[i] = new Position(edges, e, Node.NOT_ITERABLE);
                }
            }
            if (result[i] == null) {
                throw new JVMCIError("unknown field \"%s\" in class %s", names[i], nodeClass);
            }
        }
        return result;
    }

    private static final HashMap<Class<? extends NodeLIRBuilder>, Map<Class<? extends Node>, List<MatchStatement>>> registry = new HashMap<>();

    /**
     * Collect all the {@link MatchStatement}s defined by the superclass chain of theClass.
     *
     * @param theClass
     * @return the set of {@link MatchStatement}s applicable to theClass.
     */
    public static synchronized Map<Class<? extends Node>, List<MatchStatement>> lookup(Class<? extends NodeLIRBuilder> theClass) {
        Map<Class<? extends Node>, List<MatchStatement>> result = registry.get(theClass);

        if (result == null) {
            Map<Class<? extends Node>, List<MatchStatement>> rules = createRules(theClass);
            registry.put(theClass, rules);
            assert registry.get(theClass) == rules;
            result = rules;

            if (LogVerbose.getValue()) {
                try (Scope s = Debug.scope("MatchComplexExpressions")) {
                    Debug.log("Match rules for %s", theClass.getSimpleName());
                    for (Entry<Class<? extends Node>, List<MatchStatement>> entry : result.entrySet()) {
                        Debug.log("  For node class: %s", entry.getKey());
                        for (MatchStatement statement : entry.getValue()) {
                            Debug.log("    %s", statement.getPattern());
                        }
                    }
                }
            }
        }

        if (result.size() == 0) {
            return null;
        }
        return result;
    }

    /*
     * This is a separate, public method so that external clients can create rules with a custom
     * lookup and without the default caching behavior.
     */
    public static Map<Class<? extends Node>, List<MatchStatement>> createRules(Class<? extends NodeLIRBuilder> theClass) {
        HashMap<Class<? extends NodeLIRBuilder>, MatchStatementSet> matchSets = new HashMap<>();
        Iterable<MatchStatementSet> sl = Services.load(MatchStatementSet.class);
        for (MatchStatementSet rules : sl) {
            matchSets.put(rules.forClass(), rules);
        }

        // Walk the class hierarchy collecting lists and merge them together. The subclass
        // rules are first which gives them preference over earlier rules.
        Map<Class<? extends Node>, List<MatchStatement>> rules = new HashMap<>();
        Class<?> currentClass = theClass;
        do {
            MatchStatementSet matchSet = matchSets.get(currentClass);
            if (matchSet != null) {
                List<MatchStatement> statements = matchSet.statements();
                for (MatchStatement statement : statements) {
                    Class<? extends Node> nodeClass = statement.getPattern().nodeClass();
                    List<MatchStatement> current = rules.get(nodeClass);
                    if (current == null) {
                        current = new ArrayList<>();
                        rules.put(nodeClass, current);
                    }
                    current.add(statement);
                }
            }
            currentClass = currentClass.getSuperclass();
        } while (currentClass != NodeLIRBuilder.class);
        return rules;
    }
}
