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
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.util.VMError;

/**
 * Holder for localization information that is computed during image generation and used at run
 * time.
 *
 * @see LocalizationFeature
 */
public class LocalizationSupport {

    public final Map<String, Charset> charsets = new HashMap<>();

    public final Locale defaultLocale;

    public final Locale[] allLocales;

    public final Set<String> supportedLanguageTags;

    public final ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

    public LocalizationSupport(Locale defaultLocale, Set<Locale> locales) {
        this.defaultLocale = defaultLocale;
        this.allLocales = locales.toArray(new Locale[0]);
        this.supportedLanguageTags = locales.stream().map(Locale::toString).collect(Collectors.toSet());
    }

    @Fold
    public static boolean optimizedMode() {
        return LocalizationFeature.Options.LocalizationOptimizedMode.getValue();
    }

    @Fold
    public static boolean jvmMode() {
        return !optimizedMode();
    }

    public OptimizedLocalizationSupport asOptimizedSupport() {
        GraalError.guarantee(optimizedMode(), "Optimized support only available in optimized localization mode.");
        return ((OptimizedLocalizationSupport) this);
    }

    public Map<String, Object> getBundleContentOf(Object bundle) {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + bundle.getClass());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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
     * Template method for subclasses to perform additional tasks.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onBundlePrepared(@SuppressWarnings("unused") ResourceBundle bundle) {

    }

    @SuppressWarnings("unused")
    public boolean shouldSubstituteLoadLookup(String className) {
        /*- By default, keep the original code */
        return false;
    }

    @SuppressWarnings("unused")
    public void prepareNonCompliant(Class<?> clazz) {
        /*- By default, there is nothing to do */
    }
}
