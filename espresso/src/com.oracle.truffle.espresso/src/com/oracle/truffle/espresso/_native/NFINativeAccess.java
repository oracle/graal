package com.oracle.truffle.espresso._native;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
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
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public class NFINativeAccess implements NativeAccess {

    private static final boolean CACHE_SIGNATURES = "true".equals(System.getProperty("espresso.nfi.cache_signatures", "true"));

    private final Map<NativeSignature, Object> signatureCache;

    static final class NativeSignature {
        private final NativeType returnType;
        @CompilationFinal(dimensions = 1) private final NativeType[] parameterTypes;

        NativeSignature(NativeType returnType, NativeType[] parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            NativeSignature that = (NativeSignature) other;
            return returnType == that.returnType && Arrays.equals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(returnType);
            result = 31 * result + Arrays.hashCode(parameterTypes);
            return result;
        }

        @Override
        public String toString() {
            return nfiStringSignature(returnType, parameterTypes);
        }
    }

    protected final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    protected final SignatureLibrary SIGNATURE = SignatureLibrary.getUncached();

    private final EspressoContext context;

    protected EspressoContext getContext() {
        return context;
    }

    protected static NativeSimpleType nfiType(NativeType ntype) {
        switch (ntype) {
            case VOID:
                return NativeSimpleType.VOID;
            case BOOLEAN: // fall-through
            case BYTE:
                return NativeSimpleType.SINT8;
            case CHAR: // fall-through
            case SHORT:
                return NativeSimpleType.SINT16;
            case INT:
                return NativeSimpleType.SINT32;
            case LONG:
                return NativeSimpleType.SINT64;
            case FLOAT:
                return NativeSimpleType.FLOAT;
            case DOUBLE:
                return NativeSimpleType.DOUBLE;
            case OBJECT:
                return NativeSimpleType.SINT64; // word-sized handle
            case POINTER:
                return NativeSimpleType.POINTER;
            default:
                throw EspressoError.shouldNotReachHere("Unexpected: " + ntype);
        }
    }

    protected static String nfiStringSignature(NativeType returnType, NativeType... parameterTypes) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('(');
        boolean isFirst = true;
        for (NativeType param : parameterTypes) {
            if (!isFirst) {
                sb.append(',');
            }
            sb.append(nfiType(param));
            isFirst = false;
        }
        sb.append(')');
        sb.append(':');
        sb.append(nfiType(returnType));
        return sb.toString();
    }

    protected Object nfiCachedSignature(NativeType returnType, NativeType... parameterTypes) {
        NativeSignature ns = new NativeSignature(returnType, parameterTypes);
        return signatureCache.computeIfAbsent(ns, new Function<NativeSignature, Object>() {
            @Override
            public Object apply(NativeSignature nativeSignature) {
                Source source = Source.newBuilder("nfi", nativeSignature.toString(), "signature").build();
                CallTarget target = getContext().getEnv().parseInternal(source);
                return target.call();
            }
        });
    }

    public NFINativeAccess(EspressoContext context) {
        this.context = context;
        signatureCache = CACHE_SIGNATURES
                        ? new ConcurrentHashMap<>()
                        : null;
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("load(RTLD_LAZY) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    protected @Pointer TruffleObject loadLibraryHelper(String nfiSource) {
        Source source = Source.newBuilder("nfi", nfiSource, "loadLibrary").build();
        CallTarget target = getContext().getEnv().parseInternal(source);
        try {
            return (TruffleObject) target.call();
        } catch (IllegalArgumentException e) {
            getContext().getLogger().log(Level.SEVERE, "TruffleNFI native library isolation is not supported.", e);
            throw EspressoError.shouldNotReachHere(e);
        } catch (AbstractTruffleException e) {
            // TODO(peterssen): Remove assert once GR-27045 reaches a definitive consensus.
            assert "com.oracle.truffle.nfi.impl.NFIUnsatisfiedLinkError".equals(e.getClass().getName());
            // We treat AbstractTruffleException as if it were an UnsatisfiedLinkError.
            TruffleLogger.getLogger(EspressoLanguage.ID, NativeLibrary.class).fine(e.getMessage());
            return null;
        }
    }

    @Override
    public void unloadLibrary(@Pointer TruffleObject library) {
        // nop
    }

    @Override
    public @Pointer TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName) {
        try {
            return (TruffleObject) INTEROP.readMember(library, symbolName);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (UnknownIdentifierException e) {
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    final static class NativeWrapper implements TruffleObject {

        final TruffleObject delegate;
        private final NativeType returnType;
        private final NativeType[] parameterTypes;

        public NativeWrapper(TruffleObject executable, NativeType returnType, NativeType... parameterTypes) {
            this.delegate = executable;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExplodeLoop
        @ExportMessage
        Object execute(Object[] arguments, @CachedLibrary("this.delegate") InteropLibrary interop) throws ArityException {
            if (arguments.length != parameterTypes.length) {
                transferToInterpreter();
                throw ArityException.create(parameterTypes.length, arguments.length);
            }
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    NativeType param = parameterTypes[i];
                    switch (param) {
                        case BOOLEAN:
                            convertedArgs[i] = (boolean) arguments[i] ? (byte) 1 : (byte) 0;
                            break;
                        case CHAR:
                            convertedArgs[i] = (short) (char) arguments[i];
                            break;
                        default:
                            convertedArgs[i] = arguments[i];
                    }
                }
                Object ret = interop.execute(delegate, convertedArgs);
                switch (returnType) {
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
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeType returnType, NativeType... parameterTypes) {
        if (CACHE_SIGNATURES) {
            TruffleObject executable = (TruffleObject) SIGNATURE.bind(nfiCachedSignature(returnType, parameterTypes), symbol);
            return new NativeWrapper(executable, returnType, parameterTypes);
        } else {
            String signature = nfiStringSignature(returnType, parameterTypes);
            try {
                TruffleObject executable = (TruffleObject) INTEROP.invokeMember(symbol, "bind", signature);
                return new NativeWrapper(executable, returnType, parameterTypes);
            } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Cannot bind " + signature, e);
            }
        }
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        throw new UnsupportedOperationException("allocateMemory");
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Buffer TruffleObject buffer, long newSize) {
        throw new UnsupportedOperationException("reallocateMemory");
    }

    @Override
    public void freeMemory(@Buffer TruffleObject buffer) {
        throw new UnsupportedOperationException("freeMemory");
    }

    @Override
    public @Pointer TruffleObject createNativeClosure(TruffleObject executable, NativeType returnType, NativeType... parameterTypes) {
        throw new UnsupportedOperationException("createNativeClosure");
    }
}
