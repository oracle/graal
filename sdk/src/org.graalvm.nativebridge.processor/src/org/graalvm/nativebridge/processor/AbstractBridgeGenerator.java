/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;
import java.util.Objects;

abstract class AbstractBridgeGenerator {

    static final String FACTORY_METHOD_NAME = "create";

    private final AbstractBridgeParser parser;
    private final DefinitionData definitionData;
    private final AbstractTypeCache typeCache;

    AbstractBridgeGenerator(AbstractBridgeParser parser, DefinitionData definitionData, AbstractTypeCache typeCache) {
        this.parser = Objects.requireNonNull(parser, "Parser must be non-null");
        this.definitionData = Objects.requireNonNull(definitionData, "DefinitionData must be non-null.");
        this.typeCache = Objects.requireNonNull(typeCache, "TypeCache must be non-null.");
    }

    AbstractBridgeParser getParser() {
        return parser;
    }

    DefinitionData getDefinition() {
        return definitionData;
    }

    AbstractTypeCache getTypeCache() {
        return typeCache;
    }

    void configureMultipleDefinitions(@SuppressWarnings("unused") List<DefinitionData> otherDefinitions) {
    }

    @SuppressWarnings("unused")
    void generateFields(CodeBuilder builder, CharSequence targetClassSimpleName) {
    }

    abstract void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName);

    abstract void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName);

    static CharSequence createCustomObject(CodeBuilder builder, DeclaredType customObject) {
        CodeBuilder object;
        ExecutableElement element = Utilities.findCustomObjectFactory(customObject);
        if (element.getKind() == ElementKind.CONSTRUCTOR) {
            object = new CodeBuilder(builder).newInstance(customObject);
        } else {
            assert element.getKind() == ElementKind.METHOD;
            object = new CodeBuilder(builder).invokeStatic(customObject, "getInstance");
        }
        return object.build();
    }
}
