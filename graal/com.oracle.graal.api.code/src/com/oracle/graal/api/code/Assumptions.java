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

import static com.oracle.graal.api.meta.MetaUtil.*;

import java.io.*;
import java.lang.invoke.*;
import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Class for recording optimistic assumptions made during compilation.
 */
public final class Assumptions implements Serializable, Iterable<Assumptions.Assumption> {

    private static final long serialVersionUID = 5152062717588239131L;

    /**
     * Abstract base class for assumptions.
     */
    public abstract static class Assumption implements Serializable {

        private static final long serialVersionUID = -1936652569665112915L;
    }

    public static final class NoFinalizableSubclass extends Assumption {

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
                return other.receiverType == receiverType;
            }
            return false;
        }

        @Override
        public String toString() {
            return "NoFinalizableSubclass[receiverType=" + toJavaName(receiverType) + "]";
        }

    }

    /**
     * An assumption about a unique subtype of a given type.
     */
    public static final class ConcreteSubtype extends Assumption {

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
            assert !subtype.isInterface() : subtype.toString() + " : " + context.toString();
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
                return other.context == context && other.subtype == subtype;
            }
            return false;
        }

        @Override
        public String toString() {
            return "ConcreteSubtype[context=" + toJavaName(context) + ", subtype=" + toJavaName(subtype) + "]";
        }
    }

    /**
     * An assumption about a unique implementation of a virtual method.
     */
    public static final class ConcreteMethod extends Assumption {

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
                return other.method == method && other.context == context && other.impl == impl;
            }
            return false;
        }

        @Override
        public String toString() {
            return "ConcreteMethod[method=" + format("%H.%n(%p)", method) + ", context=" + toJavaName(context) + ", impl=" + format("%H.%n(%p)", impl) + "]";
        }
    }

    /**
     * An assumption that specified that a method was used during the compilation.
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
            if (obj instanceof ConcreteMethod) {
                ConcreteMethod other = (ConcreteMethod) obj;
                return other.method == method;
            }
            return false;
        }

        @Override
        public String toString() {
            return "MethodContents[method=" + format("%H.%n(%p)", method) + "]";
        }
    }

    /**
     * Assumption that a call site's method handle did not change.
     */
    public static final class CallSiteTargetValue extends Assumption {

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
                return other.callSite == callSite && other.methodHandle == methodHandle;
            }
            return false;
        }

        @Override
        public String toString() {
            return "CallSiteTargetValue[callSite=" + callSite + ", methodHandle=" + methodHandle + "]";
        }
    }

    /**
     * Array with the assumptions. This field is directly accessed from C++ code in the
     * Graal/HotSpot implementation.
     */
    private Assumption[] list;
    private boolean useOptimisticAssumptions;
    private int count;

    public Assumptions(boolean useOptimisticAssumptions) {
        this.useOptimisticAssumptions = useOptimisticAssumptions;
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

    public boolean useOptimisticAssumptions() {
        return useOptimisticAssumptions;
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
        assert useOptimisticAssumptions;
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
        assert useOptimisticAssumptions;
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
        assert useOptimisticAssumptions;
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

    public Assumption[] getAssumptions() {
        return list;
    }

    public void record(Assumptions assumptions) {
        for (int i = 0; i < assumptions.count; i++) {
            record(assumptions.list[i]);
        }
    }

    public void print(PrintStream out) {
        List<Assumption> nonNullList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length; ++i) {
                Assumption a = list[i];
                if (a != null) {
                    nonNullList.add(a);
                }
            }
        }

        out.printf("%d assumptions:\n", nonNullList.size());
        for (Assumption a : nonNullList) {
            out.println(a.toString());
        }
    }
}
