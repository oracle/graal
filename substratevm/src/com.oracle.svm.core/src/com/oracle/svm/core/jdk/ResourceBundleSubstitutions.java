/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

//Checkstyle: allow reflection

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;

import sun.util.resources.OpenListResourceBundle;

@TargetClass(java.util.ResourceBundle.class)
@SuppressWarnings({"unused"})
final class Target_java_util_ResourceBundle {

    @Substitute
    private static ResourceBundle getBundle(String baseName) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, Locale.getDefault());
    }

    @Substitute
    private static ResourceBundle getBundle(String baseName, Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, Locale.getDefault());
    }

    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, locale);
    }

    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, targetLocale);
    }

    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, locale);
    }

    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, ClassLoader loader, Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, targetLocale);
    }

    /*
     * Currently there is no support for the module system at run time. Module arguments are
     * therefore ignored.
     */

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Target_java_lang_Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, Locale.getDefault());
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, Target_java_lang_Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).getCached(baseName, targetLocale);
    }
}

@TargetClass(java.util.ListResourceBundle.class)
@SuppressWarnings({"static-method"})
final class Target_java_util_ListResourceBundle {

    @Substitute
    private void loadLookup() {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + getClass().getTypeName());
    }
}

@TargetClass(sun.util.resources.OpenListResourceBundle.class)
@SuppressWarnings({"static-method"})
final class Target_sun_util_resources_OpenListResourceBundle {

    @Substitute
    private void loadLookup() {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + getClass().getTypeName());
    }
}

@TargetClass(sun.util.resources.ParallelListResourceBundle.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_util_resources_ParallelListResourceBundle {

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
        if (lookup == null) {
            throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + getClass().getTypeName());
        }
    }
}

@TargetClass(java.text.DateFormatSymbols.class)
final class Target_java_text_DateFormatSymbols {

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static ConcurrentMap<?, ?> cachedInstances = new ConcurrentHashMap<>(3);
}

/** Dummy class to have a class with the file's name. */
public final class ResourceBundleSubstitutions {
}
