/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.nodes.others.LLVMStatementRootNode;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.StackManager;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.SulongLibrary;
import com.oracle.truffle.llvm.runtime.SystemContextExtension;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemoryOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMVoidStatementNodeGen;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

final class Runner {

    private static final String MAIN_METHOD_NAME = "main";
    private static final String START_METHOD_NAME = "_start";

    private static final String CONSTRUCTORS_VARNAME = "llvm.global_ctors";
    private static final String DESTRUCTORS_VARNAME = "llvm.global_dtors";
    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() - p2.getFirst();
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p2.getFirst() - p1.getFirst();

    private final LLVMContext context;
    private final DefaultLoader loader;
    private final LLVMLanguage language;

    Runner(LLVMContext context, DefaultLoader loader) {
        this.context = context;
        this.loader = loader;
        this.language = context.getLanguage();
    }

    /**
     * Parse bitcode data and do first initializations to prepare bitcode execution.
     */
    CallTarget parse(Source source) {
        ParserInput input = getParserData(source);
        return parse(source, input.bytes, input.library);
    }

    private static ParserInput getParserData(Source source) {
        ByteSequence bytes;
        ExternalLibrary library;
        if (source.hasBytes()) {
            bytes = source.getBytes();
            if (source.getPath() != null) {
                library = new ExternalLibrary(Paths.get(source.getPath()), false, source.isInternal());
            } else {
                library = new ExternalLibrary("<STREAM-" + UUID.randomUUID().toString() + ">", false, source.isInternal());
            }
        } else if (source.hasCharacters()) {
            switch (source.getMimeType()) {
                case LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE:
                    bytes = ByteSequence.create(decodeBase64(source.getCharacters()));
                    library = new ExternalLibrary("<STREAM-" + UUID.randomUUID().toString() + ">", false, source.isInternal());
                    break;
                default:
                    throw new LLVMParserException("Character-based source with unexpected mime type: " + source.getMimeType());
            }
        } else {
            throw new LLVMParserException("Should not reach here: Source is neither char-based nor byte-based!");
        }
        return new ParserInput(bytes, library);
    }

    private static class LoadModulesNode extends RootNode {

        final SulongLibrary sulongLibrary;
        final FrameSlot stackPointerSlot;
        final ContextReference<LLVMContext> ctxRef;

        final int initContextBefore;
        @Child LLVMStatementNode initContext;

        @Children final InitializeSymbolsNode[] initSymbols;
        @Children final InitializeModuleNode[] initModules;

        LoadModulesNode(Runner runner, FrameDescriptor rootFrame, InitializationOrder order, SulongLibrary sulongLibrary) {
            super(runner.language, rootFrame);
            this.sulongLibrary = sulongLibrary;
            this.stackPointerSlot = rootFrame.findFrameSlot(LLVMStack.FRAME_ID);
            this.ctxRef = runner.language.getContextReference();

            this.initContextBefore = order.sulongLibraries.size();
            this.initContext = runner.context.createInitializeContextNode(rootFrame);

            int libCount = order.sulongLibraries.size() + order.otherLibraries.size();
            this.initSymbols = new InitializeSymbolsNode[libCount];
            this.initModules = new InitializeModuleNode[libCount];

            createNodes(runner, rootFrame, order.sulongLibraries, 0, this.initSymbols, this.initModules);
            createNodes(runner, rootFrame, order.otherLibraries, this.initContextBefore, this.initSymbols, this.initModules);
        }

        private static void createNodes(Runner runner, FrameDescriptor rootFrame, List<LLVMParserResult> parserResults, int offset, InitializeSymbolsNode[] initSymbols,
                        InitializeModuleNode[] initModules) {
            for (int i = 0; i < parserResults.size(); i++) {
                LLVMParserResult res = parserResults.get(i);
                initSymbols[offset + i] = new InitializeSymbolsNode(runner.context, res);
                initModules[offset + i] = new InitializeModuleNode(runner, rootFrame, res);
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMContext ctx = ctxRef.get();
            try (StackPointer stackPointer = ctxRef.get().getThreadingStack().getStack().newFrame()) {
                frame.setObject(stackPointerSlot, stackPointer);

                BitSet shouldInit = createBitset();
                LLVMPointer[] roSections = new LLVMPointer[initSymbols.length];
                doInitSymbols(ctx, shouldInit, roSections);

                doInitModules(frame, ctx, shouldInit, roSections, 0, initContextBefore);
                initContext.execute(frame);
                doInitModules(frame, ctx, shouldInit, roSections, initContextBefore, initModules.length);
                return sulongLibrary;
            }
        }

        @TruffleBoundary
        private BitSet createBitset() {
            return new BitSet(initSymbols.length);
        }

        @ExplodeLoop
        private void doInitSymbols(LLVMContext ctx, BitSet shouldInit, LLVMPointer[] roSections) {
            for (int i = 0; i < initSymbols.length; i++) {
                if (initSymbols[i].shouldInitialize(ctx)) {
                    shouldInit.set(i);
                    roSections[i] = initSymbols[i].execute(ctx);
                }
            }
        }

        @ExplodeLoop
        private void doInitModules(VirtualFrame frame, LLVMContext ctx, BitSet shouldInit, LLVMPointer[] roSections, int from, int to) {
            for (int i = from; i < to; i++) {
                if (shouldInit.get(i)) {
                    initModules[i].execute(frame, ctx, roSections[i]);
                }
            }
        }
    }

    private CallTarget parse(Source source, ByteSequence bytes, ExternalLibrary library) {
        // process the bitcode file and its dependencies in the dynamic linking order
        // (breadth-first)
        List<LLVMParserResult> parserResults = new ArrayList<>();
        ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>();

        parse(parserResults, dependencyQueue, source, library, bytes);
        assert !library.isNative() && !parserResults.isEmpty();

        ExternalLibrary[] sulongLibraries = parseDependencies(parserResults, dependencyQueue);
        assert dependencyQueue.isEmpty();

        addExternalSymbolsToScopes(parserResults);
        parseFunctionsEagerly(parserResults);

        InitializationOrder initializationOrder = computeInitializationOrder(parserResults, sulongLibraries);
        overrideSulongLibraryFunctionsWithIntrinsics(initializationOrder.sulongLibraries);

        return createLibraryCallTarget(source.getName(), parserResults, initializationOrder);
    }

    private abstract static class AllocGlobalNode extends LLVMNode {

        static final AllocGlobalNode[] EMPTY = {};

        final String name;

        AllocGlobalNode(GlobalVariable global) {
            this.name = global.getName();
        }

        abstract LLVMPointer allocate(LLVMPointer roBase, LLVMPointer rwBase);
    }

    private static final class AllocPointerGlobalNode extends AllocGlobalNode {

        AllocPointerGlobalNode(GlobalVariable global) {
            super(global);
        }

        @Override
        LLVMPointer allocate(LLVMPointer roBase, LLVMPointer rwBase) {
            return LLVMManagedPointer.create(new LLVMGlobalContainer());
        }
    }

    private static final class AllocOtherGlobalNode extends AllocGlobalNode {

        final boolean readOnly;
        final long offset;

        AllocOtherGlobalNode(GlobalVariable global, Type type, DataSection roSection, DataSection rwSection) {
            super(global);
            this.readOnly = global.isReadOnly();

            DataSection dataSection = readOnly ? roSection : rwSection;
            this.offset = dataSection.add(global, type);
        }

        @Override
        LLVMPointer allocate(LLVMPointer roBase, LLVMPointer rwBase) {
            LLVMPointer base = readOnly ? roBase : rwBase;
            return base.increment(offset);
        }
    }

    private static final class DataSection {

        final DataLayout dataLayout;
        final ArrayList<Type> types = new ArrayList<>();

        private int offset = 0;

        DataSection(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
        }

        long add(GlobalVariable global, Type type) {
            int alignment = getAlignment(dataLayout, global, type);
            int padding = Type.getPadding(offset, alignment);
            addPaddingTypes(types, padding);
            offset += padding;
            long ret = offset;
            types.add(type);
            offset += type.getSize(dataLayout);
            return ret;
        }

        LLVMAllocateNode getAllocateNode(NodeFactory factory, String typeName, boolean readOnly) {
            if (offset > 0) {
                StructureType structType = new StructureType(typeName, true, types.toArray(Type.EMPTY_ARRAY));
                return factory.createAllocateGlobalsBlock(structType, readOnly);
            } else {
                return null;
            }
        }
    }

    private static final class InitializeSymbolsNode extends LLVMNode {

        @Child LLVMAllocateNode allocRoSection;
        @Child LLVMAllocateNode allocRwSection;

        @Children final AllocGlobalNode[] allocGlobals;

        final LLVMScope fileScope;

        InitializeSymbolsNode(LLVMContext context, LLVMParserResult res) {
            DataLayout dataLayout = context.getDataSpecConverter();

            // allocate all non-pointer types as two structs
            // one for read-only and one for read-write
            DataSection roSection = new DataSection(dataLayout);
            DataSection rwSection = new DataSection(dataLayout);
            ArrayList<AllocGlobalNode> allocGlobalsList = new ArrayList<>();
            for (GlobalVariable global : res.getDefinedGlobals()) {
                Type type = global.getType().getPointeeType();
                if (isSpecialGlobalSlot(type)) {
                    allocGlobalsList.add(new AllocPointerGlobalNode(global));
                } else {
                    // allocate at least one byte per global (to make the pointers unique)
                    if (type.getSize(dataLayout) == 0) {
                        type = PrimitiveType.getIntegerType(8);
                    }
                    allocGlobalsList.add(new AllocOtherGlobalNode(global, type, roSection, rwSection));
                }
            }

            this.allocRoSection = roSection.getAllocateNode(context.getLanguage().getNodeFactory(), "roglobals_struct", true);
            this.allocRwSection = rwSection.getAllocateNode(context.getLanguage().getNodeFactory(), "rwglobals_struct", false);
            this.allocGlobals = allocGlobalsList.toArray(AllocGlobalNode.EMPTY);
            this.fileScope = res.getRuntime().getFileScope();
        }

        public boolean shouldInitialize(LLVMContext ctx) {
            return !ctx.isScopeLoaded(fileScope);
        }

        public LLVMPointer execute(LLVMContext ctx) {
            LLVMPointer roBase = allocOrNull(allocRoSection);
            LLVMPointer rwBase = allocOrNull(allocRwSection);

            allocGlobals(ctx, roBase, rwBase);
            if (allocRoSection != null) {
                ctx.registerReadOnlyGlobals(roBase);
            }
            if (allocRwSection != null) {
                ctx.registerGlobals(rwBase);
            }

            bindUnresolvedSymbols(ctx);
            ctx.registerScope(fileScope);

            return roBase; // needed later to apply memory protection after initialization
        }

        @ExplodeLoop
        private void allocGlobals(LLVMContext ctx, LLVMPointer roBase, LLVMPointer rwBase) {
            for (AllocGlobalNode allocGlobal : allocGlobals) {
                LLVMGlobal descriptor = fileScope.getGlobalVariable(allocGlobal.name);
                if (!descriptor.isInitialized()) {
                    // because of our symbol overriding support, it can happen that the global was
                    // already bound before to a different target location
                    LLVMPointer ref = allocGlobal.allocate(roBase, rwBase);
                    descriptor.setTarget(ref);
                    ctx.registerGlobalReverseMap(descriptor, ref);
                }
            }
        }

        @TruffleBoundary
        private void bindUnresolvedSymbols(LLVMContext ctx) {
            NFIContextExtension nfiContextExtension = ctx.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);
            LLVMIntrinsicProvider intrinsicProvider = ctx.getLanguage().getContextExtensionOrNull(LLVMIntrinsicProvider.class);
            for (LLVMSymbol symbol : fileScope.values()) {
                if (!symbol.isDefined()) {
                    if (symbol instanceof LLVMGlobal) {
                        LLVMGlobal global = (LLVMGlobal) symbol;
                        bindGlobal(ctx, global, nfiContextExtension);
                    } else if (symbol instanceof LLVMFunctionDescriptor) {
                        LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) symbol;
                        bindUnresolvedFunction(ctx, function, nfiContextExtension, intrinsicProvider);
                    } else if (symbol instanceof LLVMAlias) {
                        // nothing to do
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("Unknown symbol: " + symbol.getClass());
                    }
                }
            }
        }

        private static LLVMPointer allocOrNull(LLVMAllocateNode allocNode) {
            if (allocNode != null) {
                return allocNode.executeWithTarget();
            } else {
                return null;
            }
        }
    }

    private static void addPaddingTypes(ArrayList<Type> result, int padding) {
        assert padding >= 0;
        int remaining = padding;
        while (remaining > 0) {
            int size = Math.min(Long.BYTES, Integer.highestOneBit(remaining));
            result.add(PrimitiveType.getIntegerType(size * Byte.SIZE));
            remaining -= size;
        }
    }

    private static int getAlignment(DataLayout dataLayout, GlobalVariable global, Type type) {
        return global.getAlign() > 0 ? 1 << (global.getAlign() - 1) : type.getAlignment(dataLayout);
    }

    private static boolean isSpecialGlobalSlot(Type type) {
        // globals of pointer type can potentially contain a TruffleObject
        return type instanceof PointerType;
    }

    ExternalLibrary[] parseDefaultLibraries(List<LLVMParserResult> parserResults) {
        ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>();

        // There could be conflicts between Sulong's default libraries and the ones that are
        // passed on the command-line. To resolve that, we add ours first but parse them later
        // on.
        String[] sulongLibraryNames = language.getContextExtension(SystemContextExtension.class).getSulongDefaultLibraries();
        ExternalLibrary[] sulongLibraries = new ExternalLibrary[sulongLibraryNames.length];
        for (int i = 0; i < sulongLibraries.length; i++) {
            sulongLibraries[i] = context.addInternalLibrary(sulongLibraryNames[i], false);
        }

        // parse all libraries that were passed on the command-line
        List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(context.getEnv());
        for (String external : externals) {
            // assume that the library is a native one until we parsed it and can say for sure
            ExternalLibrary lib = context.addExternalLibrary(external, true, "<command line>");
            if (lib != null) {
                parse(parserResults, dependencyQueue, lib);
            }
        }

        // now parse the default Sulong libraries
        // TODO (chaeubl): we have an ordering issue here... - the search order for native
        // code comes last, which is not necessarily correct...
        LLVMParserResult[] sulongLibraryResults = new LLVMParserResult[sulongLibraries.length];
        for (int i = 0; i < sulongLibraries.length; i++) {
            sulongLibraryResults[i] = parse(parserResults, dependencyQueue, sulongLibraries[i]);
        }
        while (!dependencyQueue.isEmpty()) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib);
        }

        updateOverriddenSymbols(sulongLibraryResults);
        resolveRenamedSymbols(sulongLibraryResults);
        return sulongLibraries;
    }

    /**
     * @return The sulong default libraries, if any were parsed.
     */
    private ExternalLibrary[] parseDependencies(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue) {
        // at first, we are only parsing the direct dependencies of the main bitcode file
        int directDependencies = dependencyQueue.size();
        for (int i = 0; i < directDependencies; i++) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib);
        }

        // then, we are parsing the default libraries
        ExternalLibrary[] sulongLibraries = loader.getDefaultDependencies(this, parserResults);

        // finally we are dealing with all indirect dependencies
        while (!dependencyQueue.isEmpty()) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib);
        }
        return sulongLibraries;
    }

    private static void resolveRenamedSymbols(LLVMParserResult[] sulongLibraryResults) {
        EconomicMap<String, LLVMScope> scopes = EconomicMap.create();

        for (LLVMParserResult parserResult : sulongLibraryResults) {
            scopes.put(parserResult.getRuntime().getLibrary().getName(), parserResult.getRuntime().getFileScope());
        }

        for (LLVMParserResult parserResult : sulongLibraryResults) {
            ListIterator<FunctionSymbol> it = parserResult.getExternalFunctions().listIterator();
            while (it.hasNext()) {
                FunctionSymbol external = it.next();
                String name = external.getName();
                /*
                 * An unresolved name has the form "__libName_symbolName". Check whether we have a
                 * symbol named "symbolName" in the library "libName". If it exists, introduce an
                 * alias. This can be used to explicitly call symbols from a certain standard
                 * library, in case the symbol is hidden (either using the "hidden" attribute, or
                 * because it is overridden).
                 */
                if (name.startsWith("__")) {
                    int idx = name.indexOf('_', 2);
                    if (idx > 0) {
                        String lib = name.substring(2, idx);
                        LLVMScope scope = scopes.get(lib);
                        if (scope != null) {
                            String originalName = name.substring(idx + 1);
                            LLVMFunctionDescriptor originalSymbol = scope.getFunction(originalName);
                            LLVMAlias alias = new LLVMAlias(parserResult.getRuntime().getLibrary(), name, originalSymbol);
                            parserResult.getRuntime().getFileScope().register(alias);
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    private void updateOverriddenSymbols(LLVMParserResult[] sulongLibraryResults) {
        if (sulongLibraryResults.length > 1) {
            EconomicMap<LLVMSymbol, List<LLVMAlias>> usagesInAliases = computeUsagesInAliases(sulongLibraryResults);

            // the array elements are sorted from strong to weak
            LLVMParserResult strongerLib = sulongLibraryResults[0];
            for (int i = 1; i < sulongLibraryResults.length; i++) {
                LLVMParserResult weakerLib = sulongLibraryResults[i];
                overrideConflictingSymbols(weakerLib, strongerLib, usagesInAliases);
                weakerLib.getRuntime().getFileScope().addMissingEntries(strongerLib.getRuntime().getFileScope());
                strongerLib = weakerLib;
            }
        }
    }

    private static EconomicMap<LLVMSymbol, List<LLVMAlias>> computeUsagesInAliases(LLVMParserResult[] sulongLibraryResults) {
        EconomicMap<LLVMSymbol, List<LLVMAlias>> usages = EconomicMap.create();
        for (LLVMParserResult parserResult : sulongLibraryResults) {
            for (LLVMSymbol symbol : parserResult.getRuntime().getFileScope().values()) {
                if (symbol instanceof LLVMAlias) {
                    LLVMAlias alias = (LLVMAlias) symbol;
                    LLVMSymbol target = alias.getTarget();

                    List<LLVMAlias> aliases = usages.get(target);
                    if (aliases == null) {
                        aliases = new ArrayList<>();
                        usages.put(target, aliases);
                    }
                    aliases.add(alias);
                }
            }
        }
        return usages;
    }

    private void overrideConflictingSymbols(LLVMParserResult currentLib, LLVMParserResult strongerLib, EconomicMap<LLVMSymbol, List<LLVMAlias>> usagesInAliases) {
        LLVMScope globalScope = context.getGlobalScope();
        LLVMScope weakerScope = currentLib.getRuntime().getFileScope();
        LLVMScope strongerScope = strongerLib.getRuntime().getFileScope();

        for (LLVMSymbol strongerSymbol : strongerScope.values()) {
            String name = strongerSymbol.getName();
            LLVMSymbol weakerSymbol = weakerScope.get(name);
            if (weakerSymbol != null) {
                boolean shouldOverride = strongerSymbol.isFunction() || strongerSymbol.isGlobalVariable() && !strongerSymbol.asGlobalVariable().isReadOnly();
                if (shouldOverride) {
                    /*
                     * We already have a function with the same name in another (more important)
                     * library. We update the global scope and all aliases pointing to the weaker
                     * symbol to point to the stronger symbol instead.
                     */

                    // if the weaker symbol is exported, export the stronger symbol instead
                    if (globalScope.get(name) == weakerSymbol) {
                        globalScope.rename(name, strongerSymbol);
                    }

                    // modify all aliases that point to the weaker symbol
                    List<LLVMAlias> affectedAliases = usagesInAliases.get(weakerSymbol);
                    if (affectedAliases != null) {
                        for (LLVMAlias alias : affectedAliases) {
                            alias.setTarget(strongerSymbol);
                        }
                    }
                }
            }
        }
    }

    private LLVMParserResult parse(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, ExternalLibrary lib) {
        if (lib.getPath() == null || !lib.getPath().toFile().isFile()) {
            if (!lib.isNative()) {
                throw new LLVMParserException("'" + lib.getPath() + "' is not a file or does not exist.");
            } else {
                // lets assume that this is not a bitcode file and the NFI is going to handle it
                return null;
            }
        }

        Path path = lib.getPath();
        TruffleFile file = context.getEnv().getTruffleFile(path.toUri());
        Source source;
        try {
            source = Source.newBuilder("llvm", file).internal(lib.isInternal()).build();
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            throw new LLVMParserException("Error reading file " + path + ".");
        }
        return parse(parserResults, dependencyQueue, source, lib, source.getBytes());
    }

    private LLVMParserResult parse(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, Source source,
                    ExternalLibrary library, ByteSequence bytes) {
        BinaryParserResult binaryParserResult = BinaryParser.parse(bytes, source, context);
        if (binaryParserResult != null) {
            ModelModule module = new ModelModule();
            LLVMScanner.parseBitcode(binaryParserResult.getBitcode(), module, source, context);
            library.setIsNative(false);
            context.addLibraryPaths(binaryParserResult.getLibraryPaths());
            List<String> libraries = binaryParserResult.getLibraries();
            for (String lib : libraries) {
                ExternalLibrary dependency = context.addExternalLibrary(lib, true, library, binaryParserResult.getLocator());
                if (dependency != null) {
                    dependencyQueue.addLast(dependency);
                }
            }
            LLVMScope fileScope = new LLVMScope();
            LLVMParserRuntime runtime = new LLVMParserRuntime(context, library, fileScope);
            LLVMParser parser = new LLVMParser(source, runtime);
            LLVMParserResult parserResult = parser.parse(module);
            parserResults.add(parserResult);
            return parserResult;
        } else if (!library.isNative()) {
            throw new LLVMParserException("The file '" + source.getName() + "' is not a bitcode file nor an ELF or Mach-O object file with an embedded bitcode section.");
        } else {
            return null;
        }
    }

    private void addExternalSymbolsToScopes(List<LLVMParserResult> parserResults) {
        // TODO (chaeubl): in here, we should validate if the return type/argument type/global
        // types match
        LLVMScope globalScope = context.getGlobalScope();
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            for (FunctionSymbol function : parserResult.getExternalFunctions()) {
                LLVMSymbol globalSymbol = globalScope.get(function.getName());
                if (globalSymbol == null) {
                    globalSymbol = context.createFunctionDescriptor(function.getName(), function.getType());
                    globalScope.register(globalSymbol);
                } else if (!globalSymbol.isFunction()) {
                    assert globalSymbol.isGlobalVariable();
                    throw new LLVMLinkerException(
                                    "The function " + function.getName() + " is declared as external but its definition is shadowed by a conflicting global variable with the same name.");
                }

                // there can already be a different local entry in the file scope
                if (!fileScope.contains(function.getName())) {
                    fileScope.register(globalSymbol);
                }
            }

            for (GlobalVariable global : parserResult.getExternalGlobals()) {
                LLVMSymbol globalSymbol = globalScope.get(global.getName());
                if (globalSymbol == null) {
                    globalSymbol = LLVMGlobal.create(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
                    globalScope.register(globalSymbol);
                } else if (!globalSymbol.isGlobalVariable()) {
                    assert globalSymbol.isFunction();
                    throw new LLVMLinkerException("The global variable " + global.getName() + " is declared as external but its definition is shadowed by a conflicting function with the same name.");
                }

                // there can already be a different local entry in the file scope
                if (!fileScope.contains(global.getName())) {
                    fileScope.register(globalSymbol);
                }
            }
        }
    }

    private static void bindGlobal(LLVMContext ctx, LLVMGlobal global, NFIContextExtension nfiContextExtension) {
        if (nfiContextExtension != null) {
            NativePointerIntoLibrary pointerIntoLibrary = nfiContextExtension.getNativeHandle(ctx, global.getName());
            if (pointerIntoLibrary != null) {
                global.define(pointerIntoLibrary.getLibrary());
                global.setTarget(LLVMNativePointer.create(pointerIntoLibrary.getAddress()));
            }
        }

        if (!global.isDefined() && !ctx.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            throw new LLVMLinkerException("Global variable " + global.getName() + " is declared but not defined.");
        }
    }

    private static void bindUnresolvedFunction(LLVMContext ctx, LLVMFunctionDescriptor function, NFIContextExtension nfiContextExtension, LLVMIntrinsicProvider intrinsicProvider) {
        if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(function.getName())) {
            function.define(intrinsicProvider);
        } else if (nfiContextExtension != null) {
            NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(ctx, function.getName());
            if (nativeFunction != null) {
                function.define(nativeFunction.getLibrary(), new LLVMFunctionDescriptor.NativeFunction(nativeFunction.getObject()));
            }
        }
        // if we were unable to bind the function, then we will try another lookup when
        // someone tries to execute the function
    }

    private InitializationOrder computeInitializationOrder(List<LLVMParserResult> parserResults, ExternalLibrary[] defaultLibraries) {
        // Split libraries into Sulong-specific ones and others, so that we can handle the
        // Sulong-specific ones separately.
        List<LLVMParserResult> sulongLibs = new ArrayList<>();
        List<LLVMParserResult> otherLibs = new ArrayList<>();
        List<ExternalLibrary> sulongExternalLibraries = Arrays.asList(defaultLibraries);
        for (LLVMParserResult parserResult : parserResults) {
            if (sulongExternalLibraries.contains(parserResult.getRuntime().getLibrary())) {
                sulongLibs.add(parserResult);
            } else {
                otherLibs.add(parserResult);
            }
        }

        // Typically, the initialization order is very close to the reversed parsing order. So, we
        // only want to change the order when it is really necessary.
        List<LLVMParserResult> otherLibsInitializationOrder = new ArrayList<>();
        EconomicSet<LLVMParserResult> visited = EconomicSet.create(Equivalence.IDENTITY);
        EconomicMap<LLVMParserResult, List<LLVMParserResult>> dependencies = computeDependencies(otherLibs);
        for (int i = otherLibs.size() - 1; i >= 0; i--) {
            LLVMParserResult parserResult = otherLibs.get(i);
            if (!visited.contains(parserResult)) {
                addToInitializationOrder(parserResult, dependencies, otherLibsInitializationOrder, visited);
            }
        }

        assert sulongLibs.size() + otherLibsInitializationOrder.size() == parserResults.size();
        return new InitializationOrder(sulongLibs, otherLibsInitializationOrder);
    }

    private static void addToInitializationOrder(LLVMParserResult current, EconomicMap<LLVMParserResult, List<LLVMParserResult>> dependencies, List<LLVMParserResult> initializationOrder,
                    EconomicSet<LLVMParserResult> visited) {
        visited.add(current);
        List<LLVMParserResult> currentDependencies = dependencies.get(current);
        for (LLVMParserResult dependency : currentDependencies) {
            if (!visited.contains(dependency)) {
                addToInitializationOrder(dependency, dependencies, initializationOrder, visited);
            }
        }
        initializationOrder.add(current);
    }

    private EconomicMap<LLVMParserResult, List<LLVMParserResult>> computeDependencies(List<LLVMParserResult> parserResults) {
        EconomicMap<LLVMParserResult, List<LLVMParserResult>> dependencies = EconomicMap.create(Equivalence.IDENTITY);
        Map<ExternalLibrary, LLVMParserResult> libsToParserResults = mapLibsToParserResults(parserResults);
        LLVMScope globalScope = context.getGlobalScope();
        for (LLVMParserResult parserResult : parserResults) {
            List<LLVMParserResult> currentDependencies = new ArrayList<>();
            for (ExternalLibrary lib : getImportedLibraries(globalScope, parserResult)) {
                // ignore self imports
                if (!parserResult.getRuntime().getLibrary().equals(lib)) {
                    LLVMParserResult dependency = libsToParserResults.get(lib);
                    if (dependency != null) {
                        currentDependencies.add(dependency);
                    }
                }
            }
            dependencies.put(parserResult, currentDependencies);
        }
        return dependencies;
    }

    private static EconomicSet<ExternalLibrary> getImportedLibraries(LLVMScope globalScope, LLVMParserResult parserResult) {
        EconomicSet<ExternalLibrary> importedLibs = EconomicSet.create(Equivalence.IDENTITY);
        for (String imported : parserResult.getImportedSymbols()) {
            ExternalLibrary lib = globalScope.get(imported).getLibrary();
            if (lib != null) {
                importedLibs.add(lib);
            }
        }
        return importedLibs;
    }

    private static Map<ExternalLibrary, LLVMParserResult> mapLibsToParserResults(List<LLVMParserResult> parserResults) {
        Map<ExternalLibrary, LLVMParserResult> map = new HashMap<>();
        for (LLVMParserResult parserResult : parserResults) {
            map.put(parserResult.getRuntime().getLibrary(), parserResult);
        }
        return map;
    }

    private static final class StaticInitsNode extends LLVMStatementNode {

        @Children final LLVMStatementNode[] statements;

        StaticInitsNode(LLVMStatementNode[] statements) {
            this.statements = statements;
        }

        @ExplodeLoop
        @Override
        public void execute(VirtualFrame frame) {
            for (LLVMStatementNode stmt : statements) {
                stmt.execute(frame);
            }
        }
    }

    private static final class InitializeModuleNode extends LLVMNode {

        private final RootCallTarget destructor;

        @Child StaticInitsNode globalVarInit;
        @Child LLVMMemoryOpNode protectRoData;

        @Child StaticInitsNode constructor;

        InitializeModuleNode(Runner runner, FrameDescriptor rootFrame, LLVMParserResult parserResult) {
            this.destructor = runner.createDestructor(parserResult);

            this.globalVarInit = runner.createGlobalVariableInitializer(rootFrame, parserResult);
            this.protectRoData = runner.language.getNodeFactory().createProtectGlobalsBlock();
            this.constructor = runner.createConstructor(parserResult);
        }

        void execute(VirtualFrame frame, LLVMContext ctx, LLVMPointer roDataBase) {
            if (destructor != null) {
                ctx.registerDestructorFunctions(destructor);
            }
            globalVarInit.execute(frame);
            if (roDataBase != null) {
                // TODO could be a compile-time check
                protectRoData.execute(roDataBase);
            }
            constructor.execute(frame);
        }
    }

    private StaticInitsNode createGlobalVariableInitializer(FrameDescriptor rootFrame, LLVMParserResult parserResult) {
        LLVMParserRuntime runtime = parserResult.getRuntime();
        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, rootFrame, GetStackSpaceFactory.createAllocaFactory());
        final List<LLVMStatementNode> globalNodes = new ArrayList<>();
        for (GlobalVariable global : parserResult.getDefinedGlobals()) {
            final LLVMStatementNode store = createGlobalInitialization(runtime, symbolResolver, global);
            if (store != null) {
                globalNodes.add(store);
            }
        }
        LLVMStatementNode[] initNodes = globalNodes.toArray(LLVMStatementNode.NO_STATEMENTS);
        return new StaticInitsNode(initNodes);
    }

    private LLVMStatementNode createGlobalInitialization(LLVMParserRuntime runtime, LLVMSymbolReadResolver symbolResolver, GlobalVariable global) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            final Type type = global.getType().getPointeeType();
            final int size = context.getByteSize(type);

            // for fetching the address of the global that we want to initialize, we must use the
            // file scope because we are initializing the globals of the current file
            LLVMGlobal globalDescriptor = runtime.getFileScope().getGlobalVariable(global.getName());
            final LLVMExpressionNode globalVarAddress = language.getNodeFactory().createLiteral(globalDescriptor, new PointerType(global.getType()));
            if (size != 0) {
                if (type instanceof ArrayType || type instanceof StructureType) {
                    return language.getNodeFactory().createStore(globalVarAddress, constant, type, null);
                } else {
                    Type t = global.getValue().getType();
                    return language.getNodeFactory().createStore(globalVarAddress, constant, t, null);
                }
            }
        }

        return null;
    }

    private StaticInitsNode createConstructor(LLVMParserResult parserResult) {
        return new StaticInitsNode(createStructor(CONSTRUCTORS_VARNAME, parserResult, ASCENDING_PRIORITY));
    }

    private RootCallTarget createDestructor(LLVMParserResult parserResult) {
        LLVMStatementNode[] destructor = createStructor(DESTRUCTORS_VARNAME, parserResult, DESCENDING_PRIORITY);
        if (destructor.length > 0) {
            LLVMStatementRootNode root = new LLVMStatementRootNode(language, new StaticInitsNode(destructor), StackManager.createRootFrame());
            return Truffle.getRuntime().createCallTarget(root);
        } else {
            return null;
        }
    }

    private LLVMStatementNode[] createStructor(String name, LLVMParserResult parserResult, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalVariable globalVariable : parserResult.getDefinedGlobals()) {
            if (globalVariable.getName().equals(name)) {
                return resolveStructor(parserResult.getRuntime().getFileScope(), globalVariable, priorityComparator);
            }
        }
        return LLVMStatementNode.NO_STATEMENTS;
    }

    private LLVMStatementNode[] resolveStructor(LLVMScope fileScope, GlobalVariable globalSymbol, Comparator<Pair<Integer, ?>> priorityComparator) {
        if (!(globalSymbol.getValue() instanceof ArrayConstant)) {
            // array globals of length 0 may be initialized with scalar null
            return LLVMStatementNode.NO_STATEMENTS;
        }

        final LLVMGlobal global = (LLVMGlobal) fileScope.get(globalSymbol.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalSymbol.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int elementSize = context.getByteSize(elementType);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = context.getByteAlignment(functionType);

        final ArrayList<Pair<Integer, LLVMStatementNode>> structors = new ArrayList<>(elemCount);
        FrameDescriptor rootFrame = StackManager.createRootFrame();
        for (int i = 0; i < elemCount; i++) {
            final LLVMExpressionNode globalVarAddress = language.getNodeFactory().createLiteral(global, new PointerType(globalSymbol.getType()));
            final LLVMExpressionNode iNode = language.getNodeFactory().createLiteral(i, PrimitiveType.I32);
            final LLVMExpressionNode structPointer = language.getNodeFactory().createTypedElementPointer(globalVarAddress, iNode, elementSize, elementType);
            final LLVMExpressionNode loadedStruct = language.getNodeFactory().createLoad(elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = language.getNodeFactory().createLiteral(1, PrimitiveType.I32);
            final LLVMExpressionNode functionLoadTarget = language.getNodeFactory().createTypedElementPointer(loadedStruct, oneLiteralNode, indexedTypeLength, functionType);
            final LLVMExpressionNode loadedFunction = language.getNodeFactory().createLoad(functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{
                            language.getNodeFactory().createFrameRead(PointerType.VOID, rootFrame.findFrameSlot(LLVMStack.FRAME_ID))};
            final LLVMStatementNode functionCall = LLVMVoidStatementNodeGen.create(language.getNodeFactory().createFunctionCall(loadedFunction, argNodes, functionType, null));

            final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
            final SymbolImpl prioritySymbol = structorDefinition.getElement(0);
            final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMStatementNode[]::new);
    }

    private static byte[] decodeBase64(CharSequence charSequence) {
        byte[] result = new byte[charSequence.length()];
        for (int i = 0; i < result.length; i++) {
            char ch = charSequence.charAt(i);
            assert ch >= 0 && ch <= Byte.MAX_VALUE;
            result[i] = (byte) ch;
        }
        return Base64.getDecoder().decode(result);
    }

    private CallTarget createLibraryCallTarget(String name, List<LLVMParserResult> parserResults, InitializationOrder initializationOrder) {
        RootCallTarget mainFunctionCallTarget = null;
        LLVMFunctionDescriptor mainFunctionDescriptor = findMainMethod(parserResults);
        LLVMFunctionDescriptor startFunctionDescriptor = findStartMethod();
        if (mainFunctionDescriptor != null && startFunctionDescriptor != null) {
            RootCallTarget startCallTarget = startFunctionDescriptor.getLLVMIRFunction();
            Path applicationPath = mainFunctionDescriptor.getLibrary().getPath();
            RootNode rootNode = new LLVMGlobalRootNode(language, StackManager.createRootFrame(), mainFunctionDescriptor, startCallTarget, Objects.toString(applicationPath, ""));
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(rootNode);
        }

        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            LLVMScope scope = combineScopes(parserResults);
            SulongLibrary lib = new SulongLibrary(name, scope, mainFunctionCallTarget);

            FrameDescriptor rootFrame = StackManager.createRootFrame();
            LoadModulesNode loadModules = new LoadModulesNode(this, rootFrame, initializationOrder, lib);
            return Truffle.getRuntime().createCallTarget(loadModules);
        }
    }

    private LLVMFunctionDescriptor findMainMethod(List<LLVMParserResult> parserResults) {
        // check if the freshly parsed code exports a main method
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            if (fileScope.exports(context, MAIN_METHOD_NAME)) {
                LLVMSymbol mainMethod = fileScope.get(MAIN_METHOD_NAME);
                if (mainMethod.isFunction() && mainMethod.isDefined() && mainMethod.asFunction().isLLVMIRFunction()) {
                    /*
                     * The `isLLVMIRFunction` check makes sure the `main` function is really defined
                     * in bitcode. This prevents us from finding a native `main` function (e.g. the
                     * `main` of the VM we're running in).
                     */
                    return mainMethod.asFunction();
                }
            }
        }
        return null;
    }

    private LLVMFunctionDescriptor findStartMethod() {
        // the start method just needs to be present in the global scope, we don't care when it was
        // parsed.
        LLVMSymbol startMethod = context.getGlobalScope().get(START_METHOD_NAME);
        if (startMethod != null && startMethod.isFunction() && startMethod.isDefined()) {
            return startMethod.asFunction();
        }
        return null;
    }

    private static LLVMScope combineScopes(List<LLVMParserResult> parserResults) {
        LLVMScope result = new LLVMScope();
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope scope = parserResult.getRuntime().getFileScope();
            result.addMissingEntries(scope);
        }
        return result;
    }

    private void overrideSulongLibraryFunctionsWithIntrinsics(List<LLVMParserResult> sulongLibraries) {
        LLVMIntrinsicProvider intrinsicProvider = language.getContextExtensionOrNull(LLVMIntrinsicProvider.class);
        if (intrinsicProvider != null) {
            for (LLVMParserResult parserResult : sulongLibraries) {
                for (LLVMSymbol symbol : parserResult.getRuntime().getFileScope().values()) {
                    if (symbol.isFunction() && intrinsicProvider.isIntrinsified(symbol.getName())) {
                        if (symbol instanceof LLVMAlias) {
                            throw new UnsupportedOperationException("Replacing an alias with an intrinsic is not supported at the moment");
                        } else if (symbol instanceof LLVMFunctionDescriptor) {
                            LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) symbol;
                            function.define(intrinsicProvider);
                        } else {
                            throw new IllegalStateException("Unknown symbol: " + symbol.getClass());
                        }
                    }
                }
            }
        }
    }

    private void parseFunctionsEagerly(List<LLVMParserResult> parserResults) {
        if (!context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING)) {
            for (LLVMParserResult parserResult : parserResults) {
                for (LLVMSymbol symbol : parserResult.getRuntime().getFileScope().values()) {
                    if (symbol instanceof LLVMFunctionDescriptor) {
                        LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) symbol;
                        function.resolveIfLazyLLVMIRFunction();
                    } else if (symbol instanceof LLVMGlobal || symbol instanceof LLVMAlias) {
                        // nothing to do
                    } else {
                        throw new RuntimeException("Unknown symbol: " + symbol.getClass());
                    }
                }
            }
        }
    }

    private static final class InitializationOrder {
        private final List<LLVMParserResult> sulongLibraries;
        private final List<LLVMParserResult> otherLibraries;

        private InitializationOrder(List<LLVMParserResult> sulongLibraries, List<LLVMParserResult> otherLibraries) {
            this.sulongLibraries = sulongLibraries;
            this.otherLibraries = otherLibraries;
        }
    }

    private static final class ParserInput {
        private final ByteSequence bytes;
        private final ExternalLibrary library;

        private ParserInput(ByteSequence bytes, ExternalLibrary library) {
            this.bytes = bytes;
            this.library = library;
        }
    }
}
