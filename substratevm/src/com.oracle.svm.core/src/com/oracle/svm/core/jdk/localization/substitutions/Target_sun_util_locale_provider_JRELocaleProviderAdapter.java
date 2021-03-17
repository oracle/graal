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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;

// Checkstyle: stop
import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleResources;
// Checkstyle: resume

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@TargetClass(JRELocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_JRELocaleProviderAdapter {
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private ConcurrentMap<String, Set<String>> langtagSets;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private ConcurrentMap<Locale, LocaleResources> localeResourcesMap;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    static Boolean isNonENSupported;

    @Substitute //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static boolean isNonENLangSupported() {
        /*
         * The original implementation performs lazily initialization that looks at the file system
         * (a certain .jar file being present). That cannot work in a native image, and even worse
         * it makes file access methods reachable in very basic images.
         */
        VMError.guarantee(isNonENSupported != null, "isNonENSupported must be initialized during image generation");
        return isNonENSupported;
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    protected Set<String> createLanguageTagSet(String category) {
        return ImageSingletons.lookup(LocalizationSupport.class).supportedLanguageTags;
    }
}
