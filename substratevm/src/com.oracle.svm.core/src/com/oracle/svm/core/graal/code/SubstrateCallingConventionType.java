/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Augments the {@link SubstrateCallingConventionKind} with additional flags, to avoid duplications
 * of the enum values. Use {@link SubstrateCallingConventionKind#toType} to get an instance.
 */
public final class SubstrateCallingConventionType implements CallingConvention.Type {

    public enum SubstrateCallingConventionArgumentKind {
        /**
         * Denotes an argument location that is killed by the call.
         */
        NORMAL,

        /**
         * Denotes an argument location whose value may be updated by the call. That is, this is a
         * pass-by-reference value to allow for the callee to return multiple values.
         */
        VALUE_REFERENCE,

        /**
         * Denotes an argument location whose value is unchanged by the call. That is, the location
         * is guaranteed to hold the same value upon return from the call.
         */
        IMMUTABLE,
    }

    public final SubstrateCallingConventionKind kind;
    /** Determines if this is a request for the outgoing argument locations at a call site. */
    public final boolean outgoing;

    public final AssignedLocation[] fixedParameterAssignment;
    public final AssignedLocation[] returnSaving;
    public final SubstrateCallingConventionArgumentKind[] parameterKinds;

    public final boolean destroysCallerSavedRegisters;
    public final boolean mayBeVarargs;

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
            outgoingTypes.put(kind, new SubstrateCallingConventionType(kind, true));
            incomingTypes.put(kind, new SubstrateCallingConventionType(kind, false));
        }
    }

    private SubstrateCallingConventionType(SubstrateCallingConventionKind kind, boolean outgoing) {
        this(kind, outgoing, AssignedLocation.EMPTY_ARRAY, AssignedLocation.EMPTY_ARRAY,
                        new SubstrateCallingConventionArgumentKind[0], true, true);
    }

    private SubstrateCallingConventionType(SubstrateCallingConventionKind kind, boolean outgoing, AssignedLocation[] fixedRegisters, AssignedLocation[] returnSaving,
                    SubstrateCallingConventionArgumentKind[] parameterKinds, boolean destroysCallerSavedRegisters, boolean mayBeVarargs) {
        this.kind = kind;
        this.outgoing = outgoing;
        this.fixedParameterAssignment = fixedRegisters;
        this.returnSaving = returnSaving;
        this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
        this.mayBeVarargs = mayBeVarargs;
        this.parameterKinds = parameterKinds;
    }

    /**
     * Create a calling convention with custom parameter/return assignment. The return value might
     * get buffered, see {@link SubstrateCallingConventionType#usesReturnBuffer}.
     *
     * Methods using this calling convention should implement {@link CustomCallingConventionMethod}.
     */
    public static SubstrateCallingConventionType makeCustom(boolean outgoing, AssignedLocation[] parameters, AssignedLocation[] returns) {
        return new SubstrateCallingConventionType(SubstrateCallingConventionKind.Custom, outgoing, Objects.requireNonNull(parameters), Objects.requireNonNull(returns),
                        new SubstrateCallingConventionArgumentKind[parameters.length], true, true);
    }

    public static SubstrateCallingConventionType makeCustom(boolean outgoing, AssignedLocation[] parameters, AssignedLocation[] returns,
                    SubstrateCallingConventionArgumentKind[] parameterKinds, boolean destroysCallerSavedRegisters, boolean maybeVarargs) {
        return new SubstrateCallingConventionType(SubstrateCallingConventionKind.Custom, outgoing, Objects.requireNonNull(parameters), Objects.requireNonNull(returns),
                        parameterKinds, destroysCallerSavedRegisters, maybeVarargs);
    }

    public boolean nativeABI() {
        return kind.isNativeABI();
    }

    public boolean customABI() {
        return kind.isCustom();
    }

    /**
     * Indicates whether the call target is a varargs method, which may require special handling due
     * to architecture-specific requirements. For example, on AMD64, a varargs method requires an
     * additional general-purpose register to track the number of registers used for passing
     * floating-point values.
     */
    public boolean mayBeVarargs() {
        return mayBeVarargs;
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

    public boolean destroysCallerSavedRegisters() {
        return destroysCallerSavedRegisters;
    }

    /**
     * Returns an array of additional return values for a method call. A parameter is considered an
     * additional return if it is a {@link SubstrateCallingConventionArgumentKind#VALUE_REFERENCE}
     * and is not the same as the primary return value.
     *
     * This method is only relevant when the calling convention does not destroy caller-saved
     * registers. In such cases, it returns an array of values that are considered additional
     * returns. If the calling convention destroys caller-saved registers, this method returns an
     * empty array.
     */
    public Value[] getAdditionalReturns(Value result, Value[] parameters) {
        if (destroysCallerSavedRegisters) {
            return Value.NO_VALUES;
        }
        List<Value> additionalReturns = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            if (parameterKinds[i] == SubstrateCallingConventionArgumentKind.VALUE_REFERENCE && !parameters[i].equals(result)) {
                additionalReturns.add(parameters[i]);
            }
        }
        return additionalReturns.toArray(Value.NO_VALUES);
    }

    /**
     * Returns an array of registers that are considered killed (i.e., their values are not
     * preserved) across a method call, given a list of candidate registers.
     *
     * If the calling convention destroys caller-saved registers, this method returns an empty
     * array. Otherwise, it filters out the registers that are used as non-normal parameters (i.e.,
     * parameters with a {@link SubstrateCallingConventionArgumentKind} other than
     * {@link SubstrateCallingConventionArgumentKind#NORMAL}) and returns the remaining registers as
     * an array of {@link Value} objects.
     */
    public Value[] getKilledRegister(List<Register> allCandidates) {
        if (destroysCallerSavedRegisters) {
            return Value.NO_VALUES;
        }
        List<Register> allCandidatesFiltered = new ArrayList<>(allCandidates);
        for (int i = 0; i < fixedParameterAssignment.length; i++) {
            if (parameterKinds[i] != SubstrateCallingConventionArgumentKind.NORMAL) {
                allCandidatesFiltered.remove(fixedParameterAssignment[i].register());
            }
        }
        return allCandidatesFiltered.stream().map(Register::asValue).toArray(Value[]::new);
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
        return outgoing == that.outgoing && kind == that.kind && Arrays.equals(fixedParameterAssignment, that.fixedParameterAssignment) && Arrays.equals(returnSaving, that.returnSaving) &&
                        Arrays.equals(parameterKinds, that.parameterKinds) && destroysCallerSavedRegisters == that.destroysCallerSavedRegisters && mayBeVarargs == that.mayBeVarargs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, outgoing, Arrays.hashCode(fixedParameterAssignment), Arrays.hashCode(returnSaving), Arrays.hashCode(parameterKinds), destroysCallerSavedRegisters, mayBeVarargs);
    }
}
