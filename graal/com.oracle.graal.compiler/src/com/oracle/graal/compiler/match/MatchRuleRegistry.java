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

import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.nodes.*;

public class MatchRuleRegistry {

    private static final HashMap<Class<? extends NodeLIRBuilder>, Map<Class<? extends ValueNode>, List<MatchStatement>>> registry = new HashMap<>();

    /**
     * Collect all the {@link MatchStatement}s defined by the superclass chain of theClass.
     *
     * @param theClass
     * @return the set of {@link MatchStatement}s applicable to theClass.
     */
    public synchronized static Map<Class<? extends ValueNode>, List<MatchStatement>> lookup(Class<? extends NodeLIRBuilder> theClass) {
        Map<Class<? extends ValueNode>, List<MatchStatement>> result = registry.get(theClass);

        if (result == null) {
            HashMap<Class<? extends NodeLIRBuilder>, List<MatchStatement>> localRules = new HashMap<>();
            ServiceLoader<MatchStatementSet> sl = ServiceLoader.loadInstalled(MatchStatementSet.class);
            for (MatchStatementSet rules : sl) {
                localRules.put(rules.forClass(), rules.statements());
            }

            // Walk the class hierarchy collecting lists and merge them together. The subclass
            // rules are first which gives them preference over earlier rules.
            Map<Class<? extends ValueNode>, List<MatchStatement>> rules = new HashMap<>();
            Class<?> currentClass = theClass;
            do {
                List<MatchStatement> statements = localRules.get(currentClass);
                if (statements != null) {
                    for (MatchStatement statement : statements) {
                        Class<? extends ValueNode> nodeClass = statement.getPattern().nodeClass();
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
            registry.put(theClass, rules);
            assert registry.get(theClass) == rules;
            result = rules;
        }

        if (result.size() == 0) {
            return null;
        }
        return result;
    }
}
