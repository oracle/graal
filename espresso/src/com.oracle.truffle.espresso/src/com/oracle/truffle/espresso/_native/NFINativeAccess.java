package com.oracle.truffle.espresso._native;

import java.nio.file.Path;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.Buffer;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.jni.Pointer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public class NFINativeAccess implements NativeAccess {

    protected final InteropLibrary UNCACHED = InteropLibrary.getFactory().getUncached();

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

    protected static String nfiSignature(NativeType returnType, NativeType... parameterTypes) {
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

    public NFINativeAccess(EspressoContext context) {
        this.context = context;
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
            return (TruffleObject) UNCACHED.readMember(library, symbolName);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (UnknownIdentifierException e) {
            return null;
        }
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeType returnType, NativeType... parameterTypes) {
        String signature = nfiSignature(returnType, parameterTypes);
        try {
            return (TruffleObject) UNCACHED.invokeMember(symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind " + signature, e);
        }
    }

    @Override
    public void unbindSymbol(@Pointer TruffleObject symbol) {
        throw new UnsupportedOperationException("unbindSymbol");
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

    @Override
    public void releaseClosure(@Pointer TruffleObject closure) {
        throw new UnsupportedOperationException("releaseClosure");
    }
}
