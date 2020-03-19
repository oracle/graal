/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta implements ContextAccess {

    private final EspressoContext context;
    private final ExceptionDispatch dispatch;

    public Meta(EspressoContext context) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;

        // Give access to the partially-built Meta instance.
        context.setBootstrapMeta(this);

        // Core types.
        java_lang_Object = knownKlass(Type.java_lang_Object);
        java_lang_Cloneable = knownKlass(Type.java_lang_Cloneable);
        java_io_Serializable = knownKlass(Type.java_io_Serializable);
        ARRAY_SUPERINTERFACES = new ObjectKlass[]{java_lang_Cloneable, java_io_Serializable};

        java_lang_Class = knownKlass(Type.java_lang_Class);
        HIDDEN_MIRROR_KLASS = java_lang_Class.lookupHiddenField(Name.HIDDEN_MIRROR_KLASS);
        HIDDEN_SIGNERS = java_lang_Class.lookupHiddenField(Name.HIDDEN_SIGNERS);
        java_lang_String = knownKlass(Type.java_lang_String);
        java_lang_Class_array = java_lang_Class.array();
        java_lang_Class_forName_String = java_lang_Class.lookupDeclaredMethod(Name.forName, Signature.Class_String);
        java_lang_Class_forName_String_boolean_ClassLoader = java_lang_Class.lookupDeclaredMethod(Name.forName, Signature.Class_String_boolean_ClassLoader);
        HIDDEN_PROTECTION_DOMAIN = java_lang_Class.lookupHiddenField(Name.HIDDEN_PROTECTION_DOMAIN);

        java_lang_Object_array = java_lang_Object.array();

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
        java_lang_Boolean = knownKlass(Type.java_lang_Boolean);
        java_lang_Byte = knownKlass(Type.java_lang_Byte);
        java_lang_Character = knownKlass(Type.java_lang_Character);
        java_lang_Short = knownKlass(Type.java_lang_Short);
        java_lang_Float = knownKlass(Type.java_lang_Float);
        java_lang_Integer = knownKlass(Type.java_lang_Integer);
        java_lang_Double = knownKlass(Type.java_lang_Double);
        java_lang_Long = knownKlass(Type.java_lang_Long);
        java_lang_Void = knownKlass(Type.java_lang_Void);

        BOXED_PRIMITIVE_KLASSES = new ObjectKlass[]{
                        java_lang_Boolean,
                        java_lang_Byte,
                        java_lang_Character,
                        java_lang_Short,
                        java_lang_Float,
                        java_lang_Integer,
                        java_lang_Double,
                        java_lang_Long,
                        java_lang_Void
        };

        java_lang_Boolean_valueOf = java_lang_Boolean.lookupDeclaredMethod(Name.valueOf, Signature.Boolean_boolean);
        java_lang_Byte_valueOf = java_lang_Byte.lookupDeclaredMethod(Name.valueOf, Signature.Byte_byte);
        java_lang_Character_valueOf = java_lang_Character.lookupDeclaredMethod(Name.valueOf, Signature.Character_char);
        java_lang_Short_valueOf = java_lang_Short.lookupDeclaredMethod(Name.valueOf, Signature.Short_short);
        java_lang_Float_valueOf = java_lang_Float.lookupDeclaredMethod(Name.valueOf, Signature.Float_float);
        java_lang_Integer_valueOf = java_lang_Integer.lookupDeclaredMethod(Name.valueOf, Signature.Integer_int);
        java_lang_Double_valueOf = java_lang_Double.lookupDeclaredMethod(Name.valueOf, Signature.Double_double);
        java_lang_Long_valueOf = java_lang_Long.lookupDeclaredMethod(Name.valueOf, Signature.Long_long);

        java_lang_Boolean_value = java_lang_Boolean.lookupDeclaredField(Name.value, Type._boolean);
        java_lang_Byte_value = java_lang_Byte.lookupDeclaredField(Name.value, Type._byte);
        java_lang_Character_value = java_lang_Character.lookupDeclaredField(Name.value, Type._char);
        java_lang_Short_value = java_lang_Short.lookupDeclaredField(Name.value, Type._short);
        java_lang_Float_value = java_lang_Float.lookupDeclaredField(Name.value, Type._float);
        java_lang_Integer_value = java_lang_Integer.lookupDeclaredField(Name.value, Type._int);
        java_lang_Double_value = java_lang_Double.lookupDeclaredField(Name.value, Type._double);
        java_lang_Long_value = java_lang_Long.lookupDeclaredField(Name.value, Type._long);

        java_lang_String_value = java_lang_String.lookupDeclaredField(Name.value, Type._char_array);
        EspressoError.guarantee(java_lang_String_value != null && Type._char_array.equals(java_lang_String_value.getType()), "String.value must be a char[]");

        java_lang_String_hash = java_lang_String.lookupDeclaredField(Name.hash, Type._int);
        java_lang_String_hashCode = java_lang_String.lookupDeclaredMethod(Name.hashCode, Signature._int);
        java_lang_String_length = java_lang_String.lookupDeclaredMethod(Name.length, Signature._int);

        java_lang_Throwable = knownKlass(Type.java_lang_Throwable);
        java_lang_Throwable_getStackTrace = java_lang_Throwable.lookupDeclaredMethod(Name.getStackTrace, Signature.StackTraceElement_array);
        HIDDEN_FRAMES = java_lang_Throwable.lookupHiddenField(Name.HIDDEN_FRAMES);
        java_lang_Throwable_backtrace = java_lang_Throwable.lookupField(Name.backtrace, Type.java_lang_Object);
        java_lang_Throwable_detailMessage = java_lang_Throwable.lookupField(Name.detailMessage, Type.java_lang_String);
        java_lang_Throwable_cause = java_lang_Throwable.lookupField(Name.cause, Type.java_lang_Throwable);

        java_lang_StackTraceElement = knownKlass(Type.java_lang_StackTraceElement);
        java_lang_StackTraceElement_init = java_lang_StackTraceElement.lookupDeclaredMethod(Name._init_, Signature._void_String_String_String_int);

        java_lang_Exception = knownKlass(Type.java_lang_Exception);
        java_lang_reflect_InvocationTargetException = knownKlass(Type.java_lang_reflect_InvocationTargetException);
        java_lang_NegativeArraySizeException = knownKlass(Type.java_lang_NegativeArraySizeException);
        java_lang_IllegalArgumentException = knownKlass(Type.java_lang_IllegalArgumentException);
        java_lang_NullPointerException = knownKlass(Type.java_lang_NullPointerException);
        java_lang_ClassNotFoundException = knownKlass(Type.java_lang_ClassNotFoundException);
        java_lang_NoClassDefFoundError = knownKlass(Type.java_lang_NoClassDefFoundError);
        java_lang_InterruptedException = knownKlass(Type.java_lang_InterruptedException);
        java_lang_ThreadDeath = knownKlass(Type.java_lang_ThreadDeath);

        java_lang_RuntimeException = knownKlass(Type.java_lang_RuntimeException);
        java_lang_IllegalMonitorStateException = knownKlass(Type.java_lang_IllegalMonitorStateException);
        java_lang_ArrayStoreException = knownKlass(Type.java_lang_ArrayStoreException);
        java_lang_IndexOutOfBoundsException = knownKlass(Type.java_lang_IndexOutOfBoundsException);
        java_lang_ArrayIndexOutOfBoundsException = knownKlass(Type.java_lang_ArrayIndexOutOfBoundsException);
        java_lang_StringIndexOutOfBoundsException = knownKlass(Type.java_lang_StringIndexOutOfBoundsException);
        java_lang_ExceptionInInitializerError = knownKlass(Type.java_lang_ExceptionInInitializerError);
        java_lang_InstantiationException = knownKlass(Type.java_lang_InstantiationException);
        java_lang_InstantiationError = knownKlass(Type.java_lang_InstantiationError);
        java_lang_CloneNotSupportedException = knownKlass(Type.java_lang_CloneNotSupportedException);
        java_lang_SecurityException = knownKlass(Type.java_lang_SecurityException);
        java_lang_ArithmeticException = knownKlass(Type.java_lang_ArithmeticException);
        java_lang_LinkageError = knownKlass(Type.java_lang_LinkageError);
        java_lang_NoSuchFieldException = knownKlass(Type.java_lang_NoSuchFieldException);
        java_lang_NoSuchMethodException = knownKlass(Type.java_lang_NoSuchMethodException);
        java_lang_UnsupportedOperationException = knownKlass(Type.java_lang_UnsupportedOperationException);

        java_lang_StackOverflowError = knownKlass(Type.java_lang_StackOverflowError);
        java_lang_OutOfMemoryError = knownKlass(Type.java_lang_OutOfMemoryError);
        java_lang_ClassCastException = knownKlass(Type.java_lang_ClassCastException);
        java_lang_AbstractMethodError = knownKlass(Type.java_lang_AbstractMethodError);
        java_lang_InternalError = knownKlass(Type.java_lang_InternalError);
        java_lang_VerifyError = knownKlass(Type.java_lang_VerifyError);
        java_lang_ClassFormatError = knownKlass(Type.java_lang_ClassFormatError);
        java_lang_ClassCircularityError = knownKlass(Type.java_lang_ClassCircularityError);
        java_lang_UnsatisfiedLinkError = knownKlass(Type.java_lang_UnsatisfiedLinkError);
        java_lang_UnsupportedClassVersionError = knownKlass(Type.java_lang_UnsupportedClassVersionError);

        java_lang_Error = knownKlass(Type.java_lang_Error);
        java_lang_NoSuchFieldError = knownKlass(Type.java_lang_NoSuchFieldError);
        java_lang_NoSuchMethodError = knownKlass(Type.java_lang_NoSuchMethodError);
        java_lang_IllegalAccessError = knownKlass(Type.java_lang_IllegalAccessError);
        java_lang_IncompatibleClassChangeError = knownKlass(Type.java_lang_IncompatibleClassChangeError);
        java_lang_BootstrapMethodError = knownKlass(Type.java_lang_BootstrapMethodError);

        java_security_PrivilegedActionException = knownKlass(Type.java_security_PrivilegedActionException);
        java_security_PrivilegedActionException_init_Exception = java_security_PrivilegedActionException.lookupDeclaredMethod(Name._init_, Signature._void_Exception);

        java_lang_ClassLoader = knownKlass(Type.java_lang_ClassLoader);
        java_lang_ClassLoader$NativeLibrary = knownKlass(Type.java_lang_ClassLoader$NativeLibrary);
        java_lang_ClassLoader$NativeLibrary_getFromClass = java_lang_ClassLoader$NativeLibrary.lookupDeclaredMethod(Name.getFromClass, Signature.Class);
        java_lang_ClassLoader_findNative = java_lang_ClassLoader.lookupDeclaredMethod(Name.findNative, Signature._long_ClassLoader_String);
        java_lang_ClassLoader_getSystemClassLoader = java_lang_ClassLoader.lookupDeclaredMethod(Name.getSystemClassLoader, Signature.ClassLoader);
        java_lang_ClassLoader_parent = java_lang_ClassLoader.lookupDeclaredField(Name.parent, Type.java_lang_ClassLoader);
        HIDDEN_CLASS_LOADER_REGISTRY = java_lang_ClassLoader.lookupHiddenField(Name.HIDDEN_CLASS_LOADER_REGISTRY);

        // Guest reflection.
        java_lang_reflect_Executable = knownKlass(Type.java_lang_reflect_Executable);
        java_lang_reflect_Constructor = knownKlass(Type.java_lang_reflect_Constructor);
        HIDDEN_CONSTRUCTOR_KEY = java_lang_reflect_Constructor.lookupHiddenField(Name.HIDDEN_CONSTRUCTOR_KEY);
        HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Constructor.lookupHiddenField(Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Constructor_clazz = java_lang_reflect_Constructor.lookupDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Constructor_root = java_lang_reflect_Constructor.lookupDeclaredField(Name.root, Type.java_lang_reflect_Constructor);
        java_lang_reflect_Constructor_parameterTypes = java_lang_reflect_Constructor.lookupDeclaredField(Name.parameterTypes, Type.java_lang_Class_array);
        java_lang_reflect_Constructor_signature = java_lang_reflect_Constructor.lookupDeclaredField(Name.signature, Type.java_lang_String);
        sun_reflect_MagicAccessorImpl = knownKlass(Type.sun_reflect_MagicAccessorImpl);
        sun_reflect_DelegatingClassLoader = knownKlass(Type.sun_reflect_DelegatingClassLoader);

        java_lang_reflect_Method = knownKlass(Type.java_lang_reflect_Method);
        HIDDEN_METHOD_KEY = java_lang_reflect_Method.lookupHiddenField(Name.HIDDEN_METHOD_KEY);
        HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Method.lookupHiddenField(Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Method_root = java_lang_reflect_Method.lookupDeclaredField(Name.root, Type.java_lang_reflect_Method);
        java_lang_reflect_Method_clazz = java_lang_reflect_Method.lookupDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Method_override = java_lang_reflect_Method.lookupDeclaredField(Name.override, Type._boolean);
        java_lang_reflect_Method_parameterTypes = java_lang_reflect_Method.lookupDeclaredField(Name.parameterTypes, Type.java_lang_Class_array);

        sun_reflect_MethodAccessorImpl = knownKlass(Type.sun_reflect_MethodAccessorImpl);
        sun_reflect_ConstructorAccessorImpl = knownKlass(Type.sun_reflect_ConstructorAccessorImpl);

        java_lang_reflect_Parameter = knownKlass(Type.java_lang_reflect_Parameter);

        java_lang_reflect_Field = knownKlass(Type.java_lang_reflect_Field);
        HIDDEN_FIELD_KEY = java_lang_reflect_Field.lookupHiddenField(Name.HIDDEN_FIELD_KEY);
        HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Field.lookupHiddenField(Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Field_root = java_lang_reflect_Field.lookupDeclaredField(Name.root, java_lang_reflect_Field.getType());
        java_lang_reflect_Field_class = java_lang_reflect_Field.lookupDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Field_name = java_lang_reflect_Field.lookupDeclaredField(Name.name, Type.java_lang_String);
        java_lang_reflect_Field_type = java_lang_reflect_Field.lookupDeclaredField(Name.type, Type.java_lang_Class);

        java_lang_reflect_Shutdown = knownKlass(Type.java_lang_Shutdown);
        java_lang_reflect_Shutdown_shutdown = java_lang_reflect_Shutdown.lookupDeclaredMethod(Name.shutdown, Signature._void);

        java_nio_Buffer = knownKlass(Type.java_nio_Buffer);
        sun_nio_ch_DirectBuffer = knownKlass(Type.sun_nio_ch_DirectBuffer);
        java_nio_Buffer_address = java_nio_Buffer.lookupDeclaredField(Name.address, Type._long);
        java_nio_Buffer_capacity = java_nio_Buffer.lookupDeclaredField(Name.capacity, Type._int);

        java_nio_ByteBuffer = knownKlass(Type.java_nio_ByteBuffer);
        java_nio_ByteBuffer_wrap = java_nio_ByteBuffer.lookupDeclaredMethod(Name.wrap, Signature.ByteBuffer_byte_array);
        java_nio_DirectByteBuffer = knownKlass(Type.java_nio_DirectByteBuffer);
        java_nio_DirectByteBuffer_init_long_int = java_nio_DirectByteBuffer.lookupDeclaredMethod(Name._init_, Signature._void_long_int);

        java_lang_Thread = knownKlass(Type.java_lang_Thread);
        HIDDEN_HOST_THREAD = java_lang_Thread.lookupHiddenField(Name.HIDDEN_HOST_THREAD);
        HIDDEN_IS_ALIVE = java_lang_Thread.lookupHiddenField(Name.HIDDEN_IS_ALIVE);
        HIDDEN_INTERRUPTED = java_lang_Thread.lookupHiddenField(Name.HIDDEN_INTERRUPTED);
        HIDDEN_DEATH = java_lang_Thread.lookupHiddenField(Name.HIDDEN_DEATH);
        HIDDEN_DEATH_THROWABLE = java_lang_Thread.lookupHiddenField(Name.HIDDEN_DEATH_THROWABLE);
        HIDDEN_SUSPEND_LOCK = java_lang_Thread.lookupHiddenField(Name.HIDDEN_SUSPEND_LOCK);

        if (context.EnableManagement) {
            HIDDEN_THREAD_BLOCKED_OBJECT = java_lang_Thread.lookupHiddenField(Name.HIDDEN_THREAD_BLOCKED_OBJECT);
            HIDDEN_THREAD_BLOCKED_COUNT = java_lang_Thread.lookupHiddenField(Name.HIDDEN_THREAD_BLOCKED_COUNT);
            HIDDEN_THREAD_WAITED_COUNT = java_lang_Thread.lookupHiddenField(Name.HIDDEN_THREAD_WAITED_COUNT);
        } else {
            HIDDEN_THREAD_BLOCKED_OBJECT = null;
            HIDDEN_THREAD_BLOCKED_COUNT = null;
            HIDDEN_THREAD_WAITED_COUNT = null;
        }

        java_lang_ThreadGroup = knownKlass(Type.java_lang_ThreadGroup);
        java_lang_ThreadGroup_remove = java_lang_ThreadGroup.lookupDeclaredMethod(Name.remove, Signature._void_ThreadGroup);
        java_lang_Thread_dispatchUncaughtException = java_lang_Thread.lookupDeclaredMethod(Name.dispatchUncaughtException, Signature._void_Throwable);
        java_lang_Thread_exit = java_lang_Thread.lookupDeclaredMethod(Name.exit, Signature._void);
        java_lang_Thread_run = java_lang_Thread.lookupDeclaredMethod(Name.run, Signature._void);
        java_lang_Thread_threadStatus = java_lang_Thread.lookupDeclaredField(Name.threadStatus, Type._int);
        java_lang_Thread_tid = java_lang_Thread.lookupDeclaredField(Name.tid, Type._long);

        java_lang_Thread_group = java_lang_Thread.lookupDeclaredField(Name.group, java_lang_ThreadGroup.getType());
        java_lang_Thread_name = java_lang_Thread.lookupDeclaredField(Name.name, java_lang_String.getType());
        java_lang_Thread_priority = java_lang_Thread.lookupDeclaredField(Name.priority, _int.getType());
        java_lang_Thread_blockerLock = java_lang_Thread.lookupDeclaredField(Name.blockerLock, java_lang_Object.getType());
        java_lang_Thread_daemon = java_lang_Thread.lookupDeclaredField(Name.daemon, Type._boolean);
        java_lang_Thread_inheritedAccessControlContext = java_lang_Thread.lookupDeclaredField(Name.inheritedAccessControlContext, Type.java_security_AccessControlContext);
        java_lang_Thread_checkAccess = java_lang_Thread.lookupDeclaredMethod(Name.checkAccess, Signature._void);
        java_lang_Thread_stop = java_lang_Thread.lookupDeclaredMethod(Name.stop, Signature._void);
        java_lang_ThreadGroup_maxPriority = java_lang_ThreadGroup.lookupDeclaredField(Name.maxPriority, Type._int);

        java_lang_ref_Finalizer$FinalizerThread = knownKlass(Type.java_lang_ref_Finalizer$FinalizerThread);
        java_lang_ref_Reference$ReferenceHandler = knownKlass(Type.java_lang_ref_Reference$ReferenceHandler);

        sun_misc_VM = knownKlass(Type.sun_misc_VM);
        sun_misc_VM_toThreadState = sun_misc_VM.lookupDeclaredMethod(Name.toThreadState, Signature.Thread$State_int);

        sun_reflect_ConstantPool = knownKlass(Type.sun_reflect_ConstantPool);
        sun_reflect_ConstantPool_constantPoolOop = sun_reflect_ConstantPool.lookupDeclaredField(Name.constantPoolOop, Type.java_lang_Object);

        java_lang_System = knownKlass(Type.java_lang_System);
        java_lang_System_initializeSystemClass = java_lang_System.lookupDeclaredMethod(Name.initializeSystemClass, Signature._void);
        java_lang_System_exit = java_lang_System.lookupDeclaredMethod(Name.exit, Signature._void_int);
        java_lang_System_securityManager = java_lang_System.lookupDeclaredField(Name.security, Type.java_lang_SecurityManager);

        java_security_ProtectionDomain = knownKlass(Type.java_security_ProtectionDomain);
        java_security_ProtectionDomain_impliesCreateAccessControlContext = java_security_ProtectionDomain.lookupDeclaredMethod(Name.impliesCreateAccessControlContext, Signature._boolean);
        java_security_ProtectionDomain_init_CodeSource_PermissionCollection = java_security_ProtectionDomain.lookupDeclaredMethod(Name._init_, Signature._void_CodeSource_PermissionCollection);

        java_security_AccessControlContext = knownKlass(Type.java_security_AccessControlContext);
        java_security_AccessControlContext_context = java_security_AccessControlContext.lookupDeclaredField(Name.context, Type.java_security_ProtectionDomain_array);
        java_security_AccessControlContext_privilegedContext = java_security_AccessControlContext.lookupDeclaredField(Name.privilegedContext, Type.java_security_AccessControlContext);
        java_security_AccessControlContext_isPrivileged = java_security_AccessControlContext.lookupDeclaredField(Name.isPrivileged, Type._boolean);
        java_security_AccessControlContext_isAuthorized = java_security_AccessControlContext.lookupDeclaredField(Name.isAuthorized, Type._boolean);
        java_security_AccessController = knownKlass(Type.java_security_AccessController);

        java_lang_invoke_MethodType = knownKlass(Type.java_lang_invoke_MethodType);
        java_lang_invoke_MethodType_toMethodDescriptorString = java_lang_invoke_MethodType.lookupDeclaredMethod(Name.toMethodDescriptorString, Signature.String);
        java_lang_invoke_MethodType_fromMethodDescriptorString = java_lang_invoke_MethodType.lookupDeclaredMethod(Name.fromMethodDescriptorString, Signature.MethodType_String_ClassLoader);

        java_lang_invoke_MemberName = knownKlass(Type.java_lang_invoke_MemberName);
        HIDDEN_VMINDEX = java_lang_invoke_MemberName.lookupHiddenField(Name.HIDDEN_VMINDEX);
        HIDDEN_VMTARGET = java_lang_invoke_MemberName.lookupHiddenField(Name.HIDDEN_VMTARGET);
        java_lang_invoke_MemberName_getSignature = java_lang_invoke_MemberName.lookupDeclaredMethod(Name.getSignature, Signature.String);
        java_lang_invoke_MemberName_clazz = java_lang_invoke_MemberName.lookupDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_invoke_MemberName_name = java_lang_invoke_MemberName.lookupDeclaredField(Name.name, Type.java_lang_String);
        java_lang_invoke_MemberName_type = java_lang_invoke_MemberName.lookupDeclaredField(Name.type, Type.java_lang_invoke_MethodType);
        java_lang_invoke_MemberName_flags = java_lang_invoke_MemberName.lookupDeclaredField(Name.flags, Type._int);

        java_lang_invoke_MethodHandle = knownKlass(Type.java_lang_invoke_MethodHandle);
        java_lang_invoke_MethodHandle_invokeExact = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.invokeExact, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invoke = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.invoke, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeBasic = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.invokeBasic, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeWithArguments = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.invokeWithArguments, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToInterface = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.linkToInterface, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToSpecial = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.linkToSpecial, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToStatic = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.linkToStatic, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToVirtual = java_lang_invoke_MethodHandle.lookupDeclaredMethod(Name.linkToVirtual, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_type = java_lang_invoke_MethodHandle.lookupDeclaredField(Name.type, Type.java_lang_invoke_MethodType);
        java_lang_invoke_MethodHandle_form = java_lang_invoke_MethodHandle.lookupDeclaredField(Name.form, Type.java_lang_invoke_LambdaForm);

        java_lang_invoke_MethodHandles = knownKlass(Type.java_lang_invoke_MethodHandles);
        java_lang_invoke_MethodHandles_lookup = java_lang_invoke_MethodHandles.lookupDeclaredMethod(Name.lookup, Signature.MethodHandles$Lookup);

        java_lang_invoke_CallSite = knownKlass(Type.java_lang_invoke_CallSite);
        java_lang_invoke_CallSite_target = java_lang_invoke_CallSite.lookupDeclaredField(Name.target, Type.java_lang_invoke_MethodHandle);

        java_lang_invoke_LambdaForm = knownKlass(Type.java_lang_invoke_LambdaForm);
        java_lang_invoke_LambdaForm_vmentry = java_lang_invoke_LambdaForm.lookupDeclaredField(Name.vmentry, Type.java_lang_invoke_MemberName);
        java_lang_invoke_LambdaForm_isCompiled = java_lang_invoke_LambdaForm.lookupDeclaredField(Name.isCompiled, Type._boolean);
        java_lang_invoke_LambdaForm_compileToBytecode = java_lang_invoke_LambdaForm.lookupDeclaredMethod(Name.compileToBytecode, Signature.MemberName);

        java_lang_invoke_MethodHandleNatives = knownKlass(Type.java_lang_invoke_MethodHandleNatives);
        java_lang_invoke_MethodHandleNatives_linkMethod = java_lang_invoke_MethodHandleNatives.lookupDeclaredMethod(Name.linkMethod, Signature.MemberName_Class_int_Class_String_Object_Object_array);
        java_lang_invoke_MethodHandleNatives_linkCallSite = java_lang_invoke_MethodHandleNatives.lookupDeclaredMethod(Name.linkCallSite,
                        Signature.MemberName_Object_Object_Object_Object_Object_Object_array);
        java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant = java_lang_invoke_MethodHandleNatives.lookupDeclaredMethod(Name.linkMethodHandleConstant,
                        Signature.MethodHandle_Class_int_Class_String_Object);
        java_lang_invoke_MethodHandleNatives_findMethodHandleType = java_lang_invoke_MethodHandleNatives.lookupDeclaredMethod(Name.findMethodHandleType, Signature.MethodType_Class_Class);

        java_lang_ref_Finalizer = knownKlass(Type.java_lang_ref_Finalizer);
        java_lang_ref_Finalizer_register = java_lang_ref_Finalizer.lookupDeclaredMethod(Name.register, Signature._void_Object);

        java_lang_Object_wait = java_lang_Object.lookupDeclaredMethod(Name.wait, Signature._void_long);
        java_lang_Object_toString = java_lang_Object.lookupDeclaredMethod(Name.toString, Signature._void);

        // References
        java_lang_ref_Reference = knownKlass(Type.java_lang_ref_Reference);
        java_lang_ref_Reference_referent = java_lang_ref_Reference.lookupDeclaredField(Name.referent, Type.java_lang_Object);

        java_lang_ref_Reference_discovered = java_lang_ref_Reference.lookupDeclaredField(Name.discovered, Type.java_lang_ref_Reference);
        java_lang_ref_Reference_pending = java_lang_ref_Reference.lookupDeclaredField(Name.pending, Type.java_lang_ref_Reference);
        java_lang_ref_Reference_next = java_lang_ref_Reference.lookupDeclaredField(Name.next, Type.java_lang_ref_Reference);
        java_lang_ref_Reference_queue = java_lang_ref_Reference.lookupDeclaredField(Name.queue, Type.java_lang_ref_ReferenceQueue);
        java_lang_ref_Reference_lock = java_lang_ref_Reference.lookupDeclaredField(Name.lock, Type.java_lang_ref_Reference$Lock);
        java_lang_ref_ReferenceQueue = knownKlass(Type.java_lang_ref_ReferenceQueue);
        java_lang_ref_ReferenceQueue_NULL = java_lang_ref_ReferenceQueue.lookupDeclaredField(Name.NULL, Type.java_lang_ref_ReferenceQueue);

        java_lang_ref_WeakReference = knownKlass(Type.java_lang_ref_WeakReference);
        java_lang_ref_SoftReference = knownKlass(Type.java_lang_ref_SoftReference);
        java_lang_ref_PhantomReference = knownKlass(Type.java_lang_ref_PhantomReference);
        java_lang_ref_FinalReference = knownKlass(Type.java_lang_ref_FinalReference);
        sun_misc_Cleaner = knownKlass(Type.sun_misc_Cleaner);
        HIDDEN_HOST_REFERENCE = java_lang_ref_Reference.lookupHiddenField(Name.HIDDEN_HOST_REFERENCE);

        java_lang_AssertionStatusDirectives = knownKlass(Type.java_lang_AssertionStatusDirectives);
        java_lang_AssertionStatusDirectives_classes = java_lang_AssertionStatusDirectives.lookupField(Name.classes, Type.java_lang_String_array);
        java_lang_AssertionStatusDirectives_classEnabled = java_lang_AssertionStatusDirectives.lookupField(Name.classEnabled, Type._boolean_array);
        java_lang_AssertionStatusDirectives_packages = java_lang_AssertionStatusDirectives.lookupField(Name.packages, Type.java_lang_String_array);
        java_lang_AssertionStatusDirectives_packageEnabled = java_lang_AssertionStatusDirectives.lookupField(Name.packageEnabled, Type._boolean_array);
        java_lang_AssertionStatusDirectives_deflt = java_lang_AssertionStatusDirectives.lookupField(Name.deflt, Type._boolean);

        sun_reflect_Reflection_getCallerClass = knownKlass(Type.sun_reflect_Reflection).lookupDeclaredMethod(Name.getCallerClass, Signature.Class);

        // java.management
        java_lang_management_MemoryUsage = knownKlass(Type.java_lang_management_MemoryUsage);
        sun_management_ManagementFactory = knownKlass(Type.sun_management_ManagementFactory);
        // MemoryPoolMXBean createMemoryPool(String var0, boolean var1, long var2, long var4)
        sun_management_ManagementFactory_createMemoryPool = sun_management_ManagementFactory.lookupDeclaredMethod(Name.createMemoryPool, Signature.MemoryPoolMXBean_String_boolean_long_long);
        // MemoryManagerMXBean createMemoryManager(String var0)
        sun_management_ManagementFactory_createMemoryManager = sun_management_ManagementFactory.lookupDeclaredMethod(Name.createMemoryManager, Signature.MemoryManagerMXBean_String);
        // GarbageCollectorMXBean createGarbageCollector(String var0, String var1)
        sun_management_ManagementFactory_createGarbageCollector = sun_management_ManagementFactory.lookupDeclaredMethod(Name.createGarbageCollector, Signature.GarbageCollectorMXBean_String_String);

        java_lang_management_ThreadInfo = knownKlass(Type.java_lang_management_ThreadInfo);

        this.dispatch = new ExceptionDispatch(this);
    }

    // Checkstyle: stop field name check

    public final ObjectKlass java_lang_Object;
    public final ArrayKlass java_lang_Object_array;

    public final ObjectKlass java_lang_String;
    public final ObjectKlass java_lang_Class;
    public final Field HIDDEN_MIRROR_KLASS;
    public final Field HIDDEN_PROTECTION_DOMAIN;
    public final Field HIDDEN_SIGNERS;
    public final Field sun_reflect_ConstantPool_constantPoolOop;
    public final ArrayKlass java_lang_Class_array;
    public final Method java_lang_Class_forName_String;
    public final Method java_lang_Class_forName_String_boolean_ClassLoader;

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
    public final ObjectKlass java_lang_Boolean;
    public final ObjectKlass java_lang_Byte;
    public final ObjectKlass java_lang_Character;
    public final ObjectKlass java_lang_Short;
    public final ObjectKlass java_lang_Integer;
    public final ObjectKlass java_lang_Float;
    public final ObjectKlass java_lang_Double;
    public final ObjectKlass java_lang_Long;
    public final ObjectKlass java_lang_Void;

    // Boxing conversions.
    public final Method java_lang_Boolean_valueOf;
    public final Method java_lang_Byte_valueOf;
    public final Method java_lang_Character_valueOf;
    public final Method java_lang_Short_valueOf;
    public final Method java_lang_Float_valueOf;
    public final Method java_lang_Integer_valueOf;
    public final Method java_lang_Double_valueOf;
    public final Method java_lang_Long_valueOf;

    public final Field java_lang_Boolean_value;
    public final Field java_lang_Byte_value;
    public final Field java_lang_Character_value;
    public final Field java_lang_Short_value;
    public final Field java_lang_Float_value;
    public final Field java_lang_Integer_value;
    public final Field java_lang_Double_value;
    public final Field java_lang_Long_value;

    // Guest String.
    public final Field java_lang_String_value;
    public final Field java_lang_String_hash;
    public final Method java_lang_String_hashCode;
    public final Method java_lang_String_length;

    public final ObjectKlass java_lang_ClassLoader;
    public final Field java_lang_ClassLoader_parent;
    public final ObjectKlass java_lang_ClassLoader$NativeLibrary;
    public final Method java_lang_ClassLoader$NativeLibrary_getFromClass;
    public final Method java_lang_ClassLoader_findNative;
    public final Method java_lang_ClassLoader_getSystemClassLoader;
    public final Field HIDDEN_CLASS_LOADER_REGISTRY;

    public final ObjectKlass java_lang_AssertionStatusDirectives;
    public final Field java_lang_AssertionStatusDirectives_classes;
    public final Field java_lang_AssertionStatusDirectives_classEnabled;
    public final Field java_lang_AssertionStatusDirectives_packages;
    public final Field java_lang_AssertionStatusDirectives_packageEnabled;
    public final Field java_lang_AssertionStatusDirectives_deflt;

    public final ObjectKlass java_lang_reflect_Executable;

    public final ObjectKlass java_lang_reflect_Constructor;
    public final Field HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_CONSTRUCTOR_KEY;
    public final Field java_lang_reflect_Constructor_clazz;
    public final Field java_lang_reflect_Constructor_root;
    public final Field java_lang_reflect_Constructor_parameterTypes;
    public final Field java_lang_reflect_Constructor_signature;

    public final ObjectKlass sun_reflect_MagicAccessorImpl;
    public final ObjectKlass sun_reflect_DelegatingClassLoader;

    public final ObjectKlass java_lang_reflect_Method;
    public final Field HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_METHOD_KEY;
    public final Field java_lang_reflect_Method_root;
    public final Field java_lang_reflect_Method_clazz;
    public final Field java_lang_reflect_Method_override;
    public final Field java_lang_reflect_Method_parameterTypes;

    public final ObjectKlass sun_reflect_MethodAccessorImpl;
    public final ObjectKlass sun_reflect_ConstructorAccessorImpl;

    public final ObjectKlass java_lang_reflect_Parameter;

    public final ObjectKlass java_lang_reflect_Field;
    public final Field HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_FIELD_KEY;
    public final Field java_lang_reflect_Field_root;
    public final Field java_lang_reflect_Field_class;
    public final Field java_lang_reflect_Field_name;
    public final Field java_lang_reflect_Field_type;

    public final ObjectKlass java_lang_reflect_Shutdown;
    public final Method java_lang_reflect_Shutdown_shutdown;

    public final ObjectKlass java_lang_Exception;
    public final ObjectKlass java_lang_reflect_InvocationTargetException;
    public final ObjectKlass java_lang_NegativeArraySizeException;
    public final ObjectKlass java_lang_IllegalArgumentException;
    public final ObjectKlass java_lang_IllegalMonitorStateException;
    public final ObjectKlass java_lang_NullPointerException;
    public final ObjectKlass java_lang_ClassNotFoundException;
    public final ObjectKlass java_lang_NoClassDefFoundError;
    public final ObjectKlass java_lang_InterruptedException;
    public final ObjectKlass java_lang_ThreadDeath;
    public final ObjectKlass java_lang_RuntimeException;
    public final ObjectKlass java_lang_StackOverflowError;
    public final ObjectKlass java_lang_OutOfMemoryError;
    public final ObjectKlass java_lang_ClassCastException;
    public final ObjectKlass java_lang_AbstractMethodError;
    public final ObjectKlass java_lang_InternalError;
    public final ObjectKlass java_lang_VerifyError;
    public final ObjectKlass java_lang_ClassFormatError;
    public final ObjectKlass java_lang_ClassCircularityError;
    public final ObjectKlass java_lang_UnsatisfiedLinkError;
    public final ObjectKlass java_lang_UnsupportedClassVersionError;
    public final ObjectKlass java_lang_ArrayStoreException;
    public final ObjectKlass java_lang_IndexOutOfBoundsException;
    public final ObjectKlass java_lang_ArrayIndexOutOfBoundsException;
    public final ObjectKlass java_lang_StringIndexOutOfBoundsException;
    public final ObjectKlass java_lang_ExceptionInInitializerError;
    public final ObjectKlass java_lang_InstantiationException;
    public final ObjectKlass java_lang_InstantiationError;
    public final ObjectKlass java_lang_CloneNotSupportedException;
    public final ObjectKlass java_lang_SecurityException;
    public final ObjectKlass java_lang_ArithmeticException;
    public final ObjectKlass java_lang_LinkageError;
    public final ObjectKlass java_lang_NoSuchFieldException;
    public final ObjectKlass java_lang_NoSuchMethodException;
    public final ObjectKlass java_lang_UnsupportedOperationException;

    public final ObjectKlass java_lang_Throwable;
    public final Method java_lang_Throwable_getStackTrace;
    public final Field HIDDEN_FRAMES;
    public final Field java_lang_Throwable_backtrace;
    public final Field java_lang_Throwable_detailMessage;
    public final Field java_lang_Throwable_cause;

    public final ObjectKlass java_lang_Error;
    public final ObjectKlass java_lang_NoSuchFieldError;
    public final ObjectKlass java_lang_NoSuchMethodError;
    public final ObjectKlass java_lang_IllegalAccessError;
    public final ObjectKlass java_lang_IncompatibleClassChangeError;
    public final ObjectKlass java_lang_BootstrapMethodError;

    public final ObjectKlass java_lang_StackTraceElement;
    public final Method java_lang_StackTraceElement_init;

    public final ObjectKlass java_security_PrivilegedActionException;
    public final Method java_security_PrivilegedActionException_init_Exception;

    // Array support.
    public final ObjectKlass java_lang_Cloneable;
    public final ObjectKlass java_io_Serializable;

    public final ObjectKlass sun_nio_ch_DirectBuffer;
    public final ObjectKlass java_nio_Buffer;
    public final Field java_nio_Buffer_address;
    public final Field java_nio_Buffer_capacity;

    public final ObjectKlass java_nio_ByteBuffer;
    public final Method java_nio_ByteBuffer_wrap;
    public final ObjectKlass java_nio_DirectByteBuffer;
    public final Method java_nio_DirectByteBuffer_init_long_int;

    public final ObjectKlass java_lang_ThreadGroup;
    public final Method java_lang_ThreadGroup_remove;
    public final Method java_lang_Thread_dispatchUncaughtException;
    public final Field java_lang_ThreadGroup_maxPriority;
    public final ObjectKlass java_lang_Thread;
    public final Field java_lang_Thread_threadStatus;
    public final Field java_lang_Thread_tid;
    public final Method java_lang_Thread_exit;
    public final Method java_lang_Thread_run;
    public final Method java_lang_Thread_checkAccess;
    public final Method java_lang_Thread_stop;
    public final Field HIDDEN_HOST_THREAD;
    public final Field HIDDEN_IS_ALIVE;
    public final Field HIDDEN_INTERRUPTED;
    public final Field HIDDEN_DEATH;
    public final Field HIDDEN_DEATH_THROWABLE;
    public final Field HIDDEN_SUSPEND_LOCK;
    public final Field HIDDEN_THREAD_BLOCKED_OBJECT;
    public final Field HIDDEN_THREAD_BLOCKED_COUNT;
    public final Field HIDDEN_THREAD_WAITED_COUNT;

    public final Field java_lang_Thread_group;
    public final Field java_lang_Thread_name;
    public final Field java_lang_Thread_priority;
    public final Field java_lang_Thread_blockerLock;
    public final Field java_lang_Thread_daemon;
    public final Field java_lang_Thread_inheritedAccessControlContext;

    public final ObjectKlass java_lang_ref_Finalizer$FinalizerThread;
    public final ObjectKlass java_lang_ref_Reference$ReferenceHandler;

    public final ObjectKlass sun_misc_VM;
    public final Method sun_misc_VM_toThreadState;
    public final ObjectKlass sun_reflect_ConstantPool;

    public final ObjectKlass java_lang_System;
    public final Method java_lang_System_initializeSystemClass;
    public final Method java_lang_System_exit;
    public final Field java_lang_System_securityManager;

    public final ObjectKlass java_security_ProtectionDomain;
    public final Method java_security_ProtectionDomain_impliesCreateAccessControlContext;
    public final Method java_security_ProtectionDomain_init_CodeSource_PermissionCollection;

    public final ObjectKlass java_security_AccessControlContext;
    public final Field java_security_AccessControlContext_context;
    public final Field java_security_AccessControlContext_privilegedContext;
    public final Field java_security_AccessControlContext_isPrivileged;
    public final Field java_security_AccessControlContext_isAuthorized;
    public final ObjectKlass java_security_AccessController;

    public final ObjectKlass java_lang_invoke_MethodType;
    public final Method java_lang_invoke_MethodType_toMethodDescriptorString;
    public final Method java_lang_invoke_MethodType_fromMethodDescriptorString;

    public final ObjectKlass java_lang_invoke_MemberName;
    public final Method java_lang_invoke_MemberName_getSignature;

    public final Field HIDDEN_VMTARGET;
    public final Field HIDDEN_VMINDEX;
    public final Field java_lang_invoke_MemberName_clazz;
    public final Field java_lang_invoke_MemberName_name;
    public final Field java_lang_invoke_MemberName_type;
    public final Field java_lang_invoke_MemberName_flags;

    public final ObjectKlass java_lang_invoke_MethodHandle;
    public final Method java_lang_invoke_MethodHandle_invoke;
    public final Method java_lang_invoke_MethodHandle_invokeExact;
    public final Method java_lang_invoke_MethodHandle_invokeBasic;
    public final Method java_lang_invoke_MethodHandle_invokeWithArguments;
    public final Method java_lang_invoke_MethodHandle_linkToInterface;
    public final Method java_lang_invoke_MethodHandle_linkToSpecial;
    public final Method java_lang_invoke_MethodHandle_linkToStatic;
    public final Method java_lang_invoke_MethodHandle_linkToVirtual;
    public final Field java_lang_invoke_MethodHandle_type;
    public final Field java_lang_invoke_MethodHandle_form;

    public final ObjectKlass java_lang_invoke_MethodHandles;
    public final Method java_lang_invoke_MethodHandles_lookup;

    public final ObjectKlass java_lang_invoke_CallSite;
    public final Field java_lang_invoke_CallSite_target;

    public final ObjectKlass java_lang_invoke_LambdaForm;
    public final Field java_lang_invoke_LambdaForm_vmentry;
    public final Field java_lang_invoke_LambdaForm_isCompiled;
    public final Method java_lang_invoke_LambdaForm_compileToBytecode;

    public final ObjectKlass java_lang_invoke_MethodHandleNatives;
    public final Method java_lang_invoke_MethodHandleNatives_linkMethod;
    public final Method java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant;
    public final Method java_lang_invoke_MethodHandleNatives_findMethodHandleType;
    public final Method java_lang_invoke_MethodHandleNatives_linkCallSite;

    public final Method java_lang_Object_wait;
    public final Method java_lang_Object_toString;

    // References
    public final ObjectKlass java_lang_ref_Finalizer;
    public final Method java_lang_ref_Finalizer_register;
    public final ObjectKlass java_lang_ref_Reference;
    public final Field java_lang_ref_Reference_referent;
    public final Field java_lang_ref_Reference_discovered;
    public final Field java_lang_ref_Reference_pending;
    public final Field java_lang_ref_Reference_next;
    public final Field java_lang_ref_Reference_queue;
    public final Field java_lang_ref_Reference_lock;
    public final ObjectKlass java_lang_ref_WeakReference;
    public final ObjectKlass java_lang_ref_SoftReference;
    public final ObjectKlass java_lang_ref_PhantomReference;
    public final ObjectKlass java_lang_ref_FinalReference;
    public final ObjectKlass sun_misc_Cleaner;

    public final Field HIDDEN_HOST_REFERENCE;

    public final ObjectKlass java_lang_ref_ReferenceQueue;
    public final Field java_lang_ref_ReferenceQueue_NULL;
    public final Method sun_reflect_Reflection_getCallerClass;

    public final ObjectKlass java_lang_management_MemoryUsage;
    public final ObjectKlass sun_management_ManagementFactory;
    public final Method sun_management_ManagementFactory_createMemoryPool;
    public final Method sun_management_ManagementFactory_createMemoryManager;
    public final Method sun_management_ManagementFactory_createGarbageCollector;
    public final ObjectKlass java_lang_management_ThreadInfo;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] ARRAY_SUPERINTERFACES;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] BOXED_PRIMITIVE_KLASSES;

    // Checkstyle: resume field name check

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

    /**
     * Allocate and initializes an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public @Host(Throwable.class) static StaticObject initExceptionWithMessage(@Host(Throwable.class) ObjectKlass exceptionKlass, @Host(String.class) StaticObject message) {
        assert exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(exceptionKlass);
        assert StaticObject.isNull(message) || exceptionKlass.getMeta().java_lang_String.isAssignableFrom(message.getKlass());
        return exceptionKlass.getMeta().dispatch.initEx(exceptionKlass, message, null);
    }

    /**
     * Allocate and initializes an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public @Host(Throwable.class) static StaticObject initExceptionWithMessage(@Host(Throwable.class) ObjectKlass exceptionKlass, String message) {
        return initExceptionWithMessage(exceptionKlass, exceptionKlass.getMeta().toGuestString(message));
    }

    /**
     * Allocate and initializes an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the {@link Throwable#Throwable()
     * default constructor}. The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public @Host(Throwable.class) static StaticObject initException(@Host(Throwable.class) ObjectKlass exceptionKlass) {
        assert exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(exceptionKlass);
        return exceptionKlass.getMeta().dispatch.initEx(exceptionKlass, null, null);
    }

    /**
     * Allocate and initializes an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(Throwable) constructor with cause}. The given guest class must
     * have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public @Host(Throwable.class) static StaticObject initExceptionWithCause(@Host(Throwable.class) ObjectKlass exceptionKlass, @Host(Throwable.class) StaticObject cause) {
        assert exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(exceptionKlass);
        assert StaticObject.isNull(cause) || exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(cause.getKlass());
        return exceptionKlass.getMeta().dispatch.initEx(exceptionKlass, null, cause);
    }

    /**
     * Initializes and throws an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the {@link Throwable#Throwable()
     * default constructor}. The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public static EspressoException throwException(@Host(Throwable.class) ObjectKlass exceptionKlass) {
        throw throwException(initException(exceptionKlass));
    }

    /**
     * Throws the given guest exception, wrapped in {@link EspressoException}.
     *
     * <p>
     * The given instance must be a non-{@link StaticObject#NULL NULL}, guest
     * {@link #java_lang_Throwable Throwable}.
     */
    public static EspressoException throwException(@Host(Throwable.class) StaticObject throwable) {
        assert StaticObject.notNull(throwable);
        assert InterpreterToVM.instanceOf(throwable, throwable.getKlass().getMeta().java_lang_Throwable);
        throw EspressoException.wrap(throwable);
    }

    /**
     * Initializes and throws an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public static EspressoException throwExceptionWithMessage(@Host(Throwable.class) ObjectKlass exceptionKlass, @Host(String.class) StaticObject message) {
        throw throwException(initExceptionWithMessage(exceptionKlass, message));
    }

    /**
     * Initializes and throws an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public static EspressoException throwExceptionWithMessage(@Host(Throwable.class) ObjectKlass exceptionKlass, String message) {
        throw throwExceptionWithMessage(exceptionKlass, exceptionKlass.getMeta().toGuestString(message));
    }

    /**
     * Initializes and throws an exception of the given guest klass. A guest instance is allocated
     * and initialized by calling the {@link Throwable#Throwable(Throwable) constructor with cause}.
     * The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public static EspressoException throwExceptionWithCause(@Host(Throwable.class) ObjectKlass exceptionKlass, @Host(Throwable.class) StaticObject cause) {
        throw throwException(initExceptionWithCause(exceptionKlass, cause));
    }

    /**
     * Throws a guest {@link NullPointerException}. A guest instance is allocated and initialized by
     * calling the {@link NullPointerException#NullPointerException() default constructor}.
     */
    public EspressoException throwNullPointerException() {
        throw throwException(java_lang_NullPointerException);
    }

    // endregion Guest exception handling (throw)

    private ObjectKlass knownKlass(Symbol<Type> type) {
        CompilerAsserts.neverPartOfCompilation();
        assert !Types.isArray(type);
        assert !Types.isPrimitive(type);
        return (ObjectKlass) getRegistries().loadKlassWithBootClassLoader(type);
    }

    /**
     * Performs class loading according to {&sect;5.3. Creation and Loading}. This method directly
     * asks the given class loader to perform the load, even for internal primitive types. This is
     * the method to use when loading symbols that are not directly taken from a constant pool, for
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
        char[] value = ((StaticObject) meta.java_lang_String_value.get(str)).unwrap();
        return HostJava.createString(value);
    }

    @TruffleBoundary
    public StaticObject toGuestString(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        final char[] value = HostJava.getStringValue(hostString);
        final int hash = HostJava.getStringHash(hostString);
        StaticObject guestString = java_lang_String.allocateInstance();
        java_lang_String_value.set(guestString, StaticObject.wrap(value, this));
        java_lang_String_hash.set(guestString, hash);
        // String.hashCode must be equivalent for host and guest.
        assert hostString.hashCode() == (int) java_lang_String_hashCode.invokeDirect(guestString);
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
            return StaticObject.wrap((StaticObject[]) hostObject, this);
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
            if (guestObject.isArray()) {
                return guestObject.unwrap();
            }
            if (guestObject.getKlass() == java_lang_String) {
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

        static {
            try {
                String_value = String.class.getDeclaredField("value");
                String_value.setAccessible(true);
                String_hash = String.class.getDeclaredField("hash");
                String_hash.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static char[] getStringValue(String s) {
            char[] chars = new char[s.length()];
            s.getChars(0, s.length(), chars, 0);
            return chars;
        }

        private static int getStringHash(String s) {
            try {
                return (int) String_hash.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static String createString(final char[] value) {
            return new String(value);
        }
    }

    // endregion

    // region Guest Unboxing

    public Object unboxGuest(StaticObject boxed) {
        Klass klass = boxed.getKlass();
        if (klass == java_lang_Boolean) {
            return unboxBoolean(boxed);
        } else if (klass == java_lang_Byte) {
            return unboxByte(boxed);
        } else if (klass == java_lang_Character) {
            return unboxCharacter(boxed);
        } else if (klass == java_lang_Short) {
            return unboxShort(boxed);
        } else if (klass == java_lang_Float) {
            return unboxFloat(boxed);
        } else if (klass == java_lang_Integer) {
            return unboxInteger(boxed);
        } else if (klass == java_lang_Double) {
            return unboxDouble(boxed);
        } else if (klass == java_lang_Long) {
            return unboxLong(boxed);
        } else {
            return boxed;
        }
    }

    public boolean unboxBoolean(@Host(Boolean.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Boolean) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (boolean) java_lang_Boolean_value.get(boxed);
    }

    public byte unboxByte(@Host(Byte.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Byte) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (byte) java_lang_Byte_value.get(boxed);
    }

    public char unboxCharacter(@Host(Character.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Character) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (char) java_lang_Character_value.get(boxed);
    }

    public short unboxShort(@Host(Short.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Short) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (short) java_lang_Short_value.get(boxed);
    }

    public float unboxFloat(@Host(Float.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Float) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (float) java_lang_Float_value.get(boxed);
    }

    public int unboxInteger(@Host(Integer.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Integer) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (int) java_lang_Integer_value.get(boxed);
    }

    public double unboxDouble(@Host(Double.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Double) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (double) java_lang_Double_value.get(boxed);
    }

    public long unboxLong(@Host(Long.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Long) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (long) java_lang_Long_value.get(boxed);
    }

    // endregion Guest Unboxing

    // region Guest boxing

    public @Host(Boolean.class) StaticObject boxBoolean(boolean value) {
        return (StaticObject) java_lang_Boolean_valueOf.invokeDirect(null, value);
    }

    public @Host(Byte.class) StaticObject boxByte(byte value) {
        return (StaticObject) java_lang_Byte_valueOf.invokeDirect(null, value);
    }

    public @Host(Character.class) StaticObject boxCharacter(char value) {
        return (StaticObject) java_lang_Character_valueOf.invokeDirect(null, value);
    }

    public @Host(Short.class) StaticObject boxShort(short value) {
        return (StaticObject) java_lang_Short_valueOf.invokeDirect(null, value);
    }

    public @Host(Float.class) StaticObject boxFloat(float value) {
        return (StaticObject) java_lang_Float_valueOf.invokeDirect(null, value);
    }

    public @Host(Integer.class) StaticObject boxInteger(int value) {
        return (StaticObject) java_lang_Integer_valueOf.invokeDirect(null, value);
    }

    public @Host(Double.class) StaticObject boxDouble(double value) {
        return (StaticObject) java_lang_Double_valueOf.invokeDirect(null, value);
    }

    public @Host(Long.class) StaticObject boxLong(long value) {
        return (StaticObject) java_lang_Long_valueOf.invokeDirect(null, value);
    }

    // endregion Guest boxing
}
