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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class DynamicClassesConfigurationParser extends ConfigurationParser {

    DynamicClassesParserFunction consumer;

    public DynamicClassesConfigurationParser(DynamicClassesParserFunction consumer) {
        this.consumer = consumer;
    }

    private Path configurationFileLocation;

    public void setConfigurationFileLocation(Object location) {
        if (location instanceof URL) {
            configurationFileLocation = Paths.get(((URL) location).getPath());
        } else if (location instanceof Path) {
            configurationFileLocation = (Path) location;
        } else {
            VMError.shouldNotReachHere("Input location of setConfigurationFileLocation must be either Path or URL, but is " + location.getClass().getName());
        }
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        for (Object dynamicDefinedClass : asList(json, "first level of document must be an array of dynamic defined classes")) {
            Map<String, Object> data = asMap(dynamicDefinedClass, "second level of document must be dynamic defined class descriptor objects ");
            String definedClassName = asString(data.get("name"));
            if (definedClassName == null) {
                throw new JSONParserException("Missing attribute 'name' in dynamic class descriptor object");
            }
            String dumpedFileName = asString(data.get("classFile"));
            if (dumpedFileName == null) {
                throw new JSONParserException("Missing attribute 'classFile' in dynamic class descriptor object");
            }
            if (dumpedFileName.length() == 0) {
                throw new IOException("The value of attribute 'classFile' in dynamic class descriptor must not be empty");
            }
            String checksum = asString(data.get("checksum"));
            Path dumpedFile = configurationFileLocation.getParent().resolve(dumpedFileName);
            consumer.accept(definedClassName, dumpedFile, checksum);
        }
    }

    @FunctionalInterface
    public interface DynamicClassesParserFunction {
        void accept(String definedClassName, Path dumpedFileName, String checksum);
    }
}
