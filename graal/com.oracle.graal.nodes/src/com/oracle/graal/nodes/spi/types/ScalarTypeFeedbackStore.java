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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.max.cri.ci.*;

public class ScalarTypeFeedbackStore extends TypeFeedbackStore<ScalarTypeFeedbackStore> implements ScalarTypeFeedbackTool, CloneableTypeFeedback {

    public static class Query implements ScalarTypeQuery {

        private final ScalarTypeFeedbackStore store;

        public Query(ScalarTypeFeedbackStore store) {
            this.store = store;
        }

        @Override
        public boolean constantBound(Condition condition, CiConstant constant) {
            if (constant.kind == CiKind.Int || constant.kind == CiKind.Long) {
                switch (condition) {
                    case EQ:
                        return store.constantBounds.lowerBound == constant.asLong() && store.constantBounds.upperBound == constant.asLong();
                    case NE:
                        return store.constantBounds.lowerBound > constant.asLong() || store.constantBounds.upperBound < constant.asLong();
                    case LT:
                        return store.constantBounds.upperBound < constant.asLong();
                    case LE:
                        return store.constantBounds.upperBound <= constant.asLong();
                    case GT:
                        return store.constantBounds.lowerBound > constant.asLong();
                    case GE:
                        return store.constantBounds.lowerBound >= constant.asLong();
                    case BT:
                        return constant.asLong() >= 0 && store.constantBounds.upperBound < constant.asLong() && store.constantBounds.lowerBound >= 0;
                    case BE:
                        return constant.asLong() >= 0 && store.constantBounds.upperBound <= constant.asLong() && store.constantBounds.lowerBound >= 0;
                    case AT:
                        return constant.asLong() < 0 && store.constantBounds.lowerBound > constant.asLong() && store.constantBounds.upperBound < 0;
                    case AE:
                        return constant.asLong() < 0 && store.constantBounds.lowerBound >= constant.asLong() && store.constantBounds.upperBound < 0;
                }
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
        public String toString() {
            return store.toString();
        }

        @Override
        public ScalarTypeFeedbackStore store() {
            return store;
        }

        @Override
        public ValueNode dependency() {
            return store.dependency;
        }
    }

    private static class ConstantBound {

        public long lowerBound;
        public long upperBound;

        public ConstantBound(long lowerBound, long upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public void meet(ConstantBound other) {
            lowerBound = Math.min(lowerBound, other.lowerBound);
            upperBound = Math.max(upperBound, other.upperBound);
        }

        public boolean join(ConstantBound other) {
            long oldLower = lowerBound;
            long oldUpper = upperBound;
            lowerBound = Math.max(lowerBound, other.lowerBound);
            upperBound = Math.min(upperBound, other.upperBound);
            return oldLower != lowerBound || oldUpper != upperBound;
        }
    }

    private final CiKind kind;
    private final ConstantBound constantBounds;
    private final TypeFeedbackChanged changed;
    private ValueNode dependency;
    private HashMap<ValueNode, Condition> valueBounds;

    private void updateDependency() {
        dependency = changed.node;
    }

    public ScalarTypeFeedbackStore(CiKind kind, TypeFeedbackChanged changed) {
        this.kind = kind;
        if (kind == CiKind.Int) {
            constantBounds = new ConstantBound(Integer.MIN_VALUE, Integer.MAX_VALUE);
        } else if (kind == CiKind.Long) {
            constantBounds = new ConstantBound(Long.MIN_VALUE, Long.MAX_VALUE);
        } else {
            constantBounds = null;
        }
        this.changed = changed;
        this.dependency = null;
    }

    private ScalarTypeFeedbackStore(ScalarTypeFeedbackStore other) {
        this.kind = other.kind;
        if (other.constantBounds == null) {
            constantBounds = null;
        } else {
            constantBounds = new ConstantBound(other.constantBounds.lowerBound, other.constantBounds.upperBound);
        }
        if (other.valueBounds != null && !other.valueBounds.isEmpty()) {
            valueBounds = new HashMap<>(other.valueBounds.size());
            valueBounds.putAll(other.valueBounds);
        }
        this.changed = other.changed;
        this.dependency = other.dependency;
    }

    @Override
    public void constantBound(Condition condition, CiConstant constant) {
        ConstantBound newBound = createBounds(condition, constant);
        if (newBound != null) {
            if (constantBounds.join(newBound)) {
                updateDependency();
            }
        }
    }

    private static ConstantBound createBounds(Condition condition, CiConstant constant) {
        ConstantBound newBound;
        if (constant.kind == CiKind.Int || constant.kind == CiKind.Long) {
            switch (condition) {
                case EQ:
                    newBound = new ConstantBound(constant.asLong(), constant.asLong());
                    break;
                case NE:
                    newBound = null;
                    break;
                case GT:
                    newBound = new ConstantBound(constant.asLong() + 1, Long.MAX_VALUE);
                    break;
                case GE:
                    newBound = new ConstantBound(constant.asLong(), Long.MAX_VALUE);
                    break;
                case LT:
                    newBound = new ConstantBound(Long.MIN_VALUE, constant.asLong() - 1);
                    break;
                case LE:
                    newBound = new ConstantBound(Long.MIN_VALUE, constant.asLong());
                    break;
                default:
                    newBound = null;
                    break;
            }
        } else {
            newBound = null;
        }
        return newBound;
    }

    private void simpleValueBound(Condition condition, ValueNode otherValue) {
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
    public void valueBound(Condition condition, ValueNode otherValue, ScalarTypeQuery type) {
        ScalarTypeFeedbackStore other = type.store();
        switch (condition) {
            case EQ:
                simpleValueBound(condition, otherValue);
                if (constantBounds.join(other.constantBounds)) {
                    updateDependency();
                }
                break;
            case NE:
                simpleValueBound(condition, otherValue);
                break;
            case LE:
            case LT:
                simpleValueBound(condition, otherValue);
                constantBound(condition, new CiConstant(kind, other.constantBounds.upperBound));
                break;
            case GE:
            case GT:
                simpleValueBound(condition, otherValue);
                constantBound(condition, new CiConstant(kind, other.constantBounds.lowerBound));
                break;
            case BT:
                if (other.constantBounds.lowerBound >= 0) {
                    simpleValueBound(Condition.LT, otherValue);
                    constantBound(Condition.GE, new CiConstant(kind, 0));
                    constantBound(Condition.LT, new CiConstant(kind, other.constantBounds.upperBound));
                }
                break;
            case BE:
                if (other.constantBounds.lowerBound >= 0) {
                    simpleValueBound(Condition.LE, otherValue);
                    constantBound(Condition.GE, new CiConstant(kind, 0));
                    constantBound(Condition.LE, new CiConstant(kind, other.constantBounds.upperBound));
                }
                break;
            case AT:
                if (other.constantBounds.upperBound < 0) {
                    simpleValueBound(Condition.GT, otherValue);
                    constantBound(Condition.LT, new CiConstant(kind, 0));
                    constantBound(Condition.GT, new CiConstant(kind, other.constantBounds.lowerBound));
                }
                break;
            case AE:
                if (other.constantBounds.upperBound < 0) {
                    simpleValueBound(Condition.GE, otherValue);
                    constantBound(Condition.LT, new CiConstant(kind, 0));
                    constantBound(Condition.GE, new CiConstant(kind, other.constantBounds.lowerBound));
                }
                break;
        }
    }

    public ScalarTypeQuery query() {
        return new Query(this);
    }

    public static ScalarTypeFeedbackStore meet(ScalarTypeFeedbackStore[] others) {
        boolean emptyValueBounds = false;
        for (int i = 0; i < others.length; i++) {
            if (others[i] == null) {
                return null;
            }
            if (others[i].valueBounds == null || others[i].valueBounds.isEmpty()) {
                emptyValueBounds = true;
            }
        }

        ScalarTypeFeedbackStore first = others[0];
        ScalarTypeFeedbackStore result = new ScalarTypeFeedbackStore(first.kind, first.changed);

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

        result.constantBounds.lowerBound = first.constantBounds.lowerBound;
        result.constantBounds.upperBound = first.constantBounds.upperBound;

        for (int i = 1; i < others.length; i++) {
            result.constantBounds.meet(others[i].constantBounds);
        }
        return result;
    }

    @Override
    public ScalarTypeFeedbackStore clone() {
        return new ScalarTypeFeedbackStore(this);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append('(');
        if (constantBounds.lowerBound == minValue(kind)) {
            str.append('-');
        } else {
            str.append(constantBounds.lowerBound);
        }
        str.append(',');
        if (constantBounds.upperBound == maxValue(kind)) {
            str.append('-');
        } else {
            str.append(constantBounds.upperBound);
        }
        str.append(')');

        if (valueBounds != null) {
            for (Map.Entry<ValueNode, Condition> entry : valueBounds.entrySet()) {
                str.append(", ").append(entry.getValue().operator).append(' ').append(entry.getKey());
            }
        }
        if (dependency != null) {
            str.append(" @ ").append(dependency);
        }
        return str.toString();
    }

    @Override
    public void setTranslated(CiConstant deltaConstant, ScalarTypeQuery old) {
        assert deltaConstant.kind == kind;
        ScalarTypeFeedbackStore other = old.store();
        assert other.kind == kind;
        long lower = other.constantBounds.lowerBound;
        long upper = other.constantBounds.upperBound;
        if (kind == CiKind.Int) {
            int delta = deltaConstant.asInt();
            int newLower = (int) lower + delta;
            int newUpper = (int) upper + delta;
            if ((newLower <= lower && newUpper <= upper) || (newLower > lower && newUpper > upper)) {
                constantBounds.join(new ConstantBound(newLower, newUpper));
            }
        } else if (kind == CiKind.Long) {
            long delta = deltaConstant.asLong();
            long newLower = lower + delta;
            long newUpper = upper + delta;
            if ((newLower <= lower && newUpper <= upper) || (newLower > lower && newUpper > upper)) {
                constantBounds.join(new ConstantBound(newLower, newUpper));
            }
        } else {
            // nothing yet
        }
    }

    public boolean isEmpty() {
        return constantBounds.lowerBound == minValue(kind) && constantBounds.upperBound == maxValue(kind) && (valueBounds == null || valueBounds.isEmpty());
    }

    private static long minValue(CiKind kind) {
        if (kind == CiKind.Int) {
            return Integer.MIN_VALUE;
        } else if (kind == CiKind.Long) {
            return Long.MIN_VALUE;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static long maxValue(CiKind kind) {
        if (kind == CiKind.Int) {
            return Integer.MAX_VALUE;
        } else if (kind == CiKind.Long) {
            return Long.MAX_VALUE;
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
