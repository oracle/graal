package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.MapValue;


/*
    This abstract domain maps elements (variables, memory locations, etc.) to a common
    abstract domain.
    One example could be mapping variables to intervals, signs, etc.

    We do not represent TOP values in this domain, to minimize the size of the used memory,
    if a Key value is not present inside the map, we return TOP
 */
public final class MapDomain<
        Key,
        Domain extends AbstractDomain<Domain>>
        extends LatticeDomain<MapValue<Key, Domain>, MapDomain<Key, Domain>> {
    private final Class<Domain> domainClass;

    public MapDomain(Class<Domain> domainClass) {
        super(() -> new MapValue<>(domainClass));
        this.domainClass = domainClass;
    }

    public MapDomain(AbstractValueKind kind,
                     Class<Domain> domainClass) throws IllegalAccessException {
        super(kind, () -> new MapValue<>(domainClass));
        this.domainClass = domainClass;
    }

    public MapDomain(MapValue<Key, Domain> mapValue,
                     Class<Domain> domainClass) {
        super(() -> mapValue);
        this.domainClass = domainClass;
    }

    public MapDomain(MapDomain<Key, Domain> other) {
        super(() -> new MapValue<>(other.getValue()));
        this.domainClass = other.domainClass;
        this.kind = other.kind;
    }

    public Domain get(Key key) {
        return getValue().getDomainAtKey(key);
    }

    public void put(Key key, Domain value) {
        getValue().insertOrAssign(key, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return "MapDomain{" +
                "mapValue=" + getValue() +
                '}';
    }

    @Override
    public MapDomain<Key, Domain> copyOf() {
        return new MapDomain<>(this);
    }
}