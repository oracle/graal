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

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;

import jdk.internal.util.ReferencedKeyMap;
import sun.util.locale.BaseLocale;
import sun.util.resources.Bundles;

@TargetClass(value = java.util.ResourceBundle.class, innerClass = "Control")
@SuppressWarnings("unused")
final class Target_java_util_ResourceBundle_Control_Cache {

    /*
     * This cache only memoizes candidate locale lists derived by Control.createCandidateList().
     * That computation is pure and fully reconstructible from the BaseLocale key, so a fresh cache
     * preserves the default JDK behavior while avoiding analysis-time cache rescans.
     */
    @Alias @TargetElement(name = "CANDIDATES_CACHE") @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    private static ReferencedKeyMap<BaseLocale, List<Locale>> candidatesCache = ReferencedKeyMap.create(true, ConcurrentHashMap::new);
}

@TargetClass(value = java.util.ResourceBundle.class, innerClass = "Control", onlyWith = RuntimeClassLoading.NoRuntimeClassLoading.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_java_util_ResourceBundle_Control {

    /**
     * Bundles are baked into the image, therefore their source can't really be modified at runtime.
     * Since their source can't be modified, there is no need to reload them.
     */
    @Substitute
    public boolean needsReload(String baseName, Locale locale,
                    String format, ClassLoader loader,
                    ResourceBundle bundle, long loadTime) {

        return false;
    }

    @Substitute
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                    throws IllegalAccessException, InstantiationException, IOException {
        /*
         * Legacy mechanism to locate resource bundle in unnamed module only that is visible to the
         * given loader and accessible to the given caller.
         */
        String bundleName = toBundleName(baseName, locale);
        if (format.equals("java.class") && ImageSingletons.lookup(LocalizationSupport.class).isNotIncluded(bundleName)) {
            return null;
        }
        var bundle = newBundle0(bundleName, format, loader, reload);
        if (bundle == null) {
            // Try loading legacy ISO language's other bundles
            var otherBundleName = Bundles.toOtherBundleName(baseName, bundleName, locale);
            if (!bundleName.equals(otherBundleName)) {
                bundle = newBundle0(otherBundleName, format, loader, reload);
            }
        }

        return bundle;
    }

    @Alias
    public native String toBundleName(String baseName, Locale locale);

    @Alias
    private native ResourceBundle newBundle0(String bundleName, String format, ClassLoader loader, boolean reload)
                    throws IllegalAccessException, InstantiationException, IOException;
}
