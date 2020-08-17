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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.StackManager;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.SulongLibrary;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * The {@link LoadModulesNode} initialise all libraries. This involves building the scopes (local
 * scope and global scope), allocating the symbol table, resolving external symbols, resolving
 * symbol resolution, allocating global symbols, initialising the context, and initialising the
 * constructor.
 *
 * At the start of initialisation the dependencies of the library is parsed, the start function and
 * main function are created, and the context initialisation and dispose symbols are defined. This
 * is only ever done once per library. (Soon the main function will be created lazily as it is not
 * necessary to look up the main function for every library.)
 *
 * The initialisation of a library is broken down into nine phases. The scope building phase, the
 * (defined) symbol initialisation phase, the external symbol initialisation phase, creating the
 * global symbols,
 *
 * one scope phase six initialise phase one done/mark phase
 *
 */
public final class LoadModulesNode extends RootNode {

    private static final String MAIN_METHOD_NAME = "main";
    private static final String START_METHOD_NAME = "_start";

    @CompilerDirectives.CompilationFinal RootCallTarget mainFunctionCallTarget;
    final FrameSlot stackPointerSlot;
    final String sourceName;
    final int bitcodeID;
    final Source source;
    @CompilerDirectives.CompilationFinal TruffleLanguage.ContextReference<LLVMContext> ctxRef;

    @Child LLVMStatementNode initContext;

    @Child InitializeSymbolsNode initSymbols;
    @Child InitializeScopeNode initScopes;
    @Child InitializeExternalNode initExternals;
    @Child InitializeGlobalNode initGlobals;
    @Child InitializeOverwriteNode initOverwrite;
    @Child InitializeModuleNode initModules;
    @Child IndirectCallNode indirectCall;

    @Children DirectCallNode[] dependencies;
    final CallTarget[] callTargets;
    final List<Source> dependenciesSource;
    final LLVMParserResult parserResult;
    final LLVMLanguage language;
    private boolean hasInitialised;

    private enum LLVMLoadingPhase {
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

    private LoadModulesNode(String name, LLVMParserResult parserResult, LLVMContext context,
                    FrameDescriptor rootFrame, boolean lazyParsing, List<Source> dependenciesSource, Source source, LLVMLanguage language) throws Type.TypeOverflowException {

        super(language, rootFrame);
        this.mainFunctionCallTarget = null;
        this.sourceName = name;
        this.source = source;
        this.bitcodeID = parserResult.getRuntime().getBitcodeID();
        this.stackPointerSlot = rootFrame.findFrameSlot(LLVMStack.FRAME_ID);
        this.parserResult = parserResult;
        this.dependenciesSource = dependenciesSource;
        this.language = language;
        this.callTargets = new CallTarget[dependenciesSource.size()];
        this.dependencies = new DirectCallNode[dependenciesSource.size()];
        this.hasInitialised = false;

        this.initContext = null;
        String moduleName = parserResult.getRuntime().getLibrary().toString();
        this.initSymbols = new InitializeSymbolsNode(parserResult, parserResult.getRuntime().getNodeFactory(), lazyParsing,
                        isInternalSulongLibrary(context, parserResult.getRuntime().getLibrary()), moduleName);
        this.initScopes = new InitializeScopeNode(parserResult);
        this.initExternals = new InitializeExternalNode(parserResult);
        this.initGlobals = new InitializeGlobalNode(rootFrame, parserResult, moduleName);
        this.initOverwrite = new InitializeOverwriteNode(parserResult);
        this.initModules = new InitializeModuleNode(language, parserResult, moduleName);
        this.indirectCall = IndirectCallNode.create();
    }

    @Override
    public String getName() {
        return '<' + getClass().getSimpleName() + '>';
    }

    @Override
    public SourceSection getSourceSection() {
        return source.createUnavailableSection();
    }

    public static LoadModulesNode create(String name, LLVMParserResult parserResult, FrameDescriptor rootFrame,
                    boolean lazyParsing, LLVMContext context, List<Source> dependencySources, Source source, LLVMLanguage language) {
        LoadModulesNode node = null;
        try {
            node = new LoadModulesNode(name, parserResult, context, rootFrame, lazyParsing, dependencySources, source, language);
            return node;
        } catch (Type.TypeOverflowException e) {
            throw new LLVMUnsupportedException(node, LLVMUnsupportedException.UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
        }
    }

    @ExplodeLoop
    @SuppressWarnings("unchecked")
    private LLVMScope loadModule(VirtualFrame frame,
                    @CachedContext(LLVMLanguage.class) LLVMContext context) {

        try (LLVMStack.StackPointer stackPointer = ctxRef.get().getThreadingStack().getStack().newFrame()) {
            frame.setObject(stackPointerSlot, stackPointer);

            LLVMLoadingPhase phase;
            LLVMLocalScope localScope = null;
            BitSet visited;
            ArrayDeque<CallTarget> que = null;
            LLVMScope resultScope = null;

            if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                phase = (LLVMLoadingPhase) frame.getArguments()[0];
                visited = (BitSet) frame.getArguments()[1];
                if (phase == LLVMLoadingPhase.BUILD_SCOPES) {
                    localScope = (LLVMLocalScope) frame.getArguments()[2];
                    que = (ArrayDeque<CallTarget>) frame.getArguments()[3];
                    resultScope = (LLVMScope) frame.getArguments()[4];
                }
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
                    resultScope.addMissingEntries(parserResult.getRuntime().getFileScope());
                    for (CallTarget callTarget : callTargets) {
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
                executeInitialiseExternal(context, visited);
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
                executeInitialiseOverwrite(context, visited);
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
        }
    }

    private void executeInitialiseSymbol(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_SYMBOLS, visited);
                }
            }
            initSymbols.initializeSymbolTable(context);
            initSymbols.execute(context);
        }
    }

    private void executeInitialiseExternal(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_EXTERNALS, visited);
                }
            }
            initExternals.execute(context, bitcodeID);
        }
    }

    private void executeInitialiseGlobals(LLVMContext context, BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_GLOBALS, visited);
                }
            }
            initGlobals.execute(frame, context.getReadOnlyGlobals(bitcodeID));
        }
    }

    private void executeInitialiseOverwrite(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_OVERWRITE, visited);
                }
            }
        }
        initOverwrite.execute(context, bitcodeID);
    }

    private void executeInitialiseContext(BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_CONTEXT, visited);
                }
            }
            initContext.execute(frame);
        }
    }

    private void executeInitialiseModule(LLVMContext context, BitSet visited, VirtualFrame frame) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_MODULE, visited);
                }
            }
            initModules.execute(frame, context);
        }
    }

    private void executeDone(LLVMContext context, BitSet visited) {
        if (!visited.get(bitcodeID)) {
            visited.set(bitcodeID);
            for (DirectCallNode d : dependencies) {
                if (d != null) {
                    d.call(LLVMLoadingPhase.INIT_DONE, visited);
                }
            }
            context.markLibraryLoaded(bitcodeID);
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
                    // native dependencies are null
                    if (dependenciesSource.get(i) != null) {
                        CallTarget callTarget = context.getEnv().parseInternal(dependenciesSource.get(i));
                        dependencies[i] = DirectCallNode.create(callTarget);
                        // The call targets are needed for initialising the scope.
                        callTargets[i] = callTarget;
                    }
                }
                // TODO (PLi): The main function can be created lazliy. The start function, the
                // context initialise and
                // dispose symbols should only be set once, when libsulong is first parsed.
                LLVMFunctionDescriptor startFunctionDescriptor = findAndSetSulongSpecificFunctions(language, context);
                LLVMFunction mainFunction = findMainFunction(parserResult);
                if (mainFunction != null) {
                    RootCallTarget startCallTarget = startFunctionDescriptor.getFunctionCode().getLLVMIRFunctionSlowPath();
                    Path applicationPath = mainFunction.getLibrary().getPath();
                    RootNode rootNode = new LLVMGlobalRootNode(language, StackManager.createRootFrame(), mainFunction, startCallTarget, Objects.toString(applicationPath, ""));
                    mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(rootNode);
                }
                /*
                 * check if the context already contain the initialization context -- only for
                 * libsulong. only set once per context, only insert if it's not there.
                 */
                initContext = this.insert(context.createInitializeContextNode(getFrameDescriptor()));
                hasInitialised = true;
            }

            LLVMScope scope = loadModule(frame, context);

            // or just check that scope is not null,
            if (frame.getArguments().length == 0 || !(frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                assert scope != null;
                return new SulongLibrary(sourceName, scope, mainFunctionCallTarget, context);
            }
        }
        return null;
    }

    @CompilerDirectives.TruffleBoundary
    private static void queAdd(ArrayDeque<CallTarget> que, CallTarget callTarget) {
        que.add(callTarget);
    }

    @CompilerDirectives.TruffleBoundary
    private BitSet createBitset() {
        return new BitSet(dependencies.length);
    }

    @CompilerDirectives.TruffleBoundary
    private static void addIDToLocalScope(LLVMLocalScope localScope, int id) {
        localScope.addID(id);
    }

    @CompilerDirectives.TruffleBoundary
    private static LLVMLocalScope createLocalScope() {
        return new LLVMLocalScope();
    }

    @CompilerDirectives.TruffleBoundary
    private static LLVMScope createLLVMScope() {
        return new LLVMScope();
    }

    // A library is a sulong internal library if it contains the path of the internal llvm
    // library directory
    private static boolean isInternalSulongLibrary(LLVMContext context, ExternalLibrary library) {
        Path internalPath = context.getInternalLibraryPath();
        return library.getPath().startsWith(internalPath);
    }

    /**
     * Retrieves the function for the main method.
     */
    private static LLVMFunction findMainFunction(LLVMParserResult parserResult) {
        // check if the freshly parsed code exports a main method
        LLVMScope fileScope = parserResult.getRuntime().getFileScope();
        LLVMSymbol mainSymbol = fileScope.get(MAIN_METHOD_NAME);

        if (mainSymbol != null && mainSymbol.isFunction() && mainSymbol.isDefined()) {
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

    /**
     * Find, create, and return the function descriptor for the start method. As well as set the
     * sulong specific symbols __sulong_init_context and __sulong_dispose_context to the context.
     *
     * @return The function descriptor for the start function.
     */
    protected static LLVMFunctionDescriptor findAndSetSulongSpecificFunctions(LLVMLanguage language, LLVMContext context) {

        LLVMFunctionDescriptor startFunction;
        LLVMSymbol initContext;
        LLVMSymbol disposeContext;
        LLVMScope fileScope = language.getInternalFileScopes("libsulong");

        LLVMSymbol function = fileScope.get(START_METHOD_NAME);
        if (function != null && function.isDefined()) {
            startFunction = context.createFunctionDescriptor(function.asFunction());
        } else {
            throw new IllegalStateException("Context cannot be initialized: start function, " + START_METHOD_NAME + ", was not found in sulong libraries");
        }

        LLVMSymbol tmpInitContext = fileScope.get(LLVMContext.SULONG_INIT_CONTEXT);
        if (tmpInitContext != null && tmpInitContext.isDefined() && tmpInitContext.isFunction()) {
            initContext = tmpInitContext;
        } else {
            throw new IllegalStateException("Context cannot be initialized: " + LLVMContext.SULONG_INIT_CONTEXT + " was not found in sulong libraries");
        }

        LLVMSymbol tmpDisposeContext = fileScope.get(LLVMContext.SULONG_DISPOSE_CONTEXT);
        if (tmpDisposeContext != null && tmpDisposeContext.isDefined() && tmpDisposeContext.isFunction()) {
            disposeContext = tmpDisposeContext;
        } else {
            throw new IllegalStateException("Context cannot be initialized: " + LLVMContext.SULONG_DISPOSE_CONTEXT + " was not found in sulong libraries");
        }

        context.setSulongInitContext(initContext.asFunction());
        context.setSulongDisposeContext(disposeContext.asFunction());
        return startFunction;
    }

}
