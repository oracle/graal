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

import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.jdk.localization.bundles.ExtractedBundle;
import com.oracle.svm.core.jdk.localization.bundles.StoredBundle;
import com.oracle.svm.core.jdk.localization.compression.GzipBundleCompression;
import org.graalvm.compiler.debug.GraalError;

// Checkstyle: stop
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;
// Checkstyle: resume

import static com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils.extractContent;

public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    private final GzipBundleCompression compressionAlgorithm = new GzipBundleCompression();

    public BundleContentSubstitutedLocalizationSupport(Locale defaultLocale, List<Locale> locales) {
        super(defaultLocale, locales);
    }

    @Override
    protected void onBundlePrepared(ResourceBundle bundle) {
        if (isBundleSupported(bundle)) {
            storeBundleContentOf(bundle);
        }
    }

    private void storeBundleContentOf(ResourceBundle bundle) {
        GraalError.guarantee(isBundleSupported(bundle), "Unsupported bundle %s of type %s", bundle, bundle.getClass());
        storedBundles.put(bundle.getClass(), processBundle(bundle));
    }

    private StoredBundle processBundle(ResourceBundle bundle) {
        boolean isInDefaultLocale = bundle.getLocale().equals(defaultLocale);
        if (!isInDefaultLocale && compressionAlgorithm.canCompress(bundle)) {
            return compressionAlgorithm.compress(bundle);
        }
        Map<String, Object> content = extractContent(bundle);
        return new ExtractedBundle(content);
    }

    @Override
    public Map<String, Object> getBundleContentOf(Class<?> bundleClass) {
        StoredBundle bundle = storedBundles.get(bundleClass);
        if (bundle != null) {
            try {
                return bundle.getContent();
            } catch (Exception ex) {
                throw GraalError.shouldNotReachHere(ex, "Decompression of a bundle " + bundleClass + " failed. This is an internal error. Please open an issue and submit a reproducer.");
            }
        }
        return super.getBundleContentOf(bundleClass);
    }

    public boolean isBundleSupported(ResourceBundle bundle) {
        return bundle instanceof ListResourceBundle || bundle instanceof OpenListResourceBundle || bundle instanceof ParallelListResourceBundle;
    }
}
