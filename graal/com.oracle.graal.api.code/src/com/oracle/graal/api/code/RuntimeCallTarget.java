/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * The name, signature and calling convention of a call from compiled code to the runtime. The
 * target of such a call may be a leaf stub or a call into the runtime code proper.
 */
public interface RuntimeCallTarget extends InvokeTarget {

    /**
     * The name and signature of a runtime call.
     */
    public static class Descriptor {

        private final String name;
        private final boolean hasSideEffect;
        private final Class resultType;
        private final Class[] argumentTypes;

        public Descriptor(String name, boolean hasSideEffect, Class resultType, Class... argumentTypes) {
            this.name = name;
            this.hasSideEffect = hasSideEffect;
            this.resultType = resultType;
            this.argumentTypes = argumentTypes;
        }

        /**
         * Gets the name of this runtime call.
         */
        public String getName() {
            return name;
        }

        /**
         * Determines if this call changes state visible to other threads. Such calls denote
         * boundaries across which deoptimization points cannot be moved.
         */
        public boolean hasSideEffect() {
            return hasSideEffect;
        }

        /**
         * Gets the return kind of this runtime call.
         */
        public Class getResultType() {
            return resultType;
        }

        /**
         * Gets the argument kinds of this runtime call.
         */
        public Class[] getArgumentTypes() {
            return argumentTypes.clone();
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Descriptor) {
                Descriptor nas = (Descriptor) obj;
                return nas.name.equals(name) && nas.resultType.equals(resultType) && Arrays.equals(nas.argumentTypes, argumentTypes);
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append('(');
            String sep = "";
            for (Class arg : argumentTypes) {
                sb.append(sep).append(arg.getSimpleName());
                sep = ",";
            }
            return sb.append(')').append(resultType.getSimpleName()).toString();
        }
    }

    CallingConvention getCallingConvention();

    /**
     * Returns the maximum absolute offset of PC relative call to this stub from any position in the
     * code cache or -1 when not applicable. Intended for determining the required size of
     * address/offset fields.
     */
    long getMaxCallTargetOffset();

    Descriptor getDescriptor();
}
