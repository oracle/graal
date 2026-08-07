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
package com.oracle.svm.core.util;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.shared.util.VMError;

/**
 * A map whose keys have a hosted representation and a separate representation in the image.
 * <p>
 * Some metadata is collected before its runtime keys are available. This class keeps those entries
 * in a hosted-only map and copies them to a runtime map when {@link #seal()} is called. The hosted
 * key converter is also discarded at that point, so neither hosted keys nor the converter can
 * accidentally become part of the image heap.
 */
public final class DeferredKeyMap<H, R, V> {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private EconomicMap<H, V> hostedMap;
    @Platforms(Platform.HOSTED_ONLY.class) //
    private Function<H, R> runtimeKeyConverter;

    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl", availability = AfterCompilation.class) //
    private EconomicMap<R, V> runtimeMap;

    public DeferredKeyMap(Function<H, R> runtimeKeyConverter) {
        this(Equivalence.DEFAULT, runtimeKeyConverter);
    }

    public DeferredKeyMap(Equivalence hostedKeyEquivalence, Function<H, R> runtimeKeyConverter) {
        this.hostedMap = EconomicMap.create(hostedKeyEquivalence);
        this.runtimeKeyConverter = runtimeKeyConverter;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public V getHosted(H key) {
        VMError.guarantee(hostedMap != null, "The hosted map has already been sealed");
        return hostedMap.get(key);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public V putHosted(H key, V value) {
        VMError.guarantee(hostedMap != null, "The hosted map has already been sealed");
        return hostedMap.put(key, value);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public V putHostedIfAbsent(H key, V value) {
        VMError.guarantee(hostedMap != null, "The hosted map has already been sealed");
        return hostedMap.putIfAbsent(key, value);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public MapCursor<H, V> getHostedEntries() {
        VMError.guarantee(hostedMap != null, "The hosted map has already been sealed");
        return hostedMap.getEntries();
    }

    public V getRuntime(R key) {
        VMError.guarantee(runtimeMap != null, "The runtime map has not been sealed");
        return runtimeMap.get(key);
    }

    public MapCursor<R, V> getRuntimeEntries() {
        VMError.guarantee(runtimeMap != null, "The runtime map has not been sealed");
        return runtimeMap.getEntries();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Iterable<V> getValues() {
        return hostedMap != null ? hostedMap.getValues() : runtimeMap.getValues();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void seal() {
        VMError.guarantee(hostedMap != null && runtimeMap == null, "The map should only be sealed once");
        runtimeMap = EconomicMap.create();
        MapCursor<H, V> cursor = hostedMap.getEntries();
        while (cursor.advance()) {
            runtimeMap.put(runtimeKeyConverter.apply(cursor.getKey()), cursor.getValue());
        }
        hostedMap = null;
        runtimeKeyConverter = null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isSealed() {
        return hostedMap == null;
    }
}
