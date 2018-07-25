/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.json.JSONParserException;

public abstract class ConfigurationParser {

    protected final ImageClassLoader classLoader;

    ConfigurationParser(ImageClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Parses configurations in files specified by {@code configFilesOption} and resources specified
     * by {@code configResourcesOption} and registers the parsed elements using
     * {@link #parseAndRegister(Reader, String, Object, HostedOptionKey)} .
     *
     * @param featureName name of the feature using the configuration (e.g., "JNI")
     */
    public void parseAndRegisterConfigurations(String featureName, HostedOptionKey<String> configFilesOption, HostedOptionKey<String> configResourcesOption) {
        String configFiles = configFilesOption.getValue();
        if (!configFiles.isEmpty()) {
            for (String path : configFiles.split(",")) {
                File file = new File(path).getAbsoluteFile();
                if (!file.exists()) {
                    throw UserError.abort("The " + featureName + " configuration file \"" + file + "\" does not exist.");
                }
                try (Reader reader = new FileReader(file)) {
                    parseAndRegister(reader, featureName, file, configFilesOption);
                } catch (IOException e) {
                    throw UserError.abort("Could not open " + file + ": " + e.getMessage());
                }
            }
        }
        String configResources = configResourcesOption.getValue();
        if (!configResources.isEmpty()) {
            for (String resource : configResources.split(",")) {
                URL url = classLoader.findResourceByName(resource);
                if (url == null) {
                    throw UserError.abort("Could not find " + featureName + " configuration resource \"" + resource + "\".");
                }
                try (Reader reader = new InputStreamReader(url.openStream())) {
                    parseAndRegister(reader, featureName, url, configResourcesOption);
                } catch (IOException e) {
                    throw UserError.abort("Could not open " + url + ": " + e.getMessage());
                }
            }
        }
    }

    protected abstract void parseAndRegister(Reader reader, String featureName, Object location, HostedOptionKey<String> configFilesOption);

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object data, String errorMessage) {
        if (data instanceof List) {
            return (List<Object>) data;
        }
        throw new JSONParserException(errorMessage);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object data, String errorMessage) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        throw new JSONParserException(errorMessage);
    }

    static String asString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONParserException("Invalid string value \"" + value + "\".");
    }

    static String asString(Object value, String propertyName) {
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONParserException("Invalid string value \"" + value + "\" for element '" + propertyName + "'");
    }

    static boolean asBoolean(Object value, String propertyName) {
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        throw new JSONParserException("Invalid boolean value '" + value + "' for element '" + propertyName + "'");
    }
}
