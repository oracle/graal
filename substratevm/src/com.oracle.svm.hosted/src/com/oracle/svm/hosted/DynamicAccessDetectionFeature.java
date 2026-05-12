/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.hosted.driver.IncludeOptionsSupport.parseIncludeSelector;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.LocatableMultiOptionValue;
import com.oracle.svm.shared.option.SubstrateOptionsParser;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * This feature installs and manages the hosted support used by {@link DynamicAccessDetectionPhase}
 * to report dynamic access calls that may require metadata.
 */
@AutomaticallyRegisteredFeature
public final class DynamicAccessDetectionFeature implements InternalFeature {

    public static final String TRACK_ALL = "all";
    private static final String TRACK_NONE = "none";
    private static final String TO_CONSOLE = "to-console";
    private static final String NO_DUMP = "no-dump";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageClassLoader imageClassLoader = ((FeatureImpl.AfterRegistrationAccessImpl) access).getImageClassLoader();
        NativeImageClassLoaderSupport support = imageClassLoader.classLoaderSupport;
        NativeImageClassLoaderSupport.IncludeSelectors dynamicAccessSelectors = support.getDynamicAccessSelectors();

        EconomicSet<String> tmpSet = EconomicSet.create();
        tmpSet.addAll(dynamicAccessSelectors.classpathEntries().stream()
                        .map(path -> path.toAbsolutePath().toString())
                        .collect(Collectors.toSet()));
        tmpSet.addAll(dynamicAccessSelectors.moduleNames());
        tmpSet.addAll(dynamicAccessSelectors.packages().stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet()));
        EconomicSet<String> sourceEntries = EconomicSet.create(tmpSet);

        var reportOptions = reportOptionsFromTrackDynamicAccessOptions(SubstrateOptions.TrackDynamicAccess.getValue());
        ImageSingletons.add(DynamicAccessDetectionSupport.class, new DynamicAccessDetectionSupport(sourceEntries, reportOptions));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        AnalysisMetaAccess metaAccess = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getMetaAccess();
        DynamicAccessMethodLookupSupport dynamicAccessMethodLookupSupport = new DynamicAccessMethodLookupSupport(metaAccess);
        ImageSingletons.add(DynamicAccessMethodLookupSupport.class, dynamicAccessMethodLookupSupport);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        DynamicAccessDetectionSupport.singleton().reportDynamicAccess();
        DynamicAccessMethodLookupSupport.instance().clear();
    }

    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        DynamicAccessDetectionSupport.singleton().clear();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        ImageClassLoader imageClassLoader = ((FeatureImpl.IsInConfigurationAccessImpl) access).getImageClassLoader();
        NativeImageClassLoaderSupport support = imageClassLoader.classLoaderSupport;
        return !support.dynamicAccessSelectorsEmpty();
    }

    private static String dynamicAccessPossibleOptions() {
        return String.format("[%s, %s, %s, %s, %s]",
                        TRACK_ALL, TRACK_NONE, TO_CONSOLE, NO_DUMP, IncludeOptionsSupport.possibleExtendedOptions());
    }

    private static DynamicAccessDetectionSupport.ReportOptions reportOptionsFromTrackDynamicAccessOptions(AccumulatingLocatableMultiOptionValue.Strings options) {
        boolean printToConsole = false;
        boolean dumpJsonFiles = true;
        for (String optionValue : options.values()) {
            switch (optionValue) {
                case TO_CONSOLE -> printToConsole = true;
                case NO_DUMP -> dumpJsonFiles = false;
                case TRACK_NONE -> {
                    printToConsole = false;
                    dumpJsonFiles = true;
                }
            }
        }
        return new DynamicAccessDetectionSupport.ReportOptions(printToConsole, dumpJsonFiles);
    }

    public static void parseDynamicAccessOptions(EconomicMap<OptionKey<?>, Object> hostedValues, NativeImageClassLoaderSupport classLoaderSupport) {
        AccumulatingLocatableMultiOptionValue.Strings trackDynamicAccess = SubstrateOptions.TrackDynamicAccess.getValue(new OptionValues(hostedValues));
        Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> valuesWithOrigins = trackDynamicAccess.getValuesWithOrigins();
        valuesWithOrigins.forEach(valueWithOrigin -> {
            String optionArgument = SubstrateOptionsParser.commandArgument(SubstrateOptions.TrackDynamicAccess, valueWithOrigin.value(), true, false);
            var options = Arrays.stream(valueWithOrigin.value().split(",")).toList();
            for (String option : options) {
                UserError.guarantee(!option.isEmpty(), "Option %s from %s cannot be passed an empty string",
                                optionArgument, valueWithOrigin.origin());
                switch (option) {
                    case TRACK_ALL -> classLoaderSupport.setTrackAllDynamicAccess(valueWithOrigin);
                    case TRACK_NONE -> classLoaderSupport.clearDynamicAccessSelectors();
                    case TO_CONSOLE, NO_DUMP -> {
                        // These options are parsed later in the afterRegistration hook
                    }
                    default -> parseIncludeSelector(optionArgument, valueWithOrigin, classLoaderSupport.getDynamicAccessSelectors(), IncludeOptionsSupport.ExtendedOption.parse(option),
                                    dynamicAccessPossibleOptions());
                }
            }
        });
    }
}
