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
import java.nio.file.Path;
import java.util.Map;

import com.oracle.svm.driver.launcher.json.BundleJSONParserException;

public class BundlePathMapParser extends BundleConfigurationParser {

    private static final String substitutionMapSrcField = "src";
    private static final String substitutionMapDstField = "dst";

    private final Map<Path, Path> pathMap;

    public BundlePathMapParser(Map<Path, Path> pathMap) {
        this.pathMap = pathMap;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        for (var rawEntry : asList(json, "Expected a list of path substitution objects")) {
            var entry = asMap(rawEntry, "Expected a substitution object");
            Object srcPathString = entry.get(substitutionMapSrcField);
            if (srcPathString == null) {
                throw new BundleJSONParserException("Expected " + substitutionMapSrcField + "-field in substitution object");
            }
            Object dstPathString = entry.get(substitutionMapDstField);
            if (dstPathString == null) {
                throw new BundleJSONParserException("Expected " + substitutionMapDstField + "-field in substitution object");
            }
            pathMap.put(Path.of(srcPathString.toString()), Path.of(dstPathString.toString()));
        }
    }
}
