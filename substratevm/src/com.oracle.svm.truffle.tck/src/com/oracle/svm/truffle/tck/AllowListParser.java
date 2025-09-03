/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.tck;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.graal.compiler.util.json.JsonParserException;

final class AllowListParser extends AbstractMethodListParser {

    AllowListParser(ImageClassLoader imageClassLoader, BigBang bb) {
        super(imageClassLoader, bb);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseClassArray(castList(json, "First level of document must be an array of class descriptors"));
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(castMap(clazz, "Second level of document must be class descriptor objects"));
        }
    }

    private void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "class descriptor object", Collections.singleton("name"), Arrays.asList("justification", "allDeclaredConstructors", "allDeclaredMethods", "methods"));
        Object classObject = data.get("name");
        String className = castProperty(classObject, String.class, "name");

        try {
            AnalysisType clazz = resolve(className);
            if (clazz == null) {
                throw new JsonParserException("Class " + className + " not found");
            }

            MapCursor<String, Object> cursor = data.getEntries();
            while (cursor.advance()) {
                String name = cursor.getKey();
                Object value = cursor.getValue();
                switch (name) {
                    case "allDeclaredConstructors":
                        if (castProperty(value, Boolean.class, "allDeclaredConstructors")) {
                            registerDeclaredConstructors(clazz);
                        }
                        break;
                    case "allDeclaredMethods":
                        if (castProperty(value, Boolean.class, "allDeclaredMethods")) {
                            registerDeclaredMethods(clazz);
                        }
                        break;
                    case "methods":
                        parseMethods(castList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                        break;
                }
            }
        } catch (UnsupportedPlatformException unsupportedPlatform) {
            // skip the type not available on active platform
        }
    }
}
