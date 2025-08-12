/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.EmbedderDataHolder;
import org.graalvm.wasm.ImportDescriptor;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmCustomSection;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmStore;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

public class WebAssembly extends Dictionary {
    private final WasmContext currentContext;
    private final boolean refTypes;

    @SuppressWarnings("this-escape")
    public WebAssembly(WasmContext currentContext) {
        this.currentContext = currentContext;
        this.refTypes = currentContext.getContextOptions().supportBulkMemoryAndRefTypes();
        addMember("module_decode", new Executable(this::moduleDecode));
        addMember("module_instantiate", new Executable(this::moduleInstantiate));
        addMember("module_validate", new Executable(this::moduleValidate));

        addMember("table_alloc", new Executable(this::tableAlloc));
        addMember("table_grow", new Executable(WebAssembly::tableGrow));
        addMember("table_read", new Executable(WebAssembly::tableRead));
        addMember("table_write", new Executable(this::tableWrite));
        addMember("table_size", new Executable(WebAssembly::tableSize));

        addMember("func_type", new Executable(WebAssembly::funcType));
        addMember("is_func", new Executable(WebAssembly::isFunc));

        addMember("mem_alloc", new Executable(WebAssembly::memAlloc));
        addMember("mem_grow", new Executable(WebAssembly::memGrow));
        addMember("mem_set_grow_callback", new Executable(WebAssembly::memSetGrowCallback));
        addMember("mem_as_byte_buffer", new Executable(WebAssembly::memAsByteBuffer));
        addMember("mem_set_notify_callback", new Executable(WebAssembly::memSetNotifyCallback));
        addMember("mem_set_wait_callback", new Executable(WebAssembly::memSetWaitCallback));

        addMember("global_alloc", new Executable(this::globalAlloc));
        addMember("global_read", new Executable(WebAssembly::globalRead));
        addMember("global_write", new Executable(this::globalWrite));

        addMember("module_imports", new Executable(WebAssembly::moduleImports));
        addMember("module_exports", new Executable(WebAssembly::moduleExports));

        addMember("custom_sections", new Executable(WebAssembly::customSections));

        addMember("instance_export", new Executable(WebAssembly::instanceExport));

        addMember("embedder_data_get", new Executable(WebAssembly::embedderDataGet));
        addMember("embedder_data_set", new Executable(WebAssembly::embedderDataSet));

        addMember("ref_null", WasmConstant.NULL);
    }

    public WasmInstance moduleInstantiate(Object[] args) {
        checkArgumentCount(args, 2);
        WasmModule source = toModule(args);
        Object importObject = args[1];
        return moduleInstantiate(source, importObject);
    }

    public WasmInstance moduleInstantiate(WasmModule module, Object importObject) {
        CompilerAsserts.neverPartOfCompilation();
        final WasmStore store = new WasmStore(currentContext, currentContext.language());
        return module.createInstance(store, importObject, WasmJsApiException.provider(), true);
    }

    private static String makeModuleName(byte[] data) {
        return "js:module-" + Integer.toHexString(Arrays.hashCode(data));
    }

    private WasmModuleWithSource moduleDecodeImpl(byte[] data) {
        String moduleName = makeModuleName(data);
        Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), moduleName).mimeType(WasmLanguage.WASM_MIME_TYPE).build();
        CallTarget parseResult = currentContext.environment().parsePublic(source);
        WasmModule module = WasmLanguage.getParsedModule(parseResult);
        assert module.limits().equals(JsConstants.JS_LIMITS);
        return new WasmModuleWithSource(module, source);
    }

    public Object moduleDecode(Object[] args) {
        checkArgumentCount(args, 1);
        return moduleDecodeImpl(toBytes(args[0]));
    }

    public WasmModule moduleDecode(byte[] source) {
        return moduleDecodeImpl(source).module();
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
        try {
            if (interop.hasBufferElements(source)) {
                long size = interop.getBufferSize(source);
                if (size == (int) size) {
                    byte[] bytes = new byte[(int) size];
                    interop.readBuffer(source, 0, bytes, 0, (int) size);
                    return bytes;
                }
            } else if (interop.hasArrayElements(source)) {
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
            }
        } catch (InteropException iex) {
            throw cannotConvertToBytesError(iex);
        }
        throw cannotConvertToBytesError(null);
    }

    private static WasmJsApiException cannotConvertToBytesError(Throwable cause) {
        WasmJsApiException.Kind kind = WasmJsApiException.Kind.TypeError;
        String message = "Cannot convert to bytes";
        return (cause == null) ? new WasmJsApiException(kind, message) : new WasmJsApiException(kind, message, cause);
    }

    /**
     * Extract a {@link WasmModule} from argument 0. The argument may be a {@link WasmModule} or a
     * {@link WasmModuleWithSource} as produced by the CallTarget returned from Env.parse or
     * {@link #moduleDecode}, respectively.
     */
    private static WasmModule toModule(Object[] args) {
        checkArgumentCount(args, 1);
        Object arg0 = args[0];
        if (arg0 instanceof WasmModule moduleObject) {
            return moduleObject;
        } else if (arg0 instanceof WasmModuleWithSource moduleObject) {
            return moduleObject.module();
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
            final Integer tableIndex = module.exportedTables().get(name);
            final Integer memoryIndex = module.exportedMemories().get(name);

            if (memoryIndex != null) {
                String shared = module.memoryIsShared(memoryIndex) ? "shared" : "single";
                list.add(new ModuleExportDescriptor(name, ImportExportKind.memory.name(), shared));
            } else if (tableIndex != null) {
                list.add(new ModuleExportDescriptor(name, ImportExportKind.table.name(), TableKind.toString(module.tableElementType(tableIndex))));
            } else if (f != null) {
                list.add(new ModuleExportDescriptor(name, ImportExportKind.function.name(), WebAssembly.functionTypeToString(f)));
            } else if (globalIndex != null) {
                String valueType = ValueType.fromByteValue(module.globalValueType(globalIndex)).toString();
                String mutability = module.isGlobalMutable(globalIndex) ? "mut" : "con";
                list.add(new ModuleExportDescriptor(name, ImportExportKind.global.name(), valueType + " " + mutability));
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
        return new Sequence<>(moduleImportsAsList(module));
    }

    public static List<ModuleImportDescriptor> moduleImportsAsList(WasmModule module) {
        CompilerAsserts.neverPartOfCompilation();
        final EconomicMap<ImportDescriptor, Integer> importedGlobalDescriptors = module.importedGlobalDescriptors();
        final EconomicMap<ImportDescriptor, Integer> importedTableDescriptors = module.importedTableDescriptors();
        final EconomicMap<ImportDescriptor, Integer> importedMemoryDescriptors = module.importedMemoryDescriptors();
        final ArrayList<ModuleImportDescriptor> list = new ArrayList<>();
        for (ImportDescriptor descriptor : module.importedSymbols()) {
            switch (descriptor.identifier()) {
                case ImportIdentifier.FUNCTION:
                    final WasmFunction f = module.importedFunction(descriptor);
                    list.add(new ModuleImportDescriptor(f.importedModuleName(), f.importedFunctionName(), ImportExportKind.function.name(), WebAssembly.functionTypeToString(f)));
                    break;
                case ImportIdentifier.TABLE:
                    final Integer tableIndex = importedTableDescriptors.get(descriptor);
                    if (tableIndex != null) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName(), descriptor.memberName(), ImportExportKind.table.name(), TableKind.toString(module.tableElementType(tableIndex))));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Table import inconsistent.");
                    }
                    break;
                case ImportIdentifier.MEMORY:
                    final Integer memoryIndex = importedMemoryDescriptors.get(descriptor);
                    if (memoryIndex != null) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName(), descriptor.memberName(), ImportExportKind.memory.name(), null));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Memory import inconsistent.");
                    }
                    break;
                case ImportIdentifier.GLOBAL:
                    final Integer index = importedGlobalDescriptors.get(descriptor);
                    String valueType = ValueType.fromByteValue(module.globalValueType(index)).toString();
                    list.add(new ModuleImportDescriptor(descriptor.moduleName(), descriptor.memberName(), ImportExportKind.global.name(), valueType));
                    break;
                default:
                    throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unknown import descriptor type: " + descriptor.identifier());
            }
        }
        return List.copyOf(list);
    }

    private static Object customSections(Object[] args) {
        checkArgumentCount(args, 2);
        WasmModule module = toModule(args);
        return customSections(module, args[1]);
    }

    public static Sequence<ByteArrayBuffer> customSections(WasmModule module, Object sectionName) {
        final String name;
        try {
            name = InteropLibrary.getUncached().asString(sectionName);
        } catch (UnsupportedMessageException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Section name must be a string");
        }
        List<ByteArrayBuffer> sections = new ArrayList<>();
        for (WasmCustomSection section : module.customSections()) {
            if (section.name().equals(name)) {
                sections.add(new ByteArrayBuffer(module.customData(), section.offset(), section.length()));
            }
        }
        return new Sequence<>(sections);
    }

    private Object tableAlloc(Object[] args) {
        if (refTypes) {
            checkArgumentCount(args, 2);
        } else {
            checkArgumentCount(args, 1);
        }

        final int initialSize;
        int maximumSize = -1;
        TableKind elementKind = TableKind.anyfunc;
        Object initialValue = WasmConstant.NULL;
        InteropLibrary lib = InteropLibrary.getUncached();

        try {
            initialSize = lib.asInt(args[0]);
        } catch (UnsupportedMessageException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Initial size must be convertible to int");
        }
        int state;
        if (args.length == 1) {
            // Only the initial size is provided
            state = -1;
        } else if (lib.fitsInInt(args[1])) {
            // The second parameter represents the maximum size
            state = 0;
        } else if (args.length >= 3) {
            // The second parameter represents the element kind
            state = 1;
        } else {
            // The second parameter represents the initial value
            state = 2;
        }
        Object value;
        for (int i = 1; state != -1; i++) {
            value = args[i];
            switch (state) {
                case 0: {
                    try {
                        maximumSize = lib.asInt(value);
                    } catch (UnsupportedMessageException e) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Maximum size must be convertible to int");
                    }
                    if (args.length == 2) {
                        // Only the initial and maximum size are provided
                        state = -1;
                    } else if (args.length >= 4) {
                        // The third parameter represents the element kind
                        state = 1;
                    } else {
                        // The third parameter represents the initial value
                        state = 2;
                    }
                    break;
                }
                case 1: {
                    try {
                        elementKind = TableKind.valueOf(lib.asString(value));
                    } catch (UnsupportedMessageException e) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Element kind must be one of externref or anyfunc");
                    }
                    // An initial value is expected
                    state = 2;
                    break;
                }
                case 2: {
                    initialValue = value;
                    // All values were read
                    state = -1;
                    break;
                }
            }
        }
        return tableAlloc(initialSize, maximumSize, elementKind, initialValue);
    }

    public WasmTable tableAlloc(int initial, int maximum, TableKind elemKind, Object initialValue) {
        if (Integer.compareUnsigned(initial, maximum) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min table size exceeds max memory size");
        }
        if (Integer.compareUnsigned(initial, JS_LIMITS.tableInstanceSizeLimit()) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min table size exceeds implementation limit");
        }
        if (elemKind != TableKind.externref && elemKind != TableKind.anyfunc) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Element type must be a reftype");
        }
        if (!refTypes && elemKind == TableKind.externref) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Element type must be anyfunc. Enable reference types to support externref");
        }
        final int maxAllowedSize = minUnsigned(maximum, JS_LIMITS.tableInstanceSizeLimit());
        return new WasmTable(initial, maximum, maxAllowedSize, elemKind.byteValue(), initialValue);
    }

    private static Object tableGrow(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmTable table)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        int delta = (Integer) args[1];
        if (args.length > 2) {
            return tableGrow(table, delta, args[2]);
        }
        return tableGrow(table, delta, WasmConstant.NULL);
    }

    public static int tableGrow(WasmTable table, int delta, Object ref) {
        final int result = table.grow(delta, ref);
        if (result == -1) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Cannot grow table above max limit");
        }
        return result;
    }

    private static Object tableRead(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmTable table)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        int index = (Integer) args[1];
        return tableRead(table, index);
    }

    public static Object tableRead(WasmTable table, int index) {
        try {
            return table.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }
    }

    private Object tableWrite(Object[] args) {
        checkArgumentCount(args, 3);
        if (!(args[0] instanceof WasmTable table)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        int index = (Integer) args[1];
        return tableWrite(table, index, args[2]);
    }

    public Object tableWrite(WasmTable table, int index, Object element) {
        final Object elem;
        if (element instanceof WasmFunctionInstance) {
            elem = element;
        } else if (element == WasmConstant.NULL) {
            elem = WasmConstant.NULL;
        } else {
            if (!currentContext.getContextOptions().supportBulkMemoryAndRefTypes()) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid table element");
            }
            if (table.elemType() == WasmType.FUNCREF_TYPE) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid table element");
            }
            elem = element;
        }

        try {
            table.set(index, elem);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }

        return WasmConstant.VOID;
    }

    private static Object tableSize(Object[] args) {
        checkArgumentCount(args, 1);
        if (!(args[0] instanceof WasmTable table)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm table");
        }
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

    private static Object isFunc(Object[] args) {
        checkArgumentCount(args, 1);
        return args[0] instanceof WasmFunctionInstance;
    }

    public static String functionTypeToString(WasmFunction f) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder typeInfo = new StringBuilder();

        typeInfo.append(f.index());

        typeInfo.append('(');
        int paramCount = f.paramCount();
        for (int i = 0; i < paramCount; i++) {
            if (i != 0) {
                typeInfo.append(' ');
            }
            typeInfo.append(ValueType.fromByteValue(f.paramTypeAt(i)));
        }
        typeInfo.append(')');

        int resultCount = f.resultCount();
        for (int i = 0; i < resultCount; i++) {
            if (i != 0) {
                typeInfo.append(' ');
            }
            typeInfo.append(ValueType.fromByteValue(f.resultTypeAt(i)));
        }
        return typeInfo.toString();
    }

    private static Object memAlloc(Object[] args) {
        checkArgumentCount(args, 1);
        InteropLibrary lib = InteropLibrary.getUncached();
        final int initialSize;
        try {
            initialSize = lib.asInt(args[0]);
        } catch (UnsupportedMessageException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Initial size must be convertible to int");
        }
        final int maximumSize;
        if (args.length > 1) {
            try {
                maximumSize = lib.asInt(args[1]);
            } catch (UnsupportedMessageException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Maximum size must be convertible to int");
            }
        } else {
            maximumSize = -1;
        }
        final boolean shared;
        if (args.length > 2) {
            try {
                shared = lib.asBoolean(args[2]);
            } catch (UnsupportedMessageException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Shared flag must be convertible to boolean");
            }
        } else {
            shared = false;
        }
        return memAlloc(initialSize, maximumSize, shared);
    }

    public static WasmMemory memAlloc(int initial, int maximum, boolean shared) {
        final WasmContext context = WasmContext.get(null);
        boolean useUnsafeMemory = context.getContextOptions().useUnsafeMemory();
        boolean directByteBufferMemoryAccess = context.getContextOptions().directByteBufferMemoryAccess();
        if (compareUnsigned(initial, maximum) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min memory size exceeds max memory size");
        } else if (Long.compareUnsigned(initial, WasmMemoryFactory.getMaximumAllowedSize(shared, useUnsafeMemory, directByteBufferMemoryAccess)) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Min memory size exceeds implementation limit");
        }
        return WasmMemoryFactory.createMemory(initial, maximum, false, shared, useUnsafeMemory, directByteBufferMemoryAccess, context);
    }

    private static Object memGrow(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmMemory memory)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm memory");
        }
        if (!(args[1] instanceof Integer)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be integer");
        }
        int delta = (Integer) args[1];
        return memGrow(memory, delta);
    }

    public static long memGrow(WasmMemory memory, int delta) {
        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        final long previousSize = memoryLib.grow(memory, delta);
        if (previousSize == -1) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError,
                            Math.addExact(memoryLib.size(memory), delta) <= memory.declaredMaxSize() ? "Cannot grow memory above implementation limit" : "Cannot grow memory above max limit");
        }
        return previousSize;
    }

    private static Object memSetGrowCallback(Object[] args) {
        checkArgumentCount(args, 1);
        InteropLibrary lib = InteropLibrary.getUncached();
        if (args.length > 1) {
            // TODO: drop this branch after JS adopts the single-argument version
            if (!(args[0] instanceof WasmMemory)) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be executable");
            }
            if (!lib.isExecutable(args[1])) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be executable");
            }
            return memSetGrowCallback(args[1]);
        }
        if (!lib.isExecutable(args[0])) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Argument must be executable");
        }
        return memSetGrowCallback(args[0]);
    }

    private static Object memSetGrowCallback(Object callback) {
        WasmContext context = WasmContext.get(null);
        context.setMemGrowCallback(callback);
        return WasmConstant.VOID;
    }

    public static void invokeMemGrowCallback(WasmMemory memory) {
        WasmContext context = WasmContext.get(null);
        Object callback = context.getMemGrowCallback();
        if (callback != null) {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                lib.execute(callback, memory);
            } catch (InteropException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Unable to call memory grow callback", e);
            }
        }
    }

    private static Object memSetNotifyCallback(Object[] args) {
        checkArgumentCount(args, 1);
        InteropLibrary lib = InteropLibrary.getUncached();
        if (args.length > 1) {
            // TODO: drop this branch after JS adopts the single-argument version
            if (!(args[0] instanceof WasmMemory)) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be executable");
            }
            if (!lib.isExecutable(args[1])) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be executable");
            }
            return memSetNotifyCallback(args[1]);
        }
        if (!lib.isExecutable(args[0])) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Argument must be executable");
        }
        return memSetNotifyCallback(args[0]);
    }

    private static Object memSetNotifyCallback(Object callback) {
        WasmContext context = WasmContext.get(null);
        context.setMemNotifyCallback(callback);
        return WasmConstant.VOID;
    }

    public static int invokeMemNotifyCallback(Node node, WasmMemory memory, long address, int count) {
        WasmContext context = WasmContext.get(node);
        Object callback = context.getMemNotifyCallback();
        if (callback != null) {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                return (int) lib.execute(callback, memory, address, count);
            } catch (InteropException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Unable to call memory notify callback", e);
            }
        }
        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Notify instruction used from Wasm not instantiated via JS.");
    }

    private static Object memSetWaitCallback(Object[] args) {
        checkArgumentCount(args, 1);
        InteropLibrary lib = InteropLibrary.getUncached();
        if (args.length > 1) {
            // TODO: drop this branch after JS adopts the single-argument version
            if (!(args[0] instanceof WasmMemory)) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be executable");
            }
            if (!lib.isExecutable(args[1])) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be executable");
            }
            return memSetWaitCallback(args[1]);
        }
        if (!lib.isExecutable(args[0])) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Argument must be executable");
        }
        return memSetWaitCallback(args[0]);
    }

    private static Object memSetWaitCallback(Object callback) {
        WasmContext context = WasmContext.get(null);
        context.setMemWaitCallback(callback);
        return WasmConstant.VOID;
    }

    public static int invokeMemWaitCallback(Node node, WasmMemory memory, long address, long expected, long timeout, boolean is64) {
        WasmContext context = WasmContext.get(node);
        Object callback = context.getMemWaitCallback();
        if (callback != null) {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                return (int) lib.execute(callback, memory, address, expected, timeout, is64);
            } catch (InteropException e) {
                throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Unable to call memory wait callback", e);
            }
        }
        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Wait instruction used from Wasm not instantiated via JS.");
    }

    private static Object memAsByteBuffer(Object[] args) {
        checkArgumentCount(args, 1);
        if (args[0] instanceof WasmMemory memory) {
            ByteBuffer buffer = memAsByteBuffer(memory);
            if (buffer != null) {
                return WasmContext.get(null).environment().asGuestValue(buffer);
            }
        }
        return WasmConstant.VOID;
    }

    public static ByteBuffer memAsByteBuffer(WasmMemory memory) {
        return WasmMemoryLibrary.getUncached().asByteBuffer(memory);
    }

    private Object globalAlloc(Object[] args) {
        checkArgumentCount(args, 2);
        InteropLibrary lib = InteropLibrary.getUncached();
        final ValueType valueType;
        try {
            String valueTypeString = lib.asString(args[0]);
            valueType = ValueType.valueOf(valueTypeString);
        } catch (UnsupportedMessageException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument (value type) must be convertible to String");
        } catch (IllegalArgumentException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
        }
        final boolean mutable;
        try {
            mutable = lib.asBoolean(args[1]);
        } catch (UnsupportedMessageException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument (mutable) must be convertible to boolean");
        }
        return globalAlloc(valueType, mutable, args[2]);
    }

    public WasmGlobal globalAlloc(ValueType valueType, boolean mutable, Object value) {
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
                case anyfunc:
                    if (!refTypes || !(value == WasmConstant.NULL || value instanceof WasmFunctionInstance)) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
                    }
                    return new WasmGlobal(valueType, mutable, value);
                case externref:
                    if (!refTypes) {
                        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
                    }
                    return new WasmGlobal(valueType, mutable, value);
                default:
                    throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid value type");
            }
        } catch (UnsupportedMessageException ex) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Cannot convert value to the specified value type");
        }
    }

    private static Object globalRead(Object[] args) {
        checkArgumentCount(args, 1);
        if (!(args[0] instanceof WasmGlobal global)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm global");
        }
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
            case anyfunc:
            case externref:
                return global.loadAsReference();

        }
        throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Incorrect internal Global type");
    }

    private Object globalWrite(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmGlobal global)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm global");
        }
        return globalWrite(global, args[1]);
    }

    public Object globalWrite(WasmGlobal global, Object value) {
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
            case anyfunc:
                if (!refTypes) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Invalid value type. Reference types are not enabled");
                }
                if (!(value == WasmConstant.NULL || value instanceof WasmFunctionInstance)) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Global type %s, value: %s", valueType, value);
                } else {
                    global.storeReference(value);
                }
                break;
            case externref:
                if (!refTypes) {
                    throw WasmJsApiException.format(WasmJsApiException.Kind.TypeError, "Invalid value type. Reference types are not enabled");
                }
                global.storeReference(value);
                break;
        }
        return WasmConstant.VOID;
    }

    private static Object instanceExport(Object[] args) {
        checkArgumentCount(args, 2);
        if (!(args[0] instanceof WasmInstance instance)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "First argument must be wasm instance");
        }
        if (!(args[1] instanceof String name)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Second argument must be string");
        }
        return instanceExport(instance, name);
    }

    public static Object instanceExport(WasmInstance instance, String name) {
        CompilerAsserts.neverPartOfCompilation();
        WasmFunction function = instance.module().exportedFunctions().get(name);
        Integer globalIndex = instance.module().exportedGlobals().get(name);
        Integer tableIndex = instance.module().exportedTables().get(name);
        Integer memoryIndex = instance.module().exportedMemories().get(name);

        if (function != null) {
            return instance.functionInstance(function);
        } else if (globalIndex != null) {
            return instance.externalGlobal(globalIndex);
        } else if (memoryIndex != null) {
            return instance.memory(memoryIndex);
        } else if (tableIndex != null) {
            final int address = instance.tableAddress(tableIndex);
            return instance.store().tables().table(address);
        } else {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, name + " is not a exported name of the given instance");
        }
    }

    public static Object embedderDataSet(Object[] args) {
        checkArgumentCount(args, 2);
        getEmbedderDataHolder(args).setEmbedderData(args[1]);
        return WasmConstant.VOID;
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
