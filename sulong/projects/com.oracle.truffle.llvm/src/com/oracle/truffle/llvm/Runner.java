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
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.RunnerFactory.AllocExistingLocalSymbolsNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.AllocExternalFunctionNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.AllocExternalGlobalNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.StaticInitsNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.StackManager;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import com.oracle.truffle.llvm.parser.factories.BasicPlatformCapability;
import com.oracle.truffle.llvm.parser.factories.PlatformCapabilityBase;
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
import com.oracle.truffle.llvm.runtime.LLVMGetStackFromFrameNode;
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
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
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
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.CONSTRUCTORS_VARNAME;
import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.DESTRUCTORS_VARNAME;

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
    public static CallTarget parse(LLVMContext context, AtomicInteger bitcodeID, Source source) {
        return new Runner(context, bitcodeID).parseWithDependencies(source);
    }

    private static final String MAIN_METHOD_NAME = "main";
    private static final String START_METHOD_NAME = "_start";
    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() - p2.getFirst();
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p2.getFirst() - p1.getFirst();

    private final LLVMContext context;
    private final LLVMLanguage language;
    private final AtomicInteger nextFreeBitcodeID;

    private Runner(LLVMContext context, AtomicInteger moduleID) {
        this.context = context;
        this.language = context.getLanguage();
        this.nextFreeBitcodeID = moduleID;
    }

    /**
     * Parse bitcode data and do first initializations to prepare bitcode execution.
     */
    private CallTarget parseWithDependencies(Source source) {
        ByteSequence bytes;
        ExternalLibrary library;
        if (source.hasBytes()) {
            bytes = source.getBytes();
            if (language.containsInternalExternalLibrary(source)) {
                library = language.getinternalExternalLibrary(source);
            } else if (source.getPath() != null) {
                library = ExternalLibrary.createFromFile(context.getEnv().getInternalTruffleFile(source.getPath()), false, source.isInternal());
            } else {
                library = ExternalLibrary.createFromName("<STREAM-" + UUID.randomUUID().toString() + ">", false, source.isInternal());
            }
        } else if (source.hasCharacters()) {
            throw new LLVMParserException("Unexpected character-based source with mime type: " + source.getMimeType());
        } else {
            throw new LLVMParserException("Should not reach here: Source is neither char-based nor byte-based!");
        }
        return parseWithDependencies(source, bytes, library);
    }

    /**
     * {@link InitializeSymbolsNode} creates the symbol of all defined functions and globals, and
     * put them into the symbol table. {@link InitializeGlobalNode} initializes the value of all
     * defined global symbols.
     * <p>
     * {@link InitializeExternalNode} initializes the symbol table for all the external symbols of
     * this module. For external functions, if they are already defined in the local scope or the
     * global scope, then the already defined symbol is placed into this function's spot in the
     * symbol table. Otherwise, an instrinc or native function is created if they exists. Similarly,
     * for external globals the local and global scope is checked first for this external global,
     * and if it exists, then the defined global symbol from the local/global scope is placed into
     * this external global's location in the symbol table.
     * <p>
     * The aim of {@link InitializeOverwriteNode} is to identify which defined symbols will be
     * resolved to their corresponding symbol in the local scope when they are called. If they
     * resolve to the symbol in the local scope then this symbol from the local scope is place into
     * this defined symbol's location in the symbol table. This means the local and global scope is
     * no longer required for symbol resolution, and everything is done simply by looking up the
     * symbol in the file scope.
     */
    private static final class LoadModulesNode extends RootNode {

        @CompilationFinal RootCallTarget mainFunctionCallTarget;
        final FrameSlot stackPointerSlot;
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

        @Children DirectCallNode[] dependencies; // pointing to the CallTargets of our dependencies
        final CallTarget[] callTargets;
        final List<Source> sources;
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
            INIT_DONE,
            BUILD_RETURN_SCOPES;

            boolean isActive(LLVMLoadingPhase phase) {
                return phase == this || phase == ALL;
            }
        }

        private LoadModulesNode(Runner runner, String name, LLVMParserResult parserResult, LLVMContext context,
                        FrameDescriptor rootFrame, boolean lazyParsing, List<Source> sources, Source source, LLVMLanguage language) throws TypeOverflowException {

            super(runner.language, rootFrame);
            this.mainFunctionCallTarget = null;
            this.sourceName = name;
            this.source = source;
            this.bitcodeID = parserResult.getRuntime().getBitcodeID();
            this.stackPointerSlot = rootFrame.findFrameSlot(LLVMStack.FRAME_ID);
            this.parserResult = parserResult;
            this.sources = sources;
            this.language = language;
            this.callTargets = new CallTarget[sources.size()];
            this.dependencies = new DirectCallNode[sources.size()];
            this.hasInitialised = false;

            this.initContext = null;
            String moduleName = parserResult.getRuntime().getLibrary().toString();
            this.initSymbols = new InitializeSymbolsNode(parserResult, parserResult.getRuntime().getNodeFactory(), lazyParsing,
                            isInternalSulongLibrary(context, parserResult.getRuntime().getLibrary()), moduleName);
            this.initScopes = new InitializeScopeNode(parserResult);
            this.initExternals = new InitializeExternalNode(parserResult);
            this.initGlobals = new InitializeGlobalNode(rootFrame, parserResult, moduleName);
            this.initOverwrite = new InitializeOverwriteNode(parserResult);
            this.initModules = new InitializeModuleNode(runner, parserResult, moduleName);
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

        protected static LoadModulesNode create(String name, Runner runner, LLVMParserResult parserResult, FrameDescriptor rootFrame,
                        boolean lazyParsing, LLVMContext context, List<Source> sources, Source source, LLVMLanguage language) {
            LoadModulesNode node = null;
            try {
                node = new LoadModulesNode(runner, name, parserResult, context, rootFrame, lazyParsing, sources, source, language);
                return node;
            } catch (TypeOverflowException e) {
                throw new LLVMUnsupportedException(node, UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
            }
        }

        @ExplodeLoop
        @SuppressWarnings("unchecked")
        LLVMScope loadModule(VirtualFrame frame,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {

            try (StackPointer stackPointer = ctxRef.get().getThreadingStack().getStack().newFrame()) {
                frame.setObject(stackPointerSlot, stackPointer);

                LLVMLoadingPhase phase;
                LLVMLocalScope localScope = null;
                BitSet visited;
                ArrayDeque<CallTarget> que = null;
                LLVMScope resultScope = null;
                // if no argument or first argument is not an enum of ours then it is a root, and we
                // will need to create a fresh global scope, what do we do for local scope?
                if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                    phase = (LLVMLoadingPhase) frame.getArguments()[0];
                    visited = (BitSet) frame.getArguments()[1];
                    if (phase == LLVMLoadingPhase.BUILD_SCOPES) {
                        localScope = (LLVMLocalScope) frame.getArguments()[2];
                        que = (ArrayDeque<CallTarget>) frame.getArguments()[3];
                        resultScope = (LLVMScope) frame.getArguments()[4];
                    } /*else if (phase == LLVMLoadingPhase.BUILD_RETURN_SCOPES) {
                        resultScope = (LLVMScope) frame.getArguments()[2];
                    }*/
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

                // The scope is built breadth-first with a que
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

                        if(LLVMLoadingPhase.ALL.isActive(phase)) {
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
                 * The ordering of executing these four initialization nodes is very important. The
                 * defined symbols and the external symbols must be initialized before (the value
                 * in) the global symbols can be initialized. The overwriting of symbols can only be
                 * done once all the globals are initialised and allocated in the symbol table.
                 */
                if (LLVMLoadingPhase.INIT_SYMBOLS.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.INIT_EXTERNALS.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.INIT_GLOBALS.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.INIT_OVERWRITE.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.INIT_CONTEXT.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.INIT_MODULE.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                // this should just be parsing order, right?
                /*if (LLVMLoadingPhase.BUILD_RETURN_SCOPES.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
                    if (!visited.get(bitcodeID)) {
                        visited.set(bitcodeID);
                        for (DirectCallNode d : dependencies) {
                            if (d != null) {
                                d.call(LLVMLoadingPhase.BUILD_RETURN_SCOPES, visited, resultScope);
                            }
                        }
                        resultScope.addMissingEntries(parserResult.getRuntime().getFileScope());
                    }
                }*/

                if (LLVMLoadingPhase.INIT_DONE.isActive(phase)) {
                    if (LLVMLoadingPhase.ALL == phase) {
                        visited.clear();
                    }
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

                if (LLVMLoadingPhase.ALL == phase) {
                    return resultScope;
                }

                return null;
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {

            if (ctxRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.ctxRef = lookupContextReference(LLVMLanguage.class);
            }

            LLVMContext context = ctxRef.get();

            if (!hasInitialised) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                for (int i = 0; i < sources.size(); i++) {
                    // null are native libraries
                    if (sources.get(i) != null) {
                        CallTarget callTarget = context.getEnv().parseInternal(sources.get(i));
                        dependencies[i] = DirectCallNode.create(callTarget);
                        callTargets[i] = callTarget;
                    }
                }

                if (frame.getArguments().length == 0) {
                    //This is only performed for the root node of the top level call target.
                    LLVMFunctionDescriptor startFunctionDescriptor = findAndSetSulongSpecificFunctions(language, context);
                    LLVMFunction mainFunction = findMainFunction(parserResult);
                    if (mainFunction != null) {
                        RootCallTarget startCallTarget = startFunctionDescriptor.getFunctionCode().getLLVMIRFunctionSlowPath();
                        Path applicationPath = mainFunction.getLibrary().getPath();
                        RootNode rootNode = new LLVMGlobalRootNode(language, StackManager.createRootFrame(), mainFunction, startCallTarget, Objects.toString(applicationPath, ""));
                        mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(rootNode);
                    }
                }

                initContext = this.insert(context.createInitializeContextNode(getFrameDescriptor()));
                hasInitialised = true;
            }

            LLVMScope scope = loadModule(frame, context);

            if (frame.getArguments().length == 0 || !(frame.getArguments()[0] instanceof LLVMLoadingPhase)) {
                assert scope != null;
                return new SulongLibrary(sourceName, scope, mainFunctionCallTarget, context);
            }

            return null;
        }

        @TruffleBoundary
        private static void queAdd(ArrayDeque<CallTarget> que, CallTarget callTarget) {
            que.add(callTarget);
        }

        @TruffleBoundary
        private BitSet createBitset() {
            // TODO based on the bitcode ID;
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

        // A library is a sulong internal library if it contains the path of the internal llvm
        // library directory
        private static boolean isInternalSulongLibrary(LLVMContext context, ExternalLibrary library) {
            Path internalPath = context.getInternalLibraryPath();
            return library.getPath().startsWith(internalPath);
        }

    }

    /**
     * Parses a bitcode module and all its dependencies and return a {@code CallTarget} that
     * performs all necessary module initialization on execute.
     */
    private CallTarget parseWithDependencies(Source source, ByteSequence bytes, ExternalLibrary library) {
        // process the bitcode file and its dependencies in the dynamic linking order

        ArrayList<Source> dependenciesSource = new ArrayList<>();

        getDefaultLibrariesForRootNode(dependenciesSource);
        LLVMParserResult result = parseLibraryWithSource(source, library, bytes, dependenciesSource);
        boolean isInternalLibrary = context.isInternalLibrary(library);

        if (result == null) {
            // if result is null, then it does not contain bitcode. (NFI can handle it later if it's
            // a native file.)
            NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
            if (nfiContextExtension != null) {
                nfiContextExtension.addNativeLibrary(library);
            }
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        }
        assert !library.isNative();

        // Need to clear libsulong++ if we are already in libsulong.
        removeCyclicDependency(source, dependenciesSource);

        if (isInternalLibrary) {
            String libraryName = getSimpleLibraryName(library.getName());
            // Add the file scope to the language
            language.addInternalFileScope(libraryName, result.getRuntime().getFileScope());

            if (libraryName.equals("libsulong")) {
                context.addLibsulongDataLayout(result.getDataLayout());
            }
        }


        // Need to do the renaming here, it is only for internal libraries.
        if (isInternalLibrary) {
            // renaming is attempted only for internal libraries.
            resolveRenamedSymbols(result, language, context);
        }

        addExternalSymbolsToScopes(result);
        return createLibraryCallTarget(source.getName(), result, dependenciesSource, source);
    }

    private void removeCyclicDependency(Source source, ArrayList<Source> dependenciesSource) {
        while (dependenciesSource.contains(source)) {
            dependenciesSource.remove(source);
        }

        if (source.getName().equals(BasicPlatformCapability.LIBSULONG_FILENAME)) {
            removeDependency(dependenciesSource, BasicPlatformCapability.LIBSULONGXX_FILENAME);
        }

        if (source.getName().equals(BasicPlatformCapability.LIBSULONGXX_FILENAME)) {
            removeDependency(dependenciesSource, BasicPlatformCapability.LIBSULONG_FILENAME);
        }

        if (context.getEnv().getOptions().get(SulongEngineOption.LOAD_CXX_LIBRARIES)) {
            // If the option --llvm.loadC++Libraries is used then libsulong++ will be loaded for
            // both
            // libc++ and libc++abi as default libraries. This will cause a cyclic dependency of
            // libsulong++ -> libc++ -> libsulong++ -> .. or
            // libsuling++ -> libc++ -> libc++abi -> libsulong++ -> ..
            if (source.getName().contains(PlatformCapabilityBase.LIBCXX_PREFIX)) {
                removeDependency(dependenciesSource, BasicPlatformCapability.LIBSULONGXX_FILENAME);
            } else if (source.getName().contains(PlatformCapabilityBase.LIBCXXABI_PREFIX)) {
                removeDependency(dependenciesSource, BasicPlatformCapability.LIBSULONGXX_FILENAME);
            }
        }
    }

    private static void removeDependency(ArrayList<Source> sources, String remove) {
        Source toRemove = null;
        for (Source dependency : sources) {
            if (dependency != null) {
                if (dependency.getName().equals(remove)) {
                    toRemove = dependency;
                }
            }
        }
        if (toRemove != null) {
            sources.remove(toRemove);
        }
    }

    /**
     * Allocating a symbol to the global and local scope of a module.
     */
    private static final class AllocScopeNode extends LLVMNode {

        static final AllocScopeNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocScopeNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        void allocateScope(LLVMContext context, LLVMLocalScope localScope) {
            LLVMScope globalScope = context.getGlobalScope();
            LLVMSymbol exportedSymbol = globalScope.get(symbol.getName());
            if (exportedSymbol == null) {
                globalScope.register(symbol);
            }
            LLVMSymbol exportedSymbolFromLocal = localScope.get(symbol.getName());
            if (exportedSymbolFromLocal == null) {
                localScope.register(symbol);
            }
        }
    }

    /**
     * The structure for allocating symbols to the symbol table is as follows:
     * {@link AllocExternalSymbolNode} is the top level node, with the execute method.
     * {@link AllocExistingLocalSymbolsNode} implements the case when the symbol exists in the local
     * scope, and it extends {@link AllocExternalSymbolNode}. {@link AllocExistingGlobalSymbolsNode}
     * implements the case when the symbol exists in the global scope, and it extends
     * {@link AllocExistingLocalSymbolsNode}. {@link AllocExternalGlobalNode} is for allocating a
     * native global symbol to the symbol take and {@link AllocExternalFunctionNode} is for
     * allocating an instrinsic or a native function into the symbol table, and they both extend
     * {@link AllocExistingGlobalSymbolsNode}.
     * <p>
     * {@link AllocExternalFunctionNode} is created for allocating external functions
     * {@link InitializeExternalNode}, which has four cases (the first two is covered by the
     * superclasses {@link AllocExistingGlobalSymbolsNode} and {@link AllocExistingLocalSymbolsNode}
     * ): 1) If the function is defined in the local scope. 2) If the function is defined in the
     * global scope. 3) if the function is an instrinsic function. 4) And finally, if the function
     * is a native function.
     * <p>
     * Similarly, {@link AllocExternalGlobalNode} is created for allocating external globals
     * {@link InitializeExternalNode}.
     * <p>
     * For overriding defined functions for symbol resolution {@link InitializeOverwriteNode},
     * {@link AllocExistingGlobalSymbolsNode} is created for overwriting global symbols as they can
     * be taken from the global and local scope, meanwhile {@link AllocExistingLocalSymbolsNode} is
     * created for ovewriting functions, as they can only be taken from the local scopes.
     */
    abstract static class AllocExternalSymbolNode extends LLVMNode {

        @SuppressWarnings("unused") static final AllocExternalSymbolNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocExternalSymbolNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);
    }

    /**
     * Allocating symbols to the symbol table as provided by the local scope.
     */
    abstract static class AllocExistingLocalSymbolsNode extends AllocExternalSymbolNode {

        AllocExistingLocalSymbolsNode(LLVMSymbol symbol) {
            super(symbol);
        }

        @Specialization(guards = {"cachedLocalSymbol != null", "localScope.get(symbol.getName()) == cachedLocalSymbol", "!(containsSymbol(cachedLocalSymbol))"})
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

        @Specialization(replaces = "allocateFromLocalScopeCached", guards = {"localScope.get(symbol.getName()) != null", "!(containsSymbol(localScope.get(symbol.getName())))"})
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

        @TruffleBoundary
        protected boolean containsSymbol(LLVMSymbol localSymbol) {
            return symbol.equals(localSymbol);
        }

        /**
         * Fallback for when the same symbol is being overwritten.
         *
         * There exists code where the symbol is not there.
         */
        @Fallback
        LLVMPointer allocateFromLocalScopeFallback(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NFIContextExtension nfiContextExtension) {
            return null;
        }

        @Override
        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);
    }

    /**
     * Allocating symbols to the symbol table as provided by the global scope.
     */
    abstract static class AllocExistingGlobalSymbolsNode extends AllocExistingLocalSymbolsNode {

        AllocExistingGlobalSymbolsNode(LLVMSymbol symbol) {
            super(symbol);
        }

        @Specialization(guards = {"localScope.get(symbol.getName()) == null", "cachedGlobalSymbol != null", "globalScope.get(symbol.getName()) == cachedGlobalSymbol",
                        "!(containsSymbol(cachedGlobalSymbol))"})
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

        @Specialization(replaces = "allocateFromGlobalScopeCached", guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) != null",
                        "!(containsSymbol(globalScope.get(symbol.getName())))"})
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

        @Override
        @TruffleBoundary
        protected boolean containsSymbol(LLVMSymbol globalSymbol) {
            return symbol.equals(globalSymbol);
        }

        @Override
        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);
    }

    /*
     * Allocates a managed pointer for the newly constructed function descriptors of a native
     * function and intrinsic function.
     */
    abstract static class AllocExternalFunctionNode extends AllocExistingGlobalSymbolsNode {

        private final NodeFactory nodeFactory;

        AllocExternalFunctionNode(LLVMSymbol symbol, NodeFactory nodeFactory) {
            super(symbol);
            this.nodeFactory = nodeFactory;
        }

        @TruffleBoundary
        @Specialization(guards = {"intrinsicProvider != null", "localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
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

        /*
         * Currently native functions/globals that are not in the nfi context are not written into
         * the symbol table. For function, another lookup will happen when something tries to call
         * the function. (see {@link LLVMDispatchNode#doCachedNative}) The function will be taken
         * from the filescope directly. Ideally the filescope and symbol table is in sync, and any
         * lazy look up will resolve from the function code in the symbol table.
         */
        @TruffleBoundary
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

        @Override
        public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NFIContextExtension nfiContextExtension);

    }

    /**
     * Allocating a native global symbol to the symbol table as provided by the nfi context.
     */
    abstract static class AllocExternalGlobalNode extends AllocExistingGlobalSymbolsNode {

        AllocExternalGlobalNode(LLVMSymbol symbol) {
            super(symbol);
        }

        @TruffleBoundary
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
                StructureType structType = StructureType.createNamedFromList(typeName, true, types);
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
        final String moduleName;

        @Children final AllocSymbolNode[] allocFuncs;

        private final LLVMScope fileScope;
        private NodeFactory nodeFactory;

        private final int bitcodeID;
        private final int globalLength;

        InitializeSymbolsNode(LLVMParserResult result, NodeFactory nodeFactory, boolean lazyParsing, boolean isInternalSulongLibrary, String moduleName) throws TypeOverflowException {
            DataLayout dataLayout = result.getDataLayout();
            this.nodeFactory = nodeFactory;
            this.fileScope = result.getRuntime().getFileScope();
            this.checkGlobals = LLVMCheckSymbolNodeGen.create();
            this.globalLength = result.getSymbolTableSize();
            this.bitcodeID = result.getRuntime().getBitcodeID();
            this.moduleName = moduleName;

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
                // Internal libraries in the llvm library path are allowed to have intriniscs.
                if (isInternalSulongLibrary && intrinsicProvider.isIntrinsified(function.getName())) {
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
        }

        /*
         * public boolean shouldInitialize(LLVMContext ctx) { return !ctx.isScopeLoaded(fileScope);
         * }
         */

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void initializeSymbolTable(LLVMContext context) {
            context.registerSymbolTable(bitcodeID, new AssumedValue[globalLength]);
            context.registerScope(fileScope);
        }

        public LLVMPointer execute(LLVMContext ctx) {
            if (ctx.loaderTraceStream() != null) {
                LibraryLocator.traceStaticInits(ctx, "symbol initializers", moduleName);
            }
            LLVMPointer roBase = allocOrNull(allocRoSection);
            LLVMPointer rwBase = allocOrNull(allocRwSection);

            allocGlobals(ctx, roBase, rwBase);
            allocFunctions(ctx);

            if (allocRoSection != null) {
                ctx.registerReadOnlyGlobals(bitcodeID, roBase, nodeFactory);
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
        private void allocFunctions(LLVMContext ctx) {
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

    /**
     * Globals of pointer type need to be handles specially because they can potentially contain a
     * foreign object.
     */
    private static boolean isSpecialGlobalSlot(Type type) {
        return type instanceof PointerType;
    }

    private ArrayList<Source> getDefaultLibrariesForRootNode(ArrayList<Source> sourceDependencies) {
        // There could be conflicts between Sulong's default libraries and the ones that are
        // passed on the command-line. To resolve that, we add ours first but parse them later on.
        String[] sulongLibraryNames = language.getCapability(PlatformCapability.class).getSulongDefaultLibraries();
        for (int i = 0; i < sulongLibraryNames.length; i++) {
            sourceDependencies.add(createDependencySource(context.addInternalLibrary(sulongLibraryNames[i], "<default bitcode library>")));
        }

        // parse all libraries that were passed on the command-line
        List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(context.getEnv());
        for (String external : externals) {
            sourceDependencies.add(createDependencySource(context.addExternalLibraryDefaultLocator(external, "<command line>")));
        }
        return sourceDependencies;
    }

    /**
     * Marker for renamed symbols. Keep in sync with `sulong-internal.h`.
     */
    static final String SULONG_RENAME_MARKER = "___sulong_import_";
    static final int SULONG_RENAME_MARKER_LEN = SULONG_RENAME_MARKER.length();

    protected static ArrayList<LLVMFunction> resolveRenamedSymbols(LLVMParserResult parserResult, LLVMLanguage language, LLVMContext context) {
        ListIterator<FunctionSymbol> it = parserResult.getExternalFunctions().listIterator();
        ArrayList<LLVMFunction> functions = new ArrayList<>();
        while (it.hasNext()) {
            FunctionSymbol external = it.next();
            String name = external.getName();
            LLVMScope scope = null;
            String originalName = null;
            String lib = null;
            boolean createNewFunction = false;
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
                    lib = name.substring(SULONG_RENAME_MARKER_LEN, idx);
                    scope = language.getInternalFileScopes(lib);
                    if (scope == null) {
                        try {
                            /*
                             * If the library that contains the function is not in the language, and therefore has not
                             * been parsed, then we will try to lazily parse the library now.
                             */
                            String libName = lib + "." + NFIContextExtension.getNativeLibrarySuffix();
                            ExternalLibrary library = context.addInternalLibrary(libName, "<default bitcode library>");
                            TruffleFile file = library.hasFile() ? library.getFile() : context.getEnv().getInternalTruffleFile(library.getPath().toUri());
                            context.getEnv().parseInternal(Source.newBuilder("llvm", file).internal(library.isInternal()).build());
                            scope = language.getInternalFileScopes(lib);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    if (scope == null) {
                        throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found", external.getName(), lib));
                    }
                    originalName = name.substring(idx + 1);
                    createNewFunction = true;
                }
            } else if (CXXDemangler.isRenamedNamespaceSymbol(name)) {
                ArrayList<String> namespaces = CXXDemangler.decodeNamespace(name);
                lib = CXXDemangler.getAndRemoveLibraryName(namespaces);
                scope = language.getInternalFileScopes(lib);
                if (scope == null) {
                    try {
                        /*
                         * If the library that contains the function is not in the language, and therefore has not
                         * been parsed, then we will try to lazily parse the library now.
                         */
                        String libName = lib + "." + NFIContextExtension.getNativeLibrarySuffix();
                        ExternalLibrary library = context.addInternalLibrary(libName, "<default bitcode library>");
                        TruffleFile file = library.hasFile() ? library.getFile() : context.getEnv().getInternalTruffleFile(library.getPath().toUri());
                        context.getEnv().parseInternal(Source.newBuilder("llvm", file).internal(library.isInternal()).build());
                        scope = language.getInternalFileScopes(lib);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                if (scope == null) {
                    throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found", external.getName(), lib));
                }
                originalName = CXXDemangler.encodeNamespace(namespaces);
                createNewFunction = true;
            }

            if (createNewFunction) {
                LLVMFunction originalSymbol = scope.getFunction(originalName);
                if (originalSymbol == null) {
                    throw new LLVMLinkerException(
                            String.format("The symbol %s could not be imported because the symbol %s was not found in library %s", external.getName(), originalName, lib));
                }
                LLVMFunction newFunction = LLVMFunction.create(name, originalSymbol.getLibrary(), originalSymbol.getFunction(), originalSymbol.getType(),
                        parserResult.getRuntime().getBitcodeID(), external.getIndex(), external.isExported());
                LLVMScope fileScope = parserResult.getRuntime().getFileScope();
                fileScope.register(newFunction);
                functions.add(newFunction);
                it.remove();
                parserResult.getDefinedFunctions().add(external);
            }
        }
        return functions;
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

    /**
     * Parses a binary (bitcode with optional meta information from an ELF, Mach-O object file).
     */
    private LLVMParserResult parseBinary(BinaryParserResult binaryParserResult, ExternalLibrary library) {
        ModelModule module = new ModelModule();
        Source source = binaryParserResult.getSource();
        LLVMScanner.parseBitcode(binaryParserResult.getBitcode(), module, source, context);
        TargetDataLayout layout = module.getTargetDataLayout();
        DataLayout targetDataLayout = new DataLayout(layout.getDataLayout());
        if (targetDataLayout.getByteOrder() != ByteOrder.LITTLE_ENDIAN) {
            throw new LLVMParserException("Byte order " + targetDataLayout.getByteOrder() + " of file " + library.getPath() + " is not supported");
        }
        NodeFactory nodeFactory = context.getLanguage().getActiveConfiguration().createNodeFactory(context, targetDataLayout);
        // This needs to be removed once the nodefactory is taken out of the language.
        LLVMScope fileScope = new LLVMScope();
        int bitcodeID = nextFreeBitcodeID.getAndIncrement();
        LLVMParserRuntime runtime = new LLVMParserRuntime(context, library, fileScope, nodeFactory, bitcodeID);
        LLVMParser parser = new LLVMParser(source, runtime);
        LLVMParserResult result = parser.parse(module, targetDataLayout);
        createDebugInfo(module, new LLVMSymbolReadResolver(runtime, StackManager.createRootFrame(), GetStackSpaceFactory.createAllocaFactory(), targetDataLayout, false));
        return result;
    }

    private void createDebugInfo(ModelModule model, LLVMSymbolReadResolver symbolResolver) {
        final LLVMSourceContext sourceContext = context.getSourceContext();

        model.getSourceGlobals().forEach((symbol, irValue) -> {
            final LLVMExpressionNode node = symbolResolver.resolve(irValue);
            final LLVMDebugObjectBuilder value = CommonNodeFactory.createDebugStaticValue(context, node, irValue instanceof GlobalVariable);
            sourceContext.registerStatic(symbol, value);
        });

        model.getSourceStaticMembers().forEach(((type, symbol) -> {
            final LLVMExpressionNode node = symbolResolver.resolve(symbol);
            final LLVMDebugObjectBuilder value = CommonNodeFactory.createDebugStaticValue(context, node, symbol instanceof GlobalVariable);
            type.setValue(value);
        }));
    }

    /**
     * Parses a single bitcode module and returns its {@link LLVMParserResult}. Explicit and
     * implicit dependencies of {@code lib} are added to the . The returned {@link LLVMParserResult}
     * is also added to the. This method ensures that the {@code library} parameter is added to the
     * {@link LLVMContext#ensureExternalLibraryAdded context}.
     *
     * @param source the {@link Source} of the library to be parsed
     * @param library the {@link ExternalLibrary} corresponding to the library to be parsed
     * @param bytes the bytes of the library to be parsed
     * @return the parser result corresponding to {@code lib}
     */
    private LLVMParserResult parseLibraryWithSource(Source source, ExternalLibrary library, ByteSequence bytes, ArrayList<Source> sourceDependencies) {
        BinaryParserResult binaryParserResult = BinaryParser.parse(bytes, source, context);
        if (binaryParserResult != null) {
            library.makeBitcodeLibrary();
            context.ensureExternalLibraryAdded(library);
            context.addLibraryPaths(binaryParserResult.getLibraryPaths());
            ArrayList<ExternalLibrary> dependencies = new ArrayList<>();
            processDependencies(library, binaryParserResult, sourceDependencies, dependencies);
            LLVMParserResult parserResult = parseBinary(binaryParserResult, library);
            parserResult.setDependencies(dependencies);
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
     * {@link BinaryParserResult} into {@link ExternalLibrary}s and add them to the if not already
     * in there.
     */
    private void processDependencies(ExternalLibrary library, BinaryParserResult binaryParserResult, ArrayList<Source> dependenciesSource, ArrayList<ExternalLibrary> dependencies) {
        for (String lib : context.preprocessDependencies(library, binaryParserResult.getLibraries())) {
            ExternalLibrary dependency = context.findExternalLibrary(lib, library, binaryParserResult.getLocator());
            if (dependency != null && !dependencies.contains(dependency)) {
                dependencies.add(dependency);
                Source source = createDependencySource(dependency);
                if (!dependenciesSource.contains(source)) {
                    dependenciesSource.add(source);
                }
            } else {
                dependency = context.addExternalLibrary(lib, library, binaryParserResult.getLocator());
                if (dependency != null && !dependencies.contains(dependency)) {
                    dependencies.add(dependency);
                    Source source = createDependencySource(dependency);
                    if (!dependenciesSource.contains(source)) {
                        dependenciesSource.add(source);
                    }
                }
            }
        }
    }

    private Source createDependencySource(ExternalLibrary lib) {

        if (lib.hasFile() && !lib.getFile().isRegularFile() || lib.getPath() == null || !lib.getPath().toFile().isFile()) {
            if (!lib.isNative()) {
                throw new LLVMParserException("'" + lib.getPath() + "' is not a file or does not exist.");
            } else {
                // lets assume that this is not a bitcode file and the NFI is going to handle it
                NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
                if (nfiContextExtension != null) {
                    nfiContextExtension.addNativeLibrary(lib);
                }
                return null;
            }
        }

        if (lib.getName().equalsIgnoreCase(BasicPlatformCapability.LIBSULONG_FILENAME) || lib.getName().equalsIgnoreCase(BasicPlatformCapability.LIBSULONGXX_FILENAME)) {
            if (lib.isNative()) {
                lib.makeBitcodeLibrary();
            }
        }

        TruffleFile file = lib.hasFile() ? lib.getFile() : context.getEnv().getInternalTruffleFile(lib.getPath().toUri());
        Source source;
        try {
            source = Source.newBuilder("llvm", file).internal(lib.isInternal()).build();
            language.addInternalExternalLibrary(source, lib);
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            throw new LLVMParserException("Error reading file " + lib.getPath() + ".");
        }
        return source;
    }

    private static void addExternalSymbolsToScopes(LLVMParserResult parserResult) {
        // TODO (chaeubl): in here, we should validate if the return type/argument type/global
        // types match
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

    /**
     * Initialization node for the global scope and the local scope of the module. The scopes are
     * allocated from the symbols in the file scope of the module.
     *
     * @see InitializeSymbolsNode
     * @see InitializeGlobalNode
     * @see InitializeModuleNode
     * @see InitializeOverwriteNode
     * @see InitializeExternalNode
     */
    private static final class InitializeScopeNode extends LLVMNode {
        @Children final AllocScopeNode[] allocScopes;
        private final LLVMScope fileScope;

        InitializeScopeNode(LLVMParserResult result) {
            this.fileScope = result.getRuntime().getFileScope();
            ArrayList<AllocScopeNode> allocScopesList = new ArrayList<>();
            for (LLVMSymbol symbol : fileScope.values()) {
                if (symbol.isExported()) {
                    allocScopesList.add(new AllocScopeNode(symbol));
                }
            }
            this.allocScopes = allocScopesList.toArray(AllocScopeNode.EMPTY);
        }

        void execute(LLVMContext context, LLVMLocalScope localScope) {
            synchronized (context) {
                localScope.addMissingLinkageName(fileScope);
                for (int i = 0; i < allocScopes.length; i++) {
                    AllocScopeNode allocScope = allocScopes[i];
                    allocScope.allocateScope(context, localScope);
                }
            }
        }
    }

    /**
     * Initialize external and exported symbols, by populating the symbol table of every external
     * symbols of a given bitcode file.
     * <p>
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
        private final NodeFactory nodeFactory;

        InitializeExternalNode(LLVMParserResult result) {
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
                allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global));
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
        void execute(LLVMContext context, int id) {
            LLVMScope globalScope = context.getGlobalScope();
            LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);
            NFIContextExtension nfiContextExtension = getNfiContextExtension(context);

            synchronized (context) {
                // functions and globals
                for (int i = 0; i < allocExternalSymbols.length; i++) {
                    AllocExternalSymbolNode function = allocExternalSymbols[i];
                    LLVMLocalScope localScope = context.getLocalScope(id);
                    LLVMPointer pointer = function.execute(localScope, globalScope, intrinsicProvider, nfiContextExtension);
                    // skip allocating fallbacks
                    if (pointer == null) {
                        continue;
                    }
                    writeSymbols.execute(pointer, function.symbol);
                }
            }
        }

        @TruffleBoundary
        private static NFIContextExtension getNfiContextExtension(LLVMContext context) {
            return context.getContextExtensionOrNull(NFIContextExtension.class);
        }

    }

    /**
     * Overwriting for defined symbols that will be resolved to the local scope instead of the file
     * scope.
     * <p>
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

        @Children final AllocExternalSymbolNode[] allocExternalSymbols;
        @Child LLVMWriteSymbolNode writeSymbols;

        InitializeOverwriteNode(LLVMParserResult result) {
            this.writeSymbols = LLVMWriteSymbolNodeGen.create();
            ArrayList<AllocExternalSymbolNode> allocExternaSymbolsList = new ArrayList<>();
            LLVMScope fileScope = result.getRuntime().getFileScope();

            // Rewrite all overridable functions and globals in the filescope from their respective
            // function/global in the localscope.
            for (FunctionSymbol symbol : result.getDefinedFunctions()) {
                if (symbol.isOverridable()) {
                    LLVMFunction function = fileScope.getFunction(symbol.getName());
                    // Functions are overwritten by functions from the localScope
                    allocExternaSymbolsList.add(AllocExistingLocalSymbolsNodeGen.create(function));
                }
            }
            for (GlobalSymbol symbol : result.getDefinedGlobals()) {
                // Cannot override the reserved symbols CONSTRUCTORS_VARNAME and
                // DECONSTRUCTORS_VARNAME
                if (symbol.isOverridable() && !symbol.isIntrinsicGlobalVariable()) {
                    LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
                    // Globals are overwritten by (non-hidden) global symbol of the same name in the
                    // globalscope
                    allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global));
                }
            }
            this.allocExternalSymbols = allocExternaSymbolsList.toArray(AllocExternalSymbolNode.EMPTY);
        }

        @ExplodeLoop
        void execute(LLVMContext context, int id) {
            LLVMScope globalScope = context.getGlobalScope();
            LLVMLocalScope localScope = context.getLocalScope(id);
            for (int i = 0; i < allocExternalSymbols.length; i++) {
                AllocExternalSymbolNode allocSymbol = allocExternalSymbols[i];
                LLVMPointer pointer = allocSymbol.execute(localScope, globalScope, null, null);
                // skip allocating fallbacks
                if (pointer == null) {
                    continue;
                }
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

        InitializeGlobalNode(FrameDescriptor rootFrame, LLVMParserResult parserResult, String moduleName) {
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

        InitializeModuleNode(Runner runner, LLVMParserResult parserResult, String moduleName) {
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

    private static StaticInitsNode createGlobalVariableInitializer(FrameDescriptor rootFrame, LLVMParserResult parserResult, String moduleName) {
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

    private static StaticInitsNode createConstructor(LLVMParserResult parserResult, String moduleName) {
        return StaticInitsNodeGen.create(createStructor(CONSTRUCTORS_VARNAME, parserResult, ASCENDING_PRIORITY), "init", moduleName);
    }

    private RootCallTarget createDestructor(LLVMParserResult parserResult, String moduleName) {
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
                        LLVMGetStackFromFrameNode.create(rootFrame.findFrameSlot(LLVMStack.FRAME_ID))};
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

    /*static byte[] decodeBase64(CharSequence charSequence) {
        byte[] result = new byte[charSequence.length()];
        for (int i = 0; i < result.length; i++) {
            char ch = charSequence.charAt(i);
            assert ch >= 0 && ch <= Byte.MAX_VALUE;
            result[i] = (byte) ch;
        }
        return Base64.getDecoder().decode(result);
    }*/

    // name, single parserResult, list of dependencies (list of sources)
    private CallTarget createLibraryCallTarget(String name, LLVMParserResult parserResult, List<Source> sources, Source source) {
        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            FrameDescriptor rootFrame = StackManager.createRootFrame();
            // check if the functions should be resolved eagerly or lazyly.
            boolean lazyParsing = context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING);
            LoadModulesNode loadModules = LoadModulesNode.create(name, this, parserResult, rootFrame, lazyParsing, context, sources, source, language);
            return Truffle.getRuntime().createCallTarget(loadModules);
        }
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
            if (mainFunction.getFunction() instanceof LLVMIRFunction || mainFunction.getFunction() instanceof LazyLLVMIRFunction) {
                return mainFunction;
            }
        }
        return null;
    }

    /**
     * Find, create, and return the function descriptor for the start method. As well as set the
     * sulong specific functions __sulong_init_context and __sulong_dispose_context to the context.
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
