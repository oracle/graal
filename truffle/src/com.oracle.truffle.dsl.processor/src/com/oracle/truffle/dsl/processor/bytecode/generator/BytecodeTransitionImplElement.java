/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class BytecodeTransitionImplElement extends AbstractElement {

    BytecodeTransitionImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeTransitionImpl");

        setSuperClass(types.BytecodeTransition);

        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), parent.abstractBytecodeNode.asType(), "oldBytecodeNode"));
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "oldBytecodeIndex"));
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), parent.abstractBytecodeNode.asType(), "newBytecodeNode"));
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newBytecodeIndex"));
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "transferToInterpreter"));

        this.add(createConstructor());
        this.add(createToClassSet());
        this.add(createMapAddedTags());
        this.add(createMapAddedInstrumentations());
        this.add(createIsBytecodeUpdate());
        this.add(createIsTransferToInterpreter());
        this.add(createGetAddedTags());
        this.add(createGetAddedInstrumentations());
        this.add(createGetOldLocation());
        this.add(createGetNewLocation());
    }

    private TypeMirror classSetType() {
        return type(Set.class);
    }

    private CodeExecutableElement createConstructor() {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "oldBytecodeNode"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "oldBytecodeIndex"));
        ctor.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "newBytecodeNode"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "newBytecodeIndex"));
        ctor.addParameter(new CodeVariableElement(type(boolean.class), "transferToInterpreter"));

        CodeTreeBuilder b = ctor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.statement("this.oldBytecodeNode = oldBytecodeNode");
        b.statement("this.oldBytecodeIndex = oldBytecodeIndex");
        b.statement("this.newBytecodeNode = newBytecodeNode");
        b.statement("this.newBytecodeIndex = newBytecodeIndex");
        b.statement("this.transferToInterpreter = transferToInterpreter");
        return ctor;
    }

    private CodeExecutableElement createToClassSet() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classSetType(), "toClassSet");
        ex.addParameter(new CodeVariableElement(arrayOf(type(Class.class)), "classes"));
        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked", "rawtypes");

        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("classes.length == 0").end().startBlock();
        b.startReturn().startStaticCall(type(Set.class), "of").end().end();
        b.end();
        b.statement("return Set.of(classes)");
        return ex;
    }

    private CodeExecutableElement createMapAddedTags() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classSetType(), "mapAddedTags");
        ex.addParameter(new CodeVariableElement(type(int.class), "tagMask"));
        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked");

        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("tagMask == 0").end().startBlock();
        b.startReturn().startStaticCall(type(Set.class), "of").end().end();
        b.end();

        if (model().getProvidedTags().isEmpty()) {
            b.startReturn().startStaticCall(type(Set.class), "of").end().end();
        } else {
            b.statement("return toClassSet(mapTagMaskToTagsArray(tagMask))");
        }
        return ex;
    }

    private CodeExecutableElement createMapAddedInstrumentations() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classSetType(), "mapAddedInstrumentations");
        ex.addParameter(new CodeVariableElement(type(int.class), "instrumentationMask"));

        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("instrumentationMask == 0").end().startBlock();
        b.startReturn().startStaticCall(type(Set.class), "of").end().end();
        b.end();

        int maxInstrumentations = parent.model.bytecodeConfigEncoding.numInstrumentationBits();
        if (maxInstrumentations == 0) {
            b.startReturn().startStaticCall(type(Set.class), "of").end().end();
            return ex;
        }

        b.statement("Class<?>[] classes = new Class<?>[" + maxInstrumentations + "]");
        b.statement("int classesIndex = 0");

        if (parent.model.enableInstructionTracing) {
            b.startIf().string(parent.configEncoder.checkInstructionTracingEnabled("instrumentationMask")).end().startBlock();
            b.startStatement().string("classes[classesIndex++] = ").typeLiteral(types.InstructionTracer).end();
            b.end();
        }

        for (var instrumentation : parent.model.getInstrumentations()) {
            b.startIf().string(parent.configEncoder.checkInstrumentationEnabled("instrumentationMask", instrumentation.operation)).end().startBlock();
            b.startStatement().string("classes[classesIndex++] = ").typeLiteral(instrumentation.operation.instruction().nodeType.asType()).end();
            b.end();
        }

        b.statement("Class<?>[] result = classesIndex == classes.length ? classes : Arrays.copyOf(classes, classesIndex)");
        b.statement("return toClassSet(result)");
        return ex;
    }

    private CodeExecutableElement createIsBytecodeUpdate() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "isBytecodeUpdate");
        ex.createBuilder().statement("return oldBytecodeNode.bytecodes != newBytecodeNode.bytecodes");
        return ex;
    }

    private CodeExecutableElement createIsTransferToInterpreter() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "isTransferToInterpreter");
        ex.createBuilder().startReturn().string("transferToInterpreter").end();
        return ex;
    }

    private CodeExecutableElement createGetAddedTags() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "getAddedTags");
        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked");

        CodeTreeBuilder b = ex.createBuilder();
        BytecodeConfigEncoderImplElement configEncoder = parent.configEncoder;
        b.declaration(type(int.class), "oldTagsMask", configEncoder.decodeTags("oldBytecodeNode.configEncoding"));
        b.declaration(type(int.class), "newTagsMask", configEncoder.decodeTags("newBytecodeNode.configEncoding"));
        b.statement("return (Set<Class<?>>) mapAddedTags(newTagsMask & ~oldTagsMask)");
        return ex;
    }

    private CodeExecutableElement createGetAddedInstrumentations() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "getAddedInstrumentations");
        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked");

        CodeTreeBuilder b = ex.createBuilder();
        BytecodeConfigEncoderImplElement configEncoder = parent.configEncoder;
        b.declaration(type(int.class), "oldInstrumentationsMask", configEncoder.decodeInstrumentations("oldBytecodeNode.configEncoding"));
        b.declaration(type(int.class), "newInstrumentationsMask", configEncoder.decodeInstrumentations("newBytecodeNode.configEncoding"));
        b.statement("return (Set<Class<?>>) mapAddedInstrumentations(newInstrumentationsMask & ~oldInstrumentationsMask)");
        return ex;
    }

    private CodeExecutableElement createGetOldLocation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "getOldLocation");
        ex.createBuilder().startReturn().startStaticCall(parent.abstractBytecodeNode.asType(), "findLocation").string("oldBytecodeNode").string("oldBytecodeIndex").end().end();
        return ex;
    }

    private CodeExecutableElement createGetNewLocation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeTransition, "getNewLocation");
        ex.createBuilder().startReturn().startStaticCall(parent.abstractBytecodeNode.asType(), "findLocation").string("newBytecodeNode").string("newBytecodeIndex").end().end();
        return ex;
    }
}
