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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.initialization.LoadModulesNode;
import com.oracle.truffle.llvm.initialization.LoadNativeNode;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.InternalLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFileReference;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    // Dependencies can either be Source or the call target if the library
    // has already been parsed.
    private final ArrayList<Object> dependencies = new ArrayList<>();

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
        if (source.hasBytes()) {
            bytes = source.getBytes();
        } else if (source.hasCharacters()) {
            throw new LLVMParserException("Unexpected character-based source with mime type: " + source.getMimeType());
        } else {
            throw new LLVMParserException("Should not reach here: Source is neither char-based nor byte-based!");
        }
        return parseWithDependencies(source, bytes);
    }

    /**
     * Parses a bitcode module and all its dependencies and return a {@code CallTarget} that
     * performs all necessary initialization when executed.
     *
     * @param source the {@link Source} of the file being parsed.
     * @param bytes the {@link ByteSequence} of the source.
     * @return calltarget
     */
    private CallTarget parseWithDependencies(Source source, ByteSequence bytes) {
        insertDefaultDependencies(source.getName());
        // Process the bitcode file and its dependencies in the dynamic linking order
        LLVMParserResult result = parseLibraryWithSource(source, bytes);
        if (result == null) {
            // If result is null, then the file parsed does not contain bitcode.
            // The NFI can handle it later if it's a native file.
            TruffleFile file = createNativeTruffleFile(source.getName(), source.getPath());
            // An empty call target is returned for native libraries.
            if (file == null) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            }
            return createNativeLibraryCallTarget(file);
        }
        // ensures the library of the source is not native
        if (context.isInternalLibraryFile(result.getRuntime().getFile())) {
            String libraryName = getSimpleLibraryName(source.getName());
            // Add the file scope of the source to the language
            language.addInternalFileScope(libraryName, result.getRuntime().getFileScope());
            if (libraryName.equals("libsulong")) {
                context.addLibsulongDataLayout(result.getDataLayout());
            }
            // renaming is attempted only for internal libraries.
            resolveRenamedSymbols(result);
        }
        addExternalSymbolsToScopes(result);
        return createLibraryCallTarget(source.getName(), result, source);
    }

    @TruffleBoundary
    private TruffleFile createNativeTruffleFile(String libName, String libPath) {
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension != null) {
            TruffleFile file = DefaultLibraryLocator.INSTANCE.locate(context, libName, "<native library>");
            if (file == null) {
                // Unable to locate the library -> will go to native
                LibraryLocator.traceDelegateNative(context, libPath);
                file = context.getEnv().getInternalTruffleFile(libPath);
            }
            return file;
        }
        return null;
    }

    /**
     * The default libraries are created as the initial dependencies for every library parsed.
     */
    private void insertDefaultDependencies(String currentLib) {
        // There could be conflicts between the default libraries of Sulong and the ones that are
        // passed on the command-line. To resolve that, we add ours first but parse them later on.
        String[] sulongLibraryNames = language.getCapability(PlatformCapability.class).getSulongDefaultLibraries();
        for (String sulongLibraryName : sulongLibraryNames) {
            // Don't add the library itself as one of it's own dependency.
            if (!currentLib.equals(sulongLibraryName)) {
                TruffleFile file = createTruffleFile(sulongLibraryName, null, InternalLibraryLocator.INSTANCE, "<internal library>");
                // Look into the library cache in the language for the call target.
                if (file != null) {
                    CallTarget calls = language.getCachedLibrary(file.getPath());
                    if (calls != null) {
                        dependencies.add(calls);
                    } else {
                        Object sourceOrCallTarget = createDependencySource(sulongLibraryName, null, false, file);
                        if (sourceOrCallTarget != null && !dependencies.contains(sourceOrCallTarget)) {
                            dependencies.add(sourceOrCallTarget);
                        }
                    }
                }
            }
        }

        // parse all libraries that were passed on the command-line
        List<String> externals = SulongEngineOption.getPolyglotOptionExternalLibraries(context.getEnv());
        for (String externalLibraryName : externals) {
            // Look into the library cache in the language for the call target.
            if (!currentLib.equals(externalLibraryName)) {
                TruffleFile file = createTruffleFile(externalLibraryName, null, DefaultLibraryLocator.INSTANCE, "<command-line library>");
                // Look into the library cache in the language for the call target.
                if (file != null) {
                    CallTarget calls = language.getCachedLibrary(file.getPath());
                    if (calls != null) {
                        dependencies.add(calls);
                    } else {
                        // for native libraries, the path is the same as the library's name. The NFI
                        // will figure out the path.
                        Object sourceOrCallTarget = createDependencySource(externalLibraryName, externalLibraryName, true, file);
                        if (sourceOrCallTarget != null && !dependencies.contains(sourceOrCallTarget)) {
                            dependencies.add(sourceOrCallTarget);
                        }
                    }
                }
            }
        }
    }

    /**
     * Marker for renamed symbols. Keep in sync with `sulong-internal.h`.
     */
    static final String SULONG_RENAME_MARKER = "___sulong_import_";
    static final int SULONG_RENAME_MARKER_LEN = SULONG_RENAME_MARKER.length();

    protected void resolveRenamedSymbols(LLVMParserResult parserResult) {
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
                    scope = language.getInternalFileScopes(getSimpleLibraryName(lib));
                    if (scope == null) {
                        try {
                            // If the library that contains the function has not been parsed,
                            // then the library will be lazily parse now.
                            String libName = lib + "." + language.getCapability(PlatformCapability.class).getLibrarySuffix();
                            TruffleFile file = createTruffleFile(libName, null, InternalLibraryLocator.INSTANCE, "<default bitcode library>");
                            context.getEnv().parseInternal(Source.newBuilder("llvm", file).internal(context.isInternalLibraryFile(file)).build());
                            scope = language.getInternalFileScopes(getSimpleLibraryName(lib));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    if (scope == null) {
                        // The default internal libraries should be loaded when the context is
                        // initialised.
                        throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found during symbol renaming", external.getName(), lib));
                    }
                    originalName = name.substring(idx + 1);
                    createNewFunction(scope, originalName, parserResult, external, lib, name, it);
                }
            } else if (CXXDemangler.isRenamedNamespaceSymbol(name)) {
                ArrayList<String> namespaces = CXXDemangler.decodeNamespace(name);
                lib = CXXDemangler.getAndRemoveLibraryName(namespaces);
                scope = language.getInternalFileScopes(getSimpleLibraryName(lib));
                if (scope == null) {
                    try {
                        // If the library that contains the function has not been parsed,
                        // then the library will be lazily parse now.
                        String libName = lib + "." + language.getCapability(PlatformCapability.class).getLibrarySuffix();
                        TruffleFile file = createTruffleFile(libName, null, InternalLibraryLocator.INSTANCE, "<default bitcode library>");
                        context.getEnv().parseInternal(Source.newBuilder("llvm", file).internal(context.isInternalLibraryFile(file)).build());
                        scope = language.getInternalFileScopes(getSimpleLibraryName(lib));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                if (scope == null) {
                    // The default internal libraries should be loaded when the context is
                    // initialised.
                    throw new LLVMLinkerException(String.format("The symbol %s could not be imported because library %s was not found during symbol renaming", external.getName(), lib));
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
        LLVMFunction newFunction = LLVMFunction.create(name, originalSymbol.getFunction(), originalSymbol.getType(),
                        parserResult.getRuntime().getBitcodeID(), external.getIndex(), external.isExported(), parserResult.getRuntime().getFile().getPath());
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
    private LLVMParserResult parseBinary(BinaryParserResult binaryParserResult, TruffleFile file) {
        ModelModule module = new ModelModule();
        Source source = binaryParserResult.getSource();
        LLVMScanner.parseBitcode(binaryParserResult.getBitcode(), module, source);
        TargetDataLayout layout = module.getTargetDataLayout();
        DataLayout targetDataLayout = new DataLayout(layout.getDataLayout());
        if (targetDataLayout.getByteOrder() != ByteOrder.LITTLE_ENDIAN) {
            throw new LLVMParserException("Byte order " + targetDataLayout.getByteOrder() + " of file " + source.getPath() + " is not supported");
        }
        NodeFactory nodeFactory = context.getLanguage().getActiveConfiguration().createNodeFactory(language, targetDataLayout);
        LLVMScope fileScope = new LLVMScope();
        int bitcodeID = nextFreeBitcodeID.getAndIncrement();
        LLVMParserRuntime runtime = new LLVMParserRuntime(fileScope, nodeFactory, bitcodeID, file, source.getName(), getSourceFilesWithChecksums(context.getEnv(), module));
        LLVMParser parser = new LLVMParser(source, runtime);
        LLVMParserResult result = parser.parse(module, targetDataLayout);
        createDebugInfo(module, new LLVMSymbolReadResolver(runtime, new FrameDescriptor(), GetStackSpaceFactory.createAllocaFactory(), targetDataLayout, false));
        return result;
    }

    private static List<LLVMSourceFileReference> getSourceFilesWithChecksums(TruffleLanguage.Env env, ModelModule module) {
        if (SulongEngineOption.shouldVerifyCompileUnitChecksums(env)) {
            List<LLVMSourceFileReference> sourceWithChecksum = module.getSourceFileReferences().stream().filter(f -> f.getChecksumKind() != LLVMSourceFileReference.ChecksumKind.CSK_None).collect(
                            Collectors.toList());
            if (!sourceWithChecksum.isEmpty()) {
                return sourceWithChecksum;
            }
        }
        return null;
    }

    private void createDebugInfo(ModelModule model, LLVMSymbolReadResolver symbolResolver) {
        final LLVMSourceContext sourceContext = context.getSourceContext();

        model.getSourceGlobals().forEach((symbol, irValue) -> {
            final LLVMExpressionNode node = symbolResolver.resolve(irValue);
            final LLVMDebugObjectBuilder value = CommonNodeFactory.createDebugStaticValue(node, irValue instanceof GlobalVariable);
            sourceContext.registerStatic(symbol, value);
        });

        model.getSourceStaticMembers().forEach(((type, symbol) -> {
            final LLVMExpressionNode node = symbolResolver.resolve(symbol);
            final LLVMDebugObjectBuilder value = CommonNodeFactory.createDebugStaticValue(node, symbol instanceof GlobalVariable);
            type.setValue(value);
        }));
    }

    /**
     * Parses a single bitcode module and returns its {@link LLVMParserResult}. Explicit and
     * implicit dependencies of {@code lib} are added to the . The returned {@link LLVMParserResult}
     * is also added to the.
     *
     * @param source the {@link Source} of the library to be parsed
     * @param bytes the bytes of the library to be parsed
     * @return the parser result corresponding to {@code lib}
     */
    private LLVMParserResult parseLibraryWithSource(Source source, ByteSequence bytes) {
        BinaryParserResult binaryParserResult = BinaryParser.parse(bytes, source, context);
        if (binaryParserResult != null) {
            context.addLibraryPaths(binaryParserResult.getLibraryPaths());
            TruffleFile file = createTruffleFile(source.getName(), source.getPath(), binaryParserResult.getLocator(), "<source library>");
            processDependencies(source.getName(), file, binaryParserResult);
            return parseBinary(binaryParserResult, file);
        } else {
            LibraryLocator.traceDelegateNative(context, source);
            return null;
        }
    }

    private TruffleFile createTruffleFile(String libName, String libPath, LibraryLocator locator, String reason) {
        TruffleFile file = locator.locate(context, libName, reason);
        if (file == null) {
            if (libPath != null) {
                file = context.getEnv().getInternalTruffleFile(libPath);
            } else {
                Path path = Paths.get(libName);
                LibraryLocator.traceDelegateNative(context, path);
                file = context.getEnv().getInternalTruffleFile(path.toUri());
            }
        }
        return file;
    }

    /**
     * Converts the {@link BinaryParserResult#getLibraries() dependencies} of a {@link Source} or a
     * {@link CallTarget}, if the library has already been parsed. Finally they are added into the
     * list of dependencies for this library.
     */
    private void processDependencies(String libraryName, TruffleFile libFile, BinaryParserResult binaryParserResult) {
        for (String lib : context.preprocessDependencies(binaryParserResult.getLibraries(), libFile)) {
            // don't add the library itself as one of it's own dependency.
            if (!libraryName.equals(lib)) {
                // only create a source if the library has not already been parsed.
                TruffleFile file = createTruffleFile(lib, null, binaryParserResult.getLocator(), "<dependency library>");
                CallTarget calls = language.getCachedLibrary(file.getPath());
                if (calls != null && !dependencies.contains(calls)) {
                    dependencies.add(calls);
                } else {
                    Object sourceOrCallTarget = createDependencySource(lib, lib, true, file);
                    // A source is null if it's a native library, which will be added to the NFI
                    // context extension instead.
                    if (sourceOrCallTarget != null && !dependencies.contains(sourceOrCallTarget)) {
                        dependencies.add(sourceOrCallTarget);
                    }
                }
            }
        }
    }

    private Object createDependencySource(String libName, String libPath, boolean isNative, TruffleFile file) {
        assert file != null;
        if (!file.isRegularFile()) {
            if (!isNative) {
                throw new LLVMParserException("'" + file.getName() + "' is not a file or does not exist.");
            } else {
                TruffleFile nativeFile = createNativeTruffleFile(libName, libPath);
                // null is returned if the NFIContextExtension does not exists.
                if (nativeFile == null) {
                    return null;
                }
                return createNativeLibraryCallTarget(nativeFile);
            }
        }

        Source source;
        if (language.containsLibrarySource(file.getPath())) {
            source = language.getLibrarySource(file.getPath());
        } else {
            try {
                source = Source.newBuilder("llvm", file).internal(context.isInternalLibraryFile(file)).build();
                language.addLibrarySource(file.getPath(), source);
            } catch (IOException | SecurityException | OutOfMemoryError ex) {
                throw new LLVMParserException("Error reading file " + file.getName() + ".");
            }
        }
        return source;
    }

    private static void addExternalSymbolsToScopes(LLVMParserResult parserResult) {
        // TODO (chaeubl): in here, we should validate if the return type/argument type/global
        // types match
        LLVMScope fileScope = parserResult.getRuntime().getFileScope();
        for (FunctionSymbol function : parserResult.getExternalFunctions()) {
            if (!fileScope.contains(function.getName())) {
                fileScope.register(LLVMFunction.create(function.getName(), new LLVMFunctionCode.UnresolvedFunction(), function.getType(), parserResult.getRuntime().getBitcodeID(),
                                function.getIndex(), false, parserResult.getRuntime().getFile().getPath()));
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
     * @param source the {@link Source} of the library
     * @return the call target for initialising the library.
     */
    private CallTarget createLibraryCallTarget(String name, LLVMParserResult parserResult, Source source) {
        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            // check if the functions should be resolved eagerly or lazily.
            boolean lazyParsing = context.getEnv().getOptions().get(SulongEngineOption.LAZY_PARSING);
            LoadModulesNode loadModules = LoadModulesNode.create(name, parserResult, lazyParsing, context.isInternalLibraryFile(parserResult.getRuntime().getFile()), dependencies, source, language);
            return Truffle.getRuntime().createCallTarget(loadModules);
        }
    }

    /**
     * Creates the call target of the load native module node, which initialise the native library.
     *
     * @return the call target for initialising the library.
     */
    private CallTarget createNativeLibraryCallTarget(TruffleFile file) {
        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            // check if the functions should be resolved eagerly or lazyly.
            LoadNativeNode loadNative = LoadNativeNode.create(new FrameDescriptor(), language, file);
            return Truffle.getRuntime().createCallTarget(loadNative);
        }
    }
}
