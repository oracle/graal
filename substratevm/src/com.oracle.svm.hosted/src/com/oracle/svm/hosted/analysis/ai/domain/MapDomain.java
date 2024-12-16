package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.MapValue;

/**
 * This abstract domain maps elements (variables, memory locations, etc.) to a common
 * abstract domain.
 * One example could be mapping variables to intervals, signs, etc.
 * We do not represent AbstractValueKind.TOP values in this domain, to minimize the size of the used memory,
 * if a Key is not present inside the map, we return TOP
 */
public final class MapDomain<
        Key,
        Domain extends AbstractDomain<Domain>>
        extends LatticeDomain<MapValue<Key, Domain>, MapDomain<Key, Domain>> {

    private final Domain initialDomain;

    public MapDomain(Domain initialDomain) {
        super(() -> new MapValue<>(initialDomain));
        this.initialDomain = initialDomain;
    }

    public MapDomain(AbstractValueKind kind,
                     Domain initialDomain) {
        super(kind, () -> new MapValue<>(initialDomain));
        this.initialDomain = initialDomain;
    }

    public MapDomain(MapValue<Key, Domain> mapValue, Domain initialDomain) {
        super(() -> mapValue);
        this.initialDomain = initialDomain;
    }

    public MapDomain(MapDomain<Key, Domain> other) {
        super(() -> new MapValue<>(other.initialDomain));
        this.initialDomain = other.initialDomain.copyOf();
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
        return "MapDomain{" +
                "mapValue=" + getValue() +
                '}';
    }

    @Override
    public MapDomain<Key, Domain> copyOf() {
        return new MapDomain<>(this);
    }
}