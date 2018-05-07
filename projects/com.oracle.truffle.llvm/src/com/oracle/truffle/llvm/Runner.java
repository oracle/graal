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
import java.nio.file.Files;
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
import com.oracle.truffle.llvm.parser.NodeFactory;
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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.SystemContextExtension;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
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

        private final LLVMScope scope;
        private final CallTarget main;

        private SulongLibrary(LLVMScope scope, CallTarget main) {
            this.scope = scope;
            this.main = main;
        }

        private LLVMFunctionDescriptor lookup(String name) {
            if (scope.functions().contains(name)) {
                return scope.functions().get(name);
            }
            return null;
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
                            @Cached("doGeneric(cachedLibrary, cachedName)") LLVMFunctionDescriptor cachedResult) {
                return cachedResult;
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            LLVMFunctionDescriptor doGeneric(SulongLibrary library, String name) {
                if (name.startsWith("@")) {
                    // safeguard: external users are never supposed to see the "@"
                    // TODO remove after getting rid of the @
                    return null;
                }

                String atname = "@" + name;
                LLVMFunctionDescriptor d = library.lookup(atname);
                if (d != null) {
                    return d;
                }
                return library.lookup(name);
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
    public CallTarget parse(Source source) throws IOException {
        // per context, only one thread must do any parsing
        synchronized (context.getGlobalScope()) {
            ParserInput input = getParserData(source);
            return parse(source, input.bytes, input.library);
        }
    }

    private static ParserInput getParserData(Source source) throws IOException {
        ByteBuffer bytes;
        ExternalLibrary library;
        try {
            if (source.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                bytes = ByteBuffer.wrap(decodeBase64(source.getCharacters()));
                library = new ExternalLibrary("<STREAM-" + UUID.randomUUID().toString() + ">", false, false);
            } else if (source.getMimeType().equals(LLVMLanguage.LLVM_SULONG_TYPE)) {
                NativeLibraryDescriptor descriptor = Parser.parseLibraryDescriptor(source.getCharacters());
                String filename = descriptor.getFilename();
                bytes = read(filename);
                library = new ExternalLibrary(Paths.get(filename), false, false);
            } else if (source.getPath() != null) {
                bytes = read(source.getPath());
                library = new ExternalLibrary(Paths.get(source.getPath()), false, false);
            } else {
                throw new IllegalStateException("Neither a valid path nor a valid mime-type were specified.");
            }
        } catch (Throwable t) {
            throw new IOException("Error while preparing data for parsing: " + source.getName(), t);
        }
        return new ParserInput(bytes, library);
    }

    private CallTarget parse(Source source, ByteBuffer bytes, ExternalLibrary library) throws IOException {
        // process the bitcode file and its dependencies in the dynamic linking order
        // (breadth-first)
        try {
            List<LLVMParserResult> parserResults = new ArrayList<>();
            ArrayDeque<ExternalLibrary> dependencyQueue = new ArrayDeque<>();

            parse(parserResults, dependencyQueue, source, library, bytes, new LLVMScope());
            assert !library.isNative() && !parserResults.isEmpty();

            ExternalLibrary[] defaultLibraries = parseDependencies(parserResults, dependencyQueue);
            assert dependencyQueue.isEmpty();

            addExternalsToScope(parserResults);
            bindUnresolvedGlobals(parserResults);
            bindUnresolvedFunctions(parserResults);

            InitializationOrder initializationOrder = computeInitializationOrder(parserResults, defaultLibraries);
            overrideSulongLibraryFunctionsWithIntrinsics(initializationOrder.sulongLibraries);

            parseFunctionsEagerly(parserResults);
            registerDynamicLinkChain(parserResults);
            callStructors(initializationOrder);
            return createLibraryCallTarget(parserResults);
        } catch (Throwable t) {
            throw new IOException("Error while parsing " + library, t);
        }
    }

    /**
     * @return The sulong default libraries, if any were parsed.
     */
    private ExternalLibrary[] parseDependencies(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue) {
        // at first, we are only parsing the direct dependencies of the main bitcode file
        int directDependencies = dependencyQueue.size();
        for (int i = 0; i < directDependencies; i++) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib, new LLVMScope());
        }

        // then, we are parsing the default libraries
        ExternalLibrary[] sulongLibs;
        if (!context.areDefaultLibrariesLoaded()) {
            context.setDefaultLibrariesLoaded();
            Env env = context.getEnv();

            // There could be conflicts between Sulong's default libraries and the ones that are
            // passed on the command-line. To resolve that, we add ours first but parse them later
            // on.
            String[] defaultLibraries = context.getContextExtension(SystemContextExtension.class).getSulongDefaultLibraries();
            sulongLibs = new ExternalLibrary[defaultLibraries.length];
            for (int i = 0; i < sulongLibs.length; i++) {
                sulongLibs[i] = context.addExternalLibrary(defaultLibraries[i], false, i != 0);
            }

            // parse all libraries that were passed on the command-line
            List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(env);
            for (String external : externals) {
                // assume that the library is a native one until we parsed it and can say for sure
                ExternalLibrary lib = context.addExternalLibrary(external, true, false);
                if (lib != null) {
                    parse(parserResults, dependencyQueue, lib, new LLVMScope());
                }
            }

            // now parse the default Sulong libraries
            // TODO (chaeubl): we have an ordering issue here... - the search order for native
            // code comes last, which is not necessarily correct...
            parseTogether(parserResults, dependencyQueue, sulongLibs);
        } else {
            sulongLibs = new ExternalLibrary[0];
        }

        // finally we are dealing with all indirect dependencies
        while (!dependencyQueue.isEmpty()) {
            ExternalLibrary lib = dependencyQueue.removeFirst();
            parse(parserResults, dependencyQueue, lib, new LLVMScope());
        }
        return sulongLibs;
    }

    private void parse(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, ExternalLibrary lib, LLVMScope scope) {
        if (lib.getPath() == null || !lib.getPath().toFile().isFile()) {
            if (!lib.isNative()) {
                throw new RuntimeException("'" + lib.getPath() + "' is not a file or does not exist.");
            } else {
                // lets assume that this is not a bitcode file and the NFI is going to handle it
                return;
            }
        }

        try {
            Path path = lib.getPath();
            byte[] bytes = Files.readAllBytes(path);
            // at the moment, we don't need the bitcode as the content of the source
            Source source = Source.newBuilder(path.toString()).mimeType(LLVMLanguage.LLVM_BITCODE_MIME_TYPE).name(path.getFileName().toString()).build();
            parse(parserResults, dependencyQueue, source, lib, ByteBuffer.wrap(bytes), scope);
        } catch (Throwable t) {
            throw new RuntimeException("Error while trying to parse " + lib.getName(), t);
        }
    }

    private void parse(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, Source source,
                    ExternalLibrary library, ByteBuffer bytes, LLVMScope scope) {
        ModelModule module = LLVMScanner.parse(source, bytes);
        if (module != null) {
            library.setIsNative(false);
            context.addLibraryPaths(module.getLibraryPaths());
            List<String> libraries = module.getLibraries();
            for (String lib : libraries) {
                ExternalLibrary dependency = context.addExternalLibrary(lib, true, false);
                if (dependency != null) {
                    dependencyQueue.addLast(dependency);
                }
            }
            LLVMParserRuntime runtime = new LLVMParserRuntime(context, nodeFactory, library, scope);
            LLVMParser parser = new LLVMParser(source, runtime);
            LLVMParserResult parserResult = parser.parse(module);
            parserResults.add(parserResult);
        } else if (!library.isNative()) {
            throw new RuntimeException("The file is not a bitcode file nor an ELF File with a .llvmbc section.");
        }
    }

    private void parseTogether(List<LLVMParserResult> parserResults, ArrayDeque<ExternalLibrary> dependencyQueue, ExternalLibrary... externalLibraries) {
        LLVMScope commonScope = new LLVMScope();
        for (ExternalLibrary lib : externalLibraries) {
            parse(parserResults, dependencyQueue, lib, commonScope);
        }
    }

    private void addExternalsToScope(List<LLVMParserResult> parserResults) {
        // TODO (chaeubl): in here, we should validate if the return types/argument types/global
        // types match
        LLVMScope globalScope = context.getGlobalScope();
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            for (FunctionSymbol function : parserResult.getExternalFunctions()) {
                LLVMFunctionDescriptor descriptor = globalScope.functions().getOrCreate(context, function.getName(), function.getType());
                if (!fileScope.functions().contains(function.getName())) {
                    // there can already be a different local entry in the file scope
                    fileScope.functions().register(descriptor);
                }
            }

            for (GlobalVariable global : parserResult.getExternalGlobals()) {
                LLVMGlobal descriptor = globalScope.globals().getOrCreate(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
                if (!fileScope.globals().contains(global.getName())) {
                    // there can already be a different local entry in the file scope
                    fileScope.globals().register(descriptor);
                }
            }
        }
    }

    private void bindUnresolvedGlobals(List<LLVMParserResult> parserResults) {
        NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
        for (LLVMParserResult parserResult : parserResults) {
            for (LLVMGlobal global : parserResult.getRuntime().getFileScope().globals().toArray()) {
                if (!global.isDefined()) {
                    assert context.getGlobalScope().globals().contains(global);
                    if (nfiContextExtension != null) {
                        NativePointerIntoLibrary pointerIntoLibrary = nfiContextExtension.getNativeHandle(context, global.getName());
                        if (pointerIntoLibrary != null) {
                            global.define(pointerIntoLibrary.getLibrary());
                            global.bindToNativeAddress(context, pointerIntoLibrary.getAddress());
                        }
                    }

                    if (!global.isDefined() && !context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
                        throw new LinkageError("Global variable " + global.getName() + " is declared but not defined.");
                    }
                }
            }
        }
    }

    private void bindUnresolvedFunctions(List<LLVMParserResult> parserResults) {
        NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
        LLVMIntrinsicProvider intrinsicProvider = context.getContextExtensionOrNull(LLVMIntrinsicProvider.class);
        for (LLVMParserResult parserResult : parserResults) {
            for (LLVMFunctionDescriptor function : parserResult.getRuntime().getFileScope().functions().toArray()) {
                if (!function.isDefined()) {
                    assert context.getGlobalScope().functions().contains(function);
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
            }
        }
    }

    private void registerDynamicLinkChain(List<LLVMParserResult> parserResults) {
        for (LLVMParserResult parserResult : parserResults) {
            context.registerScope(parserResult.getRuntime().getFileScope());
        }
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
        for (String imported : parserResult.getImportedFunctions()) {
            ExternalLibrary lib = globalScope.functions().get(imported).getLibrary();
            if (lib != null) {
                importedLibs.add(lib);
            }
        }

        for (String imported : parserResult.getImportedGlobals()) {
            ExternalLibrary lib = globalScope.globals().get(imported).getLibrary();
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
        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(parserResult.getRuntime(), rootFrame);
        final List<LLVMExpressionNode> globalNodes = new ArrayList<>();
        for (GlobalVariable global : parserResult.getDefinedGlobals()) {
            final LLVMExpressionNode store = createGlobalInitialization(symbolResolver, global);
            if (store != null) {
                globalNodes.add(store);
            }
        }

        if (!globalNodes.isEmpty()) {
            LLVMExpressionNode[] initNodes = globalNodes.toArray(new LLVMExpressionNode[globalNodes.size()]);
            RootNode globalVarInits = new LLVMStaticInitsBlockNode(context.getLanguage(), initNodes, rootFrame);
            return Truffle.getRuntime().createCallTarget(globalVarInits);
        }
        return null;
    }

    private LLVMExpressionNode createGlobalInitialization(LLVMSymbolReadResolver symbolResolver, GlobalVariable global) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            final Type type = global.getType().getPointeeType();
            final int size = context.getByteSize(type);

            final LLVMExpressionNode globalVarAddress = symbolResolver.resolve(global);
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
                LLVMExpressionNode[] targets = resolveStructor(parserResult.getRuntime().getFileScope(), globalVariable, priorityComparator);
                if (targets.length > 0) {
                    return Truffle.getRuntime().createCallTarget(new LLVMStaticInitsBlockNode(context.getLanguage(), targets, StackManager.createRootFrame()));
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private LLVMExpressionNode[] resolveStructor(LLVMScope fileScope, GlobalVariable globalSymbol, Comparator<Pair<Integer, ?>> priorityComparator) {
        if (!(globalSymbol.getValue() instanceof ArrayConstant)) {
            // array globals of length 0 may be initialized with scalar null
            return LLVMExpressionNode.NO_EXPRESSIONS;
        }

        final LLVMGlobal global = fileScope.globals().get(globalSymbol.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalSymbol.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int elementSize = context.getByteSize(elementType);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = context.getByteAlignment(functionType);

        final ArrayList<Pair<Integer, LLVMExpressionNode>> structors = new ArrayList<>(elemCount);
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
            final LLVMExpressionNode functionCall = nodeFactory.createFunctionCall(loadedFunction, argNodes, functionType, null);

            final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
            final SymbolImpl prioritySymbol = structorDefinition.getElement(0);
            final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMExpressionNode[]::new);
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

    private static ByteBuffer read(String filename) {
        return read(Paths.get(filename));
    }

    private static ByteBuffer read(Path path) {
        try {
            return ByteBuffer.wrap(Files.readAllBytes(path));
        } catch (IOException ignore) {
            return ByteBuffer.allocate(0);
        }
    }

    private CallTarget createLibraryCallTarget(List<LLVMParserResult> parserResults) {
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
            SulongLibrary lib = new SulongLibrary(scope, mainFunctionCallTarget);
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(lib));
        }
    }

    private LLVMFunctionDescriptor findMainMethod(List<LLVMParserResult> parserResults) {
        // check if the freshly parsed code exports a main method
        for (LLVMParserResult parserResult : parserResults) {
            LLVMScope fileScope = parserResult.getRuntime().getFileScope();
            if (fileScope.functions().exports(context, MAIN_METHOD_NAME)) {
                LLVMFunctionDescriptor mainMethod = fileScope.functions().get(MAIN_METHOD_NAME);
                if (mainMethod.isDefined()) {
                    return mainMethod;
                }
            }
        }
        return null;
    }

    private LLVMFunctionDescriptor findStartMethod() {
        // the start method just needs to be present in the global scope, we don't care when it was
        // parsed.
        if (context.getGlobalScope().functions().contains(START_METHOD_NAME)) {
            LLVMFunctionDescriptor startMethod = context.getGlobalScope().functions().get(START_METHOD_NAME);
            if (startMethod.isDefined()) {
                return startMethod;
            }
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
                for (LLVMFunctionDescriptor function : parserResult.getRuntime().getFileScope().functions().toArray()) {
                    if (intrinsicProvider.isIntrinsified(function.getName())) {
                        function.define(intrinsicProvider);
                    }
                }
            }
        }
    }

    private void parseFunctionsEagerly(List<LLVMParserResult> parserResults) {
        if (!context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING)) {
            for (LLVMParserResult parserResult : parserResults) {
                for (LLVMFunctionDescriptor function : parserResult.getRuntime().getFileScope().functions().toArray()) {
                    function.resolveIfLazyLLVMIRFunction();
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
