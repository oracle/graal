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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.initialization.LoadModulesNode;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.StackManager;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import com.oracle.truffle.llvm.parser.factories.BasicPlatformCapability;
import com.oracle.truffle.llvm.parser.factories.PlatformCapabilityBase;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives a parsing request.
 *
 * @see #parse
 */
final class ParserDriver {

    /**
     * Parses a {@code source} and all its (explicit and implicit) dependencies.
     *
     * @return a {@link CallTarget} that on execute initializes (i.e., initalize globals, run
     *         constructors, etc.) the module represented by {@code source} and all dependencies.
     */
    public static CallTarget parse(LLVMContext context, AtomicInteger bitcodeID, Source source) {
        return new ParserDriver(context, bitcodeID).parseWithDependencies(source);
    }

    private final LLVMContext context;
    private final LLVMLanguage language;
    private final AtomicInteger nextFreeBitcodeID;

    private ParserDriver(LLVMContext context, AtomicInteger moduleID) {
        this.context = context;
        this.language = context.getLanguage();
        this.nextFreeBitcodeID = moduleID;
    }

    /**
     * Prepare the source for parsing, by creating the external library from the source and
     * retrieving the bytes from the source.
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
     * Parses a bitcode module and all its dependencies and return a {@code CallTarget} that
     * performs all necessary initialization when executed.
     *
     * @param source the {@link Source} of the file being parsed.
     * @param bytes the {@link ByteSequence} of the source.
     * @param library the {@link ExternalLibrary} of the source.
     * @return calltarget
     */
    private CallTarget parseWithDependencies(Source source, ByteSequence bytes, ExternalLibrary library) {

        ArrayList<Source> dependenciesSource = new ArrayList<>();
        insertDefaultDependencies(dependenciesSource);
        // Process the bitcode file and its dependencies in the dynamic linking order
        LLVMParserResult result = parseLibraryWithSource(source, library, bytes, dependenciesSource);
        boolean isInternalLibrary = context.isInternalLibrary(library);

        if (result == null) {
            // If result is null, then the file parsed does not contain bitcode.
            // The NFI can handle it later if it's a native file.
            NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
            if (nfiContextExtension != null) {
                nfiContextExtension.addNativeLibrary(library);
            }
            // An empty
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        }
        // ensures the library of the source is not native
        assert !library.isNative();

        // Remove cyclic and redundant dependency.
        // PLi: It might be cheaper to let the truffle cache take care of this.
        // removeCyclicDependency(source, dependenciesSource);

        if (isInternalLibrary) {
            String libraryName = getSimpleLibraryName(library.getName());
            // Add the file scope of the source to the language
            language.addInternalFileScope(libraryName, result.getRuntime().getFileScope());

            if (libraryName.equals("libsulong")) {
                context.addLibsulongDataLayout(result.getDataLayout());
            }

            // renaming is attempted only for internal libraries.
            resolveRenamedSymbols(result, language, context);
        }

        addExternalSymbolsToScopes(result);
        return createLibraryCallTarget(source.getName(), result, dependenciesSource, source);
    }

    /**
     *
     * @param sourceDependencies
     */
    private void insertDefaultDependencies(ArrayList<Source> sourceDependencies) {
        // There could be conflicts between the default libraries of Sulong and the ones that are
        // passed on the command-line. To resolve that, we add ours first but parse them later on.
        String[] sulongLibraryNames = language.getCapability(PlatformCapability.class).getSulongDefaultLibraries();
        for (String sulongLibraryName : sulongLibraryNames) {
            sourceDependencies.add(createDependencySource(context.addInternalLibrary(sulongLibraryName, "<default bitcode library>")));
        }

        // parse all libraries that were passed on the command-line
        List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(context.getEnv());
        for (String external : externals) {
            sourceDependencies.add(createDependencySource(context.addExternalLibraryDefaultLocator(external, "<command line>")));
        }
    }

    /**
     * Marker for renamed symbols. Keep in sync with `sulong-internal.h`.
     */
    static final String SULONG_RENAME_MARKER = "___sulong_import_";
    static final int SULONG_RENAME_MARKER_LEN = SULONG_RENAME_MARKER.length();

    protected static void resolveRenamedSymbols(LLVMParserResult parserResult, LLVMLanguage language, LLVMContext context) {
        ListIterator<FunctionSymbol> it = parserResult.getExternalFunctions().listIterator();
        while (it.hasNext()) {
            FunctionSymbol external = it.next();
            String name = external.getName();
            LLVMScope scope;
            String originalName;
            String lib;
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
                             * If the library that contains the function is not in the language, and
                             * therefore has not been parsed, then we will try to lazily parse the
                             * library now.
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
                    createNewFunction(scope, originalName, parserResult, external, lib, name, it);
                }
            } else if (CXXDemangler.isRenamedNamespaceSymbol(name)) {
                ArrayList<String> namespaces = CXXDemangler.decodeNamespace(name);
                lib = CXXDemangler.getAndRemoveLibraryName(namespaces);
                scope = language.getInternalFileScopes(lib);
                if (scope == null) {
                    try {
                        /*
                         * If the library that contains the function is not in the language, and
                         * therefore has not been parsed, then we will try to lazily parse the
                         * library now.
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
                createNewFunction(scope, originalName, parserResult, external, lib, name, it);
            }
        }
    }

    private static void createNewFunction(LLVMScope scope, String originalName, LLVMParserResult parserResult, FunctionSymbol external, String lib, String name, ListIterator<FunctionSymbol> it) {
        LLVMFunction originalSymbol = scope.getFunction(originalName);
        if (originalSymbol == null) {
            throw new LLVMLinkerException(
                            String.format("The symbol %s could not be imported because the symbol %s was not found in library %s", external.getName(), originalName, lib));
        }
        LLVMFunction newFunction = LLVMFunction.create(name, originalSymbol.getLibrary(), originalSymbol.getFunction(), originalSymbol.getType(),
                        parserResult.getRuntime().getBitcodeID(), external.getIndex(), external.isExported());
        LLVMScope fileScope = parserResult.getRuntime().getFileScope();
        fileScope.register(newFunction);
        it.remove();
        parserResult.getDefinedFunctions().add(external);
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
                // The cached is returned if lib has already been added.
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
                // If the file or the path of the file does not exists, then assume that this is not
                // a bitcode
                // file, but a native file and the NFI is going to handle it.
                NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
                if (nfiContextExtension != null) {
                    nfiContextExtension.addNativeLibrary(lib);
                }
                return null;
            }
        }

        // Mark default bitcode libraries as bitcode libraries.
        // TODO (PLi): Remove this. The default libraries should not be created by the context as
        // a native library first and then marked as a bitcode library. Also they should not be
        // created
        // as an ExternalLibrary first, and then converted into a source.
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

    /**
     * Creates the call target of the load module node, which initialise the library.
     *
     * @param name the name of the library
     * @param parserResult the {@link LLVMParserResult} for the library
     * @param sources the list of {@link Source} of the library's dependencies
     * @param source the {@link Source} of the library
     * @return the call target for initialising the library.
     */
    private CallTarget createLibraryCallTarget(String name, LLVMParserResult parserResult, List<Source> sources, Source source) {
        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            FrameDescriptor rootFrame = StackManager.createRootFrame();
            // check if the functions should be resolved eagerly or lazyly.
            boolean lazyParsing = context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING);
            LoadModulesNode loadModules = LoadModulesNode.create(name, parserResult, rootFrame, lazyParsing, context, sources, source, language);
            return Truffle.getRuntime().createCallTarget(loadModules);
        }
    }

    private void removeCyclicDependency(Source source, ArrayList<Source> dependenciesSource) {
        // Remove the itself as it's own dependency
        while (dependenciesSource.contains(source)) {
            dependenciesSource.remove(source);
        }

        // Remove libsulong++ for libsulong
        if (source.getName().equals(BasicPlatformCapability.LIBSULONG_FILENAME)) {
            removeDependency(dependenciesSource, BasicPlatformCapability.LIBSULONGXX_FILENAME);
        }

        // Remove libsulong for libsulong++
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

    // This method is required as only the name of the libraries is given, not the source of the
    // library itself.
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
}
