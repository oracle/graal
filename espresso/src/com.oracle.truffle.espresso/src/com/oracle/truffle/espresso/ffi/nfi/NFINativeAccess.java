/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.ffi.nfi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.SignatureCallNode;
import com.oracle.truffle.espresso.ffi.TruffleByteBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Collect;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import sun.misc.Unsafe;

/**
 * Espresso native interface implementation based on TruffleNFI, this class is fully functional on
 * its own (nfi-native backend) and also serves as base for other NFI backends.
 */
public class NFINativeAccess implements NativeAccess {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final boolean CACHE_SIGNATURES = "true".equals(System.getProperty("espresso.nfi.cache_signatures", "true"));

    private final DebugCounter nfiSignaturesCreated = DebugCounter.create("NFI signatures created");

    private final Map<NativeSignature, Object> nativeSignatureCache;
    private final Map<NativeSignature, Object> javaSignatureCache;

    protected final InteropLibrary uncachedInterop = InteropLibrary.getUncached();
    protected final SignatureLibrary uncachedSignature = SignatureLibrary.getUncached();
    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, NFINativeAccess.class);

    protected final TruffleLanguage.Env env;

    protected static String nfiType(NativeType nativeType) {
        // @formatter:off
        switch (nativeType) {
            case VOID:    return "VOID";
            case BOOLEAN: // fall-through
            case BYTE:    return "SINT8";
            case CHAR:    // fall-through
            case SHORT:   return "SINT16";
            case INT:     return "SINT32";
            case LONG:    return "SINT64";
            case FLOAT:   return "FLOAT";
            case DOUBLE:  return "DOUBLE";
            case OBJECT:  return "SINT64"; // word-sized handle
            case POINTER: return "POINTER";
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Unexpected: " + nativeType);
        }
        // @formatter:on
    }

    protected String nfiStringSignature(NativeSignature nativeSignature, @SuppressWarnings("unused") boolean fromJava) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('(');
        boolean isFirst = true;
        for (int i = 0; i < nativeSignature.getParameterCount() + nativeSignature.getVarArgsParameterCount(); ++i) {
            if (!isFirst) {
                sb.append(',');
            }
            if (i == nativeSignature.getParameterCount()) {
                sb.append("...");
            }
            sb.append(nfiType(nativeSignature.parameterTypeAt(i)));
            isFirst = false;
        }
        sb.append(')');
        sb.append(':');
        sb.append(nfiType(nativeSignature.getReturnType()));
        return sb.toString();
    }

    protected final TruffleLogger getLogger() {
        return logger;
    }

    protected Object createNFISignature(NativeSignature nativeSignature, boolean fromJava) {
        nfiSignaturesCreated.inc();
        Source source = Source.newBuilder("nfi",
                        nfiStringSignature(nativeSignature, fromJava), "signature").build();
        CallTarget target = env.parseInternal(source);
        return target.call();
    }

    protected final Object getOrCreateNFISignature(NativeSignature nativeSignature, boolean fromJava) {
        return CACHE_SIGNATURES
                        ? (fromJava ? javaSignatureCache.computeIfAbsent(nativeSignature, sig -> createNFISignature(nativeSignature, true))
                                        : nativeSignatureCache.computeIfAbsent(nativeSignature, sig -> createNFISignature(nativeSignature, false)))
                        : createNFISignature(nativeSignature, fromJava);
    }

    NFINativeAccess(TruffleLanguage.Env env) {
        this.env = env;
        javaSignatureCache = CACHE_SIGNATURES
                        ? new ConcurrentHashMap<>()
                        : null;
        nativeSignatureCache = CACHE_SIGNATURES
                        ? new ConcurrentHashMap<>()
                        : null;
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        if ((libraryPath.isAbsolute() || libraryPath.getNameCount() > 1) && !Files.exists(libraryPath)) {
            return null;
        }
        return loadLibrary0(libraryPath);
    }

    protected @Pointer TruffleObject loadLibrary0(Path libraryPath) {
        String nfiSource = String.format("load(RTLD_LAZY|RTLD_LOCAL) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        return loadLibraryHelper("default");
    }

    private static boolean isExpectedException(AbstractTruffleException e) {
        String className = e.getClass().getName();
        return "com.oracle.truffle.nfi.backend.libffi.NFIUnsatisfiedLinkError".equals(className) ||
                        "com.oracle.truffle.llvm.nfi.SulongNFIException".equals(className);
    }

    protected @Pointer TruffleObject loadLibraryHelper(String nfiSource) {
        Source source = Source.newBuilder("nfi", nfiSource, "loadLibrary").build();
        CallTarget target = env.parseInternal(source);
        try {
            return (TruffleObject) target.call();
        } catch (IllegalArgumentException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getLogger().log(Level.SEVERE, "TruffleNFI native library isolation is not supported", e);
            throw EspressoError.shouldNotReachHere(e);
        } catch (AbstractTruffleException e) {
            // TODO(peterssen): Remove assert once GR-27045 reaches a definitive consensus.
            assert isExpectedException(e);
            // AbstractTruffleException is treated as if it were an UnsatisfiedLinkError.
            getLogger().fine("AbstractTruffleException while loading library though NFI (" + nfiSource + ") : " + e.getMessage());
            return null;
        }
    }

    @Override
    public void unloadLibrary(@Pointer TruffleObject library) {
        // TODO(peterssen): NFI does not support unloading libraries eagerly.
        getLogger().fine(String.format("JVM_UnloadLibrary: %x was not unloaded!", NativeUtils.interopAsPointer(library)));
    }

    @Override
    public @Pointer TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName) {
        try {
            TruffleObject symbol = (TruffleObject) uncachedInterop.readMember(library, symbolName);
            if (InteropLibrary.getUncached().isNull(symbol)) {
                return null;
            }
            return symbol;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        } catch (UnknownIdentifierException e) {
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NativeToJavaWrapper implements TruffleObject {
        private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, NativeToJavaWrapper.class);

        final TruffleObject delegate;
        final NativeSignature nativeSignature;

        NativeToJavaWrapper(TruffleObject executable, NativeSignature nativeSignature) {
            this.delegate = executable;
            this.nativeSignature = nativeSignature;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @TruffleBoundary
        Object doExecuteBoundary(Object[] arguments, InteropLibrary delegateInterop) throws ArityException {
            return doExecute(arguments, delegateInterop);
        }

        @ExplodeLoop
        Object doExecute(Object[] arguments, InteropLibrary delegateInterop) throws ArityException {
            final int paramCount = nativeSignature.getParameterCount() + nativeSignature.getVarArgsParameterCount();
            CompilerAsserts.partialEvaluationConstant(paramCount);
            if (arguments.length != paramCount) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(paramCount, paramCount, arguments.length);
            }
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < paramCount; i++) {
                    NativeType param = nativeSignature.parameterTypeAt(i);
                    CompilerAsserts.partialEvaluationConstant(param);
                    switch (param) {
                        case BOOLEAN:
                            convertedArgs[i] = ((boolean) arguments[i]) ? (byte) 1 : (byte) 0;
                            break;
                        case CHAR:
                            convertedArgs[i] = (short) (char) arguments[i];
                            break;
                        default:
                            convertedArgs[i] = arguments[i];
                    }
                }
                Object ret = delegateInterop.execute(delegate, convertedArgs);
                CompilerAsserts.partialEvaluationConstant(nativeSignature.getReturnType());
                switch (nativeSignature.getReturnType()) {
                    case BOOLEAN:
                        ret = (byte) ret != 0;
                        break;
                    case CHAR:
                        ret = (char) (short) ret;
                        break;
                    case VOID:
                        ret = StaticObject.NULL;
                        break;
                }
                return ret;
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("This can be caused by mixing native/ffi symbols with llvm signatures and vice-versa", e);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            } catch (AbstractTruffleException | StackOverflowError | OutOfMemoryError e) {
                throw e;
            } catch (Throwable t) {
                logger.log(Level.FINE, "Exception seen", t);
                throw t;
            }
        }

        @ReportPolymorphism
        @ExportMessage
        abstract static class Execute {

            static final int INLINE_CACHE_SIZE = 2;

            @Specialization(limit = "INLINE_CACHE_SIZE", guards = "receiver == cachedReceiver")
            @SuppressWarnings("unused")
            protected static Object doCached(NativeToJavaWrapper receiver, Object[] arguments,
                            @Cached(value = "receiver", weak = true) NativeToJavaWrapper cachedReceiver,
                            @CachedLibrary("cachedReceiver.delegate") InteropLibrary delegateInterop) throws ArityException {
                return cachedReceiver.doExecute(arguments, delegateInterop);
            }

            @Specialization(replaces = "doCached")
            protected static Object doGeneric(NativeToJavaWrapper receiver, Object[] arguments,
                            @CachedLibrary("receiver.delegate") InteropLibrary delegateInterop) throws ArityException {
                return receiver.doExecuteBoundary(arguments, delegateInterop);
            }
        }
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeSignature nativeSignature) {
        if (uncachedInterop.isNull(symbol)) {
            return null; // LD_DEBUG=unused makes non-existing symbols to be NULL.
        }
        TruffleObject executable = (TruffleObject) uncachedSignature.bind(getCallableSignature(nativeSignature, true), symbol);
        assert uncachedInterop.isExecutable(executable);
        return new NativeToJavaWrapper(executable, nativeSignature);
    }

    @Override
    @TruffleBoundary
    public Object getCallableSignature(NativeSignature nativeSignature, boolean fromJava) {
        return getOrCreateNFISignature(nativeSignature, fromJava);
    }

    @Override
    public Object callSignature(Object signature, @Pointer TruffleObject symbol, Object... args) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        return SignatureLibrary.getUncached().call(signature, symbol, args);
    }

    @Override
    public SignatureCallNode createSignatureCall(NativeSignature nativeSignature, boolean fromJava) {
        return NFISignatureCallNode.create(getCallableSignature(nativeSignature, fromJava));
    }

    @Override
    public @Pointer TruffleObject createNativeClosure(TruffleObject executable, NativeSignature nativeSignature) {
        assert uncachedInterop.isExecutable(executable);
        TruffleObject wrappedExecutable = new JavaToNativeWrapper(executable, nativeSignature);
        TruffleObject nativeFn = (TruffleObject) uncachedSignature.createClosure(getCallableSignature(nativeSignature, false), wrappedExecutable);
        assert uncachedInterop.isPointer(nativeFn);
        return nativeFn;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class JavaToNativeWrapper implements TruffleObject {
        private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, JavaToNativeWrapper.class);

        final TruffleObject delegate;
        final NativeSignature nativeSignature;

        JavaToNativeWrapper(TruffleObject executable, NativeSignature nativeSignature) {
            this.delegate = executable;
            this.nativeSignature = nativeSignature;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @TruffleBoundary
        Object doExecuteBoundary(Object[] arguments, InteropLibrary delegateInterop) throws ArityException {
            return doExecute(arguments, delegateInterop);
        }

        @ExplodeLoop
        Object doExecute(Object[] arguments, InteropLibrary delegateInterop) throws ArityException {
            final int paramCount = nativeSignature.getParameterCount() + nativeSignature.getVarArgsParameterCount();
            CompilerAsserts.partialEvaluationConstant(paramCount);
            if (arguments.length != paramCount) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(paramCount, paramCount, arguments.length);
            }
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < paramCount; i++) {
                    NativeType param = nativeSignature.parameterTypeAt(i);
                    CompilerAsserts.partialEvaluationConstant(param);
                    switch (param) {
                        case BOOLEAN:
                            convertedArgs[i] = (byte) arguments[i] != 0;
                            break;
                        case CHAR:
                            convertedArgs[i] = (char) (short) arguments[i];
                            break;
                        default:
                            convertedArgs[i] = arguments[i];
                    }
                }
                Object ret = delegateInterop.execute(delegate, convertedArgs);
                CompilerAsserts.partialEvaluationConstant(nativeSignature.getReturnType());
                switch (nativeSignature.getReturnType()) {
                    case BOOLEAN:
                        ret = ((boolean) ret) ? (byte) 1 : (byte) 0;
                        break;
                    case CHAR:
                        ret = (short) (char) ret;
                        break;
                    case VOID:
                        ret = StaticObject.NULL;
                        break;
                }
                return ret;
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            } catch (AbstractTruffleException | StackOverflowError | OutOfMemoryError e) {
                throw e;
            } catch (Throwable t) {
                logger.log(Level.FINE, "Exception seen", t);
                throw t;
            }
        }

        @ReportPolymorphism
        @ExportMessage
        abstract static class Execute {

            static final int INLINE_CACHE_SIZE = 2;

            @Specialization(limit = "INLINE_CACHE_SIZE", guards = "receiver == cachedReceiver")
            @SuppressWarnings("unused")
            protected static Object doCached(JavaToNativeWrapper receiver, Object[] arguments,
                            @Cached(value = "receiver", weak = true) JavaToNativeWrapper cachedReceiver,
                            @CachedLibrary("cachedReceiver.delegate") InteropLibrary delegateInterop) throws ArityException {
                return cachedReceiver.doExecute(arguments, delegateInterop);
            }

            @Specialization(replaces = "doCached")
            protected static Object doGeneric(JavaToNativeWrapper receiver, Object[] arguments,
                            @CachedLibrary("receiver.delegate") InteropLibrary delegateInterop) throws ArityException {
                return receiver.doExecuteBoundary(arguments, delegateInterop);
            }
        }
    }

    @Override
    public void prepareThread() {
        // nop
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        long address = 0L;
        try {
            address = UNSAFE.allocateMemory(size);
        } catch (OutOfMemoryError e) {
            return null;
        }
        return TruffleByteBuffer.wrap(RawPointer.create(address), Math.toIntExact(size));
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize) {
        assert InteropLibrary.getUncached().isPointer(buffer);
        long oldAddress = 0L;
        try {
            oldAddress = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
        long newAddress = 0L;
        try {
            newAddress = UNSAFE.reallocateMemory(oldAddress, newSize);
        } catch (OutOfMemoryError e) {
            return null;
        }
        return TruffleByteBuffer.wrap(RawPointer.create(newAddress), Math.toIntExact(newSize));
    }

    @Override
    public void freeMemory(@Pointer TruffleObject buffer) {
        assert InteropLibrary.getUncached().isPointer(buffer);
        long address = 0L;
        try {
            address = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
        UNSAFE.freeMemory(address);
    }

    @Collect(NativeAccess.class)
    public static final class Provider implements NativeAccess.Provider {

        public static final String ID = "nfi-native";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFINativeAccess(env);
        }
    }

}
