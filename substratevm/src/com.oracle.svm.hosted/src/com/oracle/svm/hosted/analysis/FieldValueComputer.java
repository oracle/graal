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
 * describe the field state that must be injected into the static analysis. An unavailable primitive
 * field uses its declared primitive state. Once the value becomes available, the field is handled
 * through {@link FieldValueInterceptionSupport} ({@code readFieldValue}) rather than a replacement
 * computation.
 * <p>
 * Making the value available does not automatically rescan the field. Late heap verification by
 * {@link HeapSnapshotVerifier} can materialize, structurally scan, and include an object value
 * without guaranteeing that its reachability callbacks are executed. Internal users that require
 * these callbacks must arrange an explicit {@link ImageHeapScanner#rescanField} at a supported phase
 * after the value becomes available.
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
