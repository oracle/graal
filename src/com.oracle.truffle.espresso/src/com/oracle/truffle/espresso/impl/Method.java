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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.jni.NativeEnv.word;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ExceptionsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.LineNumberTableRef;
import com.oracle.truffle.espresso.jdwp.api.LocalVariableTableRef;
import com.oracle.truffle.espresso.jdwp.api.MethodBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.NativeMethodNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class Method extends Member<Signature> implements TruffleObject, ContextAccess {

    public static final Method[] EMPTY_ARRAY = new Method[0];
    public static final MethodVersion[] EMPTY_VERSION_ARRAY = new MethodVersion[0];

    private static final byte GETTER_LENGTH = 5;
    private static final byte STATIC_GETTER_LENGTH = 4;

    private static final byte SETTER_LENGTH = 6;
    private static final byte STATIC_SETTER_LENGTH = 5;

    private final Assumption isLeaf;

    private final ObjectKlass declaringKlass;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    @CompilationFinal private int vtableIndex = -1;
    @CompilationFinal private int itableIndex = -1;

    private final ExceptionsAttribute exceptionsAttribute;

    @CompilationFinal private int refKind;

    @CompilationFinal(dimensions = 1) //
    private ObjectKlass[] checkedExceptions;

    private final Method proxy;
    private String genericSignature;

    // the parts of the method that can change when it's redefined
    // are encapsulated within the methodVersion
    @CompilationFinal volatile MethodVersion methodVersion;

    public Method identity() {
        return proxy == null ? this : proxy;
    }

    // Multiple maximally-specific interface methods. Fail on call.
    @CompilationFinal private boolean poisonPill = false;

    // Whether we need to use an additional frame slot for monitor unlock on kill.
    @CompilationFinal private byte usesMonitors = -1;

    // can have a different constant pool than it's declaring class

    public ConstantPool getConstantPool() {
        return getRuntimeConstantPool();
    }

    public RuntimeConstantPool getRuntimeConstantPool() {
        return getMethodVersion().pool;
    }

    private LinkedMethod getLinkedMethod() {
        return getMethodVersion().linkedMethod;
    }

    public CodeAttribute getCodeAttribute() {
        return getMethodVersion().codeAttribute;
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

        initMethodVersion(method.getRuntimeConstantPool(), method.getLinkedMethod(), method.getCodeAttribute());

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_ClassFormatError, e.getMessage());
        }

        this.exceptionsAttribute = (ExceptionsAttribute) getAttribute(ExceptionsAttribute.NAME);

        initRefKind();
        // Proxy the method, so that we have the same callTarget if it is not yet initialized.
        // Allows for not duplicating the codeAttribute
        this.proxy = method.proxy == null ? method : method.proxy;
        this.poisonPill = method.poisonPill;
        this.isLeaf = method.isLeaf;
    }

    private Method(Method method, CodeAttribute split) {
        super(method.getRawSignature(), method.getName());
        this.declaringKlass = method.declaringKlass;
        initMethodVersion(method.getRuntimeConstantPool(), method.getLinkedMethod(), split);

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_ClassFormatError, e.getMessage());
        }

        this.exceptionsAttribute = (ExceptionsAttribute) getAttribute(ExceptionsAttribute.NAME);

        initRefKind();
        // Proxy the method, so that we have the same callTarget if it is not yet initialized.
        // Allows for not duplicating the codeAttribute
        this.proxy = method.proxy == null ? method : method.proxy;
        this.poisonPill = method.poisonPill;
        this.isLeaf = method.isLeaf;
    }

    Method(ObjectKlass declaringKlass, LinkedMethod linkedMethod, RuntimeConstantPool pool) {
        this(declaringKlass, linkedMethod, linkedMethod.getRawSignature(), pool);
    }

    Method(ObjectKlass declaringKlass, LinkedMethod linkedMethod, Symbol<Signature> rawSignature, RuntimeConstantPool pool) {
        super(rawSignature, linkedMethod.getName());
        initMethodVersion(pool, linkedMethod, (CodeAttribute) linkedMethod.getAttribute(CodeAttribute.NAME));
        this.declaringKlass = declaringKlass;

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_ClassFormatError, e.getMessage());
        }

        this.exceptionsAttribute = (ExceptionsAttribute) linkedMethod.getAttribute(ExceptionsAttribute.NAME);

        initRefKind();
        this.proxy = null;
        this.isLeaf = Truffle.getRuntime().createAssumption();
    }

    private void initMethodVersion(RuntimeConstantPool runtimeConstantPool, LinkedMethod linkedMethod, CodeAttribute codeAttribute) {
        this.methodVersion = new MethodVersion(runtimeConstantPool, linkedMethod, codeAttribute);
    }

    public int getRefKind() {
        return refKind;
    }

    public void initRefKind() {
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

    public Attribute getAttribute(Symbol<Name> attrName) {
        return getLinkedMethod().getAttribute(attrName);
    }

    @TruffleBoundary
    public int bciToLineNumber(int atBCI) {
        if (atBCI < 0) {
            return atBCI;
        }
        return getCodeAttribute().bciToLineNumber(atBCI);
    }

    @Override
    public EspressoContext getContext() {
        return declaringKlass.getContext();
    }

    public BootstrapMethodsAttribute getBootstrapMethods() {
        return (BootstrapMethodsAttribute) getAttribute(BootstrapMethodsAttribute.NAME);
    }

    public byte[] getCode() {
        return getCodeAttribute().getCode();
    }

    public byte[] getOriginalCode() {
        return getCodeAttribute().getOriginalCode();
    }

    public int getMaxLocals() {
        return getCodeAttribute().getMaxLocals();
    }

    public int getMaxStackSize() {
        return getCodeAttribute().getMaxStack();
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return getCodeAttribute().getExceptionHandlers();
    }

    public int[] getSOEHandlerInfo() {
        ArrayList<Integer> toArray = new ArrayList<>();
        for (ExceptionHandler handler : getExceptionHandlers()) {
            if (handler.getCatchType() == Type.java_lang_StackOverflowError) {
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
        StringBuilder sb = new StringBuilder("(").append(word());
        final Symbol<Type>[] signature = method.getParsedSignature();

        // Receiver for instance methods, class for static methods.
        sb.append(", ").append(word());

        int argCount = Signatures.parameterCount(signature, false);
        for (int i = 0; i < argCount; ++i) {
            sb.append(", ").append(Utils.kindToType(Signatures.parameterKind(signature, i)));
        }

        sb.append("): ").append(Utils.kindToType(Signatures.returnKind(signature)));

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
        return getMethodVersion().getCallTarget();
    }

    public boolean usesMonitors() {
        if (usesMonitors != -1) {
            return usesMonitors != 0;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isSynchronized()) {
                return (usesMonitors = 1) != 0;
            }
            if (getCodeAttribute() != null) {
                BytecodeStream bs = new BytecodeStream(getCodeAttribute().getCode());
                int bci = 0;
                while (bci < bs.endBCI()) {
                    int opcode = bs.currentBC(bci);
                    if (opcode == MONITORENTER || opcode == MONITOREXIT) {
                        return (usesMonitors = 1) != 0;
                    }
                    bci = bs.nextBCI(bci);
                }
                return (usesMonitors = 0) != 0;
            }
            return false;
        }
    }

    public static FrameDescriptor initFrameDescriptor(int slotCount) {
        FrameDescriptor descriptor = new FrameDescriptor();
        for (int i = 0; i < slotCount; ++i) {
            descriptor.addFrameSlot(i, FrameSlotKind.Long);
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
        return Truffle.getRuntime().createCallTarget(EspressoRootNode.create(null, new NativeMethodNode(nativeMethod, this.getMethodVersion(), true)));
    }

    public boolean isConstructor() {
        return Name._init_.equals(getName());
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
        return getDeclaringKlass().isJavaLangObject() && Name._init_.equals(getName());
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

    public boolean isClassInitializer() {
        return Name._clinit_.equals(getName()) && isStatic();
    }

    @Override
    public int getModifiers() {
        return getLinkedMethod().getFlags();
    }

    public int getMethodModifiers() {
        return getLinkedMethod().getFlags() & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
    }

    @Override
    public String toString() {
        return "EspressoMethod<" + getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature() + ">";
    }

    public JavaKind getReturnKind() {
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
        assert seed.getKlass().getMeta().java_lang_reflect_Method.isAssignableFrom(seed.getKlass());
        Meta meta = seed.getKlass().getMeta();
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_METHOD_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.java_lang_reflect_Method_root.get(curMethod);
            }
        }
        return target;
    }

    public static Method getHostReflectiveConstructorRoot(StaticObject seed) {
        assert seed.getKlass().getMeta().java_lang_reflect_Constructor.isAssignableFrom(seed.getKlass());
        Meta meta = seed.getKlass().getMeta();
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_CONSTRUCTOR_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.java_lang_reflect_Constructor_root.get(curMethod);
            }
        }
        return target;
    }

    // Polymorphic signature method 'creation'

    Method findIntrinsic(Symbol<Signature> signature, MethodHandleIntrinsics.PolySigIntrinsics id) {
        return getContext().getMethodHandleIntrinsics().findIntrinsic(this, signature, id);
    }

    void setVTableIndex(int i) {
        assert (vtableIndex == -1 || vtableIndex == i);
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

    public boolean hasCode() {
        return getCodeAttribute() != null || isNative();
    }

    public boolean isVirtualCall() {
        return !isStatic() && !isConstructor() && !isPrivate() && !getDeclaringKlass().isInterface();
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

    public boolean hasSourceFileAttribute() {
        return declaringKlass.getAttribute(Name.SourceFile) != null;
    }

    public String report(int curBCI) {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(" + getSourceFile() + ":" + bciToLineNumber(curBCI) + ")";
    }

    public String report() {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(unknown source)";
    }

    public boolean isMethodHandleInvokeIntrinsic() {
        return isNative() && declaringKlass == getMeta().java_lang_invoke_MethodHandle && MethodHandleIntrinsics.getId(this) == MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
    }

    public boolean isMethodHandleIntrinsic() {
        return isNative() && declaringKlass == getMeta().java_lang_invoke_MethodHandle && MethodHandleIntrinsics.getId(this) != MethodHandleIntrinsics.PolySigIntrinsics.None;
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
        byte[] code = getCodeAttribute().getCode();
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
        byte[] code = getCodeAttribute().getCode();
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
        if (getMethodVersion().callTarget != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethodVersion().callTarget = null;
        }
    }

    @SuppressWarnings("unused")
    void printBytecodes() {
        new BytecodeStream(getCode()).printBytecode(declaringKlass);
    }

    public LineNumberTableAttribute getLineNumberTable() {
        CodeAttribute attribute = getCodeAttribute();
        if (attribute != null) {
            return attribute.getLineNumberTableAttribute();
        }
        return LineNumberTableAttribute.EMPTY;
    }

    public LocalVariableTable getLocalVariableTable() {
        CodeAttribute attribute = getCodeAttribute();
        if (attribute != null) {
            return attribute.getLocalvariableTable();
        }
        return LocalVariableTable.EMPTY;
    }

    public LocalVariableTable getLocalVariableTypeTable() {
        CodeAttribute attribute = getCodeAttribute();
        if (attribute != null) {
            return attribute.getLocalvariableTypeTable();
        }
        return LocalVariableTable.EMPTY;
    }

    /**
     * @return the source object associated with this method
     */

    public Source getSource() {
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

    public int getCatchLocation(int bci, StaticObject ex) {
        ExceptionHandler[] handlers = getExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (bci >= toCheck.getStartBCI() && bci < toCheck.getEndBCI()) {
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
        assert isMethodHandleIntrinsic();
        return new Method(declaringKlass, getLinkedMethod(), polymorphicRawSignature, getRuntimeConstantPool());
    }

    public MethodHandleIntrinsicNode spawnIntrinsicNode(Klass accessingKlass, Symbol<Name> mname, Symbol<Signature> signature) {
        assert isMethodHandleIntrinsic();
        return getContext().getMethodHandleIntrinsics().createIntrinsicNode(this, accessingKlass, mname, signature);
    }

    public Method forceSplit() {
        assert isMethodHandleIntrinsic();
        Method result = new Method(this, getCodeAttribute().forceSplit());
        FrameDescriptor frameDescriptor = initFrameDescriptor(result.getMaxLocals() + result.getMaxStackSize());

        // BCI slot is always the latest.
        FrameSlot bciSlot = frameDescriptor.addFrameSlot("bci", FrameSlotKind.Int);
        EspressoRootNode rootNode = EspressoRootNode.create(frameDescriptor, new BytecodeNode(result.getMethodVersion(), frameDescriptor, bciSlot));
        result.getMethodVersion().callTarget = Truffle.getRuntime().createCallTarget(rootNode);

        return result;
    }

    // region jdwp-specific
    public long getBCIFromLine(int line) {
        return getLineNumberTable().getBCI(line);
    }

    public boolean hasLine(int lineNumber) {
        return getLineNumberTable().getBCI(lineNumber) != -1;
    }

    public String getNameAsString() {
        return getName().toString();
    }

    public String getSignatureAsString() {
        return getRawSignature().toString();
    }

    public boolean isMethodNative() {
        return isNative();
    }

    public KlassRef[] getParameters() {
        return resolveParameterKlasses();
    }

    public Object invokeMethod(Object callee, Object[] args) {
        if (isConstructor()) {
            Object theCallee = InterpreterToVM.newObject(getDeclaringKlass(), false);
            invokeWithConversions(theCallee, args);
            return theCallee;
        }
        return invokeWithConversions(callee, args);
    }

    public boolean isLastLine(long codeIndex) {
        LineNumberTableAttribute table = getLineNumberTable();
        int lastLine = table.getLastLine();
        int lineAt = table.getLineNumber((int) codeIndex);
        return lastLine == lineAt;
    }

    public int getFirstLine() {
        return getLineNumberTable().getFirstLine();
    }

    public int getLastLine() {
        return getLineNumberTable().getLastLine();
    }

    public String getGenericSignatureAsString() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) getLinkedMethod().getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ""; // if no generics, the generic signature is empty
            } else {
                genericSignature = getRuntimeConstantPool().symbolAt(attr.getSignatureIndex()).toString();
            }
        }
        return genericSignature;
    }

    private final Field.StableBoolean hasActiveBreakpoints = new Field.StableBoolean(false);

    private MethodBreakpoint[] infos = new MethodBreakpoint[0];

    public boolean hasActiveBreakpoint() {
        return hasActiveBreakpoints.get();
    }

    public MethodBreakpoint[] getMethodBreakpointInfos() {
        return infos;
    }

    public void addMethodBreakpointInfo(MethodBreakpoint info) {
        hasActiveBreakpoints.set(true);
        if (infos.length == 0) {
            infos = new MethodBreakpoint[]{info};
            return;
        }

        infos = Arrays.copyOf(infos, infos.length + 1);
        infos[infos.length - 1] = info;
    }

    public void removeMethodBreakpointInfo(int requestId) {
        // shrink the array to avoid null values
        if (infos.length == 0) {
            throw new RuntimeException("Method: " + getNameAsString() + " should contain method breakpoint info");
        } else if (infos.length == 1) {
            infos = new MethodBreakpoint[0];
            hasActiveBreakpoints.set(false);
        } else {
            int removeIndex = -1;
            for (int i = 0; i < infos.length; i++) {
                if (infos[i].getRequestId() == requestId) {
                    removeIndex = i;
                    break;
                }
            }
            MethodBreakpoint[] temp = new MethodBreakpoint[infos.length - 1];
            for (int i = 0; i < temp.length; i++) {
                temp[i] = i < removeIndex ? infos[i] : infos[i + 1];
            }
            infos = temp;
        }
    }

    public void redefine(ParserMethod newMethod, ParserKlass newKlass, Ids<Object> ids) {
        // invalidate old version
        // install the new method version immediately
        LinkedMethod newLinkedMethod = new LinkedMethod(newMethod);
        RuntimeConstantPool runtimePool = new RuntimeConstantPool(getContext(), newKlass.getConstantPool(), getDeclaringKlass().getDefiningClassLoader());
        MethodVersion oldVersion = methodVersion;
        methodVersion = new MethodVersion(runtimePool, newLinkedMethod, (CodeAttribute) newMethod.getAttribute(Name.Code));
        oldVersion.getAssumption().invalidate();
        ids.replaceObject(oldVersion, methodVersion);
    }

    public MethodVersion getMethodVersion() {
        MethodVersion version = methodVersion;
        while (!version.getAssumption().isValid()) {
            version = methodVersion;
        }
        return version;
    }

    public final class MethodVersion implements MethodRef {
        private final Assumption assumption;
        private final RuntimeConstantPool pool;
        private final LinkedMethod linkedMethod;
        private final CodeAttribute codeAttribute;
        @CompilationFinal private CallTarget callTarget;

        MethodVersion(RuntimeConstantPool pool, LinkedMethod linkedMethod, CodeAttribute codeAttribute) {
            this.assumption = Truffle.getRuntime().createAssumption();
            this.pool = pool;
            this.linkedMethod = linkedMethod;
            this.codeAttribute = codeAttribute;
        }

        public Method getMethod() {
            return Method.this;
        }

        public Assumption getAssumption() {
            return assumption;
        }

        public CodeAttribute getCodeAttribute() {
            return codeAttribute;
        }

        public RuntimeConstantPool getPool() {
            return pool;
        }

        public CallTarget getCallTarget() {
            if (callTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Meta meta = getMeta();
                if (poisonPill) {
                    throw Meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Conflicting default methods: " + getMethod().getName());
                }
                // Initializing a class costs a lock, do it outside of this method's lock to avoid
                // congestion.
                // Note that requesting a call target is immediately followed by a call to the
                // method,
                // before advancing BCI.
                // This ensures that we are respecting the specs, saying that a class must be
                // initialized before a method is called, while saving a call to safeInitialize
                // after a
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
                    EspressoRootNode redirectedMethod = getSubstitutions().get(getMethod());
                    if (redirectedMethod != null) {
                        callTarget = Truffle.getRuntime().createCallTarget(redirectedMethod);
                    } else {
                        if (getMethod().isNative()) {
                            // Bind native method.
                            // If the loader is null we have a system class, so we attempt a lookup
                            // in
                            // the native Java library.
                            if (StaticObject.isNull(getMethod().getDeclaringKlass().getDefiningClassLoader())) {
                                // Look in libjava
                                for (boolean withSignature : new boolean[]{false, true}) {
                                    String mangledName = Mangle.mangleMethod(getMethod(), withSignature);

                                    try {
                                        TruffleObject nativeMethod = bind(getVM().getJavaLibrary(), getMethod(), mangledName);
                                        callTarget = Truffle.getRuntime().createCallTarget(EspressoRootNode.create(null, new NativeMethodNode(nativeMethod, this, true)));
                                        return callTarget;
                                    } catch (UnknownIdentifierException e) {
                                        // native method not found in libjava, safe to ignore
                                    }
                                }
                            }

                            Method findNative = meta.java_lang_ClassLoader_findNative;

                            // Lookup the short name first, otherwise lookup the long name (with
                            // signature).
                            callTarget = lookupJniCallTarget(findNative, false);
                            if (callTarget == null) {
                                callTarget = lookupJniCallTarget(findNative, true);
                            }

                            // TODO(peterssen): Search JNI methods with OS prefix/suffix
                            // (print_jni_name_suffix_on ...)

                            if (callTarget == null) {
                                if (getDeclaringKlass() == meta.java_lang_invoke_MethodHandle && (Name.invokeExact.equals(getName()) || Name.invoke.equals(getName()))) {
                                    /*
                                     * Happens only when trying to obtain call target of
                                     * MethodHandle.invoke(Object... args), or
                                     * MethodHandle.invokeExact(Object... args).
                                     *
                                     * The method was obtained through a regular lookup (since it is
                                     * in the declared method). Delegate it to a polysignature
                                     * method lookup.
                                     *
                                     * Redundant callTarget assignment. Better sure than sorry.
                                     */
                                    this.callTarget = declaringKlass.lookupPolysigMethod(getName(), getRawSignature()).getCallTarget();
                                } else {
                                    EspressoLanguage.EspressoLogger.warning(String.format("Failed to link native method: %s", this.toString()));
                                    throw Meta.throwException(meta.java_lang_UnsatisfiedLinkError);
                                }
                            }
                        } else {
                            if (getCodeAttribute() == null) {
                                throw Meta.throwExceptionWithMessage(meta.java_lang_AbstractMethodError,
                                                "Calling abstract method: " + getMethod().getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature());
                            }

                            FrameDescriptor frameDescriptor = initFrameDescriptor(getMaxLocals() + getMaxStackSize());

                            // BCI slot is always the latest.
                            FrameSlot bciSlot = frameDescriptor.addFrameSlot("bci", FrameSlotKind.Int);
                            EspressoRootNode rootNode = EspressoRootNode.create(frameDescriptor, new BytecodeNode(methodVersion, frameDescriptor, bciSlot));

                            callTarget = Truffle.getRuntime().createCallTarget(rootNode);
                        }
                    }
                }
            }
            return callTarget;
        }

        public int getCodeSize() {
            return codeAttribute.getCode() != null ? codeAttribute.getCode().length : 0;
        }

        public LineNumberTableAttribute getLineNumberTableAttribute() {
            if (codeAttribute != null) {
                LineNumberTableAttribute lineNumberTable = codeAttribute.getLineNumberTableAttribute();
                return lineNumberTable != null ? lineNumberTable : LineNumberTableAttribute.EMPTY;
            }
            return LineNumberTableAttribute.EMPTY;
        }

        @Override
        public long getBCIFromLine(int line) {
            return getMethod().getBCIFromLine(line);
        }

        @Override
        public Source getSource() {
            return getMethod().getSource();
        }

        @Override
        public boolean hasLine(int lineNumber) {
            return getMethod().hasLine(lineNumber);
        }

        @Override
        public String getSourceFile() {
            return getMethod().getSourceFile();
        }

        @Override
        public String getNameAsString() {
            return getMethod().getNameAsString();
        }

        @Override
        public String getSignatureAsString() {
            return getMethod().getSignatureAsString();
        }

        @Override
        public String getGenericSignatureAsString() {
            return getMethod().getGenericSignatureAsString();
        }

        @Override
        public int getModifiers() {
            return getMethod().getModifiers();
        }

        @Override
        public int bciToLineNumber(int bci) {
            return getMethod().bciToLineNumber(bci);
        }

        @Override
        public boolean isMethodNative() {
            return getMethod().isMethodNative();
        }

        @Override
        public byte[] getOriginalCode() {
            return getMethod().getOriginalCode();
        }

        @Override
        public KlassRef[] getParameters() {
            return getMethod().getParameters();
        }

        @Override
        public LocalVariableTableRef getLocalVariableTable() {
            return getMethod().getLocalVariableTable();
        }

        @Override
        public LocalVariableTableRef getLocalVariableTypeTable() {
            return getMethod().getLocalVariableTypeTable();
        }

        @Override
        public LineNumberTableRef getLineNumberTable() {
            return getMethod().getLineNumberTable();
        }

        @Override
        public Object invokeMethod(Object callee, Object[] args) {
            return getMethod().invokeMethod(callee, args);
        }

        @Override
        public boolean hasSourceFileAttribute() {
            return getMethod().hasSourceFileAttribute();
        }

        @Override
        public boolean isLastLine(long codeIndex) {
            return getMethod().isLastLine(codeIndex);
        }

        @Override
        public KlassRef getDeclaringKlass() {
            return getMethod().getDeclaringKlass();
        }

        @Override
        public int getFirstLine() {
            return getMethod().getFirstLine();
        }

        @Override
        public int getLastLine() {
            return getMethod().getLastLine();
        }

        @Override
        public MethodBreakpoint[] getMethodBreakpointInfos() {
            return getMethod().getMethodBreakpointInfos();
        }

        @Override
        public void addMethodBreakpointInfo(MethodBreakpoint info) {
            getMethod().addMethodBreakpointInfo(info);
        }

        @Override
        public void removeMethodBreakpointInfo(int requestId) {
            getMethod().removeMethodBreakpointInfo(requestId);
        }

        @Override
        public boolean hasActiveBreakpoint() {
            return getMethod().hasActiveBreakpoint();
        }

        @Override
        public boolean isObsolete() {
            return !assumption.isValid();
        }

        @Override
        public long getEndBCI() {
            int bci = 0;
            BytecodeStream bs = new BytecodeStream(getCodeAttribute().getCode());
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
    }
    // endregion jdwp-specific
}
