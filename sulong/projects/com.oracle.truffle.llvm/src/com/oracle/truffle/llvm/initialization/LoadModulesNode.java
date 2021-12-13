/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.SulongLibrary;
import com.oracle.truffle.llvm.runtime.SulongLibrary.CachedMainFunction;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The {@link LoadModulesNode} initialise the library. This involves building the scopes (local
 * scope and global scope), allocating the symbol table, resolving external symbols, resolving
 * symbol resolution, allocating global symbols, initialising the context, and initialising the
 * constructor.
 * <p>
 * At the start of initialisation process the dependencies of the library are parsed, the start
 * function and main function are created, and the context initialisation and dispose symbols are
 * defined. This is only done once per library.
 * <p>
 * The initialisation of a library is broken down into nine phases. The scope building phase, the
 * (defined) symbol initialisation phase, the external symbol initialisation phase, the global
 * symbol creation phase, the symbol resolution phase, the constructor initialisation phase, and
 * finally the caching or the done phase.
 * <p>
 * The initialisation is done calling the loadModule method, the dependencies of the library are
 * initialised by recursively call the loadModule method with the specific path that is being
 * initialised, i.e. INIT_EXTERNALS for external symbol initialisation. Once the initialisation has
 * been completed only the root library will return the non-null scope for the initialised library,
 * while the dependencies will return null.
 */
public final class LoadModulesNode extends LLVMRootNode {

    private static final String MAIN_METHOD_NAME = "main";

    @CompilationFinal RootCallTarget mainFunctionCallTarget;
    final String libraryName;
    final BitcodeID bitcodeID;
    final Source source;

    @Child LLVMStatementNode initContext;
    @Child InitializeSymbolsNode initSymbols;
    @Child InitializeExternalNode initExternals;
    @Child InitializeGlobalNode initGlobals;
    @Child InitializeOverwriteNode initOverwrite;
    @Child InitializeModuleNode initModules;
    @Child IndirectCallNode indirectCall;

    @Child IndirectCallNode callDependencies;
    @Children final LoadDependencyNode[] libraryDependencies;
    final LLVMParserRuntime parserRuntime;
    final LLVMLanguage language;
    private boolean hasInitialised;
    @CompilationFinal private CachedMainFunction main;

    protected enum LLVMLoadingPhase {
        ALL,
        BUILD_SCOPES,
        BUILD_DEPENDENCY,
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
                    FrameDescriptor rootFrame, boolean lazyParsing, List<LoadDependencyNode> libraryDependencies, Source source, LLVMLanguage language) throws Type.TypeOverflowException {
        super(language, rootFrame, parserResult.getRuntime().getNodeFactory().createStackAccess());
        this.mainFunctionCallTarget = null;
        this.libraryName = name;
        this.source = source;
        this.bitcodeID = parserResult.getRuntime().getBitcodeID();
        this.parserRuntime = parserResult.getRuntime();
        this.libraryDependencies = libraryDependencies.toArray(LoadDependencyNode.EMPTY);
        this.language = language;
        this.hasInitialised = false;
        this.initContext = null;
        this.initSymbols = new InitializeSymbolsNode(parserResult, lazyParsing, isInternalSulongLibrary, libraryName);
        this.initExternals = new InitializeExternalNode(parserResult);
        this.initGlobals = new InitializeGlobalNode(parserResult, libraryName);
        this.initOverwrite = new InitializeOverwriteNode(parserResult);
        this.initModules = new InitializeModuleNode(language, parserResult, libraryName);
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

    public static LoadModulesNode create(String soName, LLVMParserResult parserResult,
                    boolean lazyParsing, boolean isInternalSulongLibrary, List<LoadDependencyNode> libraryDependencies, Source source, LLVMLanguage language) {
        try {
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            int stackId = builder.addSlot(FrameSlotKind.Object, null, null);
            assert stackId == LLVMStack.STACK_ID;
            return new LoadModulesNode(soName, parserResult, isInternalSulongLibrary, builder.build(), lazyParsing, libraryDependencies, source, language);
        } catch (Type.TypeOverflowException e) {
            throw new LLVMUnsupportedException(null, LLVMUnsupportedException.UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        LLVMContext context = getContext();
        synchronized (context) {
            if (!hasInitialised) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMFunction mainFunction = findMainFunction();
                if (mainFunction != null) {
                    main = new CachedMainFunction(mainFunction);
                } else {
                    main = null;
                }
                initContext = this.insert(language.createInitializeContextNode());
                hasInitialised = true;
            }

            LLVMScopeChain firstScopeChain = loadModule(frame, context);
            context.addSourceForCache(bitcodeID, source);
            context.addCalltargetForCache(libraryName, this.getCallTarget());

            // Only the root library (not a dependency) will have a non-null scope.
            if (firstScopeChain != null) {
                SulongLibrary library = new SulongLibrary(this.libraryName, firstScopeChain, main, context, parserRuntime.getLocator(), parserRuntime.getBitcodeID());
                if (main != null) {
                    context.setMainLibrary(library);
                }
                return library;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private LLVMScopeChain loadModule(VirtualFrame frame, LLVMContext context) {

        stackAccess.executeEnter(frame, getContext().getThreadingStack().getStack());
        try {
            LLVMLoadingPhase phase;
            // instead of creating a llvm local scope, just create a llvm scope here, and then put
            // it inside the chains
            LLVMScopeChain headLocalScopeChain = null;
            LLVMScopeChain tailLocalScopeChain = null;
            BitSet visited = null;
            ArrayDeque<CallTarget> que = null;
            ArrayList<CallTarget> dependencies = null;
            LLVMScopeChain headResultScopeChain = null;
            LLVMScopeChain tailResultScopeChain = null;
            RTLDFlags localOrGlobal = RTLDFlags.RTLD_OPEN_DEFAULT;

            // check for arguments for dlOpen
            if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof RTLDFlags)) {
                localOrGlobal = (RTLDFlags) frame.getArguments()[0];
            }

            /*
             * The arguments in the frames for every loading phase include: 1) The current loading
             * phase. 2) The visited set. The scope building, the external symbol, and overwriting
             * symbol phases all require an additional argument: 3) The RTLD flag, if the library is
             * loaded with RTLD_LOCAL or RTLD_GLOBAL. The scope building phase also have: 4) The
             * tail of the linked structure for local scope. 5) The que of library dependencies. 6)
             * The tail of the linked structure for returning result scope. The external symbol and
             * overwriting symbol phases require instead: 4) The head of the linked structure for
             * local scope.
             */
            if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                phase = (LLVMLoadingPhase) frame.getArguments()[0];
                if (phase == LLVMLoadingPhase.BUILD_SCOPES || phase == LLVMLoadingPhase.BUILD_DEPENDENCY) {
                    visited = (BitSet) frame.getArguments()[1];
                }
                if (phase == LLVMLoadingPhase.INIT_EXTERNALS || phase == LLVMLoadingPhase.INIT_OVERWRITE) {
                    localOrGlobal = (RTLDFlags) frame.getArguments()[1];
                    headLocalScopeChain = (LLVMScopeChain) frame.getArguments()[2];
                }
                if (phase == LLVMLoadingPhase.BUILD_SCOPES) {
                    localOrGlobal = (RTLDFlags) frame.getArguments()[2];
                }
                if (phase == LLVMLoadingPhase.BUILD_DEPENDENCY) {
                    dependencies = (ArrayList<CallTarget>) frame.getArguments()[2];
                }
                // Additional arguments are required for building the scopes.
                if (phase == LLVMLoadingPhase.BUILD_SCOPES) {
                    tailLocalScopeChain = (LLVMScopeChain) frame.getArguments()[3];
                    que = (ArrayDeque<CallTarget>) frame.getArguments()[4];
                    tailResultScopeChain = (LLVMScopeChain) frame.getArguments()[5];
                }
                // For the root library, it is defined when either the frame has no argument, or
                // when the first argument is not one of the loading phases.
            } else if (frame.getArguments().length == 0 || !(frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                // create first and last of the scope chain.
                phase = LLVMLoadingPhase.ALL;
                headResultScopeChain = new LLVMScopeChain(bitcodeID, parserRuntime.getFileScope());
                tailResultScopeChain = headResultScopeChain;
                headLocalScopeChain = new LLVMScopeChain(bitcodeID, parserRuntime.getPublicFileScope());
                tailLocalScopeChain = headLocalScopeChain;
                visited = createBitset(libraryDependencies.length);
                que = new ArrayDeque<>();
                dependencies = new ArrayList<>();
            } else {
                throw new LLVMParserException("LoadModulesNode is called with unexpected arguments");
            }

            /*
             * The scope is built in parsing order, which requires breadth-first with a que.
             */
            if (LLVMLoadingPhase.BUILD_SCOPES.isActive(phase)) {
                int id = bitcodeID.getId();
                if (!visited.get(id)) {
                    visited.set(id);
                    if (LLVMLoadingPhase.ALL.isActive(phase)) {
                        context.addGlobalScope(new LLVMScopeChain(bitcodeID, parserRuntime.getPublicFileScope()));
                    } else {
                        LLVMScopeChain currentLocalScopeChain = new LLVMScopeChain(bitcodeID, parserRuntime.getPublicFileScope());
                        LLVMScopeChain currentResultScopeChain = new LLVMScopeChain(bitcodeID, parserRuntime.getFileScope());
                        if (RTLDFlags.RTLD_OPEN_DEFAULT.isActive(localOrGlobal)) {
                            context.addGlobalScope(new LLVMScopeChain(bitcodeID, parserRuntime.getPublicFileScope()));
                            tailLocalScopeChain.concatNextChain(currentLocalScopeChain);
                            tailResultScopeChain.concatNextChain(currentResultScopeChain);
                        } else if (RTLDFlags.RTLD_LOCAL.isActive(localOrGlobal)) {
                            tailLocalScopeChain.concatNextChain(currentLocalScopeChain);
                        } else if (RTLDFlags.RTLD_GLOBAL.isActive(localOrGlobal)) {
                            tailLocalScopeChain.concatNextChain(currentLocalScopeChain);
                            context.addGlobalScope(new LLVMScopeChain(bitcodeID, parserRuntime.getPublicFileScope()));
                        } else {
                            throw new LLVMParserException(this, "Toplevel executable %s does not contain bitcode");
                        }
                    }

                    for (int i = 0; i < libraryDependencies.length; i++) {
                        CallTarget callTarget = libraryDependencies[i].execute();
                        if (callTarget != null) {
                            queAdd(que, callTarget);
                        }
                    }

                    if (LLVMLoadingPhase.ALL.isActive(phase)) {
                        while (!que.isEmpty()) {
                            // Foward the tail scope chain to the latest scope chain.
                            while (tailLocalScopeChain != null && tailLocalScopeChain.getNext() != null) {
                                tailLocalScopeChain = tailLocalScopeChain.getNext();
                            }
                            while (tailResultScopeChain != null && tailResultScopeChain.getNext() != null) {
                                tailResultScopeChain = tailResultScopeChain.getNext();
                            }
                            indirectCall.call(quePoll(que), LLVMLoadingPhase.BUILD_SCOPES, visited, localOrGlobal, tailLocalScopeChain, que, tailResultScopeChain);
                        }
                    }
                }
            }

            if (context.isLibraryAlreadyLoaded(bitcodeID)) {
                if (RTLDFlags.RTLD_OPEN_DEFAULT.isActive(localOrGlobal)) {
                    return headResultScopeChain;
                } else {
                    return headLocalScopeChain;
                }
            }

            if (LLVMLoadingPhase.BUILD_DEPENDENCY.isActive(phase)) {
                if (LLVMLoadingPhase.ALL == phase) {
                    visited.clear();
                }

                int id = bitcodeID.getId();
                if (!visited.get(id)) {
                    visited.set(id);
                    for (LoadDependencyNode libraryDependency : libraryDependencies) {
                        CallTarget lib = libraryDependency.execute();
                        if (lib != null) {
                            callDependencies.call(lib, LLVMLoadingPhase.BUILD_DEPENDENCY, visited, dependencies);
                        }
                    }
                    dependencies.add(this.getCallTarget());
                }
            }

            /*
             * The order of the initialization nodes is very important. The defined symbols and the
             * external symbols must be initialized before the global symbols can be initialized.
             * The overwriting of symbols can only be done once all the globals are initialized and
             * the symbol table has been allocated.
             */
            switch (phase) {
                case BUILD_SCOPES:
                case BUILD_DEPENDENCY:
                    break;
                case ALL:
                    assert dependencies != null;
                    executeInitialiseAllPhase(dependencies, localOrGlobal, headLocalScopeChain);
                    break;
                case INIT_SYMBOLS:
                    executeInitialiseSymbolPhase(context);
                    break;
                case INIT_EXTERNALS:
                    initExternals.execute(context, headLocalScopeChain, localOrGlobal);
                    break;
                case INIT_GLOBALS:
                    initGlobals.execute(frame, context.getReadOnlyGlobals(bitcodeID));
                    break;
                case INIT_OVERWRITE:
                    initOverwrite.execute(context, headLocalScopeChain, localOrGlobal);
                    break;
                case INIT_CONTEXT:
                    initContext.execute(frame);
                    break;
                case INIT_MODULE:
                    initModules.execute(frame, context);
                    break;
                case INIT_DONE:
                    context.markLibraryLoaded(bitcodeID);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("Unknown loading phase");
            }

            if (LLVMLoadingPhase.ALL == phase) {
                if (RTLDFlags.RTLD_OPEN_DEFAULT.isActive(localOrGlobal)) {
                    return headResultScopeChain;
                } else {
                    return headLocalScopeChain;
                }
            }
            return null;
        } finally {
            stackAccess.executeExit(frame);
        }
    }

    @TruffleBoundary
    private void executeInitialiseAllPhase(ArrayList<CallTarget> dependencies, RTLDFlags rtldFlags, LLVMScopeChain scopeChain) {
        assert dependencies != null;
        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_SYMBOLS);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_EXTERNALS, rtldFlags, scopeChain);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_GLOBALS);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_OVERWRITE, rtldFlags, scopeChain);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_CONTEXT);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_MODULE);
        }

        for (CallTarget callTarget : dependencies) {
            callDependencies.call(callTarget, LLVMLoadingPhase.INIT_DONE);
        }
    }

    @TruffleBoundary
    private void executeInitialiseSymbolPhase(LLVMContext context) {
        initSymbols.initializeSymbolTable(context);
        initSymbols.execute(context);
    }

    @TruffleBoundary
    private static void queAdd(ArrayDeque<CallTarget> que, CallTarget callTarget) {
        que.add(callTarget);
    }

    @TruffleBoundary
    private static CallTarget quePoll(ArrayDeque<CallTarget> que) {
        return que.poll();
    }

    @TruffleBoundary
    private static BitSet createBitset(int length) {
        return new BitSet(length);
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
