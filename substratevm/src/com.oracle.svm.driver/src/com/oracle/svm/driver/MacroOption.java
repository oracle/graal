/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MacroOption {
    enum MacroOptionKind {
        Language("languages") {
            @Override
            protected String getOptionDescription(MacroOption option, boolean commandLineStyle) {
                return (commandLineStyle ? "--" : name() + ":") + option.getOptionName();
            }
        },
        Tool("tools") {
            @Override
            protected String getOptionDescription(MacroOption option, boolean commandLineStyle) {
                return (commandLineStyle ? "--tool." : name() + ":") + option.getOptionName();
            }
        },
        Builtin("");

        final String subdir;

        MacroOptionKind(String subdir) {
            this.subdir = subdir;
        }

        @SuppressWarnings("unused")
        protected String getOptionDescription(MacroOption option, boolean commandLineStyle) {
            return "";
        }

        static MacroOptionKind fromSubdir(String subdir) {
            for (MacroOptionKind kind : MacroOptionKind.values()) {
                if (kind.subdir.equals(subdir)) {
                    return kind;
                }
            }
            throw new InvalidMacroException("No MacroOptionKind for subDir: " + subdir);
        }
    }

    Path getImageJarsDirectory() {
        return optionDirectory;
    }

    Path getBuilderJarsDirectory() {
        return optionDirectory.resolve("builder");
    }

    String getOptionName() {
        return optionName;
    }

    String getDescription(boolean commandLineStyle) {
        return kind.getOptionDescription(this, commandLineStyle);
    }

    @SuppressWarnings("serial")
    static final class InvalidMacroException extends RuntimeException {
        InvalidMacroException(String arg0) {
            super(arg0);
        }
    }

    @SuppressWarnings("serial")
    static final class VerboseInvalidMacroException extends RuntimeException {
        final MacroOptionKind forKind;
        final MacroOption context;

        VerboseInvalidMacroException(String arg0, MacroOption context) {
            this(arg0, null, context);
        }

        VerboseInvalidMacroException(String arg0, MacroOptionKind forKind, MacroOption context) {
            super(arg0);
            this.forKind = forKind;
            this.context = context;

        }

        public String getMessage(Registry registry) {
            StringBuilder sb = new StringBuilder();
            if (context != null) {
                sb.append(context.getDescription(false) + " contains ");
            }
            Consumer<String> lineOut = s -> sb.append(s + "\n");
            lineOut.accept(super.getMessage());
            registry.showOptions(forKind, context == null, lineOut);
            return sb.toString();
        }
    }

    static final class EnabledOption {
        private final MacroOption option;
        private final String optionArg;

        private EnabledOption(MacroOption option, String optionArg) {
            this.option = option;
            this.optionArg = optionArg;
        }

        private String resolvePropertyValue(String val) {
            String resultVal = val;
            if (optionArg != null) {
                /* Substitute ${*} -> optionArg in resultVal (always possible) */
                resultVal = resultVal.replace("${*}", optionArg);
                /*
                 * If optionArg consists of "<argName>:<argValue>,..." additionally perform
                 * substitutions of kind ${<argName>} -> <argValue> on resultVal.
                 */
                for (String argNameValue : optionArg.split(",")) {
                    String[] splitted = argNameValue.split(":");
                    if (splitted.length == 2) {
                        String argName = splitted[0];
                        String argValue = splitted[1];
                        if (!argName.isEmpty()) {
                            resultVal = resultVal.replace("${" + argName + "}", argValue);
                        }
                    }
                }
            }
            /* Substitute ${.} -> absolute path to optionDirectory */
            resultVal = resultVal.replace("${.}", getOption().optionDirectory.toString());
            return resultVal;
        }

        String getProperty(String key, String defaultVal) {
            String val = option.properties.get(key);
            if (val == null) {
                return defaultVal;
            }
            return resolvePropertyValue(val);
        }

        String getProperty(String key) {
            return getProperty(key, null);
        }

        boolean forEachPropertyValue(String propertyKey, Consumer<String> target) {
            String propertyValueRaw = option.properties.get(propertyKey);
            if (propertyValueRaw != null) {
                for (String propertyValue : Arrays.asList(propertyValueRaw.split(" "))) {
                    target.accept(resolvePropertyValue(propertyValue));
                }
                return true;
            }
            return false;
        }

        MacroOption getOption() {
            return option;
        }
    }

    static final class Registry {
        private final Map<MacroOptionKind, Map<String, MacroOption>> supported;
        private final LinkedHashSet<EnabledOption> enabled = new LinkedHashSet<>();

        private static Map<MacroOptionKind, Map<String, MacroOption>> collectMacroOptions(Path rootDir) throws IOException {
            Map<MacroOptionKind, Map<String, MacroOption>> result = new HashMap<>();
            for (MacroOptionKind kind : MacroOptionKind.values()) {
                if (kind.subdir.isEmpty()) {
                    continue;
                }
                Path optionDir = rootDir.resolve(kind.subdir);
                Map<String, MacroOption> collectedOptions = Collections.emptyMap();
                if (Files.isDirectory(optionDir)) {
                    collectedOptions = Files.list(optionDir).filter(Files::isDirectory)
                                    .map(MacroOption::create).filter(Objects::nonNull)
                                    .collect(Collectors.toMap(MacroOption::getOptionName, Function.identity()));
                }
                result.put(kind, collectedOptions);
            }
            return result;
        }

        Registry(Path rootDir) {
            /* Discover supported MacroOptions */
            try {
                supported = collectMacroOptions(rootDir);
            } catch (IOException e) {
                throw new InvalidMacroException("Error while discovering supported MacroOptions: " + e.getMessage());
            }
        }

        MacroOption addBuiltin(String optionName) {
            MacroOption builtin = new MacroOption(optionName);
            supported.computeIfAbsent(MacroOptionKind.Builtin, key -> new HashMap<>()).put(optionName, builtin);
            return builtin;
        }

        void showOptions(MacroOptionKind forKind, boolean commandLineStyle, Consumer<String> lineOut) {
            List<String> optionsToShow = new ArrayList<>();
            for (MacroOptionKind kind : MacroOptionKind.values()) {
                if (forKind != null && !kind.equals(forKind)) {
                    continue;
                }
                for (MacroOption option : supported.get(kind).values()) {
                    String showEntry = option.getDescription(commandLineStyle);
                    if (!showEntry.isEmpty()) {
                        optionsToShow.add("    " + showEntry);
                    }
                }
            }
            if (!optionsToShow.isEmpty()) {
                lineOut.accept("Available " + (forKind != null ? forKind.name() + " options" : "MacroOptions") + " are:");
                optionsToShow.forEach(lineOut);
            }
        }

        void enableOptions(String optionsSpec, MacroOptionKind kind) {
            try {
                enableOptions(optionsSpec, kind, new HashSet<>(), null);
            } catch (AddedTwiceException e) {
                throw new InvalidMacroException(e.getMessage() + " already added before");
            }
        }

        private void enableOptions(String optionsSpec, MacroOptionKind kind, HashSet<MacroOption> addedCheck, MacroOption context) {
            if (optionsSpec.isEmpty()) {
                return;
            }

            for (String specString : optionsSpec.split(" ")) {
                MacroOptionKind kindPart;
                String specNameParts;

                if (kind != null) {
                    kindPart = kind;
                    specNameParts = specString;
                } else {
                    String[] specParts = specString.split(":");
                    if (specParts.length != 2) {
                        throw new VerboseInvalidMacroException("invalid option specification: " + specString, context);
                    }
                    try {
                        kindPart = MacroOptionKind.valueOf(specParts[0]);
                    } catch (Exception e) {
                        throw new VerboseInvalidMacroException("unknown kind in option specification: " + specString, context);
                    }
                    specNameParts = specParts[1];
                }
                if (specNameParts.isEmpty()) {
                    throw new VerboseInvalidMacroException("empty option specification: " + specString, context);
                }
                String[] parts = specNameParts.split("=");
                String optionName = parts[0];
                String optionArg = null;
                if (parts.length == 2) {
                    /* We have an option argument */
                    optionArg = parts[1];
                }
                MacroOption option = supported.get(kindPart).get(optionName);
                if (option != null) {
                    enableResolved(option, optionArg, addedCheck);
                } else {
                    throw new VerboseInvalidMacroException("unknown name in option specification: " + kindPart + ":" + optionName, kindPart, context);
                }
            }
        }

        private void enableResolved(MacroOption option, String optionArg, HashSet<MacroOption> addedCheck) {
            if (addedCheck.contains(option)) {
                if (option.kind.equals(MacroOptionKind.Builtin)) {
                    return;
                }
                throw new AddedTwiceException(option);
            }
            try {
                addedCheck.add(option);
                EnabledOption enabledOption = new EnabledOption(option, optionArg);
                String requires = enabledOption.getProperty("Requires");
                if (requires != null) {
                    enableOptions(requires, null, addedCheck, option);
                }
                enabled.add(enabledOption);
            } catch (AddedTwiceException e) {
                throw new InvalidMacroException(option + " cannot add already added " + e.getMessage());
            }
        }

        @SuppressWarnings("serial")
        private static final class AddedTwiceException extends RuntimeException {
            AddedTwiceException(MacroOption option) {
                super(option.toString());
            }
        }

        LinkedHashSet<EnabledOption> getEnabledOptions(MacroOptionKind kind) {
            return enabled.stream().filter(eo -> kind.equals(eo.option.kind)).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        LinkedHashSet<EnabledOption> getEnabledOptions() {
            return enabled;
        }

        EnabledOption getEnabledOption(MacroOption option) {
            return enabled.stream().filter(eo -> eo.getOption().equals(option)).findFirst().orElse(null);
        }

        void applyOptions(NativeImage nativeImage) {
            for (EnabledOption enabledOption : getEnabledOptions()) {
                if (enabledOption.getOption().kind.equals(MacroOptionKind.Builtin)) {
                    continue;
                }

                if (Files.isDirectory(enabledOption.getOption().getBuilderJarsDirectory())) {
                    NativeImage.getJars(enabledOption.getOption().getBuilderJarsDirectory()).forEach(nativeImage::addImageBuilderClasspath);
                }
                NativeImage.getJars(enabledOption.getOption().getImageJarsDirectory()).forEach(nativeImage::addImageClasspath);

                String imageName = enabledOption.getProperty("ImageName");
                if (imageName != null) {
                    nativeImage.addImageBuilderArg(NativeImage.oHName + imageName);
                }

                String launcherClass = enabledOption.getProperty("LauncherClass");
                if (launcherClass != null) {
                    nativeImage.addImageBuilderArg(NativeImage.oHClass + launcherClass);
                }

                enabledOption.forEachPropertyValue("JavaArgs", nativeImage::addImageBuilderJavaArgs);
                enabledOption.forEachPropertyValue("Args", nativeImage::addImageBuilderArg);
            }
        }
    }

    private final String optionName;
    private final Path optionDirectory;

    final MacroOptionKind kind;
    private final Map<String, String> properties;

    private static MacroOption create(Path macroOptionDirectory) {
        try {
            return new MacroOption(macroOptionDirectory);
        } catch (Exception e) {
            return null;
        }
    }

    private MacroOption(Path optionDirectory) {
        this.kind = MacroOptionKind.fromSubdir(optionDirectory.getParent().getFileName().toString());
        this.optionName = optionDirectory.getFileName().toString();
        this.optionDirectory = optionDirectory;
        this.properties = NativeImage.loadProperties(optionDirectory.resolve("native-image.properties"));
    }

    private MacroOption(String optionName) {
        this.kind = MacroOptionKind.Builtin;
        this.optionName = optionName;
        this.optionDirectory = null;
        this.properties = Collections.emptyMap();
    }

    @Override
    public String toString() {
        return kind + " " + getOptionName();
    }
}
