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

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class ContinuationRootNodeImplElement extends AbstractElement {

    ContinuationRootNodeImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRootNodeImpl");
        this.setEnclosingElement(parent);
        this.setSuperClass(types.ContinuationRootNode);

        this.add(new CodeVariableElement(Set.of(FINAL), parent.asType(), "root"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "sp"));
        this.add(parent.compFinal(new CodeVariableElement(Set.of(VOLATILE), types.BytecodeLocation, "location")));
    }

    void lazyInit() {
        CodeExecutableElement constructor = this.add(GeneratorUtils.createConstructorUsingFields(
                        Set.of(), this,
                        ElementFilter.constructorsIn(types.RootNode.asElement().getEnclosedElements()).stream().filter(
                                        x -> x.getParameters().size() == 1).findFirst().get()));
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startCall("super");
        b.string("BytecodeRootNodesImpl.VISIBLE_TOKEN");
        b.string("language");
        b.startNew(types.FrameDescriptor).end();
        b.end(2);
        b.statement("this.root = root");
        b.statement("this.sp = sp");
        b.statement("this.location = location");

        this.add(createExecute());
        this.add(createSyncToMaterializedFrame());
        this.add(createGetSourceRootNode());
        this.add(createGetLocation());
        this.add(createFindFrame());
        this.add(createUpdateBytecodeLocation());
        this.add(createToString());

        // RootNode overrides.
        this.add(createIsCloningAllowed());
        this.add(createIsCloneUninitializedSupported());
        this.add(createFindBytecodeIndex());
        this.addOptional(createPrepareForCompilation());
    }

    private CodeExecutableElement createExecute() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "execute", new String[]{"frame_"});

        CodeTreeBuilder b = ex.createBuilder();

        b.startDeclaration(types.FrameWithoutBoxing, "frame").cast(types.FrameWithoutBoxing).string("frame_").end();

        b.declaration(types.BytecodeLocation, "bytecodeLocation", "location");
        b.startDeclaration(parent.abstractBytecodeNode.asType(), "bytecodeNode");
        b.startGroup().cast(parent.abstractBytecodeNode.asType()).string("bytecodeLocation.getBytecodeNode()").end();
        b.end();

        if (parent.model.usesBoxingElimination()) {
            b.startIf().string("!bytecodeNode.checkStableTagsAssumption()").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.end();
        }

        b.statement("Object[] args = frame.getArguments()");
        b.startIf().string("args.length != 2").end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Expected 2 arguments: (parentFrame, inputValue)");
        b.end();

        b.startDeclaration(types.FrameWithoutBoxing, "parentFrame");
        b.cast(types.FrameWithoutBoxing).string("args[0]");
        b.end();
        b.declaration(type(Object.class), "inputValue", "args[1]");

        b.startIf().string("parentFrame.getFrameDescriptor() != root.getFrameDescriptor()").end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Invalid continuation parent frame passed");
        b.end();

        b.declaration(types.FrameWithoutBoxing, "targetFrame", "parentFrame");

        b.startIf().string("CompilerDirectives.inCompiledCode()").end().startBlock();
        b.lineComment("Create a fresh virtual frame in compiled code so frame accesses can be optimized.");
        b.lineComment("If this execution deoptimizes, continueAt syncs active stack operands back to parentFrame.");
        b.lineComment("Execution then re-enters the bytecode loop with the materialized frame.");
        b.startDeclaration(types.FrameWithoutBoxing, "virtualFrame");
        b.cast(types.FrameWithoutBoxing);
        b.startCall(CodeTreeBuilder.createBuilder().startStaticCall(types.Truffle, "getRuntime").end().build(), "createVirtualFrame");
        b.string("parentFrame.getArguments()");
        b.string("root.getFrameDescriptor()");
        b.end();
        b.end();

        emitRestoreStackOperands(b, "parentFrame", "virtualFrame");
        b.statement(BytecodeRootNodeElement.setFrameObject("virtualFrame", BytecodeRootNodeElement.CONTINUATION_FRAME_INDEX, "parentFrame"));
        b.statement("targetFrame = virtualFrame");
        b.end();

        b.statement(BytecodeRootNodeElement.setFrameObject("targetFrame", "sp - 1", "inputValue"));

        b.startReturn();
        b.startCall("root.continueAt");
        b.string("bytecodeNode");
        b.string("bytecodeLocation.getBytecodeIndex()");
        b.string("sp");
        b.string("targetFrame");
        b.string("this");
        b.end(2);

        return ex;
    }

    private static void emitRestoreStackOperands(CodeTreeBuilder b, String srcFrame, String dstFrame) {
        b.startIf().string("root.stackBase < sp - 1").end().startBlock();
        b.lineComment("Restore any stack operands below the resume value.");
        b.lineComment("These operands belong to the interval [stackBase, sp - 1).");
        b.statement(BytecodeRootNodeElement.copyFrameTo(srcFrame, "root.stackBase", dstFrame, "root.stackBase", "sp - 1 - root.stackBase"));
        b.end();
    }

    private CodeExecutableElement createSyncToMaterializedFrame() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(), types.FrameWithoutBoxing, "syncToMaterializedFrame");
        ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        ex.addParameter(new CodeVariableElement(type(int.class), "currentSp"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("!").tree(parent.hasContinuationFrame("frame")).end().startBlock();
        b.startReturn().string("frame").end();
        b.end();

        b.startDeclaration(types.FrameWithoutBoxing, "materializedFrame");
        b.cast(types.FrameWithoutBoxing).string("frame.getObject(" + BytecodeRootNodeElement.CONTINUATION_FRAME_INDEX + ")");
        b.end();
        b.startIf().string("root.stackBase < currentSp").end().startBlock();
        b.statement(BytecodeRootNodeElement.copyFrameTo("frame", "root.stackBase",
                        "materializedFrame", "root.stackBase", "currentSp - root.stackBase"));
        b.end();
        b.startReturn().string("materializedFrame").end();
        return ex;
    }

    private CodeExecutableElement createGetSourceRootNode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "getSourceRootNode");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("root").end();
        return ex;
    }

    private CodeExecutableElement createGetLocation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "getLocation");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("location").end();
        return ex;
    }

    private CodeExecutableElement createFindFrame() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "findFrame", new String[]{"frame"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.cast(types.Frame);
        b.string("frame.getArguments()[0]");
        b.end();
        return ex;
    }

    private CodeExecutableElement createUpdateBytecodeLocation() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "updateBytecodeLocation");
        ex.addParameter(new CodeVariableElement(types.BytecodeLocation, "newLocation"));
        ex.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "oldBytecode"));
        ex.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "newBytecode"));
        ex.addParameter(new CodeVariableElement(parent.context.getDeclaredType(CharSequence.class), "replaceReason"));

        CodeTreeBuilder b = ex.createBuilder();
        b.tree(createNeverPartOfCompilation());
        b.statement("location = newLocation");

        b.lineComment("We avoid reporting replacement when an update does not change the bytecode (e.g., a source reparse).");
        b.startIf().string("oldBytecode.bytecodes != newBytecode.bytecodes").end().startBlock();
        b.startStatement().startCall("reportReplace");
        b.string("oldBytecode");
        b.string("newBytecode");
        b.string("replaceReason");
        b.end(2);
        b.end();

        return ex;
    }

    private CodeExecutableElement createToString() {
        CodeExecutableElement ex = GeneratorUtils.override(parent.context.getDeclaredType(Object.class), "toString");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startStaticCall(type(String.class), "format");
        b.doubleQuote("%s(resume_bci=%s)");
        b.string("root");
        b.string("location == null ? \"unreachable\" : location.getBytecodeIndex()");
        b.end(2);
        return ex;
    }

    private CodeExecutableElement createIsCloningAllowed() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloningAllowed");
        CodeTreeBuilder b = ex.createBuilder();
        b.lineComment("Continuations are unique.");
        b.startReturn();
        b.string("false");
        b.end();
        return ex;
    }

    private CodeExecutableElement createIsCloneUninitializedSupported() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloneUninitializedSupported");
        CodeTreeBuilder b = ex.createBuilder();
        b.lineComment("Continuations are unique.");
        b.startReturn();
        b.string("false");
        b.end();
        return ex;
    }

    private CodeExecutableElement createFindBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "findBytecodeIndex", new String[]{"node", "frame"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startCall("root", "findBytecodeIndex");
        b.string("node");
        // unwrap the frame from the continuation frame
        b.startGroup().string("frame == null ? null : ");
        b.startCall("findFrame").string("frame").end();
        b.end();
        b.end(2);
        return ex;
    }

    private CodeExecutableElement createPrepareForCompilation() {
        if (!parent.model.enableUncachedInterpreter) {
            return null;
        }

        CodeExecutableElement ex = BytecodeRootNodeElement.overrideImplementRootNodeMethod(parent.model, "prepareForCompilation", new String[]{"rootCompilation", "compilationTier", "lastTier"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startCall("root.prepareForCompilation").string("rootCompilation").string("compilationTier").string("lastTier").end();
        b.string(" && ");
        // Even if the root is ready, the continuation location may not have updated yet.
        b.string("location.getBytecodeNode().getTier() != ").staticReference(types.BytecodeTier, "UNCACHED");
        b.end();
        return ex;
    }

    void addRootNodeDelegateMethods() {
        List<ExecutableElement> existing = ElementFilter.methodsIn(this.getEnclosedElements());

        List<ExecutableElement> excludes = List.of(
                        // Not supported (see isCloningAllowed, isCloneUninitializedSupported).
                        ElementUtils.findMethod(types.RootNode, "copy"),
                        ElementUtils.findMethod(types.RootNode, "cloneUninitialized"),
                        // User code can only obtain a continuation root by executing a yield.
                        // Parsing is done at this point, so the root is already prepared.
                        ElementUtils.findMethod(types.RootNode, "prepareForCall"),
                        // The instrumenter should already know about/have instrumented the root
                        // node by the time we try to instrument a continuation root.
                        ElementUtils.findMethod(types.RootNode, "isInstrumentable"),
                        ElementUtils.findMethod(types.RootNode, "prepareForInstrumentation"));

        outer: for (ExecutableElement rootNodeMethod : ElementUtils.getOverridableMethods((TypeElement) types.RootNode.asElement())) {
            // Exclude methods we have already implemented.
            for (ExecutableElement implemented : existing) {
                if (ElementUtils.signatureEquals(implemented, rootNodeMethod)) {
                    continue outer;
                }
            }
            // Exclude methods we do not wish to implement.
            for (ExecutableElement exclude : excludes) {
                if (ElementUtils.signatureEquals(exclude, rootNodeMethod)) {
                    continue outer;
                }
            }
            // Only delegate to methods overridden by the generated class or its parents.
            ExecutableElement templateMethod = ElementUtils.findOverride(rootNodeMethod, parent);
            if (templateMethod == null) {
                continue outer;
            }

            CodeExecutableElement delegateMethod = GeneratorUtils.override(templateMethod);
            CodeTreeBuilder b = delegateMethod.createBuilder();

            boolean isVoid = ElementUtils.isVoid(delegateMethod.getReturnType());
            if (isVoid) {
                b.startStatement();
            } else {
                b.startReturn();
            }

            b.startCall("root", templateMethod.getSimpleName().toString());
            for (VariableElement param : templateMethod.getParameters()) {
                b.variable(param);
            }
            b.end(); // call
            b.end(); // statement / return

            this.add(delegateMethod);
        }
    }
}
