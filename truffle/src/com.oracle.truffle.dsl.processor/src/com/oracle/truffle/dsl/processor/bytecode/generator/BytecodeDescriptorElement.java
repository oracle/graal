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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOError;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class BytecodeDescriptorElement extends AbstractElement {

    BytecodeDescriptorElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Bytecode");
    }

    void lazyInit() {
        TypeMirror superType = BytecodeDSLCodeGenerator.findBytecodeVariantType(parent.abstractBuilderType);
        if (superType == null) {
            // regular case
            superType = generic(types.BytecodeDescriptor, parent.model.getTemplateType().asType(), parent.model.languageClass, parent.builder.asType());
        }
        setSuperClass(superType);

        CodeExecutableElement c = this.add(new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString()));
        CodeTreeBuilder b = c.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();

        this.add(createGetRootNodeClass());
        this.add(createGetGeneratedClass());
        this.add(createCast());
        this.add(createGetLanguageClass());
        this.add(createGetInstructionDescriptors());
        this.add(createGetInstructionDescriptor());
        this.add(createCreate());
        this.add(createNewConfigBuilder());

        this.add(createPrepareForCall());

        this.add(createAddInstructionTracer());
        this.add(createRemoveInstructionTracer());

        if (parent.model.enableSerialization) {
            this.add(createSerialize());
            this.add(createDeserialize());
        }
    }

    private CodeExecutableElement createPrepareForCall() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "prepareForCall");
        ex.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        ex.addParameter(new CodeVariableElement(parent.asType(), "rootNode"));
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("onPrepareForLoad(language, rootNode)");
        return ex;
    }

    private CodeExecutableElement createGetRootNodeClass() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "getSpecificationClass");
        ex.setReturnType(generic(type(Class.class), parent.model.getTemplateType().asType()));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().typeLiteral(parent.model.getTemplateType().asType()).end();
        return ex;
    }

    private CodeExecutableElement createGetGeneratedClass() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "getGeneratedClass");
        ex.setReturnType(generic(type(Class.class), new CodeTypeMirror.WildcardTypeMirror(parent.model.getTemplateType().asType(), null)));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().typeLiteral(parent.asType()).end();
        return ex;
    }

    private CodeExecutableElement createCast() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "cast", new String[]{"root"}, new TypeMirror[]{types.RootNode});
        ex.setReturnType(parent.model.getTemplateType().asType());
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("root.getClass() == ").typeLiteral(parent.asType()).end().startBlock();
        b.startReturn().cast(parent.asType(), "root").end();
        b.end();
        if (parent.model.enableYield) {
            b.startElseIf().string("root instanceof ").type(parent.continuationRootNodeImpl.asType()).string(" c").end().startBlock();
            b.statement("return c.root");
            b.end();
        }
        b.startElseBlock();
        b.statement("return null");
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetLanguageClass() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "getLanguageClass");
        ex.setReturnType(generic(type(Class.class), parent.model.languageClass));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().typeLiteral(parent.model.languageClass).end();
        return ex;
    }

    private CodeExecutableElement createAddInstructionTracer() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "addInstructionTracer", new String[]{"language", "tracer"});
        ex.changeTypes(parent.model.languageClass, types.InstructionTracer);
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.enableInstructionTracing) {
            parent.emitValidateInstructionTracer(b);
            b.statement("super.addInstructionTracer(language, tracer)");
        } else {
            b.startThrow().startNew(type(UnsupportedOperationException.class));
            b.doubleQuote("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.");
            b.end().end();
        }

        return ex;
    }

    private CodeExecutableElement createRemoveInstructionTracer() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "removeInstructionTracer", new String[]{"language", "tracer"});
        ex.changeTypes(parent.model.languageClass, types.InstructionTracer);
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.enableInstructionTracing) {
            parent.emitValidateInstructionTracer(b);
            b.statement("super.removeInstructionTracer(language, tracer)");
        } else {
            b.startThrow().startNew(type(UnsupportedOperationException.class));
            b.doubleQuote("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.");
            b.end().end();
        }
        return ex;
    }

    private CodeExecutableElement createGetInstructionDescriptors() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "getInstructionDescriptors");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().staticReference(parent.instructionDescriptorList.asType(), "INSTANCE").end();
        return ex;
    }

    private CodeExecutableElement createGetInstructionDescriptor() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "getInstructionDescriptor", new String[]{"operationCode"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("operationCode < 1 || operationCode >= ").staticReference(parent.instructionDescriptorImpl.asType(), "DESCRIPTORS.length").end().startBlock();
        b.returnNull();
        b.end();
        b.startReturn().staticReference(parent.instructionDescriptorImpl.asType(), "DESCRIPTORS").string("[operationCode]").end();
        return ex;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "create", new String[]{"language", "config", "parser"});
        ex.setReturnType(generic(types.BytecodeRootNodes, parent.model.templateType.asType()));
        ex.changeTypes(parent.model.languageClass, types.BytecodeConfig, parent.parserType);
        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("BytecodeRootNodesImpl", "nodes", "new BytecodeRootNodesImpl(parser, config)");
        b.startAssign("Builder builder").startNew(parent.builder.getSimpleName().toString());
        b.string("language");
        b.string("nodes");
        b.string("withGlobalConfig(language, BytecodeConfigEncoderImpl.decode(config))");
        b.end(2);

        b.startStatement().startCall("parser", "parse");
        b.string("builder");
        b.end(2);

        b.startStatement().startCall("builder", "finish").end(2);

        b.startReturn().string("nodes").end();

        return ex;
    }

    private CodeExecutableElement createNewConfigBuilder() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "newConfigBuilder");
        CodeTreeBuilder b = ex.getBuilder();
        b.startReturn();
        b.startStaticCall(parent.asType(), "newConfigBuilder").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createSerialize() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "serialize", new String[]{"buffer", "callback", "parser"});
        ex.changeTypes(type(DataOutput.class), types.BytecodeSerializer, parent.parserType);

        CodeTreeBuilder b = ex.createBuilder();

        b.startDeclaration(parent.bytecodeRootNodesImpl.asType(), "rootNodes");
        b.startNew(parent.bytecodeRootNodesImpl.asType());
        b.string("parser");
        b.staticReference(types.BytecodeConfig, "COMPLETE");
        b.end(); // new
        b.end(); // declaration

        b.startDeclaration(parent.bytecodeBuilderType, "builder");
        b.startNew("Builder");
        b.string("null"); // language not needed for serialization
        b.string("rootNodes"); // language not needed for serialization
        b.string("rootNodes.encoding"); // language not needed for serialization
        b.end(); // new
        b.end(); // declaration

        b.startTryBlock();

        b.startStatement().startCall("builder", "serialize");
        b.string("buffer");
        b.string("callback");
        b.string("null"); // existingNodes
        b.end().end();

        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createDeserialize() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDescriptor, "deserialize", new String[]{"language", "config", "input", "callback"});
        ex.setReturnType(generic(types.BytecodeRootNodes, parent.model.getTemplateType().asType()));
        ex.changeTypes(parent.model.languageClass, types.BytecodeConfig, generic(Supplier.class, DataInput.class), types.BytecodeDeserializer);
        CodeTreeBuilder b = ex.createBuilder();

        b.startTryBlock();

        if (ElementUtils.typeEquals(parent.abstractBuilderType, types.BytecodeBuilder)) {
            b.statement("return create(language, config, (b) -> b.deserialize(input, callback, null))");
        } else {
            b.statement("return create(language, config, (b) -> ((Builder) b).deserialize(input, callback, null))");
        }

        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();

        return ex;
    }

}
