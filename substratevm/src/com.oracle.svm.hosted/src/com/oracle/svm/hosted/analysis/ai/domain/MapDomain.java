package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.MapValue;
import jdk.vm.ci.meta.Value;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This abstract domain maps elements (variables, memory locations, etc.) to a common
 * abstract domain.
 * One example could be mapping variables to intervals, signs, etc.
 * In order to minimize the size of the used Map,
 * if a Key is not present, we return TOP value of the {@link AbstractDomain}
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

    public MapDomain(Map<Key, Domain> map, Domain initialDomain) {
        super(() -> new MapValue<>(initialDomain));
        this.initialDomain = initialDomain.copyOf();
        map.forEach(this::put);
    }

    public MapDomain(MapDomain<Key, Domain> other) {
        super(() -> new MapValue<>(other.getValue()));
        this.initialDomain = other.initialDomain.copyOf();
    }

    public Domain get(Key key) {
        return getValue().getDomainAtKey(key);
    }

    public void filter(Predicate<Map.Entry<Key, Domain>> predicate) {
        getValue().filter(predicate);
        updateKind();
    }

    public void transform(Function<Domain, Domain> function) {
        getValue().transform(function);
        updateKind();
    }

    public void update(Function<Domain, Domain> function, Key key) {
        getValue().update(function, key);
        updateKind();
    }

    public void put(Key key, Domain value) {
        getValue().insertOrAssign(key, value);
        updateKind();
    }

    public int getSize() {
        return getValue().getSize();
    }

    @Override
    public String toString() {
        return "MapDomain{" +
                "mapValue=" + getValue() +
                ", kind=" + getKind() +
                '}';
    }

    @Override
    public MapDomain<Key, Domain> copyOf() {
        return new MapDomain<>(this);
    }
}