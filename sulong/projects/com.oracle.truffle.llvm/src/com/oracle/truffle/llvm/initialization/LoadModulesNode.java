/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.SulongLibrary;
import com.oracle.truffle.llvm.runtime.SulongLibrary.CachedMainFunction;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * The {@link LoadModulesNode} initialise the library. This involves building the scopes (local
 * scope and global scope), allocating the symbol table, resolving external symbols, resolving
 * symbol resolution, allocating global symbols, initialising the context, and initialising the
 * constructor.
 *
 * At the start of initialisation process the dependencies of the library are parsed, the start
 * function and main function are created, and the context initialisation and dispose symbols are
 * defined. This is only done once per library.
 *
 * The initialisation of a library is broken down into nine phases. The scope building phase, the
 * (defined) symbol initialisation phase, the external symbol initialisation phase, the global
 * symbol creation phase, the symbol resolution phase, the constructor initialisation phase, and
 * finally the caching or the done phase.
 *
 * The initialisation is done calling the loadModule method, the dependencies of the library are
 * initialised by recursively call the loadModule method with the specific path that is being
 * initialised, i.e. INIT_EXTERNALS for external symbol initialisation. Once the initialisation has
 * been completed only the root library will return the non-null scope for the initialised library,
 * while the dependencies will return null.
 *
 */
public final class LoadModulesNode extends LLVMRootNode {

    private static final String MAIN_METHOD_NAME = "main";

    @CompilationFinal RootCallTarget mainFunctionCallTarget;
    final String sourceName;
    final int bitcodeID;
    final Source source;
    @CompilationFinal ContextReference<LLVMContext> ctxRef;

    @Child LLVMStatementNode initContext;

    @Child InitializeSymbolsNode initSymbols;
    @Child InitializeScopeNode initScopes;
    @Child InitializeExternalNode initExternals;
    @Child InitializeGlobalNode initGlobals;
    @Child InitializeOverwriteNode initOverwrite;
    @Child InitializeModuleNode initModules;
    @Child IndirectCallNode indirectCall;

    @Child IndirectCallNode callDependencies;
    final CallTarget[] dependencies;
    final List<Object> dependenciesSource;
    final LLVMParserRuntime parserRuntime;
    final LLVMLanguage language;
    private boolean hasInitialised;
    @CompilationFinal private CachedMainFunction main;

    protected enum LLVMLoadingPhase {
        ALL,
        BUILD_SCOPES,
        INIT_SYMBOLS,
        INIT_EXTERNALS,
        INIT_GLOBALS,
        INIT_MODULE,
        INIT_CONTEXT,
        INIT_OVERWRITE,
        INIT_DONE;

        boolean isActive(LLVMLoadingPhase phase) {
            return phase == this || phase == ALL;
        }
    }

    private LoadModulesNode(String name, LLVMParserResult parserResult, boolean isInternalSulongLibrary,
                    FrameDescriptor rootFrame, boolean lazyParsing, List<Object> dependenciesSource, Source source, LLVMLanguage language) throws Type.TypeOverflowException {
        super(language, rootFrame, parserResult.getRuntime().getNodeFactory().createStackAccess(rootFrame));
        this.mainFunctionCallTarget = null;
        this.sourceName = name;
        this.source = source;
        this.bitcodeID = parserResult.getRuntime().getBitcodeID();
        this.parserRuntime = parserResult.getRuntime();
        this.dependenciesSource = dependenciesSource;
        this.language = language;
        this.dependencies = new CallTarget[dependenciesSource.size()];
        this.hasInitialised = false;
        this.initContext = null;
        String moduleName = parserResult.getRuntime().getLibraryName();
        this.initSymbols = new InitializeSymbolsNode(parserResult, lazyParsing, isInternalSulongLibrary, moduleName);
        this.initScopes = new InitializeScopeNode(parserRuntime);
        this.initExternals = new InitializeExternalNode(parserResult);
        this.initGlobals = new InitializeGlobalNode(parserResult, moduleName);
        this.initOverwrite = new InitializeOverwriteNode(parserResult);
        this.initModules = new InitializeModuleNode(language, parserResult, moduleName);
        this.indirectCall = IndirectCallNode.create();
        this.callDependencies = IndirectCallNode.create();
    }

    @Override
    public String getName() {
        return '<' + getClass().getSimpleName() + '>';
    }

    @Override
    public SourceSection getSourceSection() {
        return source.createUnavailableSection();
    }

    public static LoadModulesNode create(String name, LLVMParserResult parserResult,
                    boolean lazyParsing, boolean isInternalSulongLibrary, List<Object> dependencySources, Source source, LLVMLanguage language) {
        try {
            return new LoadModulesNode(name, parserResult, isInternalSulongLibrary, new FrameDescriptor(), lazyParsing, dependencySources, source, language);
        } catch (Type.TypeOverflowException e) {
            throw new LLVMUnsupportedException(null, LLVMUnsupportedException.UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {

        if (ctxRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.ctxRef = lookupContextReference(LLVMLanguage.class);
        }
        LLVMContext context = ctxRef.get();

        synchronized (context) {
            if (!hasInitialised) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Parse the dependencies of this library.
                for (int i = 0; i < dependenciesSource.size(); i++) {
                    if (dependenciesSource.get(i) instanceof Source) {
                        CallTarget callTarget = context.getEnv().parseInternal((Source) dependenciesSource.get(i));
                        // The call targets are needed for initialising the scope.
                        dependencies[i] = callTarget;
                    } else if (dependenciesSource.get(i) instanceof CallTarget) {
                        // The call targets are needed for initialising the scope.
                        dependencies[i] = (CallTarget) dependenciesSource.get(i);
                    } else {
                        throw new IllegalStateException("Unknown dependency.");
                    }
                }
                LLVMFunction mainFunction = findMainFunction();
                if (mainFunction != null) {
                    main = new CachedMainFunction(mainFunction);
                } else {
                    main = null;
                }

                initContext = this.insert(language.createInitializeContextNode());
                hasInitialised = true;
            }

            LLVMScope scope = loadModule(frame, context);
            // Only the root library (not a dependency) will scope a non-null scope.
            if (scope != null) {
                return new SulongLibrary(sourceName, scope, main, context);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private LLVMScope loadModule(VirtualFrame frame, LLVMContext context) {

        stackAccess.executeEnter(frame, ctxRef.get().getThreadingStack().getStack());
        try {
            LLVMLoadingPhase phase;
            LLVMLocalScope localScope = null;
            BitSet visited;
            ArrayDeque<CallTarget> que = null;
            LLVMScope resultScope = null;

            if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                phase = (LLVMLoadingPhase) frame.getArguments()[0];
                visited = (BitSet) frame.getArguments()[1];
                if (phase == LLVMLoadingPhase.BUILD_SCOPES || phase == LLVMLoadingPhase.INIT_EXTERNALS || phase == LLVMLoadingPhase.INIT_OVERWRITE) {
                    localScope = (LLVMLocalScope) frame.getArguments()[2];
                }
                // Additional arguments are required for building the scopes.
                if (phase == LLVMLoadingPhase.BUILD_SCOPES) {
                    que = (ArrayDeque<CallTarget>) frame.getArguments()[3];
                    resultScope = (LLVMScope) frame.getArguments()[4];
                }
                // For the root library, it is defined when either the frame has no argument, or
                // when the
                // first argument is not one of the loading phases.
            } else if (frame.getArguments().length == 0 || !(frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                phase = LLVMLoadingPhase.ALL;
                resultScope = createLLVMScope();
                localScope = createLocalScope();
                context.addLocalScope(localScope);
                visited = createBitset();
                que = new ArrayDeque<>();
            } else {
                throw new LLVMParserException("LoadModulesNode is called with unexpected arguments");
            }

            /*
             * The scope is built in parsing order, which requires breadth-first with a que.
             */
            if (LLVMLoadingPhase.BUILD_SCOPES.isActive(phase)) {
                if (!visited.get(bitcodeID)) {
                    visited.set(bitcodeID);
                    addIDToLocalScope(localScope, bitcodeID);
                    initScopes.execute(context, localScope);
                    resultScope.addMissingEntries(parserRuntime.getFileScope());
                    for (CallTarget callTarget : dependencies) {
                        if (callTarget != null) {
                            queAdd(que, callTarget);
                        }
                    }

                    if (LLVMLoadingPhase.ALL.isActive(phase)) {
                        while (!que.isEmpty()) {
                            indirectCall.call(que.poll(), LLVMLoadingPhase.BUILD_SCOPES, visited, localScope, que, resultScope);
                        }
                    }
                }
            }

            if (context.isLibraryAlreadyLoaded(bitcodeID)) {
                return resultScope;
            }

            /*
             * The order of the initialization nodes is very important. The defined symbols and the
             * external symbols must be initialized before the global symbols can be initialized.
             * The overwriting of symbols can only be done once all the globals are initialised and
             * the symbol table has been allocated.
             */
            if (LLVMLoadingPhase.INIT_SYMBOLS.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseSymbol(context, visited);
            }

            if (LLVMLoadingPhase.INIT_EXTERNALS.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseExternal(context, visited, localScope);
            }

            if (LLVMLoadingPhase.INIT_GLOBALS.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseGlobals(context, visited, frame);
            }

            if (LLVMLoadingPhase.INIT_OVERWRITE.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseOverwrite(context, visited, localScope);
            }

            if (LLVMLoadingPhase.INIT_CONTEXT.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseContext(visited, frame);
            }

            if (LLVMLoadingPhase.INIT_MODULE.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeInitialiseModule(context, visited, frame);
            }

            if (LLVMLoadingPhase.INIT_DONE.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }
                executeDone(context, visited);
            }

            if (LLVMLoadingPhase.ALL == phase) {
                return resultScope;
            }
            return null;
        } finally {
            stackAccess.executeExit(frame);
        }
    }

    @TruffleBoundary
    private void executeInitialiseSymbol(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_SYMBOLS, visited);
                }
            }
            initSymbols.initializeSymbolTable(context);
            initSymbols.execute(context);
        }
    }

    @TruffleBoundary
    private void executeInitialiseExternal(LLVMContext context, BitSet visited, LLVMLocalScope localScope) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_EXTERNALS, visited, localScope);
                }
            }
            initExternals.execute(context, localScope);
        }
    }

    private void executeInitialiseGlobals(LLVMContext context, BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_GLOBALS, visited);
                }
            }
            initGlobals.execute(frame, context.getReadOnlyGlobals(bitcodeID));
        }
    }

    @TruffleBoundary
    private void executeInitialiseOverwrite(LLVMContext context, BitSet visited, LLVMLocalScope localScope) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_OVERWRITE, visited, localScope);
                }
            }
        }
        initOverwrite.execute(context, localScope);
    }

    private void executeInitialiseContext(BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_CONTEXT, visited);
                }
            }
            initContext.execute(frame);
        }
    }

    private void executeInitialiseModule(LLVMContext context, BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_MODULE, visited);
                }
            }
            initModules.execute(frame, context);
        }
    }

    @TruffleBoundary
    private void executeDone(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (CallTarget d : dependencies) {
                if (d != null) {
                    callDependencies.call(d, LLVMLoadingPhase.INIT_DONE, visited);
                }
            }
            context.markLibraryLoaded(bitcodeID);
        }
    }

    @TruffleBoundary
    private static void queAdd(ArrayDeque<CallTarget> que, CallTarget callTarget) {
        que.add(callTarget);
    }

    @TruffleBoundary
    private BitSet createBitset() {
        return new BitSet(dependencies.length);
    }

    @TruffleBoundary
    private static void addIDToLocalScope(LLVMLocalScope localScope, int id) {
        localScope.addID(id);
    }

    @TruffleBoundary
    private static LLVMLocalScope createLocalScope() {
        return new LLVMLocalScope();
    }

    @TruffleBoundary
    private static LLVMScope createLLVMScope() {
        return new LLVMScope();
    }

    /**
     * Retrieves the function for the main method.
     */
    private LLVMFunction findMainFunction() {
        // check if the freshly parsed code exports a main method
        LLVMScope fileScope = parserRuntime.getFileScope();
        LLVMSymbol mainSymbol = fileScope.get(MAIN_METHOD_NAME);

        if (mainSymbol != null && mainSymbol.isFunction()) {
            /*
             * The `isLLVMIRFunction` check makes sure the `main` function is really defined in
             * bitcode. This prevents us from finding a native `main` function (e.g. the `main` of
             * the VM we're running in).
             */

            LLVMFunction mainFunction = mainSymbol.asFunction();
            if (mainFunction.getFunction() instanceof LLVMFunctionCode.LLVMIRFunction || mainFunction.getFunction() instanceof LLVMFunctionCode.LazyLLVMIRFunction) {
                return mainFunction;
            }
        }
        return null;
    }
}
