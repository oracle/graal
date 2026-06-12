/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.heap;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.standalone.StandaloneHost;
import com.oracle.graal.pointsto.standalone.StandaloneHost.ClassInitializationOutcome;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Standalone-owned policy layer for deciding whether a field value is currently available for
 * shadow-heap snapshotting.
 *
 * Static fields may only use shadow-heap values after standalone-managed build-time initialization
 * completed successfully for the declaring class. Runtime-only, denied, failed, or not-yet
 * started classes remain unavailable to snapshotting. Direct reads that intentionally stay on the
 * original provider path use {@link #getStaticFieldReadPolicy(AnalysisField)} to choose their
 * source of truth.
 *
 * Guest constant reflection may also report a Java {@code null} reference to mean that a guest
 * field value is not available yet. This helper folds that temporary-null state under the same
 * availability model by exposing it through {@link ValueSupplier}.
 */
public final class StandaloneFieldValueAvailabilitySupport {
    /**
     * Describes how a direct static field read should obtain its value under standalone-managed
     * class initialization.
     */
    public enum StaticFieldReadPolicy {
        /**
         * Read the value from the standalone shadow heap after build-time initialization completed.
         */
        SHADOW_HEAP,
        /**
         * Keep the original constant-reflection provider semantics for runtime-only or failed
         * build-time initialization.
         */
        ORIGINAL_PROVIDER
    }

    private final StandaloneHost host;

    public StandaloneFieldValueAvailabilitySupport(StandaloneHost host) {
        this.host = host;
    }

    /**
     * Returns the source a direct static read may use.
     */
    public StaticFieldReadPolicy getStaticFieldReadPolicy(AnalysisField field) {
        AnalysisError.guarantee(field.isStatic(), "Static field read policy requested for non-static field %s", field);
        ClassInitializationOutcome outcome = host.getClassInitializationOutcome(field.getDeclaringClass());
        AnalysisError.guarantee(outcome != ClassInitializationOutcome.PENDING, "Static field read policy requested before class-initialization outcome was available for %s", field);
        return switch (outcome) {
            case INITIALIZED -> StaticFieldReadPolicy.SHADOW_HEAP;
            case RUNTIME_ONLY, FAILED -> StaticFieldReadPolicy.ORIGINAL_PROVIDER;
            case PENDING -> throw AnalysisError.shouldNotReachHere("Unreachable after pending class-initialization outcome check.");
        };
    }

    /**
     * Creates a standalone field-value supplier that combines class-initialization availability
     * with the existing guest-null retry path.
     */
    public ValueSupplier<JavaConstant> readFieldValue(AnalysisField field, Supplier<JavaConstant> rawReader) {
        AtomicReference<JavaConstant> materialized = new AtomicReference<>();
        if (isValueAvailable(field, rawReader, materialized)) {
            return ValueSupplier.eagerValue(materialized.get());
        }
        Supplier<JavaConstant> valueSupplier = () -> materializeValue(field, rawReader, materialized);
        BooleanSupplier availabilitySupplier = () -> isValueAvailable(field, rawReader, materialized);
        return ValueSupplier.lazyValue(valueSupplier, availabilitySupplier);
    }

    private JavaConstant materializeValue(AnalysisField field, Supplier<JavaConstant> rawReader, AtomicReference<JavaConstant> materialized) {
        AnalysisError.guarantee(staticFieldSnapshottingAllowed(field), "Value is not yet available for %s", field);
        JavaConstant value = probeValue(rawReader, materialized);
        AnalysisError.guarantee(value != null, "Value is not yet available for %s", field);
        return value;
    }

    private boolean isValueAvailable(AnalysisField field, Supplier<JavaConstant> rawReader, AtomicReference<JavaConstant> materialized) {
        if (!staticFieldSnapshottingAllowed(field)) {
            return false;
        }
        return probeValue(rawReader, materialized) != null;
    }

    private boolean staticFieldSnapshottingAllowed(AnalysisField field) {
        field.beforeFieldValueAccess();
        if (!field.isStatic()) {
            return true;
        }
        return getClassInitializationOutcome(field).allowsStaticFieldSnapshotting();
    }

    private ClassInitializationOutcome getClassInitializationOutcome(AnalysisField field) {
        ClassInitializationOutcome outcome = host.getClassInitializationOutcome(field.getDeclaringClass());
        if (outcome == ClassInitializationOutcome.PENDING) {
            return host.registerPendingStaticFieldRead(field);
        }
        return outcome;
    }

    private static JavaConstant probeValue(Supplier<JavaConstant> rawReader, AtomicReference<JavaConstant> materialized) {
        JavaConstant value = materialized.get();
        if (value != null) {
            return value;
        }
        value = rawReader.get();
        if (value != null) {
            materialized.compareAndSet(null, value);
        }
        return value;
    }
}
