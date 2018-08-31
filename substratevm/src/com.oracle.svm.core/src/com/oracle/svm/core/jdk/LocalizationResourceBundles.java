/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Localization resource bundle initialization.
 *
 * This class defines the API for initializing localization resource bundles, and supplies the
 * implementation for JDK-8. In a JVM with multi-versioned jar files, a class for a later version
 * with the same name will be loaded instead of this class.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class LocalizationResourceBundles {

    static void initialize(LocalizationSupport localizationSupport) {
        final String[] bundleNameArray = new String[]{
                        "sun.util.resources.CalendarData",
                        "sun.util.resources.CurrencyNames",
                        "sun.util.resources.LocaleNames",
                        "sun.util.resources.TimeZoneNames",
                        "sun.text.resources.CollationData",
                        "sun.text.resources.FormatData",
                        "sun.util.logging.resources.logging"
        };
        for (String bundleName : bundleNameArray) {
            localizationSupport.addBundleToCache(bundleName);
        }
    }
}
