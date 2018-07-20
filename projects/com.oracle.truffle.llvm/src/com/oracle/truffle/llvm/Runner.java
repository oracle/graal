/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.RunnerFactory.SulongLibraryMessageResolutionFactory.ExecuteMainNodeGen;
import com.oracle.truffle.llvm.RunnerFactory.SulongLibraryMessageResolutionFactory.LookupNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.nodes.others.LLVMStaticInitsBlockNode;
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
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.SystemContextExtension;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateStructNode;
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
import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;

public final class Runner {

    private static final String MAIN_METHOD_NAME = "@main";
    private static final String START_METHOD_NAME = "@_start";

    private static final String CONSTRUCTORS_VARNAME = "@llvm.global_ctors";
    private static final String DESTRUCTORS_VARNAME = "@llvm.global_dtors";
    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() - p2.getFirst();
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p2.getFirst() - p1.getFirst();

    /**
     * Object that is returned when a bitcode library is parsed.
     */
    static final class SulongLibrary implements TruffleObject {

        private final String name;
        private final LLVMScope scope;
        private final CallTarget main;

        private SulongLibrary(String name, LLVMScope scope, CallTarget main) {
            this.name = name;
            this.scope = scope;
            this.main = main;
        }

        private LLVMFunctionDescriptor lookupFunctionDescriptor(String symbolName) {
            LLVMSymbol symbol = scope.get(symbolName);
            if (symbol != null && symbol.isFunction()) {
                return symbol.asFunction();
            }
            return null;
        }

        public String getName() {
            return name;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return SulongLibraryMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = SulongLibrary.class)
    abstract static class SulongLibraryMessageResolution {

        abstract static class LookupNode extends LLVMNode {

            abstract LLVMFunctionDescriptor execute(SulongLibrary library, String name);

            @Specialization(guards = {"library == cachedLibrary", "name.equals(cachedName)"})
            @SuppressWarnings("unused")
            LLVMFunctionDescriptor doCached(SulongLibrary library, String name,
                            @Cached("library") SulongLibrary cachedLibrary,
                            @Cached("name") String cachedName,
                            @Cached("lookupFunctionDescriptor(cachedLibrary, cachedName)") LLVMFunctionDescriptor cachedDescriptor) {
                return cachedDescriptor;
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            LLVMFunctionDescriptor doGeneric(SulongLibrary library, String name) {
                return lookupFunctionDescriptor(library, name);
            }

            protected static LLVMFunctionDescriptor lookupFunctionDescriptor(SulongLibrary library, String name) {
                if (name.startsWith("@")) {
                    // safeguard: external users are never supposed to see the "@"
                    // TODO remove after getting rid of the @
                    return null;
                }

                String atname = "@" + name;
                LLVMFunctionDescriptor d = library.lookupFunctionDescriptor(atname);
                if (d != null) {
                    return d;
                }
                return library.lookupFunctionDescriptor(name);
            }
        }

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            @Child LookupNode lookup = LookupNodeGen.create();

            Object access(SulongLibrary boxed, String name) {
                Object ret = lookup.execute(boxed, name);
                if (ret == null) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(name);
                }
                return ret;
            }
        }

        @Resolve(message = "INVOKE")
        abstract static class InvokeNode extends Node {

            @Child LookupNode lookup = LookupNodeGen.create();
            @Child LLVMForeignCallNode call = LLVMForeignCallNodeGen.create();

            Object access(SulongLibrary library, String name, Object[] arguments) {
                LLVMFunctionDescriptor fn = lookup.execute(library, name);
                if (fn == null) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(name);
                }

                return call.executeCall(fn, arguments);
            }
        }

        @Resolve(message = "KEYS")
        abstract static class KeysNode extends Node {

            TruffleObject access(SulongLibrary library) {
                return library.scope.getKeys();
            }
        }

        @Resolve(message = "KEY_INFO")
        abstract static class KeyInfoNode extends Node {

            @Child LookupNode lookup = LookupNodeGen.create();

            int access(SulongLibrary library, String name) {
                if (lookup.execute(library, name) != null) {
                    return KeyInfo.READABLE | KeyInfo.INVOCABLE;
                } else {
                    return KeyInfo.NONE;
                }
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutableNode extends Node {

            boolean access(SulongLibrary library) {
                return library.main != null;
            }
        }

        abstract static class ExecuteMainNode extends LLVMNode {

            abstract Object execute(SulongLibrary library, Object[] args);

            @Specialization(guards = "library == cachedLibrary")
            @SuppressWarnings("unused")
            Object executeCached(SulongLibrary library, Object[] args,
                            @Cached("library") SulongLibrary cachedLibrary,
                            @Cached("createMainCall(cachedLibrary)") DirectCallNode call) {
                return call.call(args);
            }

            static DirectCallNode createMainCall(SulongLibrary library) {
                return DirectCallNode.create(library.main);
            }

            @Specialization(replaces = "executeCached")
            Object executeGeneric(SulongLibrary library, Object[] args,
                            @Cached("create()") IndirectCallNode call) {
                return call.call(library.main, args);
            }
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {

            @Child ExecuteMainNode executeMain = ExecuteMainNodeGen.create();

            Object access(SulongLibrary library, Object[] args) {
                assert library.main != null;
                return executeMain.execute(library, args);
            }
        }

        @CanResolve
        abstract static class CanResolveSulongLibrary extends Node {

            boolean test(TruffleObject object) {
                return object instanceof SulongLibrary;
            }
        }
    }

    private final LLVMContext context;
    private final NodeFactory nodeFactory;

    public Runner(LLVMContext context, NodeFactory nodeFactory) {
        this.context = context;
        this.nodeFactory = nodeFactory;
    }

    /**
     * Parse bitcode data and do first initializations to prepare bitcode execution.
     */
    public CallTarget parse(Source source) {
        // per context, only one thread must do any parsing
        synchronized (context.getGlobalScope()) {
            ParserInput input = getParserData(source);
            return parse(source, input.bytes, input.library);
        }
    }

    private ParserInput getParserData(Source source) {
        ByteBuffer bytes;
        ExternalLibrary library;
        if (source.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
            bytes = ByteBuffer.wrap(decodeBase64(source.getCharacters()));
            library = new ExternalLibrary("<STREAM-" + UUID.randomUUID().toString() + ">", false);
        } else if (source.getMimeType().equals(LLVMLanguage.LLVM_SULONG_TYPE)) {
            NativeLibraryDescriptor descriptor = Parser.parseLibraryDescriptor(source.getCharacters());
            String filename = descriptor.getFilename();
            bytes = read(filename);
            library = new ExternalLibrary(Paths.get(filename), false);
        } else if (source.getPath() != null) {
            bytes = read(source.getPath());
            library = new ExternalLibrary(Paths.get(source.getPath()), false);
        } else {
            throw new LLVMParserException("Neither a valid path nor a valid mime-type were specified.");
        }
        return new ParserInput(bytes, library);
    }

    private CallTarget parse(Source source, ByteBuffer bytes, ExternalLibrary library) {
        // process the bitcode file and its dependencies in the dynamic linking order
        // (breadth-first)
        List<LLVMParserResult> parserResults = new ArrayList<>();
        ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>();

        parse(parserResults, dependencyQueue, source, library, bytes);
        assert !library.isNative() && !parserResults.isEmpty();

        ExternalLibrary[] sulongLibraries = parseDependencies(parserResults, dependencyQueue);
        assert dependencyQueue.isEmpty();

        allocateGlobals(parserResults);
        addExternalSymbolsToScopes(parserResults);
        bindUnresolvedSymbols(parserResults);

        InitializationOrder initializationOrder = computeInitializationOrder(parserResults, sulongLibraries);
        overrideSulongLibraryFunctionsWithIntrinsics(initializationOrder.sulongLibraries);

        parseFunctionsEagerly(parserResults);
        registerDynamicLinkChain(parserResults);
        callStructors(initializationOrder);
        return createLibraryCallTarget(source.getName(), parserResults);
    }

    private void allocateGlobals(List<LLVMParserResult> parserResults) {
        for (LLVMParserResult res : parserResults) {
            allocateGlobals(res);
        }
    }

    private void allocateGlobals(LLVMParserResult res) {
        DataLayout dataLayout = context.getDataSpecConverter();

        // allocate all non-pointer types as one struct
        ArrayList<Type> nonPointerTypes = getNonPointerTypes(res, dataLayout);
        StructureType structType = new StructureType("globals_struct", true, nonPointerTypes.toArray(new Type[0]));
        LLVMAllocateStructNode allocationNode = nodeFactory.createAllocateStruct(context, structType);
        LLVMPointer nonPointerStore = allocationNode.executeWithTarget();
        LLVMScope fileScope = res.getRuntime().getFileScope();

        HashMap<LLVMPointer, LLVMGlobal> reverseMap = new HashMap<>();
        int nonPointerOffset = 0;
        for (GlobalVariable global : res.getDefinedGlobals()) {
            Type type = global.getType().getPointeeType();
            LLVMPointer ref;
            if (isSpecialGlobalSlot(global.getType().getPointeeType())) {
                ref = LLVMManagedPointer.create(new LLVMGlobalContainer());
            } else {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                int alignment = getAlignment(dataLayout, global, type);
                nonPointerOffset += Type.getPadding(nonPointerOffset, alignment);
                ref = nonPointerStore.increment(nonPointerOffset);
                nonPointerOffset += type.getSize(dataLayout);
            }

            LLVMGlobal descriptor = fileScope.getGlobalVariable(global.getName());
            if (!descriptor.isInitialized()) {
                // because of our symbol overriding support, it can happen that the global was
                // already bound before to a different target location
                descriptor.setTarget(ref);
                reverseMap.put(ref, descriptor);
            }
        }

        context.registerGlobals(nonPointerStore, reverseMap);
    }

    private static ArrayList<Type> getNonPointerTypes(LLVMParserResult res, DataLayout dataLayout) {
        ArrayList<Type> result = new ArrayList<>();
        int nonPointerOffset = 0;
        for (GlobalVariable global : res.getDefinedGlobals()) {
            Type type = global.getType().getPointeeType();
            if (!isSpecialGlobalSlot(type)) {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                int alignment = getAlignment(dataLayout, global, type);
                int padding = Type.getPadding(nonPointerOffset, alignment);
                addPaddingTypes(result, padding);
                nonPointerOffset += padding;
                result.add(type);
                nonPointerOffset += type.getSize(dataLayout);
            }
        }
        return result;
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
        ExternalLibrary[] sulongLibraries;
        if (!context.areDefaultLibrariesLoaded()) {
            context.setDefaultLibrariesLoaded();
            Env env = context.getEnv();

            // There could be conflicts between Sulong's default libraries and the ones that are
            // passed on the command-line. To resolve that, we add ours first but parse them later
            // on.
            String[] sulongLibraryNames = context.getContextExtension(SystemContextExtension.class).getSulongDefaultLibraries();
            sulongLibraries = new ExternalLibrary[sulongLibraryNames.length];
            for (int i = 0; i < sulongLibraries.length; i++) {
                sulongLibraries[i] = context.addExternalLibrary(sulongLibraryNames[i], false);
            }

            // parse all libraries that were passed on the command-line
            List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(env);
            for (String external : externals) {
                // assume that the library is a native one until we parsed it and can say for sure
                ExternalLibrary lib = context.addExternalLibrary(external, true);
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
            combineSulongLibraries(sulongLibraryResults);
        } else {
            sulongLibraries = new ExternalLibrary[0];
        }

        // finally we are dealing with all indirect dependencies
        while (!dependencyQueue.isEmpty()) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib);
        }
        return sulongLibraries;
    }

    private void combineSulongLibraries(LLVMParserResult[] sulongLibraryResults) {
        if (sulongLibraryResults.length > 1) {
            EconomicMap<LLVMSymbol, List<LLVMAlias>> usagesInAliases = computeUsagesInAliases(sulongLibraryResults);

            // the array elements are sorted from strong to weak
            LLVMParserResult strongerLib = sulongLibraryResults[0];
            for (int i = 1; i < sulongLibraryResults.length; i++) {
                LLVMParserResult weakerLib = sulongLibraryResults[i];
                renameConflictingSymbols(weakerLib, strongerLib, usagesInAliases);
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

    private void renameConflictingSymbols(LLVMParserResult currentLib, LLVMParserResult strongerLib, EconomicMap<LLVMSymbol, List<LLVMAlias>> usagesInAliases) {
        LLVMScope globalScope = context.getGlobalScope();
        LLVMScope weakerScope = currentLib.getRuntime().getFileScope();
        LLVMScope strongerScope = strongerLib.getRuntime().getFileScope();
        String weakerLibName = currentLib.getRuntime().getLibrary().getName();

        for (LLVMSymbol strongerSymbol : strongerScope.values()) {
            String name = strongerSymbol.getName();
            LLVMSymbol weakerSymbol = weakerScope.get(name);
            if (weakerSymbol != null) {
                boolean shouldRename = strongerSymbol.isFunction() || strongerSymbol.isGlobalVariable() && !strongerSymbol.asGlobalVariable().isReadOnly();
                if (shouldRename) {
                    /*
                     * We already have a function with the same name in another (more important)
                     * library. We rename the already existing symbol by prefixing it with
                     * "__libName_", e.g., "@__clock_gettime" would be renamed to
                     * "@__libc___clock_gettime".
                     */
                    String renamedName = getRenamedSymbol(name, weakerLibName);
                    if (globalScope.contains(renamedName) || weakerScope.contains(renamedName)) {
                        throw new IllegalStateException("There is a conflict between a user defined function and an automatically renamed function: " + renamedName);
                    } else if (!(weakerSymbol.isFunction() && strongerSymbol.isFunction() || weakerSymbol.isGlobalVariable() && strongerSymbol.isGlobalVariable())) {
                        throw new IllegalStateException("Can't replace a function with a global variable or vice versa: " + name);
                    }

                    // rename and export the renamed symbol
                    weakerSymbol.setName(renamedName);
                    weakerScope.rename(name, weakerSymbol);
                    if (globalScope.get(name) == weakerSymbol) {
                        globalScope.rename(name, weakerSymbol);
                    } else {
                        globalScope.register(weakerSymbol);
                    }

                    // modify all aliases that use this symbol
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

    private static String getRenamedSymbol(String functionName, String libraryName) {
        assert functionName.charAt(0) == '@';
        return "@__" + libraryName + "_" + functionName.substring(1);
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
        byte[] bytes;
        try {
            bytes = context.getEnv().getTruffleFile(path.toString()).readAllBytes();
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            throw new LLVMParserException("Error reading file " + path + ".");
        }
        // at the moment, we don't need the bitcode as the content of the source
        Source source = Source.newBuilder(path.toString()).mimeType(LLVMLanguage.LLVM_BITCODE_MIME_TYPE).name(path.getFileName().toString()).build();
        return parse(parserResults, dependencyQueue, source, lib, ByteBuffer.wrap(bytes));
    }

    private LLVMParserResult parse(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, Source source,
                    ExternalLibrary library, ByteBuffer bytes) {
        ModelModule module = LLVMScanner.parse(bytes, context);
        if (module != null) {
            library.setIsNative(false);
            context.addLibraryPaths(module.getLibraryPaths());
            List<String> libraries = module.getLibraries();
            for (String lib : libraries) {
                ExternalLibrary dependency = context.addExternalLibrary(lib, true);
                if (dependency != null) {
                    dependencyQueue.addLast(dependency);
                }
            }
            LLVMScope fileScope = new LLVMScope();
            LLVMParserRuntime runtime = new LLVMParserRuntime(context, nodeFactory, library, fileScope);
            LLVMParser parser = new LLVMParser(source, runtime);
            LLVMParserResult parserResult = parser.parse(module);
            parserResults.add(parserResult);
            return parserResult;
        } else if (!library.isNative()) {
            throw new LLVMParserException("The file is not a bitcode file nor an ELF File with a .llvmbc section.");
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
                    globalSymbol = LLVMGlobal.create(global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
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

    private void bindUnresolvedSymbols(List<LLVMParserResult> parserResults) {
        NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
        LLVMIntrinsicProvider intrinsicProvider = context.getContextExtensionOrNull(LLVMIntrinsicProvider.class);
        for (LLVMParserResult parserResult : parserResults) {
            for (LLVMSymbol symbol : parserResult.getRuntime().getFileScope().values()) {
                if (!symbol.isDefined()) {
                    if (symbol instanceof LLVMGlobal) {
                        LLVMGlobal global = (LLVMGlobal) symbol;
                        bindGlobal(global, nfiContextExtension);
                    } else if (symbol instanceof LLVMFunctionDescriptor) {
                        LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) symbol;
                        bindUnresolvedFunction(function, nfiContextExtension, intrinsicProvider);
                    } else if (symbol instanceof LLVMAlias) {
                        // nothing to do
                    } else {
                        throw new IllegalStateException("Unknown symbol: " + symbol.getClass());
                    }
                }
            }
        }
    }

    private void bindGlobal(LLVMGlobal global, NFIContextExtension nfiContextExtension) {
        if (nfiContextExtension != null) {
            NativePointerIntoLibrary pointerIntoLibrary = nfiContextExtension.getNativeHandle(context, global.getName());
            if (pointerIntoLibrary != null) {
                global.define(pointerIntoLibrary.getLibrary());
                global.setTarget(LLVMNativePointer.create(pointerIntoLibrary.getAddress()));
            }
        }

        if (!global.isDefined() && !context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            throw new LLVMLinkerException("Global variable " + global.getName() + " is declared but not defined.");
        }
    }

    private void bindUnresolvedFunction(LLVMFunctionDescriptor function, NFIContextExtension nfiContextExtension, LLVMIntrinsicProvider intrinsicProvider) {
        if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(function.getName())) {
            function.define(intrinsicProvider);
        } else if (nfiContextExtension != null) {
            NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(context, function.getName());
            if (nativeFunction != null) {
                function.define(nativeFunction.getLibrary(), new LLVMFunctionDescriptor.NativeFunction(nativeFunction.getObject()));
            }
        }
        // if we were unable to bind the function, then we will try another lookup when
        // someone tries to execute the function
    }

    private void registerDynamicLinkChain(List<LLVMParserResult> parserResults) {
        LLVMScope[] fileScopes = new LLVMScope[parserResults.size()];
        for (int i = 0; i < parserResults.size(); i++) {
            fileScopes[i] = parserResults.get(i).getRuntime().getFileScope();
        }
        context.registerScopes(fileScopes);
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

    private void callStructors(InitializationOrder initializationOrder) {
        if (!context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            initialize(initializationOrder.sulongLibraries);
            context.initialize();
            initialize(initializationOrder.otherLibraries);
        }
    }

    private void initialize(List<LLVMParserResult> parserResults) {
        for (LLVMParserResult parserResult : parserResults) {
            // register destructor function so that we can execute it when exit is called
            RootCallTarget destructor = createDestructor(parserResult);
            if (destructor != null) {
                context.registerDestructorFunctions(destructor);
            }

            // initialize global variables
            RootCallTarget globalVarInit = createGlobalVariableInitializer(parserResult);
            if (globalVarInit != null) {
                try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                    globalVarInit.call(stackPointer);
                }
            }

            // execute constructor function
            RootCallTarget constructor = createConstructor(parserResult);
            if (constructor != null) {
                try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                    constructor.call(stackPointer);
                }
            }
        }
    }

    private RootCallTarget createGlobalVariableInitializer(LLVMParserResult parserResult) {
        FrameDescriptor rootFrame = StackManager.createRootFrame();
        LLVMParserRuntime runtime = parserResult.getRuntime();
        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, rootFrame, GetStackSpaceFactory.createAllocaFactory());
        final List<LLVMStatementNode> globalNodes = new ArrayList<>();
        for (GlobalVariable global : parserResult.getDefinedGlobals()) {
            final LLVMStatementNode store = createGlobalInitialization(runtime, symbolResolver, global);
            if (store != null) {
                globalNodes.add(store);
            }
        }

        if (!globalNodes.isEmpty()) {
            LLVMStatementNode[] initNodes = globalNodes.toArray(new LLVMStatementNode[globalNodes.size()]);
            RootNode globalVarInits = new LLVMStaticInitsBlockNode(context.getLanguage(), initNodes, rootFrame);
            return Truffle.getRuntime().createCallTarget(globalVarInits);
        }
        return null;
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
            final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(globalDescriptor, new PointerType(global.getType()));
            if (size != 0) {
                if (type instanceof ArrayType || type instanceof StructureType) {
                    return nodeFactory.createStore(context, globalVarAddress, constant, type, null);
                } else {
                    Type t = global.getValue().getType();
                    return nodeFactory.createStore(context, globalVarAddress, constant, t, null);
                }
            }
        }

        return null;
    }

    private RootCallTarget createConstructor(LLVMParserResult parserResult) {
        return createStructor(CONSTRUCTORS_VARNAME, parserResult, ASCENDING_PRIORITY);
    }

    private RootCallTarget createDestructor(LLVMParserResult parserResult) {
        return createStructor(DESTRUCTORS_VARNAME, parserResult, DESCENDING_PRIORITY);
    }

    private RootCallTarget createStructor(String name, LLVMParserResult parserResult, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalVariable globalVariable : parserResult.getDefinedGlobals()) {
            if (globalVariable.getName().equals(name)) {
                LLVMStatementNode[] targets = resolveStructor(parserResult.getRuntime().getFileScope(), globalVariable, priorityComparator);
                if (targets.length > 0) {
                    return Truffle.getRuntime().createCallTarget(new LLVMStaticInitsBlockNode(context.getLanguage(), targets, StackManager.createRootFrame()));
                } else {
                    return null;
                }
            }
        }
        return null;
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
            final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(global, new PointerType(globalSymbol.getType()));
            final LLVMExpressionNode iNode = nodeFactory.createLiteral(i, PrimitiveType.I32);
            final LLVMExpressionNode structPointer = nodeFactory.createTypedElementPointer(globalVarAddress, iNode, elementSize, elementType);
            final LLVMExpressionNode loadedStruct = nodeFactory.createLoad(elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
            final LLVMExpressionNode functionLoadTarget = nodeFactory.createTypedElementPointer(loadedStruct, oneLiteralNode, indexedTypeLength, functionType);
            final LLVMExpressionNode loadedFunction = nodeFactory.createLoad(functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{
                            nodeFactory.createFrameRead(PointerType.VOID, rootFrame.findFrameSlot(LLVMStack.FRAME_ID))};
            final LLVMStatementNode functionCall = LLVMVoidStatementNodeGen.create(nodeFactory.createFunctionCall(loadedFunction, argNodes, functionType, null));

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

    private ByteBuffer read(String filename) {
        try {
            TruffleFile truffleFile = context.getEnv().getTruffleFile(filename);
            return ByteBuffer.wrap(truffleFile.readAllBytes());
        } catch (IOException | SecurityException | OutOfMemoryError ignore) {
            return ByteBuffer.allocate(0);
        }
    }

    private CallTarget createLibraryCallTarget(String name, List<LLVMParserResult> parserResults) {
        RootCallTarget mainFunctionCallTarget = null;
        LLVMFunctionDescriptor mainFunctionDescriptor = findMainMethod(parserResults);
        LLVMFunctionDescriptor startFunctionDescriptor = findStartMethod();
        if (mainFunctionDescriptor != null && startFunctionDescriptor != null) {
            RootCallTarget startCallTarget = startFunctionDescriptor.getLLVMIRFunction();
            Path applicationPath = mainFunctionDescriptor.getLibrary().getPath();
            RootNode rootNode = new LLVMGlobalRootNode(context.getLanguage(), StackManager.createRootFrame(), mainFunctionDescriptor, startCallTarget, Objects.toString(applicationPath, ""));
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(rootNode);
        }

        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            LLVMScope scope = combineScopes(parserResults);
            SulongLibrary lib = new SulongLibrary(name, scope, mainFunctionCallTarget);
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(lib));
        }
    }

    private LLVMFunctionDescriptor findMainMethod(List<LLVMParserResult> parserResults) {
        // check if the freshly parsed code exports a main method
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            if (fileScope.exports(context, MAIN_METHOD_NAME)) {
                LLVMSymbol mainMethod = fileScope.get(MAIN_METHOD_NAME);
                if (mainMethod.isFunction() && mainMethod.isDefined()) {
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
        LLVMIntrinsicProvider intrinsicProvider = context.getContextExtensionOrNull(LLVMIntrinsicProvider.class);
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
        private final ByteBuffer bytes;
        private final ExternalLibrary library;

        private ParserInput(ByteBuffer bytes, ExternalLibrary library) {
            this.bytes = bytes;
            this.library = library;
        }
    }
}
