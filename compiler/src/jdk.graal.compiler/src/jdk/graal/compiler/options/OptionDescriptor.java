/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Describes the attributes of a static field {@linkplain Option option} and provides access to its
 * {@linkplain OptionKey value}.
 */
public final class OptionDescriptor {

    private final String name;
    private final OptionType optionType;
    private final Class<?> optionValueType;
    private final OptionKey<?> optionKey;
    private final OptionsContainer container;
    private final String fieldName;
    private final OptionStability stability;
    private final boolean deprecated;
    private final String deprecationMessage;

    /**
     * If help is being compressed, this will be an {@link Integer} index into
     * {@link Strings#values} until first access at which point it's replaced with a String value.
     *
     * @see #getHelp()
     */
    private Object help;

    public static OptionDescriptor create(String name,
                    OptionType optionType,
                    Class<?> optionValueType,
                    String help,
                    Object container,
                    String fieldName,
                    OptionKey<?> option) {
        return create(name, optionType, optionValueType, help, container, fieldName, option, OptionStability.EXPERIMENTAL, false, "");
    }

    public static OptionDescriptor create(String name,
                    OptionType optionType,
                    Class<?> optionValueType,
                    String help,
                    Object container,
                    OptionKey<?> option,
                    String fieldName,
                    OptionStability stability,
                    boolean deprecated,
                    String deprecationMessage) {
        return create(name, optionType, optionValueType, help, container, fieldName, option, stability, deprecated, deprecationMessage);
    }

    private static OptionDescriptor create(String name,
                    OptionType optionType,
                    Class<?> optionValueType,
                    String help,
                    Object container,
                    String fieldName,
                    OptionKey<?> option,
                    OptionStability stability,
                    boolean deprecated,
                    String deprecationMessage) {
        OptionsContainer oc = OptionsContainer.asContainer(container);
        Class<?> declaringClass = oc.declaringClass();
        assert option != null : declaringClass + "." + fieldName;
        OptionDescriptor result;
        // Descriptors can be initialized by multiple threads
        synchronized (option) {
            result = option.getDescriptor();
            if (result == null) {
                result = new OptionDescriptor(name, optionType, optionValueType, help, oc, fieldName, option, stability, deprecated, deprecationMessage);
                option.setDescriptor(result);
            }
        }
        assert result.name.equals(name) : result.name + " != " + name;
        assert result.optionValueType == optionValueType : result.optionValueType + " != " + optionValueType;
        assert result.getDeclaringClass() == declaringClass : result.getDeclaringClass() + " != " + declaringClass;
        assert result.fieldName.equals(fieldName) : result.fieldName + " != " + fieldName;
        assert result.optionKey == option : result.optionKey + " != " + option;
        return result;
    }

    private OptionDescriptor(String name,
                    OptionType optionType,
                    Class<?> optionValueType,
                    String help,
                    OptionsContainer container,
                    String fieldName,
                    OptionKey<?> optionKey,
                    OptionStability stability,
                    boolean deprecated,
                    String deprecationMessage) {
        this.name = name;
        this.optionType = optionType;
        this.optionValueType = optionValueType;
        this.help = COMPRESSED_HELP != null && !help.isEmpty() ? COMPRESSED_HELP.add(help) : help;
        this.optionKey = optionKey;
        this.container = container;
        this.fieldName = fieldName;
        this.stability = stability;
        this.deprecated = deprecated || deprecationMessage != null && !deprecationMessage.isEmpty();
        this.deprecationMessage = deprecationMessage;
        assert !optionValueType.isPrimitive() : "must use boxed optionValueType instead of " + optionValueType;
    }

    /**
     * Gets the type of values stored in the option. This will be the boxed type for a primitive
     * option.
     */
    public Class<?> getOptionValueType() {
        return optionValueType;
    }

    private static List<String> splitHelpIntoLines(String s) {
        if (s.indexOf('\n') == -1) {
            return List.of(s);
        }
        return List.of(s.split("\n"));
    }

    /**
     * Gets a descriptive help message for the option, split into lines. The first line should be a
     * short, self-contained synopsis.
     *
     * @see Option#help()
     */
    public List<String> getHelp() {
        if (help instanceof Integer id) {
            if (LibGraalSupport.inLibGraalRuntime()) {
                help = COMPRESSED_HELP.get(id);
            } else {
                help = COMPRESSED_HELP.getHosted(id);
            }
        }
        return splitHelpIntoLines(help.toString());
    }

    /**
     * Gets the name of the option. It's up to the client of this object how to use the name to get
     * a user specified value for the option from the environment.
     */
    public String getName() {
        return container.prefixed(name);
    }

    /**
     * Gets the type of the option.
     */
    public OptionType getOptionType() {
        return optionType;
    }

    /**
     * Gets the boxed option value.
     */
    public OptionKey<?> getOptionKey() {
        return optionKey;
    }

    /**
     * Gets metadata about the class declaring the option.
     */
    public OptionsContainer getContainer() {
        return container;
    }

    /**
     * Gets the class declaring the option.
     */
    public Class<?> getDeclaringClass() {
        return container.declaringClass();
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Gets a description of the location where this option is stored.
     */
    public String getLocation() {
        return getDeclaringClass().getName() + "." + getFieldName();
    }

    /**
     * Returns the stability of this option.
     */
    public OptionStability getStability() {
        return stability;
    }

    /**
     * Returns {@code true} if the option is deprecated.
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Returns the deprecation reason and the recommended replacement.
     */
    public String getDeprecationMessage() {
        return deprecationMessage;
    }

    /**
     * Property controlling whether to compress help strings, true by default.
     */
    private static final String COMPRESS_PROP = "debug.jdk.graal.compressOptionDescriptors";

    /**
     * Object used to compress help strings in a libgraal image.
     */
    static final Strings COMPRESSED_HELP = LibGraalSupport.INSTANCE != null && Boolean.parseBoolean(GraalServices.getSavedProperty(COMPRESS_PROP, "true")) ? new Strings() : null;

    /**
     * Compresses all the help strings going into the libgraal image. This must be called exactly
     * once during libgraal image building.
     */
    public static void sealHelpStrings() {
        if (COMPRESSED_HELP != null) {
            COMPRESSED_HELP.seal();
        }
    }

    /**
     * Generic facility for compressing strings in a libgraal image with gzip.
     */
    static class Strings {
        /**
         * Build-time only data structure to collect the strings to be compressed.
         */
        @LibGraalSupport.HostedOnly //
        final Map<String, Integer> pool = new LinkedHashMap<>();

        /**
         * Runtime uncompressed values, lazily created on first access to a compressed string. This
         * field is volatile so that it can be initialized via double-checked locking.
         */
        volatile String[] values;

        /**
         * Gzip compressed form of {@link #values}. A zero-length array is used instead of null to
         * prevent Native Image constant folding this to null which it would otherwise do as
         * {@link #seal()} is HostedOnly.
         */
        byte[] compressed = {};

        /**
         * Adds a string at build-time to be compressed.
         *
         * @param s the string value to be compressed
         * @return the id used to represent the compressed string that can be passed to
         *         {@link #get(int)} to get the uncompressed value
         */
        @LibGraalSupport.HostedOnly
        synchronized Integer add(String s) {
            GraalError.guarantee(compressed.length == 0, "already sealed");
            return pool.computeIfAbsent(s, k -> pool.size());
        }

        /**
         * Forces creation of the descriptors in {@code d} so that their help strings are
         * {@linkplain #add(String) added}.
         */
        @LibGraalSupport.HostedOnly
        void register(OptionDescriptors d) {
            for (var desc : d) {
                GraalError.guarantee(desc != null, "%s", d.getClass());
            }
        }

        /**
         * Compresses all the strings registered by {@link #add(String)}.
         */
        @LibGraalSupport.HostedOnly
        synchronized void seal() {
            GraalError.guarantee(compressed.length == 0, "already sealed");

            // Write all strings to a byte array first and then feed the whole array to
            // a gzip stream as compression improves when there is more data to analyze.
            byte[] uncompressed;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (DataOutputStream dos = new DataOutputStream(baos)) {
                    dos.writeInt(pool.size());
                    int id = 0;
                    for (var e : pool.entrySet()) {
                        Integer index = e.getValue();
                        GraalError.guarantee(index == id, "%s != %d", index, id);
                        dos.writeUTF(e.getKey());
                        id++;
                    }
                }
                uncompressed = baos.toByteArray();
            } catch (IOException e) {
                throw new GraalError(e);
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(uncompressed.length)) {
                try (GZIPOutputStream dos = new GZIPOutputStream(baos)) {
                    dos.write(uncompressed);
                }
                compressed = baos.toByteArray();
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        String get(int id) {
            if (values == null) {
                synchronized (this) {
                    if (values == null) {
                        GraalError.guarantee(compressed.length != 0, "No compressed strings available");
                        String[] result;
                        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
                            int length = dis.readInt();
                            result = new String[length];
                            for (int i = 0; i < length; i++) {
                                result[i] = dis.readUTF();
                            }
                        } catch (IOException e) {
                            throw new GraalError(e);
                        }
                        values = result;

                        // Release memory for compressed values
                        compressed = null;
                    }
                }
            }
            return values[id];
        }

        /**
         * Version of {@link #get} to be called during libgraal image building.
         */
        @LibGraalSupport.HostedOnly
        synchronized String getHosted(int id) {
            for (var e : pool.entrySet()) {
                if (e.getValue() == id) {
                    return e.getKey();
                }
            }
            throw new NoSuchElementException("unknown string id: " + id);
        }
    }
}
