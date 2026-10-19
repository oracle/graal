/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.localization.bundles.DelayedBundle;
import com.oracle.svm.core.jdk.localization.bundles.ExtractedBundle;
import com.oracle.svm.core.jdk.localization.bundles.StoredBundle;
import com.oracle.svm.core.jdk.localization.substitutions.Target_java_util_ListResourceBundle_SubstituteLoadLookup;
import com.oracle.svm.core.jdk.localization.substitutions.modes.SubstituteLoadLookup;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.GraalError;
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;

/**
 * This version of LocalizationSupport stores the content of resource bundles in a map to make the
 * (Open)ListResourceBundle.getContents() methods of individual resource bundle subclasses
 * unreachable. To do this, we substitute the lookup methods that would call the getContents methods
 * and provide the content for resource bundles ourselves. For this to work, we have to extract the
 * content of the bundles at build time via {@link BundleSerializationUtils#extractContent}.
 * <p>
 * We could avoid this dependency by including the getContents() methods of (Open)ListResourceBundle
 * subclasses in the image, but these methods are huge, so making them reachable would cause
 * regressions in compile time and binary size when multiple locales are requested.
 *
 * @see BundleSerializationUtils
 * @see SubstituteLoadLookup
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final String INTERNAL_BUNDLES_PATTERN = "sun\\..*";

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final ForkJoinPool pool;

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    private final EconomicMap<Class<?>, Method> unpreparedBundleGetters = ImageHeapMap.createNonLayeredMap();

    @Platforms(Platform.HOSTED_ONLY.class) private final Set<Class<?>> preparedBundleClasses = ConcurrentHashMap.newKeySet();

    private final Set<String> existingBundles = ConcurrentHashMap.newKeySet();

    public BundleContentSubstitutedLocalizationSupport(EconomicSet<Locale> locales, Charset defaultCharset, ForkJoinPool pool) {
        super(locales, defaultCharset);
        this.pool = pool;
    }

    @Override
    public boolean substituteLoadLookup() {
        return true;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onBundlePrepared(ResourceBundle bundle) {
        if (isBundleSupported(bundle)) {
            preparedBundleClasses.add(bundle.getClass());
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
        Map<String, Object> content = BundleSerializationUtils.extractContent(bundle);
        storedBundles.put(bundle.getClass(), new ExtractedBundle(content));
    }

    @Override
    public Map<String, Object> getBundleContentOf(Object bundle) {
        StoredBundle storedBundle = storedBundles.get(bundle.getClass());
        if (storedBundle != null) {
            return storedBundle.getContent(bundle);
        }
        return getUnpreparedBundleContentOf(bundle);
    }

    @NeverInline("Keep lazy initialization out of the prepared-bundle fast path")
    private Map<String, Object> getUnpreparedBundleContentOf(Object bundle) {
        Method getContents = unpreparedBundleGetters.get(bundle.getClass());
        if (getContents == null) {
            return super.getBundleContentOf(bundle);
        }
        synchronized (bundle) {
            var target = SubstrateUtil.cast(bundle, Target_java_util_ListResourceBundle_SubstituteLoadLookup.class);
            if (target.lookup == null) {
                Object[][] content = ReflectionUtil.invokeMethod(getContents, bundle);
                // Keep the lookup map type shared with prepared bundles for compiled lookup performance.
                target.lookup = new ExtractedBundle(content);
            }
            return target.lookup;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static boolean isBundleSupported(ResourceBundle bundle) {
        return isBundleSupported(bundle.getClass());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isBundleSupported(Class<?> bundleClass) {
        return ListResourceBundle.class.isAssignableFrom(bundleClass) || OpenListResourceBundle.class.isAssignableFrom(bundleClass) || ParallelListResourceBundle.class.isAssignableFrom(bundleClass);
    }

    @Override
    public void prepareNonCompliant(Class<?> clazz) throws ReflectiveOperationException {
        storedBundles.put(clazz, new DelayedBundle(clazz));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareUnpreparedBundle(Class<?> clazz) throws ReflectiveOperationException {
        /* Extraction may still be running in the pool; do not make prepared getters reachable. */
        if (!preparedBundleClasses.contains(clazz) && !storedBundles.containsKey(clazz)) {
            unpreparedBundleGetters.putIfAbsent(clazz, DelayedBundle.findGetContentsMethod(clazz));
        }
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
