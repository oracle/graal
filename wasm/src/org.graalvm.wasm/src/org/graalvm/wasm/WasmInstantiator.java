/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.runtime.WasmInstance;
import org.graalvm.wasm.runtime.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.runtime.memory.UnsafeWasmMemory;
import org.graalvm.wasm.runtime.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmBlockNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmRootNode;
import org.graalvm.wasm.parser.ir.BlockNode;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.IfNode;
import org.graalvm.wasm.parser.ir.LoopNode;
import org.graalvm.wasm.parser.ir.ParserNode;
import org.graalvm.wasm.parser.module.WasmDataDefinition;
import org.graalvm.wasm.parser.module.WasmElementDefinition;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.parser.module.WasmFunctionDefinition;
import org.graalvm.wasm.parser.module.WasmGlobalDefinition;
import org.graalvm.wasm.parser.module.WasmMemoryDefinition;
import org.graalvm.wasm.parser.module.WasmModule;
import org.graalvm.wasm.parser.module.WasmTableDefinition;
import org.graalvm.wasm.parser.module.imports.WasmFunctionImport;
import org.graalvm.wasm.parser.module.imports.WasmGlobalImport;
import org.graalvm.wasm.parser.module.imports.WasmMemoryImport;
import org.graalvm.wasm.parser.module.imports.WasmTableImport;
import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.runtime.WasmTable;

import java.util.List;
import java.util.Map;

import static org.graalvm.wasm.Assert.assertUnsignedIntGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.WasmMath.minUnsigned;

/**
 * Creates wasm instances by converting parser nodes into Truffle nodes.
 */
public class WasmInstantiator {
    private static final int MIN_DEFAULT_STACK_SIZE = 1_000_000;
    private static final int MAX_DEFAULT_ASYNC_STACK_SIZE = 10_000_000;

    private static class ParsingExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable parsingException = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.parsingException = e;
        }

        public Throwable parsingException() {
            return parsingException;
        }
    }

    private final WasmLanguage language;
    private final ModuleLimits limits;

    @TruffleBoundary
    public WasmInstantiator(WasmLanguage language, ModuleLimits limits) {
        this.language = language;
        this.limits = limits == null ? ModuleLimits.DEFAULTS : limits;
    }

    @TruffleBoundary
    public void reinitializeInstance(WasmModuleInstance instance, boolean resetMemory) {
        final WasmModule module = instance.getModule();
        if (resetMemory) {
            if (instance.getMemory() != null) {
                setMemoryData(module, instance);
            }
            if (instance.getTable() != null) {
                setTableElements(module, instance);
            }
        }
        WasmGlobalDefinition[] globals = module.getLocalGlobals();
        for (int i = module.getGlobalImportCount(); i < module.getGlobalCount(); i++) {
            WasmGlobal global = instance.getGlobal(i);
            WasmGlobalDefinition definition = globals[i - module.getGlobalImportCount()];
            global.storeLong(definition.getValueOrIndex());
        }
    }

    @TruffleBoundary
    public WasmModuleInstance createInstance(WasmContext context, WasmModule module, Map<String, WasmModuleInstance> externalInstances) {
        int binarySize = module.getBinarySize();
        final WasmModuleInstance instance = createInstance(module);
        final int asyncParsingBinarySize = WasmOptions.AsyncParsingBinarySize.getValue(context.environment().getOptions());
        if (binarySize < asyncParsingBinarySize) {
            instantiateModule(context, module, instance, externalInstances);
        } else {
            final Runnable parsing = new Runnable() {
                @Override
                public void run() {
                    instantiateModule(context, module, instance, externalInstances);
                }
            };
            final String name = "wasm-parsing-thread(" + module.getName() + ")";
            final int requestedSize = WasmOptions.AsyncParsingStackSize.getValue(context.environment().getOptions()) * 1000;
            final int defaultSize = Math.max(MIN_DEFAULT_STACK_SIZE, Math.min(2 * binarySize, MAX_DEFAULT_ASYNC_STACK_SIZE));
            final int stackSize = requestedSize != 0 ? requestedSize : defaultSize;
            final Thread parsingThread = new Thread(null, parsing, name, stackSize);
            final ParsingExceptionHandler handler = new ParsingExceptionHandler();
            parsingThread.setUncaughtExceptionHandler(handler);
            parsingThread.start();
            try {
                parsingThread.join();
                if (handler.parsingException() != null) {
                    throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing failed.");
                }
            } catch (InterruptedException e) {
                throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing interrupted.");
            }
        }
        return instance;
    }

    private static WasmModuleInstance createInstance(WasmModule module) {
        final String[] exportNames = module.getExportNames();
        final WasmExternalValue[] exports = module.getExports();
        final WasmModuleInstance instance = new WasmModuleInstance(
                        module,
                        module.getFunctionTypes(),
                        new WasmFunctionInstance[module.getFunctionCount()],
                        new WasmGlobal[module.getGlobalCount()],
                        exports.length,
                        module.getName(),
                        module.getFunctionNames());
        for (int i = 0; i < exports.length; i++) {
            instance.addExport(exportNames[i], exports[i]);
        }
        return instance;
    }

    private void instantiateModule(WasmContext context, WasmModule module, WasmModuleInstance instance, Map<String, WasmModuleInstance> externalInstances) {
        // Validation is already done during decoding

        checkAndDefineImports(module, instance, externalInstances);
        initializeGlobalValues(module, instance);
        allocateModule(context, module, instance);
        checkTableElements(module, instance);
        checkMemoryData(module, instance);
        setTableElements(module, instance);
        setMemoryData(module, instance);
        if (module.getStartFunctionIndex() != -1) {
            WasmFunctionInstance functionInstance = instance.getFunction(module.getStartFunctionIndex());
            functionInstance.target().call();
        }
    }

    private static WasmModuleInstance getExternalInstance(String moduleName, String instanceName, Map<String, WasmModuleInstance> externalInstances) {
        if (externalInstances.containsKey(moduleName)) {
            return externalInstances.get(moduleName);
        }
        throw WasmException.format(Failure.UNKNOWN_IMPORT, "Imported module %s of module %s does not match any provided external value", moduleName, instanceName);
    }

    private static WasmExternalValue getExternalValue(String moduleName, String name, String instanceName, WasmModuleInstance externalInstance) {
        final WasmExternalValue externalValue = externalInstance.getExport(name);
        if (externalValue == null) {
            throw WasmException.format(Failure.UNKNOWN_IMPORT, "Import %s.%s of module %s does not match any provided external value", moduleName, name, instanceName);
        }
        return externalValue;
    }

    private static void checkAndDefineImports(WasmModule module, WasmModuleInstance instance, Map<String, WasmModuleInstance> externalInstances) {
        final WasmFunctionImport[] functionImports = module.getFunctionImports();
        if (functionImports != null) {
            for (WasmFunctionImport i : functionImports) {
                final String moduleName = i.getModule();
                final String name = i.getName();
                final WasmModuleInstance externalInstance = getExternalInstance(moduleName, instance.getName(), externalInstances);
                final WasmExternalValue externalValue = getExternalValue(moduleName, name, instance.getName(), externalInstance);
                if (externalValue.isFunction()) {
                    final WasmFunctionType importType = i.getFunctionType();
                    if (!externalValue.functionExists(externalInstance)) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "The provided external function index in module %s is not part of the store", instance.getName());
                    }
                    final WasmFunctionInstance externalFunctionInstance = externalValue.getFunction(externalInstance);
                    final WasmFunctionType externalType = externalFunctionInstance.getFunctionType();
                    if (importType != externalType) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Incompatible function type for import %s.%s in module %s", moduleName, name, instance.getName());
                    }
                    instance.addFunction(externalFunctionInstance);
                } else {
                    throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Expected import %s.%s in module %s to be a function", moduleName, name, instance.getName());
                }
            }
        }

        final WasmTableImport[] tableImports = module.getTableImports();
        if (tableImports != null) {
            for (WasmTableImport i : tableImports) {
                final String moduleName = i.getModule();
                final String name = i.getName();
                final WasmModuleInstance externalInstance = getExternalInstance(moduleName, instance.getName(), externalInstances);
                final WasmExternalValue externalValue = getExternalValue(moduleName, name, instance.getName(), externalInstance);
                if (externalValue.isTable()) {
                    if (!externalValue.tableExists(externalInstance)) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "The provided external table index in module %s is not part of the store", instance.getName());
                    }
                    final WasmTable externalTable = externalValue.getTable(externalInstance);
                    assertUnsignedIntLessOrEqual(i.getMin(), externalTable.declaredMinSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                    assertUnsignedIntGreaterOrEqual(i.getMax(), externalTable.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                    instance.addTable(externalTable);
                } else {
                    throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Expected import %s.%s in module %s to be a table", moduleName, name, instance.getName());
                }
            }
        }

        final WasmMemoryImport[] memoryImports = module.getMemoryImports();
        if (memoryImports != null) {
            for (WasmMemoryImport i : memoryImports) {
                final String moduleName = i.getModule();
                final String name = i.getName();
                final WasmModuleInstance externalInstance = getExternalInstance(moduleName, instance.getName(), externalInstances);
                final WasmExternalValue externalValue = getExternalValue(moduleName, name, instance.getName(), externalInstance);
                if (externalValue.isMemory()) {
                    if (!externalValue.memoryExists(externalInstance)) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "The provided external memory in module %s is not part of the store", instance.getName());
                    }
                    final WasmMemory externalMemory = externalValue.getMemory(externalInstance);
                    assertUnsignedIntLessOrEqual(i.getMin(), externalMemory.declaredMinSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                    assertUnsignedIntGreaterOrEqual(i.getMax(), externalMemory.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                    instance.addMemory(externalMemory);
                } else {
                    throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Expected import %s.%s in module %s to be a memory", moduleName, name, instance.getName());
                }
            }
        }

        final WasmGlobalImport[] globalImports = module.getGlobalImports();
        if (globalImports != null) {
            for (WasmGlobalImport i : globalImports) {
                final String moduleName = i.getModule();
                final String name = i.getName();
                final WasmModuleInstance externalInstance = getExternalInstance(moduleName, instance.getName(), externalInstances);
                final WasmExternalValue externalValue = getExternalValue(moduleName, name, instance.getName(), externalInstance);
                if (externalValue.isGlobal()) {
                    if (!externalValue.globalExists(externalInstance)) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "The provided external global index in module %s is not part of the store", instance.getName());
                    }
                    final WasmGlobal externalGlobal = externalValue.getGlobal(externalInstance);
                    if (i.getMutability() != externalGlobal.getMutability()) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Incompatible mutability for import %s.%s in module %s", moduleName, name, instance.getName());
                    }
                    if (i.getValueType() != externalGlobal.getValueType()) {
                        throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Incompatible value type for import %s.%s in module %s", moduleName, name, instance.getName());
                    }
                    instance.addGlobal(externalGlobal);
                } else {
                    throw WasmException.format(Failure.INCOMPATIBLE_IMPORT_TYPE, "Expected import %s.%s in module %s to be a global", moduleName, name, instance.getName());
                }
            }
        }
    }

    private static void initializeGlobalValues(WasmModule module, WasmModuleInstance instance) {
        final WasmGlobalDefinition[] globals = module.getLocalGlobals();
        if (globals == null) {
            return;
        }
        for (WasmGlobalDefinition global : globals) {
            if (!global.isInitialized()) {
                WasmGlobal g = instance.getGlobal((int) global.getValueOrIndex());
                global.initialize(g.loadAsLong());
            }
        }
    }

    private void allocateModule(WasmContext context, WasmModule module, WasmModuleInstance instance) {
        final WasmFunctionDefinition[] functions = module.getLocalFunctions();

        if (functions != null) {
            // Create call targets for all functions
            WasmRootNode[] functionRootNodes = new WasmRootNode[module.getLocalFunctionCount()];
            int functionRootNodeIndex = 0;
            int functionIndex = module.getFunctionImportCount();
            for (WasmFunctionDefinition function : functions) {
                WasmRootNode rootNode = allocateFunction(functionIndex, function, module, instance);
                functionRootNodes[functionRootNodeIndex++] = rootNode;
                String name = instance.getFunctionName(functionIndex);
                if (name == null) {
                    name = function.getName();
                }
                instance.addFunction(new WasmFunctionInstance(context, function.getFunctionType(), rootNode.getCallTarget(), name, functionIndex));
                functionIndex++;
            }

            // Instantiate code entries of functions
            functionRootNodeIndex = 0;
            for (WasmFunctionDefinition function : functions) {
                instantiateCodeEntry(function, functionRootNodes[functionRootNodeIndex++], instance.getInstance());
            }
        }

        final WasmTableDefinition[] tables = module.getLocalTables();

        if (tables != null) {
            context.increaseTablesSize(tables.length);
            for (WasmTableDefinition table : tables) {
                final int maxAllowedSize = minUnsigned(table.getMax(), limits.tableInstanceSizeLimit());
                limits.checkTableInstanceSize(table.getMin());
                WasmTable t = new WasmTable(table.getMin(), table.getMax(), maxAllowedSize);
                instance.addTable(t);
                context.addTable(t);
            }
        }

        final WasmMemoryDefinition[] memories = module.getLocalMemories();

        if (memories != null) {
            context.increaseMemoriesSize(memories.length);
            for (WasmMemoryDefinition memory : memories) {
                final int maxAllowedSize = minUnsigned(memory.getMax(), limits.memoryInstanceSizeLimit());
                limits.checkMemoryInstanceSize(memory.getMin());
                final WasmMemory wasmMemory;
                if (context.environment().getOptions().get(WasmOptions.UseUnsafeMemory)) {
                    wasmMemory = new UnsafeWasmMemory(memory.getMin(), memory.getMax(), maxAllowedSize);
                } else {
                    wasmMemory = new ByteArrayWasmMemory(memory.getMin(), memory.getMax(), maxAllowedSize);
                }
                instance.addMemory(wasmMemory);
                context.addMemory(wasmMemory);
            }
        }

        final WasmGlobalDefinition[] globals = module.getLocalGlobals();

        if (globals != null) {
            context.increaseGlobalsSize(globals.length);
            for (WasmGlobalDefinition global : globals) {
                WasmGlobal g = new WasmGlobal(global.getValueType(), global.getMutability(), global.getValueOrIndex());
                instance.addGlobal(g);
                context.addGlobal(g);
            }
        }
    }

    private static void checkTableElements(WasmModule module, WasmModuleInstance instance) {
        final WasmElementDefinition[] elementDefinitions = module.getElementDefinitions();

        if (elementDefinitions == null) {
            return;
        }
        WasmTable table = instance.getTable();
        for (WasmElementDefinition elementDefinition : elementDefinitions) {
            int offset = elementDefinition.getOffsetOrIndex();
            if (!elementDefinition.isInitialized()) {
                offset = instance.getGlobal(offset).loadAsInt();
                elementDefinition.setOffset(offset);
            }
            int[] functionIndices = elementDefinition.getFunctionIndices();

            Assert.assertUnsignedIntLessOrEqual(offset, table.size(), Failure.ELEMENTS_SEGMENT_DOES_NOT_FIT);
            Assert.assertUnsignedIntLessOrEqual(offset + functionIndices.length, table.size(), Failure.ELEMENTS_SEGMENT_DOES_NOT_FIT);
        }
    }

    private static void setTableElements(WasmModule module, WasmModuleInstance instance) {
        final WasmElementDefinition[] elementDefinitions = module.getElementDefinitions();

        if (elementDefinitions == null) {
            return;
        }
        WasmTable table = instance.getTable();
        for (WasmElementDefinition elementDefinition : elementDefinitions) {
            int offset = elementDefinition.getOffsetOrIndex();
            int[] functionIndices = elementDefinition.getFunctionIndices();

            for (int i = 0; i < functionIndices.length; i++) {
                final int functionIndex = functionIndices[i];
                final WasmFunctionInstance function = instance.getFunction(functionIndex);
                table.initialize(i + offset, function);
            }
        }
    }

    private static void checkMemoryData(WasmModule module, WasmModuleInstance instance) {
        final WasmDataDefinition[] dataDefinitions = module.getDataDefinitions();

        if (dataDefinitions == null) {
            return;
        }
        WasmMemory memory = instance.getMemory();
        for (WasmDataDefinition dataDefinition : dataDefinitions) {
            int offset = dataDefinition.getOffsetOrIndex();
            if (!dataDefinition.isInitialized()) {
                offset = instance.getGlobal(offset).loadAsInt();
                dataDefinition.setOffset(offset);
            }
            byte[] data = dataDefinition.getData();

            Assert.assertUnsignedIntLessOrEqual(offset, WasmMath.toUnsignedIntExact(memory.byteSize()), Failure.DATA_SEGMENT_DOES_NOT_FIT);
            Assert.assertUnsignedIntLessOrEqual(offset + data.length, WasmMath.toUnsignedIntExact(memory.byteSize()), Failure.DATA_SEGMENT_DOES_NOT_FIT);
        }
    }

    private static void setMemoryData(WasmModule module, WasmModuleInstance instance) {
        final WasmDataDefinition[] dataDefinitions = module.getDataDefinitions();

        if (dataDefinitions == null) {
            return;
        }
        WasmMemory memory = instance.getMemory();
        for (WasmDataDefinition dataDefinition : dataDefinitions) {
            int offset = dataDefinition.getOffsetOrIndex();
            byte[] data = dataDefinition.getData();

            for (int i = 0; i < data.length; i++) {
                final byte b = data[i];
                memory.store_i32_8(null, i + offset, b);
            }
        }
    }

    private WasmRootNode allocateFunction(int functionIndex, WasmFunctionDefinition functionDefinition, WasmModule module, WasmModuleInstance instance) {
        WasmCodeEntry wasmCodeEntry = new WasmCodeEntry(functionDefinition.getFunctionType(), functionIndex, functionDefinition.getData());

        /*
         * Create the root node and create and set the call target for the body. This needs to be
         * done before translating the body block, because we need to be able to create direct call
         * nodes {@see TruffleRuntime#createDirectCallNode} during translation.
         */
        WasmRootNode rootNode = new WasmRootNode(language, instance.getInstance(), wasmCodeEntry, module.getSource(), instance.getFunctionName(functionIndex));
        /*
         * Set the code entry local variables (which contain the parameters and the locals).
         */
        wasmCodeEntry.setLocalTypes(functionDefinition.getBody().getLocals());
        return rootNode;
    }

    private void instantiateCodeEntry(WasmFunctionDefinition functionDefinition, WasmRootNode rootNode, WasmInstance instance) {
        /*
         * Translate and set the function body.
         */
        WasmBlockNode bodyBlock = instantiateBlockNode(instance, rootNode.codeEntry(), functionDefinition.getBody().getFunctionBlock());
        rootNode.setBody(bodyBlock);

        /* Initialize the Truffle-related components required for execution. */
        functionDefinition.getBody().initializeTruffleComponents(rootNode);
    }

    private WasmBlockNode instantiateBlockNode(WasmInstance instance, WasmCodeEntry codeEntry, BlockNode block) {
        final WasmBlockNode currentBlock = block.createWasmBlockNode(instance, codeEntry);
        List<ParserNode> childNodes = block.getChildNodes();
        Node[] children = new Node[childNodes.size()];
        int childIndex = 0;
        for (ParserNode childNode : childNodes) {
            Node child = null;
            if (childNode instanceof BlockNode) {
                child = instantiateBlockNode(instance, codeEntry, (BlockNode) childNode);
            }
            if (childNode instanceof LoopNode) {
                LoopNode loopNode = (LoopNode) childNode;
                WasmBlockNode loopBody = instantiateBlockNode(instance, codeEntry, loopNode.getBodyNode());
                child = Truffle.getRuntime().createLoopNode(loopBody);
            }
            if (childNode instanceof IfNode) {
                IfNode ifNode = (IfNode) childNode;
                WasmBlockNode thenBlock = instantiateBlockNode(instance, codeEntry, ifNode.getThenNode());
                WasmBlockNode elseBlock = null;
                if (ifNode.hasElseBlock()) {
                    elseBlock = instantiateBlockNode(instance, codeEntry, ifNode.getElseNode());
                }
                child = ifNode.createWasmIfNode(instance, codeEntry, thenBlock, elseBlock);
            }
            if (childNode instanceof CallNode) {
                CallNode callNode = (CallNode) childNode;
                if (callNode.isIndirectCall()) {
                    child = WasmIndirectCallNode.create();
                } else {
                    child = Truffle.getRuntime().createDirectCallNode(instance.getFunction(callNode.getFunctionIndex()).target());
                }
            }
            children[childIndex++] = child;
        }
        block.initializeWasmBlockNode(currentBlock, children);
        return currentBlock;
    }
}
