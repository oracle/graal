package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.ConstantValue;

/**
 * Abstract domain for flat lattice, also known as 3 level lattice
 *              T
 *            / | \
 *      ... -1  0  1 ...
 *            \ | /
 *             _|_
 *
 * @param <Value> the type of derived AbstractValue
 */

public class ConstantDomain<
        Value extends ConstantValue<Value>>
        extends AbstractDomain<ConstantDomain<Value>> {
    private AbstractValueKind kind;
    private Value value;

    public ConstantDomain() {
        kind = AbstractValueKind.BOT;
    }

    public ConstantDomain(Value value) {
        this.value = value;
        this.kind = AbstractValueKind.VAL;
    }

    public ConstantDomain(AbstractValueKind kind) {
        this.kind = kind;
        if (kind != AbstractValueKind.VAL) {
            throw new RuntimeException("Invalid abstract kind");
        }
    }

    @Override
    protected ConstantDomain<Value> copyOf() {
        return null;
    }

    @Override
    public boolean isBot() {
        return kind == AbstractValueKind.BOT;
    }

    @Override
    public boolean isTop() {
        return kind == AbstractValueKind.TOP;
    }

    @Override
    public boolean leq(ConstantDomain<Value> other) {
        if (isBot()) {
            return true;
        }
        if (other.isBot()) {
            return false;
        }
        if (other.isTop()) {
            return true;
        }
        if (isTop()) {
            return false;
        }
        return value.getConstant() == other.value.getConstant();
    }

    @Override
    public boolean equals(ConstantDomain<Value> other) {
        if (isBot()) {
            return other.isBot();
        }
        if (isTop()) {
            return other.isTop();
        }
        if (!other.isVal()) {
            return false;
        }
        return value.getConstant() == other.value.getConstant();
    }

    @Override
    public void setToBot() {
        kind = AbstractValueKind.BOT;
    }

    @Override
    public void setToTop() {
        kind = AbstractValueKind.TOP;
    }

    @Override
    public void joinWith(ConstantDomain<Value> other) {
        if (isTop() || other.isBot()) {
            return;
        }
        if (other.isTop()) {
            setToTop();
            return;
        }
        if (isBot()) {
            value = other.value;
            return;
        }

        kind = value.getConstant() == other.value.getConstant() ? AbstractValueKind.VAL : AbstractValueKind.TOP;
    }

    @Override
    public void widenWith(ConstantDomain<Value> other) {
        joinWith(other);
    }

    @Override
    public void meetWith(ConstantDomain<Value> other) {
        if (isTop() || other.isBot()) {
            return;
        }
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            value = other.value;
            return;
        }

        kind = value.getConstant() == other.value.getConstant() ? AbstractValueKind.VAL : AbstractValueKind.BOT;
    }

    private boolean isVal() {
        return kind == AbstractValueKind.VAL;
    }
}
