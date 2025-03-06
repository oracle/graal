package com.oracle.svm.hosted.analysis.ai.value;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Represents an AbstractValue that contains a set of elements
 *
 * @param <Element> type of the elements in the set
 */
public final class SetValue<Element> implements AbstractValue<SetValue<Element>> {

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

    public void remove(Element element) {
        set.remove(element);
    }

    public void removeIf(Predicate<Element> predicate) {
        set.removeIf(predicate.negate());
    }

    public void unionWith(SetValue<Element> other) {
        set.addAll(other.set);
    }

    public void intersectionWith(SetValue<Element> other) {
        set.retainAll(other.set);
    }

    public void differenceWith(SetValue<Element> other) {
        set.removeAll(other.set);
    }
}
