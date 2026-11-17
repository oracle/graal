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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class BytecodeRootNodesImplElement extends AbstractElement {

    private CodeTypeElement updateReason;

    BytecodeRootNodesImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeRootNodesImpl");
    }

    void lazyInit() {
        this.setSuperClass(generic(types.BytecodeRootNodes, parent.model.templateType.asType()));
        this.setEnclosingElement(parent);
        this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(Object.class), "VISIBLE_TOKEN")).createInitBuilder().string("TOKEN");
        this.add(parent.compFinal(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), type(long.class), "encoding")));

        this.updateReason = this.add(createUpdateReason());
        this.add(createConstructor());
        this.add(createReparseImpl());
        this.add(createPerformUpdate());
        this.add(createSetNodes());
        this.add(createGetParserImpl());
        this.add(createValidate());
        this.add(createGetLanguage());
        this.add(createIsParsed());

        if (parent.model.enableInstructionTracing) {
            this.add(createAddInstructionTracer());
            this.add(createRemoveInstructionTracer());
            this.add(createFindInstructionTracerAccess());
            this.add(createUpdateGlobalInstructionTracers());
        }

        if (parent.model.enableSerialization) {
            this.add(createSerializeBuilderBytecode());
            this.add(createSerializeExecutionBytecode());
        }
    }

    private CodeTypeElement createUpdateReason() {
        DeclaredType charSequence = (DeclaredType) type(CharSequence.class);
        CodeTypeElement reason = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "UpdateReason");
        reason.getImplements().add(charSequence);

        reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newSources"));
        reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newInstrumentations"));
        reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newTags"));

        reason.add(GeneratorUtils.createConstructorUsingFields(Set.of(), reason));

        CodeExecutableElement length = reason.add(GeneratorUtils.override(charSequence, "length"));
        length.createBuilder().startReturn().string("toString().length()").end();

        CodeExecutableElement charAt = reason.add(GeneratorUtils.override(charSequence, "charAt", new String[]{"index"}));
        charAt.createBuilder().startReturn().string("toString().charAt(index)").end();

        CodeExecutableElement subSequence = reason.add(GeneratorUtils.override(charSequence, "subSequence", new String[]{"start", "end"}));
        subSequence.createBuilder().startReturn().string("toString().subSequence(start, end)").end();

        CodeExecutableElement toString = reason.add(GeneratorUtils.override(charSequence, "toString"));
        CodeTreeBuilder b = toString.createBuilder();
        b.startStatement().type(type(StringBuilder.class)).string(" message = ").startNew(type(StringBuilder.class)).end().end();
        String message = String.format("%s requested ", ElementUtils.getSimpleName(parent.model.getTemplateType()));
        b.startStatement().startCall("message", "append").doubleQuote(message).end().end();

        b.declaration(type(String.class), "sep", "\"\"");

        b.startIf().string("newSources != 0").end().startBlock();
        message = "SourceInformation";
        b.startStatement().startCall("message", "append").doubleQuote(message).end().end();
        if (parent.model.sourceContentSupplier != null) {
            b.startIf().string(parent.configEncoder.checkSourceContentBit("newSources")).end().startBlock();
            b.startStatement().startCall("message", "append").doubleQuote(" with content").end().end();
            b.end();
        }
        b.startAssign("sep").doubleQuote(", ").end();
        b.end();

        if (!parent.model.getInstrumentations().isEmpty()) {
            b.startIf().string("newInstrumentations != 0").end().startBlock();
            for (CustomOperationModel instrumentation : parent.model.getInstrumentations()) {
                OperationModel operation = instrumentation.operation;
                b.startIf().string(parent.configEncoder.checkInstrumentationEnabled("newInstrumentations", operation)).end().startBlock();
                b.startStatement().startCall("message", "append").string("sep").end().end();
                b.startStatement().startCall("message", "append").doubleQuote("Instrumentation[" + operation.name + "]").end().end();
                b.startAssign("sep").doubleQuote(", ").end();
                b.end();
            }
            b.end();
        }

        if (!parent.model.getProvidedTags().isEmpty()) {
            b.startIf().string("newTags != 0").end().startBlock();
            int index = 0;
            for (TypeMirror tag : parent.model.getProvidedTags()) {
                b.startIf().string(parent.configEncoder.checkTagEnabled("newTags", index)).end().startBlock();
                b.startStatement().startCall("message", "append").string("sep").end().end();
                b.startStatement().startCall("message", "append").doubleQuote("Tag[" + ElementUtils.getSimpleName(tag) + "]").end().end();
                b.startAssign("sep").doubleQuote(", ").end();
                b.end();
                index++;
            }
            b.end();
        }

        b.startStatement().startCall("message", "append").doubleQuote(".").end().end();
        b.statement("return message.toString()");
        return reason;

    }

    private CodeExecutableElement createConstructor() {
        CodeExecutableElement ctor = new CodeExecutableElement(null, "BytecodeRootNodesImpl");
        ctor.addParameter(new CodeVariableElement(parent.parserType, "generator"));
        ctor.addParameter(new CodeVariableElement(type(long.class), "encoding"));
        CodeTreeBuilder b = ctor.createBuilder();
        b.statement("super(VISIBLE_TOKEN, generator)");
        b.statement("this.encoding = encoding");

        return ctor;
    }

    private CodeExecutableElement createAddInstructionTracer() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "addInstructionTracer", new String[]{"tracer"});
        CodeTreeBuilder b = ex.createBuilder();
        parent.emitValidateInstructionTracer(b);

        b.startDeclaration(parent.configEncoder.asType(), "encoder").staticReference(parent.configEncoder.asType(), "INSTANCE").end();
        b.startStatement();
        b.string("updateImpl(encoder, encoder.encodeInstrumentation(").typeLiteral(types.InstructionTracer).string("))");
        b.end();

        b.startFor().type(parent.model.getTemplateType().asType()).string(" root : nodes").end().startBlock();
        b.startDeclaration(parent.asType(), "castRoot").cast(parent.asType(), "root").end();
        b.statement("findInstructionTracerAccess(castRoot).addLocalTracer(tracer)");
        b.statement("castRoot.invalidate(\"Local instruction tracer added.\")");
        b.end();
        return ex;
    }

    private CodeExecutableElement createRemoveInstructionTracer() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "removeInstructionTracer", new String[]{"tracer"});
        CodeTreeBuilder b = ex.createBuilder();
        parent.emitValidateInstructionTracer(b);
        b.startDeclaration(parent.configEncoder.asType(), "encoder").staticReference(parent.configEncoder.asType(), "INSTANCE").end();
        b.startStatement();
        b.string("updateImpl(encoder, encoder.encodeInstrumentation(").typeLiteral(types.InstructionTracer).string("))");
        b.end();

        b.startFor().type(parent.model.getTemplateType().asType()).string(" root : nodes").end().startBlock();
        b.startDeclaration(parent.asType(), "castRoot").cast(parent.asType(), "root").end();
        b.statement("findInstructionTracerAccess(castRoot).removeLocalTracer(tracer)");
        b.statement("castRoot.invalidate(\"Local instruction tracer removed.\")");
        b.end();
        return ex;
    }

    private CodeExecutableElement createUpdateGlobalInstructionTracers() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "updateGlobalInstructionTracers", new String[]{"tracers"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startDeclaration(parent.configEncoder.asType(), "encoder").staticReference(parent.configEncoder.asType(), "INSTANCE").end();
        b.startStatement();
        b.string("updateImpl(encoder, encoder.encodeInstrumentation(").typeLiteral(types.InstructionTracer).string("))");
        b.end();
        b.startFor().type(parent.model.getTemplateType().asType()).string(" root : nodes").end().startBlock();
        b.startDeclaration(parent.asType(), "castRoot").cast(parent.asType(), "root").end();
        b.statement("findInstructionTracerAccess(castRoot).updateGlobalTracers(tracers)");
        b.statement("castRoot.invalidate(\"Global instruction tracers updated.\")");
        b.end();
        return ex;
    }

    private CodeExecutableElement createFindInstructionTracerAccess() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), parent.instructionTracerAccessImplElement.asType(), "findInstructionTracerAccess");
        ex.addParameter(new CodeVariableElement(parent.asType(), "root"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.cast(parent.instructionTracerAccessImplElement.asType()).string("root.getBytecodeNodeImpl().constants[Builder.INSTRUCTION_TRACER_CONSTANT_INDEX]");
        b.end();
        return ex;
    }

    private CodeExecutableElement createReparseImpl() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "updateImpl", new String[]{"encoder", "encoding"});
        mergeSuppressWarnings(ex, "hiding");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("!isParsed()").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(type(IllegalStateException.class));
        b.doubleQuote("A reparse was attempted before parsing completed. This is likely a bug in the parser or serializer/deserializer.");
        b.end(2);
        b.end();

        b.startDeclaration(type(long.class), "maskedEncoding");
        b.startStaticCall(parent.configEncoder.asType(), "normalizeEncoding").string("encoder").string("encoding").end();
        b.end();
        b.declaration(type(long.class), "oldEncoding", "this.encoding");
        b.declaration(type(long.class), "newEncoding", "maskedEncoding | oldEncoding");

        b.startIf().string("(oldEncoding | newEncoding) == oldEncoding").end().startBlock();
        b.returnFalse();
        b.end();

        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.statement("return performUpdate(maskedEncoding)");

        return ex;
    }

    private CodeExecutableElement createPerformUpdate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.SYNCHRONIZED), type(boolean.class), "performUpdate");
        ex.addParameter(new CodeVariableElement(type(long.class), "maskedEncoding"));
        ex.getModifiers().add(Modifier.SYNCHRONIZED);
        CodeTreeBuilder b = ex.createBuilder();

        b.tree(createNeverPartOfCompilation());
        b.declaration(type(long.class), "oldEncoding", "this.encoding");
        b.declaration(type(long.class), "newEncoding", "maskedEncoding | oldEncoding");
        b.startIf().string("(oldEncoding | newEncoding) == oldEncoding").end().startBlock();
        b.lineComment("double checked locking");
        b.returnFalse();
        b.end();

        BytecodeConfigEncoderImplElement configEncoder = parent.configEncoder;
        b.declaration(type(int.class), "oldSources", configEncoder.decodeSourceBits("oldEncoding"));
        b.declaration(type(int.class), "oldInstrumentations", configEncoder.decodeInstrumentations("oldEncoding"));
        b.declaration(type(int.class), "oldTags", configEncoder.decodeTags("oldEncoding"));

        b.declaration(type(int.class), "newSources", configEncoder.decodeSourceBits("newEncoding"));
        b.declaration(type(int.class), "newInstrumentations", configEncoder.decodeInstrumentations("newEncoding"));
        b.declaration(type(int.class), "newTags", configEncoder.decodeTags("newEncoding"));

        b.statement("boolean needsBytecodeReparse = newInstrumentations != oldInstrumentations || newTags != oldTags");
        /*
         * Reparse if new source info was requested, or if the bytecode will be reparsed and source
         * info already exists (so we can update the bci-to-source mapping).
         */
        b.statement("boolean needsSourceReparse = newSources != oldSources || (needsBytecodeReparse && newSources != 0)");

        b.startIf().string("!needsBytecodeReparse && !needsSourceReparse").end().startBlock();
        b.statement("return false");
        b.end();

        if (parent.model.sourceContentSupplier != null) {
            b.declaration(type(boolean.class), "needsSourceContent", configEncoder.checkSourceContentBit("newSources"));
        }

        b.declaration(parent.parserType, "parser", "getParserImpl()");

        b.startStatement().type(updateReason.asType()).string(" reason = ").startNew(updateReason.asType());
        b.string("newSources & ~oldSources");
        b.string("newInstrumentations & ~oldInstrumentations");
        b.string("newTags & ~oldTags");
        b.end().end();

        // When we reparse, we add metadata to the existing nodes. The builder gets them here.
        b.startStatement().string(parent.builder.getSimpleName().toString(), " builder = ").startNew(parent.builder.getSimpleName().toString());
        b.string("this");
        b.string("needsBytecodeReparse");
        b.string("newTags");
        b.string("newInstrumentations");
        b.string("needsSourceReparse");
        if (parent.model.sourceContentSupplier != null) {
            b.string("needsSourceContent");
        }
        b.string("reason");
        b.end(2);

        b.startFor().type(parent.model.templateType.asType()).string(" node : nodes").end().startBlock();
        b.startStatement().startCall("builder.builtNodes.add");
        b.startGroup().cast(parent.asType()).string("node").end();
        b.end(2);
        b.end(2);

        b.startStatement().startCall("parser", "parse").string("builder").end(2);
        b.startStatement().startCall("builder", "finish").string("true").end(2);

        b.statement("this.encoding = newEncoding");
        b.statement("return true");

        return ex;
    }

    private CodeExecutableElement createSetNodes() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setNodes");
        ex.addParameter(new CodeVariableElement(arrayOf(parent.asType()), "nodes"));

        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("this.nodes != null").end().startBlock();
        b.startThrow().startNew(type(AssertionError.class)).end().end();
        b.end();

        b.statement("this.nodes = nodes");
        b.startFor().type(parent.asType()).string(" node : nodes").end().startBlock();
        b.startIf().string("node.getRootNodes() != this").end().startBlock();
        b.startThrow().startNew(type(AssertionError.class)).end().end();
        b.end();
        b.startIf().string("node != nodes[node.buildIndex]").end().startBlock();
        b.startThrow().startNew(type(AssertionError.class)).end().end();
        b.end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetParserImpl() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), parent.parserType, "getParserImpl");
        mergeSuppressWarnings(ex, "unchecked");
        CodeTreeBuilder b = ex.createBuilder();

        b.startReturn();
        b.cast(parent.parserType);
        b.startCall("super.getParser");
        b.end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createValidate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(boolean.class), "validate");
        ex.addParameter(new CodeVariableElement(generic(List.class, parent.asType()), "roots"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startFor().type(parent.asType()).string(" node : roots").end().startBlock();
        b.statement("node.getBytecodeNodeImpl().validateBytecodes(roots)");
        b.end();

        b.statement("return true");
        return ex;
    }

    private CodeExecutableElement createGetLanguage() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), parent.model.languageClass, "getLanguage");
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("nodes.length == 0").end().startBlock();
        b.startReturn().string("null").end();
        b.end();
        b.startReturn().startCall("nodes[0].getLanguage");
        b.typeLiteral(parent.model.languageClass);
        b.end(2);

        return ex;
    }

    public CodeExecutableElement createIsParsed() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "isParsed");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("nodes != null").end();
        return ex;
    }

    private CodeExecutableElement createSerializeBuilderBytecode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "serializeBuilderBytecode", new String[]{"buffer", "callback"});

        BytecodeRootNodeElement.addJavadoc(ex, """
                        {@inheritDoc}
                        """);

        CodeTreeBuilder b = ex.createBuilder();
        b.startStatement().startStaticCall(parent.serializationEncodingElement.asType(), "serializeBuilderBytecode");
        b.string("buffer");
        b.string("callback");
        b.string("getParserImpl()");
        b.string("this");
        b.end().end();

        return ex;
    }

    private CodeExecutableElement createSerializeExecutionBytecode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "serializeExecutionBytecode", new String[]{"buffer", "config", "callback"});

        BytecodeRootNodeElement.addJavadoc(ex, """
                        {@inheritDoc}
                        """);

        CodeTreeBuilder b = ex.createBuilder();
        b.startStatement().startStaticCall(parent.serializationEncodingElement.asType(), "serializeExecutionBytecode");
        b.string("getLanguage()");
        b.string("buffer");
        b.string("config");
        b.string("callback");
        b.string("getParserImpl()");
        b.string("this");
        b.end().end();

        return ex;
    }
}
