/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.substitutions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicMarkableReference;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.SubstituteLoadLookup;
import com.oracle.svm.shared.util.SubstrateUtil;

import sun.util.resources.OpenListResourceBundle;

@TargetClass(value = sun.util.resources.ParallelListResourceBundle.class, onlyWith = SubstituteLoadLookup.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_util_resources_ParallelListResourceBundle_SubstituteLoadLookup {

    @Alias private ConcurrentMap<String, Object> lookup;
    @Alias private AtomicMarkableReference<Object[][]> parallelContents;

    @Alias
    protected native Object[][] getContents();

    @Substitute
    private void setParallelContents(OpenListResourceBundle rb) {
        /* Extracted contents already include supplementary contents; keep getContents unreachable. */
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(getClass()).isRuntimeLoaded()) {
            if (rb == null) {
                parallelContents.compareAndSet(null, null, false, true);
            } else {
                parallelContents.compareAndSet(null, SubstrateUtil.cast(rb, Target_sun_util_resources_OpenListResourceBundle_SubstituteLoadLookup.class).getContents(), false, false);
            }
        }
    }

    @Substitute
    private boolean areParallelContentsComplete() {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(getClass()).isRuntimeLoaded()) {
            if (parallelContents.isMarked()) {
                return true;
            }
            boolean[] markHolder = new boolean[1];
            Object[][] contents = parallelContents.get(markHolder);
            return contents != null || markHolder[0];
        }
        return true;
    }

    @Substitute
    private void loadLookupTablesIfNecessary() {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(getClass()).isRuntimeLoaded()) {
            ConcurrentMap<String, Object> localLookup = lookup;
            if (localLookup == null) {
                localLookup = new ConcurrentHashMap<>();
                ParallelListResourceBundleSupport.putContents(localLookup, getContents(), false);
            }
            Object[][] supplementaryContents = parallelContents.getReference();
            if (supplementaryContents != null) {
                ParallelListResourceBundleSupport.putContents(localLookup, supplementaryContents, true);
                parallelContents.set(null, true);
            }
            if (lookup == null) {
                synchronized (this) {
                    if (lookup == null) {
                        lookup = localLookup;
                    }
                }
            }
            return;
        }
        Map<String, Object> content = BundleContentSubstitutedLocalizationSupport.getBundleContentOf(this);
        synchronized (this) {
            if (lookup == null) {
                if (content instanceof ConcurrentMap<?, ?> concurrentContent) {
                    @SuppressWarnings("unchecked")
                    ConcurrentMap<String, Object> typedContent = (ConcurrentMap<String, Object>) concurrentContent;
                    lookup = typedContent;
                } else {
                    lookup = new ConcurrentHashMap<>(content);
                }
            }
        }
    }
}

final class ParallelListResourceBundleSupport {

    static void putContents(ConcurrentMap<String, Object> target, Object[][] contents, boolean supplementary) {
        if (contents == null) {
            return;
        }
        for (Object[] entry : contents) {
            String key = (String) entry[0];
            Object value = entry[1];
            if (supplementary) {
                target.putIfAbsent(key, value);
            } else {
                target.put(key, value);
            }
        }
    }

}
