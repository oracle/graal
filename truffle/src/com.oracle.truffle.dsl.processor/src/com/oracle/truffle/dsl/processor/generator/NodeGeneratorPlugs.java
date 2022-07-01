/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.function.Consumer;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.BoxingSplit;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ReportPolymorphismAction;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public interface NodeGeneratorPlugs {
    void setNodeData(NodeData node);

    String transformNodeMethodName(String name);

    String transformNodeInnerTypeName(String name);

    void addNodeCallParameters(CodeTreeBuilder builder, boolean isBoundary, boolean isRemoveThis);

    boolean shouldIncludeValuesInCall();

    int getMaxStateBits(int defaultValue);

    TypeMirror getBitSetType(TypeMirror defaultType);

    CodeTree createBitSetReference(BitSet bits);

    CodeTree transformValueBeforePersist(CodeTree tree);

    CodeTree createSpecializationFieldReference(FrameState frame, SpecializationData s, String fieldName, boolean useSpecializationClass, TypeMirror fieldType);

    CodeTree createNodeFieldReference(FrameState frame, NodeExecutionData execution, String nodeFieldName, boolean forRead);

    CodeTree createCacheReference(FrameState frame, SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead);

    CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder);

    void initializeFrameState(FrameState frameState, CodeTreeBuilder builder);

    boolean createCallSpecialization(FrameState frameState, SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder builder, boolean inBoundary, CodeTree[] bindings);

    boolean createCallExecuteAndSpecialize(FrameState frameState, CodeTreeBuilder builder, CodeTree call);

    void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments);

    boolean createCallWrapInAMethod(FrameState frameState, CodeTreeBuilder parentBuilder, CodeExecutableElement method, Runnable addStateParameters);

    CodeTree createCallChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState);

    CodeTree createThrowUnsupportedChild(NodeExecutionData execution);

    Boolean needsFrameToExecute(List<SpecializationData> specializations);

    void addAdditionalStateBits(List<Object> stateObjects);

    void setStateObjects(List<Object> stateObjects);

    void setMultiState(MultiStateBitSet multiState);

    int getRequiredStateBits(TypeSystemData types, Object object);

    void createSpecialize(FrameState frameState, SpecializationData specialization, CodeTreeBuilder builder);

    boolean needsRewrites();

    void setBoxingSplits(List<BoxingSplit> boxingSplits);

    List<SpecializationData> filterSpecializations(List<SpecializationData> implementedSpecializations);

    boolean isStateGuaranteed(boolean stateGuaranteed);

    StaticConstants createConstants();

    ReportPolymorphismAction createReportPolymorhoismAction(ReportPolymorphismAction original);

    CodeTree createGetLock();

    CodeTree createSuperInsert(CodeTree value);
}
