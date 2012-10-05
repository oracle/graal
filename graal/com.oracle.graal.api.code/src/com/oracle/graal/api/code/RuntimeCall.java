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
 * The name, signature and calling convention of a call from compiled code to the runtime.
 * The target of such a call may be a leaf stub or a call into the runtime code proper.
 */
public interface RuntimeCall {

    /**
     * The name and signature of a runtime call.
     */
    public static class Descriptor {
        private final String name;
        private final Kind resultKind;
        private final Kind[] argumentKinds;

        public Descriptor(String name, Kind resultKind, Kind... args) {
            this.name = name;
            this.resultKind = resultKind;
            this.argumentKinds = args;
        }

        /**
         * Gets the name of this runtime call.
         */
        public String getName() {
            return name;
        }

        /**
         * Gets the return kind of this runtime call.
         */
        public Kind getResultKind() {
            return resultKind;
        }

        /**
         * Gets the argument kinds of this runtime call.
         */
        public Kind[] getArgumentKinds() {
            return argumentKinds.clone();
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Descriptor) {
                Descriptor nas = (Descriptor) obj;
                return nas.name.equals(name) && nas.resultKind.equals(resultKind) && Arrays.equals(nas.argumentKinds, argumentKinds);
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append('(');
            String sep = "";
            for (Kind arg : argumentKinds) {
                sb.append(sep).append(arg);
                sep = ",";
            }
            return sb.append(')').append(resultKind).toString();
        }
    }

    CallingConvention getCallingConvention();

    /**
     * Determines if this call changes state visible to other threads.
     * Such calls denote boundaries across which deoptimization
     * points cannot be moved.
     */
    boolean hasSideEffect();

    /**
     * Returns the maximum absolute offset of PC relative call to this stub from any position in the code cache or -1
     * when not applicable. Intended for determining the required size of address/offset fields.
     */
    long getMaxCallTargetOffset();

    Descriptor getDescriptor();
}
