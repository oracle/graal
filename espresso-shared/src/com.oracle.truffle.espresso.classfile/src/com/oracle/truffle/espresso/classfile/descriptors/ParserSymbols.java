/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.descriptors;

import com.oracle.truffle.espresso.classfile.JavaKind;

/**
 * Predefined symbols used by the parser and shared with Espresso. Contains static definitions for
 * commonly used types, names and signatures.
 * <p>
 * This class is not meant for general symbol management - use {@link SignatureSymbols},
 * {@link TypeSymbols}, {@link NameSymbols} etc. for that purpose.
 * <p>
 * The symbols are initialized in a specific order via the inner classes:
 * <ul>
 * <li>{@link ParserTypes} - Core Java types and primitives
 * <li>{@link ParserNames} - Common names and attribute names
 * <li>{@link ParserSignatures} - Method signatures
 * </ul>
 * <p>
 * Once initialized, the symbols are frozen and no more symbols can be added.
 */
public final class ParserSymbols {

    public static final StaticSymbols SYMBOLS = new StaticSymbols(1 << 8);

    static {
        ParserNames.ensureInitialized();
        ParserTypes.ensureInitialized();
        ParserSignatures.ensureInitialized();
        JavaKind.ensureInitialized();
    }

    public static void ensureInitialized() {
        /* nop */
    }

    public static final class ParserTypes {
        // Core types.
        public static final Symbol<Type> java_lang_String = SYMBOLS.putType("Ljava/lang/String;");
        public static final Symbol<Type> java_lang_Object = SYMBOLS.putType("Ljava/lang/Object;");
        public static final Symbol<Type> java_lang_Class = SYMBOLS.putType("Ljava/lang/Class;");
        public static final Symbol<Type> java_lang_Cloneable = SYMBOLS.putType("Ljava/lang/Cloneable;");
        public static final Symbol<Type> java_io_Serializable = SYMBOLS.putType("Ljava/io/Serializable;");

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
        public static final Symbol<Type> jdk_internal_ValueBased = SYMBOLS.putType("Ljdk/internal/ValueBased;");

        // ScopedMemoryAccess
        public static final Symbol<Type> jdk_internal_misc_ScopedMemoryAccess$Scoped = SYMBOLS.putType("Ljdk/internal/misc/ScopedMemoryAccess$Scoped;");

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

        public static void ensureInitialized() {
            /* nop */
        }
    }

    public static final class ParserNames {
        // general
        public static final Symbol<Name> _init_ = SYMBOLS.putName("<init>");
        public static final Symbol<Name> _clinit_ = SYMBOLS.putName("<clinit>");

        public static final Symbol<Name> finalize = SYMBOLS.putName("finalize");

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
        public static final Symbol<Name> clone = SYMBOLS.putName("clone");

        public static void ensureInitialized() {
            /* nop */
        }
    }

    public static final class ParserSignatures {
        public static final Symbol<Signature> _void = SYMBOLS.putSignature(ParserTypes._void);

        public static void ensureInitialized() {
            /* nop */
        }
    }
}
