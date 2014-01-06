/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.array;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Implements the Ruby {@code Array} class.
 */
@SuppressWarnings("unused")
public final class RubyArray extends RubyObject {

    public static class RubyArrayClass extends RubyClass {

        public RubyArrayClass(RubyClass objectClass) {
            super(null, objectClass, "Array");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyArray(this);
        }

    }

    @CompilationFinal private ArrayStore store;

    public RubyArray(RubyClass arrayClass) {
        this(arrayClass, EmptyArrayStore.INSTANCE);
    }

    public RubyArray(RubyClass arrayClass, ArrayStore store) {
        super(arrayClass);
        this.store = store;
    }

    private static RubyArray selfAsArray(Object self) {
        if (self instanceof RubyArray) {
            return (RubyArray) self;
        } else {
            throw new IllegalStateException();
        }
    }

    @CompilerDirectives.SlowPath
    public static RubyArray specializedFromObject(RubyClass arrayClass, Object object) {
        ArrayStore store;

        if (object instanceof Integer || object instanceof RubyFixnum) {
            store = new FixnumArrayStore(new int[]{GeneralConversions.toFixnum(object)});
        } else {
            store = new ObjectArrayStore(new Object[]{object});
        }

        return new RubyArray(arrayClass, store);
    }

    /**
     * Create a Ruby array from a Java array of objects, choosing the best store.
     */
    @CompilerDirectives.SlowPath
    public static RubyArray specializedFromObjects(RubyClass arrayClass, Object... objects) {
        if (objects.length == 0) {
            return new RubyArray(arrayClass);
        }

        boolean canUseFixnum = true;

        for (Object object : objects) {
            if (!(object instanceof Integer || object instanceof RubyFixnum)) {
                canUseFixnum = false;
            }
        }

        ArrayStore store;

        if (canUseFixnum) {
            final int[] values = new int[objects.length];

            for (int n = 0; n < objects.length; n++) {
                values[n] = GeneralConversions.toFixnum(objects[n]);
            }

            store = new FixnumArrayStore(values);
        } else {
            store = new ObjectArrayStore(objects);
        }

        return new RubyArray(arrayClass, store);
    }

    public Object get(int index) {
        return store.get(ArrayUtilities.normaliseIndex(store.size(), index));
    }

    public Object getRangeInclusive(int begin, int inclusiveEnd) {
        final int l = store.size();
        final int normalisedInclusiveEnd = ArrayUtilities.normaliseIndex(l, inclusiveEnd);
        return getRangeExclusive(begin, normalisedInclusiveEnd + 1);
    }

    public Object getRangeExclusive(int begin, int exclusiveEnd) {
        final int l = store.size();
        final int normalisedBegin = ArrayUtilities.normaliseIndex(l, begin);
        final int truncatedNormalisedExclusiveEnd = ArrayUtilities.truncateNormalisedExclusiveIndex(l, ArrayUtilities.normaliseExclusiveIndex(l, exclusiveEnd));

        final Object range = store.getRange(normalisedBegin, truncatedNormalisedExclusiveEnd);

        if (range == null) {
            return new RubyArray(getRubyClass());
        } else {
            return new RubyArray(getRubyClass(), (ArrayStore) range);
        }
    }

    public void set(int index, Object value) {
        checkFrozen();

        final int l = store.size();
        final int normalisedIndex = ArrayUtilities.normaliseIndex(l, index);

        try {
            store.set(normalisedIndex, value);
        } catch (GeneraliseArrayStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.set(normalisedIndex, value);
            } catch (GeneraliseArrayStoreException ex) {
                throwSecondGeneraliseException();
            }
        }
    }

    public void setRangeSingleInclusive(int begin, int inclusiveEnd, Object value) {
        final int l = store.size();
        final int normalisedInclusiveEnd = ArrayUtilities.normaliseIndex(l, inclusiveEnd);
        setRangeSingleExclusive(begin, normalisedInclusiveEnd + 1, value);
    }

    public void setRangeSingleExclusive(int begin, int exclusiveEnd, Object value) {
        checkFrozen();

        final int l = store.size();
        final int normalisedBegin = ArrayUtilities.normaliseIndex(l, begin);
        final int truncatedNormalisedExclusiveEnd = ArrayUtilities.truncateNormalisedExclusiveIndex(l, ArrayUtilities.normaliseExclusiveIndex(l, exclusiveEnd));

        try {
            store.setRangeSingle(normalisedBegin, truncatedNormalisedExclusiveEnd, value);
        } catch (GeneraliseArrayStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.setRangeSingle(normalisedBegin, truncatedNormalisedExclusiveEnd, value);
            } catch (GeneraliseArrayStoreException ex) {
                throwSecondGeneraliseException();
            }
        }
    }

    public void setRangeArrayInclusive(int begin, int inclusiveEnd, RubyArray other) {
        final int l = store.size();
        final int normalisedInclusiveEnd = ArrayUtilities.normaliseIndex(l, inclusiveEnd);
        setRangeArrayExclusive(begin, normalisedInclusiveEnd + 1, other);
    }

    public void setRangeArrayExclusive(int begin, int exclusiveEnd, RubyArray other) {
        checkFrozen();

        final int l = store.size();
        final int normalisedBegin = ArrayUtilities.normaliseIndex(l, begin);
        final int normalisedExclusiveEnd = ArrayUtilities.normaliseExclusiveIndex(l, exclusiveEnd);

        try {
            store.setRangeArray(normalisedBegin, normalisedExclusiveEnd, other.store);
        } catch (GeneraliseArrayStoreException e) {
            store = store.generalizeFor(other.store.getIndicativeValue());

            try {
                store.setRangeArray(normalisedBegin, normalisedExclusiveEnd, other.store);
            } catch (GeneraliseArrayStoreException ex) {
                throwSecondGeneraliseException();
            }
        }
    }

    public void insert(int index, Object value) {
        checkFrozen();

        final int l = store.size();
        final int normalisedIndex = ArrayUtilities.normaliseIndex(l, index);

        try {
            store.insert(normalisedIndex, value);
        } catch (GeneraliseArrayStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.insert(normalisedIndex, value);
            } catch (GeneraliseArrayStoreException ex) {
                throwSecondGeneraliseException();
            }
        }
    }

    public void push(Object value) {
        checkFrozen();

        if (store instanceof EmptyArrayStore) {
            /*
             * Normally we want to transfer to interpreter to generalize an array store, but the
             * special case of an empty array is common, will never cause rewrites and has a simple
             * implementation, so treat it as a special case.
             */
            store = ((EmptyArrayStore) store).generalizeFor(value);
        }

        try {
            store.push(value);
        } catch (GeneraliseArrayStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.push(value);
            } catch (GeneraliseArrayStoreException ex) {
                throw new IllegalStateException("Generalised to support a specific value, but value still rejected by store");
            }
        }
    }

    public void unshift(Object value) {
        insert(0, value);
    }

    public Object deleteAt(int index) {
        checkFrozen();

        final int l = store.size();
        final int normalisedIndex = ArrayUtilities.normaliseIndex(l, index);

        return store.deleteAt(normalisedIndex);
    }

    @Override
    @CompilerDirectives.SlowPath
    public Object dup() {
        return new RubyArray(getRubyClass(), store.dup());
    }

    public ArrayStore getArrayStore() {
        return store;
    }

    public List<Object> asList() {
        final RubyArray array = this;

        return new AbstractList<Object>() {

            @Override
            public Object get(int n) {
                return array.get(n);
            }

            @Override
            public int size() {
                return array.size();
            }

        };
    }

    public Object[] toObjectArray() {
        return store.toObjectArray();
    }

    private static void throwSecondGeneraliseException() {
        CompilerAsserts.neverPartOfCompilation();
        throw new RuntimeException("Generalised based on a value, but the new store also rejected that value.");
    }

    public int size() {
        return store.size();
    }

    public boolean contains(Object value) {
        return store.contains(value);
    }

    /**
     * Recursive Cartesian product.
     * <p>
     * The Array#product method is supposed to be able to take a block, to which it yields tuples as
     * they are produced, so it might be worth abstracting this method into sending tuples to some
     * interface, which either adds them to an array, or yields them to the block.
     */
    @SlowPath
    public static RubyArray product(RubyClass arrayClass, RubyArray[] arrays, int l) {
        if (arrays.length - l == 1) {
            final RubyArray firstArray = arrays[0];

            final RubyArray tuples = new RubyArray(arrayClass);

            for (int i = 0; i < firstArray.size(); i++) {
                final RubyArray tuple = new RubyArray(arrayClass);
                tuple.push(firstArray.get(i));
                tuples.push(tuple);
            }

            return tuples;
        } else {
            final RubyArray intermediateTuples = product(arrayClass, arrays, l - 1);
            final RubyArray lastArray = arrays[l - 1];

            final RubyArray tuples = new RubyArray(arrayClass);

            for (int n = 0; n < intermediateTuples.size(); n++) {
                for (int i = 0; i < lastArray.size(); i++) {
                    final RubyArray tuple = (RubyArray) ((RubyArray) intermediateTuples.get(n)).dup();
                    tuple.push(lastArray.get(i));
                    tuples.push(tuple);
                }
            }

            return tuples;
        }
    }

    public boolean equals(RubyArray other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else {
            return store.equals(other.store);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RubyArray) {
            return equals((RubyArray) other);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        getRubyClass().getContext().implementationMessage("Array#hash returns nonsense");
        return 0;
    }

    public RubyArray relativeComplement(RubyArray other) {
        // TODO(cs): specialize for different stores

        final RubyArray result = new RubyArray(getRubyClass().getContext().getCoreLibrary().getArrayClass());

        for (Object value : asList()) {
            if (!other.contains(value)) {
                result.push(value);
            }
        }

        return result;
    }

    public String join(String separator) {
        final StringBuilder builder = new StringBuilder();

        for (int n = 0; n < size(); n++) {
            if (n > 0) {
                builder.append(separator);
            }

            builder.append(get(n).toString());
        }

        return builder.toString();
    }

    public static String join(Object[] parts, String separator) {
        final StringBuilder builder = new StringBuilder();

        for (int n = 0; n < parts.length; n++) {
            if (n > 0) {
                builder.append(separator);
            }

            builder.append(parts[n].toString());
        }

        return builder.toString();
    }

    public void flattenTo(RubyArray result) {
        for (int n = 0; n < size(); n++) {
            final Object value = get(n);

            if (value instanceof RubyArray) {
                ((RubyArray) value).flattenTo(result);
            } else {
                result.push(value);
            }
        }
    }

    public boolean isEmpty() {
        return store.size() == 0;
    }

}
