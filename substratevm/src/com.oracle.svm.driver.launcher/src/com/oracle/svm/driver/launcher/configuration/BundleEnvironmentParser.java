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
package com.oracle.svm.driver.launcher.configuration;

import java.net.URI;
import java.util.Map;

import com.oracle.svm.driver.launcher.json.BundleJSONParserException;

public class BundleEnvironmentParser extends BundleConfigurationParser {
    private static final String environmentKeyField = "key";
    private static final String environmentValueField = "val";
    private final Map<String, String> environment;

    public BundleEnvironmentParser(Map<String, String> environment) {
        environment.clear();
        this.environment = environment;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        for (var rawEntry : asList(json, "Expected a list of environment variable objects")) {
            var entry = asMap(rawEntry, "Expected a environment variable object");
            Object envVarKeyString = entry.get(environmentKeyField);
            if (envVarKeyString == null) {
                throw new BundleJSONParserException("Expected " + environmentKeyField + "-field in environment variable object");
            }
            Object envVarValueString = entry.get(environmentValueField);
            if (envVarValueString == null) {
                throw new BundleJSONParserException("Expected " + environmentValueField + "-field in environment variable object");
            }
            environment.put(envVarKeyString.toString(), envVarValueString.toString());
        }
    }
}
