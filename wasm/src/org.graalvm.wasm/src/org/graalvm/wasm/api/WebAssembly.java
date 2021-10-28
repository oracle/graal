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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.wasm.EmbedderDataHolder;
import org.graalvm.wasm.ImportDescriptor;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmCustomSection;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmMath;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.globals.DefaultWasmGlobal;
import org.graalvm.wasm.globals.ExportedWasmGlobal;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

public class WebAssembly extends Dictionary {
    private final WasmContext currentContext;

    public WebAssembly(WasmContext currentContext) {
        this.currentContext = currentContext;
        addMember("module_decode", new Executable(args -> moduleDecode(args)));
        addMember("module_instantiate", new Executable(args -> moduleInstantiate(args)));
        addMember("module_validate", new Executable(args -> moduleValidate(args)));

        addMember("table_alloc", new Executable(args -> tableAlloc(args)));
        addMember("table_grow", new Executable(args -> tableGrow(args)));
        addMember("table_read", new Executable(args -> tableRead(args)));
        addMember("table_write", new Executable(args -> tableWrite(args)));
        addMember("table_size", new Executable(args -> tableSize(args)));

        addMember("func_type", new Executable(args -> funcType(args)));

        addMember("mem_alloc", new Executable(args -> memAlloc(args)));
        addMember("mem_grow", new Executable(args -> memGrow(args)));
        addMember("mem_set_grow_callback", new Executable(args -> memSetGrowCallback(args)));
        addMember("mem_as_byte_buffer", new Executable(args -> memAsByteBuffer(args)));

        addMember("global_alloc", new Executable(args -> globalAlloc(args)));
        addMember("global_read", new Executable(args -> globalRead(args)));
        addMember("global_write", new Executable(args -> globalWrite(args)));

        addMember("module_imports", new Executable(args -> moduleImports(args)));
        addMember("module_exports", new Executable(args -> moduleExports(args)));

        addMember("custom_sections", new Executable(args -> customSections(args)));

        addMember("instance_export", new Executable(args -> instanceExport(args)));

        addMember("embedder_data_get", new Executable(args -> embedderDataGet(args)));
        addMember("embedder_data_set", new Executable(args -> embedderDataSet(args)));
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

    public WasmInstance moduleInstantiate(WasmModule module, Object importObject) {
        final TruffleContext innerTruffleContext = currentContext.environment().newContextBuilder().build();
        final Object prev = innerTruffleContext.enter(null);
        try {
            final WasmContext instanceContext = WasmContext.get(null);
            WasmInstance instance = instantiateModule(module, importObject, instanceContext, innerTruffleContext);
            instanceContext.linker().tryLink(instance);
            return instance;
        } finally {
            innerTruffleContext.leave(null, prev);
        }
    }

    private static WasmInstance instantiateModule(WasmModule module, Object importObject, WasmContext context, TruffleContext truffleContext) {
        final HashMap<String, ImportModule> importModules;
        // To read the content of the import object, we need to enter the parent context that this
        // import object originates from.
        Object prev = truffleContext.getParent().enter(null);
        try {
            importModules = readModuleImports(module, importObject);
        } finally {
            truffleContext.getParent().leave(null, prev);
        }
        for (Map.Entry<String, ImportModule> entry : importModules.entrySet()) {
            final String name = entry.getKey();
            final ImportModule importModule = entry.getValue();
            final WasmInstance importedInstance = importModule.createInstance(context.language(), context, name);
            context.register(importedInstance);
        }
        return context.readInstance(module);
    }

    private static HashMap<String, ImportModule> readModuleImports(WasmModule module, Object importObject) {
        CompilerAsserts.neverPartOfCompilation();
        final Sequence<ModuleImportDescriptor> imports = moduleImports(module);
        if (imports.getArraySize() != 0 && importObject == null) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Module requires imports, but import object is undefined.");
        }

        HashMap<String, ImportModule> importModules = new HashMap<>();
        final InteropLibrary lib = InteropLibrary.getUncached();
        try {
            int i = 0;
            while (i < imports.getArraySize()) {
                final ModuleImportDescriptor d = (ModuleImportDescriptor) imports.readArrayElement(i);
                final Object importedModule = getMember(importObject, d.module());
                final Object member = getMember(importedModule, d.name());
                switch (d.kind()) {
                    case function:
                        if (!lib.isExecutable(member)) {
                            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not callable.");
                        }
                        WasmFunction f = module.importedFunction(d.name());
                        ensureImportModule(importModules, d.module()).addFunction(d.name(), Pair.create(f, member));
                        break;
                    case memory:
                        if (!(member instanceof WasmMemory)) {
                            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid memory.");
                        }
                        ensureImportModule(importModules, d.module()).addMemory(d.name(), (WasmMemory) member);
                        break;
                    case table:
                        if (!(member instanceof WasmTable)) {
                            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid table.");
                        }
                        ensureImportModule(importModules, d.module()).addTable(d.name(), (WasmTable) member);
                        break;
                    case global:
                        if (!(member instanceof WasmGlobal)) {
                            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Member " + member + " is not a valid global.");
                        }
                        ensureImportModule(importModules, d.module()).addGlobal(d.name(), (WasmGlobal) member);
                        break;
                    default:
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unimplemented case: " + d.kind());
                }

                i += 1;
            }
        } catch (InvalidArrayIndexException | UnknownIdentifierException | ClassCastException | UnsupportedMessageException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unexpected state.");
        }

        return importModules;
    }

    private static ImportModule ensureImportModule(HashMap<String, ImportModule> importModules, String name) {
        ImportModule importModule = importModules.get(name);
        if (importModule == null) {
            importModule = new ImportModule();
            importModules.put(name, importModule);
        }
        return importModule;
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
        final ArrayList<ModuleExportDescriptor> list = new ArrayList<>();
        for (final String name : module.exportedSymbols()) {
            final WasmFunction f = module.exportedFunctions().get(name);
            final Integer globalIndex = module.exportedGlobals().get(name);

            if (module.exportedMemoryNames().contains(name)) {
                list.add(new ModuleExportDescriptor(name, ImportExportKind.memory.name(), null));
            } else if (module.exportedTableNames().contains(name)) {
                list.add(new ModuleExportDescriptor(name, ImportExportKind.table.name(), null));
            } else if (f != null) {
                list.add(new ModuleExportDescriptor(name, ImportExportKind.function.name(), WebAssembly.functionTypeToString(f)));
            } else if (globalIndex != null) {
                String valueType = ValueType.fromByteValue(module.globalValueType(globalIndex)).toString();
                list.add(new ModuleExportDescriptor(name, ImportExportKind.global.name(), valueType));
            } else {
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Exported symbol list does not match the actual exports.");
            }
        }
        return new Sequence<>(list);
    }

    private static Object moduleImports(Object[] args) {
        WasmModule module = toModule(args);
        return moduleImports(module);
    }

    public static Sequence<ModuleImportDescriptor> moduleImports(WasmModule module) {
        CompilerAsserts.neverPartOfCompilation();
        final EconomicMap<ImportDescriptor, Integer> importedGlobalDescriptors = module.importedGlobalDescriptors();
        final ArrayList<ModuleImportDescriptor> list = new ArrayList<>();
        for (ImportDescriptor descriptor : module.importedSymbols()) {
            switch (descriptor.identifier) {
                case ImportIdentifier.FUNCTION:
                    final WasmFunction f = module.importedFunction(descriptor);
                    list.add(new ModuleImportDescriptor(f.importedModuleName(), f.importedFunctionName(), ImportExportKind.function.name(), WebAssembly.functionTypeToString(f)));
                    break;
                case ImportIdentifier.TABLE:
                    if (Objects.equals(module.importedTable(), descriptor)) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, ImportExportKind.table.name(), null));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Table import inconsistent.");
                    }
                    break;
                case ImportIdentifier.MEMORY:
                    if (Objects.equals(module.importedMemory(), descriptor)) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, ImportExportKind.memory.name(), null));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Memory import inconsistent.");
                    }
                    break;
                case ImportIdentifier.GLOBAL:
                    final Integer index = importedGlobalDescriptors.get(descriptor);
                    String valueType = ValueType.fromByteValue(module.globalValueType(index)).toString();
                    list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, ImportExportKind.global.name(), valueType));
                    break;
                default:
                    throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unknown import descriptor type: " + descriptor.identifier);
            }
        }
        return new Sequence<>(list);
    }

    private static Object customSections(Object[] args) {
        checkArgumentCount(args, 2);
        WasmModule module = toModule(args);
        return customSections(module, args[1]);
    }

    public static Sequence<ByteArrayBuffer> customSections(WasmModule module, Object sectionName) {
        List<ByteArrayBuffer> sections = new ArrayList<>();
        for (WasmCustomSection section : module.customSections()) {
            if (section.getName().equals(sectionName)) {
                sections.add(new ByteArrayBuffer(module.data(), section.getOffset(), section.getLength()));
            }
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
            WasmFunction fn = ((WasmFunctionInstance) args[0]).function();
            return functionTypeToString(fn);
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm function");
        }
    }

    public static String functionTypeToString(WasmFunction f) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder typeInfo = new StringBuilder();

        typeInfo.append(f.index());

        typeInfo.append('(');
        int argumentCount = f.numArguments();
        for (int i = 0; i < argumentCount; i++) {
            if (i != 0) {
                typeInfo.append(' ');
            }
            typeInfo.append(ValueType.fromByteValue(f.argumentTypeAt(i)));
        }
        typeInfo.append(')');

        byte returnType = f.returnType();
        if (returnType != WasmType.VOID_TYPE) {
            typeInfo.append(ValueType.fromByteValue(f.returnType()));
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
                    return new DefaultWasmGlobal(valueType, mutable, valueInterop.asInt(value));
                case i64:
                    return new DefaultWasmGlobal(valueType, mutable, valueInterop.asLong(value));
                case f32:
                    return new DefaultWasmGlobal(valueType, mutable, Float.floatToRawIntBits(valueInterop.asFloat(value)));
                case f64:
                    return new DefaultWasmGlobal(valueType, mutable, Double.doubleToRawLongBits(valueInterop.asDouble(value)));
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
        switch (global.getValueType()) {
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
        ValueType valueType = global.getValueType();
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
        if (!(args[0] instanceof WasmInstance)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm instance");
        }
        if (!(args[1] instanceof String)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be string");
        }
        WasmInstance instance = (WasmInstance) args[0];
        String name = (String) args[1];
        return instanceExport(instance, name);
    }

    public static Object instanceExport(WasmInstance instance, String name) {
        CompilerAsserts.neverPartOfCompilation();
        WasmFunction function = instance.module().exportedFunctions().get(name);
        Integer globalIndex = instance.module().exportedGlobals().get(name);

        if (function != null) {
            return instance.functionInstance(function);
        } else if (globalIndex != null) {
            final int index = globalIndex;
            final int address = instance.globalAddress(index);
            if (address < 0) {
                return instance.context().globals().externalGlobal(address);
            } else {
                final ValueType valueType = ValueType.fromByteValue(instance.symbolTable().globalValueType(index));
                final boolean mutable = instance.symbolTable().isGlobalMutable(index);
                return new ExportedWasmGlobal(valueType, mutable, instance.context().globals(), address);
            }
        } else if (instance.module().exportedMemoryNames().contains(name)) {
            return instance.memory();
        } else if (instance.module().exportedTableNames().contains(name)) {
            return instance.table();
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
