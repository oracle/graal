/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.option;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.CommonOptions;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey.RuntimeOptionKeyFlag;
import com.oracle.svm.guest.staging.option.RuntimeOptionParser;
import com.oracle.svm.guest.staging.option.RuntimeOptionValues;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.AppLayerCGlobalTracking;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.heap.ImageHeapObjectAdder;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.imagelayer.LayeredImageUtils;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.CommonOptionNames;
import com.oracle.svm.shared.option.CommonOptionParser;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class RuntimeOptionFeature implements InternalFeature, IsolateArgumentParser.DefaultValuesProvider {

    private static final String LAYERED_DEFAULT_VALUES_NAME = "__svm_layer_default_isolate_option_values";
    private static final String LAYERED_DEFAULT_STRINGS_NAME = "__svm_layer_default_isolate_option_strings";

    /* Default values arrays used by {@link IsolateArgumentParser}. */
    private CGlobalData<CLongPointer> defaultValues;
    private CGlobalData<CCharPointer> defaultStrings;

    private RuntimeOptionParser runtimeOptionParser;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return ImageLayerBuildingSupport.buildingImageLayer() ? List.of(CGlobalDataFeature.class) : List.of();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            runtimeOptionParser = new RuntimeOptionParser();
            ImageSingletons.add(RuntimeOptionParser.class, runtimeOptionParser);
            ImageSingletons.add(RuntimeOptionsSupport.class, RuntimeOptionValues.createRuntimeOptionsSupport());

            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                /*
                 * For layered images we build the payload only in the application layer. In shared
                 * layers we create symbolic references which refer to the cglobal which is
                 * installed in the final layer.
                 */
                defaultValues = CGlobalDataFactory.forApplicationLayerSymbol(LAYERED_DEFAULT_VALUES_NAME);
                defaultStrings = CGlobalDataFactory.forApplicationLayerSymbol(LAYERED_DEFAULT_STRINGS_NAME);
            } else {
                /*
                 * In a traditional build we can directly create the cglobal with a payload.
                 */
                defaultValues = CGlobalDataFactory.createBytes(IsolateArgumentParser::createDefaultValues);
                defaultStrings = CGlobalDataFactory.createBytes(IsolateArgumentParser::createDefaultStrings);
            }
        } else {
            assert ImageLayerBuildingSupport.buildingApplicationLayer();
            HostedOptionParser optionParser = ((FeatureImpl.AfterRegistrationAccessImpl) access).getImageClassLoader().classLoaderSupport.getHostedOptionParser();
            defaultValues = CGlobalDataFactory.createBytes(() -> layeredCreateDefaultValues(optionParser), LAYERED_DEFAULT_VALUES_NAME);
            defaultStrings = CGlobalDataFactory.createBytes(() -> layeredCreateDefaultStrings(optionParser), LAYERED_DEFAULT_STRINGS_NAME);
            AppLayerCGlobalTracking appLayerTracking = CGlobalDataFeature.singleton().getAppLayerCGlobalTracking();
            appLayerTracking.registerCGlobalWithPriorLayerReference(defaultValues);
            appLayerTracking.registerCGlobalWithPriorLayerReference(defaultStrings);
        }
        ImageSingletons.add(IsolateArgumentParser.DefaultValuesProvider.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            access.registerObjectReachableCallback(OptionKey.class, this::collectOptionKeys);
        } else {
            access.registerObjectReachableCallback(OptionKey.class, this::collectOptionKeysExtension);
            ImageHeapObjectAdder.singleton().registerObjectAdder(this::addDefaultValuesObject);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        HostedOptionParser optionParser = accessImpl.getImageClassLoader().classLoaderSupport.getHostedOptionParser();
        boolean firstImage = ImageLayerBuildingSupport.firstImageBuild();
        if (firstImage) {
            registerPrintFlagsOptions(optionParser);
        }
        for (var descriptor : optionParser.getAllRuntimeOptions().getValues()) {
            if (descriptor.getOptionKey() instanceof RuntimeOptionKey<?> runtimeOptionKey && runtimeOptionKey.shouldRegisterForIsolateArgumentParser()) {
                if (firstImage) {
                    /*
                     * The list of options IsolateArgumentParser has to parse, is built dynamically,
                     * to include only options of the current configuration. Here, all options that
                     * should get parsed by the IsolateArgumentParser are added to this list.
                     */
                    IsolateArgumentParser.singleton().register(runtimeOptionKey);
                    registerOptionAsRead(accessImpl, runtimeOptionKey.getDescriptor().getDeclaringClass(), runtimeOptionKey.getName());
                } else {
                    /*
                     * All runtime options must have already been installed within the base layer.
                     * Within the extension layer we only confirm they are present.
                     */
                    assert IsolateArgumentParser.getOptionIndex(runtimeOptionKey) >= 0;
                }
            }
        }

        if (firstImage) {
            IsolateArgumentParser.singleton().sealOptions();
        } else {
            /* Ensure that the default values are registered and seen by the analysis. */
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(defaultValues);
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(defaultStrings);
            var universe = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getUniverse();
            LayeredImageUtils.registerObjectAsEmbeddedRoot(universe, defaultValues);
            LayeredImageUtils.registerObjectAsEmbeddedRoot(universe, defaultStrings);
        }
    }

    /**
     * The {@link OptionKey} values defined in {@link CommonOptions} are not accessed in a way seen
     * during analysis; {@link CommonOptionParser} only refers to them by name so they need to be
     * explicitly registered.
     */
    private static void registerPrintFlagsOptions(HostedOptionParser optionParser) {
        RuntimeOptionParser parser = RuntimeOptionParser.singleton();
        UnmodifiableEconomicMap<String, OptionDescriptor> allRuntimeOptions = optionParser.getAllRuntimeOptions();
        parser.addDescriptor(allRuntimeOptions.get(CommonOptionNames.PrintFlags));
        parser.addDescriptor(allRuntimeOptions.get(CommonOptionNames.PrintFlagsWithExtraHelp));
    }

    @Override
    public CGlobalData<CLongPointer> getDefaultValues() {
        return Objects.requireNonNull(defaultValues);
    }

    @Override
    public CGlobalData<CCharPointer> getDefaultStrings() {
        return Objects.requireNonNull(defaultStrings);
    }

    @SuppressWarnings("unused")
    private void collectOptionKeys(DuringAnalysisAccess access, OptionKey<?> optionKey, ObjectScanner.ScanReason reason) {
        if (optionKey instanceof HostedOptionKey<?>) {
            /* HostedOptionKey's are reached when building the NativeImage driver executable. */
            return;
        }
        OptionDescriptor optionDescriptor = optionKey.getDescriptor();
        if (optionDescriptor == null) {
            throw VMError.shouldNotReachHere("No OptionDescriptor registered for an OptionKey. Often that is the result of an incomplete build. " +
                            "The registration code is generated by an annotation processor at build time, so a clean and full rebuild often helps to solve this problem");
        }

        if (optionDescriptor.getContainer().optionsAreDiscoverable()) {
            runtimeOptionParser.addDescriptor(optionDescriptor);
        }
    }

    @SuppressWarnings("unused")
    private void collectOptionKeysExtension(DuringAnalysisAccess access, OptionKey<?> optionKey, ObjectScanner.ScanReason reason) {
        if (optionKey instanceof HostedOptionKey<?>) {
            /*
             * HostedOptionKey's are reached when building the NativeImage driver executable.
             */
            return;
        }

        ImageHeapConstant ihc = (ImageHeapConstant) ((FeatureImpl.DuringAnalysisAccessImpl) access).getUniverse().getSnippetReflection().forObject(optionKey);
        VMError.guarantee(ihc.isInSharedLayer(), "Newly seen key outside of base layer %s", optionKey);
    }

    /**
     * Creates a list of the {@link RuntimeOptionKeyFlag#RegisterForIsolateArgumentParser} option
     * key values. The order of this list must be the same as seen in the initial layer.
     */
    private static byte[] layeredCreateDefaultValues(HostedOptionParser optionParser) {
        List<RuntimeOptionKey<?>> options = layeredIsolateArgumentOptions(optionParser);
        return IsolateArgumentParser.createDefaultValuesArray(options);
    }

    private static byte[] layeredCreateDefaultStrings(HostedOptionParser optionParser) {
        List<RuntimeOptionKey<?>> options = layeredIsolateArgumentOptions(optionParser);
        return IsolateArgumentParser.createDefaultStringsArray(options);
    }

    private static ArrayList<RuntimeOptionKey<?>> layeredIsolateArgumentOptions(HostedOptionParser optionParser) {
        ArrayList<RuntimeOptionKey<?>> runtimeKeys = new ArrayList<>();
        for (var descriptor : optionParser.getAllRuntimeOptions().getValues()) {
            if (descriptor.getOptionKey() instanceof RuntimeOptionKey<?> runtimeOptionKey && runtimeOptionKey.shouldRegisterForIsolateArgumentParser()) {
                runtimeKeys.add(runtimeOptionKey);
            }
        }
        runtimeKeys.sort(Comparator.comparingInt(IsolateArgumentParser::getOptionIndex));
        return runtimeKeys;
    }

    private void addDefaultValuesObject(NativeImageHeap heap, @SuppressWarnings("unused") HostedUniverse hUniverse) {
        String addReason = "Registered as a required heap constant within RuntimeOptionFeature";

        heap.addObject(defaultValues, false, addReason);
        heap.addObject(defaultStrings, false, addReason);
    }

    public static void registerOptionAsRead(FeatureImpl.BeforeAnalysisAccessImpl accessImpl, Class<?> clazz, String fieldName) {
        try {
            Field javaField = clazz.getField(fieldName);
            AnalysisField analysisField = accessImpl.getMetaAccess().lookupJavaField(javaField);
            accessImpl.registerAsRead(analysisField, "it is a runtime option field");
        } catch (NoSuchFieldException | SecurityException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
