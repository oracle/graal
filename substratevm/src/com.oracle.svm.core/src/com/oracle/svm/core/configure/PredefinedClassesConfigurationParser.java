/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.graalvm.compiler.core.common.SuppressFBWarnings;

import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

public class PredefinedClassesConfigurationParser extends ConfigurationParser {

    private final PredefinedClassesRegistry registry;

    public PredefinedClassesConfigurationParser(PredefinedClassesRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        parseAndRegister(reader, null);
    }

    @Override
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "getParent() returning null for a valid file path is almost impossible and a NullPointerException would be acceptable")
    public void parseAndRegister(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Path basePath = path.getParent().resolve(ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR);
            parseAndRegister(reader, basePath);
        }
    }

    private void parseAndRegister(Reader reader, Path basePath) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();

        for (Object origin : asList(json, "first level of document must be an array of predefined class origin objects")) {
            parseOrigin(basePath, asMap(origin, "second level of document must be a predefined class origin object"));
        }
    }

    private void parseOrigin(Path basePath, Map<String, Object> data) {
        checkAttributes(data, "class origin descriptor object", Arrays.asList("type", "classes"));

        String type = asString(data.get("type"), "type");
        if (!type.equals("agent-extracted")) {
            throw new JSONParserException("Attribute 'type' must have value 'agent-extracted'");
        }

        for (Object clazz : asList(data.get("classes"), "Attribute 'classes' must be an array of predefined class descriptor objects")) {
            parseClass(basePath, asMap(clazz, "second level of document must be a predefined class descriptor object"));
        }
    }

    private void parseClass(Path basePath, Map<String, Object> data) {
        checkAttributes(data, "class descriptor object", Collections.singleton("hash"));

        String hash = asString(data.get("hash"), "hash");
        String nameInfo = asNullableString(data.get("nameInfo"), "nameInfo");
        registry.add(nameInfo, hash, basePath);
    }
}
