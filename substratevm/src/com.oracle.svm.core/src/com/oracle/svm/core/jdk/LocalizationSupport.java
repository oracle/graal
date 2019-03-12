/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;

public class LocalizationSupport {
    protected final Map<String, Charset> charsets;
    protected final Map<String, ResourceBundle> cache;

    public static class Options {
        @Option(help = "Comma separated list of bundles to be included into the image.", type = OptionType.User)//
        public static final HostedOptionKey<String[]> IncludeResourceBundles = new HostedOptionKey<>(null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public LocalizationSupport() {
        charsets = new HashMap<>();
        cache = new HashMap<>();
        /* Multi-version jar loading will the the bundles appropriate for the platform version. */
        LocalizationResourceBundles.initialize(this);
        includeResourceBundles();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addBundleToCache(String bundleName) {
        if (bundleName.isEmpty()) {
            return;
        }
        final ResourceBundle bundle = getBundleInDefaultLocale(bundleName);
        addBundleToCache(bundleName, bundle);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    ResourceBundle getBundleInDefaultLocale(String bundleName) {
        final ResourceBundle result = ResourceBundle.getBundle(bundleName, Locale.getDefault(), Thread.currentThread().getContextClassLoader());
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void addBundleToCache(String bundleName, ResourceBundle bundle) {
        /* Ensure that the bundle contents are loaded. */
        bundle.keySet();
        cache.put(bundleName, bundle);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void includeResourceBundles() {
        for (String bundle : OptionUtils.flatten(",", Options.IncludeResourceBundles.getValue())) {
            addBundleToCache(bundle);
        }
    }

    private final String includeResourceBundlesOption = SubstrateOptionsParser.commandArgument(Options.IncludeResourceBundles, "");

    /**
     * Get cached resource bundle.
     *
     * @param locale this parameter is not currently used.
     */
    public ResourceBundle getCached(String baseName, Locale locale) throws MissingResourceException {
        ResourceBundle result = cache.get(baseName);
        if (result == null) {
            String errorMessage = "Resource bundle not found " + baseName + ". " +
                            "Register the resource bundle using the option " + includeResourceBundlesOption + baseName + ".";
            throw new MissingResourceException(errorMessage, this.getClass().getName(), baseName);
        }
        return result;
    }
}
