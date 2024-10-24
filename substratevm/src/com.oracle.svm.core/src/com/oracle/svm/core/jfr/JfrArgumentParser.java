/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

public class JfrArgumentParser {
    public static Map<JfrArgument, String> parseJfrOptions(RuntimeOptionKey<String> runtimeOptionKey, JfrArgument[] possibleArguments) throws JfrArgumentParsingFailed {
        String userInput = runtimeOptionKey.getValue();
        if (!userInput.isEmpty()) {
            String[] options = StringUtil.split(userInput, ",");
            return parseJfrOptions(options, possibleArguments);
        }
        return new HashMap<>();
    }

    private static Map<JfrArgument, String> parseJfrOptions(String[] options, JfrArgument[] possibleArguments) throws JfrArgumentParsingFailed {
        Map<JfrArgument, String> optionsMap = new HashMap<>();

        for (String option : options) {
            String[] keyVal = StringUtil.split(option, "=");
            if (keyVal.length != 2) {
                throw new JfrArgumentParsingFailed("Invalid argument '" + keyVal[0] + "' in JFR options");
            }

            JfrArgument arg = findArgument(possibleArguments, keyVal[0]);
            if (arg == null) {
                throw new JfrArgumentParsingFailed("Unknown argument '" + keyVal[0] + "' in JFR options");
            }
            optionsMap.put(arg, keyVal[1]);
        }

        return optionsMap;
    }

    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "null allowed as return value")
    public static Boolean parseBoolean(Map<JfrArgument, String> args, JfrArgument key) throws JfrArgumentParsingFailed {
        String value = args.get(key);
        if (value == null) {
            return null;
        } else if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        } else {
            throw new JfrArgumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. Expected a boolean value.");
        }
    }

    public static Integer parseInteger(Map<JfrArgument, String> args, JfrArgument key) throws JfrArgumentParsingFailed {
        String value = args.get(key);
        if (value != null) {
            try {
                return Integer.valueOf(value);
            } catch (Throwable e) {
                throw new JfrArgumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. " + e.getMessage());
            }
        }
        return null;
    }

    public static Long parseMaxSize(Map<JfrArgument, String> args, JfrArgument key) throws JfrArgumentParsingFailed {
        String value = args.get(key);
        if (value == null) {
            return null;
        }

        try {
            int idx = indexOfFirstNonDigitCharacter(value);
            long number;
            try {
                number = Long.parseLong(value.substring(0, idx));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a positive number.");
            }

            // Missing unit, number is plain bytes
            if (idx == value.length()) {
                return number;
            }

            char unit = value.substring(idx).charAt(0);
            return switch (unit) {
                case 'k', 'K' -> number * 1024;
                case 'm', 'M' -> number * 1024 * 1024;
                case 'g', 'G' -> number * 1024 * 1024 * 1024;
                default -> number; // Unknown unit, number is treated as plain bytes
            };
        } catch (IllegalArgumentException e) {
            throw new JfrArgumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. " + e.getMessage());
        }
    }

    private static int indexOfFirstNonDigitCharacter(String durationText) {
        int idx = 0;
        while (idx < durationText.length() && Character.isDigit(durationText.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static JfrArgument findArgument(JfrArgument[] possibleArguments, String value) {
        for (JfrArgument arg : possibleArguments) {
            if (arg.getCmdLineKey().equals(value)) {
                return arg;
            }
        }
        return null;
    }

    public interface JfrArgument {
        String getCmdLineKey();
    }

    public enum FlightRecorderOptionsArgument implements JfrArgument {
        GlobalBufferSize("globalbuffersize"),
        MaxChunkSize("maxchunksize"),
        MemorySize("memorysize"),
        OldObjectQueueSize("old-object-queue-size"),
        RepositoryPath("repository"),
        StackDepth("stackdepth"),
        ThreadBufferSize("threadbuffersize"),
        PreserveRepository("preserve-repository");

        private final String cmdLineKey;

        FlightRecorderOptionsArgument(String key) {
            this.cmdLineKey = key;
        }

        @Override
        public String getCmdLineKey() {
            return cmdLineKey;
        }
    }

    public static class JfrArgumentParsingFailed extends RuntimeException {
        @Serial private static final long serialVersionUID = -1050173145647068124L;

        JfrArgumentParsingFailed(String message, Throwable cause) {
            super(message, cause);
        }

        JfrArgumentParsingFailed(String message) {
            super(message);
        }
    }
}
