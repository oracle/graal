/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.espresso.shared.jmh;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.ParserMethod;
import com.oracle.truffle.espresso.classfile.ParsingContext;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.NameSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbols;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Utf8Symbols;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.classfile.perf.TimerCollection;
import com.oracle.truffle.espresso.shared.meta.ErrorType;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.KnownTypes;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.SymbolPool;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * The smallest runtime the bytecode verifier can run against: it parses class files with the shared
 * {@link ClassfileParser} and exposes them through the {@code shared.meta} interfaces. Nothing is
 * ever executed, so the model knows only what verification asks about: class hierarchy, constant
 * pools and the code of methods. Types the verifier names without a class file behind them
 * ({@code java.lang.Object}, arrays, anything else) are stand-ins whose only superclass is
 * {@code java.lang.Object}.
 */
final class BenchRuntime implements RuntimeAccess<BenchRuntime.BType, BenchRuntime.BMethod, BenchRuntime.BField>, ParsingContext {

    private final Symbols symbols = Symbols.fromExisting(new StaticSymbols(ParserSymbols.SYMBOLS, 1 << 8).freeze(), 4 * 1024, 0);
    private final Utf8Symbols utf8 = new Utf8Symbols(symbols);
    private final NameSymbols names = new NameSymbols(symbols);
    private final TypeSymbols types = new TypeSymbols(symbols);
    private final SignatureSymbols signatures = new SignatureSymbols(symbols, types);

    private final SymbolPool symbolPool = new SymbolPool() {
        @Override
        public NameSymbols getNames() {
            return names;
        }

        @Override
        public TypeSymbols getTypes() {
            return types;
        }

        @Override
        public SignatureSymbols getSignatures() {
            return signatures;
        }
    };

    private final Map<Symbol<Type>, BType> loaded = new HashMap<>();
    private final BType object = standIn("Ljava/lang/Object;");
    private final KnownTypes<BType, BMethod, BField> knownTypes = new KnownTypes<>() {
        private final BType throwable = standIn("Ljava/lang/Throwable;");
        private final BType clazz = standIn("Ljava/lang/Class;");
        private final BType string = standIn("Ljava/lang/String;");
        private final BType methodType = standIn("Ljava/lang/invoke/MethodType;");
        private final BType methodHandle = standIn("Ljava/lang/invoke/MethodHandle;");

        @Override
        public BType java_lang_Object() {
            return object;
        }

        @Override
        public BType java_lang_Throwable() {
            return throwable;
        }

        @Override
        public BType java_lang_Class() {
            return clazz;
        }

        @Override
        public BType java_lang_String() {
            return string;
        }

        @Override
        public BType java_lang_invoke_MethodType() {
            return methodType;
        }

        @Override
        public BType java_lang_invoke_MethodHandle() {
            return methodHandle;
        }
    };

    private BType standIn(String descriptor) {
        Symbol<Type> type = types.getOrCreateValidType(descriptor);
        BType existing = loaded.get(type);
        if (existing != null) {
            return existing;
        }
        BType created = new BType(this, type, "Ljava/lang/Object;".equals(descriptor) ? null : object, null);
        loaded.put(type, created);
        return created;
    }

    /** Parses {@code classFile} the way Crema does for a user class loader, and registers it. */
    BType define(byte[] classFile) throws ValidationException {
        ParserKlass parsed = parse(classFile);
        BType type = new BType(this, parsed.getType(), object, parsed);
        loaded.put(parsed.getType(), type);
        return type;
    }

    /** Only the parse: verification-enabled, with the constant pool validated. */
    ParserKlass parse(byte[] classFile) throws ValidationException {
        return ClassfileParser.parse(this, new ClassfileStream(classFile, null), true, false, null, false, false, true);
    }

    // ParsingContext

    @Override
    public JavaVersion getJavaVersion() {
        return JavaVersion.HOST_VERSION;
    }

    @Override
    public boolean isStrictJavaCompliance() {
        return false;
    }

    @Override
    public TimerCollection getTimers() {
        return TimerCollection.create(false);
    }

    @Override
    public boolean isPreviewEnabled() {
        return false;
    }

    @Override
    public ParsingContext.Logger getLogger() {
        return ParsingContext.Logger.NOP;
    }

    @Override
    public Symbol<Name> getOrCreateName(ByteSequence byteSequence) {
        return names.getOrCreate(byteSequence);
    }

    @Override
    public Symbol<Type> getOrCreateTypeFromName(ByteSequence byteSequence) {
        return types.getOrCreateValidType(TypeSymbols.nameToType(byteSequence));
    }

    @Override
    public Symbol<? extends ModifiedUTF8> getOrCreateUtf8(ByteSequence byteSequence) {
        return utf8.getOrCreateValidUtf8(byteSequence, true);
    }

    // RuntimeAccess

    @Override
    public BType lookupOrLoadType(Symbol<Type> type, BType accessingClass) {
        BType known = loaded.get(type);
        if (known != null) {
            return known;
        }
        BType created = new BType(this, type, object, null);
        loaded.put(type, created);
        return created;
    }

    @Override
    public RuntimeException throwError(ErrorType error, String messageFormat, Object... args) {
        throw new IllegalStateException(error + ": " + String.format(messageFormat, args));
    }

    @Override
    public ErrorType getErrorType(Throwable error) {
        return ErrorType.LinkageError;
    }

    @Override
    public KnownTypes<BType, BMethod, BField> getKnownTypes() {
        return knownTypes;
    }

    @Override
    public SymbolPool getSymbolPool() {
        return symbolPool;
    }

    @Override
    public RuntimeException fatal(String messageFormat, Object... args) {
        throw new AssertionError(String.format(messageFormat, args));
    }

    @Override
    public RuntimeException fatal(Throwable t, String messageFormat, Object... args) {
        throw new AssertionError(String.format(messageFormat, args), t);
    }

    /** A class: parsed from a class file, or a stand-in for one the verifier only names. */
    static final class BType implements TypeAccess<BType, BMethod, BField> {
        private final BenchRuntime runtime;
        private final Symbol<Type> type;
        private final BType superClass;
        private final ParserKlass klass;
        private final List<BMethod> methods = new ArrayList<>();

        BType(BenchRuntime runtime, Symbol<Type> type, BType superClass, ParserKlass klass) {
            this.runtime = runtime;
            this.type = type;
            this.superClass = superClass;
            this.klass = klass;
            if (klass != null) {
                for (ParserMethod method : klass.getMethods()) {
                    methods.add(new BMethod(this, method));
                }
            }
        }

        List<BMethod> methods() {
            return methods;
        }

        @Override
        public String getJavaName() {
            return type.toString();
        }

        @Override
        public Symbol<Type> getSymbolicType() {
            return type;
        }

        @Override
        public Symbol<Name> getSymbolicName() {
            return klass != null ? klass.getName() : runtime.names.getOrCreate(type.toString());
        }

        @Override
        public boolean hasSameDefiningClassLoader(BType other) {
            return true;
        }

        @Override
        public BType getSuperClass() {
            return superClass;
        }

        @Override
        public List<BType> getSuperInterfacesList() {
            return List.of();
        }

        @Override
        public BType getHostType() {
            return this;
        }

        @Override
        public Symbol<Name> getSymbolicRuntimePackage() {
            return runtime.names.getOrCreate("");
        }

        @Override
        public BField lookupField(Symbol<Name> name, Symbol<Type> fieldType) {
            return null;
        }

        @Override
        public List<BMethod> getDeclaredMethodsList() {
            return methods;
        }

        @Override
        public List<BMethod> getImplicitInterfaceMethodsList() {
            return List.of();
        }

        @Override
        public BMethod lookupVTableEntry(int vtableIndex) {
            return null;
        }

        @Override
        public boolean isAssignableFrom(BType other) {
            if (other == this || isJavaLangObject()) {
                return true;
            }
            if (TypeSymbols.isArray(type) && TypeSymbols.isArray(other.type)) {
                BType component = runtime.lookupOrLoadType(runtime.types.getComponentType(type), this);
                BType otherComponent = runtime.lookupOrLoadType(runtime.types.getComponentType(other.type), other);
                return component.isAssignableFrom(otherComponent);
            }
            for (BType t = other.superClass; t != null; t = t.superClass) {
                if (t == this) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isMagicAccessor() {
            return false;
        }

        @Override
        public ConstantPool getConstantPool() {
            return klass.getConstantPool();
        }

        @Override
        public BType resolveClassConstantInPool(int cpi) {
            Symbol<Name> name = klass.getConstantPool().className(cpi);
            return runtime.lookupOrLoadType(runtime.types.getOrCreateValidType(TypeSymbols.nameToType(name)), this);
        }

        @Override
        public int getModifiers() {
            return klass != null ? klass.getFlags() : Modifier.PUBLIC;
        }
    }

    /** A method of a parsed class. */
    static final class BMethod implements MethodAccess<BType, BMethod, BField> {
        private final BType holder;
        private final ParserMethod method;
        private final CodeAttribute code;

        BMethod(BType holder, ParserMethod method) {
            this.holder = holder;
            this.method = method;
            CodeAttribute found = null;
            for (Attribute attribute : method.getAttributes()) {
                if (attribute instanceof CodeAttribute codeAttribute) {
                    found = codeAttribute;
                }
            }
            this.code = found;
        }

        @Override
        public BType getDeclaringClass() {
            return holder;
        }

        @Override
        public boolean accessChecks(BType accessingClass, BType holderClass) {
            return true;
        }

        @Override
        public void loadingConstraints(BType accessingClass) {
        }

        @Override
        public int getModifiers() {
            return method.getFlags();
        }

        @Override
        public Symbol<Name> getSymbolicName() {
            return method.getName();
        }

        @Override
        public Symbol<Signature> getSymbolicSignature() {
            return method.getSignature();
        }

        @Override
        public boolean shouldSkipLoadingConstraints() {
            return true;
        }

        @Override
        public boolean requiresInterfaceDispatch(BType symbolicReceiver) {
            return false;
        }

        @Override
        public CodeAttribute getCodeAttribute() {
            return code;
        }

        @Override
        public ExceptionHandler[] getSymbolicExceptionHandlers() {
            return code.getExceptionHandlers();
        }

        @Override
        public boolean isDeclaredSignaturePolymorphic() {
            return false;
        }

        @Override
        public BMethod findSignaturePolymorphicIntrinsic(Symbol<Signature> signature) {
            return null;
        }

        @Override
        public BMethod createSignaturePolymorphicIntrinsic(Symbol<Signature> newSignature) {
            return null;
        }
    }

    /** Fields never come up: the generated classes declare and use none. */
    abstract static class BField implements FieldAccess<BType, BMethod, BField> {
    }
}
