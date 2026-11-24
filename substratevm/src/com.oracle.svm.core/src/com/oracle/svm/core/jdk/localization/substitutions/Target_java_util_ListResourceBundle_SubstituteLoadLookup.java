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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.SubstituteLoadLookup;

@TargetClass(value = java.util.ListResourceBundle.class, onlyWith = SubstituteLoadLookup.class)
@SuppressWarnings({"static-method"})
public final class Target_java_util_ListResourceBundle_SubstituteLoadLookup {

    @Alias public volatile Map<String, Object> lookup;

    @Alias
    @KeepOriginal
    @TargetElement(name = "loadLookup")
    private native void loadLookupOriginal();

    @Substitute
    private void loadLookup() {
        if (lookup != null) {
            return;
        }
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(getClass()).isRuntimeLoaded()) {
            /*
             * Runtime-loaded bundle classes do not have build-time LocalizationSupport entries in
             * the libjvm image. Let their original JDK implementation populate the lookup table
             * from the runtime-loaded class contents instead.
             */
            loadLookupOriginal();
            return;
        }
        /*
         * Prepared bundles share extracted contents without locking. Unprepared bundles initialize
         * under the original JDK lock, once per runtime instance, using a method resolved at build time.
         */
        lookup = BundleContentSubstitutedLocalizationSupport.getBundleContentOf(this);
    }
}
