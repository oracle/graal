package com.oracle.svm.hosted.analysis.ai.value;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/*
    Represents a set of elements in the abstract domain.
 */
public final class SetValue<Element>
        implements AbstractValue<SetValue<Element>> {

    private final HashSet<Element> set;

    public SetValue() {
        this.set = new HashSet<>();
    }

    public SetValue(Set<Element> set) {
        this.set = new HashSet<>(set);
    }

    public SetValue(SetValue<Element> other) {
        this.set = new HashSet<>(other.set);
    }

    public Set<Element> getSet() {
        return set;
    }

    @Override
    public AbstractValueKind getKind() {
        return set.isEmpty() ? AbstractValueKind.BOT : AbstractValueKind.VAL;
    }

    @Override
    public boolean leq(SetValue<Element> other) {
        return other.set.containsAll(set);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SetValue<?> setValue = (SetValue<?>) o;
        return Objects.equals(set, setValue.set);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(set);
    }

    @Override
    public AbstractValueKind joinWith(SetValue<Element> other) {
        set.addAll(other.set);
        return getKind();
    }

    @Override
    public AbstractValueKind widenWith(SetValue<Element> other) {
        return joinWith(other);
    }

    @Override
    public AbstractValueKind meetWith(SetValue<Element> other) {
        set.retainAll(other.set);
        return getKind();
    }

    @Override
    public String toString() {
        return "SetValue{" +
                "set=" + set +
                '}';
    }

    @Override
    public void clear() {
        set.clear();
    }


    @Override
    public SetValue<Element> copyOf() {
        return new SetValue<>(this);
    }

    public boolean empty() {
        return set.isEmpty();
    }

    public int getSize() {
        return set.size();
    }

    public void add(Element element) {
        set.add(element);
    }

    public SetValue<Element> remove(Element element) {
        set.remove(element);
        return this;
    }

    public SetValue<Element> filter(Predicate<Element> predicate) {
        set.removeIf(predicate.negate());
        return this;
    }

    public SetValue<Element> unionWith(SetValue<Element> other) {
        set.addAll(other.set);
        return this;
    }

    public SetValue<Element> intersectionWith(SetValue<Element> other) {
        set.retainAll(other.set);
        return this;
    }

    public SetValue<Element> differenceWith(SetValue<Element> other) {
        set.removeAll(other.set);
        return this;
    }
}