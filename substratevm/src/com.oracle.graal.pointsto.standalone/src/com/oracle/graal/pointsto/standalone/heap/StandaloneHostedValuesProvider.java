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

import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Standalone hosted-values provider that routes field reads through the standalone availability
 * policy.
 *
 * Static fields of runtime-only, denied, failed, or not-yet-started build-time-initialization
 * classes stay unavailable to shadow-heap snapshotting. Guest constant reflection can also
 * legitimately return a Java {@code null} reference to mean "the field value is not available
 * right now". This provider keeps both cases inside one standalone-owned layer by converting them
 * into lazy {@link ValueSupplier} values.
 */
public final class StandaloneHostedValuesProvider extends HostedValuesProvider {
    private final StandaloneFieldValueAvailabilitySupport availabilitySupport;

    public StandaloneHostedValuesProvider(AnalysisMetaAccess metaAccess, AnalysisUniverse universe, StandaloneFieldValueAvailabilitySupport availabilitySupport) {
        super(metaAccess, universe);
        this.availabilitySupport = availabilitySupport;
    }

    @Override
    public ValueSupplier<JavaConstant> readFieldValue(AnalysisField field, JavaConstant receiver) {
        return availabilitySupport.readFieldValue(field, () -> doReadValue(field, receiver));
    }

    /**
     * Object scanning and reporting still use the replacement path directly, so route that path
     * through the standalone null-aware supplier as well instead of the base-class eager helper.
     */
    @Override
    public JavaConstant readFieldValueWithReplacement(AnalysisField field, JavaConstant receiver) {
        ValueSupplier<JavaConstant> valueSupplier = readFieldValue(field, receiver);
        AnalysisError.guarantee(valueSupplier.isAvailable(), "Value is not yet available for %s", field);
        return replaceObject(valueSupplier.get());
    }

    private static JavaConstant doReadValue(AnalysisField field, JavaConstant receiver) {
        return GuestAccess.get().getProviders().getConstantReflection().readFieldValue(field.wrapped, receiver);
    }
}
