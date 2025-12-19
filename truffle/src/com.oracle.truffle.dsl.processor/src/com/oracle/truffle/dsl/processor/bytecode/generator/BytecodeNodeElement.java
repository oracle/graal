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
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.InstructionGroup;
import com.oracle.truffle.dsl.processor.bytecode.model.ConstantOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
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
import com.oracle.truffle.dsl.processor.model.SpecializationData;

final class BytecodeNodeElement extends AbstractElement {

    private static final String METADATA_FIELD_NAME = "osrMetadata_";
    private static final String FORCE_UNCACHED_THRESHOLD = "Integer.MIN_VALUE";
    private final Map<InstructionModel, CodeExecutableElement> instructionHandlers = new LinkedHashMap<>();
    private final InterpreterTier tier;

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
            if (parent.model.defaultLocalValueExpression != null) {
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
        AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

        if (tier.isCached()) {
            if (generic) {
                b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
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
                if (parent.model.defaultLocalValueExpression != null) {
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
                BytecodeRootNodeElement.startExpectFrame(b, "frame", specializedType, false).string("USER_LOCALS_START_INDEX + localOffset").end();
                b.end();
            }
        } else {
            if (generic) {
                b.startReturn().string("frame.getObject(USER_LOCALS_START_INDEX + localOffset)").end();
            } else {
                b.declaration(type(Object.class), "value", "frame.getObject(USER_LOCALS_START_INDEX + localOffset)");
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

                if (parent.model.defaultLocalValueExpression == null) {
                    b.startIf().string("frame.isObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")").end().end().startBlock();
                    b.startAssign("frame").cast(types.Frame).string("frame.getObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")").end();
                    b.end();
                } else {
                    b.declaration(type(Object.class), "coroutineFrame", "frame.getObject(" + BytecodeRootNodeElement.COROUTINE_FRAME_INDEX + ")");
                    b.startIf().string("coroutineFrame != DEFAULT_LOCAL_VALUE").end().end().startBlock();
                    b.startAssign("frame").cast(types.Frame).string("coroutineFrame").end();
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
                b.startReturn();
                b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
                for (VariableElement var : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
                    b.string("this.", var.getSimpleName().toString());
                }
                if (parent.model.usesBoxingElimination()) {
                    b.string("createCachedTags(numLocals)");
                }
                b.end();
                b.end();
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
            b.statement("$root.transitionToCached(frame, 0)");
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
            b.statement("$root.transitionToCached(frame, bci)");
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
                emitCustomStackEffect(b, group.stackEffect());
                b.statement("bci += " + group.instructionLength());
                b.statement("break");
                b.end(); // case block
            }
            b.end(); // switch block
            b.statement("break");
            b.end(); // default case block

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
            b.startStatement();
            emitCallInstructionHandler(b, instruction, false);
            b.end();

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

    private void emitInstructionHandler(CodeTreeBuilder b, InstructionModel instr) {
        CodeExecutableElement method = lookupInstructionHandler(instr, true);
        if (isReturn(instr)) {
            b.startReturn();
            emitCallInstructionHandler(b, instr);
            b.end();
            return;
        }
        switch (instr.kind) {
            case CREATE_VARIADIC:
            case LOAD_VARIADIC:
                b.startStatement();
                b.string("sp -= ");
                emitCallInstructionHandler(b, instr);
                b.end();
                b.statement("bci += " + instr.getInstructionLength());
                break;
            case CUSTOM_SHORT_CIRCUIT:
                ShortCircuitInstructionModel shortCircuitInstruction = instr.shortCircuitModel;
                b.startIf();
                emitCallInstructionHandler(b, instr);
                b.end().startBlock();
                b.startAssign("bci").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX))).end();

                if (shortCircuitInstruction.producesBoolean()) {
                    // Stack: [..., convertedValue]
                    // leave convertedValue on the top of stack
                } else {
                    // Stack: [..., value, convertedValue]
                    // pop convertedValue
                    b.statement("sp -= 1");
                }

                b.end().startElseBlock();
                b.statement("bci += " + instr.getInstructionLength());

                if (shortCircuitInstruction.producesBoolean()) {
                    // Stack: [..., convertedValue]
                    // clear convertedValue
                    b.statement("sp -= 1");
                } else {
                    // Stack: [..., value, convertedValue]
                    // clear convertedValue and value
                    b.statement("sp -= 2");
                }
                b.end();
                break;
            default:
                b.startStatement();
                boolean bciReturn = ElementUtils.typeEquals(type(int.class), method.getReturnType());
                if (bciReturn) {
                    b.string("bci = ");
                } else {
                    // nothing to do void method
                }
                emitCallInstructionHandler(b, instr);
                b.end();

                if (!bciReturn) {
                    b.startStatement().string("bci += ").string(instr.getInstructionLength()).end();
                }

                emitCustomStackEffect(b, instr.getStackEffect());
                break;
        }
        b.statement("break");

    }

    private CodeExecutableElement lookupInstructionHandler(InstructionModel instr, boolean returnBci) {
        CodeExecutableElement method = instructionHandlers.get(instr);
        if (method != null) {
            return method;
        }
        String methodName = "handle" + firstLetterUpperCase(instr.getInternalName());
        method = this.add(new CodeExecutableElement(Set.of(PRIVATE), type(returnBci ? int.class : void.class), methodName));

        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        method.addParameter(new CodeVariableElement(type(int.class), "bci"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));

        // register early to support recursive use
        instructionHandlers.put(instr, method);

        CodeTreeBuilder b = method.createBuilder();

        if (instr.kind != InstructionKind.CUSTOM && BytecodeRootNodeElement.isStoreBciBeforeExecute(parent.model, tier, instr)) {
            storeBciInFrame(b);
        }

        boolean emitDefaultReturn = returnBci;
        switch (instr.kind) {
            case BRANCH:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                b.statement("return " + BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                emitDefaultReturn = false;
                break;
            case BRANCH_FALSE:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                emitBranchFalseHandler(instr, b);
                emitDefaultReturn = false;
                break;
            case CUSTOM_SHORT_CIRCUIT:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                method.setReturnType(type(boolean.class));
                emitCustomShortCircuitHandler(instr, b);
                emitDefaultReturn = false;
                break;
            case TAG_RESUME:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
                b.startDeclaration(parent.tagNode.asType(), "tagNode");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.TAG_NODE))));
                b.end();
                b.startStatement().startCall("tagNode.findProbe().onResume").string(parent.localFrame()).end(2);
                break;
            case TAG_ENTER:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
                b.startDeclaration(parent.tagNode.asType(), "tagNode");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.TAG_NODE))));
                b.end();
                b.startStatement().startCall("tagNode.findProbe().onEnter").string(parent.localFrame()).end(2);
                break;
            case TAG_YIELD:
            case TAG_YIELD_NULL:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
                if (instr.kind == InstructionKind.TAG_YIELD) {
                    b.startDeclaration(type(Object.class), "returnValue");
                    BytecodeRootNodeElement.startRequireFrame(b, type(Object.class));
                    b.string(parent.localFrame());
                    b.string("sp - 1");
                    b.end();
                    b.end(); // declaration
                }
                InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
                b.startDeclaration(parent.tagNode.asType(), "tagNode");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
                b.end();

                b.startStatement().startCall("tagNode.findProbe().onYield");
                b.string(parent.localFrame());
                switch (instr.kind) {
                    case TAG_YIELD -> b.string("returnValue");
                    case TAG_YIELD_NULL -> b.string("null");
                    default -> throw new AssertionError("unexpected tag yield instruction " + instr);
                }
                b.end(2);
                break;
            case TAG_LEAVE:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
                if (tier.isUncached() || instr.isQuickening() || !parent.model.usesBoxingElimination()) {
                    emitTagLeaveHandler(instr, b);
                } else {
                    emitTagLeaveAndSpecializeHandler(instr, b);
                }
                break;
            case TAG_LEAVE_VOID:
                method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
                b.startDeclaration(parent.tagNode.asType(), "tagNode");
                b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.TAG_NODE))));
                b.end();
                b.startStatement().startCall("tagNode.findProbe().onReturnValue");
                b.string(parent.localFrame());
                b.string("null");
                b.end(2);
                break;
            case LOAD_ARGUMENT:
                emitLoadArgumentHandler(instr, b);
                break;
            case LOAD_CONSTANT:
                emitLoadConstantHandler(instr, b);
                break;
            case LOAD_NULL:
                b.statement(BytecodeRootNodeElement.setFrameObject("frame", "sp", "null"));
                break;
            case LOAD_EXCEPTION:
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("sp");
                BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class));
                b.startGroup().string("getRoot().maxLocals + ").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.STACK_POINTER))).end();
                b.end(); // getFrameUnsafe
                b.end(); // set frame
                b.end(); // statement
                break;
            case POP:
                emitPopHandler(instr, b);
                break;
            case DUP:
                b.statement(BytecodeRootNodeElement.copyFrameSlot("sp - 1", "sp"));
                break;
            case LOAD_LOCAL:
            case LOAD_LOCAL_MATERIALIZED:
                emitLoadLocalHandler(instr, b);
                break;
            case STORE_LOCAL:
            case STORE_LOCAL_MATERIALIZED:
                emitDefaultReturn = false;
                emitStoreLocalHandler(instr, b);
                break;
            case MERGE_CONDITIONAL:
                emitMergeConditionalHandler(instr, b);
                break;
            case THROW:
                b.statement("throw sneakyThrow((Throwable) " + BytecodeRootNodeElement.uncheckedGetFrameObject("frame", "sp - 1") + ")");
                emitDefaultReturn = false;
                break;
            case CLEAR_LOCAL:
                String index = BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)).toString();
                if (parent.model.defaultLocalValueExpression != null) {
                    b.statement(BytecodeRootNodeElement.setFrameObject("frame", index, "DEFAULT_LOCAL_VALUE"));
                } else {
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", index));
                }
                break;
            case CREATE_VARIADIC:
                method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
                method.setReturnType(type(int.class));
                emitCreateVariadicHandler(instr, b);
                emitDefaultReturn = false;
                break;
            case LOAD_VARIADIC:
                method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
                method.setReturnType(type(int.class));
                emitLoadVariadicHandler(instr, b);
                emitDefaultReturn = false;
                break;
            case EMPTY_VARIADIC:
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class));
                b.string("frame");
                b.string("sp");
                b.startGroup();
                b.string("EMPTY_ARRAY");
                b.end(); // group
                b.end(); // setFrame
                b.end(); // statement
                break;
            case SPLAT_VARIADIC:
                method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
                emitSplatVariadicHandler(instr, b);
                // no stack effect
                break;
            case CUSTOM:
                if (tier.isUncached() && instr.operation.customModel.forcesCached()) {
                    throw new AssertionError("forceCached instructions should be emitted separately");
                }
                if (instr.operation.kind == OperationKind.CUSTOM_YIELD) {
                    emitDefaultReturn = false;
                    method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                    method.setReturnType(type(long.class));
                    if (tier.isCached()) {
                        method.getParameters().add(new CodeVariableElement(type(int.class), "counter"));
                    }
                    emitBeforeReturnProfilingHandler(b);
                    emitCustomHandler(b, instr);
                    String returnSp = (instr.signature.dynamicOperandCount == 0) ? "sp" : "sp - " + instr.signature.dynamicOperandCount;
                    if (parent.model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                        b.startStatement();
                        b.startCall("getRoot().afterRootExecute");
                        parent.emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("readValidBytecode(bc, bci)"));
                        BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class)).string("(", returnSp, ")");
                        b.end();
                        b.string("null");
                        b.end();
                        b.end();
                    }
                    b.startReturn().string(BytecodeRootNodeElement.encodeReturnState("(" + returnSp + ")")).end();
                } else {
                    emitCustomHandler(b, instr);
                }
                break;
            case TRACE_INSTRUCTION:
                b.startStatement();
                b.tree(parent.readConstFastPath(CodeTreeBuilder.singleString("Builder.INSTRUCTION_TRACER_CONSTANT_INDEX"), "this.constants", parent.instructionTracerAccessImplElement.asType()));
                b.startCall(".onInstructionEnter").string("this").string("bci").string(parent.localFrame()).end();
                b.end(); // statement
                break;
            case YIELD:
                emitDefaultReturn = false;
                method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                method.setReturnType(type(long.class));
                if (tier.isCached()) {
                    method.getParameters().add(new CodeVariableElement(type(int.class), "counter"));
                }
                emitBeforeReturnProfilingHandler(b);

                if (parent.model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                    b.startStatement();
                    b.startCall("getRoot().afterRootExecute");
                    parent.emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("readValidBytecode(bc, bci)"));
                    BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class)).string("(sp - 1)");
                    b.end();
                    b.string("null");
                    b.end();
                    b.end();
                }
                InstructionImmediate continuationIndex = instr.getImmediate(ImmediateKind.CONSTANT);
                emitCopyStackToLocalFrameBeforeYield(b, instr);

                b.startDeclaration(parent.continuationRootNodeImpl.asType(), "continuationRootNode");
                b.tree(parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", continuationIndex), "this.constants", parent.continuationRootNodeImpl.asType()));
                b.end();

                b.startDeclaration(types.ContinuationResult, "continuationResult");
                b.startCall("continuationRootNode.createContinuation");
                b.string(parent.localFrame());
                b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1"));
                b.end(2);

                b.statement(BytecodeRootNodeElement.setFrameObject("sp - 1", "continuationResult"));

                emitReturnTopOfStack(b);
                break;
            case RETURN:
                emitDefaultReturn = false;
                method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
                method.setReturnType(type(long.class));
                if (tier.isCached()) {
                    method.getParameters().add(new CodeVariableElement(type(int.class), "counter"));
                }
                emitBeforeReturnProfilingHandler(b);
                if (parent.model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                    b.startStatement();
                    b.startCall("getRoot().afterRootExecute");
                    parent.emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("readValidBytecode(bc, bci)"));
                    BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class)).string("(sp - 1)");
                    b.end();
                    b.string("null");
                    b.end();
                    b.end();
                }
                emitReturnTopOfStack(b);
                break;
            case INVALIDATE:
                emitDefaultReturn = false;
                method.setReturnType(type(long.class));
                if (tier.isCached()) {
                    method.getParameters().add(new CodeVariableElement(type(int.class), "counter"));
                }
                emitInvalidate(b);
                break;
            default:
                throw new UnsupportedOperationException("not implemented: " + instr.kind);

        }

        if (emitDefaultReturn) {
            emitReturnNextInstruction(b, instr);
        }

        return method;
    }

    private CodeExecutableElement createInstructionHandler(TypeMirror returnType, String name) {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        returnType, name);
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        method.addParameter(new CodeVariableElement(type(int.class), "bci"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));
        return method;
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
                b.string("root").string("frame");
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
                        return i.signature.returnType;
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
        b.statement(BytecodeRootNodeElement.clearFrame("frame", "--sp"));
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

    private void buildInstructionCases(CodeTreeBuilder b, InstructionModel instruction) {
        b.startCase().tree(parent.createInstructionConstant(instruction)).end();
        if (tier.isUncached()) {
            for (InstructionModel quickendInstruction : instruction.getFlattenedQuickenedInstructions()) {
                b.startCase().tree(parent.createInstructionConstant(quickendInstruction)).end();
            }
        }
    }

    static boolean isReturn(InstructionModel instr) {
        switch (instr.kind) {
            case RETURN:
            case YIELD:
            case INVALIDATE:
            case CUSTOM:
                if (instr.kind != InstructionKind.CUSTOM || instr.operation.kind == OperationKind.CUSTOM_YIELD) {
                    return true;
                }
                // intentional fallthrough-
        }
        return false;
    }

    private void emitCallInstructionHandler(CodeTreeBuilder b, InstructionModel instr) {
        emitCallInstructionHandler(b, instr, true);
    }

    private void emitCallInstructionHandler(CodeTreeBuilder b, InstructionModel instr, boolean returnBci) {
        CodeExecutableElement method = lookupInstructionHandler(instr, returnBci);
        BytecodeRootNodeElement.emitCallDefault(b, method, (name, innerB) -> {
            switch (name) {
                case "counter":
                    innerB.string("(");
                    innerB.startStaticCall(types.CompilerDirectives, "inCompiledCode").end();
                    innerB.string(" && ");
                    innerB.startStaticCall(types.CompilerDirectives, "hasNextTier").end();
                    innerB.string(" ? loopCounter.value : counter)");
                    break;
                default:
                    innerB.string(name);
                    break;
            }

        });
    }

    static void emitReturnNextInstruction(CodeTreeBuilder b, InstructionModel instr) {
        b.startReturn().string("bci + ").string(instr.getInstructionLength()).end();
    }

    private void buildInstructionCaseBlock(CodeTreeBuilder b, InstructionModel instr) {
        buildInstructionCases(b, instr);

        b.startCaseBlock();
        if (instr.kind == InstructionKind.BRANCH_BACKWARD) {
            /*
             * Branch.backward is the only instruction left with a regular handler, because it also
             * needs to reset the counters.
             */
            emitBranchBackward(b, instr);
        } else {
            emitInstructionHandler(b, instr);
        }

        b.end();
    }

    private void emitBranchBackward(CodeTreeBuilder b, InstructionModel instr) {
        b.startStatement().startStaticCall(types.TruffleSafepoint, "poll").string("this").end().end();

        if (tier.isUncached()) {
            b.statement("bci = " + BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            b.startIf().string("uncachedExecuteCount_ <= 1").end().startBlock();
            /*
             * The force uncached check is put in here so that we don't need to check it in the
             * common case (the else branch where we just decrement).
             */
            b.startIf().string("uncachedExecuteCount_ != ", FORCE_UNCACHED_THRESHOLD).end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("$root.transitionToCached(frame, bci)");
            b.statement("return ", parent.encodeState("bci", "sp"));
            b.end(2);
            b.startElseBlock();
            b.statement("uncachedExecuteCount_--");
            b.end();
        } else {
            b.startIf().startStaticCall(types.CompilerDirectives, "hasNextTier").end().end().startBlock();
            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("counter = ++loopCounter.value");
            b.end().startElseBlock();
            b.statement("counter++");
            b.end();

            b.startIf();
            b.startStaticCall(types.CompilerDirectives, "injectBranchProbability");
            b.staticReference(parent.loopCounter.asType(), "REPORT_LOOP_PROBABILITY");
            b.startGroup();
            b.string("counter >= ").staticReference(parent.loopCounter.asType(), "REPORT_LOOP_STRIDE");
            b.end();
            b.end(); // static call
            b.end().startBlock();

            b.startDeclaration(type(Object.class), "osrResult");
            BytecodeRootNodeElement.emitCallDefault(b, this.add(createReportLoopCount(instr)));
            b.end();

            b.startIf().string("osrResult != null").end().startBlock();
            /**
             * executeOSR invokes BytecodeNode#continueAt, which returns a long encoding the sp and
             * bci when it returns/when the bytecode is rewritten. Returning this value is correct
             * in either case: If it's a return, we'll read the result out of the frame (the OSR
             * code copies the OSR frame contents back into our frame first); if it's a rewrite,
             * we'll transition and continue executing.
             */
            b.startReturn().cast(type(long.class)).string("osrResult").end();
            b.end(); // osrResult != null

            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("loopCounter.value = 0");
            b.end().startElseBlock();
            b.statement("counter = 0");
            b.end();

            b.end(); // if counter >= REPORT_LOOP_STRIDE

            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("counter = 0");
            b.end();

            b.end();
            b.statement("bci = " + BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
        }
        b.statement("break");
    }

    private void emitCreateVariadicHandler(InstructionModel instr, CodeTreeBuilder b) {
        b.startDeclaration(type(int.class), "count");
        b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.INTEGER, "count")));
        b.end();

        InstructionImmediate offsetImmediate = instr.findImmediate(ImmediateKind.INTEGER, "offset");
        if (offsetImmediate != null) {
            b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", offsetImmediate));
        }
        String addOffset = (offsetImmediate == null ? "" : "offset + ");

        b.declaration(type(int.class), "newSize", addOffset + "count");
        if (parent.model.hasVariadicReturn) {
            b.declaration(type(int.class), "mergeCount", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.SHORT, "merge_count")));
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement("newSize += dynamicArray.length - 1");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
        }

        b.declaration(type(Object[].class), "result", "new Object[newSize]");
        b.declaration(type(int.class), "stackPopCount", "Math.min(count, VARIADIC_STACK_LIMIT)");

        if (parent.model.hasVariadicReturn) {
            b.startFor().string("int i = 0; i < stackPopCount - mergeCount; i++").end().startBlock();
        } else {
            b.startFor().string("int i = 0; i < stackPopCount; i++").end().startBlock();
        }
        b.startStatement();
        if (offsetImmediate == null) {
            b.string("result[i] = ");
        } else {
            b.string("result[offset + i] = ");
        }
        b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - stackPopCount + i"));
        b.end();
        b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - stackPopCount + i"));

        b.end();

        if (parent.model.hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.declaration(type(int.class), "mergeIndex", addOffset + "stackPopCount - mergeCount");
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - mergeCount + i"));

            b.declaration(type(int.class), "dynamicLength", "dynamicArray.length");
            b.startStatement().startStaticCall(type(System.class), "arraycopy");
            b.string("dynamicArray").string("0").string("result").string("mergeIndex").string("dynamicLength");
            b.end().end(); // static call, statement

            b.statement("mergeIndex += dynamicLength");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
        }

        b.startStatement();
        BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("sp - stackPopCount").string("result").end();
        b.end();

        b.statement("return Math.min(count - 1, VARIADIC_STACK_LIMIT - 1)");
    }

    private void emitLoadVariadicHandler(InstructionModel instr, CodeTreeBuilder b) {
        b.startDeclaration(type(int.class), "count");
        b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.SHORT, "count")));
        b.end();

        b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.INTEGER, "offset")));
        b.startDeclaration(type(Object[].class), "result");
        b.startCall("ACCESS.uncheckedCast");
        b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - count - 1"));
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end();

        if (parent.model.hasVariadicReturn) {
            b.declaration(type(int.class), "newSize", "offset + count");
            b.declaration(type(int.class), "mergeCount", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.SHORT, "merge_count")));
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement("newSize += dynamicArray.length - 1");

            b.end(); // for mergeDynamicCount

            b.startStatement().string("result = ");
            b.startStaticCall(type(Arrays.class), "copyOf").string("result").string("newSize").end();
            b.end(); // statement
            b.statement(BytecodeRootNodeElement.setFrameObject("frame", "sp - count - 1", "result"));

            b.end(); // if mergeDynamicCount > 0

            b.startFor().string("int i = 0; i < count - mergeCount; i++").end().startBlock();
        } else {
            b.startFor().string("int i = 0; i < count; i++").end().startBlock();
        }
        b.startStatement();
        b.string("result[offset + i] = ").string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - count + i"));
        b.end();
        b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - count + i"));
        b.end(); // for

        if (parent.model.hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.declaration(type(int.class), "mergeIndex", "offset + count - mergeCount");
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - mergeCount + i"));

            b.declaration(type(int.class), "dynamicLength", "dynamicArray.length");
            b.startStatement().startStaticCall(type(System.class), "arraycopy");
            b.string("dynamicArray").string("0").string("result").string("mergeIndex").string("dynamicLength");
            b.end().end(); // static call, statement

            b.statement("mergeIndex += dynamicLength");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
        }
        b.statement("return count");
    }

    private void emitSplatVariadicHandler(InstructionModel instr, CodeTreeBuilder b) {
        b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.INTEGER, "offset")));
        b.declaration(type(int.class), "count", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.INTEGER, "count")));

        b.startDeclaration(type(Object[].class), "oldArray");
        b.startCall("ACCESS.uncheckedCast");
        b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1"));
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end();

        b.declaration(type(int.class), "newSize", "oldArray.length");
        b.startFor().string("int i = 0; i < count; i++").end().startBlock();

        b.startDeclaration(type(Object[].class), "dynamicArray");
        b.startCall("ACCESS.uncheckedCast");
        b.string("oldArray[offset + i]");
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end();
        b.statement("newSize += dynamicArray.length - 1");

        b.end(); // for count

        b.declaration(type(Object[].class), "newArray", "new Object[newSize]");

        b.lineComment("copy prefixed elements");
        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("oldArray").string("0").string("newArray").string("0").string("offset");
        b.end().end();

        // copy dynamic arrays
        b.lineComment("copy dynamic elements");
        b.declaration(type(int.class), "mergeIndex", "offset");
        b.startFor().string("int i = 0; i < count; i++").end().startBlock();
        b.startDeclaration(type(Object[].class), "dynamicArray");
        b.startCall("ACCESS.uncheckedCast");
        b.string("oldArray[offset + i]");
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end(); // declaration

        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("dynamicArray").string("0").string("newArray").string("mergeIndex").string("dynamicArray.length");
        b.end().end();
        b.statement("mergeIndex += dynamicArray.length");
        b.end(); // for count

        b.lineComment("copy suffix elements");
        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("oldArray").string("offset + count").string("newArray").string("mergeIndex").string("oldArray.length - offset - count");
        b.end().end();

        b.statement(BytecodeRootNodeElement.setFrameObject("frame", "sp - 1", "newArray"));
    }

    private void emitMergeConditionalHandler(InstructionModel instr, CodeTreeBuilder b) throws AssertionError {
        if (!parent.model.usesBoxingElimination()) {
            throw new AssertionError("Merge.conditional only supports boxing elimination enabled.");
        }
        if (instr.isQuickening() || tier.isUncached() || !parent.model.usesBoxingElimination()) {
            final TypeMirror inputType = instr.signature.getDynamicOperandType(1);
            final TypeMirror returnType = instr.signature.returnType;

            if (tier.isCached() && parent.model.usesBoxingElimination()) {
                b.declaration(inputType, "value");
                b.startTryBlock();
                b.startStatement();
                b.string("value = ");
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", inputType).string("sp - 1").end();
                b.end();
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();
                b.end(); // catch block
            } else {
                b.startDeclaration(inputType, "value");
                BytecodeRootNodeElement.startRequireFrame(b, inputType).string("frame").string("sp - 1").end();
                b.end();
            }

            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, returnType).string("frame").string("sp - 2").string("value").end();
            b.end();

            if (ElementUtils.isPrimitive(inputType)) {
                // we only need to clear in compiled code for liveness if primitive
                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                b.end();
            } else {
                // always clear for references for gc behavior.
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
            }
        } else {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startDeclaration(type(Object.class), "local");
            BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", null).string("sp - 1").end();
            b.end();

            InstructionImmediate operand0 = instr.getImmediates(ImmediateKind.BYTECODE_INDEX).get(0);
            InstructionImmediate operand1 = instr.getImmediates(ImmediateKind.BYTECODE_INDEX).get(1);

            b.startDeclaration(type(boolean.class), "condition");
            b.cast(type(boolean.class));
            BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", null).string("sp - 2");
            b.end().end();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "newOtherOperand");
            b.declaration(type(int.class), "operandIndex");
            b.declaration(type(int.class), "otherOperandIndex");

            b.startIf().string("condition").end().startBlock();
            b.startAssign("operandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand0)).end();
            b.startAssign("otherOperandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand1)).end();
            b.end().startElseBlock();
            b.startAssign("operandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand1)).end();
            b.startAssign("otherOperandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand0)).end();
            b.end();

            b.startIf().string("operandIndex != -1 && otherOperandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));
            b.declaration(type(short.class), "otherOperand", BytecodeRootNodeElement.readInstruction("bc", "otherOperandIndex"));
            InstructionModel genericInstruction = instr.findGenericInstruction();

            boolean elseIf = false;
            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType));
                b.newLine().string("   && (");
                b.string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1)");
                b.end().startBlock();

                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);
                b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();
                b.startCase().tree(parent.createInstructionConstant(boxedInstruction.getQuickeningRoot())).end();
                b.startCase().tree(parent.createInstructionConstant(boxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(parent.createInstructionConstant(boxedInstruction)).end();
                b.statement("break");
                b.end();
                b.startCase().tree(parent.createInstructionConstant(unboxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(parent.createInstructionConstant(unboxedInstruction)).end();
                b.statement("break");
                b.end();
                b.caseDefault();
                b.startCaseBlock();
                b.statement("newOtherOperand = undoQuickening(otherOperand)");
                b.startAssign("newInstruction").tree(parent.createInstructionConstant(genericInstruction)).end();
                b.statement("break");
                b.end();
                b.end(); // switch

                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = operand");
            b.statement("newOtherOperand = undoQuickening(otherOperand)");
            b.startAssign("newInstruction").tree(parent.createInstructionConstant(genericInstruction)).end();
            b.end();

            parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "otherOperandIndex", "otherOperand", "newOtherOperand");

            b.end(); // case both operand indices are valid
            b.startElseBlock();
            b.startAssign("newInstruction").tree(parent.createInstructionConstant(genericInstruction)).end();
            b.end(); // case either operand index is invalid

            parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("sp - 2").string("local").end();
            b.end();
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
        }
    }

    private void emitStoreLocalHandler(InstructionModel instr, CodeTreeBuilder b) throws AssertionError {
        String localsFrame = parent.localFrame();
        boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
        if (instr.isQuickening() || tier.isUncached() || !parent.model.usesBoxingElimination()) {

            final TypeMirror inputType = instr.signature.getDynamicOperandType(0);
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                b.declaration(type(byte[].class), "localTags", readLocalTagsFastPath());
            }

            if (tier.isCached() && parent.model.usesBoxingElimination()) {
                b.declaration(inputType, "local");
                b.startTryBlock();
                b.startStatement().string("local = ");
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", inputType).string("sp - 1").end();
                b.end();

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();
                b.end(); // catch block
            } else {
                b.startDeclaration(inputType, "local");
                BytecodeRootNodeElement.startRequireFrame(b, inputType).string("frame").string("sp - 1").end();
                b.end();
            }

            if (materialized) {
                localsFrame = "materializedFrame";
                b.startDeclaration(types.FrameWithoutBoxing, "materializedFrame");
                b.cast(types.FrameWithoutBoxing).string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 2"));
                b.end();
            }

            boolean generic = ElementUtils.typeEquals(type(Object.class), inputType);

            CodeTree readSlot = BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX));
            if (generic && !ElementUtils.needsCastTo(inputType, slotType)) {
                if (materialized) {
                    b.declaration(type(int.class), "slot", readSlot);
                    readSlot = CodeTreeBuilder.singleString("slot");
                    b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                    if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                        b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                    }

                    if (parent.model.usesBoxingElimination()) {
                        b.declaration(type(int.class), "localOffset", "slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX);
                        emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                        // We need to update the tags. Call the setter method on the
                        // bytecodeNode.
                        b.startStatement().startCall("bytecodeNode.setLocalValueInternal");
                        b.string(localsFrame);
                        b.string("localOffset");
                        if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                            b.string("localIndex");
                        } else {
                            b.string("localOffset");
                        }
                        b.string("local");
                        b.end(2);
                    } else {
                        emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", null, "localIndex");
                        b.startStatement();
                        BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).tree(readSlot);
                        b.string("local");
                        b.end();
                        b.end();
                    }

                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 2"));
                } else {
                    b.startStatement();
                    BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).tree(readSlot);
                    b.string("local");
                    b.end();
                    b.end();
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                }
                emitReturnNextInstruction(b, instr);
            } else {
                if (!parent.model.usesBoxingElimination()) {
                    throw new AssertionError("Unexpected path.");
                }

                boolean needsCast = ElementUtils.needsCastTo(inputType, slotType);
                b.declaration(type(int.class), "slot", readSlot);
                if (materialized) {
                    b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                }
                String localIndex;
                if (parent.model.enableBlockScoping) {
                    b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                    localIndex = "localIndex";
                } else {
                    localIndex = "slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX;
                }

                String bytecodeNode;
                if (materialized) {
                    emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                    bytecodeNode = "bytecodeNode";
                } else {
                    bytecodeNode = "this";
                }

                b.startDeclaration(type(byte.class), "tag");
                b.startCall(bytecodeNode, "getCachedLocalTagInternal");
                if (materialized) {
                    b.startCall(bytecodeNode, "getLocalTags").end();
                } else {
                    b.string("localTags");
                }
                b.string(localIndex);
                b.end(); // call
                b.end(); // declaration

                b.startIf().string("tag == ").staticReference(parent.frameTagsElement.get(slotType));
                b.end().startBlock();
                if (needsCast) {
                    b.startTryBlock();
                }
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).string("slot");
                if (needsCast) {
                    b.startStaticCall(parent.lookupExpectMethod(inputType, slotType));
                    b.string("local");
                    b.end();
                } else {
                    b.string("local");
                }
                b.end(); // set frame
                b.end(); // statement

                if (materialized) {
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                    b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                    b.lineComment("Clear primitive for compiler liveness analysis");
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 2"));
                    b.end();
                } else {
                    b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                    b.lineComment("Clear primitive for compiler liveness analysis");
                    b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                    b.end();
                }

                emitReturnNextInstruction(b, instr);

                if (needsCast) {
                    b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                    b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                    b.statement("local = ex.getResult()");
                    b.lineComment("fall through to slow-path");
                    b.end();  // catch block
                }

                b.end();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();

            }
        } else {

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startDeclaration(type(Object.class), "local");
            BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", null).string("sp - 1").end();
            b.end();

            if (materialized) {
                localsFrame = "materializedFrame";
                b.startDeclaration(types.FrameWithoutBoxing, "materializedFrame");
                b.cast(types.FrameWithoutBoxing).string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 2"));
                b.end();
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                b.declaration(type(byte[].class), "localTags", readLocalTagsFastPath());
            }

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "slot", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)));
            if (materialized) {
                b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
            }

            String localIndex;
            if (parent.model.enableBlockScoping) {
                b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = "localIndex";
            } else {
                localIndex = "slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX;
            }
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            String bytecodeNode;
            if (materialized) {
                emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                bytecodeNode = "bytecodeNode";
            } else {
                bytecodeNode = "this";
            }

            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

            b.startDeclaration(type(byte.class), "oldTag");
            b.startCall(bytecodeNode, "getCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.string(localIndex);
            b.end(); // call
            b.end(); // declaration
            b.declaration(type(byte.class), "newTag");

            InstructionModel genericInstruction = instr.findGenericInstruction();

            boolean elseIf = false;
            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType)).end().startBlock();

                // instruction for unsuccessful operand quickening
                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);
                // instruction for successful operand quickening
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);

                b.startSwitch().string("oldTag").end().startBlock();

                b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
                b.startCase().staticReference(parent.frameTagsElement.getIllegal()).end();
                b.startCaseBlock();

                b.startIf().string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(unboxedInstruction)).end();
                b.end().startElseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(boxedInstruction)).end();
                b.startStatement().string("newOperand = operand").end();
                b.end(); // else block
                String kindName = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType));
                parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + kindName);
                b.startStatement().string("newTag = ").staticReference(parent.frameTagsElement.get(boxingType)).end();
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, boxingType).string(localsFrame).string("slot").startGroup().cast(boxingType).string("local").end().end();
                b.end();
                b.statement("break");
                b.end();

                for (TypeMirror otherType : parent.model.boxingEliminatedTypes) {
                    if (ElementUtils.typeEquals(otherType, boxingType)) {
                        continue;
                    }
                    b.startCase().staticReference(parent.frameTagsElement.get(otherType)).end();
                }

                b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
                b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
                b.startStatement().string("newTag = ").staticReference(parent.frameTagsElement.getObject()).end();
                parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string(localsFrame).string("slot").string("local").end();
                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
                b.end();

                b.end(); // switch
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
            b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
            b.startStatement().string("newTag = ").staticReference(parent.frameTagsElement.getObject()).end();
            parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string(localsFrame).string("slot").string("local").end();
            b.end();
            b.end(); // else

            b.startIf().string("newTag != oldTag").end().startBlock();
            b.startStatement().startCall(bytecodeNode, "setCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            if (parent.model.enableBlockScoping) {
                b.string("localIndex");
            } else {
                b.string("slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX);
            }
            b.string("newTag");
            b.end(2);
            b.end(); // if newTag != oldTag

            parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");

            parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
            if (instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED) {
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 2"));
            }
            emitReturnNextInstruction(b, instr);
        }
    }

    private void emitLoadLocalHandler(InstructionModel instr, CodeTreeBuilder b) {
        boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
        String localsFrame = parent.localFrame();
        if (instr.isQuickening() || tier.isUncached() || !parent.model.usesBoxingElimination()) {
            if (materialized) {
                localsFrame = "materializedFrame";
                b.startDeclaration(types.FrameWithoutBoxing, "materializedFrame");
                b.cast(types.FrameWithoutBoxing).string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1"));
                b.end();
            }

            final TypeMirror inputType = instr.signature.returnType;
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            CodeTree readSlot = BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX));
            if (materialized) {
                b.declaration(type(int.class), "slot", readSlot);
                b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                    b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                }
                emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", null, "localIndex");
                readSlot = CodeTreeBuilder.singleString("slot");
            }

            boolean generic = ElementUtils.typeEquals(type(Object.class), slotType);

            if (!generic) {
                b.startTryBlock();
            }

            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, inputType).string("frame");
            if (materialized) {
                b.string("sp - 1"); // overwrite the materialized frame
            } else {
                b.string("sp");
            }
            if (generic) {
                BytecodeRootNodeElement.startRequireFrame(b, slotType).string(localsFrame).tree(readSlot).end();
            } else {
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, slotType).tree(readSlot).end();
            }
            b.end();
            b.end(); // statement

            if (!generic) {
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();
                b.end();
            }

        } else {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            if (materialized) {
                localsFrame = "materializedFrame";
                b.startDeclaration(types.FrameWithoutBoxing, "materializedFrame");
                b.cast(types.FrameWithoutBoxing).string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1"));
                b.end();
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                b.declaration(type(byte[].class), "localTags", readLocalTagsFastPath());
            }

            b.declaration(type(int.class), "slot", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)));
            if (materialized) {
                b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
            }
            String localIndex;
            if (parent.model.enableBlockScoping) {
                b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = "localIndex";
            } else {
                localIndex = "slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX;
            }

            String bytecodeNode;
            if (materialized) {
                emitValidateMaterializedAccess(b, localsFrame, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                bytecodeNode = "bytecodeNode";
            } else {
                bytecodeNode = "this";
            }

            b.startDeclaration(type(byte.class), "tag");
            b.startCall(bytecodeNode, "getCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.string(localIndex);
            b.end(); // call
            b.end(); // declaration

            b.declaration(type(Object.class), "value");
            b.declaration(type(short.class), "newInstruction");
            InstructionModel genericInstruction = instr.findGenericInstruction();
            b.startTryBlock();

            b.startSwitch().string("tag").end().startBlock();
            for (TypeMirror boxingType : parent.model.boxingEliminatedTypes) {
                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);

                b.startCase().staticReference(parent.frameTagsElement.get(boxingType)).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(boxedInstruction)).end();
                parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + boxedInstruction.getQuickeningName());
                b.startStatement();
                b.string("value = ");
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, boxingType).string("slot").end();
                b.end();
                b.statement("break");
                b.end();
            }

            b.startCase().staticReference(parent.frameTagsElement.getObject()).end();
            b.startCase().staticReference(parent.frameTagsElement.getIllegal()).end();
            b.startCaseBlock();
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
            parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ");
            BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, type(Object.class)).string("slot").end();
            b.end();
            b.statement("break");
            b.end();

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
            b.end();

            b.end(); // switch

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            // If a FrameSlotException occurs, specialize to the generic version.
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
            parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ex.getResult()");
            b.end();

            b.end(); // catch

            parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame");
            if (materialized) {
                b.string("sp - 1"); // overwrite the materialized frame
            } else {
                b.string("sp");
            }
            b.string("value").end();
            b.end();
        }
    }

    private void emitPopHandler(InstructionModel instr, CodeTreeBuilder b) {
        if (instr.isQuickening() || tier.isUncached() || !parent.model.usesBoxingElimination()) {
            TypeMirror inputType = instr.signature.getDynamicOperandType(0);
            boolean isGeneric = ElementUtils.isObject(inputType);

            if (isGeneric) {
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
            } else {
                b.startIf().string("frame.getTag(sp - 1) != ").staticReference(parent.frameTagsElement.get(inputType)).end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();
                b.end();

                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.lineComment("Always clear in compiled code for liveness analysis");
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                b.end();
            }
        } else {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            InstructionModel genericInstruction = null;
            for (InstructionModel specialization : specializations) {
                if (parent.model.isBoxingEliminated(specialization.specializedType)) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else if (specialization.specializedType == null) {
                    genericInstruction = specialization;
                }
            }

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            // Pop may not have a valid child bci.
            b.startIf().string("operandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));
            b.startStatement();
            b.type(type(Object.class)).string(" value = ");
            BytecodeRootNodeElement.startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
            b.end();

            boolean elseIf = false;
            for (var entry : typeToSpecialization.entrySet()) {
                TypeMirror typeGroup = entry.getKey();
                elseIf = b.startIf(elseIf);
                b.string("value instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
                b.newLine().string("     (newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
                b.end().startBlock();

                InstructionModel specialization = entry.getValue();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(specialization)).end();
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = undoQuickening(operand)");
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
            b.end();

            parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");

            b.end(); // case operandIndex != -1
            b.startElseBlock();
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
            b.end(); // case operandIndex == -1

            parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
        }
    }

    private void emitLoadConstantHandler(InstructionModel instr, CodeTreeBuilder b) {
        TypeMirror returnType = instr.signature.returnType;
        if (tier.isUncached() || (parent.model.usesBoxingElimination() && !ElementUtils.isObject(returnType))) {
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, returnType).string("frame").string("sp");
            b.tree(parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.CONSTANT)), "this.constants", returnType));
            b.end();
            b.end();
        } else {
            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end(2).startBlock();
            b.statement("loadConstantCompiled(frame, bc, bci, sp)");
            b.end().startElseBlock();
            b.statement(BytecodeRootNodeElement.setFrameObject("sp",
                            parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.CONSTANT)), "this.constants").toString()));
            b.end();
        }
    }

    private void emitLoadArgumentHandler(InstructionModel instr, CodeTreeBuilder b) {
        if (instr.isReturnTypeQuickening()) {
            TypeMirror returnType = instr.signature.returnType;
            b.startTryBlock();
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, returnType).string("frame").string("sp");
            b.startGroup();
            b.startStaticCall(parent.lookupExpectMethod(type(Object.class), returnType));
            b.string(parent.localFrame() + ".getArguments()[" + BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.SHORT)).toString() + "]");
            b.end(); // expect
            b.end(); // argument group
            b.end(); // set frame
            b.end(); // statement
            b.end().startCatchBlock(types.UnexpectedResultException, "e"); // try
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            parent.emitQuickening(b, "this", "bc", "bci", null,
                            b.create().tree(parent.createInstructionConstant(instr.getQuickeningRoot())).build());
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("sp");
            b.string("e.getResult()");
            b.end(); // set frame
            b.end(); // statement
            b.end(); // catch block
        } else {
            InstructionImmediate argIndex = instr.getImmediate(ImmediateKind.SHORT);
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string("frame").string("sp");
            b.startGroup();
            b.string(parent.localFrame() + ".getArguments()[" + BytecodeRootNodeElement.readImmediate("bc", "bci", argIndex).toString() + "]");
            b.end(); // argument group
            b.end(); // set frame
            b.end(); // statement
        }
    }

    private void emitTagLeaveAndSpecializeHandler(InstructionModel instr, CodeTreeBuilder b) {
        Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
        List<InstructionModel> specializations = instr.quickenedInstructions;
        InstructionModel genericInstruction = null;
        for (InstructionModel specialization : specializations) {
            if (parent.model.isBoxingEliminated(specialization.specializedType)) {
                typeToSpecialization.put(specialization.specializedType, specialization);
            } else if (specialization.specializedType == null) {
                genericInstruction = specialization;
            }
        }

        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

        b.declaration(type(short.class), "newInstruction");
        b.declaration(type(short.class), "newOperand");
        b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
        b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

        b.startStatement();
        b.type(type(Object.class)).string(" value = ");
        BytecodeRootNodeElement.startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
        b.end();

        boolean elseIf = false;
        for (var entry : typeToSpecialization.entrySet()) {
            TypeMirror typeGroup = entry.getKey();
            elseIf = b.startIf(elseIf);
            b.string("value instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
            b.newLine().string("     (newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
            b.end().startBlock();

            InstructionModel specialization = entry.getValue();
            b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(specialization)).end();
            b.end(); // else block
            b.end(); // if block
        }

        b.startElseBlock(elseIf);
        b.statement("newOperand = undoQuickening(operand)");
        b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(genericInstruction)).end();
        b.end();

        parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
        parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

        InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
        b.startDeclaration(parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
        b.end();
        b.startStatement().startCall("tagNode.findProbe().onReturnValue");
        b.string(parent.localFrame());
        b.string("value");
        b.end(2);
    }

    private void emitTagLeaveHandler(InstructionModel instr, CodeTreeBuilder b) {
        TypeMirror inputType = instr.specializedType == null ? instr.signature.getDynamicOperandType(0) : instr.specializedType;
        TypeMirror returnType = instr.signature.returnType;
        boolean isSpecialized = instr.specializedType != null;

        b.declaration(inputType, "returnValue");
        if (isSpecialized) {
            b.startTryBlock();
        }
        b.startAssign("returnValue");
        if (isSpecialized) {
            BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", inputType);
        } else {
            BytecodeRootNodeElement.startRequireFrame(b, inputType);
            b.string("frame");
        }
        b.string("sp - 1");
        b.end();
        b.end(); // declaration

        if (isSpecialized) {
            b.end().startCatchBlock(types.UnexpectedResultException, "ex");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startReturn();
            emitCallInstructionHandler(b, instr.getQuickeningRoot());
            b.end();
        }

        b.end();

        InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
        b.startDeclaration(parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
        b.end();
        b.startStatement().startCall("tagNode.findProbe().onReturnValue");
        b.string(parent.localFrame());
        b.string("returnValue");
        b.end(2);

        if (isSpecialized && !ElementUtils.typeEquals(inputType, returnType)) {
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, returnType).string("frame").string("sp - 1").string("returnValue").end();
            b.end();
        }
    }

    private void emitBranchFalseHandler(InstructionModel instr, CodeTreeBuilder b) {
        String booleanValue = "(boolean) " + BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1");
        if (tier.isUncached()) {
            b.startIf();
            b.string(booleanValue);
            // no need to clear in uncached
            b.end(); // if
            b.startBlock();
        } else {
            if (!parent.model.isBoxingEliminated(type(boolean.class))) {
                b.declaration(type(boolean.class), "condition", booleanValue);
            } else if (instr.isQuickening()) {
                TypeMirror inputType = instr.signature.getDynamicOperandType(0);

                b.declaration(type(boolean.class), "condition");
                b.startTryBlock();
                b.startAssign("condition");

                if (ElementUtils.isObject(inputType)) {
                    b.string("(boolean) ");
                }
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", inputType);
                b.string("sp - 1");
                b.end();
                b.end(); // declaration

                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
                b.end();

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn();
                emitCallInstructionHandler(b, instr.getQuickeningRoot());
                b.end();
                b.end();

            } else {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                b.startDeclaration(type(boolean.class), "condition");
                b.string("(boolean) ");
                BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", null);
                b.string("sp - 1");
                b.end(); // require frame
                b.end(); // declaration

                TypeMirror boxingType = type(boolean.class);

                if (instr.quickenedInstructions.size() != 2) {
                    throw new AssertionError("Unexpected quickening count");
                }

                InstructionModel boxedInstruction = null;
                InstructionModel unboxedInstruction = null;
                for (InstructionModel quickening : instr.getFlattenedQuickenedInstructions()) {
                    if (ElementUtils.isObject(quickening.signature.getDynamicOperandType(0))) {
                        boxedInstruction = quickening;
                    } else {
                        unboxedInstruction = quickening;
                    }
                }

                if (boxedInstruction == null || unboxedInstruction == null) {
                    throw new AssertionError("Unexpected quickenings");
                }

                b.declaration(type(short.class), "newInstruction");
                b.declaration(type(short.class), "newOperand");
                b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.BYTECODE_INDEX, "child0")));
                b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

                b.startIf().string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(unboxedInstruction)).end();
                parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "BranchFalse$" + unboxedInstruction.getQuickeningName());
                b.end().startElseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.createInstructionConstant(boxedInstruction)).end();
                b.startStatement().string("newOperand = operand").end();
                parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "BranchFalse$" + boxedInstruction.getQuickeningName());
                b.end(); // else block

                parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
                parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

                b.lineComment("no need clear boolean locals in slow-path");
            }

            b.startIf();
            b.startCall("profileBranch");
            b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BRANCH_PROFILE)));
            b.string("condition");
            b.end();
            b.end().startBlock();
        }
        b.statement("return bci + " + instr.getInstructionLength());
        b.end().startElseBlock();
        b.statement("return " + BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate("branch_target")));
        b.end();
    }

    private void emitCustomShortCircuitHandler(InstructionModel instr, CodeTreeBuilder b) {
        ShortCircuitInstructionModel shortCircuitInstruction = instr.shortCircuitModel;

        b.startIf();
        if (tier.isCached()) {
            b.startCall("profileBranch");
            b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BRANCH_PROFILE)));
            b.startGroup();
        }

        if (shortCircuitInstruction.continueWhen()) {
            b.string("!");
        }
        b.string("(boolean) ").string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - 1"));

        if (tier.isCached()) {
            b.end(2); // profileBranch call
        }
        b.end().startBlock();
        /*
         * NB: Short circuit operations can evaluate to an operand or to the boolean conversion of
         * an operand. The stack is different in either case.
         */
        if (shortCircuitInstruction.producesBoolean()) {
            // Stack: [..., convertedValue]
            // leave convertedValue on the top of stack
        } else {
            // Stack: [..., value, convertedValue]
            // pop convertedValue
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
        }
        b.statement("return true");
        b.end().startElseBlock();
        if (shortCircuitInstruction.producesBoolean()) {
            // Stack: [..., convertedValue]
            // clear convertedValue
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
        } else {
            // Stack: [..., value, convertedValue]
            // clear convertedValue and value
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 1"));
            b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - 2"));
        }

        b.statement("return false");
        b.end(); // else
    }

    private static boolean isInstructionReachable(InstructionModel model) {
        return !model.isEpilogExceptional();
    }

    private void emitInvalidate(CodeTreeBuilder b) {
        if (tier.isCached()) {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        }
        emitBeforeReturnProfilingHandler(b);
        b.startReturn().string(parent.encodeState("bci", "sp")).end();
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
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(void.class), "doEpilogExceptional");

        method.addParameter(new CodeVariableElement(parent.asType(), "$root"));
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        method.addParameter(new CodeVariableElement(type(int.class), "bci"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));
        method.addParameter(new CodeVariableElement(types.AbstractTruffleException, "exception"));
        method.addParameter(new CodeVariableElement(type(int.class), "nodeId"));

        CodeTreeBuilder b = method.createBuilder();
        TypeMirror cachedType = BytecodeRootNodeElement.getCachedDataClassType(parent.model.epilogExceptional.operation.instruction);
        if (tier.isCached()) {
            b.declaration(cachedType, "node", "this.epilogExceptionalNode_");
        }

        List<CodeVariableElement> extraParams = createExtraParameters();
        buildCallExecute(b, parent.model.epilogExceptional.operation.instruction, "exception", extraParams);
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

    private CodeExecutableElement createReportLoopCount(InstructionModel instr) {
        CodeExecutableElement method = new CodeExecutableElement(
                        Set.of(PRIVATE),
                        type(Object.class), "reportLoopCount");

        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (parent.model.hasYieldOperation()) {
            method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
        }
        method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        method.addParameter(new CodeVariableElement(type(int.class), "bci"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));
        method.addParameter(new CodeVariableElement(type(int.class), "counter"));

        CodeTreeBuilder b = method.createBuilder();

        b.startStatement().startStaticCall(types.LoopNode, "reportLoopCount");
        b.string("this");
        b.string("counter");
        b.end().end(); // statement

        b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end().string("&&").startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("this").string(
                        "counter").end().end().startBlock();
        /**
         * When a while loop is compiled by OSR, its "false" branch profile may be zero, in which
         * case the compiler will stop at loop exits. To coerce the compiler to compile the code
         * after the loop, we encode the branch profile index in the branch.backwards instruction
         * and use it here to force the false profile to a non-zero value.
         */
        InstructionImmediate branchProfile = parent.model.branchBackwardInstruction.findImmediate(ImmediateKind.BRANCH_PROFILE, "loop_header_branch_profile");
        b.declaration(type(int.class), "branchProfileIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", branchProfile));
        b.startStatement().startCall("ensureFalseProfile").tree(BytecodeRootNodeElement.uncheckedCast(arrayOf(type(int.class)), "this.branchProfiles_")).string("branchProfileIndex").end(2);

        b.startReturn();
        b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
        b.string("this");
        String bci = BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)).toString();
        b.string(parent.encodeState(bci, "sp", parent.model.hasYieldOperation() ? "frame != " + parent.localFrame() : null));
        b.string("null"); // interpreterState
        b.string("null"); // beforeTransfer
        b.string("frame"); // parentFrame
        b.end(); // static call
        b.end(); // return

        b.end(); // if pollOSRBackEdge

        b.statement("return null");
        return method;

    }

    static void emitCopyStackToLocalFrameBeforeYield(CodeTreeBuilder b, InstructionModel instr) {
        b.statement("int maxLocals = getRoot().maxLocals");
        /*
         * The yield result will be stored at sp + stackEffect - 1 = sp + (1 - n) - 1 = sp - n (for
         * n dynamic operands). We need to copy operands lower on the stack for resumption.
         */
        String yieldResultIndex = (instr.signature.dynamicOperandCount == 0) ? "sp" : "sp - " + instr.signature.dynamicOperandCount;
        b.lineCommentf("The yield result will be stored at %s. The operands below it need to be preserved for resumption.", yieldResultIndex);
        b.lineCommentf("These operands belong to the interval [maxLocals, %s).", yieldResultIndex);
        b.startIf().string("maxLocals < " + yieldResultIndex).end().startBlock();
        b.statement(BytecodeRootNodeElement.copyFrameTo("frame", "maxLocals", "localFrame", "maxLocals", yieldResultIndex + " - maxLocals"));
        b.end();
    }

    private boolean localAccessNeedsLocalTags(InstructionModel instr) {
        // Local tags are only used for cached interpreters with BE. They need to be read
        // separately for materialized accesses, not passed into the method.
        return !instr.kind.isLocalVariableMaterializedAccess() && parent.model.usesBoxingElimination() && tier.isCached();
    }

    private CodeTree readLocalTagsFastPath() {
        return BytecodeRootNodeElement.uncheckedCast(type(byte[].class), "this.localTags_");
    }

    /**
     * Helper that emits common validation code for materialized local reads/writes.
     * <p>
     * If {@code localRootVariable} or {@code bytecodeNodeVariable} are provided, declares and
     * initializes locals with those names.
     */
    private void emitValidateMaterializedAccess(CodeTreeBuilder b, String frame, String localRootIndex, String localRootVariable, String bytecodeNodeVariable, String localIndex) {
        CodeTree getRoot = CodeTreeBuilder.createBuilder() //
                        .startCall("this.getRoot().getBytecodeRootNodeImpl") //
                        .string(localRootIndex) //
                        .end() //
                        .build();
        if (localRootVariable != null) {
            b.declaration(parent.asType(), localRootVariable, getRoot);
            getRoot = CodeTreeBuilder.singleString(localRootVariable);
        }

        b.startIf().tree(getRoot).string(".getFrameDescriptor() != ", frame, ".getFrameDescriptor()");
        b.end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Materialized frame belongs to the wrong root node.");
        b.end();

        CodeTree getBytecode = CodeTreeBuilder.createBuilder() //
                        .tree(getRoot) //
                        .string(".getBytecodeNodeImpl()") //
                        .end() //
                        .build();
        if (bytecodeNodeVariable != null) {
            b.declaration(parent.abstractBytecodeNode.asType(), bytecodeNodeVariable, getBytecode);
            getBytecode = CodeTreeBuilder.singleString(bytecodeNodeVariable);
        }
        /**
         * Check that the local is live at the current bci. We can only perform this check when the
         * bci is stored in the frame.
         */
        if (parent.model.enableBlockScoping && parent.model.storeBciInFrame && localIndex != null) {
            b.startAssert().startCall(getBytecode, "validateLocalLivenessInternal");
            b.string(frame);
            b.string("slot");
            b.string(localIndex);
            b.string("frame");
            b.string("bci");
            b.end(2);
        }

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
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "loadConstantCompiled");
        ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        ex.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "sp"));

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
            b.statement(BytecodeRootNodeElement.setFrameObject("sp", boundVariable + "." + getterMethods[i] + "()"));
            b.statement("return");
            b.end();
        }
        b.statement(BytecodeRootNodeElement.setFrameObject("sp", "constant"));

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

    private void emitCustomHandler(CodeTreeBuilder b, InstructionModel instr) throws AssertionError {
        List<CodeVariableElement> extraParams = createExtraParameters();
        boolean isCustomYield = instr.operation.kind == OperationKind.CUSTOM_YIELD;
        // Since an instruction produces at most one value, stackEffect is at most 1.
        int stackEffect = instr.getStackEffect();

        if (BytecodeRootNodeElement.isStoreBciBeforeExecute(parent.model, tier, instr)) {
            storeBciInFrame(b);
        }

        TypeMirror cachedType = BytecodeRootNodeElement.getCachedDataClassType(instr);

        if (tier.isCached()) {
            // If not in the uncached interpreter, we need to retrieve the node for the call.
            if (instr.canUseNodeSingleton()) {
                b.startDeclaration(cachedType, "node").staticReference(cachedType, "SINGLETON").end();
            } else {
                CodeTree nodeIndex = BytecodeRootNodeElement.readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.NODE_PROFILE));
                CodeTree readNode = CodeTreeBuilder.createBuilder().tree(parent.readNodeProfile(cachedType, nodeIndex)).build();
                b.declaration(cachedType, "node", readNode);
            }
        }

        if (isCustomYield) {
            emitCopyStackToLocalFrameBeforeYield(b, instr);
        }

        boolean unexpectedValue = BytecodeRootNodeElement.hasUnexpectedExecuteValue(instr);
        if (unexpectedValue) {
            b.startTryBlock();
        }

        buildCallExecute(b, instr, null, extraParams);

        if (instr.nonNull && !instr.signature.isVoid) {
            b.startStatement().startStaticCall(type(Objects.class), "requireNonNull");
            b.string("result").doubleQuote("The operation " + instr.operation.name + " must return a non-null value, but did return a null value.");
            b.end().end();
        }

        // Update the stack.
        if (!instr.signature.isVoid) {
            b.startStatement();
            if (instr.isReturnTypeQuickening()) {
                BytecodeRootNodeElement.startSetFrame(b, instr.signature.returnType);
            } else {
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class));
            }

            b.string("frame");
            if (stackEffect == 1) {
                b.string("sp");
            } else {
                b.string("sp - " + (1 - stackEffect));
            }
            b.string("result");
            b.end(); // setFrame
            b.end(); // statement
        }

        if (unexpectedValue) {
            b.end().startCatchBlock(types.UnexpectedResultException, "ex");
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            if (isBoxingOverloadReturnTypeQuickening(instr)) {
                InstructionModel generic = instr.quickeningBase.findGenericInstruction();
                if (generic == instr) {
                    throw new AssertionError("Unexpected generic instruction.");
                }
                parent.emitQuickening(b, "this", "bc", "bci", null, parent.createInstructionConstant(generic));
            }

            if (!instr.signature.isVoid) {
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class));
                b.string("frame");
                if (stackEffect == 1) {
                    b.string("sp");
                } else {
                    b.string("sp - " + (1 - stackEffect));
                }
                b.string("ex.getResult()");
                b.end(); // setFrame
                b.end(); // statement
            }
            b.end(); // catch
        }

        // When stackEffect is negative, values should be cleared from the top of the
        // stack.
        InstructionModel quickeningRoot = instr.getQuickeningRoot();
        int operandIndex = 0;
        for (int i = stackEffect; i < 0; i++) {
            TypeMirror genericType = quickeningRoot.signature.operandTypes.get(operandIndex);
            if (ElementUtils.isPrimitive(genericType)) {
                /*
                 * If the generic type is primitive we can omit clearing in the interpreter, as the
                 * clear is only needed for liveness in the compiler. Currently, we can't do that
                 * for specialized quickenings as we might miss quickeninging reference types on the
                 * stack if executeAndSpecialize is called. In the future when we keep all stack
                 * effects in this method we be able to do better and avoid clearing also for
                 * quickenings.
                 */
                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - " + -i));
                b.end();
            } else {
                b.statement(BytecodeRootNodeElement.clearFrame("frame", "sp - " + -i));
            }
            operandIndex++;
        }
    }

    private void storeBciInFrame(CodeTreeBuilder b) {
        if (BytecodeRootNodeElement.isStoreBciEnabled(parent.model, tier)) {
            BytecodeRootNodeElement.storeBciInFrame(b, parent.localFrame(), "bci");
        }
    }

    static boolean isBoxingOverloadReturnTypeQuickening(InstructionModel instr) {
        if (!instr.isReturnTypeQuickening()) {
            return false;
        }
        SpecializationData specialization = instr.resolveSingleSpecialization();
        if (specialization == null) { // multiple specializations handled
            return false;
        }
        return specialization.getBoxingOverloads().size() > 0;
    }

    static void emitCustomStackEffect(CodeTreeBuilder continueAtBuilder, int stackEffect) {
        if (stackEffect > 0) {
            continueAtBuilder.statement("sp += " + stackEffect);
        } else if (stackEffect < 0) {
            continueAtBuilder.statement("sp -= " + -stackEffect);
        }
    }

    private List<CodeVariableElement> createExtraParameters() {
        return List.of(
                        new CodeVariableElement(type(byte[].class), "bc"),
                        new CodeVariableElement(type(int.class), "bci"),
                        new CodeVariableElement(type(int.class), "sp"));
    }

    private void buildCallExecute(CodeTreeBuilder b, InstructionModel instr, String evaluatedArg, List<CodeVariableElement> extraParams) {
        boolean isVoid = instr.signature.isVoid;
        TypeMirror cachedType = BytecodeRootNodeElement.getCachedDataClassType(instr);

        b.startStatement();
        if (!isVoid) {
            b.type(instr.signature.returnType);
            b.string(" result = ");
        }
        if (tier.isUncached()) {
            b.staticReference(cachedType, "UNCACHED").startCall(".executeUncached");
        } else {
            b.startCall("node", BytecodeRootNodeElement.executeMethodName(instr));
        }

        // If we support yield, the frame forwarded to specializations should be the local frame
        // and not the stack frame.
        b.string(parent.localFrame());

        if (evaluatedArg != null) {
            b.string(evaluatedArg);
        } else if (tier.isUncached()) {
            // The uncached version takes all of its parameters. Other versions compute them.
            for (ConstantOperandModel constantOperand : instr.operation.constantOperands.before()) {
                b.tree(parent.readConstantImmediate("bc", "bci", "this", instr.constantOperandImmediates.get(constantOperand), constantOperand.type()));
            }

            for (int i = 0; i < instr.signature.dynamicOperandCount; i++) {
                TypeMirror targetType = instr.signature.getDynamicOperandType(i);
                b.startGroup();
                if (!ElementUtils.isObject(targetType)) {
                    b.cast(targetType);
                }
                b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - " + (instr.signature.dynamicOperandCount - i)));
                b.end();
            }

            for (ConstantOperandModel constantOperand : instr.operation.constantOperands.after()) {
                b.tree(parent.readConstantImmediate("bc", "bci", "this", instr.constantOperandImmediates.get(constantOperand), constantOperand.type()));
            }
        }

        if (parent.model.hasYieldOperation()) {
            b.string("frame"); // passed for $stackFrame
        }
        b.string("this");
        b.variables(extraParams);
        b.end(); // call
        b.end(); // statement
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
