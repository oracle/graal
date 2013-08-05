/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.node;

import java.util.*;

import com.oracle.truffle.dsl.processor.template.TemplateMethod.Signature;
import com.oracle.truffle.dsl.processor.typesystem.*;

/**
 * Class creates groups of specializations to optimize the layout of generated executeAndSpecialize
 * and generic execute methods.
 */
public final class SpecializationGroup {

    private final List<String> assumptions;
    private final List<TypeGuard> typeGuards;
    private final List<GuardData> guards;

    private final SpecializationData specialization;
    private final List<SpecializationGroup> children = new ArrayList<>();

    private SpecializationGroup parent;

    private SpecializationGroup(SpecializationData data) {
        this.assumptions = new ArrayList<>();
        this.typeGuards = new ArrayList<>();
        this.guards = new ArrayList<>();
        this.specialization = data;

        this.assumptions.addAll(data.getAssumptions());
        Signature sig = data.getSignature();
        for (int i = 1; i < sig.size(); i++) {
            typeGuards.add(new TypeGuard(sig.get(i), i - 1));
        }
        this.guards.addAll(data.getGuards());
    }

    public SpecializationGroup(List<SpecializationGroup> children, List<String> assumptionMatches, List<TypeGuard> typeGuardsMatches, List<GuardData> guardMatches) {
        this.assumptions = assumptionMatches;
        this.typeGuards = typeGuardsMatches;
        this.guards = guardMatches;
        this.specialization = null;
        updateChildren(children);
    }

    public TypeGuard findTypeGuard(int signatureIndex) {
        for (TypeGuard guard : typeGuards) {
            if (guard.getSignatureIndex() == signatureIndex) {
                return guard;
            }
        }
        return null;
    }

    public GuardData getElseConnectableGuard() {
        if (!getTypeGuards().isEmpty() || !getAssumptions().isEmpty()) {
            return null;
        }
        SpecializationGroup previousGroup = getPreviousGroup();
        if (previousGroup != null && getGuards().size() >= 1 && previousGroup.getGuards().size() == 1) {
            GuardData guard = getGuards().get(0);
            GuardData previousGuard = previousGroup.getGuards().get(0);

            if (guard.getMethod().equals(previousGuard.getMethod())) {
                assert guard.isNegated() != previousGuard.isNegated();
                return guard;
            }
        }
        return null;
    }

    private void updateChildren(List<SpecializationGroup> childs) {
        if (!children.isEmpty()) {
            children.clear();
        }
        this.children.addAll(childs);
        for (SpecializationGroup child : childs) {
            child.parent = this;
        }
    }

    public SpecializationGroup getParent() {
        return parent;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public List<TypeGuard> getTypeGuards() {
        return typeGuards;
    }

    public List<GuardData> getGuards() {
        return guards;
    }

    public List<SpecializationGroup> getChildren() {
        return children;
    }

    public SpecializationData getSpecialization() {
        return specialization;
    }

    private static SpecializationGroup combine(List<SpecializationGroup> groups) {
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("empty combinations");
        }
        if (groups.size() == 1) {
            return null;
        }

        List<String> assumptionMatches = new ArrayList<>();
        List<TypeGuard> typeGuardsMatches = new ArrayList<>();
        List<GuardData> guardMatches = new ArrayList<>();

        SpecializationGroup first = groups.get(0);
        List<SpecializationGroup> others = groups.subList(1, groups.size());

        outer: for (String assumption : first.assumptions) {
            for (SpecializationGroup other : others) {
                if (!other.assumptions.contains(assumption)) {
                    // assumptions can be combined unordered
                    continue outer;
                }
            }
            assumptionMatches.add(assumption);
        }

        outer: for (TypeGuard typeGuard : first.typeGuards) {
            for (SpecializationGroup other : others) {
                if (!other.typeGuards.contains(typeGuard)) {
                    // type guards can be combined unordered
                    continue outer;
                }
            }
            typeGuardsMatches.add(typeGuard);
        }

        outer: for (GuardData guard : first.guards) {
            for (SpecializationGroup other : others) {
                if (!other.guards.contains(guard)) {
                    // we must break here. One guard may depend on the other.
                    break outer;
                }
            }
            guardMatches.add(guard);
        }

        if (assumptionMatches.isEmpty() && typeGuardsMatches.isEmpty() && guardMatches.isEmpty()) {
            return null;
        }

        for (SpecializationGroup group : groups) {
            group.assumptions.removeAll(assumptionMatches);
            group.typeGuards.removeAll(typeGuardsMatches);
            group.guards.removeAll(guardMatches);
        }

        List<SpecializationGroup> newChildren = new ArrayList<>(groups);
        return new SpecializationGroup(newChildren, assumptionMatches, typeGuardsMatches, guardMatches);
    }

    public static SpecializationGroup create(List<SpecializationData> specializations) {
        List<SpecializationGroup> groups = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            groups.add(new SpecializationGroup(specialization));
        }
        return new SpecializationGroup(createCombinationalGroups(groups), Collections.<String> emptyList(), Collections.<TypeGuard> emptyList(), Collections.<GuardData> emptyList());
    }

    @Override
    public String toString() {
        return "SpecializationGroup [assumptions=" + assumptions + ", typeGuards=" + typeGuards + ", guards=" + guards + "]";
    }

    private static List<SpecializationGroup> createCombinationalGroups(List<SpecializationGroup> groups) {
        if (groups.size() <= 1) {
            return groups;
        }
        List<SpecializationGroup> newGroups = new ArrayList<>();

        int i = 0;
        for (i = 0; i < groups.size();) {
            SpecializationGroup combined = null;
            for (int j = groups.size(); j > i + 1; j--) {
                combined = combine(groups.subList(i, j));
                if (combined != null) {
                    break;
                }
            }
            SpecializationGroup newGroup;
            if (combined == null) {
                newGroup = groups.get(i);
                i++;
            } else {
                newGroup = combined;
                List<SpecializationGroup> originalGroups = new ArrayList<>(combined.children);
                combined.updateChildren(createCombinationalGroups(originalGroups));
                i += originalGroups.size();
            }

            newGroups.add(newGroup);

        }

        return newGroups;
    }

    public SpecializationGroup getPreviousGroup() {
        if (parent == null || parent.children.isEmpty()) {
            return null;
        }
        int index = parent.children.indexOf(this);
        if (index <= 0) {
            return null;
        }
        return parent.children.get(index - 1);
    }

    public int getMaxSpecializationIndex() {
        if (specialization != null) {
            return specialization.getNode().getSpecializations().indexOf(specialization);
        } else {
            int max = Integer.MIN_VALUE;
            for (SpecializationGroup childGroup : getChildren()) {
                max = Math.max(max, childGroup.getMaxSpecializationIndex());
            }
            return max;
        }
    }

    public static final class TypeGuard {

        private final int signatureIndex;
        private final TypeData type;

        public TypeGuard(TypeData type, int signatureIndex) {
            this.type = type;
            this.signatureIndex = signatureIndex;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + signatureIndex;
            result = prime * result + type.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (getClass() != obj.getClass()) {
                return false;
            }

            TypeGuard other = (TypeGuard) obj;
            if (signatureIndex != other.signatureIndex) {
                return false;
            } else if (!type.equals(other.type)) {
                return false;
            }
            return true;
        }

        public int getSignatureIndex() {
            return signatureIndex;
        }

        public TypeData getType() {
            return type;
        }
    }
}
