/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.object.Property;

/**
 * Implementation of {@link PropertyMap} as a reverse-order cons (snoc) list.
 */
final class ConsListPropertyMap extends PropertyMap {
    private final ConsListPropertyMap car;
    private final Property cdr;
    private final int size;

    private static final ConsListPropertyMap EMPTY = new ConsListPropertyMap();

    private ConsListPropertyMap() {
        this.car = null;
        this.cdr = null;
        this.size = 0;
    }

    private ConsListPropertyMap(ConsListPropertyMap parent, Property added) {
        this.car = Objects.requireNonNull(parent);
        this.cdr = Objects.requireNonNull(added);
        this.size = parent.size + 1;
    }

    public static ConsListPropertyMap empty() {
        return EMPTY;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(Object key) {
        for (Map.Entry<Object, Property> entry : reverseOrderEntrySet()) {
            if (entry.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsValue(Object value) {
        for (Map.Entry<Object, Property> entry : reverseOrderEntrySet()) {
            if (entry.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public Property get(Object key) {
        if (key == null || isEmpty()) {
            return null;
        } else if (key instanceof String) {
            return getStringKey((String) key);
        } else {
            return getEquals(key);
        }
    }

    private Property getEquals(Object key) {
        for (ConsListPropertyMap current = this; !current.isEmpty(); current = current.getParentMap()) {
            Property p = current.getLastProperty();
            Object pKey = p.getKey();
            if (pKey == key || pKey.equals(key)) {
                return p;
            }
        }
        return null;
    }

    private Property getStringKey(String key) {
        for (ConsListPropertyMap current = this; !current.isEmpty(); current = current.getParentMap()) {
            Property p = current.getLastProperty();
            Object pKey = p.getKey();
            if (pKey == key || (pKey instanceof String && ((String) pKey).equals(key))) {
                return p;
            }
        }
        return null;
    }

    public Set<Object> keySet() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return ConsListPropertyMap.this.orderedKeyIterator();
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    public Collection<Property> values() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                return ConsListPropertyMap.this.orderedValueIterator();
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    public Set<Map.Entry<Object, Property>> entrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @SuppressWarnings("unchecked")
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                Map.Entry<Object, Property>[] entries = (Map.Entry<Object, Property>[]) new Map.Entry<?, ?>[size()];
                Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
                for (int pos = size() - 1; pos >= 0; pos--) {
                    entries[pos] = iterator.next();
                }
                return Arrays.asList(entries).iterator();
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    public Set<Map.Entry<Object, Property>> reverseOrderEntrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                return new Iterator<Map.Entry<Object, Property>>() {
                    ConsListPropertyMap current = ConsListPropertyMap.this;

                    public Entry<Object, Property> next() {
                        if (hasNext()) {
                            try {
                                return new MapEntryImpl(current.cdr);
                            } finally {
                                current = current.car;
                            }
                        } else {
                            throw new NoSuchElementException();
                        }
                    }

                    public boolean hasNext() {
                        return current != empty();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    @Override
    public Iterator<Object> orderedKeyIterator() {
        Object[] keys = new Object[size()];
        Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
        for (int pos = size() - 1; pos >= 0; pos--) {
            keys[pos] = iterator.next().getKey();
        }
        return Arrays.asList(keys).iterator();
    }

    @Override
    public Iterator<Object> reverseOrderedKeyIterator() {
        return reverseOrderKeys().iterator();
    }

    public Set<Object> reverseOrderKeys() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {
                    ConsListPropertyMap current = ConsListPropertyMap.this;

                    public Object next() {
                        if (hasNext()) {
                            try {
                                return current.cdr.getKey();
                            } finally {
                                current = current.car;
                            }
                        } else {
                            throw new NoSuchElementException();
                        }
                    }

                    public boolean hasNext() {
                        return current != empty();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    @Override
    public Iterator<Property> orderedValueIterator() {
        Property[] values = new Property[size()];
        Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
        for (int pos = size() - 1; pos >= 0; pos--) {
            values[pos] = iterator.next().getValue();
        }
        return Arrays.asList(values).iterator();
    }

    @Override
    public Iterator<Property> reverseOrderedValueIterator() {
        return reverseOrderValues().iterator();
    }

    public Set<Property> reverseOrderValues() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                return new Iterator<Property>() {
                    ConsListPropertyMap current = ConsListPropertyMap.this;

                    public Property next() {
                        if (hasNext()) {
                            try {
                                return current.cdr;
                            } finally {
                                current = current.car;
                            }
                        } else {
                            throw new NoSuchElementException();
                        }
                    }

                    public boolean hasNext() {
                        return current != empty();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return ConsListPropertyMap.this.size();
            }
        };
    }

    private static final class MapEntryImpl implements Map.Entry<Object, Property> {
        private final Property backingProperty;

        MapEntryImpl(Property backingProperty) {
            this.backingProperty = backingProperty;
        }

        public Object getKey() {
            return backingProperty.getKey();
        }

        public Property getValue() {
            return backingProperty;
        }

        public Property setValue(Property value) {
            throw unmodifiableException();
        }
    }

    public PropertyMap copyAndPut(Object key, Property value) {
        if (!value.getKey().equals(key)) {
            throw new IllegalArgumentException("Key must equal extracted key of property.");
        }

        Property oldValue = get(key);
        if (oldValue != null) {
            return replaceCopy(oldValue, value);
        }
        return putCopy(value);
    }

    public ImmutableMap<Object, Property> copyAndRemove(Object key) {
        Deque<Property> shelve = new ArrayDeque<>();
        ConsListPropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().getKey().equals(key)) {
                ConsListPropertyMap newMap = current.getParentMap();
                for (Property property : shelve) {
                    newMap = newMap.putCopy(property);
                }
                return newMap;
            } else {
                shelve.push(current.getLastProperty());
                current = current.getParentMap();
            }
        }
        return this;
    }

    @Override
    public ConsListPropertyMap putCopy(Property value) {
        return new ConsListPropertyMap(this, value);
    }

    @Override
    public ConsListPropertyMap removeCopy(Property value) {
        Deque<Property> shelve = new ArrayDeque<>();
        ConsListPropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(value)) {
                ConsListPropertyMap newMap = current.getParentMap();
                for (Property property : shelve) {
                    newMap = newMap.putCopy(property);
                }
                return newMap;
            } else {
                shelve.push(current.getLastProperty());
                current = current.getParentMap();
            }
        }
        return this;
    }

    @Override
    public ConsListPropertyMap replaceCopy(Property oldValue, Property newValue) {
        Deque<Property> shelve = new ArrayDeque<>();
        ConsListPropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(oldValue)) {
                ConsListPropertyMap newMap = current.getParentMap();
                newMap = newMap.putCopy(newValue);
                for (Property property : shelve) {
                    newMap = newMap.putCopy(property);
                }
                return newMap;
            } else {
                shelve.push(current.getLastProperty());
                current = current.getParentMap();
            }
        }
        return this;
    }

    public ConsListPropertyMap getOwningMap(Property value) {
        ConsListPropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(value)) {
                return current;
            }
            current = current.getParentMap();
        }
        return null;
    }

    public ConsListPropertyMap getParentMap() {
        return car;
    }

    @Override
    public Property getLastProperty() {
        return cdr;
    }

    @Override
    public String toString() {
        return values().toString();
    }

}
