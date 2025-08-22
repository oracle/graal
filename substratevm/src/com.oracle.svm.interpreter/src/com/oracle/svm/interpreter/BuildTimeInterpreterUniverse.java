/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.svm.interpreter.classfile.ClassFile;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pltgot.GOTEntryAllocator;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedPrimitiveType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverseImpl;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Maintains the view of classes, methods and fields needed to build the InterpreterUniverse, this
 * is all metadata that is required for execution in the interpreter at run-time.
 *
 * The view is not a 1:1 mapping to other universes. For example, some methods might not be
 * executable by the interpreter and are therefore stripped from the InterpreterUniverse partially
 * (e.g. bytecode array is dropped, but a method pointer to the compiled version is still held).
 *
 * {@link #snapshot()} persist the current view into a InterpreterUniverse at the end of an image
 * build and is then serialized to an additional file.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class BuildTimeInterpreterUniverse {

    private static BuildTimeInterpreterUniverse INSTANCE;

    public static void freshSingletonInstance() {
        INSTANCE = new BuildTimeInterpreterUniverse();
    }

    private final Map<ResolvedJavaType, InterpreterResolvedJavaType> types;
    private final Map<String, UnresolvedJavaType> unresolvedTypes;
    private final Map<String, UnresolvedJavaMethod> unresolvedMethods;
    private final Map<String, UnresolvedJavaField> unresolvedFields;

    private final Map<ResolvedJavaField, InterpreterResolvedJavaField> fields;

    private final Map<ResolvedJavaMethod, InterpreterResolvedJavaMethod> methods;
    private final Map<String, InterpreterUnresolvedSignature> signatures;
    private final Map<Number, PrimitiveConstant> primitiveConstants;
    private final Map<String, String> strings;
    private final Map<ImageHeapConstant, ReferenceConstant<?>> objectConstants;

    private final Map<ExceptionHandler, ExceptionHandler> exceptionHandlers;

    private SnippetReflectionProvider snippetReflectionProvider;

    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflectionProvider;
    }

    private void setSnippetReflectionProvider(SnippetReflectionProvider snippetReflectionProvider) {
        this.snippetReflectionProvider = snippetReflectionProvider;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedObjectType createResolvedObjectType(ResolvedJavaType resolvedJavaType) {
        BuildTimeInterpreterUniverse universe = BuildTimeInterpreterUniverse.singleton();
        String name = universe.dedup(resolvedJavaType.getName());
        Class<?> clazz = OriginalClassProvider.getJavaClass(resolvedJavaType);
        ResolvedJavaType originalType = MetadataUtil.requireNonNull(resolvedJavaType);
        int modifiers = resolvedJavaType.getModifiers();
        InterpreterResolvedJavaType componentType;
        if (originalType.isArray()) {
            componentType = universe.getOrCreateType(originalType.getComponentType());
        } else {
            componentType = null;
        }
        String sourceFileName = universe.dedup(resolvedJavaType.getSourceFileName());

        ResolvedJavaType originalSuperclass = resolvedJavaType.getSuperclass();
        InterpreterResolvedObjectType superclass = null;
        if (originalSuperclass != null) {
            superclass = (InterpreterResolvedObjectType) universe.getOrCreateType(originalSuperclass);
        }

        ResolvedJavaType[] originalInterfaces = resolvedJavaType.getInterfaces();
        InterpreterResolvedObjectType[] interfaces = new InterpreterResolvedObjectType[originalInterfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaces[i] = (InterpreterResolvedObjectType) universe.getOrCreateType(originalInterfaces[i]);
        }

        return InterpreterResolvedObjectType.createAtBuildTime(resolvedJavaType, name, modifiers, componentType, superclass, interfaces, null, clazz, sourceFileName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaField createResolvedJavaField(ResolvedJavaField resolvedJavaField) {
        ResolvedJavaField originalField = resolvedJavaField;
        BuildTimeInterpreterUniverse universe = BuildTimeInterpreterUniverse.singleton();
        String name = universe.dedup(resolvedJavaField.getName());
        int modifiers = resolvedJavaField.getModifiers();
        JavaType fieldType = originalField.getType();

        InterpreterResolvedJavaType type = universe.getOrCreateType((ResolvedJavaType) fieldType);
        InterpreterResolvedObjectType declaringClass = universe.referenceType(originalField.getDeclaringClass());

        return InterpreterResolvedJavaField.create(originalField, name, modifiers, type, declaringClass, 0, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaMethod createResolveJavaMethod(ResolvedJavaMethod originalMethod) {
        assert originalMethod instanceof AnalysisMethod;
        MetadataUtil.requireNonNull(originalMethod);
        BuildTimeInterpreterUniverse universe = BuildTimeInterpreterUniverse.singleton();
        String name = universe.dedup(originalMethod.getName());
        int maxLocals = originalMethod.getMaxLocals();
        int maxStackSize = originalMethod.getMaxStackSize();
        int modifiers = originalMethod.getModifiers();
        InterpreterResolvedObjectType declaringClass = universe.referenceType(originalMethod.getDeclaringClass());
        InterpreterUnresolvedSignature signature = universe.unresolvedSignature(originalMethod.getSignature());
        byte[] interpretedCode = originalMethod.getCode() == null ? null : originalMethod.getCode().clone();

        AnalysisMethod analysisMethod = (AnalysisMethod) originalMethod;
        if (analysisMethod.wrapped instanceof SubstitutionMethod substitutionMethod) {
            modifiers = substitutionMethod.getOriginal().getModifiers();
            if (substitutionMethod.hasBytecodes()) {
                /*
                 * GR-53710: Keep bytecodes for substitutions, but only when there's no compiled
                 * entry. This is required to call Class.forName(String,boolean,ClassLoader) which
                 * is a substitution with no compiled entry.
                 */
                // Drop NATIVE flag from original method modifiers.
                modifiers &= ~Modifier.NATIVE;
            }
        }

        LineNumberTable lineNumberTable = originalMethod.getLineNumberTable();
        return InterpreterResolvedJavaMethod.create(
                        originalMethod,
                        name,
                        maxLocals,
                        maxStackSize,
                        modifiers,
                        declaringClass,
                        signature,
                        interpretedCode,
                        null,
                        lineNumberTable,
                        null,
                        null,
                        InterpreterResolvedJavaMethod.VTBL_NO_ENTRY,
                        GOTEntryAllocator.GOT_NO_ENTRY,
                        InterpreterResolvedJavaMethod.EST_NO_ENTRY,
                        InterpreterResolvedJavaMethod.UNKNOWN_METHOD_ID);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterUnresolvedSignature createUnresolvedSignature(Signature originalSignature) {
        MetadataUtil.requireNonNull(originalSignature);
        JavaType returnType = BuildTimeInterpreterUniverse.singleton().primitiveOrUnresolvedType(originalSignature.getReturnType(null));
        int parameterCount = originalSignature.getParameterCount(false);
        JavaType[] parameterTypes = new JavaType[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            parameterTypes[i] = BuildTimeInterpreterUniverse.singleton().primitiveOrUnresolvedType(originalSignature.getParameterType(i, null));
        }
        return InterpreterUnresolvedSignature.create(originalSignature, returnType, parameterTypes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static LocalVariableTable processLocalVariableTable(LocalVariableTable hostLocalVariableTable) {
        Local[] hostLocals = hostLocalVariableTable.getLocals();
        if (hostLocals.length == 0) {
            return InterpreterResolvedJavaMethod.EMPTY_LOCAL_VARIABLE_TABLE;
        }
        Local[] locals = new Local[hostLocals.length];
        for (int i = 0; i < locals.length; i++) {
            Local host = hostLocals[i];
            JavaType hostType = host.getType();
            JavaType interpreterType = null;
            if (hostType == null) {
                InterpreterUtil.log("[processLocalVariableTable] BUG? host=%s.  Remove local entry?", host);
            } else {
                interpreterType = BuildTimeInterpreterUniverse.singleton().typeOrUnresolved(hostType);
            }
            if (hostType instanceof AnalysisType && !((AnalysisType) hostType).isReachable()) {
                /*
                 * Example: SecurityManager in java.lang.ThreadGroup.checkAccess. There is a
                 * graphbuilder plugin that makes sure getSecurityManager() always returns null,
                 * thus not reachable.
                 *
                 * For now, unresolved and unreachable types are not an issue since the
                 * LocalVariableTable attribute only keeps strictly unresolved types and primitives.
                 */
            }
            locals[i] = BuildTimeInterpreterUniverse.singleton().local(new Local(host.getName(), interpreterType, host.getStartBCI(), host.getEndBCI(), host.getSlot()));
        }
        return BuildTimeInterpreterUniverse.singleton().localVariableTable(new LocalVariableTable(locals));
    }

    /* Not thread-safe, only call in single thread context */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static void setNeedMethodBody(InterpreterResolvedJavaMethod thiz, boolean needMethodBody, MetaAccessProvider metaAccessProvider) {
        if (thiz.needMethodBody && needMethodBody) {
            // skip, already scanned
            return;
        } else if (!needMethodBody) {
            // nothing to do
            thiz.needMethodBody = needMethodBody;
            return;
        }

        byte[] code = thiz.getInterpretedCode();
        if (code == null) {
            // nothing to scan, method is not interpreterExecutable
            return;
        }

        thiz.needMethodBody = true;

        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.opcode(code, bci);
            switch (opcode) {
                /* GR-53540: Handle invokedyanmic too */
                case INVOKEVIRTUAL, INVOKEINTERFACE -> {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    try {
                        JavaMethod method = thiz.getOriginalMethod().getConstantPool().lookupMethod(originalCPI, opcode);
                        if (!(method instanceof ResolvedJavaMethod resolvedJavaMethod)) {
                            continue;
                        }
                        if (!InterpreterFeature.callableByInterpreter(resolvedJavaMethod, metaAccessProvider)) {
                            return;
                        }
                        BuildTimeInterpreterUniverse.singleton().getOrCreateMethodWithMethodBody(resolvedJavaMethod, metaAccessProvider);
                    } catch (UnsupportedFeatureException | UserError.UserException e) {
                        InterpreterUtil.log("[process invokes] lookup in method %s failed due to:", thiz.getOriginalMethod());
                        InterpreterUtil.log(e);
                        // ignore, call will fail at run-time if reached
                    }
                }
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setUnmaterializedConstantValue(InterpreterResolvedJavaField thiz, JavaConstant constant) {
        assert constant == JavaConstant.NULL_POINTER || constant instanceof PrimitiveConstant || constant instanceof ImageHeapConstant;
        BuildTimeInterpreterUniverse buildTimeInterpreterUniverse = BuildTimeInterpreterUniverse.singleton();
        switch (thiz.getJavaKind()) {
            case Boolean, Byte, Short, Char, Int, Float, Long, Double:
                assert constant instanceof PrimitiveConstant;
                thiz.setUnmaterializedConstant(buildTimeInterpreterUniverse.constant(constant));
                break;
            case Object:
                if (constant.isNull()) {
                    // The value is always null.
                    thiz.setUnmaterializedConstant(buildTimeInterpreterUniverse.constant(JavaConstant.NULL_POINTER));
                } else if (constant.getJavaKind() == JavaKind.Illegal) {
                    // Materialized field without location e.g. DynamicHub#vtable.
                    thiz.setUnmaterializedConstant(buildTimeInterpreterUniverse.constant(JavaConstant.ILLEGAL));
                } else if (thiz.getType().isWordType()) {
                    // Can be a WordType with a primitive constant value.
                    thiz.setUnmaterializedConstant(buildTimeInterpreterUniverse.constant(constant));
                } else if (constant instanceof ImageHeapConstant imageHeapConstant) {
                    // Create a WeakImageHeapReference, the referent is only preserved iff it is
                    // present in the native image heap.
                    thiz.setUnmaterializedConstant(ReferenceConstant.createFromImageHeapConstant(imageHeapConstant));
                } else {
                    throw VMError.shouldNotReachHere("Unsupported unmaterialized constant value: " + constant);
                }
                break;
            default:
                throw VMError.shouldNotReachHere("Invalid field kind: " + thiz.getJavaKind());
        }
        if (!thiz.isUndefined()) {
            if (thiz.getType().isWordType()) {
                VMError.guarantee(thiz.getUnmaterializedConstant().getJavaKind() == InterpreterToVM.wordJavaKind());
            } else {
                VMError.guarantee(thiz.getUnmaterializedConstant().getJavaKind() == thiz.getJavaKind());
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class LocalWrapper {

        final int hash;
        final Local local;

        private LocalWrapper(Local local) {
            this.local = MetadataUtil.requireNonNull(local);
            this.hash = hashCode(this.local);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof LocalWrapper thatWrapper) {
                Local that = thatWrapper.local;
                return local.getName().equals(that.getName()) && local.getStartBCI() == that.getStartBCI() && local.getEndBCI() == that.getEndBCI() && local.getSlot() == that.getSlot() &&
                                MetadataUtil.equals(local.getType(), that.getType());

            } else {
                return false;
            }
        }

        public static int hashCode(Local local) {
            int h = MetadataUtil.hashCode(local.getName());
            h = h * 31 + local.getStartBCI();
            h = h * 31 + local.getEndBCI();
            h = h * 31 + local.getSlot();
            h = h * 31 + MetadataUtil.hashCode(local.getType());
            return h;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private final Map<LocalWrapper, LocalWrapper> locals;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class LocalVariableTableWrapper {
        final LocalVariableTable localVariableTable;
        final Local[] locals;
        final int hash;

        private LocalVariableTableWrapper(LocalVariableTable localVariableTable) {
            this.localVariableTable = localVariableTable;
            this.locals = localVariableTable.getLocals();
            int h = 0;
            for (Local local : this.locals) {
                h = h * 31 + LocalWrapper.hashCode(local);
            }
            this.hash = h;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof LocalVariableTableWrapper thatWrapper) {
                if (localVariableTable == thatWrapper.localVariableTable) {
                    return true;
                }
                return Arrays.equals(locals, thatWrapper.locals);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private final Map<LocalVariableTableWrapper, LocalVariableTableWrapper> localVariableTables;

    private final Map<JavaConstant, JavaConstant> indyAppendices;

    public BuildTimeInterpreterUniverse() {
        this.types = new ConcurrentHashMap<>();
        this.fields = new ConcurrentHashMap<>();
        this.methods = new ConcurrentHashMap<>();
        this.signatures = new ConcurrentHashMap<>();
        this.primitiveConstants = new ConcurrentHashMap<>();
        this.strings = new ConcurrentHashMap<>();
        this.objectConstants = new ConcurrentHashMap<>();
        this.exceptionHandlers = new ConcurrentHashMap<>();
        this.locals = new ConcurrentHashMap<>();
        this.localVariableTables = new ConcurrentHashMap<>();
        this.unresolvedTypes = new ConcurrentHashMap<>();
        this.unresolvedMethods = new ConcurrentHashMap<>();
        this.unresolvedFields = new ConcurrentHashMap<>();
        this.indyAppendices = new ConcurrentHashMap<>();
    }

    public static BuildTimeInterpreterUniverse singleton() {
        return INSTANCE;
    }

    public InterpreterResolvedObjectType referenceType(ResolvedJavaType resolvedJavaType) {
        return (InterpreterResolvedObjectType) getOrCreateType(resolvedJavaType);
    }

    @SuppressWarnings("static-method")
    public String dedup(String string) {
        return HostedStringDeduplication.singleton().deduplicate(string, false);
    }

    public InterpreterResolvedPrimitiveType primitiveType(ResolvedJavaType resolvedJavaType) {
        return (InterpreterResolvedPrimitiveType) getOrCreateType(resolvedJavaType);
    }

    public InterpreterResolvedJavaType getType(ResolvedJavaType resolvedJavaType) {
        return types.get(resolvedJavaType);
    }

    public InterpreterResolvedJavaType getOrCreateType(ResolvedJavaType resolvedJavaType) {
        assert resolvedJavaType instanceof AnalysisType;
        InterpreterResolvedJavaType result = getType(resolvedJavaType);

        if (result != null) {
            return result;
        }

        if (resolvedJavaType.isPrimitive()) {
            result = InterpreterResolvedPrimitiveType.fromKind(JavaKind.fromPrimitiveOrVoidTypeChar(resolvedJavaType.getName().charAt(0)));
        } else {
            result = createResolvedObjectType(resolvedJavaType);
        }

        InterpreterResolvedJavaType previous = types.putIfAbsent(resolvedJavaType, result);
        if (previous != null) {
            return previous;
        }

        InterpreterUtil.log("[universe] Adding type '%s'", resolvedJavaType);
        return result;
    }

    public InterpreterResolvedJavaField getOrCreateField(ResolvedJavaField resolvedJavaField) {
        assert resolvedJavaField instanceof AnalysisField;
        InterpreterResolvedJavaField result = fields.get(resolvedJavaField);

        if (result != null) {
            return result;
        }

        result = createResolvedJavaField(resolvedJavaField);

        InterpreterResolvedJavaField previous = fields.putIfAbsent(resolvedJavaField, result);
        if (previous != null) {
            return previous;
        }

        InterpreterUtil.log("[universe] Adding field '%s'", resolvedJavaField);
        return result;
    }

    public InterpreterResolvedJavaMethod getMethod(ResolvedJavaMethod method) {
        ResolvedJavaMethod wrapped = method;
        if (wrapped instanceof HostedMethod hostedMethod) {
            wrapped = hostedMethod.getWrapped();
        }
        return methods.get(wrapped);
    }

    public InterpreterResolvedJavaMethod getOrCreateMethod(ResolvedJavaMethod resolvedJavaMethod) {
        assert resolvedJavaMethod instanceof AnalysisMethod;
        InterpreterResolvedJavaMethod result = getMethod(resolvedJavaMethod);

        if (result != null) {
            return result;
        }

        result = createResolveJavaMethod(resolvedJavaMethod);

        InterpreterResolvedJavaMethod previous = methods.putIfAbsent(resolvedJavaMethod, result);
        if (previous != null) {
            return previous;
        }

        InterpreterUtil.log("[universe] Adding method '%s'", resolvedJavaMethod);
        return result;
    }

    public InterpreterResolvedJavaMethod getOrCreateMethodWithMethodBody(ResolvedJavaMethod resolvedJavaMethod, MetaAccessProvider metaAccessProvider) {
        InterpreterResolvedJavaMethod result = getOrCreateMethod(resolvedJavaMethod);

        /* added explicitly, bytecodes are needed for interpretation */
        setNeedMethodBody(result, true, metaAccessProvider);

        return result;
    }

    public JavaConstant weakObjectConstant(ImageHeapConstant imageHeapConstant) {
        // Try to extract hosted references if possible.
        // Some constants are stored in the interpreter metadata even if they are not included in
        // the image heap e.g. String.
        if (imageHeapConstant.isBackedByHostedObject()) {
            Object value = snippetReflectionProvider.asObject(Object.class, imageHeapConstant.getHostedObject());
            if (value != null) {
                return objectConstants.computeIfAbsent(imageHeapConstant, (key) -> ReferenceConstant.createFromNonNullReference(value));
            }
        }
        return objectConstants.computeIfAbsent(imageHeapConstant, (key) -> ReferenceConstant.createFromImageHeapConstant(imageHeapConstant));
    }

    public PrimitiveConstant primitiveConstant(int value) {
        return primitiveConstants.computeIfAbsent(value, (key) -> JavaConstant.forInt(value));
    }

    public PrimitiveConstant primitiveConstant(long value) {
        return primitiveConstants.computeIfAbsent(value, (key) -> JavaConstant.forLong(value));
    }

    public PrimitiveConstant primitiveConstant(float value) {
        return primitiveConstants.computeIfAbsent(value, (key) -> JavaConstant.forFloat(value));
    }

    public PrimitiveConstant primitiveConstant(double value) {
        return primitiveConstants.computeIfAbsent(value, (key) -> JavaConstant.forDouble(value));
    }

    public String stringConstant(String value) {
        return strings.computeIfAbsent(value, Function.identity());
    }

    public JavaType primitiveOrUnresolvedType(JavaType type) {
        // Primitives are always resolved.
        if (type.getJavaKind().isPrimitive()) {
            return InterpreterResolvedPrimitiveType.fromKind(type.getJavaKind());
        }
        return unresolvedTypes.computeIfAbsent(dedup(MetadataUtil.toUniqueString(type)), UnresolvedJavaType::create);
    }

    public JavaConstant appendix(JavaConstant appendix) {
        Objects.requireNonNull(appendix);
        JavaConstant result = indyAppendices.get(appendix);
        if (result != null) {
            return result;
        }
        if (appendix instanceof ImageHeapConstant imageHeapConstant) {
            result = weakObjectConstant(imageHeapConstant);
        } else {
            VMError.shouldNotReachHere("unexpected appendix: " + appendix);
        }

        JavaConstant previous = indyAppendices.putIfAbsent(appendix, result);
        if (previous != null) {
            return previous;
        }

        return result;
    }

    public InterpreterUnresolvedSignature unresolvedSignature(Signature signature) {
        return signatures.computeIfAbsent(MetadataUtil.toUniqueString(signature), key -> createUnresolvedSignature(signature));
    }

    public JavaType typeOrUnresolved(JavaType type) {
        JavaType result = types.get(type);
        if (result == null) {
            // UnresolvedJavaType can be trusted because it only refers to type by name.
            result = primitiveOrUnresolvedType(type);
        }
        return result;
    }

    public ExceptionHandler exceptionHandler(ExceptionHandler handler) {
        return exceptionHandlers.computeIfAbsent(handler, Function.identity());
    }

    public Local local(Local local) {
        return locals.computeIfAbsent(new LocalWrapper(local), Function.identity()).local;
    }

    public LocalVariableTable localVariableTable(LocalVariableTable localVariableTable) {
        return localVariableTables.computeIfAbsent(new LocalVariableTableWrapper(localVariableTable), Function.identity()).localVariableTable;
    }

    // returns InterpreterResolvedJavaField | UnresolvedJavaField
    public JavaField fieldOrUnresolved(JavaField field) {
        JavaField result = fields.get(field);
        if (result == null) {
            // Do not trust incoming UnresolvedJavaField, an unresolved field may have a resolved
            // declaring type.
            JavaType holder = primitiveOrUnresolvedType(field.getDeclaringClass());
            JavaType type = primitiveOrUnresolvedType(field.getType());
            result = unresolvedFields.computeIfAbsent(MetadataUtil.toUniqueString(field), key -> new UnresolvedJavaField(holder, dedup(field.getName()), type));
        }
        return result;
    }

    // returns InterpreterResolvedJavaMethod | UnresolvedJavaMethod
    public JavaMethod methodOrUnresolved(JavaMethod method0) {
        final JavaMethod method = method0 instanceof HostedMethod hostedMethod ? hostedMethod.wrapped : method0;
        JavaMethod result = methods.get(method);
        if (result == null) {
            // Do not trust incoming unresolved method, it may have resolved holder.
            JavaType holder = primitiveOrUnresolvedType(method.getDeclaringClass());
            Signature signature = unresolvedSignature(method.getSignature());
            result = unresolvedMethods.computeIfAbsent(MetadataUtil.toUniqueString(method), key -> new UnresolvedJavaMethod(dedup(method.getName()), signature, holder));
        }
        return result;
    }

    private Map<InterpreterResolvedObjectType, List<InterpreterResolvedJavaMethod>> classToMethods;
    private Map<InterpreterResolvedObjectType, List<InterpreterResolvedJavaField>> classToFields;

    /**
     * This classes cause problems when resolving constant pool entries.
     */
    public void createConstantPools(HostedUniverse hUniverse) {
        setSnippetReflectionProvider(hUniverse.getSnippetReflection());

        this.classToMethods = methods.values().stream().collect(Collectors.groupingBy(InterpreterResolvedJavaMethod::getDeclaringClass));
        this.classToFields = fields.values().stream().collect(Collectors.groupingBy(InterpreterResolvedJavaField::getDeclaringClass));

        boolean needsAnotherRound = true;
        int iterations = 0;
        while (needsAnotherRound) {
            needsAnotherRound = false;
            iterations++;
            InterpreterUtil.log("[weedout] iteration %s", iterations);
            for (InterpreterResolvedJavaType type : types.values()) {
                if (type instanceof InterpreterResolvedObjectType referenceType) {
                    needsAnotherRound |= BuildTimeConstantPool.weedOut(referenceType, hUniverse);
                }
            }
        }

        for (InterpreterResolvedJavaType type : types.values()) {
            if (type instanceof InterpreterResolvedObjectType referenceType) {
                // TODO(peterssen): GR-68564 Obtain proper major/minor version for this type.
                BuildTimeConstantPool buildTimeConstantPool = BuildTimeConstantPool.create(referenceType, ClassFile.MAJOR_VERSION, ClassFile.MINOR_VERSION);
                referenceType.setConstantPool(buildTimeConstantPool.snapshot());
            }
        }
    }

    public List<InterpreterResolvedJavaMethod> allDeclaredMethods(InterpreterResolvedObjectType type) {
        return classToMethods.getOrDefault(type, Collections.emptyList());
    }

    public List<InterpreterResolvedJavaField> allDeclaredFields(InterpreterResolvedObjectType type) {
        return classToFields.getOrDefault(type, Collections.emptyList());
    }

    public Collection<InterpreterResolvedJavaField> getFields() {
        return fields.values();
    }

    public Collection<InterpreterResolvedJavaMethod> getMethods() {
        return methods.values();
    }

    public Collection<InterpreterResolvedJavaType> getTypes() {
        return types.values();
    }

    private static boolean isReachable(InterpreterResolvedJavaType type) {
        if (type instanceof InterpreterResolvedPrimitiveType) {
            return true;
        }
        AnalysisType originalType = (AnalysisType) ((InterpreterResolvedObjectType) type).getOriginalType();
        return originalType.isReachable();
    }

    static boolean isReachable(InterpreterResolvedJavaField field) {
        AnalysisField originalField = (AnalysisField) field.getOriginalField();
        // Artificial reachability ensures that the interpreter keeps the field metadata around,
        // but reachability still depends on the reachability of the declaring class and field type.
        return field.isArtificiallyReachable() || (originalField.isReachable() && originalField.getDeclaringClass().isReachable());
    }

    static boolean isReachable(InterpreterResolvedJavaMethod method) {
        AnalysisMethod originalMethod = (AnalysisMethod) method.getOriginalMethod();
        return originalMethod.isReachable() && originalMethod.getDeclaringClass().isReachable();
    }

    public void purgeUnreachable(MetaAccessProvider metaAccessProvider) {

        List<InterpreterResolvedObjectType> nonReachableTypes = new ArrayList<>();
        for (InterpreterResolvedJavaType type : types.values()) {
            if (!isReachable(type)) {
                nonReachableTypes.add((InterpreterResolvedObjectType) type);
            }
        }
        Iterator<Map.Entry<ResolvedJavaMethod, InterpreterResolvedJavaMethod>> iteratorMethods = methods.entrySet().iterator();
        while (iteratorMethods.hasNext()) {
            Map.Entry<ResolvedJavaMethod, InterpreterResolvedJavaMethod> next = iteratorMethods.next();
            InterpreterResolvedJavaMethod interpreterMethod = next.getValue();
            InterpreterResolvedObjectType declaringClass = interpreterMethod.getDeclaringClass();
            if (isReachable(interpreterMethod) && interpreterMethod.isInterpreterExecutable() && !isReachable(declaringClass)) {
                InterpreterUtil.log("[purge] declaring class=%s of method=%s is not reachable, which cannot be represented in the interpreter universe currently", declaringClass, interpreterMethod);
                VMError.shouldNotReachHere("declaring class should be reachable");
            }

            int removeMethodReason = 0;

            if (!isReachable(interpreterMethod) || !interpreterMethod.isInterpreterExecutable()) {
                /*
                 * we might need that method as a holder for the call-site signature and vtable
                 * index
                 */
                InterpreterUtil.log("[purge] downgrading method=%s", interpreterMethod);
                setNeedMethodBody(interpreterMethod, false, metaAccessProvider);
                if (!isReachable(declaringClass)) {
                    InterpreterUtil.log("[purge] remove declaring class=%s", declaringClass);
                    nonReachableTypes.add(declaringClass);
                    removeMethodReason |= (1 << 0);
                }
            }

            if (!isReachable(interpreterMethod)) {
                AnalysisMethod analysisMethod = (AnalysisMethod) interpreterMethod.getOriginalMethod();
                boolean isRoot = analysisMethod.isDirectRootMethod() || analysisMethod.isVirtualRootMethod() || analysisMethod.isInvoked();
                int implementations = analysisMethod.collectMethodImplementations(true).size();
                if (!isRoot && (next.getValue().isStatic() || implementations <= 1)) {
                    removeMethodReason |= (1 << 1);
                }
            }
            if (removeMethodReason > 0) {
                InterpreterUtil.log("[purge] remove method '%s' with reason %s", interpreterMethod, Integer.toBinaryString(removeMethodReason));
                iteratorMethods.remove();
            }
        }
        Iterator<Map.Entry<ResolvedJavaField, InterpreterResolvedJavaField>> iteratorFields = fields.entrySet().iterator();
        while (iteratorFields.hasNext()) {
            Map.Entry<ResolvedJavaField, InterpreterResolvedJavaField> next = iteratorFields.next();
            if (!isReachable(next.getValue()) || !isReachable(next.getValue().getDeclaringClass()) || !isReachable(next.getValue().getType())) {
                InterpreterUtil.log("[purge] remove field '%s'", next.getValue());
                iteratorFields.remove();
            }
        }
        for (InterpreterResolvedObjectType nonReachableType : nonReachableTypes) {
            InterpreterUtil.log("[purge] remove type '%s'", nonReachableType);
            types.remove(nonReachableType.getOriginalType());
        }

        // Verification.
        for (InterpreterResolvedJavaType type : types.values()) {
            VMError.guarantee(isReachable(type));
        }

        for (InterpreterResolvedJavaField field : fields.values()) {
            VMError.guarantee(isReachable(field));
        }

        for (InterpreterResolvedJavaMethod method : methods.values()) {
            VMError.guarantee(isReachable(method) || !method.isStatic(), "non reachable");
        }
    }

    static void topSort(ResolvedJavaType type, List<ResolvedJavaType> order, Set<ResolvedJavaType> seen) {
        if (type == null || seen.contains(type)) {
            return;
        }
        topSort(type.getSuperclass(), order, seen);
        topSort(type.getComponentType(), order, seen);
        for (ResolvedJavaType interf : type.getInterfaces()) {
            topSort(interf, order, seen);
        }
        order.add(type);
        seen.add(type);
    }

    static List<ResolvedJavaType> topologicalOrder(Collection<? extends ResolvedJavaType> types) {
        List<ResolvedJavaType> order = new ArrayList<>(types.size());
        Set<ResolvedJavaType> seen = Collections.newSetFromMap(new IdentityHashMap<>(types.size()));
        for (ResolvedJavaType type : types) {
            topSort(type, order, seen);
        }
        assert types.size() == order.size();
        return order;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public InterpreterUniverseImpl snapshot() {
        Collection<InterpreterResolvedJavaType> values = types.values();
        // All type dependencies must appear strictly before in the ordering for serialization.
        // Superclasses, super interfaces and component type of T must appear strictly before T.
        List<ResolvedJavaType> topologicalOrder = topologicalOrder(values);
        assert checkOrder(topologicalOrder);
        assert topologicalOrder.stream().allMatch(type -> type instanceof InterpreterResolvedJavaType);
        List<InterpreterResolvedJavaType> result = (List) topologicalOrder;
        return new InterpreterUniverseImpl(result, fields.values(), methods.values());
    }

    private static List<ResolvedJavaType> dependencies(ResolvedJavaType type) {
        List<ResolvedJavaType> result = new ArrayList<>(Arrays.asList(type.getInterfaces()));
        if (type.getSuperclass() != null) {
            result.add(type.getSuperclass());
        }
        if (type.getComponentType() != null) {
            result.add(type.getComponentType());
        }
        return result;
    }

    private static boolean checkOrder(List<ResolvedJavaType> order) {
        Set<ResolvedJavaType> seen = Collections.newSetFromMap(new IdentityHashMap<>(order.size()));
        for (ResolvedJavaType type : order) {
            for (ResolvedJavaType strictlyBefore : dependencies(type)) {
                if (!seen.contains(strictlyBefore)) {
                    return false;
                }
            }
            seen.add(type);
        }
        return true;
    }

    /**
     * Converts a {@link JavaConstant} into constant supported by the interpreter.
     *
     * @param constant a constant handled from analysis or hosted world.
     * @return a {@link PrimitiveConstant} or a {@link JavaConstant#NULL_POINTER}.
     */
    public JavaConstant constant(JavaConstant constant) {
        if (constant.getClass() == PrimitiveConstant.class) {
            PrimitiveConstant primitiveConstant = (PrimitiveConstant) constant;
            // Dedup constants.
            switch (primitiveConstant.getJavaKind()) {
                case Int:
                    return primitiveConstant(primitiveConstant.asInt());
                case Float:
                    return primitiveConstant(primitiveConstant.asFloat());
                case Long:
                    return primitiveConstant(primitiveConstant.asLong());
                case Double:
                    return primitiveConstant(primitiveConstant.asDouble());
                default:
                    return constant;
            }
        }
        if (constant.isNull()) {
            return JavaConstant.NULL_POINTER;
        }
        throw VMError.shouldNotReachHere("unsupported constant: " + constant);
    }

    public void mirrorSVMVTable(HostedType hostedType, Consumer<Object> rescanFieldInHeap) {
        AnalysisType analysisType = hostedType.getWrapped();
        InterpreterResolvedJavaType iType = getType(analysisType);

        if (!(iType instanceof InterpreterResolvedObjectType objectType)) {
            return;
        }
        HostedMethod[] hostedDispatchTable = hostedType.getInterpreterDispatchTable();
        VMError.guarantee(hostedDispatchTable != null, "Missing dispatch table for %s", hostedType);

        InterpreterResolvedJavaMethod[] iVTable;
        if (hostedDispatchTable.length == 0) {
            iVTable = InterpreterResolvedJavaType.NO_METHODS;
        } else {
            iVTable = new InterpreterResolvedJavaMethod[hostedDispatchTable.length];

            for (int i = 0; i < iVTable.length; i++) {
                iVTable[i] = getMethod(hostedDispatchTable[i].getWrapped());
            }
        }
        objectType.setVtable(iVTable);
        rescanFieldInHeap.accept(objectType);
    }
}
