/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;

import jdk.vm.ci.code.CallingConvention;

/**
 * Augments the {@link SubstrateCallingConventionKind} with additional flags, to avoid duplications
 * of the enum values. Use {@link SubstrateCallingConventionKind#toType} to get an instance.
 */
public final class SubstrateCallingConventionType implements CallingConvention.Type {

    public final SubstrateCallingConventionKind kind;
    /** Determines if this is a request for the outgoing argument locations at a call site. */
    public final boolean outgoing;

    public final AssignedLocation[] fixedParameterAssignment;
    public final AssignedLocation[] returnSaving;

    static final EnumMap<SubstrateCallingConventionKind, SubstrateCallingConventionType> outgoingTypes;
    static final EnumMap<SubstrateCallingConventionKind, SubstrateCallingConventionType> incomingTypes;

    static {
        outgoingTypes = new EnumMap<>(SubstrateCallingConventionKind.class);
        incomingTypes = new EnumMap<>(SubstrateCallingConventionKind.class);
        for (SubstrateCallingConventionKind kind : SubstrateCallingConventionKind.values()) {
            if (kind.isCustom()) {
                // Custom conventions cannot be enumerated this way
                continue;
            }
            outgoingTypes.put(kind, new SubstrateCallingConventionType(kind, true, null, null));
            incomingTypes.put(kind, new SubstrateCallingConventionType(kind, false, null, null));
        }
    }

    private SubstrateCallingConventionType(SubstrateCallingConventionKind kind, boolean outgoing, AssignedLocation[] fixedRegisters, AssignedLocation[] returnSaving) {
        this.kind = kind;
        this.outgoing = outgoing;
        this.fixedParameterAssignment = fixedRegisters;
        this.returnSaving = returnSaving;
    }

    /**
     * Create a calling convention with custom parameter/return assignment. The return value might
     * get buffered, see {@link SubstrateCallingConventionType#usesReturnBuffer}.
     *
     * Methods using this calling convention should implement {@link CustomCallingConventionMethod}.
     */
    public static SubstrateCallingConventionType makeCustom(boolean outgoing, AssignedLocation[] parameters, AssignedLocation[] returns) {
        return new SubstrateCallingConventionType(SubstrateCallingConventionKind.Custom, outgoing, Objects.requireNonNull(parameters), Objects.requireNonNull(returns));
    }

    public boolean nativeABI() {
        return kind.isNativeABI();
    }

    public boolean customABI() {
        return kind.isCustom();
    }

    /**
     * In the case of an outgoing call with multiple returned values, or if the return is more than
     * one quadword long, there is no way to represent them in Java and the returns need special
     * treatment. This is done using an extra prefix argument which is interpreted as a pointer to a
     * buffer where the values will be stored.
     *
     * This is currently only allowed in custom conventions. Some ABIs allow return values which
     * span multiple registers. This value thus has to be moved to the heap before returning to
     * Java.
     */
    public boolean usesReturnBuffer() {
        return outgoing && returnSaving != null && returnSaving.length >= 2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubstrateCallingConventionType that = (SubstrateCallingConventionType) o;
        return outgoing == that.outgoing && kind == that.kind && Arrays.equals(fixedParameterAssignment, that.fixedParameterAssignment) && Arrays.equals(returnSaving, that.returnSaving);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, outgoing, Arrays.hashCode(fixedParameterAssignment), Arrays.hashCode(returnSaving));
    }
}
