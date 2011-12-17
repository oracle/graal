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
package com.sun.cri.ci;

import java.io.*;
import java.util.*;

import com.sun.cri.ri.*;

/**
 * Class for recording optimistic assumptions made during compilation.
 * Recorded assumption can be visited for subsequent processing using
 * an implementation of the {@link CiAssumptionProcessor} interface.
 */
public final class CiAssumptions implements Serializable, Iterable<CiAssumptions.Assumption> {

    public abstract static class Assumption implements Serializable {
    }

    /**
     * An assumption about a unique subtype of a given type.
     */
    public static final class ConcreteSubtype extends Assumption {
        /**
         * Type the assumption is made about.
         */
        public final RiResolvedType context;

        /**
         * Assumed unique concrete sub-type of the context type.
         */
        public final RiResolvedType subtype;

        public ConcreteSubtype(RiResolvedType context, RiResolvedType subtype) {
            this.context = context;
            this.subtype = subtype;
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
    }

    /**
     * An assumption about a unique implementation of a virtual method.
     */
    public static final class ConcreteMethod extends Assumption {

        /**
         * A virtual (or interface) method whose unique implementation for the receiver type
         * in {@link #context} is {@link #impl}.
         */
        public final RiResolvedMethod method;

        /**
         * A receiver type.
         */
        public final RiResolvedType context;

        /**
         * The unique implementation of {@link #method} for {@link #context}.
         */
        public final RiResolvedMethod impl;

        public ConcreteMethod(RiResolvedMethod method, RiResolvedType context, RiResolvedMethod impl) {
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
    }

    /**
     * Array with the assumptions. This field is directly accessed from C++ code in the Graal/HotSpot implementation.
     */
    private Assumption[] list;

    private int count;

    /**
     * Returns whether any assumptions have been registered.
     * @return {@code true} if at least one assumption has been registered, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public Iterator<Assumption> iterator() {
        return new Iterator<CiAssumptions.Assumption>() {
            int index;
            public void remove() {
                throw new UnsupportedOperationException();
            }
            public Assumption next() {
                if (index >= count) {
                    throw new NoSuchElementException();
                }
                return list[index++];
            }
            public boolean hasNext() {
                return index < count;
            }
        };
    }

    /**
     * Records an assumption that the specified type has no finalizable subclasses.
     *
     * @param receiverType the type that is assumed to have no finalizable subclasses
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordNoFinalizableSubclassAssumption(RiResolvedType receiverType) {
        return false;
    }

    /**
     * Records that {@code subtype} is the only concrete subtype in the class hierarchy below {@code context}.
     * @param context the root of the subtree of the class hierarchy that this assumptions is about
     * @param subtype the one concrete subtype
     */
    public void recordConcreteSubtype(RiResolvedType context, RiResolvedType subtype) {
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
    public void recordConcreteMethod(RiResolvedMethod method, RiResolvedType context, RiResolvedMethod impl) {
        record(new ConcreteMethod(method, context, impl));
    }

    private void record(Assumption assumption) {
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

}
