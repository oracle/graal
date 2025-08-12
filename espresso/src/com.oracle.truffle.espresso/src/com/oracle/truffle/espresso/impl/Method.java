/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_DONT_INLINE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FORCE_INLINE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_HIDDEN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SCOPED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_STATIC;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SYNTHETIC;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VARARGS;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.threads.ThreadState.IN_ESPRESSO;

import java.io.PrintStream;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.analysis.frame.FrameAnalysis;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyAssumption;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.bytecode.BytecodePrinter;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.ParserMethod;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ExceptionsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.impl.generics.factory.CoreReflectionFactory;
import com.oracle.truffle.espresso.impl.generics.parser.SignatureParser;
import com.oracle.truffle.espresso.impl.generics.tree.MethodTypeSignature;
import com.oracle.truffle.espresso.impl.generics.tree.ReturnType;
import com.oracle.truffle.espresso.impl.generics.tree.TypeSignature;
import com.oracle.truffle.espresso.impl.generics.visitor.Reifier;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.MethodVersionRef;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.interop.AbstractLookupNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode.MethodHandleInvoker;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.ErrorType;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;
import com.oracle.truffle.espresso.shared.meta.SymbolPool;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;
import com.oracle.truffle.espresso.shared.vtable.PartialMethod;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.threads.Transition;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM.EspressoStackElement;

public final class Method extends Member<Signature> implements MethodRef, TruffleObject, ContextAccess,
                MethodAccess<Klass, Method, Field> {

    public static final Method[] EMPTY_ARRAY = new Method[0];
    public static final MethodVersion[] EMPTY_VERSION_ARRAY = new MethodVersion[0];

    private static final byte GETTER_LENGTH = 5;
    private static final byte STATIC_GETTER_LENGTH = 4;

    private static final byte SETTER_LENGTH = 6;
    private static final byte STATIC_SETTER_LENGTH = 5;

    private final ObjectKlass declaringKlass;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    private final Method proxy;
    @CompilationFinal private String genericSignature;
    @CompilationFinal(dimensions = 1) private EspressoType[] typeArguments;
    @CompilationFinal private EspressoType returnType;

    private final Symbol<Signature> rawSignature;
    private final int rawFlags;

    // the parts of the method that can change when it's redefined
    // are encapsulated within the methodVersion
    @CompilationFinal private volatile MethodVersion methodVersion;

    private boolean removedByRedefinition;
    private final ClassHierarchyAssumption isLeaf;

    private MethodHook[] hooks = MethodHook.EMPTY;
    private final Field.StableBoolean hasActiveHook = new Field.StableBoolean(false);

    Method(Method method) {
        this(method, method.getCodeAttribute());
        assert method.getDeclaringKlass().isInterface();
    }

    private Method(Method method, CodeAttribute split) {
        this.rawSignature = method.rawSignature;
        this.rawFlags = method.rawFlags;
        this.declaringKlass = method.declaringKlass;
        this.methodVersion = new MethodVersion(method.getMethodVersion().klassVersion, method.getRuntimeConstantPool(), method.getParserMethod(),
                        method.getMethodVersion().poisonPill, split);

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ParserException.ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
        }
        // Proxy the method, so that we have the same callTarget if it is not yet initialized.
        // Allows for not duplicating the codeAttribute
        this.proxy = method.proxy;
        this.isLeaf = method.isLeaf;
    }

    Method(ObjectKlass.KlassVersion klassVersion, ParserMethod parserMethod, RuntimeConstantPool pool) {
        this(klassVersion, parserMethod, parserMethod.getSignature(), pool, parserMethod.getFlags());
    }

    Method(ObjectKlass.KlassVersion klassVersion, ParserMethod parserMethod, Symbol<Signature> rawSignature, RuntimeConstantPool pool, int rawFlags) {
        this.declaringKlass = klassVersion.getKlass();
        this.rawSignature = rawSignature;
        this.rawFlags = rawFlags;
        this.methodVersion = new MethodVersion(klassVersion, pool, parserMethod, false, (CodeAttribute) parserMethod.getAttribute(CodeAttribute.NAME));

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ParserException.ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
        }
        this.proxy = this;
        this.isLeaf = getContext().getClassHierarchyOracle().createLeafAssumptionForNewMethod(this);
    }

    public Method identity() {
        return proxy;
    }

    @Override
    public Symbol<Name> getName() {
        return getMethodVersion().getName();
    }

    // can have a different constant pool than it's declaring class
    public ConstantPool getConstantPool() {
        return getRuntimeConstantPool();
    }

    public RuntimeConstantPool getRuntimeConstantPool() {
        return getMethodVersion().pool;
    }

    public ParserMethod getParserMethod() {
        return getMethodVersion().parserMethod;
    }

    public CodeAttribute getCodeAttribute() {
        return getMethodVersion().codeAttribute;
    }

    @Override
    @Idempotent
    public ObjectKlass getDeclaringKlass() {
        return declaringKlass;
    }

    public Symbol<Signature> getRawSignature() {
        if (rawSignature != null) {
            return rawSignature;
        }
        return getParserMethod().getSignature();
    }

    public Symbol<Type>[] getParsedSignature() {
        assert parsedSignature != null;
        return parsedSignature;
    }

    public ClassHierarchyAssumption getLeafAssumption(ClassHierarchyOracle.ClassHierarchyAccessor accessor) {
        Objects.requireNonNull(accessor);
        return isLeaf;
    }

    public int getRefKind() {
        return getMethodVersion().getRefKind();
    }

    public Attribute getAttribute(Symbol<Name> attrName) {
        return getParserMethod().getAttribute(attrName);
    }

    @Override
    public EspressoContext getContext() {
        return declaringKlass.getContext();
    }

    public byte[] getOriginalCode() {
        return getCodeAttribute().getOriginalCode();
    }

    public ExceptionHandler[] getSymbolicExceptionHandlers() {
        return getCodeAttribute().getExceptionHandlers();
    }

    public int[] getSOEHandlerInfo() {
        ArrayList<Integer> toArray = new ArrayList<>();
        for (ExceptionHandler handler : getSymbolicExceptionHandlers()) {
            if (handler.isCatchAll() //
                            || handler.getCatchType() == Types.java_lang_StackOverflowError //
                            || handler.getCatchType() == Types.java_lang_VirtualMachineError //
                            || handler.getCatchType() == Types.java_lang_Error //
                            || handler.getCatchType() == Types.java_lang_Throwable) {
                toArray.add(handler.getStartBCI());
                toArray.add(handler.getEndBCI());
                toArray.add(handler.getHandlerBCI());
            }
        }
        if (toArray.isEmpty()) {
            return null;
        }
        int[] res = new int[toArray.size()];
        int pos = 0;
        for (Integer i : toArray) {
            res[pos++] = i;
        }
        return res;
    }

    public static NativeSignature buildJniNativeSignature(Symbol<Type>[] signature) {
        NativeType returnType = NativeAccess.kindToNativeType(SignatureSymbols.returnKind(signature));
        int argCount = SignatureSymbols.parameterCount(signature);

        // Prepend JNIEnv* and class|receiver.
        NativeType[] parameterTypes = new NativeType[argCount + 2];

        // Prepend JNIEnv*.
        parameterTypes[0] = NativeType.POINTER;

        // Receiver for instance methods, class for static methods.
        parameterTypes[1] = NativeType.OBJECT;

        for (int i = 0; i < argCount; ++i) {
            parameterTypes[i + 2] = NativeAccess.kindToNativeType(SignatureSymbols.parameterKind(signature, i));
        }

        return NativeSignature.create(returnType, parameterTypes);
    }

    private TruffleObject bind(@Pointer TruffleObject symbol) {
        NativeSignature signature = buildJniNativeSignature(getParsedSignature());
        return getNativeAccess().bindSymbol(symbol, signature);
    }

    /**
     * Ensure any callTarget is called immediately before a BCI is advanced, or it could violate the
     * specs on class init.
     */
    public CallTarget getCallTarget() {
        return getMethodVersion().getCallTarget();
    }

    /**
     * Exposed to Interop API, to ensure that VM calls to guest classes properly initialize guest
     * classes prior to the calls.
     */
    public CallTarget getCallTargetForceInit() {
        getDeclaringKlass().safeInitialize();
        return getCallTarget();
    }

    /**
     * Obtains the original call target for the method, ignoring espresso substitutions. Note that
     * this completely ignores the call target cache, therefore, all calls to this method will
     * generate a new CallTarget. This is fine, as this method is not intended to be used outside of
     * the substitutions themselves.
     */
    public CallTarget getCallTargetNoSubstitution() {
        return getMethodVersion().getCallTargetNoSubstitution();
    }

    public boolean usesMonitors() {
        return getMethodVersion().usesMonitors();
    }

    private CallTarget lookupLibJavaCallTarget(String[] lookupNames) {
        // If the loader is null we have a system class, so we attempt a lookup
        // in the native Java library.
        if (StaticObject.isNull(getDeclaringKlass().getDefiningClassLoader())) {
            NativeSignature signature = buildJniNativeSignature(getParsedSignature());
            for (String lookupName : lookupNames) {
                // Look in libjava
                TruffleObject nativeMethod = getNativeAccess().lookupAndBindSymbol(getVM().getJavaLibrary(), lookupName, signature, false, true);
                if (nativeMethod != null) {
                    return EspressoRootNode.createNative(getContext().getJNI(), getMethodVersion(), nativeMethod).getCallTarget();
                }
            }
        }
        return null;
    }

    private CallTarget lookupAgents(String[] lookupNames) {
        if (!getContext().getEspressoEnv().EnableNativeAgents) {
            return null;
        }
        // Look in agents
        for (String lookupName : lookupNames) {
            TruffleObject symbol = getContext().lookupAgentSymbol(lookupName);
            if (symbol != null) {
                TruffleObject nativeMethod = bind(symbol);
                return EspressoRootNode.createNative(getContext().getJNI(symbol), getMethodVersion(), nativeMethod).getCallTarget();
            }
        }
        return null;
    }

    private CallTarget lookupJniCallTarget(Meta meta, String[] lookupNames) {
        for (String lookupName : lookupNames) {
            long handle;
            if (getJavaVersion().java21OrEarlier()) {
                handle = (long) meta.java_lang_ClassLoader_findNative.invokeDirectStatic(getDeclaringKlass().getDefiningClassLoader(), meta.toGuestString(lookupName));
            } else {
                handle = (long) meta.java_lang_ClassLoader_findNative.invokeDirectStatic(
                                getDeclaringKlass().getDefiningClassLoader(),
                                getDeclaringKlass().mirror(),
                                meta.toGuestString(lookupName),
                                meta.toGuestString(getName()));
            }
            if (handle == 0) { // not found
                continue;
            }
            TruffleObject symbol = getVM().getFunction(handle);
            TruffleObject nativeMethod = bind(symbol);
            return EspressoRootNode.createNative(getContext().getJNI(symbol), getMethodVersion(), nativeMethod).getCallTarget();
        }
        return null;
    }

    @Override
    public boolean isConstructor() {
        return Names._init_.equals(getName());
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringKlass().isInterface();
    }

    public boolean canOverride(Method other) {
        assert !isPrivate() && !isStatic() && !other.isPrivate() && !other.isStatic();
        if (other.isPublic() || other.isProtected()) {
            return true;
        }
        return getDeclaringKlass().sameRuntimePackage(other.getDeclaringKlass());
    }

    public ObjectKlass[] getCheckedExceptions() {
        return getMethodVersion().getCheckedExceptions();
    }

    /**
     * Checks whether the method has bytecodes associated with it. Methods without bytecodes are
     * either abstract or native methods.
     *
     * @return whether the definition of this method is Java bytecodes
     */
    public boolean hasBytecodes() {
        return isConcrete() && !isNative();
    }

    /**
     * Checks whether the method has a receiver parameter - i.e., whether it is not static.
     *
     * @return whether the method has a receiver parameter
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    /**
     * Determines if this method is {@link java.lang.Object#Object()}.
     */
    public boolean isJavaLangObjectInit() {
        return getDeclaringKlass().isJavaLangObject() && Names._init_.equals(getName());
    }

    // region Meta.Method

    /**
     * Invoke guest method, parameters and return value are converted to host world. Primitives,
     * primitive arrays are shared, and are passed verbatim, conversions are provided for String and
     * StaticObject.NULL/null. There's no parameter casting based on the method's signature,
     * widening nor narrowing.
     */
    @TruffleBoundary
    public Object invokeWithConversions(Object self, Object... args) {
        Transition transition = Transition.transition(IN_ESPRESSO, this);
        try {
            getLanguage().clearPendingException();
            assert args.length == SignatureSymbols.parameterCount(getParsedSignature());
            // assert !isStatic() || ((StaticObject) self).isStatic();

            final Object[] filteredArgs;
            if (isStatic()) {
                getDeclaringKlass().safeInitialize();
                filteredArgs = new Object[args.length];
                for (int i = 0; i < filteredArgs.length; ++i) {
                    filteredArgs[i] = getMeta().toGuestBoxed(args[i]);
                }
            } else {
                filteredArgs = new Object[args.length + 1];
                filteredArgs[0] = getMeta().toGuestBoxed(self);
                for (int i = 1; i < filteredArgs.length; ++i) {
                    filteredArgs[i] = getMeta().toGuestBoxed(args[i - 1]);
                }
            }
            return getMeta().toHostBoxed(getCallTarget().call(filteredArgs));
        } finally {
            transition.restore(this);
        }
    }

    /**
     * Invokes a guest method without parameter/return type conversions. There's no parameter
     * casting, widening nor narrowing based on the method signature.
     * <p>
     * e.g. Host (boxed) Integer represents int, guest Integer doesn't.
     */
    @TruffleBoundary
    public Object invokeDirect(Object... args) {
        Transition transition = Transition.transition(IN_ESPRESSO, this);
        try {
            getLanguage().clearPendingException();
            assert !isStatic();
            assert args.length == SignatureSymbols.parameterCount(getParsedSignature()) + 1;
            assert getDeclaringKlass().isAssignableFrom(((StaticObject) args[0]).getKlass());
            return getCallTarget().call(args);
        } finally {
            transition.restore(this);
        }
    }

    public static Object call(ResolvedCall<Klass, Method, Field> resolved, Object... args) {
        return switch (resolved.getCallKind()) {
            case STATIC ->
                resolved.getResolvedMethod().invokeDirectStatic(args);
            case DIRECT ->
                resolved.getResolvedMethod().invokeDirect(args);
            case VTABLE_LOOKUP ->
                resolved.getResolvedMethod().invokeDirectVirtual(args);
            case ITABLE_LOOKUP ->
                resolved.getResolvedMethod().invokeDirectInterface(args);
        };
    }

    @TruffleBoundary
    public Object invokeDirectStatic(Object... args) {
        Transition transition = Transition.transition(IN_ESPRESSO, this);
        try {
            getLanguage().clearPendingException();
            assert isStatic();
            assert args.length == SignatureSymbols.parameterCount(getParsedSignature());
            getDeclaringKlass().safeInitialize();
            return getCallTarget().call(args);
        } finally {
            transition.restore(this);
        }
    }

    @TruffleBoundary
    public Object invokeDirectVirtual(Object... args) {
        assert getVTableIndex() >= 0;
        StaticObject self = (StaticObject) args[0];
        return self.getKlass().vtableLookup(getVTableIndex()).invokeDirect(args);
    }

    @TruffleBoundary
    public Object invokeDirectInterface(Object... args) {
        assert getITableIndex() >= 0;
        StaticObject self = (StaticObject) args[0];
        return ((ObjectKlass) self.getKlass()).itableLookup(getDeclaringKlass(), getITableIndex()).invokeDirect(args);
    }

    @TruffleBoundary
    public Object invokeDirectSpecial(Object... args) {
        assert isFinalFlagSet() || getDeclaringKlass().isFinalFlagSet() || isPrivate() || isConstructor();
        return invokeDirect(args);
    }

    @Override
    public boolean isClassInitializer() {
        return Names._clinit_.equals(getName()) && isStatic();
    }

    @Override
    public int getModifiers() {
        return rawFlags;
    }

    public boolean isCallerSensitive() {
        return (getModifiers() & ACC_CALLER_SENSITIVE) != 0;
    }

    /**
     * The {@code @ForceInline} annotation only takes effect for methods or constructors of classes
     * loaded by the boot loader. Annotations on methods or constructors of classes loaded outside
     * of the boot loader are ignored.
     */
    public boolean isForceInline() {
        return (getModifiers() & ACC_FORCE_INLINE) != 0;
    }

    public boolean isDontInline() {
        // Respect in truffle compilations when that's possible (GR-57507)
        return (getModifiers() & ACC_DONT_INLINE) != 0;
    }

    public boolean isHidden() {
        return (getModifiers() & ACC_HIDDEN) != 0;
    }

    public boolean isScoped() {
        return (getModifiers() & ACC_SCOPED) != 0;
    }

    public int getMethodModifiers() {
        return getModifiers() & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
    }

    @Override
    public String toString() {
        return getMethodVersion().toString();
    }

    public JavaKind getReturnKind() {
        return SignatureSymbols.returnKind(getParsedSignature());
    }

    public Klass[] resolveParameterKlasses() {
        // TODO(peterssen): Use resolved signature.
        final Symbol<Type>[] signature = getParsedSignature();
        int paramCount = SignatureSymbols.parameterCount(signature);
        Klass[] paramsKlasses = paramCount > 0 ? new Klass[paramCount] : Klass.EMPTY_ARRAY;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = SignatureSymbols.parameterType(signature, i);
            paramsKlasses[i] = getMeta().resolveSymbolOrFail(paramType,
                            getDeclaringKlass().getDefiningClassLoader(),
                            getDeclaringKlass().protectionDomain());
        }
        return paramsKlasses;
    }

    public EspressoType[] getGenericParameterTypes() {
        if (typeArguments == null) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            resolveMethodTypes();
        }
        return typeArguments;
    }

    public EspressoType getGenericReturnType() {
        if (returnType == null) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            resolveMethodTypes();
        }
        return returnType;
    }

    private void resolveMethodTypes() {
        EspressoType[] result = new EspressoType[getParameterCount()];
        String sig = getGenericSignatureAsString();

        if (sig.isEmpty() || !getContext().isGenericTypeHintsEnabled()) {
            typeArguments = resolveParameterKlasses();
            returnType = resolveReturnKlass();
            return;
        }
        MethodTypeSignature typeSignature = SignatureParser.make().parseMethodSig(sig);
        TypeSignature[] parameterTypes = typeSignature.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            result[i] = getGenericEspressoType(parameterTypes[i]);
        }
        returnType = getGenericEspressoType(typeSignature.getReturnType());
        typeArguments = result;
    }

    @TruffleBoundary
    private EspressoType getGenericEspressoType(ReturnType type) {
        CoreReflectionFactory factory = CoreReflectionFactory.make(methodVersion.getDeclaringKlass());
        Reifier reifier = Reifier.make(factory);
        type.accept(reifier);
        EspressoType espressoType = reifier.getResult();
        if (espressoType == null) {
            return getMeta().java_lang_Object;
        }
        return espressoType;
    }

    public Klass resolveReturnKlass() {
        // TODO(peterssen): Use resolved signature.
        return getMeta().resolveSymbolOrFail(SignatureSymbols.returnType(getParsedSignature()),
                        getDeclaringKlass().getDefiningClassLoader(),
                        getDeclaringKlass().protectionDomain());
    }

    @Idempotent
    public int getParameterCount() {
        return SignatureSymbols.parameterCount(getParsedSignature());
    }

    public int getArgumentCount() {
        return getParameterCount() + (isStatic() ? 0 : 1);
    }

    public static Method getHostReflectiveMethodRoot(StaticObject seed, Meta meta) {
        assert seed.getKlass().getMeta().java_lang_reflect_Method.isAssignableFrom(seed.getKlass());
        StaticObject curMethod = seed;
        do {
            Method target = (Method) meta.HIDDEN_METHOD_KEY.getHiddenObject(curMethod);
            if (target != null) {
                return target;
            }
            curMethod = meta.java_lang_reflect_Method_root.getObject(curMethod);
        } while (StaticObject.notNull(curMethod));
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Could not find HIDDEN_METHOD_KEY");
    }

    public static Method getHostReflectiveConstructorRoot(StaticObject seed, Meta meta) {
        assert seed.getKlass().getMeta().java_lang_reflect_Constructor.isAssignableFrom(seed.getKlass());
        StaticObject curMethod = seed;
        StaticObject rootMethod;
        do {
            Method target = (Method) meta.HIDDEN_CONSTRUCTOR_KEY.getHiddenObject(curMethod);
            if (target != null) {
                return target;
            }
            rootMethod = curMethod;
            curMethod = meta.java_lang_reflect_Constructor_root.getObject(curMethod);
        } while ((StaticObject.notNull(curMethod)));
        CompilerDirectives.transferToInterpreter();
        // the root Constructor was not created by makeConstructor
        // this can happen in ReflectionFactory#generateConstructor
        // use the reflection data to find the constructor.
        // the best would be to use the slot, but we don't have a redefinition-stable int that
        // identifies the constructor.
        Klass holder = meta.java_lang_reflect_Constructor_clazz.getObject(rootMethod).getMirrorKlass(meta);
        Symbol<Signature> signature = rebuildConstructorSignature(meta, rootMethod);
        assert signature != null;
        Method method = holder.lookupDeclaredMethod(Names._init_, signature, Klass.LookupMode.INSTANCE_ONLY);
        assert method != null;
        // remember the mapping for the next query
        meta.HIDDEN_CONSTRUCTOR_KEY.setHiddenObject(rootMethod, method);
        return method;
    }

    private static Symbol<Signature> rebuildConstructorSignature(Meta meta, StaticObject rootMethod) {
        StaticObject ptypes = meta.java_lang_reflect_Constructor_parameterTypes.getObject(rootMethod);
        return meta.getSignatures().lookupValidSignature(getSignatureFromGuestDescription(ptypes, meta._void.mirror(), meta));
    }

    public static ByteSequence getSignatureFromGuestDescription(@JavaType(Class[].class) StaticObject ptypes,
                    @JavaType(Class.class) StaticObject rtype, Meta meta) {
        Symbol<Type> returnType = rtype.getMirrorKlass(meta).getType();
        StaticObject[] parameterTypes = ptypes.unwrap(meta.getLanguage());
        int len = 2;
        for (StaticObject parameterType : parameterTypes) {
            len += parameterType.getMirrorKlass(meta).getType().length();
        }
        len += returnType.length();
        byte[] bytes = new byte[len];
        int pos = 0;
        bytes[pos++] = '(';
        for (StaticObject parameterType : parameterTypes) {
            Symbol<Type> paramType = parameterType.getMirrorKlass(meta).getType();
            paramType.writeTo(bytes, pos);
            pos += paramType.length();
        }
        bytes[pos++] = ')';
        returnType.writeTo(bytes, pos);
        pos += returnType.length();
        assert pos == bytes.length;
        return ByteSequence.wrap(bytes);
    }

    // Polymorphic signature method 'creation'

    Method findIntrinsic(Symbol<Signature> signature) {
        return getContext().getMethodHandleIntrinsics().findIntrinsic(this, signature);
    }

    public boolean isSignaturePolymorphicDeclared() {
        // JVM 2.9 Special Methods:
        // A method is signature polymorphic if and only if all of the following conditions hold :
        // * It is declared in the java.lang.invoke.MethodHandle or java.lang.invoke.VarHandle
        // class.
        // * It has a single formal parameter of type Object[].
        // * It has the ACC_VARARGS and ACC_NATIVE flags set.
        // * ONLY JAVA <= 8: It has a return type of Object.
        if (!Meta.isSignaturePolymorphicHolderType(getDeclaringKlass().getType())) {
            return false;
        }
        Symbol<Type>[] signature = getParsedSignature();
        if (SignatureSymbols.parameterCount(signature) != 1) {
            return false;
        }
        if (SignatureSymbols.parameterType(signature, 0) != Types.java_lang_Object_array) {
            return false;
        }
        if (getJavaVersion().java8OrEarlier()) {
            if (SignatureSymbols.returnType(signature) != Types.java_lang_Object) {
                return false;
            }
        }
        int required = ACC_NATIVE | ACC_VARARGS;
        int flags = getModifiers();
        return (flags & required) == required;
    }

    public int getVTableIndex() {
        return getMethodVersion().getVTableIndex();
    }

    void setITableIndex(int i) {
        getMethodVersion().setITableIndex(i);
    }

    public int getITableIndex() {
        return getMethodVersion().getITableIndex();
    }

    public boolean hasCode() {
        return getCodeAttribute() != null || isNative();
    }

    public boolean isVirtualCall() {
        return !isStatic() && !isConstructor() && !isPrivate() && !getDeclaringKlass().isInterface();
    }

    public Method setPoisonPill() {
        getMethodVersion().setPoisonPill();
        return this;
    }

    public String report(int curBCI) {
        String sourceFile = getDeclaringKlass().getSourceFile();
        if (sourceFile == null) {
            sourceFile = "unknown source";
        }
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(" + sourceFile + ":" + bciToLineNumber(curBCI) + ")";
    }

    public String report() {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(unknown source)";
    }

    public boolean isInvokeIntrinsic() {
        return isNative() && MethodHandleIntrinsics.getId(this) == MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
    }

    public boolean isPolySignatureIntrinsic() {
        return isNative() && MethodHandleIntrinsics.getId(this) != MethodHandleIntrinsics.PolySigIntrinsics.None;
    }

    public boolean isInlinableGetter() {
        if (!getSubstitutions().hasSubstitutionFor(this) && !isDontInline()) {
            if (getParameterCount() == 0 && !isAbstract() && !isNative() && !isSynchronized()) {
                return hasGetterBytecodes();
            }
        }
        return false;
    }

    private boolean hasGetterBytecodes() {
        byte[] code = getOriginalCode();
        if (isStatic()) {
            if (code.length == STATIC_GETTER_LENGTH && getSymbolicExceptionHandlers().length == 0) {
                return (code[0] == (byte) GETSTATIC) && (Bytecodes.isReturn(code[3])) && code[3] != (byte) RETURN;
            }
        } else {
            if (code.length == GETTER_LENGTH && getSymbolicExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) GETFIELD) && (Bytecodes.isReturn(code[4])) && code[4] != (byte) RETURN;
            }
        }
        return false;
    }

    public boolean isInlinableSetter() {
        if (!getSubstitutions().hasSubstitutionFor(this) && !isDontInline()) {
            if (getParameterCount() == 1 && !isAbstract() && !isNative() && !isSynchronized()) {
                return hasSetterBytecodes();
            }
        }
        return false;
    }

    private boolean hasSetterBytecodes() {
        byte[] code = getOriginalCode();
        if (isStatic()) {
            if (code.length == STATIC_SETTER_LENGTH && getSymbolicExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) PUTSTATIC) && (code[4] == (byte) RETURN);
            }
        } else {
            if (code.length == SETTER_LENGTH && getSymbolicExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (Bytecodes.isLoad1(code[1])) && (code[2] == (byte) PUTFIELD) && (code[5] == (byte) RETURN);
            }
        }
        return false;
    }

    public void unregisterNative() {
        assert isNative();
        if (getMethodVersion().callTarget != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethodVersion().callTarget = null;
        }
    }

    @SuppressWarnings("unused")
    public void printBytecodes(PrintStream out) {
        BytecodePrinter.printBytecode(declaringKlass, out, getOriginalCode());
    }

    @TruffleBoundary
    @Override
    public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2, Function<String, RuntimeException> errorHandler) {
        for (Symbol<Type> type : getParsedSignature()) {
            getContext().getRegistries().checkLoadingConstraint(type, loader1, loader2, errorHandler);
        }
    }

    public int getCatchLocation(int bci, StaticObject ex) {
        ExceptionHandler[] handlers = getSymbolicExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (toCheck.covers(bci)) {
                Klass catchType = null;
                if (!toCheck.isCatchAll()) {
                    catchType = getRuntimeConstantPool().resolvedKlassAt(getDeclaringKlass(), toCheck.catchTypeCPI());
                }
                if (catchType == null || InterpreterToVM.instanceOf(ex, catchType)) {
                    // the first found exception handler is our exception handler
                    resolved = toCheck;
                    break;
                }
            }
        }
        if (resolved != null) {
            return resolved.getHandlerBCI();
        } else {
            return -1;
        }
    }

    // Spawns a placeholder method for MH intrinsics
    public Method createIntrinsic(Symbol<Signature> polymorphicRawSignature) {
        assert isPolySignatureIntrinsic();
        int flags;
        MethodHandleIntrinsics.PolySigIntrinsics iid = MethodHandleIntrinsics.getId(this);
        if (iid == MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric) {
            flags = getModifiers() & ~ACC_VARARGS;
        } else {
            flags = ACC_NATIVE | ACC_SYNTHETIC | ACC_FINAL;
            if (iid.isStaticPolymorphicSignature()) {
                flags |= ACC_STATIC;
            }
        }
        assert Modifier.isNative(flags);
        return new Method(declaringKlass.getKlassVersion(), getParserMethod(), polymorphicRawSignature, getRuntimeConstantPool(), flags);
    }

    public MethodHandleIntrinsicNode spawnIntrinsicNode(MethodHandleInvoker invoker) {
        assert isPolySignatureIntrinsic();
        return MethodHandleIntrinsics.createIntrinsicNode(getMeta(), this, invoker);
    }

    public Method forceSplit() {
        Method result = new Method(this, getCodeAttribute());
        EspressoRootNode root = EspressoRootNode.createForBytecodes(result.getMethodVersion());
        result.getMethodVersion().callTarget = root.getCallTarget();
        return result;
    }

    // region jdwp-specific

    @TruffleBoundary
    public String getInteropString() {
        return getNameAsString() + AbstractLookupNode.METHOD_SELECTION_SEPARATOR + getRawSignature();
    }

    SharedRedefinitionContent redefine(ObjectKlass.KlassVersion klassVersion, ParserMethod newMethod, ParserKlass newKlass) {
        // install the new method version immediately
        RuntimeConstantPool runtimePool = new RuntimeConstantPool(newKlass.getConstantPool(), klassVersion.getKlass());
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Names.Code);
        MethodVersion oldVersion = methodVersion;
        methodVersion = oldVersion.replace(klassVersion, runtimePool, newMethod, newCodeAttribute);
        return new SharedRedefinitionContent(methodVersion, newMethod, runtimePool, newCodeAttribute);
    }

    void redefine(ObjectKlass.KlassVersion klassVersion, SharedRedefinitionContent content) {
        // install the new method version immediately
        MethodVersion oldVersion = methodVersion;
        methodVersion = oldVersion.replace(klassVersion, content.getPool(), content.getParserMethod(), content.codeAttribute);
    }

    MethodVersion swapMethodVersion(ObjectKlass.KlassVersion klassVersion) {
        MethodVersion oldVersion = methodVersion;
        CodeAttribute codeAttribute = oldVersion.getCodeAttribute();
        // create a copy of the code attribute using the original
        // code of the old version. An obsolete method could be
        // running quickened bytecode and we can't safely patch
        // the bytecodes back to the original.
        CodeAttribute newCodeAttribute = codeAttribute != null ? new CodeAttribute(codeAttribute) : null;
        methodVersion = oldVersion.replace(klassVersion, oldVersion.pool, oldVersion.parserMethod, newCodeAttribute);
        return methodVersion;
    }

    public MethodVersion getMethodVersion() {
        MethodVersion version = methodVersion;
        if (!version.getRedefineAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // we block until class redefinition is done
            // unless we're the redefine thread
            getContext().getClassRedefinition().check();
            if (isRemovedByRedefinition()) {
                // for a removed method, we return the latest known
                // method version in case active frames try to
                // retrieve information for obsolete methods
                return version;
            }
            do {
                version = methodVersion;
            } while (!version.getRedefineAssumption().isValid());
        }
        return version;
    }

    public void removedByRedefinition() {
        removedByRedefinition = true;
    }

    @Override
    public boolean isRemovedByRedefinition() {
        return removedByRedefinition;
    }

    @TruffleBoundary
    public StaticObject makeMirror(Meta meta) {
        if (isConstructor()) {
            return makeConstructorMirror(meta);
        } else {
            return makeMethodMirror(meta);
        }
    }

    @TruffleBoundary
    public StaticObject makeConstructorMirror(Meta meta) {
        assert isConstructor();
        Attribute rawRuntimeVisibleAnnotations = getAttribute(Names.RuntimeVisibleAnnotations);
        StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleParameterAnnotations = getAttribute(Names.RuntimeVisibleParameterAnnotations);
        StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleTypeAnnotations = getAttribute(Names.RuntimeVisibleTypeAnnotations);
        StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                        : StaticObject.NULL;

        final Klass[] rawParameterKlasses = resolveParameterKlasses();
        StaticObject parameterTypes = meta.java_lang_Class.allocateReferenceArray(
                        getParameterCount(),
                        new IntFunction<StaticObject>() {
                            @Override
                            public StaticObject apply(int j) {
                                return rawParameterKlasses[j].mirror();
                            }
                        });

        final Klass[] rawCheckedExceptions = getCheckedExceptions();
        StaticObject checkedExceptions = meta.java_lang_Class.allocateReferenceArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int j) {
                return rawCheckedExceptions[j].mirror();
            }
        });

        StaticObject guestGenericSignature = StaticObject.NULL;
        String genericSignatureString = getGenericSignatureAsString();
        if (genericSignatureString != null && !genericSignatureString.isEmpty()) {
            guestGenericSignature = meta.toGuestString(genericSignatureString);
        }

        StaticObject instance = meta.java_lang_reflect_Constructor.allocateInstance(getContext());
        meta.java_lang_reflect_Constructor_init.invokeDirectSpecial(
                        /* this */ instance,
                        /* declaringKlass */ getDeclaringKlass().mirror(),
                        /* parameterTypes */ parameterTypes,
                        /* checkedExceptions */ checkedExceptions,
                        /* modifiers */ getMethodModifiers(),
                        /* slot */ 0, // unused
                        /* signature */ guestGenericSignature,
                        /* annotations */ runtimeVisibleAnnotations,
                        /* parameterAnnotations */ runtimeVisibleParameterAnnotations);

        meta.HIDDEN_CONSTRUCTOR_KEY.setHiddenObject(instance, this);
        meta.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);

        return instance;
    }

    @TruffleBoundary
    public StaticObject makeMethodMirror(Meta meta) {
        assert !isConstructor();
        Attribute rawRuntimeVisibleAnnotations = getAttribute(Names.RuntimeVisibleAnnotations);
        StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleParameterAnnotations = getAttribute(Names.RuntimeVisibleParameterAnnotations);
        StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleTypeAnnotations = getAttribute(Names.RuntimeVisibleTypeAnnotations);
        StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawAnnotationDefault = getAttribute(Names.AnnotationDefault);
        StaticObject annotationDefault = rawAnnotationDefault != null
                        ? StaticObject.wrap(rawAnnotationDefault.getData(), meta)
                        : StaticObject.NULL;
        final Klass[] rawParameterKlasses = resolveParameterKlasses();
        StaticObject parameterTypes = meta.java_lang_Class.allocateReferenceArray(
                        getParameterCount(),
                        new IntFunction<StaticObject>() {
                            @Override
                            public StaticObject apply(int j) {
                                return rawParameterKlasses[j].mirror();
                            }
                        });

        final Klass[] rawCheckedExceptions = getCheckedExceptions();
        StaticObject guestCheckedExceptions = meta.java_lang_Class.allocateReferenceArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int j) {
                return rawCheckedExceptions[j].mirror();
            }
        });

        SignatureAttribute signatureAttribute = (SignatureAttribute) getAttribute(Names.Signature);
        StaticObject guestGenericSignature = StaticObject.NULL;
        if (signatureAttribute != null) {
            String sig = getConstantPool().utf8At(signatureAttribute.getSignatureIndex(), "signature").toString();
            guestGenericSignature = meta.toGuestString(sig);
        }

        StaticObject instance = meta.java_lang_reflect_Method.allocateInstance(meta.getContext());

        meta.java_lang_reflect_Method_init.invokeDirectSpecial(
                        /* this */ instance,
                        /* declaringClass */ getDeclaringKlass().mirror(),
                        /* name */ getContext().getStrings().intern(getName()),
                        /* parameterTypes */ parameterTypes,
                        /* returnType */ resolveReturnKlass().mirror(),
                        /* checkedExceptions */ guestCheckedExceptions,
                        /* modifiers */ getMethodModifiers(),
                        /* slot */ getVTableIndex(),
                        /* signature */ guestGenericSignature,
                        /* annotations */ runtimeVisibleAnnotations,
                        /* parameterAnnotations */ runtimeVisibleParameterAnnotations,
                        /* annotationDefault */ annotationDefault);
        meta.HIDDEN_METHOD_KEY.setHiddenObject(instance, this);
        meta.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);
        return instance;
    }

    /**
     * Returns the maximally specific method between the two given methods. If they are both
     * maximally-specific, returns a proxy of the second, to which a poison pill has been set.
     * <p>
     * Determining maximally specific method works as follow:
     * <li>If both methods are abstract, return any of the two.
     * <li>If exactly one is non-abstract, return it.
     * <li>If both are non-abstract, check if one of the declaring class subclasses the other. If
     * that is the case, return the method that is lower in the hierarchy. Otherwise, return a
     * freshly spawned proxy method pointing to either of them, which is set to fail on invocation.
     */
    public static MethodVersion resolveMaximallySpecific(Method m1, Method m2) {
        ObjectKlass k1 = m1.getDeclaringKlass();
        ObjectKlass k2 = m2.getDeclaringKlass();
        if (k1.isAssignableFrom(k2)) {
            return m2.getMethodVersion();
        } else if (k2.isAssignableFrom(k1)) {
            return m1.getMethodVersion();
        } else {
            boolean b1 = m1.isAbstract();
            boolean b2 = m2.isAbstract();
            if (b1 && b2) {
                return m1.getMethodVersion();
            }
            if (b1) {
                return m2.getMethodVersion();
            }
            if (b2) {
                return m1.getMethodVersion();
            }
            // JVM specs:
            // Can *declare* ambiguous default method (in bytecodes only, javac wouldn't compile
            // it). (5.4.3.3.)
            //
            // But if you try to *use* them, specs dictate to fail. (6.5.invoke{virtual,interface})
            return new Method(m2).setPoisonPill().getMethodVersion();
        }
    }

    // region MethodAccess impl

    @Override
    public Symbol<Signature> getSymbolicSignature() {
        return getRawSignature();
    }

    @Override
    public Symbol<Type>[] getParsedSymbolicSignature(SymbolPool symbolPool) {
        return getParsedSignature();
    }

    @Override
    @Idempotent
    // Re-implement here for indempotent annotation. Some of our nodes benefit from it.
    public boolean isAbstract() {
        return super.isAbstract();
    }

    @Override
    public boolean shouldSkipLoadingConstraints() {
        return isPolySignatureIntrinsic();
    }

    @Override
    public boolean hasVTableIndex() {
        return getVTableIndex() != -1;
    }

    @Override
    public PartialMethod<Klass, Method, Field> withVTableIndex(int index) {
        assert getVTableIndex() == -1;
        if (getMethodVersion().isInterfaceMethod()) {
            assert getITableIndex() != -1;
            Method proxied = new Method(this);
            proxied.getMethodVersion().setVTableIndex(index);
            return proxied;
        } else {
            getMethodVersion().setVTableIndex(index);
            return this;
        }
    }

    @Override
    public Method asMethodAccess() {
        return this;
    }

    public boolean isProxy() {
        return this != identity();
    }

    public boolean hasPoisonPill() {
        return getMethodVersion().poisonPill;
    }

    // endregion MethodAccess impl

    // region MethodRef impl
    @Override
    public long getBCIFromLine(int line) {
        return getLineNumberTable().getBCI(line);
    }

    @Override
    public Source getSource() {
        return getDeclaringKlass().getSource();
    }

    @Override
    public boolean hasLine(int lineNumber) {
        return getLineNumberTable().getBCI(lineNumber) != -1;
    }

    @Override
    public String getSourceFile() {
        return getDeclaringKlass().getSourceFile();
    }

    @Override
    public String getSignatureAsString() {
        return getRawSignature().toString();
    }

    @Override
    public String getGenericSignatureAsString() {
        if (genericSignature == null) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            SignatureAttribute attr = (SignatureAttribute) getParserMethod().getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ""; // if no generics, the generic signature is empty
            } else {
                genericSignature = getRuntimeConstantPool().utf8At(attr.getSignatureIndex(), "generic signature").toString();
            }
        }
        return genericSignature;
    }

    @Override
    public boolean isMethodNative() {
        return isNative();
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        if (getMethodVersion().codeAttribute != null) {
            return getMethodVersion().codeAttribute.getLocalvariableTable();
        }
        return LocalVariableTable.EMPTY_LVT;
    }

    @Override
    public LocalVariableTable getLocalVariableTypeTable() {
        if (getMethodVersion().codeAttribute != null) {
            return getMethodVersion().codeAttribute.getLocalvariableTypeTable();
        }
        return LocalVariableTable.EMPTY_LVTT;
    }

    @Override
    public LineNumberTableAttribute getLineNumberTable() {
        return getMethodVersion().getLineNumberTableAttribute();
    }

    @Override
    public Object invokeMethodVirtual(Object... args) {
        checkRemovedByRedefinition();
        return invokeDirectVirtual(args);
    }

    @Override
    public Object invokeMethodStatic(Object... args) {
        checkRemovedByRedefinition();
        return invokeDirectStatic(args);
    }

    @Override
    public Object invokeMethodSpecial(Object... args) {
        checkRemovedByRedefinition();
        return invokeDirectSpecial(args);
    }

    @Override
    public Object invokeMethodInterface(Object... args) {
        checkRemovedByRedefinition();
        return invokeDirectInterface(args);
    }

    @Override
    public Object invokeMethodNonVirtual(Object... args) {
        checkRemovedByRedefinition();
        return invokeDirect(args);
    }

    private void checkRemovedByRedefinition() {
        if (isRemovedByRedefinition()) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError,
                            meta.toGuestString(getDeclaringKlass().getNameAsString() + "." + getName() + getRawSignature()));
        }
    }

    @Override
    public KlassRef getDeclaringKlassRef() {
        return getDeclaringKlass();
    }

    @Override
    public int getLastLine() {
        return getLineNumberTable().getLastLine();
    }

    @Override
    public int getLastBCI() {
        int bci = 0;
        BytecodeStream bs = new BytecodeStream(getOriginalCode());
        int end = bs.endBCI();

        while (bci < end) {
            int nextBCI = bs.nextBCI(bci);
            if (nextBCI >= end || nextBCI == bci) {
                return bci;
            } else {
                bci = nextBCI;
            }
        }
        return bci;
    }

    @Override
    public int bciToLineNumber(int bci) {
        if (bci < 0) {
            return bci;
        }
        if (isNative()) {
            return EspressoStackElement.NATIVE_BCI;
        }
        if (getCodeAttribute() == null) {
            assert isAbstract();
            return EspressoStackElement.UNKNOWN_BCI;
        }
        return getCodeAttribute().bciToLineNumber(bci);
    }

    @Override
    public boolean hasSourceFileAttribute() {
        return declaringKlass.getAttribute(Names.SourceFile) != null;
    }

    @Override
    public String getNameAsString() {
        return getName().toString();
    }

    @Override
    public boolean hasActiveHook() {
        return hasActiveHook.get();
    }

    @Override
    public synchronized MethodHook[] getMethodHooks() {
        return Arrays.copyOf(hooks, hooks.length);
    }

    @Override
    public synchronized void addMethodHook(MethodHook info) {
        hasActiveHook.set(true);
        hooks = Arrays.copyOf(hooks, hooks.length + 1);
        hooks[hooks.length - 1] = info;
    }

    private void expectActiveHooks() {
        if (hooks.length == 0) {
            throw new RuntimeException("Method: " + getNameAsString() + " expected to contain method hook");
        }
    }

    @Override
    public synchronized void removeMethodHook(int requestId) {
        expectActiveHooks();
        boolean removed = false;
        // shrink the array to avoid null values
        if (hooks.length == 1) {
            // make sure it's the right hook
            if (hooks[0].getRequestId() == requestId) {
                hooks = MethodHook.EMPTY;
                hasActiveHook.set(false);
                removed = true;
            }
        } else {
            int removeIndex = -1;
            for (int i = 0; i < hooks.length; i++) {
                if (hooks[i].getRequestId() == requestId) {
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex != -1) {
                MethodHook[] temp = new MethodHook[hooks.length - 1];
                for (int i = 0; i < temp.length; i++) {
                    temp[i] = i < removeIndex ? hooks[i] : hooks[i + 1];
                }
                hooks = temp;
                removed = true;
            }
        }
        if (!removed) {
            throw new RuntimeException("Method: " + getNameAsString() + " should contain method hook");
        }
    }

    @Override
    public synchronized void removeMethodHook(MethodHook hook) {
        expectActiveHooks();
        boolean removed = false;
        // shrink the array to avoid null values
        if (hooks.length == 1) {
            // make sure it's the right hook
            if (hooks[0] == hook) {
                hooks = MethodHook.EMPTY;
                hasActiveHook.set(false);
                removed = true;
            }
        } else {
            int removeIndex = -1;
            for (int i = 0; i < hooks.length; i++) {
                if (hooks[i] == hook) {
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex != -1) {
                MethodHook[] temp = new MethodHook[hooks.length - 1];
                for (int i = 0; i < temp.length; i++) {
                    temp[i] = i < removeIndex ? hooks[i] : hooks[i + 1];
                }
                hooks = temp;
                removed = true;
            }
        }
        if (!removed) {
            throw new RuntimeException("Method: " + getNameAsString() + " should contain method hook");
        }
    }

    public synchronized void disposeHooks() {
        hasActiveHook.set(false);
        hooks = MethodHook.EMPTY;
    }
    // endregion Methodref impl

    private static final class Continuum {
        Continuum(LivenessAnalysis livenessAnalysis) {
            this.livenessAnalysis = livenessAnalysis;
        }

        private record ContinuumData(int bci, CallTarget continuable, EspressoFrameDescriptor frameDescriptor) {
        }

        private static final ContinuumData[] EMPTY_DATA = new ContinuumData[0];

        // Force Liveness Analysis for all method in a continuation frame record.
        private final LivenessAnalysis livenessAnalysis;
        @CompilationFinal(dimensions = 1) //
        private volatile ContinuumData[] continuumData = EMPTY_DATA;

        @ExplodeLoop
        private ContinuumData getData(MethodVersion mv, int bci) {
            ContinuumData[] localData = continuumData;
            for (ContinuumData data : localData) {
                if (data.bci() == bci) {
                    return data;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                localData = continuumData;
                for (ContinuumData data : localData) {
                    if (data.bci() == bci) {
                        return data;
                    }
                }
                int prevSize = continuumData.length;
                localData = Arrays.copyOf(continuumData, prevSize + 1);
                EspressoFrameDescriptor fd = FrameAnalysis.apply(mv, bci, livenessAnalysis);
                CallTarget ct = EspressoRootNode.createContinuable(mv, bci, fd).getCallTarget();
                ContinuumData data = new ContinuumData(bci, ct, fd);
                localData[prevSize] = data;
                this.continuumData = localData;
                return data;
            }
        }

        public EspressoFrameDescriptor getFrameDescriptor(MethodVersion mv, int bci) {
            return getData(mv, bci).frameDescriptor();
        }

        public CallTarget getContinuableCallTarget(MethodVersion mv, int bci) {
            return getData(mv, bci).continuable();
        }
    }

    /**
     * Each time a method is changed via class redefinition it gets a new version, and therefore a
     * new MethodVersion object.
     */
    public final class MethodVersion implements MethodVersionRef, ModifiersProvider {
        private static final int CODE_FLAGS_MASK = 0b00001111;
        private static final int CODE_FLAGS_READY = 0x1;
        private static final int CODE_FLAGS_HAS_JSR = 0x2;
        private static final int CODE_FLAGS_USES_MONITORS = 0x4;
        private static final int CODE_FLAGS_HAS_INDY = 0x8;

        /**
         * During KlassVersion initialization, the ObjectKlass is not yet linked to the newest
         * KlassVersion. We therefore use this flag to know during that time if this method is
         * declared in an interface, rather than using
         * {@link #getDeclaringClass()}.{@link ObjectKlass#isInterface() isInterface()}.
         */
        private static final int METHOD_FLAGS_IS_INTERFACE_METHOD = 0b01000000;

        private final ObjectKlass.KlassVersion klassVersion;
        private final RuntimeConstantPool pool;
        private final ParserMethod parserMethod;
        private final CodeAttribute codeAttribute;
        private final ExceptionsAttribute exceptionsAttribute;

        @CompilationFinal private CallTarget callTarget;
        @CompilationFinal private CallTarget callTargetNoSubstitutions;
        @CompilationFinal private Continuum continuum;

        @CompilationFinal private int vtableIndex = -1;
        @CompilationFinal private int itableIndex = -1;

        @CompilationFinal private byte refKind;
        @CompilationFinal private byte methodFlags;

        @CompilationFinal(dimensions = 1) //
        private ObjectKlass[] checkedExceptions;

        // Multiple maximally-specific interface methods. Fail on call.
        @CompilationFinal private boolean poisonPill;

        @CompilationFinal private LivenessAnalysis livenessAnalysis;

        private MethodVersion(ObjectKlass.KlassVersion klassVersion, RuntimeConstantPool pool, ParserMethod parserMethod, boolean poisonPill,
                        CodeAttribute codeAttribute) {
            this.klassVersion = klassVersion;
            this.pool = pool;
            this.parserMethod = parserMethod;
            this.codeAttribute = codeAttribute;
            this.exceptionsAttribute = (ExceptionsAttribute) parserMethod.getAttribute(ExceptionsAttribute.NAME);
            this.poisonPill = poisonPill;
            if (klassVersion.isInterface()) {
                methodFlags |= METHOD_FLAGS_IS_INTERFACE_METHOD;
            }
            initRefKind();
        }

        public SourceSection getWholeMethodSourceSection() {
            Source s = getSource();
            if (s == null) {
                return null;
            }

            LineNumberTableAttribute lineNumberTable = getLineNumberTableAttribute();

            if (lineNumberTable != LineNumberTableAttribute.EMPTY) {
                List<LineNumberTableAttribute.Entry> entries = lineNumberTable.getEntries();
                int startLine = Integer.MAX_VALUE;
                int endLine = 0;

                for (int i = 0; i < entries.size(); i++) {
                    int line = entries.get(i).getLineNumber();
                    if (line > endLine) {
                        endLine = line;
                    }
                    if (line < startLine) {
                        startLine = line;
                    }
                }

                if (startLine >= 1 && endLine >= 1 && startLine <= endLine) {
                    return s.createSection(startLine, -1, endLine, -1);
                } // (else) Most likely generated bytecodes with dummy LineNumberTable attribute.
            }
            return s.createUnavailableSection();
        }

        public SourceSection getSourceSectionAtBCI(int bci) {
            Source s = getSource();
            if (s == null) {
                return null;
            }
            if (codeAttribute == null) {
                return s.createUnavailableSection();
            }
            if (bci < 0 || bci >= codeAttribute.getOriginalCode().length) {
                return s.createUnavailableSection();
            }
            int line = codeAttribute.bciToLineNumber(bci);
            if (line < 0) {
                return s.createUnavailableSection();
            }
            return s.createSection(line);
        }

        public void initRefKind() {
            if (Modifier.isStatic(parserMethod.getFlags())) {
                this.refKind = REF_invokeStatic;
            } else if (Modifier.isPrivate(parserMethod.getFlags()) || Names._init_.equals(parserMethod.getName())) {
                this.refKind = REF_invokeSpecial;
            } else if (klassVersion.isInterface()) {
                this.refKind = REF_invokeInterface;
            } else {
                assert !declaringKlass.isPrimitive();
                this.refKind = REF_invokeVirtual;
            }
        }

        public MethodVersion replace(ObjectKlass.KlassVersion version, RuntimeConstantPool constantPool, ParserMethod newParserMethod, CodeAttribute newCodeAttribute) {
            MethodVersion result = new MethodVersion(version, constantPool, newParserMethod, false, newCodeAttribute);
            // make sure the table indices are copied
            result.vtableIndex = vtableIndex;
            result.itableIndex = itableIndex;
            return result;
        }

        public ParserMethod getParserMethod() {
            return parserMethod;
        }

        @Override
        @Idempotent
        public Method getMethod() {
            return Method.this;
        }

        @Override
        public boolean isObsolete() {
            return !klassVersion.getAssumption().isValid();
        }

        public Symbol<Name> getName() {
            return parserMethod.getName();
        }

        public Symbol<Signature> getRawSignature() {
            return getMethod().getRawSignature();
        }

        public Assumption getRedefineAssumption() {
            return klassVersion.getAssumption();
        }

        public CodeAttribute getCodeAttribute() {
            return codeAttribute;
        }

        void resetTableIndexes() {
            this.vtableIndex = -1;
            this.itableIndex = -1;
        }

        void setVTableIndex(int i) {
            assert vtableIndex == -1 || vtableIndex == i;
            assert itableIndex == -1;
            CompilerAsserts.neverPartOfCompilation();
            this.vtableIndex = i;
        }

        public int getVTableIndex() {
            return vtableIndex;
        }

        void setITableIndex(int i) {
            assert (itableIndex == -1 || itableIndex == i);
            assert vtableIndex == -1;
            CompilerAsserts.neverPartOfCompilation();
            this.itableIndex = i;
        }

        public int getITableIndex() {
            return itableIndex;
        }

        public Method.MethodVersion setPoisonPill() {
            poisonPill = true;
            return this;
        }

        public ExceptionHandler[] getExceptionHandlers() {
            return codeAttribute.getExceptionHandlers();
        }

        public RuntimeConstantPool getPool() {
            return pool;
        }

        @SuppressFBWarnings(value = "DC_DOUBLECHECK", //
                        justification = "Publication uses a release fence, assuming data dependency ordering on the reader side.")
        private CallTarget getCallTargetNoSubstitution() {
            CompilerAsserts.neverPartOfCompilation();
            EspressoError.guarantee(getSubstitutions().hasSubstitutionFor(getMethod()),
                            "Using 'getCallTargetNoSubstitution' should be done only to bypass the substitution mechanism.");
            if (callTargetNoSubstitutions == null) {
                synchronized (this) {
                    if (callTargetNoSubstitutions == null) {
                        CallTarget target = findCallTarget();
                        VarHandle.releaseFence();
                        callTargetNoSubstitutions = target;
                    }
                }
            }
            return callTargetNoSubstitutions;
        }

        public CallTarget getCallTarget() {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, callTarget == null)) {
                if (CompilerDirectives.isCompilationConstant(this)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                resolveCallTarget();
            }
            return callTarget;
        }

        @SuppressFBWarnings(value = "DC_DOUBLECHECK", //
                        justification = "Publication uses a release fence, assuming data dependency ordering on the reader side.")
        private Continuum getContinuum() {
            if (continuum == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Compute LA out of lock.
                LivenessAnalysis la = getLivenessAnalysis();
                if (la.isEmpty()) {
                    /*
                     * Ensure we compute and use a full liveness analysis for all method in the
                     * continuation records, so that the frame capture prunes as much unneeded data
                     * as possible in order to make the records slimmer.
                     */
                    la = LivenessAnalysis.analyze(EspressoOptions.LivenessAnalysisMode.ALL, this);
                }
                synchronized (this) {
                    if (continuum == null) {
                        Continuum c = new Continuum(la);
                        VarHandle.releaseFence();
                        continuum = c;
                    }
                }
            }
            return continuum;
        }

        /**
         * Obtains a {@link com.oracle.truffle.espresso.meta.Meta.ContinuumSupport continuation}
         * call target that can be used for rewinding a continuation.
         */
        public CallTarget getContinuableCallTarget(int bci) {
            return getContinuum().getContinuableCallTarget(this, bci);
        }

        /**
         * Obtains a {@link EspressoFrameDescriptor} describing the shape of the Espresso execution
         * frame at this BCI.
         */
        public EspressoFrameDescriptor getFrameDescriptor(int bci) {
            return getContinuum().getFrameDescriptor(this, bci);
        }

        @TruffleBoundary
        private void resolveCallTarget() {
            Meta meta = getMeta();
            checkPoisonPill(meta);
            synchronized (this) {
                if (callTarget != null) {
                    return;
                }
                CallTarget target;
                if (proxy != Method.this) {
                    target = proxy.getCallTarget();
                } else {
                    /*
                     * The substitution factory does the validation e.g. some substitutions only
                     * apply for classes/methods in the boot or platform class loaders. A warning is
                     * logged if the validation fails.
                     */
                    EspressoRootNode redirectedMethod = getSubstitutions().get(getMethod());
                    if (redirectedMethod != null) {
                        target = redirectedMethod.getCallTarget();
                    } else {
                        assert callTargetNoSubstitutions == null;
                        target = findCallTarget();
                    }
                }
                if (target != null) {
                    VarHandle.releaseFence();
                    callTarget = target;
                }
            }
        }

        private CallTarget findCallTarget() {
            CallTarget target;
            if (isNative()) {
                target = lookupNativeCallTarget(getName());
                // also check native method prefixes set by Java agents if any
                if (target == null && getContext().getJavaAgents() != null && getContext().getJavaAgents().hasNativePrefixes()) {
                    target = lookupWithNativePrefixes();
                }
                // TODO(peterssen): Search JNI methods with OS prefix/suffix
                // (print_jni_name_suffix_on ...)

                if (target == null && isSignaturePolymorphicDeclared()) {
                    /*
                     * Happens only when trying to obtain call target of
                     * MethodHandle.invoke(Object... args), or MethodHandle.invokeExact(Object...
                     * args).
                     *
                     * The method was obtained through a regular lookup (since it is in the declared
                     * methods). Delegate it to a polysignature method lookup.
                     */
                    target = declaringKlass.lookupPolysigMethod(getName(), getRawSignature(), Klass.LookupMode.ALL).getCallTarget();
                }

                if (target == null) {
                    throw unsatisfiedLinkError();
                }
            } else {
                if (codeAttribute == null) {
                    Meta meta = getMeta();
                    throw meta.throwExceptionWithMessage(meta.java_lang_AbstractMethodError,
                                    "Calling abstract method: " + toExternalString());
                }
                EspressoRootNode rootNode = EspressoRootNode.createForBytecodes(this);
                target = rootNode.getCallTarget();
            }
            return target;
        }

        private CallTarget lookupWithNativePrefixes() {
            CallTarget target;
            Symbol<Name> resolvedName = getContext().getJavaAgents().resolveMethodNameFromPrefixes(getName());
            if (getName().equals(resolvedName)) {
                // no need to lookup when nothing changed
                return null;
            }
            // before using the prefix-stripped method name, make sure we have a
            // non-native wrapper method, if not we can't link
            Method wrapperMethod = getDeclaringKlass().lookupDeclaredMethod(resolvedName, getRawSignature(), isStatic() ? Klass.LookupMode.STATIC_ONLY : Klass.LookupMode.INSTANCE_ONLY);
            if (wrapperMethod == null || wrapperMethod.isNative()) {
                return null;
            }
            // OK, we found a usable stripped method name, so redo the lookup
            assert resolvedName != null;
            target = lookupNativeCallTarget(resolvedName);
            return target;
        }

        private CallTarget lookupNativeCallTarget(Symbol<Name> lookupName) {
            Meta meta = getMeta();
            String mangledNameNoSignature = Mangle.mangleMethod(getDeclaringClass().type, lookupName.toString(), null, false);
            String mangledNameWithSignature = Mangle.mangleMethod(getDeclaringClass().type, lookupName.toString(), getRawSignature(), false);
            String[] lookupNames = new String[]{mangledNameNoSignature, mangledNameWithSignature};
            CallTarget target;
            // Bind native method.
            target = lookupLibJavaCallTarget(lookupNames);
            if (target == null) {
                target = lookupAgents(lookupNames);
            }
            if (target == null) {
                target = lookupJniCallTarget(meta, lookupNames);
            }
            return target;
        }

        private EspressoException unsatisfiedLinkError() {
            getContext().getLogger().log(Level.WARNING, "Failed to link native method: {0}", toString());
            Meta meta = getMeta();
            return meta.throwExceptionWithMessage(meta.java_lang_UnsatisfiedLinkError, toExternalString());
        }

        private void checkPoisonPill(Meta meta) {
            if (poisonPill) {
                // Conflicting Maximally-specific non-abstract interface methods.
                if (getJavaVersion().inRange(9, 25) && getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.HOTSPOT) {
                    /*
                     * Supposed to be IncompatibleClassChangeError (see jvms-6.5.invokeinterface),
                     * but HotSpot throws AbstractMethodError. See JDK-8356942.
                     */
                    throw meta.throwExceptionWithMessage(meta.java_lang_AbstractMethodError, "Conflicting default methods: " + getName());
                }
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Conflicting default methods: " + getName());
            }
        }

        public LineNumberTableAttribute getLineNumberTableAttribute() {
            if (codeAttribute != null) {
                LineNumberTableAttribute lineNumberTable = codeAttribute.getLineNumberTableAttribute();
                return lineNumberTable != null ? lineNumberTable : LineNumberTableAttribute.EMPTY;
            }
            return LineNumberTableAttribute.EMPTY;
        }

        @Override
        public String toString() {
            return "EspressoMethod<" + getDeclaringKlass().getType() + "." + getName() + getRawSignature() + ">";
        }

        @TruffleBoundary
        public String toExternalString() {
            return getDeclaringKlass().getExternalName() + "." + getName() + getRawSignature();
        }

        public boolean isInterfaceMethod() {
            return (methodFlags & METHOD_FLAGS_IS_INTERFACE_METHOD) != 0;
        }

        public boolean usesMonitors() {
            // Whether we need to use an additional frame slot for monitor unlock on kill.
            return isSynchronized() || (getCodeFlags() & CODE_FLAGS_USES_MONITORS) != 0;
        }

        /**
         * Returns true if this method uses the JSR/RET bytecodes.
         */
        public boolean hasJsr() {
            return (getCodeFlags() & CODE_FLAGS_HAS_JSR) != 0;
        }

        public boolean usesIndy() {
            return (getCodeFlags() & CODE_FLAGS_HAS_INDY) != 0;
        }

        private byte getCodeFlags() {
            byte localFlags = (byte) (methodFlags & CODE_FLAGS_MASK);
            if ((localFlags & CODE_FLAGS_READY) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (codeAttribute == null) {
                    localFlags = CODE_FLAGS_READY;
                } else {
                    localFlags = computeCodeFlags(codeAttribute.getOriginalCode());
                }
                assert (localFlags & CODE_FLAGS_READY) != 0;
                assert localFlags == (localFlags & CODE_FLAGS_MASK);
                methodFlags |= localFlags;
            }
            return localFlags;
        }

        private static byte computeCodeFlags(byte[] code) {
            BytecodeStream bs = new BytecodeStream(code);
            int bci = 0;
            int flags = CODE_FLAGS_READY;
            while (bci < bs.endBCI()) {
                int opcode = bs.opcode(bci);
                switch (opcode) {
                    case JSR, JSR_W, RET ->
                        flags |= CODE_FLAGS_HAS_JSR;
                    case MONITORENTER, MONITOREXIT ->
                        flags |= CODE_FLAGS_USES_MONITORS;
                    case INVOKEDYNAMIC ->
                        flags |= CODE_FLAGS_HAS_INDY;
                }
                bci = bs.nextBCI(bci);
            }
            return (byte) flags;
        }

        public int getRefKind() {
            return refKind;
        }

        public ObjectKlass[] getCheckedExceptions() {
            if (checkedExceptions == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createCheckedExceptions();
            }
            return checkedExceptions;
        }

        private synchronized void createCheckedExceptions() {
            if (checkedExceptions == null) {
                if (exceptionsAttribute == null) {
                    checkedExceptions = ObjectKlass.EMPTY_ARRAY;
                    return;
                }
                int entryCount = exceptionsAttribute.entryCount();
                ObjectKlass[] tmpchecked = new ObjectKlass[entryCount];
                for (int i = 0; i < entryCount; ++i) {
                    // TODO(peterssen): Resolve and cache CP entries.
                    tmpchecked[i] = (ObjectKlass) pool.resolvedKlassAt(declaringKlass, exceptionsAttribute.entryAt(i));
                }
                checkedExceptions = tmpchecked;
            }
        }

        @SuppressFBWarnings(value = "DC_DOUBLECHECK", //
                        justification = "fields of LivenessAnalysis are final.")
        public LivenessAnalysis getLivenessAnalysis() {
            CompilerAsserts.neverPartOfCompilation();
            LivenessAnalysis result = this.livenessAnalysis;
            if (result == null) {
                synchronized (this) {
                    result = this.livenessAnalysis;
                    if (result == null) {
                        this.livenessAnalysis = result = LivenessAnalysis.analyze(getLanguage().getLivenessAnalysisMode(), this);
                    }
                }
            }
            return result;
        }

        public ObjectKlass.KlassVersion getKlassVersion() {
            return klassVersion;
        }

        public ObjectKlass getDeclaringKlass() {
            return declaringKlass;
        }

        public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
            getMethod().checkLoadingConstraints(loader1, loader2, m -> {
                throw getContext().throwError(ErrorType.LinkageError, m);
            });
        }

        public int getMaxLocals() {
            return codeAttribute.getMaxLocals();
        }

        public int getMaxStackSize() {
            return codeAttribute.getMaxStack();
        }

        @Override
        public int getModifiers() {
            return getMethod().getModifiers();
        }

        public byte[] getOriginalCode() {
            return codeAttribute.getOriginalCode();
        }
    }

    static class SharedRedefinitionContent {
        private final MethodVersion version;
        private final ParserMethod parserMethod;
        private final RuntimeConstantPool pool;
        private final CodeAttribute codeAttribute;

        SharedRedefinitionContent(MethodVersion version, ParserMethod parserMethod, RuntimeConstantPool pool, CodeAttribute codeAttribute) {
            this.version = version;
            this.parserMethod = parserMethod;
            this.pool = pool;
            this.codeAttribute = codeAttribute;
        }

        public MethodVersion getMethodVersion() {
            return version;
        }

        public ParserMethod getParserMethod() {
            return parserMethod;
        }

        public RuntimeConstantPool getPool() {
            return pool;
        }
    }
    // endregion jdwp-specific

}
