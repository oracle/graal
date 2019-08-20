/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.APIOption.APIOptionKind;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

class APIOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    static final class OptionInfo {
        final String builderOption;
        final String defaultValue;
        final String helpText;
        final boolean hasPathArguments;
        final boolean defaultFinal;
        final String deprecationWarning;

        final List<Function<Object, Object>> valueTransformers;

        OptionInfo(String builderOption, String defaultValue, String helpText, boolean hasPathArguments, boolean defaultFinal, String deprecationWarning,
                        List<Function<Object, Object>> valueTransformers) {
            this.builderOption = builderOption;
            this.defaultValue = defaultValue;
            this.helpText = helpText;
            this.hasPathArguments = hasPathArguments;
            this.defaultFinal = defaultFinal;
            this.deprecationWarning = deprecationWarning;
            this.valueTransformers = valueTransformers;
        }

        boolean isDeprecated() {
            return deprecationWarning.length() > 0;
        }
    }

    private final SortedMap<String, OptionInfo> apiOptions;

    APIOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
        if (NativeImage.IS_AOT) {
            apiOptions = ImageSingletons.lookup(APIOptionCollector.class).options;
        } else {
            List<Class<? extends OptionDescriptors>> optionDescriptorsList = new ArrayList<>();
            ServiceLoader<OptionDescriptors> serviceLoader = ServiceLoader.load(OptionDescriptors.class, nativeImage.getClass().getClassLoader());
            for (OptionDescriptors optionDescriptors : serviceLoader) {
                optionDescriptorsList.add(optionDescriptors.getClass());
            }
            apiOptions = extractOptions(optionDescriptorsList);
        }
    }

    static SortedMap<String, OptionInfo> extractOptions(List<Class<? extends OptionDescriptors>> optionsClasses) {
        SortedMap<String, OptionDescriptor> hostedOptions = new TreeMap<>();
        SortedMap<String, OptionDescriptor> runtimeOptions = new TreeMap<>();
        HostedOptionParser.collectOptions(optionsClasses, hostedOptions, runtimeOptions);
        SortedMap<String, OptionInfo> apiOptions = new TreeMap<>();
        hostedOptions.values().forEach(o -> extractOption(NativeImage.oH, o, apiOptions));
        runtimeOptions.values().forEach(o -> extractOption(NativeImage.oR, o, apiOptions));
        return apiOptions;
    }

    private static void extractOption(String optionPrefix, OptionDescriptor optionDescriptor, SortedMap<String, OptionInfo> apiOptions) {
        try {
            Field optionField = optionDescriptor.getDeclaringClass().getDeclaredField(optionDescriptor.getFieldName());
            APIOption[] apiAnnotations = optionField.getAnnotationsByType(APIOption.class);

            for (APIOption apiAnnotation : apiAnnotations) {
                String builderOption = optionPrefix;
                String apiOptionName = APIOption.Utils.name(apiAnnotation);
                String rawOptionName = optionDescriptor.getName();
                boolean booleanOption = false;
                if (optionDescriptor.getOptionValueType().equals(Boolean.class)) {
                    VMError.guarantee(!apiAnnotation.kind().equals(APIOptionKind.Paths),
                                    String.format("Boolean APIOption %s(%s) cannot use APIOptionKind.Paths", apiOptionName, rawOptionName));
                    VMError.guarantee(apiAnnotation.defaultValue().length == 0,
                                    String.format("Boolean APIOption %s(%s) cannot use APIOption.defaultValue", apiOptionName, rawOptionName));
                    VMError.guarantee(apiAnnotation.fixedValue().length == 0,
                                    String.format("Boolean APIOption %s(%s) cannot use APIOption.fixedValue", apiOptionName, rawOptionName));
                    builderOption += apiAnnotation.kind().equals(APIOptionKind.Negated) ? "-" : "+";
                    builderOption += rawOptionName;
                    booleanOption = true;
                } else {
                    VMError.guarantee(!apiAnnotation.kind().equals(APIOptionKind.Negated),
                                    String.format("Non-boolean APIOption %s(%s) cannot use APIOptionKind.Negated", apiOptionName, rawOptionName));
                    VMError.guarantee(apiAnnotation.defaultValue().length <= 1,
                                    String.format("APIOption %s(%s) cannot have more than one APIOption.defaultValue", apiOptionName, rawOptionName));
                    VMError.guarantee(apiAnnotation.fixedValue().length <= 1,
                                    String.format("APIOption %s(%s) cannot have more than one APIOption.fixedValue", apiOptionName, rawOptionName));
                    VMError.guarantee(apiAnnotation.fixedValue().length == 0 && apiAnnotation.defaultValue().length == 0 ||
                                    (apiAnnotation.fixedValue().length > 0) ^ (apiAnnotation.defaultValue().length > 0),
                                    String.format("APIOption %s(%s) APIOption.defaultValue and APIOption.fixedValue cannot be combined", apiOptionName, rawOptionName));
                    builderOption += rawOptionName;
                    builderOption += "=";
                }

                String helpText = optionDescriptor.getHelp();
                if (!apiAnnotation.customHelp().isEmpty()) {
                    helpText = apiAnnotation.customHelp();
                }
                VMError.guarantee(helpText != null && !helpText.isEmpty(),
                                String.format("APIOption %s(%s) needs to provide help text", apiOptionName, rawOptionName));
                helpText = helpText.substring(0, 1).toLowerCase() + helpText.substring(1);

                String defaultValue = null;
                if (apiAnnotation.defaultValue().length > 0) {
                    defaultValue = apiAnnotation.defaultValue()[0];
                }
                if (apiAnnotation.fixedValue().length > 0) {
                    defaultValue = apiAnnotation.fixedValue()[0];
                }

                List<Function<Object, Object>> valueTransformers = new ArrayList<>(apiAnnotation.valueTransformer().length);
                for (Class<? extends Function<Object, Object>> transformerClass : apiAnnotation.valueTransformer()) {
                    try {
                        valueTransformers.add(ReflectionUtil.newInstance(transformerClass));
                    } catch (ReflectionUtilError ex) {
                        throw VMError.shouldNotReachHere(
                                        "Class specified as valueTransformer for @APIOption " + apiOptionName + " cannot be loaded or instantiated: " + transformerClass.getTypeName(), ex.getCause());
                    }
                }
                apiOptions.put(apiOptionName,
                                new APIOptionHandler.OptionInfo(builderOption, defaultValue, helpText, apiAnnotation.kind().equals(APIOptionKind.Paths),
                                                booleanOption || apiAnnotation.fixedValue().length > 0, apiAnnotation.deprecated(), valueTransformers));
            }
        } catch (NoSuchFieldException e) {
            /* Does not qualify as APIOption */
        }
    }

    @Override
    boolean consume(Queue<String> args) {
        String headArg = args.peek();
        String translatedOption = translateOption(headArg);
        if (translatedOption != null) {
            args.poll();
            nativeImage.addPlainImageBuilderArg(translatedOption);
            return true;
        }
        return false;
    }

    String translateOption(String arg) {
        String[] optionParts = arg.split("=", 2);
        OptionInfo option = apiOptions.get(optionParts[0]);
        if (option != null) {
            if (!option.deprecationWarning.isEmpty()) {
                NativeImage.showWarning("Using a deprecated option " + optionParts[0] + ". " + option.deprecationWarning);
            }
            String builderOption = option.builderOption;
            String optionValue = option.defaultValue;
            if (optionParts.length == 2) {
                if (option.defaultFinal) {
                    NativeImage.showError("Passing values to option " + optionParts[0] + " is not supported.");
                }
                optionValue = optionParts[1];
            }
            if (optionValue != null) {
                if (option.hasPathArguments) {
                    optionValue = Arrays.stream(optionValue.split(","))
                                    .filter(s -> !s.isEmpty())
                                    .map(this::tryCanonicalize)
                                    .collect(Collectors.joining(","));
                }
                Object transformed = optionValue;
                for (Function<Object, Object> transformer : option.valueTransformers) {
                    transformed = transformer.apply(transformed);
                }

                builderOption += transformed.toString();
            }

            return builderOption;
        }
        return null;
    }

    private String tryCanonicalize(String path) {
        try {
            return nativeImage.canonicalize(Paths.get(path)).toString();
        } catch (NativeImage.NativeImageError e) {
            /* Allow features to handle the path string. */
            return path;
        }
    }

    void printOptions(Consumer<String> println) {
        apiOptions.entrySet().stream()
                        .filter(e -> !e.getValue().isDeprecated())
                        .forEach(e -> SubstrateOptionsParser.printOption(println, e.getKey(), e.getValue().helpText, 4, 22, 66));
    }
}

@AutomaticFeature
final class APIOptionCollector implements Feature {

    SortedMap<String, APIOptionHandler.OptionInfo> options;

    @Platforms(Platform.HOSTED_ONLY.class)
    APIOptionCollector() {
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        List<Class<? extends OptionDescriptors>> optionClasses = accessImpl.getImageClassLoader().findSubclasses(OptionDescriptors.class, true);
        options = APIOptionHandler.extractOptions(optionClasses);
    }
}
