/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class TruffleTypes {

    public static final String EXPECT_ERROR_CLASS_NAME1 = "com.oracle.truffle.api.dsl.test.ExpectError";
    public static final String EXPECT_ERROR_CLASS_NAME2 = "com.oracle.truffle.api.test.ExpectError";

    private final DeclaredType node;
    private final ArrayType nodeArray;
    private final TypeMirror unexpectedValueException;
    private final TypeMirror frame;
    private final TypeMirror assumption;
    private final TypeMirror invalidAssumption;
    private final DeclaredType childAnnotation;
    private final DeclaredType childrenAnnotation;
    private final DeclaredType nodeInfoAnnotation;
    private final DeclaredType nodeCost;
    private final TypeMirror compilerDirectives;
    private final TypeMirror compilerAsserts;
    private final DeclaredType truffleBoundary;
    private final DeclaredType sourceSection;
    private final DeclaredType truffleOptions;
    private final DeclaredType compilationFinal;
    private final DeclaredType nodeUtil;
    private final DeclaredType nodeFactory;
    private final DeclaredType generateNodeFactory;

    private final List<String> errors = new ArrayList<>();

    TruffleTypes(ProcessorContext context) {
        node = getRequired(context, Node.class);
        nodeArray = context.getEnvironment().getTypeUtils().getArrayType(node);
        unexpectedValueException = getRequired(context, UnexpectedResultException.class);
        frame = getRequired(context, VirtualFrame.class);
        childAnnotation = getRequired(context, Child.class);
        childrenAnnotation = getRequired(context, Children.class);
        compilerDirectives = getRequired(context, CompilerDirectives.class);
        compilerAsserts = getRequired(context, CompilerAsserts.class);
        assumption = getRequired(context, Assumption.class);
        invalidAssumption = getRequired(context, InvalidAssumptionException.class);
        nodeInfoAnnotation = getRequired(context, NodeInfo.class);
        nodeCost = getRequired(context, NodeCost.class);
        truffleBoundary = getRequired(context, TruffleBoundary.class);
        sourceSection = getRequired(context, SourceSection.class);
        truffleOptions = getRequired(context, TruffleOptions.class);
        compilationFinal = getRequired(context, CompilationFinal.class);
        nodeUtil = getRequired(context, NodeUtil.class);
        nodeFactory = getRequired(context, NodeFactory.class);
        generateNodeFactory = getRequired(context, GenerateNodeFactory.class);
    }

    public DeclaredType getGenerateNodeFactory() {
        return generateNodeFactory;
    }

    public DeclaredType getNodeFactory() {
        return nodeFactory;
    }

    public DeclaredType getCompilationFinal() {
        return compilationFinal;
    }

    public DeclaredType getNodeInfoAnnotation() {
        return nodeInfoAnnotation;
    }

    public boolean verify(ProcessorContext context, Element element, AnnotationMirror mirror) {
        if (errors.isEmpty()) {
            return true;
        }

        for (String error : errors) {
            context.getLog().message(Kind.ERROR, element, mirror, null, error);
        }

        return false;
    }

    public DeclaredType getNodeCost() {
        return nodeCost;
    }

    private DeclaredType getRequired(ProcessorContext context, Class<?> clazz) {
        TypeMirror type = context.getType(clazz);
        if (type == null) {
            errors.add(String.format("Could not find required type: %s", clazz.getSimpleName()));
        }
        return (DeclaredType) type;
    }

    public TypeMirror getInvalidAssumption() {
        return invalidAssumption;
    }

    public TypeMirror getAssumption() {
        return assumption;
    }

    public TypeMirror getCompilerDirectives() {
        return compilerDirectives;
    }

    public DeclaredType getNode() {
        return node;
    }

    public ArrayType getNodeArray() {
        return nodeArray;
    }

    public TypeMirror getFrame() {
        return frame;
    }

    public TypeMirror getUnexpectedValueException() {
        return unexpectedValueException;
    }

    public DeclaredType getChildAnnotation() {
        return childAnnotation;
    }

    public DeclaredType getChildrenAnnotation() {
        return childrenAnnotation;
    }

    public TypeMirror getCompilerAsserts() {
        return compilerAsserts;
    }

    public DeclaredType getTruffleOptions() {
        return truffleOptions;
    }

    public DeclaredType getTruffleBoundary() {
        return truffleBoundary;
    }

    public DeclaredType getSourceSection() {
        return sourceSection;
    }

    public DeclaredType getNodeUtil() {
        return nodeUtil;
    }
}
