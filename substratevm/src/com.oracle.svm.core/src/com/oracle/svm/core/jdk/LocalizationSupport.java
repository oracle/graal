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
import java.util.spi.LocaleServiceProvider;

import com.oracle.svm.core.option.SubstrateOptionsParser;

//Checkstyle: stop
import sun.util.locale.provider.LocaleProviderAdapter;
//Checkstyle: resume

/**
 * Holder for localization information that is computed during image generation and used at run
 * time.
 */
public final class LocalizationSupport {

    final Map<String, Charset> charsets = new HashMap<>();
    final Map<Class<? extends LocaleServiceProvider>, LocaleProviderAdapter> adaptersByClass = new HashMap<>();
    final Map<LocaleProviderAdapter.Type, LocaleProviderAdapter> adaptersByType = new HashMap<>();
    final Map<Class<? extends LocaleServiceProvider>, Object> providerPools = new HashMap<>();
    final Map<String, ResourceBundle> resourceBundles = new HashMap<>();

    private final String includeResourceBundlesOption = SubstrateOptionsParser.commandArgument(LocalizationFeature.Options.IncludeResourceBundles, "");

    /**
     * Get cached resource bundle.
     *
     * @param locale this parameter is not currently used.
     */
    public ResourceBundle getCached(String baseName, Locale locale) throws MissingResourceException {
        ResourceBundle result = resourceBundles.get(baseName);
        if (result == null) {
            String errorMessage = "Resource bundle not found " + baseName + ". " +
                            "Register the resource bundle using the option " + includeResourceBundlesOption + baseName + ".";
            throw new MissingResourceException(errorMessage, this.getClass().getName(), baseName);
        }
        return result;
    }
}
