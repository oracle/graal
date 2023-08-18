/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.driver.NativeImage.BuildConfiguration;
import com.oracle.svm.driver.metainf.NativeImageMetaInfWalker;

final class MacroOption {

    Path getOptionDirectory() {
        return optionDirectory;
    }

    String getOptionName() {
        return optionName;
    }

    String getDescription(boolean commandLineStyle) {
        return kind.getDescriptionPrefix(commandLineStyle) + getOptionName();
    }

    @SuppressWarnings("serial")
    static final class VerboseInvalidMacroException extends RuntimeException {
        private final OptionUtils.MacroOptionKind forKind;
        private final MacroOption context;

        VerboseInvalidMacroException(String arg0, MacroOption context) {
            this(arg0, null, context);
        }

        VerboseInvalidMacroException(String arg0, OptionUtils.MacroOptionKind forKind, MacroOption context) {
            super(arg0);
            this.forKind = forKind;
            this.context = context;

        }

        public String getMessage(Registry registry) {
            StringBuilder sb = new StringBuilder();
            String message = super.getMessage();
            if (context != null) {
                sb.append(context.getDescription(false) + " contains ");
                if (!message.isEmpty()) {
                    sb.append(Character.toLowerCase(message.charAt(0)));
                    sb.append(message.substring(1));
                }
            } else {
                sb.append(message);
            }
            Consumer<String> lineOut = s -> sb.append("\n" + s);
            registry.showOptions(forKind, context == null, lineOut);
            return sb.toString();
        }
    }

    @SuppressWarnings("serial")
    static final class AddedTwiceException extends RuntimeException {
        private final MacroOption option;
        private final MacroOption context;

        AddedTwiceException(MacroOption option, MacroOption context) {
            this.option = option;
            this.context = context;
        }

        @Override
        public String getMessage() {
            StringBuilder sb = new StringBuilder();
            if (context != null) {
                sb.append("MacroOption ").append(context.getDescription(false));
                if (option.equals(context)) {
                    sb.append(" cannot require itself");
                } else {
                    sb.append(" requires ").append(option.getDescription(false)).append(" more than once");
                }

            } else {
                sb.append("Command line option ").append(option.getDescription(true));
                sb.append(" used more than once");
            }
            return sb.toString();
        }
    }

    static final class EnabledOption {
        private final MacroOption option;
        private final String optionArg;
        private final boolean enabledFromCommandline;

        private EnabledOption(MacroOption option, String optionArg, boolean enabledFromCommandline) {
            this.option = Objects.requireNonNull(option);
            this.optionArg = optionArg;
            this.enabledFromCommandline = enabledFromCommandline;
        }

        private String resolvePropertyValue(BuildConfiguration config, String val) {
            return NativeImage.resolvePropertyValue(val, optionArg, getOption().optionDirectory, config);
        }

        String getProperty(BuildConfiguration config, String key, String defaultVal) {
            String val = option.properties.get(key);
            if (val == null) {
                return defaultVal;
            }
            return resolvePropertyValue(config, val);
        }

        String getProperty(BuildConfiguration config, String key) {
            return getProperty(config, key, null);
        }

        boolean forEachPropertyValue(BuildConfiguration config, String propertyKey, Consumer<String> target) {
            return forEachPropertyValue(config, propertyKey, target, " ");
        }

        boolean forEachPropertyValue(BuildConfiguration config, String propertyKey, Consumer<String> target, String separatorRegex) {
            Function<String, String> resolvePropertyValue = str -> resolvePropertyValue(config, str);
            return NativeImage.forEachPropertyValue(option.properties.get(propertyKey), target, resolvePropertyValue, separatorRegex);
        }

        MacroOption getOption() {
            return option;
        }

        boolean isEnabledFromCommandline() {
            return enabledFromCommandline;
        }
    }

    static final class Registry {
        private final Map<OptionUtils.MacroOptionKind, Map<String, MacroOption>> supported = new HashMap<>();
        private final LinkedHashSet<EnabledOption> enabled = new LinkedHashSet<>();

        private static Map<OptionUtils.MacroOptionKind, Map<String, MacroOption>> collectMacroOptions(Path rootDir) throws IOException {
            Map<OptionUtils.MacroOptionKind, Map<String, MacroOption>> result = new HashMap<>();
            for (OptionUtils.MacroOptionKind kind : OptionUtils.MacroOptionKind.values()) {
                Path optionsDir = rootDir.resolve(kind.subdir);
                Map<String, MacroOption> collectedOptions = Collections.emptyMap();
                if (Files.isDirectory(optionsDir)) {
                    collectedOptions = Files.list(optionsDir).filter(Files::isDirectory)
                                    .filter(optionDir -> Files.isReadable(optionDir.resolve(NativeImageMetaInfWalker.nativeImagePropertiesFilename)))
                                    .map(MacroOption::create).filter(Objects::nonNull)
                                    .collect(Collectors.toMap(MacroOption::getOptionName, Function.identity()));
                }
                result.put(kind, collectedOptions);
            }
            return result;
        }

        Registry() {
            for (OptionUtils.MacroOptionKind kind : OptionUtils.MacroOptionKind.values()) {
                supported.put(kind, new HashMap<>());
            }
        }

        void addMacroOptionRoot(Path rootDir) {
            /* Discover MacroOptions and add to supported */
            try {
                collectMacroOptions(rootDir).forEach((optionKind, optionMap) -> {
                    supported.get(optionKind).putAll(optionMap);
                });
            } catch (IOException e) {
                throw new OptionUtils.InvalidMacroException("Error while discovering supported MacroOptions in " + rootDir + ": " + e.getMessage());
            }
        }

        Set<String> getAvailableOptions(OptionUtils.MacroOptionKind forKind) {
            return supported.get(forKind).keySet();
        }

        void showOptions(OptionUtils.MacroOptionKind forKind, boolean commandLineStyle, Consumer<String> lineOut) {
            List<String> optionsToShow = new ArrayList<>();
            for (OptionUtils.MacroOptionKind kind : OptionUtils.MacroOptionKind.values()) {
                if (forKind != null && !kind.equals(forKind)) {
                    continue;
                }
                if (forKind == null && kind == OptionUtils.MacroOptionKind.Macro) {
                    // skip non-API macro options by default
                    continue;
                }
                for (MacroOption option : supported.get(kind).values()) {
                    if (!option.kind.subdir.isEmpty()) {
                        String linePrefix = "    ";
                        if (commandLineStyle) {
                            linePrefix += OptionUtils.MacroOptionKind.macroOptionPrefix;
                        }
                        optionsToShow.add(linePrefix + option);
                    }
                }
            }
            if (!optionsToShow.isEmpty()) {
                StringBuilder sb = new StringBuilder().append("Available ");
                if (forKind != null) {
                    sb.append(forKind.toString()).append(' ');
                } else {
                    sb.append("macro-");
                }
                lineOut.accept(sb.append("options are:").toString());
                optionsToShow.stream().sorted().forEachOrdered(lineOut);
            }
        }

        MacroOption getMacroOption(OptionUtils.MacroOptionKind kindPart, String optionName) {
            return supported.get(kindPart).get(optionName);
        }

        boolean enableOption(BuildConfiguration config, String optionString, HashSet<MacroOption> addedCheck, MacroOption context, Consumer<EnabledOption> enabler) {
            String specString;
            if (context == null) {
                if (optionString.startsWith(OptionUtils.MacroOptionKind.macroOptionPrefix)) {
                    specString = optionString.substring(OptionUtils.MacroOptionKind.macroOptionPrefix.length());
                } else {
                    return false;
                }
            } else {
                specString = optionString;
            }

            String[] specParts = specString.split(":", 2);
            if (specParts.length != 2) {
                if (context == null) {
                    return false;
                } else {
                    throw new VerboseInvalidMacroException("Invalid option specification: " + optionString, context);
                }
            }

            OptionUtils.MacroOptionKind kindPart;
            try {
                kindPart = OptionUtils.MacroOptionKind.fromString(specParts[0]);
            } catch (Exception e) {
                if (context == null) {
                    return false;
                } else {
                    throw new VerboseInvalidMacroException("Unknown kind in option specification: " + optionString, context);
                }
            }

            String specNameParts = specParts[1];
            if (specNameParts.isEmpty()) {
                throw new VerboseInvalidMacroException("Empty option specification: " + optionString, kindPart, context);
            }

            if (specNameParts.equals("all")) {
                if (!kindPart.allowAll) {
                    throw new VerboseInvalidMacroException("Empty option specification: " + kindPart + " does no support 'all'", kindPart, context);
                }
                for (String optionName : getAvailableOptions(kindPart)) {
                    MacroOption option = getMacroOption(kindPart, optionName);
                    if (Boolean.parseBoolean(option.properties.getOrDefault("ExcludeFromAll", "false"))) {
                        continue;
                    }
                    enableResolved(config, option, null, addedCheck, context, enabler);
                }
                return true;
            }

            String[] parts = specNameParts.split("=", 2);
            String optionName = parts[0];
            MacroOption option = getMacroOption(kindPart, optionName);
            if (option != null) {
                String optionArg = parts.length == 2 ? parts[1] : null;
                enableResolved(config, option, optionArg, addedCheck, context, enabler);
            } else {
                throw new VerboseInvalidMacroException("Unknown name in option specification: " + kindPart + ":" + optionName, kindPart, context);
            }
            return true;
        }

        private void enableResolved(BuildConfiguration config, MacroOption option, String optionArg, HashSet<MacroOption> addedCheck, MacroOption context, Consumer<EnabledOption> enabler) {
            if (addedCheck.contains(option)) {
                return;
            }
            addedCheck.add(option);

            EnabledOption enabledOption = new EnabledOption(option, optionArg, context == null);
            if (optionArg == null) {
                String defaultArg = enabledOption.getProperty(config, "DefaultArg");
                if (defaultArg != null) {
                    enabledOption = new EnabledOption(option, defaultArg, context == null);
                }
            }

            String requires = enabledOption.getProperty(config, "Requires", "");
            if (!requires.isEmpty()) {
                for (String specString : requires.split(" ")) {
                    enableOption(config, specString, addedCheck, option, enabler);
                }
            }

            if (option.kind.equals(OptionUtils.MacroOptionKind.Language)) {
                MacroOption truffleOption = getMacroOption(OptionUtils.MacroOptionKind.Macro, "truffle");
                if (truffleOption == null) {
                    throw new VerboseInvalidMacroException("Cannot locate the truffle macro", null);
                }
                if (!addedCheck.contains(truffleOption)) {
                    /*
                     * Every language requires Truffle. If it is not specified explicitly as a
                     * requirement, add it automatically.
                     */
                    enableResolved(config, truffleOption, null, addedCheck, option, enabler);
                }
            }
            enabler.accept(enabledOption);
            enabled.add(enabledOption);
        }

        LinkedHashSet<EnabledOption> getEnabledOptions(OptionUtils.MacroOptionKind kind) {
            return enabled.stream().filter(eo -> kind.equals(eo.option.kind)).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Stream<EnabledOption> getEnabledOptionsStream(OptionUtils.MacroOptionKind kind, OptionUtils.MacroOptionKind... otherKinds) {
            EnumSet<OptionUtils.MacroOptionKind> kindSet = EnumSet.of(kind, otherKinds);
            return enabled.stream().filter(eo -> kindSet.contains(eo.option.kind));
        }

        LinkedHashSet<EnabledOption> getEnabledOptions() {
            return enabled;
        }

        EnabledOption getEnabledOption(MacroOption option) {
            return enabled.stream().filter(eo -> eo.getOption().equals(option)).findFirst().orElse(null);
        }
    }

    private final String optionName;
    private final Path optionDirectory;

    final OptionUtils.MacroOptionKind kind;
    private final Map<String, String> properties;

    private static MacroOption create(Path macroOptionDirectory) {
        try {
            return new MacroOption(macroOptionDirectory);
        } catch (Exception e) {
            return null;
        }
    }

    private MacroOption(Path optionDirectory) {
        this.kind = OptionUtils.MacroOptionKind.fromSubdir(optionDirectory.getParent().getFileName().toString());
        this.optionName = optionDirectory.getFileName().toString();
        this.optionDirectory = optionDirectory;
        this.properties = NativeImage.loadProperties(optionDirectory.resolve(NativeImageMetaInfWalker.nativeImagePropertiesFilename));
    }

    @Override
    public String toString() {
        return getDescription(false);
    }
}
