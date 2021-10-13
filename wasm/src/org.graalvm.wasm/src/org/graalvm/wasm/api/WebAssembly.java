/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.api;

import static java.lang.Integer.compareUnsigned;
import static org.graalvm.wasm.WasmMath.minUnsigned;
import static org.graalvm.wasm.api.JsConstants.JS_LIMITS;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmCustomSection;
import org.graalvm.wasm.WasmMath;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.parser.module.WasmModule;
import org.graalvm.wasm.parser.module.imports.WasmFunctionImport;
import org.graalvm.wasm.parser.module.imports.WasmGlobalImport;
import org.graalvm.wasm.parser.module.imports.WasmMemoryImport;
import org.graalvm.wasm.parser.module.imports.WasmTableImport;
import org.graalvm.wasm.runtime.EmbedderDataHolder;
import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.runtime.WasmTable;
import org.graalvm.wasm.runtime.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.runtime.memory.UnsafeWasmMemory;
import org.graalvm.wasm.runtime.memory.WasmMemory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

public class WebAssembly extends Dictionary {
    private final WasmContext currentContext;

    public WebAssembly(WasmContext currentContext) {
        this.currentContext = currentContext;
        addMember("module_decode", new Executable(this::moduleDecode));
        addMember("module_instantiate", new Executable(this::moduleInstantiate));
        addMember("module_validate", new Executable(this::moduleValidate));

        addMember("table_alloc", new Executable(WebAssembly::tableAlloc));
        addMember("table_grow", new Executable(WebAssembly::tableGrow));
        addMember("table_read", new Executable(WebAssembly::tableRead));
        addMember("table_write", new Executable(WebAssembly::tableWrite));
        addMember("table_size", new Executable(WebAssembly::tableSize));

        addMember("func_type", new Executable(WebAssembly::funcType));

        addMember("mem_alloc", new Executable(WebAssembly::memAlloc));
        addMember("mem_grow", new Executable(WebAssembly::memGrow));
        addMember("mem_set_grow_callback", new Executable(WebAssembly::memSetGrowCallback));
        addMember("mem_as_byte_buffer", new Executable(WebAssembly::memAsByteBuffer));

        addMember("global_alloc", new Executable(WebAssembly::globalAlloc));
        addMember("global_read", new Executable(WebAssembly::globalRead));
        addMember("global_write", new Executable(WebAssembly::globalWrite));

        addMember("module_imports", new Executable(WebAssembly::moduleImports));
        addMember("module_exports", new Executable(WebAssembly::moduleExports));

        addMember("custom_sections", new Executable(WebAssembly::customSections));

        addMember("instance_export", new Executable(WebAssembly::instanceExport));

        addMember("embedder_data_get", new Executable(WebAssembly::embedderDataGet));
        addMember("embedder_data_set", new Executable(WebAssembly::embedderDataSet));
    }

    private Object moduleInstantiate(Object[] args) {
        checkArgumentCount(args, 2);
        Object source = args[0];
        Object importObject = args[1];
        if (!(source instanceof WasmModule)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm module");
        }
        return moduleInstantiate((WasmModule) source, importObject);
    }

    public WasmModuleInstance moduleInstantiate(WasmModule module, Object importObject) {
        final TruffleContext innerTruffleContext = currentContext.environment().newContextBuilder().build();
        final Object prev = innerTruffleContext.enter(null);
        try {
            final WasmContext instanceContext = WasmContext.get(null);
            return instantiateModule(module, importObject, instanceContext, innerTruffleContext);
        } finally {
            innerTruffleContext.leave(null, prev);
        }
    }

    private static WasmModuleInstance instantiateModule(WasmModule module, Object importObject, WasmContext context, TruffleContext truffleContext) {
        final Map<String, ImportModule> importInstances;
        // To read the content of the import object, we need to enter the parent context that this
        // import object originates from.
        Object prev = truffleContext.getParent().enter(null);
        try {
            importInstances = readModuleImports(module, importObject);
        } finally {
            truffleContext.getParent().leave(null, prev);
        }
        for (Map.Entry<String, ImportModule> entry : importInstances.entrySet()) {
            final String name = entry.getKey();
            final ImportModule importModule = entry.getValue();
            final WasmModuleInstance importInstance = importModule.createInstance(context.language(), context, name, JS_LIMITS);
            context.register(importInstance);
        }
        return context.readInstance(module, JS_LIMITS);
    }

    private static Map<String, ImportModule> readModuleImports(WasmModule module, Object importObject) {
        CompilerAsserts.neverPartOfCompilation();
        if (module.getImportCount() != 0 && importObject == null) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Module requires imports, but import object is undefined.");
        }
        final Map<String, ImportModule> importedModules = new HashMap<>();
        try {
            final InteropLibrary lib = InteropLibrary.getUncached();
            final WasmFunctionImport[] functionImports = module.getFunctionImports();
            if (functionImports != null) {
                for (WasmFunctionImport i : functionImports) {
                    final Object importModule = getMember(importObject, i.getModule());
                    final Object member = getMember(importModule, i.getName());
                    if (!lib.isExecutable(member)) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not callable.");
                    }
                    ensureModuleExists(importedModules, i.getModule()).addFunction(i.getName(), Pair.create(i.getFunctionType(), member));
                }
            }

            final WasmTableImport[] tableImports = module.getTableImports();
            if (tableImports != null) {
                for (WasmTableImport i : tableImports) {
                    final Object importModule = getMember(importObject, i.getModule());
                    final Object member = getMember(importModule, i.getName());
                    if (!(member instanceof WasmTable)) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid table.");
                    }
                    ensureModuleExists(importedModules, i.getModule()).addTable(i.getName(), (WasmTable) member);
                }
            }

            final WasmMemoryImport[] memoryImports = module.getMemoryImports();
            if (memoryImports != null) {
                for (WasmMemoryImport i : memoryImports) {
                    final Object importModule = getMember(importObject, i.getModule());
                    final Object member = getMember(importModule, i.getName());
                    if (!(member instanceof WasmMemory)) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid memory.");
                    }
                    ensureModuleExists(importedModules, i.getModule()).addMemory(i.getName(), (WasmMemory) member);
                }
            }

            final WasmGlobalImport[] globalImports = module.getGlobalImports();
            if (globalImports != null) {
                for (WasmGlobalImport i : globalImports) {
                    final Object importModule = getMember(importObject, i.getModule());
                    final Object member = getMember(importModule, i.getName());
                    if (!(member instanceof WasmGlobal)) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid global.");
                    }
                    ensureModuleExists(importedModules, i.getModule()).addGlobal(i.getName(), (WasmGlobal) member);
                }
            }
        } catch (UnknownIdentifierException | ClassCastException | UnsupportedMessageException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unexpected state.");
        }
        return importedModules;
    }

    private static ImportModule ensureModuleExists(Map<String, ImportModule> importModules, String name) {
        ImportModule importedModule = importModules.get(name);
        if (importedModule == null) {
            importedModule = new ImportModule();
            importModules.put(name, importedModule);
        }
        return importedModule;
    }

    private static Object getMember(Object object, String name) throws UnknownIdentifierException, UnsupportedMessageException {
        final InteropLibrary lib = InteropLibrary.getUncached();
        if (!lib.isMemberReadable(object, name)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Object does not contain member " + name + ".");
        }
        return lib.readMember(object, name);
    }

    private Object moduleDecode(Object[] args) {
        checkArgumentCount(args, 1);
        return moduleDecode(toBytes(args[0]));
    }

    @SuppressWarnings("unused")
    public WasmModule moduleDecode(byte[] source) {
        return currentContext.readModule(source, JS_LIMITS);
    }

    private boolean moduleValidate(Object[] args) {
        checkArgumentCount(args, 1);
        return moduleValidate(toBytes(args[0]));
    }

    public boolean moduleValidate(byte[] bytes) {
        try {
            moduleDecode(bytes);
            return true;
        } catch (WasmException ex) {
            return false;
        }
    }

    private static void checkArgumentCount(Object[] args, int requiredCount) {
        if (args.length < requiredCount) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Insufficient number of arguments");
        }
    }

    private static byte[] toBytes(Object source) {
        InteropLibrary interop = InteropLibrary.getUncached(source);
        if (interop.hasArrayElements(source)) {
            try {
                long size = interop.getArraySize(source);
                if (size == (int) size) {
                    byte[] bytes = new byte[(int) size];
                    for (int i = 0; i < bytes.length; i++) {
                        Object element = interop.readArrayElement(source, i);
                        if (element instanceof Number) {
                            bytes[i] = ((Number) element).byteValue();
                        } else {
                            bytes[i] = InteropLibrary.getUncached(element).asByte(element);
                        }
                    }
                    return bytes;
                }
            } catch (InteropException iex) {
                throw cannotConvertToBytesError(iex);
            }
        }
        throw cannotConvertToBytesError(null);
    }

    private static WasmJsApiException cannotConvertToBytesError(Throwable cause) {
        WasmJsApiException.Kind kind = WasmJsApiException.Kind.TypeError;
        String message = "Cannot convert to bytes";
        return (cause == null) ? new WasmJsApiException(kind, message) : new WasmJsApiException(kind, message, cause);
    }

    private static int[] toSizeLimits(Object[] args) {
        if (args.length == 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Initial argument is required");
        }

        int initial;
        try {
            initial = InteropLibrary.getUncached().asInt(args[0]);
        } catch (UnsupportedMessageException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Initial argument must be convertible to int");
        }

        int maximum;
        if (args.length == 1) {
            maximum = -1;
        } else {
            try {
                maximum = InteropLibrary.getUncached().asInt(args[1]);
            } catch (UnsupportedMessageException ex) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Maximum argument must be convertible to int");
            }
        }

        return new int[]{initial, maximum};
    }

    private static WasmModule toModule(Object[] args) {
        checkArgumentCount(args, 1);
        if (args[0] instanceof WasmModule) {
            return (WasmModule) args[0];
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm module");
        }
    }

    private static Object moduleExports(Object[] args) {
        WasmModule module = toModule(args);
        return moduleExports(module);
    }

    public static Sequence<ModuleExportDescriptor> moduleExports(WasmModule module) {
        CompilerAsserts.neverPartOfCompilation();
        final ModuleExportDescriptor[] exports = new ModuleExportDescriptor[module.getExportCount()];
        if (exports.length == 0) {
            return new Sequence<>(exports);
        }
        String[] names = module.getExportNames();
        WasmExternalValue[] externalValues = module.getExports();
        for (int i = 0; i < module.getExportCount(); i++) {
            final String name = names[i];
            final WasmExternalValue export = externalValues[i];

            if (export.isFunction()) {
                WasmFunctionType functionType = export.getFunctionType(module);
                int functionIndex = export.getFunctionIndex();
                exports[export.getExportIndex()] = new ModuleExportDescriptor(name, ImportExportKind.function.name(), WebAssembly.functionTypeToString(functionType, functionIndex));
            } else if (export.isTable()) {
                exports[export.getExportIndex()] = new ModuleExportDescriptor(name, ImportExportKind.table.name(), null);
            } else if (export.isMemory()) {
                exports[export.getExportIndex()] = new ModuleExportDescriptor(name, ImportExportKind.memory.name(), null);
            } else if (export.isGlobal()) {
                byte globalValueType = export.getGlobalValueType(module);
                String valueType = ValueType.fromByteValue(globalValueType).toString();
                exports[export.getExportIndex()] = new ModuleExportDescriptor(name, ImportExportKind.global.name(), valueType);
            } else {
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Exported symbol list does not match the actual exports.");
            }
        }
        return new Sequence<>(exports);
    }

    private static Object moduleImports(Object[] args) {
        WasmModule module = toModule(args);
        return moduleImports(module);
    }

    public static Sequence<ModuleImportDescriptor> moduleImports(WasmModule module) {
        CompilerAsserts.neverPartOfCompilation();
        final ModuleImportDescriptor[] imports = new ModuleImportDescriptor[module.getImportCount()];
        final WasmFunctionImport[] functionImports = module.getFunctionImports();
        if (functionImports != null) {
            for (WasmFunctionImport i : functionImports) {
                imports[i.getImportIndex()] = new ModuleImportDescriptor(i.getModule(), i.getName(), ImportExportKind.function.name(),
                                WebAssembly.functionTypeToString(i.getFunctionType(), -1));
            }
        }
        final WasmTableImport[] tableImports = module.getTableImports();
        if (tableImports != null) {
            for (WasmTableImport i : tableImports) {
                imports[i.getImportIndex()] = new ModuleImportDescriptor(i.getModule(), i.getName(), ImportExportKind.table.name(), null);
            }
        }
        final WasmMemoryImport[] memoryImports = module.getMemoryImports();
        if (memoryImports != null) {
            for (WasmMemoryImport i : memoryImports) {
                imports[i.getImportIndex()] = new ModuleImportDescriptor(i.getModule(), i.getName(), ImportExportKind.memory.name(), null);
            }
        }
        final WasmGlobalImport[] globalImports = module.getGlobalImports();
        if (globalImports != null) {
            for (WasmGlobalImport i : globalImports) {
                String valueType = ValueType.fromByteValue(i.getValueType()).toString();
                imports[i.getImportIndex()] = new ModuleImportDescriptor(i.getModule(), i.getName(), ImportExportKind.global.name(), valueType);
            }
        }
        return new Sequence<>(imports);
    }

    private static Object customSections(Object[] args) {
        checkArgumentCount(args, 2);
        WasmModule module = toModule(args);
        return customSections(module, args[1]);
    }

    public static Sequence<ByteArrayBuffer> customSections(WasmModule module, Object sectionName) {
        final List<WasmCustomSection> customSections = module.getCustomSections(sectionName);
        final ByteArrayBuffer[] sections = new ByteArrayBuffer[customSections.size()];
        for (int i = 0; i < sections.length; i++) {
            WasmCustomSection section = customSections.get(i);
            sections[i] = new ByteArrayBuffer(section.getData(), section.getOffset(), section.getLength());
        }
        return new Sequence<>(sections);
    }

    private static Object tableAlloc(Object[] args) {
        final int[] limits = toSizeLimits(args);
        return tableAlloc(limits[0], limits[1]);
    }

    public static WasmTable tableAlloc(int initial, int maximum) {
        if (Integer.compareUnsigned(initial, maximum) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min table size exceeds max memory size");
        }
        if (Integer.compareUnsigned(initial, JS_LIMITS.tableInstanceSizeLimit()) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min table size exceeds implementation limit");
        }
        final int maxAllowedSize = WasmMath.minUnsigned(maximum, JS_LIMITS.tableInstanceSizeLimit());
        return new WasmTable(initial, maximum, maxAllowedSize);
    }

    private static Object tableGrow(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmTable)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        WasmTable table = (WasmTable) args[0];
        int delta = (Integer) args[1];
        return tableGrow(table, delta);
    }

    public static int tableGrow(WasmTable table, int delta) {
        final int size = table.size();
        try {
            table.grow(delta);
        } catch (IllegalArgumentException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, e.getMessage());
        }
        return size;
    }

    private static Object tableRead(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmTable)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        WasmTable table = (WasmTable) args[0];
        int index = (Integer) args[1];
        return tableRead(table, index);
    }

    public static Object tableRead(WasmTable table, int index) {
        try {
            final Object result = table.get(index);
            return result == null ? WasmVoidResult.getInstance() : result;
        } catch (IndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }
    }

    private static Object tableWrite(Object[] args) {
        checkArgumentCount(args, 3);
        if (!(args[0] instanceof WasmTable)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        WasmTable table = (WasmTable) args[0];
        int index = (Integer) args[1];
        return tableWrite(table, index, args[2]);
    }

    public static Object tableWrite(WasmTable table, int index, Object element) {
        final WasmFunctionInstance functionInstance;
        if (element instanceof WasmFunctionInstance) {
            functionInstance = (WasmFunctionInstance) element;
        } else if (InteropLibrary.getUncached(element).isNull(element)) {
            functionInstance = null;
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid table element");
        }

        try {
            table.set(index, functionInstance);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }

        return WasmVoidResult.getInstance();
    }

    private static Object tableSize(Object[] args) {
        checkArgumentCount(args, 1);
        if (!(args[0] instanceof WasmTable)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        WasmTable table = (WasmTable) args[0];
        return tableSize(table);
    }

    public static int tableSize(WasmTable table) {
        return table.size();
    }

    private static Object funcType(Object[] args) {
        checkArgumentCount(args, 1);
        if (args[0] instanceof WasmFunctionInstance) {
            WasmFunctionInstance fn = (WasmFunctionInstance) args[0];
            return functionTypeToString(fn.getFunctionType(), fn.getFunctionIndex());
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm function");
        }
    }

    public static String functionTypeToString(WasmFunctionType f, int functionIndex) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder typeInfo = new StringBuilder();

        typeInfo.append(functionIndex);

        typeInfo.append('(');
        byte[] parameters = f.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i != 0) {
                typeInfo.append(' ');
            }
            typeInfo.append(ValueType.fromByteValue(parameters[i]));
        }
        typeInfo.append(')');

        byte returnType = f.getReturnType();
        if (returnType != WasmType.VOID_TYPE) {
            typeInfo.append(ValueType.fromByteValue(f.getReturnType()));
        }
        return typeInfo.toString();
    }

    private static Object memAlloc(Object[] args) {
        final int[] limits = toSizeLimits(args);
        return memAlloc(limits[0], limits[1]);
    }

    public static WasmMemory memAlloc(int initial, int maximum) {
        if (compareUnsigned(initial, maximum) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min memory size exceeds max memory size");
        } else if (compareUnsigned(initial, JS_LIMITS.memoryInstanceSizeLimit()) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min memory size exceeds implementation limit");
        }
        final int maxAllowedSize = minUnsigned(maximum, JS_LIMITS.memoryInstanceSizeLimit());
        if (WasmContext.get(null).environment().getOptions().get(WasmOptions.UseUnsafeMemory)) {
            return new UnsafeWasmMemory(initial, maximum, maxAllowedSize);
        } else {
            return new ByteArrayWasmMemory(initial, maximum, maxAllowedSize);
        }
    }

    private static Object memGrow(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmMemory)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm memory");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        WasmMemory memory = (WasmMemory) args[0];
        int delta = (Integer) args[1];
        return memGrow(memory, delta);
    }

    public static long memGrow(WasmMemory memory, int delta) {
        final long pageSize = memory.size();
        if (!memory.grow(delta)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Cannot grow memory above max limit");
        }
        return pageSize;
    }

    private static Object memSetGrowCallback(Object[] args) {
        InteropLibrary lib = InteropLibrary.getUncached();
        if (!(args[0] instanceof WasmMemory)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm memory");
        }
        if (!lib.isExecutable(args[1])) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be executable");
        }
        WasmMemory memory = (WasmMemory) args[0];
        return memSetGrowCallback(memory, args[1]);
    }

    private static Object memSetGrowCallback(WasmMemory memory, Object callback) {
        memory.setGrowCallback(callback);
        return WasmVoidResult.getInstance();
    }

    public static void invokeMemGrowCallback(WasmMemory memory) {
        Object callback = memory.getGrowCallback();
        if (callback != null) {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                lib.execute(callback, memory);
            } catch (InteropException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Unable to call memory grow callback", e);
            }
        }
    }

    private static Object memAsByteBuffer(Object[] args) {
        checkArgumentCount(args, 1);
        if (args[0] instanceof WasmMemory) {
            WasmMemory memory = (WasmMemory) args[0];
            ByteBuffer buffer = memory.asByteBuffer();
            if (buffer != null) {
                return WasmContext.get(null).environment().asGuestValue(buffer);
            }
        }
        return WasmVoidResult.getInstance();
    }

    private static Object globalAlloc(Object[] args) {
        checkArgumentCount(args, 3);

        ValueType valueType;
        try {
            String valueTypeString = InteropLibrary.getUncached().asString(args[0]);
            valueType = ValueType.valueOf(valueTypeString);
        } catch (UnsupportedMessageException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument (value type) must be convertible to String");
        } catch (IllegalArgumentException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
        }

        boolean mutable;
        try {
            mutable = InteropLibrary.getUncached().asBoolean(args[1]);
        } catch (UnsupportedMessageException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument (mutable) must be convertible to boolean");
        }

        return globalAlloc(valueType, mutable, args[2]);
    }

    public static WasmGlobal globalAlloc(ValueType valueType, boolean mutable, Object value) {
        InteropLibrary valueInterop = InteropLibrary.getUncached(value);
        try {
            switch (valueType) {
                case i32:
                    return new WasmGlobal(valueType, mutable, valueInterop.asInt(value));
                case i64:
                    return new WasmGlobal(valueType, mutable, valueInterop.asLong(value));
                case f32:
                    return new WasmGlobal(valueType, mutable, Float.floatToRawIntBits(valueInterop.asFloat(value)));
                case f64:
                    return new WasmGlobal(valueType, mutable, Double.doubleToRawLongBits(valueInterop.asDouble(value)));
                default:
                    throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
            }
        } catch (UnsupportedMessageException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Cannot convert value to the specified value type");
        }
    }

    private static Object globalRead(Object[] args) {
        checkArgumentCount(args, 1);
        if (!(args[0] instanceof WasmGlobal)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm global");
        }
        WasmGlobal global = (WasmGlobal) args[0];
        return globalRead(global);
    }

    public static Object globalRead(WasmGlobal global) {
        switch (ValueType.fromByteValue(global.getValueType())) {
            case i32:
                return global.loadAsInt();
            case i64:
                return global.loadAsLong();
            case f32:
                return Float.intBitsToFloat(global.loadAsInt());
            case f64:
                return Double.longBitsToDouble(global.loadAsLong());
        }
        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Incorrect internal Global type");
    }

    private static Object globalWrite(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmGlobal)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm global");
        }
        WasmGlobal global = (WasmGlobal) args[0];
        return globalWrite(global, args[1]);
    }

    public static Object globalWrite(WasmGlobal global, Object value) {
        if (!global.isMutable()) {
            throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global is not mutable.");
        }
        ValueType valueType = ValueType.fromByteValue(global.getValueType());
        switch (valueType) {
            case i32:
                if (!(value instanceof Integer)) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global type %s, value: %s", valueType, value);
                }
                global.storeInt((int) value);
                break;
            case i64:
                if (!(value instanceof Long)) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global type %s, value: %s", valueType, value);
                }
                global.storeLong((long) value);
                break;
            case f32:
                if (!(value instanceof Float)) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global type %s, value: %s", valueType, value);
                }
                global.storeInt(Float.floatToRawIntBits((float) value));
                break;
            case f64:
                if (!(value instanceof Double)) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global type %s, value: %s", valueType, value);
                }
                global.storeLong(Double.doubleToRawLongBits((double) value));
                break;
        }
        return WasmVoidResult.getInstance();
    }

    private static Object instanceExport(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmModuleInstance)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm instance");
        }
        if (!(args[1] instanceof String)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be string");
        }
        WasmModuleInstance instance = (WasmModuleInstance) args[0];
        String name = (String) args[1];
        return instanceExport(instance, name);
    }

    public static Object instanceExport(WasmModuleInstance instance, String name) {
        CompilerAsserts.neverPartOfCompilation();
        WasmExternalValue export = instance.getExport(name);
        if (export == null) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, name + " is not a exported name of the given instance");
        }
        if (export.isFunction() && export.functionExists(instance)) {
            return export.getFunction(instance);
        } else if (export.isTable() && export.tableExists(instance)) {
            return export.getTable(instance);
        } else if (export.isMemory() && export.memoryExists(instance)) {
            return export.getMemory(instance);
        } else if (export.isGlobal() && export.globalExists(instance)) {
            return export.getGlobal(instance);
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, name + " is not a exported name of the given instance");
        }
    }

    public static Object embedderDataSet(Object[] args) {
        checkArgumentCount(args, 2);
        getEmbedderDataHolder(args).setEmbedderData(args[1]);
        return WasmVoidResult.getInstance();
    }

    public static Object embedderDataGet(Object[] args) {
        checkArgumentCount(args, 1);
        return getEmbedderDataHolder(args).getEmbedderData();
    }

    private static EmbedderDataHolder getEmbedderDataHolder(Object[] args) {
        if (!(args[0] instanceof EmbedderDataHolder)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument is an object that cannot hold embedder data");
        }
        return ((EmbedderDataHolder) args[0]);
    }

}
