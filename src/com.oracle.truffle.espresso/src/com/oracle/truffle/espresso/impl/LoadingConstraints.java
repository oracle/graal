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

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

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
final class LoadingConstraints implements ContextAccess {
    private final EspressoContext context;

    LoadingConstraints(EspressoContext context) {
        this.context = context;
    }

    /**
     * Checks that loader1 and loader2 resolve type as the same Klass instance.
     */
    void checkConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2) {
        Klass k1 = getContext().getRegistries().findLoadedClass(type, loader1);
        Klass k2 = getContext().getRegistries().findLoadedClass(type, loader2);
        checkOrAdd(type, k1, k2, getLoaderID(loader1, getMeta()), getLoaderID(loader2, getMeta()));
    }

    /**
     * Records that loader resolves type as klass.
     */
    void recordConstraint(Symbol<Type> type, Klass klass, StaticObject loader) {
        int loaderID = getLoaderID(loader, getMeta());
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
            Constraint constraint = bucket.lookup(loaderID);
            if (constraint == null) {
                constraint = bucket.lookup(klass);
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

    private void checkOrAdd(Symbol<Type> type, Klass k1, Klass k2, int loader1, int loader2) {
        if (k1 != null && k2 != null && k1 != k2) {
            throw linkageError("Loading constraint violated !");
        }
        Klass klass = k1 == null ? k2 : k1;
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
            Constraint c1 = bucket.lookup(loader1);
            klass = checkConstraint(klass, c1);
            Constraint c2 = bucket.lookup(loader2);
            klass = checkConstraint(klass, c2);
            if (c1 == null && c2 == null) {
                bucket.add(Constraint.create(klass, loader1, loader2));
            } else if (c1 == c2) {
                /* Constraint already added */
            } else if (c1 == null) {
                c2.add(loader1);
                if (c2.klass == null) {
                    c2.klass = klass;
                }
            } else if (c2 == null) {
                c1.add(loader2);
                if (c1.klass == null) {
                    c1.klass = klass;
                }
            } else {
                mergeConstraints(bucket, c1, c2);
            }
        }
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    private static final class ConstraintBucket {

        private Constraint constraint;

        Constraint lookup(int loader) {
            Constraint curr = constraint;
            while (curr != null) {
                if (curr.contains(loader)) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        Constraint lookup(Klass klass) {
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

    }

    private static final class Constraint {
        private Klass klass;

        /*
         * Most applications will only see the boot loader and the app class loader used.
         */
        private static final int DEFAULT_INITIAL_SIZE = 2;

        private int[] loaders = new int[DEFAULT_INITIAL_SIZE];
        private int size = 0;
        private int capacity = DEFAULT_INITIAL_SIZE;

        Constraint prev;
        Constraint next;

        boolean contains(int loader) {
            for (int i = 0; i < size; i++) {
                if (loaders[i] == loader) {
                    return true;
                }
            }
            return false;
        }

        void merge(Constraint other) {
            for (int loader : other.loaders) {
                if (!contains(loader)) {
                    add(loader);
                }
            }
        }

        Constraint(Klass k) {
            this.klass = k;
        }

        void add(int loader) {
            assert !contains(loader);
            if (size >= capacity) {
                loaders = Arrays.copyOf(loaders, capacity << 1);
            }
            loaders[size++] = loader;
        }

        static Constraint create(Klass klass, int loader1, int loader2) {
            if (loader1 == loader2) {
                return create(klass, loader1);
            }
            Constraint constraint = new Constraint(klass);
            constraint.add(loader1);
            constraint.add(loader2);
            return constraint;
        }

        static Constraint create(Klass klass, int loader) {
            Constraint constraint = new Constraint(klass);
            constraint.add(loader);
            return constraint;
        }

    }

    private final ConcurrentHashMap<Symbol<Type>, ConstraintBucket> pairings = new ConcurrentHashMap<>();

    private ConstraintBucket lookup(Symbol<Type> type) {
        return pairings.get(type);
    }

    private Klass checkConstraint(Klass klass, Constraint c1) {
        if (c1 != null) {
            if (c1.klass != null) {
                if (klass != null) {
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
        throw Meta.throwExceptionWithMessage(meta.java_lang_LinkageError, message);
    }

    private static int getLoaderID(StaticObject loader, Meta meta) {
        if (StaticObject.isNull(loader)) {
            return meta.getContext().getBootClassLoaderID();
        }
        ClassRegistry classRegistry = (ClassRegistry) loader.getHiddenFieldVolatile(meta.HIDDEN_CLASS_LOADER_REGISTRY);
        if (classRegistry == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return classRegistry.getLoaderID();
    }
}
