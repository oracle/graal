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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.OptimizedLocaleMode;

@TargetClass(java.util.ResourceBundle.class)
@SuppressWarnings({"unused"})
final class Target_java_util_ResourceBundle {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    private static ConcurrentMap<?, ?> cacheList = new ConcurrentHashMap<>();

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, locale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, locale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, ClassLoader loader, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }

    /**
     * Currently there is no support for the module system at run time. Module arguments are
     * therefore ignored.
     */

    @Substitute
    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    private static ResourceBundle getBundle(String baseName, @SuppressWarnings("unused") Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @Substitute
    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, @SuppressWarnings("unused") Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }
}
