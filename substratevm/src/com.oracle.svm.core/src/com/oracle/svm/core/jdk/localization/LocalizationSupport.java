/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.localization;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Holder for localization information that is computed during image generation and used at run
 * time.
 */
public class LocalizationSupport {

    final Map<String, Charset> charsets = new HashMap<>();

    final Locale defaultLocale;
    /**
     * All available locales configured during image build time.
     */
    final Locale[] allLocales;

    final Set<String> supportedLanguageTags;

    final ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

    protected LocalizationSupport(Locale defaultLocale, List<Locale> locales) {
        this.defaultLocale = defaultLocale;
        this.allLocales = locales.toArray(new Locale[0]);
        this.supportedLanguageTags = locales.stream().map(Locale::toString).collect(Collectors.toSet());
    }

    public OptimizedLocalizationSupport asOptimizedSupport() {
        GraalError.guarantee(LocalizationFeature.optimizedMode(), "Optimized support only available in optimized localization mode.");
        return ((OptimizedLocalizationSupport) this);
    }

    public Map<String, Object> getBundleContentOf(Class<?> bundleClass) {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + bundleClass.getTypeName());
    }

    public void prepareBundle(String bundleName, ResourceBundle bundle, Locale locale) {
        if (bundle instanceof PropertyResourceBundle) {
            String withLocale = control.toBundleName(bundleName, locale);
            ImageSingletons.lookup(ResourcesRegistry.class).addResources(withLocale.replace('.', '/') + "\\.properties");
        } else {
            RuntimeReflection.register(bundle.getClass());
            RuntimeReflection.registerForReflectiveInstantiation(bundle.getClass());
            onBundlePrepared(bundle);
        }
    }

    /**
     * Template method for subclasses to do perform additional tasks.
     */
    protected void onBundlePrepared(@SuppressWarnings("unused") ResourceBundle bundle) {

    }
}
