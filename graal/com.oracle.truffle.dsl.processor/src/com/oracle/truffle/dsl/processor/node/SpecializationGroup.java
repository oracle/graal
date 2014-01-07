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

import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.template.TemplateMethod.TypeSignature;
import com.oracle.truffle.dsl.processor.typesystem.*;

/**
 * Class creates groups of specializations to optimize the layout of generated executeAndSpecialize
 * and generic execute methods.
 */
public final class SpecializationGroup {

    private final List<String> assumptions;
    private final List<TypeGuard> typeGuards;
    private final List<GuardData> guards;

    private final NodeData node;
    private final SpecializationData specialization;
    private final List<SpecializationGroup> children = new ArrayList<>();

    private SpecializationGroup parent;

    private SpecializationGroup(SpecializationData data) {
        this.node = data.getNode();
        this.assumptions = new ArrayList<>();
        this.typeGuards = new ArrayList<>();
        this.guards = new ArrayList<>();
        this.specialization = data;

        this.assumptions.addAll(data.getAssumptions());
        TypeSignature sig = data.getTypeSignature();
        for (int i = 1; i < sig.size(); i++) {
            typeGuards.add(new TypeGuard(sig.get(i), i - 1));
        }
        this.guards.addAll(data.getGuards());
    }

    public SpecializationGroup(List<SpecializationGroup> children, List<String> assumptionMatches, List<TypeGuard> typeGuardsMatches, List<GuardData> guardMatches) {
        assert !children.isEmpty() : "children must not be empty";
        this.assumptions = assumptionMatches;
        this.typeGuards = typeGuardsMatches;
        this.guards = guardMatches;
        this.node = children.get(0).node;
        this.specialization = null;
        updateChildren(children);
    }

    public List<TypeGuard> getAllGuards() {
        List<TypeGuard> collectedGuards = new ArrayList<>();
        collectedGuards.addAll(typeGuards);
        if (parent != null) {
            collectedGuards.addAll(parent.getAllGuards());
        }
        return collectedGuards;
    }

    public TypeGuard findTypeGuard(int signatureIndex) {
        for (TypeGuard guard : typeGuards) {
            if (guard.getSignatureIndex() == signatureIndex) {
                return guard;
            }
        }
        return null;
    }

    public List<GuardData> findElseConnectableGuards(boolean minimumStateCheck) {
        if (minimumStateCheck) {
            /*
             * TODO investigate further if we really cannot else connect guards if minimum state is
             * required
             */
            return Collections.emptyList();
        }
        if (!getTypeGuards().isEmpty() || !getAssumptions().isEmpty()) {
            return Collections.emptyList();
        }

        if (getGuards().isEmpty()) {
            return Collections.emptyList();
        }

        List<GuardData> elseConnectableGuards = new ArrayList<>();
        int guardIndex = 0;
        while (guardIndex < getGuards().size() && findNegatedGuardInPrevious(getGuards().get(guardIndex), minimumStateCheck) != null) {
            elseConnectableGuards.add(getGuards().get(guardIndex));
            guardIndex++;
        }

        return elseConnectableGuards;
    }

    private GuardData findNegatedGuardInPrevious(GuardData guard, boolean minimumStateCheck) {
        SpecializationGroup previous = this.getPreviousGroup();
        if (previous == null) {
            return null;
        }
        List<GuardData> elseConnectedGuards = previous.findElseConnectableGuards(minimumStateCheck);

        if (previous == null || previous.getGuards().size() != elseConnectedGuards.size() + 1) {
            return null;
        }

        /* Guard is else branch can be connected in previous specialization. */
        if (elseConnectedGuards.contains(guard)) {
            return guard;
        }

        GuardData previousGuard = previous.getGuards().get(elseConnectedGuards.size());
        if (guard.getMethod().equals(previousGuard.getMethod()) && guard.isNegated() != previousGuard.isNegated()) {
            return guard;
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

        // check for guards for required type casts
        for (Iterator<GuardData> iterator = guardMatches.iterator(); iterator.hasNext();) {
            GuardData guardMatch = iterator.next();

            int signatureIndex = 0;
            for (ActualParameter parameter : guardMatch.getParameters()) {
                signatureIndex++;
                if (!parameter.getSpecification().isSignature()) {
                    continue;
                }

                TypeMirror guardType = parameter.getType();

                // object guards can be safely moved up
                if (Utils.isObject(guardType)) {
                    continue;
                }

                // generic guards can be safely moved up
                SpecializationData generic = first.node.getGenericSpecialization();
                if (generic != null) {
                    ActualParameter genericParameter = generic.findParameter(parameter.getLocalName());
                    if (genericParameter != null && Utils.typeEquals(genericParameter.getType(), guardType)) {
                        continue;
                    }
                }

                // signature index required for moving up guards
                if (containsIndex(typeGuardsMatches, signatureIndex) || (first.getParent() != null && first.getParent().containsTypeGuardIndex(signatureIndex))) {
                    continue;
                }

                iterator.remove();
                break;
            }
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

    private boolean containsTypeGuardIndex(int index) {
        if (containsIndex(typeGuards, index)) {
            return true;
        }
        if (parent != null) {
            return parent.containsTypeGuardIndex(index);
        }
        return false;
    }

    private static boolean containsIndex(List<TypeGuard> typeGuards, int signatureIndex) {
        for (TypeGuard guard : typeGuards) {
            if (guard.signatureIndex == signatureIndex) {
                return true;
            }
        }
        return false;
    }

    public static SpecializationGroup create(SpecializationData specialization) {
        return new SpecializationGroup(specialization);
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

    public int getUncheckedSpecializationIndex() {
        int groupMaxIndex = getMaxSpecializationIndex();

        int genericIndex = node.getSpecializations().indexOf(node.getGenericSpecialization());
        if (groupMaxIndex >= genericIndex) {
            // no minimum state check for an generic index
            groupMaxIndex = -1;
        }

        if (groupMaxIndex > -1) {
            // no minimum state check if already checked by parent group
            int parentMaxIndex = -1;
            if (getParent() != null) {
                parentMaxIndex = getParent().getMaxSpecializationIndex();
            }
            if (groupMaxIndex == parentMaxIndex) {
                groupMaxIndex = -1;
            }
        }
        return groupMaxIndex;
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
