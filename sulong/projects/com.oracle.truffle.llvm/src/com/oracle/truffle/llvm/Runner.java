/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.CONSTRUCTORS_VARNAME;
import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.DESTRUCTORS_VARNAME;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.RunnerFactory.AllocExternalFunctionNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.AllocExternalGlobalNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.StaticInitsNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.StackManager;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import com.oracle.truffle.llvm.parser.model.GlobalSymbol;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LazyLLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.SulongLibrary;
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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMVoidStatementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMCheckSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMCheckSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMStatementRootNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNodeGen;
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
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

/**
 * Drives a parsing request.
 *
 * @see #parse
 */
final class Runner {

    /**
     * Parses a {@code source} and all its (explicit and implicit) dependencies.
     *
     * @return a {@link CallTarget} that on execute initializes (i.e., initalize globals, run
     *         constructors, etc.) the module represented by {@code source} and all dependencies.
     */
    public static CallTarget parse(LLVMContext context, DefaultLoader loader, AtomicInteger id, Source source) {
        return new Runner(context, loader, id).parseWithDependencies(source);
    }

    public static void loadDefaults(LLVMContext context, DefaultLoader loader, AtomicInteger id, Path internalLibraryPath) {
        new Runner(context, loader, id).loadDefaults(internalLibraryPath);
    }

    private static final String MAIN_METHOD_NAME = "main";
    private static final String START_METHOD_NAME = "_start";

    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() - p2.getFirst();
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p2.getFirst() - p1.getFirst();

    private final LLVMContext context;
    private final DefaultLoader loader;
    private final LLVMLanguage language;
    private final AtomicInteger id;
    private final LLVMLocalScope localScope;
    private final LLVMLocalScope localScopeTmp;

    private Runner(LLVMContext context, DefaultLoader loader, AtomicInteger id) {
        this.context = context;
        this.loader = loader;
        this.language = context.getLanguage();
        this.id = id;
        this.localScope = new LLVMLocalScope();
        this.localScopeTmp = new LLVMLocalScope();
        this.context.addLocalScopeTmp(localScopeTmp);
        this.context.addLocalScope(localScope);
    }

    /**
     * Parse bitcode data and do first initializations to prepare bitcode execution.
     */
    private CallTarget parseWithDependencies(Source source) {
        ByteSequence bytes;
        ExternalLibrary library;
        if (source.hasBytes()) {
            bytes = source.getBytes();
            if (source.getPath() != null) {
                library = ExternalLibrary.createFromFile(context.getEnv().getInternalTruffleFile(source.getPath()), false, source.isInternal());
            } else {
                library = ExternalLibrary.createFromName("<STREAM-" + UUID.randomUUID().toString() + ">", false, source.isInternal());
            }
        } else if (source.hasCharacters()) {
            switch (source.getMimeType()) {
                case LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE:
                    bytes = ByteSequence.create(decodeBase64(source.getCharacters()));
                    library = ExternalLibrary.createFromName("<STREAM-" + UUID.randomUUID().toString() + ">", false, source.isInternal());
                    break;
                default:
                    throw new LLVMParserException("Character-based source with unexpected mime type: " + source.getMimeType());
            }
        } else {
            throw new LLVMParserException("Should not reach here: Source is neither char-based nor byte-based!");
        }
        return parseWithDependencies(source, bytes, library);
    }

    /**
     * {@link InitializeSymbolsNode} creates the symbol of all defined functions and globals, and
     * put them into the symbol table. {@link InitializeGlobalNode} initializes the value of all
     * defined global symbols.
     *
     * {@link InitializeExternalNode} initializes the symbol table for all the external symbols of
     * this module. For external functions, if they are already defined in the local scope or the
     * global scope, then the already defined symbol is placed into this function's spot in the
     * symbol table. Otherwise, an instrinc or native function is created if they exists. Similarly,
     * for external globals the local and global scope is checked first for this external global,
     * and if it exists, then the defined global symbol from the local/global scope is placed into
     * this external global's location in the symbol table.
     *
     * The aim of {@link InitializeOverwriteNode} is to identify which defined symbols will be
     * resolved to their corresponding symbol in the local scope when they are called. If they
     * resolve to the symbol in the local scope then this symbol from the local scope is place into
     * this defined symbol's location in the symbol table. This means the local and global scope is
     * no longer required for symbol resolution, and everything is done simply by looking up the
     * symbol in the file scope.
     */
    private static final class LoadModulesNode extends RootNode {

        final SulongLibrary sulongLibrary;
        final FrameSlot stackPointerSlot;
        @CompilationFinal ContextReference<LLVMContext> ctxRef;

        @Child LLVMStatementNode initContext;

        @Children final InitializeSymbolsNode[] initSymbols;
        @Children final InitializeScopeNode[] initScopes;
        @Children final InitializeExternalNode[] initExternals;
        @Children final InitializeGlobalNode[] initGlobals;
        @Children final InitializeOverwriteNode[] initOverwrite;
        @Children final InitializeModuleNode[] initModules;

        private LoadModulesNode(Runner runner, FrameDescriptor rootFrame, InitializationOrder order, SulongLibrary sulongLibrary) {
            super(runner.language, rootFrame);
            this.sulongLibrary = sulongLibrary;
            this.stackPointerSlot = rootFrame.findFrameSlot(LLVMStack.FRAME_ID);
            this.initContext = runner.context.createInitializeContextNode(rootFrame);
            int libCount = order.sulongLibraries.size() + order.otherLibraries.size();
            this.initSymbols = new InitializeSymbolsNode[libCount];
            this.initScopes = new InitializeScopeNode[libCount];
            this.initExternals = new InitializeExternalNode[libCount];
            this.initGlobals = new InitializeGlobalNode[libCount];
            this.initOverwrite = new InitializeOverwriteNode[libCount];
            this.initModules = new InitializeModuleNode[libCount];
        }

        static LoadModulesNode create(Runner runner, FrameDescriptor rootFrame, InitializationOrder order, SulongLibrary sulongLibrary, boolean lazyParsing) {
            LoadModulesNode node = new LoadModulesNode(runner, rootFrame, order, sulongLibrary);
            try {
                createNodes(runner, rootFrame, order.sulongLibraries, 0, node.initSymbols, node.initScopes, node.initOverwrite, node.initExternals, node.initGlobals, node.initModules, lazyParsing,
                                true, order.originalOtherLibraries);
                createNodes(runner, rootFrame, order.otherLibraries, order.sulongLibraries.size(), node.initSymbols, node.initScopes, node.initOverwrite, node.initExternals, node.initGlobals,
                                node.initModules,
                                lazyParsing, false, order.originalOtherLibraries);
                return node;
            } catch (TypeOverflowException e) {
                throw new LLVMUnsupportedException(node, UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
            }
        }

        private static void createNodes(Runner runner, FrameDescriptor rootFrame, List<LLVMParserResult> parserResults, int offset, InitializeSymbolsNode[] initSymbols,
                        InitializeScopeNode[] initScopes,
                        InitializeOverwriteNode[] initOverwrite, InitializeExternalNode[] initExternals,
                        InitializeGlobalNode[] initGlobals, InitializeModuleNode[] initModules, boolean lazyParsing, boolean isSulongLibrary, List<LLVMParserResult> originalParserResults)
                        throws TypeOverflowException {
            for (int i = 0; i < parserResults.size(); i++) {
                LLVMParserResult res = parserResults.get(i);
                Object moduleName = res.getRuntime().getLibrary().toString();
                initSymbols[offset + i] = new InitializeSymbolsNode(res, res.getRuntime().getNodeFactory(), lazyParsing, isSulongLibrary, moduleName);
                if (isSulongLibrary) {
                    initScopes[offset + i] = new InitializeScopeNode(res);
                } else {
                    initScopes[offset + i] = new InitializeScopeNode(originalParserResults.get(i));
                }
                initExternals[offset + i] = new InitializeExternalNode(res);
                initGlobals[offset + i] = new InitializeGlobalNode(rootFrame, res, moduleName);
                initOverwrite[offset + i] = new InitializeOverwriteNode(res, runner.localScope);
                initModules[offset + i] = new InitializeModuleNode(runner, res, moduleName);
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (ctxRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.ctxRef = lookupContextReference(LLVMLanguage.class);
            }

            LLVMContext ctx = ctxRef.get();
            try (StackPointer stackPointer = ctxRef.get().getThreadingStack().getStack().newFrame()) {
                frame.setObject(stackPointerSlot, stackPointer);

                BitSet shouldInit = createBitset();
                LLVMPointer[] roSections = new LLVMPointer[initSymbols.length];

                /*
                 * The ordering of executing these four initialization nodes is very important. They
                 * must be in this particular order. The defined symbols and the external symbols
                 * must be initialized before (the value in) the global symbols can be initialized.
                 * The overwriting of symbols can only be done once all the globals are initialised
                 * and allocated in the symbol table.
                 */
                doInitSymbols(ctx, shouldInit, roSections);
                doInitScope(ctx, shouldInit);
                // ctx.printCheckGlobalScope();
                ctx.assertCheckGlobalScope();
                // ctx.printCheckLocalScope();
                ctx.assertCheckLocalScope();
                doInitExternal(ctx, shouldInit);
                doInitGlobals(frame, shouldInit, roSections);
                doInitOverwrite(ctx, shouldInit);

                initContext.execute(frame);
                doInitModules(frame, ctx, shouldInit);
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
                    initSymbols[i].initializeSymbolTable(ctx);
                }
            }
            for (int i = 0; i < initSymbols.length; i++) {
                // Only execute the symbols that are initialized into the symbol table.
                if (shouldInit.get(i)) {
                    roSections[i] = initSymbols[i].execute(ctx);
                }
            }
        }

        @ExplodeLoop
        private void doInitScope(LLVMContext ctx, BitSet shouldInit) {
            for (int i = 0; i < initScopes.length; i++) {
                if (shouldInit.get(i)) {
                    initScopes[i].execute(ctx);
                }
            }
        }

        @ExplodeLoop
        private void doInitExternal(LLVMContext ctx, BitSet shouldInit) {
            for (int i = 0; i < initExternals.length; i++) {
                if (shouldInit.get(i)) {
                    initExternals[i].execute(ctx);
                }
            }
        }

        @ExplodeLoop
        private void doInitGlobals(VirtualFrame frame, BitSet shouldInit, LLVMPointer[] roSections) {
            for (int i = 0; i < initGlobals.length; i++) {
                if (shouldInit.get(i)) {
                    initGlobals[i].execute(frame, roSections[i]);
                }
            }
        }

        @ExplodeLoop
        private void doInitOverwrite(LLVMContext ctx, BitSet shouldInit) {
            for (int i = 0; i < initOverwrite.length; i++) {
                if (shouldInit.get(i)) {
                    initOverwrite[i].execute(ctx);
                }
            }
        }

        @ExplodeLoop
        private void doInitModules(VirtualFrame frame, LLVMContext ctx, BitSet shouldInit) {
            for (int i = 0; i < initModules.length; i++) {
                if (shouldInit.get(i)) {
                    initModules[i].execute(frame, ctx);
                }
            }
        }
    }

    /**
     * Parses a bitcode module and all its dependencies and return a {@code CallTarget} that
     * performs all necessary module initialization on execute.
     */
    private CallTarget parseWithDependencies(Source source, ByteSequence bytes, ExternalLibrary library) {
        // process the bitcode file and its dependencies in the dynamic linking order
        // (breadth-first)
        ParseContext parseContext = ParseContext.create();
        parseLibraryWithSource(source, library, bytes, parseContext);
        assert !library.isNative() && !parseContext.parserResultsIsEmpty();

        ExternalLibrary[] sulongLibraries = parseDependencies(parseContext);
        assert parseContext.dependencyQueueIsEmpty();

        for (LLVMParserResult parserResult : parseContext.getParserResults()) {
            if (context.isInternalLibrary(parserResult.getRuntime().getLibrary())) {
                // renaming is attempted only for internal libraries.
                resolveRenamedSymbols(parserResult, parseContext);
            }
        }

        List<LLVMParserResult> parserResults = parseContext.getParserResults();
        addExternalSymbolsToScopes(parserResults);

        InitializationOrder initializationOrder = computeInitializationOrder(parserResults, sulongLibraries);

        return createLibraryCallTarget(source.getName(), parserResults, initializationOrder);
    }

    private static final class AllocScopeNode extends LLVMNode {

        static final AllocScopeNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocScopeNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        void allocateScope(LLVMContext context, int ID) {
            LLVMScope globalScope = context.getGlobalScopeTmp();
            LLVMSymbol exportedSymbol = globalScope.get(symbol.getName());
            if (exportedSymbol == null && symbol.isExported()) {
                globalScope.register(symbol);
            }
            LLVMLocalScope localScope = context.getLocalScopeTmp(ID);
            LLVMSymbol exportedSymbolFromLocal = localScope.get(symbol.getName());
            if (exportedSymbolFromLocal == null && symbol.isExported()) {
                localScope.register(symbol);
            }
        }
    }

    abstract static class AllocExternalSymbolNode extends LLVMNode {

        @SuppressWarnings("unused") static final AllocExternalSymbolNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocExternalSymbolNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);
    }

    abstract static class AllocExternalFunctionNode extends AllocExternalSymbolNode {

        private final NodeFactory nodeFactory;

        AllocExternalFunctionNode(LLVMSymbol symbol, NodeFactory nodeFactory) {
            super(symbol);
            this.nodeFactory = nodeFactory;
        }

        @Specialization(guards = {"cachedLocalSymbol != null", "localScope.get(symbol.getName()) == cachedLocalSymbol"})
        LLVMPointer allocateFromLocalScopeCached(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension,
                        @SuppressWarnings("unused") @Cached("localScope.get(symbol.getName())") LLVMSymbol cachedLocalSymbol,
                        @Cached("create(cachedLocalSymbol)") LLVMAccessSymbolNode accessSymbol,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMPointer pointer = accessSymbol.execute();
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @Specialization(replaces = "allocateFromLocalScopeCached", guards = "localScope.get(symbol.getName()) != null")
        LLVMPointer allocateFromLocalScope(LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMSymbol function = localScope.get(symbol.getName());
            while (function.isAlias()) {
                function = ((LLVMAlias) function).getTarget();
            }
            AssumedValue<LLVMPointer>[] symbolTable = context.findSymbolTable(function.getBitcodeID(false));
            LLVMPointer pointer = symbolTable[function.getSymbolIndex(false)].get();
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @Specialization(guards = {"localScope.get(symbol.getName()) == null", "cachedGlobalSymbol != null", "globalScope.get(symbol.getName()) == cachedGlobalSymbol"})
        LLVMPointer allocateFromGlobalScopeCached(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension,
                        @SuppressWarnings("unused") @Cached("globalScope.get(symbol.getName())") LLVMSymbol cachedGlobalSymbol,
                        @Cached("create(cachedGlobalSymbol)") LLVMAccessSymbolNode accessSymbol,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMPointer pointer = accessSymbol.execute();
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @Specialization(replaces = "allocateFromGlobalScopeCached", guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) != null"})
        LLVMPointer allocateFromGlobalScope(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMSymbol function = globalScope.get(symbol.getName());
            assert function.isFunction();
            while (function.isAlias()) {
                function = ((LLVMAlias) function).getTarget();
            }
            AssumedValue<LLVMPointer>[] symbolTable = context.findSymbolTable(function.getBitcodeID(false));
            LLVMPointer pointer = symbolTable[function.getSymbolIndex(false)].get();
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                        "!symbol.isDefined()", "intrinsicProvider.isIntrinsified(symbol.getName())", "symbol.isFunction()"})
        LLVMPointer allocateIntrinsicFunction(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction());
            functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
            return LLVMManagedPointer.create(functionDescriptor);
        }

        @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                        "!symbol.isDefined()", "!intrinsicProvider.isIntrinsified(symbol.getName())", "nfiContextExtension != null",
                        "symbol.isFunction()"})
        LLVMPointer allocateNativeFunction(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        NFIContextExtension nfiContextExtension,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(context, symbol.getName());
            if (nativeFunction != null) {
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction());
                functionDescriptor.getFunctionCode().define(nativeFunction.getLibrary(), new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                return LLVMManagedPointer.create(functionDescriptor);
            }
            return null;
        }

        /*
         * Currently native functions/globals that are not in the nfi context are not written into
         * the symbol table. For function, another lookup will happen when something tries to call
         * the function. (see doCachedNative in LLVMDispatchNode) The function will be taken from
         * the filescope directly. Ideally the filescope and symbol table is in sync, and any lazy
         * look up will resolve from the function code in the symbol table.
         */
        @Fallback
        LLVMPointer allocateNativeFunctionFallback(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension) {
            return null;
        }

        @Override
        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);

    }

    abstract static class AllocExternalGlobalNode extends AllocExternalFunctionNode {

        AllocExternalGlobalNode(LLVMSymbol symbol, NodeFactory nodeFactory) {
            super(symbol, nodeFactory);
        }

        @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                        "!symbol.isDefined()", "!intrinsicProvider.isIntrinsified(symbol.getName())", "nfiContextExtension != null",
                        "symbol.isGlobalVariable()"})
        LLVMPointer allocateNativeGlobal(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        NFIContextExtension nfiContextExtension,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            NativePointerIntoLibrary pointer = nfiContextExtension.getNativeHandle(context, symbol.getName());
            if (pointer != null) {
                if (!symbol.isDefined()) {
                    symbol.asGlobalVariable().define(pointer.getLibrary());
                }
                return LLVMNativePointer.create(pointer.getAddress());
            }
            return null;
        }

        @Override
        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);

    }

    private abstract static class AllocSymbolNode extends LLVMNode {

        static final AllocSymbolNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocSymbolNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        abstract LLVMPointer allocate(LLVMContext context);
    }

    /*
     * Allocation for internal functions, they can either be regular LLVM bitcode function, eager
     * LLVM bitcode function, and intrinsic function.
     *
     */
    private static final class AllocLLVMFunctionNode extends AllocSymbolNode {

        AllocLLVMFunctionNode(LLVMFunction function) {
            super(function);
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndResolve(LLVMContext context) {
            return context.createFunctionDescriptor(symbol.asFunction());
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndResolve(context);
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    private static final class AllocLLVMEagerFunctionNode extends AllocSymbolNode {

        AllocLLVMEagerFunctionNode(LLVMFunction function) {
            super(function);
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndResolve(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction());
            functionDescriptor.getFunctionCode().resolveIfLazyLLVMIRFunction();
            return functionDescriptor;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndResolve(context);
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    private static final class AllocExistingSymbolNode extends AllocSymbolNode {

        @Child LLVMAccessSymbolNode accessSymbol;

        AllocExistingSymbolNode(LLVMSymbol symbol, LLVMAccessSymbolNode accessSymbol) {
            super(symbol);
            this.accessSymbol = accessSymbol;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMPointer pointer = accessSymbol.execute();
            context.registerSymbol(symbol, pointer);
            return pointer;
        }
    }

    private static final class AllocIntrinsicFunctionNode extends AllocSymbolNode {

        private NodeFactory nodeFactory;
        LLVMIntrinsicProvider intrinsicProvider;

        AllocIntrinsicFunctionNode(LLVMFunction function, NodeFactory nodeFactory, LLVMIntrinsicProvider intrinsicProvider) {
            super(function);
            this.nodeFactory = nodeFactory;
            this.intrinsicProvider = intrinsicProvider;
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndDefine(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction());
            if (intrinsicProvider.isIntrinsified(symbol.getName())) {
                functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                return functionDescriptor;
            }
            throw new IllegalStateException("Failed to allocate intrinsic function " + symbol.getName());
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndDefine(context);
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    /*
     * Allocates a managed pointer for the newly constructed function descriptors of a native
     * function.
     */
    @SuppressWarnings("unused")
    private static final class AllocNativeFunctionNode extends AllocSymbolNode {

        @SuppressWarnings("unused")
        AllocNativeFunctionNode(LLVMFunction function) {
            super(function);
        }

        @TruffleBoundary
        LLVMFunctionDescriptor createAndDefine(LLVMContext context) {
            NFIContextExtension nfiContextExtension = context.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);
            if (nfiContextExtension == null) {
                throw new IllegalStateException("NFIContextExtension is null for function: " + symbol.getName());
            }
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction());
            NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(context, symbol.getName());

            if (nativeFunction != null) {
                functionDescriptor.getFunctionCode().define(nativeFunction.getLibrary(), new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                return functionDescriptor;
            }
            // null is returned for native functions that does not exists in the NFI context.
            return null;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndDefine(context);
            if (functionDescriptor == null) {
                // null is propagated back up when the native function does not exists in the NFI
                // context.
                return null;
            }
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    /*
     * Allocates a native pointer for the newly constructed global symbol of a native global.
     */
    @SuppressWarnings("unused")
    private static final class AllocNativeGlobalNode extends AllocSymbolNode {

        @SuppressWarnings("unused")
        AllocNativeGlobalNode(LLVMGlobal global) {
            super(global);
        }

        @TruffleBoundary
        NativePointerIntoLibrary createAndDefine(LLVMContext context) {
            NFIContextExtension nfiContextExtension = context.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);
            if (nfiContextExtension == null) {
                throw new IllegalStateException("NFIContextExtension is null for function: " + symbol.getName());
            }
            LLVMGlobal global = symbol.asGlobalVariable();
            NativePointerIntoLibrary pointer = nfiContextExtension.getNativeHandle(context, global.getName());
            if (pointer != null) {
                if (!symbol.isDefined()) {
                    symbol.asGlobalVariable().define(pointer.getLibrary());
                }
                return pointer;
            }
            // null is returned for native globals that does not exists in the NFI context.
            return null;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            NativePointerIntoLibrary pointer = createAndDefine(context);
            if (pointer == null) {
                // null is propagated back up when the native global does not exists in the NFI
                // context.
                return null;
            }
            return LLVMNativePointer.create(pointer.getAddress());
        }
    }

    private abstract static class AllocGlobalNode extends LLVMNode {

        static final AllocGlobalNode[] EMPTY = {};

        final String name;

        AllocGlobalNode(GlobalVariable global) {
            this.name = global.getName();
        }

        abstract LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase);

        @Override
        public String toString() {
            return "AllocGlobal: " + name;
        }
    }

    private static final class AllocPointerGlobalNode extends AllocGlobalNode {

        AllocPointerGlobalNode(GlobalVariable global) {
            super(global);
        }

        @Override
        LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
            return LLVMManagedPointer.create(new LLVMGlobalContainer());
        }
    }

    private static final class AllocOtherGlobalNode extends AllocGlobalNode {

        final boolean readOnly;
        final long offset;

        AllocOtherGlobalNode(GlobalVariable global, Type type, DataSection roSection, DataSection rwSection) throws TypeOverflowException {
            super(global);
            this.readOnly = global.isReadOnly();

            DataSection dataSection = readOnly ? roSection : rwSection;
            this.offset = dataSection.add(global, type);
        }

        @Override
        LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
            LLVMPointer base = readOnly ? roBase : rwBase;
            return base.increment(offset);
        }
    }

    private static final class DataSection {

        final DataLayout dataLayout;
        final ArrayList<Type> types = new ArrayList<>();

        private long offset = 0;

        DataSection(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
        }

        long add(GlobalVariable global, Type type) throws TypeOverflowException {
            int alignment = getAlignment(dataLayout, global, type);
            int padding = Type.getPadding(offset, alignment);
            addPaddingTypes(types, padding);
            offset = Type.addUnsignedExact(offset, padding);
            long ret = offset;
            types.add(type);
            offset = Type.addUnsignedExact(offset, type.getSize(dataLayout));
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

    /**
     * Allocates global storage for a module and initializes the global table.
     * 
     * @see InitializeGlobalNode
     * @see InitializeModuleNode
     * @see InitializeExternalNode
     * @see InitializeOverwriteNode
     */
    private static final class InitializeSymbolsNode extends LLVMNode {

        @Child LLVMAllocateNode allocRoSection;
        @Child LLVMAllocateNode allocRwSection;
        @Child LLVMCheckSymbolNode checkGlobals;
        @Child LLVMWriteSymbolNode writeSymbols;

        @Children final AllocGlobalNode[] allocGlobals;
        final Object moduleName;

        @Children final AllocSymbolNode[] allocFuncs;

        private final LLVMScope fileScope;
        private NodeFactory nodeFactory;

        private final int ID;
        private final int globalLength;

        InitializeSymbolsNode(LLVMParserResult result, NodeFactory nodeFactory, boolean lazyParsing, boolean isSulongLibrary, Object moduleName) throws TypeOverflowException {
            DataLayout dataLayout = result.getDataLayout();
            this.nodeFactory = nodeFactory;
            this.fileScope = result.getRuntime().getFileScope();
            this.checkGlobals = LLVMCheckSymbolNodeGen.create();
            this.globalLength = result.getSymbolTableSize();
            this.ID = result.getRuntime().getBitcodeID();

            // allocate all non-pointer types as two structs
            // one for read-only and one for read-write
            DataSection roSection = new DataSection(dataLayout);
            DataSection rwSection = new DataSection(dataLayout);
            ArrayList<AllocGlobalNode> allocGlobalsList = new ArrayList<>();
            LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);

            for (GlobalVariable global : result.getDefinedGlobals()) {
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

            /*
             * Functions are allocated based on whether they are intrinsic function, regular llvm
             * bitcode function, or eager llvm bitcode function.
             */

            ArrayList<AllocSymbolNode> allocFuncsAndAliasesList = new ArrayList<>();
            for (FunctionSymbol functionSymbol : result.getDefinedFunctions()) {
                LLVMFunction function = fileScope.getFunction(functionSymbol.getName());
                if (isSulongLibrary && intrinsicProvider.isIntrinsified(function.getName())) {
                    allocFuncsAndAliasesList.add(new AllocIntrinsicFunctionNode(function, nodeFactory, intrinsicProvider));
                } else if (lazyParsing) {
                    allocFuncsAndAliasesList.add(new AllocLLVMFunctionNode(function));
                } else {
                    allocFuncsAndAliasesList.add(new AllocLLVMEagerFunctionNode(function));
                }
            }
            this.allocRoSection = roSection.getAllocateNode(nodeFactory, "roglobals_struct", true);
            this.allocRwSection = rwSection.getAllocateNode(nodeFactory, "rwglobals_struct", false);
            this.allocGlobals = allocGlobalsList.toArray(AllocGlobalNode.EMPTY);
            this.allocFuncs = allocFuncsAndAliasesList.toArray(AllocSymbolNode.EMPTY);
            this.writeSymbols = LLVMWriteSymbolNodeGen.create();
            this.moduleName = moduleName;
        }

        public boolean shouldInitialize(LLVMContext ctx) {
            return !ctx.isScopeLoaded(fileScope);
        }

        @SuppressWarnings("unchecked")
        public void initializeSymbolTable(LLVMContext context) {
            context.registerSymbolTable(ID, new AssumedValue[globalLength]);
            context.registerScope(fileScope);
        }

        public LLVMPointer execute(LLVMContext ctx) {
            if (ctx.loaderTraceStream() != null) {
                LibraryLocator.traceStaticInits(ctx, "symbol initializers", moduleName);
            }
            LLVMPointer roBase = allocOrNull(allocRoSection);
            LLVMPointer rwBase = allocOrNull(allocRwSection);

            allocGlobals(ctx, roBase, rwBase);
            allocFunctionsAndAliases(ctx);

            if (allocRoSection != null) {
                ctx.registerReadOnlyGlobals(roBase, nodeFactory);
            }
            if (allocRwSection != null) {
                ctx.registerGlobals(rwBase, nodeFactory);
            }
            return roBase; // needed later to apply memory protection after initialization
        }

        @ExplodeLoop
        private void allocGlobals(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
            for (int i = 0; i < allocGlobals.length; i++) {
                AllocGlobalNode allocGlobal = allocGlobals[i];
                LLVMGlobal descriptor = fileScope.getGlobalVariable(allocGlobal.name);
                if (descriptor == null) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(String.format("Global variable %s not found", allocGlobal.name));
                }
                if (!checkGlobals.execute(descriptor)) {
                    // because of our symbol overriding support, it can happen that the global was
                    // already bound before to a different target location
                    LLVMPointer ref = allocGlobal.allocate(context, roBase, rwBase);
                    writeSymbols.execute(ref, descriptor);
                    List<LLVMSymbol> list = new ArrayList<>();
                    list.add(descriptor);
                    context.registerSymbolReverseMap(list, ref);
                }
            }
        }

        @ExplodeLoop
        private void allocFunctionsAndAliases(LLVMContext ctx) {
            for (int i = 0; i < allocFuncs.length; i++) {
                AllocSymbolNode allocSymbol = allocFuncs[i];
                LLVMPointer pointer = allocSymbol.allocate(ctx);
                writeSymbols.execute(pointer, allocSymbol.symbol);
                List<LLVMSymbol> list = new ArrayList<>();
                list.add(allocSymbol.symbol);
                ctx.registerSymbolReverseMap(list, pointer);
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

    private ExternalLibrary[] parseDefaultLibraries(ParseContext parseContext) {
        // There could be conflicts between Sulong's default libraries and the ones that are
        // passed on the command-line. To resolve that, we add ours first but parse them later
        // on.
        String[] sulongLibraryNames = language.getCapability(PlatformCapability.class).getSulongDefaultLibraries();
        ExternalLibrary[] sulongLibraries = new ExternalLibrary[sulongLibraryNames.length];
        for (int i = 0; i < sulongLibraries.length; i++) {
            sulongLibraries[i] = context.addInternalLibrary(sulongLibraryNames[i], "<default bitcode library>");
        }

        // parse all libraries that were passed on the command-line
        List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(context.getEnv());
        for (String external : externals) {
            ExternalLibrary lib = context.addExternalLibraryDefaultLocator(external, "<command line>");
            if (lib != null) {
                parseLibrary(lib, parseContext);
            }
        }

        // now parse the default Sulong libraries
        // TODO (chaeubl): we have an ordering issue here... - the search order for native
        // code comes last, which is not necessarily correct...
        LLVMParserResult[] sulongLibraryResults = new LLVMParserResult[sulongLibraries.length];
        for (int i = 0; i < sulongLibraries.length; i++) {
            sulongLibraryResults[i] = parseLibrary(sulongLibraries[i], parseContext);
            if (sulongLibraries[i].getName().startsWith("libsulong.")) {
                context.addLibsulongDataLayout(sulongLibraryResults[i].getDataLayout());
            }
        }
        while (!parseContext.dependencyQueueIsEmpty()) {
            ExternalLibrary lib = parseContext.dependencyQueueRemoveFirst();
            parseLibrary(lib, parseContext);
        }
        return sulongLibraries;
    }

    /**
     * @return The sulong default libraries, if any were parsed.
     */
    private ExternalLibrary[] parseDependencies(ParseContext parseContext) {
        // at first, we are only parsing the direct dependencies of the main bitcode file
        int directDependencies = parseContext.dependencyQueueSize();
        for (int i = 0; i < directDependencies; i++) {
            ExternalLibrary lib = parseContext.dependencyQueueRemoveFirst();
            parseLibrary(lib, parseContext);
        }

        // then, we are parsing the default libraries
        ExternalLibrary[] sulongLibraries = getDefaultDependencies(parseContext);

        // finally we are dealing with all indirect dependencies
        while (!parseContext.dependencyQueueIsEmpty()) {
            ExternalLibrary lib = parseContext.dependencyQueueRemoveFirst();
            parseLibrary(lib, parseContext);
        }
        return sulongLibraries;
    }

    /**
     * Returns the default dependencies (as {@link ExternalLibrary} and adds their
     * {@link LLVMParserResult parser results} to the current {@link ParseContext}. The default
     * dependencies are cached in the {@link #loader}.
     */
    private ExternalLibrary[] getDefaultDependencies(ParseContext parseContext) {
        if (loader.getCachedDefaultDependencies() == null) {
            synchronized (loader) {
                if (loader.getCachedDefaultDependencies() == null) {
                    ParseContext newParseContext = ParseContext.create();
                    ExternalLibrary[] defaultLibraries = parseDefaultLibraries(newParseContext);
                    List<LLVMParserResult> parserResults = newParseContext.getParserResults();
                    loader.setDefaultLibraries(defaultLibraries, parserResults);
                }
            }
        }
        parseContext.parserResultsAddAll(loader.getCachedDefaultDependencies());
        return loader.getCachedSulongLibraries();
    }

    /**
     * Marker for renamed symbols. Keep in sync with `sulong-internal.h`.
     */
    static final String SULONG_RENAME_MARKER = "___sulong_import_";
    static final int SULONG_RENAME_MARKER_LEN = SULONG_RENAME_MARKER.length();

    private static void resolveRenamedSymbols(LLVMParserResult parserResult, ParseContext parseContext) {
        EconomicMap<ExternalLibrary, LLVMParserResult> libToRes = EconomicMap.create();
        for (LLVMParserResult res : parseContext.getParserResults()) {
            libToRes.put(res.getRuntime().getLibrary(), res);
        }
        EconomicMap<String, LLVMScope> scopes = EconomicMap.create();
        EconomicMap<String, ExternalLibrary> libs = EconomicMap.create();
        // TODO (je) we should probably do this in symbol resolution order - let's fix that when we
        // fix symbol resolution [GR-21400]
        ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>(parserResult.getDependencies());
        EconomicSet<ExternalLibrary> visited = EconomicSet.create(Equivalence.IDENTITY);
        visited.addAll(parserResult.getDependencies());
        while (!dependencyQueue.isEmpty()) {
            ExternalLibrary dep = dependencyQueue.removeFirst();
            LLVMParserResult depResult = libToRes.get(dep);
            if (depResult != null) {
                String libraryName = getSimpleLibraryName(dep.getName());
                scopes.put(libraryName, depResult.getRuntime().getFileScope());
                libs.put(libraryName, dep);
                // add transitive dependencies
                for (ExternalLibrary transDep : depResult.getDependencies()) {
                    if (!visited.contains(transDep)) {
                        dependencyQueue.addLast(transDep);
                        visited.add(transDep);
                    }
                }
            }
        }
        ListIterator<FunctionSymbol> it = parserResult.getExternalFunctions().listIterator();
        while (it.hasNext()) {
            FunctionSymbol external = it.next();
            String name = external.getName();
            /*
             * An unresolved name has the form defined by the {@code _SULONG_IMPORT_SYMBOL(libName,
             * symbolName)} macro defined in the {@code sulong-internal.h} header file. Check
             * whether we have a symbol named "symbolName" in the library "libName". If it exists,
             * introduce an alias. This can be used to explicitly call symbols from a certain
             * standard library, in case the symbol is hidden (either using the "hidden" attribute,
             * or because it is overridden).
             */
            if (name.startsWith(SULONG_RENAME_MARKER)) {
                int idx = name.indexOf('_', SULONG_RENAME_MARKER_LEN);
                if (idx > 0) {
                    String lib = name.substring(SULONG_RENAME_MARKER_LEN, idx);
                    LLVMScope scope = scopes.get(lib);
                    if (scope != null) {
                        String originalName = name.substring(idx + 1);
                        LLVMFunction originalSymbol = scope.getFunction(originalName);
                        if (originalSymbol == null) {
                            throw new LLVMLinkerException(
                                            String.format("The symbol %s could not be imported because the symbol %s was not found in library %s", external.getName(), originalName, libs.get(lib)));
                        }
                        LLVMFunction newFunction = LLVMFunction.create(name, originalSymbol.getLibrary(), originalSymbol.getFunction(), originalSymbol.getType(),
                                        parserResult.getRuntime().getBitcodeID(), external.getIndex(), external.isExported());
                        parserResult.getRuntime().getFileScope().register(newFunction);
                        it.remove();
                        parserResult.getDefinedFunctions().add(external);
                    } else {
                        throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found", external.getName(), libs.get(lib)));
                    }
                }
            } else if (CXXDemangler.isRenamedNamespaceSymbol(name)) {
                ArrayList<String> namespaces = CXXDemangler.decodeNamespace(name);
                final String lib = CXXDemangler.getAndRemoveLibraryName(namespaces);
                LLVMScope scope = scopes.get(lib);
                if (scope != null) {
                    final String originalName = CXXDemangler.encodeNamespace(namespaces);
                    LLVMFunction originalSymbol = scope.getFunction(originalName);
                    if (originalSymbol == null) {
                        throw new LLVMLinkerException(
                                        String.format("The symbol %s could not be imported because the symbol %s was not found in library %s", external.getName(), originalName, libs.get(lib)));
                    }
                    LLVMAlias alias = new LLVMAlias(parserResult.getRuntime().getLibrary(), name, originalSymbol, originalSymbol.isExported());
                    parserResult.getRuntime().getFileScope().register(alias);
                    it.remove();
                } else {
                    throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found", external.getName(), lib));
                }
            }
        }
    }

    /**
     * Drop everything after the first "{@code .}".
     */
    private static String getSimpleLibraryName(String name) {
        int index = name.indexOf(".");
        if (index == -1) {
            return name;
        }
        return name.substring(0, index);
    }

    @SuppressWarnings("unchecked")
    private void loadDefaults(Path internalLibraryPath) {
        ExternalLibrary polyglotMock = ExternalLibrary.createFromPath(internalLibraryPath.resolve(language.getCapability(PlatformCapability.class).getPolyglotMockLibrary()), false, true);
        // TODO (je) maybe add the polyglotMock to the context already?
        LLVMParserResult polyglotMockResult = parseLibrary(polyglotMock, ParseContext.create());
        // We use the global scope here to avoid trying to intrinsify functions in the file scope.
        // However, this is based on the assumption that polyglot-mock is the first loaded library!
        int symbolSize = polyglotMockResult.getDefinedFunctions().size() + polyglotMockResult.getDefinedGlobals().size() + polyglotMockResult.getExternalFunctions().size() +
                        polyglotMockResult.getExternalGlobals().size();
        context.registerSymbolTable(polyglotMockResult.getRuntime().getBitcodeID(), new AssumedValue[symbolSize]);

        for (LLVMSymbol symbol : polyglotMockResult.getRuntime().getGlobalScope().values()) {
            if (symbol.isFunction()) {
                LLVMFunction function = symbol.asFunction();
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(function);
                functionDescriptor.getFunctionCode().define(language.getCapability(LLVMIntrinsicProvider.class), polyglotMockResult.getRuntime().getNodeFactory());

                int index = function.getSymbolIndex(false);
                AssumedValue<LLVMPointer>[] symbols = context.findSymbolTable(function.getBitcodeID(false));
                LLVMPointer pointer = LLVMManagedPointer.create(functionDescriptor);
                symbols[index] = new AssumedValue<>("LLVMFunction." + function.getName(), pointer);

                LLVMSymbol exportedSymbol = context.getGlobalScopeTmp().get(function.getName());
                if (exportedSymbol == null) {
                    context.getGlobalScopeTmp().register(function);
                }

                LLVMLocalScope localtmp = context.getLocalScopeTmp(polyglotMockResult.getRuntime().getBitcodeID());
                LLVMSymbol exportedSymbolFromLocal = localtmp.get(function.getName());
                if (exportedSymbolFromLocal == null) {
                    localtmp.register(function);
                }

                List<LLVMSymbol> list = new ArrayList<>();
                list.add(function);
                context.registerSymbolReverseMap(list, pointer);
            }
        }
    }

    /**
     * Parses the {@link ExternalLibrary} {@code lib} and returns its {@link LLVMParserResult}.
     * Explicit and implicit dependencies of {@code lib} are added to the
     * {@link ParseContext#dependencyQueueAddLast dependency queue}. The returned
     * {@link LLVMParserResult} is also added to the {@link ParseContext#parserResultsAdd parser
     * results}. The {@code lib} parameter is add to the {@link LLVMContext#addExternalLibrary
     * context}.
     *
     * @param lib the library to be parsed
     * @param parseContext
     * @return the parser result corresponding to {@code lib}
     */
    private LLVMParserResult parseLibrary(ExternalLibrary lib, ParseContext parseContext) {
        if (lib.hasFile() && !lib.getFile().isRegularFile() || lib.getPath() == null || !lib.getPath().toFile().isFile()) {
            if (!lib.isNative()) {
                throw new LLVMParserException("'" + lib.getPath() + "' is not a file or does not exist.");
            } else {
                // lets assume that this is not a bitcode file and the NFI is going to handle it
                return null;
            }
        }
        TruffleFile file = lib.hasFile() ? lib.getFile() : context.getEnv().getInternalTruffleFile(lib.getPath().toUri());
        Source source;
        try {
            source = Source.newBuilder("llvm", file).internal(lib.isInternal()).build();
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            throw new LLVMParserException("Error reading file " + lib.getPath() + ".");
        }
        return parseLibraryWithSource(source, lib, source.getBytes(), parseContext);
    }

    /**
     * Parses a binary (bitcode with optional meta information from an ELF, Mach-O object file).
     */
    private LLVMParserResult parseBinary(BinaryParserResult binaryParserResult, ExternalLibrary library) {
        ModelModule module = new ModelModule();
        Source source = binaryParserResult.getSource();
        LLVMScanner.parseBitcode(binaryParserResult.getBitcode(), module, source, context);
        TargetDataLayout layout = module.getTargetDataLayout();
        DataLayout targetDataLayout = new DataLayout(layout.getDataLayout());
        NodeFactory nodeFactory = context.getLanguage().getActiveConfiguration().createNodeFactory(context, targetDataLayout);
        // This needs to be removed once the nodefactory is taken out of the language.
        LLVMScope fileScope = new LLVMScope();
        int bitcodeID = id.getAndIncrement();
        LLVMParserRuntime runtime = new LLVMParserRuntime(context, library, fileScope, nodeFactory, bitcodeID);
        localScopeTmp.addID(bitcodeID);
        localScope.addID(bitcodeID);
        LLVMParser parser = new LLVMParser(source, runtime, localScope);
        return parser.parse(module, targetDataLayout);
    }

    /**
     * Parses a single bitcode module and returns its {@link LLVMParserResult}. Explicit and
     * implicit dependencies of {@code lib} are added to the
     * {@link ParseContext#dependencyQueueAddLast dependency queue}. The returned
     * {@link LLVMParserResult} is also added to the {@link ParseContext#parserResultsAdd parser
     * results}. This method ensures that the {@code library} parameter is added to the
     * {@link LLVMContext#ensureExternalLibraryAdded context}.
     *
     * @param source the {@link Source} of the library to be parsed
     * @param library the {@link ExternalLibrary} corresponding to the library to be parsed
     * @param bytes the bytes of the library to be parsed
     * @param parseContext
     * @return the parser result corresponding to {@code lib}
     */
    private LLVMParserResult parseLibraryWithSource(Source source, ExternalLibrary library, ByteSequence bytes, ParseContext parseContext) {
        BinaryParserResult binaryParserResult = BinaryParser.parse(bytes, source, context);
        if (binaryParserResult != null) {
            library.makeBitcodeLibrary();
            context.ensureExternalLibraryAdded(library);
            context.addLibraryPaths(binaryParserResult.getLibraryPaths());
            ArrayList<ExternalLibrary> dependencies = processDependencies(library, binaryParserResult, parseContext);
            LLVMParserResult parserResult = parseBinary(binaryParserResult, library);
            parserResult.setDependencies(dependencies);
            parseContext.parserResultsAdd(parserResult);
            return parserResult;
        } else if (!library.isNative()) {
            throw new LLVMParserException("The file '" + source.getName() + "' is not a bitcode file nor an ELF or Mach-O object file with an embedded bitcode section.");
        } else {
            LibraryLocator.traceDelegateNative(context, library);
            return null;
        }
    }

    /**
     * Converts the {@link BinaryParserResult#getLibraries() dependencies} of a
     * {@link BinaryParserResult} into {@link ExternalLibrary}s and add them to the
     * {@link ParseContext#dependencyQueueAddLast dependency queue} if not already in there.
     */
    private ArrayList<ExternalLibrary> processDependencies(ExternalLibrary library, BinaryParserResult binaryParserResult, ParseContext parseContext) {
        ArrayList<ExternalLibrary> dependencies = new ArrayList<>();
        for (String lib : context.preprocessDependencies(library, binaryParserResult.getLibraries())) {
            ExternalLibrary dependency = context.findExternalLibrary(lib, library, binaryParserResult.getLocator());
            if (dependency != null) {
                dependencies.add(dependency);
            } else {
                dependency = context.addExternalLibrary(lib, library, binaryParserResult.getLocator());
                if (dependency != null) {
                    parseContext.dependencyQueueAddLast(dependency);
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies;
    }

    private static void addExternalSymbolsToScopes(List<LLVMParserResult> parserResults) {
        // TODO (chaeubl): in here, we should validate if the return type/argument type/global
        // types match
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            for (FunctionSymbol function : parserResult.getExternalFunctions()) {
                if (!fileScope.contains(function.getName())) {
                    fileScope.register(LLVMFunction.create(function.getName(), null, new LLVMFunctionCode.UnresolvedFunction(), function.getType(), parserResult.getRuntime().getBitcodeID(),
                                    function.getIndex(), false));
                }
            }
            for (GlobalVariable global : parserResult.getExternalGlobals()) {
                if (!fileScope.contains(global.getName())) {
                    fileScope.register(
                                    LLVMGlobal.create(global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly(), global.getIndex(), parserResult.getRuntime().getBitcodeID(),
                                                    false));
                }
            }
        }
    }

    private static InitializationOrder computeInitializationOrder(List<LLVMParserResult> parserResults, ExternalLibrary[] defaultLibraries) {
        List<ExternalLibrary> sulongExternalLibraries = Arrays.asList(defaultLibraries);

        ArrayList<LLVMParserResult> sulongLibs = new ArrayList<>();
        ArrayList<LLVMParserResult> otherLibs = new ArrayList<>();
        ArrayList<LLVMParserResult> otherLibsInitializationOrder = new ArrayList<>();
        EconomicMap<Object, LLVMParserResult> dependencyToParserResult = EconomicMap.create(Equivalence.IDENTITY);
        EconomicSet<LLVMParserResult> visited = EconomicSet.create(Equivalence.IDENTITY);
        /*
         * Split libraries into Sulong-specific ones and others, so that we can handle the
         * Sulong-specific ones separately.
         */
        for (LLVMParserResult parserResult : parserResults) {
            ExternalLibrary library = parserResult.getRuntime().getLibrary();
            dependencyToParserResult.put(library, parserResult);
            if (sulongExternalLibraries.contains(library)) {
                sulongLibs.add(parserResult);
                visited.add(parserResult);
            } else {
                otherLibs.add(parserResult);
            }
        }

        for (LLVMParserResult otherlib : otherLibs) {
            if (!visited.contains(otherlib)) {
                addModuleToInitializationOrder(otherlib, otherLibsInitializationOrder, dependencyToParserResult, visited);
                assert otherLibsInitializationOrder.contains(otherlib);
            }
        }
        assert otherLibsInitializationOrder.containsAll(otherLibs);
        return new InitializationOrder(sulongLibs, otherLibsInitializationOrder, otherLibs);
    }

    private static void addModuleToInitializationOrder(LLVMParserResult module, ArrayList<LLVMParserResult> initializationOrder, EconomicMap<Object, LLVMParserResult> dependencyToParserResult,
                    EconomicSet<LLVMParserResult> visited) {
        if (visited.contains(module)) {
            /*
             * We don't know if the module has already been added to the initialization order list
             * or if we are still processing its dependencies. In the second case we found a cycle,
             * which we silently ignore.
             */
            return;
        }
        visited.add(module);
        for (ExternalLibrary dep : module.getDependencies()) {
            LLVMParserResult depLib = dependencyToParserResult.get(dep);
            if (depLib != null) {
                addModuleToInitializationOrder(depLib, initializationOrder, dependencyToParserResult, visited);
            }
        }
        initializationOrder.add(module);
    }

    abstract static class StaticInitsNode extends LLVMStatementNode {

        @Children final LLVMStatementNode[] statements;
        final Object moduleName;
        final String prefix;

        StaticInitsNode(LLVMStatementNode[] statements, String prefix, Object moduleName) {
            this.statements = statements;
            this.prefix = prefix;
            this.moduleName = moduleName;
        }

        @ExplodeLoop
        @Specialization
        public void doInit(VirtualFrame frame,
                        @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (ctx.loaderTraceStream() != null) {
                traceExecution(ctx);
            }
            for (LLVMStatementNode stmt : statements) {
                stmt.execute(frame);
            }
        }

        @TruffleBoundary
        private void traceExecution(LLVMContext ctx) {
            LibraryLocator.traceStaticInits(ctx, prefix, moduleName, String.format("[%d inst]", statements.length));
        }
    }

    private static final class InitializeScopeNode extends LLVMNode {
        @Children final AllocScopeNode[] allocScopes;
        private final int ID;

        InitializeScopeNode(LLVMParserResult result) {
            this.ID = result.getRuntime().getBitcodeID();
            ArrayList<AllocScopeNode> allocScopesList = new ArrayList<>();
            LLVMScope fileScope = result.getRuntime().getFileScope();
            for (LLVMSymbol symbol : fileScope.values()) {
                allocScopesList.add(new AllocScopeNode(symbol));
            }
            this.allocScopes = allocScopesList.toArray(AllocScopeNode.EMPTY);
        }

        void execute(LLVMContext context) {
            synchronized (context) {
                for (int i = 0; i < allocScopes.length; i++) {
                    AllocScopeNode allocScope = allocScopes[i];
                    allocScope.allocateScope(context, ID);
                }
            }
        }
    }

    /**
     * Initialize external and exported symbols, by populating the symbol table of every external
     * symbols of a given bitcode file.
     *
     * External bitcode functions will have their entry into the symbol table be replaced with the
     * entry of it's corresponding defined function in the local scope, or the gloabl scope if the
     * function is loaded in a previous parsing phase. Otherwise an instrinic or native function
     * will be created if they are available. Similarly, external global will have their entry into
     * the symbol table be that of the corresponding defined global symbol in the local scope. If no
     * global of such name exists, a native global is created if it exists in the NFI context.
     *
     * @see InitializeSymbolsNode
     * @see InitializeGlobalNode
     * @see InitializeModuleNode
     * @see InitializeOverwriteNode
     */
    private static final class InitializeExternalNode extends LLVMNode {
        @Child LLVMWriteSymbolNode writeSymbols;
        @Children AllocExternalSymbolNode[] allocExternalSymbols;
        private final int ID;
        private final NodeFactory nodeFactory;

        InitializeExternalNode(LLVMParserResult result) {
            this.ID = result.getRuntime().getBitcodeID();
            this.nodeFactory = result.getRuntime().getNodeFactory();
            LLVMScope fileScope = result.getRuntime().getFileScope();
            ArrayList<AllocExternalSymbolNode> allocExternaSymbolsList = new ArrayList<>();

            // Bind all functions that are not defined/resolved as either a bitcode function
            // defined in another library, an intrinsic function or a native function.
            for (FunctionSymbol symbol : result.getExternalFunctions()) {
                String name = symbol.getName();
                LLVMFunction function = fileScope.getFunction(name);
                if (name.startsWith("llvm.") || name.startsWith("__builtin_") || name.equals("polyglot_get_arg") || name.equals("polyglot_get_arg_count")) {
                    continue;
                }
                allocExternaSymbolsList.add(AllocExternalFunctionNodeGen.create(function, nodeFactory));
            }

            for (GlobalSymbol symbol : result.getExternalGlobals()) {
                LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
                allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global, nodeFactory));
            }

            this.writeSymbols = LLVMWriteSymbolNodeGen.create();
            this.allocExternalSymbols = allocExternaSymbolsList.toArray(AllocExternalSymbolNode.EMPTY);
        }

        /*
         * (PLi): Need to be careful of native functions/globals that are not in the nfi context
         * (i.e. __xstat). Ideally they will be added to the symbol table as unresolved/undefined
         * functions/globals.
         */
        @ExplodeLoop
        void execute(LLVMContext context) {
            LLVMLocalScope localScope = context.getLocalScopeTmp(ID);
            LLVMScope globalScope = context.getGlobalScopeTmp();
            LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);
            NFIContextExtension nfiContextExtension = LLVMLanguage.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);

            synchronized (context) {
                // functions and globals
                for (int i = 0; i < allocExternalSymbols.length; i++) {
                    AllocExternalSymbolNode function = allocExternalSymbols[i];
                    LLVMPointer pointer = function.execute(localScope, globalScope, intrinsicProvider, nfiContextExtension);
                    if (pointer == null) {
                        continue;
                    }
                    writeSymbols.execute(pointer, function.symbol);
                }
            }
        }
    }

    /**
     * Overwriting for defined symbols that will be resolved to the local scope instead of the file
     * scope.
     *
     * If a defined symbol is required to be access via the local scope instead of it's file scope,
     * then that symbol's entry in the symbol table will be that of the defined symbol from the
     * local scope.
     *
     * @see InitializeSymbolsNode
     * @see InitializeGlobalNode
     * @see InitializeModuleNode
     * @see InitializeExternalNode
     */
    private static final class InitializeOverwriteNode extends LLVMNode {

        @Children final AllocSymbolNode[] allocOverridableSymbols;
        @Child LLVMWriteSymbolNode writeSymbols;

        InitializeOverwriteNode(LLVMParserResult result, LLVMLocalScope localScope) {
            this.writeSymbols = LLVMWriteSymbolNodeGen.create();

            ArrayList<AllocSymbolNode> allocOverrideSymbolsList = new ArrayList<>();
            LLVMScope fileScope = result.getRuntime().getFileScope();

            // Rewrite all overridable functions and globals in the filescope from their respective
            // function/global in the localscope.
            for (FunctionSymbol symbol : result.getDefinedFunctions()) {
                if (symbol.isOverridable()) {
                    LLVMFunction function = fileScope.getFunction(symbol.getName());
                    LLVMSymbol localFunction = localScope.get(symbol.getName());
                    if (localFunction != null && localFunction.isFunction() && !(function.equals(localFunction.asFunction()))) {
                        allocOverrideSymbolsList.add(new AllocExistingSymbolNode(function, LLVMAccessSymbolNodeGen.create(localFunction)));
                    }
                }
            }
            for (GlobalSymbol symbol : result.getDefinedGlobals()) {
                // Cannot override the reserved symbols CONSTRUCTORS_VARNAME and
                // DECONSTRUCTORS_VARNAME
                if (symbol.isOverridable() && !symbol.isIntrinsicGlobalVariable()) {
                    LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
                    LLVMSymbol localGlobal = result.getRuntime().getGlobalScope().get(symbol.getName());
                    // Global symbol from the fileScope will be overridden if there exists a
                    // different (non-hidden) global symbol of the same name in the globalscope
                    if (localGlobal != null && localGlobal.isGlobalVariable() && !(global.equals(localGlobal.asGlobalVariable()))) {
                        // Cannot override with a hidden global symbol from the localScope
                        allocOverrideSymbolsList.add(new AllocExistingSymbolNode(global, LLVMAccessSymbolNodeGen.create(localGlobal)));
                    }
                }
            }

            this.allocOverridableSymbols = allocOverrideSymbolsList.toArray(AllocSymbolNode.EMPTY);
        }

        @ExplodeLoop
        void execute(LLVMContext context) {
            for (int i = 0; i < allocOverridableSymbols.length; i++) {
                AllocSymbolNode allocSymbol = allocOverridableSymbols[i];
                LLVMPointer pointer = allocSymbol.allocate(context);
                writeSymbols.execute(pointer, allocSymbol.symbol);
            }
        }
    }

    /**
     * Initializes the memory, allocated by {@link InitializeSymbolsNode}, for a module and protects
     * the read only section.
     *
     * @see InitializeSymbolsNode
     * @see InitializeModuleNode
     * @see InitializeExternalNode
     * @see InitializeOverwriteNode
     */
    private static final class InitializeGlobalNode extends LLVMNode implements LLVMHasDatalayoutNode {

        private final DataLayout dataLayout;

        @Child StaticInitsNode globalVarInit;
        @Child LLVMMemoryOpNode protectRoData;

        InitializeGlobalNode(FrameDescriptor rootFrame, LLVMParserResult parserResult, Object moduleName) {
            this.dataLayout = parserResult.getDataLayout();

            this.globalVarInit = Runner.createGlobalVariableInitializer(rootFrame, parserResult, moduleName);
            this.protectRoData = parserResult.getRuntime().getNodeFactory().createProtectGlobalsBlock();
        }

        void execute(VirtualFrame frame, LLVMPointer roDataBase) {
            globalVarInit.execute(frame);
            if (roDataBase != null) {
                // TODO could be a compile-time check
                protectRoData.execute(roDataBase);
            }
        }

        @Override
        public DataLayout getDatalayout() {
            return dataLayout;
        }
    }

    /**
     * Registers the destructor and executes the constructor of a module. This happens after
     * <emph>all</emph> globals have been initialized by {@link InitializeGlobalNode}.
     *
     * @see InitializeSymbolsNode
     * @see InitializeGlobalNode
     * @see InitializeExternalNode
     * @see InitializeOverwriteNode
     */
    private static final class InitializeModuleNode extends LLVMNode implements LLVMHasDatalayoutNode {

        private final RootCallTarget destructor;
        private final DataLayout dataLayout;

        @Child StaticInitsNode constructor;

        InitializeModuleNode(Runner runner, LLVMParserResult parserResult, Object moduleName) {
            this.destructor = runner.createDestructor(parserResult, moduleName);
            this.dataLayout = parserResult.getDataLayout();

            this.constructor = Runner.createConstructor(parserResult, moduleName);
        }

        void execute(VirtualFrame frame, LLVMContext ctx) {
            if (destructor != null) {
                ctx.registerDestructorFunctions(destructor);
            }
            constructor.execute(frame);
        }

        @Override
        public DataLayout getDatalayout() {
            return dataLayout;
        }
    }

    private static StaticInitsNode createGlobalVariableInitializer(FrameDescriptor rootFrame, LLVMParserResult parserResult, Object moduleName) {
        LLVMParserRuntime runtime = parserResult.getRuntime();
        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, rootFrame, GetStackSpaceFactory.createAllocaFactory(), parserResult.getDataLayout(), false);
        final List<LLVMStatementNode> globalNodes = new ArrayList<>();
        for (GlobalVariable global : parserResult.getDefinedGlobals()) {
            final LLVMStatementNode store = createGlobalInitialization(runtime, symbolResolver, global, parserResult.getDataLayout());
            if (store != null) {
                globalNodes.add(store);
            }
        }
        LLVMStatementNode[] initNodes = globalNodes.toArray(LLVMStatementNode.NO_STATEMENTS);
        return StaticInitsNodeGen.create(initNodes, "global variable initializers", moduleName);
    }

    private static LLVMStatementNode createGlobalInitialization(LLVMParserRuntime runtime, LLVMSymbolReadResolver symbolResolver, GlobalVariable global, DataLayout dataLayout) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            try {
                final Type type = global.getType().getPointeeType();
                final long size = type.getSize(dataLayout);

                /*
                 * For fetching the address of the global that we want to initialize, we must use
                 * the file scope because we are initializing the globals of the current file.
                 */
                LLVMGlobal globalDescriptor = runtime.getFileScope().getGlobalVariable(global.getName());
                assert globalDescriptor != null;
                final LLVMExpressionNode globalVarAddress = runtime.getNodeFactory().createLiteral(globalDescriptor, new PointerType(global.getType()));
                if (size != 0) {
                    if (type instanceof ArrayType || type instanceof StructureType) {
                        return runtime.getNodeFactory().createStore(globalVarAddress, constant, type);
                    } else {
                        Type t = global.getValue().getType();
                        return runtime.getNodeFactory().createStore(globalVarAddress, constant, t);
                    }
                }
            } catch (TypeOverflowException e) {
                return Type.handleOverflowStatement(e);
            }
        }

        return null;
    }

    private static StaticInitsNode createConstructor(LLVMParserResult parserResult, Object moduleName) {
        return StaticInitsNodeGen.create(createStructor(CONSTRUCTORS_VARNAME, parserResult, ASCENDING_PRIORITY), "init", moduleName);
    }

    private RootCallTarget createDestructor(LLVMParserResult parserResult, Object moduleName) {
        LLVMStatementNode[] destructor = createStructor(DESTRUCTORS_VARNAME, parserResult, DESCENDING_PRIORITY);
        if (destructor.length > 0) {
            LLVMStatementRootNode root = new LLVMStatementRootNode(language, StaticInitsNodeGen.create(destructor, "fini", moduleName), StackManager.createRootFrame());
            return Truffle.getRuntime().createCallTarget(root);
        } else {
            return null;
        }
    }

    private static LLVMStatementNode[] createStructor(String name, LLVMParserResult parserResult, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalVariable globalVariable : parserResult.getDefinedGlobals()) {
            if (globalVariable.getName().equals(name)) {
                return resolveStructor(parserResult.getRuntime().getFileScope(), globalVariable, priorityComparator, parserResult.getDataLayout(), parserResult.getRuntime().getNodeFactory());
            }
        }
        return LLVMStatementNode.NO_STATEMENTS;
    }

    private static LLVMStatementNode[] resolveStructor(LLVMScope fileScope, GlobalVariable globalSymbol, Comparator<Pair<Integer, ?>> priorityComparator, DataLayout dataLayout,
                    NodeFactory nodeFactory) {
        if (!(globalSymbol.getValue() instanceof ArrayConstant)) {
            // array globals of length 0 may be initialized with scalar null
            return LLVMStatementNode.NO_STATEMENTS;
        }

        final LLVMGlobal global = (LLVMGlobal) fileScope.get(globalSymbol.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalSymbol.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        try {
            final long elementSize = elementType.getSize(dataLayout);

            final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
            final int indexedTypeLength = functionType.getAlignment(dataLayout);

            final ArrayList<Pair<Integer, LLVMStatementNode>> structors = new ArrayList<>(elemCount);
            FrameDescriptor rootFrame = StackManager.createRootFrame();
            for (int i = 0; i < elemCount; i++) {
                final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(global, new PointerType(globalSymbol.getType()));
                final LLVMExpressionNode iNode = nodeFactory.createLiteral(i, PrimitiveType.I32);
                final LLVMExpressionNode structPointer = nodeFactory.createTypedElementPointer(elementSize, elementType, globalVarAddress, iNode);
                final LLVMExpressionNode loadedStruct = CommonNodeFactory.createLoad(elementType, structPointer);

                final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
                final LLVMExpressionNode functionLoadTarget = nodeFactory.createTypedElementPointer(indexedTypeLength, functionType, loadedStruct, oneLiteralNode);
                final LLVMExpressionNode loadedFunction = CommonNodeFactory.createLoad(functionType, functionLoadTarget);
                final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{
                                CommonNodeFactory.createFrameRead(PointerType.VOID, rootFrame.findFrameSlot(LLVMStack.FRAME_ID))};
                final LLVMStatementNode functionCall = LLVMVoidStatementNodeGen.create(CommonNodeFactory.createFunctionCall(loadedFunction, argNodes, functionType));

                final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
                final SymbolImpl prioritySymbol = structorDefinition.getElement(0);
                final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
                structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
            }

            return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMStatementNode[]::new);
        } catch (TypeOverflowException e) {
            return new LLVMStatementNode[]{Type.handleOverflowStatement(e)};
        }
    }

    static byte[] decodeBase64(CharSequence charSequence) {
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
        LLVMFunctionDescriptor startFunctionDescriptor = findStartFunctionDescriptor();
        LLVMFunction mainFunction = findMainFunction(parserResults);
        if (startFunctionDescriptor != null && mainFunction != null) {
            RootCallTarget startCallTarget = startFunctionDescriptor.getFunctionCode().getLLVMIRFunctionSlowPath();
            Path applicationPath = mainFunction.getLibrary().getPath();
            RootNode rootNode = new LLVMGlobalRootNode(language, StackManager.createRootFrame(), mainFunction, startCallTarget, Objects.toString(applicationPath, ""));
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(rootNode);
        }

        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            LLVMScope scope = combineScopes(parserResults);
            SulongLibrary lib = new SulongLibrary(name, scope, mainFunctionCallTarget, context);

            FrameDescriptor rootFrame = StackManager.createRootFrame();

            // check if the functions should be resolved eagerly or lazyly.
            boolean lazyParsing = context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING);
            LoadModulesNode loadModules = LoadModulesNode.create(this, rootFrame, initializationOrder, lib, lazyParsing);
            return Truffle.getRuntime().createCallTarget(loadModules);
        }
    }

    /**
     * Retrieves the function for the main method.
     */
    private static LLVMFunction findMainFunction(List<LLVMParserResult> parserResults) {
        // check if the freshly parsed code exports a main method
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            LLVMSymbol mainSymbol = fileScope.get(MAIN_METHOD_NAME);

            if (mainSymbol != null && mainSymbol.isFunction() && mainSymbol.isDefined()) {
                /*
                 * The `isLLVMIRFunction` check makes sure the `main` function is really defined in
                 * bitcode. This prevents us from finding a native `main` function (e.g. the `main`
                 * of the VM we're running in).
                 */

                LLVMFunction mainFunction = mainSymbol.asFunction();
                if (mainFunction.getFunction() instanceof LLVMIRFunction || mainFunction.getFunction() instanceof LazyLLVMIRFunction) {
                    return mainFunction;
                }
            }
        }
        return null;
    }

    /**
     * Creates and returns the function descriptor (function code) of the start method.
     *
     * @return The function descriptor containing the start function.
     */
    private LLVMFunctionDescriptor findStartFunctionDescriptor() {
        // the start method just needs to be present in the global scope, we don't care when it was
        // parsed.
        LLVMSymbol function = localScope.get(START_METHOD_NAME);
        if (function != null && function.isDefined()) {
            return context.createFunctionDescriptor(function.asFunction());
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

    private static final class InitializationOrder {
        private final List<LLVMParserResult> sulongLibraries;
        private final List<LLVMParserResult> otherLibraries;
        private final List<LLVMParserResult> originalOtherLibraries;

        private InitializationOrder(List<LLVMParserResult> sulongLibraries, List<LLVMParserResult> otherLibraries, List<LLVMParserResult> originalOtherLibraries) {
            this.sulongLibraries = sulongLibraries;
            this.otherLibraries = otherLibraries;
            this.originalOtherLibraries = originalOtherLibraries;
        }
    }
}
