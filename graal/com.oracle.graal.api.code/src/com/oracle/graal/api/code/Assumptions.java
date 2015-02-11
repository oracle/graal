/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.io.*;
import java.lang.invoke.*;
import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Class for recording assumptions made during compilation. {@link OptimisticAssumption}s can only
 * be recorded in an {@link Assumptions} object if it {@linkplain #useOptimisticAssumptions()
 * allows} them.
 */
public final class Assumptions implements Serializable, Iterable<Assumptions.Assumption> {

    private static final long serialVersionUID = 5152062717588239131L;

    /**
     * Abstract base class for assumptions.
     */
    public abstract static class Assumption implements Serializable {

        private static final long serialVersionUID = -1936652569665112915L;
    }

    /**
     * Abstract base class for optimistic assumptions. An optimistic assumption assumes a property
     * of the runtime that may be invalidated by subsequent execution (e.g., that a class has no
     * subclasses implementing {@link NoFinalizableSubclass Object.finalize()}). A non-optimistic
     * assumption assumes a property that will most likely only be invalidated by an external
     * interface to the runtime (e.g., a {@linkplain MethodContents breakpoint is set or a class is
     * redefined}).
     */
    public abstract static class OptimisticAssumption extends Assumption {

        private static final long serialVersionUID = -1936652569665112932L;
    }

    /**
     * An optimistic assumption that a given class has no subclasses implementing
     * {@link Object#finalize()}).
     */
    public static final class NoFinalizableSubclass extends OptimisticAssumption {

        private static final long serialVersionUID = 6451169735564055081L;

        private ResolvedJavaType receiverType;

        public NoFinalizableSubclass(ResolvedJavaType receiverType) {
            this.receiverType = receiverType;
        }

        @Override
        public int hashCode() {
            return 31 + receiverType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NoFinalizableSubclass) {
                NoFinalizableSubclass other = (NoFinalizableSubclass) obj;
                return other.receiverType.equals(receiverType);
            }
            return false;
        }

        @Override
        public String toString() {
            return "NoFinalizableSubclass[receiverType=" + receiverType.toJavaName() + "]";
        }

    }

    /**
     * An optimistic assumption that a given type has a given unique subtype.
     */
    public static final class ConcreteSubtype extends OptimisticAssumption {

        private static final long serialVersionUID = -1457173265437676252L;

        /**
         * Type the assumption is made about.
         */
        public final ResolvedJavaType context;

        /**
         * Assumed unique concrete sub-type of the context type.
         */
        public final ResolvedJavaType subtype;

        public ConcreteSubtype(ResolvedJavaType context, ResolvedJavaType subtype) {
            this.context = context;
            this.subtype = subtype;
            assert subtype.isConcrete() : subtype.toString() + " : " + context.toString();
            assert !subtype.isArray() || subtype.getElementalType().isFinal() : subtype.toString() + " : " + context.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + context.hashCode();
            result = prime * result + subtype.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConcreteSubtype) {
                ConcreteSubtype other = (ConcreteSubtype) obj;
                return other.context.equals(context) && other.subtype.equals(subtype);
            }
            return false;
        }

        @Override
        public String toString() {
            return "ConcreteSubtype[context=" + context.toJavaName() + ", subtype=" + subtype.toJavaName() + "]";
        }
    }

    /**
     * An optimistic assumption that a given virtual method has a given unique implementation.
     */
    public static final class ConcreteMethod extends OptimisticAssumption {

        private static final long serialVersionUID = -7636746737947390059L;

        /**
         * A virtual (or interface) method whose unique implementation for the receiver type in
         * {@link #context} is {@link #impl}.
         */
        public final ResolvedJavaMethod method;

        /**
         * A receiver type.
         */
        public final ResolvedJavaType context;

        /**
         * The unique implementation of {@link #method} for {@link #context}.
         */
        public final ResolvedJavaMethod impl;

        public ConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType context, ResolvedJavaMethod impl) {
            this.method = method;
            this.context = context;
            this.impl = impl;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + method.hashCode();
            result = prime * result + context.hashCode();
            result = prime * result + impl.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConcreteMethod) {
                ConcreteMethod other = (ConcreteMethod) obj;
                return other.method.equals(method) && other.context.equals(context) && other.impl.equals(impl);
            }
            return false;
        }

        @Override
        public String toString() {
            return "ConcreteMethod[method=" + method.format("%H.%n(%p)%r") + ", context=" + context.toJavaName() + ", impl=" + impl.format("%H.%n(%p)%r") + "]";
        }
    }

    /**
     * An non-optimistic assumption that the bytecodes of a given method used during compilation
     * will not change. This kind of dependency may be used to invalidate and deoptimize compiled
     * code when:
     * <ul>
     * <li>one of its constituent methods is redefined or</li>
     * <li>a breakpoint is set in one of its constituent methods and the runtime only implements
     * breakpoint support in non-compiled code.
     * </ul>
     */
    public static final class MethodContents extends Assumption {

        private static final long serialVersionUID = -4821594103928571659L;

        public final ResolvedJavaMethod method;

        public MethodContents(ResolvedJavaMethod method) {
            this.method = method;
        }

        @Override
        public int hashCode() {
            return 31 + method.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodContents) {
                MethodContents other = (MethodContents) obj;
                return other.method.equals(method);
            }
            return false;
        }

        @Override
        public String toString() {
            return "MethodContents[method=" + method.format("%H.%n(%p)%r") + "]";
        }
    }

    /**
     * An optimistic assumption that a given call site's method handle did not change.
     */
    public static final class CallSiteTargetValue extends OptimisticAssumption {

        private static final long serialVersionUID = 1732459941784550371L;

        public final CallSite callSite;
        public final MethodHandle methodHandle;

        public CallSiteTargetValue(CallSite callSite, MethodHandle methodHandle) {
            this.callSite = callSite;
            this.methodHandle = methodHandle;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + callSite.hashCode();
            result = prime * result + methodHandle.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CallSiteTargetValue) {
                CallSiteTargetValue other = (CallSiteTargetValue) obj;
                return callSite.equals(other.callSite) && methodHandle.equals(other.methodHandle);
            }
            return false;
        }

        @Override
        public String toString() {
            return "CallSiteTargetValue[callSite=" + callSite + ", methodHandle=" + methodHandle + "]";
        }
    }

    private Assumption[] list;
    private int count;

    /**
     * Specifies whether {@link OptimisticAssumption}s can be made.
     */
    private boolean allowOptimisticAssumptions;

    public static final boolean ALLOW_OPTIMISTIC_ASSUMPTIONS = true;
    public static final boolean DONT_ALLOW_OPTIMISTIC_ASSUMPTIONS = false;

    /**
     * Creates an object for recording assumptions.
     *
     * @param allowOptimisticAssumptions specifies whether {@link OptimisticAssumption}s can be
     *            recorded in this object
     */
    public Assumptions(boolean allowOptimisticAssumptions) {
        this.allowOptimisticAssumptions = allowOptimisticAssumptions;
        list = new Assumption[4];
    }

    /**
     * Returns whether any assumptions have been registered.
     *
     * @return {@code true} if at least one assumption has been registered, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Determines whether {@link OptimisticAssumption}s can be made.
     */
    public boolean useOptimisticAssumptions() {
        return allowOptimisticAssumptions;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Assumptions) {
            Assumptions that = (Assumptions) obj;
            if (allowOptimisticAssumptions != that.allowOptimisticAssumptions || count != that.count) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                if (!list[i].equals(that.list[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public Iterator<Assumption> iterator() {
        return new Iterator<Assumptions.Assumption>() {

            int index;

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Assumption next() {
                if (index >= count) {
                    throw new NoSuchElementException();
                }
                return list[index++];
            }

            @Override
            public boolean hasNext() {
                return index < count;
            }
        };
    }

    /**
     * Records an assumption that the specified type has no finalizable subclasses.
     *
     * @param receiverType the type that is assumed to have no finalizable subclasses
     */
    public void recordNoFinalizableSubclassAssumption(ResolvedJavaType receiverType) {
        record(new NoFinalizableSubclass(receiverType));
    }

    /**
     * Records that {@code subtype} is the only concrete subtype in the class hierarchy below
     * {@code context}.
     *
     * @param context the root of the subtree of the class hierarchy that this assumptions is about
     * @param subtype the one concrete subtype
     */
    public void recordConcreteSubtype(ResolvedJavaType context, ResolvedJavaType subtype) {
        record(new ConcreteSubtype(context, subtype));
    }

    /**
     * Records that {@code impl} is the only possible concrete target for a virtual call to
     * {@code method} with a receiver of type {@code context}.
     *
     * @param method a method that is the target of a virtual call
     * @param context the receiver type of a call to {@code method}
     * @param impl the concrete method that is the only possible target for the virtual call
     */
    public void recordConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType context, ResolvedJavaMethod impl) {
        record(new ConcreteMethod(method, context, impl));
    }

    /**
     * Records that {@code method} was used during the compilation.
     *
     * @param method a method whose contents were used
     */
    public void recordMethodContents(ResolvedJavaMethod method) {
        record(new MethodContents(method));
    }

    public void record(Assumption assumption) {
        assert allowOptimisticAssumptions || !(assumption instanceof OptimisticAssumption) : "cannot make optimistic assumption: " + assumption;
        if (list == null) {
            list = new Assumption[4];
        } else {
            for (int i = 0; i < count; ++i) {
                if (assumption.equals(list[i])) {
                    return;
                }
            }
        }
        if (list.length == count) {
            Assumption[] newList = new Assumption[list.length * 2];
            for (int i = 0; i < list.length; ++i) {
                newList[i] = list[i];
            }
            list = newList;
        }
        list[count] = assumption;
        count++;
    }

    /**
     * Gets a copy of the assumptions recorded in this object as an array.
     */
    public Assumption[] getAssumptionsCopy() {
        return Arrays.copyOf(list, count);
    }

    /**
     * Copies assumptions recorded by another {@link Assumptions} object into this object.
     */
    public void record(Assumptions assumptions) {
        assert assumptions != this;
        for (int i = 0; i < assumptions.count; i++) {
            record(assumptions.list[i]);
        }
    }

    @Override
    public String toString() {
        return "Assumptions{optimistic=" + allowOptimisticAssumptions + ", assumptions=" + Arrays.asList(list).subList(0, count) + "}";
    }
}
