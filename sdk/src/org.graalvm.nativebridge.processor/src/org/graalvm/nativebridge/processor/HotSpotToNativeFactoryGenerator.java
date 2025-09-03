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

import org.graalvm.nativebridge.processor.HotSpotToNativeFactoryParser.HotSpotToNativeFactoryDefinitionData;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class HotSpotToNativeFactoryGenerator extends AbstractBridgeGenerator {

    static final String START_POINT_FACTORY_SIMPLE_NAME = "HSToNativeFactoryStartPoint";
    static final String END_POINT_FACTORY_SIMPLE_NAME = "HSToNativeFactoryEndPoint";

    private final Types types;
    private final String factoryStartPointName;
    private final String factoryEndPointName;
    private boolean hasOtherHotSpotToNative;

    HotSpotToNativeFactoryGenerator(AbstractFactoryParser parser, HotSpotToNativeFactoryDefinitionData definitionData, HotSpotToNativeTypeCache typeCache,
                    String factoryStartPointName, String factoryEndPointName) {
        super(parser, definitionData, typeCache);
        this.types = parser.types;
        this.factoryStartPointName = Objects.requireNonNull(factoryStartPointName, "FactoryStartPointName must be non-null");
        this.factoryEndPointName = Objects.requireNonNull(factoryEndPointName, "FactoryEndPointName must be non-null");
    }

    @Override
    HotSpotToNativeTypeCache getTypeCache() {
        return (HotSpotToNativeTypeCache) super.getTypeCache();
    }

    @Override
    HotSpotToNativeFactoryDefinitionData getDefinition() {
        return (HotSpotToNativeFactoryDefinitionData) super.getDefinition();
    }

    @Override
    void configureMultipleDefinitions(List<AbstractBridgeParser.DefinitionData> otherDefinitions) {
        hasOtherHotSpotToNative = otherDefinitions.stream().anyMatch((d) -> d instanceof HotSpotToNativeFactoryDefinitionData);
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!hasOtherHotSpotToNative) {
            builder.lineEnd("");
            generateStartPointFactory(builder, IsolateHandlerSnippet.single(START_POINT_FACTORY_SIMPLE_NAME, getTypeCache(), getDefinition().nativeIsolateHandler));
        }
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateFactoryStartPoint(builder, true);
        builder.lineEnd("");
        generateFactoryEndPoint(builder, targetClassSimpleName, true);
    }

    void generateFactoryClassesToInitializeIsolate(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateFactoryStartPoint(builder, false);
        builder.lineEnd("");
        generateFactoryEndPoint(builder, targetClassSimpleName, false);
    }

    void generateStartPointFactory(CodeBuilder builder, IsolateHandlerSnippet isolateHandlerSnippet) {
        builder.lineStart().annotation(getTypeCache().suppressWarnings, "restricted").lineEnd("");
        CharSequence configVar = "config";
        CharSequence isolateThreadVar = "isolateThreadAddress";
        CharSequence isolateVar = "isolateAddress";
        CharSequence nativeIsolateVar = "nativeIsolate";
        CharSequence nativeIsolateThreadVar = "nativeIsolateThread";
        CharSequence nativeIsolateHandlerVar = "nativeIsolateHandler";
        CharSequence successVar = "initializeIsolateSuccess";
        CharSequence handleVar = "initialServiceHandle";
        CharSequence serviceVar = "initialService";

        TypeMirror booleanType = types.getPrimitiveType(TypeKind.BOOLEAN);
        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);

        builder.methodStart(EnumSet.of(Modifier.STATIC), FACTORY_METHOD_NAME, getDefinition().initialService,
                        List.of(CodeBuilder.newParameter(getTypeCache().nativeIsolateConfig, configVar)), List.of(getTypeCache().isolateCreateException));

        builder.indent();
        CodeBuilder getIsolateLibrary = new CodeBuilder(builder).invoke(new CodeBuilder(builder).invoke(configVar, "getIsolateLibrary").build(), "toString");
        builder.lineStart().invokeStatic(getTypeCache().system, "load", getIsolateLibrary.build()).lineEnd(";");
        CharSequence newIsolateHandler = isolateHandlerSnippet.newIsolateHandler(builder);
        if (newIsolateHandler != null) {
            builder.lineStart().write(getTypeCache().nativeIsolateHandler).space().write(nativeIsolateHandlerVar).write(" = ").write(newIsolateHandler).lineEnd(";");
        }
        CharSequence create = isolateHandlerSnippet.createIsolate(builder, nativeIsolateHandlerVar, configVar);
        CharSequence tearDown = isolateHandlerSnippet.tearDownIsolateCallBack(builder, nativeIsolateHandlerVar);
        builder.lineStart().write(longType).space().write(isolateThreadVar).write(" = ").write(create).lineEnd(";");
        builder.lineStart().write(longType).space().write(isolateVar).write(" = ").invoke(isolateHandlerSnippet.defaultStartPointName(), "getIsolate", isolateThreadVar).lineEnd(";");
        CharSequence[] nativeIsolateArgs = new CharSequence[]{
                        isolateVar,
                        configVar,
                        new CodeBuilder(builder).write(isolateHandlerSnippet.defaultStartPointName()).write("::").write("attachIsolate").build(),
                        new CodeBuilder(builder).write(isolateHandlerSnippet.defaultStartPointName()).write("::").write("detachIsolate").build(),
                        tearDown,
                        new CodeBuilder(builder).write(isolateHandlerSnippet.defaultStartPointName()).write("::").write("releaseObjectHandle").build()
        };
        builder.lineStart().write(getTypeCache().nativeIsolate).space().write(nativeIsolateVar).write(" = ").invokeStatic(getTypeCache().nativeIsolate, "create", nativeIsolateArgs).lineEnd(";");
        builder.lineStart().write(booleanType).space().write(successVar).lineEnd(" = false;");
        builder.lineStart().write(getTypeCache().nativeIsolateThread).space().write(nativeIsolateThreadVar).write(" = ").invoke(nativeIsolateVar, "enter").lineEnd(";");
        builder.line("try {");
        builder.indent();
        CharSequence threadAddr = new CodeBuilder(builder).invoke(nativeIsolateThreadVar, "getIsolateThreadId").build();
        builder.lineStart().write(longType).space().write(handleVar).write(" = ").write(isolateHandlerSnippet.initializeIsolate(builder, threadAddr)).lineEnd(";");
        builder.lineStart("if (").write(handleVar).lineEnd(" == 0L) {");
        builder.indent();
        CharSequence message = new CodeBuilder(builder).stringLiteral("Failed to initialize an isolate 0x").write(" + ").invokeStatic(getTypeCache().boxedLong, "toHexString", isolateVar).build();
        builder.lineStart("throw ").newInstance(getTypeCache().isolateCreateException, message).lineEnd(";");
        builder.dedent();
        builder.line("}");
        CharSequence peer = new CodeBuilder(builder).invokeStatic(getTypeCache().peer, "create", nativeIsolateVar, handleVar).build();
        builder.lineStart().write(getDefinition().initialService).space().write(serviceVar).write(" = ").invoke(Utilities.getGenClassName(getDefinition().initialService), "create", peer).lineEnd(";");
        builder.lineStart().write(successVar).write(" = true").lineEnd(";");
        builder.lineStart("return ").write(serviceVar).lineEnd(";");
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart().invoke(nativeIsolateThreadVar, "leave").lineEnd(";");
        builder.lineStart("if (!").write(successVar).lineEnd(") {");
        builder.indent();
        builder.lineStart().invoke(nativeIsolateVar, "shutdown").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private void generateFactoryStartPoint(CodeBuilder builder, boolean completeDefinition) {
        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        CodeBuilder.Parameter isolateThreadParameter = CodeBuilder.newParameter(longType, "isolateThread");
        CodeBuilder.Parameter isolateParameter = CodeBuilder.newParameter(longType, "isolate");
        builder.classStart(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryStartPointName, null, List.of());
        builder.indent();
        if (completeDefinition) {
            builder.line("");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "createIsolate", longType, List.of(), List.of());
            builder.line("");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "getIsolate", longType, List.of(isolateThreadParameter), List.of());
        }
        builder.line("");
        builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "initializeIsolate", longType, List.of(isolateThreadParameter), List.of());
        if (completeDefinition) {
            builder.line("");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "attachIsolate", longType, List.of(isolateParameter), List.of());
            builder.line("");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "detachIsolate", intType, List.of(isolateThreadParameter), List.of());
            builder.line("");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "tearDownIsolate", intType, List.of(isolateThreadParameter), List.of());
            builder.line("");
            CodeBuilder.Parameter handleParameter = CodeBuilder.newParameter(longType, "handle");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "releaseObjectHandle", intType, List.of(isolateThreadParameter, handleParameter), List.of());
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateFactoryEndPoint(CodeBuilder builder, CharSequence targetClassSimpleName, boolean completeDefinition) {
        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        CodeBuilder.Parameter jniEnvParameter = CodeBuilder.newParameter(getTypeCache().jniEnv, "jniEnv");
        CodeBuilder.Parameter jClassParameter = CodeBuilder.newParameter(getTypeCache().jClass, "jniClazz");
        CodeBuilder.Parameter isolateThreadParameter = CodeBuilder.newParameter(longType, "isolateThread", new CodeBuilder(builder).annotation(getTypeCache().isolateThreadContext, null).build());
        CodeBuilder.Parameter isolateParameter = CodeBuilder.newParameter(longType, "isolate", new CodeBuilder(builder).annotation(getTypeCache().isolateContext, null).build());
        String packageName = Utilities.getEnclosingPackageElement((TypeElement) getDefinition().annotatedType.asElement()).getQualifiedName().toString();
        String className = targetClassSimpleName + "$" + factoryStartPointName;
        String symbolBase = String.format("Java_%s_%s_", Utilities.cSymbol(packageName), Utilities.cSymbol(className));
        builder.lineStart().annotation(getTypeCache().suppressWarnings, "unused").lineEnd("");
        builder.classStart(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryEndPointName, null, List.of());
        builder.indent();
        if (completeDefinition) {
            builder.line("");
            generateCEntryPointAnnotation(builder, symbolBase + "createIsolate", "CREATE_ISOLATE");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "createIsolate", longType, List.of(jniEnvParameter, jClassParameter), List.of());
            builder.line("");
            generateCEntryPointAnnotation(builder, symbolBase + "getIsolate", "GET_ISOLATE");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "getIsolate", longType, List.of(jniEnvParameter, jClassParameter, isolateThreadParameter), List.of());
        }
        builder.line("");
        generateCEntryPointAnnotation(builder, symbolBase + "initializeIsolate", null);
        builder.lineStart().annotation(getTypeCache().suppressWarnings, "try").lineEnd("");
        builder.methodStart(Set.of(Modifier.STATIC), "initializeIsolate", longType, List.of(jniEnvParameter, jClassParameter, isolateThreadParameter), List.of());
        builder.indent();
        builder.lineStart("try (").write(getTypeCache().jniMethodScope).space().write("scope").write(" = ").invokeStatic(getTypeCache().foreignException, "openJNIMethodScope",
                        "\"" + className + "::initializeIsolate\"",
                        jniEnvParameter.name).lineEnd(") {");
        builder.indent();
        builder.lineStart().invokeStatic(getTypeCache().hsIsolate, "create", jniEnvParameter.name).lineEnd(";");
        CharSequence object = createCustomObject(builder, getDefinition().implementation);
        builder.lineStart("return ").invokeStatic(getTypeCache().referenceHandles, "create", object).lineEnd(";");
        builder.dedent();
        builder.lineStart("} catch (").write(getTypeCache().throwable).space().write("throwable").lineEnd(") {");
        builder.indent();
        builder.line("return 0;");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        builder.line("");
        if (completeDefinition) {
            generateCEntryPointAnnotation(builder, symbolBase + "attachIsolate", "ATTACH_THREAD");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "attachIsolate", longType, List.of(jniEnvParameter, jClassParameter, isolateParameter), List.of());
            builder.line("");
            generateCEntryPointAnnotation(builder, symbolBase + "detachIsolate", "DETACH_THREAD");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "detachIsolate", intType, List.of(jniEnvParameter, jClassParameter, isolateThreadParameter), List.of());
            builder.line("");
            generateCEntryPointAnnotation(builder, symbolBase + "tearDownIsolate", "TEAR_DOWN_ISOLATE");
            builder.methodStart(Set.of(Modifier.STATIC, Modifier.NATIVE), "tearDownIsolate", intType, List.of(jniEnvParameter, jClassParameter, isolateThreadParameter), List.of());
            builder.line("");
            CodeBuilder.Parameter handleParameter = CodeBuilder.newParameter(longType, "handle");
            generateCEntryPointAnnotation(builder, symbolBase + "releaseObjectHandle", null);
            builder.methodStart(Set.of(Modifier.STATIC), "releaseObjectHandle", intType, List.of(jniEnvParameter, jClassParameter, isolateThreadParameter, handleParameter), List.of());
            builder.indent();
            builder.line("try {");
            builder.indent();
            builder.lineStart().invokeStatic(getTypeCache().referenceHandles, "remove", handleParameter.name).lineEnd(";");
            builder.line("return 0;");
            builder.dedent();
            builder.lineStart("} catch (").write(getTypeCache().throwable).space().write("throwable").lineEnd(") {");
            builder.indent();
            builder.line("return -1;");
            builder.dedent();
            builder.line("}");
            builder.dedent();
            builder.line("}");
        }
        builder.dedent();
        builder.line("}");
    }

    abstract static class IsolateHandlerSnippet {

        private final String defaultFactoryStartPointName;
        private final String nativeOverrideFactoryStartPointName;
        final HotSpotToNativeTypeCache typeCache;

        private IsolateHandlerSnippet(String defaultFactoryStartPointName, String nativeOverrideFactoryStartPointName, HotSpotToNativeTypeCache typeCache) {
            this.defaultFactoryStartPointName = defaultFactoryStartPointName;
            this.nativeOverrideFactoryStartPointName = nativeOverrideFactoryStartPointName;
            this.typeCache = typeCache;
        }

        abstract CharSequence newIsolateHandler(CodeBuilder builder);

        abstract CharSequence createIsolate(CodeBuilder builder, CharSequence isolateHandlerVar, CharSequence configVar);

        abstract CharSequence tearDownIsolateCallBack(CodeBuilder builder, CharSequence isolateHandlerVar);

        final CharSequence defaultStartPointName() {
            return defaultFactoryStartPointName;
        }

        final CharSequence initializeIsolate(CodeBuilder builder, CharSequence isolateThreadAddr) {
            CharSequence initializeDefault = new CodeBuilder(builder).invoke(defaultFactoryStartPointName, "initializeIsolate", isolateThreadAddr).build();
            if (defaultFactoryStartPointName.equals(nativeOverrideFactoryStartPointName)) {
                return initializeDefault;
            } else {
                CharSequence initializeNative = new CodeBuilder(builder).invoke(nativeOverrideFactoryStartPointName, "initializeIsolate", isolateThreadAddr).build();
                return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").write(initializeNative).write(" : ").write(initializeDefault).build();
            }
        }

        static IsolateHandlerSnippet single(String startPoint, HotSpotToNativeTypeCache typeCache, DeclaredType isolateHandler) {
            return new Single(startPoint, startPoint, typeCache, isolateHandler);
        }

        static IsolateHandlerSnippet composite(String nativeOverrideStartPoint, Types types, HotSpotToNativeTypeCache typeCache,
                        DeclaredType hotSpotIsolateHandler, DeclaredType nativeIsolateHandler) {
            if (isCompatibleIsolateHandler(types, hotSpotIsolateHandler, nativeIsolateHandler)) {
                return new Single(START_POINT_FACTORY_SIMPLE_NAME, nativeOverrideStartPoint, typeCache, hotSpotIsolateHandler);
            } else {
                return new Composite(START_POINT_FACTORY_SIMPLE_NAME, nativeOverrideStartPoint, typeCache, hotSpotIsolateHandler, nativeIsolateHandler);
            }
        }

        private static boolean isCompatibleIsolateHandler(Types types, DeclaredType hotSpotIsolateHandler, DeclaredType nativeIsolateHandler) {
            if (hotSpotIsolateHandler != null && nativeIsolateHandler != null) {
                return types.isSameType(hotSpotIsolateHandler, nativeIsolateHandler);
            }
            return hotSpotIsolateHandler == null && nativeIsolateHandler == null;
        }

        private static final class Single extends IsolateHandlerSnippet {

            private final DeclaredType isolateHandler;

            private Single(String defaultName, String nativeOverrideName, HotSpotToNativeTypeCache typeCache, DeclaredType isolateHandler) {
                super(defaultName, nativeOverrideName, typeCache);
                this.isolateHandler = isolateHandler;
            }

            @Override
            CharSequence newIsolateHandler(CodeBuilder builder) {
                return isolateHandler != null ? createCustomObject(builder, isolateHandler) : null;
            }

            @Override
            CharSequence createIsolate(CodeBuilder builder, CharSequence isolateHandlerVar, CharSequence configVar) {
                if (isolateHandler != null) {
                    return new CodeBuilder(builder).invoke(isolateHandlerVar, "createIsolate", configVar).build();
                } else {
                    return new CodeBuilder(builder).invoke(defaultStartPointName(), "createIsolate").build();
                }
            }

            @Override
            CharSequence tearDownIsolateCallBack(CodeBuilder builder, CharSequence isolateHandlerVar) {
                if (isolateHandler != null) {
                    return new CodeBuilder(builder).write(isolateHandlerVar).write("::").write("tearDownIsolate").build();
                } else {
                    return new CodeBuilder(builder).write("(i,t) -> ").invoke(defaultStartPointName(), "tearDownIsolate", "t").build();
                }
            }
        }

        private static final class Composite extends IsolateHandlerSnippet {

            private final HotSpotToNativeTypeCache typeCache;
            private final DeclaredType hotSpotIsolateHandler;
            private final DeclaredType nativeIsolateHandler;

            Composite(String defaultName, String nativeOverrideName, HotSpotToNativeTypeCache typeCache, DeclaredType hotSpotIsolateHandler, DeclaredType nativeIsolateHandler) {
                super(defaultName, nativeOverrideName, typeCache);
                this.typeCache = typeCache;
                this.hotSpotIsolateHandler = hotSpotIsolateHandler;
                this.nativeIsolateHandler = nativeIsolateHandler;
            }

            @Override
            CharSequence newIsolateHandler(CodeBuilder builder) {
                if (hotSpotIsolateHandler != null) {
                    if (nativeIsolateHandler != null) {
                        return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").//
                                        write(createCustomObject(builder, nativeIsolateHandler)).write(" : ").//
                                        write(createCustomObject(builder, hotSpotIsolateHandler)).build();
                    } else {
                        return new CodeBuilder(builder).write(createCustomObject(builder, hotSpotIsolateHandler)).build();
                    }
                } else {
                    assert nativeIsolateHandler != null;
                    return new CodeBuilder(builder).write(createCustomObject(builder, nativeIsolateHandler)).build();
                }
            }

            @Override
            CharSequence createIsolate(CodeBuilder builder, CharSequence isolateHandlerVar, CharSequence configVar) {
                CharSequence invokeCreateIsolate = new CodeBuilder(builder).invoke(isolateHandlerVar, "createIsolate", configVar).build();
                CharSequence defaultCreateIsolate = new CodeBuilder(builder).invoke(defaultStartPointName(), "createIsolate").build();
                if (hotSpotIsolateHandler == null) {
                    return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").write(invokeCreateIsolate).write(" : ").write(defaultCreateIsolate).build();
                } else if (nativeIsolateHandler == null) {
                    return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").write(defaultCreateIsolate).write(" : ").write(invokeCreateIsolate).build();
                } else {
                    return invokeCreateIsolate;
                }
            }

            @Override
            CharSequence tearDownIsolateCallBack(CodeBuilder builder, CharSequence isolateHandlerVar) {
                CharSequence invokeTearDownIsolate = new CodeBuilder(builder).write(isolateHandlerVar).write("::").write("tearDownIsolate").build();
                CharSequence defaultTearDownIsolate = new CodeBuilder(builder).write("(i,t) -> ").invoke(defaultStartPointName(), "tearDownIsolate", "t").build();
                if (hotSpotIsolateHandler == null) {
                    return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").write(invokeTearDownIsolate).write(" : ").write(defaultTearDownIsolate).build();
                } else if (nativeIsolateHandler == null) {
                    return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").write(defaultTearDownIsolate).write(" : ").write(invokeTearDownIsolate).build();
                } else {
                    return invokeTearDownIsolate;
                }
            }
        }
    }

    private void generateCEntryPointAnnotation(CodeBuilder builder, CharSequence entryPointName, String builtin) {
        Map<String, Object> centryPointAttrs = new LinkedHashMap<>();
        centryPointAttrs.put("name", entryPointName);
        if (builtin != null) {
            VariableElement enumConstant = null;
            for (VariableElement field : ElementFilter.fieldsIn(getTypeCache().builtin.asElement().getEnclosedElements())) {
                if (builtin.contentEquals(field.getSimpleName())) {
                    enumConstant = field;
                }
            }
            assert enumConstant != null : "Builtin " + builtin + " does not exist in " + Utilities.getTypeName(getTypeCache().builtin);
            centryPointAttrs.put("builtin", enumConstant);
        }
        DeclaredType centryPointPredicate = getDefinition().centryPointPredicate;
        if (centryPointPredicate != null) {
            centryPointAttrs.put("include", centryPointPredicate);
        }
        builder.lineStart().annotationWithAttributes(getTypeCache().centryPoint, centryPointAttrs).lineEnd("");
    }
}
