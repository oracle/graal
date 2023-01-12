/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

/**
 * Constants per top-level class. If multiple nodes are generated per top-level class, this allows
 * to put them into the top-level class. They must not be specific to the individual generated node.
 */
public final class StaticConstants {

    public final Map<String, CodeVariableElement> libraries = new LinkedHashMap<>();
    public final Map<String, CodeVariableElement> contextReferences = new LinkedHashMap<>();
    public final Map<String, CodeVariableElement> languageReferences = new LinkedHashMap<>();
    public final Map<String, CodeVariableElement> enumValues = new LinkedHashMap<>();
    public final Map<String, CodeExecutableElement> decodeConstants = new LinkedHashMap<>();
    public final Map<String, CodeExecutableElement> encodeConstants = new LinkedHashMap<>();
    public final Map<String, TypeMirror> reservedSymbols = new LinkedHashMap<>();

    public void clear() {
        libraries.clear();
        contextReferences.clear();
        languageReferences.clear();
        enumValues.clear();
        reservedSymbols.clear();
        decodeConstants.clear();
        encodeConstants.clear();
    }

    public void addElementsTo(CodeElement<Element> element) {
        element.addAll(libraries.values());
        element.addAll(contextReferences.values());
        element.addAll(languageReferences.values());
        element.addAll(enumValues.values());
        element.addAll(decodeConstants.values());
        element.addAll(encodeConstants.values());
    }

    public String reserveSymbol(TypeMirror type, String name) {
        TypeMirror foundType = reservedSymbols.get(name);
        if (foundType == null) {
            reservedSymbols.put(name, type);
            return name;
        } else if (ElementUtils.typeEquals(foundType, type)) {
            return name;
        } else {
            return reserveSymbol(type, name + "_");
        }
    }

}
