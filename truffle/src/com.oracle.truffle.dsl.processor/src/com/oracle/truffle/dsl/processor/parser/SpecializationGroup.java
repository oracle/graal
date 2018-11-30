/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod.TypeSignature;

/**
 * Class creates groups of specializations to optimize the layout of generated executeAndSpecialize
 * and generic execute methods.
 */
public final class SpecializationGroup {

    private final List<TypeGuard> typeGuards;
    private final List<GuardExpression> guards;

    private final NodeData node;
    private final SpecializationData specialization;
    private final List<SpecializationGroup> children = new ArrayList<>();

    private SpecializationGroup parent;

    private SpecializationGroup(SpecializationData data) {
        this.node = data.getNode();
        this.typeGuards = new ArrayList<>();
        this.guards = new ArrayList<>();
        this.specialization = data;

        TypeSignature sig = data.getTypeSignature();
        for (int i = 1; i < sig.size(); i++) {
            typeGuards.add(new TypeGuard(sig.get(i), i - 1));
        }
        this.guards.addAll(data.getGuards());
    }

    private SpecializationGroup(List<SpecializationGroup> children, List<TypeGuard> typeGuardsMatches, List<GuardExpression> guardMatches) {
        assert !children.isEmpty() : "children must not be empty";
        this.typeGuards = typeGuardsMatches;
        this.guards = guardMatches;
        this.node = children.get(0).node;
        this.specialization = null;
        updateChildren(children);
    }

    public boolean isEmpty() {
        return typeGuards.isEmpty() && guards.isEmpty();
    }

    public List<TypeGuard> getAllGuards() {
        List<TypeGuard> collectedGuards = new ArrayList<>();
        collectedGuards.addAll(typeGuards);
        if (parent != null) {
            collectedGuards.addAll(parent.getAllGuards());
        }
        return collectedGuards;
    }

    public List<SpecializationData> collectSpecializations() {
        List<SpecializationData> specializations = new ArrayList<>();
        if (specialization != null) {
            specializations.add(specialization);
        }
        for (SpecializationGroup group : children) {
            specializations.addAll(group.collectSpecializations());
        }
        return specializations;
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

    public List<TypeGuard> getTypeGuards() {
        return typeGuards;
    }

    public List<GuardExpression> getGuards() {
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

        List<TypeGuard> typeGuardsMatches = new ArrayList<>();
        List<GuardExpression> guardMatches = new ArrayList<>();

        SpecializationGroup first = groups.get(0);
        List<SpecializationGroup> others = groups.subList(1, groups.size());

        outer: for (TypeGuard typeGuard : first.typeGuards) {
            for (SpecializationGroup other : others) {
                if (!other.typeGuards.contains(typeGuard)) {
                    // type guards can be combined unordered
                    continue outer;
                }
            }
            typeGuardsMatches.add(typeGuard);
        }

        outer: for (GuardExpression guard : first.guards) {
            for (SpecializationGroup other : others) {
                if (!other.guards.contains(guard)) {
                    // we must break here. One guard may depend on the other.
                    break outer;
                }
            }
            guardMatches.add(guard);
        }

        // check for guards for required type casts
        for (Iterator<GuardExpression> iterator = guardMatches.iterator(); iterator.hasNext();) {
            GuardExpression guardMatch = iterator.next();
            if (!guardMatch.getExpression().findBoundVariables().isEmpty()) {
                iterator.remove();
            }
            // TODO we need to be smarter here with bound parameters.
        }

        if (typeGuardsMatches.isEmpty() && guardMatches.isEmpty()) {
            return null;
        }

        for (SpecializationGroup group : groups) {
            group.typeGuards.removeAll(typeGuardsMatches);
            group.guards.removeAll(guardMatches);
        }

        List<SpecializationGroup> newChildren = new ArrayList<>(groups);
        return new SpecializationGroup(newChildren, typeGuardsMatches, guardMatches);
    }

    public static SpecializationGroup create(List<SpecializationData> specializations) {
        List<SpecializationGroup> groups = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            groups.add(new SpecializationGroup(specialization));
        }
        SpecializationGroup group1 = new SpecializationGroup(createCombinationalGroups(groups), Collections.<TypeGuard> emptyList(), Collections.<GuardExpression> emptyList());
        SpecializationGroup group = group1;

        // trim groups
        while (group.isEmpty() && group.getChildren().size() == 1) {
            group = group.getChildren().iterator().next();
        }
        return group;
    }

    @Override
    public String toString() {
        return "SpecializationGroup [typeGuards=" + typeGuards + ", guards=" + guards + "]";
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
        private final TypeMirror type;

        public TypeGuard(TypeMirror type, int signatureIndex) {
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
            } else if (!ElementUtils.typeEquals(type, other.type)) {
                return false;
            }
            return true;
        }

        public int getSignatureIndex() {
            return signatureIndex;
        }

        public TypeMirror getType() {
            return type;
        }
    }

    public SpecializationGroup getPrevious() {
        if (getParent() == null) {
            return null;
        }

        List<SpecializationGroup> parentChildren = getParent().getChildren();
        int index = parentChildren.indexOf(this);
        if (index <= 0) {
            return null;
        }
        return parentChildren.get(index - 1);
    }

    public List<SpecializationData> getAllSpecializations() {
        SpecializationGroup p = this;
        while (p.getParent() != null) {
            p = p.getParent();
        }
        return p.collectSpecializations();
    }

    public boolean isLast() {
        SpecializationGroup p = getParent();
        if (p == null) {
            return true;
        }
        if (p.getChildren().indexOf(this) == p.getChildren().size() - 1) {
            return p.isLast();
        }
        return false;
    }

    public SpecializationGroup getLast() {
        if (children.isEmpty()) {
            return null;
        }
        return children.get(children.size() - 1);
    }

    private boolean hasFallthrough;

    public void setFallthrough(boolean hasFallthrough) {
        this.hasFallthrough = hasFallthrough;
    }

    public boolean hasFallthrough() {
        if (hasFallthrough) {
            return true;
        }
        SpecializationGroup lastChild = getLast();
        if (lastChild != null) {
            return lastChild.hasFallthrough();
        }
        return false;
    }

}
