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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.SEALED;
import static javax.lang.model.element.Modifier.STATIC;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class AbstractBytecodeNodeElement extends AbstractElement {

    private final CodeExecutableElement continueAt;
    final CodeExecutableElement getCachedLocalTagInternal;
    final CodeExecutableElement setCachedLocalTagInternal;
    final CodeExecutableElement checkStableTagsAssumption;

    AbstractBytecodeNodeElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, ABSTRACT, SEALED), ElementKind.CLASS, null, "AbstractBytecodeNode");

        setSuperClass(types.BytecodeNode);
        add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(byte.class)), "bytecodes")));
        add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(Object.class)), "constants")));
        add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(int.class)), "handlers")));
        add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), type(int[].class), "locals")));
        add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), type(int[].class), "sourceInfo")));
        add(new CodeVariableElement(Set.of(FINAL), generic(type(List.class), types.Source), "sources"));
        add(new CodeVariableElement(Set.of(FINAL), type(int.class), "numNodes"));

        if (parent.model.enableTagInstrumentation) {
            parent.child(add(new CodeVariableElement(Set.of(), parent.tagRootNode.asType(), "tagRoot")));
        }

        for (ExecutableElement superConstructor : ElementFilter.constructorsIn(ElementUtils.castTypeElement(types.BytecodeNode).getEnclosedElements())) {
            CodeExecutableElement constructor = CodeExecutableElement.cloneNoAnnotations(superConstructor);
            constructor.setReturnType(null);
            constructor.setSimpleName(this.getSimpleName());
            constructor.getParameters().remove(0);

            for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                constructor.addParameter(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
            }

            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().string("BytecodeRootNodesImpl.VISIBLE_TOKEN").end().end();
            for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                b.startStatement();
                b.string("this.", var.getSimpleName().toString(), " = ", var.getSimpleName().toString());
                b.end();
            }
            add(constructor);
            break;
        }

        if (parent.model.enableTagInstrumentation) {
            add(createFindInstrumentableCallNode());
        }

        add(createFindBytecodeIndex2());
        add(createReadValidBytecode());

        continueAt = add(new CodeExecutableElement(Set.of(ABSTRACT), type(long.class), "continueAt"));
        continueAt.addParameter(new CodeVariableElement(parent.asType(), "$root"));
        continueAt.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            continueAt.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        continueAt.addParameter(new CodeVariableElement(type(long.class), "startState"));

        var getRoot = add(new CodeExecutableElement(Set.of(FINAL), parent.asType(), "getRoot"));
        CodeTreeBuilder b = getRoot.createBuilder();
        b.lineComment("We do not support changing or wrapping the bytecode node.");
        b.startReturn().tree(BytecodeRootNodeElement.uncheckedCast(parent.asType(), "getParent()")).end();

        var findLocation = this.add(new CodeExecutableElement(Set.of(STATIC), types.BytecodeLocation, "findLocation"));
        findLocation.addParameter(new CodeVariableElement(this.asType(), "node"));
        findLocation.addParameter(new CodeVariableElement(type(int.class), "bci"));
        b = findLocation.createBuilder();
        b.startReturn().startCall("node.findLocation").string("bci").end().end();

        var toCached = this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "toCached"));
        if (parent.model.usesBoxingElimination()) {
            toCached.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
        }

        CodeExecutableElement update = this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "update"));

        for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
            update.addParameter(new CodeVariableElement(e.asType(), e.getSimpleName().toString() + "_"));
        }

        if (parent.model.isBytecodeUpdatable()) {
            this.add(createInvalidate());
        }

        this.add(createValidateBytecodes());
        this.add(createDumpInvalid());

        this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "cloneUninitialized"));
        this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(types.Node), "getCachedNodes"));
        this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(int.class)), "getBranchProfiles"));
        if (parent.model.usesBoxingElimination()) {
            this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(byte.class)), "getLocalTags"));
            /**
             * Even though tags are only cached on cached nodes, all nodes need to implement these
             * methods, because the callee does not know if the node is cached/uncached.
             */
            getCachedLocalTagInternal = this.add(createGetCachedLocalTagInternal());
            setCachedLocalTagInternal = this.add(createSetCachedLocalTagInternal());
            if (parent.model.hasYieldOperation()) {
                checkStableTagsAssumption = this.add(createCheckStableTagsAssumption());
            } else {
                checkStableTagsAssumption = null;
            }
        } else {
            getCachedLocalTagInternal = null;
            setCachedLocalTagInternal = null;
            checkStableTagsAssumption = null;
        }

        // Define methods for introspecting the bytecode and source.
        this.add(createGetSourceSection());
        this.add(createGetSourceLocation());
        this.add(createGetSourceLocations());
        this.add(createCreateSourceSection());
        this.add(createFindInstruction());
        this.add(createValidateBytecodeIndex());
        this.add(createGetSourceInformation());
        this.add(createHasSourceInformation());
        this.add(createGetSourceInformationTree());
        this.add(createGetExceptionHandlers());
        this.add(createGetTagTree());

        this.add(createGetLocalCount());
        this.add(createClearLocalValueInternal());
        this.add(createIsLocalClearedInternal());
        this.add(createGetLocalNameInternal());
        this.add(createGetLocalInfoInternal());

        if (!parent.model.usesBoxingElimination()) {
            this.add(createGetLocalValue());
            this.add(createSetLocalValue());
            this.add(createGetLocalValueInternal());
            this.add(createSetLocalValueInternal());
        } else {
            this.add(createAbstractSetLocalValueInternal());
        }

        if (parent.model.enableBlockScoping) {
            this.add(createLocalOffsetToTableIndex());
            this.add(createLocalOffsetToLocalIndex());
            this.add(createLocalIndexToAnyTableIndex());
        }
        if (parent.model.canValidateMaterializedLocalLiveness()) {
            this.add(createValidateMaterializedLocalLivenessInternal());
            this.add(createLocalIndexToTableIndex());
        }

        this.add(createGetLocalName());
        this.add(createGetLocalInfo());
        this.add(createGetLocals());

        this.add(createResolveFrameImplFrameInstance());
        if (parent.model.captureFramesForTrace) {
            this.add(createResolveFrameImplTruffleStackTraceElement());
            this.add(createResolveNonVirtualFrameImpl());
        }

        if (parent.model.enableTagInstrumentation) {
            this.add(createGetTagNodes());
        }

        this.add(createTranslateBytecodeIndex());
        if (parent.model.isBytecodeUpdatable() || parent.model.hasYieldOperation()) {
            this.add(createTransition());
        }
        if (parent.model.hasYieldOperation() && parent.model.enableInstructionTracing) {
            this.add(createIsInstructionTracingEnabled());
        }

        if (parent.model.isBytecodeUpdatable()) {
            this.add(createToStableBytecodeIndex());
            this.add(createFromStableBytecodeIndex());
            this.add(createTransitionInstrumentationIndex());
            this.add(createComputeNewBci());
        }
        this.add(createAdoptNodesAfterUpdate());
    }

    private CodeExecutableElement createIsInstructionTracingEnabled() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "isInstructionTracingEnabled");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.string("readValidBytecode(this.bytecodes, 0) == ").tree(parent.createInstructionConstant(parent.model.traceInstruction));
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetLocalCount() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalCount", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
        ex.getModifiers().add(FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
        b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();

        if (parent.model.enableBlockScoping) {
            b.declaration(type(int.class), "count", "0");
            b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
            b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
            b.statement("count++");
            b.end();
            b.end();
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("count").end().end();
            b.statement("return count");
        } else {
            b.statement("return locals.length / LOCALS_LENGTH");
        }
        return ex;
    }

    private CodeExecutableElement createClearLocalValueInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "clearLocalValueInternal",
                        new String[]{"frame", "localOffset", "localIndex"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        buildVerifyFrameDescriptor(b, true);
        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        b.statement("frame.clear(frameIndex)");

        return ex;
    }

    private CodeExecutableElement createIsLocalClearedInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "isLocalClearedInternal",
                        new String[]{"frame", "localOffset", "localIndex"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        buildVerifyFrameDescriptor(b, true);
        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        b.startReturn();
        b.string("frame.getTag(frameIndex) == FrameSlotKind.Illegal.tag");
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocalValue() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValue",
                        new String[]{"bci", "frame", "localOffset"},
                        new TypeMirror[]{type(int.class), types.Frame, type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        buildVerifyLocalsIndex(b);
        buildVerifyFrameDescriptor(b, false);

        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        b.startIf().string("frame.isObject(frameIndex)").end().startBlock();
        b.startReturn().string("frame.getObject(frameIndex)").end();
        b.end();
        b.statement("return null");

        return ex;
    }

    private CodeExecutableElement createGetLocalValueInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValueInternal",
                        new String[]{"frame", "localOffset", "localIndex"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        buildVerifyFrameDescriptor(b, true);

        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        b.startReturn().string("frame.getObject(frameIndex)").end();
        return ex;
    }

    private CodeExecutableElement createSetLocalValue() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValue",
                        new String[]{"bci", "frame", "localOffset", "value"},
                        new TypeMirror[]{type(int.class), types.Frame, type(int.class), type(Object.class)});
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
        buildVerifyFrameDescriptor(b, false);
        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        b.startStatement();
        b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createSetLocalValueInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal",
                        new String[]{"frame", "localOffset", "localIndex", "value"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class), type(Object.class)});
        CodeTreeBuilder b = ex.createBuilder();
        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

        b.startStatement();
        b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("USER_LOCALS_START_INDEX + localOffset").string("value").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createAbstractSetLocalValueInternal() {
        // Redeclare the method so it is visible on the AbstractBytecodeNode.
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError();
        }
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal",
                        new String[]{"frame", "localOffset", "localIndex", "value"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class), type(Object.class)});
        ex.getModifiers().add(ABSTRACT);
        return ex;
    }

    private CodeExecutableElement createLocalOffsetToTableIndex() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localOffsetToTableIndex");
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(type(int.class), "count", "0");
        b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
        b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
        b.startIf().string("count == localOffset").end().startBlock();
        b.startReturn().string("index").end();
        b.end();
        b.statement("count++");
        b.end();
        b.end();
        b.statement("return -1");
        return ex;
    }

    private CodeExecutableElement createLocalOffsetToLocalIndex() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localOffsetToLocalIndex");
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(type(int.class), "tableIndex", "localOffsetToTableIndex(bci, localOffset)");
        b.startAssert().string("locals[tableIndex + LOCALS_OFFSET_FRAME_INDEX] == localOffset + USER_LOCALS_START_INDEX : ").doubleQuote("Inconsistent indices.").end();
        b.startReturn().string("locals[tableIndex + LOCALS_OFFSET_LOCAL_INDEX]").end();
        return ex;
    }

    private CodeExecutableElement createLocalIndexToTableIndex() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localIndexToTableIndex");
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
        CodeTreeBuilder b = ex.createBuilder();
        b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
        b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
        b.startIf().string("locals[index + LOCALS_OFFSET_LOCAL_INDEX] == localIndex").end().startBlock();
        b.startReturn().string("index").end();
        b.end();
        b.end();
        b.end();
        b.statement("return -1");
        return ex;
    }

    /**
     * Like localIndexToTableIndex, but does not check a bci. Useful for metadata (names/infos) that
     * are consistent across all table entries for a given local index.
     */
    private CodeExecutableElement createLocalIndexToAnyTableIndex() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localIndexToAnyTableIndex");
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
        CodeTreeBuilder b = ex.createBuilder();
        b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
        b.startIf().string("locals[index + LOCALS_OFFSET_LOCAL_INDEX] == localIndex").end().startBlock();
        b.startReturn().string("index").end();
        b.end();
        b.end();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(type(AssertionError.class));
        b.doubleQuote("Local index not found in locals table");
        b.end(2);
        return ex;
    }

    private CodeExecutableElement createValidateMaterializedLocalLivenessInternal() {
        if (!parent.model.canValidateMaterializedLocalLiveness()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(boolean.class), "validateLocalLivenessInternal");
        ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
        ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
        ex.addParameter(new CodeVariableElement(types.Frame, "stackFrame"));
        ex.addParameter(new CodeVariableElement(type(int.class), "stackFrameBci"));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int.class), "bci");
        b.startIf().string("frame == stackFrame").end().startBlock();
        b.lineComment("Loading a value from the current frame. Use the precise bci (the frame is only updated when control escapes).");
        b.statement("bci = stackFrameBci");
        b.end();
        b.end().startElseBlock();
        b.startAssign("bci");
        BytecodeRootNodeElement.startGetFrame(b, "frame", type(int.class), false).string("BCI_INDEX").end();
        b.end();
        b.end();

        b.lineComment("Ensure the local we're trying to access is live at the current bci.");
        b.startIf().string("locals[localIndexToTableIndex(bci, localIndex) + LOCALS_OFFSET_FRAME_INDEX] != frameIndex").end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Local is out of scope in the frame passed for a materialized local access.");
        b.end();

        b.returnTrue();

        return ex;
    }

    private CodeExecutableElement createGetCachedLocalTagInternal() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(byte.class), "getCachedLocalTagInternal");
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
        return ex;
    }

    private CodeExecutableElement createSetCachedLocalTagInternal() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(void.class), "setCachedLocalTagInternal");
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
        ex.addParameter(new CodeVariableElement(type(byte.class), "tag"));
        return ex;
    }

    private CodeExecutableElement createCheckStableTagsAssumption() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(boolean.class), "checkStableTagsAssumption");
        return ex;
    }

    static void buildVerifyLocalsIndex(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(ProcessorContext.types().CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();
        b.startStatement().startStaticCall(ProcessorContext.types().CompilerAsserts, "partialEvaluationConstant").string("localOffset").end().end();
        b.startAssert().string("localOffset >= 0 && localOffset < getLocalCount(bci) : ").doubleQuote("Invalid out-of-bounds local offset provided.").end();
    }

    static void buildVerifyFrameDescriptor(CodeTreeBuilder b, boolean trustFrame) {
        String errorMessage = "Invalid frame with invalid descriptor passed.";
        if (trustFrame) {
            b.startAssert();
            b.string("getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : \"", errorMessage, "\"");
            b.end();
        } else {
            b.startIf().string("getRoot().getFrameDescriptor() != frame.getFrameDescriptor()").end().startBlock();
            BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, errorMessage);
            b.end();
        }
    }

    private CodeExecutableElement createGetLocalName() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalName",
                        new String[]{"bci", "localOffset"}, new TypeMirror[]{type(int.class), type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        buildVerifyLocalsIndex(b);

        if (parent.model.enableBlockScoping) {
            b.declaration(type(int.class), "index", "localOffsetToTableIndex(bci, localOffset)");
            b.startIf().string("index == -1").end().startBlock();
            b.returnNull();
            b.end();
            b.declaration(type(int.class), "nameId", "locals[index + LOCALS_OFFSET_NAME]");
        } else {
            b.declaration(type(int.class), "nameId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_NAME]");
        }
        b.startIf().string("nameId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("nameId", "this.constants")).end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocalNameInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalNameInternal",
                        new String[]{"localOffset", "localIndex"}, new TypeMirror[]{type(int.class), type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.enableBlockScoping) {
            b.declaration(type(int.class), "index", "localIndexToAnyTableIndex(localIndex)");
            b.declaration(type(int.class), "nameId", "locals[index + LOCALS_OFFSET_NAME]");
        } else {
            b.declaration(type(int.class), "nameId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_NAME]");
        }
        b.startIf().string("nameId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("nameId", "this.constants")).end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocalInfo() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalInfo",
                        new String[]{"bci", "localOffset"}, new TypeMirror[]{type(int.class), type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        buildVerifyLocalsIndex(b);

        if (parent.model.enableBlockScoping) {
            b.declaration(type(int.class), "index", "localOffsetToTableIndex(bci, localOffset)");
            b.startIf().string("index == -1").end().startBlock();
            b.returnNull();
            b.end();
            b.declaration(type(int.class), "infoId", "locals[index + LOCALS_OFFSET_INFO]");
        } else {
            b.declaration(type(int.class), "infoId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_INFO]");
        }
        b.startIf().string("infoId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("infoId", "this.constants")).end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocalInfoInternal() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalInfoInternal",
                        new String[]{"localOffset", "localIndex"}, new TypeMirror[]{type(int.class), type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.enableBlockScoping) {
            b.declaration(type(int.class), "index", "localIndexToAnyTableIndex(localIndex)");
            b.declaration(type(int.class), "infoId", "locals[index + LOCALS_OFFSET_INFO]");
        } else {
            b.declaration(type(int.class), "infoId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_INFO]");
        }
        b.startIf().string("infoId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("infoId", "this.constants")).end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocals() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocals");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startNew("LocalVariableList").string("this").end().end();
        return ex;
    }

    private CodeExecutableElement createResolveFrameImplFrameInstance() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "resolveFrameImpl", new String[]{"frameInstance", "access"});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.hasYieldOperation()) {
            b.startIf().string("frameInstance.getCallTarget() instanceof ").type(types.RootCallTarget).string(" root && root.getRootNode() instanceof ").type(
                            parent.continuationRootNodeImpl.asType()).string(" continuation").end().startBlock();
            b.lineComment("Continuations use materialized frames, which support all access modes.");
            b.startReturn().startCall("continuation.findFrame").startCall("frameInstance.getFrame");
            b.staticReference(types.FrameInstance_FrameAccess, "READ_ONLY");
            b.end(3);
            b.end(); // if
        }
        b.startReturn().string("frameInstance.getFrame(access)").end();
        return ex;
    }

    private CodeExecutableElement createResolveFrameImplTruffleStackTraceElement() {
        if (!parent.model.captureFramesForTrace) {
            throw new AssertionError("should not generate resolveFrameImpl(TruffleStackTraceElement) if frames are not captured.");
        }
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "resolveFrameImpl", new String[]{"element"});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.hasYieldOperation()) {
            b.declaration(types.Frame, "frame", "element.getFrame()");
            b.startIf().string("frame != null && element.getTarget().getRootNode() instanceof ").type(parent.continuationRootNodeImpl.asType()).string(
                            " continuation").end().startBlock();
            b.statement("frame = continuation.findFrame(frame)");
            b.end();
            b.startReturn().string("frame").end();
        } else {
            b.startReturn().string("element.getFrame()").end();
        }
        return ex;
    }

    private CodeExecutableElement createResolveNonVirtualFrameImpl() {
        if (!parent.model.captureFramesForTrace) {
            throw new AssertionError("should not generate resolveNonVirtualFrameImpl(TruffleStackTraceElement) if frames are not captured.");
        }
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "resolveNonVirtualFrameImpl", new String[]{"element"});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.hasYieldOperation()) {
            b.declaration(types.Frame, "frame", "element.getFrame()");
            b.startIf().string("frame != null && element.getTarget().getRootNode() instanceof ").type(parent.continuationRootNodeImpl.asType()).string(
                            " continuation").end().startBlock();
            b.lineComment("Continuation frames are always materialized.");
            b.startReturn().string("continuation.findFrame(frame)").end();
            b.end();
        }
        b.lineComment("Frames obtained in stack walks are always read-only.");
        b.startReturn().string("null").end();
        return ex;
    }

    record InstructionValidationGroup(List<InstructionImmediate> immediates, int instructionLength, boolean allowNegativeChildBci, boolean localVar, boolean localVarMat) {

        InstructionValidationGroup(BytecodeDSLModel model, InstructionModel instruction) {
            this(instruction.getImmediates(), instruction.getInstructionLength(),
                            acceptsInvalidChildBci(model, instruction),
                            instruction.kind.isLocalVariableAccess(),
                            instruction.kind.isLocalVariableMaterializedAccess());
        }

    }

    private CodeExecutableElement createValidateBytecodes() {
        CodeExecutableElement validate = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "validateBytecodes");
        CodeTreeBuilder b = validate.createBuilder();

        b.declaration(parent.asType(), "root");
        b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
        b.startIf().string("bc == null").end().startBlock();
        b.lineComment("bc is null for serialization root nodes.");
        b.statement("return true");
        b.end();

        b.declaration(arrayOf(types.Node), "cachedNodes", "getCachedNodes()");
        b.declaration(arrayOf(type(int.class)), "branchProfiles", "getBranchProfiles()");
        b.declaration(type(int.class), "bci", "0");
        if (parent.model.enableTagInstrumentation) {
            b.declaration(arrayOf(parent.tagNode.asType()), "tagNodes", "tagRoot != null ? tagRoot.tagNodes : null");
        }

        b.startIf().string("bc.length == 0").end().startBlock();
        b.tree(createValidationError("bytecode array must not be null"));
        b.end();

        // Bytecode validation
        b.startWhile().string("bci < bc.length").end().startBlock();
        b.startTryBlock();
        b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();

        Map<InstructionValidationGroup, List<InstructionModel>> groups = parent.model.getInstructions().stream().collect(
                        BytecodeRootNodeElement.deterministicGroupingBy((i) -> new AbstractBytecodeNodeElement.InstructionValidationGroup(parent.model, i)));

        for (var entry : groups.entrySet()) {
            InstructionValidationGroup group = entry.getKey();
            List<InstructionModel> instructions = entry.getValue();

            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startBlock();

            boolean rootNodeAvailable = false;
            for (InstructionImmediate immediate : group.immediates()) {
                String localName = immediate.name();
                CodeTree declareImmediate = CodeTreeBuilder.createBuilder() //
                                .startDeclaration(immediate.kind().toType(parent.context), localName) //
                                .tree(BytecodeRootNodeElement.readImmediate("bc", "bci", immediate)) //
                                .end() //
                                .build();

                switch (immediate.kind()) {
                    case BYTECODE_INDEX:
                        b.tree(declareImmediate);
                        b.startIf();
                        if (group.allowNegativeChildBci()) {
                            // supports -1 immediates
                            b.string(localName, " < -1");
                        } else {
                            b.string(localName, " < 0");
                        }
                        b.string(" || ").string(localName).string(" >= bc.length").end().startBlock();
                        b.tree(createValidationErrorWithBci("bytecode index is out of bounds"));
                        b.end();
                        break;
                    case SHORT:
                    case INTEGER:
                    case CONSTANT_LONG:
                    case CONSTANT_DOUBLE:
                    case CONSTANT_INT:
                    case CONSTANT_FLOAT:
                    case CONSTANT_SHORT:
                    case CONSTANT_CHAR:
                    case CONSTANT_BYTE:
                    case CONSTANT_BOOL:
                        break;
                    case STACK_POINTER:
                        b.tree(declareImmediate);
                        b.startAssign("root").string("this.getRoot()").end();
                        b.declaration(type(int.class), "maxStackHeight", "root.getFrameDescriptor().getNumberOfSlots() - root.maxLocals");
                        b.startIf().string(localName, " < 0 || ", localName, " > maxStackHeight").end().startBlock();
                        b.tree(createValidationErrorWithBci("stack pointer is out of bounds"));
                        b.end();
                        break;
                    case FRAME_INDEX: {
                        b.tree(declareImmediate);
                        if (!rootNodeAvailable) {
                            rootNodeAvailable = tryEmitRootNodeForLocalInstruction(b, group);
                        }
                        b.startIf().string(localName).string(" < USER_LOCALS_START_INDEX");
                        if (rootNodeAvailable) {
                            b.string(" || ").string(localName).string(" >= root.maxLocals");
                        }
                        b.end().startBlock();
                        b.tree(createValidationErrorWithBci("local offset is out of bounds"));
                        b.end();
                        break;
                    }
                    case LOCAL_INDEX: {
                        b.tree(declareImmediate);
                        /*
                         * NB: There is an edge case where instructions have local index immediates
                         * that cannot be validated because the numLocals field is not generated
                         * (intentionally, to reduce footprint). It happens with materialized
                         * loads/stores, and only when the bci is stored in the frame, in which case
                         * the local index is validated at run time with an assertion anyway.
                         */
                        boolean hasNumLocals = parent.model.usesBoxingElimination();
                        if (!rootNodeAvailable && hasNumLocals) {
                            rootNodeAvailable = tryEmitRootNodeForLocalInstruction(b, group);
                        }
                        b.startIf().string(localName).string(" < 0");
                        if (rootNodeAvailable && hasNumLocals) {
                            b.string(" || ").string(localName).string(" >= root.numLocals");
                        }
                        b.end().startBlock();
                        b.tree(createValidationErrorWithBci("local index is out of bounds"));
                        b.end();
                        break;
                    }
                    case LOCAL_ROOT:
                        // checked via LOCAL_OFFSET and LOCAL_INDEX
                        break;
                    case CONSTANT:
                        b.tree(declareImmediate);
                        b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= constants.length").end().startBlock();
                        b.tree(createValidationErrorWithBci("constant is out of bounds"));
                        b.end();
                        break;
                    case NODE_PROFILE:
                        b.tree(declareImmediate);
                        b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= numNodes").end().startBlock();
                        b.tree(createValidationErrorWithBci("node profile is out of bounds"));
                        b.end();
                        break;
                    case BRANCH_PROFILE:
                        b.tree(declareImmediate);
                        b.startIf().string("branchProfiles != null").end().startBlock();
                        b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= branchProfiles.length").end().startBlock();
                        b.tree(createValidationErrorWithBci("branch profile is out of bounds"));
                        b.end();
                        b.end();
                        break;
                    case TAG_NODE:
                        b.tree(declareImmediate);
                        b.startIf().string("tagNodes != null").end().startBlock();
                        b.declaration(parent.tagNode.asType(), "node", BytecodeRootNodeElement.readTagNodeSafe(CodeTreeBuilder.singleString(immediate.name())));
                        b.startIf().string("node == null").end().startBlock();
                        b.tree(createValidationErrorWithBci("tagNode is null"));
                        b.end();
                        b.end();
                        break;
                    case STATE_PROFILE:
                        // indirectly validated by node profile
                        break;
                    default:
                        throw new AssertionError("Unexpected kind " + immediate.kind());
                }
            }

            b.statement("bci = bci + " + group.instructionLength());
            b.statement("break");

            b.end();
        }

        b.caseDefault().startCaseBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere(b.create().doubleQuote("Invalid BCI at index: ").string(" + bci").build()));
        b.end();

        b.end(); // switch
        b.end().startCatchBlock(type(AssertionError.class), "e");
        b.statement("throw e");
        b.end();
        b.startCatchBlock(type(Throwable.class), "e");
        b.tree(createValidationError(null, "e", false));
        b.end();

        b.end(); // while

        b.startIf().string("bci != bc.length").end().startBlock();
        b.tree(createValidationError("index after walking bytecode array does not match bytecode array length", null, true));
        b.end();

        // Exception handler validation
        b.declaration(arrayOf(type(int.class)), "ex", "this.handlers");

        b.startIf().string("ex.length % EXCEPTION_HANDLER_LENGTH != 0").end().startBlock();
        b.tree(createValidationError("exception handler table size is incorrect"));
        b.end();

        b.startFor().string("int i = 0; i < ex.length; i = i + EXCEPTION_HANDLER_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startBci", "ex[i + EXCEPTION_HANDLER_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endBci", "ex[i + EXCEPTION_HANDLER_OFFSET_END_BCI]");
        b.declaration(type(int.class), "handlerKind", "ex[i + EXCEPTION_HANDLER_OFFSET_KIND]");
        b.declaration(type(int.class), "handlerBci", "ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
        b.declaration(type(int.class), "handlerSp", "ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP]");

        b.startIf().string("startBci").string(" < 0 || ").string("startBci").string(" >= bc.length").end().startBlock();
        b.tree(createValidationError("exception handler startBci is out of bounds"));
        b.end();

        // exclusive
        b.startIf().string("endBci").string(" < 0 || ").string("endBci").string(" > bc.length").end().startBlock();
        b.tree(createValidationError("exception handler endBci is out of bounds"));
        b.end();

        b.startIf().string("startBci > endBci").end().startBlock();
        b.tree(createValidationError("exception handler bci range is malformed"));
        b.end();

        b.startSwitch().string("handlerKind").end().startBlock();
        if (parent.model.epilogExceptional != null) {
            b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();

            b.startIf().string("handlerBci").string(" != -1").end().startBlock();
            b.tree(createValidationError("exception handler handlerBci is invalid"));
            b.end();

            b.startIf().string("handlerSp").string(" != -1").end().startBlock();
            b.tree(createValidationError("exception handler handlerBci is invalid"));
            b.end();

            b.statement("break");
            b.end();
        }

        if (parent.model.enableTagInstrumentation) {
            b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();

            b.startIf().string("tagNodes != null").end().startBlock();
            b.declaration(parent.tagNode.asType(), "node", BytecodeRootNodeElement.readTagNodeSafe(CodeTreeBuilder.singleString("handlerBci")));
            b.startIf().string("node == null").end().startBlock();
            b.tree(createValidationError("tagNode is null"));
            b.end();
            b.end();

            b.statement("break");
            b.end();
        }

        b.caseDefault().startCaseBlock();
        b.startIf().string("handlerKind != HANDLER_CUSTOM").end().startBlock();
        b.tree(createValidationError("unexpected handler kind"));
        b.end();

        b.startIf().string("handlerBci").string(" < 0 || ").string("handlerBci").string(" >= bc.length").end().startBlock();
        b.tree(createValidationError("exception handler handlerBci is out of bounds"));
        b.end();

        b.statement("break");
        b.end(); // case default

        b.end(); // switch
        b.end(); // for handler

        // Source information validation
        b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
        b.declaration(generic(declaredType(List.class), types.Source), "localSources", "this.sources");

        b.startIf().string("info != null").end().startBlock();
        b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");
        b.declaration(type(int.class), "sourceIndex", "info[i + SOURCE_INFO_OFFSET_SOURCE]");
        b.startIf().string("startBci > endBci").end().startBlock();
        b.tree(createValidationError("source bci range is malformed"));
        b.end().startElseIf().string("sourceIndex < 0 || sourceIndex > localSources.size()").end().startBlock();
        b.tree(createValidationError("source index is out of bounds"));
        b.end();

        b.end(); // for sources
        b.end(); // if sources

        b.startReturn().string("true").end();

        return validate;
    }

    static boolean tryEmitRootNodeForLocalInstruction(CodeTreeBuilder b, InstructionValidationGroup group) {
        if (group.localVar()) {
            b.startAssign("root").string("this.getRoot()").end();
            return true;
        } else if (group.localVarMat()) {
            InstructionImmediate rootImmediate = group.immediates.stream() //
                            .filter(imm -> imm.kind() == ImmediateKind.LOCAL_ROOT) //
                            .findFirst().get();
            b.startAssign("root").startCall("this.getRoot().getBytecodeRootNodeImpl").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", rootImmediate)).end(2);
            return true;
        }
        return false;
    }

    /**
     * Returns true if the instruction can take -1 as a child bci.
     */
    private static boolean acceptsInvalidChildBci(BytecodeDSLModel model, InstructionModel instr) {
        if (!model.usesBoxingElimination()) {
            // Child bci immediates are only used for boxing elimination.
            return false;
        }

        if (instr.isShortCircuitConverter() || instr.isEpilogReturn()) {
            return true;
        }
        return isSameOrGenericQuickening(instr, model.popInstruction) //
                        || isSameOrGenericQuickening(instr, model.storeLocalInstruction) //
                        || (model.usesBoxingElimination() && isSameOrGenericQuickening(instr, model.conditionalOperation.instruction));
    }

    private static boolean isSameOrGenericQuickening(InstructionModel instr, InstructionModel expected) {
        return instr == expected || instr.getQuickeningRoot() == expected && instr.specializedType == null;
    }

    // calls dump, but catches any exceptions and falls back on an error string
    private CodeExecutableElement createDumpInvalid() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(String.class), "dumpInvalid");
        ex.addParameter(new CodeVariableElement(types.BytecodeLocation, "highlightedLocation"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startTryBlock();

        b.startReturn();
        b.string("dump(highlightedLocation)");
        b.end();

        b.end().startCatchBlock(parent.context.getDeclaredType(Throwable.class), "t");
        b.startReturn();
        b.doubleQuote("<dump error>");
        b.end();

        b.end();

        return ex;
    }

    private CodeTree createValidationError(String message) {
        return createValidationError(message, null, false);
    }

    private CodeTree createValidationErrorWithBci(String message) {
        return createValidationError(message, null, true);
    }

    private CodeTree createValidationError(String message, String cause, boolean includeBci) {
        CodeTreeBuilder b = new CodeTreeBuilder(null);
        b.startThrow().startStaticCall(types.CompilerDirectives, "shouldNotReachHere");
        b.startGroup();
        b.startStaticCall(type(String.class), "format");
        b.startGroup().string("\"Bytecode validation error");
        if (includeBci) {
            b.string(" at index: %s.");
        } else {
            b.string(":");
        }
        if (message != null) {
            b.string(" " + message);
        }
        b.string("%n%s\"").end(); // group
        if (includeBci) {
            b.string("bci");
        }
        b.string("dumpInvalid(findLocation(bci))");
        b.end(); // String.format
        b.end(); // group
        if (cause != null) {
            b.string(cause);
        }
        b.end().end();
        return b.build();
    }

    private CodeExecutableElement createFindInstrumentableCallNode() {
        CodeExecutableElement ex = new CodeExecutableElement(
                        Set.of(FINAL),
                        types.Node, "findInstrumentableCallNode",
                        new CodeVariableElement(type(int.class), "bci"));
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int[].class), "localHandlers", "handlers");
        b.startFor().string("int i = 0; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();
        b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci").end().startBlock().statement("continue").end();
        b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci").end().startBlock().statement("continue").end();
        b.statement("int handlerKind = localHandlers[i + EXCEPTION_HANDLER_OFFSET_KIND]");
        b.startIf().string("handlerKind != HANDLER_TAG_EXCEPTIONAL").end().startBlock();
        b.statement("continue");
        b.end();
        b.statement("int nodeId = localHandlers[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
        b.statement("return tagRoot.tagNodes[nodeId]");
        b.end();
        b.statement("return null");
        return ex;
    }

    private CodeExecutableElement createFindBytecodeIndex2() {
        // override to make method visible
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                        new String[]{"frame", "operationNode"}, new TypeMirror[]{types.Frame, types.Node});
        ex.getModifiers().add(ABSTRACT);
        return ex;
    }

    private CodeExecutableElement createReadValidBytecode() {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(FINAL),
                        type(int.class), "readValidBytecode",
                        new CodeVariableElement(type(byte[].class), "bc"),
                        new CodeVariableElement(type(int.class), "bci"));
        CodeTreeBuilder b = method.createBuilder();
        if (parent.model.isBytecodeUpdatable()) {
            b.declaration(type(int.class), "op", BytecodeRootNodeElement.readInstruction("bc", "bci"));
            b.startSwitch().string("op").end().startBlock();
            for (InstructionModel instruction : parent.model.getInvalidateInstructions()) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.lineComment("While we were processing the exception handler the code invalidated.");
            b.lineComment("We need to re-read the op from the old bytecodes.");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startReturn().tree(BytecodeRootNodeElement.readInstruction("oldBytecodes", "bci")).end();
            b.end(); // case
            b.caseDefault().startCaseBlock();
            b.statement("return op");
            b.end();
            b.end(); // switch
        } else {
            b.lineComment("The bytecode is not updatable so the bytecode is always valid.");
            b.startReturn().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end();
        }
        return method;
    }

    private CodeExecutableElement createGetTagNodes() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), arrayOf(parent.tagNode.asType()), "getTagNodes");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("tagRoot != null ? tagRoot.tagNodes : null").end();
        return ex;
    }

    private CodeExecutableElement createTranslateBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "translateBytecodeIndex", new String[]{"newNode", "bytecodeIndex"});
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.isBytecodeUpdatable()) {

            CodeTreeBuilder tb = CodeTreeBuilder.createBuilder();
            tb.startCall("transition");
            tb.startGroup();
            tb.cast(this.asType());
            tb.string("newNode");
            tb.end();
            tb.string(parent.encodeState("bytecodeIndex", null));
            if (parent.model.hasYieldOperation()) {
                tb.string("null");
            }
            tb.end();

            b.startReturn();
            b.string(BytecodeRootNodeElement.decodeBci(tb.build().toString()));
            b.end();
        } else {
            b.statement("return bytecodeIndex");
        }
        return ex;
    }

    private CodeExecutableElement createTransition() {
        // Returns updated state long, if updatable.
        TypeMirror returnType = parent.model.isBytecodeUpdatable() ? type(long.class) : type(void.class);

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), returnType, "transition");
        ex.addParameter(new CodeVariableElement(this.asType(), "newBytecode"));
        if (parent.model.isBytecodeUpdatable()) {
            ex.addParameter(new CodeVariableElement(type(long.class), "state"));
        }
        if (parent.model.hasYieldOperation()) {
            ex.addParameter(new CodeVariableElement(parent.continuationRootNodeImpl.asType(), "continuationRootNode"));
        }

        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.hasYieldOperation()) {
            /*
             * We can be here for one of two reasons:
             *
             * 1. We transitioned from uncached/uninitialized to cached. In this case, we update the
             * ContinuationRootNode so future calls will start executing the cached interpreter.
             *
             * 2. Bytecode was rewritten. In this case, since the bytecode invalidation logic
             * patches all ContinuationRootNodes with the new bytecode, we don't have to update
             * anything.
             */
            b.startIf().string("continuationRootNode != null && this.getTier() == ").staticReference(types.BytecodeTier, "UNCACHED").end().startBlock();
            b.lineComment("Transition continuationRootNode to cached.");

            b.startDeclaration(types.BytecodeLocation, "newContinuationLocation");
            b.startCall("newBytecode.getBytecodeLocation");
            b.string("continuationRootNode.getLocation().getBytecodeIndex()");
            b.end(2);

            b.startStatement().startCall("continuationRootNode.updateBytecodeLocation");
            b.string("newContinuationLocation");
            b.string("this");
            b.string("newBytecode");
            b.doubleQuote("transition to cached");
            b.end(2);

            b.end();
        }

        if (parent.model.isBytecodeUpdatable()) {
            b.declaration(arrayOf(type(byte.class)), "oldBc", "this.oldBytecodes");
            b.declaration(arrayOf(type(byte.class)), "newBc", "newBytecode.bytecodes");
            b.startIf().string("oldBc == null || this == newBytecode || this.bytecodes == newBc").end().startBlock();
            b.lineComment("No change in bytecodes.");
            b.startReturn().string("state").end();
            b.end();

            b.declaration(type(int.class), "oldBci", BytecodeRootNodeElement.decodeBci("state"));

            b.startDeclaration(type(int.class), "newBci");
            b.startCall("computeNewBci").string("oldBci").string("oldBc").string("newBc");
            if (parent.model.enableTagInstrumentation) {
                b.string("this.getTagNodes()");
                b.string("newBytecode.getTagNodes()");
            }
            b.end(); // call

            b.end();

            if (parent.model.overridesBytecodeDebugListenerMethod("onBytecodeStackTransition")) {
                b.startStatement();
                b.startCall("getRoot().onBytecodeStackTransition");
                parent.emitParseInstruction(b, "this", "oldBci", BytecodeRootNodeElement.readInstruction("oldBc", "oldBci"));
                parent.emitParseInstruction(b, "newBytecode", "newBci", BytecodeRootNodeElement.readInstruction("newBc", "newBci"));
                b.end().end();
            }

            b.startReturn().string(BytecodeRootNodeElement.encodeNewBci("newBci", "state")).end();
        }

        return ex;
    }

    record InstrumentationGroup(int instructionLength, boolean instrumentation, boolean tagInstrumentation, InstructionImmediate tagNodeImmediate)
                    implements
                        Comparable<AbstractBytecodeNodeElement.InstrumentationGroup> {
        InstrumentationGroup(InstructionModel instr) {
            this(instr.getInstructionLength(), instr.isInstrumentation(), instr.isTagInstrumentation(),
                            instr.isTagInstrumentation() ? instr.getImmediate(ImmediateKind.TAG_NODE) : null);
        }

        @Override
        public int compareTo(AbstractBytecodeNodeElement.InstrumentationGroup o) {
            int compare = Boolean.compare(this.instrumentation, o.instrumentation);
            if (compare != 0) {
                return compare;
            }
            compare = Boolean.compare(this.tagInstrumentation, o.tagInstrumentation);
            if (compare != 0) {
                return compare;
            }

            if (this.tagInstrumentation) {
                compare = Integer.compare(this.tagNodeImmediate.offset(), o.tagNodeImmediate.offset());

                if (compare != 0) {
                    return compare;
                }
            }

            compare = Integer.compare(this.instructionLength, o.instructionLength);
            if (compare != 0) {
                return compare;
            }
            return 0;
        }
    }

    private CodeExecutableElement createTransitionInstrumentationIndex() {

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "transitionInstrumentationIndex");
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "oldBc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "oldBciBase"));
        ex.addParameter(new CodeVariableElement(type(int.class), "oldBciTarget"));
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "newBc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "newBciBase"));
        if (parent.model.enableTagInstrumentation) {
            ex.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "oldTagNodes"));
            ex.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "newTagNodes"));
        }
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(type(int.class), "oldBci", "oldBciBase");
        b.declaration(type(int.class), "newBci", "newBciBase");
        b.lineComment("Find the last instrumentation instruction executed before oldBciTarget.");
        b.lineComment("The new bci should point directly after this reference instruction in the new bytecode.");
        b.declaration(type(short.class), "searchOp", "-1");
        if (parent.model.enableTagInstrumentation) {
            b.declaration(type(int.class), "searchTags", "-1");
        }

        b.startWhile().string("oldBci < oldBciTarget").end().startBlock();
        b.declaration(type(short.class), "op", BytecodeRootNodeElement.readInstruction("oldBc", "oldBci"));
        b.statement("searchOp = op");
        b.startSwitch().string("op").end().startBlock();
        for (var groupEntry : groupInstructionsSortedBy(AbstractBytecodeNodeElement.InstrumentationGroup::new)) {
            AbstractBytecodeNodeElement.InstrumentationGroup group = groupEntry.getKey();
            if (!group.instrumentation) {
                // only instrumentation instructions should be reached
                continue;
            }
            List<InstructionModel> instructions = groupEntry.getValue();
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            if (parent.model.enableTagInstrumentation) {
                if (group.tagInstrumentation) {
                    b.startStatement();
                    b.string("searchTags = ");
                    b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), "oldTagNodes", BytecodeRootNodeElement.readImmediate("oldBc", "oldBci", group.tagNodeImmediate)));
                    b.string(".tags");
                    b.end();
                } else {
                    b.statement("searchTags = -1");
                }
            }
            b.statement("oldBci += " + group.instructionLength);
            b.statement("break");
            b.end();
        }
        b.caseDefault().startCaseBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
        b.end(); // default block
        b.end(); // switch block
        b.end(); // while block

        b.startAssert().string("searchOp != -1").end();
        b.lineComment("The instruction may occur multiple times between oldBci and oldTargetBci.");
        b.lineComment("Count the number of occurrences so that we identify the correct reference instruction.");
        b.startAssign("oldBci").string("oldBciBase").end();
        b.declaration(type(int.class), "opCounter", "0");

        b.startWhile().string("oldBci < oldBciTarget").end().startBlock();
        b.declaration(type(short.class), "op", BytecodeRootNodeElement.readInstruction("oldBc", "oldBci"));
        b.startSwitch().string("op").end().startBlock();
        for (var groupEntry : groupInstructionsSortedBy(AbstractBytecodeNodeElement.InstrumentationGroup::new)) {
            AbstractBytecodeNodeElement.InstrumentationGroup group = groupEntry.getKey();
            if (!group.instrumentation) {
                // only instrumentation instructions should be reached
                continue;
            }
            List<InstructionModel> instructions = groupEntry.getValue();
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startBlock();

            if (group.tagInstrumentation) {
                b.startDeclaration(type(int.class), "opTags");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), "oldTagNodes", BytecodeRootNodeElement.readImmediate("oldBc", "oldBci", group.tagNodeImmediate)));
                b.string(".tags");
                b.end(); // declaration
                b.startIf().string("searchOp == op && searchTags == opTags").end().startBlock();
                b.statement("opCounter++");
                b.end();
            } else {
                b.startIf().string("searchOp == op").end().startBlock();
                b.statement("opCounter++");
                b.end();
            }

            b.statement("oldBci += " + group.instructionLength);
            b.statement("break");
            b.end();
        }
        b.caseDefault().startCaseBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
        b.end(); // default block
        b.end(); // switch block
        b.end(); // while block

        b.startAssert().string("opCounter > 0").end();
        b.lineComment("Walk the new bytecode to find the location directly after the reference instruction.");
        b.startWhile().string("opCounter > 0").end().startBlock();
        b.declaration(type(short.class), "op", BytecodeRootNodeElement.readInstruction("newBc", "newBci"));
        b.startSwitch().string("op").end().startBlock();
        for (var groupEntry : groupInstructionsSortedBy(AbstractBytecodeNodeElement.InstrumentationGroup::new)) {
            AbstractBytecodeNodeElement.InstrumentationGroup group = groupEntry.getKey();
            if (!group.instrumentation) {
                // only instrumentation instructions should be reached
                continue;
            }
            List<InstructionModel> instructions = groupEntry.getValue();
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startBlock();

            if (group.tagInstrumentation) {
                b.startDeclaration(type(int.class), "opTags");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), "newTagNodes", BytecodeRootNodeElement.readImmediate("newBc", "newBci", group.tagNodeImmediate)));
                b.string(".tags");
                b.end(); // declaration
                b.startIf().string("searchOp == op && searchTags == opTags").end().startBlock();
                b.statement("opCounter--");
                b.end();
            } else {
                b.startIf().string("searchOp == op").end().startBlock();
                b.statement("opCounter--");
                b.end();
            }

            b.statement("newBci += " + group.instructionLength);
            b.statement("break");
            b.end();
        }
        b.caseDefault().startCaseBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
        b.end(); // default block
        b.end(); // switch block
        b.end(); // while block

        b.startReturn().string("newBci").end();

        return ex;
    }

    private CodeExecutableElement createComputeNewBci() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL, STATIC), type(int.class), "computeNewBci");
        ex.addParameter(new CodeVariableElement(type(int.class), "oldBci"));
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "oldBc"));
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "newBc"));
        if (parent.model.enableTagInstrumentation) {
            ex.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "oldTagNodes"));
            ex.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "newTagNodes"));
        }
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int.class), "stableBci", "toStableBytecodeIndex(oldBc, oldBci)");
        b.declaration(type(int.class), "newBci", "fromStableBytecodeIndex(newBc, stableBci)");
        b.declaration(type(int.class), "oldBciBase", "fromStableBytecodeIndex(oldBc, stableBci)");

        b.startIf().string("oldBci != oldBciBase").end().startBlock();
        b.lineComment("Transition within an in instrumentation bytecode.");
        b.lineComment("Needs to compute exact location where to continue.");
        b.startAssign("newBci");
        b.startCall("transitionInstrumentationIndex").string("oldBc").string("oldBciBase").string("oldBci").string("newBc").string("newBci");
        if (parent.model.enableTagInstrumentation) {
            b.string("oldTagNodes").string("newTagNodes");
        }
        b.end(); // call
        b.end(); // assign
        b.end(); // if block

        b.startReturn().string("newBci").end();

        return ex;
    }

    record SearchGroup(int instructionLength, boolean instrumentation) implements Comparable<AbstractBytecodeNodeElement.SearchGroup> {
        SearchGroup(InstructionModel instr) {
            this(instr.getInstructionLength(), instr.isInstrumentation());
        }

        // needs a deterministic ordering after grouping
        @Override
        public int compareTo(AbstractBytecodeNodeElement.SearchGroup o) {
            int compare = Boolean.compare(this.instrumentation, o.instrumentation);
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(this.instructionLength, o.instructionLength);
            if (compare != 0) {
                return compare;
            }
            return 0;
        }
    }

    /**
     * This function emits the code to map an internal bci to/from a stable value (e.g., a stable
     * bci or instruction index).
     * <p>
     * Assumes the bytecode is stored in a variable {@code bc}.
     *
     * @param b the builder
     * @param targetVariable the name of the variable storing the "target" value to map from.
     * @param stableVariable the name of the variable storing the "stable" value.
     * @param stableIncrement produces a numeric value to increment the stable variable by.
     * @param toStableValue whether to return the stable value or the internal bci.
     */
    private void emitStableBytecodeSearch(CodeTreeBuilder b, String targetVariable, String stableVariable, boolean toStableValue) {
        b.declaration(type(int.class), "bci", "0");
        b.declaration(type(int.class), stableVariable, "0");

        String resultVariable;
        String searchVariable;
        if (toStableValue) {
            resultVariable = stableVariable;
            searchVariable = "bci";
        } else {
            resultVariable = "bci";
            searchVariable = stableVariable;
        }

        b.startWhile().string(searchVariable, " != ", targetVariable, " && bci < bc.length").end().startBlock();
        b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();

        for (var groupEntry : groupInstructionsSortedBy(AbstractBytecodeNodeElement.SearchGroup::new)) {
            AbstractBytecodeNodeElement.SearchGroup group = groupEntry.getKey();
            List<InstructionModel> instructions = groupEntry.getValue();

            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            if (group.instrumentation) {
                b.statement("bci += " + group.instructionLength);
            } else {
                b.statement("bci += " + group.instructionLength);
                b.statement(stableVariable + " += " + group.instructionLength);
            }
            b.statement("break");
            b.end();
        }

        b.caseDefault().startCaseBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Invalid bytecode."));
        b.end();

        b.end(); // switch

        b.end(); // while

        b.startIf().string("bci >= bc.length").end().startBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Could not translate bytecode index."));
        b.end();

        b.startReturn().string(resultVariable).end();
    }

    private <T extends Comparable<T>> List<Entry<T, List<InstructionModel>>> groupInstructionsSortedBy(Function<InstructionModel, T> constructor) {
        return parent.model.getInstructions().stream()//
                        .collect(BytecodeRootNodeElement.deterministicGroupingBy(constructor)).entrySet() //
                        .stream().sorted(Comparator.comparing(e -> e.getKey())).toList();
    }

    private CodeExecutableElement createToStableBytecodeIndex() {
        CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "toStableBytecodeIndex");
        translate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "bc"));
        translate.addParameter(new CodeVariableElement(type(int.class), "searchBci"));
        emitStableBytecodeSearch(translate.createBuilder(), "searchBci", "stableBci", true);
        return translate;
    }

    private CodeExecutableElement createFromStableBytecodeIndex() {
        CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "fromStableBytecodeIndex");
        translate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "bc"));
        translate.addParameter(new CodeVariableElement(type(int.class), "stableSearchBci"));
        emitStableBytecodeSearch(translate.createBuilder(), "stableSearchBci", "stableBci", false);
        return translate;
    }

    private CodeExecutableElement createInvalidate() {
        CodeExecutableElement invalidate = new CodeExecutableElement(Set.of(FINAL), type(void.class), "invalidate");
        invalidate.addParameter(new CodeVariableElement(this.asType(), "newNode"));
        invalidate.addParameter(new CodeVariableElement(type(CharSequence.class), "reason"));
        CodeTreeBuilder b = invalidate.createBuilder();

        b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
        b.declaration(type(int.class), "bci", "0");
        if (parent.model.hasYieldOperation()) {
            b.declaration(type(int.class), "continuationIndex", "0");
        }

        b.startAssign("this.oldBytecodes").startStaticCall(type(Arrays.class), "copyOf").string("bc").string("bc.length").end().end();

        b.startStatement().startStaticCall(type(VarHandle.class), "loadLoadFence").end().end();

        b.startWhile().string("bci < bc.length").end().startBlock();
        b.declaration(type(short.class), "op", BytecodeRootNodeElement.readInstruction("bc", "bci"));
        b.startSwitch().string("op").end().startBlock();

        for (List<InstructionModel> instructions : BytecodeRootNodeElement.groupInstructionsByLength(parent.model.getInstructions())) {
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            int length = instructions.getFirst().getInstructionLength();
            InstructionModel invalidateInstruction = parent.model.getInvalidateInstruction(length);
            parent.emitInvalidateInstruction(b, "this", "bc", "bci", CodeTreeBuilder.singleString("op"), parent.createInstructionConstant(invalidateInstruction));
            b.statement("bci += " + length);
            b.statement("break");
            b.end();
        }

        b.end();
        b.end(); // switch
        b.end(); // while

        b.statement("reportReplace(this, newNode, reason)");

        return invalidate;
    }

    private CodeExecutableElement createAdoptNodesAfterUpdate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "adoptNodesAfterUpdate");
        CodeTreeBuilder b = ex.createBuilder();
        b.lineComment("no nodes to adopt");
        return ex;
    }

    private CodeExecutableElement createGetSourceLocation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceLocation", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
        ex.getModifiers().add(FINAL);
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");

        b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
        b.startIf().string("info == null").end().startBlock();
        b.startReturn().string("null").end();
        b.end();

        b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");

        b.startIf().string("startBci <= bci && bci < endBci").end().startBlock();
        b.startReturn().string("createSourceSection(sources, info, i)").end();
        b.end();

        b.end();

        b.startReturn().string("null").end();
        return ex;
    }

    private CodeExecutableElement createGetSourceLocations() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceLocations", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
        ex.getModifiers().add(FINAL);
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");

        b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
        b.startIf().string("info == null").end().startBlock();
        b.startReturn().string("null").end();
        b.end();

        b.declaration(type(int.class), "sectionIndex", "0");
        b.startDeclaration(arrayOf(types.SourceSection), "sections").startNewArray(arrayOf(types.SourceSection), CodeTreeBuilder.singleString("8")).end().end();

        b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
        b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
        b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");

        b.startIf().string("startBci <= bci && bci < endBci").end().startBlock();

        b.startIf().string("sectionIndex == sections.length").end().startBlock();
        b.startAssign("sections").startStaticCall(type(Arrays.class), "copyOf");
        b.string("sections");
        // Double the size of the array, but cap it at the number of source section entries.
        b.startStaticCall(type(Math.class), "min").string("sections.length * 2").string("info.length / SOURCE_INFO_LENGTH").end();
        b.end(2); // assign
        b.end(); // if

        b.startStatement().string("sections[sectionIndex++] = createSourceSection(sources, info, i)").end();

        b.end(); // if

        b.end(); // for block

        b.startReturn().startStaticCall(type(Arrays.class), "copyOf").string("sections").string("sectionIndex").end().end();
        return ex;
    }

    private CodeExecutableElement createCreateSourceSection() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), types.SourceSection, "createSourceSection");
        ex.addParameter(new CodeVariableElement(generic(List.class, types.Source), "sources"));
        ex.addParameter(new CodeVariableElement(type(int[].class), "info"));
        ex.addParameter(new CodeVariableElement(type(int.class), "index"));

        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(type(int.class), "sourceIndex", "info[index + SOURCE_INFO_OFFSET_SOURCE]");
        b.declaration(type(int.class), "start", "info[index + SOURCE_INFO_OFFSET_START]");
        b.declaration(type(int.class), "length", "info[index + SOURCE_INFO_OFFSET_LENGTH]");

        b.startIf().string("start == -1 && length == -1").end().startBlock();
        b.startReturn().string("sources.get(sourceIndex).createUnavailableSection()").end();
        b.end();

        b.startAssert().string("start >= 0 : ").doubleQuote("invalid source start index").end();
        b.startAssert().string("length >= 0 : ").doubleQuote("invalid source length").end();
        b.startReturn().string("sources.get(sourceIndex).createSection(start, length)").end();
        return ex;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
        b.startIf().string("info == null || info.length == 0").end().startBlock();
        b.startReturn().string("null").end();
        b.end();

        b.declaration(type(int.class), "lastEntry", "info.length - SOURCE_INFO_LENGTH");
        b.startIf();
        b.string("info[lastEntry + SOURCE_INFO_OFFSET_START_BCI] == 0 &&").startIndention().newLine();
        b.string("info[lastEntry + SOURCE_INFO_OFFSET_END_BCI] == bytecodes.length").end();
        b.end().startBlock();
        b.startReturn();
        b.string("createSourceSection(sources, info, lastEntry)");
        b.end();
        b.end(); // if

        b.startReturn().string("null").end();
        return ex;
    }

    private CodeExecutableElement createFindInstruction() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findInstruction", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startNew(parent.instructionImpl.asType());
        b.string("this").string("bci").string("readValidBytecode(this.bytecodes, bci)");
        b.end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createValidateBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "validateBytecodeIndex", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(byte[].class), "bc", "this.bytecodes");
        b.startIf().string("bci < 0 || bci >= bc.length").end().startBlock();
        b.startThrow().startNew(type(IllegalArgumentException.class)).startGroup().doubleQuote("Bytecode index out of range ").string(" + bci").end().end().end();
        b.end();

        b.declaration(type(int.class), "op", "readValidBytecode(bc, bci)");

        b.startIf().string("op <= 0 || op > ").string(parent.model.getInstructions().size() + 1).end().startBlock();
        b.startThrow().startNew(type(IllegalArgumentException.class)).startGroup().doubleQuote("Invalid op at bytecode index ").string(" + op").end().end().end();
        b.end();

        // we could do more validations here, but they would likely be too expensive

        b.returnTrue();
        return ex;
    }

    private CodeExecutableElement createHasSourceInformation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "hasSourceInformation");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return sourceInfo != null");
        return ex;
    }

    private CodeExecutableElement createGetSourceInformation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceInformation");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("sourceInfo == null").end().startBlock();
        b.returnNull();
        b.end();
        b.startReturn();
        b.startNew("SourceInformationList").string("this").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetSourceInformationTree() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceInformationTree");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("sourceInfo == null").end().startBlock();
        b.returnNull();
        b.end();
        b.startReturn();
        b.string("SourceInformationTreeImpl.parse(this)");
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetExceptionHandlers() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getExceptionHandlers");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startNew("ExceptionHandlerList").string("this").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetTagTree() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getTagTree");
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.enableTagInstrumentation) {
            b.startIf().string("this.tagRoot == null").end().startBlock();
            b.statement("return null");
            b.end();
            b.statement("return this.tagRoot.root");
        } else {
            b.statement("return null");
        }
        return ex;
    }

}
