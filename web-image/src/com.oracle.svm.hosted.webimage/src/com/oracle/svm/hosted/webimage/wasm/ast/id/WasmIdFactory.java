/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast.id;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlock;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Tag;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.BlockLabel;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.LoopLabel;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.MethodName;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.Param;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.SwitchLabel;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.TempLocal;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.WebImageWasmGCIds;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Thread safe factory for creating unique {@link WasmId}s.
 * <p>
 * Guarantees that any ids created with the same arguments produce the same object. This ensures
 * that we can use the equality operator for comparison instead of having to implement
 * {@link Object#equals(Object)} for all ids.
 * <p>
 * Once the instance is frozen it must not be accessed concurrently.
 */
public class WasmIdFactory {

    private final AtomicBoolean frozen = new AtomicBoolean(false);

    /**
     * Stores all ids created.
     */
    private final Set<WasmId> allIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConcurrentMap<ResolvedJavaMethod, MethodName> methodIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<HIRBlock, LoopLabel> loopLabels = new ConcurrentHashMap<>();
    private final ConcurrentMap<LabeledBlock, BlockLabel> blockLabels = new ConcurrentHashMap<>();
    private final Set<SwitchLabel> switchLabels = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentMap<ResolvedVar, WebImageWasmIds.NodeVariable> localVars = new ConcurrentHashMap<>();
    private final Set<Param> parameters = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<TempLocal> temporaryVariables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Table> tables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentMap<ImportDescriptor.Function, WasmId.FunctionImport> functionImports = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, WasmId.Memory> memories = new ConcurrentHashMap<>();
    private final Set<Tag> tags = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Global> globals = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConcurrentMap<ResolvedJavaType, WebImageWasmGCIds.JavaStruct> javaStructs = new ConcurrentHashMap<>();
    private final ConcurrentMap<JavaKind, WebImageWasmGCIds.JavaArrayStruct> javaArrayStructs = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResolvedJavaField, WebImageWasmGCIds.JavaField> javaFields = new ConcurrentHashMap<>();
    private final ConcurrentMap<JavaKind, WebImageWasmGCIds.JavaInnerArray> javaInnerArrays = new ConcurrentHashMap<>();
    private final Set<WebImageWasmGCIds.InternalField> internalFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<WebImageWasmGCIds.InternalStruct> internalStructs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<WebImageWasmGCIds.InternalArray> internalArrays = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<WasmId.Data> dataSegments = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentMap<FunctionTypeDescriptor, WebImageWasmIds.DescriptorFuncType> descriptorFuncTypes = new ConcurrentHashMap<>();
    private final Set<WebImageWasmGCIds.InternalFuncType> internalFuncTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<WebImageWasmIds.InternalFunction> internalFunctions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<WebImageWasmIds.InternalLabel> internalLabels = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentMap<ResolvedJavaField, WebImageWasmGCIds.StaticField> staticFields = new ConcurrentHashMap<>();

    public boolean isFrozen() {
        return frozen.get();
    }

    private boolean assertNotFrozen() {
        assert !isFrozen() : "Attempt to modify frozen factory";
        return true;
    }

    public void freeze() {
        assert !isFrozen() : "Factory is already frozen";
        frozen.set(true);
    }

    public Collection<WasmId> allIds() {
        assert isFrozen() : "Ids can only be retrieved once factory is frozen.";
        return Collections.unmodifiableSet(allIds);
    }

    private <T extends WasmId> T addId(T id) {
        assert id != null;
        boolean wasAdded = allIds.add(id);
        // Ensure no duplicate ids exist.
        assert wasAdded : id;
        return id;
    }

    /**
     * Creates a {@link WasmId} for the given key and inserts it into the map if it doesn't exist.
     *
     * @param key They key to look up or insert.
     * @param map Mappings from keys to ids.
     * @param constructor Creates the {@link WasmId} from the key
     * @return The created {@link WasmId}
     * @param <T> Map key type
     * @param <I> Created {@link WasmId} subtype
     */
    private <T, I extends WasmId> I createForKey(T key, Map<T, I> map, Function<T, I> constructor) {
        assert key != null;

        return map.computeIfAbsent(key, k -> {
            // Only disallow insertion when frozen. Querying should still work.
            assert assertNotFrozen();
            return addId(constructor.apply(k));
        });
    }

    /**
     * Creates a new {@link WasmId} and inserts it into the given set.
     *
     * @param set Set of {@linkplain WasmId ids}.
     * @param constructor Create a new {@link WasmId}
     * @return The created {@link WasmId}
     * @param <T> The {@link WasmId} subtype to create.
     */
    private <T extends WasmId> T insert(Set<T> set, Supplier<T> constructor) {
        assert assertNotFrozen();
        T id = addId(constructor.get());
        set.add(id);
        return id;
    }

    public MethodName forMethod(ResolvedJavaMethod method) {
        return createForKey(method, methodIds, WebImageWasmIds.MethodName::new);
    }

    public boolean hasMethod(ResolvedJavaMethod method) {
        return methodIds.containsKey(method);
    }

    public LoopLabel forLoopLabel(HIRBlock loopHeader) {
        return createForKey(loopHeader, loopLabels, WebImageWasmIds.LoopLabel::new);
    }

    public BlockLabel forBlockLabel(LabeledBlock block) {
        return createForKey(block, blockLabels, WebImageWasmIds.BlockLabel::new);
    }

    public SwitchLabel newSwitchLabel() {
        return insert(switchLabels, WebImageWasmIds.SwitchLabel::new);
    }

    public WebImageWasmIds.NodeVariable forVariable(ResolvedVar resolvedVar, WasmValType type) {
        return createForKey(resolvedVar, localVars, (var) -> new WebImageWasmIds.NodeVariable(var, type));
    }

    public Param newParam(int idx, WasmValType type) {
        assert NumUtil.assertNonNegativeInt(idx);
        return insert(parameters, () -> new Param(idx, type));
    }

    public TempLocal newTemporaryVariable(WasmValType type) {
        return insert(temporaryVariables, () -> new TempLocal(type));
    }

    public Table newTable() {
        return insert(tables, WasmId.Table::new);
    }

    public WasmId.FunctionImport forFunctionImport(ImportDescriptor.Function wasmImport) {
        return createForKey(wasmImport, functionImports, WasmId.FunctionImport::new);
    }

    public WasmId.Memory forMemory(int num) {
        // For now, WASM only supports a single memory
        assert num == 0 : "Only memory index 0 is allowed: " + num;
        return createForKey(num, memories, WasmId.Memory::new);
    }

    public Tag newTag(TypeUse typeUse) {
        return insert(tags, () -> new Tag(typeUse));
    }

    public Global newGlobal(WasmValType type, String name) {
        return insert(globals, () -> new Global(type, name));
    }

    public WebImageWasmGCIds.JavaStruct newJavaStruct(ResolvedJavaType type) {
        return createForKey(type, javaStructs, WebImageWasmGCIds.JavaStruct::new);
    }

    public WebImageWasmGCIds.JavaArrayStruct newJavaArrayStruct(JavaKind kind) {
        return createForKey(kind, javaArrayStructs, WebImageWasmGCIds.JavaArrayStruct::new);
    }

    public WebImageWasmGCIds.JavaField newJavaField(ResolvedJavaField field) {
        return createForKey(field, javaFields, WebImageWasmGCIds.JavaField::new);
    }

    public WebImageWasmGCIds.JavaInnerArray newJavaInnerArray(JavaKind kind) {
        return createForKey(kind, javaInnerArrays, WebImageWasmGCIds.JavaInnerArray::new);
    }

    public WebImageWasmGCIds.InternalField newInternalField(String name) {
        return insert(internalFields, () -> new WebImageWasmGCIds.InternalField(name));
    }

    public WebImageWasmGCIds.InternalStruct newInternalStruct(String name) {
        return insert(internalStructs, () -> new WebImageWasmGCIds.InternalStruct(name));
    }

    public WebImageWasmGCIds.InternalArray newInternalArray(String name) {
        return insert(internalArrays, () -> new WebImageWasmGCIds.InternalArray(name));
    }

    public WasmId.Data newDataSegment(String name) {
        return insert(dataSegments, () -> new WasmId.Data(name));
    }

    public WebImageWasmIds.DescriptorFuncType newFuncType(FunctionTypeDescriptor descriptor) {
        return createForKey(descriptor, descriptorFuncTypes, WebImageWasmIds.DescriptorFuncType::new);
    }

    public WebImageWasmGCIds.InternalFuncType newInternalFuncType(String name) {
        return insert(internalFuncTypes, () -> new WebImageWasmGCIds.InternalFuncType(name));
    }

    public WebImageWasmIds.InternalFunction newInternalFunction(String name) {
        return insert(internalFunctions, () -> new WebImageWasmIds.InternalFunction(name));
    }

    public WebImageWasmIds.InternalLabel newInternalLabel(String name) {
        return insert(internalLabels, () -> new WebImageWasmIds.InternalLabel(name));
    }

    public WebImageWasmGCIds.StaticField forStaticField(WasmValType fieldType, ResolvedJavaField field) {
        return createForKey(field, staticFields, f -> new WebImageWasmGCIds.StaticField(fieldType, f));
    }
}
