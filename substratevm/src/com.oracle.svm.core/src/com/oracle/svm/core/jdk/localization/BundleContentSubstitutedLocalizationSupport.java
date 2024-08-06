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
package com.oracle.svm.core.jdk.localization;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.localization.bundles.DelayedBundle;
import com.oracle.svm.core.jdk.localization.bundles.ExtractedBundle;
import com.oracle.svm.core.jdk.localization.bundles.StoredBundle;
import com.oracle.svm.core.jdk.localization.compression.GzipBundleCompression;
import com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.debug.GraalError;
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;

public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final String INTERNAL_BUNDLES_PATTERN = "sun\\..*";

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final List<Pattern> compressBundlesPatterns;

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final ForkJoinPool pool;

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    private final Set<String> existingBundles = ConcurrentHashMap.newKeySet();

    public BundleContentSubstitutedLocalizationSupport(Locale defaultLocale, Set<Locale> locales, Charset defaultCharset, List<String> requestedPatterns, ForkJoinPool pool) {
        super(defaultLocale, locales, defaultCharset);
        this.pool = pool;
        this.compressBundlesPatterns = parseCompressBundlePatterns(requestedPatterns);
    }

    @Override
    public boolean substituteLoadLookup() {
        return true;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onBundlePrepared(ResourceBundle bundle) {
        if (isBundleSupported(bundle)) {
            if (pool != null) {
                pool.execute(() -> storeBundleContentOf(bundle));
            } else {
                storeBundleContentOf(bundle);
            }
        }
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onClassBundlePrepared(Class<?> bundleClass) {
        if (isBundleSupported(bundleClass)) {
            try {
                prepareNonCompliant(bundleClass);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void storeBundleContentOf(ResourceBundle bundle) {
        GraalError.guarantee(isBundleSupported(bundle), "Unsupported bundle %s of type %s", bundle, bundle.getClass());
        storedBundles.put(bundle.getClass(), processBundle(bundle));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private StoredBundle processBundle(ResourceBundle bundle) {
        boolean isInDefaultLocale = bundle.getLocale().equals(defaultLocale);
        if (!isInDefaultLocale && shouldCompressBundle(bundle) && GzipBundleCompression.canCompress(bundle)) {
            return GzipBundleCompression.compress(bundle);
        }
        Map<String, Object> content = BundleSerializationUtils.extractContent(bundle);
        return new ExtractedBundle(content);
    }

    @Override
    public Map<String, Object> getBundleContentOf(Object bundle) {
        StoredBundle storedBundle = storedBundles.get(bundle.getClass());
        if (storedBundle != null) {
            return storedBundle.getContent(bundle);
        }
        return super.getBundleContentOf(bundle);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static boolean isBundleSupported(ResourceBundle bundle) {
        return isBundleSupported(bundle.getClass());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static boolean isBundleSupported(Class<?> bundleClass) {
        return ListResourceBundle.class.isAssignableFrom(bundleClass) || OpenListResourceBundle.class.isAssignableFrom(bundleClass) || ParallelListResourceBundle.class.isAssignableFrom(bundleClass);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static List<Pattern> parseCompressBundlePatterns(List<String> userPatterns) {
        List<Pattern> compiled = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        compiled.add(Pattern.compile(INTERNAL_BUNDLES_PATTERN));
        for (String pattern : userPatterns) {
            try {
                compiled.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException ex) {
                invalid.add(pattern);
            }
        }
        if (!invalid.isEmpty()) {
            throw UserError.abort("Invalid patterns specified: %s", invalid);
        }
        return compiled;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean shouldCompressBundle(ResourceBundle bundle) {
        String className = bundle.getClass().getName();
        return compressBundlesPatterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
    }

    @Override
    public void prepareNonCompliant(Class<?> clazz) throws ReflectiveOperationException {
        storedBundles.put(clazz, new DelayedBundle(clazz));
    }

    @Override
    public boolean isNotIncluded(String bundleName) {
        return !existingBundles.contains(bundleName);
    }

    @Override
    public void prepareBundle(String bundleName, ResourceBundle bundle, Function<String, Optional<Module>> findModule, Locale locale, boolean jdkLocale) {
        super.prepareBundle(bundleName, bundle, findModule, locale, jdkLocale);
        /* Initialize ResourceBundle.keySet eagerly */
        bundle.keySet();
        if (!jdkLocale) {
            this.existingBundles.add(control.toBundleName(bundleName, locale));
        }
    }

    @Override
    public void prepareClassResourceBundle(String basename, Class<?> bundleClass) {
        super.prepareClassResourceBundle(basename, bundleClass);
        this.existingBundles.add(bundleClass.getName());
    }
}
