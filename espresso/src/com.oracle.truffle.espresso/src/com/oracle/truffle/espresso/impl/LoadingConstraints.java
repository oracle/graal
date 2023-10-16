/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * This class takes care of the loading constraints in Espresso, as described in {5.3.4. Loading
 * Constraints}.
 * <p>
 * Constraints are recorded per type instance. a constraint is represented as a Klass instance,
 * along with the loaders that load the given type as this klass. Most of the time, there will be a
 * single Klass instance around for a given type, but if multiple loaders have a different instance
 * of a Klass, say k1 and k2, with the same given type, then we need to record a different
 * constraint for k1 and k2.
 * <p>
 * As such, the entire set of constraints for a particular type, called a bucket, is therefore list
 * of constraints, one per Klass instance of this type.
 * <p>
 * To represent this, we use a map from types to buckets. Buckets are a doubly linked list. To
 * support concurrency, we use a ConcurrentHashMap. failure to insert an item in the map due to
 * concurrency simply means someone was faster than us, we can therefore simply use the one that is
 * already present.
 * <p>
 * Once the bucket is obtained, we immediately synchronize on it. This prevents concurrency problems
 * as a whole, while allowing multiple threads to do constraint checking on different types.
 */
final class LoadingConstraints extends ContextAccessImpl {
    private static DebugTimer CONSTRAINTS = DebugTimer.create("constraints");

    private static final long NULL_KLASS_ID = -1;
    static final long INVALID_LOADER_ID = -1;

    private final PurgeInfo info = new PurgeInfo();

    LoadingConstraints(EspressoContext context) {
        super(context);
    }

    /**
     * Checks that loader1 and loader2 resolve type as the same Klass instance.
     */
    @SuppressWarnings("try")
    void checkConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2) {
        try (DebugCloseable constraints = CONSTRAINTS.scope(getContext().getTimers())) {
            Klass k1 = getContext().getRegistries().findLoadedClass(type, loader1);
            Klass k2 = getContext().getRegistries().findLoadedClass(type, loader2);
            checkOrAdd(type, getKlassID(k1), getKlassID(k2), getLoaderID(loader1, getMeta()), getLoaderID(loader2, getMeta()));
        }
    }

    /**
     * Records that loader resolves type as klass.
     */
    void recordConstraint(Symbol<Type> type, Klass k, StaticObject loader) {
        long loaderID = getLoaderID(loader, getMeta());
        long klass = getKlassID(k);
        ConstraintBucket bucket = lookup(type);
        if (bucket == null) {
            bucket = new ConstraintBucket();
            Constraint newConstraint = Constraint.create(klass, loaderID);
            bucket.add(newConstraint);
            ConstraintBucket previous = pairings.putIfAbsent(type, bucket);
            if (previous != null) {
                bucket = lookup(type);
            }
        }
        synchronized (bucket) {
            Constraint constraint = bucket.lookupLoader(loaderID);
            if (constraint == null) {
                constraint = bucket.lookupKlass(klass);
                if (constraint == null) {
                    bucket.add(Constraint.create(klass, loaderID));
                } else {
                    constraint.add(loaderID);
                }
            } else {
                checkConstraint(klass, constraint);
            }
        }
    }

    void removeUnloadedKlassConstraint(Klass klass, Symbol<Type> type) {
        long loaderId = getLoaderID(klass.getDefiningClassLoader(), getMeta());
        long klassId = getKlassID(klass);
        ConstraintBucket bucket = lookup(type);
        Constraint toRemove = bucket.lookupLoader(loaderId);
        bucket.remove(toRemove);
        toRemove = bucket.lookupKlass(klassId);
        if (toRemove != null) {
            bucket.remove(toRemove);
        }
    }

    void purge() {
        long[] alive = getContext().getRegistries().aliveLoaders();
        info.emptyBuckets = 0;
        for (ConstraintBucket bucket : pairings.values()) {
            synchronized (bucket) {
                bucket.purge(alive, info);
                if (bucket.constraint == null) {
                    // Unfortunately, we cannot remove safely this bucket without blocking
                    // constraint checking context-wide. Some other thread might have already
                    // obtained the bucket while we are reclaiming it. Removing it here would
                    // require having the other thread run checks on its size, slowing down the
                    // constraint checking process.
                    info.emptyBuckets++;
                }
            }
        }
        getContext().getLogger().log(Level.FINE, "purging constraints stats:\n" +
                        "reclaimed slots: " + info.reclaimedSlots + "\n" +
                        "reclaimed constraints: " + info.reclaimedConstraints + "\n" +
                        "empty buckets: " + info.emptyBuckets);
    }

    private void checkOrAdd(Symbol<Type> type, long k1, long k2, long loader1, long loader2) {
        if (exists(k1) && exists(k2) && k1 != k2) {
            throw linkageError("Loading constraint violated !");
        }
        long klass = !exists(k1) ? k2 : k1;
        ConstraintBucket bucket = lookup(type);
        if (bucket == null) {
            bucket = new ConstraintBucket();
            bucket.add(Constraint.create(klass, loader1, loader2));
            ConstraintBucket previous = pairings.putIfAbsent(type, bucket);
            if (previous != null) {
                bucket = lookup(type);
            }
        }
        synchronized (bucket) {
            Constraint c1 = bucket.lookupLoader(loader1);
            klass = checkConstraint(klass, c1);
            Constraint c2 = bucket.lookupLoader(loader2);
            klass = checkConstraint(klass, c2);
            if (c1 == null && c2 == null) {
                bucket.add(Constraint.create(klass, loader1, loader2));
            } else if (c1 == c2) {
                /* Constraint already added */
            } else if (c1 == null) {
                c2.add(loader1);
                if (!exists(c2.klass)) {
                    c2.klass = klass;
                }
            } else if (c2 == null) {
                c1.add(loader2);
                if (!exists(c1.klass)) {
                    c1.klass = klass;
                }
            } else {
                mergeConstraints(bucket, c1, c2);
            }
        }
    }

    private static final class ConstraintBucket {

        private Constraint constraint;

        Constraint lookupLoader(long loader) {
            Constraint curr = constraint;
            while (curr != null) {
                if (curr.contains(loader)) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        Constraint lookupKlass(long klass) {
            Constraint curr = constraint;
            while (curr != null) {
                if (curr.klass == klass) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        void add(Constraint newConstraint) {
            newConstraint.next = constraint;
            newConstraint.prev = null;
            if (constraint != null) {
                constraint.prev = newConstraint;
            }
            constraint = newConstraint;
        }

        void remove(Constraint toRemove) {
            Constraint prev = toRemove.prev;
            Constraint next = toRemove.next;
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
            if (toRemove == constraint) {
                constraint = next;
            }
        }

        void purge(long[] alive, PurgeInfo info) {
            assert Thread.holdsLock(this);
            Constraint curr = constraint;
            while (curr != null) {
                curr.purge(alive, info);
                if (curr.size == 0) {
                    remove(curr);
                    info.reclaimedConstraints++;
                }
                curr = curr.next;
            }
        }
    }

    private static final class Constraint {
        private long klass;

        /*
         * Most applications will only see the boot loader and the app class loader used.
         */
        private static final int DEFAULT_INITIAL_SIZE = 2;

        private long[] loaders = new long[DEFAULT_INITIAL_SIZE];
        private int size = 0;
        private int capacity = DEFAULT_INITIAL_SIZE;

        Constraint prev;
        Constraint next;

        boolean contains(long loader) {
            for (int i = 0; i < size; i++) {
                if (loaders[i] == loader) {
                    return true;
                }
            }
            return false;
        }

        void merge(Constraint other) {
            for (long loader : other.loaders) {
                if (!contains(loader)) {
                    add(loader);
                }
            }
        }

        Constraint(long k) {
            this.klass = k;
        }

        void add(long loader) {
            assert !contains(loader);
            if (size >= capacity) {
                loaders = Arrays.copyOf(loaders, capacity <<= 1);
            }
            loaders[size++] = loader;
        }

        public void purge(long[] alive, PurgeInfo info) {
            int i = 0;
            while (i < size) {
                if (!isAlive(loaders[i], alive)) {
                    swap(i, --size, loaders);
                    info.reclaimedSlots++;
                } else {
                    i++;
                }
            }
            if (size > 0 && // size == 0 gets reclaimed after
                            size < (capacity >> 1) &&
                            capacity > DEFAULT_INITIAL_SIZE // have 2 slots
            ) {
                int shift = 1;
                while (size < (capacity >> (shift + 1))) {
                    shift += 1;
                }
                long[] newLoaders = new long[capacity = Math.max(DEFAULT_INITIAL_SIZE, capacity >> shift)];
                System.arraycopy(loaders, 0, newLoaders, 0, size);
                loaders = newLoaders;
            }
        }

        static void swap(int i, int j, long[] loaders) {
            long a = loaders[i];
            loaders[i] = loaders[j];
            loaders[j] = a;
        }

        static boolean isAlive(long loader, long[] alive) {
            for (int i = 0; i < alive.length; i++) {
                long live = alive[i];
                if (live == INVALID_LOADER_ID) {
                    return false;
                }
                if (live == loader) {
                    return true;
                }
            }
            return false;
        }

        static Constraint create(long klass, long loader1, long loader2) {
            if (loader1 == loader2) {
                return create(klass, loader1);
            }
            Constraint constraint = new Constraint(klass);
            constraint.add(loader1);
            constraint.add(loader2);
            return constraint;
        }

        static Constraint create(long klass, long loader) {
            Constraint constraint = new Constraint(klass);
            constraint.add(loader);
            return constraint;
        }
    }

    private final ConcurrentHashMap<Symbol<Type>, ConstraintBucket> pairings = new ConcurrentHashMap<>();

    private ConstraintBucket lookup(Symbol<Type> type) {
        return pairings.get(type);
    }

    private long checkConstraint(long klass, Constraint c1) {
        if (c1 != null) {
            if (exists(c1.klass)) {
                if (exists(klass)) {
                    if (klass != c1.klass) {
                        throw linkageError("New loading constraint violates an older one!");
                    }
                } else {
                    return c1.klass;
                }
            } else {
                c1.klass = klass;
            }
        }
        return klass;
    }

    private static void mergeConstraints(ConstraintBucket bucket, Constraint c1, Constraint c2) {
        Constraint merge;
        Constraint delete;
        if (c1.loaders.length < c2.loaders.length) {
            merge = c2;
            delete = c1;
        } else {
            merge = c1;
            delete = c2;
        }
        merge.merge(delete);
        bucket.remove(delete);
    }

    private LinkageError linkageError(String message) {
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, message);
    }

    private static long getLoaderID(StaticObject loader, Meta meta) {
        if (StaticObject.isNull(loader)) {
            return meta.getContext().getBootClassLoaderID();
        }
        ClassRegistry classRegistry = (ClassRegistry) meta.HIDDEN_CLASS_LOADER_REGISTRY.getHiddenObject(loader, true);
        if (classRegistry == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return classRegistry.getLoaderID();
    }

    private static long getKlassID(Klass k) {
        return k == null ? NULL_KLASS_ID : k.getId();
    }

    private static boolean exists(long klass) {
        return klass != NULL_KLASS_ID;
    }

    private static class PurgeInfo {
        int reclaimedSlots = 0;
        int reclaimedConstraints = 0;
        int emptyBuckets = 0;
    }
}
