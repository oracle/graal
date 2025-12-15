/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;
import static javax.lang.model.element.Modifier.PRIVATE;

import java.util.List;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ChildExecutionResult;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

/**
 * Interface that allows node generators to customize the way {@link FlatNodeGenFactory} generates
 * nodes. A node generator (e.g., {@link BytecodeRootNodeElement}) can pass its own implementation
 * of this interface to the {@link FlatNodeGenFactory} during construction, and the factory will
 * delegate to it.
 */
public interface NodeGeneratorPlugs {
    NodeGeneratorPlugs DEFAULT = new NodeGeneratorPlugs() {
    };

    default List<? extends VariableElement> additionalArguments() {
        return List.of();
    }

    default void modifyIntrospectionMethod(@SuppressWarnings("unused") CodeExecutableElement m) {
    }

    default ChildExecutionResult createExecuteChild(FlatNodeGenFactory factory, CodeTreeBuilder builder, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution,
                    LocalVariable targetValue) {
        return factory.createExecuteChild(builder, originalFrameState, frameState, execution, targetValue);
    }

    default String createNodeChildReferenceForException(FlatNodeGenFactory flatNodeGenFactory, FrameState frameState, NodeExecutionData execution, NodeChildData child) {
        return flatNodeGenFactory.createNodeChildReferenceForException(frameState, execution, child);
    }

    default boolean canEliminateTypeGuard(NodeExecutionData currentExecution, TypeMirror type) {
        if (!isPrimitive(type)) {
            return false;
        }
        return currentExecution.getChild().findExecutableType(type) != null;
    }

    @SuppressWarnings("unused")
    default void beforeCallSpecialization(FlatNodeGenFactory nodeFactory, CodeTreeBuilder builder, FrameState frameState, SpecializationData specialization) {
    }

    @SuppressWarnings("unused")
    default void notifySpecialize(FlatNodeGenFactory nodeFactory, CodeTreeBuilder builder, FrameState frameState, SpecializationData specialization) {
    }

    @SuppressWarnings("unused")
    default CodeTree bindExpressionValue(FrameState frameState, Variable variable) {
        return switch (variable.getName()) {
            case NodeParser.SYMBOL_FRAME -> {
                if (!ElementUtils.isAssignable(frameState.getFrameType(), ProcessorContext.types().Frame)) {
                    throw new AssertionError("Expression binds the frame, but the frame is unavailable. This should have been validated already.");
                }
                yield CodeTreeBuilder.singleString(TemplateMethod.FRAME_NAME);
            }
            default -> null;
        };
    }

    default CodeTree createStateLoad(FlatNodeGenFactory factory, FrameState frameState, BitSet bitSet) {
        return factory.createInlinedAccess(frameState, null, CodeTreeBuilder.singleString("this." + bitSet.getName() + "_"), null);
    }

    default CodeTree createStatePersist(FlatNodeGenFactory factory, FrameState frameState, BitSet bitSet, CodeTree valueTree) {
        return factory.createInlinedAccess(frameState, null, CodeTreeBuilder.singleString("this." + bitSet.getName() + "_"), valueTree);
    }

    @SuppressWarnings("unused")
    default CodeVariableElement createStateField(FlatNodeGenFactory factory, BitSet bitSet) {
        return FlatNodeGenFactory.createNodeField(PRIVATE, bitSet.getType(), bitSet.getName() + "_",
                        ProcessorContext.types().CompilerDirectives_CompilationFinal);
    }

    default int getMaxStateBitWidth() {
        return FlatNodeGenFactory.DEFAULT_MAX_BIT_WIDTH;
    }

}
