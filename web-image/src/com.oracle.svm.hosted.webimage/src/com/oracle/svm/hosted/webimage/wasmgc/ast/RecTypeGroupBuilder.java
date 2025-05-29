/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc.ast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;

/**
 * Builds a {@link RecursiveGroup} while satisfying ordering requirements.
 * <p>
 * Instances of this class must be created through {@link RecursiveGroup#builder()}.
 * <p>
 * In Wasm, types that recursively reference each other (e.g. through field or array element types),
 * have to be in the same recursive group. In addition, a type definition has to be defined after
 * its supertype. This class satisfies the first requirement by putting all types into the same
 * recursive group. For the second requirement, this class topologically sorts types according to
 * their supertype relation.
 * <p>
 * Types are implicitly represented in a graph. Each type is a node and each type has an edge to all
 * its subtypes. Because there can be at most a single supertype, this is a forest with trees rooted
 * at types without supertypes.
 */
public class RecTypeGroupBuilder {

    /**
     * Maps types to a list of direct subtypes.
     * <p>
     * Encodes the type graph edges.
     */
    private final Map<WasmId.Type, List<WasmId.Type>> subTypeMap = new HashMap<>();

    /**
     * Maps type ids to their {@link TypeDefinition}.
     * <p>
     * {@link TypeDefinition} cannot be used as the graph nodes because the type definition for a
     * type's supertype may not be created yet when it's added.
     */
    private final Map<WasmId.Type, TypeDefinition> typeDefinitions = new HashMap<>();

    /**
     * Roots of the topological sort.
     * <p>
     * These are type ids that don't have any supertypes.
     */
    private final List<WasmId.Type> roots = new ArrayList<>();

    protected RecTypeGroupBuilder() {
    }

    public void addTypeDefinition(TypeDefinition definition) {
        WasmId.Type typeId = definition.getId();
        WasmId.Type superType = definition.supertype;
        assert !typeDefinitions.containsKey(typeId) : "Duplicate type registered: " + definition;

        typeDefinitions.put(typeId, definition);

        if (superType == null) {
            roots.add(typeId);
        } else {
            subTypeMap.computeIfAbsent(superType, t -> new ArrayList<>()).add(typeId);
        }
    }

    /**
     * Builds a {@link RecursiveGroup} from all added {@link TypeDefinition}s.
     * <p>
     * Only call this method once all types are added.
     */
    public RecursiveGroup build(Object comment) {
        /*
         * Uses a SequencedSet because it has to preserve insertion order and because we want fast
         * lookups for the assertions.
         */
        SequencedSet<TypeDefinition> topologicalSort = LinkedHashSet.newLinkedHashSet(typeDefinitions.size());
        Deque<WasmId.Type> worklist = new ArrayDeque<>(roots);

        while (!worklist.isEmpty()) {
            WasmId.Type t = worklist.pop();

            TypeDefinition typeDefinition = typeDefinitions.get(t);

            assert !topologicalSort.contains(typeDefinition) : "Type already visited: " + t;
            assert typeDefinition.supertype == null || topologicalSort.contains(typeDefinitions.get(typeDefinition.supertype)) : "Supertype of " + t + " not yet visited";

            topologicalSort.add(typeDefinition);

            List<WasmId.Type> subTypes = subTypeMap.get(t);

            // Types that are not in the subTypeMap do not have any subtypes
            if (subTypes != null) {
                /*
                 * Types can have at most one supertype, so once a type is visited, all its subtypes
                 * can be visited without violating topological order.
                 */
                subTypes.forEach(worklist::push);
            }
        }

        assert topologicalSort.size() <= typeDefinitions.size() : "Topological sort found too many type definitions: " + new HashSet<>(topologicalSort).removeAll(typeDefinitions.values());
        assert topologicalSort.size() >= typeDefinitions.size() : "Some type definitions were not encountered during topological sort (are there cycles?): " +
                        new HashSet<>(typeDefinitions.values()).removeAll(topologicalSort);

        return new RecursiveGroup(topologicalSort, comment);
    }
}
