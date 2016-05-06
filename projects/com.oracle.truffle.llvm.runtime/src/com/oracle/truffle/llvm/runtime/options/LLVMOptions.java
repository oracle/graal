/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import com.oracle.truffle.llvm.runtime.LLVMLogger;

public class LLVMOptions {

    private static final String OPTION_FORMAT_STRING = "%40s (default = %5s) %s";

    private static String toString(LLVMOption option) {
        return String.format(OPTION_FORMAT_STRING, option.getKey(), option.getDefaultValue(), option.getDescription());
    }

    public static void main(String[] args) {
        List<String> categoryLabels = registeredProperties.stream().map(option -> option.getCategory()).distinct().collect(Collectors.toList());
        for (String category : categoryLabels) {
            List<LLVMOption> props = registeredProperties.stream().filter(option -> option.getCategory().equals(category)).collect(Collectors.toList());
            if (!props.isEmpty()) {
                LLVMLogger.unconditionalInfo(category + ":");
                for (LLVMOption prop : props) {
                    LLVMLogger.unconditionalInfo(toString(prop));
                }
                LLVMLogger.unconditionalInfo("");
            }
        }
    }

    private static final String PATH_DELIMITER = ":";
    private static final String OPTION_PREFIX = "sulong.";
    private static final String OBSOLETE_OPTION_PREFIX = "llvm.";

    public static String getPathDelimiter() {
        return PATH_DELIMITER;
    }

    public static String getOptionPrefix() {
        return OPTION_PREFIX;
    }

    @FunctionalInterface
    public interface OptionParser {
        Object parse(LLVMOption property);
    }

    public static boolean parseBoolean(LLVMOption prop) {
        return Boolean.parseBoolean(System.getProperty(prop.getKey(), prop.getDefaultValue()));
    }

    public static String parseString(LLVMOption prop) {
        return System.getProperty(prop.getKey(), prop.getDefaultValue());
    }

    public static int parseInteger(LLVMOption prop) {
        return Integer.parseInt(System.getProperty(prop.getKey(), prop.getDefaultValue()));
    }

    public static String[] parseDynamicLibraryPath(LLVMOption prop) {
        String property = System.getProperty(prop.getKey(), prop.getDefaultValue());
        if (property == null) {
            return new String[0];
        } else {
            return property.split(PATH_DELIMITER);
        }
    }

    private static Map<LLVMOption, Object> parsedProperties = new HashMap<>();
    private static final List<LLVMOption> registeredProperties = new ArrayList<>();

    static {
        registerOptions();
        parseOptions();
        checkForInvalidOptionNames();
        checkForObsoleteOptionPrefix();
    }

    private static void registerOptions() {
        registeredProperties.addAll(Arrays.asList(LLVMBaseOption.values()));
        ServiceLoader<LLVMOptionServiceProvider> loader = ServiceLoader.load(LLVMOptionServiceProvider.class);
        for (LLVMOptionServiceProvider definitions : loader) {
            registeredProperties.addAll(definitions.getOptions());
        }
    }

    private static void checkForInvalidOptionNames() {
        boolean wrongOptionName = false;
        Properties allProperties = System.getProperties();
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(OPTION_PREFIX)) {
                if (registeredProperties.stream().noneMatch(option -> option.getKey().equals(key))) {
                    wrongOptionName = true;
                    LLVMLogger.error(key + " is an invalid option!");
                }
            }
        }
        if (wrongOptionName) {
            LLVMLogger.error("\nvalid options:");
            printOptions();
            System.exit(-1);
        }
    }

    private static void printOptions() {
        LLVMOptions.main(new String[0]);
    }

    private static void checkForObsoleteOptionPrefix() {
        Properties allProperties = System.getProperties();
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(OBSOLETE_OPTION_PREFIX)) {
                LLVMLogger.error(
                                "The prefix '" + OBSOLETE_OPTION_PREFIX + "' in option '" + key + "' is an obsolete option prefix and has been replaced by the prefix '" + OPTION_PREFIX + "':");
                printOptions();
                System.exit(-1);
            }
        }
    }

    private static void parseOptions() {
        for (LLVMOption prop : registeredProperties) {
            parsedProperties.put(prop, prop.parse());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getParsedProperty(LLVMBaseOption property) {
        return (T) parsedProperties.get(property);
    }

}
