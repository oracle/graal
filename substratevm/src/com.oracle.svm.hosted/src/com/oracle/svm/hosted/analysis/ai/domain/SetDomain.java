package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.domain.value.SetValue;

import java.util.Set;
import java.util.function.Predicate;

/*
    This abstract domain represents a set of elements.
    For example the domain could represent set of live variables
 */
public final class SetDomain<Element> extends LatticeDomain<SetValue<Element>, SetDomain<Element>> {

    public SetDomain() {
        super(SetValue::new);
    }

    public SetDomain(AbstractValueKind kind) {
        super(kind, SetValue::new);
    }

    public SetDomain(SetValue<Element> setValue) {
        super(() -> setValue);
    }

    public SetDomain(SetDomain<Element> other) {
        super(() -> new SetValue<>(other.getValue()));
    }

    public void add(Element element) {
        getValue().add(element);
        updateKind();
    }

    public void remove(Element element) {
        getValue().remove(element);
        updateKind();
    }

    public void removeIf(Predicate<Element> predicate) {
        getValue().getSet().removeIf(predicate);
        updateKind();
    }

    public void clear() {
        getValue().clear();
        updateKind();
    }

    public boolean empty() {
        return getValue().empty();
    }

    public int getSize() {
        return getValue().getSize();
    }

    public Set<Element> getSet() {
        return getValue().getSet();
    }

    public void filter(Predicate<Element> predicate) {
        getValue().removeIf(predicate);
        updateKind();
    }

    public void unionWith(SetDomain<Element> other) {
        getValue().unionWith(other.getValue());
        updateKind();
    }

    public void intersectionWith(SetDomain<Element> other) {
        getValue().intersectionWith(other.getValue());
        updateKind();
    }

    public void differenceWith(SetDomain<Element> other) {
        getValue().differenceWith(other.getValue());
        updateKind();
    }

    @Override
    public String toString() {
        return getValue().toString();
    }

    @Override
    public SetDomain<Element> copyOf() {
        return new SetDomain<>(this);
    }
}
