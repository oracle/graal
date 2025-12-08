/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.SubstituteLoadLookup;
import com.oracle.svm.core.util.VMError;

import sun.util.resources.OpenListResourceBundle;

@TargetClass(value = sun.util.resources.ParallelListResourceBundle.class, onlyWith = SubstituteLoadLookup.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_util_resources_ParallelListResourceBundle_SubstituteLoadLookup {

    @Alias private ConcurrentMap<String, Object> lookup;

    @Substitute
    private void setParallelContents(OpenListResourceBundle rb) {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + getClass().getTypeName());
    }

    @Substitute
    private boolean areParallelContentsComplete() {
        return true;
    }

    @Substitute
    private void loadLookupTablesIfNecessary() {
        LocalizationSupport support = ImageSingletons.lookup(LocalizationSupport.class);
        synchronized (this) {
            if (lookup == null) {
                lookup = new ConcurrentHashMap<>(support.getBundleContentOf(this));
            }
        }
    }
}
