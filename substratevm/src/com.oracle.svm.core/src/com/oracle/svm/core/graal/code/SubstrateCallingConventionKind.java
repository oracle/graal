/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.code;

public enum SubstrateCallingConventionKind {
    /**
     * A Java-to-Java call. The majority of calls are like that.
     */
    Java,
    /**
     * A method whose only parameter is passed in the register also used to return values. This
     * allows tail calls that take the value returned by the caller as the parameter.
     */
    ForwardReturnValue,
    /**
     * A call between Java and native code, which must use the platform ABI.
     */
    Native,
    /**
     * A call between Java and native code, which uses a method-specific ABI. This can be used to
     * pass values which are not easily representable, e.g. a structure which is split across
     * multiple registers.
     *
     * Methods using this calling convention should implement {@link CustomCallingConventionMethod}.
     */
    Custom;

    public SubstrateCallingConventionType toType(boolean outgoing) {
        if (isCustom()) {
            throw new IllegalArgumentException("Custom calling conventions cannot be created using toType.");
        }
        return (outgoing ? SubstrateCallingConventionType.outgoingTypes : SubstrateCallingConventionType.incomingTypes).get(this);
    }

    public boolean isCustom() {
        return this == Custom;
    }

    public boolean isNativeABI() {
        return this == Native || isCustom();
    }
}
