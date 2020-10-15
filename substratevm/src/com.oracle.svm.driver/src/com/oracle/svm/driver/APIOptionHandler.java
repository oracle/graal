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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.APIOption.APIOptionKind;
import com.oracle.svm.core.option.APIOptionGroup;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

class APIOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    static final class OptionInfo {
        final String[] variants;
        final char valueSeparator;
        final String builderOption;
        final String defaultValue;
        final String helpText;
        final boolean hasPathArguments;
        final boolean defaultFinal;
        final String deprecationWarning;

        final List<Function<Object, Object>> valueTransformers;
        final APIOptionGroup group;

        OptionInfo(String[] variants, char valueSeparator, String builderOption, String defaultValue, String helpText, boolean hasPathArguments, boolean defaultFinal, String deprecationWarning,
                        List<Function<Object, Object>> valueTransformers, APIOptionGroup group) {
            this.variants = variants;
            this.valueSeparator = valueSeparator;
            this.builderOption = builderOption;
            this.defaultValue = defaultValue;
            this.helpText = helpText;
            this.hasPathArguments = hasPathArguments;
            this.defaultFinal = defaultFinal;
            this.deprecationWarning = deprecationWarning;
            this.valueTransformers = valueTransformers;
            this.group = group;
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
        Map<String, List<String>> groupDefaults = new HashMap<>();
        hostedOptions.values().forEach(o -> extractOption(NativeImage.oH, o, apiOptions, groupDefaults));
        runtimeOptions.values().forEach(o -> extractOption(NativeImage.oR, o, apiOptions, groupDefaults));
        groupDefaults.forEach((groupName, defaults) -> {
            if (defaults.size() > 1) {
                VMError.shouldNotReachHere(String.format("APIOptionGroup %s must only have a single default (but has: %s)",
                                groupName, String.join(", ", defaults)));
            }
        });
        return apiOptions;
    }

    private static void extractOption(String optionPrefix, OptionDescriptor optionDescriptor,
                    SortedMap<String, OptionInfo> apiOptions, Map<String, List<String>> groupDefaults) {
        try {
            Field optionField = optionDescriptor.getDeclaringClass().getDeclaredField(optionDescriptor.getFieldName());
            APIOption[] apiAnnotations = optionField.getAnnotationsByType(APIOption.class);

            for (APIOption apiAnnotation : apiAnnotations) {
                String builderOption = optionPrefix;
                if (apiAnnotation.name().length <= 0) {
                    VMError.shouldNotReachHere(String.format("APIOption for %s does not provide a name entry", optionDescriptor.getLocation()));
                }
                String apiOptionName = APIOption.Utils.optionName(apiAnnotation.name()[0]);
                String rawOptionName = optionDescriptor.getName();
                APIOptionGroup group = null;
                String defaultValue = null;

                boolean booleanOption = false;
                if (optionDescriptor.getOptionValueType().equals(Boolean.class)) {
                    if (!apiAnnotation.group().equals(APIOption.NullGroup.class)) {
                        try {
                            Class<? extends APIOptionGroup> groupClass = apiAnnotation.group();
                            group = ReflectionUtil.newInstance(groupClass);
                            String groupName = APIOption.Utils.groupName(group);
                            if (group.helpText() == null || group.helpText().isEmpty()) {
                                VMError.shouldNotReachHere(String.format("APIOptionGroup %s(%s) needs to provide help text", groupClass.getName(), group.name()));
                            }
                            String groupMember = apiAnnotation.name()[0];
                            apiOptionName = groupName + groupMember;
                            Boolean isEnabled = (Boolean) optionDescriptor.getOptionKey().getDefaultValue();
                            if (isEnabled) {
                                groupDefaults.computeIfAbsent(groupName, cls -> new ArrayList<>()).add(groupMember);
                                /* Use OptionInfo.defaultValue to remember group default value */
                                defaultValue = groupMember;
                            }
                        } catch (ReflectionUtilError ex) {
                            throw VMError.shouldNotReachHere(
                                            "Class specified as group for @APIOption " + apiOptionName + " cannot be loaded or instantiated: " + apiAnnotation.group().getTypeName(), ex.getCause());
                        }
                    }
                    if (apiAnnotation.kind().equals(APIOptionKind.Paths)) {
                        VMError.shouldNotReachHere(String.format("Boolean APIOption %s(%s) cannot use APIOptionKind.Paths", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.defaultValue().length > 0) {
                        VMError.shouldNotReachHere(String.format("Boolean APIOption %s(%s) cannot use APIOption.defaultValue", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.fixedValue().length > 0) {
                        VMError.shouldNotReachHere(String.format("Boolean APIOption %s(%s) cannot use APIOption.fixedValue", apiOptionName, rawOptionName));
                    }
                    builderOption += apiAnnotation.kind().equals(APIOptionKind.Negated) ? "-" : "+";
                    builderOption += rawOptionName;
                    booleanOption = true;
                } else {
                    if (!apiAnnotation.group().equals(APIOption.NullGroup.class)) {
                        VMError.shouldNotReachHere(String.format("Using @APIOption.group not supported for non-boolean APIOption %s(%s)", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.kind().equals(APIOptionKind.Negated)) {
                        VMError.shouldNotReachHere(String.format("Non-boolean APIOption %s(%s) cannot use APIOptionKind.Negated", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.defaultValue().length > 1) {
                        VMError.shouldNotReachHere(String.format("APIOption %s(%s) cannot have more than one APIOption.defaultValue", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.fixedValue().length > 1) {
                        VMError.shouldNotReachHere(String.format("APIOption %s(%s) cannot have more than one APIOption.fixedValue", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.fixedValue().length > 0 && apiAnnotation.defaultValue().length > 0) {
                        VMError.shouldNotReachHere(String.format("APIOption %s(%s) APIOption.defaultValue and APIOption.fixedValue cannot be combined", apiOptionName, rawOptionName));
                    }
                    if (apiAnnotation.defaultValue().length > 0) {
                        defaultValue = apiAnnotation.defaultValue()[0];
                    }
                    if (apiAnnotation.fixedValue().length > 0) {
                        defaultValue = apiAnnotation.fixedValue()[0];
                    }

                    builderOption += rawOptionName;
                    builderOption += "=";
                }

                String helpText = optionDescriptor.getHelp();
                if (!apiAnnotation.customHelp().isEmpty()) {
                    helpText = apiAnnotation.customHelp();
                }
                if (helpText == null || helpText.isEmpty()) {
                    VMError.shouldNotReachHere(String.format("APIOption %s(%s) needs to provide help text", apiOptionName, rawOptionName));
                }
                if (group == null) {
                    /* Regular help text needs to start with lower-case letter */
                    helpText = startLowerCase(helpText);
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
                                new APIOptionHandler.OptionInfo(apiAnnotation.name(), apiAnnotation.valueSeparator(), builderOption, defaultValue, helpText,
                                                apiAnnotation.kind().equals(APIOptionKind.Paths),
                                                booleanOption || apiAnnotation.fixedValue().length > 0, apiAnnotation.deprecated(), valueTransformers, group));
            }
        } catch (NoSuchFieldException e) {
            /* Does not qualify as APIOption */
        }
    }

    private static String startLowerCase(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
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
        OptionInfo option = null;
        String[] optionNameAndOptionValue = null;
        found: for (OptionInfo optionInfo : apiOptions.values()) {
            for (String variant : optionInfo.variants) {
                String optionName;
                if (optionInfo.group == null) {
                    optionName = APIOption.Utils.optionName(variant);
                } else {
                    optionName = APIOption.Utils.groupName(optionInfo.group) + variant;
                }
                if (arg.equals(optionName)) {
                    option = optionInfo;
                    optionNameAndOptionValue = new String[]{optionName};
                    break found;
                }
                if (arg.startsWith(optionName + optionInfo.valueSeparator)) {
                    option = optionInfo;
                    optionNameAndOptionValue = SubstrateUtil.split(arg, Character.toString(optionInfo.valueSeparator), 2);
                    break found;
                }
            }

        }
        if (option != null) {
            if (!option.deprecationWarning.isEmpty()) {
                NativeImage.showWarning("Using a deprecated option " + optionNameAndOptionValue[0] + ". " + option.deprecationWarning);
            }
            String builderOption = option.builderOption;
            /* If option is in group, defaultValue has different use */
            String optionValue = option.group != null ? null : option.defaultValue;
            if (optionNameAndOptionValue.length == 2) {
                if (option.defaultFinal) {
                    NativeImage.showError("Passing values to option " + optionNameAndOptionValue[0] + " is not supported.");
                }
                optionValue = optionNameAndOptionValue[1];
            }
            if (optionValue != null) {
                if (option.hasPathArguments) {
                    optionValue = Arrays.stream(SubstrateUtil.split(optionValue, ","))
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
        SortedMap<String, List<OptionInfo>> optionInfo = new TreeMap<>();
        apiOptions.forEach((optionName, option) -> {
            if (option.isDeprecated()) {
                return;
            }
            String groupOrOptionName = option.group != null ? APIOption.Utils.groupName(option.group) : optionName;
            if (optionInfo.containsKey(groupOrOptionName)) {
                List<OptionInfo> options = optionInfo.get(groupOrOptionName);
                if (options.size() == 1) {
                    /* Switch from singletonList to ArrayList */
                    options = new ArrayList<>(options);
                    optionInfo.put(groupOrOptionName, options);
                }
                options.add(option);
            } else {
                /* Start with space efficient singletonList */
                optionInfo.put(groupOrOptionName, Collections.singletonList(option));
            }
        });
        optionInfo.forEach((optionName, options) -> {
            if (options.size() == 1) {
                OptionInfo singleOption = options.get(0);
                if (singleOption.group == null) {
                    SubstrateOptionsParser.printOption(println, optionName, singleOption.helpText, 4, 22, 66);
                } else {
                    /*
                     * Only print option group with single entry if not enabled by default anyway.
                     */
                    if (!Arrays.asList(singleOption.variants).contains(singleOption.defaultValue)) {
                        printGroupOption(println, optionName, options);
                    }
                }
            } else {
                printGroupOption(println, optionName, options);
            }
        });
    }

    private static void printGroupOption(Consumer<String> println, String groupName, List<OptionInfo> options) {
        APIOptionGroup group = options.get(0).group;
        assert group != null;
        StringBuilder sb = new StringBuilder();

        sb.append(startLowerCase(group.helpText()));
        if (!group.helpText().endsWith(".")) {
            sb.append(".");
        }
        sb.append(" Allowed options for <value>:");
        SubstrateOptionsParser.printOption(println, groupName + "<value>", sb.toString(), 4, 22, 66);

        for (OptionInfo groupEntry : options) {
            assert groupEntry.group == group;
            sb.setLength(0);

            boolean first = true;
            boolean isDefault = false;
            for (String variant : groupEntry.variants) {
                if (variant.equals(groupEntry.defaultValue)) {
                    isDefault = true;
                }
                if (first) {
                    first = false;
                } else {
                    sb.append(" | ");
                }
                sb.append("'").append(variant).append("'");
            }
            sb.append(": ").append(groupEntry.helpText);
            if (isDefault) {
                sb.append(" (default)");
            }
            SubstrateOptionsParser.printOption(println, "", sb.toString(), 4, 22, 66);
        }
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
