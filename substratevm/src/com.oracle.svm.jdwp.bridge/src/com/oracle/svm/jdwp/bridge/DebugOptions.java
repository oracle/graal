/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DebugOptions {

    /**
     * Supported JDWP options.
     */
    public record Options(String jdwpOptions,
                    String additionalOptions,
                    boolean help,
                    boolean server,
                    boolean suspend,
                    boolean quiet,
                    int timeout,
                    String address,
                    String host,
                    int port,
                    String transport,
                    String mode,
                    String libraryPath,
                    String vmOptions,
                    boolean tracing,
                    Map<String, String> unknownOptions) {

        public boolean hasUnknownOptions() {
            return unknownOptions != null && !unknownOptions.isEmpty();
        }
    }

    private static Map<String, String> parseKeyValues(String text) {
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < text.length();) {
            int equalsIndex = text.indexOf('=', index);
            if (equalsIndex < 0) {
                throw new IllegalArgumentException("Invalid key=value at index " + index);
            }
            String key = text.substring(index, equalsIndex);
            int nextIndex = text.indexOf(',', equalsIndex + 1);
            if (nextIndex < 0) {
                nextIndex = text.length();
            }
            String value = text.substring(equalsIndex + 1, nextIndex);
            if (options.containsKey(key)) {
                throw new IllegalArgumentException("Repeated key " + key);
            }
            options.put(key, value);
            index = nextIndex + 1;
        }
        return options;
    }

    private static String checkAllowedValues(String key, String value, String... allowedValues) {
        for (String allowedValue : allowedValues) {
            if (Objects.equals(value, allowedValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid entry " + key + "=" + value + " allowed values: " + String.join(", ", allowedValues));
    }

    private static int checkInt(String key, String value, int lowerBound, int upperBound) {
        int intValue;
        try {
            intValue = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid entry " + key + "=" + value + " not an integer");
        }
        if (!(lowerBound <= intValue && intValue <= upperBound)) {
            throw new IllegalArgumentException("Invalid entry " + key + "=" + value + " must be within [" + lowerBound + ", " + upperBound + "]");
        }
        return intValue;
    }

    public static Options parse(String jdwpOptions, boolean throwIfUnknown) {
        return parse(jdwpOptions, null, throwIfUnknown, false);
    }

    /**
     * Parses JDWP options. This method only validates common known options e.g. doesn't check if
     * required options are present.
     * 
     * @param throwIfUnknown if true, throws {@link IllegalArgumentException} if there's an
     *            unknown/unsupported options
     * @throws IllegalArgumentException if options cannot be parsed (malformed)
     */
    public static Options parse(String jdwpOptions, String additionalOptions, boolean throwIfUnknown, boolean tracing) {

        boolean help = false;
        boolean server = false; // n
        boolean suspend = true; // y
        boolean quiet = false; // n
        int timeout = 0; // no timeout
        String address = null;
        String host = null;
        int port = 0;
        String transport = null;
        String mode = "native";
        String libraryPath = null;
        String vmOptions = null;

        Map<String, String> unknownOptions = new HashMap<>();

        help = "help".equals(jdwpOptions);

        String combinedOptions = help ? "" : jdwpOptions;
        if (additionalOptions != null && !additionalOptions.isEmpty()) {
            if (!combinedOptions.isEmpty()) {
                combinedOptions += ",";
            }
            combinedOptions += additionalOptions;
        }
        Map<String, String> keyValues = parseKeyValues(combinedOptions);

        for (Map.Entry<String, String> entry : keyValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
                case "server" -> server = checkAllowedValues(key, value, "y", "n").equals("y");
                case "suspend" -> suspend = checkAllowedValues(key, value, "y", "n").equals("y");
                case "quiet" -> quiet = checkAllowedValues(key, value, "y", "n").equals("y");
                case "timeout" -> timeout = checkInt(key, value, 0, Integer.MAX_VALUE);
                // GR-55160: Revisit adding support for dt_shmem (on Windows).
                case "transport" -> transport = checkAllowedValues(key, value, "dt_socket");
                case "address" -> {
                    String[] parts = value.split(":");
                    String inputHost = null;
                    String inputPort;
                    if (parts.length == 1) {
                        inputPort = parts[0];
                    } else if (parts.length == 2) {
                        inputHost = parts[0];
                        inputPort = parts[1];
                    } else {
                        throw new IllegalArgumentException("Invalid entry " + key + "=" + value + " not a '[host:]port' pair");
                    }
                    address = value;
                    host = inputHost;
                    port = checkInt(key, inputPort, 0, 65535);
                }
                case "mode" -> {
                    String[] parts = value.split(":", 2);
                    mode = checkAllowedValues(key, parts[0], "native", "jvm");
                    if (parts.length > 1) {
                        // native:/path/to/libsvmdebugger.so
                        // jvm:/path/to/libjvm.so
                        libraryPath = parts[1];
                    } else {
                        libraryPath = null;
                    }
                }
                case "vm.options" -> {
                    // VM options passed to the JVM or native isolate where the JDWP server
                    // runs.
                    vmOptions = value;
                }
                default -> {
                    if (throwIfUnknown) {
                        throw new IllegalArgumentException("Unknown/unsupported option: " + key);
                    } else {
                        unknownOptions.put(key, value);
                    }
                }
            }
        }

        return new Options(jdwpOptions, additionalOptions, help, server, suspend, quiet, timeout, address, host, port, transport, mode, libraryPath, vmOptions, tracing, unknownOptions);
    }
}
