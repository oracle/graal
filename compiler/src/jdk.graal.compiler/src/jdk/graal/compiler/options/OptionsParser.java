/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.util.EconomicHashSet;

/**
 * This class contains methods for parsing Graal options and matching them against a set of
 * {@link OptionDescriptors}. The {@link OptionDescriptors} are loaded via a {@link ServiceLoader}.
 */
public class OptionsParser {

    /**
     * Info about libgraal options.
     *
     * @param descriptors set of compiler options available in libgraal. These correspond to the
     *            reachable {@link OptionKey}s discovered during Native Image static analysis.
     * @param enterpriseOptions {@linkplain OptionKey#getName() names} of enterprise options
     */
    public record LibGraalOptionsInfo(EconomicMap<String, OptionDescriptor> descriptors, Set<String> enterpriseOptions) {
        public static LibGraalOptionsInfo create() {
            return new LibGraalOptionsInfo(EconomicMap.create(), new EconomicHashSet<>());
        }
    }

    /**
     * Compiler options info available in libgraal.
     */
    public static final LibGraalOptionsInfo libgraalOptions = LibGraalSupport.INSTANCE != null ? LibGraalOptionsInfo.create() : null;

    /**
     * Gets an iterable of available {@link OptionDescriptors}.
     */
    @ExcludeFromJacocoGeneratedReport("contains libgraal-only path")
    public static Iterable<OptionDescriptors> getOptionsLoader() {
        if (LibGraalSupport.inLibGraalRuntime()) {
            return List.of(new OptionDescriptorsMap(Objects.requireNonNull(libgraalOptions.descriptors, "missing options")));
        }
        if (LibGraalSupport.INSTANCE != null) {
            /*
             * Executing in the context of the libgraal class loader so use it to load the
             * OptionDescriptors.
             */
            ClassLoader libgraalLoader = OptionsParser.class.getClassLoader();
            return OptionsContainer.getDiscoverableOptions(libgraalLoader);
        } else {
            /*
             * The Graal module (i.e., jdk.graal.compiler) is loaded by the platform class loader.
             * Modules that depend on and extend Graal are loaded by the app class loader so use it
             * (instead of the platform class loader) to load the OptionDescriptors.
             */
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            return OptionsContainer.getDiscoverableOptions(loader);
        }
    }

    /**
     * Parses a map representing assignments of values to options.
     *
     * @param optionSettings option settings (i.e., assignments of values to options)
     * @param values the object in which to store the parsed values
     * @param loader source of the available {@link OptionDescriptors}
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOptions(EconomicMap<String, String> optionSettings, EconomicMap<OptionKey<?>, Object> values, Iterable<OptionDescriptors> loader) {
        if (optionSettings != null && !optionSettings.isEmpty()) {
            MapCursor<String, String> cursor = optionSettings.getEntries();
            while (cursor.advance()) {
                parseOption(cursor.getKey(), cursor.getValue(), values, loader);
            }
        }
    }

    /**
     * Parses an array of option settings of the form {@code "OptionKey=Value"} in {@code options},
     * adding parsed options to {@code values}.
     *
     * @param options array of option setting strings (i.e., assignments of values to options).
     * @param values the object in which to store the parsed values
     * @param loader source of the available {@link OptionDescriptors}
     * @throws IllegalArgumentException if there's a problem parsing any of {@code options}
     */
    public static void parseOptions(String[] options, EconomicMap<OptionKey<?>, Object> values, Iterable<OptionDescriptors> loader) {
        EconomicMap<String, String> settings = EconomicMap.create();
        for (String option : options) {
            parseOptionSettingTo(option, settings);
        }
        parseOptions(settings, values, loader);
    }

    /**
     * Parses a given option setting string and adds the parsed key and value to {@code dst}.
     *
     * @param optionSetting a string matching the pattern {@code <name>=<value>}
     */
    public static void parseOptionSettingTo(String optionSetting, EconomicMap<String, String> dst) {
        int eqIndex = optionSetting.indexOf('=');
        if (eqIndex == -1) {
            throw new IllegalArgumentException("Option setting has does not match the pattern <name>=<value>: " + optionSetting);
        }
        dst.put(optionSetting.substring(0, eqIndex), optionSetting.substring(eqIndex + 1));
    }

    /**
     * Splits a list of option settings into an array of options. If {@code options} starts with a
     * non-letter character, that character is used as the delimiter between options. Otherwise,
     * whitespace is the delimiter.
     *
     * @param options string containing a separated list of option settings.
     * @return an array of strings containing the individual parsed options.
     * @throws IllegalArgumentException if a non-whitespace delimiter is used and the delimiter
     *             appears repeated contiguously in {@code options}.
     */
    public static String[] splitOptions(String options) {
        String sepRegex = "\\s+";
        String toParse = options;
        if (!options.isEmpty() && !Character.isLetter(options.charAt(0))) {
            sepRegex = Pattern.quote(options.substring(0, 1));
            toParse = options.substring(1);
        }

        String[] settings = toParse.split(sepRegex);
        for (String optionSetting : settings) {
            if (optionSetting.isEmpty()) {
                throw new IllegalArgumentException(String.format("Delimiter '%s' is repeated contiguously in \"%s\"", options.charAt(0), options));
            }
        }
        return settings;
    }

    /**
     * Looks up an {@link OptionDescriptor} based on a given name.
     *
     * @param loader source of the available {@link OptionDescriptors}
     * @param name the name of the option to look up
     * @return the {@link OptionDescriptor} whose name equals {@code name} or null if not such
     *         descriptor is available
     */
    private static OptionDescriptor lookup(Iterable<OptionDescriptors> loader, String name) {
        for (OptionDescriptors optionDescriptors : loader) {
            OptionDescriptor desc = optionDescriptors.get(name);
            if (desc != null) {
                return desc;
            }
        }
        return null;
    }

    /**
     * Parses a given option name and value.
     *
     * @param name the option name
     * @param uncheckedValue the unchecked value for the option
     * @param values the object in which to store the parsed option and value
     * @param loader source of the available {@link OptionDescriptors}
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOption(String name, Object uncheckedValue, EconomicMap<OptionKey<?>, Object> values, Iterable<OptionDescriptors> loader) {

        OptionDescriptor desc = lookup(loader, name);
        if (desc == null) {
            Formatter msg = new Formatter();
            if (name.equals("PrintGraphFile")) {
                msg.format("Option PrintGraphFile has been removed - use PrintGraph=File instead");
            } else {
                List<OptionDescriptor> matches = fuzzyMatch(loader, name);
                msg.format("Could not find option %s", name);
                if (!matches.isEmpty()) {
                    msg.format("%nDid you mean one of the following?");
                    for (OptionDescriptor match : matches) {
                        msg.format("%n    %s=<value>", match.getName());
                    }
                }
            }
            throw new IllegalArgumentException(msg.toString());
        }

        Object value = parseOptionValue(desc, uncheckedValue);

        desc.getOptionKey().update(values, value);
    }

    /**
     * Parses a given option value with a known descriptor.
     */
    public static Object parseOptionValue(OptionDescriptor desc, Object uncheckedValue) {
        Class<?> optionType = desc.getOptionValueType();
        Object value;
        if (!(uncheckedValue instanceof String valueString)) {
            if (optionType != uncheckedValue.getClass()) {
                String type = optionType.getSimpleName();
                throw new IllegalArgumentException(type + " option '" + desc.getName() + "' must have " + type + " value, not " + uncheckedValue.getClass() + " [toString: " + uncheckedValue + "]");
            }
            value = uncheckedValue;
        } else {
            if (optionType == Boolean.class) {
                if ("true".equals(valueString)) {
                    value = Boolean.TRUE;
                } else if ("false".equals(valueString)) {
                    value = Boolean.FALSE;
                } else {
                    throw new IllegalArgumentException("Boolean option '" + desc.getName() + "' must have value \"true\" or \"false\", not \"" + uncheckedValue + "\"");
                }
            } else if (optionType == String.class) {
                value = valueString;
            } else if (Enum.class.isAssignableFrom(optionType)) {
                value = ((EnumOptionKey<?>) desc.getOptionKey()).valueOf(valueString);
            } else if (optionType == EconomicSet.class) {
                value = ((EnumMultiOptionKey<?>) desc.getOptionKey()).valueOf(valueString);
            } else {
                if (valueString.isEmpty()) {
                    throw new IllegalArgumentException("Non empty value required for option '" + desc.getName() + "'");
                }
                try {
                    if (optionType == Float.class) {
                        value = Float.parseFloat(valueString);
                    } else if (optionType == Double.class) {
                        value = Double.parseDouble(valueString);
                    } else if (optionType == Integer.class) {
                        value = (int) parseLong(valueString);
                    } else if (optionType == Long.class) {
                        value = parseLong(valueString);
                    } else {
                        throw new IllegalArgumentException("Wrong value for option '" + desc.getName() + "'");
                    }
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Value for option '" + desc.getName() + "' has invalid number format: " + valueString);
                }
            }
        }
        return value;
    }

    private static long parseLong(String v) {
        String valueString = v.toLowerCase(Locale.ROOT);
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     * <p>
     * Ported from str_similar() in globals.cpp.
     */
    public static float stringSimilarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    public static final float FUZZY_MATCH_THRESHOLD = 0.7F;

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    private static List<OptionDescriptor> fuzzyMatch(Iterable<OptionDescriptors> loader, String optionName) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (OptionDescriptors options : loader) {
            collectFuzzyMatches(options, optionName, matches);
        }
        return matches;
    }

    /**
     * Collects the set of options that fuzzy match a given option name. String similarity for fuzzy
     * matching is based on Dice's coefficient.
     *
     * @param toSearch the set of option descriptors to search
     * @param name the option name to search for
     * @param matches the collection to which fuzzy matches of {@code name} will be added
     * @return whether any fuzzy matches were found
     */
    public static boolean collectFuzzyMatches(Iterable<OptionDescriptor> toSearch, String name, Collection<OptionDescriptor> matches) {
        return collectFuzzyMatches(toSearch, name, matches, OptionDescriptor::getName);
    }

    /**
     * Collects from given entries toSearch the ones that fuzzy match a given String name. String
     * similarity for fuzzy matching is based on Dice's coefficient.
     *
     * @param toSearch the entries search
     * @param name the name to search for
     * @param matches the collection to which fuzzy matches of {@code name} will be added
     * @param extractor functor that maps entry to String
     * @return whether any fuzzy matches were found
     */
    public static <T> boolean collectFuzzyMatches(Iterable<T> toSearch, String name, Collection<T> matches, Function<T, String> extractor) {
        boolean found = false;
        for (T entry : toSearch) {
            float score = stringSimilarity(extractor.apply(entry), name);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                found = true;
                matches.add(entry);
            }
        }
        return found;
    }

    static boolean isEnterpriseOption(OptionDescriptor desc) {
        if (LibGraalSupport.inLibGraalRuntime()) {
            if (libgraalOptions == null) {
                return false;
            }
            return Objects.requireNonNull(libgraalOptions.enterpriseOptions, "missing options").contains(desc.getName());
        }
        Class<?> declaringClass = desc.getDeclaringClass();
        String module = declaringClass.getModule().getName();
        return module != null && module.contains("enterprise");
    }
}
