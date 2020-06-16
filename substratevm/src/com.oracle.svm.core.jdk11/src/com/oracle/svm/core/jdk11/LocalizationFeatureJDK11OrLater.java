/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk11;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.LocalizationFeature;
//Checkstyle: stop
import sun.text.spi.JavaTimeDateTimePatternProvider;
//Checkstyle: start

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.spi.LocaleServiceProvider;

@AutomaticFeature
final class LocalizationFeatureJDK11OrLater extends LocalizationFeature {

    private static final List<Class<? extends LocaleServiceProvider>> jdk9PlusClasses = Collections.singletonList(
                    JavaTimeDateTimePatternProvider.class);

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Override
    protected void addResourceBundles() {
        super.addResourceBundles();

        addBundleToCache(localeData(java.text.spi.BreakIteratorProvider.class).getBreakIteratorResources(imageLocale));
    }

    @Override
    protected List<Class<? extends LocaleServiceProvider>> getSpiClasses() {
        List<Class<? extends LocaleServiceProvider>> allClasses = new ArrayList<>(super.getSpiClasses());
        allClasses.addAll(jdk9PlusClasses);
        return allClasses;
    }
}
