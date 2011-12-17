/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.profile;

import java.util.*;
import java.util.Arrays;

import com.sun.max.*;
import com.sun.max.profile.Metrics.*;
import com.sun.max.program.*;

/**
 * This class is a container for a number of inner classes that support recording
 * metrics about the distribution (i.e. frequency) of values. For example,
 * these utilities can be used to gather the distribution of compilation times,
 * object sizes, heap allocations, etc. Several different distribution implementations
 * are available, with different time and space tradeoffs and different approximations,
 * ranging from exact distributions to only distributions within a limited range.
 */
public class ValueMetrics {

    public static final Approximation EXACT = new Approximation();
    public static final Approximation TRACE = new IntegerTraceApproximation(1024);

    public static class Approximation {
    }

    public static class FixedApproximation extends Approximation {
        protected final Object[] values;
        public FixedApproximation(Object... values) {
            this.values = values.clone();
        }
    }

    public static class IntegerRangeApproximation extends Approximation {
        protected final int lowValue;
        protected final int highValue;
        public IntegerRangeApproximation(int low, int high) {
            lowValue = low;
            highValue = high;
        }
    }

    public static class IntegerTraceApproximation extends Approximation {
        protected final int bufferSize;
        public IntegerTraceApproximation(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }

    /**
     * This class and its descendants allow recording of a distribution of integer values.
     * Various implementations implement different approximations with different time and
     * space tradeoffs. Because this class and its descendants record distributions only
     * for integers, it can be more efficient than using boxed (e.g. {@code java.lang.Integer}) values.
     *
     */
    public abstract static class IntegerDistribution extends Distribution<Integer> {

        public abstract void record(int value);
    }

    public static class FixedRangeIntegerDistribution extends IntegerDistribution {
        protected final int lowValue;
        protected final int[] counts;
        protected int missed;

        public FixedRangeIntegerDistribution(int low, int high) {
            lowValue = low;
            counts = new int[high - low];
        }

        @Override
        public void record(int value) {
            total++;
            final int index = value - lowValue;
            if (index >= 0 && index < counts.length) {
                counts[index]++;
            } else {
                missed++;
            }
        }

        @Override
        public int getCount(Integer value) {
            final int index = value - lowValue;
            if (index >= 0 && index < counts.length) {
                return counts[index];
            }
            return missed > 0 ? -1 : 0;
        }

        @Override
        public Map<Integer, Integer> asMap() {
            final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            for (int i = 0; i != counts.length; ++i) {
                map.put(lowValue + i, counts[i]);
            }
            return map;
        }

        @Override
        public void reset() {
            super.reset();
            missed = 0;
            Arrays.fill(counts, 0);
        }
    }

    public static class FixedSetIntegerDistribution extends IntegerDistribution {

        private final int[] set;
        private final int[] count;
        protected int missed;

        public FixedSetIntegerDistribution(int[] set) {
            // TODO: sort() and binarySearch for large sets.
            this.set = set.clone();
            count = new int[set.length];
        }

        @Override
        public void record(int value) {
            total++;
            for (int i = 0; i < set.length; i++) {
                if (set[i] == value) {
                    count[i]++;
                    return;
                }
            }
            missed++;
        }

        @Override
        public int getCount(Integer value) {
            final int val = value;
            for (int i = 0; i < set.length; i++) {
                if (set[i] == val) {
                    return count[i];
                }
            }
            return missed > 0 ? -1 : 0;
        }

        @Override
        public Map<Integer, Integer> asMap() {
            final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            for (int i = 0; i != count.length; ++i) {
                map.put(set[i], count[i]);
            }
            return map;
        }

        @Override
        public void reset() {
            super.reset();
            missed = 0;
            Arrays.fill(set, 0);
            Arrays.fill(count, 0);
        }
    }

    /**
     * The trace integer distribution class collects an exact distribution of integer
     * values by internally recording every integer value in order in a fixed size buffer.
     * When the buffer fills up, its contents are sorted and then reduced into a
     * sorted list of value/count pairs. This results in an exact distribution
     * with a tunable size/performance tradeoff, since a larger buffer means
     * fewer reduction steps.
     *
     * This implementation is <b>not thread safe</b>. It may lose updates and potentially
     * generate exceptions if used in a multi-threaded scenario. To make updates
     * to this distribution thread safe, {@linkplain ValueMetrics#threadSafe(IntegerDistribution) wrap}
     * it with a {@link ThreadsafeIntegerDistribution}.
     *
     */
    public static class TraceIntegerDistribution extends IntegerDistribution {
        private final int[] buffer;
        private int cursor;

        private int[] values;
        private int[] counts;

        public TraceIntegerDistribution(int bufferSize) {
            assert bufferSize > 0;
            buffer = new int[bufferSize];
        }

        @Override
        public void record(int value) {
            total++;
            buffer[cursor++] = value;
            if (cursor == buffer.length) {
                reduce();
            }
        }

        @Override
        public int getCount(Integer value) {
            reduce();
            final int index = Arrays.binarySearch(values, value);
            if (index < 0) {
                return 0;
            }
            return counts[index];
        }

        @Override
        public Map<Integer, Integer> asMap() {
            reduce();
            // TODO: another map implementation might be better.
            final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    map.put(values[i], counts[i]);
                }
            }
            return map;
        }

        @Override
        public void reset() {
            super.reset();
            cursor = 0;
            Arrays.fill(buffer, 0);
            Arrays.fill(values, 0);
            Arrays.fill(counts, 0);
        }

        private void reduce() {
            if (cursor == 0) {
                // no recorded values to reduce
                return;
            }
            // compression needed.
            Arrays.sort(buffer, 0, cursor);
            if (values != null) {
                // there are already some entries. need to merge counts
                removeExistingValues();
                if (cursor > 0) {
                    final int[] ovalues = values;
                    final int[] ocounts = counts;
                    reduceBuffer(buffer);
                    mergeValues(ovalues, ocounts, values, counts);
                }
            } else {
                // there currently aren't any entry arrays. reduce the buffer to generate the first ones.
                reduceBuffer(buffer);
            }
            cursor = 0;
        }

        private void removeExistingValues() {
            // increment counts for values that already occur in the entry arrays
            // and leave leftover values in the buffer
            int valuesPos = 0;
            final int ocursor = cursor;
            cursor = 0;
            for (int i = 0; i < ocursor; i++) {
                final int nvalue = buffer[i];
                while (valuesPos < values.length && values[valuesPos] < nvalue) {
                    valuesPos++;
                }
                if (valuesPos < values.length && values[valuesPos] == nvalue) {
                    counts[valuesPos]++;
                } else {
                    // retain the new values in the buffer
                    buffer[cursor++] = nvalue;
                }
            }
        }

        private void mergeValues(int[] avalues, int[] acounts, int[] bvalues, int[] bcounts) {
            final int[] nvalues = new int[avalues.length + values.length];
            final int[] ncounts = new int[acounts.length + counts.length];

            int a = 0;
            int b = 0;
            int n = 0;
            while (n < nvalues.length) {
                while (a < avalues.length && (b == bvalues.length || avalues[a] < bvalues[b])) {
                    nvalues[n] = avalues[a];
                    ncounts[n] = acounts[a];
                    n++;
                    a++;
                }
                while (b < bvalues.length && (a == avalues.length || bvalues[b] < avalues[a])) {
                    nvalues[n] = bvalues[b];
                    ncounts[n] = bcounts[b];
                    n++;
                    b++;
                }
            }
            assert n == nvalues.length && a == avalues.length && b == bvalues.length;
            values = nvalues;
            counts = ncounts;
        }

        private void reduceBuffer(int[] buf) {
            // count the unique values
            int last1 = buf[0];
            int unique = 1;
            for (int i1 = 1; i1 < cursor; i1++) {
                if (buffer[i1] != last1) {
                    unique++;
                    last1 = buffer[i1];
                }
            }
            // create the values arrays and populate them
            values = new int[unique];
            counts = new int[unique];
            int last = buffer[0];
            int count = 1;
            int pos = 0;
            for (int i = 1; i < cursor; i++) {
                if (buffer[i] != last) {
                    values[pos] = last;
                    counts[pos] = count;
                    pos++;
                    count = 1;
                    last = buffer[i];
                } else {
                    count++;
                }
            }
            assert pos == values.length - 1;
            values[pos] = last;
            counts[pos] = count;
        }
    }

    public static class HashedIntegerDistribution extends IntegerDistribution {
        private Map<Integer, Distribution> map;

        private Map<Integer, Distribution> map() {
            if (map == null) {
                map = new HashMap<Integer, Distribution>();
            }
            return map;
        }

        @Override
        public void record(int value) {
            total++;
            final Integer integer = value;
            Distribution distribution = map().get(integer);
            if (distribution == null) {
                distribution = new Distribution();
                map().put(integer, distribution);
            }
            distribution.total++;
        }
        @Override
        public int getCount(Integer value) {
            final Distribution distribution = map().get(value);
            if (distribution != null) {
                return distribution.total;
            }
            return 0;
        }

        @Override
        public Map<Integer, Integer> asMap() {
            final Map<Integer, Integer> result = new HashMap<Integer, Integer>();
            for (Map.Entry<Integer, Distribution> entry : map().entrySet()) {
                result.put(entry.getKey(), entry.getValue().total);
            }
            return result;
        }

        @Override
        public void reset() {
            super.reset();
            map = null;
        }
    }

    /**
     * This class and its descendants support profiling of individual objects. Reference
     * equality is used here exclusively. Various implementations with different size and space
     * tradeoffs offer various levels of accuracy.
     *
     */
    public abstract static class ObjectDistribution<T> extends Distribution<T> {
        public abstract void record(T value);
    }

    public static class HashedObjectDistribution<T> extends ObjectDistribution<T> {
        private Map<T, Distribution> map;
        private Map<T, Distribution> map() {
            if (map == null) {
                map = new IdentityHashMap<T, Distribution>();
            }
            return map;
        }

        public HashedObjectDistribution() {
        }

        @Override
        public void record(T value) {
            total++;
            Distribution distribution = map().get(value);
            if (distribution == null) {
                distribution = new Distribution();
                map().put(value, distribution);
            }
            distribution.total++;
        }
        @Override
        public int getCount(T value) {
            final Distribution distribution = map().get(value);
            if (distribution != null) {
                return distribution.total;
            }
            return 0;
        }

        @Override
        public Map<T, Integer> asMap() {
            final Map<T, Integer> result = new IdentityHashMap<T, Integer>();
            for (Map.Entry<T, Distribution> entry : map().entrySet()) {
                result.put(entry.getKey(), entry.getValue().total);
            }
            return result;
        }

        @Override
        public void reset() {
            super.reset();
            map = null;
        }
    }

    public static class FixedSetObjectDistribution<T> extends ObjectDistribution<T> {

        private final T[] set;
        private final int[] count;
        private int missed;

        public FixedSetObjectDistribution(T[] set) {
            this.set = set.clone();
            count = new int[set.length];
        }

        @Override
        public void record(T value) {
            total++;
            for (int i = 0; i < set.length; i++) {
                if (set[i] == value) {
                    count[i]++;
                    return;
                }
            }
            missed++;
        }
        @Override
        public int getCount(T value) {
            for (int i = 0; i < set.length; i++) {
                if (set[i] == value) {
                    return count[i];
                }
            }
            return missed > 0 ? -1 : 0;
        }

        @Override
        public Map<T, Integer> asMap() {
            final Map<T, Integer> map = new IdentityHashMap<T, Integer>();
            for (int i = 0; i != count.length; ++i) {
                map.put(set[i], count[i]);
            }
            return map;
        }

        @Override
        public void reset() {
            super.reset();
            missed = 0;
            Arrays.fill(set, 0);
            Arrays.fill(count, 0);
        }
    }

    /**
     * This method creates a new distribution for the occurrence of integer values.
     *
     * @param name the name of the metric; if non-null, then a global metric of the specified
     * name will be returned {@see GlobalMetrics}
     * @param approx the approximation level that is requested. Different approximation levels
     * have different time and space tradeoffs versus accuracy.
     * @return a new integer distribution object that can be used to record occurrences of integers
     */
    public static IntegerDistribution newIntegerDistribution(String name, Approximation approx) {
        if (name != null) {
            final IntegerDistribution prev = GlobalMetrics.getMetric(name, IntegerDistribution.class);
            if (prev != null) {
                return prev;
            }
            return GlobalMetrics.setMetric(name, IntegerDistribution.class, createIntegerDistribution(approx));
        }
        return createIntegerDistribution(approx);
    }

    private static IntegerDistribution createIntegerDistribution(Approximation approx) throws ProgramError {
        if (approx instanceof FixedApproximation) {
            final FixedApproximation fixedApprox = (FixedApproximation) approx;
            final int[] values = new int[fixedApprox.values.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = (Integer) fixedApprox.values[i];
            }
            return new FixedSetIntegerDistribution(values);
        }
        if (approx instanceof IntegerRangeApproximation) {
            final IntegerRangeApproximation fixedApprox = (IntegerRangeApproximation) approx;
            return new FixedRangeIntegerDistribution(fixedApprox.lowValue, fixedApprox.highValue);
        }
        if (approx == EXACT) {
            return new HashedIntegerDistribution();
        }
        if (approx instanceof IntegerTraceApproximation) {
            final IntegerTraceApproximation traceApprox = (IntegerTraceApproximation) approx;
            return new TraceIntegerDistribution(traceApprox.bufferSize);
        }
        return new HashedIntegerDistribution();
    }

    /**
     * This is a utility method to create an integer distribution over a range of specified integers.
     *
     * @param name the name of the metric
     * @param low the lowest value to be recorded in the range (inclusive)
     * @param high the highest value to be recorded (inclusive)
     * @return a new integer distribution object that will record exact profiles for the integers in the
     * specified range
     */
    public static IntegerDistribution newIntegerDistribution(String name, int low, int high) {
        return newIntegerDistribution(name, new IntegerRangeApproximation(low, high));
    }

    /**
     * This utility method creates a new integer distribution with the {@link #EXACT} approximation.
     * Note that the implementation of this integer distribution may consume excessive time and/or
     * space for unstructured distributions.
     *
     * @param name the name of the metric; if non-null, then a shared, global metric of the specified name will be returned
     * @return a new integer distribution that records an exact profile
     */
    public static IntegerDistribution newIntegerDistribution(String name) {
        return newIntegerDistribution(name, EXACT);
    }

    /**
     * This utility method creates an integer distribution that records only the specified set of
     * values, with all other values being not recorded.
     *
     * @param name the name of the metric; if non-null, then a shared, global metric of the specified name will be returned
     * @param values the set of integer values to be recored
     * @return a new integer distribution recorder
     */
    public static IntegerDistribution newIntegerDistribution(String name, int[] values) {
        final Object[] vals = new Object[values.length];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = values[i];
        }
        return newIntegerDistribution(name, new FixedApproximation(vals));
    }

    /**
     * This method creates a new distribution capable of recording individual objects.
     *
     * @param <T> the type of objects being profiled
     * @param name the name of the metric; if non-null, then a shared, global metric of the specified name will be
     * returned
     * @param approx the approximation for the distribution
     * @return a new distribution capable of profiling the occurrence of objects
     */
    public static <T> ObjectDistribution<T> newObjectDistribution(String name, Approximation approx) {
        if (name != null) {
            final ObjectDistribution<T> prev = Utils.cast(GlobalMetrics.getMetric(name, ObjectDistribution.class));
            if (prev != null) {
                return prev;
            }
            return Utils.cast(GlobalMetrics.setMetric(name, ObjectDistribution.class, createObjectDistribution(approx)));
        }
        return createObjectDistribution(approx);
    }

    private static <T> ObjectDistribution<T> createObjectDistribution(Approximation approx) {
        if (approx instanceof FixedApproximation) {
            final FixedApproximation fixedApprox = (FixedApproximation) approx;
            final T[] values = Utils.cast(fixedApprox.values);
            return new FixedSetObjectDistribution<T>(values);
        }
        if (approx == EXACT) {
            return new HashedObjectDistribution<T>();
        }
        // default is to use the hashed object distribution
        return new HashedObjectDistribution<T>();
    }

    /**
     * This is a utility method to create a new object distribution that only records occurrences of
     * objects in the specified set.
     *
     * @param <T> the type of the objects being profiled
     * @param name the name of the metric
     * @param set the set of objects for which to record exact profiling information
     * @return a new distribution capable of producing an exact profile of the occurence of the specified objects
     */
    public static <T> ObjectDistribution<T> newObjectDistribution(String name, T... set) {
        return newObjectDistribution(name, new FixedApproximation(set));
    }

    /**
     * This is utility method to create a new object distribution with an exact profile.
     *
     * @param <T> the type of the objects being profiled
     * @param name the name of metric
     * @return a new distribution capable of producing an exact profile of the occurrences of all of the specified
     * objects.
     */
    public static <T> ObjectDistribution<T> newObjectDistribution(String name) {
        return newObjectDistribution(name, EXACT);
    }

    public static class ThreadsafeIntegerDistribution extends IntegerDistribution {

        private final IntegerDistribution distribution;

        public ThreadsafeIntegerDistribution(IntegerDistribution distribution) {
            this.distribution = distribution;
        }
        @Override
        public void record(int value) {
            synchronized (distribution) {
                distribution.record(value);
            }
        }
        @Override
        public int getCount(Integer value) {
            synchronized (distribution) {
                return distribution.getCount(value);
            }
        }

        @Override
        public Map<Integer, Integer> asMap() {
            synchronized (distribution) {
                return distribution.asMap();
            }
        }
        @Override
        public void reset() {
            super.reset();
            distribution.reset();
        }

    }

    private static class ThreadsafeObjectDistribution<T> extends ObjectDistribution<T> {

        private final ObjectDistribution<T> distribution;

        ThreadsafeObjectDistribution(ObjectDistribution<T> distribution) {
            this.distribution = distribution;
        }
        @Override
        public void record(T value) {
            synchronized (distribution) {
                distribution.record(value);
            }
        }
        @Override
        public int getCount(T value) {
            synchronized (distribution) {
                return distribution.getCount(value);
            }
        }

        @Override
        public Map<T, Integer> asMap() {
            synchronized (distribution) {
                return distribution.asMap();
            }
        }
    }

    /**
     * This method creates a wrapper around the specified integer distribution that ensures
     * access to the distribution is synchronized.
     *
     * @param distribution the distribution to wrap in a synchronization
     * @return a synchronized view of the distribution
     */
    public static IntegerDistribution threadSafe(IntegerDistribution distribution) {
        return new ThreadsafeIntegerDistribution(distribution);
    }

    /**
     * This method creates a wrapper around the specified integer distribution that ensures
     * access to the distribution is synchronized.
     *
     * @param distribution the distribution to wrap in a synchronization
     * @return a synchronized view of the distribution
     */
    public static <T> ObjectDistribution<T> threadSafe(ObjectDistribution<T> distribution) {
        return new ThreadsafeObjectDistribution<T>(distribution);
    }
}
