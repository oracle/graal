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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.localization.bundles.DelayedBundle;
import com.oracle.svm.core.jdk.localization.bundles.ExtractedBundle;
import com.oracle.svm.core.jdk.localization.bundles.StoredBundle;
import com.oracle.svm.core.jdk.localization.substitutions.Target_java_util_ListResourceBundle_SubstituteLoadLookup;
import com.oracle.svm.core.jdk.localization.substitutions.modes.SubstituteLoadLookup;
import com.oracle.svm.core.meta.MethodRefUtils;
import com.oracle.svm.guest.staging.c.function.InvokeJavaFunctionPointer;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.meta.MethodRef;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;

/**
 * This version of LocalizationSupport stores the content of resource bundles in a map to make the
 * (Open)ListResourceBundle.getContents() methods of prepared resource bundle subclasses
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
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final String INTERNAL_BUNDLES_PATTERN = "sun\\..*";

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final ForkJoinPool pool;

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    private final EconomicMap<Class<?>, Integer> unpreparedBundleGetterIndices = ImageHeapMap.createNonLayeredMap();

    @Platforms(Platform.HOSTED_ONLY.class) private final List<MethodRef> hostedBundleGetters = new ArrayList<>();

    @UnknownObjectField(availability = ReadyForCompilation.class) private MethodRef[] unpreparedBundleGetters;

    @Platforms(Platform.HOSTED_ONLY.class) private final Set<Class<?>> preparedBundleClasses = ConcurrentHashMap.newKeySet();

    private final Set<String> existingBundles = ConcurrentHashMap.newKeySet();

    public BundleContentSubstitutedLocalizationSupport(EconomicSet<Locale> locales, ForkJoinPool pool) {
        super(locales);
        this.pool = pool;
    }

    @Fold
    static boolean substituteLoadLookupEnabled() {
        /* The mode is invariant across layers, including when the extraction key is absent. */
        return ImageSingletons.contains(BundleContentSubstitutedLocalizationSupport.class);
    }

    protected static BundleContentSubstitutedLocalizationSupport[] getAllLayers() {
        return MultiLayeredImageSingleton.getAllLayers(BundleContentSubstitutedLocalizationSupport.class);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onBundlePrepared(ResourceBundle bundle) {
        if (isBundleSupported(bundle)) {
            // Record the class before scheduling extraction in the pool. Analysis must not register
            // a runtime getter while extraction is pending.
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

    public static Map<String, Object> getBundleContentOf(Object bundle) {
        BundleContentSubstitutedLocalizationSupport[] localizationSupports = BundleContentSubstitutedLocalizationSupport.getAllLayers();
        /* Prefer prepared contents from the most recent layer. */
        for (int i = localizationSupports.length - 1; i >= 0; i--) {
            StoredBundle storedBundle = localizationSupports[i].storedBundles.get(bundle.getClass());
            if (storedBundle != null) {
                return storedBundle.getContent(bundle);
            }
        }
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In non-layered images, pass the singleton directly so analysis can propagate a
             * constant receiver into the non-inlined helper. An indexed load from the layer array
             * can lose that constant even with one entry. This preserves folding of an empty
             * getter array, avoiding the WasmGC object/word-array mismatch on the unreachable
             * getter path.
             */
            var support = ImageSingletons.lookup(BundleContentSubstitutedLocalizationSupport.class);
            var unpreparedBundleContentOf = support.getUnpreparedBundleContentOf(bundle);
            if (unpreparedBundleContentOf != null) {
                return unpreparedBundleContentOf;
            }
        } else {
            /* Prepared contents in any layer take precedence over lazy runtime initialization. */
            for (int i = localizationSupports.length - 1; i >= 0; i--) {
                var unpreparedBundleContentOf = localizationSupports[i].getUnpreparedBundleContentOf(bundle);
                if (unpreparedBundleContentOf != null) {
                    return unpreparedBundleContentOf;
                }
            }
        }
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + bundle.getClass());
    }

    @NeverInline("Keep lazy initialization out of the prepared-bundle fast path")
    private Map<String, Object> getUnpreparedBundleContentOf(Object bundle) {
        Integer getterIndex = unpreparedBundleGetterIndices.get(bundle.getClass());
        if (getterIndex == null) {
            return null;
        }
        synchronized (bundle) {
            var target = SubstrateUtil.cast(bundle, Target_java_util_ListResourceBundle_SubstituteLoadLookup.class);
            if (target.lookup == null) {
                // The class lookup already selected the implementation; do not dispatch to prepared overrides.
                BundleContentsGetter getter = MethodRefUtils.asCodePointer(unpreparedBundleGetters[getterIndex]);
                Object[][] content = getter.invoke(bundle);
                // Keep the lookup map type shared with prepared bundles for compiled lookup performance.
                target.lookup = new ExtractedBundle(content);
            }
            return target.lookup;
        }
    }

    private interface BundleContentsGetter extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Object[][] invoke(Object bundle);
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
        DelayedBundle bundle = new DelayedBundle(clazz);
        preparedBundleClasses.add(clazz);
        storedBundles.put(clazz, bundle);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isPreparedBundle(Class<?> clazz) {
        return preparedBundleClasses.contains(clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean hasUnpreparedBundleGetter(Class<?> clazz) {
        synchronized (unpreparedBundleGetterIndices) {
            return unpreparedBundleGetterIndices.containsKey(clazz);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareUnpreparedBundle(Class<?> clazz, Function<Method, MethodRef> createGetter) throws ReflectiveOperationException {
        // Prepared bundles use extracted contents or a DelayedBundle; no additional getter is needed.
        if (!isPreparedBundle(clazz)) {
            synchronized (unpreparedBundleGetterIndices) {
                if (!unpreparedBundleGetterIndices.containsKey(clazz)) {
                    MethodRef getter = createGetter.apply(DelayedBundle.findGetContentsMethod(clazz));
                    int index = hostedBundleGetters.size();
                    hostedBundleGetters.add(getter);
                    unpreparedBundleGetterIndices.put(clazz, index);
                }
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public MethodRef[] finishBundleGetters() {
        // Convert the hosted list to a typed word array for runtime lookup.
        unpreparedBundleGetters = hostedBundleGetters.toArray(new MethodRef[0]);
        return unpreparedBundleGetters;
    }

    public static boolean isNotIncluded(String bundleName) {
        if (!substituteLoadLookupEnabled()) {
            return false;
        }
        for (BundleContentSubstitutedLocalizationSupport localizationSupport : BundleContentSubstitutedLocalizationSupport.getAllLayers()) {
            if (localizationSupport.existingBundles.contains(bundleName)) {
                return false;
            }
        }
        return true;
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
