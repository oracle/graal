/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nativemode.runtime.NFIContextExtensionFactory.CreateClosureNodeGen;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeWrapper;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

public final class NFIContextExtension extends NativeContextExtension {

    private static final String SIGNATURE_SOURCE_NAME = "llvm-nfi-signature";

    /**
     * The current well-known functions that are used through this interface are:
     *
     * <pre>
     * - `__sulong_fp80_*` (5 operations)
     * - `__sulong_posix_syscall`
     * - `identity`
     * </pre>
     *
     * Rounding up to the next power of 2.
     */
    private static final int WELL_KNOWN_CACHE_INITIAL_SIZE = 8;

    /**
     * Used to cache well-known native functions that are used by the LLVM runtime directly from
     * Truffle nodes.
     */
    static final class WellKnownFunction {

        /**
         * Index into the {@link NFIContextExtension#wellKnownFunctionCache} array. This array is
         * created once per context. Calls from the fast-path can get the function from that array.
         */
        final int index;

        /**
         * The name of the function.
         */
        final String name;

        /**
         * Cached source for the signature of the well-known function. This is cached per engine.
         * Each context has to lookup the native function separately, but the NFI signature can be
         * shared.
         */
        final Source signatureSource;

        WellKnownFunction(int index, String name, Source signatureSource) {
            this.index = index;
            this.name = name;
            this.signatureSource = signatureSource;
        }
    }

    private static final class SignatureSourceCache {

        /**
         * Cache mapping function types to the {@link Source} of a matching NFI signature. The
         * argument types of the {@link FunctionType} correspond 1:1 to the arguments in the NFI
         * signature arguments.
         */
        private final WeakHashMap<FunctionType, Source> sigCache;

        /**
         * Cache mapping function types to the {@link Source} of a matching NFI signature. The
         * {@link FunctionType} contains a first argument that points to the Sulong stack. This
         * argument is removed from the NFI signature.
         */
        private final WeakHashMap<FunctionType, Source> sigCacheSkipStackArg;

        /**
         * Cache of well-known native functions, mapping their name to a matching NFI signature and
         * a unique index.
         */
        private final EconomicMap<String, WellKnownFunction> wellKnown;
        private int nextIndex;

        SignatureSourceCache() {
            sigCache = new WeakHashMap<>();
            sigCacheSkipStackArg = new WeakHashMap<>();

            wellKnown = EconomicMap.create();
            nextIndex = 0;
        }

        Source getSignatureSource(FunctionType type) throws UnsupportedNativeTypeException {
            return getSignatureSource(type, sigCache, 0);
        }

        Source getSignatureSourceSkipStackArg(FunctionType type) throws UnsupportedNativeTypeException {
            return getSignatureSource(type, sigCacheSkipStackArg, LLVMCallNode.USER_ARGUMENT_OFFSET);
        }

        private static Source getSignatureSource(FunctionType type, WeakHashMap<FunctionType, Source> map, int skipArgs) throws UnsupportedNativeTypeException {
            synchronized (map) {
                Source ret = map.get(type);
                if (ret == null) {
                    String sig = getNativeSignature(type, skipArgs);
                    ret = Source.newBuilder("nfi", sig, SIGNATURE_SOURCE_NAME).build();
                    map.put(type, ret);
                }
                return ret;
            }
        }

        synchronized WellKnownFunction getWellKnownFunction(String name, String signature) {
            WellKnownFunction ret = wellKnown.get(name);
            if (ret == null) {
                Source signatureSource = Source.newBuilder("nfi", signature, SIGNATURE_SOURCE_NAME).build();
                ret = new WellKnownFunction(nextIndex++, name, signatureSource);
                wellKnown.put(name, ret);
            }
            return ret;
        }
    }

    public static final class Factory implements ContextExtension.Factory<NativeContextExtension> {

        // share the SignatureSourceCache between contexts
        private final SignatureSourceCache signatureSourceCache = new SignatureSourceCache();

        @Override
        public NativeContextExtension create(Env env) {
            return new NFIContextExtension(env, signatureSourceCache);
        }
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private Object defaultLibraryHandle;
    private boolean internalLibrariesAdded = false;
    private final List<Object> libraryHandles = new ArrayList<>();
    private final EconomicMap<String, CallTarget> visited = EconomicMap.create();
    private final Env env;

    private final SignatureSourceCache signatureSourceCache;
    private final EconomicMap<Source, Object> signatureCache = EconomicMap.create(Equivalence.IDENTITY);

    // This is an array instead of an ArrayList because it's accessed from the fast-path.
    private WellKnownNativeFunctionAndSignature[] wellKnownFunctionCache;

    private NFIContextExtension(Env env, SignatureSourceCache signatureSourceCache) {
        assert env.getOptions().get(SulongNativeOption.ENABLE_NFI);
        this.env = env;
        this.signatureSourceCache = signatureSourceCache;
        this.wellKnownFunctionCache = new WellKnownNativeFunctionAndSignature[WELL_KNOWN_CACHE_INITIAL_SIZE];
    }

    @Override
    public void initialize(LLVMContext context) {
        assert !isInitialized();
        if (!internalLibrariesAdded) {
            TruffleFile file = locateInternalLibrary(context, getNativeLibrary("sulong-native"), "<default nfi library>");
            Object lib = loadLibrary(file.getPath(), context);
            if (lib instanceof CallTarget) {
                libraryHandles.add(((CallTarget) lib).call());
            }

            Object defaultLib = loadDefaultLibrary();
            if (defaultLib instanceof CallTarget) {
                this.defaultLibraryHandle = ((CallTarget) defaultLib).call();
            }
            internalLibrariesAdded = true;
        }
    }

    public boolean isInitialized() {
        return defaultLibraryHandle != null;
    }

    @Override
    public NativePointerIntoLibrary getNativeHandle(String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            NativeLookupResult result = getNativeDataObjectOrNull(name);
            if (result != null) {
                long pointer = INTEROP.asPointer(result.getObject());
                return new NativePointerIntoLibrary(pointer);
            }
            return null;
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    abstract static class CreateClosureNode extends RootNode {

        private final ContextExtension.Key<NativeContextExtension> ctxExtKey;

        private final Source signatureSource;
        private final LLVMNativeWrapper nativeWrapper;

        CreateClosureNode(LLVMLanguage language, Source signatureSource, LLVMNativeWrapper nativeWrapper) {
            super(language);
            this.ctxExtKey = language.lookupContextExtension(NativeContextExtension.class);
            this.signatureSource = signatureSource;
            this.nativeWrapper = nativeWrapper;
        }

        @Specialization
        Object doCreateClosure(
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
            NFIContextExtension ctxExt = (NFIContextExtension) ctxExtKey.get(LLVMContext.get(this));
            Object signature = ctxExt.getCachedSignature(signatureSource);
            return signatureLibrary.createClosure(signature, nativeWrapper);
        }
    }

    @Override
    public CallTarget createNativeWrapperFactory(LLVMFunctionCode code) {
        CompilerAsserts.neverPartOfCompilation();
        /*
         * We create a CallTarget here instead of directly the native closure so the NFI has a place
         * to put a cache for the NFI closure. The implementation in LLVMNativeWrapper expects
         * successfully cached monomorphic calls. This CallTarget will be created only once per
         * function and engine, and cached in the LLVMFunctionCode object. Caching this is fine
         * because both the LLVMFunctionCode and the LLVMNativeWrapper are context independent
         * objects.
         */
        try {
            Source signatureSource = signatureSourceCache.getSignatureSource(code.getLLVMFunction().getType());
            return CreateClosureNodeGen.create(LLVMLanguage.get(null), signatureSource, new LLVMNativeWrapper(code)).getCallTarget();
        } catch (UnsupportedNativeTypeException ex) {
            // ignore, fall back to tagged id
            return null;
        }
    }

    @Override
    public synchronized void addLibraryHandles(Object library) {
        CompilerAsserts.neverPartOfCompilation();
        if (!libraryHandles.contains(library)) {
            libraryHandles.add(library);
        }
    }

    @Override
    public synchronized CallTarget parseNativeLibrary(String path, LLVMContext context) throws UnsatisfiedLinkError {
        CompilerAsserts.neverPartOfCompilation();
        if (!visited.containsKey(path)) {
            Object callTarget = loadLibrary(path, context);
            if (callTarget != null) {
                visited.put(path, (CallTarget) callTarget);
                return (CallTarget) callTarget;
            } else {
                throw new IllegalStateException("Native library call target is null.");
            }
        } else {
            return visited.get(path);
        }
    }

    public static String getNativeLibrarySuffix() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return "dylib";
        } else {
            return "so";
        }
    }

    public static String getNativeLibrarySuffixVersioned(int version) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return version + ".dylib";
        } else {
            return "so." + version;
        }
    }

    private Object loadLibrary(String path, LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        return loadLibrary(path, false, null, context);
    }

    private Object loadLibrary(String path, boolean optional, String flags, LLVMContext context) {
        LibraryLocator.traceLoadNative(context, path);
        String loadExpression;
        if (flags == null) {
            loadExpression = String.format("load \"%s\"", path);
        } else {
            loadExpression = String.format("load(%s) \"%s\"", flags, path);
        }
        final Source source = Source.newBuilder("nfi", loadExpression, "(load " + path + ")").internal(true).build();
        try {
            // remove the call to the calltarget
            return env.parseInternal(source);
        } catch (UnsatisfiedLinkError ex) {
            if (optional) {
                return null;
            } else {
                throw ex;
            }
        }
    }

    private Object loadDefaultLibrary() {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = Source.newBuilder("nfi", "default", "default").internal(true).build();
        try {
            // remove the call to the calltarget
            return env.parseInternal(source);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static Object getNativeFunctionOrNull(Object library, String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (!INTEROP.isMemberReadable(library, name)) {
            // try another library
            return null;
        }
        try {
            return INTEROP.readMember(library, name);
        } catch (UnknownIdentifierException ex) {
            return null;
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String getNativeType(Type type) throws UnsupportedNativeTypeException {
        if (type instanceof FunctionType) {
            return getNativeSignature((FunctionType) type, 0);
        } else if (type instanceof PointerType && ((PointerType) type).getPointeeType() instanceof FunctionType) {
            FunctionType functionType = (FunctionType) ((PointerType) type).getPointeeType();
            return getNativeSignature(functionType, 0);
        } else if (type instanceof PointerType) {
            return "POINTER";
        } else if (type instanceof PrimitiveType) {
            PrimitiveType primitiveType = (PrimitiveType) type;
            PrimitiveKind kind = primitiveType.getPrimitiveKind();
            switch (kind) {
                case I1:
                case I8:
                    return "SINT8";
                case I16:
                    return "SINT16";
                case I32:
                    return "SINT32";
                case I64:
                    return "SINT64";
                case FLOAT:
                    return "FLOAT";
                case DOUBLE:
                    return "DOUBLE";
                case X86_FP80:
                    return "FP80";
                default:
                    throw new UnsupportedNativeTypeException(primitiveType);

            }
        } else if (type instanceof VoidType) {
            return "VOID";
        }
        throw new UnsupportedNativeTypeException(type);
    }

    private static String[] getNativeArgumentTypes(FunctionType functionType, int skipArguments) throws UnsupportedNativeTypeException {
        String[] types = new String[functionType.getNumberOfArguments() - skipArguments];
        for (int i = skipArguments; i < functionType.getNumberOfArguments(); i++) {
            types[i - skipArguments] = getNativeType(functionType.getArgumentType(i));
        }
        return types;
    }

    @Override
    public synchronized NativeLookupResult getNativeFunctionOrNull(String name) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] cursor = libraryHandles.toArray();
        for (int i = 0; i < cursor.length; i++) {
            Object symbol = getNativeFunctionOrNull(cursor[i], name);
            if (symbol != null) {
                return new NativeLookupResult(symbol);
            }
        }
        Object symbol = getNativeFunctionOrNull(defaultLibraryHandle, name);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(symbol);
        }
        return null;
    }

    private synchronized NativeLookupResult getNativeDataObjectOrNull(String name) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] cursor = libraryHandles.toArray();
        for (int i = 0; i < cursor.length; i++) {
            Object symbol = getNativeFunctionOrNull(cursor[i], name);
            if (symbol != null) {
                return new NativeLookupResult(symbol);
            }
        }
        Object symbol = getNativeDataObjectOrNull(defaultLibraryHandle, name);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(symbol);
        }
        return null;
    }

    private static Object getNativeDataObjectOrNull(Object libraryHandle, String name) {
        try {
            Object symbol = INTEROP.readMember(libraryHandle, name);
            if (symbol != null && 0 != INTEROP.asPointer(symbol)) {
                return symbol;
            } else {
                return null;
            }
        } catch (UnknownIdentifierException ex) {
            // try another library
            return null;
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Object bindNativeFunction(Object symbol, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        Source sigSource = Source.newBuilder("nfi", signature, SIGNATURE_SOURCE_NAME).build();
        return SignatureLibrary.getUncached().bind(getCachedSignature(sigSource), symbol);
    }

    @Override
    public Object getNativeFunction(String name, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        NativeLookupResult result = getNativeFunctionOrNull(name);
        if (result != null) {
            return bindNativeFunction(result.getObject(), signature);
        }
        throw new LLVMLinkerException(String.format("External function %s cannot be found.", name));
    }

    @Override
    public WellKnownNativeFunctionNode getWellKnownNativeFunction(String name, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        WellKnownFunction fn = signatureSourceCache.getWellKnownFunction(name, signature);
        return WellKnownNFIFunctionNodeGen.create(fn);
    }

    @Override
    @TruffleBoundary
    public WellKnownNativeFunctionAndSignature getWellKnownNativeFunctionAndSignature(String name, String signature) {
        WellKnownFunction fn = signatureSourceCache.getWellKnownFunction(name, signature);
        return getCachedWellKnownFunction(fn);
    }

    private WellKnownNativeFunctionAndSignature createWellKnownFunction(WellKnownFunction fn) {
        CompilerAsserts.neverPartOfCompilation();
        NativeLookupResult result = getNativeFunctionOrNull(fn.name);
        if (result != null) {
            CallTarget parsedSignature = env.parseInternal(fn.signatureSource);
            Object signature = parsedSignature.call();
            Object boundSignature = SignatureLibrary.getUncached().bind(signature, result.getObject());
            return new WellKnownNativeFunctionAndSignature(signature, result.getObject(), boundSignature);
        }
        throw new LLVMLinkerException(String.format("External function %s cannot be found.", fn.name));
    }

    @TruffleBoundary
    private WellKnownNativeFunctionAndSignature getWellKnownFunctionSlowPath(WellKnownFunction fn) {
        synchronized (this) {
            if (wellKnownFunctionCache.length <= fn.index) {
                int newLength = wellKnownFunctionCache.length * 2;
                assert fn.index < signatureSourceCache.nextIndex;
                while (newLength < signatureSourceCache.nextIndex) {
                    newLength *= 2;
                }
                wellKnownFunctionCache = Arrays.copyOf(wellKnownFunctionCache, newLength);
            }
            WellKnownNativeFunctionAndSignature ret = wellKnownFunctionCache[fn.index];
            if (ret == null) {
                ret = createWellKnownFunction(fn);
                wellKnownFunctionCache[fn.index] = ret;
            }
            return ret;
        }
    }

    WellKnownNativeFunctionAndSignature getCachedWellKnownFunction(WellKnownFunction fn) {
        if (fn.index < wellKnownFunctionCache.length) {
            WellKnownNativeFunctionAndSignature ret = wellKnownFunctionCache[fn.index];
            if (ret != null) {
                return ret;
            }
        }
        return getWellKnownFunctionSlowPath(fn);
    }

    @Override
    public Source getNativeSignatureSourceSkipStackArg(FunctionType type) throws UnsupportedNativeTypeException {
        CompilerAsserts.neverPartOfCompilation();
        return signatureSourceCache.getSignatureSourceSkipStackArg(type);
    }

    @Override
    @TruffleBoundary
    public Object createSignature(Source signatureSource) {
        synchronized (signatureCache) {
            Object ret = signatureCache.get(signatureSource);
            if (ret == null) {
                CallTarget createSignature = env.parseInternal(signatureSource);
                ret = createSignature.call();
                signatureCache.put(signatureSource, ret);
            }
            return ret;
        }
    }

    @TruffleBoundary
    private Object getCachedSignature(Source signatureSource) {
        Object ret = signatureCache.get(signatureSource);
        if (ret == null) {
            ret = createSignature(signatureSource);
        }
        return ret;
    }

    @Override
    public Object bindSignature(LLVMFunctionCode function, Source signatureSource) {
        // TODO(rs): make a fast-path version of this code
        CompilerAsserts.neverPartOfCompilation();
        Object nativeFunction = function.getNativeFunctionSlowPath();
        Object signature = getCachedSignature(signatureSource);
        return SignatureLibrary.getUncached().bind(signature, nativeFunction);
    }

    @Override
    public Object bindSignature(long fnPtr, Source signatureSource) {
        // TODO(rs): make a fast-path version of this code
        CompilerAsserts.neverPartOfCompilation();
        Object signature = getCachedSignature(signatureSource);
        return SignatureLibrary.getUncached().bind(signature, LLVMNativePointer.create(fnPtr));
    }

    private static String getNativeSignature(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException {
        CompilerAsserts.neverPartOfCompilation();
        String nativeRet = getNativeType(type.getReturnType());
        String[] argTypes = getNativeArgumentTypes(type, skipArguments);
        StringBuilder sb = new StringBuilder();

        sb.append("(");
        for (int pos = 0; pos < argTypes.length; pos++) {
            String a = argTypes[pos];

            if (pos == type.getFixedArgs()) {
                sb.append("...");
            }
            sb.append(a);
            sb.append(",");
        }
        if (argTypes.length > 0) {
            sb.setCharAt(sb.length() - 1, ')');
        } else {
            sb.append(')');
        }
        sb.append(":");
        sb.append(nativeRet);
        return sb.toString();
    }

}
