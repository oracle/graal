/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta implements ContextAccess {

    private final EspressoContext context;

    public Meta(EspressoContext context) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;

        // Give access to the partially-built Meta instance.
        context.setBootstrapMeta(this);

        // Core types.
        Object = knownKlass(Type.Object);
        Cloneable = knownKlass(Type.Cloneable);
        Serializable = knownKlass(Type.Serializable);
        ARRAY_SUPERINTERFACES = new ObjectKlass[]{Cloneable, Serializable};

        Class = knownKlass(Type.Class);
        HIDDEN_MIRROR_KLASS = Class.lookupHiddenField(Name.HIDDEN_MIRROR_KLASS);
        HIDDEN_SIGNERS = Class.lookupHiddenField(Name.HIDDEN_SIGNERS);
        String = knownKlass(Type.String);
        Class_Array = Class.array();
        Class_forName_String = Class.lookupDeclaredMethod(Name.forName, Signature.Class_String);
        Class_forName_String_boolean_ClassLoader = Class.lookupDeclaredMethod(Name.forName, Signature.Class_String_boolean_ClassLoader);
        HIDDEN_PROTECTION_DOMAIN = Class.lookupHiddenField(Name.HIDDEN_PROTECTION_DOMAIN);

        Object_array = Object.array();

        // Primitives.
        _boolean = new PrimitiveKlass(context, JavaKind.Boolean);
        _byte = new PrimitiveKlass(context, JavaKind.Byte);
        _char = new PrimitiveKlass(context, JavaKind.Char);
        _short = new PrimitiveKlass(context, JavaKind.Short);
        _float = new PrimitiveKlass(context, JavaKind.Float);
        _int = new PrimitiveKlass(context, JavaKind.Int);
        _double = new PrimitiveKlass(context, JavaKind.Double);
        _long = new PrimitiveKlass(context, JavaKind.Long);
        _void = new PrimitiveKlass(context, JavaKind.Void);

        _boolean_array = _boolean.array();
        _byte_array = _byte.array();
        _char_array = _char.array();
        _short_array = _short.array();
        _float_array = _float.array();
        _int_array = _int.array();
        _double_array = _double.array();
        _long_array = _long.array();

        // Boxed types.
        Boolean = knownKlass(Type.Boolean);
        Byte = knownKlass(Type.Byte);
        Character = knownKlass(Type.Character);
        Short = knownKlass(Type.Short);
        Float = knownKlass(Type.Float);
        Integer = knownKlass(Type.Integer);
        Double = knownKlass(Type.Double);
        Long = knownKlass(Type.Long);
        Void = knownKlass(Type.Void);

        BOXED_PRIMITIVE_KLASSES = new ObjectKlass[]{
                        Boolean,
                        Byte,
                        Character,
                        Short,
                        Float,
                        Integer,
                        Double,
                        Long,
                        Void
        };

        Boolean_valueOf = Boolean.lookupDeclaredMethod(Name.valueOf, Signature.Boolean_boolean);
        Byte_valueOf = Byte.lookupDeclaredMethod(Name.valueOf, Signature.Byte_byte);
        Character_valueOf = Character.lookupDeclaredMethod(Name.valueOf, Signature.Character_char);
        Short_valueOf = Short.lookupDeclaredMethod(Name.valueOf, Signature.Short_short);
        Float_valueOf = Float.lookupDeclaredMethod(Name.valueOf, Signature.Float_float);
        Integer_valueOf = Integer.lookupDeclaredMethod(Name.valueOf, Signature.Integer_int);
        Double_valueOf = Double.lookupDeclaredMethod(Name.valueOf, Signature.Double_double);
        Long_valueOf = Long.lookupDeclaredMethod(Name.valueOf, Signature.Long_long);

        Boolean_value = Boolean.lookupDeclaredField(Name.value, Type._boolean);
        Byte_value = Byte.lookupDeclaredField(Name.value, Type._byte);
        Character_value = Character.lookupDeclaredField(Name.value, Type._char);
        Short_value = Short.lookupDeclaredField(Name.value, Type._short);
        Float_value = Float.lookupDeclaredField(Name.value, Type._float);
        Integer_value = Integer.lookupDeclaredField(Name.value, Type._int);
        Double_value = Double.lookupDeclaredField(Name.value, Type._double);
        Long_value = Long.lookupDeclaredField(Name.value, Type._long);

        String_value = String.lookupDeclaredField(Name.value, Type._char_array);
        String_hash = String.lookupDeclaredField(Name.hash, Type._int);
        String_hashCode = String.lookupDeclaredMethod(Name.hashCode, Signature._int);
        String_length = String.lookupDeclaredMethod(Name.length, Signature._int);

        Throwable = knownKlass(Type.Throwable);
        HIDDEN_FRAMES = Throwable.lookupHiddenField(Name.HIDDEN_FRAMES);
        Throwable_backtrace = Throwable.lookupField(Name.backtrace, Type.Object);
        Throwable_cause = Throwable.lookupField(Name.cause, Type.Throwable);

        StackTraceElement = knownKlass(Type.StackTraceElement);
        StackTraceElement_init = StackTraceElement.lookupDeclaredMethod(Name.INIT, Signature._void_String_String_String_int);

        Exception = knownKlass(Type.Exception);
        InvocationTargetException = knownKlass(Type.InvocationTargetException);
        NegativeArraySizeException = knownKlass(Type.NegativeArraySizeException);
        IllegalArgumentException = knownKlass(Type.IllegalArgumentException);
        NullPointerException = knownKlass(Type.NullPointerException);
        ClassNotFoundException = knownKlass(Type.ClassNotFoundException);
        NoClassDefFoundError = knownKlass(Type.NoClassDefFoundError);
        InterruptedException = knownKlass(Type.InterruptedException);
        RuntimeException = knownKlass(Type.RuntimeException);
        IllegalMonitorStateException = knownKlass(Type.IllegalMonitorStateException);

        StackOverflowError = knownKlass(Type.StackOverflowError);
        OutOfMemoryError = knownKlass(Type.OutOfMemoryError);
        ClassCastException = knownKlass(Type.ClassCastException);
        AbstractMethodError = knownKlass(Type.AbstractMethodError);
        InternalError = knownKlass(Type.InternalError);
        VerifyError = knownKlass(Type.VerifyError);

        Error = knownKlass(Type.Error);
        NoSuchFieldError = knownKlass(Type.NoSuchFieldError);
        NoSuchMethodError = knownKlass(Type.NoSuchMethodError);
        IllegalAccessError = knownKlass(Type.IllegalAccessError);
        IncompatibleClassChangeError = knownKlass(Type.IncompatibleClassChangeError);

        PrivilegedActionException = knownKlass(Type.PrivilegedActionException);
        PrivilegedActionException_init_Exception = PrivilegedActionException.lookupDeclaredMethod(Name.INIT, Signature._void_Exception);

        ClassLoader = knownKlass(Type.ClassLoader);
        ClassLoader_findNative = ClassLoader.lookupDeclaredMethod(Name.findNative, Signature._long_ClassLoader_String);
        ClassLoader_getSystemClassLoader = ClassLoader.lookupDeclaredMethod(Name.getSystemClassLoader, Signature.ClassLoader);
        ClassLoader_parent = ClassLoader.lookupDeclaredField(Name.parent, Type.ClassLoader);

        // Guest reflection.
        Executable = knownKlass(Type.Executable);
        Constructor = knownKlass(Type.Constructor);
        HIDDEN_CONSTRUCTOR_KEY = Constructor.lookupHiddenField(Name.HIDDEN_CONSTRUCTOR_KEY);
        HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = Constructor.lookupHiddenField(Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        Constructor_clazz = Constructor.lookupDeclaredField(Name.clazz, Type.Class);
        Constructor_root = Constructor.lookupDeclaredField(Name.root, Type.Constructor);
        Constructor_parameterTypes = Constructor.lookupDeclaredField(Name.parameterTypes, Type.Class_array);
        Constructor_signature = Constructor.lookupDeclaredField(Name.signature, Type.String);
        MagicAccessorImpl = knownKlass(Type.MagicAccessorImpl);
        sun_reflect_DelegatingClassLoader = knownKlass(Type.sun_reflect_DelegatingClassLoader);

        Method = knownKlass(Type.Method);
        HIDDEN_METHOD_KEY = Method.lookupHiddenField(Name.HIDDEN_METHOD_KEY);
        HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = Method.lookupHiddenField(Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        Method_root = Method.lookupDeclaredField(Name.root, Type.Method);
        Method_clazz = Method.lookupDeclaredField(Name.clazz, Type.Class);
        Method_override = Method.lookupDeclaredField(Name.override, Type._boolean);
        Method_parameterTypes = Method.lookupDeclaredField(Name.parameterTypes, Type.Class_array);

        MethodAccessorImpl = knownKlass(Type.MethodAccessorImpl);
        ConstructorAccessorImpl = knownKlass(Type.ConstructorAccessorImpl);

        Parameter = knownKlass(Type.Parameter);

        Field = knownKlass(Type.Field);
        HIDDEN_FIELD_KEY = Field.lookupHiddenField(Name.HIDDEN_FIELD_KEY);
        HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = Field.lookupHiddenField(Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        Field_root = Field.lookupDeclaredField(Name.root, Field.getType());
        Field_class = Field.lookupDeclaredField(Name.clazz, Type.Class);
        Field_name = Field.lookupDeclaredField(Name.name, Type.String);
        Field_type = Field.lookupDeclaredField(Name.type, Type.Class);

        Shutdown = knownKlass(Type.Shutdown);
        Shutdown_shutdown = Shutdown.lookupDeclaredMethod(Name.shutdown, Signature._void);

        Buffer = knownKlass(Type.Buffer);
        sun_nio_ch_DirectBuffer = knownKlass(Type.sun_nio_ch_DirectBuffer);
        Buffer_address = Buffer.lookupDeclaredField(Name.address, Type._long);
        Buffer_capacity = Buffer.lookupDeclaredField(Name.capacity, Type._int);

        ByteBuffer = knownKlass(Type.ByteBuffer);
        ByteBuffer_wrap = ByteBuffer.lookupDeclaredMethod(Name.wrap, Signature.ByteBuffer_byte_array);

        Thread = knownKlass(Type.Thread);
        HIDDEN_HOST_THREAD = Thread.lookupHiddenField(Name.HIDDEN_HOST_THREAD);
        HIDDEN_IS_ALIVE = Thread.lookupHiddenField(Name.HIDDEN_IS_ALIVE);
        HIDDEN_INTERRUPTED = Thread.lookupHiddenField(Name.HIDDEN_INTERRUPTED);
        HIDDEN_DEATH = Thread.lookupHiddenField(Name.HIDDEN_DEATH);
        HIDDEN_SUSPEND_LOCK = Thread.lookupHiddenField(Name.HIDDEN_SUSPEND_LOCK);
        ThreadGroup = knownKlass(Type.ThreadGroup);
        ThreadGroup_remove = ThreadGroup.lookupDeclaredMethod(Name.remove, Signature.ThreadGroup_remove);
        Thread_dispatchUncaughtException = Thread.lookupDeclaredMethod(Name.dispatchUncaughtException, Signature._void_Throwable);
        Thread_exit = Thread.lookupDeclaredMethod(Name.exit, Signature._void);
        Thread_run = Thread.lookupDeclaredMethod(Name.run, Signature._void);
        Thread_threadStatus = Thread.lookupDeclaredField(Name.threadStatus, Type._int);

        Thread_group = Thread.lookupDeclaredField(Name.group, ThreadGroup.getType());
        Thread_name = Thread.lookupDeclaredField(Name.name, String.getType());
        Thread_priority = Thread.lookupDeclaredField(Name.priority, _int.getType());
        Thread_blockerLock = Thread.lookupDeclaredField(Name.blockerLock, Object.getType());
        Thread_daemon = Thread.lookupDeclaredField(Name.daemon, Type._boolean);
        Thread_inheritedAccessControlContext = Thread.lookupDeclaredField(Name.inheritedAccessControlContext, Type.AccessControlContext);
        Thread_checkAccess = Thread.lookupDeclaredMethod(Name.checkAccess, Signature._void);
        Thread_stop = Thread.lookupDeclaredMethod(Name.stop, Signature._void);
        ThreadGroup_maxPriority = ThreadGroup.lookupDeclaredField(Name.maxPriority, Type._int);

        FinalizerThread = knownKlass(Type.FinalizerThread);
        ReferenceHandler = knownKlass(Type.ReferenceHandler);

        sun_misc_VM = knownKlass(Type.sun_misc_VM);
        VM_toThreadState = sun_misc_VM.lookupDeclaredMethod(Name.toThreadState, Signature.toThreadState);

        sun_reflect_ConstantPool = knownKlass(Type.sun_reflect_ConstantPool);
        constantPoolOop = sun_reflect_ConstantPool.lookupDeclaredField(Name.constantPoolOop, Type.Object);

        System = knownKlass(Type.System);
        System_initializeSystemClass = System.lookupDeclaredMethod(Name.initializeSystemClass, Signature._void);
        System_exit = System.lookupDeclaredMethod(Name.exit, Signature._void_int);
        System_securityManager = System.lookupDeclaredField(Name.security, Type.SecurityManager);

        ProtectionDomain = knownKlass(Type.ProtectionDomain);
        ProtectionDomain_impliesCreateAccessControlContext = ProtectionDomain.lookupDeclaredMethod(Name.impliesCreateAccessControlContext, Signature._boolean);
        ProtectionDomain_init_CodeSource_PermissionCollection = ProtectionDomain.lookupDeclaredMethod(Name.INIT, Signature.CodeSource_PermissionCollection);

        AccessControlContext = knownKlass(Type.AccessControlContext);
        AccessControlContext_isAuthorized = AccessControlContext.lookupDeclaredMethod(Name.isAuthorized, Signature._boolean);
        ACC_context = AccessControlContext.lookupDeclaredField(Name.context, Type.ProtectionDomain_array);
        ACC_privilegedContext = AccessControlContext.lookupDeclaredField(Name.privilegedContext, Type.AccessControlContext);
        ACC_isPrivileged = AccessControlContext.lookupDeclaredField(Name.isPrivileged, Type._boolean);
        ACC_isAuthorized = AccessControlContext.lookupDeclaredField(Name.isAuthorized, Type._boolean);

        MethodType = knownKlass(Type.MethodType);
        MethodType_toMethodDescriptorString = MethodType.lookupDeclaredMethod(Name.toMethodDescriptorString, Signature.String);
        MethodType_fromMethodDescriptorString = MethodType.lookupDeclaredMethod(Name.fromMethodDescriptorString, Signature.fromMethodDescriptorString_signature);

        MemberName = knownKlass(Type.MemberName);
        HIDDEN_VMINDEX = MemberName.lookupHiddenField(Name.HIDDEN_VMINDEX);
        HIDDEN_VMTARGET = MemberName.lookupHiddenField(Name.HIDDEN_VMTARGET);
        MemberName_getSignature = MemberName.lookupDeclaredMethod(Name.getSignature, Signature.String);
        MemberName_clazz = MemberName.lookupDeclaredField(Name.clazz, Type.Class);
        MemberName_name = MemberName.lookupDeclaredField(Name.name, Type.String);
        MemberName_type = MemberName.lookupDeclaredField(Name.type, Type.MethodType);
        MemberName_flags = MemberName.lookupDeclaredField(Name.flags, Type._int);

        MethodHandle = knownKlass(Type.MethodHandle);
        invokeExact = MethodHandle.lookupDeclaredMethod(Name.invokeExact, Signature.Object_ObjectArray);
        invoke = MethodHandle.lookupDeclaredMethod(Name.invoke, Signature.Object_ObjectArray);
        invokeBasic = MethodHandle.lookupDeclaredMethod(Name.invokeBasic, Signature.Object_ObjectArray);
        invokeWithArguments = MethodHandle.lookupDeclaredMethod(Name.invokeWithArguments, Signature.Object_ObjectArray);
        linkToInterface = MethodHandle.lookupDeclaredMethod(Name.linkToInterface, Signature.Object_ObjectArray);
        linkToSpecial = MethodHandle.lookupDeclaredMethod(Name.linkToSpecial, Signature.Object_ObjectArray);
        linkToStatic = MethodHandle.lookupDeclaredMethod(Name.linkToStatic, Signature.Object_ObjectArray);
        linkToVirtual = MethodHandle.lookupDeclaredMethod(Name.linkToVirtual, Signature.Object_ObjectArray);
        MethodHandle_type = MethodHandle.lookupDeclaredField(Name.type, Type.MethodType);
        MethodHandle_form = MethodHandle.lookupDeclaredField(Name.form, Type.LambdaForm);

        MethodHandles = knownKlass(Type.MethodHandles);
        lookup = MethodHandles.lookupDeclaredMethod(Name.lookup, Signature.lookup_signature);

        CallSite = knownKlass(Type.CallSite);
        CallSite_target = CallSite.lookupDeclaredField(Name.target, Type.MethodHandle);

        LambdaForm = knownKlass(Type.LambdaForm);
        LambdaForm_vmentry = LambdaForm.lookupDeclaredField(Name.vmentry, Type.MemberName);
        LambdaForm_isCompiled = LambdaForm.lookupDeclaredField(Name.isCompiled, Type._boolean);
        LambdaForm_compileToBytecode = LambdaForm.lookupDeclaredMethod(Name.compileToBytecode, Signature.compileToBytecode);

        MethodHandleNatives = knownKlass(Type.MethodHandleNatives);
        MethodHandleNatives_linkMethod = MethodHandleNatives.lookupDeclaredMethod(Name.linkMethod, Signature.linkMethod_signature);
        MethodHandleNatives_linkCallSite = MethodHandleNatives.lookupDeclaredMethod(Name.linkCallSite, Signature.linkCallSite_signature);
        MethodHandleNatives_linkMethodHandleConstant = MethodHandleNatives.lookupDeclaredMethod(Name.linkMethodHandleConstant, Signature.linkMethodHandleConstant_signature);
        MethodHandleNatives_findMethodHandleType = MethodHandleNatives.lookupDeclaredMethod(Name.findMethodHandleType, Signature.MethodType_cons);

        Finalizer = knownKlass(Type.java_lang_ref_Finalizer);
        Finalizer_register = Finalizer.lookupDeclaredMethod(Name.register, Signature._void_Object);

        // References
        Reference = knownKlass(java.lang.ref.Reference.class);
        Reference_referent = Reference.lookupDeclaredField(Name.referent, Type.Object);

        Reference_discovered = Reference.lookupDeclaredField(Name.discovered, Type.java_lang_ref_Reference);
        Reference_pending = Reference.lookupDeclaredField(Name.pending, Type.java_lang_ref_Reference);
        Reference_next = Reference.lookupDeclaredField(Name.next, Type.java_lang_ref_Reference);
        Reference_queue = Reference.lookupDeclaredField(Name.queue, Type.java_lang_ref_ReferenceQueue);
        Reference_lock = Reference.lookupDeclaredField(Name.lock, Type.java_lang_ref_Reference_Lock);
        ReferenceQueue = knownKlass(java.lang.ref.ReferenceQueue.class);
        ReferenceQueue_NULL = ReferenceQueue.lookupDeclaredField(Name.NULL, Type.java_lang_ref_ReferenceQueue);

        WeakReference = knownKlass(java.lang.ref.WeakReference.class);
        SoftReference = knownKlass(java.lang.ref.SoftReference.class);
        PhantomReference = knownKlass(java.lang.ref.PhantomReference.class);
        FinalReference = knownKlass(Type.java_lang_ref_FinalReference);
        Cleaner = knownKlass(sun.misc.Cleaner.class);
        HIDDEN_HOST_REFERENCE = Reference.lookupHiddenField(Name.HIDDEN_HOST_REFERENCE);

        AssertionStatusDirectives = knownKlass(Type.AssertionStatusDirectives);
        AssertionStatusDirectives_classes = AssertionStatusDirectives.lookupField(Name.classes, Type.String_array);
        AssertionStatusDirectives_classEnabled = AssertionStatusDirectives.lookupField(Name.classEnabled, Type._boolean_array);
        AssertionStatusDirectives_packages = AssertionStatusDirectives.lookupField(Name.packages, Type.String_array);
        AssertionStatusDirectives_packageEnabled = AssertionStatusDirectives.lookupField(Name.packageEnabled, Type._boolean_array);
        AssertionStatusDirectives_deflt = AssertionStatusDirectives.lookupField(Name.deflt, Type._boolean);
    }

    public final ObjectKlass Object;
    public final ArrayKlass Object_array;

    public final ObjectKlass String;
    public final ObjectKlass Class;
    public final Field HIDDEN_MIRROR_KLASS;
    public final Field HIDDEN_PROTECTION_DOMAIN;
    public final Field HIDDEN_SIGNERS;
    public final Field constantPoolOop;
    public final ArrayKlass Class_Array;
    public final Method Class_forName_String;
    public final Method Class_forName_String_boolean_ClassLoader;

    // Primitives.
    public final PrimitiveKlass _boolean;
    public final PrimitiveKlass _byte;
    public final PrimitiveKlass _char;
    public final PrimitiveKlass _short;
    public final PrimitiveKlass _float;
    public final PrimitiveKlass _int;
    public final PrimitiveKlass _double;
    public final PrimitiveKlass _long;
    public final PrimitiveKlass _void;

    public final ArrayKlass _boolean_array;
    public final ArrayKlass _byte_array;
    public final ArrayKlass _char_array;
    public final ArrayKlass _short_array;
    public final ArrayKlass _float_array;
    public final ArrayKlass _int_array;
    public final ArrayKlass _double_array;
    public final ArrayKlass _long_array;

    // Boxed primitives.
    public final ObjectKlass Boolean;
    public final ObjectKlass Byte;
    public final ObjectKlass Character;
    public final ObjectKlass Short;
    public final ObjectKlass Integer;
    public final ObjectKlass Float;
    public final ObjectKlass Double;
    public final ObjectKlass Long;
    public final ObjectKlass Void;

    // Boxing conversions.
    public final Method Boolean_valueOf;
    public final Method Byte_valueOf;
    public final Method Character_valueOf;
    public final Method Short_valueOf;
    public final Method Float_valueOf;
    public final Method Integer_valueOf;
    public final Method Double_valueOf;
    public final Method Long_valueOf;

    public final Field Boolean_value;
    public final Field Byte_value;
    public final Field Character_value;
    public final Field Short_value;
    public final Field Float_value;
    public final Field Integer_value;
    public final Field Double_value;
    public final Field Long_value;

    // Guest String.
    public final Field String_value;
    public final Field String_hash;
    public final Method String_hashCode;
    public final Method String_length;

    public final ObjectKlass ClassLoader;
    public final Field ClassLoader_parent;
    public final Method ClassLoader_findNative;
    public final Method ClassLoader_getSystemClassLoader;

    public final ObjectKlass AssertionStatusDirectives;
    public final Field AssertionStatusDirectives_classes;
    public final Field AssertionStatusDirectives_classEnabled;
    public final Field AssertionStatusDirectives_packages;
    public final Field AssertionStatusDirectives_packageEnabled;
    public final Field AssertionStatusDirectives_deflt;

    public final ObjectKlass Executable;

    public final ObjectKlass Constructor;
    public final Field HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_CONSTRUCTOR_KEY;
    public final Field Constructor_clazz;
    public final Field Constructor_root;
    public final Field Constructor_parameterTypes;
    public final Field Constructor_signature;

    public final ObjectKlass MagicAccessorImpl;
    public final ObjectKlass sun_reflect_DelegatingClassLoader;

    public final ObjectKlass Method;
    public final Field HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_METHOD_KEY;
    public final Field Method_root;
    public final Field Method_clazz;
    public final Field Method_override;
    public final Field Method_parameterTypes;

    public final ObjectKlass MethodAccessorImpl;
    public final ObjectKlass ConstructorAccessorImpl;

    public final ObjectKlass Parameter;

    public final ObjectKlass Field;
    public final Field HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_FIELD_KEY;
    public final Field Field_root;
    public final Field Field_class;
    public final Field Field_name;
    public final Field Field_type;

    public final Method Shutdown_shutdown;
    public final ObjectKlass Shutdown;

    public final ObjectKlass Exception;
    public final ObjectKlass InvocationTargetException;
    public final ObjectKlass NegativeArraySizeException;
    public final ObjectKlass IllegalArgumentException;
    public final ObjectKlass IllegalMonitorStateException;
    public final ObjectKlass NullPointerException;
    public final ObjectKlass ClassNotFoundException;
    public final ObjectKlass NoClassDefFoundError;
    public final ObjectKlass InterruptedException;
    public final ObjectKlass RuntimeException;
    public final ObjectKlass StackOverflowError;
    public final ObjectKlass OutOfMemoryError;
    public final ObjectKlass ClassCastException;
    public final ObjectKlass AbstractMethodError;
    public final ObjectKlass InternalError;
    public final ObjectKlass VerifyError;

    public final ObjectKlass Throwable;
    public final Field HIDDEN_FRAMES;
    public final Field Throwable_backtrace;
    public final Field Throwable_cause;

    public final ObjectKlass Error;
    public final ObjectKlass NoSuchFieldError;
    public final ObjectKlass NoSuchMethodError;
    public final ObjectKlass IllegalAccessError;
    public final ObjectKlass IncompatibleClassChangeError;

    public final ObjectKlass StackTraceElement;
    public final Method StackTraceElement_init;

    public final ObjectKlass PrivilegedActionException;
    public final Method PrivilegedActionException_init_Exception;

    // Array support.
    public final ObjectKlass Cloneable;
    public final ObjectKlass Serializable;

    public final ObjectKlass sun_nio_ch_DirectBuffer;
    public final ObjectKlass Buffer;
    public final Field Buffer_address;
    public final Field Buffer_capacity;

    public final ObjectKlass ByteBuffer;
    public final Method ByteBuffer_wrap;

    public final ObjectKlass ThreadGroup;
    public final Method ThreadGroup_remove;
    public final Method Thread_dispatchUncaughtException;
    public final Field ThreadGroup_maxPriority;
    public final ObjectKlass Thread;
    public final Field Thread_threadStatus;
    public final Method Thread_exit;
    public final Method Thread_run;
    public final Method Thread_checkAccess;
    public final Method Thread_stop;
    public final Field HIDDEN_HOST_THREAD;
    public final Field HIDDEN_IS_ALIVE;
    public final Field HIDDEN_INTERRUPTED;
    public final Field HIDDEN_DEATH;
    public final Field HIDDEN_SUSPEND_LOCK;
    public final Field Thread_group;
    public final Field Thread_name;
    public final Field Thread_priority;
    public final Field Thread_blockerLock;
    public final Field Thread_daemon;
    public final Field Thread_inheritedAccessControlContext;

    public final ObjectKlass FinalizerThread;
    public final ObjectKlass ReferenceHandler;

    public final ObjectKlass sun_misc_VM;
    public final Method VM_toThreadState;
    public final ObjectKlass sun_reflect_ConstantPool;

    public final ObjectKlass System;
    public final Method System_initializeSystemClass;
    public final Method System_exit;
    public final Field System_securityManager;

    public final ObjectKlass ProtectionDomain;
    public final Method ProtectionDomain_impliesCreateAccessControlContext;
    public final Method ProtectionDomain_init_CodeSource_PermissionCollection;

    public final ObjectKlass AccessControlContext;
    public final Method AccessControlContext_isAuthorized;
    public final Field ACC_context;
    public final Field ACC_privilegedContext;
    public final Field ACC_isPrivileged;
    public final Field ACC_isAuthorized;

    public final ObjectKlass MethodType;
    public final Method MethodType_toMethodDescriptorString;

    public final ObjectKlass MemberName;
    public final Method MemberName_getSignature;
    public final Method MethodType_fromMethodDescriptorString;
    public final Field HIDDEN_VMTARGET;
    public final Field HIDDEN_VMINDEX;
    public final Field MemberName_clazz;
    public final Field MemberName_name;
    public final Field MemberName_type;
    public final Field MemberName_flags;

    public final ObjectKlass MethodHandle;
    public final Method invoke;
    public final Method invokeExact;
    public final Method invokeBasic;
    public final Method invokeWithArguments;
    public final Method linkToInterface;
    public final Method linkToSpecial;
    public final Method linkToStatic;
    public final Method linkToVirtual;
    public final Field MethodHandle_type;
    public final Field MethodHandle_form;

    public final ObjectKlass MethodHandles;
    public final Method lookup;

    public final ObjectKlass CallSite;
    public final Field CallSite_target;

    public final ObjectKlass LambdaForm;
    public final Field LambdaForm_vmentry;
    public final Field LambdaForm_isCompiled;
    public final Method LambdaForm_compileToBytecode;

    public final ObjectKlass MethodHandleNatives;
    public final Method MethodHandleNatives_linkMethod;
    public final Method MethodHandleNatives_linkMethodHandleConstant;
    public final Method MethodHandleNatives_findMethodHandleType;
    public final Method MethodHandleNatives_linkCallSite;

    // References

    public final ObjectKlass Finalizer;
    public final Method Finalizer_register;
    public final ObjectKlass Reference;
    public final Field Reference_referent;
    public final Field Reference_discovered;
    public final Field Reference_pending;
    public final Field Reference_next;
    public final Field Reference_queue;
    public final Field Reference_lock;
    public final ObjectKlass WeakReference;
    public final ObjectKlass SoftReference;
    public final ObjectKlass PhantomReference;
    public final ObjectKlass FinalReference;
    public final ObjectKlass Cleaner;

    public final Field HIDDEN_HOST_REFERENCE;

    public final ObjectKlass ReferenceQueue;
    public final Field ReferenceQueue_NULL;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] ARRAY_SUPERINTERFACES;

    @CompilationFinal(dimensions = 1) public final ObjectKlass[] BOXED_PRIMITIVE_KLASSES;

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: (host) known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public static StaticObject box(Meta meta, Object arg) {
        if (arg instanceof Boolean) {
            return meta.boxBoolean((boolean) arg);
        }
        if (arg instanceof Character) {
            return meta.boxCharacter((char) arg);
        }
        if (arg instanceof Short) {
            return meta.boxShort((short) arg);
        }
        if (arg instanceof Byte) {
            return meta.boxByte((byte) arg);
        }
        if (arg instanceof Integer) {
            return meta.boxInteger((int) arg);
        }
        if (arg instanceof Long) {
            return meta.boxLong((long) arg);
        }
        if (arg instanceof Float) {
            return meta.boxFloat((float) arg);
        }
        if (arg instanceof Double) {
            return meta.boxDouble((double) arg);
        }
        throw EspressoError.shouldNotReachHere();
    }

    public StaticObject initEx(java.lang.Class<?> clazz) {
        assert Throwable.class.isAssignableFrom(clazz);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void).invokeDirect(ex);
        return ex;
    }

    public static StaticObject initExWithMessage(ObjectKlass klass, String message) {
        assert klass.getMeta().Throwable.isAssignableFrom(klass);
        StaticObject ex = klass.allocateInstance();
        // Call constructor.
        klass.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(ex, ex.getKlass().getMeta().toGuestString(message));
        return ex;
    }

    public static StaticObject initExWithMessage(ObjectKlass klass, @Host(String.class) StaticObject message) {
        assert klass.getMeta().Throwable.isAssignableFrom(klass);
        assert StaticObject.isNull(message) || klass.getMeta().String.isAssignableFrom(message.getKlass());
        StaticObject ex = klass.allocateInstance();
        // Call constructor.
        klass.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(ex, message);
        return ex;
    }

    public static StaticObject initEx(ObjectKlass klass) {
        assert klass.getMeta().Throwable.isAssignableFrom(klass);
        StaticObject ex = klass.allocateInstance();
        // Call constructor.
        klass.lookupDeclaredMethod(Name.INIT, Signature._void).invokeDirect(ex);
        return ex;
    }

    public StaticObject initExWithMessage(java.lang.Class<?> clazz, String message) {
        assert Throwable.class.isAssignableFrom(clazz);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(ex, toGuestString(message));
        return ex;
    }

    public StaticObject initExWithCause(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.class.isAssignableFrom(clazz);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_Throwable).invokeDirect(ex, cause);
        return ex;
    }

    public StaticObject initExWithCause(ObjectKlass exKlass, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.isAssignableFrom(exKlass);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_Throwable).invokeDirect(ex, cause);
        return ex;
    }

    public StaticObject initExWithCauseAndMessage(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause, String message) {
        assert Throwable.class.isAssignableFrom(clazz);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_String_Throwable).invokeDirect(ex, toGuestString(message), cause);
        return ex;
    }

    public StaticObject initExWithCauseAndMessage(ObjectKlass exKlass, @Host(Throwable.class) StaticObject cause, @Host(String.class) StaticObject message) {
        assert Throwable.isAssignableFrom(exKlass);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_String_Throwable).invokeDirect(ex, message, cause);
        return ex;
    }

    @TruffleBoundary
    public EspressoException throwEx(ObjectKlass exKlass) {
        assert Throwable.isAssignableFrom(exKlass);
        throw new EspressoException(initEx(exKlass));
    }

    @TruffleBoundary
    public EspressoException throwEx(java.lang.Class<?> clazz) {
        assert Throwable.class.isAssignableFrom(clazz);
        throw new EspressoException(initEx(clazz));
    }

    @TruffleBoundary
    public EspressoException throwExWithMessage(java.lang.Class<?> clazz, String message) {
        throw new EspressoException(initExWithMessage(clazz, message));
    }

    @TruffleBoundary
    public EspressoException throwExWithCause(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.class.isAssignableFrom(clazz);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        throw new EspressoException(initExWithCause(clazz, cause));
    }

    @TruffleBoundary
    public EspressoException throwExWithCauseAndMessage(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause, String message) {
        assert Throwable.class.isAssignableFrom(clazz);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        throw new EspressoException(initExWithCauseAndMessage(clazz, cause, message));
    }

    @TruffleBoundary
    public EspressoException throwExWithMessage(ObjectKlass exKlass, @Host(String.class) StaticObject message) {
        assert Throwable.isAssignableFrom(exKlass);
        assert StaticObject.isNull(message) || String.isAssignableFrom(message.getKlass());
        throw new EspressoException(initExWithMessage(exKlass, message));
    }

    @TruffleBoundary
    public EspressoException throwExWithCause(ObjectKlass exKlass, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.isAssignableFrom(exKlass);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        throw new EspressoException(initExWithCause(exKlass, cause));
    }

    @TruffleBoundary
    public EspressoException throwExWithCauseAndMessage(ObjectKlass exKlass, @Host(Throwable.class) StaticObject cause, @Host(java.lang.String.class) StaticObject message) {
        assert Throwable.isAssignableFrom(exKlass);
        assert StaticObject.isNull(cause) || Throwable.isAssignableFrom(cause.getKlass());
        throw new EspressoException(initExWithCauseAndMessage(exKlass, cause, message));
    }

    @TruffleBoundary
    public Klass throwableKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Throwable.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    @TruffleBoundary
    public ObjectKlass knownKlass(Symbol<Type> type) {
        assert !Types.isArray(type);
        assert !Types.isPrimitive(type);
        return (ObjectKlass) getRegistries().loadKlassWithBootClassLoader(type);
    }

    @TruffleBoundary
    public ObjectKlass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        assert !hostClass.isPrimitive();
        // Resolve non-primitive classes using BCL.
        return knownKlass(getTypes().fromClass(hostClass));
    }

    /**
     * Performs class loading according to {§5.3. Creation and Loading}. This method directly asks
     * the given class loader to perform the load, even for internal primitive types. This is the
     * method to use when loading symbols that are not directly taken from a constant pool, for
     * example, when loading a class whose name is given by a guest string..
     *
     * @param type The symbolic type.
     * @param classLoader The class loader
     * @return The asked Klass.
     */
    @TruffleBoundary
    public Klass loadKlass(Symbol<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        return getRegistries().loadKlass(type, classLoader);
    }

    /**
     * Resolves an internal symbolic type descriptor taken from the constant pool, and returns the
     * corresponding Klass.
     * <li>If the symbol represents an internal primitive (/ex: 'B' or 'I'), this method returns
     * immediately returns the corresponding primitive. Thus, primitives are not 'loaded'.
     * <li>If the symbol is a symbolic references, it asks the given ClassLoader to load the
     * corresponding Klass.
     * <li>If the symbol represents an array, recursively resolve its elemental type, and returns
     * the array Klass need.
     *
     * @param type The symbolic type
     * @param classLoader The class loader of the constant pool holder.
     * @return The asked Klass.
     */
    public Klass resolveSymbol(Symbol<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        // Resolution only resolves references. Bypass loading for primitives.
        if (type.length() == 1) {
            switch (type.byteAt(0)) {
                case 'B': // byte
                    return _byte;
                case 'C': // char
                    return _char;
                case 'D': // double
                    return _double;
                case 'F': // float
                    return _float;
                case 'I': // int
                    return _int;
                case 'J': // long
                    return _long;
                case 'S': // short
                    return _short;
                case 'V': // void
                    return _void;
                case 'Z': // boolean
                    return _boolean;
                default:
            }
        }
        if (Types.isArray(type)) {
            Klass elemental = resolveSymbol(getTypes().getElementalType(type), classLoader);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }
        return loadKlass(type, classLoader);
    }

    @TruffleBoundary
    public static String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        Meta meta = str.getKlass().getMeta();
        char[] value = ((StaticObject) meta.String_value.get(str)).unwrap();
        return HostJava.createString(value);
    }

    @TruffleBoundary
    public StaticObject toGuestString(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        final char[] value = HostJava.getStringValue(hostString);
        final int hash = HostJava.getStringHash(hostString);
        StaticObject guestString = String.allocateInstance();
        String_value.set(guestString, StaticObject.wrap(value));
        String_hash.set(guestString, hash);
        // String.hashCode must be equivalent for host and guest.
        assert hostString.hashCode() == (int) String_hashCode.invokeDirect(guestString);
        return guestString;
    }

    @TruffleBoundary
    public StaticObject toGuestString(Symbol<?> hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        return toGuestString(hostString.toString());
    }

    public static boolean isString(Object string) {
        if (string instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) string;
            return staticObject.isString();
        }
        return false;
    }

    public Object toGuestBoxed(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuestString((String) hostObject);
        }
        if (hostObject instanceof StaticObject) {
            return hostObject;
        }
        if (hostObject instanceof StaticObject[]) {
            return hostObject;
        }

        if (hostObject instanceof Boolean ||
                        hostObject instanceof Byte ||
                        hostObject instanceof Character ||
                        hostObject instanceof Short ||
                        hostObject instanceof Integer ||
                        hostObject instanceof Long ||
                        hostObject instanceof Float ||
                        hostObject instanceof Double) {
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toHostBoxed(Object object) {
        assert object != null;
        if (object instanceof StaticObject) {
            StaticObject guestObject = (StaticObject) object;
            if (StaticObject.isNull(guestObject)) {
                return null;
            }
            if (guestObject == StaticObject.VOID) {
                return null;
            }
            if (guestObject.isArray()) {
                return guestObject.unwrap();
            }
            if (guestObject.getKlass() == String) {
                return toHostString(guestObject);
            }
            return unboxGuest((StaticObject) object);
        }
        return object;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // region Low level host String access

    private static class HostJava {

        private static final java.lang.reflect.Field String_value;
        private static final java.lang.reflect.Field String_hash;
        private static final Constructor<String> String_init;

        static {
            try {
                String_value = String.class.getDeclaredField("value");
                String_value.setAccessible(true);
                String_hash = String.class.getDeclaredField("hash");
                String_hash.setAccessible(true);
                String_init = String.class.getDeclaredConstructor(char[].class, boolean.class);
                String_init.setAccessible(true);
            } catch (NoSuchMethodException | NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static char[] getStringValue(String s) {
            try {
                return (char[]) String_value.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static int getStringHash(String s) {
            try {
                return (int) String_hash.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static String createString(final char[] value) {
            try {
                return HostJava.String_init.newInstance(value, true);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    // endregion

    // region Guest Unboxing

    public Object unboxGuest(StaticObject boxed) {
        Klass klass = boxed.getKlass();
        if (klass == Boolean) {
            return unboxBoolean(boxed);
        } else if (klass == Byte) {
            return unboxByte(boxed);
        } else if (klass == Character) {
            return unboxCharacter(boxed);
        } else if (klass == Short) {
            return unboxShort(boxed);
        } else if (klass == Float) {
            return unboxFloat(boxed);
        } else if (klass == Integer) {
            return unboxInteger(boxed);
        } else if (klass == Double) {
            return unboxDouble(boxed);
        } else if (klass == Long) {
            return unboxLong(boxed);
        } else {
            return boxed;
        }
    }

    public boolean unboxBoolean(@Host(Boolean.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Boolean) {
            throw throwEx(IllegalArgumentException);
        }
        return (boolean) Boolean_value.get(boxed);
    }

    public byte unboxByte(@Host(Byte.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Byte) {
            throw throwEx(IllegalArgumentException);
        }
        return (byte) Byte_value.get(boxed);
    }

    public char unboxCharacter(@Host(Character.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Character) {
            throw throwEx(IllegalArgumentException);
        }
        return (char) Character_value.get(boxed);
    }

    public short unboxShort(@Host(Short.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Short) {
            throw throwEx(IllegalArgumentException);
        }
        return (short) Short_value.get(boxed);
    }

    public float unboxFloat(@Host(Float.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Float) {
            throw throwEx(IllegalArgumentException);
        }
        return (float) Float_value.get(boxed);
    }

    public int unboxInteger(@Host(Integer.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Integer) {
            throw throwEx(IllegalArgumentException);
        }
        return (int) Integer_value.get(boxed);
    }

    public double unboxDouble(@Host(Double.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Double) {
            throw throwEx(IllegalArgumentException);
        }
        return (double) Double_value.get(boxed);
    }

    public long unboxLong(@Host(Long.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != Long) {
            throw throwEx(IllegalArgumentException);
        }
        return (long) Long_value.get(boxed);
    }

    // endregion Guest Unboxing

    // region Guest

    public @Host(Boolean.class) StaticObject boxBoolean(boolean value) {
        return (StaticObject) Boolean_valueOf.invokeDirect(null, value);
    }

    public @Host(Byte.class) StaticObject boxByte(byte value) {
        return (StaticObject) Byte_valueOf.invokeDirect(null, value);
    }

    public @Host(Character.class) StaticObject boxCharacter(char value) {
        return (StaticObject) Character_valueOf.invokeDirect(null, value);
    }

    public @Host(Short.class) StaticObject boxShort(short value) {
        return (StaticObject) Short_valueOf.invokeDirect(null, value);
    }

    public @Host(Float.class) StaticObject boxFloat(float value) {
        return (StaticObject) Float_valueOf.invokeDirect(null, value);
    }

    public @Host(Integer.class) StaticObject boxInteger(int value) {
        return (StaticObject) Integer_valueOf.invokeDirect(null, value);
    }

    public @Host(Double.class) StaticObject boxDouble(double value) {
        return (StaticObject) Double_valueOf.invokeDirect(null, value);
    }

    public @Host(Long.class) StaticObject boxLong(long value) {
        return (StaticObject) Long_valueOf.invokeDirect(null, value);
    }

    // endregion Guest boxing
}
