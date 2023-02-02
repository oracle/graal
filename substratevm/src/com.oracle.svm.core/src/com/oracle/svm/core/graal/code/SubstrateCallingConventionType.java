/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;

import jdk.vm.ci.code.CallingConvention;

/**
 * Augments the {@link SubstrateCallingConventionKind} with additional flags, to avoid duplications
 * of the enum values. Use {@link SubstrateCallingConventionKind#toType} to get an instance.
 */
public final class SubstrateCallingConventionType implements CallingConvention.Type {
    public final SubstrateCallingConventionKind kind;
    /** Determines if this is a request for the outgoing argument locations at a call site. */
    public final boolean outgoing;

    static final EnumMap<SubstrateCallingConventionKind, SubstrateCallingConventionType> outgoingTypes;
    static final EnumMap<SubstrateCallingConventionKind, SubstrateCallingConventionType> incomingTypes;

    static {
        outgoingTypes = new EnumMap<>(SubstrateCallingConventionKind.class);
        incomingTypes = new EnumMap<>(SubstrateCallingConventionKind.class);
        for (SubstrateCallingConventionKind kind : SubstrateCallingConventionKind.values()) {
            outgoingTypes.put(kind, new SubstrateCallingConventionType(kind, true));
            incomingTypes.put(kind, new SubstrateCallingConventionType(kind, false));
        }
    }

    private SubstrateCallingConventionType(SubstrateCallingConventionKind kind, boolean outgoing) {
        this.kind = kind;
        this.outgoing = outgoing;
    }

    public boolean nativeABI() {
        return kind == SubstrateCallingConventionKind.Native;
    }
}
