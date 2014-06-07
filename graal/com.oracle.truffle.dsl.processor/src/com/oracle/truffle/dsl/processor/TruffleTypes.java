/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.source.*;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class TruffleTypes {

    public static final String OPTION_DETAILED_REWRITE_REASONS = "DetailedRewriteReasons";

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
    private final DeclaredType slowPath;
    private final DeclaredType sourceSection;
    private final DeclaredType truffleOptions;
    private final DeclaredType compilationFinal;
    private final TypeElement expectError;

    private final List<String> errors = new ArrayList<>();

    public TruffleTypes(ProcessorContext context) {
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
        slowPath = getRequired(context, SlowPath.class);
        sourceSection = getRequired(context, SourceSection.class);
        truffleOptions = getRequired(context, TruffleOptions.class);
        compilationFinal = getRequired(context, CompilationFinal.class);
        expectError = (TypeElement) getRequired(context, ExpectError.class).asElement();
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

    public DeclaredType getSlowPath() {
        return slowPath;
    }

    public DeclaredType getSourceSection() {
        return sourceSection;
    }
}
