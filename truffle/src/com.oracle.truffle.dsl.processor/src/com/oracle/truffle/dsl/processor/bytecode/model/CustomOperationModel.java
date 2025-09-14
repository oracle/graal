/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.Template;

/**
 * Model for a user-defined operation.
 *
 * We define this class using composition rather than inheritance because a custom operation is
 * generated based on some template type (like an {@link Operation} or {@link OperationProxy}), and
 * it needs to accept warning/error messages when the operation is validated.
 */
public class CustomOperationModel extends Template {

    public final BytecodeDSLModel bytecode;
    public final OperationModel operation;
    public final List<TypeMirror> implicitTags = new ArrayList<>();
    public Boolean forceCached;
    public boolean customYield;
    public Boolean storeBytecodeIndex;

    public CustomOperationModel(ProcessorContext context, BytecodeDSLModel bytecode, TypeElement templateType, AnnotationMirror mirror, OperationModel operation) {
        super(context, templateType, mirror);
        this.bytecode = bytecode;
        this.operation = operation;
        operation.customModel = this;
    }

    public List<TypeMirror> getImplicitTags() {
        return implicitTags;
    }

    public boolean isEpilogExceptional() {
        return ElementUtils.typeEquals(getTemplateTypeAnnotation().getAnnotationType(), types.EpilogExceptional);
    }

    public MessageContainer getModelForMessages() {
        if (ElementUtils.typeEquals(getTemplateTypeAnnotation().getAnnotationType(), types.OperationProxy)) {
            // Messages should appear on the @OperationProxy annotation, which is defined on the
            // root node, not the proxied node.
            return bytecode;
        } else {
            return this;
        }
    }

    public void setForceCached() {
        this.forceCached = true;
    }

    public boolean forcesCached() {
        return forceCached != null && forceCached;
    }

    public void setCustomYield() {
        this.customYield = true;
    }

    public boolean isCustomYield() {
        return this.customYield;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        if (operation.instruction != null && operation.instruction.nodeData != null) {
            return List.of(operation.instruction.nodeData);
        }
        return List.of();
    }

    public void setStoreBytecodeIndex(Boolean performsCalls) {
        this.storeBytecodeIndex = performsCalls;
    }

    public boolean isStoreBytecodeIndex() {
        return storeBytecodeIndex == null ? true : storeBytecodeIndex;
    }

    public boolean isStoreBytecodeIndexSet() {
        return storeBytecodeIndex != null;
    }

    public boolean inferStoreBytecodeIndex() {
        NodeData node = this.operation.instruction.nodeData;
        if (node == null) {
            // not a custom node, so not bytecode index to store
            return false;
        }
        for (SpecializationData specializationData : node.getReachableSpecializations()) {
            if (inferStoreBytecodeIndex(specializationData)) {
                return true;
            }
        }
        return false;
    }

    /**
     * To avoid storing the bci in cases when the operation is simple, we use the heuristic that a
     * node will not escape/read its own bci unless it has a cached value. The bci generally escapes
     * for calls.
     *
     * Note: the caches list includes bind values, so @Bind("$node") is included in the check.
     */
    @SuppressWarnings("static-method")
    public boolean inferStoreBytecodeIndex(SpecializationData s) {
        return !s.getCaches().isEmpty();
    }

    public boolean shouldStoreBytecodeIndex() {
        if (isStoreBytecodeIndexSet()) {
            if (isStoreBytecodeIndex()) {
                return true;
            } else {
                NodeData node = this.operation.instruction.nodeData;
                if (node == null) {
                    return false;
                }
                for (SpecializationData specializationData : node.getReachableSpecializations()) {
                    if (shouldStoreBytecodeIndex(specializationData)) {
                        return true;
                    }
                }
                return false;
            }
        } else {
            return inferStoreBytecodeIndex();
        }
    }

    @SuppressWarnings("static-method")
    public boolean shouldStoreBytecodeIndex(SpecializationData s) {
        if (isStoreBytecodeIndexSet()) {
            if (isStoreBytecodeIndex()) {
                return inferStoreBytecodeIndex(s);
            } else {
                if (s.getMethod() == null) {
                    return false;
                }
                return ElementUtils.findAnnotationMirror(s.getMethod(), ProcessorContext.types().StoreBytecodeIndex) != null;
            }
        } else {
            return inferStoreBytecodeIndex(s);
        }
    }

}
