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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.graal.compiler.util.json.JsonParserException;

final class PrivilegedListParser extends AbstractMethodListParser {

    private final Set<String> forModules;

    PrivilegedListParser(ImageClassLoader imageClassLoader, BigBang bb, Collection<? extends Module> forModules) {
        super(imageClassLoader, bb);
        this.forModules = forModules.stream().map(Module::getName).collect(Collectors.toSet());
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseModuleArray(castList(json, "First level of document must be an array of module descriptors"));
    }

    private void parseModuleArray(List<Object> modules) {
        for (Object module : modules) {
            parseModule(castMap(module, "Second level of document must be module descriptor objects"));
        }
    }

    private void parseModule(EconomicMap<String, Object> data) {
        checkAttributes(data, "module descriptor object", Set.of("name", "classes"), Set.of());
        String moduleName = castProperty(data.get("name"), String.class, "name");
        if (forModules.contains(moduleName)) {
            Object classes = data.get("classes");
            parseClassArray(castList(classes, "Attribute 'classes' must be an array of method descriptors"));
        }
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(castMap(clazz, "Third level of document must be class descriptor objects"));
        }
    }

    private void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "class descriptor object", Set.of("name", "methods"), Set.of());
        String className = castProperty(data.get("name"), String.class, "name");
        List<Object> methods = castList(data.get("methods"), "Attribute 'methods' must be an array of method descriptors");
        try {
            AnalysisType clazz = resolve(className);
            if (clazz == null) {
                throw new JsonParserException("Class " + className + " not found");
            }
            parseMethods(methods, clazz);
        } catch (UnsupportedPlatformException unsupportedPlatform) {
            // skip the type not available on active platform
        }
    }
}
