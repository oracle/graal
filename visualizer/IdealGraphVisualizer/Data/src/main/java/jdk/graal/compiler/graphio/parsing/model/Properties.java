/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.graphio.parsing.model;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class Properties implements Iterable<Property<Object>> {

    /**
     * Array implementation of Properties when memory and access of properties is concern. Each
     * property increases addition time as all properties are checked for equality. This class has
     * O(1) access time via index.
     */
    public static class ArrayProperties extends Properties {
        protected String[] names;
        protected Object[] values;
        protected int size = 0;

        public ArrayProperties(int expectedSize) {
            names = new String[expectedSize];
            values = new Object[expectedSize];
        }

        public ArrayProperties() {
            this(0);
        }

        protected ArrayProperties(String[] names, Object[] values) {
            assert values.length == names.length;
            this.values = values;
            this.names = names;
            this.size = names.length;
        }

        @Override
        public void reserve(int capacity) {
            if (capacity > names.length) {
                names = Arrays.copyOf(names, capacity);
                values = Arrays.copyOf(values, capacity);
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean containsName(String name) {
            return getKeyIndex(name) != -1;
        }

        @Override
        public Object get(String name) {
            int i = getKeyIndex(name);
            return i == -1 ? null : values[i];
        }

        private int getKeyIndex(String name) {
            for (int i = 0; i < size; ++i) {
                if (names[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Map<String, Object> toMap(Map<String, Object> props, Set<String> excludeNames, String... excludePrefixes) {
            Set<String> excludes = excludeNames;
            if (excludes == null) {
                excludes = Collections.emptySet();
            }
            if (excludes.isEmpty() && excludePrefixes.length == 0) {
                for (int i = 0; i < size; ++i) {
                    props.put(names[i], values[i]);
                }
            } else {
                P: for (int i = 0; i < size; ++i) {
                    String n = names[i];
                    if (!excludes.contains(n)) {
                        for (String p : excludePrefixes) {
                            if (n.startsWith(p)) {
                                continue P;
                            }
                        }
                        props.put(names[i], values[i]);
                    }
                }
            }
            return props;
        }

        @Override
        public Object remove(String name) {
            return removeAt(getKeyIndex(name));
        }

        @Override
        protected Object removeAt(int index) {
            if (index < 0 || index >= size) {
                return null;
            }
            Object obj = values[index];
            Object[] newVals = new Object[size - 1];
            String[] newKeys = new String[size - 1];
            System.arraycopy(names, 0, newKeys, 0, index);
            System.arraycopy(values, 0, newVals, 0, index);
            int remIndex = index + 1;
            System.arraycopy(names, remIndex, newKeys, remIndex - 1, size - remIndex);
            System.arraycopy(values, remIndex, newVals, remIndex - 1, size - remIndex);
            names = newKeys;
            values = newVals;
            --size;
            return obj;
        }

        @Override
        public void clear() {
            values = new Object[0];
            names = new String[0];
            size = 0;
        }

        @Override
        public Property<Object> atIndex(int index) {
            if (index >= size || index < 0) {
                throw new IndexOutOfBoundsException();
            }
            return new Property<>(names[index], values[index]);
        }

        @Override
        protected void setPropertyInternal(String name, Object value) {
            assert name != null;
            int i = getKeyIndex(name);
            if (i != -1) {
                values[i] = value;
                return;
            }
            if (names.length == size) {
                names = Arrays.copyOf(names, size + 1);
                values = Arrays.copyOf(values, size + 1);
            }
            names[size] = name;
            values[size] = value;
            ++size;
        }

        @Override
        <TSource extends Iterable<? extends TProp>, TProp> void addFrom(TSource source, Function<? super TProp, String> getNameFunc, Function<? super TProp, ?> getValueFunc) {
            List<TProp> rest = new ArrayList<>();
            for (TProp p : source) {
                int index = getKeyIndex(getNameFunc.apply(p));
                if (index == -1) {
                    rest.add(p);
                } else {
                    values[index] = getValueFunc.apply(p);
                }
            }
            if (!rest.isEmpty()) {
                names = Arrays.copyOf(names, size + rest.size());
                values = Arrays.copyOf(values, size + rest.size());
                for (TProp p : rest) {
                    names[size] = getNameFunc.apply(p);
                    values[size] = getValueFunc.apply(p);
                    ++size;
                }
            }
        }

        private final class ArrayPropertiesIterator implements Iterator<Property<Object>> {
            int index;

            @Override
            public boolean hasNext() {
                return index < size();
            }

            @Override
            public Property<Object> next() {
                if (index < size()) {
                    ++index;
                    return atIndex(index - 1);
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Not supported.");
            }
        }

        @Override
        public Iterator<Property<Object>> iterator() {
            return new ArrayPropertiesIterator();
        }

        private class ArrayPropertiesTypedIterator<T> implements Iterator<Property<T>> {
            int index;
            Class<T> clazz;

            ArrayPropertiesTypedIterator(Class<T> clazz) {
                this.clazz = clazz;
            }

            @Override
            public boolean hasNext() {
                while (index < size && !clazz.isInstance(values[index])) {
                    ++index;
                }
                return index < size;
            }

            @Override
            public Property<T> next() {
                if (hasNext()) {
                    ++index;
                    return new Property<>(names[index - 1], clazz.cast(values[index - 1]));
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Not supported.");
            }
        }

        @Override
        public <T> Iterable<Property<T>> typedIter(Class<T> clazz) {
            if (clazz == null) {
                throw new IllegalArgumentException("Class<T> can't be null.");
            }
            return () -> new ArrayPropertiesTypedIterator<>(clazz);
        }
    }

    /**
     * LinkedHashMap implementation of Properties to be used when the speed of addition is concern.
     * LinkedHashMap makes greater use of memory, but allows for O(1) check of key inclusion. Access
     * time by index is O(n).
     */
    public static class LinkedHashMapProperties extends Properties {
        protected LinkedHashMap<String, Object> map;

        public LinkedHashMapProperties() {
            this(0);
        }

        public LinkedHashMapProperties(Properties p) {
            this(p.size());
            for (Property<?> prop : p) {
                map.put(prop.getName(), prop.getValue());
            }
        }

        public LinkedHashMapProperties(int expectedSize) {
            map = new LinkedHashMap<>(expectedSize);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public Property<?> atIndex(int index) {
            if (index >= size() || index < 0) {
                throw new IndexOutOfBoundsException();
            }
            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (i == index) {
                    return new Property<>(entry.getKey(), entry.getValue());
                } else {
                    ++i;
                }
            }
            return null;
        }

        @Override
        public void reserve(int size) {
        }

        @Override
        public Map<String, Object> toMap(Map<String, Object> props, Set<String> excludeNames, String... excludePrefixes) {
            Set<String> excludes = excludeNames;
            if (excludes == null) {
                excludes = Collections.emptySet();
            }
            if (excludes.isEmpty() && excludePrefixes.length == 0) {
                props.putAll(map);
            } else {
                P: for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String n = entry.getKey();
                    if (!excludes.contains(n)) {
                        for (String p : excludePrefixes) {
                            if (n.startsWith(p)) {
                                continue P;
                            }
                        }
                        props.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            return props;
        }

        @Override
        public boolean containsName(String name) {
            return map.containsKey(name);
        }

        @Override
        public Object remove(String name) {
            return map.remove(name);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public Object get(String name) {
            return map.get(name);
        }

        @Override
        protected void setPropertyInternal(String name, Object value) {
            map.put(name, value);
        }

        @Override
        <TSource extends Iterable<? extends TProp>, TProp> void addFrom(TSource source, Function<? super TProp, String> getNameFunc, Function<? super TProp, ?> getValueFunc) {
            for (TProp p : source) {
                String name = getNameFunc.apply(p);
                Object value = getValueFunc.apply(p);
                map.put(name, value);
            }
        }

        @Override
        public <T> Iterable<Property<T>> typedIter(Class<T> clazz) {
            if (clazz == null) {
                throw new IllegalArgumentException("Class<T> can't be null.");
            }
            return () -> map.entrySet().stream().filter(entry -> clazz.isInstance(entry.getValue())).map(entry -> new Property<>(entry.getKey(), clazz.cast(entry.getValue()))).iterator();
        }

        @Override
        public Iterator<Property<Object>> iterator() {
            return map.entrySet().stream().map(entry -> new Property<>(entry.getKey(), entry.getValue())).iterator();
        }
    }

    public static Properties immutableEmpty() {
        return SharedProperties.EMPTY;
    }

    /**
     * Factory method to be used when no specific implementation is needed. This allows to change
     * preferred used implementation.
     *
     * @return new Properties
     */
    public static Properties newProperties() {
        return new LinkedHashMapProperties();
    }

    public static Properties newProperties(Properties p) {
        return new LinkedHashMapProperties(p);
    }

    public static Properties newProperties(String name, Object value) {
        Properties p = new LinkedHashMapProperties(1);
        p.setProperty(name, value);
        return p;
    }

    public static Properties newProperties(String name, Object value, String name1, Object value1) {
        Properties p = new LinkedHashMapProperties(2);
        p.setProperty(name, value);
        p.setProperty(name1, value1);
        return p;
    }

    public static Properties newProperties(String name, Object value, String name1, Object value1, String name2, Object value2) {
        Properties p = new LinkedHashMapProperties(3);
        p.setProperty(name, value);
        p.setProperty(name1, value1);
        p.setProperty(name2, value2);
        return p;
    }

    /**
     * @return number of properties
     */
    public abstract int size();

    public abstract Property<?> atIndex(int index);

    public abstract void reserve(int size);

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Properties)) {
            return false;
        }

        Properties p = (Properties) o;
        if (this.size() != p.size()) {
            // different number of properties
            return false;
        }

        for (Property<?> prop : this) {
            Object value = p.get(prop.getName());
            if (!Objects.deepEquals(value, prop.getValue())) {
                return false;
            }
        }

        return true;
    }

    protected final int makeHash() {
        int hash = 5;
        for (Property<?> prop : this) {
            hash = hash ^ (Property.makeHash(prop.getName(), prop.getValue())); // position affected
                                                                                // hash would
                                                                                // violate
                                                                                // equal/hash
                                                                                // contract
        }
        return hash;
    }

    @Override
    public int hashCode() {
        return makeHash();
    }

    public abstract Map<String, Object> toMap(Map<String, Object> props, Set<String> excludes, String... excludePrefixes);

    public Map<String, Object> toMap() {
        return toMap(null);
    }

    public Map<String, Object> toMap(Map<String, Object> props) {
        return toMap(props, null);
    }

    public Map<String, Object> toMap(Set<String> excludes, String... excludePrefixes) {
        return toMap(new LinkedHashMap<>(), excludes, excludePrefixes);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public abstract boolean containsName(String name);

    public boolean containsValue(Object value) {
        for (Property<?> prop : this) {
            if (Objects.deepEquals(prop.getValue(), value)) {
                return true;
            }
        }
        return false;
    }

    public abstract Object remove(String name);

    protected Object removeAt(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        return remove(atIndex(index).getName());
    }

    public abstract void clear();

    static class SharedProperties extends ArrayProperties {
        static final Properties EMPTY = new SharedProperties(new ArrayProperties());

        final int hashCode;

        SharedProperties(Properties p) {
            super(p.size());
            for (Property<?> prop : p) {
                super.setPropertyInternal(prop.getName(), prop.getValue());
            }
            this.hashCode = makeHash();
        }

        @Override
        protected void setPropertyInternal(String name, Object value) {
            throw new UnsupportedOperationException("SharedProperties are immutable.");
        }

        @Override
        protected Object removeAt(int index) {
            throw new UnsupportedOperationException("SharedProperties are immutable.");
        }

        @Override
        public void add(Properties properties) {
            throw new UnsupportedOperationException("SharedProperties are immutable.");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("SharedProperties are immutable.");
        }

        @Override
        public void putAll(Map<String, ?> m) {
            throw new UnsupportedOperationException("SharedProperties are immutable.");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class PropertyCache {
        static final WeakHashMap</* Shared */ Properties, WeakReference<SharedProperties>> immutableCache = new WeakHashMap<>();

        static synchronized SharedProperties intern(Properties properties) {
            WeakReference<SharedProperties> entry = immutableCache.get(properties);
            if (entry != null) {
                SharedProperties props = entry.get();
                if (props != null) {
                    return props;
                }
            }
            SharedProperties key = new SharedProperties(properties);
            immutableCache.put(key, new WeakReference<>(key));
            return key;
        }
    }

    public interface MutableOwner<T> extends Provider {
        ChangedEvent<T> getPropertyChangedEvent();

        Properties writableProperties();

        void updateProperties(Properties props);
    }

    public static class Entity implements Provider {
        private Properties properties;

        public Entity() {
            properties = newProperties();
        }

        public Entity(Properties.Entity object) {
            this(object.getProperties());
        }

        public Entity(Properties newProperties) {
            properties = newProperties(newProperties);
        }

        @Override
        public synchronized Properties getProperties() {
            return properties;
        }

        public void internProperties() {
            properties = PropertyCache.intern(properties);
        }

        protected final void freezeProperties() {
            properties = new SharedProperties(properties);
        }

        public boolean isMutable() {
            return !(properties instanceof SharedProperties);
        }

        protected synchronized void replaceProperties(Properties props) {
            this.properties = props;
        }
    }

    public interface PropertyMatcher {
        PropertyMatcher ALL = new PropertyMatcher() {
            @Override
            public String getName() {
                return "ALL";
            }

            @Override
            public boolean match(Object value) {
                return true;
            }

            @Override
            public Property<?> matchProperties(Properties p) {
                Iterator<Property<Object>> i = p.iterator();
                return i.hasNext() ? i.next() : null;
            }
        };

        String getName();

        boolean match(Object value);

        default Property<?> matchProperties(Properties p) {
            String name = getName();
            Object value = p.get(name);
            if (match(value)) {
                return new Property<>(name, value);
            }
            return null;
        }
    }

    public static class InvertPropertyMatcher implements PropertyMatcher {
        private final PropertyMatcher matcher;

        public InvertPropertyMatcher(PropertyMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public String getName() {
            return matcher.getName();
        }

        @Override
        public boolean match(Object p) {
            return !matcher.match(p);
        }
    }

    public static class EqualityPropertyMatcher implements PropertyMatcher {
        private final String name;
        private final Object value;

        public EqualityPropertyMatcher(String name, Object value) {
            if (name == null) {
                throw new IllegalArgumentException("Property name must not be null!");
            }
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean match(Object p) {
            return Objects.deepEquals(p, value);
        }
    }

    public static class RegexpPropertyMatcher implements PropertyMatcher {
        private final String name;
        private final Pattern valuePattern;
        private final boolean entireMatch;

        public RegexpPropertyMatcher(String name, String value) {
            this(name, value, true, 0);
        }

        /**
         * Constructs a regular expression based matcher.
         *
         * @param name name of the property to search
         * @param value pattern
         * @param entireMatch whether the matcher should only accept full matches
         * @param flags flags to use to compile the pattern defined by {@code value}
         */
        public RegexpPropertyMatcher(String name, String value, boolean entireMatch, int flags) {
            if (name == null) {
                throw new IllegalArgumentException("Property name must not be null!");
            }

            if (value == null) {
                throw new IllegalArgumentException("Property value pattern must not be null!");
            }

            this.name = name;
            this.entireMatch = entireMatch;

            try {
                valuePattern = Pattern.compile(value, flags);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Bad pattern: " + value);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean match(Object p) {
            String s;
            if (p == null) {
                return false;
            }
            s = p.toString();
            Matcher m = valuePattern.matcher(s);
            return entireMatch ? m.matches() : m.find();
        }

        public String getRegexpValue() {
            return valuePattern.pattern();
        }
    }

    public Property<?> selectSingle(PropertyMatcher matcher) {
        return matcher.matchProperties(this);
    }

    public interface Provider {
        Properties getProperties();
    }

    @Override
    public String toString() {
        Integer[] indexes = new Integer[size()];
        for (int i = 0; i < indexes.length; ++i) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, (Integer v1, Integer v2) -> atIndex(v1).getName().compareTo(atIndex(v2).getName()));

        StringBuilder sb = new StringBuilder("[");
        for (Integer i : indexes) {
            Property<?> p = atIndex(i);
            sb.append(p.toString()).append(", ");
        }
        sb.setLength(sb.length() == 1 ? 1 : sb.length() - 2);

        return sb.append("]").toString();
    }

    public static class PropertySelector<T extends Properties.Provider> {
        private final Collection<T> objects;

        public PropertySelector(Collection<T> objects) {
            this.objects = objects;
        }

        public T selectSingle(PropertyMatcher matcher) {
            for (T t : objects) {
                Property<?> p = t.getProperties().selectSingle(matcher);
                if (p != null) {
                    return t;
                }
            }

            return null;
        }

        public List<T> selectMultiple(PropertyMatcher matcher) {
            List<T> result = new ArrayList<>();

            for (T t : objects) {
                Property<?> p = t.getProperties().selectSingle(matcher);
                if (p != null) {
                    result.add(t);
                }
            }

            return result;
        }
    }

    public abstract Object get(String name);

    public <T> T get(String key, Class<T> clazz) {
        Object tmp = get(key);
        assert tmp == null || clazz.isInstance(tmp) : "Property value is of different class: " + tmp.getClass().getName();
        return clazz.cast(tmp);
    }

    /**
     * Returns String the default representation of the object. Returns the value of
     * {@link Objects#toString(java.lang.Object)} for the value of the property. If the property is
     * not defined or is {@code null}, returns the value of {@code defValue} parameter.
     * <p/>
     * For better compatibility, use this method if you convert the result to String anyway, since
     * {@link #get(java.lang.String, java.lang.Class)} will throw an exception/assertion on
     * non-String values.
     *
     * @param key property key
     * @param defValue value to be returned if the property is not defined or is {@code null}
     * @return String representation
     */
    public String getString(String key, String defValue) {
        Object val = get(key);
        return val == null ? defValue : Objects.toString(val);
    }

    public void setProperty(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Name can't be null.");
        }
        setPropertyInternal(name, value);
    }

    protected abstract void setPropertyInternal(String name, Object value);

    public void putAll(Map<String, ?> map) {
        addFrom(map.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
    }

    public void add(Properties properties) {
        addFrom(properties, Property::getName, Property::getValue);
    }

    abstract <TSource extends Iterable<? extends TProp>, TProp> void addFrom(TSource source, Function<? super TProp, String> getNameFunc, Function<? super TProp, ?> getValueFunc);

    public abstract <T> Iterable<Property<T>> typedIter(Class<T> clazz);
}
