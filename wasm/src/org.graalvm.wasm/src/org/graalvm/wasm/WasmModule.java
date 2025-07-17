/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.parser.DebugTranslator;
import org.graalvm.wasm.exception.ExceptionProvider;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.parser.ir.CodeEntry;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;

/**
 * Represents a parsed and validated WebAssembly module, which has not yet been instantiated.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class WasmModule extends SymbolTable implements TruffleObject {
    private final String name;
    private volatile List<LinkAction> linkActions;
    private final ModuleLimits limits;

    private Source source;
    @CompilationFinal(dimensions = 1) private byte[] bytecode;
    @CompilationFinal(dimensions = 1) private byte[] customData;
    @CompilationFinal(dimensions = 1) private byte[] codeSection;
    @CompilationFinal(dimensions = 1) private CodeEntry[] codeEntries;
    @CompilationFinal private boolean isParsed;
    private volatile boolean hasBeenInstantiated;

    private final ReentrantLock lock = new ReentrantLock();

    @CompilationFinal private int debugInfoOffset;
    @CompilationFinal private EconomicMap<Integer, DebugFunction> debugFunctions;

    private static final VarHandle LINK_ACTIONS;
    static {
        try {
            LINK_ACTIONS = MethodHandles.lookup().findVarHandle(WasmModule.class, "linkActions", List.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private WasmModule(String name, ModuleLimits limits) {
        super();
        this.name = name;
        this.limits = limits == null ? ModuleLimits.DEFAULTS : limits;
        this.linkActions = new ArrayList<>();
        this.isParsed = false;
        this.debugInfoOffset = -1;
    }

    public static WasmModule create(String name, ModuleLimits limits) {
        return new WasmModule(name, limits);
    }

    public static WasmModule createBuiltin(String name) {
        return new WasmModule(name, null);
    }

    @Override
    protected WasmModule module() {
        return this;
    }

    public SymbolTable symbolTable() {
        return this;
    }

    public String name() {
        return name;
    }

    public List<LinkAction> getOrRecreateLinkActions() {
        var result = (List<LinkAction>) LINK_ACTIONS.getAndSet(this, (List<LinkAction>) null);
        if (result != null) {
            return result;
        } else {
            return WasmInstantiator.recreateLinkActions(this);
        }
    }

    public void addLinkAction(LinkAction action) {
        assert !isParsed();
        linkActions.add(action);
    }

    public boolean hasBeenInstantiated() {
        return hasBeenInstantiated;
    }

    public void setHasBeenInstantiated() {
        this.hasBeenInstantiated = true;
    }

    /**
     * Private lock used for instantiation and linking.
     */
    public ReentrantLock getLock() {
        return lock;
    }

    public ModuleLimits limits() {
        return limits;
    }

    public Source source() {
        if (source == null) {
            if (isBuiltin()) {
                source = Source.newBuilder(WasmLanguage.ID, "", name).internal(true).build();
            } else {
                source = Source.newBuilder(WasmLanguage.ID, "", name).build();
            }
        }
        return source;
    }

    public byte[] bytecode() {
        return bytecode;
    }

    public int bytecodeLength() {
        return bytecode != null ? bytecode.length : 0;
    }

    public void setBytecode(byte[] bytecode) {
        this.bytecode = bytecode;
    }

    public CodeEntry[] codeEntries() {
        return codeEntries;
    }

    public void setCodeEntries(CodeEntry[] codeEntries) {
        this.codeEntries = codeEntries;
    }

    public boolean hasCodeEntries() {
        return codeEntries != null;
    }

    public void setParsed() {
        isParsed = true;
    }

    public boolean isParsed() {
        return isParsed;
    }

    public boolean isBuiltin() {
        return bytecode == null;
    }

    public byte[] customData() {
        return customData;
    }

    public void setCustomData(byte[] customData) {
        this.customData = customData;
    }

    public byte[] codeSection() {
        return codeSection;
    }

    public void setCodeSection(byte[] codeSection) {
        this.codeSection = codeSection;
    }

    public boolean hasDebugInfo() {
        return debugInfoOffset != -1;
    }

    public void setDebugInfoOffset(int offset) {
        this.debugInfoOffset = offset;
    }

    public int debugInfoOffset() {
        return debugInfoOffset;
    }

    @TruffleBoundary
    public int functionSourceCodeStartOffset(int functionIndex) {
        if (codeSection == null) {
            return -1;
        }
        final int codeEntryIndex = functionIndex - numImportedFunctions();
        final int startOffset = BinaryStreamParser.rawPeekI32(codeSection, codeSection.length - 4);
        return BinaryStreamParser.rawPeekI32(codeSection, startOffset + 12 * codeEntryIndex);
    }

    @TruffleBoundary
    public int functionSourceCodeInstructionOffset(int functionIndex) {
        if (codeSection == null) {
            return -1;
        }
        final int codeEntryIndex = functionIndex - numImportedFunctions();
        final int startOffset = BinaryStreamParser.rawPeekI32(codeSection, codeSection.length - 4);
        return BinaryStreamParser.rawPeekI32(codeSection, startOffset + 4 + 12 * codeEntryIndex);
    }

    @TruffleBoundary
    public int functionSourceCodeEndOffset(int functionIndex) {
        if (codeSection == null) {
            return -1;
        }
        final int codeEntryIndex = functionIndex - numImportedFunctions();
        final int startOffset = BinaryStreamParser.rawPeekI32(codeSection, codeSection.length - 4);
        return BinaryStreamParser.rawPeekI32(codeSection, startOffset + 8 + 12 * codeEntryIndex);
    }

    @Override
    public String toString() {
        return "wasm-module(" + name + ")";
    }

    @TruffleBoundary
    public EconomicMap<Integer, DebugFunction> debugFunctions() {
        // lazily load debug information if needed.
        if (debugFunctions == null && hasDebugInfo()) {
            DebugTranslator translator = new DebugTranslator(customData);
            debugFunctions = translator.readCompilationUnits(customData, debugInfoOffset);
        }
        return debugFunctions;
    }

    @ExportMessage
    boolean isInstantiable() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object instantiate(Object... arguments) {
        final WasmContext context = WasmContext.get(null);
        final Object importObject;
        if (arguments.length == 0) {
            importObject = WasmConstant.NULL;
        } else if (arguments.length == 1) {
            importObject = arguments[0];
        } else {
            throw WasmException.provider().createTypeError(Failure.TYPE_MISMATCH, "Can only provide a single import object.");
        }
        final WasmStore store = new WasmStore(context, context.language());
        return createInstance(store, importObject, WasmException.provider(), false);
    }

    public WasmInstance createInstance(WasmStore store, Object importObject, ExceptionProvider exceptionProvider, boolean importsOnlyInImportObject) {
        final WasmInstance instance = store.readInstance(this);
        var imports = resolveModuleImports(importObject, exceptionProvider, importsOnlyInImportObject);
        store.linker().tryLink(instance, imports);
        return instance;
    }

    private ImportValueSupplier resolveModuleImports(Object importObject, ExceptionProvider exceptionProvider, boolean importsOnlyInImportObject) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(importObject);
        List<Object> resolvedImports = new ArrayList<>(numImportedSymbols());

        if (!importedSymbols().isEmpty()) {
            if (!importObjectExists(importObject)) {
                if (importsOnlyInImportObject) {
                    throw exceptionProvider.createTypeError(Failure.TYPE_MISMATCH, "Module requires imports, but import object is undefined.");
                } else {
                    // imports could be provided by another source, such as a module.
                    return ImportValueSupplier.none();
                }
            }
        }

        for (ImportDescriptor descriptor : importedSymbols()) {
            final int listIndex = resolvedImports.size();
            assert listIndex == descriptor.importedSymbolIndex();

            final Object member = getImportObjectMember(importObject, descriptor, exceptionProvider, importsOnlyInImportObject);
            if (member == null) {
                // import could be provided by another source, such as a module.
                assert !importsOnlyInImportObject;
                resolvedImports.add(null);
                continue;
            }

            resolvedImports.add(switch (descriptor.identifier()) {
                case ImportIdentifier.FUNCTION -> requireCallable(member, descriptor, exceptionProvider);
                case ImportIdentifier.TABLE -> requireWasmTable(member, descriptor, exceptionProvider);
                case ImportIdentifier.MEMORY -> requireWasmMemory(member, descriptor, exceptionProvider);
                case ImportIdentifier.GLOBAL -> requireWasmGlobal(member, descriptor, exceptionProvider);
                default -> throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unknown import descriptor type: " + descriptor.identifier());
            });
        }

        assert resolvedImports.size() == numImportedSymbols();
        return (importDesc, instance) -> {
            // Import values are only valid in the module where they were resolved.
            if (instance.module() == WasmModule.this) {
                return resolvedImports.get(importDesc.importedSymbolIndex());
            } else {
                return null;
            }
        };
    }

    private static boolean importObjectExists(Object importObject) {
        final InteropLibrary interop = InteropLibrary.getUncached(importObject);
        return !interop.isNull(importObject) && interop.hasMembers(importObject);
    }

    private static Object getImportObjectMember(Object importObject, ImportDescriptor descriptor, ExceptionProvider exceptionProvider, boolean importsOnlyInImportObject) {
        try {
            final InteropLibrary importObjectInterop = InteropLibrary.getUncached(importObject);
            if (!importObjectInterop.isMemberReadable(importObject, descriptor.moduleName())) {
                // import could be provided by another source, such as a module.
                if (!importsOnlyInImportObject) {
                    return null;
                }
                throw exceptionProvider.formatTypeError(Failure.TYPE_MISMATCH, "Import object does not contain module \"%s\".", descriptor.moduleName());
            }
            final Object importedModuleObject = importObjectInterop.readMember(importObject, descriptor.moduleName());
            final InteropLibrary moduleObjectInterop = InteropLibrary.getUncached(importedModuleObject);
            if (!moduleObjectInterop.isMemberReadable(importedModuleObject, descriptor.memberName())) {
                throw exceptionProvider.formatLinkError(Failure.UNKNOWN_IMPORT, "Import module object \"%s\" does not contain \"%s\".", descriptor.moduleName(), descriptor.memberName());
            }
            return moduleObjectInterop.readMember(importedModuleObject, descriptor.memberName());
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unexpected state.");
        }
    }

    private static Object requireCallable(Object member, ImportDescriptor importDescriptor, ExceptionProvider exceptionProvider) {
        if (!(member instanceof WasmFunctionInstance || InteropLibrary.getUncached().isExecutable(member))) {
            throw exceptionProvider.createLinkError(Failure.INCOMPATIBLE_IMPORT_TYPE, "Member " + member + " " + importDescriptor + " is not callable.");
        }
        return member;
    }

    private static WasmMemory requireWasmMemory(Object member, ImportDescriptor importDescriptor, ExceptionProvider exceptionProvider) {
        if (!(member instanceof WasmMemory memory)) {
            throw exceptionProvider.createLinkError(Failure.INCOMPATIBLE_IMPORT_TYPE, "Member " + member + " " + importDescriptor + " is not a valid memory.");
        }
        return memory;
    }

    private static WasmTable requireWasmTable(Object member, ImportDescriptor importDescriptor, ExceptionProvider exceptionProvider) {
        if (!(member instanceof WasmTable table)) {
            throw exceptionProvider.createLinkError(Failure.INCOMPATIBLE_IMPORT_TYPE, "Member " + member + " " + importDescriptor + " is not a valid table.");
        }
        return table;
    }

    private static WasmGlobal requireWasmGlobal(Object member, ImportDescriptor importDescriptor, ExceptionProvider exceptionProvider) {
        if (!(member instanceof WasmGlobal global)) {
            throw exceptionProvider.createLinkError(Failure.INCOMPATIBLE_IMPORT_TYPE, "Member " + member + " " + importDescriptor + " is not a valid global.");
        }
        return global;
    }
}
