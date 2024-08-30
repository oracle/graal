/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

import org.graalvm.collections.EconomicMap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.graal.compiler.phases.common.LazyValue;
import jdk.graal.compiler.util.json.JsonParserException;

public class InstrumentConfigurationParser extends ConfigurationParser {

    private final InstrumentRegistry registry;

    public InstrumentConfigurationParser(InstrumentRegistry registry, boolean strictConfiguration) {
        super(strictConfiguration);
        this.registry = registry;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) throws IOException {
        for (Object classDataOrigin : asList(json, "first level of document must be an array of predefined class origin objects")) {
            EconomicMap<String, Object> data = asMap(classDataOrigin, "second level of document must be a predefined class origin object");
            String type = asString(data.get("type"), "type");
            if (!type.equals("agent-extracted")) {
                throw new JsonParserException("Attribute 'type' must have value 'agent-extracted'");
            }

            for (Object premain : asList(data.get("methods"), "Attribute 'methods' must be an array of premain class descriptor objects")) {
                EconomicMap<String, Object> preMainElement = asMap(premain, "");
                String className = (String) preMainElement.get("class");
                int index = Integer.parseInt((String) preMainElement.get("index"));
                String options = (String) preMainElement.get("option");
                if ("null".equals(options)) {
                    options = null;
                }
                registry.add(className, index, options);
            }
        }
    }

    public static LazyValue<Path> directorySupplier(Path root) {
        return new LazyValue<>(() -> {
            try {
                return Files.createDirectories(root.resolve(ConfigurationFile.INSTRUMENT_CLASSES_SUBDIR));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
