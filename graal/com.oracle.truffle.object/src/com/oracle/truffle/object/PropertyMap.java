/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.object;

import java.util.*;

import com.oracle.truffle.api.object.*;

public final class PropertyMap implements Map<Object, Property> {
    private final PropertyMap car;
    private final Property cdr;
    private final int size;

    private static final PropertyMap EMPTY = new PropertyMap();

    private PropertyMap() {
        this.car = null;
        this.cdr = null;
        this.size = 0;
    }

    private PropertyMap(PropertyMap parent, Property added) {
        this.car = Objects.requireNonNull(parent);
        this.cdr = added;
        this.size = parent.size + 1;
    }

    public static PropertyMap empty() {
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
        for (Map.Entry<Object, Property> entry : reverseOrderEntrySet()) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Property put(Object key, Property value) {
        throw unmodifiableException();
    }

    public Property remove(Object key) {
        throw unmodifiableException();
    }

    public void putAll(Map<? extends Object, ? extends Property> m) {
        throw unmodifiableException();
    }

    public void clear() {
        throw unmodifiableException();
    }

    public Set<Object> keySet() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                Object[] keys = new Object[size()];
                Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
                for (int pos = size() - 1; pos >= 0; pos--) {
                    keys[pos] = iterator.next().getKey();
                }
                return Arrays.asList(keys).iterator();
            }

            @Override
            public int size() {
                return PropertyMap.this.size();
            }
        };
    }

    public Collection<Property> values() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                Property[] values = new Property[size()];
                Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
                for (int pos = size() - 1; pos >= 0; pos--) {
                    values[pos] = iterator.next().getValue();
                }
                return Arrays.asList(values).iterator();
            }

            @Override
            public int size() {
                return PropertyMap.this.size();
            }
        };
    }

    public Set<Map.Entry<Object, Property>> entrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                @SuppressWarnings("unchecked")
                Map.Entry<Object, Property>[] entries = (Map.Entry<Object, Property>[]) new Map.Entry<?, ?>[size()];
                Iterator<Map.Entry<Object, Property>> iterator = reverseOrderEntrySet().iterator();
                for (int pos = size() - 1; pos >= 0; pos--) {
                    entries[pos] = iterator.next();
                }
                return Arrays.asList(entries).iterator();
            }

            @Override
            public int size() {
                return PropertyMap.this.size();
            }
        };
    }

    public Set<Map.Entry<Object, Property>> reverseOrderEntrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                return new Iterator<Map.Entry<Object, Property>>() {
                    PropertyMap current = PropertyMap.this;

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
                return PropertyMap.this.size();
            }
        };
    }

    public Set<Object> reverseOrderKeys() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {
                    PropertyMap current = PropertyMap.this;

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
                return PropertyMap.this.size();
            }
        };
    }

    public Set<Property> reverseOrderValues() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                return new Iterator<Property>() {
                    PropertyMap current = PropertyMap.this;

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
                return PropertyMap.this.size();
            }
        };
    }

    private static final class MapEntryImpl implements Map.Entry<Object, Property> {
        private final Property backingProperty;

        public MapEntryImpl(Property backingProperty) {
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

    private static UnsupportedOperationException unmodifiableException() {
        throw new UnsupportedOperationException("unmodifiable");
    }

    public PropertyMap putCopy(Property value) {
        assert !this.containsValue(value);
        return new PropertyMap(this, value);
    }

    public PropertyMap removeCopy(Property value) {
        Deque<Property> shelve = new ArrayDeque<>();
        PropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(value)) {
                PropertyMap newMap = current.getParentMap();
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

    public PropertyMap replaceCopy(Property oldValue, Property newValue) {
        Deque<Property> shelve = new ArrayDeque<>();
        PropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(oldValue)) {
                PropertyMap newMap = current.getParentMap();
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

    public PropertyMap getOwningMap(Property value) {
        PropertyMap current = this;
        while (!current.isEmpty()) {
            if (current.getLastProperty().equals(value)) {
                return current;
            }
            current = current.getParentMap();
        }
        return null;
    }

    PropertyMap getParentMap() {
        return car;
    }

    public Property getLastProperty() {
        return cdr;
    }
}
