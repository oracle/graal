/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
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
import com.oracle.truffle.dsl.processor.java.ElementUtils;

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
    private final TypeElement expectError;

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
        expectError = getOptional(context, EXPECT_ERROR_CLASS_NAME1);
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

    public TypeElement getExpectError() {
        return expectError;
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

    private static TypeElement getOptional(ProcessorContext context, String name) {
        return ElementUtils.getTypeElement(context.getEnvironment(), name);
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
