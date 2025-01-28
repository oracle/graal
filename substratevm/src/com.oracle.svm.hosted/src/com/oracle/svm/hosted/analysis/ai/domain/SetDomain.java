package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.SetValue;

/*
    This abstract domain represents a set of elements.
    For example the domain could represent set of live variables
 */
public final class SetDomain<Element> extends LatticeDomain<SetValue<Element>, SetDomain<Element>> {

    public SetDomain() {
        super(SetValue::new);
    }

    public SetDomain(AbstractValueKind kind) throws IllegalAccessException {
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

    @Override
    public String toString() {
        return "SetDomain{" +
                "setValue=" + getValue() +
                '}';
    }

    @Override
    public SetDomain<Element> copyOf() {
        return new SetDomain<>(this);
    }
}