/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

import static jdk.vm.ci.common.InitTimer.timer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.vm.ci.common.InitTimer;

/**
 * A context for obtaining values for {@link OptionKey}s.
 *
 * The {@link #GLOBAL} value contains the values set via system properties when this class is
 * initialized.
 */
public class OptionValues {
    /**
     * The name of the system property specifying a file containing extra Graal option settings.
     */
    private static final String GRAAL_OPTIONS_FILE_PROPERTY_NAME = "graal.options.file";

    /**
     * The name of the system property specifying the Graal version.
     */
    private static final String GRAAL_VERSION_PROPERTY_NAME = "graal.version";

    /**
     * The prefix for system properties that correspond to {@link Option} annotated fields. A field
     * named {@code MyOption} will have its value set from a system property with the name
     * {@code GRAAL_OPTION_PROPERTY_PREFIX + "MyOption"}.
     */
    public static final String GRAAL_OPTION_PROPERTY_PREFIX = "graal.";

    /**
     * Gets the system property assignment that would set the current value for a given option.
     */
    public static String asSystemPropertySetting(OptionValues options, OptionKey<?> value) {
        return GRAAL_OPTION_PROPERTY_PREFIX + value.getName() + "=" + value.getValue(options);
    }

    private static Properties getSavedProperties() {
        try {
            String value = System.getProperty("java.specification.version");
            if (value.startsWith("1.")) {
                value = value.substring(2);
            }
            int javaVersion = Integer.parseInt(value);
            String vmClassName = javaVersion <= 8 ? "sun.misc.VM" : "jdk.internal.misc.VM";
            Class<?> vmClass = Class.forName(vmClassName);
            Field savedPropsField = vmClass.getDeclaredField("savedProps");
            savedPropsField.setAccessible(true);
            return (Properties) savedPropsField.get(null);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    @SuppressWarnings("try")
    private static OptionValues initialize() {
        Map<OptionKey<?>, Object> values = new HashMap<>();
        try (InitTimer t = timer("InitializeOptions")) {

            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            Properties savedProps = getSavedProperties();
            String optionsFile = savedProps.getProperty(GRAAL_OPTIONS_FILE_PROPERTY_NAME);

            if (optionsFile != null) {
                File graalOptions = new File(optionsFile);
                if (graalOptions.exists()) {
                    try (FileReader fr = new FileReader(graalOptions)) {
                        Properties props = new Properties();
                        props.load(fr);
                        Map<String, String> optionSettings = new HashMap<>();
                        for (Map.Entry<Object, Object> e : props.entrySet()) {
                            optionSettings.put((String) e.getKey(), (String) e.getValue());
                        }
                        try {
                            OptionsParser.parseOptions(optionSettings, values, loader);
                        } catch (Throwable e) {
                            throw new InternalError("Error parsing an option from " + graalOptions, e);
                        }
                    } catch (IOException e) {
                        throw new InternalError("Error reading " + graalOptions, e);
                    }
                }
            }

            Map<String, String> optionSettings = new HashMap<>();
            for (Map.Entry<Object, Object> e : savedProps.entrySet()) {
                String name = (String) e.getKey();
                if (name.startsWith(GRAAL_OPTION_PROPERTY_PREFIX)) {
                    if (name.equals("graal.PrintFlags") || name.equals("graal.ShowFlags")) {
                        System.err.println("The " + name + " option has been removed and will be ignored. Use -XX:+JVMCIPrintProperties instead.");
                    } else if (name.equals(GRAAL_OPTIONS_FILE_PROPERTY_NAME) || name.equals(GRAAL_VERSION_PROPERTY_NAME)) {
                        // Ignore well known properties that do not denote an option
                    } else {
                        String value = (String) e.getValue();
                        optionSettings.put(name.substring(GRAAL_OPTION_PROPERTY_PREFIX.length()), value);
                    }
                }
            }

            OptionsParser.parseOptions(optionSettings, values, loader);
            return new OptionValues(values);
        }
    }

    /**
     * Global options. The values for these options are initialized by parsing the file denoted by
     * the {@code VM.getSavedProperty(String) saved} system property named
     * {@value #GRAAL_OPTIONS_FILE_PROPERTY_NAME} if the file exists followed by parsing the options
     * encoded in saved system properties whose names start with
     * {@value #GRAAL_OPTION_PROPERTY_PREFIX}. Key/value pairs are parsed from the aforementioned
     * file with {@link Properties#load(java.io.Reader)}.
     */
    public static final OptionValues GLOBAL = initialize();

    private final Map<OptionKey<?>, Object> values = new HashMap<>();

    /**
     * Used to assert the invariant of stability for {@link StableOptionKey}s.
     */
    private final Map<StableOptionKey<?>, Object> stabilized = new HashMap<>();

    OptionValues set(OptionKey<?> key, Object value) {
        decodeNull(values.put(key, encodeNull(value)));
        return this;
    }

    /**
     * Registers {@code key} as stable in this map. It should not be updated after this call.
     *
     * Note: Should only be used in an assertion.
     */
    synchronized boolean stabilize(StableOptionKey<?> key, Object value) {
        stabilized.put(key, encodeNull(value));
        return true;
    }

    /**
     * Determines if the value of {@code key} is {@linkplain #stabilize(StableOptionKey, Object)
     * stable} in this map.
     *
     * Note: Should only be used in an assertion.
     */
    synchronized boolean isStabilized(StableOptionKey<?> key) {
        return key.getDescriptor() == null;
    }

    boolean containsKey(OptionKey<?> key) {
        return key.getDescriptor() != null && values.containsKey(key);
    }

    public OptionValues(OptionValues initialValues, Map<OptionKey<?>, Object> extraPairs) {
        if (initialValues != null) {
            values.putAll(initialValues.values);
        }
        for (Map.Entry<OptionKey<?>, Object> e : extraPairs.entrySet()) {
            values.put(e.getKey(), encodeNull(e.getValue()));
        }
    }

    public OptionValues(OptionValues initialValues, OptionKey<?> key1, Object value1, Object... extraPairs) {
        this(initialValues, asMap(key1, value1, extraPairs));
    }

    /**
     * Gets an immutable view of the key/value pairs in this object.
     */
    public Map<OptionKey<?>, Object> getMap() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * @param key1 first key in map
     * @param value1 first value in map
     * @param extraPairs key/value pairs of the form {@code [key1, value1, key2, value2, ...]}
     * @return a map containing the key/value pairs as entries
     */
    public static Map<OptionKey<?>, Object> asMap(OptionKey<?> key1, Object value1, Object... extraPairs) {
        Map<OptionKey<?>, Object> map = new HashMap<>();
        map.put(key1, value1);
        for (int i = 0; i < extraPairs.length; i += 2) {
            OptionKey<?> key = (OptionKey<?>) extraPairs[i];
            Object value = extraPairs[i + 1];
            map.put(key, value);
        }
        return map;
    }

    /**
     * Constructor only for initializing {@link #GLOBAL}.
     */
    OptionValues(Map<OptionKey<?>, Object> values) {
        for (Map.Entry<OptionKey<?>, Object> e : values.entrySet()) {
            this.values.put(e.getKey(), encodeNull(e.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    <T> T get(OptionKey<T> key) {
        if (key.getDescriptor() != null) {
            Object value = values.get(key);
            if (value == null) {
                return key.getDefaultValue();
            }
            return (T) decodeNull(value);
        } else {
            // If a key has no descriptor, it cannot be in the map
            // since OptionKey.hashCode() will ensure the key has a
            // descriptor as a result of using OptionKey.getName().
            return key.getDefaultValue();
        }

    }

    private static final Object NULL = new Object();

    private static Object encodeNull(Object value) {
        return value == null ? NULL : value;
    }

    private static Object decodeNull(Object value) {
        return value == NULL ? null : value;
    }

    @Override
    public String toString() {
        Comparator<OptionKey<?>> comparator = new Comparator<OptionKey<?>>() {
            @Override
            public int compare(OptionKey<?> o1, OptionKey<?> o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
        SortedMap<OptionKey<?>, Object> sorted = new TreeMap<>(comparator);
        sorted.putAll(values);
        return sorted.toString();
    }

    private static final int PROPERTY_LINE_WIDTH = 80;
    private static final int PROPERTY_HELP_INDENT = 10;

    /**
     * Wraps some given text to one or more lines of a given maximum width.
     *
     * @param text text to wrap
     * @param width maximum width of an output line, exception for words in {@code text} longer than
     *            this value
     * @return {@code text} broken into lines
     */
    private static List<String> wrap(String text, int width) {
        List<String> lines = Collections.singletonList(text);
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String chunk : chunks) {
                if (line.length() + chunk.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() != 0) {
                    line.append(' ');
                }
                String[] embeddedLines = chunk.split("%n", -2);
                if (embeddedLines.length == 1) {
                    line.append(chunk);
                } else {
                    for (int i = 0; i < embeddedLines.length; i++) {
                        line.append(embeddedLines[i]);
                        if (i < embeddedLines.length - 1) {
                            lines.add(line.toString());
                            line.setLength(0);
                        }
                    }
                }
            }
            if (line.length() != 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    /**
     * Prints a help message to {@code out} describing all options available via {@code loader}. The
     * key/value for each option is separated by {@code :=} if the option has an entry in this
     * object otherwise {@code =} is used as the separator.
     *
     * @param loader
     * @param out
     * @param namePrefix
     */
    public void printHelp(ServiceLoader<OptionDescriptors> loader, PrintStream out, String namePrefix) {
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
        for (OptionDescriptors opts : loader) {
            for (OptionDescriptor desc : opts) {
                String name = desc.getName();
                OptionDescriptor existing = sortedOptions.put(name, desc);
                assert existing == null : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
            }
        }
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionKey().getValue(this);
            if (value instanceof String) {
                value = '"' + String.valueOf(value) + '"';
            }
            String help = desc.getHelp();
            if (desc.getOptionKey() instanceof EnumOptionKey) {
                EnumOptionKey<?> eoption = (EnumOptionKey<?>) desc.getOptionKey();
                String evalues = eoption.getAllValues().toString();
                if (help.length() > 0 && !help.endsWith(".")) {
                    help += ".";
                }
                help += " Valid values are: " + evalues.substring(1, evalues.length() - 1);
            }
            String name = namePrefix + e.getKey();
            String assign = containsKey(desc.optionKey) ? ":=" : "=";
            String typeName = desc.getOptionKey() instanceof EnumOptionKey ? "String" : desc.getType().getSimpleName();
            String linePrefix = String.format("%s %s %s ", name, assign, value);
            int typeStartPos = PROPERTY_LINE_WIDTH - typeName.length();
            int linePad = typeStartPos - linePrefix.length();
            if (linePad > 0) {
                out.printf("%s%-" + linePad + "s[%s]%n", linePrefix, "", typeName);
            } else {
                out.printf("%s[%s]%n", linePrefix, typeName);
            }

            if (help.length() != 0) {
                List<String> helpLines = wrap(help, PROPERTY_LINE_WIDTH - PROPERTY_HELP_INDENT);
                for (int i = 0; i < helpLines.size(); i++) {
                    out.printf("%" + PROPERTY_HELP_INDENT + "s%s%n", "", helpLines.get(i));
                }
            }
        }
    }
}
