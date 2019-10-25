/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.function.Function;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.CodeAttribute;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ExceptionsAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.SourceFileAttribute;
import com.oracle.truffle.espresso.classfile.LineNumberTable;
import com.oracle.truffle.espresso.debugger.api.MethodRef;
import com.oracle.truffle.espresso.debugger.api.klassRef;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.LocalVariableTable;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoMethodNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.NativeRootNode;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public final class Method extends Member<Signature> implements TruffleObject, ContextAccess, MethodRef {
    public static final Method[] EMPTY_ARRAY = new Method[0];

    private static final byte GETTER_LENGTH = 5;
    private static final byte STATIC_GETTER_LENGTH = 4;

    private static final byte SETTER_LENGTH = 6;
    private static final byte STATIC_SETTER_LENGTH = 5;

    private final Assumption isLeaf;

    private final LinkedMethod linkedMethod;
    private final RuntimeConstantPool pool;

    private final ObjectKlass declaringKlass;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    @CompilationFinal private int vtableIndex = -1;
    @CompilationFinal private int itableIndex = -1;

    private final ExceptionsAttribute exceptionsAttribute;
    private final CodeAttribute codeAttribute;

    @CompilationFinal private int refKind;

    @CompilationFinal //
    private CallTarget callTarget;

    @CompilationFinal(dimensions = 1) //
    private ObjectKlass[] checkedExceptions;

    private final Method proxy;

    public Method identity() {
        return proxy == null ? this : proxy;
    }

    // Multiple maximally-specific interface methods. Fail on call.
    @CompilationFinal private boolean poisonPill = false;

    // can have a different constant pool than it's declaring class
    public ConstantPool getConstantPool() {
        return pool;
    }

    public RuntimeConstantPool getRuntimeConstantPool() {
        return pool;
    }

    @Override
    public ObjectKlass getDeclaringKlass() {
        return declaringKlass;
    }

    public Symbol<Signature> getRawSignature() {
        return descriptor;
    }

    public Symbol<Type>[] getParsedSignature() {
        assert parsedSignature != null;
        return parsedSignature;
    }

    private Source source;

    Method(Method method) {
        super(method.getRawSignature(), method.getName());
        this.declaringKlass = method.declaringKlass;
        // TODO(peterssen): Custom constant pool for methods is not supported.
        this.pool = (RuntimeConstantPool) method.getConstantPool();

        this.linkedMethod = method.linkedMethod;

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw getMeta().throwExWithMessage(ClassFormatError.class, e.getMessage());
        }

        this.codeAttribute = method.codeAttribute;
        this.callTarget = method.callTarget;

        this.exceptionsAttribute = (ExceptionsAttribute) getAttribute(ExceptionsAttribute.NAME);

        initRefKind();
        // Proxy the method, so that we have the same callTarget if it is not yet initialized.
        // Allows for not duplicating the codeAttribute
        this.proxy = method.proxy == null ? method : method.proxy;
        this.poisonPill = method.poisonPill;
        this.isLeaf = method.isLeaf;
    }

    Method(ObjectKlass declaringKlass, LinkedMethod linkedMethod) {
        this(declaringKlass, linkedMethod, linkedMethod.getRawSignature());
    }

    Method(ObjectKlass declaringKlass, LinkedMethod linkedMethod, Symbol<Signature> rawSignature) {
        super(rawSignature, linkedMethod.getName());
        this.declaringKlass = declaringKlass;
        // TODO(peterssen): Custom constant pool for methods is not supported.
        this.pool = declaringKlass.getConstantPool();

        this.linkedMethod = linkedMethod;

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw getMeta().throwExWithMessage(ClassFormatError.class, e.getMessage());
        }

        this.codeAttribute = (CodeAttribute) getAttribute(CodeAttribute.NAME);
        this.exceptionsAttribute = (ExceptionsAttribute) getAttribute(ExceptionsAttribute.NAME);

        initRefKind();
        this.proxy = null;
        this.isLeaf = Truffle.getRuntime().createAssumption();
    }

    public final int getRefKind() {
        return refKind;
    }

    public final void initRefKind() {
        if (isStatic()) {
            this.refKind = REF_invokeStatic;
        } else if (isPrivate() || isConstructor()) {
            this.refKind = REF_invokeSpecial;
        } else if (declaringKlass.isInterface()) {
            this.refKind = REF_invokeInterface;
        } else {
            assert !declaringKlass.isPrimitive();
            this.refKind = REF_invokeVirtual;
        }
    }

    public final Attribute getAttribute(Symbol<Name> attrName) {
        return linkedMethod.getAttribute(attrName);
    }

    @TruffleBoundary
    public final int BCItoLineNumber(int atBCI) {
        if (atBCI < 0) {
            return atBCI;
        }
        return codeAttribute.BCItoLineNumber(atBCI);
    }

    @Override
    public EspressoContext getContext() {
        return declaringKlass.getContext();
    }

    public final BootstrapMethodsAttribute getBootstrapMethods() {
        return (BootstrapMethodsAttribute) getAttribute(BootstrapMethodsAttribute.NAME);
    }

    public byte[] getCode() {
        return codeAttribute.getCode();
    }

    public CodeAttribute getCodeAttribute() {
        return codeAttribute;
    }

    public int getCodeSize() {
        return getCode() != null ? getCode().length : 0;
    }

    public int getMaxLocals() {
        return codeAttribute.getMaxLocals();
    }

    public int getMaxStackSize() {
        return codeAttribute.getMaxStack();
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return codeAttribute.getExceptionHandlers();
    }

    public int[] getSOEHandlerInfo() {
        ArrayList<Integer> toArray = new ArrayList<>();
        for (ExceptionHandler handler : getExceptionHandlers()) {
            if (handler.getCatchType() == Type.StackOverflowError) {
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

    private static String buildJniNativeSignature(Method method) {
        // Prepend JNIEnv*.
        StringBuilder sb = new StringBuilder("(").append(NativeSimpleType.SINT64);
        final Symbol<Type>[] signature = method.getParsedSignature();

        // Receiver for instance methods, class for static methods.
        sb.append(", ").append(NativeSimpleType.NULLABLE);

        int argCount = Signatures.parameterCount(signature, false);
        for (int i = 0; i < argCount; ++i) {
            sb.append(", ").append(Utils.kindToType(Signatures.parameterKind(signature, i), true));
        }

        sb.append("): ").append(Utils.kindToType(Signatures.returnKind(signature), false));

        return sb.toString();
    }

    private static TruffleObject bind(TruffleObject library, Method m, String mangledName) throws UnknownIdentifierException {
        String signature = buildJniNativeSignature(m);
        return NativeLibrary.lookupAndBind(library, mangledName, signature);
    }

    private static TruffleObject bind(TruffleObject symbol, Method m) {
        String signature = buildJniNativeSignature(m);
        return NativeLibrary.bind(symbol, signature);
    }

    /**
     * Ensure any callTarget is called immediately before a BCI is advanced, or it could violate the
     * specs on class init.
     */
    @TruffleBoundary
    public CallTarget getCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (poisonPill) {
                getMeta().throwExWithMessage(IncompatibleClassChangeError.class, "Conflicting default methods: " + this.getName());
            }
            // Initializing a class costs a lock, do it outside of this method's lock to avoid
            // congestion.
            // Note that requesting a call target is immediately followed by a call to the method,
            // before advancing BCI.
            // This ensures that we are respecting the specs, saying that a class must be
            // initialized before a method is called, while saving a call to safeInitialize after a
            // method lookup.
            declaringKlass.safeInitialize();

            synchronized (this) {
                if (callTarget != null) {
                    return callTarget;
                }
                if (proxy != null) {
                    this.callTarget = proxy.getCallTarget();
                    return callTarget;
                }
                EspressoRootNode redirectedMethod = getSubstitutions().get(this);
                if (redirectedMethod != null) {
                    callTarget = Truffle.getRuntime().createCallTarget(redirectedMethod);
                } else {
                    if (this.isNative()) {
                        // Bind native method.
                        // System.err.println("Linking native method: " +
                        // meta(this).getDeclaringClass().getName() + "#" + getName() + " " +
                        // getSignature());

                        // If the loader is null we have a system class, so we attempt a lookup in
                        // the native Java library.
                        if (StaticObject.isNull(getDeclaringKlass().getDefiningClassLoader())) {
                            // Look in libjava
                            for (boolean withSignature : new boolean[]{false, true}) {
                                String mangledName = Mangle.mangleMethod(this, withSignature);

                                try {
                                    TruffleObject nativeMethod = bind(getVM().getJavaLibrary(), this, mangledName);
                                    callTarget = Truffle.getRuntime().createCallTarget(EspressoRootNode.create(null, new NativeRootNode(nativeMethod, this, true)));
                                    return callTarget;
                                } catch (UnknownIdentifierException e) {
                                    // native method not found in libjava, safe to ignore
                                }
                            }
                        }

                        Method findNative = getMeta().ClassLoader_findNative;

                        // Lookup the short name first, otherwise lookup the long name (with
                        // signature).
                        callTarget = lookupJniCallTarget(findNative, false);
                        if (callTarget == null) {
                            callTarget = lookupJniCallTarget(findNative, true);
                        }

                        // TODO(peterssen): Search JNI methods with OS prefix/suffix
                        // (print_jni_name_suffix_on ...)

                        if (callTarget == null) {
                            if (getDeclaringKlass() == getMeta().MethodHandle && (getName() == Name.invokeExact || getName() == Name.invoke)) {
                                /*
                                 * Happens only when trying to obtain call target of
                                 * MethodHandle.invoke(Object... args), or
                                 * MethodHandle.invokeExact(Object... args).
                                 *
                                 * The method was obtained through a regular lookup (since it is in
                                 * the declared method). Delegate it to a polysignature method
                                 * lookup.
                                 *
                                 * Redundant callTarget assignment. Better sure than sorry.
                                 */
                                this.callTarget = declaringKlass.lookupPolysigMethod(getName(), getRawSignature(), declaringKlass).getCallTarget();
                            } else {
                                System.err.println("Failed to link native method: " + getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature());
                                throw getMeta().throwEx(UnsatisfiedLinkError.class);
                            }
                        }
                    } else {
                        if (codeAttribute == null) {
                            throw getMeta().throwExWithMessage(AbstractMethodError.class, "Calling abstract method: " + getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature());
                        }
                        FrameDescriptor frameDescriptor = initFrameDescriptor(getMaxLocals() + getMaxStackSize());
                        EspressoRootNode rootNode = EspressoRootNode.create(frameDescriptor, new BytecodeNode(this, frameDescriptor));
                        callTarget = Truffle.getRuntime().createCallTarget(rootNode);
                    }
                }
            }
        }

        return callTarget;
    }

    public static FrameDescriptor initFrameDescriptor(int slotCount) {
        FrameDescriptor descriptor = new FrameDescriptor();
        for (int i = 0; i < slotCount; ++i) {
            descriptor.addFrameSlot(i);
        }
        return descriptor;
    }

    private CallTarget lookupJniCallTarget(Method findNative, boolean fullSignature) {
        String mangledName = Mangle.mangleMethod(this, fullSignature);
        long handle = (long) findNative.invokeWithConversions(null, getDeclaringKlass().getDefiningClassLoader(), mangledName);
        if (handle == 0) { // not found
            return null;
        }
        TruffleObject symbol = getVM().getFunction(handle);
        TruffleObject nativeMethod = bind(symbol, this);
        return Truffle.getRuntime().createCallTarget(EspressoRootNode.create(null, new NativeRootNode(nativeMethod, this, true)));
    }

    public boolean isConstructor() {
        return Name.INIT.equals(getName());
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringKlass().isInterface();
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
            final int[] entries = exceptionsAttribute.getCheckedExceptionsCPI();
            ObjectKlass[] tmpchecked = new ObjectKlass[entries.length];
            for (int i = 0; i < entries.length; ++i) {
                // TODO(peterssen): Resolve and cache CP entries.
                tmpchecked[i] = (ObjectKlass) (getDeclaringKlass().getConstantPool()).resolvedKlassAt(getDeclaringKlass(), entries[i]);
            }
            checkedExceptions = tmpchecked;
        }
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
        return getDeclaringKlass().isJavaLangObject() && Name.INIT.equals(getName());
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
        getContext().getJNI().clearPendingException();
        assert args.length == Signatures.parameterCount(getParsedSignature(), false);
        // assert !isStatic() || ((StaticObject) self).isStatic();

        final Object[] filteredArgs;
        if (isStatic()) {
            // clinit done when obtaining call target
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
    }

    /**
     * Invokes a guest method without parameter/return type conversions. There's no parameter
     * casting, widening nor narrowing based on the method signature.
     * <p>
     * e.g. Host (boxed) Integer represents int, guest Integer doesn't.
     */
    @TruffleBoundary
    public Object invokeDirect(Object self, Object... args) {
        getContext().getJNI().clearPendingException();
        if (isStatic()) {
            assert args.length == Signatures.parameterCount(getParsedSignature(), false);
            // clinit performed on obtaining call target
            return getCallTarget().call(args);
        } else {
            assert args.length + 1 /* self */ == Signatures.parameterCount(getParsedSignature(), !isStatic());
            Object[] fullArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, fullArgs, 1, args.length);
            fullArgs[0] = self;
            return getCallTarget().call(fullArgs);
        }
    }

    public final boolean isClassInitializer() {
        return Name.CLINIT.equals(getName()) && isStatic();
    }

    @Override
    public int getModifiers() {
        return linkedMethod.getFlags() & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
    }

    @Override
    public String toString() {
        return "EspressoMethod<" + getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature() + ">";
    }

    public final JavaKind getReturnKind() {
        return Signatures.returnKind(getParsedSignature());
    }

    public Klass[] resolveParameterKlasses() {
        // TODO(peterssen): Use resolved signature.
        final Symbol<Type>[] signature = getParsedSignature();
        int paramCount = Signatures.parameterCount(signature, false);
        Klass[] paramsKlasses = paramCount > 0 ? new Klass[paramCount] : Klass.EMPTY_ARRAY;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = Signatures.parameterType(signature, i);
            paramsKlasses[i] = getMeta().resolveSymbol(paramType, getDeclaringKlass().getDefiningClassLoader());
        }
        return paramsKlasses;
    }

    public Klass resolveReturnKlass() {
        // TODO(peterssen): Use resolved signature.
        Symbol<Type> returnType = Signatures.returnType(getParsedSignature());
        return getMeta().resolveSymbol(returnType, getDeclaringKlass().getDefiningClassLoader());
    }

    public int getParameterCount() {
        return Signatures.parameterCount(getParsedSignature(), false);
    }

    public static Method getHostReflectiveMethodRoot(StaticObject seed) {
        assert seed.getKlass().getMeta().Method.isAssignableFrom(seed.getKlass());
        Meta meta = seed.getKlass().getMeta();
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_METHOD_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.Method_root.get(curMethod);
            }
        }
        return target;
    }

    public static Method getHostReflectiveConstructorRoot(StaticObject seed) {
        assert seed.getKlass().getMeta().Constructor.isAssignableFrom(seed.getKlass());
        Meta meta = seed.getKlass().getMeta();
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_CONSTRUCTOR_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.Constructor_root.get(curMethod);
            }
        }
        return target;
    }

    // Polymorphic signature method 'creation'

    final Method findIntrinsic(Symbol<Signature> signature, Function<Method, EspressoMethodNode> baseNodeFactory, MethodHandleIntrinsics.PolySigIntrinsics id) {
        return getContext().getMethodHandleIntrinsics().findIntrinsic(this, signature, baseNodeFactory, id);
    }

    final void setVTableIndex(int i) {
        assert (vtableIndex == -1 || vtableIndex == i);
        CompilerAsserts.neverPartOfCompilation();
        this.vtableIndex = i;
    }

    final public int getVTableIndex() {
        return vtableIndex;
    }

    final void setITableIndex(int i) {
        assert (itableIndex == -1 || itableIndex == i);
        CompilerAsserts.neverPartOfCompilation();
        this.itableIndex = i;
    }

    final public int getITableIndex() {
        return itableIndex;
    }

    public final boolean hasCode() {
        return codeAttribute != null || isNative();
    }

    public final boolean isVirtualCall() {
        return !isStatic() && !isConstructor() && !isPrivate() && !getDeclaringKlass().isInterface();
    }

    public Method createIntrinsic(Symbol<Signature> polymorphicRawSignature, Function<Method, EspressoMethodNode> baseNodeFactory) {
        assert (declaringKlass == getMeta().MethodHandle);
        Method method = new Method(declaringKlass, linkedMethod, polymorphicRawSignature);
        EspressoRootNode rootNode = EspressoRootNode.create(null, baseNodeFactory.apply(method));
        method.callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        return method;
    }

    public void setPoisonPill() {
        this.poisonPill = true;
    }

    public String getSourceFile() {
        SourceFileAttribute sfa = (SourceFileAttribute) declaringKlass.getAttribute(Name.SourceFile);
        if (sfa == null) {
            return "unknown source";
        }
        return declaringKlass.getConstantPool().utf8At(sfa.getSourceFileIndex()).toString();
    }

    public final String report(int curBCI) {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(" + getSourceFile() + ":" + BCItoLineNumber(curBCI) + ")";
    }

    public final String report() {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(unknown source)";
    }

    public boolean isMethodHandleInvokeIntrinsic() {
        return isNative() && declaringKlass == getMeta().MethodHandle && MethodHandleIntrinsics.getId(this) == MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
    }

    public boolean isMethodHandleIntrinsic() {
        return isNative() && declaringKlass == getMeta().MethodHandle && MethodHandleIntrinsics.getId(this) != MethodHandleIntrinsics.PolySigIntrinsics.None;
    }

    public boolean isInlinableGetter() {
        if (getSubstitutions().get(this) == null) {
            if (getParameterCount() == 0 && !isAbstract() && !isNative() && !isSynchronized()) {
                if (isFinalFlagSet() || declaringKlass.isFinalFlagSet() || leafAssumption() || isStatic()) {
                    return hasGetterBytecodes();
                }
            }
        }
        return false;
    }

    private boolean hasGetterBytecodes() {
        byte[] code = codeAttribute.getCode();
        if (isStatic()) {
            if (code.length == STATIC_GETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) GETSTATIC) && (Bytecodes.isReturn(code[3])) && code[3] != (byte) RETURN;
            }
        } else {
            if (code.length == GETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) GETFIELD) && (Bytecodes.isReturn(code[4])) && code[4] != (byte) RETURN;
            }
        }
        return false;
    }

    public boolean isInlinableSetter() {
        if (getSubstitutions().get(this) == null) {
            if (getParameterCount() == 1 && !isAbstract() && !isNative() && !isSynchronized()) {
                if (isFinalFlagSet() || declaringKlass.isFinalFlagSet() || leafAssumption() || isStatic()) {
                    return hasSetterBytecodes();
                }
            }
        }
        return false;
    }

    private boolean hasSetterBytecodes() {
        byte[] code = codeAttribute.getCode();
        if (isStatic()) {
            if (code.length == STATIC_SETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) PUTSTATIC) && (code[4] == (byte) RETURN);
            }
        } else {
            if (code.length == SETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (Bytecodes.isLoad1(code[1])) && (code[2] == (byte) PUTFIELD) && (code[5] == (byte) RETURN);
            }
        }
        return false;
    }

    public boolean leafAssumption() {
        return isLeaf.isValid();
    }

    public void invalidateLeaf() {
        isLeaf.invalidate();
    }

    public void unregisterNative() {
        assert isNative();
        if (callTarget != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTarget = null;
        }
    }

    @SuppressWarnings("unused")
    void printBytecodes() {
        new BytecodeStream(getCode()).printBytecode(declaringKlass);
    }

    public LineNumberTable getLineNumberTable() {
        CodeAttribute codeAttribute = getCodeAttribute();
        if (codeAttribute != null) {
            return codeAttribute.getLineNumberTableAttribute();
        }
        return LineNumberTable.EMPTY;
    }

    public LocalVariableTable getLocalVariableTable() {
        CodeAttribute codeAttribute = getCodeAttribute();
        if (codeAttribute != null) {
            return codeAttribute.getLocalvariableTable();
        }
        return LocalVariableTable.EMPTY;
    }

    /**
     * @return the source object associated with this method
     */

    public final Source getSource() {
        Source localSource = this.source;
        if (localSource == null) {
            this.source = localSource = getContext().findOrCreateSource(this);
        }
        return localSource;
    }

    public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
        for (Symbol<Type> type : getParsedSignature()) {
            getContext().getRegistries().checkLoadingConstraint(type, loader1, loader2);
        }
    }

    // region jdwp-specific

    @Override
    public long getBCIFromLine(int line) {
        return getLineNumberTable().getBCI(line);
    }

    @Override
    public boolean hasLine(int lineNumber) {
        return getLineNumberTable().getBCI(lineNumber) != -1;
    }

    @Override
    public String getNameAsString() {
        return getName().toString();
    }

    @Override
    public String getSignatureAsString() {
        return getRawSignature().toString();
    }

    @Override
    public boolean isMethodNative() {
        return isNative();
    }

    @Override
    public klassRef[] getParameters() {
        return resolveParameterKlasses();
    }

    @Override
    public Object invokeMethod(Object callee, Object[] args) {
        return invokeWithConversions(callee, args);
    }

    //endregion jdwp-specific
}
