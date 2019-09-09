package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class LoadingConstraints implements ContextAccess {
    private final EspressoContext context;

    LoadingConstraints(EspressoContext context) {
        this.context = context;
    }

    void checkConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2) {
        Klass k1 = getContext().getRegistries().findLoadedClass(type, loader1);
        Klass k2 = getContext().getRegistries().findLoadedClass(type, loader2);
        checkOrAdd(type, k1, k2, loader1, loader2);
    }

    void recordConstraint(Symbol<Type> type, Klass klass, StaticObject loader) {
        ConstraintsCollection bucket = lookup(type);
        if (bucket == null) {
            bucket = new ConstraintsCollection();
            bucket.add(Constraint.create(klass, loader));
            pairings.put(type, bucket);
        } else {
            Constraint constraint = bucket.lookup(loader);
            if (constraint == null) {
                constraint = bucket.lookup(klass);
                if (constraint == null) {
                    bucket.add(Constraint.create(klass, loader));
                } else {
                    constraint.add(loader);
                }
            } else {
                checkConstraint(klass, constraint);
            }
        }
    }

    private void checkOrAdd(Symbol<Type> type, Klass k1, Klass k2, StaticObject loader1, StaticObject loader2) {
        if (k1 != null && k2 != null && k1 != k2) {
            throw linkageError("Loading constraint violated !");
        }
        ConstraintsCollection bucket = lookup(type);
        Klass klass = k1 == null ? k2 : k1;
        if (bucket == null) {
            bucket = new ConstraintsCollection();
            bucket.add(Constraint.create(klass, loader1, loader2));
            pairings.put(type, bucket);
        } else {
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

    private static final class ConstraintsCollection {

        private Constraint constraint;

        Constraint lookup(StaticObject loader) {
            Constraint curr = constraint;
            while (curr != null) {
                if (curr.loaders.contains(loader)) {
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

        private final List<@Host(ClassLoader.class) StaticObject> loaders = new ArrayList<>();
        Constraint prev;

        Constraint next;

        Constraint(Klass k) {
            this.klass = k;
        }

        void add(StaticObject loader) {
            loaders.add(loader);
        }

        static Constraint create(Klass klass, StaticObject loader1, StaticObject loader2) {
            Constraint constraint = new Constraint(klass);
            constraint.add(loader1);
            constraint.add(loader2);
            return constraint;
        }

        static Constraint create(Klass klass, StaticObject loader) {
            Constraint constraint = new Constraint(klass);
            constraint.add(loader);
            return constraint;
        }

    }

    private final ConcurrentHashMap<Symbol<Type>, ConstraintsCollection> pairings = new ConcurrentHashMap<>();

    private ConstraintsCollection lookup(Symbol<Type> type) {
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

    private static void mergeConstraints(ConstraintsCollection bucket, Constraint c1, Constraint c2) {
        Constraint merge;
        Constraint delete;
        if (c1.loaders.size() < c2.loaders.size()) {
            merge = c2;
            delete = c1;
        } else {
            merge = c1;
            delete = c2;
        }
        merge.loaders.addAll(delete.loaders);
        bucket.remove(delete);
    }

    private LinkageError linkageError(String message) {
        throw getContext().getMeta().throwExWithMessage(LinkageError.class, message);
    }
}
