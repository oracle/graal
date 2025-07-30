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
package org.graalvm.nativebridge.processor;

import org.graalvm.nativebridge.processor.ProcessToProcessFactoryParser.ProcessToProcessFactoryDefinitionData;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class ProcessToProcessFactoryGenerator extends AbstractBridgeGenerator {

    private static final String START_POINT_FACTORY_SIMPLE_NAME = "ProcessToProcessFactoryStartPoint";
    private static final String END_POINT_FACTORY_SIMPLE_NAME = "ProcessToProcessFactoryEndPoint";
    private static final String SERVICE_ORDERING_FIELD = "SERVICE_ORDERING";
    private static final String DISPATCH_HANDLERS_FIELD = "DISPATCH_HANDLERS";
    private static final String SERVICE_SCOPE_FIELD = "SERVICE_SCOPE";

    private final Types types;

    ProcessToProcessFactoryGenerator(ProcessToProcessFactoryParser parser, ProcessToProcessFactoryDefinitionData definitionData, ProcessToProcessTypeCache typeCache) {
        super(parser, definitionData, typeCache);
        this.types = parser.types;
    }

    @Override
    ProcessToProcessTypeCache getTypeCache() {
        return (ProcessToProcessTypeCache) super.getTypeCache();
    }

    @Override
    ProcessToProcessFactoryDefinitionData getDefinition() {
        return (ProcessToProcessFactoryDefinitionData) super.getDefinition();
    }

    @Override
    ProcessToProcessFactoryParser getParser() {
        return (ProcessToProcessFactoryParser) super.getParser();
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateProcessToProcessInitialStartPointFactory(builder);
        builder.lineEnd("");
        generateProcessToProcessListen(builder);
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateServiceOrderingMap(builder);
        builder.lineEnd("");
        generateLookupServiceId(builder);
        builder.lineEnd("");
        generateFactoryStartPoint(builder, targetClassSimpleName);
        builder.lineEnd("");
        generateFactoryEndPoint(builder);
    }

    private void generateProcessToProcessInitialStartPointFactory(CodeBuilder builder) {
        CharSequence configVar = "config";
        CharSequence processIsolateVar = "processIsolate";
        CharSequence successVar = "initializeIsolateSuccess";
        CharSequence handleVar = "initialServiceHandle";
        CharSequence serviceVar = "initialService";
        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);

        builder.methodStart(EnumSet.of(Modifier.STATIC), FACTORY_METHOD_NAME, getDefinition().initialService,
                        List.of(CodeBuilder.newParameter(getTypeCache().processIsolateConfig, configVar)), List.of(getTypeCache().isolateCreateException));
        builder.indent();
        CharSequence marshallers = new CodeBuilder(builder).invokeStatic(getDefinition().marshallerConfig, "getInstance").build();
        CharSequence throwableClassLiteral = new CodeBuilder(builder).classLiteral(getTypeCache().throwable).build();
        CharSequence throwableMarshaller = new CodeBuilder(builder).invoke(marshallers, "lookupMarshaller", throwableClassLiteral).build();
        CharSequence releaseObjectRef = new CodeBuilder(builder).write(START_POINT_FACTORY_SIMPLE_NAME).write("::").write("releaseObjectHandle").build();
        builder.lineStart().write(getTypeCache().processIsolate).space().write(processIsolateVar).write(" = ").//
                        invokeStatic(getTypeCache().processIsolate, "spawnProcessIsolate", configVar, throwableMarshaller, DISPATCH_HANDLERS_FIELD, releaseObjectRef).lineEnd(";");
        builder.lineStart().write(types.getPrimitiveType(TypeKind.BOOLEAN)).space().write(successVar).write(" = false").lineEnd(";");
        builder.line("try {");
        builder.indent();
        builder.lineStart().write(longType).space().write(handleVar).write(" = ").invoke(START_POINT_FACTORY_SIMPLE_NAME, "initializeIsolate", processIsolateVar).lineEnd(";");
        builder.lineStart("if (").write(handleVar).lineEnd(" == 0L) {");
        builder.indent();
        CharSequence message = new CodeBuilder(builder).stringLiteral("Failed to initialize an isolate ").write(" + ").invoke(processIsolateVar, "getIsolateId").build();
        builder.lineStart("throw ").newInstance(getTypeCache().isolateCreateException, message).lineEnd(";");
        builder.dedent();
        builder.line("}");
        CharSequence peer = new CodeBuilder(builder).invokeStatic(getTypeCache().peer, "create", processIsolateVar, handleVar).build();
        builder.lineStart().write(getDefinition().initialService).space().write(serviceVar).write(" = ").invoke(Utilities.getGenClassName(getDefinition().initialService), FACTORY_METHOD_NAME,
                        peer).lineEnd(";");
        builder.lineStart().write(successVar).write(" = true").lineEnd(";");
        builder.lineStart("return ").write(serviceVar).lineEnd(";");
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart("if (!").write(successVar).lineEnd(") {");
        builder.indent();
        builder.lineStart().invoke(processIsolateVar, "shutdown").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private void generateProcessToProcessListen(CodeBuilder builder) {
        CharSequence configVar = "isolateConfig";
        CodeBuilder.Parameter configParam = CodeBuilder.newParameter(getTypeCache().processIsolateConfig, configVar);
        builder.methodStart(Set.of(Modifier.STATIC), "listen", types.getNoType(TypeKind.VOID), List.of(configParam), List.of(getTypeCache().isolateCreateException));
        builder.indent();
        CharSequence marshallers = new CodeBuilder(builder).invokeStatic(getDefinition().marshallerConfig, "getInstance").build();
        CharSequence throwableClassLiteral = new CodeBuilder(builder).classLiteral(getTypeCache().throwable).build();
        CharSequence throwableMarshaller = new CodeBuilder(builder).invoke(marshallers, "lookupMarshaller", throwableClassLiteral).build();
        CharSequence releaseObjectRef = new CodeBuilder(builder).write(START_POINT_FACTORY_SIMPLE_NAME).write("::").write("releaseObjectHandle").build();
        builder.lineStart().invokeStatic(getTypeCache().processIsolate, "connectProcessIsolate", configVar, throwableMarshaller, DISPATCH_HANDLERS_FIELD, releaseObjectRef).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private void generateServiceOrderingMap(CodeBuilder builder) {
        CharSequence classIdVar = "classId";
        DeclaredType classType = types.getDeclaredType((TypeElement) getTypeCache().clazz.asElement(), types.getWildcardType(null, null));
        DeclaredType integerType = (DeclaredType) types.boxedClass(types.getPrimitiveType(TypeKind.INT)).asType();
        DeclaredType mapType = types.getDeclaredType((TypeElement) getTypeCache().map.asElement(), classType, integerType);
        ArrayType dispatchHandlersType = types.getArrayType(getTypeCache().dispatchHandler);
        builder.lineStart().writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)).space().write(mapType).space().write(SERVICE_ORDERING_FIELD).lineEnd(";");
        builder.lineStart().writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)).space().write(dispatchHandlersType).space().write(DISPATCH_HANDLERS_FIELD).lineEnd(";");
        builder.lineEnd("");
        builder.line("static {");
        builder.indent();
        List<DeclaredType> services = getDefinition().services;
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(classIdVar).write(" = 0").lineEnd(";");
        builder.lineStart(SERVICE_ORDERING_FIELD).write(" = ").newInstance((DeclaredType) types.erasure(getTypeCache().hashMap), List.of()).lineEnd(";");
        builder.lineStart(DISPATCH_HANDLERS_FIELD).write(" = ").newArray(getTypeCache().dispatchHandler, Integer.toString(services.size() + 1)).lineEnd(";");
        CharSequence getAndIncClassId = new CodeBuilder(builder).write(classIdVar).write("++").build();
        builder.lineStart().invoke(SERVICE_ORDERING_FIELD, "put", START_POINT_FACTORY_SIMPLE_NAME + ".class", classIdVar).lineEnd(";");
        builder.lineStart().arrayElement(DISPATCH_HANDLERS_FIELD, getAndIncClassId).write(" = ").write(END_POINT_FACTORY_SIMPLE_NAME).write("::").write("dispatch").lineEnd(";");
        for (DeclaredType service : services) {
            CharSequence serviceClassLiteral = new CodeBuilder(builder).classLiteral(service).build();
            builder.lineStart().invoke(SERVICE_ORDERING_FIELD, "put", serviceClassLiteral, classIdVar).lineEnd(";");
            CharSequence targetClass;
            if (getParser().isHandWritten(service.asElement())) {
                targetClass = new CodeBuilder(builder).write(service).build();
            } else {
                targetClass = new CodeBuilder(builder).write(service).write("Gen").build();
            }
            builder.lineStart().arrayElement(DISPATCH_HANDLERS_FIELD, getAndIncClassId).write(" = ").write(targetClass).write("::").write("dispatch").lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateLookupServiceId(CodeBuilder builder) {
        DeclaredType foreignObjectClass = types.getDeclaredType((TypeElement) getTypeCache().clazz.asElement(), types.getWildcardType(null, null));
        CharSequence foreignObjectVar = "foreignObject";
        CodeBuilder.Parameter[] params = new CodeBuilder.Parameter[]{CodeBuilder.newParameter(foreignObjectClass, foreignObjectVar)};
        builder.methodStart(Set.of(Modifier.STATIC), "lookupServiceId", types.getPrimitiveType(TypeKind.INT), List.of(params), List.of());
        builder.indent();
        CharSequence idVar = "id";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(idVar).write(" = ").invoke(SERVICE_ORDERING_FIELD, "getOrDefault", foreignObjectVar, "-1").lineEnd(";");
        builder.lineStart("if (").write(idVar).lineEnd(" == -1) {");
        builder.indent();
        CharSequence message = new CodeBuilder(builder).stringLiteral("Unregistered foreign object ").write(" + ").write(foreignObjectVar).write(" + ").stringLiteral(
                        ". Add the class into @GenerateProcessToProcessFactory.services in the " + Utilities.getSimpleName(getDefinition().annotatedType) + " registration.").build();
        builder.lineStart("throw ").newInstance(getTypeCache().illegalArgumentException, message).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.lineStart("return ").write(idVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private void generateFactoryStartPoint(CodeBuilder builder, CharSequence targetClassSimpleName) {
        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        CharSequence isolateVar = "processIsolate";
        CharSequence isolateThreadVar = "processIsolateThread";
        CharSequence binaryOutputVar = "binaryOutput";
        CharSequence binaryInputVar = "binaryInput";
        builder.classStart(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), START_POINT_FACTORY_SIMPLE_NAME, null, List.of());
        builder.indent();
        builder.lineEnd("");
        CharSequence startPointFactoryLiteral = START_POINT_FACTORY_SIMPLE_NAME + ".class";
        builder.lineStart().writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)).space().write(types.getPrimitiveType(TypeKind.INT)).space().//
                        write(SERVICE_SCOPE_FIELD).write(" = ").invoke(targetClassSimpleName, "lookupServiceId", startPointFactoryLiteral).//
                        write(" << 16").//
                        lineEnd(";");

        builder.lineEnd("");
        CodeBuilder.Parameter isolateParameter = CodeBuilder.newParameter(getTypeCache().processIsolate, isolateVar);
        builder.methodStart(Set.of(Modifier.STATIC), "initializeIsolate", longType, List.of(isolateParameter), List.of(getTypeCache().isolateCreateException));
        builder.indent();
        builder.lineStart().write(getTypeCache().byteArrayBinaryOutput).space().write(binaryOutputVar).write(" = ").invokeStatic(getTypeCache().binaryOutput, "create").lineEnd(";");
        CharSequence messageId = new CodeBuilder(builder).write(SERVICE_SCOPE_FIELD).write(" | ").write("0").build();
        builder.lineStart().invoke(binaryOutputVar, "writeInt", messageId).lineEnd(";");
        builder.lineStart().write(getTypeCache().processIsolateThread).space().write(isolateThreadVar).write(" = null").lineEnd(";");
        builder.line("try {");
        builder.indent();
        builder.lineStart().write(isolateThreadVar).write(" = ").invoke(isolateVar, "enter").lineEnd(";");
        builder.lineStart().write(getTypeCache().binaryInput).space().write(binaryInputVar).write(" = ").invoke(isolateThreadVar, "sendAndReceive", binaryOutputVar).lineEnd(";");
        builder.lineStart("return ").invoke(binaryInputVar, "readLong").lineEnd(";");
        builder.dedent();
        CharSequence isolateDeathVar = "isolateDeathException";
        builder.lineStart("} catch(").write(getTypeCache().isolateDeathException).space().write(isolateDeathVar).lineEnd(") {");
        builder.indent();
        builder.lineStart("throw ").newInstance(getTypeCache().isolateCreateException, isolateDeathVar).lineEnd(";");
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart("if (").write(isolateThreadVar).write(" != null) ").lineEnd("{");
        builder.indent();
        builder.lineStart().invoke(isolateThreadVar, "leave").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        builder.lineEnd("");
        CodeBuilder.Parameter isolateThreadParameter = CodeBuilder.newParameter(getTypeCache().processIsolateThread, isolateThreadVar);
        CharSequence objectHandleVar = "objectHandle";
        CodeBuilder.Parameter handleParameter = CodeBuilder.newParameter(longType, objectHandleVar);
        builder.methodStart(Set.of(Modifier.STATIC), "releaseObjectHandle", intType, List.of(isolateThreadParameter, handleParameter), List.of());
        builder.indent();
        builder.lineStart().write(getTypeCache().byteArrayBinaryOutput).space().write(binaryOutputVar).write(" = ").invokeStatic(getTypeCache().binaryOutput, "create").lineEnd(";");
        messageId = new CodeBuilder(builder).write(SERVICE_SCOPE_FIELD).write(" | ").write("1").build();
        builder.lineStart().invoke(binaryOutputVar, "writeInt", messageId).lineEnd(";");
        builder.lineStart().invoke(binaryOutputVar, "writeLong", objectHandleVar).lineEnd(";");
        builder.lineStart().write(getTypeCache().binaryInput).space().write(binaryInputVar).write(" = ").invoke(isolateThreadVar, "sendAndReceive", binaryOutputVar).lineEnd(";");
        builder.lineStart("return ").invoke(binaryInputVar, "readInt").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private void generateFactoryEndPoint(CodeBuilder builder) {
        CharSequence binaryInputVar = "binaryInput";
        CharSequence binaryOutputVar = "binaryOutput";
        builder.lineStart().annotation(getTypeCache().suppressWarnings, "unused").lineEnd("");
        builder.classStart(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), END_POINT_FACTORY_SIMPLE_NAME, null, List.of());
        builder.indent();
        builder.lineEnd("");
        generateDispatchMethod(builder);
        builder.lineEnd("");
        CodeBuilder.Parameter isolateParameter = CodeBuilder.newParameter(getTypeCache().processIsolate, "processIsolate");
        CodeBuilder.Parameter binaryInputParameter = CodeBuilder.newParameter(getTypeCache().binaryInput, binaryInputVar);
        builder.methodStart(Set.of(Modifier.PRIVATE, Modifier.STATIC), "initializeIsolate", getTypeCache().binaryOutput, List.of(isolateParameter, binaryInputParameter), List.of());
        builder.indent();
        builder.lineStart().write(getTypeCache().binaryOutput).space().write(binaryOutputVar).write(" = ").invokeStatic(getTypeCache().binaryOutput, "claimBuffer", binaryInputVar).lineEnd(";");
        CharSequence initialObject = createCustomObject(builder, getDefinition().implementation);
        CharSequence createRef = new CodeBuilder(builder).invokeStatic(getTypeCache().referenceHandles, "create", initialObject).build();
        builder.lineStart().invoke(binaryOutputVar, "writeLong", createRef).lineEnd(";");
        builder.lineStart("return ").write(binaryOutputVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.lineEnd("");
        builder.methodStart(Set.of(Modifier.PRIVATE, Modifier.STATIC), "releaseObjectHandle", getTypeCache().binaryOutput, List.of(isolateParameter, binaryInputParameter), List.of());
        builder.indent();
        CharSequence objectHandleVar = "objectHandle";
        CharSequence resultVar = "result";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.LONG)).space().write(objectHandleVar).write(" = ").invoke(binaryInputVar, "readLong").lineEnd(";");
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(resultVar).lineEnd(";");
        builder.line("try {");
        builder.indent();
        builder.lineStart().invokeStatic(getTypeCache().referenceHandles, "remove", objectHandleVar).lineEnd(";");
        builder.lineStart(resultVar).write(" = ").write("0").lineEnd(";");
        builder.dedent();
        builder.lineStart("} catch(").write(getTypeCache().throwable).space().write("throwable) ").lineEnd("{");
        builder.indent();
        builder.lineStart().write(resultVar).write(" = 1").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.lineStart().write(getTypeCache().binaryOutput).space().write(binaryOutputVar).write(" = ").invokeStatic(getTypeCache().binaryOutput, "claimBuffer", binaryInputVar).lineEnd(";");
        builder.lineStart().invoke(binaryOutputVar, "writeInt", resultVar).lineEnd(";");
        builder.lineStart("return ").write(binaryOutputVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private void generateDispatchMethod(CodeBuilder builder) {
        CharSequence messageIdVar = "messageId";
        CharSequence isolateVar = "processIsolate";
        CharSequence inputVar = "binaryInput";
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        builder.methodStart(Set.of(Modifier.STATIC), "dispatch", getTypeCache().binaryOutput,
                        List.of(CodeBuilder.newParameter(intType, messageIdVar),
                                        CodeBuilder.newParameter(getTypeCache().processIsolate, isolateVar),
                                        CodeBuilder.newParameter(getTypeCache().binaryInput, inputVar)),
                        List.of(getTypeCache().throwable));
        builder.indent();
        builder.lineStart("return switch (").write(messageIdVar).write(" & 0xffff)").lineEnd(" {");
        builder.indent();
        builder.lineStart("case ").write("0").write(" -> ").invoke(null, "initializeIsolate", isolateVar, inputVar).lineEnd(";");
        builder.lineStart("case ").write("1").write(" -> ").invoke(null, "releaseObjectHandle", isolateVar, inputVar).lineEnd(";");
        CharSequence message = new CodeBuilder(builder).stringLiteral("Unknown message id ").write(" + ").write(messageIdVar).build();
        builder.lineStart("default -> throw ").newInstance(getTypeCache().illegalArgumentException, message).lineEnd(";");
        builder.dedent();
        builder.line("};");
        builder.dedent();
        builder.line("}");
    }
}
