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
package com.oracle.graal.nodes.spi.types;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

public class ObjectTypeFeedbackStore extends TypeFeedbackStore<ObjectTypeFeedbackStore> implements ObjectTypeFeedbackTool, CloneableTypeFeedback {

    private abstract static class BooleanPredicate <T> {
        public abstract boolean evaluate(T element);
    }

    public static class Query implements ObjectTypeQuery {

        private final ObjectTypeFeedbackStore store;

        public Query(ObjectTypeFeedbackStore store) {
            this.store = store;
        }

        @Override
        public boolean constantBound(Condition condition, final Constant constant) {
            assert condition == Condition.EQ || condition == Condition.NE;
            if (condition == Condition.EQ) {
                return store.prove(Equals.class, new BooleanPredicate<Equals>() {
                    @Override
                    public boolean evaluate(Equals element) {
                        return element.constant.equals(constant);
                    }
                });
            } else if (condition == Condition.NE) {
                boolean result = store.prove(Equals.class, new BooleanPredicate<Equals>() {
                    @Override
                    public boolean evaluate(Equals element) {
                        return !element.constant.equals(constant);
                    }
                });
                if (result) {
                    return true;
                }
                return store.prove(NotEquals.class, new BooleanPredicate<NotEquals>() {
                    @Override
                    public boolean evaluate(NotEquals element) {
                        return element.constant.equals(constant);
                    }
                });
            }
            return false;
        }

        @Override
        public boolean valueBound(Condition condition, ValueNode otherValue) {
            Condition cond = store.valueBounds == null ? null : store.valueBounds.get(otherValue);
            if (cond == null) {
                return false;
            } else {
                return cond.implies(condition);
            }
        }

        @Override
        public boolean declaredType(final ResolvedJavaType type) {
            return store.prove(Info.class, new BooleanPredicate<Info>() {
                @Override
                public boolean evaluate(Info element) {
                    if (element instanceof ObjectType) {
                        return ((ObjectType) element).type.isSubtypeOf(type);
                    } else {
                        return (element instanceof Equals) && ((Equals) element).constant.isNull();
                    }
                }
            });
        }

        @Override
        public boolean exactType(final ResolvedJavaType type) {
            return store.prove(ObjectTypeExact.class, new BooleanPredicate<ObjectTypeExact>() {
                @Override
                public boolean evaluate(ObjectTypeExact element) {
                    return type == element.type;
                }
            });
        }

        @Override
        public boolean notDeclaredType(ResolvedJavaType type) {
            return false;
        }

        @Override
        public boolean notExactType(final ResolvedJavaType type) {
            return store.prove(Info.class, new BooleanPredicate<Info>() {
                @Override
                public boolean evaluate(Info element) {
                    if (element instanceof ObjectTypeExact) {
                        return ((ObjectTypeExact) element).type != type;
                    } else {
                        return (element instanceof Equals) && ((Equals) element).constant.isNull();
                    }
                }
            });
        }

        @Override
        public String toString() {
            return store.toString();
        }

        @Override
        public ObjectTypeFeedbackStore store() {
            return store;
        }

        @Override
        public ValueNode dependency() {
            return store.dependency;
        }
    }

    private static final Info[] EMPTY_INFO_ARRAY = new Info[0];

    private static class Info {

    }

    private static final class Equals extends Info {
        public final Constant constant;

        public Equals(Constant constant) {
            this.constant = constant;
        }

        @Override
        public String toString() {
            return "== " + constant.asObject();
        }
    }

    private static final class NotEquals extends Info {
        public final Constant constant;

        public NotEquals(Constant constant) {
            this.constant = constant;
        }

        @Override
        public String toString() {
            return "!= " + constant.asObject();
        }
    }

    private static class ObjectType extends Info {
        public final ResolvedJavaType type;

        public ObjectType(ResolvedJavaType type) {
            this.type = type;
        }
    }

    private static final class ObjectTypeDeclared extends ObjectType {

        public ObjectTypeDeclared(ResolvedJavaType type) {
            super(type);
        }

        @Override
        public String toString() {
            return "instanceof " + type;
        }
    }

    private static final class ObjectTypeExact extends ObjectType {

        public ObjectTypeExact(ResolvedJavaType type) {
            super(type);
        }

        @Override
        public String toString() {
            return "exact " + type;
        }
    }

    private static final class MergedTypeInfo extends Info {
        public final Info[][] mergedInfos;

        public MergedTypeInfo(Info[][] infos) {
            mergedInfos = infos;
        }

        @Override
        public String toString() {
            return "merged type: [" + Arrays.deepToString(mergedInfos) + "]";
        }
    }

    private final LinkedList<Info> infos = new LinkedList<>();
    private HashMap<ValueNode, Condition> valueBounds;

    private final TypeFeedbackChanged changed;

    private ValueNode dependency;

    private void updateDependency() {
        dependency = changed.node;
    }

    private static <T extends Info> boolean prove(Info[] infos, Class<T> clazz, IdentityHashMap<MergedTypeInfo, Boolean> cache, BooleanPredicate<T> predicate) {
        for (Info info : infos) {
            if (clazz.isAssignableFrom(info.getClass())) {
                if (predicate.evaluate(clazz.cast(info))) {
                    return true;
                }
            }
            if (info instanceof MergedTypeInfo) {
                if (cache.get(info) != null) {
                    return cache.get(info);
                }
                for (Info[] subInfos : ((MergedTypeInfo) info).mergedInfos) {
                    if (!prove(subInfos, clazz, cache, predicate)) {
                        cache.put((MergedTypeInfo) info, false);
                        return false;
                    }
                }
                cache.put((MergedTypeInfo) info, true);
                return true;
            }
        }
        return false;
    }

    public <T extends Info> boolean prove(Class<T> clazz, BooleanPredicate<T> predicate) {
        for (Info info : infos) {
            if (clazz.isAssignableFrom(info.getClass())) {
                if (predicate.evaluate(clazz.cast(info))) {
                    return true;
                }
            }
            if (info instanceof MergedTypeInfo) {
                IdentityHashMap<MergedTypeInfo, Boolean> cache = new IdentityHashMap<>();
                for (Info[] subInfos : ((MergedTypeInfo) info).mergedInfos) {
                    if (!prove(subInfos, clazz, cache, predicate)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public ObjectTypeFeedbackStore(TypeFeedbackChanged changed) {
        this.changed = changed;
        this.dependency = null;
    }

    private ObjectTypeFeedbackStore(ObjectTypeFeedbackStore other) {
        this.changed = other.changed;
        if (other.valueBounds != null && !other.valueBounds.isEmpty()) {
            valueBounds = new HashMap<>(other.valueBounds.size());
            valueBounds.putAll(other.valueBounds);
        }
        infos.addAll(other.infos);
        this.dependency = other.dependency;
    }

    @Override
    public void constantBound(Condition condition, Constant constant) {
        assert condition == Condition.EQ || condition == Condition.NE;

        if (condition == Condition.EQ) {
            if (infos.size() == 1 && infos.element() instanceof Equals && ((Equals) infos.element()).constant.equals(constant)) {
                return;
            }
            infos.clear();
            infos.add(new Equals(constant));
            updateDependency();
        } else if (condition == Condition.NE) {
            for (ListIterator<Info> iter = infos.listIterator(); iter.hasNext();) {
                int index = iter.nextIndex();
                Info info = iter.next();
                if (info instanceof NotEquals && ((NotEquals) info).constant.equals(constant)) {
                    if (index == 0) {
                        return;
                    } else {
                        iter.remove();
                    }
                }
            }
            infos.add(new NotEquals(constant));
            updateDependency();
        } else {
            throw new GraalInternalError("unexpected condition: %s", condition);
        }
    }

    @Override
    public void valueBound(Condition condition, ValueNode otherValue) {
        assert condition == Condition.EQ || condition == Condition.NE;

        if (otherValue != null) {
            if (valueBounds == null) {
                valueBounds = new HashMap<>();
            }
            Condition cond = valueBounds.get(otherValue);
            if (cond == null) {
                valueBounds.put(otherValue, condition);
                updateDependency();
            } else {
                Condition newCondition = cond.join(condition);
                if (newCondition == null) {
                    valueBounds.remove(otherValue);
                } else {
                    if (cond != newCondition) {
                        valueBounds.put(otherValue, newCondition);
                        updateDependency();
                    }
                }
            }
        }
    }

    @Override
    public void declaredType(ResolvedJavaType type, boolean nonNull) {
        assert type != null;

        for (ListIterator<Info> iter = infos.listIterator(); iter.hasNext();) {
            int index = iter.nextIndex();
            Info info = iter.next();
            if (info instanceof ObjectTypeDeclared) {
                ObjectTypeDeclared typeInfo = (ObjectTypeDeclared) info;
                if (typeInfo.type == type && index == 0) {
                    if (index == 0) {
                        if (nonNull) {
                            constantBound(Condition.NE, Constant.NULL_OBJECT);
                        }
                        return;
                    } else {
                        iter.remove();
                    }
                }
            }
        }
        infos.add(new ObjectTypeDeclared(type));
        updateDependency();
        if (nonNull) {
            constantBound(Condition.NE, Constant.NULL_OBJECT);
        }
    }

    @Override
    public void exactType(ResolvedJavaType type) {
        assert type != null;

        for (ListIterator<Info> iter = infos.listIterator(); iter.hasNext();) {
            int index = iter.nextIndex();
            Info info = iter.next();
            if (info instanceof ObjectTypeExact) {
                ObjectTypeExact typeInfo = (ObjectTypeExact) info;
                if (typeInfo.type == type && index == 0) {
                    if (index == 0) {
                        constantBound(Condition.NE, Constant.NULL_OBJECT);
                        return;
                    } else {
                        iter.remove();
                    }
                }
            }
        }
        infos.add(new ObjectTypeExact(type));
        updateDependency();
        constantBound(Condition.NE, Constant.NULL_OBJECT);
    }

    @Override
    public void notDeclaredType(ResolvedJavaType type, boolean includesNull) {
    }

    @Override
    public void notExactType(ResolvedJavaType type) {
    }

    public static ObjectTypeFeedbackStore meet(ObjectTypeFeedbackStore[] others) {
        boolean emptyValueBounds = false;
        for (int i = 0; i < others.length; i++) {
            if (others[i] == null) {
                return null;
            }
            if (others[i].valueBounds == null || others[i].valueBounds.isEmpty()) {
                emptyValueBounds = true;
            }
        }

        ObjectTypeFeedbackStore first = others[0];
        ObjectTypeFeedbackStore result = new ObjectTypeFeedbackStore(first.changed);

        if (!emptyValueBounds) {
            for (Map.Entry<ValueNode, Condition> entry : first.valueBounds.entrySet()) {
                Condition condition = entry.getValue();
                for (int i = 1; i < others.length; i++) {
                    Condition otherCond = others[i].valueBounds.get(entry.getKey());
                    if (otherCond != null) {
                        condition = null;
                        break;
                    }
                    condition = condition.meet(otherCond);
                    if (condition == null) {
                        break;
                    }
                }
                if (condition != null) {
                    if (result.valueBounds == null) {
                        result.valueBounds = new HashMap<>(first.valueBounds.size());
                    }
                    result.valueBounds.put(entry.getKey(), condition);
                }
            }
        }

        boolean simpleMerge = true;
        for (int i = 1; i < others.length; i++) {
            if (!others[i].infos.equals(others[i - 1].infos)) {
                simpleMerge = false;
                break;
            }
        }
        if (simpleMerge) {
            result.infos.addAll(others[0].infos);
        } else {
            Info[][] infos = new Info[others.length][];
            for (int i = 0; i < others.length; i++) {
                infos[i] = others[i].infos.toArray(EMPTY_INFO_ARRAY);
            }
            MergedTypeInfo merged = new MergedTypeInfo(infos);
            result.infos.add(merged);
        }
        return result;
    }

    @Override
    public ObjectTypeFeedbackStore clone() {
        return new ObjectTypeFeedbackStore(this);
    }

    public ObjectTypeQuery query() {
        return new Query(this);
    }

    public boolean isEmpty() {
        return infos.isEmpty() && (valueBounds == null || valueBounds.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (Info info : infos) {
            str.append(info).append(", ");
        }
        if (valueBounds != null) {
            for (Map.Entry<ValueNode, Condition> entry : valueBounds.entrySet()) {
                str.append(entry.getValue().operator).append(' ').append(entry.getKey()).append(", ");
            }
        }
        if (str.length() > 1) {
            str.setLength(str.length() - 2);
        }
        if (dependency != null) {
            str.append(" @ ").append(dependency);
        }
        return str.toString();
    }

    /*
//    equals contains all the values that might happen to be in this variable. If it is null then there is no information about possible values.
//    If it is empty, then we're currently in a branch that will be removed by canonicalization later on.
    private Set<CiConstant> equals;
//    notEquals contains all the values that cannot be in this variable.
    private Set<CiConstant> notEquals;

    private HashMap<ValueNode, Condition> valueBounds;

    private Set<RiResolvedType> exactTypes;

    private Set<RiResolvedType> declaredTypes;
    private final TypeFeedbackChanged changed;

    private Node dependency;

    private void updateDependency() {
        dependency = changed.node;
    }

    public ObjectTypeFeedbackStore(TypeFeedbackChanged changed) {
        this.changed = changed;
        this.dependency = null;
    }

    private ObjectTypeFeedbackStore(ObjectTypeFeedbackStore other) {
        this.changed = other.changed;
        if (other.valueBounds != null && !other.valueBounds.isEmpty()) {
            valueBounds = new HashMap<>(other.valueBounds.size());
            valueBounds.putAll(other.valueBounds);
        }
        if (other.equals != null) {
            equals = new HashSet<>(other.equals);
        }
        if (other.notEquals != null && !other.notEquals.isEmpty()) {
            notEquals = new HashSet<>(other.notEquals);
        }
        this.dependency = other.dependency;
    }

    @Override
    public void constantBound(Condition condition, CiConstant constant) {
        assert condition == Condition.EQ || condition == Condition.NE;

        if (condition == Condition.EQ) {
            if (equals == null) {
                equals = new HashSet<>();
                equals.add(constant);
                updateDependency();
            } else {
                if (equals.contains(constant)) {
                    equals.clear();
                    equals.add(constant);
                    updateDependency();
                } else {
                    // join with a value that cannot exist: we're in a branch that will hopefully be canonicalized away
                    equals.clear();
                }
            }
        } else if (condition == Condition.NE) {
            if (notEquals == null) {
                notEquals = new HashSet<>();
            }
            if (equals != null && equals.contains(constant)) {
                equals.remove(constant);
            }
            if (notEquals.add(constant)) {
                updateDependency();
            }
        }
    }

    @Override
    public void valueBound(Condition condition, ValueNode otherValue) {
        assert condition == Condition.EQ || condition == Condition.NE;

        if (otherValue != null) {
            if (valueBounds == null) {
                valueBounds = new HashMap<>();
            }
            Condition cond = valueBounds.get(otherValue);
            if (cond == null) {
                valueBounds.put(otherValue, condition);
                updateDependency();
            } else {
                Condition newCondition = cond.join(condition);
                if (newCondition == null) {
                    valueBounds.remove(otherValue);
                } else {
                    if (cond != newCondition) {
                        valueBounds.put(otherValue, newCondition);
                        updateDependency();
                    }
                }
            }
        }
    }

    @Override
    public void declaredType(RiResolvedType type, boolean nonNull) {
        if (declaredTypes == null) {
            declaredTypes = new HashSet<>();
            declaredTypes.add(type);
            updateDependency();
        } else {
            if (type.isInterface()) {
                for (Iterator<RiResolvedType> iter = declaredTypes.iterator(); iter.hasNext();) {
                    RiResolvedType declaredType = iter.next();
                    if (declaredType.isInterface()) {
                        if (type.isSubtypeOf(declaredType)) {
                            iter.remove();
                        } else if (declaredType.isSubtypeOf(type)) {
                            // some more specific type is already in the list - nothing to do
                            return;
                        }
                    }
                }
                if (declaredTypes.add(type)) {
                    updateDependency();
                }
            } else {
                for (Iterator<RiResolvedType> iter = declaredTypes.iterator(); iter.hasNext();) {
                    RiResolvedType declaredType = iter.next();
                    if (!declaredType.isInterface()) {
                        if (type.isSubtypeOf(declaredType)) {
                            iter.remove();
                        } else if (declaredType.isSubtypeOf(type)) {
                            // some more specific type is already in the list - nothing to do
                            return;
                        }
                    }
                }
                if (declaredTypes.add(type)) {
                    updateDependency();
                }
            }
        }
        if (nonNull) {
            constantBound(Condition.NE, CiConstant.NULL_OBJECT);
        }
    }

    @Override
    public void exactType(RiResolvedType type) {
        if (exactTypes == null) {
            exactTypes = new HashSet<>();
            exactTypes.add(type);
            updateDependency();
        } else {
            if (exactTypes.contains(type)) {
                exactTypes.clear();
                exactTypes.add(type);
                updateDependency();
            } else {
                // join with a value that cannot exist: we're in a branch that will hopefully be canonicalized away
                exactTypes.clear();
            }
        }
        constantBound(Condition.NE, CiConstant.NULL_OBJECT);
    }

    @Override
    public void notDeclaredType(RiResolvedType type, boolean nonNull) {
    }

    @Override
    public void notExactType(RiResolvedType type) {
    }

    @Override
    public void meet(ObjectTypeFeedbackStore other) {
        dependency = null;
        if (equals != null && other.equals != null) {
            equals.addAll(other.equals);
        } else {
            equals = null;
        }
        if (notEquals != null && !notEquals.isEmpty() && other.notEquals != null && !other.notEquals.isEmpty()) {
            for (Iterator<CiConstant> iter = notEquals.iterator(); iter.hasNext();) {
                CiConstant constant = iter.next();
                if (!other.notEquals.contains(constant)) {
                    iter.remove();
                }
            }
        } else {
            notEquals = null;
        }
        if (valueBounds != null && !valueBounds.isEmpty() && other.valueBounds != null && !other.valueBounds.isEmpty()) {
            HashMap<ValueNode, Condition> newBounds = new HashMap<>(valueBounds.size());
            for (Map.Entry<ValueNode, Condition> entry : valueBounds.entrySet()) {
                Condition otherCond = other.valueBounds.get(entry.getKey());
                if (otherCond != null) {
                    Condition newCondition = entry.getValue().meet(otherCond);
                    if (newCondition != null) {
                        newBounds.put(entry.getKey(), newCondition);
                    }
                }
            }
            if (newBounds.isEmpty()) {
                valueBounds = null;
            } else {
                valueBounds = newBounds;
            }
        } else {
            valueBounds = null;
        }
        declaredTypes = null;
        exactTypes = null;
    }

    @Override
    public ObjectTypeFeedbackStore clone() {
        return new ObjectTypeFeedbackStore(this);
    }

    public ObjectTypeQuery query() {
        return new Query(this);
    }

    public boolean isEmpty() {
        return equals == null && (notEquals == null || notEquals.isEmpty()) && (valueBounds == null || valueBounds.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        if (equals != null && !equals.isEmpty()) {
            str.append("== ");
            if (equals.size() == 1) {
                str.append(equals.iterator().next());
            } else {
                str.append("(");
                for (CiConstant constant : equals) {
                    str.append(constant).append(',');
                }
                str.setLength(str.length() - 1);
                str.append(')');
            }
            str.append(", ");
        }
        if (notEquals != null && !notEquals.isEmpty()) {
            str.append("!= ");
            if (notEquals.size() == 1) {
                str.append(notEquals.iterator().next());
            } else {
                str.append("(");
                for (CiConstant constant : notEquals) {
                    str.append(constant).append(',');
                }
                str.setLength(str.length() - 1);
                str.append(')');
            }
            str.append(", ");
        }
        if (valueBounds != null) {
            for (Map.Entry<ValueNode, Condition> entry : valueBounds.entrySet()) {
                str.append(entry.getValue().operator).append(' ').append(entry.getKey()).append(", ");
            }
        }
        if (declaredTypes != null) {
            str.append("declared (");
            for (RiResolvedType type: declaredTypes) {
                str.append(type).append(',');
            }
            str.setLength(str.length() - 1);
            str.append("), ");
        }
        if (exactTypes != null) {
            str.append("exact (");
            for (RiResolvedType type: exactTypes) {
                str.append(type).append(',');
            }
            str.setLength(str.length() - 1);
            str.append("), ");
        }
        if (str.length() > 1) {
            str.setLength(str.length() - 2);
        }
        if (dependency != null) {
            str.append(" @ ").append(dependency);
        }
        return str.toString();
    }*/
}
