/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

/**
 * User code directly references some generated types and methods, like builder methods. When there
 * is an error in the model, this factory generates stubs for the user-accessible names to prevent
 * the compiler from emitting many unhelpful error messages about unknown types/methods.
 */
final class BytecodeRootNodeErrorElement extends CodeTypeElement {
    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();

    private final BytecodeDSLModel model;
    private final DeclaredType languageClass;
    private final BuilderElement builder;
    private final BytecodeDescriptorElement bytecodeDescriptorElement;
    private final TypeMirror parserType;
    private final TypeMirror abstractBuilderType;

    BytecodeRootNodeErrorElement(BytecodeDSLModel model, TypeMirror abstractBuilderType) {
        super(Set.of(PUBLIC, FINAL), ElementKind.CLASS, ElementUtils.findPackageElement(model.getTemplateType()), model.getName());
        this.model = model;
        this.languageClass = model.languageClass == null ? generic(types.TruffleLanguage) : model.languageClass;
        this.abstractBuilderType = abstractBuilderType;
        this.setSuperClass(model.templateType.asType());
        GeneratorUtils.addGeneratedBy(context, this, model.templateType);
        this.builder = this.add(new BuilderElement());
        this.parserType = generic(types.BytecodeParser, abstractBuilderType == null ? builder.asType() : abstractBuilderType);
        this.bytecodeDescriptorElement = this.add(new BytecodeDescriptorElement());
        CodeVariableElement descriptor = new CodeVariableElement(Set.of(PUBLIC, STATIC, FINAL), bytecodeDescriptorElement.asType(), "BYTECODE");
        descriptor.createInitBuilder().string("null").end();
        this.getEnclosedElements().add(0, descriptor);

        this.add(createExecute());
        this.add(createConstructor());
        this.add(createCreate());
        if (model.enableSerialization) {
            this.add(createSerialize());
            this.add(createDeserialize());
        }
        this.add(createGetSourceSection());
        this.add(createIsInstrumentable());
        this.addOptional(createFindInstrumentableCallNode());
        this.addOptional(createPrepareForInstrumentation());
        this.add(createNewConfigBuilder());
    }

    private CodeExecutableElement createExecute() {
        CodeExecutableElement ex = BytecodeRootNodeElement.overrideImplementRootNodeMethod(model, "execute", new String[]{"frame"}, new TypeMirror[]{types.VirtualFrame});
        CodeTreeBuilder b = ex.createBuilder();
        emitThrowNotImplemented(b);
        return ex;
    }

    private CodeExecutableElement createConstructor() {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(languageClass, "language"));
        ctor.addParameter(new CodeVariableElement(types.FrameDescriptor_Builder, "builder"));
        CodeTreeBuilder b = ctor.getBuilder();
        b.startStatement().startCall("super");
        b.string("language");
        if (model.fdBuilderConstructor != null) {
            b.string("builder");
        } else {
            b.string("builder.build()");
        }
        b.end(2);
        emitThrowNotImplemented(b);
        return ctor;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.BytecodeRootNodes, model.templateType.asType()), "create");
        method.addParameter(new CodeVariableElement(languageClass, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(types.BytecodeParser, builder.asType()), "generator"));
        CodeTreeBuilder b = method.getBuilder();
        emitThrowNotImplemented(b);
        return method;
    }

    private CodeExecutableElement createSerialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), type(void.class), "serialize");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parserType, "parser"));
        method.addThrownType(type(IOException.class));
        CodeTreeBuilder b = method.createBuilder();
        emitThrowNotImplemented(b);
        return method;
    }

    private CodeExecutableElement createDeserialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC),
                        generic(types.BytecodeRootNodes, model.getTemplateType().asType()), "deserialize");
        method.addParameter(new CodeVariableElement(languageClass, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addThrownType(type(IOException.class));
        CodeTreeBuilder b = method.createBuilder();
        emitThrowNotImplemented(b);
        return method;
    }

    private CodeExecutableElement createNewConfigBuilder() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), types.BytecodeConfig_Builder, "newConfigBuilder");
        CodeTreeBuilder b = method.createBuilder();
        emitThrowNotImplemented(b);
        return method;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();
        emitThrowNotImplemented(b);
        return ex;
    }

    private CodeExecutableElement createIsInstrumentable() {
        CodeExecutableElement ex = BytecodeRootNodeElement.overrideImplementRootNodeMethod(model, "isInstrumentable");
        CodeTreeBuilder b = ex.createBuilder();
        emitThrowNotImplemented(b);
        return ex;
    }

    private CodeExecutableElement createFindInstrumentableCallNode() {
        if (!model.enableTagInstrumentation) {
            return null;
        }
        CodeExecutableElement ex = BytecodeRootNodeElement.overrideImplementRootNodeMethod(model, "findInstrumentableCallNode", new String[]{"callNode", "frame", "bytecodeIndex"});
        CodeTreeBuilder b = ex.createBuilder();
        emitThrowNotImplemented(b);
        return ex;
    }

    private CodeExecutableElement createPrepareForInstrumentation() {
        if (!model.enableTagInstrumentation) {
            return null;
        }
        CodeExecutableElement ex = BytecodeRootNodeElement.overrideImplementRootNodeMethod(model, "prepareForInstrumentation", new String[]{"materializedTags"});
        CodeTreeBuilder b = ex.createBuilder();
        emitThrowNotImplemented(b);
        return ex;
    }

    private void emitThrowNotImplemented(CodeTreeBuilder b) {
        b.startThrow().startNew(type(AbstractMethodError.class));
        b.string("\"There are error(s) with the operation node specification. Please resolve the error(s) and recompile.\"");
        b.end(2);
    }

    TypeMirror type(Class<?> c) {
        return context.getType(c);
    }

    final class BytecodeDescriptorElement extends CodeTypeElement {
        BytecodeDescriptorElement() {
            super(Set.of(PUBLIC, STATIC, ABSTRACT), ElementKind.CLASS, null, "Bytecode");

            TypeMirror superType = abstractBuilderType == null ? null : BytecodeDSLCodeGenerator.findBytecodeVariantType(abstractBuilderType);
            if (superType == null) {
                // regular case
                superType = generic(types.BytecodeDescriptor, model.getTemplateType().asType(), model.languageClass, builder.asType());
            }
            setSuperClass(superType);

            CodeExecutableElement ctor = this.add(new CodeExecutableElement(Set.of(PRIVATE), null, "Bytecode"));
            ctor.createBuilder().startStatement().startSuperCall().string("null").end().end();
        }
    }

    private final class BuilderElement extends CodeTypeElement {
        BuilderElement() {
            super(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
            this.setSuperClass(abstractBuilderType == null ? types.BytecodeBuilder : abstractBuilderType);
            mergeSuppressWarnings(this, "all");

            this.add(createMethodStub("createLocal", types.BytecodeLocal));
            this.add(createMethodStub("createLabel", types.BytecodeLabel));
            this.add(createMethodStub("beginSourceSectionUnavailable", type(void.class)));
            this.add(createMethodStub("endSourceSectionUnavailable", type(void.class)));
            this.add(createMethodStub("beginSourceSection", type(void.class)));
            this.add(createMethodStub("endSourceSection", type(void.class)));

            for (OperationModel operation : model.getOperations()) {
                switch (operation.kind) {
                    case ROOT: {
                        this.add(createBeginRoot());
                        this.add(createEndRoot());
                        break;
                    }
                    default:
                        this.add(createBegin(operation));
                        this.add(createEnd(operation));
                        this.add(createEmit(operation));
                }

            }

            this.add(createConstructor());
        }

        private CodeExecutableElement createMethodStub(String name, TypeMirror returnType) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), returnType, name);
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(Object.class), "args"));
            ex.setVarArgs(true);
            emitThrowNotImplemented(ex.createBuilder());
            return ex;
        }

        private CodeExecutableElement createTypedMethodStub(String name, TypeMirror returnType, OperationArgument[] args, boolean varArgs) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), returnType, name);
            for (OperationArgument arg : args) {
                ex.addParameter(arg.toVariableElement());
            }
            ex.setVarArgs(varArgs);
            emitThrowNotImplemented(ex.createBuilder());
            return ex;
        }

        private boolean hasTypedSignature(OperationArgument[] args) {
            return args.length > 0;
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
            if (hasTypedSignature(operation.operationBeginArguments)) {
                return createTypedMethodStub("begin" + operation.name, type(void.class), operation.operationBeginArguments, operation.kind == OperationModel.OperationKind.TAG);
            }
            return createMethodStub("begin" + operation.name, type(void.class));
        }

        private CodeExecutableElement createBeginRoot() {
            if (model.prolog != null && hasTypedSignature(model.prolog.operation.operationBeginArguments)) {
                return createTypedMethodStub("beginRoot", type(void.class), model.prolog.operation.operationBeginArguments, false);
            }
            return createMethodStub("beginRoot", type(void.class));
        }

        private CodeExecutableElement createEnd(OperationModel operation) {
            TypeMirror returnType = operation.kind == OperationKind.BIND_STACKVALUE ? types.StackValue : type(void.class);
            if (hasTypedSignature(operation.operationEndArguments)) {
                return createTypedMethodStub("end" + operation.name, returnType, operation.operationEndArguments, operation.kind == OperationModel.OperationKind.TAG);
            }
            return createMethodStub("end" + operation.name, returnType);
        }

        private CodeExecutableElement createEndRoot() {
            if (model.prolog != null && hasTypedSignature(model.prolog.operation.operationEndArguments)) {
                return createTypedMethodStub("endRoot", model.templateType.asType(), model.prolog.operation.operationEndArguments, false);
            }
            return createMethodStub("endRoot", model.templateType.asType());
        }

        private CodeExecutableElement createEmit(OperationModel operation) {
            if (hasTypedSignature(operation.operationBeginArguments)) {
                return createTypedMethodStub("emit" + operation.name, type(void.class), operation.operationBeginArguments, false);
            }
            return createMethodStub("emit" + operation.name, type(void.class));
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
            CodeTreeBuilder b = ctor.getBuilder();
            b.startStatement().startCall("super").string("null").end(2);
            return ctor;
        }
    }
}
