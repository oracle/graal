/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class NativeLookup {

    private final TruffleObject defaultLibrary;
    private final List<TruffleObject> libraryHandles;

    private final TruffleLanguage.Env env;

    NativeLookup(Env env) {
        this.env = env;
        this.libraryHandles = loadLibraries(env, LLVMOptions.ENGINE.dynamicNativeLibraryPath());
        this.defaultLibrary = loadDefaultLibrary(env);
    }

    /*
     * PRIVATE
     */

    private static List<TruffleObject> loadLibraries(TruffleLanguage.Env env, String[] dynamicLibraryPaths) {
        CompilerAsserts.neverPartOfCompilation();
        List<TruffleObject> handles = new ArrayList<>();
        for (String library : dynamicLibraryPaths) {
            try {
                TruffleObject lib = loadLibrary(env, library);
                handles.add(lib);
            } catch (UnsatisfiedLinkError e) {
                LLVMLogger.unconditionalInfo(library + " not found!\n" + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
        return handles;
    }

    private static TruffleObject loadLibrary(TruffleLanguage.Env env, String libName) {
        CompilerAsserts.neverPartOfCompilation();
        String loadExpression = String.format("load \"%s\"", libName);
        final Source source = Source.newBuilder(loadExpression).name("(load " + libName + ")").mimeType("application/x-native").build();
        try {
            return (TruffleObject) env.parse(source).call();
        } catch (Exception ex) {
            throw new IllegalArgumentException(loadExpression, ex);
        }
    }

    private static TruffleObject loadDefaultLibrary(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = Source.newBuilder("default").name("default").mimeType("application/x-native").build();
        try {
            return (TruffleObject) env.parse(source).call();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static TruffleObject getNativeFunction(TruffleObject library, String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, name.substring(1));
        } catch (UnknownIdentifierException ex) {
            // try another library
            return null;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String getNativeType(Type type) {
        if (type instanceof FunctionType) {
            return prepareSignature((FunctionType) type, 0);
        } else if (type instanceof PointerType && ((PointerType) type).getPointeeType() instanceof FunctionType) {
            FunctionType functionType = (FunctionType) ((PointerType) type).getPointeeType();
            return prepareSignature(functionType, 0);
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
                default:
                    throw new AssertionError(primitiveType);

            }
        } else if (type instanceof VoidType) {
            return "VOID";
        }
        throw new AssertionError(type);
    }

    private static String[] getNativeTypes(Type[] argTypes, int skipArguments) {
        String[] types = new String[argTypes.length - skipArguments];
        for (int i = skipArguments; i < argTypes.length; i++) {
            types[i - skipArguments] = getNativeType(argTypes[i]);
        }
        return types;
    }

    private static TruffleObject getNativeFunction(List<TruffleObject> libraryHandles, TruffleObject defaultLibrary, String name) {
        for (TruffleObject libraryHandle : libraryHandles) {
            TruffleObject symbol = getNativeFunction(libraryHandle, name);
            if (symbol != null) {
                return symbol;
            }
        }
        TruffleObject symbol = getNativeFunction(defaultLibrary, name);
        if (symbol == null) {
            LLVMLogger.info("external symbol " + name + " could not be resolved!");
        }
        return symbol;
    }

    private static TruffleObject getNativeDataObject(TruffleObject libraryHandle, String name) {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(),
                            libraryHandle, name);
            if (symbol != null && 0 != ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), symbol)) {
                return symbol;
            } else {
                return null;
            }
        } catch (UnknownIdentifierException ex) {
            // try another library
            return null;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TruffleObject getNativeDataObject(List<TruffleObject> libraryHandles, TruffleObject defaultLibrary, String name) {
        String realName = name.substring(1);
        for (TruffleObject libraryHandle : libraryHandles) {
            TruffleObject symbol = getNativeDataObject(libraryHandle, realName);
            if (symbol != null) {
                return symbol;
            }
        }
        TruffleObject symbol = getNativeDataObject(defaultLibrary, realName);
        if (symbol == null) {
            LLVMLogger.info("external symbol " + name + " could not be resolved!");
        }
        return symbol;
    }

    public TruffleObject getNativeDataObject(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return getNativeDataObject(libraryHandles, defaultLibrary, name);
    }

    public TruffleObject getNativeFunction(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return getNativeFunction(libraryHandles, defaultLibrary, name);
    }

    private static TruffleObject bindNativeFunction(TruffleObject symbol, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), symbol, "bind",
                            signature);
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    TruffleObject getNativeFunction(String name, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        TruffleObject nativeSymbol = getNativeFunction(name);
        if (nativeSymbol != null) {
            return bindNativeFunction(nativeSymbol, signature);
        } else {
            return null;
        }
    }

    static String prepareSignature(FunctionType type, int skipArguments) {
        // TODO varargs
        CompilerAsserts.neverPartOfCompilation();
        String nativeRet = getNativeType(type.getReturnType());
        String[] argTypes = getNativeTypes(type.getArgumentTypes(), skipArguments);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (String a : argTypes) {
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

    void addLibraryToNativeLookup(String library) {
        CompilerAsserts.neverPartOfCompilation();
        libraryHandles.add(loadLibrary(env, library));
    }

}
