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
package com.oracle.svm.core.jdk;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.compiler.debug.GraalError;

// Checkstyle: stop
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;
// Checkstyle: resume

public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    private interface StoredBundle {
        Map<String, Object> getContent();
    }

    private static class ExtractedBundle implements StoredBundle {
        private final Map<String, Object> lookup;

        ExtractedBundle(Map<String, Object> lookup) {
            this.lookup = lookup;
        }

        @Override
        public Map<String, Object> getContent() {
            return lookup;
        }
    }

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    public BundleContentSubstitutedLocalizationSupport(Locale defaultLocale, List<Locale> locales) {
        super(defaultLocale, locales);
    }

    public void storeBundleContentOf(ResourceBundle bundle) {
        GraalError.guarantee(isBundleSupported(bundle), "Unsupported bundle " + bundle + " of type " + bundle.getClass());
        storedBundles.put(bundle.getClass(), processBundle(bundle));
    }

    private StoredBundle processBundle(ResourceBundle bundle) {
        Map<String, Object> content = extractContent(bundle);
        return maybeCompressBundle(bundle, content);
    }

    private ExtractedBundle maybeCompressBundle(ResourceBundle bundle, Map<String, Object> content) {
        return new ExtractedBundle(content);
    }

    @Override
    public Map<String, Object> getBundleContentFor(Class<?> bundleClass) {
        StoredBundle bundle = storedBundles.get(bundleClass);
        if (bundle != null) {
            return bundle.getContent();
        }
        return super.getBundleContentFor(bundleClass);
    }

    public boolean isBundleSupported(ResourceBundle bundle) {
        return bundle instanceof ListResourceBundle || bundle instanceof OpenListResourceBundle || bundle instanceof ParallelListResourceBundle;
    }

    /**
     * Extracts the content of the bundle by looking up the lookup field. All the jdk internal
     * bundles can be resolved this way, except from the BreakIterators. In the future, it can be
     * extended with a fallback to user defined bundles by using the handleKeySet and
     * handleGetObject methods.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractContent(ResourceBundle bundle) {
        bundle.keySet(); // force lazy initialization
        Class<?> clazz = bundle.getClass().getSuperclass();
        while (clazz != null && ResourceBundle.class.isAssignableFrom(clazz)) {
            try {
                return (Map<String, Object>) ReflectionUtil.lookupField(clazz, "lookup").get(bundle);
            } catch (ReflectionUtil.ReflectionUtilError | ReflectiveOperationException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw VMError.shouldNotReachHere("Failed to extract content for " + bundle + " of type " + bundle.getClass());
    }
}
