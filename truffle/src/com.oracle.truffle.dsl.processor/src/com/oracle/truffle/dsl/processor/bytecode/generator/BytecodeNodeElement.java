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
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.InstructionGroup;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel.LoadIllegalLocalStrategy;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class BytecodeNodeElement extends AbstractElement {

    private static final String METADATA_FIELD_NAME = "osrMetadata_";
    static final String FORCE_UNCACHED_THRESHOLD = "Integer.MIN_VALUE";
    final InterpreterTier tier;
    final Map<InstructionModel, CodeExecutableElement> instructionSlowPaths = new LinkedHashMap<>();

    BytecodeNodeElement(BytecodeRootNodeElement parent, InterpreterTier tier) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, tier.bytecodeClassName());
        this.tier = tier;
        this.setSuperClass(parent.abstractBytecodeNode.asType());
        this.getAnnotationMirrors().add(new CodeAnnotationMirror(types.DenyReplace));

        emitContinueAt();

        if (tier.isUncached()) {
            this.add(createUncachedConstructor());
            this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "uncachedExecuteCount_"));
        } else if (tier.isCached()) {
            this.add(createCachedConstructor());
            this.add(parent.compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(types.Node), "cachedNodes_")));
            this.add(parent.compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(type(boolean.class)), "exceptionProfiles_")));
            if (parent.model.epilogExceptional != null) {
                this.add(parent.child(new CodeVariableElement(Set.of(PRIVATE), BytecodeRootNodeElement.getCachedDataClassType(parent.model.epilogExceptional.operation.instruction),
                                "epilogExceptionalNode_")));
            }

            if (parent.model.usesBoxingElimination()) {
                this.add(parent.compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(type(byte.class)), "localTags_")));
                if (parent.model.hasYieldOperation()) {
                    this.add(parent.compFinal(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.Assumption, "stableTagsAssumption_")));
                }
            }
            this.add(createLoadConstantCompiled());
            this.add(createAdoptNodesAfterUpdate());
            this.addAll(createBranchProfileMembers());

            // Define the members required to support OSR.
            this.getImplements().add(types.BytecodeOSRNode);
            this.add(createExecuteOSR());
            this.add(createPrepareOSR());
            this.add(createCopyIntoOSRFrame());
            this.addAll(createMetadataMembers());
            this.addAll(createStoreAndRestoreParentFrameMethods());
        } else if (tier.isUninitialized()) {
            this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));
        } else {
            throw new AssertionError("invalid tier");
        }

        this.add(createSetUncachedThreshold());
        this.add(createGetTier());

        if (!tier.isUninitialized()) {
            // uninitialized does not need a copy constructor as the default constructor is
            // already copying.
            this.add(createCopyConstructor());
            this.add(createResolveThrowable());
            this.add(createResolveHandler());

            if (parent.model.epilogExceptional != null) {
                this.add(createDoEpilogExceptional());
            }
            if (parent.model.enableTagInstrumentation) {
                this.add(createDoTagExceptional());
            }
            if (parent.model.interceptControlFlowException != null) {
                this.add(createResolveControlFlowException());
            }
        }

        if (parent.model.usesBoxingElimination()) {
            this.add(createGetLocalTags());
            this.add(createGetLocalValue());
            this.add(createSetLocalValue());

            this.add(createGetLocalValueInternal(type(Object.class)));
            this.add(createSetLocalValueInternal(type(Object.class)));

            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                this.add(createGetLocalValueInternal(boxingType));
                this.add(createSetLocalValueInternal(boxingType));
            }

            if (tier.isCached()) {
                this.add(createSetLocalValueImpl());
                this.add(createSpecializeSlotTag());
                this.add(createGetCachedLocalTag());
            }
            this.add(createGetCachedLocalTagInternal());
            this.add(createSetCachedLocalTagInternal());
            if (parent.model.hasYieldOperation()) {
                this.add(createCheckStableTagsAssumption());
            }
        } else {
            // generated in AbstractBytecodeNode
        }

        this.add(createToCached());
        this.add(createUpdate());
        this.add(createCloneUninitialized());
        this.add(createGetCachedNodes());
        this.add(createGetBranchProfiles());
        this.add(createFindBytecodeIndex1());
        this.add(createFindBytecodeIndex2());
        if (parent.model.storeBciInFrame) {
            this.add(createGetBytecodeIndex());
        } else {
            this.add(createFindBytecodeIndexOfOperationNode());
        }
        this.add(createToString());
    }

    private CodeExecutableElement createExecuteOSR() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "executeOSR",
                        new String[]{"frame", "target", "unused"},
                        new TypeMirror[]{types.VirtualFrame, type(long.class), type(Object.class)});
        CodeTreeBuilder b = ex.getBuilder();

        if (parent.model.hasYieldOperation()) {
            b.declaration(types.FrameWithoutBoxing, "localFrame");
            b.startIf().string(parent.decodeUseContinuationFrame("target")).string(" /* use continuation frame */").end().startBlock();
            b.startAssign("localFrame");
            b.cast(types.FrameWithoutBoxing);
            BytecodeRootNodeElement.startGetFrame(b, "frame", type(Object.class), false).string(BytecodeRootNodeElement.COROUTINE_FRAME_INDEX).end();
            b.end();
            b.end().startElseBlock();
            b.startAssign("localFrame").cast(types.FrameWithoutBoxing).string("frame").end();
            b.end();
        }

        b.startReturn().startCall("continueAt");
        b.string("getRoot()");
        b.startGroup().cast(types.FrameWithoutBoxing).string("frame").end();
        if (parent.model.hasYieldOperation()) {
            b.string("localFrame");
            b.string(parent.clearUseContinuationFrame("target"));
        } else {
            b.string("target");
        }
        b.end(2);

        return ex;
    }

    private CodeExecutableElement createPrepareOSR() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "prepareOSR",
                        new String[]{"target"},
                        new TypeMirror[]{type(long.class)});
        CodeTreeBuilder b = ex.getBuilder();
        b.lineComment("do nothing");
        return ex;
    }

    private CodeExecutableElement createCopyIntoOSRFrame() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "copyIntoOSRFrame",
                        new String[]{"osrFrame", "parentFrame", "target", "targetMetadata"},
                        new TypeMirror[]{types.VirtualFrame, types.VirtualFrame, type(long.class), type(Object.class)});
        CodeTreeBuilder b = ex.getBuilder();
        // default behaviour. we just need to explicitly implement the long overload.
        b.startStatement().startCall("transferOSRFrame");
        b.string("osrFrame");
        b.string("parentFrame");
        b.string("target");
        b.string("targetMetadata");
        b.end(2);
        return ex;
    }

    private List<CodeElement<Element>> createMetadataMembers() {
        CodeVariableElement osrMetadataField = parent.compFinal(new CodeVariableElement(Set.of(PRIVATE), parent.context.getDeclaredType(Object.class), METADATA_FIELD_NAME));

        CodeExecutableElement getOSRMetadata = GeneratorUtils.override(types.BytecodeOSRNode, "getOSRMetadata");
        getOSRMetadata.getBuilder().startReturn().string(METADATA_FIELD_NAME).end();

        CodeExecutableElement setOSRMetadata = GeneratorUtils.override(types.BytecodeOSRNode, "setOSRMetadata", new String[]{"osrMetadata"});
        setOSRMetadata.getBuilder().startAssign(METADATA_FIELD_NAME).string("osrMetadata").end();

        return List.of(osrMetadataField, getOSRMetadata, setOSRMetadata);
    }

    private List<CodeExecutableElement> createStoreAndRestoreParentFrameMethods() {
        // Append parent frame to end of array so that regular argument reads work as expected.
        CodeExecutableElement storeParentFrameInArguments = GeneratorUtils.override(types.BytecodeOSRNode, "storeParentFrameInArguments", new String[]{"parentFrame"});
        CodeTreeBuilder sb = storeParentFrameInArguments.getBuilder();
        sb.declaration(type(Object[].class), "parentArgs", "parentFrame.getArguments()");
        sb.declaration(type(Object[].class), "result", "Arrays.copyOf(parentArgs, parentArgs.length + 1)");
        sb.statement("result[result.length - 1] = parentFrame");
        sb.startReturn().string("result").end();

        CodeExecutableElement restoreParentFrameFromArguments = GeneratorUtils.override(types.BytecodeOSRNode, "restoreParentFrameFromArguments", new String[]{"arguments"});
        CodeTreeBuilder rb = restoreParentFrameFromArguments.getBuilder();
        rb.startReturn().cast(types.Frame).string("arguments[arguments.length - 1]").end();

        return List.of(storeParentFrameInArguments, restoreParentFrameFromArguments);
    }

    private boolean useOperationNodeForBytecodeIndex() {
        return !parent.model.storeBciInFrame && tier == InterpreterTier.CACHED;
    }

    private boolean useFrameForBytecodeIndex() {
        return parent.model.storeBciInFrame || tier == InterpreterTier.UNCACHED;
    }

    private CodeExecutableElement createGetLocalValue() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValue",
                        new String[]{"bci", "frame", "localOffset"},
                        new TypeMirror[]{type(int.class), types.Frame, type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        if (tier.isCached()) {
            b.startTryBlock();
            b.declaration(type(byte.class), "tag");
            b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end().end().startBlock();
            b.lineComment("Resolving the local index is expensive. Don't do it in the interpreter.");
            b.startAssign("tag");
            b.string("frame.getTag(frameIndex)");
            b.end();

            b.end().startElseBlock();

            if (parent.model.enableBlockScoping) {
                b.declaration(type(int.class), "localIndex", "localOffsetToLocalIndex(bci, localOffset)");
                b.startAssign("tag").startCall("getCachedLocalTagInternal");
                b.tree(readLocalTagsFastPath());
                b.string("localIndex");
                b.end().end();
            } else {
                b.startAssign("tag").string("getCachedLocalTag(localOffset)").end();
            }
            b.end();

            b.startSwitch().string("tag").end().startBlock();
            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
                b.startCaseBlock();

                b.startReturn();
                BytecodeRootNodeElement.startExpectFrame(b, "frame", boxingType, false).string("frameIndex").end();
                b.end();
                b.end(); // case block
            }

            b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
            b.startCaseBlock();
            b.startReturn();
            BytecodeRootNodeElement.startExpectFrame(b, "frame", type(Object.class), false).string("frameIndex").end();
            b.end();
            b.end(); // case block

            b.startCase().staticReference(parent.frameTagsElement.getIllegal()).end();
            b.startCaseBlock();
            b.startReturn();
            if (parent.model.loadIllegalLocalStrategy == LoadIllegalLocalStrategy.DEFAULT_VALUE) {
                b.string("DEFAULT_LOCAL_VALUE");
            } else {
                b.string("null");
            }
            b.end();
            b.end(); // case block

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected tag"));
            b.end();

            b.end(); // switch block
            b.end().startCatchBlock(types.UnexpectedResultException, "ex");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startReturn().string("ex.getResult()").end();
            b.end(); // catch
        } else {
            b.startIf().string("frame.isObject(frameIndex)").end().startBlock();
            b.startReturn().string("frame.getObject(frameIndex)").end();
            b.end();
            b.statement("return null");
        }

        return ex;
    }

    private CodeExecutableElement createSetLocalValue() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValue",
                        new String[]{"bci", "frame", "localOffset", "value"},
                        new TypeMirror[]{type(int.class), types.Frame, type(int.class), type(Object.class)});
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert validateBytecodeIndex(bci)");
        AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);
        if (parent.model.usesBoxingElimination() && tier.isCached()) {
            b.startStatement().startCall("setLocalValueImpl");
            b.string("frame").string("localOffset").string("value");
            if (parent.model.enableBlockScoping) {
                b.string("bci");
            }
            b.end().end(); // call, statement
        } else {
            b.startStatement();
            b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("localOffset + " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX).string("value").end();
            b.end();
        }
        return ex;
    }

    private CodeExecutableElement createSetLocalValueImpl() {
        if (!parent.model.usesBoxingElimination() || !tier.isCached()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setLocalValueImpl");
        ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
        ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
        ex.addParameter(new CodeVariableElement(type(Object.class), "value"));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int.class), "frameIndex", "localOffset + " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX);

        if (parent.model.enableBlockScoping) {
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            b.declaration(type(int.class), "localIndex", "localOffsetToLocalIndex(bci, localOffset)");
            b.declaration(type(byte.class), "oldTag", "getCachedLocalTagInternal(this.localTags_, localIndex)");
        } else {
            b.declaration(type(byte.class), "oldTag", "getCachedLocalTag(localOffset)");
        }
        b.declaration(type(byte.class), "newTag");

        b.startSwitch().string("oldTag").end().startBlock();

        for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
            b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
            b.startCaseBlock();
            String primitiveValue = boxingType.toString().toLowerCase() + "Value";
            b.startIf().instanceOf("value", ElementUtils.boxType(boxingType), primitiveValue).end().startBlock();
            b.startStatement();
            b.startCall("frame", BytecodeRootNodeElement.getSetMethod(boxingType)).string("frameIndex").string(primitiveValue).end();
            b.end(); // statement
            b.statement("return");
            b.end(); // if block
            b.startElseBlock();
            b.startAssign("newTag").staticReference(parent.frameTagsElement.getObject()).end();
            b.end();
            b.statement("break");
            b.end(); // case block
        }

        b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
        b.startCaseBlock();
        b.startStatement();
        b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
        b.end();
        b.statement("return");
        b.end(); // case block
        b.caseDefault().startCaseBlock();
        b.startAssign("newTag").string("specializeSlotTag(value)").end();
        b.statement("break");
        b.end();
        b.end(); // switch block

        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

        if (parent.model.enableBlockScoping) {
            b.statement("setCachedLocalTagInternal(this.localTags_, localIndex, newTag)");
            b.statement("setLocalValueImpl(frame, localOffset, value, bci)");
        } else {
            b.statement("setCachedLocalTagInternal(this.localTags_, localOffset, newTag)");
            b.statement("setLocalValueImpl(frame, localOffset, value)");
        }

        return ex;
    }

    private CodeExecutableElement createSetLocalValueInternal(TypeMirror specializedType) {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }
        boolean generic = ElementUtils.isObject(specializedType);
        String suffix;
        if (ElementUtils.isObject(specializedType)) {
            suffix = "";
        } else {
            suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
        }

        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal" + suffix,
                        new String[]{"frame", "localOffset", "localIndex", "value"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class), specializedType});
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.localAccessorsUsed.isEmpty()) {
            BytecodeRootNodeElement.emitThrowIllegalStateException(ex, b, "method should not be reached");
            return ex;
        }

        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);
        if (tier.isCached()) {
            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");

            b.startDeclaration(type(byte.class), "oldTag");
            b.startCall("getCachedLocalTag");
            b.string("localIndex");
            b.end(); // call
            b.end(); // declaration

            b.declaration(type(byte.class), "newTag");

            b.startSwitch().string("oldTag").end().startBlock();

            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                if (!generic && !ElementUtils.typeEquals(boxingType, specializedType)) {
                    continue;
                }

                b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
                b.startCaseBlock();

                if (generic) {
                    String primitiveValue = boxingType.toString().toLowerCase() + "Value";
                    b.startIf().instanceOf("value", ElementUtils.boxType(boxingType), primitiveValue).end().startBlock();
                    b.startStatement();
                    b.startCall("frame", BytecodeRootNodeElement.getSetMethod(boxingType)).string("frameIndex").string(primitiveValue).end();
                    b.end(); // statement
                    b.statement("return");
                    b.end(); // if block
                    b.startElseBlock();
                    b.startAssign("newTag").staticReference(parent.frameTagsElement.getObject()).end();
                    b.end();
                    b.statement("break");
                } else {
                    b.startStatement();
                    b.startCall("frame", BytecodeRootNodeElement.getSetMethod(boxingType)).string("frameIndex").string("value").end();
                    b.end(); // statement
                    b.statement("return");
                }
                b.end(); // case block
            }

            b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
            b.startCaseBlock();
            b.startStatement();
            b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
            b.end();
            b.statement("return");
            b.end(); // case block
            b.caseDefault().startCaseBlock();
            b.startAssign("newTag").string("specializeSlotTag(value)").end();
            b.statement("break");
            b.end();
            b.end(); // switch block

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startStatement().startCall("setCachedLocalTagInternal");
            b.tree(readLocalTagsFastPath());
            b.string("localIndex");
            b.string("newTag");
            b.end(2);
            b.statement("setLocalValueInternal(frame, localOffset, localIndex, value)");

        } else {
            b.startStatement();
            b.startCall("frame", BytecodeRootNodeElement.getSetMethod(type(Object.class))).string("USER_LOCALS_START_INDEX + localOffset").string("value").end();
            b.end();
        }
        return ex;
    }

    private CodeExecutableElement createGetLocalValueInternal(TypeMirror specializedType) {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }

        boolean generic = ElementUtils.isObject(specializedType);
        String suffix;
        if (generic) {
            suffix = "";
        } else {
            suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
        }

        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValueInternal" + suffix,
                        new String[]{"frame", "localOffset", "localIndex"},
                        new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
        ex.getModifiers().add(FINAL);

        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.localAccessorsUsed.isEmpty()) {
            BytecodeRootNodeElement.emitThrowIllegalStateException(ex, b, "method should not be reached");
            return ex;
        }

        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

        b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
        if (parent.model.loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION) {
            b.startIf();
            b.startCall("frame", "getTag").string("frameIndex").end();
            b.string(" == ");
            b.staticReference(parent.frameTagsElement.getIllegal());
            b.end().startBlock();
            emitThrowIllegalLocalException(parent.model, b, null, CodeTreeBuilder.singleString("this"), CodeTreeBuilder.singleString("localIndex"), true);
            b.end();
        }

        if (tier.isCached()) {
            if (generic) {
                b.startTryBlock();
                b.startDeclaration(type(byte.class), "tag").startCall("getCachedLocalTag");
                b.string("localIndex");
                b.end(2);

                b.startSwitch().string("tag").end().startBlock();
                for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                    b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
                    b.startCaseBlock();

                    b.startReturn();
                    BytecodeRootNodeElement.startExpectFrame(b, "frame", boxingType, false).string("frameIndex").end();
                    b.end();
                    b.end(); // case block
                }

                b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startReturn();
                BytecodeRootNodeElement.startExpectFrame(b, "frame", type(Object.class), false).string("frameIndex").end();
                b.end();
                b.end(); // case block

                b.startCase().staticReference(parent.frameTagsElement.getIllegal()).end();
                b.startCaseBlock();
                if (parent.model.loadIllegalLocalStrategy == LoadIllegalLocalStrategy.DEFAULT_VALUE) {
                    b.startReturn();
                    b.string("DEFAULT_LOCAL_VALUE");
                    b.end();
                } else {
                    b.startThrow().startNew(types.FrameSlotTypeException).end().end();
                }
                b.end(); // case block

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("unexpected tag"));
                b.end();

                b.end(); // switch block
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn().string("ex.getResult()").end();
                b.end(); // catch
            } else {
                b.startReturn();
                BytecodeRootNodeElement.startExpectFrame(b, "frame", specializedType, false).string("frameIndex").end();
                b.end();
            }
        } else {
            if (generic) {
                b.startReturn().string("frame.getObject(frameIndex)").end();
            } else {
                b.declaration(type(Object.class), "value", "frame.getObject(frameIndex)");
                b.startIf().string("value instanceof ").type(ElementUtils.boxType(specializedType)).string(" castValue").end().startBlock();
                b.startReturn().string("castValue").end();
                b.end();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startThrow().startNew(types.UnexpectedResultException).string("value").end().end();
            }
        }

        return ex;
    }

    private CodeExecutableElement createGetCachedLocalTag() {
        if (!parent.model.usesBoxingElimination() || !tier.isCached()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(byte.class), "getCachedLocalTag");
        ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(arrayOf(type(byte.class)), "localTags", readLocalTagsFastPath());
        b.startIf().string("localIndex < 0 || localIndex >= localTags.length").end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Invalid local offset");
        b.end();
        b.startReturn().startCall("getCachedLocalTagInternal");
        b.string("localTags");
        b.string("localIndex");
        b.end(2);

        return ex;
    }

    private CodeExecutableElement createSetCachedLocalTagInternal() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = GeneratorUtils.override(parent.abstractBytecodeNode.setCachedLocalTagInternal);
        CodeTreeBuilder b = ex.createBuilder();

        if (tier.isCached()) {
            b.tree(createNeverPartOfCompilation());
            b.statement(BytecodeRootNodeElement.writeByte("localTags", "localIndex", "tag"));
            // Invalidate call targets.
            b.startStatement().startCall("reportReplace");
            b.string("this").string("this").doubleQuote("local tags updated");
            b.end(2);
            if (parent.model.usesBoxingElimination() && parent.model.hasYieldOperation()) {
                // Invalidate continuation call targets.
                b.declaration(types.Assumption, "oldStableTagsAssumption", "this.stableTagsAssumption_");
                b.startIf().string("oldStableTagsAssumption != null").end().startBlock();
                b.startAssign("this.stableTagsAssumption_").startStaticCall(types.Assumption, "create");
                b.doubleQuote("Stable local tags");
                b.end(2);
                b.startStatement().startCall("oldStableTagsAssumption.invalidate").doubleQuote("local tags updated").end(2);
                b.end(); // if
            }
        } else {
            // nothing to do
        }
        return ex;
    }

    private CodeExecutableElement createGetCachedLocalTagInternal() {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = GeneratorUtils.override(parent.abstractBytecodeNode.getCachedLocalTagInternal);
        CodeTreeBuilder b = ex.createBuilder();

        if (tier.isCached()) {
            b.startReturn();
            b.string(BytecodeRootNodeElement.readByte("localTags", "localIndex"));
            b.end();
        } else {
            b.startReturn().staticReference(parent.frameTagsElement.getObject()).end();
        }
        return ex;
    }

    private CodeExecutableElement createCheckStableTagsAssumption() {
        if (!parent.model.usesBoxingElimination() || !parent.model.hasYieldOperation()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = GeneratorUtils.override(parent.abstractBytecodeNode.checkStableTagsAssumption);
        CodeTreeBuilder b = ex.createBuilder();

        if (tier.isCached()) {
            b.startReturn().string("this.stableTagsAssumption_.isValid()").end();
        } else {
            b.startReturn().string("true").end();
        }
        return ex;
    }

    private CodeExecutableElement createSpecializeSlotTag() {
        if (!parent.model.usesBoxingElimination() || !tier.isCached()) {
            throw new AssertionError("Not supported.");
        }

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(byte.class), "specializeSlotTag");
        ex.addParameter(new CodeVariableElement(type(Object.class), "value"));
        CodeTreeBuilder b = ex.createBuilder();
        boolean elseIf = false;
        for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
            elseIf = b.startIf(elseIf);
            b.string("value instanceof ").type(ElementUtils.boxType(boxingType)).end().startBlock();
            b.startReturn().staticReference(parent.frameTagsElement.get(boxingType)).end();
            b.end();
        }
        b.startElseBlock();
        b.startReturn().staticReference(parent.frameTagsElement.getObject()).end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createFindBytecodeIndex1() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                        new String[]{"frameInstance"}, new TypeMirror[]{types.FrameInstance});
        CodeTreeBuilder b = ex.createBuilder();

        if (useOperationNodeForBytecodeIndex()) {
            b.declaration(types.Node, "prev", "null");
            b.startFor().string("Node current = frameInstance.getCallNode(); current != null; current = current.getParent()").end().startBlock();
            b.startIf().string("current == this && prev != null").end().startBlock();
            b.statement("return findBytecodeIndexOfOperationNode(prev)");
            b.end();
            b.statement("prev = current");
            b.end();
            b.startReturn().string("-1").end();
        } else if (useFrameForBytecodeIndex()) {
            CodeTree getFrame = CodeTreeBuilder.createBuilder() //
                            .startCall("frameInstance", "getFrame") //
                            .staticReference(types.FrameInstance_FrameAccess, "READ_ONLY") //
                            .end().build();
            if (parent.model.hasYieldOperation()) {
                /**
                 * If the frame is from a continuation, the bci will be in the locals frame, which
                 * is stored in slot COROUTINE_FRAME_INDEX.
                 */
                b.declaration(types.Frame, "frame", getFrame);

                if (parent.model.loadIllegalLocalStrategy == LoadIllegalLocalStrategy.DEFAULT_VALUE) {
                    b.declaration(type(Object.class), "coroutineFrame", "frame.getObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")");
                    b.startIf().string("coroutineFrame != DEFAULT_LOCAL_VALUE").end().end().startBlock();
                    b.startAssign("frame").cast(types.Frame).string("coroutineFrame").end();
                    b.end();
                } else {
                    b.startIf().string("frame.isObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")").end().end().startBlock();
                    b.startAssign("frame").cast(types.Frame).string("frame.getObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")").end();
                    b.end();
                }

                b.startReturn();
                b.startCall("frame", "getInt");
                b.string(BytecodeRootNodeElement.BCI_INDEX);
                b.end(2);
            } else {
                b.startReturn();
                b.startCall(getFrame, "getInt");
                b.string(BytecodeRootNodeElement.BCI_INDEX);
                b.end(2);
            }
        } else {
            b.startReturn().string("-1").end();
        }

        return parent.withTruffleBoundary(ex);
    }

    private CodeExecutableElement createFindBytecodeIndex2() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                        new String[]{"frame", "node"}, new TypeMirror[]{types.Frame, types.Node});
        CodeTreeBuilder b = ex.createBuilder();

        if (useOperationNodeForBytecodeIndex()) {
            b.startIf().string("node != null").end().startBlock();
            b.statement("return findBytecodeIndexOfOperationNode(node)");
            b.end();
            b.startReturn().string("-1").end();
        } else if (useFrameForBytecodeIndex()) {
            b.startReturn().string("frame.getInt(" + BytecodeRootNodeElement.BCI_INDEX + ")").end();
        } else {
            b.startReturn().string("-1").end();
        }

        return ex;
    }

    private CodeExecutableElement createGetBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getBytecodeIndex", new String[]{"frame"}, new TypeMirror[]{types.Frame});
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("frame.getInt(" + BytecodeRootNodeElement.BCI_INDEX + ")").end();
        return ex;
    }

    private CodeExecutableElement createGetLocalTags() {
        CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) parent.abstractBytecodeNode.asType(), "getLocalTags");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        switch (tier) {
            case UNCACHED:
            case UNINITIALIZED:
                b.string("null");
                break;
            case CACHED:
                b.string("this.localTags_");
                break;
        }
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetCachedNodes() {
        CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) parent.abstractBytecodeNode.asType(), "getCachedNodes");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        switch (tier) {
            case UNCACHED:
            case UNINITIALIZED:
                b.string("null");
                break;
            case CACHED:
                b.string("this.cachedNodes_");
                break;
        }
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetBranchProfiles() {
        CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) parent.abstractBytecodeNode.asType(), "getBranchProfiles");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        switch (tier) {
            case UNCACHED:
            case UNINITIALIZED:
                b.string("null");
                break;
            case CACHED:
                b.string("this.branchProfiles_");
                break;
        }
        b.end();
        return ex;
    }

    private CodeExecutableElement createCloneUninitialized() {
        CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) parent.abstractBytecodeNode.asType(), "cloneUninitialized");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startNew(tier.friendlyName + "BytecodeNode");
        for (VariableElement var : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
            b.startGroup();
            if (var.getSimpleName().contentEquals("tagRoot")) {
                b.string("tagRoot != null ? ").cast(parent.tagRootNode.asType()).string("tagRoot.deepCopy() : null");
            } else if (var.getSimpleName().contentEquals("bytecodes")) {
                if (tier.isCached() && parent.cloneUninitializedNeedsUnquickenedBytecode()) {
                    b.startCall("unquickenBytecode").string("this.bytecodes").end();
                } else {
                    b.startStaticCall(type(Arrays.class), "copyOf");
                    b.string("this.bytecodes").string("this.bytecodes.length");
                    b.end();
                }
            } else {
                b.string("this.", var.getSimpleName().toString());
            }
            b.end();
        }

        if (tier.isCached() && parent.model.usesBoxingElimination()) {
            b.string("createCachedTags(this.localTags_.length)");
        }
        b.end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createToCached() {
        CodeExecutableElement ex = GeneratorUtils.override(ElementUtils.findInstanceMethod(parent.abstractBytecodeNode, "toCached", null));
        CodeTreeBuilder b = ex.createBuilder();
        switch (tier) {
            case UNCACHED:
            case UNINITIALIZED:
                if (parent.model.isBytecodeUpdatable()) {
                    b.startDeclaration(parent.abstractBytecodeNode.asType(), "cachedNode");
                } else {
                    b.startReturn();
                }
                b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
                for (VariableElement var : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
                    b.string("this.", var.getSimpleName().toString());
                }
                if (parent.model.usesBoxingElimination()) {
                    b.string("createCachedTags(numLocals)");
                }
                b.end();

                if (parent.model.isBytecodeUpdatable()) {
                    b.end();
                    b.startAssign("cachedNode.oldBytecodesBox").string("this.allocateOldBytecodesBox()").end();
                    b.startReturn().string("cachedNode").end();
                } else {
                    b.end();
                }
                break;
            case CACHED:
                b.startReturn().string("this").end();
                break;
        }

        return ex;
    }

    private CodeExecutableElement createCopyConstructor() {
        CodeExecutableElement ex = new CodeExecutableElement(null, this.getSimpleName().toString());
        CodeTreeBuilder b = ex.createBuilder();

        b.startStatement();
        b.startSuperCall();
        for (VariableElement var : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
            String name = var.getSimpleName().toString();
            ex.addParameter(new CodeVariableElement(var.asType(), name));
            b.string(name);
        }
        b.end();
        b.end();

        for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
            if (var.getModifiers().contains(STATIC)) {
                continue;
            }
            String name = var.getSimpleName().toString();
            ex.addParameter(new CodeVariableElement(var.asType(), name));
            b.statement("this.", name, " = ", name);
        }
        if (parent.model.isBytecodeUpdatable()) {
            ex.addParameter(new CodeVariableElement(parent.oldBytecodesBoxElement.asType(), "oldBytecodesBox_"));
            b.statement("this.oldBytecodesBox = oldBytecodesBox_");
        }

        return ex;
    }

    private CodeExecutableElement createUpdate() {
        CodeExecutableElement ex = GeneratorUtils.override(ElementUtils.findInstanceMethod(parent.abstractBytecodeNode, "update", null));
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("assert bytecodes_ != null || sourceInfo_ != null");

        for (VariableElement e : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
            if (e.getModifiers().contains(STATIC)) {
                continue;
            }
            b.declaration(e.asType(), e.getSimpleName().toString() + "__");
        }

        b.startIf().string("bytecodes_ != null").end().startBlock();
        if (parent.model.isBytecodeUpdatable()) {
            b.statement("bytecodes__ = bytecodes_");
            b.statement("constants__ = constants_");
            b.statement("handlers__ = handlers_");
            b.statement("numNodes__ = numNodes_");
            b.statement("locals__ = locals_");

            if (parent.model.enableTagInstrumentation) {
                b.statement("tagRoot__ = tagRoot_");
            }

        } else {
            b.tree(GeneratorUtils.createShouldNotReachHere("The bytecode is not updatable for this node."));
        }
        b.end().startElseBlock();
        b.statement("bytecodes__ = this.bytecodes");
        b.statement("constants__ = this.constants");
        b.statement("handlers__ = this.handlers");
        b.statement("numNodes__ = this.numNodes");
        b.statement("locals__ = this.locals");

        if (parent.model.enableTagInstrumentation) {
            b.statement("tagRoot__ = this.tagRoot");
        }

        b.end();

        b.startIf().string("sourceInfo_ != null").end().startBlock();
        b.statement("sourceInfo__ = sourceInfo_");
        b.statement("sources__ = sources_");
        b.end().startElseBlock();
        b.statement("sourceInfo__ = this.sourceInfo");
        b.statement("sources__ = this.sources");
        b.end();

        if (tier.isCached()) {
            b.startIf().string("bytecodes_ != null").end().startBlock();
            b.lineComment("Can't reuse profile if bytecodes are changed.");
            b.startReturn();
            b.startNew(this.asType());
            for (VariableElement e : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
                if (e.getModifiers().contains(STATIC)) {
                    continue;
                }
                b.string(e.getSimpleName().toString() + "__");
            }
            if (parent.model.usesBoxingElimination()) {
                b.string("this.localTags_");
            }
            b.end();
            b.end();
            b.end().startElseBlock();
            /**
             * NOTE: When we reuse cached nodes, they get adopted *without* invalidation. Code that
             * relies on the identity of the BytecodeNode parent (e.g., source location
             * computations) should *not* be on compiled code paths and instead be placed behind a
             * boundary.
             */
            b.lineComment("Can reuse profile if bytecodes are unchanged.");
            b.startReturn();
            b.startNew(this.asType());
            for (VariableElement e : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
                if (e.getModifiers().contains(STATIC)) {
                    continue;
                }
                b.string(e.getSimpleName().toString() + "__");
            }
            for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                if (e.getModifiers().contains(STATIC)) {
                    continue;
                }
                b.string("this.", e.getSimpleName().toString());
            }
            if (parent.model.isBytecodeUpdatable()) {
                b.string("this.allocateOldBytecodesBox()");
            }
            b.end();
            b.end();

        } else {
            b.startReturn();
            b.startNew(this.asType());
            for (VariableElement e : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "__");
            }
            for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                b.string("this.", e.getSimpleName().toString());
            }
            if (tier.isUncached() && parent.model.isBytecodeUpdatable()) {
                // If the bytecode is being reused, allocate the shared box.
                b.string("bytecodes_ == null ? this.allocateOldBytecodesBox() : null");
            }
            b.end();
            b.end();
        }

        b.end(); // else
        return ex;
    }

    private CodeExecutableElement createGetTier() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getTier");
        CodeTreeBuilder b = ex.createBuilder();
        switch (tier) {
            case UNCACHED:
            case UNINITIALIZED:
                b.startReturn().staticReference(types.BytecodeTier, "UNCACHED").end();
                break;
            case CACHED:
                b.startReturn().staticReference(types.BytecodeTier, "CACHED").end();
                break;
        }

        return ex;
    }

    record LengthAndNodeIndex(int length, int nodeIndex) {
    }

    private CodeExecutableElement createFindBytecodeIndexOfOperationNode() {
        CodeExecutableElement ex = new CodeExecutableElement(type(int.class), "findBytecodeIndexOfOperationNode");
        ex.addParameter(new CodeVariableElement(types.Node, "operationNode"));

        CodeTreeBuilder b = ex.createBuilder();
        if (!tier.isCached()) {
            b.startReturn().string("-1").end();
            return ex;
        }

        boolean hasNodeImmediate = false;
        for (InstructionModel instr : parent.model.getInstructions()) {
            if (instr.hasNodeImmediate()) {
                hasNodeImmediate = true;
                break;
            }
        }
        if (!hasNodeImmediate) {
            b.lineComment("No operation node exposed.");
            b.startReturn().string("-1").end();
            return ex;
        }

        b.startAssert().string("operationNode.getParent() == this : ").doubleQuote("Passed node must be an operation node of the same bytecode node.").end();
        b.declaration(arrayOf(types.Node), "localNodes", "this.cachedNodes_");
        b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
        b.statement("int bci = 0");
        b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
        b.declaration(type(int.class), "currentBci", "bci");
        b.declaration(type(int.class), "nodeIndex");
        b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();

        Map<Boolean, List<InstructionModel>> instructionsGroupedByHasNode = parent.model.getInstructions().stream().collect(Collectors.partitioningBy(InstructionModel::hasNodeImmediate));
        Map<Integer, List<InstructionModel>> nodelessGroupedByLength = instructionsGroupedByHasNode.get(false).stream().collect(
                        BytecodeRootNodeElement.deterministicGroupingBy(InstructionModel::getInstructionLength));

        Map<BytecodeNodeElement.LengthAndNodeIndex, List<InstructionModel>> nodedGroupedByLengthAndNodeIndex = instructionsGroupedByHasNode.get(true).stream() //
                        .collect(BytecodeRootNodeElement.deterministicGroupingBy(
                                        insn -> new BytecodeNodeElement.LengthAndNodeIndex(insn.getInstructionLength(), insn.getImmediate(ImmediateKind.NODE_PROFILE).offset())));

        // Skip the nodeless instructions. We group them by size to simplify the generated code.
        for (Map.Entry<Integer, List<InstructionModel>> entry : nodelessGroupedByLength.entrySet()) {
            for (InstructionModel instr : entry.getValue()) {
                b.startCase().tree(parent.createInstructionConstant(instr)).end();
            }
            b.startBlock();
            b.statement("bci += " + entry.getKey());
            b.statement("continue loop");
            b.end();
        }

        // For each noded instruction, read its node index and continue after the switch.
        // We group them by size and node index to simplify the generated code.
        for (Map.Entry<BytecodeNodeElement.LengthAndNodeIndex, List<InstructionModel>> entry : nodedGroupedByLengthAndNodeIndex.entrySet()) {
            for (InstructionModel instr : entry.getValue()) {
                b.startCase().tree(parent.createInstructionConstant(instr)).end();
            }
            InstructionModel representativeInstruction = entry.getValue().get(0);
            InstructionImmediate imm = representativeInstruction.getImmediate(ImmediateKind.NODE_PROFILE);
            b.startBlock();

            b.startStatement().string("nodeIndex = ");
            b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", imm));
            b.end();

            b.statement("bci += " + representativeInstruction.getInstructionLength());
            b.statement("break");
            b.end();
        }

        b.caseDefault().startBlock();
        BytecodeRootNodeElement.emitThrowAssertionError(b, "\"Should not reach here\"");
        b.end();

        b.end(); // } switch

        // nodeIndex is guaranteed to be set, since we continue to the top of the loop when
        // there's
        // no node.
        b.startIf().string("localNodes[nodeIndex] == operationNode").end().startBlock();
        b.startReturn().string("currentBci").end();
        b.end();

        b.end(); // } while

        // Fallback: the node wasn't found.
        b.startReturn().string("-1").end();

        return parent.withTruffleBoundary(ex);

    }

    private CodeExecutableElement createToString() {
        CodeExecutableElement ex = GeneratorUtils.override(parent.context.getDeclaredType(Object.class), "toString");
        CodeTreeBuilder b = ex.createBuilder();
        String tierString = switch (tier) {
            case UNCACHED -> "uncached";
            case UNINITIALIZED -> "uninitialized";
            case CACHED -> "cached";
        };

        b.startReturn();
        b.startStaticCall(type(String.class), "format");
        b.doubleQuote(ElementUtils.getSimpleName(types.BytecodeNode) + " [name=%s, sources=%s, tier=" + tierString + "]");
        b.string("((RootNode) getParent()).getQualifiedName()");
        b.string("this.sourceInfo != null");
        b.end(2);

        return ex;
    }

    private CodeExecutableElement createUncachedConstructor() {
        CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), this);
        CodeTreeBuilder b = ex.appendBuilder();
        if (parent.model.defaultUncachedThresholdExpression.resolveConstant() != null) {
            if (!(parent.model.defaultUncachedThresholdExpression.resolveConstant() instanceof Integer i) || i < 0) {
                // The parser should have validated the type. The expression grammar should not
                // support negative literals like "-42".
                throw new AssertionError();
            }
            b.statement("this.uncachedExecuteCount_ = ", parent.model.defaultUncachedThreshold);
        } else {
            // Constant needs to be validated at run time.
            b.startStatement().startCall("setUncachedThreshold").string(parent.model.defaultUncachedThreshold).end(2);
        }
        return ex;
    }

    private CodeExecutableElement createCachedConstructor() {
        CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), this);
        if (parent.model.usesBoxingElimination()) {
            ex.addParameter(new CodeVariableElement(type(byte[].class), "cachedTags"));
        }

        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);

        CodeTreeBuilder b = ex.appendBuilder();

        b.tree(createNeverPartOfCompilation());
        b.declaration(nodeArrayType, "result", "new Node[this.numNodes]");
        b.statement("byte[] bc = bytecodes");
        b.statement("int bci = 0");
        b.statement("int numConditionalBranches = 0");
        if (parent.model.usesBoxingElimination() && parent.model.hasYieldOperation()) {
            b.statement("boolean hasContinuations = false");
        }

        b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();

        Map<EqualityCodeTree, List<InstructionModel>> caseGrouping = EqualityCodeTree.group(b, parent.model.getInstructions(), (InstructionModel instr, CodeTreeBuilder group) -> {
            group.startCaseBlock();
            for (InstructionImmediate immediate : instr.getImmediates()) {
                switch (immediate.kind()) {
                    case BRANCH_PROFILE:
                        group.statement("numConditionalBranches++");
                        break;
                    case NODE_PROFILE:
                        group.startStatement().string("result[");
                        group.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", immediate)).string("] = ");
                        group.string("insert(new " + BytecodeRootNodeElement.cachedDataClassName(instr) + "())");
                        group.end();
                        break;
                    default:
                        break;
                }
            }

            if (parent.model.usesBoxingElimination() && (instr.kind == InstructionKind.YIELD || instr.operation != null && instr.operation.kind == OperationKind.CUSTOM_YIELD)) {
                if (!parent.model.usesBoxingElimination() || !parent.model.hasYieldOperation()) {
                    throw new AssertionError();
                }
                group.statement("hasContinuations = true");
            }

            group.statement("bci += " + instr.getInstructionLength());
            group.statement("break");
            group.end(); // case block
        });

        b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();
        for (var group : caseGrouping.entrySet()) {
            EqualityCodeTree key = group.getKey();
            for (InstructionModel instruction : group.getValue()) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.tree(key.getTree());
            b.end();
        }

        b.caseDefault().startBlock();
        BytecodeRootNodeElement.emitThrowAssertionError(b, "\"Should not reach here\"");
        b.end();
        b.end(); // switch
        b.end(); // } while

        b.startIf().string("bci != bc.length").end().startBlock();
        BytecodeRootNodeElement.emitThrowAssertionError(b, "\"%d != %d\"", "bci", "bc.length");
        b.end();
        b.startAssign("this.cachedNodes_").string("result").end();
        b.startAssign("this.branchProfiles_").startCall("allocateBranchProfiles").string("numConditionalBranches").end(2);
        b.startAssign("this.exceptionProfiles_").string("handlers.length == 0 ? EMPTY_EXCEPTION_PROFILES : new boolean[handlers.length / 5]").end();

        if (parent.model.epilogExceptional != null) {
            b.startAssign("this.epilogExceptionalNode_").startCall("insert").startNew(
                            BytecodeRootNodeElement.getCachedDataClassType(parent.model.epilogExceptional.operation.instruction)).end().end().end();
        }

        if (parent.model.usesBoxingElimination()) {
            b.statement("this.localTags_ = cachedTags");

            if (parent.model.hasYieldOperation()) {
                b.startAssign("this.stableTagsAssumption_");
                b.string("hasContinuations ? ");
                b.startStaticCall(types.Assumption, "create").doubleQuote("Stable local tags").end();
                b.string(" : null");
                b.end();
            }
        }

        this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(boolean[].class), "EMPTY_EXCEPTION_PROFILES")).createInitBuilder().string("new boolean[0]");
        return ex;
    }

    private CodeExecutableElement createSetUncachedThreshold() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setUncachedThreshold", new String[]{"threshold"}, new TypeMirror[]{type(int.class)});
        ElementUtils.setVisibility(ex.getModifiers(), PUBLIC);
        ex.getModifiers().remove(ABSTRACT);

        CodeTreeBuilder b = ex.createBuilder();
        if (tier.isUncached()) {
            b.tree(createNeverPartOfCompilation());
            b.startIf().string("threshold < 0 && threshold != ", FORCE_UNCACHED_THRESHOLD).end().startBlock();
            BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "threshold cannot be a negative value other than " + FORCE_UNCACHED_THRESHOLD);
            b.end();
            b.startAssign("uncachedExecuteCount_").string("threshold").end();
        } else {
            // do nothing for cached
        }
        return ex;
    }

    private void emitContinueAt() {
        // This method returns a list containing the continueAt method plus helper methods for
        // custom instructions. The helper methods help reduce the bytecode size of the dispatch
        // loop.
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(long.class), "continueAt");
        GeneratorUtils.addOverride(ex);
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));
        ex.addParameter(new CodeVariableElement(parent.asType(), "$root"));
        ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame_"));
        if (parent.model.hasYieldOperation()) {
            ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame_"));
        }
        ex.addParameter(new CodeVariableElement(type(long.class), "startState"));

        this.add(ex);

        CodeTreeBuilder b = ex.createBuilder();
        if (tier.isUninitialized()) {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("$root.transitionToCached()");
            b.startReturn().string("startState").end();
            return;
        }

        b.startDeclaration(types.FrameWithoutBoxing, "frame").startCall("ACCESS.uncheckedCast").string("frame_").typeLiteral(types.FrameWithoutBoxing).end().end();
        if (parent.model.hasYieldOperation()) {
            b.startDeclaration(types.FrameWithoutBoxing, "localFrame").startCall("ACCESS.uncheckedCast").string("localFrame_").typeLiteral(types.FrameWithoutBoxing).end().end();
        }

        if (tier.isUncached()) {
            b.startDeclaration(types.EncapsulatingNodeReference, "encapsulatingNode").startStaticCall(types.EncapsulatingNodeReference, "getCurrent").end().end();
            b.startDeclaration(types.Node, "prev").startCall("encapsulatingNode", "set").string("this").end().end();
            b.startTryBlock();

            b.startIf().string("uncachedExecuteCount_ <= 1").end().startBlock();
            b.startIf().string("uncachedExecuteCount_ != " + FORCE_UNCACHED_THRESHOLD).end().startBlock();
            b.statement("$root.transitionToCached()");

            b.startReturn().string("startState").end();
            b.end(2);
            b.startElseBlock();
            b.statement("uncachedExecuteCount_--");
            b.end();
        }

        b.declaration(arrayOf(type(byte.class)), "bc", BytecodeRootNodeElement.uncheckedCast(type(byte[].class), "this.bytecodes"));
        if (tier.isCached()) {
            ex.addAnnotationMirror(parent.createExplodeLoopAnnotation("MERGE_EXPLODE"));
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyEscapeAnalysis));

            b.declaration(type(int.class), "counter", "0");
            b.declaration(parent.loopCounter.asType(), "loopCounter", "null");
        }

        if (tier.isCached()) {
            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().string(" && ").startStaticCall(types.CompilerDirectives,
                            "hasNextTier").end().end().startBlock();
            b.lineComment("Using a class for the loop counter is a workaround to prevent PE from merging it at the end of the loop.");
            b.lineComment("We need to use a class with PE, in the interpreter we can use a regular counter.");
            b.startAssign("loopCounter").startStaticCall(parent.loopCounter.asType(), "create").end().end();
            b.end();
        }

        b.statement("int bci = ", BytecodeRootNodeElement.decodeBci("startState"));
        if (parent.model.enableStackPointerBoxing) {
            b.startDeclaration(parent.stackPointerElement.asType(), "sp").startNew(parent.stackPointerElement.asType()).string(BytecodeRootNodeElement.decodeSp("startState")).end().end();
        } else {
            b.statement("int sp = ", BytecodeRootNodeElement.decodeSp("startState"));
        }

        if (parent.model.needsBciSlot() && !parent.model.storeBciInFrame && !tier.isUncached()) {
            // If a bci slot is allocated but not used for non-uncached interpreters, set it to
            // an invalid value just in case it gets read during a stack walk.
            b.statement("FRAMES.setInt(" + parent.localFrame() + ", " + BytecodeRootNodeElement.BCI_INDEX + ", -1)");
        }

        b.string("loop: ").startWhile().string("true").end().startBlock();
        b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();
        // filtered instructions
        List<InstructionModel> instructions = parent.model.getInstructions().stream().//
                        filter((i) -> !tier.isUncached() || !i.isQuickening()).//
                        filter((i) -> isInstructionReachable(i)).//
                        toList();

        List<List<InstructionModel>> instructionPartitions = BytecodeRootNodeElement.partitionInstructions(instructions);

        CodeTree op;
        if (parent.model.bytecodeDebugListener || instructionPartitions.size() > 1) {
            b.declaration(type(int.class), "op", BytecodeRootNodeElement.readInstruction("bc", "bci"));
            op = CodeTreeBuilder.singleString("op");
        } else {
            op = BytecodeRootNodeElement.readInstruction("bc", "bci");
        }

        if (parent.model.overridesBytecodeDebugListenerMethod("beforeInstructionExecute")) {
            b.startStatement();
            b.startCall("$root.beforeInstructionExecute");
            parent.emitParseInstruction(b, "this", "bci", op);
            b.end().end();
        }

        b.startTryBlock();
        b.startSwitch();

        if (parent.model.enableThreadedSwitch) {
            b.startStaticCall(types.HostCompilerDirectives, "markThreadedSwitch");
        }
        b.tree(op);
        if (parent.model.enableThreadedSwitch) {
            b.end();
        }

        b.end().startBlock();

        List<InstructionModel> topLevelInstructions = instructionPartitions.get(0);
        Map<Boolean, List<InstructionModel>> groupedInstructions = topLevelInstructions.stream().collect(BytecodeRootNodeElement.deterministicGroupingBy((i) -> isForceCached(tier, i)));
        List<InstructionModel> forceCachedInstructions = groupedInstructions.getOrDefault(Boolean.TRUE, List.of());
        List<InstructionModel> otherTopLevelInstructions = groupedInstructions.getOrDefault(Boolean.FALSE, List.of());

        for (InstructionModel instruction : otherTopLevelInstructions) {
            buildInstructionCaseBlock(b, instruction);
        }

        if (!forceCachedInstructions.isEmpty()) {
            if (!tier.isUncached()) {
                throw new AssertionError();
            }
            for (InstructionModel forceCachedInstruction : forceCachedInstructions) {
                buildInstructionCases(b, forceCachedInstruction);
            }
            b.startBlock();
            b.statement("$root.transitionToCached()");
            b.statement("return ", parent.encodeState("bci", "sp"));
            b.end();
        }

        if (instructionPartitions.size() > 1) {
            Map<InstructionGroup, Integer> groupIndices = new LinkedHashMap<>();
            Map<InstructionGroup, List<InstructionModel>> instructionGroups = new LinkedHashMap<>();
            AtomicInteger index = new AtomicInteger();
            CodeExecutableElement firstContinueAt = null;
            for (int partitionIndex = 1; partitionIndex < instructionPartitions.size(); partitionIndex++) {
                for (InstructionModel instruction : instructionPartitions.get(partitionIndex)) {
                    if (isForceCached(tier, instruction)) {
                        throw new AssertionError("Force cached not supported in non top-level partion.");
                    }
                    InstructionGroup group = new BytecodeRootNodeElement.InstructionGroup(instruction);
                    groupIndices.computeIfAbsent(group, (k) -> index.incrementAndGet());
                    instructionGroups.computeIfAbsent(group, (k) -> new ArrayList<>()).add(instruction);
                }
                boolean hasMorePartitions = (partitionIndex + 1) < instructionPartitions.size();
                CodeExecutableElement continueAt = createPartitionContinueAt(partitionIndex, instructionPartitions.get(partitionIndex), groupIndices, hasMorePartitions);
                this.add(continueAt);
                if (firstContinueAt == null) {
                    firstContinueAt = continueAt;
                }
            }

            b.caseDefault().startCaseBlock();
            b.lineComment("Due to a limit of " + BytecodeRootNodeElement.JAVA_JIT_BYTECODE_LIMIT + " bytecodes for Java JIT compilation");
            b.lineComment("we delegate further bytecodes into a separate method.");
            b.startSwitch().startCall(firstContinueAt.getSimpleName().toString());
            for (VariableElement var : firstContinueAt.getParameters()) {
                b.string(var.getSimpleName().toString());
            }
            b.end().end().startBlock();
            for (var entry : groupIndices.entrySet()) {
                InstructionGroup group = entry.getKey();
                int groupIndex = entry.getValue();
                b.startCase().string(groupIndex).end().startCaseBlock();
                b.statement("bci += " + group.instructionLength());
                emitCustomStackEffect(b, group.stackEffect());
                b.statement("break");
                b.end(); // case block
            }
            b.end(); // switch block
            b.statement("break");
            b.end(); // default case block

        } else {
            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere());
            b.end();
        }

        b.end(); // switch
        if (parent.model.overridesBytecodeDebugListenerMethod("afterInstructionExecute")) {
            b.startStatement();
            b.startCall("$root.afterInstructionExecute");
            parent.emitParseInstruction(b, "this", "bci", op);
            b.string("null");
            b.end().end();
        }
        b.end(); // try

        b.startCatchBlock(type(Throwable.class), "originalThrowable");

        b.startDeclaration(type(long.class), "state");
        BytecodeRootNodeElement.emitCallDefault(b, this.add(createHandleException()), (name, inner) -> {
            switch (name) {
                case "originalBci":
                    inner.string("bci");
                    break;
                case "originalSp":
                    inner.string("sp");
                    break;
                case "counter":
                    inner.string("(");
                    inner.startStaticCall(types.CompilerDirectives, "inCompiledCode").end();
                    inner.string(" && ");
                    inner.startStaticCall(types.CompilerDirectives, "hasNextTier").end();
                    inner.string(" ? loopCounter.value : counter)");
                    break;
                default:
                    inner.string(name);
                    break;
            }
        });
        b.end(); // declaration

        b.statement("bci = " + BytecodeRootNodeElement.decodeBci("state"));
        b.startIf().string("bci == 0xFFFFFFFF").end().startBlock();
        b.statement("return state");
        b.end();
        b.statement("sp = " + BytecodeRootNodeElement.decodeSp("state"));

        b.end(); // catch block

        b.end(); // while (true)

        if (tier.isUncached()) {
            b.end().startFinallyBlock();
            b.startStatement();
            b.startCall("encapsulatingNode", "set").string("prev").end();
            b.end();
            b.end();
        }

        return;
    }

    private static boolean isForceCached(InterpreterTier tier, InstructionModel instruction) {
        return tier.isUncached() && instruction.kind == InstructionKind.CUSTOM && instruction.operation.customModel.forcesCached();
    }

    private CodeExecutableElement createPartitionContinueAt(int partitionIndex, List<InstructionModel> instructionGroup,
                    Map<InstructionGroup, Integer> groupIndices, boolean hasMorePartitions) {
        String methodName = "continueAt_" + partitionIndex;
        CodeExecutableElement continueAtMethod = createInstructionHandler(type(int.class), methodName);

        continueAtMethod.addParameter(new CodeVariableElement(type(int.class), "op"));
        continueAtMethod.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));
        continueAtMethod.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));

        CodeTreeBuilder b = continueAtMethod.createBuilder();

        b.startSwitch().string("op").end().startBlock();
        for (InstructionModel instruction : instructionGroup) {
            int groupIndex = groupIndices.get(new BytecodeRootNodeElement.InstructionGroup(instruction));
            if (instruction.kind != InstructionKind.CUSTOM) {
                throw new AssertionError("Only custom supported in partition.");
            }
            buildInstructionCases(b, instruction);
            b.startCaseBlock();
            emitInstructionCase(b, instruction, CodeTreeBuilder.singleString(String.valueOf(groupIndex)));
            b.startReturn().string(groupIndex).end();
            b.end();
        }

        if (hasMorePartitions) {
            b.caseDefault().startCaseBlock();
            b.startReturn();
            b.startCall("continueAt_" + (partitionIndex + 1));
            for (VariableElement var : continueAtMethod.getParameters()) {
                b.string(var.getSimpleName().toString());
            }
            b.end();
            b.end();
            b.end();
        }
        b.end(); // switch block

        if (!hasMorePartitions) {
            b.returnDefault();
        }
        return continueAtMethod;
    }

    CodeExecutableElement createInstructionHandler(TypeMirror returnType, String name) {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        returnType, name);
        initializeInstructionHandler(method, returnType, name);
        return method;
    }

    void initializeInstructionHandler(CodeExecutableElement method, TypeMirror returnType, String name) {
        method.setVisibility(Modifier.PRIVATE);
        method.setReturnType(returnType);
        method.setSimpleName(CodeNames.of(name));
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        method.addParameter(new CodeVariableElement(type(int.class), "bci"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));
    }

    private CodeExecutableElement createHandleException() {
        CodeExecutableElement method = createInstructionHandler(type(long.class), "handleException");
        method.addParameter(new CodeVariableElement(type(Throwable.class), "originalThrowable"));
        method.findParameter("bci").setSimpleName(CodeNames.of("originalBci"));
        method.findParameter("sp").setSimpleName(CodeNames.of("originalSp"));

        if (tier.isCached()) {
            method.addParameter(new CodeVariableElement(type(int.class), "counter"));
        }

        method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "bci", "originalBci");
        b.declaration(type(int.class), "sp", "originalSp");

        if (BytecodeRootNodeElement.isStoreBciEnabled(parent.model, tier)) {
            storeBciInFrame(b);
        }
        b.declaration(parent.asType(), "root", "getRoot()");

        if (parent.model.overridesBytecodeDebugListenerMethod("afterInstructionExecute")) {
            b.startStatement();
            b.startCall("root.afterInstructionExecute");
            parent.emitParseInstruction(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"));
            b.string("throwable");
            b.end().end();
        }
        /*
         * Three kinds of exceptions are supported: AbstractTruffleException, ControlFlowException,
         * and internal error (anything else). All of these can be intercepted by user-provided
         * hooks.
         *
         * The interception order is ControlFlowException -> internal error ->
         * AbstractTruffleException. An intercept method can produce a new exception that can be
         * intercepted by a subsequent intercept method.
         */
        b.declaration(type(int.class), "targetSp", "sp");
        b.declaration(type(Throwable.class), "throwable", "originalThrowable");
        if (parent.model.interceptControlFlowException != null) {
            b.startIf().string("throwable instanceof ").type(types.ControlFlowException).string(" cfe").end().startBlock();
            b.startTryBlock();
            b.startDeclaration(type(long.class), "target");
            BytecodeRootNodeElement.emitCallDefault(b, this.add(createHandleControlFlowException()));
            b.end();
            emitBeforeReturnProfilingHandler(b);

            b.statement("return target");

            b.end().startCatchBlock(types.ControlFlowException, "rethrownCfe");
            b.startThrow().string("rethrownCfe").end();
            b.end().startCatchBlock(types.AbstractTruffleException, "t");
            b.statement("throwable = t");
            b.end().startCatchBlock(type(Throwable.class), "t");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("throwable = t");
            b.end();
            b.end(); // if
            b.startAssign("throwable").string("resolveThrowable(root, " + parent.localFrame() + ", bci, throwable)").end();
        } else {
            b.startAssign("throwable").string("resolveThrowable(root, " + parent.localFrame() + ", bci, throwable)").end();
        }

        b.startDeclaration(type(int[].class), "handlerTable").string("this.handlers").end();
        b.startDeclaration(type(int.class), "handler").string("-EXCEPTION_HANDLER_LENGTH").end();
        b.startWhile().string("(handler = resolveHandler(bci, handler + EXCEPTION_HANDLER_LENGTH, handlerTable)) != -1").end().startBlock();

        boolean hasSpecialHandler = parent.model.enableTagInstrumentation || parent.model.epilogExceptional != null;

        if (hasSpecialHandler) {
            b.startTryBlock();
            b.startSwitch().string("handlerTable[handler + EXCEPTION_HANDLER_OFFSET_KIND]").end().startBlock();
            if (parent.model.epilogExceptional != null) {
                b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();
                b.startIf().string("throwable instanceof ").type(type(ThreadDeath.class)).end().startBlock();
                b.statement("continue");
                b.end();
                b.startStatement().startCall("doEpilogExceptional");
                b.string("frame");
                if (parent.model.hasYieldOperation()) {
                    b.string("localFrame");
                }
                b.string("bc").string("bci").string("sp");
                b.startGroup().cast(types.AbstractTruffleException);
                b.string("throwable");
                b.end();
                b.string("handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                b.end().end();
                b.statement("throw sneakyThrow(throwable)");
                b.end();
            }
            if (parent.model.enableTagInstrumentation) {
                b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();

                b.declaration(parent.tagNode.asType(), "node", "this.tagRoot.tagNodes[handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]]");
                b.startDeclaration(type(Object.class), "result").startCall("doTagExceptional");
                b.string(parent.localFrame());
                b.string("node");
                b.string("handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                b.string("bc");
                b.string("bci");
                b.string("throwable");
                b.end(2);

                b.startIf().string("result == null").end().startBlock();
                b.startThrow().string("throwable").end();
                b.end();
                b.statement("targetSp = handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + root.maxLocals");
                b.startIf().string("result == ").staticReference(types.ProbeNode, "UNWIND_ACTION_REENTER").end().startBlock();
                b.lineComment("Reenter by jumping to the begin bci.");
                b.statement("bci = node.enterBci");
                b.end().startElseBlock();

                b.startSwitch().string("readValidBytecode(bc, node.returnBci)").end().startBlock();
                for (var entry : parent.model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE).collect(BytecodeRootNodeElement.deterministicGroupingBy((i) -> {
                    if (i.isReturnTypeQuickening()) {
                        return i.signature.returnType();
                    } else {
                        return type(Object.class);
                    }
                })).entrySet()) {
                    int length = -1;
                    for (InstructionModel instruction : entry.getValue()) {
                        b.startCase().tree(parent.createInstructionConstant(instruction)).end();
                        if (length != -1 && instruction.getInstructionLength() != length) {
                            throw new AssertionError("Unexpected length.");
                        }
                        length = instruction.getInstructionLength();
                    }
                    TypeMirror targetType = entry.getKey();
                    b.startCaseBlock();

                    CodeExecutableElement expectMethod = null;
                    if (!ElementUtils.isObject(targetType)) {
                        expectMethod = parent.lookupExpectMethod(parent.parserType, targetType);
                        b.startTryBlock();
                    }

                    b.startStatement();
                    BytecodeRootNodeElement.startSetFrame(b, targetType).string("frame").string("targetSp");
                    if (expectMethod == null) {
                        b.string("result");
                    } else {
                        b.startStaticCall(expectMethod);
                        b.string("result");
                        b.end();
                    }
                    b.end(); // setFrame
                    b.end(); // statement

                    if (!ElementUtils.isObject(targetType)) {
                        b.end().startCatchBlock(types.UnexpectedResultException, "e");
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("targetSp").string("e.getResult()").end();
                        b.end(); // statement
                        b.end(); // catch
                    }

                    b.statement("targetSp = targetSp + 1");
                    b.statement("bci = node.returnBci + " + length);

                    b.statement("break");
                    b.end();
                }
                for (InstructionModel instruction : parent.model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE_VOID).toList()) {
                    b.startCase().tree(parent.createInstructionConstant(instruction)).end();
                    b.startCaseBlock();
                    b.statement("bci = node.returnBci + " + instruction.getInstructionLength());
                    b.lineComment("discard return value");
                    b.statement("break");
                    b.end();
                }
                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere());
                b.end(); // case default
                b.end(); // switch
                b.end();

                b.statement("break");
                b.end();
            }

            b.caseDefault().startCaseBlock();
        }
        b.startIf().string("throwable instanceof ").type(type(ThreadDeath.class)).end().startBlock();
        b.statement("continue");
        b.end();
        b.startAssert().string("throwable instanceof ").type(types.AbstractTruffleException).end();
        b.statement("bci = handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
        b.statement("targetSp = handlerTable[handler + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + root.maxLocals");
        b.statement(BytecodeRootNodeElement.setFrameObject("targetSp - 1", "throwable"));

        if (hasSpecialHandler) {
            b.statement("break");
            b.end(); // case block
            b.end(); // switch
            b.end(); // try
            b.startCatchBlock(type(Throwable.class), "t");
            b.startIf().string("t != throwable").end().startBlock();
            b.statement("throwable = resolveThrowable(root, " + parent.localFrame() + ", bci, t)");
            b.end();
            b.statement("continue");
            b.end();
        }

        /**
         * handlerSp - 1 is the sp before pushing the exception. The current s p should be at or
         * above this height.
         */
        b.statement("assert sp >= targetSp - 1");
        b.startWhile().string("sp > targetSp").end().startBlock();
        b.statement("sp--");
        b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp"));
        b.end();
        b.statement("sp = targetSp");
        b.startReturn().string(parent.encodeState("bci", "sp")).end();
        b.end(); // while

        /**
         * NB: Reporting here ensures loop counts are reported before a guest-language exception
         * bubbles up. Loop counts may be lost when host exceptions are thrown (a compromise to
         * avoid complicating the generated code too much).
         */
        emitBeforeReturnProfilingHandler(b);
        if (parent.model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
            b.startStatement();
            b.startCall("root.afterRootExecute");
            parent.emitParseInstruction(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"));
            b.string("null");
            b.string("throwable");
            b.end();
            b.end();
        }
        b.statement("throw sneakyThrow(throwable)");

        b.end(); // catch
        return method;

    }

    private CodeExecutableElement createHandleControlFlowException() {
        CodeExecutableElement method = createInstructionHandler(type(long.class), "handleControlFlowException");
        method.addParameter(new CodeVariableElement(types.ControlFlowException, "cfe"));
        method.getThrownTypes().add(type(Throwable.class));
        method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(parent.asType(), "root", "getRoot()");

        b.startAssign("Object result").startCall("root", parent.model.interceptControlFlowException).string("cfe").string("frame").string("this").string("bci").end(2);
        // There may not be room above the sp. Just use the first stack slot.
        b.statement(BytecodeRootNodeElement.setFrameObject("root.maxLocals", "result"));
        b.startDeclaration(type(int.class), "newSp").string("root.maxLocals + 1").end();
        b.startReturn().string(BytecodeRootNodeElement.encodeReturnState("(newSp - 1)")).end();
        return method;

    }

    protected static void emitThrowIllegalLocalException(BytecodeDSLModel model, CodeTreeBuilder b, CodeTree bci, CodeTree localBytecodeNode, CodeTree localIndex, boolean fastPath) {
        if (model.loadIllegalLocalStrategy != LoadIllegalLocalStrategy.CUSTOM_EXCEPTION) {
            throw new AssertionError();
        }

        if (fastPath && !ElementUtils.isAssignable(model.illegalLocalException, model.getContext().getTypes().AbstractTruffleException)) {
            // If it's not a Truffle exception, always deopt.
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        }

        var factoryMethod = model.illegalLocalExceptionFactory;
        b.startThrow();
        b.startStaticCall(factoryMethod.method());
        for (var param : factoryMethod.parameters()) {
            switch (param) {
                case NODE, BYTECODE_NODE -> {
                    b.string("this");
                }
                case BYTECODE_LOCATION -> {
                    if (bci == null) {
                        throw new AssertionError();
                    }
                    b.startCall("findLocation").tree(bci).end();
                }
                case LOCAL_VARIABLE -> {
                    b.startNew("LocalVariableImpl");
                    b.tree(localBytecodeNode);
                    b.startGroup();
                    b.startParantheses().tree(localIndex).end();
                    b.string(" * LOCALS_LENGTH");
                    b.end();
                    b.end();
                }
                default -> throw new AssertionError();
            }
        }
        b.end(2);
    }

    private void buildInstructionCases(CodeTreeBuilder b, InstructionModel instruction) {
        b.startCase().tree(parent.createInstructionConstant(instruction)).end();
        if (tier.isUncached()) {
            for (InstructionModel quickendInstruction : instruction.getFlattenedQuickenedInstructions()) {
                b.startCase().tree(parent.createInstructionConstant(quickendInstruction)).end();
            }
        }
    }

    private void buildInstructionCaseBlock(CodeTreeBuilder b, InstructionModel instr) {
        buildInstructionCases(b, instr);
        b.startCaseBlock();
        emitInstructionCase(b, instr, null);
        b.end();
    }

    private void emitInstructionCase(CodeTreeBuilder caseBuilder, InstructionModel instr, CodeTree effectGroup) {
        this.add(new BytecodeInstructionHandler(this, instr, effectGroup).emit(caseBuilder));
    }

    private static boolean isInstructionReachable(InstructionModel model) {
        return !model.isEpilogExceptional();
    }

    private CodeExecutableElement createResolveControlFlowException() {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(long.class), "resolveControlFlowException",
                        new CodeVariableElement(parent.asType(), "$root"),
                        new CodeVariableElement(types.FrameWithoutBoxing, "frame"),
                        new CodeVariableElement(type(int.class), "bci"),
                        new CodeVariableElement(types.ControlFlowException, "cfe"));

        method.getThrownTypes().add(type(Throwable.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startAssign("Object result").startCall("$root", parent.model.interceptControlFlowException).string("cfe").string("frame").string("this").string("bci").end(2);
        // There may not be room above the sp. Just use the first stack slot.
        b.statement(BytecodeRootNodeElement.setFrameObject("$root.maxLocals", "result"));
        b.startDeclaration(type(int.class), "sp").string("$root.maxLocals + 1").end();
        emitReturnTopOfStack(b);
        return method;

    }

    private CodeExecutableElement createResolveThrowable() {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(Throwable.class), "resolveThrowable",
                        new CodeVariableElement(parent.asType(), "$root"),
                        new CodeVariableElement(types.FrameWithoutBoxing, "frame"),
                        new CodeVariableElement(type(int.class), "bci"),
                        new CodeVariableElement(type(Throwable.class), "throwable"));

        method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

        CodeTreeBuilder b = method.createBuilder();

        if (parent.model.interceptTruffleException == null) {
            b.startIf().startGroup().string("throwable instanceof ").type(types.AbstractTruffleException).string(" ate").end(2).startBlock();
            b.startReturn().string("ate").end();
            b.end();
        } else {
            b.declaration(types.AbstractTruffleException, "ex");
            b.startIf().startGroup().string("throwable instanceof ").type(types.AbstractTruffleException).string(" ate").end(2).startBlock();
            b.startAssign("ex").string("ate").end();
            b.end();
        }
        b.startElseIf().startGroup().string("throwable instanceof ").type(types.ControlFlowException).string(" cfe").end(2).startBlock();
        b.startThrow().string("cfe").end();
        b.end();
        if (parent.model.enableTagInstrumentation) {
            b.startElseIf().startGroup().string("throwable instanceof ").type(type(ThreadDeath.class)).string(" cfe").end(2).startBlock();
            b.startReturn().string("cfe").end();
            b.end();
        }

        if (parent.model.interceptInternalException == null) {
            // Special case: no handlers for non-Truffle exceptions. Just rethrow.
            b.startElseBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startThrow().string("sneakyThrow(throwable)").end();
            b.end();
        } else {
            b.startElseBlock();
            b.startTryBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startThrow().startCall("sneakyThrow");
            if (parent.model.interceptInternalException != null) {
                b.startCall("$root", parent.model.interceptInternalException).string("throwable").string("frame").string("this").string("bci").end();
            } else {
                b.string("throwable");
            }
            b.end(2);
            b.end().startCatchBlock(types.AbstractTruffleException, "ate");
            if (parent.model.interceptTruffleException == null) {
                b.startReturn().string("ate").end();
            } else {
                b.startAssign("ex").string("ate").end();
            }
            b.end();
            b.end();
        }

        if (parent.model.interceptTruffleException != null) {
            b.startReturn().startCall("$root", parent.model.interceptTruffleException).string("ex").string("frame").string("this").string("bci").end(2);
        }

        return method;

    }

    private CodeExecutableElement createResolveHandler() {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(int.class), "resolveHandler",
                        new CodeVariableElement(type(int.class), "bci"),
                        new CodeVariableElement(type(int.class), "handler"),
                        new CodeVariableElement(type(int[].class), "localHandlers"));
        method.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));

        if (!tier.isCached()) {
            method.getModifiers().add(STATIC);
        }

        CodeTreeBuilder b = method.createBuilder();

        if (tier.isCached()) {
            b.startFor().string("int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();
        } else {
            b.startFor().string("int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();
        }
        b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci").end().startBlock().statement("continue").end();
        b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci").end().startBlock().statement("continue").end();

        if (tier.isCached()) {
            b.declaration(type(int.class), "handlerEntryIndex", "Math.floorDiv(i, EXCEPTION_HANDLER_LENGTH)");
            b.startIf().string("!this.exceptionProfiles_[handlerEntryIndex]").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("this.exceptionProfiles_[handlerEntryIndex] = true");
            b.end();
        }

        b.statement("return i");

        b.end();

        b.statement("return -1");
        return method;

    }

    private Collection<List<InstructionModel>> groupInstructionsByKindAndImmediates(InstructionModel.InstructionKind... kinds) {
        return parent.model.getInstructions().stream().filter((i) -> {
            for (InstructionKind kind : kinds) {
                if (i.kind == kind) {
                    return true;
                }
            }
            return false;
        }).collect(BytecodeRootNodeElement.deterministicGroupingBy((i -> {
            return i.getImmediates();
        }))).values();
    }

    private CodeExecutableElement createDoEpilogExceptional() {
        InstructionModel instruction = parent.model.epilogExceptional.operation.instruction;
        CodeExecutableElement method = new BytecodeInstructionHandler(this, instruction, null).emit(CodeTreeBuilder.createBuilder());
        method.setSimpleName(CodeNames.of("doEpilogExceptional"));
        method.setReturnType(type(void.class));
        method.addParameter(new CodeVariableElement(types.AbstractTruffleException, "exception"));
        method.addParameter(new CodeVariableElement(type(int.class), "nodeId"));
        return method;
    }

    private CodeExecutableElement createDoTagExceptional() {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(Object.class), "doTagExceptional",
                        new CodeVariableElement(types.FrameWithoutBoxing, parent.localFrame()),
                        new CodeVariableElement(parent.tagNode.asType(), "node"),
                        new CodeVariableElement(type(int.class), "nodeId"),
                        new CodeVariableElement(type(byte[].class), "bc"),
                        new CodeVariableElement(type(int.class), "bci"),
                        new CodeVariableElement(type(Throwable.class), "exception"));

        method.getThrownTypes().add(type(Throwable.class));

        Collection<List<InstructionModel>> groupedInstructions = groupInstructionsByKindAndImmediates(InstructionKind.TAG_LEAVE, InstructionKind.TAG_LEAVE_VOID);

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(boolean.class), "wasOnReturnExecuted");

        b.startSwitch().string("readValidBytecode(bc, bci)").end().startBlock();
        for (List<InstructionModel> instructions : groupedInstructions) {
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(parent.createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            InstructionImmediate immediate = parent.model.tagLeaveValueInstruction.getImmediate(ImmediateKind.TAG_NODE);
            b.startAssign("wasOnReturnExecuted").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", immediate)).string(" == nodeId").end();
            b.statement("break");
            b.end();
        }
        b.caseDefault().startCaseBlock();
        b.statement("wasOnReturnExecuted = false");
        b.statement("break");
        b.end(); // case default
        b.end(); // switch

        b.startReturn().startCall("node.findProbe().onReturnExceptionalOrUnwind");
        b.string(parent.localFrame());
        b.string("exception");
        b.string("wasOnReturnExecuted");
        b.end(2);
        return method;

    }

    boolean localAccessNeedsLocalTags(InstructionModel instr) {
        // Local tags are only used for cached interpreters with BE. They need to be read
        // separately for materialized accesses, not passed into the method.
        return !instr.kind.isLocalVariableMaterializedAccess() && parent.model.usesBoxingElimination() && tier.isCached();
    }

    CodeTree readLocalTagsFastPath() {
        return BytecodeRootNodeElement.uncheckedCast(type(byte[].class), "this.localTags_");
    }

    /**
     * We use this method to load constants on the compiled code path.
     *
     * The compiler can often detect and remove redundant box-unbox sequences, but when we load
     * primitives from the constants array that are already boxed, there is no initial "box"
     * operation. By extracting and re-boxing primitive values here, we create a fresh "box"
     * operation with which the compiler can match and eliminate subsequent "unbox" operations.
     */
    private CodeExecutableElement createLoadConstantCompiled() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(Object.class), "loadConstantCompiled");
        ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        ex.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));

        CodeTreeBuilder b = ex.createBuilder();
        InstructionImmediate constant = parent.model.loadConstantInstruction.getImmediate(ImmediateKind.CONSTANT);
        b.declaration(parent.context.getDeclaredType(Object.class), "constant", parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", constant), "this.constants"));
        Class<?>[] boxedTypes = new Class<?>[]{Boolean.class, Byte.class, Character.class, Float.class, Integer.class, Long.class, Short.class, Double.class};
        String[] getterMethods = new String[]{"booleanValue", "byteValue", "charValue", "floatValue", "intValue", "longValue", "shortValue", "doubleValue"};
        for (int i = 0; i < boxedTypes.length; i++) {
            b.startIf(i != 0);
            String className = boxedTypes[i].getSimpleName();
            char boundVariable = className.toLowerCase().charAt(0);
            b.string("constant instanceof " + className + " " + boundVariable);
            b.end().startBlock();
            b.statement("return ", boundVariable + "." + getterMethods[i] + "()");
            b.end();
        }
        b.startElseBlock();
        b.statement("return constant");
        b.end();

        return ex;
    }

    /**
     * When a node gets re-adopted, the insertion logic validates that the old and new parents both
     * have/don't have a root node. Thus, the bytecode node cannot adopt the operation nodes until
     * the node itself is adopted by the root node. We adopt them after insertion.
     */
    private CodeExecutableElement createAdoptNodesAfterUpdate() {
        CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) parent.abstractBytecodeNode.asType(), "adoptNodesAfterUpdate");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("insert(this.cachedNodes_)");
        return ex;
    }

    private List<CodeElement<Element>> createBranchProfileMembers() {
        ArrayType branchProfilesType = arrayOf(type(int.class));
        CodeVariableElement branchProfilesField = parent.compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), branchProfilesType, "branchProfiles_"));

        CodeExecutableElement allocateBranchProfiles = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), branchProfilesType, "allocateBranchProfiles",
                        new CodeVariableElement(type(int.class), "numProfiles"));
        allocateBranchProfiles.getBuilder() //
                        .lineComment("Encoding: [t1, f1, t2, f2, ..., tn, fn]") //
                        .startReturn().startNewArray(branchProfilesType, CodeTreeBuilder.singleString("numProfiles * 2")).end(2);

        CodeExecutableElement profileBranch = createProfileBranch(branchProfilesType);
        CodeExecutableElement ensureFalseProfile = createEnsureFalseProfile(branchProfilesType);

        return List.of(branchProfilesField, allocateBranchProfiles, profileBranch, ensureFalseProfile);
    }

    /**
     * This code implements the same logic as the CountingConditionProfile.
     */
    private CodeExecutableElement createProfileBranch(TypeMirror branchProfilesType) {
        CodeExecutableElement allocateBranchProfiles = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "profileBranch",
                        new CodeVariableElement(type(int.class), "profileIndex"),
                        new CodeVariableElement(type(boolean.class), "condition"));

        emitNewBranchProfile(allocateBranchProfiles, branchProfilesType);

        return allocateBranchProfiles;
    }

    private void emitNewBranchProfile(CodeExecutableElement allocateBranchProfiles, TypeMirror branchProfilesType) {
        CodeTreeBuilder b = allocateBranchProfiles.createBuilder();
        b.declaration(branchProfilesType, "branchProfiles", BytecodeRootNodeElement.uncheckedCast(branchProfilesType, "this.branchProfiles_"));
        b.declaration("int", "t", (CodeTree) null);
        b.declaration("int", "f", (CodeTree) null);

        b.startIf().startStaticCall(types.HostCompilerDirectives, "inInterpreterFastPath").end().end().startBlock();

        b.startIf().string("condition").end().startBlock();
        emitNewProfileBranchCase(b, "t", "f", "profileIndex * 2", "profileIndex * 2 + 1");
        b.end().startElseBlock();
        emitNewProfileBranchCase(b, "f", "t", "profileIndex * 2 + 1", "profileIndex * 2");
        b.end();

        b.statement("return condition");

        b.end().startElseBlock(); // inInterpreterFasthPath

        b.startAssign("t").tree(BytecodeRootNodeElement.readIntArray("branchProfiles", "profileIndex * 2")).end();
        b.startAssign("f").tree(BytecodeRootNodeElement.readIntArray("branchProfiles", "profileIndex * 2 + 1")).end();

        b.startIf().string("condition").end().startBlock();

        b.startIf().string("t == 0").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.end();

        b.startIf().string("f == 0").end().startBlock();
        b.returnTrue();
        b.end();

        b.end().startElseBlock(); // condition
        b.startIf().string("f == 0").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.end();

        b.startIf().string("t == 0").end().startBlock();
        b.returnFalse();
        b.end();
        b.end(); // condition

        b.startReturn().startStaticCall(types.CompilerDirectives, "injectBranchProbability");
        b.string("(double) t / (double) (t + f)");
        b.string("condition");
        b.end(2);

        b.end();

    }

    private void emitNewProfileBranchCase(CodeTreeBuilder b, String count, String otherCount, String index, String otherIndex) {
        b.startAssign(count).tree(BytecodeRootNodeElement.readIntArray("branchProfiles", index)).end();

        b.startIf().string(count).string(" == 0").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.end();

        b.startTryBlock();
        b.startAssign(count).startStaticCall(type(Math.class), "addExact").string(count).string("1").end().end();
        b.end().startCatchBlock(type(ArithmeticException.class), "e");
        b.startAssign(otherCount).tree(BytecodeRootNodeElement.readIntArray("branchProfiles", otherIndex)).end();
        b.lineComment("shift count but never make it go to 0");
        b.startAssign(otherCount).string("(" + otherCount + " & 0x1) + (" + otherCount + " >> 1)").end();
        b.statement(BytecodeRootNodeElement.writeIntArray("branchProfiles", otherIndex, otherCount));
        b.startAssign(count).staticReference(type(Integer.class), "MAX_VALUE").string(" >> 1").end();
        b.end(); // catch block

        b.statement(BytecodeRootNodeElement.writeIntArray("branchProfiles", index, count));
    }

    private CodeExecutableElement createEnsureFalseProfile(TypeMirror branchProfilesType) {
        CodeExecutableElement ensureFalseProfile = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), type(void.class), "ensureFalseProfile",
                        new CodeVariableElement(branchProfilesType, "branchProfiles"),
                        new CodeVariableElement(type(int.class), "profileIndex"));
        CodeTreeBuilder b = ensureFalseProfile.createBuilder();

        b.startIf().tree(BytecodeRootNodeElement.readIntArray("branchProfiles", "profileIndex * 2 + 1")).string(" == 0").end().startBlock();
        b.statement(BytecodeRootNodeElement.writeIntArray("branchProfiles", "profileIndex * 2 + 1", "1"));
        b.end();

        return ensureFalseProfile;
    }

    void storeBciInFrame(CodeTreeBuilder b) {
        if (BytecodeRootNodeElement.isStoreBciEnabled(parent.model, tier)) {
            BytecodeRootNodeElement.storeBciInFrame(b, parent.localFrame(), "bci");
        }
    }

    static void emitCustomStackEffect(CodeTreeBuilder continueAtBuilder, int stackEffect) {
        if (stackEffect > 0) {
            continueAtBuilder.statement("sp += " + stackEffect);
        } else if (stackEffect < 0) {
            continueAtBuilder.statement("sp -= " + -stackEffect);
        }
    }

    private static void emitReturnTopOfStack(CodeTreeBuilder b) {
        b.startReturn().string(BytecodeRootNodeElement.encodeReturnState("(sp - 1)")).end();
    }

    private void emitBeforeReturnProfilingHandler(CodeTreeBuilder b) {
        if (tier.isCached()) {
            b.startIf().string("counter > 0").end().startBlock();
            b.startStatement().startStaticCall(types.LoopNode, "reportLoopCount");
            b.string("this");
            b.string("counter");
            b.end().end();  // statement
            b.end();  // if counter > 0
        }
    }

}
