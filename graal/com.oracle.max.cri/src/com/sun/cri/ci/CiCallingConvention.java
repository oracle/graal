/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.cri.ri.*;


/**
 * A calling convention describes the locations in which the arguments for a call are placed.
 */
public class CiCallingConvention {

    /**
     * Constants denoting the type of a call for which a calling convention is
     * {@linkplain RiRegisterConfig#getCallingConvention(Type, CiKind[], CiTarget, boolean) requested}.
     */
    public enum Type {
        /**
         * A request for the outgoing argument locations at a call site to Java code.
         */
        JavaCall(true),

        /**
         * A request for the incoming argument locations.
         */
        JavaCallee(false),

        /**
         * A request for the outgoing argument locations at a call site to the runtime (which may be Java or native code).
         */
        RuntimeCall(true),

        /**
         * A request for the outgoing argument locations at a call site to
         * external native code that complies with the platform ABI.
         */
        NativeCall(true);

        /**
         * Determines if this is a request for the outgoing argument locations at a call site.
         */
        public final boolean out;

        public static final Type[] VALUES = values();

        private Type(boolean out) {
            this.out = out;
        }
    }

    /**
     * The amount of stack space (in bytes) required for the stack-based arguments of the call.
     */
    public final int stackSize;

    /**
     * The locations in which the arguments are placed. This array ordered by argument index.
     */
    public final CiValue[] locations;

    public CiCallingConvention(CiValue[] locations, int stackSize) {
        this.locations = locations;
        this.stackSize = stackSize;
        assert verify();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("CallingConvention[");
        for (CiValue op : locations) {
            result.append(op.toString()).append(" ");
        }
        result.append("]");
        return result.toString();
    }

    private boolean verify() {
        for (int i = 0; i < locations.length; i++) {
            CiValue location = locations[i];
            assert location.isStackSlot() || location.isRegister();
        }
        return true;
    }
}
