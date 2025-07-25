/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

public class EspressoSymbols {

    // Pre-allocate enough slots to avoid resizing the underlying map.
    // But not too much, since these maps will be persisted in the image heap (Native Image).
    public static final StaticSymbols SYMBOLS = new StaticSymbols(ParserSymbols.SYMBOLS, 1 << 12);

    static {
        Names.ensureInitialized();
        Types.ensureInitialized();
        Signatures.ensureInitialized();
    }

    public static void ensureInitialized() {
        /* nop */
    }

    /**
     * Contains commonly used (type) symbols.
     *
     * <p>
     * Naming convention: Use the fully qualified type name, '_' as package separator and '$' as
     * separator for inner classes.<br>
     * - {@link #_long}<br>
     * - {@link #java_lang_Object}<br>
     * - {@link #java_lang_String_array}<br>
     * - {@link #java_lang_ref_Finalizer$FinalizerThread}<br>
     */
    public static final class Types {

        public static void ensureInitialized() {
            // Sanity check.
            assert Types.java_lang_Object == ParserSymbols.ParserTypes.java_lang_Object;
        }

        // Core types.
        public static final Symbol<Type> java_lang_String = SYMBOLS.putType("Ljava/lang/String;");
        public static final Symbol<Type> java_lang_String_array = SYMBOLS.putType("[Ljava/lang/String;");
        public static final Symbol<Type> java_lang_CharSequence = SYMBOLS.putType("Ljava/lang/CharSequence;");

        public static final Symbol<Type> java_lang_Object = SYMBOLS.putType("Ljava/lang/Object;");
        public static final Symbol<Type> java_lang_Object_array = SYMBOLS.putType("[Ljava/lang/Object;");

        public static final Symbol<Type> java_lang_Class = SYMBOLS.putType("Ljava/lang/Class;");
        public static final Symbol<Type> java_lang_Class_array = SYMBOLS.putType("[Ljava/lang/Class;");

        public static final Symbol<Type> java_lang_Enum = SYMBOLS.putType("Ljava/lang/Enum;");

        public static final Symbol<Type> java_lang_Throwable = SYMBOLS.putType("Ljava/lang/Throwable;");
        public static final Symbol<Type> java_lang_Exception = SYMBOLS.putType("Ljava/lang/Exception;");
        public static final Symbol<Type> java_lang_System = SYMBOLS.putType("Ljava/lang/System;");
        public static final Symbol<Type> java_security_ProtectionDomain = SYMBOLS.putType("Ljava/security/ProtectionDomain;");
        public static final Symbol<Type> java_security_ProtectionDomain_array = SYMBOLS.putType("[Ljava/security/ProtectionDomain;");
        public static final Symbol<Type> java_security_AccessControlContext = SYMBOLS.putType("Ljava/security/AccessControlContext;");
        public static final Symbol<Type> java_security_AccessController = SYMBOLS.putType("Ljava/security/AccessController;");
        public static final Symbol<Type> java_lang_SecurityManager = SYMBOLS.putType("Ljava/lang/SecurityManager;");
        public static final Symbol<Type> java_security_CodeSource = SYMBOLS.putType("Ljava/security/CodeSource;");
        public static final Symbol<Type> java_security_PermissionCollection = SYMBOLS.putType("Ljava/security/PermissionCollection;");

        public static final Symbol<Type> java_lang_ClassLoader = SYMBOLS.putType("Ljava/lang/ClassLoader;");
        public static final Symbol<Type> java_lang_ClassLoader$NativeLibrary = SYMBOLS.putType("Ljava/lang/ClassLoader$NativeLibrary;");
        public static final Symbol<Type> jdk_internal_loader_NativeLibraries = SYMBOLS.putType("Ljdk/internal/loader/NativeLibraries;");
        public static final Symbol<Type> jdk_internal_loader_NativeLibraries$NativeLibraryImpl = SYMBOLS.putType("Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;");
        public static final Symbol<Type> sun_misc_Launcher$ExtClassLoader = SYMBOLS.putType("Lsun/misc/Launcher$ExtClassLoader;");
        public static final Symbol<Type> sun_instrument_InstrumentationImpl = SYMBOLS.putType("Lsun/instrument/InstrumentationImpl;");
        public static final Symbol<Type> java_lang_instrument_ClassDefinition = SYMBOLS.putType("Ljava/lang/instrument/ClassDefinition;");
        public static final Symbol<Type> java_lang_instrument_UnmodifiableClassException = SYMBOLS.putType("Ljava/lang/instrument/UnmodifiableClassException;");
        public static final Symbol<Type> jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl = SYMBOLS.putType("Ljdk/internal/loader/RawNativeLibraries$RawNativeLibraryImpl;");
        public static final Symbol<Type> jdk_internal_util_ArraysSupport = SYMBOLS.putType("Ljdk/internal/util/ArraysSupport;");
        public static final Symbol<Type> jdk_internal_util_SystemProps_Raw = SYMBOLS.putType("Ljdk/internal/util/SystemProps$Raw;");

        // CDS
        public static final Symbol<Type> jdk_internal_misc_CDS = SYMBOLS.putType("Ljdk/internal/misc/CDS;");
        public static final Symbol<Type> jdk_internal_module_ArchivedBootLayer = SYMBOLS.putType("Ljdk/internal/module/ArchivedBootLayer;");

        // io
        public static final Symbol<Type> java_io_InputStream = SYMBOLS.putType("Ljava/io/InputStream;");
        public static final Symbol<Type> java_io_PrintStream = SYMBOLS.putType("Ljava/io/PrintStream;");
        public static final Symbol<Type> java_io_IOException = SYMBOLS.putType("Ljava/io/IOException;");
        public static final Symbol<Type> java_io_File = SYMBOLS.putType("Ljava/io/File;");
        public static final Symbol<Type> java_io_FileNotFoundException = SYMBOLS.putType("Ljava/io/FileNotFoundException;");
        public static final Symbol<Type> java_nio_channels_ClosedByInterruptException = SYMBOLS.putType("Ljava/nio/channels/ClosedByInterruptException;");
        public static final Symbol<Type> java_nio_channels_AsynchronousCloseException = SYMBOLS.putType("Ljava/nio/channels/AsynchronousCloseException;");
        public static final Symbol<Type> java_nio_channels_ClosedChannelException = SYMBOLS.putType("Ljava/nio/channels/ClosedChannelException;");
        public static final Symbol<Type> java_io_FileDescriptor = SYMBOLS.putType("Ljava/io/FileDescriptor;");
        public static final Symbol<Type> java_io_FileInputStream = SYMBOLS.putType("Ljava/io/FileInputStream;");
        public static final Symbol<Type> java_io_FileOutputStream = SYMBOLS.putType("Ljava/io/FileOutputStream;");
        public static final Symbol<Type> java_io_FileSystem = SYMBOLS.putType("Ljava/io/FileSystem;");

        public static final Symbol<Type> java_io_TruffleFileSystem = SYMBOLS.putType("Ljava/io/TruffleFileSystem;");
        public static final Symbol<Type> java_io_DefaultFileSystem = SYMBOLS.putType("Ljava/io/DefaultFileSystem;");

        public static final Symbol<Type> java_io_RandomAccessFile = SYMBOLS.putType("Ljava/io/RandomAccessFile;");

        public static final Symbol<Type> java_nio_file_Path = SYMBOLS.putType("Ljava/nio/file/Path;");
        public static final Symbol<Type> java_nio_file_Path_array = SYMBOLS.putType("[Ljava/nio/file/Path;");
        public static final Symbol<Type> java_nio_file_Paths = SYMBOLS.putType("Ljava/nio/file/Paths;");
        public static final Symbol<Type> java_nio_file_FileSystems_DefaultFileSystemHolder = SYMBOLS.putType("Ljava/nio/file/FileSystems$DefaultFileSystemHolder;");
        public static final Symbol<Type> java_nio_file_FileAlreadyExistsException = SYMBOLS.putType("Ljava/nio/file/FileAlreadyExistsException;");
        public static final Symbol<Type> java_nio_file_DirectoryNotEmptyException = SYMBOLS.putType("Ljava/nio/file/DirectoryNotEmptyException;");
        public static final Symbol<Type> java_nio_file_AtomicMoveNotSupportedException = SYMBOLS.putType("Ljava/nio/file/AtomicMoveNotSupportedException;");
        public static final Symbol<Type> java_nio_file_AccessDeniedException = SYMBOLS.putType("Ljava/nio/file/AccessDeniedException;");
        public static final Symbol<Type> java_nio_file_NoSuchFileException = SYMBOLS.putType("Ljava/nio/file/NoSuchFileException;");
        public static final Symbol<Type> java_nio_file_InvalidPathException = SYMBOLS.putType("Ljava/nio/file/InvalidPathException;");
        public static final Symbol<Type> java_nio_file_NotDirectoryException = SYMBOLS.putType("Ljava/nio/file/NotDirectoryException;");

        public static final Symbol<Type> java_nio_file_NotLinkException = SYMBOLS.putType("Ljava/nio/file/NotLinkException;");

        public static final Symbol<Type> java_nio_channels_FileChannel = SYMBOLS.putType("Ljava/nio/channels/FileChannel;");

        public static final Symbol<Type> sun_nio_fs_TruffleBasicFileAttributes = SYMBOLS.putType("Lsun/nio/fs/TruffleBasicFileAttributes;");
        public static final Symbol<Type> sun_nio_fs_TrufflePath = SYMBOLS.putType("Lsun/nio/fs/TrufflePath;");

        public static final Symbol<Type> sun_nio_fs_TruffleFileSystem = SYMBOLS.putType("Lsun/nio/fs/TruffleFileSystem;");
        public static final Symbol<Type> sun_nio_fs_TruffleFileSystemProvider = SYMBOLS.putType("Lsun/nio/fs/TruffleFileSystemProvider;");
        public static final Symbol<Type> sun_nio_fs_FileAttributeParser = SYMBOLS.putType("Lsun/nio/fs/FileAttributeParser;");
        public static final Symbol<Type> sun_nio_ch_FileChannelImpl = SYMBOLS.putType("Lsun/nio/ch/FileChannelImpl;");
        public static final Symbol<Type> sun_nio_fs_DefaultFileSystemProvider = SYMBOLS.putType("Lsun/nio/fs/DefaultFileSystemProvider;");
        public static final Symbol<Type> sun_nio_ch_NativeThread = SYMBOLS.putType("Lsun/nio/ch/NativeThread;");

        public static final Symbol<Type> jdk_internal_loader_ClassLoaders = SYMBOLS.putType("Ljdk/internal/loader/ClassLoaders;");
        public static final Symbol<Type> jdk_internal_loader_ClassLoaders$PlatformClassLoader = SYMBOLS.putType("Ljdk/internal/loader/ClassLoaders$PlatformClassLoader;");
        public static final Symbol<Type> jdk_internal_loader_ClassLoaders$AppClassLoader = SYMBOLS.putType("Ljdk/internal/loader/ClassLoaders$AppClassLoader;");
        public static final Symbol<Type> sun_misc_Launcher$AppClassLoader = SYMBOLS.putType("Lsun/misc/Launcher$AppClassLoader;");
        public static final Symbol<Type> jdk_internal_module_ModuleLoaderMap = SYMBOLS.putType("Ljdk/internal/module/ModuleLoaderMap;");
        public static final Symbol<Type> jdk_internal_module_ModuleLoaderMap_Modules = SYMBOLS.putType("Ljdk/internal/module/ModuleLoaderMap$Modules;");
        public static final Symbol<Type> jdk_internal_module_SystemModuleFinders = SYMBOLS.putType("Ljdk/internal/module/SystemModuleFinders;");
        public static final Symbol<Type> jdk_internal_module_SystemModules = SYMBOLS.putType("Ljdk/internal/module/SystemModules;");
        public static final Symbol<Type> java_lang_module_ModuleFinder = SYMBOLS.putType("Ljava/lang/module/ModuleFinder;");
        public static final Symbol<Type> java_lang_module_ModuleFinder_array = SYMBOLS.putType("[Ljava/lang/module/ModuleFinder;");
        public static final Symbol<Type> jdk_internal_module_ModulePath = SYMBOLS.putType("Ljdk/internal/module/ModulePath;");
        public static final Symbol<Type> jdk_internal_module_Modules = SYMBOLS.putType("Ljdk/internal/module/Modules;");
        public static final Symbol<Type> java_lang_module_ModuleDescriptor = SYMBOLS.putType("Ljava/lang/module/ModuleDescriptor;");

        // Espresso Libs
        public static final Symbol<Type> java_util_zip_CRC32 = SYMBOLS.putType("Ljava/util/zip/CRC32;");
        public static final Symbol<Type> java_util_zip_Inflater = SYMBOLS.putType("Ljava/util/zip/Inflater;");
        public static final Symbol<Type> java_util_zip_DataFormatException = SYMBOLS.putType("Ljava/util/zip/DataFormatException;");

        // URL class loader
        public static final Symbol<Type> java_net_URLClassLoader = SYMBOLS.putType("Ljava/net/URLClassLoader;");
        public static final Symbol<Type> java_net_URL = SYMBOLS.putType("Ljava/net/URL;");
        public static final Symbol<Type> java_net_URI = SYMBOLS.putType("Ljava/net/URI;");
        public static final Symbol<Type> java_net_URL_array = SYMBOLS.putType("[Ljava/net/URL;");

        public static final Symbol<Type> java_beans_Introspector = SYMBOLS.putType("Ljava/beans/Introspector;");
        public static final Symbol<Type> java_beans_ThreadGroupContext = SYMBOLS.putType("Ljava/beans/ThreadGroupContext;");

        public static final Symbol<Type> java_lang_reflect_Proxy = SYMBOLS.putType("Ljava/lang/reflect/Proxy;");
        public static final Symbol<Type> java_lang_reflect_ProxyGenerator = SYMBOLS.putType("Ljava/lang/reflect/ProxyGenerator;");
        public static final Symbol<Type> sun_misc_ProxyGenerator = SYMBOLS.putType("Lsun/misc/ProxyGenerator;");

        // Primitive types.
        public static final Symbol<Type> _boolean = SYMBOLS.putType("Z" /* boolean */);
        public static final Symbol<Type> _byte = SYMBOLS.putType("B" /* byte */);
        public static final Symbol<Type> _char = SYMBOLS.putType("C" /* char */);
        public static final Symbol<Type> _short = SYMBOLS.putType("S" /* short */);
        public static final Symbol<Type> _int = SYMBOLS.putType("I" /* int */);
        public static final Symbol<Type> _float = SYMBOLS.putType("F" /* float */);
        public static final Symbol<Type> _double = SYMBOLS.putType("D" /* double */);
        public static final Symbol<Type> _long = SYMBOLS.putType("J" /* long */);
        public static final Symbol<Type> _void = SYMBOLS.putType("V" /* void */);

        public static final Symbol<Type> _boolean_array = SYMBOLS.putType("[Z" /* boolean[] */);
        public static final Symbol<Type> _byte_array = SYMBOLS.putType("[B" /* byte[] */);
        public static final Symbol<Type> _char_array = SYMBOLS.putType("[C" /* char[] */);
        public static final Symbol<Type> _short_array = SYMBOLS.putType("[S" /* short[] */);
        public static final Symbol<Type> _int_array = SYMBOLS.putType("[I" /* int[] */);
        public static final Symbol<Type> _float_array = SYMBOLS.putType("[F" /* float[] */);
        public static final Symbol<Type> _double_array = SYMBOLS.putType("[D" /* double[] */);
        public static final Symbol<Type> _long_array = SYMBOLS.putType("[J" /* long[] */);

        // Boxed types.
        public static final Symbol<Type> java_lang_Boolean = SYMBOLS.putType("Ljava/lang/Boolean;");
        public static final Symbol<Type> java_lang_Byte = SYMBOLS.putType("Ljava/lang/Byte;");
        public static final Symbol<Type> java_lang_Character = SYMBOLS.putType("Ljava/lang/Character;");
        public static final Symbol<Type> java_lang_Short = SYMBOLS.putType("Ljava/lang/Short;");
        public static final Symbol<Type> java_lang_Integer = SYMBOLS.putType("Ljava/lang/Integer;");
        public static final Symbol<Type> java_lang_Float = SYMBOLS.putType("Ljava/lang/Float;");
        public static final Symbol<Type> java_lang_Double = SYMBOLS.putType("Ljava/lang/Double;");
        public static final Symbol<Type> java_lang_Long = SYMBOLS.putType("Ljava/lang/Long;");
        public static final Symbol<Type> java_lang_Void = SYMBOLS.putType("Ljava/lang/Void;");
        public static final Symbol<Type> java_lang_Number = SYMBOLS.putType("Ljava/lang/Number;");

        public static final Symbol<Type> java_lang_Cloneable = SYMBOLS.putType("Ljava/lang/Cloneable;");

        public static final Symbol<Type> java_lang_StackOverflowError = SYMBOLS.putType("Ljava/lang/StackOverflowError;");
        public static final Symbol<Type> java_lang_VirtualMachineError = SYMBOLS.putType("Ljava/lang/VirtualMachineError;");
        public static final Symbol<Type> java_lang_OutOfMemoryError = SYMBOLS.putType("Ljava/lang/OutOfMemoryError;");
        public static final Symbol<Type> java_lang_AssertionError = SYMBOLS.putType("Ljava/lang/AssertionError;");

        public static final Symbol<Type> java_lang_NullPointerException = SYMBOLS.putType("Ljava/lang/NullPointerException;");
        public static final Symbol<Type> java_lang_ClassCastException = SYMBOLS.putType("Ljava/lang/ClassCastException;");
        public static final Symbol<Type> java_lang_ArrayStoreException = SYMBOLS.putType("Ljava/lang/ArrayStoreException;");
        public static final Symbol<Type> java_lang_ArithmeticException = SYMBOLS.putType("Ljava/lang/ArithmeticException;");
        public static final Symbol<Type> java_lang_IllegalMonitorStateException = SYMBOLS.putType("Ljava/lang/IllegalMonitorStateException;");
        public static final Symbol<Type> java_lang_IllegalArgumentException = SYMBOLS.putType("Ljava/lang/IllegalArgumentException;");
        public static final Symbol<Type> java_lang_IllegalStateException = SYMBOLS.putType("Ljava/lang/IllegalStateException;");
        public static final Symbol<Type> java_lang_ClassNotFoundException = SYMBOLS.putType("Ljava/lang/ClassNotFoundException;");
        public static final Symbol<Type> java_lang_NoClassDefFoundError = SYMBOLS.putType("Ljava/lang/NoClassDefFoundError;");
        public static final Symbol<Type> java_lang_InterruptedException = SYMBOLS.putType("Ljava/lang/InterruptedException;");
        public static final Symbol<Type> java_lang_ThreadDeath = SYMBOLS.putType("Ljava/lang/ThreadDeath;");
        public static final Symbol<Type> java_lang_NegativeArraySizeException = SYMBOLS.putType("Ljava/lang/NegativeArraySizeException;");
        public static final Symbol<Type> java_lang_RuntimeException = SYMBOLS.putType("Ljava/lang/RuntimeException;");
        public static final Symbol<Type> java_lang_IndexOutOfBoundsException = SYMBOLS.putType("Ljava/lang/IndexOutOfBoundsException;");
        public static final Symbol<Type> java_lang_ArrayIndexOutOfBoundsException = SYMBOLS.putType("Ljava/lang/ArrayIndexOutOfBoundsException;");
        public static final Symbol<Type> java_lang_StringIndexOutOfBoundsException = SYMBOLS.putType("Ljava/lang/StringIndexOutOfBoundsException;");
        public static final Symbol<Type> java_lang_ExceptionInInitializerError = SYMBOLS.putType("Ljava/lang/ExceptionInInitializerError;");
        public static final Symbol<Type> java_lang_InstantiationException = SYMBOLS.putType("Ljava/lang/InstantiationException;");
        public static final Symbol<Type> java_lang_InstantiationError = SYMBOLS.putType("Ljava/lang/InstantiationError;");
        public static final Symbol<Type> java_lang_CloneNotSupportedException = SYMBOLS.putType("Ljava/lang/CloneNotSupportedException;");
        public static final Symbol<Type> java_lang_SecurityException = SYMBOLS.putType("Ljava/lang/SecurityException;");
        public static final Symbol<Type> java_lang_LinkageError = SYMBOLS.putType("Ljava/lang/LinkageError;");
        public static final Symbol<Type> java_lang_BootstrapMethodError = SYMBOLS.putType("Ljava/lang/BootstrapMethodError;");
        public static final Symbol<Type> java_lang_NoSuchFieldException = SYMBOLS.putType("Ljava/lang/NoSuchFieldException;");
        public static final Symbol<Type> java_lang_NoSuchMethodException = SYMBOLS.putType("Ljava/lang/NoSuchMethodException;");
        public static final Symbol<Type> java_lang_UnsupportedOperationException = SYMBOLS.putType("Ljava/lang/UnsupportedOperationException;");
        public static final Symbol<Type> java_lang_UnsupportedClassVersionError = SYMBOLS.putType("Ljava/lang/UnsupportedClassVersionError;");
        public static final Symbol<Type> java_lang_reflect_InvocationTargetException = SYMBOLS.putType("Ljava/lang/reflect/InvocationTargetException;");
        public static final Symbol<Type> java_lang_NumberFormatException = SYMBOLS.putType("Ljava/lang/NumberFormatException;");

        public static final Symbol<Type> java_lang_reflect_Type = SYMBOLS.putType("Ljava/lang/reflect/Type;");
        public static final Symbol<Type> java_lang_reflect_Type_array = SYMBOLS.putType("[Ljava/lang/reflect/Type;");
        public static final Symbol<Type> java_lang_reflect_ParameterizedType = SYMBOLS.putType("Ljava/lang/reflect/ParameterizedType;");

        public static final Symbol<Type> java_lang_Thread = SYMBOLS.putType("Ljava/lang/Thread;");
        public static final Symbol<Type> java_lang_Thread_FieldHolder = SYMBOLS.putType("Ljava/lang/Thread$FieldHolder;");
        public static final Symbol<Type> java_lang_Thread_Constants = SYMBOLS.putType("Ljava/lang/Thread$Constants;");
        public static final Symbol<Type> java_lang_ThreadGroup = SYMBOLS.putType("Ljava/lang/ThreadGroup;");
        public static final Symbol<Type> java_lang_BaseVirtualThread = SYMBOLS.putType("Ljava/lang/BaseVirtualThread;");

        public static final Symbol<Type> java_lang_Runnable = SYMBOLS.putType("Ljava/lang/Runnable;");

        public static final Symbol<Type> sun_misc_VM = SYMBOLS.putType("Lsun/misc/VM;");
        public static final Symbol<Type> jdk_internal_misc_VM = SYMBOLS.putType("Ljdk/internal/misc/VM;");
        public static final Symbol<Type> java_lang_Thread$State = SYMBOLS.putType("Ljava/lang/Thread$State;");
        public static final Symbol<Type> jdk_internal_vm_ContinuationScope = SYMBOLS.putType("Ljdk/internal/vm/ContinuationScope;");
        public static final Symbol<Type> jdk_internal_vm_Continuation = SYMBOLS.putType("Ljdk/internal/vm/Continuation;");

        public static final Symbol<Type> sun_misc_Signal = SYMBOLS.putType("Lsun/misc/Signal;");
        public static final Symbol<Type> jdk_internal_misc_Signal = SYMBOLS.putType("Ljdk/internal/misc/Signal;");
        public static final Symbol<Type> sun_misc_NativeSignalHandler = SYMBOLS.putType("Lsun/misc/NativeSignalHandler;");
        public static final Symbol<Type> jdk_internal_misc_Signal$NativeHandler = SYMBOLS.putType("Ljdk/internal/misc/Signal$NativeHandler;");
        public static final Symbol<Type> sun_misc_SignalHandler = SYMBOLS.putType("Lsun/misc/SignalHandler;");
        public static final Symbol<Type> jdk_internal_misc_Signal$Handler = SYMBOLS.putType("Ljdk/internal/misc/Signal$Handler;");

        public static final Symbol<Type> sun_nio_ch_DirectBuffer = SYMBOLS.putType("Lsun/nio/ch/DirectBuffer;");
        public static final Symbol<Type> java_nio_Buffer = SYMBOLS.putType("Ljava/nio/Buffer;");

        // Guest reflection.
        public static final Symbol<Type> java_lang_reflect_Field = SYMBOLS.putType("Ljava/lang/reflect/Field;");
        public static final Symbol<Type> java_lang_reflect_Method = SYMBOLS.putType("Ljava/lang/reflect/Method;");
        public static final Symbol<Type> java_lang_reflect_Constructor = SYMBOLS.putType("Ljava/lang/reflect/Constructor;");
        public static final Symbol<Type> java_lang_reflect_Parameter = SYMBOLS.putType("Ljava/lang/reflect/Parameter;");
        public static final Symbol<Type> java_lang_reflect_Executable = SYMBOLS.putType("Ljava/lang/reflect/Executable;");
        public static final Symbol<Type> sun_reflect_Reflection = SYMBOLS.putType("Lsun/reflect/Reflection;");
        public static final Symbol<Type> jdk_internal_reflect_Reflection = SYMBOLS.putType("Ljdk/internal/reflect/Reflection;");

        // MagicAccessorImpl is not public.
        public static final Symbol<Type> sun_reflect_MagicAccessorImpl = SYMBOLS.putType("Lsun/reflect/MagicAccessorImpl;");
        public static final Symbol<Type> jdk_internal_reflect_MagicAccessorImpl = SYMBOLS.putType("Ljdk/internal/reflect/MagicAccessorImpl;");
        public static final Symbol<Type> jdk_internal_reflect_SerializationConstructorAccessorImpl = SYMBOLS.putType("Ljdk/internal/reflect/SerializationConstructorAccessorImpl;");
        // DelegatingClassLoader is not public.
        public static final Symbol<Type> sun_reflect_DelegatingClassLoader = SYMBOLS.putType("Lsun/reflect/DelegatingClassLoader;");
        public static final Symbol<Type> jdk_internal_reflect_DelegatingClassLoader = SYMBOLS.putType("Ljdk/internal/reflect/DelegatingClassLoader;");

        // MethodAccessorImpl is not public.
        public static final Symbol<Type> sun_reflect_MethodAccessorImpl = SYMBOLS.putType("Lsun/reflect/MethodAccessorImpl;");
        public static final Symbol<Type> jdk_internal_reflect_MethodAccessorImpl = SYMBOLS.putType("Ljdk/internal/reflect/MethodAccessorImpl;");
        public static final Symbol<Type> sun_reflect_ConstructorAccessorImpl = SYMBOLS.putType("Lsun/reflect/ConstructorAccessorImpl;");
        public static final Symbol<Type> jdk_internal_reflect_ConstructorAccessorImpl = SYMBOLS.putType("Ljdk/internal/reflect/ConstructorAccessorImpl;");
        public static final Symbol<Type> jdk_internal_reflect_NativeConstructorAccessorImpl = SYMBOLS.putType("Ljdk/internal/reflect/NativeConstructorAccessorImpl;");

        public static final Symbol<Type> sun_reflect_ConstantPool = SYMBOLS.putType("Lsun/reflect/ConstantPool;");
        public static final Symbol<Type> jdk_internal_reflect_ConstantPool = SYMBOLS.putType("Ljdk/internal/reflect/ConstantPool;");

        public static final Symbol<Type> java_io_Serializable = SYMBOLS.putType("Ljava/io/Serializable;");
        public static final Symbol<Type> java_nio_ByteBuffer = SYMBOLS.putType("Ljava/nio/ByteBuffer;");
        public static final Symbol<Type> java_nio_DirectByteBuffer = SYMBOLS.putType("Ljava/nio/DirectByteBuffer;");
        public static final Symbol<Type> java_nio_ByteOrder = SYMBOLS.putType("Ljava/nio/ByteOrder;");

        public static final Symbol<Type> java_security_PrivilegedActionException = SYMBOLS.putType("Ljava/security/PrivilegedActionException;");

        // Shutdown is not public.
        public static final Symbol<Type> java_lang_Shutdown = SYMBOLS.putType("Ljava/lang/Shutdown;");

        public static final Symbol<Type> sun_launcher_LauncherHelper = SYMBOLS.putType("Lsun/launcher/LauncherHelper;");

        // Finalizer is not public.
        public static final Symbol<Type> java_lang_ref_Finalizer = SYMBOLS.putType("Ljava/lang/ref/Finalizer;");
        public static final Symbol<Type> java_lang_ref_Reference = SYMBOLS.putType("Ljava/lang/ref/Reference;");
        public static final Symbol<Type> java_lang_ref_FinalReference = SYMBOLS.putType("Ljava/lang/ref/FinalReference;");
        public static final Symbol<Type> java_lang_ref_WeakReference = SYMBOLS.putType("Ljava/lang/ref/WeakReference;");
        public static final Symbol<Type> java_lang_ref_SoftReference = SYMBOLS.putType("Ljava/lang/ref/SoftReference;");
        public static final Symbol<Type> java_lang_ref_PhantomReference = SYMBOLS.putType("Ljava/lang/ref/PhantomReference;");
        public static final Symbol<Type> java_lang_ref_ReferenceQueue = SYMBOLS.putType("Ljava/lang/ref/ReferenceQueue;");
        public static final Symbol<Type> java_lang_ref_Reference$Lock = SYMBOLS.putType("Ljava/lang/ref/Reference$Lock;");

        public static final Symbol<Type> sun_misc_Cleaner = SYMBOLS.putType("Lsun/misc/Cleaner;");

        public static final Symbol<Type> java_lang_StackTraceElement = SYMBOLS.putType("Ljava/lang/StackTraceElement;");
        public static final Symbol<Type> java_lang_StackTraceElement_array = SYMBOLS.putType("[Ljava/lang/StackTraceElement;");

        public static final Symbol<Type> java_lang_Error = SYMBOLS.putType("Ljava/lang/Error;");
        public static final Symbol<Type> java_lang_NoSuchFieldError = SYMBOLS.putType("Ljava/lang/NoSuchFieldError;");
        public static final Symbol<Type> java_lang_NoSuchMethodError = SYMBOLS.putType("Ljava/lang/NoSuchMethodError;");
        public static final Symbol<Type> java_lang_IllegalAccessError = SYMBOLS.putType("Ljava/lang/IllegalAccessError;");
        public static final Symbol<Type> java_lang_IncompatibleClassChangeError = SYMBOLS.putType("Ljava/lang/IncompatibleClassChangeError;");
        public static final Symbol<Type> java_lang_AbstractMethodError = SYMBOLS.putType("Ljava/lang/AbstractMethodError;");
        public static final Symbol<Type> java_lang_InternalError = SYMBOLS.putType("Ljava/lang/InternalError;");
        public static final Symbol<Type> java_lang_VerifyError = SYMBOLS.putType("Ljava/lang/VerifyError;");
        public static final Symbol<Type> java_lang_ClassFormatError = SYMBOLS.putType("Ljava/lang/ClassFormatError;");
        public static final Symbol<Type> java_lang_ClassCircularityError = SYMBOLS.putType("Ljava/lang/ClassCircularityError;");
        public static final Symbol<Type> java_lang_UnsatisfiedLinkError = SYMBOLS.putType("Ljava/lang/UnsatisfiedLinkError;");

        public static final Symbol<Type> java_lang_invoke_MethodType = SYMBOLS.putType("Ljava/lang/invoke/MethodType;");

        public static final Symbol<Type> java_lang_AssertionStatusDirectives = SYMBOLS.putType("Ljava/lang/AssertionStatusDirectives;");

        public static final Symbol<Type> java_lang_invoke_MethodHandles = SYMBOLS.putType("Ljava/lang/invoke/MethodHandles;");
        public static final Symbol<Type> java_lang_invoke_VarHandle = SYMBOLS.putType("Ljava/lang/invoke/VarHandle;");
        public static final Symbol<Type> java_lang_invoke_VarHandles = SYMBOLS.putType("Ljava/lang/invoke/VarHandles;");
        public static final Symbol<Type> java_lang_invoke_MethodHandles$Lookup = SYMBOLS.putType("Ljava/lang/invoke/MethodHandles$Lookup;");
        public static final Symbol<Type> java_lang_invoke_CallSite = SYMBOLS.putType("Ljava/lang/invoke/CallSite;");
        public static final Symbol<Type> java_lang_invoke_DirectMethodHandle = SYMBOLS.putType("Ljava/lang/invoke/DirectMethodHandle;");
        public static final Symbol<Type> java_lang_invoke_LambdaMetafactory = SYMBOLS.putType("Ljava/lang/invoke/LambdaMetafactory;");

        // MethodHandleNatives is not public.
        public static final Symbol<Type> java_lang_invoke_MethodHandleNatives = SYMBOLS.putType("Ljava/lang/invoke/MethodHandleNatives;");
        public static final Symbol<Type> java_lang_invoke_MemberName = SYMBOLS.putType("Ljava/lang/invoke/MemberName;");
        public static final Symbol<Type> java_lang_invoke_MethodHandle = SYMBOLS.putType("Ljava/lang/invoke/MethodHandle;");
        public static final Symbol<Type> java_lang_invoke_LambdaForm = SYMBOLS.putType("Ljava/lang/invoke/LambdaForm;");
        public static final Symbol<Type> java_lang_invoke_LambdaForm$Compiled = SYMBOLS.putType("Ljava/lang/invoke/LambdaForm$Compiled;");
        public static final Symbol<Type> java_lang_invoke_LambdaForm$Hidden = SYMBOLS.putType("Ljava/lang/invoke/LambdaForm$Hidden;");
        public static final Symbol<Type> jdk_internal_vm_annotation_Hidden = SYMBOLS.putType("Ljdk/internal/vm/annotation/Hidden;");
        public static final Symbol<Type> jdk_internal_vm_annotation_Stable = SYMBOLS.putType("Ljdk/internal/vm/annotation/Stable;");
        public static final Symbol<Type> sun_reflect_CallerSensitive = SYMBOLS.putType("Lsun/reflect/CallerSensitive;");
        public static final Symbol<Type> jdk_internal_reflect_CallerSensitive = SYMBOLS.putType("Ljdk/internal/reflect/CallerSensitive;");
        public static final Symbol<Type> java_lang_invoke_ForceInline = SYMBOLS.putType("Ljava/lang/invoke/ForceInline;");
        public static final Symbol<Type> jdk_internal_vm_annotation_ForceInline = SYMBOLS.putType("Ljdk/internal/vm/annotation/ForceInline;");
        public static final Symbol<Type> java_lang_invoke_DontInline = SYMBOLS.putType("Ljava/lang/invoke/DontInline;");
        public static final Symbol<Type> jdk_internal_vm_annotation_DontInline = SYMBOLS.putType("Ljdk/internal/vm/annotation/DontInline;");

        // ScopedMemoryAccess
        public static final Symbol<Type> jdk_internal_misc_ScopedMemoryAccess$Scoped = SYMBOLS.putType("Ljdk/internal/misc/ScopedMemoryAccess$Scoped;");

        // Modules
        public static final Symbol<Type> java_lang_Module = SYMBOLS.putType("Ljava/lang/Module;");

        // Record
        public static final Symbol<Type> java_lang_Record = SYMBOLS.putType("Ljava/lang/Record;");
        public static final Symbol<Type> java_lang_reflect_RecordComponent = SYMBOLS.putType("Ljava/lang/reflect/RecordComponent;");

        // Unsafe Constants (required for 13+)
        public static final Symbol<Type> jdk_internal_misc_UnsafeConstants = SYMBOLS.putType("Ljdk/internal/misc/UnsafeConstants;");

        // Stack walking API
        public static final Symbol<Type> java_lang_StackWalker = SYMBOLS.putType("Ljava/lang/StackWalker;");
        public static final Symbol<Type> java_lang_StackStreamFactory = SYMBOLS.putType("Ljava/lang/StackStreamFactory;");
        public static final Symbol<Type> java_lang_StackStreamFactory_AbstractStackWalker = SYMBOLS.putType("Ljava/lang/StackStreamFactory$AbstractStackWalker;");
        public static final Symbol<Type> java_lang_StackFrameInfo = SYMBOLS.putType("Ljava/lang/StackFrameInfo;");
        public static final Symbol<Type> java_lang_ClassFrameInfo = SYMBOLS.putType("Ljava/lang/ClassFrameInfo;");
        public static final Symbol<Type> java_lang_invoke_ResolvedMethodName = SYMBOLS.putType("Ljava/lang/invoke/ResolvedMethodName;");

        // Special threads
        public static final Symbol<Type> java_lang_ref_Finalizer$FinalizerThread = SYMBOLS.putType("Ljava/lang/ref/Finalizer$FinalizerThread;");
        public static final Symbol<Type> java_lang_ref_Reference$ReferenceHandler = SYMBOLS.putType("Ljava/lang/ref/Reference$ReferenceHandler;");
        public static final Symbol<Type> jdk_internal_misc_InnocuousThread = SYMBOLS.putType("Ljdk/internal/misc/InnocuousThread;");
        public static final Symbol<Type> sun_misc_InnocuousThread = SYMBOLS.putType("Lsun/misc/InnocuousThread;");
        // java.management
        public static final Symbol<Type> java_lang_management_MemoryManagerMXBean = SYMBOLS.putType("Ljava/lang/management/MemoryManagerMXBean;");
        public static final Symbol<Type> java_lang_management_MemoryPoolMXBean = SYMBOLS.putType("Ljava/lang/management/MemoryPoolMXBean;");
        public static final Symbol<Type> java_lang_management_GarbageCollectorMXBean = SYMBOLS.putType("Ljava/lang/management/GarbageCollectorMXBean;");
        public static final Symbol<Type> sun_management_ManagementFactory = SYMBOLS.putType("Lsun/management/ManagementFactory;");
        public static final Symbol<Type> sun_management_ManagementFactoryHelper = SYMBOLS.putType("Lsun/management/ManagementFactoryHelper;");
        public static final Symbol<Type> java_lang_management_MemoryUsage = SYMBOLS.putType("Ljava/lang/management/MemoryUsage;");
        public static final Symbol<Type> java_lang_management_ThreadInfo = SYMBOLS.putType("Ljava/lang/management/ThreadInfo;");

        // Secrets
        public static final Symbol<Type> sun_misc_SharedSecrets = SYMBOLS.putType("Lsun/misc/SharedSecrets;");
        public static final Symbol<Type> jdk_internal_misc_SharedSecrets = SYMBOLS.putType("Ljdk/internal/misc/SharedSecrets;");
        public static final Symbol<Type> jdk_internal_access_SharedSecrets = SYMBOLS.putType("Ljdk/internal/access/SharedSecrets;");
        public static final Symbol<Type> sun_misc_JavaLangAccess = SYMBOLS.putType("Lsun/misc/JavaLangAccess;");
        public static final Symbol<Type> jdk_internal_misc_JavaLangAccess = SYMBOLS.putType("Ljdk/internal/misc/JavaLangAccess;");
        public static final Symbol<Type> jdk_internal_access_JavaLangAccess = SYMBOLS.putType("Ljdk/internal/access/JavaLangAccess;");

        // Interop conversions.
        public static final Symbol<Type> java_time_Duration = SYMBOLS.putType("Ljava/time/Duration;");
        public static final Symbol<Type> java_time_LocalTime = SYMBOLS.putType("Ljava/time/LocalTime;");
        public static final Symbol<Type> java_time_LocalDateTime = SYMBOLS.putType("Ljava/time/LocalDateTime;");
        public static final Symbol<Type> java_time_LocalDate = SYMBOLS.putType("Ljava/time/LocalDate;");
        public static final Symbol<Type> java_time_Instant = SYMBOLS.putType("Ljava/time/Instant;");
        public static final Symbol<Type> java_time_ZonedDateTime = SYMBOLS.putType("Ljava/time/ZonedDateTime;");
        public static final Symbol<Type> java_util_Date = SYMBOLS.putType("Ljava/util/Date;");
        public static final Symbol<Type> java_time_ZoneId = SYMBOLS.putType("Ljava/time/ZoneId;");

        // List / Map / Iterator
        public static final Symbol<Type> java_lang_Iterable = SYMBOLS.putType("Ljava/lang/Iterable;");
        public static final Symbol<Type> java_util_List = SYMBOLS.putType("Ljava/util/List;");
        public static final Symbol<Type> java_util_Map = SYMBOLS.putType("Ljava/util/Map;");
        public static final Symbol<Type> java_util_HashMap = SYMBOLS.putType("Ljava/util/HashMap;");
        public static final Symbol<Type> java_util_Iterator = SYMBOLS.putType("Ljava/util/Iterator;");
        public static final Symbol<Type> java_util_NoSuchElementException = SYMBOLS.putType("Ljava/util/NoSuchElementException;");
        public static final Symbol<Type> java_util_Map_Entry = SYMBOLS.putType("Ljava/util/Map$Entry;");
        public static final Symbol<Type> java_util_Set = SYMBOLS.putType("Ljava/util/Set;");
        public static final Symbol<Type> java_util_Collection = SYMBOLS.putType("Ljava/util/Collection;");

        // Optional
        public static final Symbol<Type> java_util_Optional = SYMBOLS.putType("Ljava/util/Optional;");

        // java.util.regex
        public static final Symbol<Type> java_util_regex_Pattern = SYMBOLS.putType("Ljava/util/regex/Pattern;");
        public static final Symbol<Type> java_util_regex_Pattern_Node = SYMBOLS.putType("Ljava/util/regex/Pattern$Node;");
        public static final Symbol<Type> java_util_regex_Matcher = SYMBOLS.putType("Ljava/util/regex/Matcher;");
        public static final Symbol<Type> java_util_regex_IntHashSet = SYMBOLS.putType("Ljava/util/regex/IntHashSet;");
        public static final Symbol<Type> java_util_regex_IntHashSet_array = SYMBOLS.putType("[Ljava/util/regex/IntHashSet;");

        public static final Symbol<Type> java_util_concurrent_locks_AbstractOwnableSynchronizer = SYMBOLS.putType("Ljava/util/concurrent/locks/AbstractOwnableSynchronizer;");
        public static final Symbol<Type> java_util_concurrent_locks_ReentrantLock_Sync = SYMBOLS.putType("Ljava/util/concurrent/locks/ReentrantLock$Sync;");
        public static final Symbol<Type> java_util_concurrent_locks_ReentrantReadWriteLock_Sync = SYMBOLS.putType("Ljava/util/concurrent/locks/ReentrantReadWriteLock$Sync;");

        // java math
        public static final Symbol<Type> java_math_BigInteger = SYMBOLS.putType("Ljava/math/BigInteger;");
        public static final Symbol<Type> java_math_BigDecimal = SYMBOLS.putType("Ljava/math/BigDecimal;");
        public static final Symbol<Type> java_math_MathContext = SYMBOLS.putType("Ljava/math/MathContext;");
        public static final Symbol<Type> java_math_RoundingMode = SYMBOLS.putType("Ljava/math/RoundingMode;");

        // Polyglot/interop API.
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_Polyglot = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/Polyglot;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_ArityException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/ArityException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_UnknownIdentifierException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_UnsupportedMessageException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_UnsupportedTypeException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/InvalidArrayIndexException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_InvalidBufferOffsetException = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_StopIterationException = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/StopIterationException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_UnknownKeyException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/UnknownKeyException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_ForeignException = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/ForeignException;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_ExceptionType = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/ExceptionType;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_VMHelper = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/VMHelper;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_TypeLiteral = SYMBOLS.putType("Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_TypeLiteral$InternalTypeLiteral = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral$InternalTypeLiteral;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterable = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignIterable;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignList = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignList;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignCollection = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignCollection;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterator = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignIterator;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignMap = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignMap;");
        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_collections_EspressoForeignSet = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/collections/EspressoForeignSet;");

        public static final Symbol<Type> com_oracle_truffle_espresso_polyglot_impl_EspressoForeignNumber = SYMBOLS.putType(
                        "Lcom/oracle/truffle/espresso/polyglot/impl/EspressoForeignNumber;");

        // Continuations
        public static final Symbol<Type> org_graalvm_continuations_ContinuationImpl = SYMBOLS.putType(
                        "Lorg/graalvm/continuations/ContinuationImpl;");
        public static final Symbol<Type> org_graalvm_continuations_ContinuationImpl_FrameRecord = SYMBOLS.putType(
                        "Lorg/graalvm/continuations/ContinuationImpl$FrameRecord;");
        public static final Symbol<Type> org_graalvm_continuations_IllegalMaterializedRecordException = SYMBOLS.putType(
                        "Lorg/graalvm/continuations/IllegalMaterializedRecordException;");
        public static final Symbol<Type> org_graalvm_continuations_IllegalContinuationStateException = SYMBOLS.putType(
                        "Lorg/graalvm/continuations/IllegalContinuationStateException;");

        // JVMCI
        public static final Symbol<Type> jdk_vm_ci_runtime_JVMCIRuntime = SYMBOLS.putType("Ljdk/vm/ci/runtime/JVMCIRuntime;");
        public static final Symbol<Type> jdk_vm_ci_services_Services = SYMBOLS.putType("Ljdk/vm/ci/services/Services;");
        public static final Symbol<Type> jdk_vm_ci_meta_UnresolvedJavaType = SYMBOLS.putType("Ljdk/vm/ci/meta/UnresolvedJavaType;");
        public static final Symbol<Type> jdk_vm_ci_meta_UnresolvedJavaField = SYMBOLS.putType("Ljdk/vm/ci/meta/UnresolvedJavaField;");
        public static final Symbol<Type> jdk_vm_ci_meta_LineNumberTable = SYMBOLS.putType("Ljdk/vm/ci/meta/LineNumberTable;");
        public static final Symbol<Type> jdk_vm_ci_meta_LocalVariableTable = SYMBOLS.putType("Ljdk/vm/ci/meta/LocalVariableTable;");
        public static final Symbol<Type> jdk_vm_ci_meta_Local = SYMBOLS.putType("Ljdk/vm/ci/meta/Local;");
        public static final Symbol<Type> jdk_vm_ci_meta_Local_array = SYMBOLS.putType("[Ljdk/vm/ci/meta/Local;");
        public static final Symbol<Type> jdk_vm_ci_meta_JavaType = SYMBOLS.putType("Ljdk/vm/ci/meta/JavaType;");
        public static final Symbol<Type> jdk_vm_ci_meta_ExceptionHandler = SYMBOLS.putType("Ljdk/vm/ci/meta/ExceptionHandler;");
        public static final Symbol<Type> jdk_vm_ci_meta_JavaConstant = SYMBOLS.putType("Ljdk/vm/ci/meta/JavaConstant;");
        public static final Symbol<Type> jdk_vm_ci_meta_JavaConstant_array = SYMBOLS.putType("[Ljdk/vm/ci/meta/JavaConstant;");
        public static final Symbol<Type> jdk_vm_ci_meta_PrimitiveConstant = SYMBOLS.putType("Ljdk/vm/ci/meta/PrimitiveConstant;");
        public static final Symbol<Type> jdk_vm_ci_meta_MethodHandleAccessProvider$IntrinsicMethod = SYMBOLS.putType("Ljdk/vm/ci/meta/MethodHandleAccessProvider$IntrinsicMethod;");
        // @formatter:off
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_EspressoJVMCIRuntime = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/EspressoJVMCIRuntime;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaField = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedArrayType = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedArrayType;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedPrimitiveType = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedPrimitiveType;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaType = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaType;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoConstantPool = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoConstantPool;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoObjectConstant = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;");
        public static final Symbol<Type> com_oracle_truffle_espresso_jvmci_meta_EspressoBootstrapMethodInvocation = SYMBOLS.putType("Lcom/oracle/truffle/espresso/jvmci/meta/EspressoBootstrapMethodInvocation;");
        // @formatter:on

        public static final Symbol<Type> jdk_graal_compiler_espresso_DummyEspressoGraalJVMCICompiler = SYMBOLS.putType("Lcom/oracle/truffle/espresso/graal/DummyEspressoGraalJVMCICompiler;");
        public static final Symbol<Type> jdk_graal_compiler_api_runtime_GraalJVMCICompiler = SYMBOLS.putType("Ljdk/graal/compiler/api/runtime/GraalJVMCICompiler;");

        // Panama
        public static final Symbol<Type> jdk_internal_foreign_abi_VMStorage = SYMBOLS.putType("Ljdk/internal/foreign/abi/VMStorage;");
        public static final Symbol<Type> jdk_internal_foreign_abi_NativeEntryPoint = SYMBOLS.putType("Ljdk/internal/foreign/abi/NativeEntryPoint;");
        public static final Symbol<Type> jdk_internal_foreign_abi_UpcallLinker_CallRegs = SYMBOLS.putType("Ljdk/internal/foreign/abi/UpcallLinker$CallRegs;");
        public static final Symbol<Type> jdk_internal_foreign_abi_VMStorage_array = SYMBOLS.putType("[Ljdk/internal/foreign/abi/VMStorage;");
    }

    /**
     * Contains commonly used (name) symbols.
     *
     * Symbols declared here must match exactly the field name; notable exceptions include
     * {@link Names#_init_}, {@link Names#_clinit_} and hidden field names.
     */
    public static class Names {

        // java.base
        public static final Symbol<Name> java_base = SYMBOLS.putName("java.base");
        // general
        public static final Symbol<Name> _init_ = SYMBOLS.putName("<init>");
        public static final Symbol<Name> _clinit_ = SYMBOLS.putName("<clinit>");
        // Boxing and String
        public static final Symbol<Name> value = SYMBOLS.putName("value");
        public static final Symbol<Name> valueOf = SYMBOLS.putName("valueOf");
        public static final Symbol<Name> booleanValue = SYMBOLS.putName("booleanValue");
        public static final Symbol<Name> byteValue = SYMBOLS.putName("byteValue");
        public static final Symbol<Name> shortValue = SYMBOLS.putName("shortValue");
        public static final Symbol<Name> intValue = SYMBOLS.putName("intValue");
        public static final Symbol<Name> longValue = SYMBOLS.putName("longValue");
        public static final Symbol<Name> floatValue = SYMBOLS.putName("floatValue");
        public static final Symbol<Name> doubleValue = SYMBOLS.putName("doubleValue");
        // Field, Thread, Module and MemberName
        public static final Symbol<Name> name = SYMBOLS.putName("name");
        // Thread and Runnable
        public static final Symbol<Name> run = SYMBOLS.putName("run");
        // Thread and System
        public static final Symbol<Name> exit = SYMBOLS.putName("exit");
        // Thread
        public static final Symbol<Name> getThreadGroup = SYMBOLS.putName("getThreadGroup");
        // Object and arrays
        public static final Symbol<Name> clone = SYMBOLS.putName("clone");
        public static final Symbol<Name> toString = SYMBOLS.putName("toString");
        // variable 'this' name
        public static final Symbol<Name> thiz = SYMBOLS.putName("this");
        // finding main
        public static final Symbol<Name> checkAndLoadMain = SYMBOLS.putName("checkAndLoadMain");
        // java agents premain
        public static final Symbol<Name> loadClassAndCallPremain = SYMBOLS.putName("loadClassAndCallPremain");
        public static final Symbol<Name> transform = SYMBOLS.putName("transform");
        public static final Symbol<Name> getDefinitionClass = SYMBOLS.putName("getDefinitionClass");
        public static final Symbol<Name> getDefinitionClassFile = SYMBOLS.putName("getDefinitionClassFile");
        public static final Symbol<Name> appendToClassPathForInstrumentation = SYMBOLS.putName("appendToClassPathForInstrumentation");

        public static final Symbol<Name> main = SYMBOLS.putName("main");
        // Reflection
        public static final Symbol<Name> jdk_internal_reflect = SYMBOLS.putName("jdk/internal/reflect");
        public static final Symbol<Name> sun_reflect = SYMBOLS.putName("sun/reflect");
        public static final Symbol<Name> clazz = SYMBOLS.putName("clazz");
        public static final Symbol<Name> getParameterTypes = SYMBOLS.putName("getParameterTypes");
        public static final Symbol<Name> override = SYMBOLS.putName("override");
        public static final Symbol<Name> parameterTypes = SYMBOLS.putName("parameterTypes");
        public static final Symbol<Name> root = SYMBOLS.putName("root");
        public static final Symbol<Name> signature = SYMBOLS.putName("signature");
        public static final Symbol<Name> slot = SYMBOLS.putName("slot");
        public static final Symbol<Name> type = SYMBOLS.putName("type");
        public static final Symbol<Name> getRawType = SYMBOLS.putName("getRawType");
        public static final Symbol<Name> getActualTypeArguments = SYMBOLS.putName("getActualTypeArguments");
        // java.lang.*
        // j.l.AssertionStatusDirectives
        public static final Symbol<Name> classes = SYMBOLS.putName("classes");
        public static final Symbol<Name> classEnabled = SYMBOLS.putName("classEnabled");
        public static final Symbol<Name> deflt = SYMBOLS.putName("deflt");
        public static final Symbol<Name> packages = SYMBOLS.putName("packages");
        public static final Symbol<Name> packageEnabled = SYMBOLS.putName("packageEnabled");
        // j.l.Class
        public static final Symbol<Name> checkPackageAccess = SYMBOLS.putName("checkPackageAccess");
        public static final Symbol<Name> getName = SYMBOLS.putName("getName");
        public static final Symbol<Name> getSimpleName = SYMBOLS.putName("getSimpleName");
        public static final Symbol<Name> getTypeName = SYMBOLS.putName("getTypeName");
        public static final Symbol<Name> forName = SYMBOLS.putName("forName");
        public static final Symbol<Name> module = SYMBOLS.putName("module");
        public static final Symbol<Name> classData = SYMBOLS.putName("classData");
        public static final Symbol<Name> classLoader = SYMBOLS.putName("classLoader");
        public static final Symbol<Name> classRedefinedCount = SYMBOLS.putName("classRedefinedCount");
        public static final Symbol<Name> componentType = SYMBOLS.putName("componentType");
        public static final Symbol<Name> protectionDomain = SYMBOLS.putName("protectionDomain");
        public static final Symbol<Name> modifiers = SYMBOLS.putName("modifiers");
        public static final Symbol<Name> classFileAccessFlags = SYMBOLS.putName("classFileAccessFlags");
        public static final Symbol<Name> primitive = SYMBOLS.putName("primitive");
        public static final Symbol<Name> signers = SYMBOLS.putName("signers");
        // j.l.ClassLoader
        public static final Symbol<Name> addClass = SYMBOLS.putName("addClass");
        public static final Symbol<Name> findNative = SYMBOLS.putName("findNative");
        public static final Symbol<Name> getSystemClassLoader = SYMBOLS.putName("getSystemClassLoader");
        public static final Symbol<Name> loadClass = SYMBOLS.putName("loadClass");
        public static final Symbol<Name> getResourceAsStream = SYMBOLS.putName("getResourceAsStream");
        public static final Symbol<Name> parent = SYMBOLS.putName("parent");
        public static final Symbol<Name> unnamedModule = SYMBOLS.putName("unnamedModule");
        public static final Symbol<Name> nameAndId = SYMBOLS.putName("nameAndId");
        public static final Symbol<Name> resetArchivedStates = SYMBOLS.putName("resetArchivedStates");
        public static final Symbol<Name> HIDDEN_CLASS_LOADER_REGISTRY = SYMBOLS.putName("0HIDDEN_CLASS_LOADER_REGISTRY");
        // j.l.Module
        public static final Symbol<Name> loader = SYMBOLS.putName("loader");
        // j.l.RecordComponent
        public static final Symbol<Name> accessor = SYMBOLS.putName("accessor");
        public static final Symbol<Name> annotations = SYMBOLS.putName("annotations");
        public static final Symbol<Name> typeAnnotations = SYMBOLS.putName("typeAnnotations");
        // j.l.String
        public static final Symbol<Name> hash = SYMBOLS.putName("hash");
        public static final Symbol<Name> hashCode = SYMBOLS.putName("hashCode");
        public static final Symbol<Name> length = SYMBOLS.putName("length");
        public static final Symbol<Name> toCharArray = SYMBOLS.putName("toCharArray");
        public static final Symbol<Name> charAt = SYMBOLS.putName("charAt");
        public static final Symbol<Name> coder = SYMBOLS.putName("coder");
        public static final Symbol<Name> COMPACT_STRINGS = SYMBOLS.putName("COMPACT_STRINGS");
        public static final Symbol<Name> indexOf = SYMBOLS.putName("indexOf");
        // j.l.Throwable
        public static final Symbol<Name> backtrace = SYMBOLS.putName("backtrace");
        public static final Symbol<Name> stackTrace = SYMBOLS.putName("stackTrace");
        public static final Symbol<Name> cause = SYMBOLS.putName("cause");
        public static final Symbol<Name> depth = SYMBOLS.putName("depth");
        public static final Symbol<Name> fillInStackTrace = SYMBOLS.putName("fillInStackTrace");
        public static final Symbol<Name> fillInStackTrace0 = SYMBOLS.putName("fillInStackTrace0");
        public static final Symbol<Name> getMessage = SYMBOLS.putName("getMessage");
        public static final Symbol<Name> getCause = SYMBOLS.putName("getCause");
        public static final Symbol<Name> initCause = SYMBOLS.putName("initCause");
        public static final Symbol<Name> detailMessage = SYMBOLS.putName("detailMessage");
        public static final Symbol<Name> printStackTrace = SYMBOLS.putName("printStackTrace");
        public static final Symbol<Name> extendedMessageState = SYMBOLS.putName("extendedMessageState");
        // j.l.Thread
        public static final Symbol<Name> add = SYMBOLS.putName("add");
        public static final Symbol<Name> checkAccess = SYMBOLS.putName("checkAccess");
        public static final Symbol<Name> daemon = SYMBOLS.putName("daemon");
        public static final Symbol<Name> dispatchUncaughtException = SYMBOLS.putName("dispatchUncaughtException");
        public static final Symbol<Name> getStackTrace = SYMBOLS.putName("getStackTrace");
        public static final Symbol<Name> group = SYMBOLS.putName("group");
        public static final Symbol<Name> holder = SYMBOLS.putName("holder");
        public static final Symbol<Name> VTHREAD_GROUP = SYMBOLS.putName("VTHREAD_GROUP");
        public static final Symbol<Name> inheritedAccessControlContext = SYMBOLS.putName("inheritedAccessControlContext");
        public static final Symbol<Name> maxPriority = SYMBOLS.putName("maxPriority");
        public static final Symbol<Name> priority = SYMBOLS.putName("priority");
        public static final Symbol<Name> remove = SYMBOLS.putName("remove");
        public static final Symbol<Name> stop = SYMBOLS.putName("stop");
        public static final Symbol<Name> threadStatus = SYMBOLS.putName("threadStatus");
        public static final Symbol<Name> toThreadState = SYMBOLS.putName("toThreadState");
        public static final Symbol<Name> contextClassLoader = SYMBOLS.putName("contextClassLoader");
        public static final Symbol<Name> EMPTY = SYMBOLS.putName("EMPTY");
        public static final Symbol<Name> precision = SYMBOLS.putName("precision");
        // j.l.StackTraceElement
        public static final Symbol<Name> declaringClassObject = SYMBOLS.putName("declaringClassObject");
        public static final Symbol<Name> classLoaderName = SYMBOLS.putName("classLoaderName");
        public static final Symbol<Name> moduleName = SYMBOLS.putName("moduleName");
        public static final Symbol<Name> moduleVersion = SYMBOLS.putName("moduleVersion");
        public static final Symbol<Name> declaringClass = SYMBOLS.putName("declaringClass");
        public static final Symbol<Name> methodName = SYMBOLS.putName("methodName");
        public static final Symbol<Name> fileName = SYMBOLS.putName("fileName");
        public static final Symbol<Name> lineNumber = SYMBOLS.putName("lineNumber");
        // j.l.System
        public static final Symbol<Name> err = SYMBOLS.putName("err");
        public static final Symbol<Name> getProperty = SYMBOLS.putName("getProperty");
        public static final Symbol<Name> in = SYMBOLS.putName("in");
        public static final Symbol<Name> initializeSystemClass = SYMBOLS.putName("initializeSystemClass");
        public static final Symbol<Name> initPhase1 = SYMBOLS.putName("initPhase1");
        public static final Symbol<Name> initPhase2 = SYMBOLS.putName("initPhase2");
        public static final Symbol<Name> initPhase3 = SYMBOLS.putName("initPhase3");
        public static final Symbol<Name> out = SYMBOLS.putName("out");
        public static final Symbol<Name> security = SYMBOLS.putName("security");
        public static final Symbol<Name> setProperty = SYMBOLS.putName("setProperty");
        // j.l.Shutdown
        public static final Symbol<Name> shutdown = SYMBOLS.putName("shutdown");
        // j.l.StackStreamFactory
        public static final Symbol<Name> doStackWalk = SYMBOLS.putName("doStackWalk");
        public static final Symbol<Name> callStackWalk = SYMBOLS.putName("callStackWalk");
        // j.l.StackFrameInfo
        public static final Symbol<Name> classOrMemberName = SYMBOLS.putName("classOrMemberName");
        public static final Symbol<Name> memberName = SYMBOLS.putName("memberName");
        public static final Symbol<Name> bci = SYMBOLS.putName("bci");
        // io
        // java.nio.ByteBuffer
        public static final Symbol<Name> wrap = SYMBOLS.putName("wrap");
        public static final Symbol<Name> order = SYMBOLS.putName("order");
        public static final Symbol<Name> isReadOnly = SYMBOLS.putName("isReadOnly");
        public static final Symbol<Name> getShort = SYMBOLS.putName("getShort");
        public static final Symbol<Name> getInt = SYMBOLS.putName("getInt");
        public static final Symbol<Name> getLong = SYMBOLS.putName("getLong");
        public static final Symbol<Name> getFloat = SYMBOLS.putName("getFloat");
        public static final Symbol<Name> getDouble = SYMBOLS.putName("getDouble");
        public static final Symbol<Name> putShort = SYMBOLS.putName("putShort");
        public static final Symbol<Name> putInt = SYMBOLS.putName("putInt");
        public static final Symbol<Name> putLong = SYMBOLS.putName("putLong");
        public static final Symbol<Name> putFloat = SYMBOLS.putName("putFloat");
        public static final Symbol<Name> putDouble = SYMBOLS.putName("putDouble");
        // java.nio.ByteOrder
        public static final Symbol<Name> LITTLE_ENDIAN = SYMBOLS.putName("LITTLE_ENDIAN");
        // java.nio.Buffer
        public static final Symbol<Name> address = SYMBOLS.putName("address");
        public static final Symbol<Name> capacity = SYMBOLS.putName("capacity");
        public static final Symbol<Name> limit = SYMBOLS.putName("limit");
        public static final Symbol<Name> wait = SYMBOLS.putName("wait");
        // java.io.InputStream
        public static final Symbol<Name> available = SYMBOLS.putName("available");
        public static final Symbol<Name> read = SYMBOLS.putName("read");
        public static final Symbol<Name> close = SYMBOLS.putName("close");
        public static final Symbol<Name> skip = SYMBOLS.putName("skip");
        // java.io.RandomAccessFile
        public static final Symbol<Name> O_RDONLY = SYMBOLS.putName("O_RDONLY");
        public static final Symbol<Name> O_RDWR = SYMBOLS.putName("O_RDWR");
        public static final Symbol<Name> O_SYNC = SYMBOLS.putName("O_SYNC");
        public static final Symbol<Name> O_DSYNC = SYMBOLS.putName("O_DSYNC");
        public static final Symbol<Name> O_TEMPORARY = SYMBOLS.putName("O_TEMPORARY");
        // java.io.File
        public static final Symbol<Name> BA_EXISTS = SYMBOLS.putName("BA_EXISTS");
        public static final Symbol<Name> BA_REGULAR = SYMBOLS.putName("BA_REGULAR");
        public static final Symbol<Name> BA_DIRECTORY = SYMBOLS.putName("BA_DIRECTORY");
        public static final Symbol<Name> BA_HIDDEN = SYMBOLS.putName("BA_HIDDEN");
        public static final Symbol<Name> ACCESS_READ = SYMBOLS.putName("ACCESS_READ");
        public static final Symbol<Name> ACCESS_WRITE = SYMBOLS.putName("ACCESS_WRITE");
        public static final Symbol<Name> ACCESS_EXECUTE = SYMBOLS.putName("ACCESS_EXECUTE");
        // java.io.FileDescriptor
        public static final Symbol<Name> fd = SYMBOLS.putName("fd");
        public static final Symbol<Name> append = SYMBOLS.putName("append");
        // java.io.PrintStream
        public static final Symbol<Name> println = SYMBOLS.putName("println");
        //
        public static final Symbol<Name> jniVersion = SYMBOLS.putName("jniVersion");
        public static final Symbol<Name> path = SYMBOLS.putName("path");
        public static final Symbol<Name> open = SYMBOLS.putName("open");
        public static final Symbol<Name> INSTANCE = SYMBOLS.putName("INSTANCE");
        public static final Symbol<Name> theFileSystem = SYMBOLS.putName("theFileSystem");
        // sun.nio.fs.TrufflePath
        public static final Symbol<Name> HIDDEN_TRUFFLE_FILE = SYMBOLS.putName("0HIDDEN_TRUFFLE_FILE");
        public static final Symbol<Name> instance = SYMBOLS.putName("instance");
        // sun.nio.fs.TruffleFileSystemProvider
        public static final Symbol<Name> OWNER_READ_VALUE = SYMBOLS.putName("OWNER_READ_VALUE");
        public static final Symbol<Name> OWNER_WRITE_VALUE = SYMBOLS.putName("OWNER_WRITE_VALUE");
        public static final Symbol<Name> OWNER_EXECUTE_VALUE = SYMBOLS.putName("OWNER_EXECUTE_VALUE");
        public static final Symbol<Name> GROUP_READ_VALUE = SYMBOLS.putName("GROUP_READ_VALUE");
        public static final Symbol<Name> GROUP_WRITE_VALUE = SYMBOLS.putName("GROUP_WRITE_VALUE");
        public static final Symbol<Name> GROUP_EXECUTE_VALUE = SYMBOLS.putName("GROUP_EXECUTE_VALUE");
        public static final Symbol<Name> OTHERS_READ_VALUE = SYMBOLS.putName("OTHERS_READ_VALUE");
        public static final Symbol<Name> OTHERS_WRITE_VALUE = SYMBOLS.putName("OTHERS_WRITE_VALUE");
        public static final Symbol<Name> OTHERS_EXECUTE_VALUE = SYMBOLS.putName("OTHERS_EXECUTE_VALUE");
        // sun.nio.ch.FileChannelImpl
        public static final Symbol<Name> MAP_RW = SYMBOLS.putName("MAP_RW");
        // java.util.zip
        public static final Symbol<Name> HIDDEN_CRC32 = SYMBOLS.putName("0HIDDEN_CRC32");
        public static final Symbol<Name> inputConsumed = SYMBOLS.putName("inputConsumed");
        public static final Symbol<Name> outputConsumed = SYMBOLS.putName("outputConsumed");
        // java.lang.invoke.*
        // CallSite
        public static final Symbol<Name> target = SYMBOLS.putName("target");
        // java.lang.Enum
        public static final Symbol<Name> $VALUES = SYMBOLS.putName("$VALUES");
        public static final Symbol<Name> ENUM$VALUES = SYMBOLS.putName("ENUM$VALUES");
        // LambdaForm
        public static final Symbol<Name> compileToBytecode = SYMBOLS.putName("compileToBytecode");
        public static final Symbol<Name> isCompiled = SYMBOLS.putName("isCompiled");
        public static final Symbol<Name> vmentry = SYMBOLS.putName("vmentry");
        public static final Symbol<Name> getCallerClass = SYMBOLS.putName("getCallerClass");
        public static final Symbol<Name> createMemoryPool = SYMBOLS.putName("createMemoryPool");
        public static final Symbol<Name> createMemoryManager = SYMBOLS.putName("createMemoryManager");
        public static final Symbol<Name> createGarbageCollector = SYMBOLS.putName("createGarbageCollector");
        public static final Symbol<Name> tid = SYMBOLS.putName("tid");
        public static final Symbol<Name> eetop = SYMBOLS.putName("eetop");
        public static final Symbol<Name> getFromClass = SYMBOLS.putName("getFromClass");
        // MemberName
        public static final Symbol<Name> flags = SYMBOLS.putName("flags");
        public static final Symbol<Name> form = SYMBOLS.putName("form");
        // ResolvedMethodName
        public static final Symbol<Name> vmholder = SYMBOLS.putName("vmholder");
        public static final Symbol<Name> HIDDEN_VM_METHOD = SYMBOLS.putName("0HIDDEN_VM_METHOD");
        // MethodHandle
        public static final Symbol<Name> invoke = SYMBOLS.putName("invoke");
        public static final Symbol<Name> invokeExact = SYMBOLS.putName("invokeExact");
        public static final Symbol<Name> invokeBasic = SYMBOLS.putName("invokeBasic");
        public static final Symbol<Name> invokeWithArguments = SYMBOLS.putName("invokeWithArguments");
        public static final Symbol<Name> linkToVirtual = SYMBOLS.putName("linkToVirtual");
        public static final Symbol<Name> linkToStatic = SYMBOLS.putName("linkToStatic");
        public static final Symbol<Name> linkToInterface = SYMBOLS.putName("linkToInterface");
        public static final Symbol<Name> linkToSpecial = SYMBOLS.putName("linkToSpecial");
        public static final Symbol<Name> linkToNative = SYMBOLS.putName("linkToNative");
        public static final Symbol<Name> asFixedArity = SYMBOLS.putName("asFixedArity");
        public static final Symbol<Name> member = SYMBOLS.putName("member");
        // VarHandles
        public static final Symbol<Name> getStaticFieldFromBaseAndOffset = SYMBOLS.putName("getStaticFieldFromBaseAndOffset");
        // MethodHandleNatives
        public static final Symbol<Name> findMethodHandleType = SYMBOLS.putName("findMethodHandleType");
        public static final Symbol<Name> linkMethod = SYMBOLS.putName("linkMethod");
        public static final Symbol<Name> linkCallSite = SYMBOLS.putName("linkCallSite");
        public static final Symbol<Name> linkDynamicConstant = SYMBOLS.putName("linkDynamicConstant");
        public static final Symbol<Name> linkMethodHandleConstant = SYMBOLS.putName("linkMethodHandleConstant");
        // MethodHandles
        public static final Symbol<Name> lookup = SYMBOLS.putName("lookup");
        // MethodType
        public static final Symbol<Name> ptypes = SYMBOLS.putName("ptypes");
        public static final Symbol<Name> rtype = SYMBOLS.putName("rtype");
        // java.lang.invoke.LambdaMetafactory
        public static final Symbol<Name> metafactory = SYMBOLS.putName("metafactory");
        public static final Symbol<Name> altMetafactory = SYMBOLS.putName("altMetafactory");
        // j.l.ref.Finalizer
        public static final Symbol<Name> finalize = SYMBOLS.putName("finalize");
        public static final Symbol<Name> register = SYMBOLS.putName("register");
        public static final Symbol<Name> runFinalizer = SYMBOLS.putName("runFinalizer");
        // j.l.ref.Reference
        public static final Symbol<Name> discovered = SYMBOLS.putName("discovered");
        public static final Symbol<Name> enqueue = SYMBOLS.putName("enqueue");
        public static final Symbol<Name> getFromInactiveFinalReference = SYMBOLS.putName("getFromInactiveFinalReference");
        public static final Symbol<Name> clearInactiveFinalReference = SYMBOLS.putName("clearInactiveFinalReference");
        public static final Symbol<Name> lock = SYMBOLS.putName("lock");
        public static final Symbol<Name> next = SYMBOLS.putName("next");
        public static final Symbol<Name> NULL = SYMBOLS.putName("NULL");
        public static final Symbol<Name> NULL_QUEUE = SYMBOLS.putName("NULL_QUEUE");
        public static final Symbol<Name> pending = SYMBOLS.putName("pending");
        public static final Symbol<Name> processPendingLock = SYMBOLS.putName("processPendingLock");
        public static final Symbol<Name> queue = SYMBOLS.putName("queue");
        public static final Symbol<Name> referent = SYMBOLS.putName("referent");
        // java.util.regex
        public static final Symbol<Name> parentPattern = SYMBOLS.putName("parentPattern");
        public static final Symbol<Name> text = SYMBOLS.putName("text");
        public static final Symbol<Name> pattern = SYMBOLS.putName("pattern");
        public static final Symbol<Name> flags0 = SYMBOLS.putName("flags0");
        public static final Symbol<Name> compiled = SYMBOLS.putName("compiled");
        public static final Symbol<Name> namedGroups = SYMBOLS.putName("namedGroups");
        public static final Symbol<Name> compile = SYMBOLS.putName("compile");
        public static final Symbol<Name> capturingGroupCount = SYMBOLS.putName("capturingGroupCount");
        public static final Symbol<Name> groupCount = SYMBOLS.putName("groupCount");
        public static final Symbol<Name> match = SYMBOLS.putName("match");
        public static final Symbol<Name> search = SYMBOLS.putName("search");
        public static final Symbol<Name> modCount = SYMBOLS.putName("modCount");
        public static final Symbol<Name> to = SYMBOLS.putName("to");
        public static final Symbol<Name> transparentBounds = SYMBOLS.putName("transparentBounds");
        public static final Symbol<Name> anchoringBounds = SYMBOLS.putName("anchoringBounds");
        public static final Symbol<Name> locals = SYMBOLS.putName("locals");
        public static final Symbol<Name> localsPos = SYMBOLS.putName("localsPos");
        public static final Symbol<Name> hitEnd = SYMBOLS.putName("hitEnd");
        public static final Symbol<Name> requireEnd = SYMBOLS.putName("requireEnd");
        public static final Symbol<Name> localTCNCount = SYMBOLS.putName("localTCNCount");
        public static final Symbol<Name> localCount = SYMBOLS.putName("localCount");
        public static final Symbol<Name> reset = SYMBOLS.putName("reset");
        public static final Symbol<Name> groups = SYMBOLS.putName("groups");
        public static final Symbol<Name> first = SYMBOLS.putName("first");
        public static final Symbol<Name> last = SYMBOLS.putName("last");
        public static final Symbol<Name> oldLast = SYMBOLS.putName("oldLast");
        // java.security.ProtectionDomain
        public static final Symbol<Name> impliesCreateAccessControlContext = SYMBOLS.putName("impliesCreateAccessControlContext");
        // java.security.AccessControlContext
        public static final Symbol<Name> context = SYMBOLS.putName("context");
        public static final Symbol<Name> isAuthorized = SYMBOLS.putName("isAuthorized");
        public static final Symbol<Name> isPrivileged = SYMBOLS.putName("isPrivileged");
        public static final Symbol<Name> privilegedContext = SYMBOLS.putName("privilegedContext");
        public static final Symbol<Name> doPrivileged = SYMBOLS.putName("doPrivileged");
        public static final Symbol<Name> executePrivileged = SYMBOLS.putName("executePrivileged");
        // jdk.internal.misc.UnsafeConstants
        public static final Symbol<Name> ADDRESS_SIZE0 = SYMBOLS.putName("ADDRESS_SIZE0");
        public static final Symbol<Name> PAGE_SIZE = SYMBOLS.putName("PAGE_SIZE");
        public static final Symbol<Name> BIG_ENDIAN = SYMBOLS.putName("BIG_ENDIAN");
        public static final Symbol<Name> UNALIGNED_ACCESS = SYMBOLS.putName("UNALIGNED_ACCESS");
        public static final Symbol<Name> DATA_CACHE_LINE_FLUSH_SIZE = SYMBOLS.putName("DATA_CACHE_LINE_FLUSH_SIZE");
        // sun.launcher.LauncherHelper
        public static final Symbol<Name> printHelpMessage = SYMBOLS.putName("printHelpMessage");
        public static final Symbol<Name> ostream = SYMBOLS.putName("ostream");
        // sun.reflect.ConstantPool
        public static final Symbol<Name> constantPoolOop = SYMBOLS.putName("constantPoolOop");
        // sun.misc.SignalHandler
        public static final Symbol<Name> handle = SYMBOLS.putName("handle");
        public static final Symbol<Name> SIG_DFL = SYMBOLS.putName("SIG_DFL");
        public static final Symbol<Name> SIG_IGN = SYMBOLS.putName("SIG_IGN");
        // sun.misc.NativeSignalHandler
        public static final Symbol<Name> handler = SYMBOLS.putName("handler");
        // sun.nio.ch.NativeThread
        public static final Symbol<Name> isNativeThread = SYMBOLS.putName("isNativeThread");
        public static final Symbol<Name> current0 = SYMBOLS.putName("current0");
        public static final Symbol<Name> signal = SYMBOLS.putName("signal");
        public static final Symbol<Name> init = SYMBOLS.putName("init");
        // jdk.internal.util.ArraysSupport
        public static final Symbol<Name> vectorizedMismatch = SYMBOLS.putName("vectorizedMismatch");
        // Attribute names
        public static final Symbol<Name> AnnotationDefault = SYMBOLS.putName("AnnotationDefault");
        public static final Symbol<Name> BootstrapMethods = SYMBOLS.putName("BootstrapMethods");
        public static final Symbol<Name> Code = SYMBOLS.putName("Code");
        public static final Symbol<Name> ConstantValue = SYMBOLS.putName("ConstantValue");
        public static final Symbol<Name> Deprecated = SYMBOLS.putName("Deprecated");
        public static final Symbol<Name> EnclosingMethod = SYMBOLS.putName("EnclosingMethod");
        public static final Symbol<Name> Exceptions = SYMBOLS.putName("Exceptions");
        public static final Symbol<Name> InnerClasses = SYMBOLS.putName("InnerClasses");
        public static final Symbol<Name> LineNumberTable = SYMBOLS.putName("LineNumberTable");
        public static final Symbol<Name> LocalVariableTable = SYMBOLS.putName("LocalVariableTable");
        public static final Symbol<Name> LocalVariableTypeTable = SYMBOLS.putName("LocalVariableTypeTable");
        public static final Symbol<Name> MethodParameters = SYMBOLS.putName("MethodParameters");
        public static final Symbol<Name> NestHost = SYMBOLS.putName("NestHost");
        public static final Symbol<Name> NestMembers = SYMBOLS.putName("NestMembers");
        public static final Symbol<Name> PermittedSubclasses = SYMBOLS.putName("PermittedSubclasses");
        public static final Symbol<Name> Record = SYMBOLS.putName("Record");
        public static final Symbol<Name> RuntimeVisibleAnnotations = SYMBOLS.putName("RuntimeVisibleAnnotations");
        public static final Symbol<Name> RuntimeInvisibleAnnotations = SYMBOLS.putName("RuntimeInvisibleAnnotations");
        public static final Symbol<Name> RuntimeVisibleTypeAnnotations = SYMBOLS.putName("RuntimeVisibleTypeAnnotations");
        public static final Symbol<Name> RuntimeInvisibleTypeAnnotations = SYMBOLS.putName("RuntimeInvisibleTypeAnnotations");
        public static final Symbol<Name> RuntimeVisibleParameterAnnotations = SYMBOLS.putName("RuntimeVisibleParameterAnnotations");
        public static final Symbol<Name> RuntimeInvisibleParameterAnnotations = SYMBOLS.putName("RuntimeInvisibleParameterAnnotations");
        public static final Symbol<Name> Signature = SYMBOLS.putName("Signature");
        public static final Symbol<Name> SourceFile = SYMBOLS.putName("SourceFile");
        public static final Symbol<Name> SourceDebugExtension = SYMBOLS.putName("SourceDebugExtension");
        public static final Symbol<Name> StackMapTable = SYMBOLS.putName("StackMapTable");
        public static final Symbol<Name> Synthetic = SYMBOLS.putName("Synthetic");
        // Interop conversions
        public static final Symbol<Name> seconds = SYMBOLS.putName("seconds");
        public static final Symbol<Name> nanos = SYMBOLS.putName("nanos");
        public static final Symbol<Name> year = SYMBOLS.putName("year");
        public static final Symbol<Name> month = SYMBOLS.putName("month");
        public static final Symbol<Name> day = SYMBOLS.putName("day");
        public static final Symbol<Name> toLocalDate = SYMBOLS.putName("toLocalDate");
        public static final Symbol<Name> toLocalTime = SYMBOLS.putName("toLocalTime");
        public static final Symbol<Name> toInstant = SYMBOLS.putName("toInstant");
        public static final Symbol<Name> from = SYMBOLS.putName("from");
        public static final Symbol<Name> ofInstant = SYMBOLS.putName("ofInstant");
        public static final Symbol<Name> getZone = SYMBOLS.putName("getZone");
        public static final Symbol<Name> getId = SYMBOLS.putName("getId");
        public static final Symbol<Name> of = SYMBOLS.putName("of");
        public static final Symbol<Name> compose = SYMBOLS.putName("compose");
        public static final Symbol<Name> toByteArray = SYMBOLS.putName("toByteArray");
        public static final Symbol<Name> hour = SYMBOLS.putName("hour");
        public static final Symbol<Name> minute = SYMBOLS.putName("minute");
        public static final Symbol<Name> second = SYMBOLS.putName("second");
        public static final Symbol<Name> nano = SYMBOLS.putName("nano");
        public static final Symbol<Name> atZone = SYMBOLS.putName("atZone");
        public static final Symbol<Name> ofEpochSecond = SYMBOLS.putName("ofEpochSecond");
        // Map / List / Iterator
        public static final Symbol<Name> get = SYMBOLS.putName("get");
        public static final Symbol<Name> set = SYMBOLS.putName("set");
        public static final Symbol<Name> iterator = SYMBOLS.putName("iterator");
        public static final Symbol<Name> put = SYMBOLS.putName("put");
        public static final Symbol<Name> size = SYMBOLS.putName("size");
        public static final Symbol<Name> containsKey = SYMBOLS.putName("containsKey");
        public static final Symbol<Name> getKey = SYMBOLS.putName("getKey");
        public static final Symbol<Name> getValue = SYMBOLS.putName("getValue");
        public static final Symbol<Name> setValue = SYMBOLS.putName("setValue");
        public static final Symbol<Name> entrySet = SYMBOLS.putName("entrySet");
        public static final Symbol<Name> hasNext = SYMBOLS.putName("hasNext");
        public static final Symbol<Name> toArray = SYMBOLS.putName("toArray");

        // CDS
        public static final Symbol<Name> IS_USING_ARCHIVE = SYMBOLS.putName("IS_USING_ARCHIVE");
        public static final Symbol<Name> IS_DUMPING_STATIC_ARCHIVE = SYMBOLS.putName("IS_DUMPING_STATIC_ARCHIVE");
        public static final Symbol<Name> archivedBootLayer = SYMBOLS.putName("archivedBootLayer");

        // j.l.Object
        public static final Symbol<Name> HIDDEN_SYSTEM_IHASHCODE = SYMBOLS.putName("0HIDDEN_SYSTEM_IHASHCODE");
        // MemberName
        public static final Symbol<Name> HIDDEN_VMINDEX = SYMBOLS.putName("0HIDDEN_VMINDEX");
        public static final Symbol<Name> HIDDEN_VMTARGET = SYMBOLS.putName("0HIDDEN_VMTARGET");
        // Method
        public static final Symbol<Name> HIDDEN_METHOD_KEY = SYMBOLS.putName("0HIDDEN_METHOD_KEY");
        public static final Symbol<Name> HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = SYMBOLS.putName("0HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        // Constructor
        public static final Symbol<Name> HIDDEN_CONSTRUCTOR_KEY = SYMBOLS.putName("0HIDDEN_CONSTRUCTOR_KEY");
        public static final Symbol<Name> HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = SYMBOLS.putName("0HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        // Field
        public static final Symbol<Name> HIDDEN_FIELD_KEY = SYMBOLS.putName("0HIDDEN_FIELD_KEY");
        public static final Symbol<Name> HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = SYMBOLS.putName("0HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS");
        // Throwable
        public static final Symbol<Name> HIDDEN_FRAMES = SYMBOLS.putName("0HIDDEN_FRAMES");
        public static final Symbol<Name> HIDDEN_EXCEPTION_WRAPPER = SYMBOLS.putName("0HIDDEN_EXCEPTION_WRAPPER");
        // Thread
        public static final Symbol<Name> interrupted = SYMBOLS.putName("interrupted");
        public static final Symbol<Name> interrupt = SYMBOLS.putName("interrupt");
        public static final Symbol<Name> HIDDEN_DEPRECATION_SUPPORT = SYMBOLS.putName("0HIDDEN_DEPRECATION_SUPPORT");
        public static final Symbol<Name> HIDDEN_THREAD_UNPARK_SIGNALS = SYMBOLS.putName("0HIDDEN_THREAD_UNPARK_SIGNALS");
        public static final Symbol<Name> HIDDEN_THREAD_PARK_LOCK = SYMBOLS.putName("0HIDDEN_THREAD_PARK_LOCK");
        public static final Symbol<Name> HIDDEN_HOST_THREAD = SYMBOLS.putName("0HIDDEN_HOST_THREAD");
        public static final Symbol<Name> HIDDEN_ESPRESSO_MANAGED = SYMBOLS.putName("0HIDDEN_ESPRESSO_MANAGED");
        public static final Symbol<Name> HIDDEN_TO_NATIVE_LOCK = SYMBOLS.putName("0HIDDEN_TO_NATIVE_LOCK");
        public static final Symbol<Name> HIDDEN_INTERRUPTED = SYMBOLS.putName("0HIDDEN_INTERRUPTED");
        public static final Symbol<Name> HIDDEN_THREAD_PENDING_MONITOR = SYMBOLS.putName("0HIDDEN_THREAD_PENDING_MONITOR");
        public static final Symbol<Name> HIDDEN_THREAD_WAITING_MONITOR = SYMBOLS.putName("0HIDDEN_THREAD_WAITING_MONITOR");
        public static final Symbol<Name> HIDDEN_THREAD_BLOCKED_COUNT = SYMBOLS.putName("0HIDDEN_THREAD_BLOCKED_COUNT");
        public static final Symbol<Name> HIDDEN_THREAD_WAITED_COUNT = SYMBOLS.putName("0HIDDEN_THREAD_WAITED_COUNT");
        public static final Symbol<Name> HIDDEN_THREAD_DEPTH_FIRST_NUMBER = SYMBOLS.putName("0HIDDEN_THREAD_DEPTH_FIRST_NUMBER");
        public static final Symbol<Name> HIDDEN_THREAD_SCOPED_VALUE_CACHE = SYMBOLS.putName("0HIDDEN_THREAD_SCOPED_VALUE_CACHE");
        // Class
        public static final Symbol<Name> HIDDEN_MIRROR_KLASS = SYMBOLS.putName("0HIDDEN_MIRROR_KLASS");
        public static final Symbol<Name> HIDDEN_SIGNERS = SYMBOLS.putName("0HIDDEN_SIGNERS");
        public static final Symbol<Name> HIDDEN_PROTECTION_DOMAIN = SYMBOLS.putName("0HIDDEN_PROTECTION_DOMAIN");
        // Module
        public static final Symbol<Name> HIDDEN_MODULE_ENTRY = SYMBOLS.putName("0HIDDEN_MODULE_ENTRY");
        // Pattern
        public static final Symbol<Name> HIDDEN_TREGEX_MATCH = SYMBOLS.putName("0HIDDEN_TREGEX_MATCH");
        public static final Symbol<Name> HIDDEN_TREGEX_FULLMATCH = SYMBOLS.putName("0HIDDEN_TREGEX_FULLMATCH");
        public static final Symbol<Name> HIDDEN_TREGEX_SEARCH = SYMBOLS.putName("0HIDDEN_TREGEX_SEARCH");
        public static final Symbol<Name> HIDDEN_TREGEX_STATUS = SYMBOLS.putName("0HIDDEN_TREGEX_STATUS");
        // Matcher
        public static final Symbol<Name> HIDDEN_TREGEX_TSTRING = SYMBOLS.putName("0HIDDEN_TREGEX_TSTRING");
        public static final Symbol<Name> HIDDEN_TREGEX_TEXT_SYNC = SYMBOLS.putName("0HIDDEN_TREGEX_TEXT_SYNC");
        public static final Symbol<Name> HIDDEN_TREGEX_PATTERN_SYNC = SYMBOLS.putName("0HIDDEN_TREGEX_PATTERN_SYNC");
        public static final Symbol<Name> HIDDEN_TREGEX_OLD_LAST_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_OLD_LAST_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_MOD_COUNT_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_MOD_COUNT_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_TRANSPARENT_BOUNDS_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_TRANSPARENT_BOUNDS_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_ANCHORING_BOUNDS_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_ANCHORING_BOUNDS_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_FROM_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_FROM_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_TO_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_TO_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_MATCHING_MODE_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_MATCHING_MODE_BACKUP");
        public static final Symbol<Name> HIDDEN_TREGEX_SEARCH_FROM_BACKUP = SYMBOLS.putName("0HIDDEN_TREGEX_SEARCH_FROM_BACKUP");
        // Reference
        public static final Symbol<Name> processPendingReferences = SYMBOLS.putName("processPendingReferences");
        public static final Symbol<Name> tryHandlePending = SYMBOLS.putName("tryHandlePending");
        public static final Symbol<Name> poll = SYMBOLS.putName("poll");
        public static final Symbol<Name> HIDDEN_HOST_REFERENCE = SYMBOLS.putName("0HIDDEN_HOST_REFERENCE");
        // Secrets
        public static final Symbol<Name> javaLangAccess = SYMBOLS.putName("javaLangAccess");
        // Polyglot ExceptionType
        public static final Symbol<Name> EXIT = SYMBOLS.putName("EXIT");
        public static final Symbol<Name> INTERRUPT = SYMBOLS.putName("INTERRUPT");
        public static final Symbol<Name> RUNTIME_ERROR = SYMBOLS.putName("RUNTIME_ERROR");
        public static final Symbol<Name> PARSE_ERROR = SYMBOLS.putName("PARSE_ERROR");
        public static final Symbol<Name> create = SYMBOLS.putName("create");
        public static final Symbol<Name> toGuest = SYMBOLS.putName("toGuest");
        // Interop VM helpers
        public static final Symbol<Name> getDynamicModuleDescriptor = SYMBOLS.putName("getDynamicModuleDescriptor");
        public static final Symbol<Name> getEspressoType = SYMBOLS.putName("getEspressoType");
        public static final Symbol<Name> HIDDEN_INTERNAL_TYPE = SYMBOLS.putName("0HIDDEN_INTERNAL_TYPE");
        public static final Symbol<Name> rawType = SYMBOLS.putName("rawType");
        public static final Symbol<Name> espresso_polyglot = SYMBOLS.putName("espresso.polyglot");
        // Class redefinition plugin helpers
        public static final Symbol<Name> flushFromCaches = SYMBOLS.putName("flushFromCaches");
        public static final Symbol<Name> generateProxyClass = SYMBOLS.putName("generateProxyClass");
        public static final Symbol<Name> removeBeanInfo = SYMBOLS.putName("removeBeanInfo");
        public static final Symbol<Name> platformClassLoader = SYMBOLS.putName("platformClassLoader");
        public static final Symbol<Name> bootModules = SYMBOLS.putName("bootModules");
        public static final Symbol<Name> platformModules = SYMBOLS.putName("platformModules");
        public static final Symbol<Name> descriptor = SYMBOLS.putName("descriptor");
        public static final Symbol<Name> ofSystem = SYMBOLS.putName("ofSystem");
        public static final Symbol<Name> defineModule = SYMBOLS.putName("defineModule");
        public static final Symbol<Name> transformedByAgent = SYMBOLS.putName("transformedByAgent");
        // Continuations
        public static final Symbol<Name> suspend = SYMBOLS.putName("suspend");
        public static final Symbol<Name> stackFrameHead = SYMBOLS.putName("stackFrameHead");
        public static final Symbol<Name> HIDDEN_CONTINUATION_FRAME_RECORD = SYMBOLS.putName("0HIDDEN_CONTINUATION_FRAME_RECORD");
        public static final Symbol<Name> pointers = SYMBOLS.putName("pointers");
        public static final Symbol<Name> primitives = SYMBOLS.putName("primitives");
        public static final Symbol<Name> method = SYMBOLS.putName("method");
        // Panama
        public static final Symbol<Name> segmentMaskOrSize = SYMBOLS.putName("segmentMaskOrSize");
        public static final Symbol<Name> indexOrOffset = SYMBOLS.putName("indexOrOffset");
        public static final Symbol<Name> downcallStubAddress = SYMBOLS.putName("downcallStubAddress");
        public static final Symbol<Name> argRegs = SYMBOLS.putName("argRegs");
        public static final Symbol<Name> retRegs = SYMBOLS.putName("retRegs");
        public static final Symbol<Name> exclusiveOwnerThread = SYMBOLS.putName("exclusiveOwnerThread");

        // JVMCI
        public static final Symbol<Name> runtime = SYMBOLS.putName("runtime");
        public static final Symbol<Name> forBasicType = SYMBOLS.putName("forBasicType");
        public static final Symbol<Name> openJVMCITo = SYMBOLS.putName("openJVMCITo");
        public static final Symbol<Name> NULL_POINTER = SYMBOLS.putName("NULL_POINTER");
        public static final Symbol<Name> ILLEGAL = SYMBOLS.putName("ILLEGAL");
        public static final Symbol<Name> forInt = SYMBOLS.putName("forInt");
        public static final Symbol<Name> forLong = SYMBOLS.putName("forLong");
        public static final Symbol<Name> forFloat = SYMBOLS.putName("forFloat");
        public static final Symbol<Name> forDouble = SYMBOLS.putName("forDouble");
        public static final Symbol<Name> forPrimitive = SYMBOLS.putName("forPrimitive");
        public static final Symbol<Name> code = SYMBOLS.putName("code");
        public static final Symbol<Name> INVOKE_BASIC = SYMBOLS.putName("INVOKE_BASIC");
        public static final Symbol<Name> LINK_TO_VIRTUAL = SYMBOLS.putName("LINK_TO_VIRTUAL");
        public static final Symbol<Name> LINK_TO_STATIC = SYMBOLS.putName("LINK_TO_STATIC");
        public static final Symbol<Name> LINK_TO_SPECIAL = SYMBOLS.putName("LINK_TO_SPECIAL");
        public static final Symbol<Name> LINK_TO_INTERFACE = SYMBOLS.putName("LINK_TO_INTERFACE");
        public static final Symbol<Name> LINK_TO_NATIVE = SYMBOLS.putName("LINK_TO_NATIVE");
        public static final Symbol<Name> HIDDEN_OBJECTKLASS_MIRROR = SYMBOLS.putName("0HIDDEN_KLASS_MIRROR");
        public static final Symbol<Name> HIDDEN_JVMCIINDY = SYMBOLS.putName("0HIDDEN_JVMCIINDY");
        public static final Symbol<Name> HIDDEN_FIELD_MIRROR = SYMBOLS.putName("0HIDDEN_FIELD_MIRROR");
        public static final Symbol<Name> HIDDEN_METHOD_MIRROR = SYMBOLS.putName("0HIDDEN_METHOD_MIRROR");
        public static final Symbol<Name> HIDDEN_OBJECT_CONSTANT = SYMBOLS.putName("0HIDDEN_OBJECT_CONSTANT");

        public static void ensureInitialized() {
            assert _init_ == ParserSymbols.ParserNames._init_;
        }
    }

    public static class Signatures {

        public static final Symbol<Signature> _boolean = SYMBOLS.putSignature(Types._boolean);
        public static final Symbol<Signature> _byte = SYMBOLS.putSignature(Types._byte);
        public static final Symbol<Signature> _short = SYMBOLS.putSignature(Types._short);
        public static final Symbol<Signature> _char = SYMBOLS.putSignature(Types._char);
        public static final Symbol<Signature> _int = SYMBOLS.putSignature(Types._int);
        public static final Symbol<Signature> _long = SYMBOLS.putSignature(Types._long);
        public static final Symbol<Signature> _float = SYMBOLS.putSignature(Types._float);
        public static final Symbol<Signature> _double = SYMBOLS.putSignature(Types._double);
        public static final Symbol<Signature> _void = SYMBOLS.putSignature(Types._void);
        public static final Symbol<Signature> Class = SYMBOLS.putSignature(Types.java_lang_Class);
        public static final Symbol<Signature> LocalDate = SYMBOLS.putSignature(Types.java_time_LocalDate);
        public static final Symbol<Signature> LocalDate_int_int_int = SYMBOLS.putSignature(Types.java_time_LocalDate, Types._int, Types._int, Types._int);
        public static final Symbol<Signature> LocalTime = SYMBOLS.putSignature(Types.java_time_LocalTime);
        public static final Symbol<Signature> LocalDateTime_LocalDate_LocalTime = SYMBOLS.putSignature(Types.java_time_LocalDateTime, Types.java_time_LocalDate, Types.java_time_LocalTime);
        public static final Symbol<Signature> LocalTime_int_int_int_int = SYMBOLS.putSignature(Types.java_time_LocalTime, Types._int, Types._int, Types._int, Types._int);
        public static final Symbol<Signature> Instant = SYMBOLS.putSignature(Types.java_time_Instant);
        public static final Symbol<Signature> Date_Instant = SYMBOLS.putSignature(Types.java_util_Date, Types.java_time_Instant);
        public static final Symbol<Signature> Instant_long_long = SYMBOLS.putSignature(Types.java_time_Instant, Types._long, Types._long);
        public static final Symbol<Signature> ZoneId = SYMBOLS.putSignature(Types.java_time_ZoneId);
        public static final Symbol<Signature> ZonedDateTime_Instant_ZoneId = SYMBOLS.putSignature(Types.java_time_ZonedDateTime, Types.java_time_Instant, Types.java_time_ZoneId);
        public static final Symbol<Signature> ZonedDateTime_ZoneId = SYMBOLS.putSignature(Types.java_time_ZonedDateTime, Types.java_time_ZoneId);
        public static final Symbol<Signature> ZoneId_String = SYMBOLS.putSignature(Types.java_time_ZoneId, Types.java_lang_String);
        public static final Symbol<Signature> _void_Object = SYMBOLS.putSignature(Types._void, Types.java_lang_Object);
        public static final Symbol<Signature> Object_Object = SYMBOLS.putSignature(Types.java_lang_Object, Types.java_lang_Object);
        public static final Symbol<Signature> Object_Object_Object = SYMBOLS.putSignature(Types.java_lang_Object, Types.java_lang_Object, Types.java_lang_Object);
        public static final Symbol<Signature> Object_int = SYMBOLS.putSignature(Types.java_lang_Object, Types._int);
        public static final Symbol<Signature> Object_int_Object = SYMBOLS.putSignature(Types.java_lang_Object, Types._int, Types.java_lang_Object);
        public static final Symbol<Signature> Object = SYMBOLS.putSignature(Types.java_lang_Object);
        public static final Symbol<Signature> String = SYMBOLS.putSignature(Types.java_lang_String);
        public static final Symbol<Signature> _void_CharSequence_Pattern = SYMBOLS.putSignature(Types._void, Types.java_util_regex_Pattern, Types.java_lang_CharSequence);
        public static final Symbol<Signature> Matcher_CharSequence = SYMBOLS.putSignature(Types.java_util_regex_Matcher, Types.java_lang_CharSequence);
        public static final Symbol<Signature> ClassLoader = SYMBOLS.putSignature(Types.java_lang_ClassLoader);
        public static final Symbol<Signature> Map = SYMBOLS.putSignature(Types.java_util_Map);
        public static final Symbol<Signature> _void_URL_array_ClassLoader = SYMBOLS.putSignature(Types._void, Types.java_net_URL_array, Types.java_lang_ClassLoader);
        public static final Symbol<Signature> Class_PermissionDomain = SYMBOLS.putSignature(Types._void, Types.java_lang_Class, Types.java_security_ProtectionDomain);
        public static final Symbol<Signature> _void_Class = SYMBOLS.putSignature(Types._void, Types.java_lang_Class);
        public static final Symbol<Signature> Class_array = SYMBOLS.putSignature(Types.java_lang_Class_array);
        public static final Symbol<Signature> Object_String_String = SYMBOLS.putSignature(Types.java_lang_Object, Types.java_lang_String, Types.java_lang_String);
        public static final Symbol<Signature> _void_String_array = SYMBOLS.putSignature(Types._void, Types.java_lang_String_array);
        public static final Symbol<Signature> Class_String_boolean_ClassLoader = SYMBOLS.putSignature(Types.java_lang_Class, Types.java_lang_String, Types._boolean, Types.java_lang_ClassLoader);
        public static final Symbol<Signature> Throwable = SYMBOLS.putSignature(Types.java_lang_Throwable);
        public static final Symbol<Signature> Throwable_Throwable = SYMBOLS.putSignature(Types.java_lang_Throwable, Types.java_lang_Throwable);
        public static final Symbol<Signature> _void_long_boolean_boolean = SYMBOLS.putSignature(Types._void, Types._long, Types._boolean, Types._boolean);
        public static final Symbol<Signature> _void_long_boolean_boolean_boolean = SYMBOLS.putSignature(Types._void, Types._long, Types._boolean, Types._boolean, Types._boolean);
        public static final Symbol<Signature> _byte_array_Module_ClassLoader_String_Class_ProtectionDomain_byte_array_boolean = SYMBOLS.putSignature(
                        Types._byte_array,
                        Types.java_lang_Module,
                        Types.java_lang_ClassLoader,
                        Types.java_lang_String,
                        Types.java_lang_Class,
                        Types.java_security_ProtectionDomain,
                        Types._byte_array,
                        Types._boolean);
        public static final Symbol<Signature> _byte_array_ClassLoader_String_Class_ProtectionDomain_byte_array_boolean = SYMBOLS.putSignature(
                        Types._byte_array,
                        Types.java_lang_ClassLoader,
                        Types.java_lang_String,
                        Types.java_lang_Class,
                        Types.java_security_ProtectionDomain,
                        Types._byte_array,
                        Types._boolean);
        public static final Symbol<Signature> _void_String_String = SYMBOLS.putSignature(Types._void, Types.java_lang_String, Types.java_lang_String);
        public static final Symbol<Signature> _void_Throwable = SYMBOLS.putSignature(Types._void, Types.java_lang_Throwable);
        public static final Symbol<Signature> StackTraceElement_array = SYMBOLS.putSignature(Types.java_lang_StackTraceElement_array);
        public static final Symbol<Signature> _void_String_Throwable = SYMBOLS.putSignature(Types._void, Types.java_lang_String, Types.java_lang_Throwable);
        public static final Symbol<Signature> _void_String = SYMBOLS.putSignature(Types._void, Types.java_lang_String);
        public static final Symbol<Signature> _void_String_int = SYMBOLS.putSignature(Types._void, Types.java_lang_String, Types._int);
        public static final Symbol<Signature> Class_String = SYMBOLS.putSignature(Types.java_lang_Class, Types.java_lang_String);
        public static final Symbol<Signature> InputStream_String = SYMBOLS.putSignature(Types.java_io_InputStream, Types.java_lang_String);
        public static final Symbol<Signature> _int_byte_array_int_int = SYMBOLS.putSignature(Types._int, Types._byte_array, Types._int, Types._int);
        public static final Symbol<Signature> ByteBuffer_int_byte_array_int_int = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._byte_array, Types._int, Types._int);
        public static final Symbol<Signature> ByteBuffer_int_byte = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._byte);
        public static final Symbol<Signature> ByteBuffer_int_short = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._short);
        public static final Symbol<Signature> ByteBuffer_int_int = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._int);
        public static final Symbol<Signature> ByteBuffer_int_long = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._long);
        public static final Symbol<Signature> ByteBuffer_int_float = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._float);
        public static final Symbol<Signature> ByteBuffer_int_double = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._int, Types._double);
        public static final Symbol<Signature> _byte_int = SYMBOLS.putSignature(Types._byte, Types._int);
        public static final Symbol<Signature> _short_int = SYMBOLS.putSignature(Types._short, Types._int);
        public static final Symbol<Signature> _int_int = SYMBOLS.putSignature(Types._int, Types._int);
        public static final Symbol<Signature> _long_int = SYMBOLS.putSignature(Types._long, Types._int);
        public static final Symbol<Signature> _long_long = SYMBOLS.putSignature(Types._long, Types._long);
        public static final Symbol<Signature> _float_int = SYMBOLS.putSignature(Types._float, Types._int);
        public static final Symbol<Signature> _double_int = SYMBOLS.putSignature(Types._double, Types._int);
        public static final Symbol<Signature> ByteBuffer_byte_array = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types._byte_array);
        public static final Symbol<Signature> ByteOrder = SYMBOLS.putSignature(Types.java_nio_ByteOrder);
        public static final Symbol<Signature> ByteBuffer_ByteOrder = SYMBOLS.putSignature(Types.java_nio_ByteBuffer, Types.java_nio_ByteOrder);
        public static final Symbol<Signature> _long_ClassLoader_String = SYMBOLS.putSignature(Types._long, Types.java_lang_ClassLoader, Types.java_lang_String);
        public static final Symbol<Signature> _long_ClassLoader_Class_String_String = SYMBOLS.putSignature(Types._long,
                        Types.java_lang_ClassLoader, Types.java_lang_Class, Types.java_lang_String, Types.java_lang_String);
        public static final Symbol<Signature> _void_Exception = SYMBOLS.putSignature(Types._void, Types.java_lang_Exception);
        public static final Symbol<Signature> _void_String_String_String_int = SYMBOLS.putSignature(Types._void, Types.java_lang_String, Types.java_lang_String, Types.java_lang_String, Types._int);
        public static final Symbol<Signature> _void_int = SYMBOLS.putSignature(Types._void, Types._int);
        public static final Symbol<Signature> _void_boolean = SYMBOLS.putSignature(Types._void, Types._boolean);
        public static final Symbol<Signature> _void_long = SYMBOLS.putSignature(Types._void, Types._long);
        public static final Symbol<Signature> _void_long_int = SYMBOLS.putSignature(Types._void, Types._long, Types._int);
        public static final Symbol<Signature> _void_long_long = SYMBOLS.putSignature(Types._void, Types._long, Types._long);
        public static final Symbol<Signature> _boolean_int = SYMBOLS.putSignature(Types._boolean, Types._int);
        public static final Symbol<Signature> _boolean_int_int = SYMBOLS.putSignature(Types._boolean, Types._int, Types._int);
        public static final Symbol<Signature> _boolean_long = SYMBOLS.putSignature(Types._boolean, Types._long);
        public static final Symbol<Signature> _int_int_int = SYMBOLS.putSignature(Types._int, Types._int, Types._int);
        public static final Symbol<Signature> _void_char_array = SYMBOLS.putSignature(Types._void, Types._char_array);
        public static final Symbol<Signature> _void_byte_array = SYMBOLS.putSignature(Types._void, Types._byte_array);
        public static final Symbol<Signature> _byte_array = SYMBOLS.putSignature(Types._byte_array);
        public static final Symbol<Signature> _char_array = SYMBOLS.putSignature(Types._char_array);
        public static final Symbol<Signature> _int_boolean_boolean = SYMBOLS.putSignature(Types._int, Types._boolean, Types._boolean);
        public static final Symbol<Signature> _boolean_boolean = SYMBOLS.putSignature(Types._boolean, Types._boolean);
        public static final Symbol<Signature> _boolean_Object = SYMBOLS.putSignature(Types._boolean, Types.java_lang_Object);
        public static final Symbol<Signature> Object_long_int_int_int_int = SYMBOLS.putSignature(Types.java_lang_Object, Types._long, Types._int, Types._int, Types._int, Types._int);
        public static final Symbol<Signature> Object_long_int_int_int_Object_array = SYMBOLS.putSignature(Types.java_lang_Object, Types._long, Types._int, Types._int, Types._int,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> _int_Object_long_Object_long_int_int = SYMBOLS.putSignature(Types._int, Types.java_lang_Object, Types._long, Types.java_lang_Object, Types._long,
                        Types._int, Types._int);
        public static final Symbol<Signature> Object_long_int_ContinuationScope_Continuation_int_int_Object_array = SYMBOLS.putSignature(Types.java_lang_Object, Types._long, Types._int,
                        Types.jdk_internal_vm_ContinuationScope, Types.jdk_internal_vm_Continuation, Types._int, Types._int,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> Object_int_int_ContinuationScope_Continuation_int_int_Object_array = SYMBOLS.putSignature(Types.java_lang_Object, Types._int, Types._int,
                        Types.jdk_internal_vm_ContinuationScope, Types.jdk_internal_vm_Continuation, Types._int, Types._int,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> _byte_array_String_Class_array_int = SYMBOLS.putSignature(Types._byte_array, Types.java_lang_String, Types.java_lang_Class_array, Types._int);
        public static final Symbol<Signature> _byte_array_ClassLoader_String_List_int = SYMBOLS.putSignature(Types._byte_array, Types.java_lang_ClassLoader, Types.java_lang_String,
                        Types.java_util_List,
                        Types._int);
        public static final Symbol<Signature> _void_BigInteger_int_MathContext = SYMBOLS.putSignature(Types._void, Types.java_math_BigInteger, Types._int, Types.java_math_MathContext);
        public static final Symbol<Signature> Boolean_boolean = SYMBOLS.putSignature(Types.java_lang_Boolean, Types._boolean);
        public static final Symbol<Signature> Byte_byte = SYMBOLS.putSignature(Types.java_lang_Byte, Types._byte);
        public static final Symbol<Signature> Character_char = SYMBOLS.putSignature(Types.java_lang_Character, Types._char);
        public static final Symbol<Signature> Short_short = SYMBOLS.putSignature(Types.java_lang_Short, Types._short);
        public static final Symbol<Signature> Float_float = SYMBOLS.putSignature(Types.java_lang_Float, Types._float);
        public static final Symbol<Signature> Integer_int = SYMBOLS.putSignature(Types.java_lang_Integer, Types._int);
        public static final Symbol<Signature> Double_double = SYMBOLS.putSignature(Types.java_lang_Double, Types._double);
        public static final Symbol<Signature> Long_long = SYMBOLS.putSignature(Types.java_lang_Long, Types._long);
        public static final Symbol<Signature> Object_array_Object_array = SYMBOLS.putSignature(Types.java_lang_Object_array, Types.java_lang_Object_array);
        public static final Symbol<Signature> Object_Object_array = SYMBOLS.putSignature(Types.java_lang_Object, Types.java_lang_Object_array);
        public static final Symbol<Signature> java_util_Iterator = SYMBOLS.putSignature(Types.java_util_Iterator);
        public static final Symbol<Signature> java_util_Set = SYMBOLS.putSignature(Types.java_util_Set);
        public static final Symbol<Signature> Set_Object_array = SYMBOLS.putSignature(Types.java_util_Set, Types.java_lang_Object_array);
        public static final Symbol<Signature> java_lang_reflect_Method_init_signature = SYMBOLS.putSignature(Types._void,
                        /* declaringClass */ Types.java_lang_Class,
                        /* name */ Types.java_lang_String,
                        /* parameterTypes */ Types.java_lang_Class_array,
                        /* returnType */ Types.java_lang_Class,
                        /* checkedExceptions */ Types.java_lang_Class_array,
                        /* modifiers */ Types._int,
                        /* slot */ Types._int,
                        /* signature */ Types.java_lang_String,
                        /* annotations */ Types._byte_array,
                        /* parameterAnnotations */ Types._byte_array,
                        /* annotationDefault */ Types._byte_array);
        public static final Symbol<Signature> java_lang_reflect_Constructor_init_signature = SYMBOLS.putSignature(Types._void,
                        /* declaringClass */ Types.java_lang_Class,
                        /* parameterTypes */ Types.java_lang_Class_array,
                        /* checkedExceptions */ Types.java_lang_Class_array,
                        /* modifiers */ Types._int,
                        /* slot */ Types._int,
                        /* signature */ Types.java_lang_String,
                        /* annotations */ Types._byte_array,
                        /* parameterAnnotations */ Types._byte_array);
        public static final Symbol<Signature> java_lang_reflect_Field_init_signature = SYMBOLS.putSignature(Types._void,
                        /* declaringClass */ Types.java_lang_Class,
                        /* name */ Types.java_lang_String,
                        /* type */ Types.java_lang_Class,
                        /* modifiers */ Types._int,
                        /* slot */ Types._int,
                        /* signature */ Types.java_lang_String,
                        /* annotations */ Types._byte_array);
        public static final Symbol<Signature> java_lang_reflect_Field_init_signature_15 = SYMBOLS.putSignature(Types._void,
                        /* declaringClass */ Types.java_lang_Class,
                        /* name */ Types.java_lang_String,
                        /* type */ Types.java_lang_Class,
                        /* modifiers */ Types._int,
                        /* trustedFinal */ Types._boolean,
                        /* slot */ Types._int,
                        /* signature */ Types.java_lang_String,
                        /* annotations */ Types._byte_array);
        public static final Symbol<Signature> MethodType_Class_Class = SYMBOLS.putSignature(Types.java_lang_invoke_MethodType, Types.java_lang_Class, Types.java_lang_Class_array);
        public static final Symbol<Signature> MethodType_String_ClassLoader = SYMBOLS.putSignature(Types.java_lang_invoke_MethodType, Types.java_lang_String, Types.java_lang_ClassLoader);
        public static final Symbol<Signature> Java_lang_reflect_Type = SYMBOLS.putSignature(Types.java_lang_reflect_Type);
        public static final Symbol<Signature> Type_array = SYMBOLS.putSignature(Types.java_lang_reflect_Type_array);
        public static final Symbol<Signature> MemberName = SYMBOLS.putSignature(Types.java_lang_invoke_MemberName);
        public static final Symbol<Signature> MemberName_Class_int_Class_String_Object_Object_array = SYMBOLS.putSignature(Types.java_lang_invoke_MemberName, Types.java_lang_Class, Types._int,
                        Types.java_lang_Class, Types.java_lang_String, Types.java_lang_Object, Types.java_lang_Object_array);
        public static final Symbol<Signature> MethodHandle_Class_int_Class_String_Object = SYMBOLS.putSignature(Types.java_lang_invoke_MethodHandle, Types.java_lang_Class, Types._int,
                        Types.java_lang_Class, Types.java_lang_String, Types.java_lang_Object);
        public static final Symbol<Signature> MemberName_Object_Object_Object_Object_Object_Object_array = SYMBOLS.putSignature(
                        Types.java_lang_invoke_MemberName,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> MemberName_Object_int_Object_Object_Object_Object_Object_array = SYMBOLS.putSignature(
                        Types.java_lang_invoke_MemberName,
                        Types.java_lang_Object,
                        Types._int,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> Object_Object_int_Object_Object_Object_Object = SYMBOLS.putSignature(
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types._int,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object);
        public static final Symbol<Signature> Object_Object_Object_Object_Object_Object = SYMBOLS.putSignature(
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object,
                        Types.java_lang_Object);
        public static final Symbol<Signature> MethodHandles$Lookup = SYMBOLS.putSignature(Types.java_lang_invoke_MethodHandles$Lookup);
        public static final Symbol<Signature> MethodHandle = SYMBOLS.putSignature(Types.java_lang_invoke_MethodHandle);
        public static final Symbol<Signature> CallSite_Lookup_String_MethodType_MethodType_MethodHandle_MethodType = SYMBOLS.putSignature(
                        Types.java_lang_invoke_CallSite,
                        Types.java_lang_invoke_MethodHandles$Lookup,
                        Types.java_lang_String,
                        Types.java_lang_invoke_MethodType,
                        Types.java_lang_invoke_MethodType,
                        Types.java_lang_invoke_MethodHandle,
                        Types.java_lang_invoke_MethodType);
        public static final Symbol<Signature> CallSite_Lookup_String_MethodType_Object_array = SYMBOLS.putSignature(
                        Types.java_lang_invoke_CallSite,
                        Types.java_lang_invoke_MethodHandles$Lookup,
                        Types.java_lang_String,
                        Types.java_lang_invoke_MethodType,
                        Types.java_lang_Object_array);
        public static final Symbol<Signature> Field_Object_long_Class = SYMBOLS.putSignature(Types.java_lang_reflect_Field, Types.java_lang_Object, Types._long, Types.java_lang_Class);
        public static final Symbol<Signature> Field_Class_long_Class = SYMBOLS.putSignature(Types.java_lang_reflect_Field, Types.java_lang_Class, Types._long, Types.java_lang_Class);
        public static final Symbol<Signature> Thread$State_int = SYMBOLS.putSignature(Types.java_lang_Thread$State, Types._int);
        public static final Symbol<Signature> _void_ThreadGroup_String = SYMBOLS.putSignature(Types._void, Types.java_lang_ThreadGroup, Types.java_lang_String);
        public static final Symbol<Signature> _void_ThreadGroup_Runnable = SYMBOLS.putSignature(Types._void, Types.java_lang_ThreadGroup, Types.java_lang_Runnable);
        public static final Symbol<Signature> ThreadGroup = SYMBOLS.putSignature(Types.java_lang_ThreadGroup);
        public static final Symbol<Signature> _void_Thread = SYMBOLS.putSignature(Types._void, Types.java_lang_Thread);
        public static final Symbol<Signature> Reference = SYMBOLS.putSignature(Types.java_lang_ref_Reference);
        public static final Symbol<Signature> _void_sun_misc_JavaLangAccess = SYMBOLS.putSignature(Types._void, Types.sun_misc_JavaLangAccess);
        public static final Symbol<Signature> _void_jdk_internal_misc_JavaLangAccess = SYMBOLS.putSignature(Types._void, Types.jdk_internal_misc_JavaLangAccess);
        public static final Symbol<Signature> _void_jdk_internal_access_JavaLangAccess = SYMBOLS.putSignature(Types._void, Types.jdk_internal_access_JavaLangAccess);
        public static final Symbol<Signature> _void_CodeSource_PermissionCollection = SYMBOLS.putSignature(Types._void, Types.java_security_CodeSource, Types.java_security_PermissionCollection);
        // java.management
        public static final Symbol<Signature> MemoryPoolMXBean_String_boolean_long_long = SYMBOLS.putSignature(Types.java_lang_management_MemoryPoolMXBean, Types.java_lang_String, Types._boolean,
                        Types._long, Types._long);
        public static final Symbol<Signature> MemoryManagerMXBean_String = SYMBOLS.putSignature(Types.java_lang_management_MemoryManagerMXBean, Types.java_lang_String);
        public static final Symbol<Signature> GarbageCollectorMXBean_String_String = SYMBOLS.putSignature(Types.java_lang_management_GarbageCollectorMXBean, Types.java_lang_String,
                        Types.java_lang_String);
        // Polyglot/interop API.
        public static final Symbol<Signature> UnsupportedMessageException = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException);
        public static final Symbol<Signature> UnsupportedMessageException_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException,
                        Types.java_lang_Throwable);
        public static final Symbol<Signature> UnsupportedTypeException_Object_array_String = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException,
                        Types.java_lang_Object_array,
                        Types.java_lang_String);
        public static final Symbol<Signature> UnsupportedTypeException_Object_array_String_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException,
                        Types.java_lang_Object_array,
                        Types.java_lang_String,
                        Types.java_lang_Throwable);
        public static final Symbol<Signature> UnknownIdentifierException_String = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException,
                        Types.java_lang_String);
        public static final Symbol<Signature> UnknownIdentifierException_String_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException,
                        Types.java_lang_String, Types.java_lang_Throwable);
        public static final Symbol<Signature> ArityException_int_int_int = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_ArityException, Types._int, Types._int, Types._int);
        public static final Symbol<Signature> ArityException_int_int_int_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_ArityException, Types._int, Types._int,
                        Types._int,
                        Types.java_lang_Throwable);
        public static final Symbol<Signature> InvalidArrayIndexException_long = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException, Types._long);
        public static final Symbol<Signature> InvalidArrayIndexException_long_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException, Types._long,
                        Types.java_lang_Throwable);
        public static final Symbol<Signature> InvalidBufferOffsetException_long_long = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_InvalidBufferOffsetException, Types._long,
                        Types._long);
        public static final Symbol<Signature> InvalidBufferOffsetException_long_long_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_InvalidBufferOffsetException,
                        Types._long, Types._long,
                        Types.java_lang_Throwable);
        public static final Symbol<Signature> StopIterationException = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_StopIterationException);
        public static final Symbol<Signature> StopIterationException_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_StopIterationException, Types.java_lang_Throwable);
        public static final Symbol<Signature> UnknownKeyException_Object = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnknownKeyException,
                        Types.java_lang_Object);
        public static final Symbol<Signature> UnknownKeyException_Object_Throwable = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_polyglot_UnknownKeyException,
                        Types.java_lang_Object, Types.java_lang_Throwable);
        public static final Symbol<Signature> String_String = SYMBOLS.putSignature(Types.java_lang_String, Types.java_lang_String);

        // JVMCI
        public static final Symbol<Signature> EspressoJVMCIRuntime = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_jvmci_EspressoJVMCIRuntime);
        public static final Symbol<Signature> _void_EspressoResolvedJavaType_int_Class = SYMBOLS.putSignature(Types._void, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaType,
                        Types._int, Types.java_lang_Class);
        public static final Symbol<Signature> _void_EspressoResolvedInstanceType = SYMBOLS.putSignature(Types._void, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType);
        public static final Symbol<Signature> _void_EspressoResolvedInstanceType_boolean = SYMBOLS.putSignature(Types._void, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType,
                        Types._boolean);
        public static final Symbol<Signature> EspressoResolvedPrimitiveType_int = SYMBOLS.putSignature(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedPrimitiveType, Types._int);
        public static final Symbol<Signature> DummyEspressoGraalJVMCICompiler_JVMCIRuntime = SYMBOLS.putSignature(Types.jdk_graal_compiler_espresso_DummyEspressoGraalJVMCICompiler,
                        Types.jdk_vm_ci_runtime_JVMCIRuntime);
        public static final Symbol<Signature> _void_Module = SYMBOLS.putSignature(Types._void, Types.java_lang_Module);
        public static final Symbol<Signature> _void_Local_array = SYMBOLS.putSignature(Types._void, Types.jdk_vm_ci_meta_Local_array);
        public static final Symbol<Signature> _void_int_array_int_array = SYMBOLS.putSignature(Types._void, Types._int_array, Types._int_array);
        public static final Symbol<Signature> _void_String_JavaType_int_int_int = SYMBOLS.putSignature(Types._void, Types.java_lang_String, Types.jdk_vm_ci_meta_JavaType, Types._int, Types._int,
                        Types._int);
        public static final Symbol<Signature> _void_int_int_int_int_JavaType = SYMBOLS.putSignature(Types._void, Types._int, Types._int, Types._int, Types._int, Types.jdk_vm_ci_meta_JavaType);
        public static final Symbol<Signature> _void_JavaType_String_JavaType = SYMBOLS.putSignature(Types._void, Types.jdk_vm_ci_meta_JavaType, Types.java_lang_String,
                        Types.jdk_vm_ci_meta_JavaType);
        public static final Symbol<Signature> PrimitiveConstant_int = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_PrimitiveConstant, Types._int);
        public static final Symbol<Signature> PrimitiveConstant_long = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_PrimitiveConstant, Types._long);
        public static final Symbol<Signature> PrimitiveConstant_float = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_PrimitiveConstant, Types._float);
        public static final Symbol<Signature> PrimitiveConstant_double = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_PrimitiveConstant, Types._double);
        public static final Symbol<Signature> PrimitiveConstant_char_long = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_PrimitiveConstant, Types._char, Types._long);
        public static final Symbol<Signature> _void_boolean_EspressoResolvedJavaMethod_String_JavaConstant_JavaConstant_array_int_EspressoConstantPool = SYMBOLS.putSignature(Types._void,
                        Types._boolean, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod, Types.java_lang_String, Types.jdk_vm_ci_meta_JavaConstant,
                        Types.jdk_vm_ci_meta_JavaConstant_array, Types._int, Types.com_oracle_truffle_espresso_jvmci_meta_EspressoConstantPool);
        public static final Symbol<Signature> UnresolvedJavaType_String = SYMBOLS.putSignature(Types.jdk_vm_ci_meta_UnresolvedJavaType, Types.java_lang_String);

        public static final Symbol<Signature> _void_sun_misc_Signal = SYMBOLS.putSignature(Types._void, Types.sun_misc_Signal);
        public static final Symbol<Signature> _void_jdk_internal_misc_Signal = SYMBOLS.putSignature(Types._void, Types.jdk_internal_misc_Signal);
        public static final Symbol<Signature> Path_String_String_array = SYMBOLS.putSignature(Types.java_nio_file_Path, Types.java_lang_String, Types.java_lang_String_array);
        public static final Symbol<Signature> ModuleFinder = SYMBOLS.putSignature(Types.java_lang_module_ModuleFinder);
        public static final Symbol<Signature> ModuleFinder_SystemModules = SYMBOLS.putSignature(Types.java_lang_module_ModuleFinder, Types.jdk_internal_module_SystemModules);
        public static final Symbol<Signature> ModuleFinder_Path_array = SYMBOLS.putSignature(Types.java_lang_module_ModuleFinder, Types.java_nio_file_Path_array);
        public static final Symbol<Signature> ModuleFinder_ModuleFinder_array = SYMBOLS.putSignature(Types.java_lang_module_ModuleFinder, Types.java_lang_module_ModuleFinder_array);
        public static final Symbol<Signature> Module_ClassLoader_ModuleDescriptor_URI = SYMBOLS.putSignature(Types.java_lang_Module, Types.java_lang_ClassLoader,
                        Types.java_lang_module_ModuleDescriptor, Types.java_net_URI);
        public static final Symbol<Signature> ModuleDescriptor_String_String = SYMBOLS.putSignature(Types.java_lang_module_ModuleDescriptor, Types.java_lang_String, Types.java_lang_String);
        // io

        public static final Symbol<Signature> sun_nio_fs_TruffleBasicFileAttributes_init_signature = SYMBOLS.putSignature(
                        Types._void,
                        /* lastModifiedTimeMillis */ Types._long,
                        /* lastAccessTimeMillis */ Types._long,
                        /* creationTimeMillis */ Types._long,
                        /* isRegularFile */ Types._boolean,
                        /* isDirectory */ Types._boolean,
                        /* isSymbolicLink */ Types._boolean,
                        /* isOther */ Types._boolean,
                        /* size */ Types._long);
        public static final Symbol<Signature> sun_nio_fs_TruffleFileSystemProvider = SYMBOLS.putSignature(Types.sun_nio_fs_TruffleFileSystemProvider);

        public static final Symbol<Signature> FileChannel_FileDescriptor_String_boolean_boolean_boolean_Object = SYMBOLS.putSignature(Types.java_nio_channels_FileChannel,
                        Types.java_io_FileDescriptor, Types.java_lang_String, Types._boolean, Types._boolean, Types._boolean, Types.java_lang_Object);
        public static final Symbol<Signature> _void_TruffleFileSystem_String = SYMBOLS.putSignature(Types._void, Types.sun_nio_fs_TruffleFileSystem, Types.java_lang_String);
        // Continuations
        public static final Symbol<Signature> _void_FrameRecord_Object_array_long_array_Method_int_int_Object = SYMBOLS.putSignature(
                        Types._void,
                        Types.java_lang_Object_array,
                        Types._long_array,
                        Types.java_lang_reflect_Method,
                        Types._int,
                        Types._int,
                        Types.java_lang_Object);

        public static void ensureInitialized() {
            assert _void == ParserSymbols.ParserSignatures._void;
        }
    }
}
