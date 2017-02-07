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
package com.oracle.truffle.llvm.option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public final class OptionSummary {

    private static final class OptionInfo {
        private final String category;
        @SuppressWarnings("unused") private final String name;
        private final String optionName;
        private final String defaultValue;
        private final String help;

        private OptionInfo(String category, String name, String optionName, String defaultValue, String help) {
            this.category = category;
            this.name = name;
            this.optionName = optionName;
            this.defaultValue = defaultValue;
            this.help = help;
        }

    }

    private static final List<OptionInfo> options = new ArrayList<>();

    public static void registerOption(String category, String name, String optionName, String defaultValue, String help) {
        options.add(new OptionInfo(category, name, optionName, defaultValue, help));
    }

    public static void printOptions() {
        Map<String, List<OptionInfo>> groupedOptions = options.stream().collect(Collectors.groupingBy(o -> o.category));
        for (String category : groupedOptions.keySet()) {
            List<OptionInfo> optionsInCategory = groupedOptions.get(category);
            Constants.OUT.println(String.format("%s:", category));
            for (OptionInfo info : optionsInCategory) {
                Constants.OUT.println(String.format(Constants.OPTION_FORMAT_STRING, info.optionName, info.defaultValue, info.help));
            }
        }
    }

    public static void checkForInvalidOptionNames() {
        boolean wrongOptionName = false;
        Properties allProperties = System.getProperties();
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(Constants.OPTION_PREFIX)) {
                if (options.stream().noneMatch(option -> option.optionName.equals(key))) {
                    wrongOptionName = true;
                    Constants.OUT.println(key + " is an invalid option!");
                }
            }
        }
        if (wrongOptionName) {
            Constants.OUT.println("\nInvalid options:");
            printOptions();
            System.exit(-1);
        }
    }
}
