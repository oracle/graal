package com.oracle.svm.hosted.analysis.ai.value;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/*
    Represents a map of elements (variables, memory locations, etc.) to a common abstract domain.
    It is used by MapDomain to map elements to abstract domains.
 */
public final class MapValue<
        Key,
        Domain extends AbstractDomain<Domain>>
        implements AbstractValue<MapValue<Key, Domain>> {
    private final HashMap<Key, Domain> map;
    private final Class<Domain> domainClass;

    public MapValue(Class<Domain> domainClass) {
        this.map = new HashMap<>();
        this.domainClass = domainClass;
    }

    public MapValue(Map<Key, Domain> map, Class<Domain> domainClass) {
        this.map = new HashMap<>(map);
        this.domainClass = domainClass;
    }

    public MapValue(MapValue<Key, Domain> other) {
        this.map = new HashMap<>(other.map);
        this.domainClass = other.domainClass;
    }

    public Map<Key, Domain> getMap() {
        return map;
    }

    @Override
    public AbstractValueKind kind() {
        return map.isEmpty() ? AbstractValueKind.BOT : AbstractValueKind.VAL;
    }

    @Override
    public boolean leq(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : map.entrySet()) {
            if (!entry.getValue().leq(other.map.getOrDefault(entry.getKey(), AbstractDomain.createTop(domainClass)))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(MapValue<Key, Domain> other) {
        return map.equals(other.map);
    }

    @Override
    public AbstractValueKind joinWith(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Domain::join);
        }
        return kind();
    }

    @Override
    public AbstractValueKind widenWith(MapValue<Key, Domain> other) {
        return joinWith(other);
    }

    @Override
    public AbstractValueKind meetWith(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Domain::meet);
        }
        return kind();
    }

    @Override
    public String toString() {
        return "MapValue{" +
                "map=" + map +
                '}';
    }

    @Override
    public void clear() {
        map.clear();
    }

    public boolean empty() {
        return map.isEmpty();
    }

    public int getSize() {
        return map.size();
    }

    public Domain getDomainAtKey(Key key) {
        return map.getOrDefault(key, AbstractDomain.createTop(domainClass));
    }

    public void insertOrAssign(Key key, Domain value) {
        map.put(key, value);
    }

    public MapValue<Key, Domain> remove(Key key) {
        map.remove(key);
        return this;
    }

    public void visit(Function<Map.Entry<Key, Domain>, Void> visitor) {
        for (Map.Entry<Key, Domain> entry : map.entrySet()) {
            visitor.apply(entry);
        }
    }

    public MapValue<Key, Domain> filter(Predicate<Map.Entry<Key, Domain>> predicate) {
        map.entrySet().removeIf(entry -> !predicate.test(entry));
        return this;
    }

    public boolean eraseAllMatching(Key key) {
        return map.keySet().removeIf(k -> (k.hashCode() & key.hashCode()) != 0);
    }

    public MapValue<Key, Domain> update(Function<Domain, Domain> operation, Key key) {
        map.put(key, operation.apply(map.get(key)));
        return this;
    }

    public boolean transform(Function<Domain, Domain> function) {
        for (Map.Entry<Key, Domain> entry : map.entrySet()) {
            map.put(entry.getKey(), function.apply(entry.getValue()));
        }
        return true;
    }

    public MapValue<Key, Domain> unionWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
        return this;
    }

    public MapValue<Key, Domain> intersectionWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        map.entrySet().removeIf(entry -> !other.map.containsKey(entry.getKey()));
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
        return this;
    }

    public MapValue<Key, Domain> differenceWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
        return this;
    }
}