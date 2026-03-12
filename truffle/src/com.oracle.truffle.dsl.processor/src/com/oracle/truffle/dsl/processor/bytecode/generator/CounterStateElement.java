/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Set;

import javax.lang.model.element.ElementKind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class CounterStateElement extends AbstractElement {

    public static final String LOCAL_NAME = "cstate";

    CounterStateElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "CounterState");
        this.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_ValueType));
        this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "counter"));
        this.add(new CodeVariableElement(Set.of(PRIVATE), parent.loopCounter.asType(), "loopCounter"));
        CodeExecutableElement element = this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));
        element.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        this.add(createGetCounter());
        this.add(createIncrementCounter());
        this.add(createResetCounter());
    }

    private CodeExecutableElement createGetCounter() {
        CodeExecutableElement m = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "getCounter");
        m.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        CodeTreeBuilder b = m.createBuilder();
        b.startIf();
        b.startStaticCall(types.CompilerDirectives, "inCompiledCode").end().string(" && ");
        b.startStaticCall(types.CompilerDirectives, "hasNextTier").end();
        b.end().startBlock();
        b.statement("return this.loopCounter.value");
        b.end().startElseBlock();
        b.statement("return this.counter");
        b.end();
        return m;
    }

    private CodeExecutableElement createIncrementCounter() {
        CodeExecutableElement m = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "incrementCounter");
        m.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        CodeTreeBuilder b = m.createBuilder();
        b.declaration(type(int.class), "c");
        b.startIf().startStaticCall(types.CompilerDirectives, "hasNextTier").end().end().startBlock();
        b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
        b.statement("c = counter = ++loopCounter.value");
        b.end().startElseBlock();
        b.statement("c = ++counter");
        b.end();
        b.end().startElseBlock();
        b.statement("c = 0");
        b.end();
        b.statement("return c");
        return m;
    }

    private CodeExecutableElement createResetCounter() {
        CodeExecutableElement m = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "resetCounter");
        m.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        CodeTreeBuilder b = m.createBuilder();
        b.startIf().startStaticCall(types.CompilerDirectives, "hasNextTier").end().end().startBlock();
        b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
        b.statement("counter = ++loopCounter.value");
        b.end().startElseBlock();
        b.statement("counter++");
        b.end();
        b.end();
        return m;
    }

}
