package com.oracle.svm.hosted.analysis.ai.domain.value;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents an AbstractValue that maps keys to a common abstract domain
 *
 * @param <Key>    type of the Key
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class MapValue<
        Key,
        Domain extends AbstractDomain<Domain>>
        implements AbstractValue<MapValue<Key, Domain>> {

    private final HashMap<Key, Domain> map;
    private final Domain initialDomain;

    public MapValue(Domain initialDomain) {
        this.map = new HashMap<>();
        this.initialDomain = initialDomain;
    }

    public MapValue(MapValue<Key, Domain> other) {
        this.map = new HashMap<>(other.map);
        this.initialDomain = other.initialDomain;
    }

    public MapValue(Map<Key, Domain> other, Domain initialDomain) {
        this.map = new HashMap<>(other);
        this.initialDomain = initialDomain;
    }

    public Map<Key, Domain> getMap() {
        return map;
    }

    @Override
    public AbstractValueKind getKind() {
        return map.isEmpty() ? AbstractValueKind.BOT : AbstractValueKind.VAL;
    }

    @Override
    public boolean leq(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : map.entrySet()) {
            if (!entry.getValue().leq(other.map.getOrDefault(entry.getKey(), AbstractDomain.createTop(initialDomain)))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MapValue<?, ?> mapValue = (MapValue<?, ?>) o;
        return Objects.equals(map, mapValue.map) && Objects.equals(initialDomain, mapValue.initialDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map, initialDomain);
    }

    @Override
    public AbstractValueKind joinWith(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Domain::join);
        }
        return getKind();
    }

    @Override
    public AbstractValueKind widenWith(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Domain::widen);
        }
        return getKind();
    }

    @Override
    public AbstractValueKind meetWith(MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Domain::meet);
        }
        return getKind();
    }

    @Override
    public String toString() {
        if (map.isEmpty()) {
            return "{}";
        }

        return map.entrySet()
                .stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue())
                .collect(Collectors.joining("\n", "{\n", "\n}"));
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public MapValue<Key, Domain> copyOf() {
        return new MapValue<>(map, initialDomain);
    }

    public boolean empty() {
        return map.isEmpty();
    }

    public int getSize() {
        return map.size();
    }

    /* Return TOP value of the {@link AbstractDomain}, when the key is not found */
    public Domain getDomainAtKey(Key key) {
        return map.getOrDefault(key, AbstractDomain.createTop(initialDomain));
    }

    public void insertOrAssign(Key key, Domain value) {
        map.put(key, value);
    }

    public void remove(Key key) {
        map.remove(key);
    }

    public void visit(Function<Map.Entry<Key, Domain>, Void> visitor) {
        for (Map.Entry<Key, Domain> entry : map.entrySet()) {
            visitor.apply(entry);
        }
    }

    public void removeIf(Predicate<Map.Entry<Key, Domain>> predicate) {
        map.entrySet().removeIf(entry -> !predicate.test(entry));
    }

    public void eraseAllMatching(Key key) {
        map.keySet().removeIf(k -> (k.hashCode() & key.hashCode()) != 0);
    }

    public void update(Function<Domain, Domain> operation, Key key) {
        map.put(key, operation.apply(map.get(key)));
    }

    public void transform(Function<Domain, Domain> function) {
        map.replaceAll((k, v) -> function.apply(v));
    }

    public void unionWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
    }

    public void intersectionWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        map.entrySet().removeIf(entry -> !other.map.containsKey(entry.getKey()));
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
    }

    public void differenceWith(BiFunction<Domain, Domain, Domain> combine, MapValue<Key, Domain> other) {
        for (Map.Entry<Key, Domain> entry : other.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), combine);
        }
    }
}
