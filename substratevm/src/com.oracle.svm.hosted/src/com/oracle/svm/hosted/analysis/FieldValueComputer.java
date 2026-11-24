/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Describes a hosted field whose value is unavailable until {@link #isAvailable()} returns
 * {@code true}. While an object value is unavailable, {@link #types()} and {@link #canBeNull()}
 * supply value bounds and nullability for a conservative analysis summary. Open bounds must also
 * cover unknown extension types; later hosted availability does not narrow that summary. Tracked
 * primitive fields receive unconstrained values. Available values are read through
 * {@link FieldValueInterceptionSupport} ({@code readFieldValue}).
 * <p>
 * Availability is polled rather than notified. Becoming available does not enqueue field
 * processing; a later scan, explicit {@link ImageHeapScanner#rescanRoot static-root rescan},
 * {@link ImageHeapScanner#rescanField instance-field rescan}, or {@link HeapSnapshotVerifier heap
 * verification} must revisit the field. Object values then follow normal reachability processing,
 * even if an equal speculative snapshot exists. Clients that require earlier processing must
 * request an explicit rescan.
 * <p>
 * Unlike a {@link FieldValueTransformer}, this class does not compute a replacement value and does
 * not introduce a transformed-value cache. The ordinary {@link ImageHeapConstant image-heap
 * snapshot} may still retain a field value after it has been materialized.
 * <p>
 * This class is the internal representation of {@link UnknownObjectField} and
 * {@link UnknownPrimitiveField}.
 */
public final class FieldValueComputer {
    private final BooleanSupplier availability;
    private final List<ResolvedJavaType> types;
    private final boolean canBeNull;

    public FieldValueComputer(BooleanSupplier availability, List<ResolvedJavaType> types, boolean canBeNull) {
        this.availability = availability;
        this.types = types;
        this.canBeNull = canBeNull;
    }

    public boolean isAvailable() {
        return availability.getAsBoolean();
    }

    public List<ResolvedJavaType> types() {
        return types;
    }

    public boolean canBeNull() {
        return canBeNull;
    }
}
