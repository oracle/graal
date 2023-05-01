/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.match;

import static org.graalvm.compiler.debug.DebugOptions.LogVerbose;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

public class MatchRuleRegistry {

    /**
     * Convert a list of field names into {@link org.graalvm.compiler.graph.Position} objects that
     * can be used to read them during a match. The names should already have been confirmed to
     * exist in the type.
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
                throw new GraalError("unknown field \"%s\" in class %s", names[i], nodeClass);
            }
        }
        return result;
    }

    private static final EconomicMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> registry = EconomicMap.create(Equivalence.IDENTITY);

    /**
     * Collect all the {@link MatchStatement}s defined by the superclass chain of theClass.
     *
     * @param theClass
     * @param options
     * @return the set of {@link MatchStatement}s applicable to theClass.
     */
    @SuppressWarnings("try")
    public static synchronized EconomicMap<Class<? extends Node>, List<MatchStatement>> lookup(Class<? extends NodeMatchRules> theClass, OptionValues options, DebugContext debug) {
        EconomicMap<Class<? extends Node>, List<MatchStatement>> result = registry.get(theClass);

        if (result == null) {
            EconomicMap<Class<? extends Node>, List<MatchStatement>> rules = createRules(theClass);
            registry.put(theClass, rules);
            assert registry.get(theClass) == rules;
            result = rules;

            if (LogVerbose.getValue(options)) {
                try (DebugContext.Scope s = debug.scope("MatchComplexExpressions")) {
                    debug.log("Match rules for %s", theClass.getSimpleName());
                    MapCursor<Class<? extends Node>, List<MatchStatement>> cursor = result.getEntries();
                    while (cursor.advance()) {
                        debug.log("  For node class: %s", cursor.getKey());
                        for (MatchStatement statement : cursor.getValue()) {
                            debug.log("    %s", statement.getPattern());
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
    @SuppressWarnings("unchecked")
    public static EconomicMap<Class<? extends Node>, List<MatchStatement>> createRules(Class<? extends NodeMatchRules> theClass) {
        EconomicMap<Class<? extends NodeMatchRules>, MatchStatementSet> matchSets = EconomicMap.create(Equivalence.IDENTITY);
        Iterable<MatchStatementSet> sl = GraalServices.load(MatchStatementSet.class);
        for (MatchStatementSet rules : sl) {
            matchSets.put(rules.forClass(), rules);
        }

        // Walk the class hierarchy collecting lists and merge them together. The subclass
        // rules are first which gives them preference over earlier rules.
        EconomicMap<Class<? extends Node>, List<MatchStatement>> rules = EconomicMap.create(Equivalence.IDENTITY);
        Class<? extends NodeMatchRules> currentClass = theClass;
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
            currentClass = (Class<? extends NodeMatchRules>) currentClass.getSuperclass();
        } while (currentClass != NodeMatchRules.class);
        return rules;
    }
}
