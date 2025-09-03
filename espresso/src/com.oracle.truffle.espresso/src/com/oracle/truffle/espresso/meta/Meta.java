/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.EspressoOptions.SpecComplianceMode.HOTSPOT;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_16_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_17_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_19_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_20_OR_LOWER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_21_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_21_OR_LOWER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_22_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_22_TO_23;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_24_OR_LOWER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_25_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_26_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_8_OR_LOWER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_9_OR_HIGHER;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_9_TO_21;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.VERSION_9_TO_23;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.between;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.higher;
import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.lower;
import static com.oracle.truffle.espresso.impl.EspressoClassLoadingException.wrapClassNotFoundGuestException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.EspressoOptions.SpecComplianceMode;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.KnownTypes;
import com.oracle.truffle.espresso.substitutions.JImageExtensions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta extends ContextAccessImpl
                implements KnownTypes<Klass, Method, Field> {

    private final ExceptionDispatch dispatch;
    private final StringConversion stringConversion;
    private final InteropKlassesDispatch interopDispatch;
    private StaticObject cachedPlatformClassLoader;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Meta(EspressoContext context) {
        super(context);
        CompilerAsserts.neverPartOfCompilation();
        this.stringConversion = StringConversion.select(context);

        // Give access to the partially-built Meta instance.
        context.setBootstrapMeta(this);

        // Core types.
        // Object and Class (+ Class fields) must be initialized before all other classes in order
        // to eagerly create the guest Class instances.
        java_lang_Object = knownKlass(Types.java_lang_Object);
        HIDDEN_SYSTEM_IHASHCODE = context.getLanguage().isContinuumEnabled() ? java_lang_Object.requireHiddenField(Names.HIDDEN_SYSTEM_IHASHCODE) : null;
        // Cloneable must be loaded before Serializable.
        java_lang_Cloneable = knownKlass(Types.java_lang_Cloneable);
        java_lang_Class = knownKlass(Types.java_lang_Class);
        java_lang_Class_classRedefinedCount = java_lang_Class.requireDeclaredField(Names.classRedefinedCount, Types._int);
        java_lang_Class_name = java_lang_Class.requireDeclaredField(Names.name, Types.java_lang_String);
        java_lang_Class_classLoader = java_lang_Class.requireDeclaredField(Names.classLoader, Types.java_lang_ClassLoader);
        java_lang_Class_modifiers = diff() //
                        .field(VERSION_25_OR_HIGHER, Names.modifiers, Types._char) //
                        .notRequiredField(java_lang_Class);
        java_lang_Class_classFileAccessFlags = diff() //
                        .field(VERSION_26_OR_HIGHER, Names.classFileAccessFlags, Types._char) //
                        .notRequiredField(java_lang_Class);
        java_lang_Class_primitive = diff() //
                        .field(VERSION_25_OR_HIGHER, Names.primitive, Types._boolean) //
                        .notRequiredField(java_lang_Class);
        java_lang_Class_componentType = diff() //
                        .field(VERSION_9_OR_HIGHER, Names.componentType, Types.java_lang_Class)//
                        .notRequiredField(java_lang_Class);
        java_lang_Class_classData = diff() //
                        .field(higher(15), Names.classData, Types.java_lang_Object)//
                        .notRequiredField(java_lang_Class);
        HIDDEN_MIRROR_KLASS = java_lang_Class.requireHiddenField(Names.HIDDEN_MIRROR_KLASS);
        HIDDEN_SIGNERS = diff() //
                        .field(VERSION_24_OR_LOWER, Names.HIDDEN_SIGNERS, Types.java_lang_Object_array) //
                        .maybeHiddenfield(java_lang_Class);
        HIDDEN_PROTECTION_DOMAIN = diff() //
                        .field(lower(24), Names.HIDDEN_PROTECTION_DOMAIN, Types.java_security_ProtectionDomain) //
                        .field(VERSION_25_OR_HIGHER, Names.protectionDomain, Types.java_security_ProtectionDomain) //
                        .maybeHiddenfield(java_lang_Class);

        if (getJavaVersion().modulesEnabled()) {
            java_lang_Class_module = java_lang_Class.requireDeclaredField(Names.module, Types.java_lang_Module);
        } else {
            java_lang_Class_module = null;
        }

        // Ensure that Object, Cloneable, Class and all its super-interfaces have the guest Class
        // initialized.
        initializeEspressoClassInHierarchy(java_lang_Cloneable);
        initializeEspressoClassInHierarchy(java_lang_Class);
        // From now on, all Klass'es will safely initialize the guest Class.

        java_io_Serializable = knownKlass(Types.java_io_Serializable);
        ARRAY_SUPERINTERFACES = new ObjectKlass[]{java_lang_Cloneable, java_io_Serializable};
        java_lang_Object_array = java_lang_Object.array();
        java_lang_Enum = knownKlass(Types.java_lang_Enum);

        EspressoError.guarantee(
                        new HashSet<>(Arrays.asList(ARRAY_SUPERINTERFACES)).equals(new HashSet<>(Arrays.asList(java_lang_Object_array.getSuperInterfaces()))),
                        "arrays super interfaces must contain java.lang.Cloneable and java.io.Serializable");

        java_lang_Class_array = java_lang_Class.array();
        java_lang_Class_getName = java_lang_Class.requireDeclaredMethod(Names.getName, Signatures.String);
        java_lang_Class_getSimpleName = java_lang_Class.requireDeclaredMethod(Names.getSimpleName, Signatures.String);
        java_lang_Class_getTypeName = java_lang_Class.requireDeclaredMethod(Names.getTypeName, Signatures.String);
        java_lang_Class_forName_String = java_lang_Class.requireDeclaredMethod(Names.forName, Signatures.Class_String);
        java_lang_Class_forName_String_boolean_ClassLoader = java_lang_Class.requireDeclaredMethod(Names.forName, Signatures.Class_String_boolean_ClassLoader);
        if (getLanguage().isJVMCIEnabled()) {
            HIDDEN_JVMCIINDY = java_lang_Class.requireHiddenField(Names.HIDDEN_JVMCIINDY);
        } else {
            HIDDEN_JVMCIINDY = null;
        }

        java_lang_String = knownKlass(Types.java_lang_String);
        java_lang_String_array = java_lang_String.array();
        java_lang_CharSequence = knownKlass(Types.java_lang_CharSequence);

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

        PRIMITIVE_KLASSES = new PrimitiveKlass[]{
                        _boolean,
                        _byte,
                        _char,
                        _short,
                        _float,
                        _int,
                        _double,
                        _long,
                        _void
        };

        _boolean_array = _boolean.array();
        _byte_array = _byte.array();
        _char_array = _char.array();
        _short_array = _short.array();
        _float_array = _float.array();
        _int_array = _int.array();
        _double_array = _double.array();
        _long_array = _long.array();

        // Boxed types.
        java_lang_Boolean = knownKlass(Types.java_lang_Boolean);
        java_lang_Byte = knownKlass(Types.java_lang_Byte);
        java_lang_Character = knownKlass(Types.java_lang_Character);
        java_lang_Short = knownKlass(Types.java_lang_Short);
        java_lang_Float = knownKlass(Types.java_lang_Float);
        java_lang_Integer = knownKlass(Types.java_lang_Integer);
        java_lang_Double = knownKlass(Types.java_lang_Double);
        java_lang_Long = knownKlass(Types.java_lang_Long);
        java_lang_Void = knownKlass(Types.java_lang_Void);

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

        java_lang_Number = knownKlass(Types.java_lang_Number);

        java_lang_Boolean_valueOf = java_lang_Boolean.requireDeclaredMethod(Names.valueOf, Signatures.Boolean_boolean);
        java_lang_Byte_valueOf = java_lang_Byte.requireDeclaredMethod(Names.valueOf, Signatures.Byte_byte);
        java_lang_Character_valueOf = java_lang_Character.requireDeclaredMethod(Names.valueOf, Signatures.Character_char);
        java_lang_Short_valueOf = java_lang_Short.requireDeclaredMethod(Names.valueOf, Signatures.Short_short);
        java_lang_Float_valueOf = java_lang_Float.requireDeclaredMethod(Names.valueOf, Signatures.Float_float);
        java_lang_Integer_valueOf = java_lang_Integer.requireDeclaredMethod(Names.valueOf, Signatures.Integer_int);
        java_lang_Double_valueOf = java_lang_Double.requireDeclaredMethod(Names.valueOf, Signatures.Double_double);
        java_lang_Long_valueOf = java_lang_Long.requireDeclaredMethod(Names.valueOf, Signatures.Long_long);

        java_lang_Boolean_value = java_lang_Boolean.requireDeclaredField(Names.value, Types._boolean);
        java_lang_Byte_value = java_lang_Byte.requireDeclaredField(Names.value, Types._byte);
        java_lang_Character_value = java_lang_Character.requireDeclaredField(Names.value, Types._char);
        java_lang_Short_value = java_lang_Short.requireDeclaredField(Names.value, Types._short);
        java_lang_Float_value = java_lang_Float.requireDeclaredField(Names.value, Types._float);
        java_lang_Integer_value = java_lang_Integer.requireDeclaredField(Names.value, Types._int);
        java_lang_Double_value = java_lang_Double.requireDeclaredField(Names.value, Types._double);
        java_lang_Long_value = java_lang_Long.requireDeclaredField(Names.value, Types._long);

        java_lang_String_value = diff() //
                        .field(VERSION_8_OR_LOWER, Names.value, Types._char_array) //
                        .field(VERSION_9_OR_HIGHER, Names.value, Types._byte_array) //
                        .field(java_lang_String);
        java_lang_String_hash = java_lang_String.requireDeclaredField(Names.hash, Types._int);
        java_lang_String_hashCode = java_lang_String.requireDeclaredMethod(Names.hashCode, Signatures._int);
        java_lang_String_length = java_lang_String.requireDeclaredMethod(Names.length, Signatures._int);
        java_lang_String_toCharArray = java_lang_String.requireDeclaredMethod(Names.toCharArray, Signatures._char_array);
        java_lang_String_indexOf = java_lang_String.requireDeclaredMethod(Names.indexOf, Signatures._int_int_int);
        java_lang_String_init_char_array = java_lang_String.requireDeclaredMethod(Names._init_, Signatures._void_char_array);
        if (getJavaVersion().java9OrLater()) {
            java_lang_String_coder = java_lang_String.requireDeclaredField(Names.coder, Types._byte);
            java_lang_String_COMPACT_STRINGS = java_lang_String.requireDeclaredField(Names.COMPACT_STRINGS, Types._boolean);
        } else {
            java_lang_String_coder = null;
            java_lang_String_COMPACT_STRINGS = null;
        }

        java_lang_CharSequence_toString = java_lang_CharSequence.requireDeclaredMethod(Names.toString, Signatures.String);

        java_lang_Throwable = knownKlass(Types.java_lang_Throwable);
        java_lang_Throwable_getStackTrace = java_lang_Throwable.requireDeclaredMethod(Names.getStackTrace, Signatures.StackTraceElement_array);
        java_lang_Throwable_getMessage = java_lang_Throwable.requireDeclaredMethod(Names.getMessage, Signatures.String);
        java_lang_Throwable_getCause = java_lang_Throwable.requireDeclaredMethod(Names.getCause, Signatures.Throwable);
        java_lang_Throwable_initCause = java_lang_Throwable.requireDeclaredMethod(Names.initCause, Signatures.Throwable_Throwable);
        java_lang_Throwable_printStackTrace = java_lang_Throwable.requireDeclaredMethod(Names.printStackTrace, Signatures._void);
        HIDDEN_FRAMES = java_lang_Throwable.requireHiddenField(Names.HIDDEN_FRAMES);
        HIDDEN_EXCEPTION_WRAPPER = java_lang_Throwable.requireHiddenField(Names.HIDDEN_EXCEPTION_WRAPPER);
        java_lang_Throwable_backtrace = java_lang_Throwable.requireDeclaredField(Names.backtrace, Types.java_lang_Object);
        java_lang_Throwable_stackTrace = java_lang_Throwable.requireDeclaredField(Names.stackTrace, Types.java_lang_StackTraceElement_array);
        java_lang_Throwable_detailMessage = java_lang_Throwable.requireDeclaredField(Names.detailMessage, Types.java_lang_String);
        java_lang_Throwable_cause = java_lang_Throwable.requireDeclaredField(Names.cause, Types.java_lang_Throwable);
        if (getJavaVersion().java9OrLater()) {
            java_lang_Throwable_depth = java_lang_Throwable.requireDeclaredField(Names.depth, Types._int);
        } else {
            java_lang_Throwable_depth = null;
        }

        java_lang_StackTraceElement = knownKlass(Types.java_lang_StackTraceElement);
        java_lang_StackTraceElement_init = java_lang_StackTraceElement.requireDeclaredMethod(Names._init_, Signatures._void_String_String_String_int);
        java_lang_StackTraceElement_declaringClass = java_lang_StackTraceElement.requireDeclaredField(Names.declaringClass, Types.java_lang_String);
        java_lang_StackTraceElement_methodName = java_lang_StackTraceElement.requireDeclaredField(Names.methodName, Types.java_lang_String);
        java_lang_StackTraceElement_fileName = java_lang_StackTraceElement.requireDeclaredField(Names.fileName, Types.java_lang_String);
        java_lang_StackTraceElement_lineNumber = java_lang_StackTraceElement.requireDeclaredField(Names.lineNumber, Types._int);
        if (getJavaVersion().java9OrLater()) {
            java_lang_StackTraceElement_declaringClassObject = java_lang_StackTraceElement.requireDeclaredField(Names.declaringClassObject, Types.java_lang_Class);
            java_lang_StackTraceElement_classLoaderName = java_lang_StackTraceElement.requireDeclaredField(Names.classLoaderName, Types.java_lang_String);
            java_lang_StackTraceElement_moduleName = java_lang_StackTraceElement.requireDeclaredField(Names.moduleName, Types.java_lang_String);
            java_lang_StackTraceElement_moduleVersion = java_lang_StackTraceElement.requireDeclaredField(Names.moduleVersion, Types.java_lang_String);
        } else {
            java_lang_StackTraceElement_declaringClassObject = null;
            java_lang_StackTraceElement_classLoaderName = null;
            java_lang_StackTraceElement_moduleName = null;
            java_lang_StackTraceElement_moduleVersion = null;
        }

        java_lang_Exception = knownKlass(Types.java_lang_Exception);
        java_lang_reflect_InvocationTargetException = knownKlass(Types.java_lang_reflect_InvocationTargetException);
        java_lang_NegativeArraySizeException = knownKlass(Types.java_lang_NegativeArraySizeException);
        java_lang_IllegalArgumentException = knownKlass(Types.java_lang_IllegalArgumentException);
        java_lang_IllegalStateException = knownKlass(Types.java_lang_IllegalStateException);
        java_lang_NullPointerException = knownKlass(Types.java_lang_NullPointerException);
        if (getJavaVersion().java14OrLater()) {
            java_lang_NullPointerException_extendedMessageState = java_lang_NullPointerException.requireDeclaredField(Names.extendedMessageState, Types._int);
        } else {
            java_lang_NullPointerException_extendedMessageState = null;
        }
        java_lang_ClassNotFoundException = knownKlass(Types.java_lang_ClassNotFoundException);
        java_lang_NoClassDefFoundError = knownKlass(Types.java_lang_NoClassDefFoundError);
        java_lang_InterruptedException = knownKlass(Types.java_lang_InterruptedException);
        java_lang_ThreadDeath = knownKlass(Types.java_lang_ThreadDeath);

        java_lang_RuntimeException = knownKlass(Types.java_lang_RuntimeException);
        java_lang_IllegalMonitorStateException = knownKlass(Types.java_lang_IllegalMonitorStateException);
        java_lang_ArrayStoreException = knownKlass(Types.java_lang_ArrayStoreException);
        java_lang_IndexOutOfBoundsException = knownKlass(Types.java_lang_IndexOutOfBoundsException);
        java_lang_ArrayIndexOutOfBoundsException = knownKlass(Types.java_lang_ArrayIndexOutOfBoundsException);
        java_lang_StringIndexOutOfBoundsException = knownKlass(Types.java_lang_StringIndexOutOfBoundsException);
        java_lang_ExceptionInInitializerError = knownKlass(Types.java_lang_ExceptionInInitializerError);
        java_lang_InstantiationException = knownKlass(Types.java_lang_InstantiationException);
        java_lang_InstantiationError = knownKlass(Types.java_lang_InstantiationError);
        java_lang_CloneNotSupportedException = knownKlass(Types.java_lang_CloneNotSupportedException);
        java_lang_SecurityException = knownKlass(Types.java_lang_SecurityException);
        java_lang_ArithmeticException = knownKlass(Types.java_lang_ArithmeticException);
        java_lang_LinkageError = knownKlass(Types.java_lang_LinkageError);
        java_lang_NoSuchFieldException = knownKlass(Types.java_lang_NoSuchFieldException);
        java_lang_NoSuchMethodException = knownKlass(Types.java_lang_NoSuchMethodException);
        java_lang_UnsupportedOperationException = knownKlass(Types.java_lang_UnsupportedOperationException);
        java_util_NoSuchElementException = knownKlass(Types.java_util_NoSuchElementException);
        java_lang_NumberFormatException = knownKlass(Types.java_lang_NumberFormatException);

        java_lang_StackOverflowError = knownKlass(Types.java_lang_StackOverflowError);
        java_lang_OutOfMemoryError = knownKlass(Types.java_lang_OutOfMemoryError);
        java_lang_ClassCastException = knownKlass(Types.java_lang_ClassCastException);
        java_lang_AbstractMethodError = knownKlass(Types.java_lang_AbstractMethodError);
        java_lang_InternalError = knownKlass(Types.java_lang_InternalError);
        java_lang_VerifyError = knownKlass(Types.java_lang_VerifyError);
        java_lang_ClassFormatError = knownKlass(Types.java_lang_ClassFormatError);
        java_lang_ClassCircularityError = knownKlass(Types.java_lang_ClassCircularityError);
        java_lang_UnsatisfiedLinkError = knownKlass(Types.java_lang_UnsatisfiedLinkError);
        java_lang_UnsupportedClassVersionError = knownKlass(Types.java_lang_UnsupportedClassVersionError);

        java_lang_Error = knownKlass(Types.java_lang_Error);
        java_lang_NoSuchFieldError = knownKlass(Types.java_lang_NoSuchFieldError);
        java_lang_NoSuchMethodError = knownKlass(Types.java_lang_NoSuchMethodError);
        java_lang_IllegalAccessError = knownKlass(Types.java_lang_IllegalAccessError);
        java_lang_IncompatibleClassChangeError = knownKlass(Types.java_lang_IncompatibleClassChangeError);
        java_lang_BootstrapMethodError = knownKlass(Types.java_lang_BootstrapMethodError);

        // Initialize dispatch once common exceptions are discovered.
        this.dispatch = new ExceptionDispatch(this);

        java_security_PrivilegedActionException = knownKlass(Types.java_security_PrivilegedActionException);
        java_security_PrivilegedActionException_init_Exception = java_security_PrivilegedActionException.requireDeclaredMethod(Names._init_, Signatures._void_Exception);

        java_lang_ClassLoader = knownKlass(Types.java_lang_ClassLoader);
        java_lang_ClassLoader$NativeLibrary = diff() //
                        .klass(lower(14), Types.java_lang_ClassLoader$NativeLibrary) //
                        .klass(higher(15), Types.jdk_internal_loader_NativeLibraries) //
                        .klass();
        java_lang_ClassLoader$NativeLibrary_getFromClass = java_lang_ClassLoader$NativeLibrary.requireDeclaredMethod(Names.getFromClass, Signatures.Class);
        java_lang_ClassLoader_checkPackageAccess = diff() //
                        .method(VERSION_21_OR_LOWER, Names.checkPackageAccess, Signatures.Class_PermissionDomain) //
                        .notRequiredMethod(java_lang_ClassLoader);
        java_lang_ClassLoader_findNative = diff() //
                        .method(VERSION_21_OR_LOWER, Names.findNative, Signatures._long_ClassLoader_String) //
                        .method(VERSION_25_OR_HIGHER, Names.findNative, Signatures._long_ClassLoader_Class_String_String) //
                        .method(java_lang_ClassLoader);
        java_lang_ClassLoader_getSystemClassLoader = java_lang_ClassLoader.requireDeclaredMethod(Names.getSystemClassLoader, Signatures.ClassLoader);
        java_lang_ClassLoader_parent = java_lang_ClassLoader.requireDeclaredField(Names.parent, Types.java_lang_ClassLoader);
        HIDDEN_CLASS_LOADER_REGISTRY = java_lang_ClassLoader.requireHiddenField(Names.HIDDEN_CLASS_LOADER_REGISTRY);
        if (getJavaVersion().java9OrLater()) {
            java_lang_ClassLoader_unnamedModule = java_lang_ClassLoader.requireDeclaredField(Names.unnamedModule, Types.java_lang_Module);
            java_lang_ClassLoader_name = java_lang_ClassLoader.requireDeclaredField(Names.name, Types.java_lang_String);
            java_lang_ClassLoader_nameAndId = java_lang_ClassLoader.requireDeclaredField(Names.nameAndId, Types.java_lang_String);
        } else {
            java_lang_ClassLoader_unnamedModule = null;
            java_lang_ClassLoader_name = null;
            java_lang_ClassLoader_nameAndId = null;
        }

        if (getJavaVersion().java19OrLater()) {
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl = knownKlass(Types.jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl);
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle = jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl.requireDeclaredField(Names.handle, Types._long);
        } else {
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl = null;
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle = null;
        }
        jdk_internal_loader_NativeLibraries$NativeLibraryImpl = diff().klass(higher(15), Types.jdk_internal_loader_NativeLibraries$NativeLibraryImpl).notRequiredKlass();
        jdk_internal_loader_NativeLibraries$NativeLibraryImpl_handle = diff().field(higher(15), Names.handle, Types._long).notRequiredField(jdk_internal_loader_NativeLibraries$NativeLibraryImpl);
        jdk_internal_loader_NativeLibraries$NativeLibraryImpl_jniVersion = diff().field(higher(15), Names.jniVersion, Types._int).notRequiredField(
                        jdk_internal_loader_NativeLibraries$NativeLibraryImpl);

        if (getJavaVersion().java9OrLater()) {
            jdk_internal_util_ArraysSupport = knownKlass(Types.jdk_internal_util_ArraysSupport);
            jdk_internal_util_ArraysSupport_vectorizedMismatch = jdk_internal_util_ArraysSupport.requireDeclaredMethod(Names.vectorizedMismatch, Signatures._int_Object_long_Object_long_int_int);
        } else {
            jdk_internal_util_ArraysSupport = null;
            jdk_internal_util_ArraysSupport_vectorizedMismatch = null;
        }

        java_net_URL = knownKlass(Types.java_net_URL);

        java_lang_ClassLoader_getResourceAsStream = java_lang_ClassLoader.requireDeclaredMethod(Names.getResourceAsStream, Signatures.InputStream_String);
        java_lang_ClassLoader_loadClass = java_lang_ClassLoader.requireDeclaredMethod(Names.loadClass, Signatures.Class_String);
        java_io_InputStream = knownKlass(Types.java_io_InputStream);
        java_io_InputStream_read = java_io_InputStream.requireDeclaredMethod(Names.read, Signatures._int_byte_array_int_int);
        java_io_InputStream_close = java_io_InputStream.requireDeclaredMethod(Names.close, Signatures._void);
        java_io_InputStream_skip = java_io_InputStream.requireDeclaredMethod(Names.skip, Signatures._long_long);
        java_io_PrintStream = knownKlass(Types.java_io_PrintStream);
        java_io_PrintStream_println = java_io_PrintStream.requireDeclaredMethod(Names.println, Signatures._void_String);
        java_nio_file_Path = knownKlass(Types.java_nio_file_Path);
        java_nio_file_Paths = knownKlass(Types.java_nio_file_Paths);
        java_nio_file_Paths_get = java_nio_file_Paths.requireDeclaredMethod(Names.get, Signatures.Path_String_String_array);

        java_nio_file_FileAlreadyExistsException = knownKlass(Types.java_nio_file_FileAlreadyExistsException);
        java_nio_file_DirectoryNotEmptyException = knownKlass(Types.java_nio_file_DirectoryNotEmptyException);
        java_nio_file_AtomicMoveNotSupportedException = knownKlass(Types.java_nio_file_AtomicMoveNotSupportedException);
        java_nio_file_AccessDeniedException = knownKlass(Types.java_nio_file_AccessDeniedException);
        java_nio_file_NoSuchFileException = knownKlass(Types.java_nio_file_NoSuchFileException);
        java_nio_file_InvalidPathException = knownKlass(Types.java_nio_file_InvalidPathException);
        java_nio_file_NotDirectoryException = knownKlass(Types.java_nio_file_NotDirectoryException);
        java_nio_file_NotLinkException = knownKlass(Types.java_nio_file_NotLinkException);

        ObjectKlass nioNativeThreadKlass = knownKlass(Types.sun_nio_ch_NativeThread);
        sun_nio_ch_NativeThread_init = nioNativeThreadKlass.lookupDeclaredMethod(Names.init, Signatures._void);
        if (getJavaVersion().java21OrLater()) {
            sun_nio_ch_NativeThread_isNativeThread = nioNativeThreadKlass.requireDeclaredMethod(Names.isNativeThread, Signatures._boolean_long);
            sun_nio_ch_NativeThread_current0 = nioNativeThreadKlass.lookupDeclaredMethod(Names.current0, Signatures._long);
            sun_nio_ch_NativeThread_signal = null;
        } else {
            sun_nio_ch_NativeThread_isNativeThread = null;
            sun_nio_ch_NativeThread_current0 = null;
            sun_nio_ch_NativeThread_signal = nioNativeThreadKlass.requireDeclaredMethod(Names.signal, Signatures._void_long);
        }

        sun_launcher_LauncherHelper = knownKlass(Types.sun_launcher_LauncherHelper);
        sun_launcher_LauncherHelper_printHelpMessage = sun_launcher_LauncherHelper.requireDeclaredMethod(Names.printHelpMessage, Signatures._void_boolean);
        sun_launcher_LauncherHelper_ostream = sun_launcher_LauncherHelper.requireDeclaredField(Names.ostream, Types.java_io_PrintStream);

        // Guest reflection.
        java_lang_reflect_Executable = knownKlass(Types.java_lang_reflect_Executable);
        java_lang_reflect_Constructor = knownKlass(Types.java_lang_reflect_Constructor);
        java_lang_reflect_Constructor_init = java_lang_reflect_Constructor.requireDeclaredMethod(Names._init_, Signatures.java_lang_reflect_Constructor_init_signature);

        HIDDEN_CONSTRUCTOR_KEY = java_lang_reflect_Constructor.requireHiddenField(Names.HIDDEN_CONSTRUCTOR_KEY);
        HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Constructor.requireHiddenField(Names.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Constructor_clazz = java_lang_reflect_Constructor.requireDeclaredField(Names.clazz, Types.java_lang_Class);
        java_lang_reflect_Constructor_root = java_lang_reflect_Constructor.requireDeclaredField(Names.root, Types.java_lang_reflect_Constructor);
        java_lang_reflect_Constructor_parameterTypes = java_lang_reflect_Constructor.requireDeclaredField(Names.parameterTypes, Types.java_lang_Class_array);
        java_lang_reflect_Constructor_signature = java_lang_reflect_Constructor.requireDeclaredField(Names.signature, Types.java_lang_String);

        java_lang_reflect_Method = knownKlass(Types.java_lang_reflect_Method);
        java_lang_reflect_Method_init = java_lang_reflect_Method.lookupDeclaredMethod(Names._init_, Signatures.java_lang_reflect_Method_init_signature);
        HIDDEN_METHOD_KEY = java_lang_reflect_Method.requireHiddenField(Names.HIDDEN_METHOD_KEY);
        HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Method.requireHiddenField(Names.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Method_root = java_lang_reflect_Method.requireDeclaredField(Names.root, Types.java_lang_reflect_Method);
        java_lang_reflect_Method_clazz = java_lang_reflect_Method.requireDeclaredField(Names.clazz, Types.java_lang_Class);
        java_lang_reflect_Method_parameterTypes = java_lang_reflect_Method.requireDeclaredField(Names.parameterTypes, Types.java_lang_Class_array);

        java_lang_reflect_Parameter = knownKlass(Types.java_lang_reflect_Parameter);
        java_lang_reflect_ParameterizedType = knownKlass(Types.java_lang_reflect_ParameterizedType);
        java_lang_reflect_ParameterizedType_getRawType = java_lang_reflect_ParameterizedType.requireDeclaredMethod(Names.getRawType, Signatures.Java_lang_reflect_Type);

        java_lang_reflect_Field = knownKlass(Types.java_lang_reflect_Field);
        HIDDEN_FIELD_KEY = java_lang_reflect_Field.requireHiddenField(Names.HIDDEN_FIELD_KEY);
        HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Field.requireHiddenField(Names.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Field_root = java_lang_reflect_Field.requireDeclaredField(Names.root, java_lang_reflect_Field.getType());
        java_lang_reflect_Field_class = java_lang_reflect_Field.requireDeclaredField(Names.clazz, Types.java_lang_Class);
        java_lang_reflect_Field_name = java_lang_reflect_Field.requireDeclaredField(Names.name, Types.java_lang_String);
        java_lang_reflect_Field_type = java_lang_reflect_Field.requireDeclaredField(Names.type, Types.java_lang_Class);

        java_lang_reflect_Field_init = diff() //
                        .method(lower(14), Names._init_, Signatures.java_lang_reflect_Field_init_signature) //
                        .method(higher(15), Names._init_, Signatures.java_lang_reflect_Field_init_signature_15) //
                        .method(java_lang_reflect_Field);

        java_lang_Shutdown = knownKlass(Types.java_lang_Shutdown);
        java_lang_Shutdown_shutdown = java_lang_Shutdown.requireDeclaredMethod(Names.shutdown, Signatures._void);

        java_nio_Buffer = knownKlass(Types.java_nio_Buffer);
        sun_nio_ch_DirectBuffer = knownKlass(Types.sun_nio_ch_DirectBuffer);
        java_nio_Buffer_address = java_nio_Buffer.requireDeclaredField(Names.address, Types._long);
        java_nio_Buffer_capacity = java_nio_Buffer.requireDeclaredField(Names.capacity, Types._int);
        java_nio_Buffer_limit = java_nio_Buffer.requireDeclaredMethod(Names.limit, Signatures._int);
        java_nio_Buffer_isReadOnly = java_nio_Buffer.requireDeclaredMethod(Names.isReadOnly, Signatures._boolean);

        java_nio_ByteBuffer = knownKlass(Types.java_nio_ByteBuffer);
        java_nio_ByteBuffer_wrap = java_nio_ByteBuffer.requireDeclaredMethod(Names.wrap, Signatures.ByteBuffer_byte_array);
        if (getJavaVersion().java13OrLater()) {
            java_nio_ByteBuffer_get = java_nio_ByteBuffer.requireDeclaredMethod(Names.get, Signatures.ByteBuffer_int_byte_array_int_int);
        } else {
            java_nio_ByteBuffer_get = null;
        }

        java_nio_ByteBuffer_getByte = java_nio_ByteBuffer.requireDeclaredMethod(Names.get, Signatures._byte_int);
        java_nio_ByteBuffer_getShort = java_nio_ByteBuffer.requireDeclaredMethod(Names.getShort, Signatures._short_int);
        java_nio_ByteBuffer_getInt = java_nio_ByteBuffer.requireDeclaredMethod(Names.getInt, Signatures._int_int);
        java_nio_ByteBuffer_getLong = java_nio_ByteBuffer.requireDeclaredMethod(Names.getLong, Signatures._long_int);
        java_nio_ByteBuffer_getFloat = java_nio_ByteBuffer.requireDeclaredMethod(Names.getFloat, Signatures._float_int);
        java_nio_ByteBuffer_getDouble = java_nio_ByteBuffer.requireDeclaredMethod(Names.getDouble, Signatures._double_int);
        java_nio_ByteBuffer_putByte = java_nio_ByteBuffer.requireDeclaredMethod(Names.put, Signatures.ByteBuffer_int_byte);
        java_nio_ByteBuffer_putShort = java_nio_ByteBuffer.requireDeclaredMethod(Names.putShort, Signatures.ByteBuffer_int_short);
        java_nio_ByteBuffer_putInt = java_nio_ByteBuffer.requireDeclaredMethod(Names.putInt, Signatures.ByteBuffer_int_int);
        java_nio_ByteBuffer_putLong = java_nio_ByteBuffer.requireDeclaredMethod(Names.putLong, Signatures.ByteBuffer_int_long);
        java_nio_ByteBuffer_putFloat = java_nio_ByteBuffer.requireDeclaredMethod(Names.putFloat, Signatures.ByteBuffer_int_float);
        java_nio_ByteBuffer_putDouble = java_nio_ByteBuffer.requireDeclaredMethod(Names.putDouble, Signatures.ByteBuffer_int_double);
        java_nio_ByteBuffer_order = java_nio_ByteBuffer.requireDeclaredMethod(Names.order, Signatures.ByteOrder);
        java_nio_ByteBuffer_setOrder = java_nio_ByteBuffer.requireDeclaredMethod(Names.order, Signatures.ByteBuffer_ByteOrder);

        java_nio_DirectByteBuffer = knownKlass(Types.java_nio_DirectByteBuffer);
        java_nio_DirectByteBuffer_init_long_int = diff() //
                        .method(lower(20), Names._init_, Signatures._void_long_int) //
                        .method(higher(21), Names._init_, Signatures._void_long_long) //
                        .method(java_nio_DirectByteBuffer);
        java_nio_ByteOrder = knownKlass(Types.java_nio_ByteOrder);
        java_nio_ByteOrder_LITTLE_ENDIAN = java_nio_ByteOrder.requireDeclaredField(Names.LITTLE_ENDIAN, Types.java_nio_ByteOrder);
        java_nio_ByteOrder_BIG_ENDIAN = java_nio_ByteOrder.requireDeclaredField(Names.BIG_ENDIAN, Types.java_nio_ByteOrder);

        java_lang_Thread = knownKlass(Types.java_lang_Thread);
        // The interrupted field is no longer hidden as of JDK14+
        HIDDEN_INTERRUPTED = diff() //
                        .field(lower(13), Names.HIDDEN_INTERRUPTED, Types._boolean)//
                        .field(higher(14), Names.interrupted, Types._boolean) //
                        .maybeHiddenfield(java_lang_Thread);
        HIDDEN_HOST_THREAD = java_lang_Thread.requireHiddenField(Names.HIDDEN_HOST_THREAD);
        HIDDEN_ESPRESSO_MANAGED = java_lang_Thread.requireHiddenField(Names.HIDDEN_ESPRESSO_MANAGED);
        HIDDEN_TO_NATIVE_LOCK = java_lang_Thread.requireHiddenField(Names.HIDDEN_TO_NATIVE_LOCK);
        HIDDEN_DEPRECATION_SUPPORT = java_lang_Thread.requireHiddenField(Names.HIDDEN_DEPRECATION_SUPPORT);
        HIDDEN_THREAD_UNPARK_SIGNALS = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_UNPARK_SIGNALS);
        HIDDEN_THREAD_PARK_LOCK = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_PARK_LOCK);
        if (getJavaVersion().java19OrLater()) {
            HIDDEN_THREAD_SCOPED_VALUE_CACHE = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_SCOPED_VALUE_CACHE);
        } else {
            HIDDEN_THREAD_SCOPED_VALUE_CACHE = null;
        }

        if (context.getEspressoEnv().EnableManagement) {
            HIDDEN_THREAD_PENDING_MONITOR = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_PENDING_MONITOR);
            HIDDEN_THREAD_WAITING_MONITOR = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_WAITING_MONITOR);
            HIDDEN_THREAD_BLOCKED_COUNT = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_BLOCKED_COUNT);
            HIDDEN_THREAD_WAITED_COUNT = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_WAITED_COUNT);
            HIDDEN_THREAD_DEPTH_FIRST_NUMBER = java_lang_Thread.requireHiddenField(Names.HIDDEN_THREAD_DEPTH_FIRST_NUMBER);
        } else {
            HIDDEN_THREAD_PENDING_MONITOR = null;
            HIDDEN_THREAD_WAITING_MONITOR = null;
            HIDDEN_THREAD_BLOCKED_COUNT = null;
            HIDDEN_THREAD_WAITED_COUNT = null;
            HIDDEN_THREAD_DEPTH_FIRST_NUMBER = null;
        }

        if (getJavaVersion().java19OrLater()) {
            java_lang_BaseVirtualThread = knownKlass(Types.java_lang_BaseVirtualThread);
            java_lang_Thread_threadGroup = null;
            java_lang_Thread$FieldHolder = knownKlass(Types.java_lang_Thread_FieldHolder);
            java_lang_Thread$Constants = knownKlass(Types.java_lang_Thread_Constants);
            java_lang_Thread$FieldHolder_group = java_lang_Thread$FieldHolder.requireDeclaredField(Names.group, Types.java_lang_ThreadGroup);
            java_lang_Thread$Constants_VTHREAD_GROUP = java_lang_Thread$Constants.requireDeclaredField(Names.VTHREAD_GROUP, Types.java_lang_ThreadGroup);
        } else {
            java_lang_BaseVirtualThread = null;
            java_lang_Thread$FieldHolder = null;
            java_lang_Thread$Constants = null;
            java_lang_Thread_threadGroup = java_lang_Thread.requireDeclaredField(Names.group, Types.java_lang_ThreadGroup);
            java_lang_Thread$FieldHolder_group = null;
            java_lang_Thread$Constants_VTHREAD_GROUP = null;
        }
        java_lang_ThreadGroup = knownKlass(Types.java_lang_ThreadGroup);
        if (getJavaVersion().java17OrEarlier()) {
            java_lang_ThreadGroup_add = java_lang_ThreadGroup.requireDeclaredMethod(Names.add, Signatures._void_Thread);
        } else {
            java_lang_ThreadGroup_add = null;
        }
        java_lang_Thread_dispatchUncaughtException = java_lang_Thread.requireDeclaredMethod(Names.dispatchUncaughtException, Signatures._void_Throwable);
        java_lang_Thread_init_ThreadGroup_Runnable = java_lang_Thread.requireDeclaredMethod(Names._init_, Signatures._void_ThreadGroup_Runnable);
        java_lang_Thread_init_ThreadGroup_String = java_lang_Thread.requireDeclaredMethod(Names._init_, Signatures._void_ThreadGroup_String);
        java_lang_Thread_interrupt = java_lang_Thread.requireDeclaredMethod(Names.interrupt, Signatures._void);
        java_lang_Thread_exit = java_lang_Thread.requireDeclaredMethod(Names.exit, Signatures._void);
        java_lang_Thread_run = java_lang_Thread.requireDeclaredMethod(Names.run, Signatures._void);
        java_lang_Thread_getThreadGroup = java_lang_Thread.requireDeclaredMethod(Names.getThreadGroup, Signatures.ThreadGroup);
        if (getJavaVersion().java17OrEarlier()) {
            java_lang_Thread_holder = null;

            java_lang_Thread_threadStatus = java_lang_Thread.requireDeclaredField(Names.threadStatus, Types._int);
            java_lang_Thread$FieldHolder_threadStatus = null;

            java_lang_Thread_priority = java_lang_Thread.requireDeclaredField(Names.priority, _int.getType());
            java_lang_Thread$FieldHolder_priority = null;

            java_lang_Thread_daemon = java_lang_Thread.requireDeclaredField(Names.daemon, Types._boolean);
            java_lang_Thread$FieldHolder_daemon = null;
        } else {
            java_lang_Thread_holder = java_lang_Thread.requireDeclaredField(Names.holder, java_lang_Thread$FieldHolder.getType());

            java_lang_Thread_threadStatus = null;
            java_lang_Thread$FieldHolder_threadStatus = java_lang_Thread$FieldHolder.requireDeclaredField(Names.threadStatus, Types._int);

            java_lang_Thread_priority = null;
            java_lang_Thread$FieldHolder_priority = java_lang_Thread$FieldHolder.requireDeclaredField(Names.priority, _int.getType());

            java_lang_Thread_daemon = null;
            java_lang_Thread$FieldHolder_daemon = java_lang_Thread$FieldHolder.requireDeclaredField(Names.daemon, Types._boolean);
        }
        java_lang_Thread_tid = java_lang_Thread.requireDeclaredField(Names.tid, Types._long);
        java_lang_Thread_eetop = java_lang_Thread.requireDeclaredField(Names.eetop, Types._long);
        java_lang_Thread_contextClassLoader = java_lang_Thread.requireDeclaredField(Names.contextClassLoader, Types.java_lang_ClassLoader);

        java_lang_Thread_name = java_lang_Thread.requireDeclaredField(Names.name, java_lang_String.getType());
        java_lang_Thread_inheritedAccessControlContext = diff()//
                        .field(VERSION_21_OR_LOWER, Names.inheritedAccessControlContext, Types.java_security_AccessControlContext)//
                        .notRequiredField(java_lang_Thread);
        java_lang_Thread_checkAccess = java_lang_Thread.requireDeclaredMethod(Names.checkAccess, Signatures._void);
        java_lang_Thread_stop = java_lang_Thread.requireDeclaredMethod(Names.stop, Signatures._void);
        java_lang_ThreadGroup_maxPriority = java_lang_ThreadGroup.requireDeclaredField(Names.maxPriority, Types._int);

        java_lang_ref_Finalizer$FinalizerThread = knownKlass(Types.java_lang_ref_Finalizer$FinalizerThread);
        java_lang_ref_Reference$ReferenceHandler = knownKlass(Types.java_lang_ref_Reference$ReferenceHandler);
        misc_InnocuousThread = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_misc_InnocuousThread) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_misc_InnocuousThread) //
                        .klass();

        java_lang_System = knownKlass(Types.java_lang_System);
        java_lang_System_exit = java_lang_System.requireDeclaredMethod(Names.exit, Signatures._void_int);
        java_lang_System_getProperty = java_lang_System.requireDeclaredMethod(Names.getProperty, Signatures.String_String);
        java_lang_System_securityManager = diff() //
                        .field(VERSION_21_OR_LOWER, Names.security, Types.java_lang_SecurityManager) //
                        .notRequiredField(java_lang_System);
        java_lang_System_in = java_lang_System.requireDeclaredField(Names.in, Types.java_io_InputStream);
        java_lang_System_out = java_lang_System.requireDeclaredField(Names.out, Types.java_io_PrintStream);
        java_lang_System_err = java_lang_System.requireDeclaredField(Names.err, Types.java_io_PrintStream);

        jdk_internal_util_SystemProps_Raw = diff().klass(VERSION_9_OR_HIGHER, Types.jdk_internal_util_SystemProps_Raw).notRequiredKlass();

        java_security_ProtectionDomain = knownKlass(Types.java_security_ProtectionDomain);
        java_security_ProtectionDomain_impliesCreateAccessControlContext = diff() //
                        .method(lower(11), Names.impliesCreateAccessControlContext, Signatures._boolean) //
                        .notRequiredMethod(java_security_ProtectionDomain);
        java_security_ProtectionDomain_init_CodeSource_PermissionCollection = diff() //
                        .method(lower(11), Names._init_, Signatures._void_CodeSource_PermissionCollection) //
                        .notRequiredMethod(java_security_ProtectionDomain);

        java_security_AccessControlContext = knownKlass(Types.java_security_AccessControlContext);
        java_security_AccessControlContext_context = java_security_AccessControlContext.requireDeclaredField(Names.context, Types.java_security_ProtectionDomain_array);
        java_security_AccessControlContext_privilegedContext = diff() //
                        .field(VERSION_21_OR_LOWER, Names.privilegedContext, Types.java_security_AccessControlContext) //
                        .notRequiredField(java_security_AccessControlContext);
        java_security_AccessControlContext_isPrivileged = diff() //
                        .field(VERSION_21_OR_LOWER, Names.isPrivileged, Types._boolean) //
                        .notRequiredField(java_security_AccessControlContext);
        java_security_AccessControlContext_isAuthorized = diff() //
                        .field(VERSION_21_OR_LOWER, Names.isAuthorized, Types._boolean) //
                        .notRequiredField(java_security_AccessControlContext);
        java_security_AccessController = knownKlass(Types.java_security_AccessController);

        java_lang_invoke_MethodType = knownKlass(Types.java_lang_invoke_MethodType);
        java_lang_invoke_MethodType_ptypes = java_lang_invoke_MethodType.requireDeclaredField(Names.ptypes, Types.java_lang_Class_array);
        java_lang_invoke_MethodType_rtype = java_lang_invoke_MethodType.requireDeclaredField(Names.rtype, Types.java_lang_Class);

        java_lang_invoke_MemberName = knownKlass(Types.java_lang_invoke_MemberName);
        HIDDEN_VMINDEX = java_lang_invoke_MemberName.requireHiddenField(Names.HIDDEN_VMINDEX);
        HIDDEN_VMTARGET = java_lang_invoke_MemberName.requireHiddenField(Names.HIDDEN_VMTARGET);
        java_lang_invoke_MemberName_clazz = java_lang_invoke_MemberName.requireDeclaredField(Names.clazz, Types.java_lang_Class);
        java_lang_invoke_MemberName_name = java_lang_invoke_MemberName.requireDeclaredField(Names.name, Types.java_lang_String);
        java_lang_invoke_MemberName_type = java_lang_invoke_MemberName.requireDeclaredField(Names.type, Types.java_lang_Object);
        java_lang_invoke_MemberName_flags = java_lang_invoke_MemberName.requireDeclaredField(Names.flags, Types._int);

        java_lang_invoke_MethodHandle = knownKlass(Types.java_lang_invoke_MethodHandle);
        java_lang_invoke_MethodHandle_invokeExact = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.invokeExact, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_invoke = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.invoke, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeBasic = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.invokeBasic, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeWithArguments = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.invokeWithArguments, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToInterface = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.linkToInterface, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToSpecial = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.linkToSpecial, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToStatic = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.linkToStatic, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToVirtual = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.linkToVirtual, Signatures.Object_Object_array);
        java_lang_invoke_MethodHandle_asFixedArity = java_lang_invoke_MethodHandle.requireDeclaredMethod(Names.asFixedArity, Signatures.MethodHandle);
        java_lang_invoke_MethodHandle_type = java_lang_invoke_MethodHandle.requireDeclaredField(Names.type, Types.java_lang_invoke_MethodType);
        java_lang_invoke_MethodHandle_form = java_lang_invoke_MethodHandle.requireDeclaredField(Names.form, Types.java_lang_invoke_LambdaForm);

        java_lang_invoke_MethodHandles = knownKlass(Types.java_lang_invoke_MethodHandles);
        java_lang_invoke_MethodHandles_lookup = java_lang_invoke_MethodHandles.requireDeclaredMethod(Names.lookup, Signatures.MethodHandles$Lookup);

        java_lang_invoke_DirectMethodHandle = knownKlass(Types.java_lang_invoke_DirectMethodHandle);
        java_lang_invoke_DirectMethodHandle_member = java_lang_invoke_DirectMethodHandle.requireDeclaredField(Names.member, Types.java_lang_invoke_MemberName);

        // j.l.i.VarHandles is there in JDK9+, but we only need it to be known for 14+
        java_lang_invoke_VarHandles = diff() //
                        .klass(higher(14), Types.java_lang_invoke_VarHandles) //
                        .notRequiredKlass();
        java_lang_invoke_VarHandles_getStaticFieldFromBaseAndOffset = diff() //
                        .method(between(14, 20), Names.getStaticFieldFromBaseAndOffset, Signatures.Field_Object_long_Class) //
                        .method(VERSION_21_OR_HIGHER, Names.getStaticFieldFromBaseAndOffset, Signatures.Field_Class_long_Class) //
                        .notRequiredMethod(java_lang_invoke_VarHandles);

        java_lang_invoke_CallSite = knownKlass(Types.java_lang_invoke_CallSite);
        java_lang_invoke_CallSite_target = java_lang_invoke_CallSite.requireDeclaredField(Names.target, Types.java_lang_invoke_MethodHandle);

        java_lang_invoke_LambdaForm = knownKlass(Types.java_lang_invoke_LambdaForm);
        java_lang_invoke_LambdaForm_vmentry = java_lang_invoke_LambdaForm.requireDeclaredField(Names.vmentry, Types.java_lang_invoke_MemberName);
        java_lang_invoke_LambdaForm_isCompiled = java_lang_invoke_LambdaForm.requireDeclaredField(Names.isCompiled, Types._boolean);
        java_lang_invoke_LambdaForm_compileToBytecode = diff() //
                        .method(VERSION_8_OR_LOWER, Names.compileToBytecode, Signatures.MemberName) //
                        .method(VERSION_9_OR_HIGHER, Names.compileToBytecode, Signatures._void) //
                        .method(java_lang_invoke_LambdaForm);

        java_lang_invoke_MethodHandleNatives = knownKlass(Types.java_lang_invoke_MethodHandleNatives);
        java_lang_invoke_MethodHandleNatives_linkMethod = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Names.linkMethod,
                        Signatures.MemberName_Class_int_Class_String_Object_Object_array);
        java_lang_invoke_MethodHandleNatives_linkCallSite = diff() //
                        .method(VERSION_8_OR_LOWER, Names.linkCallSite, Signatures.MemberName_Object_Object_Object_Object_Object_Object_array) //
                        .method(VERSION_9_OR_HIGHER, Names.linkCallSite, Signatures.MemberName_Object_int_Object_Object_Object_Object_Object_array) //
                        .method(VERSION_19_OR_HIGHER, Names.linkCallSite, Signatures.MemberName_Object_Object_Object_Object_Object_Object_array) //
                        .method(java_lang_invoke_MethodHandleNatives);

        java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Names.linkMethodHandleConstant,
                        Signatures.MethodHandle_Class_int_Class_String_Object);
        java_lang_invoke_MethodHandleNatives_findMethodHandleType = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Names.findMethodHandleType, Signatures.MethodType_Class_Class);

        java_lang_ref_Finalizer = knownKlass(Types.java_lang_ref_Finalizer);
        java_lang_ref_Finalizer_register = java_lang_ref_Finalizer.requireDeclaredMethod(Names.register, Signatures._void_Object);

        java_lang_Object_wait = java_lang_Object.requireDeclaredMethod(Names.wait, Signatures._void_long);
        java_lang_Object_toString = java_lang_Object.requireDeclaredMethod(Names.toString, Signatures.String);

        // References
        java_lang_ref_Reference = knownKlass(Types.java_lang_ref_Reference);
        java_lang_ref_Reference_referent = java_lang_ref_Reference.requireDeclaredField(Names.referent, Types.java_lang_Object);
        java_lang_ref_Reference_enqueue = java_lang_ref_Reference.requireDeclaredMethod(Names.enqueue, Signatures._boolean);
        java_lang_ref_Reference_getFromInactiveFinalReference = diff() //
                        .method(VERSION_16_OR_HIGHER, Names.getFromInactiveFinalReference, Signatures.Object) //
                        .notRequiredMethod(java_lang_ref_Reference);
        java_lang_ref_Reference_clearInactiveFinalReference = diff() //
                        .method(VERSION_16_OR_HIGHER, Names.clearInactiveFinalReference, Signatures._void) //
                        .notRequiredMethod(java_lang_ref_Reference);

        java_lang_ref_Reference_discovered = java_lang_ref_Reference.requireDeclaredField(Names.discovered, Types.java_lang_ref_Reference);
        java_lang_ref_Reference_next = java_lang_ref_Reference.requireDeclaredField(Names.next, Types.java_lang_ref_Reference);
        java_lang_ref_Reference_queue = java_lang_ref_Reference.requireDeclaredField(Names.queue, Types.java_lang_ref_ReferenceQueue);
        java_lang_ref_ReferenceQueue = knownKlass(Types.java_lang_ref_ReferenceQueue);
        java_lang_ref_ReferenceQueue_NULL = diff() //
                        .field(VERSION_24_OR_LOWER, Names.NULL, Types.java_lang_ref_ReferenceQueue) //
                        .field(VERSION_25_OR_HIGHER, Names.NULL_QUEUE, Types.java_lang_ref_ReferenceQueue) //
                        .field(java_lang_ref_ReferenceQueue);

        java_lang_ref_WeakReference = knownKlass(Types.java_lang_ref_WeakReference);
        java_lang_ref_SoftReference = knownKlass(Types.java_lang_ref_SoftReference);
        java_lang_ref_PhantomReference = knownKlass(Types.java_lang_ref_PhantomReference);
        java_lang_ref_FinalReference = knownKlass(Types.java_lang_ref_FinalReference);
        HIDDEN_HOST_REFERENCE = java_lang_ref_Reference.requireHiddenField(Names.HIDDEN_HOST_REFERENCE);

        java_lang_AssertionStatusDirectives = knownKlass(Types.java_lang_AssertionStatusDirectives);
        java_lang_AssertionStatusDirectives_classes = java_lang_AssertionStatusDirectives.requireDeclaredField(Names.classes, Types.java_lang_String_array);
        java_lang_AssertionStatusDirectives_classEnabled = java_lang_AssertionStatusDirectives.requireDeclaredField(Names.classEnabled, Types._boolean_array);
        java_lang_AssertionStatusDirectives_packages = java_lang_AssertionStatusDirectives.requireDeclaredField(Names.packages, Types.java_lang_String_array);
        java_lang_AssertionStatusDirectives_packageEnabled = java_lang_AssertionStatusDirectives.requireDeclaredField(Names.packageEnabled, Types._boolean_array);
        java_lang_AssertionStatusDirectives_deflt = java_lang_AssertionStatusDirectives.requireDeclaredField(Names.deflt, Types._boolean);

        // Classes and Members that differ from Java 8 to 11

        if (getJavaVersion().java9OrLater()) {
            java_lang_System_initializeSystemClass = null;
            jdk_internal_loader_ClassLoaders = knownKlass(Types.jdk_internal_loader_ClassLoaders);
            jdk_internal_loader_ClassLoaders_platformClassLoader = jdk_internal_loader_ClassLoaders.requireDeclaredMethod(Names.platformClassLoader, Signatures.ClassLoader);
            jdk_internal_loader_ClassLoaders$PlatformClassLoader = knownKlass(Types.jdk_internal_loader_ClassLoaders$PlatformClassLoader);
            java_lang_StackWalker = knownKlass(Types.java_lang_StackWalker);
            java_lang_StackStreamFactory_AbstractStackWalker = knownKlass(Types.java_lang_StackStreamFactory_AbstractStackWalker);
            java_lang_StackStreamFactory_AbstractStackWalker_doStackWalk = java_lang_StackStreamFactory_AbstractStackWalker.requireDeclaredMethod(Names.doStackWalk,
                            Signatures.Object_long_int_int_int_int);

            java_lang_StackStreamFactory = knownKlass(Types.java_lang_StackStreamFactory);

            java_lang_ClassFrameInfo = diff() //
                            .klass(VERSION_22_OR_HIGHER, Types.java_lang_ClassFrameInfo) //
                            .notRequiredKlass();
            java_lang_ClassFrameInfo_classOrMemberName = diff() //
                            .field(VERSION_22_OR_HIGHER, Names.classOrMemberName, Types.java_lang_Object) //
                            .notRequiredField(java_lang_ClassFrameInfo);
            java_lang_ClassFrameInfo_flags = diff() //
                            .field(VERSION_22_OR_HIGHER, Names.flags, Types._int) //
                            .notRequiredField(java_lang_ClassFrameInfo);

            java_lang_StackFrameInfo = knownKlass(Types.java_lang_StackFrameInfo);
            java_lang_StackFrameInfo_memberName = diff() //
                            .field(JavaVersion.VersionRange.VERSION_9_TO_21, Names.memberName, Types.java_lang_Object) //
                            .notRequiredField(java_lang_StackFrameInfo);
            java_lang_StackFrameInfo_name = diff() //
                            .field(JavaVersion.VersionRange.VERSION_22_OR_HIGHER, Names.name, Types.java_lang_String) //
                            .notRequiredField(java_lang_StackFrameInfo);
            java_lang_StackFrameInfo_type = diff() //
                            .field(JavaVersion.VersionRange.VERSION_22_OR_HIGHER, Names.type, Types.java_lang_Object) //
                            .notRequiredField(java_lang_StackFrameInfo);
            java_lang_StackFrameInfo_bci = java_lang_StackFrameInfo.requireDeclaredField(Names.bci, Types._int);

            java_lang_invoke_ResolvedMethodName = diff() //
                            .klass(VERSION_22_OR_HIGHER, Types.java_lang_invoke_ResolvedMethodName) //
                            .notRequiredKlass();
            java_lang_invoke_ResolvedMethodName_vmholder = diff() //
                            .field(VERSION_22_OR_HIGHER, Names.vmholder, Types.java_lang_Class) //
                            .notRequiredField(java_lang_invoke_ResolvedMethodName);
            HIDDEN_VM_METHOD = diff() //
                            .field(VERSION_22_OR_HIGHER, Names.HIDDEN_VM_METHOD, Types.java_lang_Object) //
                            .maybeHiddenfield(java_lang_invoke_ResolvedMethodName);

            java_lang_System_initPhase1 = java_lang_System.requireDeclaredMethod(Names.initPhase1, Signatures._void);
            java_lang_System_initPhase2 = java_lang_System.requireDeclaredMethod(Names.initPhase2, Signatures._int_boolean_boolean);
            java_lang_System_initPhase3 = java_lang_System.requireDeclaredMethod(Names.initPhase3, Signatures._void);
        } else {
            java_lang_System_initializeSystemClass = java_lang_System.requireDeclaredMethod(Names.initializeSystemClass, Signatures._void);
            jdk_internal_loader_ClassLoaders = null;
            jdk_internal_loader_ClassLoaders_platformClassLoader = null;
            jdk_internal_loader_ClassLoaders$PlatformClassLoader = null;
            java_lang_StackWalker = null;
            java_lang_StackStreamFactory_AbstractStackWalker = null;
            java_lang_StackStreamFactory_AbstractStackWalker_doStackWalk = null;

            java_lang_StackStreamFactory = null;

            java_lang_ClassFrameInfo = null;
            java_lang_ClassFrameInfo_classOrMemberName = null;
            java_lang_ClassFrameInfo_flags = null;

            java_lang_StackFrameInfo = null;
            java_lang_StackFrameInfo_memberName = null;
            java_lang_StackFrameInfo_name = null;
            java_lang_StackFrameInfo_type = null;
            java_lang_StackFrameInfo_bci = null;

            java_lang_invoke_ResolvedMethodName = null;
            java_lang_invoke_ResolvedMethodName_vmholder = null;
            HIDDEN_VM_METHOD = null;

            java_lang_System_initPhase1 = null;
            java_lang_System_initPhase2 = null;
            java_lang_System_initPhase3 = null;
        }

        jdk_internal_loader_ClassLoaders$AppClassLoader = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_misc_Launcher$AppClassLoader) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_loader_ClassLoaders$AppClassLoader) //
                        .klass();

        if (getJavaVersion().modulesEnabled()) {
            java_lang_Module = knownKlass(Types.java_lang_Module);
            java_lang_Module_name = java_lang_Module.requireDeclaredField(Names.name, Types.java_lang_String);
            java_lang_Module_loader = java_lang_Module.requireDeclaredField(Names.loader, Types.java_lang_ClassLoader);
            java_lang_Module_descriptor = java_lang_Module.requireDeclaredField(Names.descriptor, Types.java_lang_module_ModuleDescriptor);
            HIDDEN_MODULE_ENTRY = java_lang_Module.requireHiddenField(Names.HIDDEN_MODULE_ENTRY);
            java_lang_module_ModuleDescriptor = knownKlass(Types.java_lang_module_ModuleDescriptor);
            java_lang_module_ModuleDescriptor_packages = java_lang_module_ModuleDescriptor.requireDeclaredField(Names.packages, Types.java_util_Set);
        } else {
            java_lang_Module = null;
            java_lang_Module_name = null;
            java_lang_Module_loader = null;
            java_lang_Module_descriptor = null;
            HIDDEN_MODULE_ENTRY = null;
            java_lang_module_ModuleDescriptor = null;
            java_lang_module_ModuleDescriptor_packages = null;
        }

        java_lang_Record = diff() //
                        .klass(VERSION_16_OR_HIGHER, Types.java_lang_Record) //
                        .notRequiredKlass();
        java_lang_reflect_RecordComponent = diff() //
                        .klass(VERSION_16_OR_HIGHER, Types.java_lang_reflect_RecordComponent) //
                        .notRequiredKlass();
        java_lang_reflect_RecordComponent_clazz = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.clazz, Types.java_lang_Class) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_name = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.name, Types.java_lang_String) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_type = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.type, Types.java_lang_Class) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_accessor = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.accessor, Types.java_lang_reflect_Method) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_signature = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.signature, Types.java_lang_String) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_annotations = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.annotations, Types._byte_array) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_typeAnnotations = diff() //
                        .field(VERSION_16_OR_HIGHER, Names.typeAnnotations, Types._byte_array) //
                        .notRequiredField(java_lang_reflect_RecordComponent);

        sun_reflect_MagicAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_MagicAccessorImpl) //
                        .klass(VERSION_9_TO_21, Types.jdk_internal_reflect_MagicAccessorImpl) //
                        .klass(VERSION_22_TO_23, Types.jdk_internal_reflect_SerializationConstructorAccessorImpl) //
                        .notRequiredKlass();
        sun_reflect_DelegatingClassLoader = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_DelegatingClassLoader) //
                        .klass(VERSION_9_TO_23, Types.jdk_internal_reflect_DelegatingClassLoader) //
                        .notRequiredKlass();

        sun_reflect_MethodAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_MethodAccessorImpl) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_reflect_MethodAccessorImpl) //
                        .klass();
        sun_reflect_ConstructorAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_ConstructorAccessorImpl) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_reflect_ConstructorAccessorImpl) //
                        .klass();

        sun_misc_Signal = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_misc_Signal) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_misc_Signal) //
                        .klass();
        sun_misc_Signal_name = sun_misc_Signal.requireDeclaredField(Names.name, Types.java_lang_String);
        sun_misc_Signal_init_String = sun_misc_Signal.requireDeclaredMethod(Names._init_, Signatures._void_String);
        sun_misc_NativeSignalHandler = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_misc_NativeSignalHandler) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_misc_Signal$NativeHandler) //
                        .klass();
        sun_misc_NativeSignalHandler_handler = sun_misc_NativeSignalHandler.requireDeclaredField(Names.handler, Types._long);
        sun_misc_SignalHandler = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_misc_SignalHandler) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_misc_Signal$Handler) //
                        .klass();
        sun_misc_SignalHandler_handle = diff() //
                        .method(VERSION_8_OR_LOWER, Names.handle, Signatures._void_sun_misc_Signal) //
                        .method(VERSION_9_OR_HIGHER, Names.handle, Signatures._void_jdk_internal_misc_Signal) //
                        .method(sun_misc_SignalHandler);
        sun_misc_SignalHandler_SIG_DFL = diff() //
                        .field(VERSION_8_OR_LOWER, Names.SIG_DFL, Types.sun_misc_SignalHandler) //
                        .field(VERSION_9_OR_HIGHER, Names.SIG_DFL, Types.jdk_internal_misc_Signal$Handler) //
                        .field(sun_misc_SignalHandler);
        sun_misc_SignalHandler_SIG_IGN = diff() //
                        .field(VERSION_8_OR_LOWER, Names.SIG_IGN, Types.sun_misc_SignalHandler) //
                        .field(VERSION_9_OR_HIGHER, Names.SIG_IGN, Types.jdk_internal_misc_Signal$Handler) //
                        .field(sun_misc_SignalHandler);

        sun_reflect_ConstantPool = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_ConstantPool) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_reflect_ConstantPool) //
                        .klass();
        sun_reflect_ConstantPool_constantPoolOop = sun_reflect_ConstantPool.requireDeclaredField(Names.constantPoolOop, Types.java_lang_Object);

        if (getJavaVersion().java8OrEarlier()) {
            java_lang_ref_Reference_pending = java_lang_ref_Reference.requireDeclaredField(Names.pending, Types.java_lang_ref_Reference);
            sun_misc_Cleaner = knownKlass(Types.sun_misc_Cleaner);
        } else {
            java_lang_ref_Reference_pending = null;
            sun_misc_Cleaner = null;
        }
        java_lang_ref_Reference_lock = diff() //
                        .field(VERSION_8_OR_LOWER, Names.lock, Types.java_lang_ref_Reference$Lock) //
                        .field(VERSION_9_OR_HIGHER, Names.processPendingLock, Types.java_lang_Object) //
                        .field(java_lang_ref_Reference);

        sun_reflect_Reflection = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_reflect_Reflection) //
                        .klass(VERSION_9_OR_HIGHER, Types.jdk_internal_reflect_Reflection) //
                        .klass();
        sun_reflect_Reflection_getCallerClass = sun_reflect_Reflection.requireDeclaredMethod(Names.getCallerClass, Signatures.Class);

        if (getJavaVersion().java11OrLater()) {
            if (getJavaVersion().java17OrEarlier()) {
                java_lang_invoke_MethodHandleNatives_linkDynamicConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Names.linkDynamicConstant,
                                Signatures.Object_Object_int_Object_Object_Object_Object);
            } else {
                java_lang_invoke_MethodHandleNatives_linkDynamicConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Names.linkDynamicConstant,
                                Signatures.Object_Object_Object_Object_Object_Object);
            }
        } else {
            java_lang_invoke_MethodHandleNatives_linkDynamicConstant = null;
        }

        ObjectKlass lambdaMetafactory = knownKlass(Types.java_lang_invoke_LambdaMetafactory);
        java_lang_invoke_LambdaMetafactory_metafactory = lambdaMetafactory.requireDeclaredMethod(Names.metafactory, Signatures.CallSite_Lookup_String_MethodType_MethodType_MethodHandle_MethodType);
        java_lang_invoke_LambdaMetafactory_altMetafactory = lambdaMetafactory.requireDeclaredMethod(Names.altMetafactory, Signatures.CallSite_Lookup_String_MethodType_Object_array);

        // Interop
        java_time_Duration = knownKlass(Types.java_time_Duration);
        java_time_Duration_seconds = java_time_Duration.requireDeclaredField(Names.seconds, Types._long);
        java_time_Duration_nanos = java_time_Duration.requireDeclaredField(Names.nanos, Types._int);

        java_time_Instant = knownKlass(Types.java_time_Instant);
        java_time_Instant_seconds = java_time_Instant.requireDeclaredField(Names.seconds, Types._long);
        java_time_Instant_nanos = java_time_Instant.requireDeclaredField(Names.nanos, Types._int);
        java_time_Instant_atZone = java_time_Instant.requireDeclaredMethod(Names.atZone, Signatures.ZonedDateTime_ZoneId);
        assert java_time_Instant_atZone.isFinalFlagSet() || java_time_Instant.isFinalFlagSet();
        java_time_Instant_ofEpochSecond = java_time_Instant.requireDeclaredMethod(Names.ofEpochSecond, Signatures.Instant_long_long);

        java_time_LocalTime = knownKlass(Types.java_time_LocalTime);
        java_time_LocalTime_hour = java_time_LocalTime.requireDeclaredField(Names.hour, Types._byte);
        java_time_LocalTime_minute = java_time_LocalTime.requireDeclaredField(Names.minute, Types._byte);
        java_time_LocalTime_second = java_time_LocalTime.requireDeclaredField(Names.second, Types._byte);
        java_time_LocalTime_nano = java_time_LocalTime.requireDeclaredField(Names.nano, Types._int);
        java_time_LocalTime_of = java_time_LocalTime.requireDeclaredMethod(Names.of, Signatures.LocalTime_int_int_int_int);

        java_time_LocalDateTime = knownKlass(Types.java_time_LocalDateTime);
        java_time_LocalDateTime_toLocalDate = java_time_LocalDateTime.requireDeclaredMethod(Names.toLocalDate, Signatures.LocalDate);
        java_time_LocalDateTime_toLocalTime = java_time_LocalDateTime.requireDeclaredMethod(Names.toLocalTime, Signatures.LocalTime);
        assert java_time_LocalDateTime_toLocalTime.isFinalFlagSet() || java_time_LocalDateTime.isFinalFlagSet();
        java_time_LocalDateTime_of = java_time_LocalDateTime.requireDeclaredMethod(Names.of, Signatures.LocalDateTime_LocalDate_LocalTime);

        java_time_LocalDate = knownKlass(Types.java_time_LocalDate);
        java_time_LocalDate_year = java_time_LocalDate.requireDeclaredField(Names.year, Types._int);
        java_time_LocalDate_month = diff() //
                        .field(VERSION_24_OR_LOWER, Names.month, Types._short) //
                        .field(VERSION_25_OR_HIGHER, Names.month, Types._byte) //
                        .field(java_time_LocalDate);
        java_time_LocalDate_day = diff() //
                        .field(VERSION_24_OR_LOWER, Names.day, Types._short) //
                        .field(VERSION_25_OR_HIGHER, Names.day, Types._byte) //
                        .field(java_time_LocalDate);
        java_time_LocalDate_of = java_time_LocalDate.requireDeclaredMethod(Names.of, Signatures.LocalDate_int_int_int);

        java_time_ZonedDateTime = knownKlass(Types.java_time_ZonedDateTime);
        java_time_ZonedDateTime_toLocalTime = java_time_ZonedDateTime.requireDeclaredMethod(Names.toLocalTime, Signatures.LocalTime);
        assert java_time_ZonedDateTime_toLocalTime.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();

        java_time_ZonedDateTime_toLocalDate = java_time_ZonedDateTime.requireDeclaredMethod(Names.toLocalDate, Signatures.LocalDate);
        assert java_time_ZonedDateTime_toLocalDate.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();

        java_time_ZonedDateTime_getZone = java_time_ZonedDateTime.requireDeclaredMethod(Names.getZone, Signatures.ZoneId);
        assert java_time_ZonedDateTime_getZone.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();
        java_time_ZonedDateTime_toInstant = java_time_ZonedDateTime.requireMethod(Names.toInstant, Signatures.Instant); // default
        assert java_time_ZonedDateTime_toInstant.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();
        java_time_ZonedDateTime_ofInstant = java_time_ZonedDateTime.requireDeclaredMethod(Names.ofInstant, Signatures.ZonedDateTime_Instant_ZoneId);

        java_util_Date = knownKlass(Types.java_util_Date);
        java_util_Date_toInstant = java_util_Date.requireDeclaredMethod(Names.toInstant, Signatures.Instant);
        java_util_Date_from = java_util_Date.requireDeclaredMethod(Names.from, Signatures.Date_Instant);
        java_time_ZoneId = knownKlass(Types.java_time_ZoneId);
        java_time_ZoneId_getId = java_time_ZoneId.requireDeclaredMethod(Names.getId, Signatures.String);
        java_time_ZoneId_of = java_time_ZoneId.requireDeclaredMethod(Names.of, Signatures.ZoneId_String);
        assert java_time_ZoneId_of.isStatic();

        java_util_Map = knownKlass(Types.java_util_Map);
        java_util_Map_get = java_util_Map.requireDeclaredMethod(Names.get, Signatures.Object_Object);
        java_util_Map_put = java_util_Map.requireDeclaredMethod(Names.put, Signatures.Object_Object_Object);
        java_util_Map_size = java_util_Map.requireDeclaredMethod(Names.size, Signatures._int);
        java_util_Map_remove = java_util_Map.requireDeclaredMethod(Names.remove, Signatures.Object_Object);
        java_util_Map_containsKey = java_util_Map.requireDeclaredMethod(Names.containsKey, Signatures._boolean_Object);
        java_util_Map_entrySet = java_util_Map.requireDeclaredMethod(Names.entrySet, Signatures.java_util_Set);
        assert java_util_Map.isInterface();

        java_util_HashMap = knownKlass(Types.java_util_HashMap);
        java_util_HashMap_init = java_util_HashMap.requireDeclaredMethod(Names._init_, Signatures._void_int);
        java_util_HashMap_put = java_util_HashMap.requireDeclaredMethod(Names.put, Signatures.Object_Object_Object);

        java_util_Map_Entry = knownKlass(Types.java_util_Map_Entry);
        java_util_Map_Entry_getKey = java_util_Map_Entry.requireDeclaredMethod(Names.getKey, Signatures.Object);
        java_util_Map_Entry_getValue = java_util_Map_Entry.requireDeclaredMethod(Names.getValue, Signatures.Object);
        java_util_Map_Entry_setValue = java_util_Map_Entry.requireDeclaredMethod(Names.setValue, Signatures.Object_Object);

        java_util_List = knownKlass(Types.java_util_List);
        java_util_List_get = java_util_List.requireDeclaredMethod(Names.get, Signatures.Object_int);
        java_util_List_set = java_util_List.requireDeclaredMethod(Names.set, Signatures.Object_int_Object);
        java_util_List_size = java_util_List.requireDeclaredMethod(Names.size, Signatures._int);
        java_util_List_add = java_util_List.requireDeclaredMethod(Names.add, Signatures._boolean_Object);
        java_util_List_remove = java_util_List.requireDeclaredMethod(Names.remove, Signatures.Object_int);
        assert java_util_List.isInterface();

        java_util_Set = knownKlass(Types.java_util_Set);
        java_util_Set_add = java_util_Set.requireDeclaredMethod(Names.add, Signatures._boolean_Object);
        assert java_util_Set.isInterface();
        if (getJavaVersion().java9OrLater()) {
            java_util_Set_of = java_util_Set.requireDeclaredMethod(Names.of, Signatures.Set_Object_array);
        } else {
            java_util_Set_of = null;
        }

        java_lang_Iterable = knownKlass(Types.java_lang_Iterable);
        java_lang_Iterable_iterator = java_lang_Iterable.requireDeclaredMethod(Names.iterator, Signatures.java_util_Iterator);
        assert java_lang_Iterable.isInterface();

        java_util_Iterator = knownKlass(Types.java_util_Iterator);
        java_util_Iterator_next = java_util_Iterator.requireDeclaredMethod(Names.next, Signatures.Object);
        java_util_Iterator_hasNext = java_util_Iterator.requireDeclaredMethod(Names.hasNext, Signatures._boolean);
        java_util_Iterator_remove = java_util_Iterator.requireDeclaredMethod(Names.remove, Signatures._void);
        assert java_util_Iterator.isInterface();

        java_util_Collection = knownKlass(Types.java_util_Collection);
        java_util_Collection_size = java_util_Collection.requireDeclaredMethod(Names.size, Signatures._int);
        java_util_Collection_toArray = java_util_Collection.requireDeclaredMethod(Names.toArray, Signatures.Object_array_Object_array);

        java_util_Optional = knownKlass(Types.java_util_Optional);
        java_util_Optional_EMPTY = java_util_Optional.requireDeclaredField(Names.EMPTY, Types.java_util_Optional);
        java_util_Optional_value = java_util_Optional.requireDeclaredField(Names.value, Types.java_lang_Object);

        java_util_concurrent_locks_AbstractOwnableSynchronizer = knownKlass(Types.java_util_concurrent_locks_AbstractOwnableSynchronizer);
        java_util_concurrent_locks_AbstractOwnableSynchronizer_exclusiveOwnerThread = java_util_concurrent_locks_AbstractOwnableSynchronizer.requireDeclaredField(Names.exclusiveOwnerThread,
                        Types.java_lang_Thread);
        java_util_concurrent_locks_ReentrantLock_Sync = knownKlass(Types.java_util_concurrent_locks_ReentrantLock_Sync);
        java_util_concurrent_locks_ReentrantReadWriteLock_Sync = knownKlass(Types.java_util_concurrent_locks_ReentrantReadWriteLock_Sync);

        java_math_BigInteger = knownKlass(Types.java_math_BigInteger);
        java_math_BigInteger_init = java_math_BigInteger.requireDeclaredMethod(Names._init_, Signatures._void_byte_array);
        java_math_BigInteger_toByteArray = java_math_BigInteger.requireDeclaredMethod(Names.toByteArray, Signatures._byte_array);

        java_math_BigDecimal = knownKlass(Types.java_math_BigDecimal);
        java_math_BigDecimal_init = java_math_BigDecimal.requireDeclaredMethod(Names._init_, Signatures._void_BigInteger_int_MathContext);

        java_math_MathContext = knownKlass(Types.java_math_MathContext);
        java_math_MathContext_init = java_math_MathContext.requireDeclaredMethod(Names._init_, Signatures._void_int);

        jdk_internal_misc_UnsafeConstants = diff() //
                        .klass(higher(13), Types.jdk_internal_misc_UnsafeConstants) //
                        .notRequiredKlass();
        if (jdk_internal_misc_UnsafeConstants != null) {
            jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0 = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Names.ADDRESS_SIZE0, Types._int);
            jdk_internal_misc_UnsafeConstants_PAGE_SIZE = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Names.PAGE_SIZE, Types._int);
            jdk_internal_misc_UnsafeConstants_BIG_ENDIAN = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Names.BIG_ENDIAN, Types._boolean);
            jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Names.UNALIGNED_ACCESS, Types._boolean);
            jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Names.DATA_CACHE_LINE_FLUSH_SIZE, Types._int);
        } else {
            jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0 = null;
            jdk_internal_misc_UnsafeConstants_PAGE_SIZE = null;
            jdk_internal_misc_UnsafeConstants_BIG_ENDIAN = null;
            jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS = null;
            jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE = null;
        }

        if (getJavaVersion().java9OrLater()) {
            jdk_internal_module_ModuleLoaderMap = knownKlass(Types.jdk_internal_module_ModuleLoaderMap);
            jdk_internal_module_ModuleLoaderMap_bootModules = jdk_internal_module_ModuleLoaderMap.requireDeclaredMethod(Names.bootModules, Signatures.java_util_Set);
            jdk_internal_module_ModuleLoaderMap_platformModules = jdk_internal_module_ModuleLoaderMap.requireDeclaredMethod(Names.platformModules, Signatures.java_util_Set);
            jdk_internal_module_SystemModuleFinders = knownKlass(Types.jdk_internal_module_SystemModuleFinders);
            jdk_internal_module_SystemModuleFinders_of = jdk_internal_module_SystemModuleFinders.requireDeclaredMethod(Names.of, Signatures.ModuleFinder_SystemModules);
            jdk_internal_module_SystemModuleFinders_ofSystem = jdk_internal_module_SystemModuleFinders.requireDeclaredMethod(Names.ofSystem, Signatures.ModuleFinder);
            jdk_internal_module_ModulePath = knownKlass(Types.jdk_internal_module_ModulePath);
            jdk_internal_module_ModulePath_of = jdk_internal_module_ModulePath.requireDeclaredMethod(Names.of, Signatures.ModuleFinder_Path_array);
            java_lang_module_ModuleFinder = knownKlass(Types.java_lang_module_ModuleFinder);
            java_lang_module_ModuleFinder_compose = java_lang_module_ModuleFinder.requireDeclaredMethod(Names.compose, Signatures.ModuleFinder_ModuleFinder_array);
            jdk_internal_module_Modules = knownKlass(Types.jdk_internal_module_Modules);
            jdk_internal_module_Modules_defineModule = jdk_internal_module_Modules.requireDeclaredMethod(Names.defineModule, Signatures.Module_ClassLoader_ModuleDescriptor_URI);
            jdk_internal_module_Modules_transformedByAgent = jdk_internal_module_Modules.requireDeclaredMethod(Names.transformedByAgent, Signatures._void_Module);
        } else {
            jdk_internal_module_ModuleLoaderMap = null;
            jdk_internal_module_ModuleLoaderMap_bootModules = null;
            jdk_internal_module_ModuleLoaderMap_platformModules = null;
            jdk_internal_module_SystemModuleFinders = null;
            jdk_internal_module_SystemModuleFinders_of = null;
            jdk_internal_module_SystemModuleFinders_ofSystem = null;
            jdk_internal_module_ModulePath = null;
            jdk_internal_module_ModulePath_of = null;
            java_lang_module_ModuleFinder = null;
            java_lang_module_ModuleFinder_compose = null;
            jdk_internal_module_Modules = null;
            jdk_internal_module_Modules_defineModule = null;
            jdk_internal_module_Modules_transformedByAgent = null;
        }

        if (getJavaVersion().java20OrLater()) {
            jdk_internal_foreign_abi_VMStorage = knownKlass(Types.jdk_internal_foreign_abi_VMStorage);
            jdk_internal_foreign_abi_VMStorage_type = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Names.type, Types._byte);
            jdk_internal_foreign_abi_VMStorage_segmentMaskOrSize = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Names.segmentMaskOrSize, Types._short);
            jdk_internal_foreign_abi_VMStorage_indexOrOffset = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Names.indexOrOffset, Types._int);
            jdk_internal_foreign_abi_NativeEntryPoint = knownKlass(Types.jdk_internal_foreign_abi_NativeEntryPoint);
            jdk_internal_foreign_abi_NativeEntryPoint_downcallStubAddress = jdk_internal_foreign_abi_NativeEntryPoint.requireDeclaredField(Names.downcallStubAddress, Types._long);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs = knownKlass(Types.jdk_internal_foreign_abi_UpcallLinker_CallRegs);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_argRegs = jdk_internal_foreign_abi_UpcallLinker_CallRegs.requireDeclaredField(Names.argRegs, Types.jdk_internal_foreign_abi_VMStorage_array);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_retRegs = jdk_internal_foreign_abi_UpcallLinker_CallRegs.requireDeclaredField(Names.retRegs, Types.jdk_internal_foreign_abi_VMStorage_array);
        } else {
            // also exists in a different shape in 19 but we don't support that
            jdk_internal_foreign_abi_VMStorage = null;
            jdk_internal_foreign_abi_VMStorage_type = null;
            jdk_internal_foreign_abi_VMStorage_segmentMaskOrSize = null;
            jdk_internal_foreign_abi_VMStorage_indexOrOffset = null;
            jdk_internal_foreign_abi_NativeEntryPoint = null;
            jdk_internal_foreign_abi_NativeEntryPoint_downcallStubAddress = null;
            jdk_internal_foreign_abi_UpcallLinker_CallRegs = null;
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_argRegs = null;
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_retRegs = null;
        }

        jdk_internal_module_ModuleLoaderMap_Modules = diff() //
                        .klass(VERSION_17_OR_HIGHER, Types.jdk_internal_module_ModuleLoaderMap_Modules) //
                        .notRequiredKlass();
        jdk_internal_module_ModuleLoaderMap_Modules_clinit = diff() //
                        .method(VERSION_17_OR_HIGHER, Names._clinit_, Signatures._void) //
                        .notRequiredMethod(jdk_internal_module_ModuleLoaderMap_Modules);

        interopDispatch = new InteropKlassesDispatch(this);

        tRegexSupport = context.getLanguage().useTRegex() ? new TRegexSupport() : null;
    }

    private static void initializeEspressoClassInHierarchy(ObjectKlass klass) {
        klass.initializeEspressoClass();
        if (klass.getSuperKlass() != null) {
            initializeEspressoClassInHierarchy(klass.getSuperKlass());
        }
        for (ObjectKlass k : klass.getSuperInterfaces()) {
            initializeEspressoClassInHierarchy(k);
        }
    }

    /**
     * This method registers known classes that are NOT in {@code java.base} module after VM
     * initialization (/ex: {@code java.management}, {@code java.desktop}, etc...), or classes whose
     * hierarchy loads classes to early in the boot process..
     * <p>
     * Espresso's Polyglot API (polyglot.jar) is injected on the boot CP, must be loaded after
     * modules initialization.
     * <p>
     * The classes in module java.management become known after modules initialization.
     */
    public void postSystemInit() {
        // java.management
        java_lang_management_MemoryUsage = loadKlassWithBootClassLoader(Types.java_lang_management_MemoryUsage);

        java_lang_management_ThreadInfo = loadKlassWithBootClassLoader(Types.java_lang_management_ThreadInfo);

        sun_management_ManagementFactory = diff() //
                        .klass(VERSION_8_OR_LOWER, Types.sun_management_ManagementFactory) //
                        .klass(VERSION_9_OR_HIGHER, Types.sun_management_ManagementFactoryHelper) //
                        .notRequiredKlass();
        if (sun_management_ManagementFactory != null) {
            // MemoryPoolMXBean createMemoryPool(String var0, boolean var1, long var2, long var4)
            sun_management_ManagementFactory_createMemoryPool = sun_management_ManagementFactory.requireDeclaredMethod(Names.createMemoryPool, Signatures.MemoryPoolMXBean_String_boolean_long_long);
            // MemoryManagerMXBean createMemoryManager(String var0)
            sun_management_ManagementFactory_createMemoryManager = sun_management_ManagementFactory.requireDeclaredMethod(Names.createMemoryManager, Signatures.MemoryManagerMXBean_String);
            // GarbageCollectorMXBean createGarbageCollector(String var0, String var1)
            sun_management_ManagementFactory_createGarbageCollector = sun_management_ManagementFactory.requireDeclaredMethod(Names.createGarbageCollector,
                            Signatures.GarbageCollectorMXBean_String_String);
        } else {
            // MemoryPoolMXBean createMemoryPool(String var0, boolean var1, long var2, long var4)
            sun_management_ManagementFactory_createMemoryPool = null;
            // MemoryManagerMXBean createMemoryManager(String var0)
            sun_management_ManagementFactory_createMemoryManager = null;
            // GarbageCollectorMXBean createGarbageCollector(String var0, String var1)
            sun_management_ManagementFactory_createGarbageCollector = null;
        }

        // used for class redefinition
        java_lang_reflect_Proxy = knownKlass(Types.java_lang_reflect_Proxy);

        // java.beans package only available if java.desktop module is present on JDK9+
        java_beans_ThreadGroupContext = loadKlassWithBootClassLoader(Types.java_beans_ThreadGroupContext);
        java_beans_Introspector = loadKlassWithBootClassLoader(Types.java_beans_Introspector);

        java_beans_ThreadGroupContext_init = java_beans_ThreadGroupContext != null ? java_beans_ThreadGroupContext.requireDeclaredMethod(Names._init_, Signatures._void) : null;
        java_beans_ThreadGroupContext_removeBeanInfo = java_beans_ThreadGroupContext != null ? java_beans_ThreadGroupContext.requireDeclaredMethod(Names.removeBeanInfo, Signatures._void_Class) : null;
        java_beans_Introspector_flushFromCaches = java_beans_Introspector != null ? java_beans_Introspector.requireDeclaredMethod(Names.flushFromCaches, Signatures._void_Class) : null;

        // sun.misc.Proxygenerator -> java.lang.reflect.Proxygenerator in JDK 9
        if (getJavaVersion().java8OrEarlier()) {
            sun_misc_ProxyGenerator = knownKlass(Types.sun_misc_ProxyGenerator);
            sun_misc_ProxyGenerator_generateProxyClass = sun_misc_ProxyGenerator.lookupDeclaredMethod(Names.generateProxyClass, Signatures._byte_array_String_Class_array_int);

            java_lang_reflect_ProxyGenerator = null;
            java_lang_reflect_ProxyGenerator_generateProxyClass = null;
        } else {
            sun_misc_ProxyGenerator = null;
            sun_misc_ProxyGenerator_generateProxyClass = null;

            java_lang_reflect_ProxyGenerator = knownKlass(Types.java_lang_reflect_ProxyGenerator);
            java_lang_reflect_ProxyGenerator_generateProxyClass = diff() //
                            .method(lower(13), Names.generateProxyClass, Signatures._byte_array_String_Class_array_int) //
                            .method(higher(14), Names.generateProxyClass, Signatures._byte_array_ClassLoader_String_List_int) //
                            .notRequiredMethod(java_lang_reflect_ProxyGenerator);
        }

        // when no agents are added, those classes might not be discoverable
        sun_instrument_InstrumentationImpl = loadPlatformKlassOrNull(Types.sun_instrument_InstrumentationImpl);
        if (sun_instrument_InstrumentationImpl != null) {
            sun_instrument_InstrumentationImpl_init = diff() //
                            .method(VERSION_20_OR_LOWER, Names._init_, Signatures._void_long_boolean_boolean) //
                            .method(VERSION_21_OR_HIGHER, Names._init_, Signatures._void_long_boolean_boolean_boolean) //
                            .method(sun_instrument_InstrumentationImpl);
            sun_instrument_InstrumentationImpl_loadClassAndCallPremain = sun_instrument_InstrumentationImpl.requireDeclaredMethod(Names.loadClassAndCallPremain, Signatures._void_String_String);
            sun_instrument_InstrumentationImpl_transform = diff() //
                            .method(VERSION_8_OR_LOWER, Names.transform, Signatures._byte_array_ClassLoader_String_Class_ProtectionDomain_byte_array_boolean) //
                            .method(VERSION_9_OR_HIGHER, Names.transform, Signatures._byte_array_Module_ClassLoader_String_Class_ProtectionDomain_byte_array_boolean) //
                            .method(sun_instrument_InstrumentationImpl);
        }
        java_lang_instrument_ClassDefinition = loadPlatformKlassOrNull(Types.java_lang_instrument_ClassDefinition);
        if (java_lang_instrument_ClassDefinition != null) {
            java_lang_instrument_UnmodifiableClassException = knownPlatformKlass(Types.java_lang_instrument_UnmodifiableClassException);
            java_lang_instrument_ClassDefinition_getDefinitionClass = java_lang_instrument_ClassDefinition.requireDeclaredMethod(Names.getDefinitionClass, Signatures.Class);
            java_lang_instrument_ClassDefinition_getDefinitionClassFile = java_lang_instrument_ClassDefinition.requireDeclaredMethod(Names.getDefinitionClassFile,
                            Signatures._byte_array);
        }
        // Load Espresso's Polyglot API.
        boolean polyglotSupport = getContext().getEspressoEnv().Polyglot;
        this.polyglot = polyglotSupport ? new PolyglotSupport() : null;

        // Load Espresso's JVMCI implementation.
        if (getLanguage().isInternalJVMCIEnabled()) {
            this.jvmci = new JVMCISupport();
        } else {
            this.jvmci = null;
        }

        JImageExtensions jImageExtensions = getLanguage().getJImageExtensions();
        if (jImageExtensions != null && getJavaVersion().java9OrLater()) {
            for (Map.Entry<String, Set<String>> entry : jImageExtensions.getExtensions().entrySet()) {
                Symbol<Name> name = getNames().lookup(entry.getKey());
                if (name == null) {
                    continue;
                }
                ModuleTable.ModuleEntry moduleEntry = getRegistries().findUniqueModule(name);
                if (moduleEntry != null) {
                    extendModulePackages(moduleEntry, entry.getValue());
                }
            }
        }

        // Continuations
        boolean continuumSupport = getLanguage().isContinuumEnabled();
        this.continuum = continuumSupport ? new ContinuumSupport() : null;
    }

    private void extendModulePackages(ModuleTable.ModuleEntry moduleEntry, Set<String> extraPackages) {
        StaticObject moduleDescriptor = java_lang_Module_descriptor.getObject(moduleEntry.module());
        StaticObject origPackages = java_lang_module_ModuleDescriptor_packages.getObject(moduleDescriptor);
        StaticObject newPackages = extendedStringSet(origPackages, extraPackages);
        java_lang_module_ModuleDescriptor_packages.setObject(moduleDescriptor, newPackages);
    }

    public @JavaType(Set.class) StaticObject extendedStringSet(@JavaType(Set.class) StaticObject original, Collection<String> extraStrings) {
        int origSize = (int) java_util_Collection_size.invokeDirectInterface(original);
        StaticObject stringArray = java_lang_String.allocateReferenceArray(origSize + extraStrings.size());
        StaticObject toArrayResult = (StaticObject) java_util_Collection_toArray.invokeDirectInterface(original, stringArray);
        assert toArrayResult == stringArray;
        StaticObject[] unwrappedStringArray = stringArray.unwrap(getLanguage());
        int idx = origSize;
        for (String extraPackage : extraStrings) {
            assert StaticObject.isNull(unwrappedStringArray[idx]);
            unwrappedStringArray[idx++] = toGuestString(extraPackage);
        }
        return (StaticObject) java_util_Set_of.invokeDirectStatic(stringArray);
    }

    private DiffVersionLoadHelper diff() {
        return new DiffVersionLoadHelper(this);
    }

    // Checkstyle: stop field name check

    public final ObjectKlass java_lang_Object;
    public final ArrayKlass java_lang_Object_array;
    /*
     * Though only used when Continuum is enabled, the hashcode is used during VM initialization, so
     * it cannot be put in the ContinuumSupport object.
     */
    public final Field HIDDEN_SYSTEM_IHASHCODE;

    public final ObjectKlass java_lang_String;
    public final ArrayKlass java_lang_String_array;
    public final ObjectKlass java_lang_Class;
    public final ObjectKlass java_lang_CharSequence;
    public final Field HIDDEN_MIRROR_KLASS;
    public final Field HIDDEN_PROTECTION_DOMAIN;
    public final Field HIDDEN_SIGNERS;
    public final Field java_lang_Class_module;
    public final Field java_lang_Class_classLoader;
    public final Field java_lang_Class_modifiers;
    public final Field java_lang_Class_classFileAccessFlags;
    public final Field java_lang_Class_primitive;
    public final Field sun_reflect_ConstantPool_constantPoolOop;
    public final ArrayKlass java_lang_Class_array;
    public final Method java_lang_Class_getName;
    public final Method java_lang_Class_getSimpleName;
    public final Method java_lang_Class_getTypeName;
    public final Method java_lang_Class_forName_String;
    public final Method java_lang_Class_forName_String_boolean_ClassLoader;
    public final Field java_lang_Class_classRedefinedCount;
    public final Field java_lang_Class_name;
    public final Field java_lang_Class_componentType;
    public final Field java_lang_Class_classData;

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

    public final ObjectKlass java_lang_Number;

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
    public final Field java_lang_String_coder;
    public final Field java_lang_String_COMPACT_STRINGS;
    public final Method java_lang_String_hashCode;
    public final Method java_lang_String_length;
    public final Method java_lang_String_toCharArray;
    public final Method java_lang_String_indexOf;
    public final Method java_lang_String_init_char_array;

    // Charsequence
    public final Method java_lang_CharSequence_toString;

    public final ObjectKlass java_lang_ClassLoader;
    public final Field java_lang_ClassLoader_parent;
    public final Field java_lang_ClassLoader_unnamedModule;
    public final Field java_lang_ClassLoader_name;
    public final Field java_lang_ClassLoader_nameAndId;
    public final ObjectKlass java_lang_ClassLoader$NativeLibrary;
    public final Method java_lang_ClassLoader_checkPackageAccess;
    public final Method java_lang_ClassLoader$NativeLibrary_getFromClass;
    public final Method java_lang_ClassLoader_findNative;
    public final Method java_lang_ClassLoader_getSystemClassLoader;
    public final Field HIDDEN_CLASS_LOADER_REGISTRY;
    public final Method java_lang_ClassLoader_getResourceAsStream;
    public final Method java_lang_ClassLoader_loadClass;

    public final ObjectKlass jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl;
    public final Field jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle;

    public final ObjectKlass jdk_internal_loader_NativeLibraries$NativeLibraryImpl;
    public final Field jdk_internal_loader_NativeLibraries$NativeLibraryImpl_handle;
    public final Field jdk_internal_loader_NativeLibraries$NativeLibraryImpl_jniVersion;

    public final ObjectKlass jdk_internal_util_ArraysSupport;
    public final Method jdk_internal_util_ArraysSupport_vectorizedMismatch;
    public final ObjectKlass java_net_URL;

    public final ObjectKlass sun_launcher_LauncherHelper;
    public final Method sun_launcher_LauncherHelper_printHelpMessage;
    public final Field sun_launcher_LauncherHelper_ostream;

    public final ObjectKlass jdk_internal_loader_ClassLoaders;
    public final Method jdk_internal_loader_ClassLoaders_platformClassLoader;
    public final ObjectKlass jdk_internal_loader_ClassLoaders$PlatformClassLoader;
    public final ObjectKlass jdk_internal_loader_ClassLoaders$AppClassLoader;

    public final ObjectKlass java_lang_Module;
    public final Field java_lang_Module_name;
    public final Field java_lang_Module_loader;
    public final Field java_lang_Module_descriptor;
    public final Field HIDDEN_MODULE_ENTRY;
    public final ObjectKlass java_lang_module_ModuleDescriptor;
    public final Field java_lang_module_ModuleDescriptor_packages;

    public final ObjectKlass java_lang_Record;
    public final ObjectKlass java_lang_reflect_RecordComponent;
    public final Field java_lang_reflect_RecordComponent_clazz;
    public final Field java_lang_reflect_RecordComponent_name;
    public final Field java_lang_reflect_RecordComponent_type;
    public final Field java_lang_reflect_RecordComponent_accessor;
    public final Field java_lang_reflect_RecordComponent_signature;
    public final Field java_lang_reflect_RecordComponent_annotations;
    public final Field java_lang_reflect_RecordComponent_typeAnnotations;

    public final ObjectKlass java_lang_AssertionStatusDirectives;
    public final Field java_lang_AssertionStatusDirectives_classes;
    public final Field java_lang_AssertionStatusDirectives_classEnabled;
    public final Field java_lang_AssertionStatusDirectives_packages;
    public final Field java_lang_AssertionStatusDirectives_packageEnabled;
    public final Field java_lang_AssertionStatusDirectives_deflt;

    public final ObjectKlass java_lang_reflect_Executable;

    public final ObjectKlass java_lang_reflect_Constructor;
    public final Method java_lang_reflect_Constructor_init;
    public final Field HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_CONSTRUCTOR_KEY;
    public final Field java_lang_reflect_Constructor_clazz;
    public final Field java_lang_reflect_Constructor_root;
    public final Field java_lang_reflect_Constructor_parameterTypes;
    public final Field java_lang_reflect_Constructor_signature;

    public final ObjectKlass sun_reflect_MagicAccessorImpl;
    public final ObjectKlass sun_reflect_DelegatingClassLoader;

    public final ObjectKlass java_lang_reflect_Method;
    public final Method java_lang_reflect_Method_init;
    public final Field HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_METHOD_KEY;
    public final Field java_lang_reflect_Method_root;
    public final Field java_lang_reflect_Method_clazz;
    public final Field java_lang_reflect_Method_parameterTypes;

    public final ObjectKlass sun_reflect_MethodAccessorImpl;
    public final ObjectKlass sun_reflect_ConstructorAccessorImpl;

    public final ObjectKlass java_lang_reflect_Parameter;
    public final ObjectKlass java_lang_reflect_ParameterizedType;
    public final Method java_lang_reflect_ParameterizedType_getRawType;

    public final ObjectKlass java_lang_reflect_Field;
    public final Method java_lang_reflect_Field_init;
    public final Field HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
    public final Field HIDDEN_FIELD_KEY;
    public final Field java_lang_reflect_Field_root;
    public final Field java_lang_reflect_Field_class;
    public final Field java_lang_reflect_Field_name;
    public final Field java_lang_reflect_Field_type;

    public final ObjectKlass java_lang_Shutdown;
    public final Method java_lang_Shutdown_shutdown;

    public final ObjectKlass java_lang_Exception;
    public final ObjectKlass java_lang_reflect_InvocationTargetException;
    public final ObjectKlass java_lang_NegativeArraySizeException;
    public final ObjectKlass java_lang_IllegalArgumentException;
    public final ObjectKlass java_lang_IllegalMonitorStateException;
    public final ObjectKlass java_lang_IllegalStateException;
    public final ObjectKlass java_lang_NullPointerException;
    public final Field java_lang_NullPointerException_extendedMessageState;
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
    public final ObjectKlass java_util_NoSuchElementException;
    public final ObjectKlass java_lang_NumberFormatException;

    public final ObjectKlass java_lang_Throwable;
    public final Method java_lang_Throwable_getStackTrace;
    public final Method java_lang_Throwable_getMessage;
    public final Method java_lang_Throwable_getCause;
    public final Method java_lang_Throwable_initCause;
    public final Method java_lang_Throwable_printStackTrace;
    public final Field HIDDEN_FRAMES;
    public final Field HIDDEN_EXCEPTION_WRAPPER;
    public final Field java_lang_Throwable_backtrace;
    public final Field java_lang_Throwable_stackTrace;
    public final Field java_lang_Throwable_detailMessage;
    public final Field java_lang_Throwable_cause;
    public final Field java_lang_Throwable_depth;

    public final ObjectKlass java_lang_Error;
    public final ObjectKlass java_lang_NoSuchFieldError;
    public final ObjectKlass java_lang_NoSuchMethodError;
    public final ObjectKlass java_lang_IllegalAccessError;
    public final ObjectKlass java_lang_IncompatibleClassChangeError;
    public final ObjectKlass java_lang_BootstrapMethodError;

    public final ObjectKlass java_lang_StackTraceElement;
    public final Method java_lang_StackTraceElement_init;
    public final Field java_lang_StackTraceElement_declaringClassObject;
    public final Field java_lang_StackTraceElement_classLoaderName;
    public final Field java_lang_StackTraceElement_moduleName;
    public final Field java_lang_StackTraceElement_moduleVersion;
    public final Field java_lang_StackTraceElement_declaringClass;
    public final Field java_lang_StackTraceElement_methodName;
    public final Field java_lang_StackTraceElement_fileName;
    public final Field java_lang_StackTraceElement_lineNumber;

    public final ObjectKlass java_security_PrivilegedActionException;
    public final Method java_security_PrivilegedActionException_init_Exception;

    public final ObjectKlass java_io_InputStream;
    public final Method java_io_InputStream_read;
    public final Method java_io_InputStream_close;
    public final Method java_io_InputStream_skip;

    public final ObjectKlass java_io_PrintStream;
    public final Method java_io_PrintStream_println;

    public final ObjectKlass java_nio_file_Path;
    public final ObjectKlass java_nio_file_Paths;
    public final Method java_nio_file_Paths_get;

    public final ObjectKlass java_nio_file_FileAlreadyExistsException;
    public final ObjectKlass java_nio_file_DirectoryNotEmptyException;
    public final ObjectKlass java_nio_file_AtomicMoveNotSupportedException;
    public final ObjectKlass java_nio_file_AccessDeniedException;
    public final ObjectKlass java_nio_file_NoSuchFileException;
    public final ObjectKlass java_nio_file_NotDirectoryException;
    public final ObjectKlass java_nio_file_InvalidPathException;
    public final ObjectKlass java_nio_file_NotLinkException;

    public final Method sun_nio_ch_NativeThread_isNativeThread;
    public final Method sun_nio_ch_NativeThread_current0;
    public final Method sun_nio_ch_NativeThread_signal;
    public final Method sun_nio_ch_NativeThread_init;

    // Array support.
    public final ObjectKlass java_lang_Cloneable;
    public final ObjectKlass java_io_Serializable;

    public final ObjectKlass sun_nio_ch_DirectBuffer;
    public final ObjectKlass java_nio_Buffer;
    public final Method java_nio_Buffer_isReadOnly;
    public final Field java_nio_Buffer_address;
    public final Field java_nio_Buffer_capacity;
    public final Method java_nio_Buffer_limit;

    public final ObjectKlass java_nio_ByteBuffer;
    public final Method java_nio_ByteBuffer_wrap;
    public final Method java_nio_ByteBuffer_get;
    public final Method java_nio_ByteBuffer_getByte;
    public final Method java_nio_ByteBuffer_getShort;
    public final Method java_nio_ByteBuffer_getInt;
    public final Method java_nio_ByteBuffer_getLong;
    public final Method java_nio_ByteBuffer_getFloat;
    public final Method java_nio_ByteBuffer_getDouble;
    public final Method java_nio_ByteBuffer_putByte;
    public final Method java_nio_ByteBuffer_putShort;
    public final Method java_nio_ByteBuffer_putInt;
    public final Method java_nio_ByteBuffer_putLong;
    public final Method java_nio_ByteBuffer_putFloat;
    public final Method java_nio_ByteBuffer_putDouble;
    public final Method java_nio_ByteBuffer_order;
    public final Method java_nio_ByteBuffer_setOrder;
    public final ObjectKlass java_nio_DirectByteBuffer;
    public final Method java_nio_DirectByteBuffer_init_long_int;
    public final ObjectKlass java_nio_ByteOrder;
    public final Field java_nio_ByteOrder_LITTLE_ENDIAN;
    public final Field java_nio_ByteOrder_BIG_ENDIAN;

    public final ObjectKlass java_lang_BaseVirtualThread;
    public final ObjectKlass java_lang_ThreadGroup;
    public final Method java_lang_ThreadGroup_add;
    public final Method java_lang_Thread_dispatchUncaughtException;
    public final Field java_lang_ThreadGroup_maxPriority;
    public final ObjectKlass java_lang_Thread;
    public final ObjectKlass java_lang_Thread$FieldHolder;
    public final Field java_lang_Thread_holder;
    public final Field java_lang_Thread_threadStatus;
    public final Field java_lang_Thread$FieldHolder_threadStatus;
    public final Field java_lang_Thread_threadGroup;
    public final Field java_lang_Thread$FieldHolder_group;
    public final Field java_lang_Thread_tid;
    public final Field java_lang_Thread_eetop;
    public final ObjectKlass java_lang_Thread$Constants;
    public final Field java_lang_Thread$Constants_VTHREAD_GROUP;
    public final Field java_lang_Thread_contextClassLoader;
    public final Method java_lang_Thread_init_ThreadGroup_Runnable;
    public final Method java_lang_Thread_init_ThreadGroup_String;
    public final Method java_lang_Thread_interrupt;
    public final Method java_lang_Thread_exit;
    public final Method java_lang_Thread_run;
    public final Method java_lang_Thread_checkAccess;
    public final Method java_lang_Thread_stop;
    public final Method java_lang_Thread_getThreadGroup;
    public final Field HIDDEN_HOST_THREAD;
    public final Field HIDDEN_ESPRESSO_MANAGED;
    public final Field HIDDEN_TO_NATIVE_LOCK;
    public final Field HIDDEN_INTERRUPTED;
    public final Field HIDDEN_THREAD_UNPARK_SIGNALS;
    public final Field HIDDEN_THREAD_PARK_LOCK;
    public final Field HIDDEN_DEPRECATION_SUPPORT;
    public final Field HIDDEN_THREAD_PENDING_MONITOR;
    public final Field HIDDEN_THREAD_WAITING_MONITOR;
    public final Field HIDDEN_THREAD_BLOCKED_COUNT;
    public final Field HIDDEN_THREAD_WAITED_COUNT;
    public final Field HIDDEN_THREAD_DEPTH_FIRST_NUMBER;
    public final Field HIDDEN_THREAD_SCOPED_VALUE_CACHE;

    public final Field java_lang_Thread_name;
    public final Field java_lang_Thread_priority;
    public final Field java_lang_Thread$FieldHolder_priority;
    public final Field java_lang_Thread_daemon;
    public final Field java_lang_Thread$FieldHolder_daemon;
    public final Field java_lang_Thread_inheritedAccessControlContext;

    public final ObjectKlass java_lang_ref_Finalizer$FinalizerThread;
    public final ObjectKlass java_lang_ref_Reference$ReferenceHandler;
    public final ObjectKlass misc_InnocuousThread;

    public final ObjectKlass sun_reflect_ConstantPool;

    public final ObjectKlass sun_misc_Signal;
    public final Field sun_misc_Signal_name;
    public final Method sun_misc_Signal_init_String;
    public final ObjectKlass sun_misc_NativeSignalHandler;
    public final Field sun_misc_NativeSignalHandler_handler;
    public final ObjectKlass sun_misc_SignalHandler;
    public final Method sun_misc_SignalHandler_handle;
    public final Field sun_misc_SignalHandler_SIG_DFL;
    public final Field sun_misc_SignalHandler_SIG_IGN;

    public final ObjectKlass java_lang_System;
    public final Method java_lang_System_initializeSystemClass;
    public final Method java_lang_System_initPhase1;
    public final Method java_lang_System_initPhase2;
    public final Method java_lang_System_initPhase3;
    public final Method java_lang_System_getProperty;
    public final Method java_lang_System_exit;
    public final Field java_lang_System_securityManager;
    public final Field java_lang_System_in;
    public final Field java_lang_System_out;
    public final Field java_lang_System_err;

    public final ObjectKlass jdk_internal_util_SystemProps_Raw;

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
    public final Field java_lang_invoke_MethodType_ptypes;
    public final Field java_lang_invoke_MethodType_rtype;

    public final ObjectKlass java_lang_invoke_MemberName;

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
    public final Method java_lang_invoke_MethodHandle_asFixedArity;
    public final Field java_lang_invoke_MethodHandle_type;
    public final Field java_lang_invoke_MethodHandle_form;

    public final ObjectKlass java_lang_invoke_DirectMethodHandle;
    public final Field java_lang_invoke_DirectMethodHandle_member;

    public final ObjectKlass java_lang_invoke_MethodHandles;
    public final Method java_lang_invoke_MethodHandles_lookup;

    public final ObjectKlass java_lang_invoke_VarHandles;
    public final Method java_lang_invoke_VarHandles_getStaticFieldFromBaseAndOffset;

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
    public final Method java_lang_invoke_MethodHandleNatives_linkDynamicConstant;

    public final Method java_lang_invoke_LambdaMetafactory_metafactory;
    public final Method java_lang_invoke_LambdaMetafactory_altMetafactory;

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
    public final Method java_lang_ref_Reference_enqueue;
    public final Method java_lang_ref_Reference_getFromInactiveFinalReference;
    public final Method java_lang_ref_Reference_clearInactiveFinalReference;
    public final ObjectKlass java_lang_ref_WeakReference;
    public final ObjectKlass java_lang_ref_SoftReference;
    public final ObjectKlass java_lang_ref_PhantomReference;
    public final ObjectKlass java_lang_ref_FinalReference;
    public final ObjectKlass sun_misc_Cleaner;

    public final Field HIDDEN_HOST_REFERENCE;

    public final ObjectKlass java_lang_ref_ReferenceQueue;
    public final Field java_lang_ref_ReferenceQueue_NULL;
    public final ObjectKlass sun_reflect_Reflection;
    public final Method sun_reflect_Reflection_getCallerClass;

    public final ObjectKlass java_lang_StackWalker;
    public final ObjectKlass java_lang_StackStreamFactory_AbstractStackWalker;
    public final ObjectKlass java_lang_StackStreamFactory;
    public final Method java_lang_StackStreamFactory_AbstractStackWalker_doStackWalk;

    public final ObjectKlass java_lang_ClassFrameInfo;
    public final Field java_lang_ClassFrameInfo_classOrMemberName;
    public final Field java_lang_ClassFrameInfo_flags;

    public final ObjectKlass java_lang_StackFrameInfo;
    public final Field java_lang_StackFrameInfo_memberName;
    public final Field java_lang_StackFrameInfo_name;
    public final Field java_lang_StackFrameInfo_type;
    public final Field java_lang_StackFrameInfo_bci;

    public final ObjectKlass java_lang_invoke_ResolvedMethodName;
    public final Field java_lang_invoke_ResolvedMethodName_vmholder;
    public final Field HIDDEN_VM_METHOD;

    // Module system
    public final ObjectKlass jdk_internal_module_ModuleLoaderMap;
    public final Method jdk_internal_module_ModuleLoaderMap_bootModules;
    public final Method jdk_internal_module_ModuleLoaderMap_platformModules;
    public final ObjectKlass jdk_internal_module_ModuleLoaderMap_Modules;
    public final Method jdk_internal_module_ModuleLoaderMap_Modules_clinit;
    public final ObjectKlass jdk_internal_module_SystemModuleFinders;
    public final Method jdk_internal_module_SystemModuleFinders_of;
    public final Method jdk_internal_module_SystemModuleFinders_ofSystem;
    public final ObjectKlass jdk_internal_module_ModulePath;
    public final Method jdk_internal_module_ModulePath_of;
    public final ObjectKlass java_lang_module_ModuleFinder;
    public final Method java_lang_module_ModuleFinder_compose;
    public final ObjectKlass jdk_internal_module_Modules;
    public final Method jdk_internal_module_Modules_defineModule;
    public final Method jdk_internal_module_Modules_transformedByAgent;

    // Interop conversions.
    public final ObjectKlass java_time_Duration;
    public final Field java_time_Duration_seconds;
    public final Field java_time_Duration_nanos;

    public final ObjectKlass java_time_Instant;
    public final Field java_time_Instant_seconds;
    public final Field java_time_Instant_nanos;
    public final Method java_time_Instant_atZone;
    public final Method java_time_Instant_ofEpochSecond;

    public final ObjectKlass java_time_LocalTime;
    public final Field java_time_LocalTime_hour;
    public final Field java_time_LocalTime_minute;
    public final Field java_time_LocalTime_second;
    public final Field java_time_LocalTime_nano;
    public final Method java_time_LocalTime_of;

    public final ObjectKlass java_time_LocalDate;
    public final Field java_time_LocalDate_year;
    public final Field java_time_LocalDate_month;
    public final Field java_time_LocalDate_day;
    public final Method java_time_LocalDate_of;

    public final ObjectKlass java_time_LocalDateTime;
    public final Method java_time_LocalDateTime_toLocalTime;
    public final Method java_time_LocalDateTime_toLocalDate;
    public final Method java_time_LocalDateTime_of;
    public final ObjectKlass java_time_ZonedDateTime;
    public final Method java_time_ZonedDateTime_toLocalTime;
    public final Method java_time_ZonedDateTime_toLocalDate;
    public final Method java_time_ZonedDateTime_getZone;
    public final Method java_time_ZonedDateTime_toInstant;
    public final Method java_time_ZonedDateTime_ofInstant;

    public final ObjectKlass java_util_Date;
    public final Method java_util_Date_toInstant;
    public final Method java_util_Date_from;
    public final ObjectKlass java_time_ZoneId;
    public final Method java_time_ZoneId_getId;
    public final Method java_time_ZoneId_of;

    public final ObjectKlass java_util_Map;
    public final Method java_util_Map_size;
    public final Method java_util_Map_get;
    public final Method java_util_Map_put;
    public final Method java_util_Map_remove;
    public final Method java_util_Map_containsKey;
    public final Method java_util_Map_entrySet;
    public final ObjectKlass java_util_HashMap;
    public final Method java_util_HashMap_init;
    public final Method java_util_HashMap_put;
    public final ObjectKlass java_util_Map_Entry;
    public final Method java_util_Map_Entry_getKey;
    public final Method java_util_Map_Entry_getValue;
    public final Method java_util_Map_Entry_setValue;

    public final ObjectKlass java_util_List;
    public final Method java_util_List_get;
    public final Method java_util_List_set;
    public final Method java_util_List_size;
    public final Method java_util_List_add;
    public final Method java_util_List_remove;

    public final ObjectKlass java_util_Set;
    public final Method java_util_Set_add;
    public final Method java_util_Set_of;

    public final ObjectKlass java_lang_Iterable;
    public final Method java_lang_Iterable_iterator;

    public final ObjectKlass java_util_Iterator;
    public final Method java_util_Iterator_next;
    public final Method java_util_Iterator_hasNext;
    public final Method java_util_Iterator_remove;

    public final ObjectKlass java_util_Collection;
    public final Method java_util_Collection_size;
    public final Method java_util_Collection_toArray;

    public final ObjectKlass java_util_Optional;
    public final Field java_util_Optional_value;
    public final Field java_util_Optional_EMPTY;

    public final ObjectKlass java_util_concurrent_locks_AbstractOwnableSynchronizer;
    public final Field java_util_concurrent_locks_AbstractOwnableSynchronizer_exclusiveOwnerThread;
    public final ObjectKlass java_util_concurrent_locks_ReentrantLock_Sync;
    public final ObjectKlass java_util_concurrent_locks_ReentrantReadWriteLock_Sync;

    public final ObjectKlass java_math_BigInteger;
    public final Method java_math_BigInteger_init;
    public final Method java_math_BigInteger_toByteArray;

    public final ObjectKlass java_math_BigDecimal;
    public final Method java_math_BigDecimal_init;

    public final ObjectKlass java_math_MathContext;
    public final Method java_math_MathContext_init;

    public final ObjectKlass jdk_internal_misc_UnsafeConstants;
    public final Field jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0;
    public final Field jdk_internal_misc_UnsafeConstants_PAGE_SIZE;
    public final Field jdk_internal_misc_UnsafeConstants_BIG_ENDIAN;
    public final Field jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS;
    public final Field jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE;

    // Foreign
    public final Klass jdk_internal_foreign_abi_VMStorage;
    public final Field jdk_internal_foreign_abi_VMStorage_type;
    public final Field jdk_internal_foreign_abi_VMStorage_segmentMaskOrSize;
    public final Field jdk_internal_foreign_abi_VMStorage_indexOrOffset;
    public final Klass jdk_internal_foreign_abi_NativeEntryPoint;
    public final Field jdk_internal_foreign_abi_NativeEntryPoint_downcallStubAddress;
    public final Klass jdk_internal_foreign_abi_UpcallLinker_CallRegs;
    public final Field jdk_internal_foreign_abi_UpcallLinker_CallRegs_argRegs;
    public final Field jdk_internal_foreign_abi_UpcallLinker_CallRegs_retRegs;

    @CompilationFinal public ObjectKlass java_lang_management_MemoryUsage;
    @CompilationFinal public ObjectKlass sun_management_ManagementFactory;
    @CompilationFinal public Method sun_management_ManagementFactory_createMemoryPool;
    @CompilationFinal public Method sun_management_ManagementFactory_createMemoryManager;
    @CompilationFinal public Method sun_management_ManagementFactory_createGarbageCollector;
    @CompilationFinal public ObjectKlass java_lang_management_ThreadInfo;

    // used by class redefinition
    public final Klass java_lang_Enum;
    @CompilationFinal public ObjectKlass java_lang_reflect_Proxy;
    @CompilationFinal public ObjectKlass sun_misc_ProxyGenerator;
    @CompilationFinal public Method sun_misc_ProxyGenerator_generateProxyClass;
    @CompilationFinal public ObjectKlass java_lang_reflect_ProxyGenerator;
    @CompilationFinal public Method java_lang_reflect_ProxyGenerator_generateProxyClass;
    @CompilationFinal public ObjectKlass java_beans_ThreadGroupContext;
    @CompilationFinal public Method java_beans_ThreadGroupContext_init;
    @CompilationFinal public Method java_beans_ThreadGroupContext_removeBeanInfo;
    @CompilationFinal public ObjectKlass java_beans_Introspector;
    @CompilationFinal public Method java_beans_Introspector_flushFromCaches;
    @CompilationFinal public ObjectKlass sun_instrument_InstrumentationImpl;
    @CompilationFinal public Method sun_instrument_InstrumentationImpl_init;
    @CompilationFinal public Method sun_instrument_InstrumentationImpl_loadClassAndCallPremain;
    @CompilationFinal public Method sun_instrument_InstrumentationImpl_transform;
    @CompilationFinal public ObjectKlass java_lang_instrument_ClassDefinition;
    @CompilationFinal public Method java_lang_instrument_ClassDefinition_getDefinitionClass;
    @CompilationFinal public Method java_lang_instrument_ClassDefinition_getDefinitionClassFile;
    @CompilationFinal public ObjectKlass java_lang_instrument_UnmodifiableClassException;

    public final class ContinuumSupport {
        public final Method org_graalvm_continuations_ContinuationImpl_run;
        public final Method org_graalvm_continuations_ContinuationImpl_suspend;
        public final Field org_graalvm_continuations_ContinuationImpl_stackFrameHead;
        public final Field HIDDEN_CONTINUATION_FRAME_RECORD;
        public final ObjectKlass org_graalvm_continuations_ContinuationImpl_FrameRecord;
        public final Field org_graalvm_continuations_ContinuationImpl_FrameRecord_pointers;
        public final Field org_graalvm_continuations_ContinuationImpl_FrameRecord_primitives;
        public final Field org_graalvm_continuations_ContinuationImpl_FrameRecord_method;
        public final Field org_graalvm_continuations_ContinuationImpl_FrameRecord_next;
        public final Field org_graalvm_continuations_ContinuationImpl_FrameRecord_bci;
        public final ObjectKlass org_graalvm_continuations_IllegalMaterializedRecordException;
        public final ObjectKlass org_graalvm_continuations_IllegalContinuationStateException;

        private ContinuumSupport() {
            ObjectKlass org_graalvm_continuations_ContinuationImpl = knownKlass(Types.org_graalvm_continuations_ContinuationImpl);
            org_graalvm_continuations_ContinuationImpl_run = org_graalvm_continuations_ContinuationImpl.requireDeclaredMethod(Names.run, Signatures._void);
            org_graalvm_continuations_ContinuationImpl_suspend = org_graalvm_continuations_ContinuationImpl.requireDeclaredMethod(Names.suspend, Signatures._void);
            org_graalvm_continuations_ContinuationImpl_stackFrameHead = org_graalvm_continuations_ContinuationImpl.requireDeclaredField(Names.stackFrameHead,
                            Types.org_graalvm_continuations_ContinuationImpl_FrameRecord);
            HIDDEN_CONTINUATION_FRAME_RECORD = org_graalvm_continuations_ContinuationImpl.requireHiddenField(Names.HIDDEN_CONTINUATION_FRAME_RECORD);
            org_graalvm_continuations_ContinuationImpl_FrameRecord = knownKlass(Types.org_graalvm_continuations_ContinuationImpl_FrameRecord);
            org_graalvm_continuations_ContinuationImpl_FrameRecord_pointers = org_graalvm_continuations_ContinuationImpl_FrameRecord.requireDeclaredField(
                            Names.pointers, Types.java_lang_Object_array);
            org_graalvm_continuations_ContinuationImpl_FrameRecord_primitives = org_graalvm_continuations_ContinuationImpl_FrameRecord.requireDeclaredField(
                            Names.primitives, Types._long_array);
            org_graalvm_continuations_ContinuationImpl_FrameRecord_method = org_graalvm_continuations_ContinuationImpl_FrameRecord.requireDeclaredField(
                            Names.method, Types.java_lang_reflect_Method);
            org_graalvm_continuations_ContinuationImpl_FrameRecord_next = org_graalvm_continuations_ContinuationImpl_FrameRecord.requireDeclaredField(
                            Names.next, Types.org_graalvm_continuations_ContinuationImpl_FrameRecord);
            org_graalvm_continuations_ContinuationImpl_FrameRecord_bci = org_graalvm_continuations_ContinuationImpl_FrameRecord.requireDeclaredField(
                            Names.bci, Types._int);
            org_graalvm_continuations_IllegalMaterializedRecordException = knownKlass(
                            Types.org_graalvm_continuations_IllegalMaterializedRecordException);
            org_graalvm_continuations_IllegalContinuationStateException = knownKlass(
                            Types.org_graalvm_continuations_IllegalContinuationStateException);
        }
    }

    @CompilationFinal //
    public ContinuumSupport continuum;

    public final class PolyglotSupport {
        public final ObjectKlass UnknownIdentifierException;
        public final Method UnknownIdentifierException_create_String;
        public final Method UnknownIdentifierException_create_String_Throwable;

        public final ObjectKlass UnsupportedMessageException;
        public final Method UnsupportedMessageException_create;
        public final Method UnsupportedMessageException_create_Throwable;

        public final ObjectKlass UnsupportedTypeException;
        public final Method UnsupportedTypeException_create_Object_array_String;
        public final Method UnsupportedTypeException_create_Object_array_String_Throwable;

        public final ObjectKlass ArityException;
        public final Method ArityException_create_int_int_int;
        public final Method ArityException_create_int_int_int_Throwable;

        public final ObjectKlass InvalidArrayIndexException;
        public final Method InvalidArrayIndexException_create_long;
        public final Method InvalidArrayIndexException_create_long_Throwable;

        public final ObjectKlass InvalidBufferOffsetException;
        public final Method InvalidBufferOffsetException_create_long_long;
        public final Method InvalidBufferOffsetException_create_long_long_Throwable;

        public final ObjectKlass StopIterationException;
        public final Method StopIterationException_create;
        public final Method StopIterationException_create_Throwable;

        public final ObjectKlass UnknownKeyException;
        public final Method UnknownKeyException_create_Object;
        public final Method UnknownKeyException_create_Object_Throwable;

        public final ObjectKlass ForeignException;
        public final ObjectKlass ExceptionType;
        public final Field ExceptionType_EXIT;
        public final Field ExceptionType_INTERRUPT;
        public final Field ExceptionType_RUNTIME_ERROR;
        public final Field ExceptionType_PARSE_ERROR;

        public final ObjectKlass VMHelper;
        public final Method VMHelper_getDynamicModuleDescriptor;

        public final ObjectKlass TypeLiteral;
        public final Field TypeLiteral_rawType;
        public final Field HIDDEN_TypeLiteral_internalType;
        public final ObjectKlass TypeLiteral$InternalTypeLiteral;
        public final ObjectKlass EspressoForeignList;
        public final ObjectKlass EspressoForeignCollection;
        public final ObjectKlass EspressoForeignIterable;
        public final ObjectKlass EspressoForeignIterator;
        public final ObjectKlass EspressoForeignMap;
        public final ObjectKlass EspressoForeignSet;

        public final ObjectKlass EspressoForeignNumber;

        private PolyglotSupport() {
            boolean polyglotSupport = getContext().getEnv().getOptions().get(EspressoOptions.Polyglot);
            EspressoError.guarantee(polyglotSupport, "--java.Polyglot must be enabled");
            // polyglot.jar is either on boot class path (JDK 8)
            // or defined by a platform module (JDK 11+)
            if (getJavaVersion().java8OrEarlier()) {
                EspressoError.guarantee(loadKlassWithBootClassLoader(Types.com_oracle_truffle_espresso_polyglot_Polyglot) != null,
                                "polyglot.jar (Polyglot API) is not accessible");
            } else {
                EspressoError.guarantee(loadKlassOrNull(Types.com_oracle_truffle_espresso_polyglot_Polyglot, getPlatformClassLoader()) != null,
                                "polyglot.jar (Polyglot API) is not accessible");
            }

            ArityException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_ArityException);
            ArityException_create_int_int_int = ArityException.requireDeclaredMethod(Names.create, Signatures.ArityException_int_int_int);
            ArityException_create_int_int_int_Throwable = ArityException.requireDeclaredMethod(Names.create, Signatures.ArityException_int_int_int_Throwable);

            UnknownIdentifierException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException);
            UnknownIdentifierException_create_String = UnknownIdentifierException.requireDeclaredMethod(Names.create, Signatures.UnknownIdentifierException_String);
            UnknownIdentifierException_create_String_Throwable = UnknownIdentifierException.requireDeclaredMethod(Names.create, Signatures.UnknownIdentifierException_String_Throwable);

            UnsupportedMessageException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException);
            UnsupportedMessageException_create = UnsupportedMessageException.requireDeclaredMethod(Names.create, Signatures.UnsupportedMessageException);
            UnsupportedMessageException_create_Throwable = UnsupportedMessageException.requireDeclaredMethod(Names.create, Signatures.UnsupportedMessageException_Throwable);

            UnsupportedTypeException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException);
            UnsupportedTypeException_create_Object_array_String = UnsupportedTypeException.requireDeclaredMethod(Names.create, Signatures.UnsupportedTypeException_Object_array_String);
            UnsupportedTypeException_create_Object_array_String_Throwable = UnsupportedTypeException.requireDeclaredMethod(Names.create,
                            Signatures.UnsupportedTypeException_Object_array_String_Throwable);

            InvalidArrayIndexException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException);
            InvalidArrayIndexException_create_long = InvalidArrayIndexException.requireDeclaredMethod(Names.create, Signatures.InvalidArrayIndexException_long);
            InvalidArrayIndexException_create_long_Throwable = InvalidArrayIndexException.requireDeclaredMethod(Names.create, Signatures.InvalidArrayIndexException_long_Throwable);

            InvalidBufferOffsetException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_InvalidBufferOffsetException);
            InvalidBufferOffsetException_create_long_long = InvalidBufferOffsetException.requireDeclaredMethod(Names.create, Signatures.InvalidBufferOffsetException_long_long);
            InvalidBufferOffsetException_create_long_long_Throwable = InvalidBufferOffsetException.requireDeclaredMethod(Names.create, Signatures.InvalidBufferOffsetException_long_long_Throwable);

            StopIterationException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_StopIterationException);
            StopIterationException_create = StopIterationException.requireDeclaredMethod(Names.create, Signatures.StopIterationException);
            StopIterationException_create_Throwable = StopIterationException.requireDeclaredMethod(Names.create, Signatures.StopIterationException_Throwable);

            UnknownKeyException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_UnknownKeyException);
            UnknownKeyException_create_Object = UnknownKeyException.requireDeclaredMethod(Names.create, Signatures.UnknownKeyException_Object);
            UnknownKeyException_create_Object_Throwable = UnknownKeyException.requireDeclaredMethod(Names.create, Signatures.UnknownKeyException_Object_Throwable);

            ForeignException = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_ForeignException);
            ExceptionType = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_ExceptionType);

            ExceptionType_EXIT = ExceptionType.requireDeclaredField(Names.EXIT,
                            Types.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_INTERRUPT = ExceptionType.requireDeclaredField(Names.INTERRUPT,
                            Types.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_RUNTIME_ERROR = ExceptionType.requireDeclaredField(Names.RUNTIME_ERROR,
                            Types.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_PARSE_ERROR = ExceptionType.requireDeclaredField(Names.PARSE_ERROR,
                            Types.com_oracle_truffle_espresso_polyglot_ExceptionType);

            TypeLiteral = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_TypeLiteral);
            TypeLiteral_rawType = TypeLiteral.requireDeclaredField(Names.rawType, Types.java_lang_Class);
            HIDDEN_TypeLiteral_internalType = TypeLiteral.requireHiddenField(Names.HIDDEN_INTERNAL_TYPE);
            TypeLiteral$InternalTypeLiteral = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_TypeLiteral$InternalTypeLiteral);

            VMHelper = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_VMHelper);
            VMHelper_getDynamicModuleDescriptor = VMHelper.requireDeclaredMethod(Names.getDynamicModuleDescriptor, Signatures.ModuleDescriptor_String_String);

            EspressoForeignList = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignList);
            EspressoForeignCollection = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignCollection);
            EspressoForeignIterable = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterable);
            EspressoForeignIterator = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterator);
            EspressoForeignMap = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignMap);
            EspressoForeignSet = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignSet);

            EspressoForeignNumber = knownPlatformKlass(Types.com_oracle_truffle_espresso_polyglot_impl_EspressoForeignNumber);
        }
    }

    @CompilationFinal //
    public PolyglotSupport polyglot;

    // needed for external and internal JVMCI support
    public final Field HIDDEN_JVMCIINDY;

    // needed for internal JVMCI support only
    public final class JVMCISupport {
        public final ObjectKlass EspressoJVMCIRuntime;
        public final Method EspressoJVMCIRuntime_runtime;

        public final ObjectKlass DummyEspressoGraalJVMCICompiler;
        public final Method DummyEspressoGraalJVMCICompiler_create;

        public final ObjectKlass GraalJVMCICompiler;

        public final ObjectKlass EspressoResolvedInstanceType;
        public final Method EspressoResolvedInstanceType_init;
        public final Field HIDDEN_OBJECTKLASS_MIRROR;

        public final ObjectKlass EspressoResolvedJavaField;
        public final Method EspressoResolvedJavaField_init;
        public final Field HIDDEN_FIELD_MIRROR;

        public final ObjectKlass EspressoResolvedJavaMethod;
        public final Method EspressoResolvedJavaMethod_init;
        public final Field EspressoResolvedJavaMethod_holder;
        public final Field HIDDEN_METHOD_MIRROR;

        public final ObjectKlass EspressoResolvedArrayType;
        public final Method EspressoResolvedArrayType_init;

        public final ObjectKlass EspressoResolvedPrimitiveType;
        public final Method EspressoResolvedPrimitiveType_forBasicType;

        public final ObjectKlass EspressoConstantPool;
        public final Field EspressoConstantPool_holder;

        public final ObjectKlass EspressoObjectConstant;
        public final Method EspressoObjectConstant_init;
        public final Field HIDDEN_OBJECT_CONSTANT;

        public final ObjectKlass EspressoBootstrapMethodInvocation;
        public final Method EspressoBootstrapMethodInvocation_init;

        public final ObjectKlass Services;
        public final Method Services_openJVMCITo;

        public final ObjectKlass UnresolvedJavaType;
        public final Method UnresolvedJavaType_create;
        public final Field UnresolvedJavaType_name;

        public final ObjectKlass UnresolvedJavaField;
        public final Method UnresolvedJavaField_init;

        public final ObjectKlass LineNumberTable;
        public final Method LineNumberTable_init;

        public final ObjectKlass LocalVariableTable;
        public final Method LocalVariableTable_init;

        public final ObjectKlass Local;
        public final Method Local_init;

        public final ObjectKlass ExceptionHandler;
        public final Method ExceptionHandler_init;

        public final ObjectKlass JavaConstant;
        public final Field JavaConstant_NULL_POINTER;
        public final Field JavaConstant_ILLEGAL;
        public final Method JavaConstant_forInt;
        public final Method JavaConstant_forLong;
        public final Method JavaConstant_forFloat;
        public final Method JavaConstant_forDouble;
        public final Method JavaConstant_forPrimitive;

        public final StaticObject IntrinsicMethod_INVOKE_BASIC;
        public final StaticObject IntrinsicMethod_LINK_TO_VIRTUAL;
        public final StaticObject IntrinsicMethod_LINK_TO_STATIC;
        public final StaticObject IntrinsicMethod_LINK_TO_SPECIAL;
        public final StaticObject IntrinsicMethod_LINK_TO_INTERFACE;
        public final StaticObject IntrinsicMethod_LINK_TO_NATIVE;

        private JVMCISupport() {
            // JVMCI
            EspressoJVMCIRuntime = knownKlass(Types.com_oracle_truffle_espresso_jvmci_EspressoJVMCIRuntime);
            EspressoJVMCIRuntime_runtime = EspressoJVMCIRuntime.requireDeclaredMethod(Names.runtime, Signatures.EspressoJVMCIRuntime);

            EspressoResolvedInstanceType = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType);
            EspressoResolvedInstanceType_init = EspressoResolvedInstanceType.requireDeclaredMethod(Names._init_, Signatures._void);
            HIDDEN_OBJECTKLASS_MIRROR = EspressoResolvedInstanceType.requireHiddenField(Names.HIDDEN_OBJECTKLASS_MIRROR);

            EspressoResolvedJavaField = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaField);
            EspressoResolvedJavaField_init = EspressoResolvedJavaField.requireDeclaredMethod(Names._init_, Signatures._void_EspressoResolvedInstanceType);
            HIDDEN_FIELD_MIRROR = EspressoResolvedJavaField.requireHiddenField(Names.HIDDEN_FIELD_MIRROR);

            EspressoResolvedJavaMethod = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod);
            EspressoResolvedJavaMethod_init = EspressoResolvedJavaMethod.requireDeclaredMethod(Names._init_, Signatures._void_EspressoResolvedInstanceType_boolean);
            HIDDEN_METHOD_MIRROR = EspressoResolvedJavaMethod.requireHiddenField(Names.HIDDEN_METHOD_MIRROR);
            EspressoResolvedJavaMethod_holder = EspressoResolvedJavaMethod.requireDeclaredField(Names.holder, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType);

            EspressoResolvedArrayType = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedArrayType);
            EspressoResolvedArrayType_init = EspressoResolvedArrayType.requireDeclaredMethod(Names._init_, Signatures._void_EspressoResolvedJavaType_int_Class);

            EspressoResolvedPrimitiveType = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedPrimitiveType);
            EspressoResolvedPrimitiveType_forBasicType = EspressoResolvedPrimitiveType.requireDeclaredMethod(Names.forBasicType, Signatures.EspressoResolvedPrimitiveType_int);

            EspressoConstantPool = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoConstantPool);
            EspressoConstantPool_holder = EspressoConstantPool.requireDeclaredField(Names.holder, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType);

            EspressoObjectConstant = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoObjectConstant);
            EspressoObjectConstant_init = EspressoObjectConstant.requireDeclaredMethod(Names._init_, Signatures._void);
            HIDDEN_OBJECT_CONSTANT = EspressoObjectConstant.requireHiddenField(Names.HIDDEN_OBJECT_CONSTANT);

            EspressoBootstrapMethodInvocation = knownKlass(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoBootstrapMethodInvocation);
            EspressoBootstrapMethodInvocation_init = EspressoBootstrapMethodInvocation.requireDeclaredMethod(Names._init_,
                            Signatures._void_boolean_EspressoResolvedJavaMethod_String_JavaConstant_JavaConstant_array_int_EspressoConstantPool);

            Services = knownKlass(Types.jdk_vm_ci_services_Services);
            Services_openJVMCITo = Services.requireDeclaredMethod(Names.openJVMCITo, Signatures._void_Module);

            UnresolvedJavaType = knownKlass(Types.jdk_vm_ci_meta_UnresolvedJavaType);
            UnresolvedJavaType_create = UnresolvedJavaType.requireDeclaredMethod(Names.create, Signatures.UnresolvedJavaType_String);
            UnresolvedJavaType_name = UnresolvedJavaType.requireDeclaredField(Names.name, Types.java_lang_String);

            UnresolvedJavaField = knownKlass(Types.jdk_vm_ci_meta_UnresolvedJavaField);
            UnresolvedJavaField_init = UnresolvedJavaField.requireDeclaredMethod(Names._init_, Signatures._void_JavaType_String_JavaType);

            LineNumberTable = knownKlass(Types.jdk_vm_ci_meta_LineNumberTable);
            LineNumberTable_init = LineNumberTable.requireDeclaredMethod(Names._init_, Signatures._void_int_array_int_array);

            LocalVariableTable = knownKlass(Types.jdk_vm_ci_meta_LocalVariableTable);
            LocalVariableTable_init = LocalVariableTable.requireDeclaredMethod(Names._init_, Signatures._void_Local_array);

            Local = knownKlass(Types.jdk_vm_ci_meta_Local);
            Local_init = Local.requireDeclaredMethod(Names._init_, Signatures._void_String_JavaType_int_int_int);

            ExceptionHandler = knownKlass(Types.jdk_vm_ci_meta_ExceptionHandler);
            ExceptionHandler_init = ExceptionHandler.requireDeclaredMethod(Names._init_, Signatures._void_int_int_int_int_JavaType);

            JavaConstant = knownKlass(Types.jdk_vm_ci_meta_JavaConstant);
            JavaConstant_NULL_POINTER = JavaConstant.requireDeclaredField(Names.NULL_POINTER, Types.jdk_vm_ci_meta_JavaConstant);
            JavaConstant_ILLEGAL = JavaConstant.requireDeclaredField(Names.ILLEGAL, Types.jdk_vm_ci_meta_PrimitiveConstant);
            JavaConstant_forInt = JavaConstant.requireDeclaredMethod(Names.forInt, Signatures.PrimitiveConstant_int);
            JavaConstant_forLong = JavaConstant.requireDeclaredMethod(Names.forLong, Signatures.PrimitiveConstant_long);
            JavaConstant_forFloat = JavaConstant.requireDeclaredMethod(Names.forFloat, Signatures.PrimitiveConstant_float);
            JavaConstant_forDouble = JavaConstant.requireDeclaredMethod(Names.forDouble, Signatures.PrimitiveConstant_double);
            JavaConstant_forPrimitive = JavaConstant.requireDeclaredMethod(Names.forPrimitive, Signatures.PrimitiveConstant_char_long);

            ObjectKlass IntrinsicMethod = knownKlass(Types.jdk_vm_ci_meta_MethodHandleAccessProvider$IntrinsicMethod);
            IntrinsicMethod_INVOKE_BASIC = IntrinsicMethod.requireEnumConstant(Names.INVOKE_BASIC);
            IntrinsicMethod_LINK_TO_VIRTUAL = IntrinsicMethod.requireEnumConstant(Names.LINK_TO_VIRTUAL);
            IntrinsicMethod_LINK_TO_STATIC = IntrinsicMethod.requireEnumConstant(Names.LINK_TO_STATIC);
            IntrinsicMethod_LINK_TO_SPECIAL = IntrinsicMethod.requireEnumConstant(Names.LINK_TO_SPECIAL);
            IntrinsicMethod_LINK_TO_INTERFACE = IntrinsicMethod.requireEnumConstant(Names.LINK_TO_INTERFACE);
            IntrinsicMethod_LINK_TO_NATIVE = IntrinsicMethod.lookupEnumConstant(Names.LINK_TO_NATIVE);

            // Compiler
            DummyEspressoGraalJVMCICompiler = loadPlatformKlassOrNull(Types.jdk_graal_compiler_espresso_DummyEspressoGraalJVMCICompiler);
            if (DummyEspressoGraalJVMCICompiler != null) {
                DummyEspressoGraalJVMCICompiler_create = DummyEspressoGraalJVMCICompiler.requireDeclaredMethod(Names.create, Signatures.DummyEspressoGraalJVMCICompiler_JVMCIRuntime);
            } else {
                DummyEspressoGraalJVMCICompiler_create = null;
            }

            GraalJVMCICompiler = loadPlatformKlassOrNull(Types.jdk_graal_compiler_api_runtime_GraalJVMCICompiler);
        }

        public StaticObject boxInt(int value) {
            return (StaticObject) JavaConstant_forInt.invokeDirectStatic(value);
        }

        public StaticObject boxLong(long value) {
            return (StaticObject) JavaConstant_forLong.invokeDirectStatic(value);
        }

        public StaticObject boxFloat(float value) {
            return (StaticObject) JavaConstant_forFloat.invokeDirectStatic(value);
        }

        public StaticObject boxDouble(double value) {
            return (StaticObject) JavaConstant_forDouble.invokeDirectStatic(value);
        }
    }

    @CompilationFinal public JVMCISupport jvmci;

    public final class TRegexSupport {
        public final ObjectKlass java_util_regex_Pattern;
        public final Field java_util_regex_Pattern_HIDDEN_tregexMatch;
        public final Field java_util_regex_Pattern_HIDDEN_tregexFullmatch;
        public final Field java_util_regex_Pattern_HIDDEN_tregexSearch;
        public final Field java_util_regex_Pattern_HIDDEN_status;
        public final Field java_util_regex_Pattern_pattern;
        public final Field java_util_regex_Pattern_flags;
        public final Field java_util_regex_Pattern_flags0;
        public final Field java_util_regex_Pattern_compiled;
        public final Method java_util_regex_Pattern_init;
        public final Method java_util_regex_Pattern_namedGroups;
        public final Field java_util_regex_Pattern_namedGroups_field;
        public final Field java_util_regex_Pattern_capturingGroupCount;
        public final Field java_util_regex_Pattern_root;
        public final Method java_util_regex_Pattern_compile;
        public final Field java_util_regex_Pattern_localCount;
        public final Field java_util_regex_Pattern_localTCNCount;
        public final ObjectKlass java_util_regex_IntHashSet;
        public final ObjectKlass java_util_regex_Matcher;
        public final Method java_util_regex_Matcher_init;
        public final Method java_util_regex_Matcher_reset;
        public final Field java_util_regex_Matcher_HIDDEN_tstring;
        public final Field java_util_regex_Matcher_HIDDEN_textSync;
        public final Field java_util_regex_Matcher_HIDDEN_patternSync;
        public final Field java_util_regex_Matcher_HIDDEN_oldLastBackup;
        public final Field java_util_regex_Matcher_HIDDEN_modCountBackup;
        public final Field java_util_regex_Matcher_HIDDEN_transparentBoundsBackup;
        public final Field java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup;
        public final Field java_util_regex_Matcher_HIDDEN_fromBackup;
        public final Field java_util_regex_Matcher_HIDDEN_toBackup;
        public final Field java_util_regex_Matcher_HIDDEN_matchingModeBackup;
        public final Field java_util_regex_Matcher_HIDDEN_searchFromBackup;
        public final Field java_util_regex_Matcher_text;
        public final Field java_util_regex_Matcher_modCount;
        public final Field java_util_regex_Matcher_parentPattern;
        public final Field java_util_regex_Matcher_groups;
        public final Field java_util_regex_Matcher_first;
        public final Field java_util_regex_Matcher_last;
        public final Field java_util_regex_Matcher_oldLast;
        public final Field java_util_regex_Matcher_from;
        public final Field java_util_regex_Matcher_to;
        public final Method java_util_regex_Matcher_match;
        public final Method java_util_regex_Matcher_search;
        public final Field java_util_regex_Matcher_transparentBounds;
        public final Field java_util_regex_Matcher_anchoringBounds;
        public final Field java_util_regex_Matcher_locals;
        public final Field java_util_regex_Matcher_localsPos;
        public final Field java_util_regex_Matcher_hitEnd;
        public final Field java_util_regex_Matcher_requireEnd;
        public final Method java_util_regex_Matcher_groupCount;

        private TRegexSupport() {
            assert getLanguage().useTRegex();
            assert getJavaVersion().java21OrLater();

            java_util_regex_Pattern = knownKlass(Types.java_util_regex_Pattern);
            java_util_regex_Pattern_init = java_util_regex_Pattern.requireMethod(Names._init_, Signatures._void_String_int);
            java_util_regex_Pattern_compile = java_util_regex_Pattern.requireDeclaredMethod(Names.compile, Signatures._void);

            java_util_regex_Pattern_namedGroups = java_util_regex_Pattern.requireMethod(Names.namedGroups, Signatures.Map);

            java_util_regex_Pattern_HIDDEN_tregexMatch = java_util_regex_Pattern.requireHiddenField(Names.HIDDEN_TREGEX_MATCH);
            java_util_regex_Pattern_HIDDEN_tregexFullmatch = java_util_regex_Pattern.requireHiddenField(Names.HIDDEN_TREGEX_FULLMATCH);
            java_util_regex_Pattern_HIDDEN_tregexSearch = java_util_regex_Pattern.requireHiddenField(Names.HIDDEN_TREGEX_SEARCH);
            java_util_regex_Pattern_HIDDEN_status = java_util_regex_Pattern.requireHiddenField(Names.HIDDEN_TREGEX_STATUS);

            java_util_regex_Pattern_pattern = java_util_regex_Pattern.requireDeclaredField(Names.pattern, Types.java_lang_String);
            java_util_regex_Pattern_flags = java_util_regex_Pattern.requireDeclaredField(Names.flags, Types._int);
            java_util_regex_Pattern_flags0 = java_util_regex_Pattern.requireDeclaredField(Names.flags0, Types._int);
            java_util_regex_Pattern_compiled = java_util_regex_Pattern.requireDeclaredField(Names.compiled, Types._boolean);
            java_util_regex_Pattern_namedGroups_field = java_util_regex_Pattern.requireDeclaredField(Names.namedGroups, Types.java_util_Map);
            java_util_regex_Pattern_capturingGroupCount = java_util_regex_Pattern.requireDeclaredField(Names.capturingGroupCount, Types._int);
            java_util_regex_Pattern_root = java_util_regex_Pattern.requireDeclaredField(Names.root, Types.java_util_regex_Pattern_Node);
            java_util_regex_Pattern_localCount = java_util_regex_Pattern.requireDeclaredField(Names.localCount, Types._int);
            java_util_regex_Pattern_localTCNCount = java_util_regex_Pattern.requireDeclaredField(Names.localTCNCount, Types._int);

            java_util_regex_Matcher = knownKlass(Types.java_util_regex_Matcher);
            java_util_regex_Matcher_init = java_util_regex_Matcher.requireMethod(Names._init_, Signatures._void_CharSequence_Pattern);
            java_util_regex_Matcher_reset = java_util_regex_Matcher.requireMethod(Names.reset, Signatures.Matcher_CharSequence);
            java_util_regex_Matcher_match = java_util_regex_Matcher.requireMethod(Names.match, Signatures._boolean_int_int);
            java_util_regex_Matcher_search = java_util_regex_Matcher.requireMethod(Names.search, Signatures._boolean_int);
            java_util_regex_Matcher_groupCount = java_util_regex_Matcher.requireDeclaredMethod(Names.groupCount, Signatures._int);
            java_util_regex_Matcher_hitEnd = java_util_regex_Matcher.requireDeclaredField(Names.hitEnd, Types._boolean);
            java_util_regex_Matcher_requireEnd = java_util_regex_Matcher.requireDeclaredField(Names.requireEnd, Types._boolean);

            java_util_regex_Matcher_HIDDEN_tstring = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_TSTRING);
            java_util_regex_Matcher_HIDDEN_textSync = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_TEXT_SYNC);
            java_util_regex_Matcher_HIDDEN_patternSync = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_PATTERN_SYNC);
            java_util_regex_Matcher_HIDDEN_oldLastBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_OLD_LAST_BACKUP);
            java_util_regex_Matcher_HIDDEN_modCountBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_MOD_COUNT_BACKUP);
            java_util_regex_Matcher_HIDDEN_transparentBoundsBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_TRANSPARENT_BOUNDS_BACKUP);
            java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_ANCHORING_BOUNDS_BACKUP);
            java_util_regex_Matcher_HIDDEN_fromBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_FROM_BACKUP);
            java_util_regex_Matcher_HIDDEN_toBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_TO_BACKUP);
            java_util_regex_Matcher_HIDDEN_matchingModeBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_MATCHING_MODE_BACKUP);
            java_util_regex_Matcher_HIDDEN_searchFromBackup = java_util_regex_Matcher.requireHiddenField(Names.HIDDEN_TREGEX_SEARCH_FROM_BACKUP);

            java_util_regex_Matcher_parentPattern = java_util_regex_Matcher.requireDeclaredField(Names.parentPattern, Types.java_util_regex_Pattern);
            java_util_regex_Matcher_text = java_util_regex_Matcher.requireDeclaredField(Names.text, Types.java_lang_CharSequence);
            java_util_regex_Matcher_groups = java_util_regex_Matcher.requireDeclaredField(Names.groups, Types._int_array);
            java_util_regex_Matcher_first = java_util_regex_Matcher.requireDeclaredField(Names.first, Types._int);
            java_util_regex_Matcher_last = java_util_regex_Matcher.requireDeclaredField(Names.last, Types._int);
            java_util_regex_Matcher_oldLast = java_util_regex_Matcher.requireDeclaredField(Names.oldLast, Types._int);
            java_util_regex_Matcher_from = java_util_regex_Matcher.requireDeclaredField(Names.from, Types._int);
            java_util_regex_Matcher_to = java_util_regex_Matcher.requireDeclaredField(Names.to, Types._int);
            java_util_regex_Matcher_modCount = java_util_regex_Matcher.requireDeclaredField(Names.modCount, Types._int);
            java_util_regex_Matcher_transparentBounds = java_util_regex_Matcher.requireDeclaredField(Names.transparentBounds, Types._boolean);
            java_util_regex_Matcher_anchoringBounds = java_util_regex_Matcher.requireDeclaredField(Names.anchoringBounds, Types._boolean);
            java_util_regex_Matcher_locals = java_util_regex_Matcher.requireDeclaredField(Names.locals, Types._int_array);
            java_util_regex_Matcher_localsPos = java_util_regex_Matcher.requireDeclaredField(Names.localsPos, Types.java_util_regex_IntHashSet_array);

            java_util_regex_IntHashSet = knownKlass(Types.java_util_regex_IntHashSet);
        }
    }

    public final TRegexSupport tRegexSupport;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] ARRAY_SUPERINTERFACES;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] BOXED_PRIMITIVE_KLASSES;

    @CompilationFinal(dimensions = 1) //
    public final PrimitiveKlass[] PRIMITIVE_KLASSES;

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
        CompilerDirectives.transferToInterpreterAndInvalidate();
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
    public static @JavaType(Throwable.class) StaticObject initExceptionWithMessage(ObjectKlass exceptionKlass, @JavaType(String.class) StaticObject message) {
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
    public static @JavaType(Throwable.class) StaticObject initExceptionWithMessage(ObjectKlass exceptionKlass, String message) {
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
    public static @JavaType(Throwable.class) StaticObject initException(ObjectKlass exceptionKlass) {
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
    public static @JavaType(Throwable.class) StaticObject initExceptionWithCause(ObjectKlass exceptionKlass, @JavaType(Throwable.class) StaticObject cause) {
        assert exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(exceptionKlass);
        assert StaticObject.isNull(cause) || exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(cause.getKlass());
        return exceptionKlass.getMeta().dispatch.initEx(exceptionKlass, null, cause);
    }

    /**
     * Allocate and initializes an exception of the given guest klass.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String, Throwable) constructor with message and cause}. The given
     * guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    public static @JavaType(Throwable.class) StaticObject initException(ObjectKlass exceptionKlass, @JavaType(String.class) StaticObject message, @JavaType(Throwable.class) StaticObject cause) {
        assert exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(exceptionKlass);
        assert StaticObject.isNull(cause) || exceptionKlass.getMeta().java_lang_Throwable.isAssignableFrom(cause.getKlass());
        assert StaticObject.isNull(message) || exceptionKlass.getMeta().java_lang_String.isAssignableFrom(message.getKlass());
        return exceptionKlass.getMeta().dispatch.initEx(exceptionKlass, message, cause);
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
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwException(@JavaType(Throwable.class) ObjectKlass exceptionKlass) {
        throw throwException(initException(exceptionKlass));
    }

    /**
     * Throws the given guest exception, wrapped in {@link EspressoException}.
     *
     * <p>
     * The given instance must be a non-{@link StaticObject#NULL NULL}, guest
     * {@link #java_lang_Throwable Throwable}.
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwException(@JavaType(Throwable.class) StaticObject throwable) {
        assert InterpreterToVM.instanceOf(throwable, throwable.getKlass().getMeta().java_lang_Throwable);
        throw EspressoException.wrap(throwable, this);
    }

    /**
     * Initializes and throws an exception of the given guest klass with the given message.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     * @param message the message to be used when initializing the exception
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwExceptionWithMessage(@JavaType(Throwable.class) ObjectKlass exceptionKlass, @JavaType(String.class) StaticObject message) {
        throw throwException(initExceptionWithMessage(exceptionKlass, message));
    }

    /**
     * Initializes and throws an exception of the given guest klass with the given message.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     * @param message the message to be used when initializing the exception
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwExceptionWithMessage(@JavaType(Throwable.class) ObjectKlass exceptionKlass, String message) {
        throw throwExceptionWithMessage(exceptionKlass, exceptionKlass.getMeta().toGuestString(message));
    }

    /**
     * Initializes and throws an exception of the given guest klass with the given message.
     *
     * <p>
     * A guest instance is allocated and initialized by calling the
     * {@link Throwable#Throwable(String) constructor with message}. The given guest class must have
     * such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     * @param msgFormat the {@linkplain java.util.Formatter format string} to be used to construct
     *            the message used when initializing the exception
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwExceptionWithMessage(@JavaType(Throwable.class) ObjectKlass exceptionKlass, String msgFormat, Object... args) {
        throw throwExceptionWithMessage(exceptionKlass, exceptionKlass.getMeta().toGuestString(EspressoError.format(msgFormat, args)));
    }

    /**
     * Initializes and throws an exception of the given guest klass. A guest instance is allocated
     * and initialized by calling the {@link Throwable#Throwable(Throwable) constructor with cause}.
     * The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwExceptionWithCause(@JavaType(Throwable.class) ObjectKlass exceptionKlass, @JavaType(Throwable.class) StaticObject cause) {
        throw throwException(initExceptionWithCause(exceptionKlass, cause));
    }

    /**
     * Initializes and throws an exception of the given guest klass. A guest instance is allocated
     * and initialized by calling the {@link Throwable#Throwable(String, Throwable) constructor with
     * cause}. The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwException(@JavaType(Throwable.class) ObjectKlass exceptionKlass, @JavaType(String.class) StaticObject message, @JavaType(Throwable.class) StaticObject cause) {
        throw throwException(initException(exceptionKlass, message, cause));
    }

    /**
     * Initializes and throws an exception of the given guest klass. A guest instance is allocated
     * and initialized by calling the {@link Throwable#Throwable(String, Throwable) constructor with
     * cause}. The given guest class must have such constructor declared.
     *
     * @param exceptionKlass guest exception class, subclass of guest {@link #java_lang_Throwable
     *            Throwable}.
     */
    @HostCompilerDirectives.InliningCutoff
    public EspressoException throwException(@JavaType(Throwable.class) ObjectKlass exceptionKlass, String message, @JavaType(Throwable.class) StaticObject cause) {
        throw throwException(initException(exceptionKlass, exceptionKlass.getMeta().toGuestString(message), cause));
    }

    /**
     * Throws a guest {@link NullPointerException}. A guest instance is allocated and initialized by
     * calling the {@link NullPointerException#NullPointerException() default constructor}.
     */
    public EspressoException throwNullPointerException() {
        throw throwException(java_lang_NullPointerException);
    }

    @TruffleBoundary
    public EspressoException throwNullPointerExceptionBoundary() {
        throw throwNullPointerException();
    }

    @TruffleBoundary
    public EspressoException throwIllegalArgumentExceptionBoundary() {
        throw throwException(java_lang_IllegalArgumentException);
    }

    @TruffleBoundary
    public EspressoException throwIllegalArgumentExceptionBoundary(String message) {
        throw throwExceptionWithMessage(java_lang_IllegalArgumentException, message);
    }

    @TruffleBoundary
    public EspressoException throwNoClassDefFoundErrorBoundary(String message) {
        throw throwExceptionWithMessage(java_lang_NoClassDefFoundError, message);
    }

    @TruffleBoundary
    public EspressoException throwIndexOutOfBoundsExceptionBoundary(String message, int index, int length) {
        throw throwExceptionWithMessage(java_lang_IndexOutOfBoundsException, message + ": index=" + index + " length=" + length);
    }

    /**
     * Throws a guest {@link ArrayIndexOutOfBoundsException}. Uses the given int to construct a
     * useful message.
     */
    public EspressoException throwArrayIndexOutOfBounds(int index) {
        throw throwExceptionWithMessage(java_lang_ArrayIndexOutOfBoundsException, "Array index out of range: " + index);
    }

    public EspressoException throwArrayIndexOutOfBounds(int index, int length) {
        throw throwExceptionWithMessage(java_lang_ArrayIndexOutOfBoundsException, "Array index out of range: " + index + " for length " + length);
    }

    // endregion Guest exception handling (throw)

    public ObjectKlass knownKlass(Symbol<Type> type) {
        return knownKlass(type, StaticObject.NULL);
    }

    private ObjectKlass knownPlatformKlass(Symbol<Type> type) {
        // known platform classes are loaded by the platform loader on JDK 11 and
        // by the boot classloader on JDK 8
        return knownKlass(type, getJavaVersion().java8OrEarlier() ? StaticObject.NULL : getPlatformClassLoader());
    }

    private ObjectKlass knownKlass(Symbol<Type> type, StaticObject classLoader) {
        CompilerAsserts.neverPartOfCompilation();
        assert !TypeSymbols.isArray(type);
        assert !TypeSymbols.isPrimitive(type);
        ObjectKlass k = loadKlassOrNull(type, classLoader);
        if (k == null) {
            throw EspressoError.shouldNotReachHere("Failed loading known class: " + type + ", discovered java version: " + getJavaVersion());
        }
        return k;
    }

    private ObjectKlass loadPlatformKlassOrNull(Symbol<Type> type) {
        // platform classes are loaded by the platform loader on JDK 11 and
        // by the boot classloader on JDK 8
        Klass result;
        StaticObject classLoader = getJavaVersion().java8OrEarlier() ? StaticObject.NULL : getPlatformClassLoader();
        try {
            result = getRegistries().loadKlass(type, classLoader, StaticObject.NULL);
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(this);
        } catch (EspressoException e) {
            if (e.getGuestException().getKlass() == java_lang_NoClassDefFoundError || e.getGuestException().getKlass() == java_lang_ClassNotFoundException) {
                return null;
            }
            throw e;
        }
        return (ObjectKlass) result;
    }

    public Class<?> resolveDispatch(Klass k) {
        return interopDispatch.resolveDispatch(k);
    }

    /**
     * Performs class loading according to {&sect;5.3. Creation and Loading}. This method directly
     * asks the given class loader to perform the load, even for internal primitive types. This is
     * the method to use when loading symbols that are not directly taken from a constant pool, for
     * example, when loading a class whose name is given by a guest string.
     * <p>
     * This method is designed to fail if given the type symbol for primitives (eg: 'Z' for
     * booleans).
     *
     * @throws NoClassDefFoundError guest exception is no representation of type can be found.
     */
    @TruffleBoundary
    public Klass loadKlassOrFail(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        Klass k;
        try {
            k = getRegistries().loadKlass(type, classLoader, protectionDomain);
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(this);
        } catch (EspressoException e) {
            throw wrapClassNotFoundGuestException(this, e, type);
        }
        if (k == null) {
            throw throwExceptionWithMessage(java_lang_NoClassDefFoundError, TypeSymbols.typeToName(type).toString());
        }
        return k;
    }

    /**
     * Same as {@link #loadKlassOrFail(Symbol, StaticObject, StaticObject)}, except this method
     * returns null instead of throwing if class is not found. Note that this method can still throw
     * LinkageErrors due to other errors (class file malformed, etc...)
     *
     * @see #loadKlassOrFail(Symbol, StaticObject, StaticObject)
     */
    @TruffleBoundary
    public Klass loadKlassOrNull(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        try {
            return getRegistries().loadKlass(type, classLoader, protectionDomain);
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(this);
        } catch (EspressoException e) {
            if (this.java_lang_ClassNotFoundException.isAssignableFrom(e.getGuestException().getKlass())) {
                return null;
            }
            throw e;
        }
    }

    @TruffleBoundary
    private ObjectKlass loadKlassOrNull(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader) {
        return (ObjectKlass) loadKlassOrNull(type, classLoader, StaticObject.NULL);
    }

    @TruffleBoundary
    ObjectKlass loadKlassWithBootClassLoader(Symbol<Type> type) {
        return loadKlassOrNull(type, StaticObject.NULL);
    }

    private StaticObject getPlatformClassLoader() {
        if (cachedPlatformClassLoader == null) {
            cachedPlatformClassLoader = (StaticObject) jdk_internal_loader_ClassLoaders_platformClassLoader.invokeDirectStatic();
        }
        return cachedPlatformClassLoader;
    }

    public Klass resolvePrimitive(Symbol<Type> type) {
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
        return null;
    }

    /**
     * Resolves an internal symbolic type descriptor taken from the constant pool, and returns the
     * corresponding Klass.
     * <li>If the symbol represents an internal primitive (/ex: 'B' or 'I'), this method returns the
     * corresponding primitive. Primitives are therefore not "loaded", but directly resolved..
     * <li>If the symbol is a symbolic references, it asks the given ClassLoader to load the
     * corresponding Klass.
     * <li>If the symbol represents an array, resolves its elemental type, and returns the array
     * corresponding array Klass.
     *
     * @return The asked Klass, or null if no representation can be found.
     */
    public Klass resolveSymbolOrNull(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        CompilerAsserts.partialEvaluationConstant(type);
        assert classLoader != null : "use StaticObject.NULL for BCL";
        // Resolution only resolves references. Bypass loading for primitives.
        Klass k = resolvePrimitive(type);
        if (k != null) {
            return k;
        }
        if (TypeSymbols.isArray(type)) {
            Klass elemental = resolveSymbolOrNull(getTypes().getElementalType(type), classLoader, protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }
        return loadKlassOrNull(type, classLoader, protectionDomain);
    }

    /**
     * Same as {@link #resolveSymbolOrNull(Symbol, StaticObject, StaticObject)}, except this throws
     * a guest NoClassDefFoundError if the representation for the type can not be found.
     *
     * @see #resolveSymbolOrNull(Symbol, StaticObject, StaticObject)
     */
    public Klass resolveSymbolOrFail(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        // Resolution only resolves references. Bypass loading for primitives.
        Klass k = resolvePrimitive(type);
        if (k != null) {
            return k;
        }
        if (TypeSymbols.isArray(type)) {
            Klass elemental = resolveSymbolOrFail(getTypes().getElementalType(type), classLoader, protectionDomain);
            return elemental.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }
        return loadKlassOrFail(type, classLoader, protectionDomain);
    }

    /**
     * Resolves the symbol using {@link #resolveSymbolOrFail(Symbol, StaticObject, StaticObject)},
     * and applies access checking, possibly throwing {@link IllegalAccessError}.
     */
    public Klass resolveSymbolAndAccessCheck(Symbol<Type> type, ObjectKlass accessingKlass) {
        assert accessingKlass != null;
        Klass klass = resolveSymbolOrFail(type, accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
        if (!Klass.checkAccess(klass.getElementalType(), accessingKlass)) {
            throw throwException(java_lang_IllegalAccessError);
        }
        return klass;
    }

    public String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        return stringConversion.toHost(str, getLanguage(), this);
    }

    public StaticObject toGuestString(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        return stringConversion.toGuest(hostString, this);
    }

    @TruffleBoundary
    public static String toHostStringStatic(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        return str.getKlass().getMeta().toHostString(str);
    }

    public ByteSequence toByteSequence(@JavaType(String.class) StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        return ByteSequence.create(toHostString(str));
    }

    @TruffleBoundary
    public StaticObject toGuestString(ByteSequence byteSequence) {
        if (byteSequence == null) {
            return StaticObject.NULL;
        }
        return toGuestString(byteSequence.toString());
    }

    public static boolean isString(Object string) {
        if (string instanceof StaticObject staticObject) {
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
        if (object instanceof StaticObject guestObject) {
            if (StaticObject.isNull(guestObject)) {
                return null;
            }
            if (guestObject.isArray()) {
                return guestObject.unwrap(getLanguage());
            }
            if (guestObject.getKlass() == java_lang_String) {
                return toHostString(guestObject);
            }
            return unboxGuest((StaticObject) object);
        }
        return object;
    }

    public static boolean isSignaturePolymorphicHolderType(Symbol<Type> type) {
        return type == Types.java_lang_invoke_MethodHandle || type == Types.java_lang_invoke_VarHandle;
    }

    // region Guest Unboxing

    public boolean isNumber(Klass klass) {
        return klass == java_lang_Byte || klass == java_lang_Short || klass == java_lang_Integer || klass == java_lang_Long ||
                        klass == java_lang_Float || klass == java_lang_Double;
    }

    public boolean isBoxed(Klass klass) {
        return isNumber(klass) || klass == java_lang_Boolean || klass == java_lang_Character;
    }

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

    public boolean unboxBoolean(@JavaType(Boolean.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Boolean) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (boolean) java_lang_Boolean_value.get(boxed);
    }

    public byte unboxByte(@JavaType(Byte.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Byte) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (byte) java_lang_Byte_value.get(boxed);
    }

    public char unboxCharacter(@JavaType(Character.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Character) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (char) java_lang_Character_value.get(boxed);
    }

    public short unboxShort(@JavaType(Short.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Short) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (short) java_lang_Short_value.get(boxed);
    }

    public float unboxFloat(@JavaType(Float.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Float) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (float) java_lang_Float_value.get(boxed);
    }

    public int unboxInteger(@JavaType(Integer.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Integer) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (int) java_lang_Integer_value.get(boxed);
    }

    public double unboxDouble(@JavaType(Double.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Double) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (double) java_lang_Double_value.get(boxed);
    }

    public long unboxLong(@JavaType(Long.class) StaticObject boxed) {
        if (StaticObject.isNull(boxed) || boxed.getKlass() != java_lang_Long) {
            throw throwException(java_lang_IllegalArgumentException);
        }
        return (long) java_lang_Long_value.get(boxed);
    }

    // endregion Guest Unboxing

    // region Guest boxing

    public @JavaType(Boolean.class) StaticObject boxBoolean(boolean value) {
        return (StaticObject) java_lang_Boolean_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Byte.class) StaticObject boxByte(byte value) {
        return (StaticObject) java_lang_Byte_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Character.class) StaticObject boxCharacter(char value) {
        return (StaticObject) java_lang_Character_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Short.class) StaticObject boxShort(short value) {
        return (StaticObject) java_lang_Short_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Float.class) StaticObject boxFloat(float value) {
        return (StaticObject) java_lang_Float_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Integer.class) StaticObject boxInteger(int value) {
        return (StaticObject) java_lang_Integer_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Double.class) StaticObject boxDouble(double value) {
        return (StaticObject) java_lang_Double_valueOf.invokeDirectStatic(value);
    }

    public @JavaType(Long.class) StaticObject boxLong(long value) {
        return (StaticObject) java_lang_Long_valueOf.invokeDirectStatic(value);
    }

    public StaticObject boxPrimitive(Object hostPrimitive) {
        if (hostPrimitive instanceof Integer) {
            return (StaticObject) getMeta().java_lang_Integer_valueOf.invokeDirectStatic((int) hostPrimitive);
        }
        if (hostPrimitive instanceof Boolean) {
            return (StaticObject) getMeta().java_lang_Boolean_valueOf.invokeDirectStatic((boolean) hostPrimitive);
        }
        if (hostPrimitive instanceof Byte) {
            return (StaticObject) getMeta().java_lang_Byte_valueOf.invokeDirectStatic((byte) hostPrimitive);
        }
        if (hostPrimitive instanceof Character) {
            return (StaticObject) getMeta().java_lang_Character_valueOf.invokeDirectStatic((char) hostPrimitive);
        }
        if (hostPrimitive instanceof Short) {
            return (StaticObject) getMeta().java_lang_Short_valueOf.invokeDirectStatic((short) hostPrimitive);
        }
        if (hostPrimitive instanceof Float) {
            return (StaticObject) getMeta().java_lang_Float_valueOf.invokeDirectStatic((float) hostPrimitive);
        }
        if (hostPrimitive instanceof Double) {
            return (StaticObject) getMeta().java_lang_Double_valueOf.invokeDirectStatic((double) hostPrimitive);
        }
        if (hostPrimitive instanceof Long) {
            return (StaticObject) getMeta().java_lang_Long_valueOf.invokeDirectStatic((long) hostPrimitive);
        }

        throw EspressoError.shouldNotReachHere("Not a boxed type " + hostPrimitive);
    }

    // endregion Guest boxing

    // region Type conversions

    /**
     * Converts a boxed value to a boolean.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public boolean asBoolean(Object value, boolean defaultIfNull) {
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        return tryBitwiseConversionToLong(value, defaultIfNull) != 0; // == 1?
    }

    /**
     * Converts a boxed value to a byte.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public byte asByte(Object value, boolean defaultIfNull) {
        if (value instanceof Byte) {
            return (byte) value;
        }
        return (byte) tryBitwiseConversionToLong(value, defaultIfNull);
    }

    /**
     * Converts a boxed value to a short.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public short asShort(Object value, boolean defaultIfNull) {
        if (value instanceof Short) {
            return (short) value;
        }
        return (short) tryBitwiseConversionToLong(value, defaultIfNull);
    }

    /**
     * Converts a boxed value to a char.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public char asChar(Object value, boolean defaultIfNull) {
        if (value instanceof Character) {
            return (char) value;
        }
        return (char) tryBitwiseConversionToLong(value, defaultIfNull);
    }

    /**
     * Converts a boxed value to an int.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public int asInt(Object value, boolean defaultIfNull) {
        if (value instanceof Integer) {
            return (int) value;
        }
        return (int) tryBitwiseConversionToLong(value, defaultIfNull);
    }

    /**
     * Converts a boxed value to a float.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public float asFloat(Object value, boolean defaultIfNull) {
        if (value instanceof Float) {
            return (float) value;
        }
        return Float.intBitsToFloat((int) tryBitwiseConversionToLong(value, defaultIfNull));
    }

    /**
     * Converts a boxed value to a double.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public double asDouble(Object value, boolean defaultIfNull) {
        if (value instanceof Double) {
            return (double) value;
        }
        return Double.longBitsToDouble(tryBitwiseConversionToLong(value, defaultIfNull));
    }

    /**
     * Converts a boxed value to a long.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will take the lower bits that fit in the primitive type or fill upper bits with 0. If the
     * conversion is not possible, throws {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    public long asLong(Object value, boolean defaultIfNull) {
        if (value instanceof Long) {
            return (long) value;
        }
        return tryBitwiseConversionToLong(value, defaultIfNull);
    }

    /**
     * Converts a Object value to a StaticObject.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will return StaticObject.NULL when the Object value is not a StaticObject. If the conversion
     * is not possible, throws {@link EspressoError}.
     */
    public StaticObject asObject(Object value) {
        if (value instanceof StaticObject) {
            return (StaticObject) value;
        }
        return hotSpotMaybeNull(value);
    }

    /**
     * Bitwise conversion from a boxed value to a long.
     * <p>
     * In {@link SpecComplianceMode#HOTSPOT HotSpot} compatibility-mode, the conversion is lax and
     * will fill the upper bits with 0. If the conversion is not possible, throws
     * {@link EspressoError}.
     *
     * @param defaultIfNull if true and value is {@link StaticObject#isNull(StaticObject) guest
     *            null}, the conversion will return the default value of the primitive type.
     */
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    private long tryBitwiseConversionToLong(Object value, boolean defaultIfNull) {
        if (getLanguage().getSpecComplianceMode() == HOTSPOT) {
            // Checkstyle: stop
            // @formatter:off
            if (value instanceof Boolean)   return ((boolean) value) ? 1 : 0;
            if (value instanceof Byte)      return (byte) value;
            if (value instanceof Short)     return (short) value;
            if (value instanceof Character) return (char) value;
            if (value instanceof Integer)   return (int) value;
            if (value instanceof Long)      return (long) value;
            if (value instanceof Float)     return Float.floatToRawIntBits((float) value);
            if (value instanceof Double)    return Double.doubleToRawLongBits((double) value);
            // @formatter:on
            // Checkstyle: resume
            if (defaultIfNull) {
                if (value instanceof StaticObject && StaticObject.isNull((StaticObject) value)) {
                    return 0L;
                }
            }
        }
        throw EspressoError.shouldNotReachHere("Unexpected primitive value: " + value);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    private StaticObject hotSpotMaybeNull(Object value) {
        assert !(value instanceof StaticObject);
        if (getLanguage().getSpecComplianceMode() == HOTSPOT) {
            return StaticObject.NULL;
        }
        throw EspressoError.shouldNotReachHere("Unexpected object:" + value);
    }

    // endregion Type conversions

    // region KnownTypes impl

    @Override
    public Klass java_lang_Object() {
        return java_lang_Object;
    }

    @Override
    public Klass java_lang_Throwable() {
        return java_lang_Throwable;
    }

    @Override
    public Klass java_lang_Class() {
        return java_lang_Class;
    }

    @Override
    public Klass java_lang_String() {
        return java_lang_String;
    }

    @Override
    public Klass java_lang_invoke_MethodType() {
        return java_lang_invoke_MethodType;
    }

    @Override
    public Klass java_lang_invoke_MethodHandle() {
        return java_lang_invoke_MethodHandle;
    }

    // endregion KnownTypes impl
}
