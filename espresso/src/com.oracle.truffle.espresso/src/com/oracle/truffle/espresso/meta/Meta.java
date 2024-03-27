/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.ALL;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.VERSION_16_OR_HIGHER;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.VERSION_17_OR_HIGHER;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.VERSION_19_OR_HIGHER;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.VERSION_8_OR_LOWER;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.VERSION_9_OR_HIGHER;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.higher;
import static com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange.lower;

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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
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
import com.oracle.truffle.espresso.substitutions.JImageExtensions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta extends ContextAccessImpl {

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
        java_lang_Object = knownKlass(Type.java_lang_Object);
        // Cloneable must be loaded before Serializable.
        java_lang_Cloneable = knownKlass(Type.java_lang_Cloneable);
        java_lang_Class = knownKlass(Type.java_lang_Class);
        java_lang_Class_classRedefinedCount = java_lang_Class.requireDeclaredField(Name.classRedefinedCount, Type._int);
        java_lang_Class_name = java_lang_Class.requireDeclaredField(Name.name, Type.java_lang_String);
        java_lang_Class_classLoader = java_lang_Class.requireDeclaredField(Name.classLoader, Type.java_lang_ClassLoader);
        java_lang_Class_componentType = diff() //
                        .field(VERSION_9_OR_HIGHER, Name.componentType, Type.java_lang_Class)//
                        .notRequiredField(java_lang_Class);
        java_lang_Class_classData = diff() //
                        .field(higher(15), Name.classData, Type.java_lang_Object)//
                        .notRequiredField(java_lang_Class);
        HIDDEN_MIRROR_KLASS = java_lang_Class.requireHiddenField(Name.HIDDEN_MIRROR_KLASS);
        HIDDEN_SIGNERS = java_lang_Class.requireHiddenField(Name.HIDDEN_SIGNERS);
        HIDDEN_PROTECTION_DOMAIN = java_lang_Class.requireHiddenField(Name.HIDDEN_PROTECTION_DOMAIN);

        if (getJavaVersion().modulesEnabled()) {
            java_lang_Class_module = java_lang_Class.requireDeclaredField(Name.module, Type.java_lang_Module);
        } else {
            java_lang_Class_module = null;
        }

        // Ensure that Object, Cloneable, Class and all its super-interfaces have the guest Class
        // initialized.
        initializeEspressoClassInHierarchy(java_lang_Cloneable);
        initializeEspressoClassInHierarchy(java_lang_Class);
        // From now on, all Klass'es will safely initialize the guest Class.

        java_io_Serializable = knownKlass(Type.java_io_Serializable);
        ARRAY_SUPERINTERFACES = new ObjectKlass[]{java_lang_Cloneable, java_io_Serializable};
        java_lang_Object_array = java_lang_Object.array();
        java_lang_Enum = knownKlass(Type.java_lang_Enum);

        EspressoError.guarantee(
                        new HashSet<>(Arrays.asList(ARRAY_SUPERINTERFACES)).equals(new HashSet<>(Arrays.asList(java_lang_Object_array.getSuperInterfaces()))),
                        "arrays super interfaces must contain java.lang.Cloneable and java.io.Serializable");

        java_lang_Class_array = java_lang_Class.array();
        java_lang_Class_getName = java_lang_Class.requireDeclaredMethod(Name.getName, Signature.String);
        java_lang_Class_getSimpleName = java_lang_Class.requireDeclaredMethod(Name.getSimpleName, Signature.String);
        java_lang_Class_getTypeName = java_lang_Class.requireDeclaredMethod(Name.getTypeName, Signature.String);
        java_lang_Class_forName_String = java_lang_Class.requireDeclaredMethod(Name.forName, Signature.Class_String);
        java_lang_Class_forName_String_boolean_ClassLoader = java_lang_Class.requireDeclaredMethod(Name.forName, Signature.Class_String_boolean_ClassLoader);

        java_lang_String = knownKlass(Type.java_lang_String);
        java_lang_String_array = java_lang_String.array();
        java_lang_CharSequence = knownKlass(Type.java_lang_CharSequence);

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

        java_lang_Number = knownKlass(Type.java_lang_Number);

        java_lang_Boolean_valueOf = java_lang_Boolean.requireDeclaredMethod(Name.valueOf, Signature.Boolean_boolean);
        java_lang_Byte_valueOf = java_lang_Byte.requireDeclaredMethod(Name.valueOf, Signature.Byte_byte);
        java_lang_Character_valueOf = java_lang_Character.requireDeclaredMethod(Name.valueOf, Signature.Character_char);
        java_lang_Short_valueOf = java_lang_Short.requireDeclaredMethod(Name.valueOf, Signature.Short_short);
        java_lang_Float_valueOf = java_lang_Float.requireDeclaredMethod(Name.valueOf, Signature.Float_float);
        java_lang_Integer_valueOf = java_lang_Integer.requireDeclaredMethod(Name.valueOf, Signature.Integer_int);
        java_lang_Double_valueOf = java_lang_Double.requireDeclaredMethod(Name.valueOf, Signature.Double_double);
        java_lang_Long_valueOf = java_lang_Long.requireDeclaredMethod(Name.valueOf, Signature.Long_long);

        java_lang_Boolean_value = java_lang_Boolean.requireDeclaredField(Name.value, Type._boolean);
        java_lang_Byte_value = java_lang_Byte.requireDeclaredField(Name.value, Type._byte);
        java_lang_Character_value = java_lang_Character.requireDeclaredField(Name.value, Type._char);
        java_lang_Short_value = java_lang_Short.requireDeclaredField(Name.value, Type._short);
        java_lang_Float_value = java_lang_Float.requireDeclaredField(Name.value, Type._float);
        java_lang_Integer_value = java_lang_Integer.requireDeclaredField(Name.value, Type._int);
        java_lang_Double_value = java_lang_Double.requireDeclaredField(Name.value, Type._double);
        java_lang_Long_value = java_lang_Long.requireDeclaredField(Name.value, Type._long);

        java_lang_String_value = diff() //
                        .field(VERSION_8_OR_LOWER, Name.value, Type._char_array) //
                        .field(VERSION_9_OR_HIGHER, Name.value, Type._byte_array) //
                        .field(java_lang_String);
        java_lang_String_hash = java_lang_String.requireDeclaredField(Name.hash, Type._int);
        java_lang_String_hashCode = java_lang_String.requireDeclaredMethod(Name.hashCode, Signature._int);
        java_lang_String_length = java_lang_String.requireDeclaredMethod(Name.length, Signature._int);
        java_lang_String_toCharArray = java_lang_String.requireDeclaredMethod(Name.toCharArray, Signature._char_array);
        java_lang_String_indexOf = java_lang_String.requireDeclaredMethod(Name.indexOf, Signature._int_int_int);
        java_lang_String_init_char_array = java_lang_String.requireDeclaredMethod(Name._init_, Signature._void_char_array);
        if (getJavaVersion().java9OrLater()) {
            java_lang_String_coder = java_lang_String.requireDeclaredField(Name.coder, Type._byte);
            java_lang_String_COMPACT_STRINGS = java_lang_String.requireDeclaredField(Name.COMPACT_STRINGS, Type._boolean);
        } else {
            java_lang_String_coder = null;
            java_lang_String_COMPACT_STRINGS = null;
        }

        java_lang_Throwable = knownKlass(Type.java_lang_Throwable);
        java_lang_Throwable_getStackTrace = java_lang_Throwable.requireDeclaredMethod(Name.getStackTrace, Signature.StackTraceElement_array);
        java_lang_Throwable_getMessage = java_lang_Throwable.requireDeclaredMethod(Name.getMessage, Signature.String);
        java_lang_Throwable_getCause = java_lang_Throwable.requireDeclaredMethod(Name.getCause, Signature.Throwable);
        HIDDEN_FRAMES = java_lang_Throwable.requireHiddenField(Name.HIDDEN_FRAMES);
        HIDDEN_EXCEPTION_WRAPPER = java_lang_Throwable.requireHiddenField(Name.HIDDEN_EXCEPTION_WRAPPER);
        java_lang_Throwable_backtrace = java_lang_Throwable.requireDeclaredField(Name.backtrace, Type.java_lang_Object);
        java_lang_Throwable_stackTrace = java_lang_Throwable.requireDeclaredField(Name.stackTrace, Type.java_lang_StackTraceElement_array);
        java_lang_Throwable_detailMessage = java_lang_Throwable.requireDeclaredField(Name.detailMessage, Type.java_lang_String);
        java_lang_Throwable_cause = java_lang_Throwable.requireDeclaredField(Name.cause, Type.java_lang_Throwable);
        if (getJavaVersion().java9OrLater()) {
            java_lang_Throwable_depth = java_lang_Throwable.requireDeclaredField(Name.depth, Type._int);
        } else {
            java_lang_Throwable_depth = null;
        }

        java_lang_StackTraceElement = knownKlass(Type.java_lang_StackTraceElement);
        java_lang_StackTraceElement_init = java_lang_StackTraceElement.requireDeclaredMethod(Name._init_, Signature._void_String_String_String_int);
        java_lang_StackTraceElement_declaringClass = java_lang_StackTraceElement.requireDeclaredField(Name.declaringClass, Type.java_lang_String);
        java_lang_StackTraceElement_methodName = java_lang_StackTraceElement.requireDeclaredField(Name.methodName, Type.java_lang_String);
        java_lang_StackTraceElement_fileName = java_lang_StackTraceElement.requireDeclaredField(Name.fileName, Type.java_lang_String);
        java_lang_StackTraceElement_lineNumber = java_lang_StackTraceElement.requireDeclaredField(Name.lineNumber, Type._int);
        if (getJavaVersion().java9OrLater()) {
            java_lang_StackTraceElement_declaringClassObject = java_lang_StackTraceElement.requireDeclaredField(Name.declaringClassObject, Type.java_lang_Class);
            java_lang_StackTraceElement_classLoaderName = java_lang_StackTraceElement.requireDeclaredField(Name.classLoaderName, Type.java_lang_String);
            java_lang_StackTraceElement_moduleName = java_lang_StackTraceElement.requireDeclaredField(Name.moduleName, Type.java_lang_String);
            java_lang_StackTraceElement_moduleVersion = java_lang_StackTraceElement.requireDeclaredField(Name.moduleVersion, Type.java_lang_String);
        } else {
            java_lang_StackTraceElement_declaringClassObject = null;
            java_lang_StackTraceElement_classLoaderName = null;
            java_lang_StackTraceElement_moduleName = null;
            java_lang_StackTraceElement_moduleVersion = null;
        }

        java_lang_Exception = knownKlass(Type.java_lang_Exception);
        java_lang_reflect_InvocationTargetException = knownKlass(Type.java_lang_reflect_InvocationTargetException);
        java_lang_NegativeArraySizeException = knownKlass(Type.java_lang_NegativeArraySizeException);
        java_lang_IllegalArgumentException = knownKlass(Type.java_lang_IllegalArgumentException);
        java_lang_IllegalStateException = knownKlass(Type.java_lang_IllegalStateException);
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
        java_util_NoSuchElementException = knownKlass(Type.java_util_NoSuchElementException);
        java_lang_NumberFormatException = knownKlass(Type.java_lang_NumberFormatException);

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

        // Initialize dispatch once common exceptions are discovered.
        this.dispatch = new ExceptionDispatch(this);

        java_security_PrivilegedActionException = knownKlass(Type.java_security_PrivilegedActionException);
        java_security_PrivilegedActionException_init_Exception = java_security_PrivilegedActionException.requireDeclaredMethod(Name._init_, Signature._void_Exception);

        java_lang_ClassLoader = knownKlass(Type.java_lang_ClassLoader);
        java_lang_ClassLoader$NativeLibrary = diff() //
                        .klass(lower(14), Type.java_lang_ClassLoader$NativeLibrary) //
                        .klass(higher(15), Type.jdk_internal_loader_NativeLibraries) //
                        .klass();
        java_lang_ClassLoader$NativeLibrary_getFromClass = java_lang_ClassLoader$NativeLibrary.requireDeclaredMethod(Name.getFromClass, Signature.Class);
        java_lang_ClassLoader_checkPackageAccess = java_lang_ClassLoader.requireDeclaredMethod(Name.checkPackageAccess, Signature.Class_PermissionDomain);
        java_lang_ClassLoader_findNative = java_lang_ClassLoader.requireDeclaredMethod(Name.findNative, Signature._long_ClassLoader_String);
        java_lang_ClassLoader_getSystemClassLoader = java_lang_ClassLoader.requireDeclaredMethod(Name.getSystemClassLoader, Signature.ClassLoader);
        java_lang_ClassLoader_parent = java_lang_ClassLoader.requireDeclaredField(Name.parent, Type.java_lang_ClassLoader);
        HIDDEN_CLASS_LOADER_REGISTRY = java_lang_ClassLoader.requireHiddenField(Name.HIDDEN_CLASS_LOADER_REGISTRY);
        if (getJavaVersion().java9OrLater()) {
            java_lang_ClassLoader_unnamedModule = java_lang_ClassLoader.requireDeclaredField(Name.unnamedModule, Type.java_lang_Module);
            java_lang_ClassLoader_name = java_lang_ClassLoader.requireDeclaredField(Name.name, Type.java_lang_String);
            java_lang_ClassLoader_nameAndId = java_lang_ClassLoader.requireDeclaredField(Name.nameAndId, Type.java_lang_String);
        } else {
            java_lang_ClassLoader_unnamedModule = null;
            java_lang_ClassLoader_name = null;
            java_lang_ClassLoader_nameAndId = null;
        }

        if (getJavaVersion().java19OrLater()) {
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl = knownKlass(Type.jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl);
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle = jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl.requireDeclaredField(Name.handle, Type._long);
        } else {
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl = null;
            jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle = null;
        }

        java_net_URL = knownKlass(Type.java_net_URL);

        java_lang_ClassLoader_getResourceAsStream = java_lang_ClassLoader.requireDeclaredMethod(Name.getResourceAsStream, Signature.InputStream_String);
        java_lang_ClassLoader_loadClass = java_lang_ClassLoader.requireDeclaredMethod(Name.loadClass, Signature.Class_String);
        java_io_InputStream = knownKlass(Type.java_io_InputStream);
        java_io_InputStream_read = java_io_InputStream.requireDeclaredMethod(Name.read, Signature._int_byte_array_int_int);
        java_io_InputStream_close = java_io_InputStream.requireDeclaredMethod(Name.close, Signature._void);
        java_io_PrintStream = knownKlass(Type.java_io_PrintStream);
        java_io_PrintStream_println = java_io_PrintStream.requireDeclaredMethod(Name.println, Signature._void_String);
        java_nio_file_Path = knownKlass(Type.java_nio_file_Path);
        java_nio_file_Paths = knownKlass(Type.java_nio_file_Paths);
        java_nio_file_Paths_get = java_nio_file_Paths.requireDeclaredMethod(Name.get, Signature.Path_String_String_array);

        sun_launcher_LauncherHelper = knownKlass(Type.sun_launcher_LauncherHelper);
        sun_launcher_LauncherHelper_printHelpMessage = sun_launcher_LauncherHelper.requireDeclaredMethod(Name.printHelpMessage, Signature._void_boolean);
        sun_launcher_LauncherHelper_ostream = sun_launcher_LauncherHelper.requireDeclaredField(Name.ostream, Type.java_io_PrintStream);

        // Guest reflection.
        java_lang_reflect_Executable = knownKlass(Type.java_lang_reflect_Executable);
        java_lang_reflect_Constructor = knownKlass(Type.java_lang_reflect_Constructor);
        java_lang_reflect_Constructor_init = java_lang_reflect_Constructor.requireDeclaredMethod(Name._init_, Signature.java_lang_reflect_Constructor_init_signature);

        HIDDEN_CONSTRUCTOR_KEY = java_lang_reflect_Constructor.requireHiddenField(Name.HIDDEN_CONSTRUCTOR_KEY);
        HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Constructor.requireHiddenField(Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Constructor_clazz = java_lang_reflect_Constructor.requireDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Constructor_root = java_lang_reflect_Constructor.requireDeclaredField(Name.root, Type.java_lang_reflect_Constructor);
        java_lang_reflect_Constructor_parameterTypes = java_lang_reflect_Constructor.requireDeclaredField(Name.parameterTypes, Type.java_lang_Class_array);
        java_lang_reflect_Constructor_signature = java_lang_reflect_Constructor.requireDeclaredField(Name.signature, Type.java_lang_String);

        java_lang_reflect_Method = knownKlass(Type.java_lang_reflect_Method);
        java_lang_reflect_Method_init = java_lang_reflect_Method.lookupDeclaredMethod(Name._init_, Signature.java_lang_reflect_Method_init_signature);
        HIDDEN_METHOD_KEY = java_lang_reflect_Method.requireHiddenField(Name.HIDDEN_METHOD_KEY);
        HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Method.requireHiddenField(Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Method_root = java_lang_reflect_Method.requireDeclaredField(Name.root, Type.java_lang_reflect_Method);
        java_lang_reflect_Method_clazz = java_lang_reflect_Method.requireDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Method_parameterTypes = java_lang_reflect_Method.requireDeclaredField(Name.parameterTypes, Type.java_lang_Class_array);

        java_lang_reflect_Parameter = knownKlass(Type.java_lang_reflect_Parameter);

        java_lang_reflect_Field = knownKlass(Type.java_lang_reflect_Field);
        HIDDEN_FIELD_KEY = java_lang_reflect_Field.requireHiddenField(Name.HIDDEN_FIELD_KEY);
        HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = java_lang_reflect_Field.requireHiddenField(Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        java_lang_reflect_Field_root = java_lang_reflect_Field.requireDeclaredField(Name.root, java_lang_reflect_Field.getType());
        java_lang_reflect_Field_class = java_lang_reflect_Field.requireDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_reflect_Field_name = java_lang_reflect_Field.requireDeclaredField(Name.name, Type.java_lang_String);
        java_lang_reflect_Field_type = java_lang_reflect_Field.requireDeclaredField(Name.type, Type.java_lang_Class);

        java_lang_reflect_Field_init = diff() //
                        .method(lower(14), Name._init_, Signature.java_lang_reflect_Field_init_signature) //
                        .method(higher(15), Name._init_, Signature.java_lang_reflect_Field_init_signature_15) //
                        .method(java_lang_reflect_Field);

        java_lang_Shutdown = knownKlass(Type.java_lang_Shutdown);
        java_lang_Shutdown_shutdown = java_lang_Shutdown.requireDeclaredMethod(Name.shutdown, Signature._void);

        java_nio_Buffer = knownKlass(Type.java_nio_Buffer);
        sun_nio_ch_DirectBuffer = knownKlass(Type.sun_nio_ch_DirectBuffer);
        java_nio_Buffer_address = java_nio_Buffer.requireDeclaredField(Name.address, Type._long);
        java_nio_Buffer_capacity = java_nio_Buffer.requireDeclaredField(Name.capacity, Type._int);
        java_nio_Buffer_limit = java_nio_Buffer.requireDeclaredMethod(Name.limit, Signature._int);
        java_nio_Buffer_isReadOnly = java_nio_Buffer.requireDeclaredMethod(Name.isReadOnly, Signature._boolean);

        java_nio_ByteBuffer = knownKlass(Type.java_nio_ByteBuffer);
        java_nio_ByteBuffer_wrap = java_nio_ByteBuffer.requireDeclaredMethod(Name.wrap, Signature.ByteBuffer_byte_array);
        if (getJavaVersion().java13OrLater()) {
            java_nio_ByteBuffer_get = java_nio_ByteBuffer.requireDeclaredMethod(Name.get, Signature.ByteBuffer_int_byte_array_int_int);
        } else {
            java_nio_ByteBuffer_get = null;
        }
        java_nio_ByteBuffer_getByte = java_nio_ByteBuffer.requireDeclaredMethod(Name.get, Signature._byte_int);
        java_nio_ByteBuffer_getShort = java_nio_ByteBuffer.requireDeclaredMethod(Name.getShort, Signature._short_int);
        java_nio_ByteBuffer_getInt = java_nio_ByteBuffer.requireDeclaredMethod(Name.getInt, Signature._int_int);
        java_nio_ByteBuffer_getLong = java_nio_ByteBuffer.requireDeclaredMethod(Name.getLong, Signature._long_int);
        java_nio_ByteBuffer_getFloat = java_nio_ByteBuffer.requireDeclaredMethod(Name.getFloat, Signature._float_int);
        java_nio_ByteBuffer_getDouble = java_nio_ByteBuffer.requireDeclaredMethod(Name.getDouble, Signature._double_int);
        java_nio_ByteBuffer_putByte = java_nio_ByteBuffer.requireDeclaredMethod(Name.put, Signature.ByteBuffer_int_byte);
        java_nio_ByteBuffer_putShort = java_nio_ByteBuffer.requireDeclaredMethod(Name.putShort, Signature.ByteBuffer_int_short);
        java_nio_ByteBuffer_putInt = java_nio_ByteBuffer.requireDeclaredMethod(Name.putInt, Signature.ByteBuffer_int_int);
        java_nio_ByteBuffer_putLong = java_nio_ByteBuffer.requireDeclaredMethod(Name.putLong, Signature.ByteBuffer_int_long);
        java_nio_ByteBuffer_putFloat = java_nio_ByteBuffer.requireDeclaredMethod(Name.putFloat, Signature.ByteBuffer_int_float);
        java_nio_ByteBuffer_putDouble = java_nio_ByteBuffer.requireDeclaredMethod(Name.putDouble, Signature.ByteBuffer_int_double);
        java_nio_ByteBuffer_order = java_nio_ByteBuffer.requireDeclaredMethod(Name.order, Signature.ByteOrder);
        java_nio_ByteBuffer_setOrder = java_nio_ByteBuffer.requireDeclaredMethod(Name.order, Signature.ByteBuffer_ByteOrder);

        java_nio_DirectByteBuffer = knownKlass(Type.java_nio_DirectByteBuffer);
        java_nio_DirectByteBuffer_init_long_int = diff() //
                        .method(lower(20), Name._init_, Signature._void_long_int) //
                        .method(higher(21), Name._init_, Signature._void_long_long) //
                        .method(java_nio_DirectByteBuffer);
        java_nio_ByteOrder = knownKlass(Type.java_nio_ByteOrder);
        java_nio_ByteOrder_LITTLE_ENDIAN = java_nio_ByteOrder.requireDeclaredField(Name.LITTLE_ENDIAN, Type.java_nio_ByteOrder);
        java_nio_ByteOrder_BIG_ENDIAN = java_nio_ByteOrder.requireDeclaredField(Name.BIG_ENDIAN, Type.java_nio_ByteOrder);

        java_lang_Thread = knownKlass(Type.java_lang_Thread);
        // The interrupted field is no longer hidden as of JDK14+
        HIDDEN_INTERRUPTED = diff() //
                        .field(lower(13), Name.HIDDEN_INTERRUPTED, Type._boolean)//
                        .field(higher(14), Name.interrupted, Type._boolean) //
                        .maybeHiddenfield(java_lang_Thread);
        HIDDEN_HOST_THREAD = java_lang_Thread.requireHiddenField(Name.HIDDEN_HOST_THREAD);
        HIDDEN_ESPRESSO_MANAGED = java_lang_Thread.requireHiddenField(Name.HIDDEN_ESPRESSO_MANAGED);
        HIDDEN_DEPRECATION_SUPPORT = java_lang_Thread.requireHiddenField(Name.HIDDEN_DEPRECATION_SUPPORT);
        HIDDEN_THREAD_UNPARK_SIGNALS = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_UNPARK_SIGNALS);
        HIDDEN_THREAD_PARK_LOCK = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_PARK_LOCK);
        if (getJavaVersion().java19OrLater()) {
            HIDDEN_THREAD_SCOPED_VALUE_CACHE = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_SCOPED_VALUE_CACHE);
        } else {
            HIDDEN_THREAD_SCOPED_VALUE_CACHE = null;
        }

        if (context.getEspressoEnv().EnableManagement) {
            HIDDEN_THREAD_PENDING_MONITOR = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_PENDING_MONITOR);
            HIDDEN_THREAD_WAITING_MONITOR = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_WAITING_MONITOR);
            HIDDEN_THREAD_BLOCKED_COUNT = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_BLOCKED_COUNT);
            HIDDEN_THREAD_WAITED_COUNT = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_WAITED_COUNT);
            HIDDEN_THREAD_DEPTH_FIRST_NUMBER = java_lang_Thread.requireHiddenField(Name.HIDDEN_THREAD_DEPTH_FIRST_NUMBER);
        } else {
            HIDDEN_THREAD_PENDING_MONITOR = null;
            HIDDEN_THREAD_WAITING_MONITOR = null;
            HIDDEN_THREAD_BLOCKED_COUNT = null;
            HIDDEN_THREAD_WAITED_COUNT = null;
            HIDDEN_THREAD_DEPTH_FIRST_NUMBER = null;
        }

        if (getJavaVersion().java19OrLater()) {
            java_lang_BaseVirtualThread = knownKlass(Type.java_lang_BaseVirtualThread);
            java_lang_Thread_threadGroup = null;
            java_lang_Thread$FieldHolder = knownKlass(Type.java_lang_Thread_FieldHolder);
            java_lang_Thread$Constants = knownKlass(Type.java_lang_Thread_Constants);
            java_lang_Thread$FieldHolder_group = java_lang_Thread$FieldHolder.requireDeclaredField(Name.group, Type.java_lang_ThreadGroup);
            java_lang_Thread$Constants_VTHREAD_GROUP = java_lang_Thread$Constants.requireDeclaredField(Name.VTHREAD_GROUP, Type.java_lang_ThreadGroup);
        } else {
            java_lang_BaseVirtualThread = null;
            java_lang_Thread$FieldHolder = null;
            java_lang_Thread$Constants = null;
            java_lang_Thread_threadGroup = java_lang_Thread.requireDeclaredField(Name.group, Type.java_lang_ThreadGroup);
            java_lang_Thread$FieldHolder_group = null;
            java_lang_Thread$Constants_VTHREAD_GROUP = null;
        }
        java_lang_ThreadGroup = knownKlass(Type.java_lang_ThreadGroup);
        if (getJavaVersion().java17OrEarlier()) {
            java_lang_ThreadGroup_add = java_lang_ThreadGroup.requireDeclaredMethod(Name.add, Signature._void_Thread);
        } else {
            java_lang_ThreadGroup_add = null;
        }
        java_lang_Thread_dispatchUncaughtException = java_lang_Thread.requireDeclaredMethod(Name.dispatchUncaughtException, Signature._void_Throwable);
        java_lang_Thread_init_ThreadGroup_Runnable = java_lang_Thread.requireDeclaredMethod(Name._init_, Signature._void_ThreadGroup_Runnable);
        java_lang_Thread_init_ThreadGroup_String = java_lang_Thread.requireDeclaredMethod(Name._init_, Signature._void_ThreadGroup_String);
        java_lang_Thread_interrupt = java_lang_Thread.requireDeclaredMethod(Name.interrupt, Signature._void);
        java_lang_Thread_exit = java_lang_Thread.requireDeclaredMethod(Name.exit, Signature._void);
        java_lang_Thread_run = java_lang_Thread.requireDeclaredMethod(Name.run, Signature._void);
        java_lang_Thread_getThreadGroup = java_lang_Thread.requireDeclaredMethod(Name.getThreadGroup, Signature.ThreadGroup);
        if (getJavaVersion().java17OrEarlier()) {
            java_lang_Thread_holder = null;

            java_lang_Thread_threadStatus = java_lang_Thread.requireDeclaredField(Name.threadStatus, Type._int);
            java_lang_Thread$FieldHolder_threadStatus = null;

            java_lang_Thread_priority = java_lang_Thread.requireDeclaredField(Name.priority, _int.getType());
            java_lang_Thread$FieldHolder_priority = null;

            java_lang_Thread_daemon = java_lang_Thread.requireDeclaredField(Name.daemon, Type._boolean);
            java_lang_Thread$FieldHolder_daemon = null;
        } else {
            java_lang_Thread_holder = java_lang_Thread.requireDeclaredField(Name.holder, java_lang_Thread$FieldHolder.getType());

            java_lang_Thread_threadStatus = null;
            java_lang_Thread$FieldHolder_threadStatus = java_lang_Thread$FieldHolder.requireDeclaredField(Name.threadStatus, Type._int);

            java_lang_Thread_priority = null;
            java_lang_Thread$FieldHolder_priority = java_lang_Thread$FieldHolder.requireDeclaredField(Name.priority, _int.getType());

            java_lang_Thread_daemon = null;
            java_lang_Thread$FieldHolder_daemon = java_lang_Thread$FieldHolder.requireDeclaredField(Name.daemon, Type._boolean);
        }
        java_lang_Thread_tid = java_lang_Thread.requireDeclaredField(Name.tid, Type._long);
        java_lang_Thread_eetop = java_lang_Thread.requireDeclaredField(Name.eetop, Type._long);
        java_lang_Thread_contextClassLoader = java_lang_Thread.requireDeclaredField(Name.contextClassLoader, Type.java_lang_ClassLoader);

        java_lang_Thread_name = java_lang_Thread.requireDeclaredField(Name.name, java_lang_String.getType());
        java_lang_Thread_inheritedAccessControlContext = java_lang_Thread.requireDeclaredField(Name.inheritedAccessControlContext, Type.java_security_AccessControlContext);
        java_lang_Thread_checkAccess = java_lang_Thread.requireDeclaredMethod(Name.checkAccess, Signature._void);
        java_lang_Thread_stop = java_lang_Thread.requireDeclaredMethod(Name.stop, Signature._void);
        java_lang_ThreadGroup_maxPriority = java_lang_ThreadGroup.requireDeclaredField(Name.maxPriority, Type._int);

        java_lang_ref_Finalizer$FinalizerThread = knownKlass(Type.java_lang_ref_Finalizer$FinalizerThread);
        java_lang_ref_Reference$ReferenceHandler = knownKlass(Type.java_lang_ref_Reference$ReferenceHandler);
        misc_InnocuousThread = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_InnocuousThread) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_misc_InnocuousThread) //
                        .klass();

        java_lang_System = knownKlass(Type.java_lang_System);
        java_lang_System_exit = java_lang_System.requireDeclaredMethod(Name.exit, Signature._void_int);
        java_lang_System_securityManager = java_lang_System.requireDeclaredField(Name.security, Type.java_lang_SecurityManager);

        java_security_ProtectionDomain = knownKlass(Type.java_security_ProtectionDomain);
        java_security_ProtectionDomain_impliesCreateAccessControlContext = diff() //
                        .method(lower(11), Name.impliesCreateAccessControlContext, Signature._boolean) //
                        .notRequiredMethod(java_security_ProtectionDomain);
        java_security_ProtectionDomain_init_CodeSource_PermissionCollection = diff() //
                        .method(lower(11), Name._init_, Signature._void_CodeSource_PermissionCollection) //
                        .notRequiredMethod(java_security_ProtectionDomain);

        java_security_AccessControlContext = knownKlass(Type.java_security_AccessControlContext);
        java_security_AccessControlContext_context = java_security_AccessControlContext.requireDeclaredField(Name.context, Type.java_security_ProtectionDomain_array);
        java_security_AccessControlContext_privilegedContext = java_security_AccessControlContext.requireDeclaredField(Name.privilegedContext, Type.java_security_AccessControlContext);
        java_security_AccessControlContext_isPrivileged = java_security_AccessControlContext.requireDeclaredField(Name.isPrivileged, Type._boolean);
        java_security_AccessControlContext_isAuthorized = java_security_AccessControlContext.requireDeclaredField(Name.isAuthorized, Type._boolean);
        java_security_AccessController = knownKlass(Type.java_security_AccessController);

        java_lang_invoke_MethodType = knownKlass(Type.java_lang_invoke_MethodType);
        java_lang_invoke_MethodType_ptypes = java_lang_invoke_MethodType.requireDeclaredField(Name.ptypes, Type.java_lang_Class_array);
        java_lang_invoke_MethodType_rtype = java_lang_invoke_MethodType.requireDeclaredField(Name.rtype, Type.java_lang_Class);

        java_lang_invoke_MemberName = knownKlass(Type.java_lang_invoke_MemberName);
        HIDDEN_VMINDEX = java_lang_invoke_MemberName.requireHiddenField(Name.HIDDEN_VMINDEX);
        HIDDEN_VMTARGET = java_lang_invoke_MemberName.requireHiddenField(Name.HIDDEN_VMTARGET);
        java_lang_invoke_MemberName_clazz = java_lang_invoke_MemberName.requireDeclaredField(Name.clazz, Type.java_lang_Class);
        java_lang_invoke_MemberName_name = java_lang_invoke_MemberName.requireDeclaredField(Name.name, Type.java_lang_String);
        java_lang_invoke_MemberName_type = java_lang_invoke_MemberName.requireDeclaredField(Name.type, Type.java_lang_Object);
        java_lang_invoke_MemberName_flags = java_lang_invoke_MemberName.requireDeclaredField(Name.flags, Type._int);

        java_lang_invoke_MethodHandle = knownKlass(Type.java_lang_invoke_MethodHandle);
        java_lang_invoke_MethodHandle_invokeExact = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.invokeExact, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invoke = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.invoke, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeBasic = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.invokeBasic, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_invokeWithArguments = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.invokeWithArguments, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToInterface = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.linkToInterface, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToSpecial = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.linkToSpecial, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToStatic = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.linkToStatic, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_linkToVirtual = java_lang_invoke_MethodHandle.requireDeclaredMethod(Name.linkToVirtual, Signature.Object_Object_array);
        java_lang_invoke_MethodHandle_type = java_lang_invoke_MethodHandle.requireDeclaredField(Name.type, Type.java_lang_invoke_MethodType);
        java_lang_invoke_MethodHandle_form = java_lang_invoke_MethodHandle.requireDeclaredField(Name.form, Type.java_lang_invoke_LambdaForm);

        java_lang_invoke_MethodHandles = knownKlass(Type.java_lang_invoke_MethodHandles);
        java_lang_invoke_MethodHandles_lookup = java_lang_invoke_MethodHandles.requireDeclaredMethod(Name.lookup, Signature.MethodHandles$Lookup);

        // j.l.i.VarHandles is there in JDK9+, but we only need it to be known for 14+
        java_lang_invoke_VarHandles = diff() //
                        .klass(higher(14), Type.java_lang_invoke_VarHandles) //
                        .notRequiredKlass();
        java_lang_invoke_VarHandles_getStaticFieldFromBaseAndOffset = diff() //
                        .method(higher(14), Name.getStaticFieldFromBaseAndOffset, Signature.Field_Object_long_Class) //
                        .notRequiredMethod(java_lang_invoke_VarHandles);

        java_lang_invoke_CallSite = knownKlass(Type.java_lang_invoke_CallSite);
        java_lang_invoke_CallSite_target = java_lang_invoke_CallSite.requireDeclaredField(Name.target, Type.java_lang_invoke_MethodHandle);

        java_lang_invoke_LambdaForm = knownKlass(Type.java_lang_invoke_LambdaForm);
        java_lang_invoke_LambdaForm_vmentry = java_lang_invoke_LambdaForm.requireDeclaredField(Name.vmentry, Type.java_lang_invoke_MemberName);
        java_lang_invoke_LambdaForm_isCompiled = java_lang_invoke_LambdaForm.requireDeclaredField(Name.isCompiled, Type._boolean);

        java_lang_invoke_MethodHandleNatives = knownKlass(Type.java_lang_invoke_MethodHandleNatives);
        java_lang_invoke_MethodHandleNatives_linkMethod = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Name.linkMethod, Signature.MemberName_Class_int_Class_String_Object_Object_array);
        java_lang_invoke_MethodHandleNatives_linkCallSite = diff() //
                        .method(VERSION_8_OR_LOWER, Name.linkCallSite, Signature.MemberName_Object_Object_Object_Object_Object_Object_array) //
                        .method(VERSION_9_OR_HIGHER, Name.linkCallSite, Signature.MemberName_Object_int_Object_Object_Object_Object_Object_array) //
                        .method(VERSION_19_OR_HIGHER, Name.linkCallSite, Signature.MemberName_Object_Object_Object_Object_Object_Object_array) //
                        .method(java_lang_invoke_MethodHandleNatives);

        java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Name.linkMethodHandleConstant,
                        Signature.MethodHandle_Class_int_Class_String_Object);
        java_lang_invoke_MethodHandleNatives_findMethodHandleType = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Name.findMethodHandleType, Signature.MethodType_Class_Class);

        java_lang_ref_Finalizer = knownKlass(Type.java_lang_ref_Finalizer);
        java_lang_ref_Finalizer_register = java_lang_ref_Finalizer.requireDeclaredMethod(Name.register, Signature._void_Object);

        java_lang_Object_wait = java_lang_Object.requireDeclaredMethod(Name.wait, Signature._void_long);
        java_lang_Object_toString = java_lang_Object.requireDeclaredMethod(Name.toString, Signature.String);

        // References
        java_lang_ref_Reference = knownKlass(Type.java_lang_ref_Reference);
        java_lang_ref_Reference_referent = java_lang_ref_Reference.requireDeclaredField(Name.referent, Type.java_lang_Object);
        java_lang_ref_Reference_enqueue = java_lang_ref_Reference.requireDeclaredMethod(Name.enqueue, Signature._boolean);
        java_lang_ref_Reference_getFromInactiveFinalReference = diff() //
                        .method(VERSION_16_OR_HIGHER, Name.getFromInactiveFinalReference, Signature.Object) //
                        .notRequiredMethod(java_lang_ref_Reference);
        java_lang_ref_Reference_clearInactiveFinalReference = diff() //
                        .method(VERSION_16_OR_HIGHER, Name.clearInactiveFinalReference, Signature._void) //
                        .notRequiredMethod(java_lang_ref_Reference);

        java_lang_ref_Reference_discovered = java_lang_ref_Reference.requireDeclaredField(Name.discovered, Type.java_lang_ref_Reference);
        java_lang_ref_Reference_next = java_lang_ref_Reference.requireDeclaredField(Name.next, Type.java_lang_ref_Reference);
        java_lang_ref_Reference_queue = java_lang_ref_Reference.requireDeclaredField(Name.queue, Type.java_lang_ref_ReferenceQueue);
        java_lang_ref_ReferenceQueue = knownKlass(Type.java_lang_ref_ReferenceQueue);
        java_lang_ref_ReferenceQueue_NULL = java_lang_ref_ReferenceQueue.requireDeclaredField(Name.NULL, Type.java_lang_ref_ReferenceQueue);

        java_lang_ref_WeakReference = knownKlass(Type.java_lang_ref_WeakReference);
        java_lang_ref_SoftReference = knownKlass(Type.java_lang_ref_SoftReference);
        java_lang_ref_PhantomReference = knownKlass(Type.java_lang_ref_PhantomReference);
        java_lang_ref_FinalReference = knownKlass(Type.java_lang_ref_FinalReference);
        HIDDEN_HOST_REFERENCE = java_lang_ref_Reference.requireHiddenField(Name.HIDDEN_HOST_REFERENCE);

        java_lang_AssertionStatusDirectives = knownKlass(Type.java_lang_AssertionStatusDirectives);
        java_lang_AssertionStatusDirectives_classes = java_lang_AssertionStatusDirectives.requireDeclaredField(Name.classes, Type.java_lang_String_array);
        java_lang_AssertionStatusDirectives_classEnabled = java_lang_AssertionStatusDirectives.requireDeclaredField(Name.classEnabled, Type._boolean_array);
        java_lang_AssertionStatusDirectives_packages = java_lang_AssertionStatusDirectives.requireDeclaredField(Name.packages, Type.java_lang_String_array);
        java_lang_AssertionStatusDirectives_packageEnabled = java_lang_AssertionStatusDirectives.requireDeclaredField(Name.packageEnabled, Type._boolean_array);
        java_lang_AssertionStatusDirectives_deflt = java_lang_AssertionStatusDirectives.requireDeclaredField(Name.deflt, Type._boolean);

        // Classes and Members that differ from Java 8 to 11

        if (getJavaVersion().java9OrLater()) {
            java_lang_System_initializeSystemClass = null;
            jdk_internal_loader_ClassLoaders = knownKlass(Type.jdk_internal_loader_ClassLoaders);
            jdk_internal_loader_ClassLoaders_platformClassLoader = jdk_internal_loader_ClassLoaders.requireDeclaredMethod(Name.platformClassLoader, Signature.ClassLoader);
            jdk_internal_loader_ClassLoaders$PlatformClassLoader = knownKlass(Type.jdk_internal_loader_ClassLoaders$PlatformClassLoader);
            java_lang_StackWalker = knownKlass(Type.java_lang_StackWalker);
            java_lang_StackStreamFactory_AbstractStackWalker = knownKlass(Type.java_lang_StackStreamFactory_AbstractStackWalker);
            java_lang_StackStreamFactory_AbstractStackWalker_doStackWalk = java_lang_StackStreamFactory_AbstractStackWalker.requireDeclaredMethod(Name.doStackWalk,
                            Signature.Object_long_int_int_int_int);

            java_lang_StackStreamFactory = knownKlass(Type.java_lang_StackStreamFactory);

            java_lang_StackFrameInfo = knownKlass(Type.java_lang_StackFrameInfo);
            java_lang_StackFrameInfo_memberName = java_lang_StackFrameInfo.requireDeclaredField(Name.memberName, Type.java_lang_Object);
            java_lang_StackFrameInfo_bci = java_lang_StackFrameInfo.requireDeclaredField(Name.bci, Type._int);

            java_lang_System_initPhase1 = java_lang_System.requireDeclaredMethod(Name.initPhase1, Signature._void);
            java_lang_System_initPhase2 = java_lang_System.requireDeclaredMethod(Name.initPhase2, Signature._int_boolean_boolean);
            java_lang_System_initPhase3 = java_lang_System.requireDeclaredMethod(Name.initPhase3, Signature._void);
        } else {
            java_lang_System_initializeSystemClass = java_lang_System.requireDeclaredMethod(Name.initializeSystemClass, Signature._void);
            jdk_internal_loader_ClassLoaders = null;
            jdk_internal_loader_ClassLoaders_platformClassLoader = null;
            jdk_internal_loader_ClassLoaders$PlatformClassLoader = null;
            java_lang_StackWalker = null;
            java_lang_StackStreamFactory_AbstractStackWalker = null;
            java_lang_StackStreamFactory_AbstractStackWalker_doStackWalk = null;

            java_lang_StackStreamFactory = null;

            java_lang_StackFrameInfo = null;
            java_lang_StackFrameInfo_memberName = null;
            java_lang_StackFrameInfo_bci = null;

            java_lang_System_initPhase1 = null;
            java_lang_System_initPhase2 = null;
            java_lang_System_initPhase3 = null;
        }

        jdk_internal_loader_ClassLoaders$AppClassLoader = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_Launcher$AppClassLoader) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_loader_ClassLoaders$AppClassLoader) //
                        .klass();

        if (getJavaVersion().modulesEnabled()) {
            java_lang_Module = knownKlass(Type.java_lang_Module);
            java_lang_Module_name = java_lang_Module.requireDeclaredField(Name.name, Type.java_lang_String);
            java_lang_Module_loader = java_lang_Module.requireDeclaredField(Name.loader, Type.java_lang_ClassLoader);
            java_lang_Module_descriptor = java_lang_Module.requireDeclaredField(Name.descriptor, Type.java_lang_module_ModuleDescriptor);
            HIDDEN_MODULE_ENTRY = java_lang_Module.requireHiddenField(Name.HIDDEN_MODULE_ENTRY);
            java_lang_module_ModuleDescriptor = knownKlass(Type.java_lang_module_ModuleDescriptor);
            java_lang_module_ModuleDescriptor_packages = java_lang_module_ModuleDescriptor.requireDeclaredField(Name.packages, Type.java_util_Set);
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
                        .klass(VERSION_16_OR_HIGHER, Type.java_lang_Record) //
                        .notRequiredKlass();
        java_lang_reflect_RecordComponent = diff() //
                        .klass(VERSION_16_OR_HIGHER, Type.java_lang_reflect_RecordComponent) //
                        .notRequiredKlass();
        java_lang_reflect_RecordComponent_clazz = diff() //
                        .field(ALL, Name.clazz, Type.java_lang_Class) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_name = diff() //
                        .field(ALL, Name.name, Type.java_lang_String) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_type = diff() //
                        .field(ALL, Name.type, Type.java_lang_Class) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_accessor = diff() //
                        .field(ALL, Name.accessor, Type.java_lang_reflect_Method) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_signature = diff() //
                        .field(ALL, Name.signature, Type.java_lang_String) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_annotations = diff() //
                        .field(ALL, Name.annotations, Type._byte_array) //
                        .notRequiredField(java_lang_reflect_RecordComponent);
        java_lang_reflect_RecordComponent_typeAnnotations = diff() //
                        .field(ALL, Name.typeAnnotations, Type._byte_array) //
                        .notRequiredField(java_lang_reflect_RecordComponent);

        sun_reflect_MagicAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_MagicAccessorImpl) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_MagicAccessorImpl) //
                        .klass();
        sun_reflect_DelegatingClassLoader = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_DelegatingClassLoader) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_DelegatingClassLoader) //
                        .klass();

        sun_reflect_MethodAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_MethodAccessorImpl) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_MethodAccessorImpl) //
                        .klass();
        sun_reflect_ConstructorAccessorImpl = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_ConstructorAccessorImpl) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_ConstructorAccessorImpl) //
                        .klass();

        sun_misc_Signal = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_Signal) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_misc_Signal) //
                        .klass();
        sun_misc_Signal_name = sun_misc_Signal.requireDeclaredField(Name.name, Type.java_lang_String);
        sun_misc_Signal_init_String = sun_misc_Signal.requireDeclaredMethod(Name._init_, Signature._void_String);
        sun_misc_NativeSignalHandler = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_NativeSignalHandler) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_misc_Signal$NativeHandler) //
                        .klass();
        sun_misc_NativeSignalHandler_handler = sun_misc_NativeSignalHandler.requireDeclaredField(Name.handler, Type._long);
        sun_misc_SignalHandler = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_SignalHandler) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_misc_Signal$Handler) //
                        .klass();
        sun_misc_SignalHandler_handle = diff() //
                        .method(VERSION_8_OR_LOWER, Name.handle, Signature._void_sun_misc_Signal) //
                        .method(VERSION_9_OR_HIGHER, Name.handle, Signature._void_jdk_internal_misc_Signal) //
                        .method(sun_misc_SignalHandler);
        sun_misc_SignalHandler_SIG_DFL = diff() //
                        .field(VERSION_8_OR_LOWER, Name.SIG_DFL, Type.sun_misc_SignalHandler) //
                        .field(VERSION_9_OR_HIGHER, Name.SIG_DFL, Type.jdk_internal_misc_Signal$Handler) //
                        .field(sun_misc_SignalHandler);
        sun_misc_SignalHandler_SIG_IGN = diff() //
                        .field(VERSION_8_OR_LOWER, Name.SIG_IGN, Type.sun_misc_SignalHandler) //
                        .field(VERSION_9_OR_HIGHER, Name.SIG_IGN, Type.jdk_internal_misc_Signal$Handler) //
                        .field(sun_misc_SignalHandler);

        sun_reflect_ConstantPool = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_ConstantPool) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_ConstantPool) //
                        .klass();
        sun_reflect_ConstantPool_constantPoolOop = sun_reflect_ConstantPool.requireDeclaredField(Name.constantPoolOop, Type.java_lang_Object);

        sun_misc_Cleaner = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_misc_Cleaner) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_ref_Cleaner) //
                        .klass();

        if (getJavaVersion().java8OrEarlier()) {
            java_lang_ref_Reference_pending = java_lang_ref_Reference.requireDeclaredField(Name.pending, Type.java_lang_ref_Reference);
        } else {
            java_lang_ref_Reference_pending = null;
        }
        java_lang_ref_Reference_lock = diff() //
                        .field(VERSION_8_OR_LOWER, Name.lock, Type.java_lang_ref_Reference$Lock) //
                        .field(VERSION_9_OR_HIGHER, Name.processPendingLock, Type.java_lang_Object) //
                        .field(java_lang_ref_Reference);

        sun_reflect_Reflection = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_reflect_Reflection) //
                        .klass(VERSION_9_OR_HIGHER, Type.jdk_internal_reflect_Reflection) //
                        .klass();
        sun_reflect_Reflection_getCallerClass = sun_reflect_Reflection.requireDeclaredMethod(Name.getCallerClass, Signature.Class);

        if (getJavaVersion().java11OrLater()) {
            if (getJavaVersion().java17OrEarlier()) {
                java_lang_invoke_MethodHandleNatives_linkDynamicConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Name.linkDynamicConstant,
                                Signature.Object_Object_int_Object_Object_Object_Object);
            } else {
                java_lang_invoke_MethodHandleNatives_linkDynamicConstant = java_lang_invoke_MethodHandleNatives.requireDeclaredMethod(Name.linkDynamicConstant,
                                Signature.Object_Object_Object_Object_Object_Object);
            }
        } else {
            java_lang_invoke_MethodHandleNatives_linkDynamicConstant = null;
        }

        // Interop
        java_time_Duration = knownKlass(Type.java_time_Duration);
        java_time_Duration_seconds = java_time_Duration.requireDeclaredField(Name.seconds, Type._long);
        java_time_Duration_nanos = java_time_Duration.requireDeclaredField(Name.nanos, Type._int);

        java_time_Instant = knownKlass(Type.java_time_Instant);
        java_time_Instant_seconds = java_time_Instant.requireDeclaredField(Name.seconds, Type._long);
        java_time_Instant_nanos = java_time_Instant.requireDeclaredField(Name.nanos, Type._int);
        java_time_Instant_atZone = java_time_Instant.requireDeclaredMethod(Name.atZone, Signature.ZonedDateTime_ZoneId);
        assert java_time_Instant_atZone.isFinalFlagSet() || java_time_Instant.isFinalFlagSet();
        java_time_Instant_ofEpochSecond = java_time_Instant.requireDeclaredMethod(Name.ofEpochSecond, Signature.Instant_long_long);

        java_time_LocalTime = knownKlass(Type.java_time_LocalTime);
        java_time_LocalTime_hour = java_time_LocalTime.requireDeclaredField(Name.hour, Type._byte);
        java_time_LocalTime_minute = java_time_LocalTime.requireDeclaredField(Name.minute, Type._byte);
        java_time_LocalTime_second = java_time_LocalTime.requireDeclaredField(Name.second, Type._byte);
        java_time_LocalTime_nano = java_time_LocalTime.requireDeclaredField(Name.nano, Type._int);
        java_time_LocalTime_of = java_time_LocalTime.requireDeclaredMethod(Name.of, Signature.LocalTime_int_int_int_int);

        java_time_LocalDateTime = knownKlass(Type.java_time_LocalDateTime);
        java_time_LocalDateTime_toLocalDate = java_time_LocalDateTime.requireDeclaredMethod(Name.toLocalDate, Signature.LocalDate);
        java_time_LocalDateTime_toLocalTime = java_time_LocalDateTime.requireDeclaredMethod(Name.toLocalTime, Signature.LocalTime);
        assert java_time_LocalDateTime_toLocalTime.isFinalFlagSet() || java_time_LocalDateTime.isFinalFlagSet();
        java_time_LocalDateTime_of = java_time_LocalDateTime.requireDeclaredMethod(Name.of, Signature.LocalDateTime_LocalDate_LocalTime);

        java_time_LocalDate = knownKlass(Type.java_time_LocalDate);
        java_time_LocalDate_year = java_time_LocalDate.requireDeclaredField(Name.year, Type._int);
        assert java_time_LocalDate_year.getKind() == JavaKind.Int;
        java_time_LocalDate_month = java_time_LocalDate.requireDeclaredField(Name.month, Type._short);
        assert java_time_LocalDate_month.getKind() == JavaKind.Short;
        java_time_LocalDate_day = java_time_LocalDate.requireDeclaredField(Name.day, Type._short);
        assert java_time_LocalDate_day.getKind() == JavaKind.Short;
        java_time_LocalDate_of = java_time_LocalDate.requireDeclaredMethod(Name.of, Signature.LocalDate_int_int_int);

        java_time_ZonedDateTime = knownKlass(Type.java_time_ZonedDateTime);
        java_time_ZonedDateTime_toLocalTime = java_time_ZonedDateTime.requireDeclaredMethod(Name.toLocalTime, Signature.LocalTime);
        assert java_time_ZonedDateTime_toLocalTime.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();

        java_time_ZonedDateTime_toLocalDate = java_time_ZonedDateTime.requireDeclaredMethod(Name.toLocalDate, Signature.LocalDate);
        assert java_time_ZonedDateTime_toLocalDate.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();

        java_time_ZonedDateTime_getZone = java_time_ZonedDateTime.requireDeclaredMethod(Name.getZone, Signature.ZoneId);
        assert java_time_ZonedDateTime_getZone.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();
        java_time_ZonedDateTime_toInstant = java_time_ZonedDateTime.requireMethod(Name.toInstant, Signature.Instant); // default
        assert java_time_ZonedDateTime_toInstant.isFinalFlagSet() || java_time_ZonedDateTime.isFinalFlagSet();
        java_time_ZonedDateTime_ofInstant = java_time_ZonedDateTime.requireDeclaredMethod(Name.ofInstant, Signature.ZonedDateTime_Instant_ZoneId);

        java_util_Date = knownKlass(Type.java_util_Date);
        java_util_Date_toInstant = java_util_Date.requireDeclaredMethod(Name.toInstant, Signature.Instant);
        java_util_Date_from = java_util_Date.requireDeclaredMethod(Name.from, Signature.Date_Instant);
        java_time_ZoneId = knownKlass(Type.java_time_ZoneId);
        java_time_ZoneId_getId = java_time_ZoneId.requireDeclaredMethod(Name.getId, Signature.String);
        java_time_ZoneId_of = java_time_ZoneId.requireDeclaredMethod(Name.of, Signature.ZoneId_String);
        assert java_time_ZoneId_of.isStatic();

        java_util_Map = knownKlass(Type.java_util_Map);
        java_util_Map_get = java_util_Map.requireDeclaredMethod(Name.get, Signature.Object_Object);
        java_util_Map_put = java_util_Map.requireDeclaredMethod(Name.put, Signature.Object_Object_Object);
        java_util_Map_size = java_util_Map.requireDeclaredMethod(Name.size, Signature._int);
        java_util_Map_remove = java_util_Map.requireDeclaredMethod(Name.remove, Signature.Object_Object);
        java_util_Map_containsKey = java_util_Map.requireDeclaredMethod(Name.containsKey, Signature._boolean_Object);
        java_util_Map_entrySet = java_util_Map.requireDeclaredMethod(Name.entrySet, Signature.java_util_Set);
        assert java_util_Map.isInterface();

        java_util_Map_Entry = knownKlass(Type.java_util_Map_Entry);
        java_util_Map_Entry_getKey = java_util_Map_Entry.requireDeclaredMethod(Name.getKey, Signature.Object);
        java_util_Map_Entry_getValue = java_util_Map_Entry.requireDeclaredMethod(Name.getValue, Signature.Object);
        java_util_Map_Entry_setValue = java_util_Map_Entry.requireDeclaredMethod(Name.setValue, Signature.Object_Object);

        java_util_List = knownKlass(Type.java_util_List);
        java_util_List_get = java_util_List.requireDeclaredMethod(Name.get, Signature.Object_int);
        java_util_List_set = java_util_List.requireDeclaredMethod(Name.set, Signature.Object_int_Object);
        java_util_List_size = java_util_List.requireDeclaredMethod(Name.size, Signature._int);
        java_util_List_add = java_util_List.requireDeclaredMethod(Name.add, Signature._boolean_Object);
        java_util_List_remove = java_util_List.requireDeclaredMethod(Name.remove, Signature.Object_int);
        assert java_util_List.isInterface();

        java_util_Set = knownKlass(Type.java_util_Set);
        java_util_Set_add = java_util_Set.requireDeclaredMethod(Name.add, Signature._boolean_Object);
        assert java_util_Set.isInterface();
        if (getJavaVersion().java9OrLater()) {
            java_util_Set_of = java_util_Set.requireDeclaredMethod(Name.of, Signature.Set_Object_array);
        } else {
            java_util_Set_of = null;
        }

        java_lang_Iterable = knownKlass(Type.java_lang_Iterable);
        java_lang_Iterable_iterator = java_lang_Iterable.requireDeclaredMethod(Name.iterator, Signature.java_util_Iterator);
        assert java_lang_Iterable.isInterface();

        java_util_Iterator = knownKlass(Type.java_util_Iterator);
        java_util_Iterator_next = java_util_Iterator.requireDeclaredMethod(Name.next, Signature.Object);
        java_util_Iterator_hasNext = java_util_Iterator.requireDeclaredMethod(Name.hasNext, Signature._boolean);
        java_util_Iterator_remove = java_util_Iterator.requireDeclaredMethod(Name.remove, Signature._void);
        assert java_util_Iterator.isInterface();

        java_util_Collection = knownKlass(Type.java_util_Collection);
        java_util_Collection_size = java_util_Collection.requireDeclaredMethod(Name.size, Signature._int);
        java_util_Collection_toArray = java_util_Collection.requireDeclaredMethod(Name.toArray, Signature.Object_array_Object_array);

        java_util_Optional = knownKlass(Type.java_util_Optional);
        java_util_Optional_EMPTY = java_util_Optional.requireDeclaredField(Name.EMPTY, Type.java_util_Optional);
        java_util_Optional_value = java_util_Optional.requireDeclaredField(Name.value, Type.java_lang_Object);

        java_math_BigInteger = knownKlass(Type.java_math_BigInteger);
        java_math_BigInteger_init = java_math_BigInteger.requireDeclaredMethod(Name._init_, Signature._void_byte_array);
        java_math_BigInteger_toByteArray = java_math_BigInteger.requireDeclaredMethod(Name.toByteArray, Signature._byte_array);

        java_math_BigDecimal = knownKlass(Type.java_math_BigDecimal);
        java_math_BigDecimal_init = java_math_BigDecimal.requireDeclaredMethod(Name._init_, Signature._void_BigInteger_int_MathContext);

        java_math_MathContext = knownKlass(Type.java_math_MathContext);
        java_math_MathContext_init = java_math_MathContext.requireDeclaredMethod(Name._init_, Signature._void_int);

        jdk_internal_misc_UnsafeConstants = diff() //
                        .klass(higher(13), Type.jdk_internal_misc_UnsafeConstants) //
                        .notRequiredKlass();
        if (jdk_internal_misc_UnsafeConstants != null) {
            jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0 = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Name.ADDRESS_SIZE0, Type._int);
            jdk_internal_misc_UnsafeConstants_PAGE_SIZE = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Name.PAGE_SIZE, Type._int);
            jdk_internal_misc_UnsafeConstants_BIG_ENDIAN = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Name.BIG_ENDIAN, Type._boolean);
            jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Name.UNALIGNED_ACCESS, Type._boolean);
            jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE = jdk_internal_misc_UnsafeConstants.requireDeclaredField(Name.DATA_CACHE_LINE_FLUSH_SIZE, Type._int);
        } else {
            jdk_internal_misc_UnsafeConstants_ADDRESS_SIZE0 = null;
            jdk_internal_misc_UnsafeConstants_PAGE_SIZE = null;
            jdk_internal_misc_UnsafeConstants_BIG_ENDIAN = null;
            jdk_internal_misc_UnsafeConstants_UNALIGNED_ACCESS = null;
            jdk_internal_misc_UnsafeConstants_DATA_CACHE_LINE_FLUSH_SIZE = null;
        }

        if (getJavaVersion().java9OrLater()) {
            jdk_internal_module_ModuleLoaderMap = knownKlass(Type.jdk_internal_module_ModuleLoaderMap);
            jdk_internal_module_ModuleLoaderMap_bootModules = jdk_internal_module_ModuleLoaderMap.requireDeclaredMethod(Name.bootModules, Signature.java_util_Set);
            jdk_internal_module_ModuleLoaderMap_platformModules = jdk_internal_module_ModuleLoaderMap.requireDeclaredMethod(Name.platformModules, Signature.java_util_Set);
            jdk_internal_module_SystemModuleFinders = knownKlass(Type.jdk_internal_module_SystemModuleFinders);
            jdk_internal_module_SystemModuleFinders_of = jdk_internal_module_SystemModuleFinders.requireDeclaredMethod(Name.of, Signature.ModuleFinder_SystemModules);
            jdk_internal_module_SystemModuleFinders_ofSystem = jdk_internal_module_SystemModuleFinders.requireDeclaredMethod(Name.ofSystem, Signature.ModuleFinder);
            jdk_internal_module_ModulePath = knownKlass(Type.jdk_internal_module_ModulePath);
            jdk_internal_module_ModulePath_of = jdk_internal_module_ModulePath.requireDeclaredMethod(Name.of, Signature.ModuleFinder_Path_array);
            java_lang_module_ModuleFinder = knownKlass(Type.java_lang_module_ModuleFinder);
            java_lang_module_ModuleFinder_compose = java_lang_module_ModuleFinder.requireDeclaredMethod(Name.compose, Signature.ModuleFinder_ModuleFinder_array);
            jdk_internal_module_Modules = knownKlass(Type.jdk_internal_module_Modules);
            jdk_internal_module_Modules_defineModule = jdk_internal_module_Modules.requireDeclaredMethod(Name.defineModule, Signature.Module_ClassLoader_ModuleDescriptor_URI);
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
        }

        if (getJavaVersion().java20OrLater()) {
            jdk_internal_foreign_abi_VMStorage = knownKlass(Type.jdk_internal_foreign_abi_VMStorage);
            jdk_internal_foreign_abi_VMStorage_type = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Name.type, Type._byte);
            jdk_internal_foreign_abi_VMStorage_segmentMaskOrSize = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Name.segmentMaskOrSize, Type._short);
            jdk_internal_foreign_abi_VMStorage_indexOrOffset = jdk_internal_foreign_abi_VMStorage.requireDeclaredField(Name.indexOrOffset, Type._int);
            jdk_internal_foreign_abi_NativeEntryPoint = knownKlass(Type.jdk_internal_foreign_abi_NativeEntryPoint);
            jdk_internal_foreign_abi_NativeEntryPoint_downcallStubAddress = jdk_internal_foreign_abi_NativeEntryPoint.requireDeclaredField(Name.downcallStubAddress, Type._long);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs = knownKlass(Type.jdk_internal_foreign_abi_UpcallLinker_CallRegs);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_argRegs = jdk_internal_foreign_abi_UpcallLinker_CallRegs.requireDeclaredField(Name.argRegs, Type.jdk_internal_foreign_abi_VMStorage_array);
            jdk_internal_foreign_abi_UpcallLinker_CallRegs_retRegs = jdk_internal_foreign_abi_UpcallLinker_CallRegs.requireDeclaredField(Name.retRegs, Type.jdk_internal_foreign_abi_VMStorage_array);
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
                        .klass(VERSION_17_OR_HIGHER, Type.jdk_internal_module_ModuleLoaderMap_Modules) //
                        .notRequiredKlass();
        jdk_internal_module_ModuleLoaderMap_Modules_clinit = diff() //
                        .method(ALL, Name._clinit_, Signature._void) //
                        .notRequiredMethod(jdk_internal_module_ModuleLoaderMap_Modules);

        interopDispatch = new InteropKlassesDispatch(this);
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
        java_lang_management_MemoryUsage = loadKlassWithBootClassLoader(Type.java_lang_management_MemoryUsage);

        java_lang_management_ThreadInfo = loadKlassWithBootClassLoader(Type.java_lang_management_ThreadInfo);

        sun_management_ManagementFactory = diff() //
                        .klass(VERSION_8_OR_LOWER, Type.sun_management_ManagementFactory) //
                        .klass(VERSION_9_OR_HIGHER, Type.sun_management_ManagementFactoryHelper) //
                        .notRequiredKlass();
        if (sun_management_ManagementFactory != null) {
            // MemoryPoolMXBean createMemoryPool(String var0, boolean var1, long var2, long var4)
            sun_management_ManagementFactory_createMemoryPool = sun_management_ManagementFactory.requireDeclaredMethod(Name.createMemoryPool, Signature.MemoryPoolMXBean_String_boolean_long_long);
            // MemoryManagerMXBean createMemoryManager(String var0)
            sun_management_ManagementFactory_createMemoryManager = sun_management_ManagementFactory.requireDeclaredMethod(Name.createMemoryManager, Signature.MemoryManagerMXBean_String);
            // GarbageCollectorMXBean createGarbageCollector(String var0, String var1)
            sun_management_ManagementFactory_createGarbageCollector = sun_management_ManagementFactory.requireDeclaredMethod(Name.createGarbageCollector,
                            Signature.GarbageCollectorMXBean_String_String);
        } else {
            // MemoryPoolMXBean createMemoryPool(String var0, boolean var1, long var2, long var4)
            sun_management_ManagementFactory_createMemoryPool = null;
            // MemoryManagerMXBean createMemoryManager(String var0)
            sun_management_ManagementFactory_createMemoryManager = null;
            // GarbageCollectorMXBean createGarbageCollector(String var0, String var1)
            sun_management_ManagementFactory_createGarbageCollector = null;
        }

        // used for class redefinition
        java_lang_reflect_Proxy = knownKlass(Type.java_lang_reflect_Proxy);

        // java.beans package only available if java.desktop module is present on JDK9+
        java_beans_ThreadGroupContext = loadKlassWithBootClassLoader(Type.java_beans_ThreadGroupContext);
        java_beans_Introspector = loadKlassWithBootClassLoader(Type.java_beans_Introspector);

        java_beans_ThreadGroupContext_init = java_beans_ThreadGroupContext != null ? java_beans_ThreadGroupContext.requireDeclaredMethod(Name._init_, Signature._void) : null;
        java_beans_ThreadGroupContext_removeBeanInfo = java_beans_ThreadGroupContext != null ? java_beans_ThreadGroupContext.requireDeclaredMethod(Name.removeBeanInfo, Signature._void_Class) : null;
        java_beans_Introspector_flushFromCaches = java_beans_Introspector != null ? java_beans_Introspector.requireDeclaredMethod(Name.flushFromCaches, Signature._void_Class) : null;

        // sun.misc.Proxygenerator -> java.lang.reflect.Proxygenerator in JDK 9
        if (getJavaVersion().java8OrEarlier()) {
            sun_misc_ProxyGenerator = knownKlass(Type.sun_misc_ProxyGenerator);
            sun_misc_ProxyGenerator_generateProxyClass = sun_misc_ProxyGenerator.lookupDeclaredMethod(Name.generateProxyClass, Signature._byte_array_String_Class_array_int);

            java_lang_reflect_ProxyGenerator = null;
            java_lang_reflect_ProxyGenerator_generateProxyClass = null;
        } else {
            sun_misc_ProxyGenerator = null;
            sun_misc_ProxyGenerator_generateProxyClass = null;

            java_lang_reflect_ProxyGenerator = knownKlass(Type.java_lang_reflect_ProxyGenerator);
            java_lang_reflect_ProxyGenerator_generateProxyClass = diff() //
                            .method(lower(13), Name.generateProxyClass, Signature._byte_array_String_Class_array_int) //
                            .method(higher(14), Name.generateProxyClass, Signature._byte_array_ClassLoader_String_List_int) //
                            .notRequiredMethod(java_lang_reflect_ProxyGenerator);
        }

        // Load Espresso's Polyglot API.
        boolean polyglotSupport = getContext().getEnv().getOptions().get(EspressoOptions.Polyglot);
        this.polyglot = polyglotSupport ? new PolyglotSupport() : null;

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
    }

    private void extendModulePackages(ModuleTable.ModuleEntry moduleEntry, Set<String> extraPackages) {
        StaticObject moduleDescriptor = java_lang_Module_descriptor.getObject(moduleEntry.module());
        StaticObject origPackages = java_lang_module_ModuleDescriptor_packages.getObject(moduleDescriptor);
        StaticObject newPackages = extendedStringSet(origPackages, extraPackages);
        java_lang_module_ModuleDescriptor_packages.setObject(moduleDescriptor, newPackages);
    }

    public @JavaType(Set.class) StaticObject extendedStringSet(@JavaType(Set.class) StaticObject original, Collection<String> extraStrings) {
        Method getSizeImpl = ((ObjectKlass) original.getKlass()).itableLookup(java_util_Set, java_util_Collection_size.getITableIndex());
        int origSize = (int) getSizeImpl.invokeDirect(original);
        StaticObject stringArray = java_lang_String.allocateReferenceArray(origSize + extraStrings.size());
        Method toArrayImpl = ((ObjectKlass) original.getKlass()).itableLookup(java_util_Set, java_util_Collection_toArray.getITableIndex());
        StaticObject toArrayResult = (StaticObject) toArrayImpl.invokeDirect(original, stringArray);
        assert toArrayResult == stringArray;
        StaticObject[] unwrappedStringArray = stringArray.unwrap(getLanguage());
        int idx = origSize;
        for (String extraPackage : extraStrings) {
            assert StaticObject.isNull(unwrappedStringArray[idx]);
            unwrappedStringArray[idx++] = toGuestString(extraPackage);
        }
        return (StaticObject) java_util_Set_of.invokeDirect(null, stringArray);
    }

    private DiffVersionLoadHelper diff() {
        return new DiffVersionLoadHelper(this);
    }

    // Checkstyle: stop field name check

    public final ObjectKlass java_lang_Object;
    public final ArrayKlass java_lang_Object_array;

    public final ObjectKlass java_lang_String;
    public final ArrayKlass java_lang_String_array;
    public final ObjectKlass java_lang_Class;
    public final ObjectKlass java_lang_CharSequence;
    public final Field HIDDEN_MIRROR_KLASS;
    public final Field HIDDEN_PROTECTION_DOMAIN;
    public final Field HIDDEN_SIGNERS;
    public final Field java_lang_Class_module;
    public final Field java_lang_Class_classLoader;
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

    public final ObjectKlass java_io_PrintStream;
    public final Method java_io_PrintStream_println;

    public final ObjectKlass java_nio_file_Path;
    public final ObjectKlass java_nio_file_Paths;
    public final Method java_nio_file_Paths_get;

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
    public final Field java_lang_invoke_MethodHandle_type;
    public final Field java_lang_invoke_MethodHandle_form;

    public final ObjectKlass java_lang_invoke_MethodHandles;
    public final Method java_lang_invoke_MethodHandles_lookup;

    public final ObjectKlass java_lang_invoke_VarHandles;
    public final Method java_lang_invoke_VarHandles_getStaticFieldFromBaseAndOffset;

    public final ObjectKlass java_lang_invoke_CallSite;
    public final Field java_lang_invoke_CallSite_target;

    public final ObjectKlass java_lang_invoke_LambdaForm;
    public final Field java_lang_invoke_LambdaForm_vmentry;
    public final Field java_lang_invoke_LambdaForm_isCompiled;

    public final ObjectKlass java_lang_invoke_MethodHandleNatives;
    public final Method java_lang_invoke_MethodHandleNatives_linkMethod;
    public final Method java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant;
    public final Method java_lang_invoke_MethodHandleNatives_findMethodHandleType;
    public final Method java_lang_invoke_MethodHandleNatives_linkCallSite;
    public final Method java_lang_invoke_MethodHandleNatives_linkDynamicConstant;

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

    public final ObjectKlass java_lang_StackFrameInfo;
    public final Field java_lang_StackFrameInfo_memberName;
    public final Field java_lang_StackFrameInfo_bci;

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
                EspressoError.guarantee(loadKlassWithBootClassLoader(Type.com_oracle_truffle_espresso_polyglot_Polyglot) != null,
                                "polyglot.jar (Polyglot API) is not accessible");
            } else {
                EspressoError.guarantee(loadKlassOrNull(Type.com_oracle_truffle_espresso_polyglot_Polyglot, getPlatformClassLoader()) != null,
                                "polyglot.jar (Polyglot API) is not accessible");
            }

            ArityException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_ArityException);
            ArityException_create_int_int_int = ArityException.requireDeclaredMethod(Name.create, Signature.ArityException_int_int_int);
            ArityException_create_int_int_int_Throwable = ArityException.requireDeclaredMethod(Name.create, Signature.ArityException_int_int_int_Throwable);

            UnknownIdentifierException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException);
            UnknownIdentifierException_create_String = UnknownIdentifierException.requireDeclaredMethod(Name.create, Signature.UnknownIdentifierException_String);
            UnknownIdentifierException_create_String_Throwable = UnknownIdentifierException.requireDeclaredMethod(Name.create, Signature.UnknownIdentifierException_String_Throwable);

            UnsupportedMessageException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException);
            UnsupportedMessageException_create = UnsupportedMessageException.requireDeclaredMethod(Name.create, Signature.UnsupportedMessageException);
            UnsupportedMessageException_create_Throwable = UnsupportedMessageException.requireDeclaredMethod(Name.create, Signature.UnsupportedMessageException_Throwable);

            UnsupportedTypeException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException);
            UnsupportedTypeException_create_Object_array_String = UnsupportedTypeException.requireDeclaredMethod(Name.create, Signature.UnsupportedTypeException_Object_array_String);
            UnsupportedTypeException_create_Object_array_String_Throwable = UnsupportedTypeException.requireDeclaredMethod(Name.create,
                            Signature.UnsupportedTypeException_Object_array_String_Throwable);

            InvalidArrayIndexException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException);
            InvalidArrayIndexException_create_long = InvalidArrayIndexException.requireDeclaredMethod(Name.create, Signature.InvalidArrayIndexException_long);
            InvalidArrayIndexException_create_long_Throwable = InvalidArrayIndexException.requireDeclaredMethod(Name.create, Signature.InvalidArrayIndexException_long_Throwable);

            InvalidBufferOffsetException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_InvalidBufferOffsetException);
            InvalidBufferOffsetException_create_long_long = InvalidBufferOffsetException.requireDeclaredMethod(Name.create, Signature.InvalidBufferOffsetException_long_long);
            InvalidBufferOffsetException_create_long_long_Throwable = InvalidBufferOffsetException.requireDeclaredMethod(Name.create, Signature.InvalidBufferOffsetException_long_long_Throwable);

            StopIterationException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_StopIterationException);
            StopIterationException_create = StopIterationException.requireDeclaredMethod(Name.create, Signature.StopIterationException);
            StopIterationException_create_Throwable = StopIterationException.requireDeclaredMethod(Name.create, Signature.StopIterationException_Throwable);

            UnknownKeyException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_UnknownKeyException);
            UnknownKeyException_create_Object = UnknownKeyException.requireDeclaredMethod(Name.create, Signature.UnknownKeyException_Object);
            UnknownKeyException_create_Object_Throwable = UnknownKeyException.requireDeclaredMethod(Name.create, Signature.UnknownKeyException_Object_Throwable);

            ForeignException = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_ForeignException);
            ExceptionType = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_ExceptionType);

            ExceptionType_EXIT = ExceptionType.requireDeclaredField(Name.EXIT,
                            Type.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_INTERRUPT = ExceptionType.requireDeclaredField(Name.INTERRUPT,
                            Type.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_RUNTIME_ERROR = ExceptionType.requireDeclaredField(Name.RUNTIME_ERROR,
                            Type.com_oracle_truffle_espresso_polyglot_ExceptionType);
            ExceptionType_PARSE_ERROR = ExceptionType.requireDeclaredField(Name.PARSE_ERROR,
                            Type.com_oracle_truffle_espresso_polyglot_ExceptionType);

            VMHelper = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_VMHelper);
            VMHelper_getDynamicModuleDescriptor = VMHelper.requireDeclaredMethod(Name.getDynamicModuleDescriptor, Signature.ModuleDescriptor_String_String);

            EspressoForeignList = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignList);
            EspressoForeignCollection = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignCollection);
            EspressoForeignIterable = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterable);
            EspressoForeignIterator = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterator);
            EspressoForeignMap = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignMap);
            EspressoForeignSet = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_collections_EspressoForeignSet);

            EspressoForeignNumber = knownPlatformKlass(Type.com_oracle_truffle_espresso_polyglot_impl_EspressoForeignNumber);
        }
    }

    @CompilationFinal //
    public PolyglotSupport polyglot;

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
    public @JavaType(Throwable.class) static StaticObject initExceptionWithMessage(@JavaType(Throwable.class) ObjectKlass exceptionKlass, @JavaType(String.class) StaticObject message) {
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
    public @JavaType(Throwable.class) static StaticObject initExceptionWithMessage(@JavaType(Throwable.class) ObjectKlass exceptionKlass, String message) {
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
    public @JavaType(Throwable.class) static StaticObject initException(@JavaType(Throwable.class) ObjectKlass exceptionKlass) {
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
    public @JavaType(Throwable.class) static StaticObject initExceptionWithCause(@JavaType(Throwable.class) ObjectKlass exceptionKlass, @JavaType(Throwable.class) StaticObject cause) {
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
     * Throws a guest {@link NullPointerException}. A guest instance is allocated and initialized by
     * calling the {@link NullPointerException#NullPointerException() default constructor}.
     */
    public EspressoException throwNullPointerException() {
        throw throwException(java_lang_NullPointerException);
    }

    @TruffleBoundary
    public EspressoException throwIllegalArgumentExceptionBoundary() {
        throw throwException(java_lang_IllegalArgumentException);
    }

    // endregion Guest exception handling (throw)

    ObjectKlass knownKlass(Symbol<Type> type) {
        return knownKlass(type, StaticObject.NULL);
    }

    private ObjectKlass knownPlatformKlass(Symbol<Type> type) {
        // known platform classes are loaded by the platform loader on JDK 11 and
        // by the boot classloader on JDK 8
        return knownKlass(type, getJavaVersion().java8OrEarlier() ? StaticObject.NULL : getPlatformClassLoader());
    }

    private ObjectKlass knownKlass(Symbol<Type> type, StaticObject classLoader) {
        CompilerAsserts.neverPartOfCompilation();
        assert !Types.isArray(type);
        assert !Types.isPrimitive(type);
        ObjectKlass k = loadKlassOrNull(type, classLoader);
        if (k == null) {
            throw EspressoError.shouldNotReachHere("Failed loading known class: " + type + ", discovered java version: " + getJavaVersion());
        }
        return k;
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
        Klass k = loadKlassOrNull(type, classLoader, protectionDomain);
        if (k == null) {
            throw throwException(java_lang_NoClassDefFoundError);
        }
        return k;
    }

    /**
     * Same as {@link #loadKlassOrFail(Symbol, StaticObject, StaticObject)}, except this method
     * returns null instead of throwing if class is not found. Note that this mthod can still throw
     * due to other errors (class file malformed, etc...)
     *
     * @see #loadKlassOrFail(Symbol, StaticObject, StaticObject)
     */
    @TruffleBoundary
    public Klass loadKlassOrNull(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        try {
            return getRegistries().loadKlass(type, classLoader, protectionDomain);
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(this);
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
            cachedPlatformClassLoader = (StaticObject) jdk_internal_loader_ClassLoaders_platformClassLoader.invokeDirect(StaticObject.NULL);
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
        if (Types.isArray(type)) {
            Klass elemental = resolveSymbolOrNull(getTypes().getElementalType(type), classLoader, protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }
        return loadKlassOrNull(type, classLoader, protectionDomain);
    }

    /**
     * Same as {@link #resolveSymbolOrNull(Symbol, StaticObject, StaticObject)}, except this throws
     * an exception of the given klass if the representation for the type can not be found.
     *
     * @see #resolveSymbolOrNull(Symbol, StaticObject, StaticObject)
     */
    public Klass resolveSymbolOrFail(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, ObjectKlass exception, StaticObject protectionDomain) {
        Klass k = resolveSymbolOrNull(type, classLoader, protectionDomain);
        if (k == null) {
            throw throwException(exception);
        }
        return k;
    }

    /**
     * Same as {@link #resolveSymbolOrFail(Symbol, StaticObject, ObjectKlass, StaticObject)}, but
     * throws {@link NoClassDefFoundError} by default..
     */
    public Klass resolveSymbolOrFail(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) {
        return resolveSymbolOrFail(type, classLoader, java_lang_NoClassDefFoundError, protectionDomain);
    }

    /**
     * Resolves the symbol using {@link #resolveSymbolOrFail(Symbol, StaticObject, StaticObject)},
     * and applies access checking, possibly throwing {@link IllegalAccessError}.
     */
    public Klass resolveSymbolAndAccessCheck(Symbol<Type> type, ObjectKlass accessingKlass) {
        assert accessingKlass != null;
        Klass klass = resolveSymbolOrFail(type, accessingKlass.getDefiningClassLoader(), java_lang_NoClassDefFoundError, accessingKlass.protectionDomain());
        if (!Klass.checkAccess(klass.getElementalType(), accessingKlass, false)) {
            throw throwException(java_lang_IllegalAccessError);
        }
        return klass;
    }

    @TruffleBoundary
    public String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        return stringConversion.toHost(str, getLanguage(), this);
    }

    @TruffleBoundary
    public static String toHostStringStatic(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        return str.getKlass().getMeta().toHostString(str);
    }

    @TruffleBoundary
    public StaticObject toGuestString(Symbol<?> hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        return toGuestString(hostString.toString());
    }

    @TruffleBoundary
    public StaticObject toGuestString(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        return stringConversion.toGuest(hostString, this);
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
                return guestObject.unwrap(getLanguage());
            }
            if (guestObject.getKlass() == java_lang_String) {
                return toHostString(guestObject);
            }
            return unboxGuest((StaticObject) object);
        }
        return object;
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
        return (StaticObject) java_lang_Boolean_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Byte.class) StaticObject boxByte(byte value) {
        return (StaticObject) java_lang_Byte_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Character.class) StaticObject boxCharacter(char value) {
        return (StaticObject) java_lang_Character_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Short.class) StaticObject boxShort(short value) {
        return (StaticObject) java_lang_Short_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Float.class) StaticObject boxFloat(float value) {
        return (StaticObject) java_lang_Float_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Integer.class) StaticObject boxInteger(int value) {
        return (StaticObject) java_lang_Integer_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Double.class) StaticObject boxDouble(double value) {
        return (StaticObject) java_lang_Double_valueOf.invokeDirect(null, value);
    }

    public @JavaType(Long.class) StaticObject boxLong(long value) {
        return (StaticObject) java_lang_Long_valueOf.invokeDirect(null, value);
    }

    public StaticObject boxPrimitive(Object hostPrimitive) {
        if (hostPrimitive instanceof Integer) {
            return (StaticObject) getMeta().java_lang_Integer_valueOf.invokeDirect(null, (int) hostPrimitive);
        }
        if (hostPrimitive instanceof Boolean) {
            return (StaticObject) getMeta().java_lang_Boolean_valueOf.invokeDirect(null, (boolean) hostPrimitive);
        }
        if (hostPrimitive instanceof Byte) {
            return (StaticObject) getMeta().java_lang_Byte_valueOf.invokeDirect(null, (byte) hostPrimitive);
        }
        if (hostPrimitive instanceof Character) {
            return (StaticObject) getMeta().java_lang_Character_valueOf.invokeDirect(null, (char) hostPrimitive);
        }
        if (hostPrimitive instanceof Short) {
            return (StaticObject) getMeta().java_lang_Short_valueOf.invokeDirect(null, (short) hostPrimitive);
        }
        if (hostPrimitive instanceof Float) {
            return (StaticObject) getMeta().java_lang_Float_valueOf.invokeDirect(null, (float) hostPrimitive);
        }
        if (hostPrimitive instanceof Double) {
            return (StaticObject) getMeta().java_lang_Double_valueOf.invokeDirect(null, (double) hostPrimitive);
        }
        if (hostPrimitive instanceof Long) {
            return (StaticObject) getMeta().java_lang_Long_valueOf.invokeDirect(null, (long) hostPrimitive);
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
}
