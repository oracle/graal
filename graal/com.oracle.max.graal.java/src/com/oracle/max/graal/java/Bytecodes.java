/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.java;

import static com.oracle.max.graal.java.Bytecodes.Flags.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;

/**
 * The definitions of the bytecodes that are valid input to the compiler and
 * related utility methods. This comprises two groups: the standard Java
 * bytecodes defined by <a href=
 * "http://java.sun.com/docs/books/jvms/second_edition/html/VMSpecTOC.doc.html">
 * Java Virtual Machine Specification</a>, and a set of <i>extended</i>
 * bytecodes that support low-level programming, for example, memory barriers.
 *
 * The extended bytecodes are one or three bytes in size. The one-byte bytecodes
 * follow the values in the standard set, with no gap. The three-byte extended
 * bytecodes share a common first byte and carry additional instruction-specific
 * information in the second and third bytes.
 */
public class Bytecodes {
    public static final int NOP                  =   0; // 0x00
    public static final int ACONST_NULL          =   1; // 0x01
    public static final int ICONST_M1            =   2; // 0x02
    public static final int ICONST_0             =   3; // 0x03
    public static final int ICONST_1             =   4; // 0x04
    public static final int ICONST_2             =   5; // 0x05
    public static final int ICONST_3             =   6; // 0x06
    public static final int ICONST_4             =   7; // 0x07
    public static final int ICONST_5             =   8; // 0x08
    public static final int LCONST_0             =   9; // 0x09
    public static final int LCONST_1             =  10; // 0x0A
    public static final int FCONST_0             =  11; // 0x0B
    public static final int FCONST_1             =  12; // 0x0C
    public static final int FCONST_2             =  13; // 0x0D
    public static final int DCONST_0             =  14; // 0x0E
    public static final int DCONST_1             =  15; // 0x0F
    public static final int BIPUSH               =  16; // 0x10
    public static final int SIPUSH               =  17; // 0x11
    public static final int LDC                  =  18; // 0x12
    public static final int LDC_W                =  19; // 0x13
    public static final int LDC2_W               =  20; // 0x14
    public static final int ILOAD                =  21; // 0x15
    public static final int LLOAD                =  22; // 0x16
    public static final int FLOAD                =  23; // 0x17
    public static final int DLOAD                =  24; // 0x18
    public static final int ALOAD                =  25; // 0x19
    public static final int ILOAD_0              =  26; // 0x1A
    public static final int ILOAD_1              =  27; // 0x1B
    public static final int ILOAD_2              =  28; // 0x1C
    public static final int ILOAD_3              =  29; // 0x1D
    public static final int LLOAD_0              =  30; // 0x1E
    public static final int LLOAD_1              =  31; // 0x1F
    public static final int LLOAD_2              =  32; // 0x20
    public static final int LLOAD_3              =  33; // 0x21
    public static final int FLOAD_0              =  34; // 0x22
    public static final int FLOAD_1              =  35; // 0x23
    public static final int FLOAD_2              =  36; // 0x24
    public static final int FLOAD_3              =  37; // 0x25
    public static final int DLOAD_0              =  38; // 0x26
    public static final int DLOAD_1              =  39; // 0x27
    public static final int DLOAD_2              =  40; // 0x28
    public static final int DLOAD_3              =  41; // 0x29
    public static final int ALOAD_0              =  42; // 0x2A
    public static final int ALOAD_1              =  43; // 0x2B
    public static final int ALOAD_2              =  44; // 0x2C
    public static final int ALOAD_3              =  45; // 0x2D
    public static final int IALOAD               =  46; // 0x2E
    public static final int LALOAD               =  47; // 0x2F
    public static final int FALOAD               =  48; // 0x30
    public static final int DALOAD               =  49; // 0x31
    public static final int AALOAD               =  50; // 0x32
    public static final int BALOAD               =  51; // 0x33
    public static final int CALOAD               =  52; // 0x34
    public static final int SALOAD               =  53; // 0x35
    public static final int ISTORE               =  54; // 0x36
    public static final int LSTORE               =  55; // 0x37
    public static final int FSTORE               =  56; // 0x38
    public static final int DSTORE               =  57; // 0x39
    public static final int ASTORE               =  58; // 0x3A
    public static final int ISTORE_0             =  59; // 0x3B
    public static final int ISTORE_1             =  60; // 0x3C
    public static final int ISTORE_2             =  61; // 0x3D
    public static final int ISTORE_3             =  62; // 0x3E
    public static final int LSTORE_0             =  63; // 0x3F
    public static final int LSTORE_1             =  64; // 0x40
    public static final int LSTORE_2             =  65; // 0x41
    public static final int LSTORE_3             =  66; // 0x42
    public static final int FSTORE_0             =  67; // 0x43
    public static final int FSTORE_1             =  68; // 0x44
    public static final int FSTORE_2             =  69; // 0x45
    public static final int FSTORE_3             =  70; // 0x46
    public static final int DSTORE_0             =  71; // 0x47
    public static final int DSTORE_1             =  72; // 0x48
    public static final int DSTORE_2             =  73; // 0x49
    public static final int DSTORE_3             =  74; // 0x4A
    public static final int ASTORE_0             =  75; // 0x4B
    public static final int ASTORE_1             =  76; // 0x4C
    public static final int ASTORE_2             =  77; // 0x4D
    public static final int ASTORE_3             =  78; // 0x4E
    public static final int IASTORE              =  79; // 0x4F
    public static final int LASTORE              =  80; // 0x50
    public static final int FASTORE              =  81; // 0x51
    public static final int DASTORE              =  82; // 0x52
    public static final int AASTORE              =  83; // 0x53
    public static final int BASTORE              =  84; // 0x54
    public static final int CASTORE              =  85; // 0x55
    public static final int SASTORE              =  86; // 0x56
    public static final int POP                  =  87; // 0x57
    public static final int POP2                 =  88; // 0x58
    public static final int DUP                  =  89; // 0x59
    public static final int DUP_X1               =  90; // 0x5A
    public static final int DUP_X2               =  91; // 0x5B
    public static final int DUP2                 =  92; // 0x5C
    public static final int DUP2_X1              =  93; // 0x5D
    public static final int DUP2_X2              =  94; // 0x5E
    public static final int SWAP                 =  95; // 0x5F
    public static final int IADD                 =  96; // 0x60
    public static final int LADD                 =  97; // 0x61
    public static final int FADD                 =  98; // 0x62
    public static final int DADD                 =  99; // 0x63
    public static final int ISUB                 = 100; // 0x64
    public static final int LSUB                 = 101; // 0x65
    public static final int FSUB                 = 102; // 0x66
    public static final int DSUB                 = 103; // 0x67
    public static final int IMUL                 = 104; // 0x68
    public static final int LMUL                 = 105; // 0x69
    public static final int FMUL                 = 106; // 0x6A
    public static final int DMUL                 = 107; // 0x6B
    public static final int IDIV                 = 108; // 0x6C
    public static final int LDIV                 = 109; // 0x6D
    public static final int FDIV                 = 110; // 0x6E
    public static final int DDIV                 = 111; // 0x6F
    public static final int IREM                 = 112; // 0x70
    public static final int LREM                 = 113; // 0x71
    public static final int FREM                 = 114; // 0x72
    public static final int DREM                 = 115; // 0x73
    public static final int INEG                 = 116; // 0x74
    public static final int LNEG                 = 117; // 0x75
    public static final int FNEG                 = 118; // 0x76
    public static final int DNEG                 = 119; // 0x77
    public static final int ISHL                 = 120; // 0x78
    public static final int LSHL                 = 121; // 0x79
    public static final int ISHR                 = 122; // 0x7A
    public static final int LSHR                 = 123; // 0x7B
    public static final int IUSHR                = 124; // 0x7C
    public static final int LUSHR                = 125; // 0x7D
    public static final int IAND                 = 126; // 0x7E
    public static final int LAND                 = 127; // 0x7F
    public static final int IOR                  = 128; // 0x80
    public static final int LOR                  = 129; // 0x81
    public static final int IXOR                 = 130; // 0x82
    public static final int LXOR                 = 131; // 0x83
    public static final int IINC                 = 132; // 0x84
    public static final int I2L                  = 133; // 0x85
    public static final int I2F                  = 134; // 0x86
    public static final int I2D                  = 135; // 0x87
    public static final int L2I                  = 136; // 0x88
    public static final int L2F                  = 137; // 0x89
    public static final int L2D                  = 138; // 0x8A
    public static final int F2I                  = 139; // 0x8B
    public static final int F2L                  = 140; // 0x8C
    public static final int F2D                  = 141; // 0x8D
    public static final int D2I                  = 142; // 0x8E
    public static final int D2L                  = 143; // 0x8F
    public static final int D2F                  = 144; // 0x90
    public static final int I2B                  = 145; // 0x91
    public static final int I2C                  = 146; // 0x92
    public static final int I2S                  = 147; // 0x93
    public static final int LCMP                 = 148; // 0x94
    public static final int FCMPL                = 149; // 0x95
    public static final int FCMPG                = 150; // 0x96
    public static final int DCMPL                = 151; // 0x97
    public static final int DCMPG                = 152; // 0x98
    public static final int IFEQ                 = 153; // 0x99
    public static final int IFNE                 = 154; // 0x9A
    public static final int IFLT                 = 155; // 0x9B
    public static final int IFGE                 = 156; // 0x9C
    public static final int IFGT                 = 157; // 0x9D
    public static final int IFLE                 = 158; // 0x9E
    public static final int IF_ICMPEQ            = 159; // 0x9F
    public static final int IF_ICMPNE            = 160; // 0xA0
    public static final int IF_ICMPLT            = 161; // 0xA1
    public static final int IF_ICMPGE            = 162; // 0xA2
    public static final int IF_ICMPGT            = 163; // 0xA3
    public static final int IF_ICMPLE            = 164; // 0xA4
    public static final int IF_ACMPEQ            = 165; // 0xA5
    public static final int IF_ACMPNE            = 166; // 0xA6
    public static final int GOTO                 = 167; // 0xA7
    public static final int JSR                  = 168; // 0xA8
    public static final int RET                  = 169; // 0xA9
    public static final int TABLESWITCH          = 170; // 0xAA
    public static final int LOOKUPSWITCH         = 171; // 0xAB
    public static final int IRETURN              = 172; // 0xAC
    public static final int LRETURN              = 173; // 0xAD
    public static final int FRETURN              = 174; // 0xAE
    public static final int DRETURN              = 175; // 0xAF
    public static final int ARETURN              = 176; // 0xB0
    public static final int RETURN               = 177; // 0xB1
    public static final int GETSTATIC            = 178; // 0xB2
    public static final int PUTSTATIC            = 179; // 0xB3
    public static final int GETFIELD             = 180; // 0xB4
    public static final int PUTFIELD             = 181; // 0xB5
    public static final int INVOKEVIRTUAL        = 182; // 0xB6
    public static final int INVOKESPECIAL        = 183; // 0xB7
    public static final int INVOKESTATIC         = 184; // 0xB8
    public static final int INVOKEINTERFACE      = 185; // 0xB9
    public static final int XXXUNUSEDXXX         = 186; // 0xBA
    public static final int NEW                  = 187; // 0xBB
    public static final int NEWARRAY             = 188; // 0xBC
    public static final int ANEWARRAY            = 189; // 0xBD
    public static final int ARRAYLENGTH          = 190; // 0xBE
    public static final int ATHROW               = 191; // 0xBF
    public static final int CHECKCAST            = 192; // 0xC0
    public static final int INSTANCEOF           = 193; // 0xC1
    public static final int MONITORENTER         = 194; // 0xC2
    public static final int MONITOREXIT          = 195; // 0xC3
    public static final int WIDE                 = 196; // 0xC4
    public static final int MULTIANEWARRAY       = 197; // 0xC5
    public static final int IFNULL               = 198; // 0xC6
    public static final int IFNONNULL            = 199; // 0xC7
    public static final int GOTO_W               = 200; // 0xC8
    public static final int JSR_W                = 201; // 0xC9
    public static final int BREAKPOINT           = 202; // 0xCA

    public static final int ILLEGAL = 255;
    public static final int END = 256;

    /**
     * The last opcode defined by the JVM specification. To iterate over all JVM bytecodes:
     * <pre>
     *     for (int opcode = 0; opcode <= Bytecodes.LAST_JVM_OPCODE; ++opcode) {
     *         //
     *     }
     * </pre>
     */
    public static final int LAST_JVM_OPCODE = JSR_W;

    /**
     * A collection of flags describing various bytecode attributes.
     */
    static class Flags {

        /**
         * Denotes an instruction that ends a basic block and does not let control flow fall through to its lexical successor.
         */
        static final int STOP = 0x00000001;

        /**
         * Denotes an instruction that ends a basic block and may let control flow fall through to its lexical successor.
         * In practice this means it is a conditional branch.
         */
        static final int FALL_THROUGH = 0x00000002;

        /**
         * Denotes an instruction that has a 2 or 4 byte operand that is an offset to another instruction in the same method.
         * This does not include the {@link Bytecodes#TABLESWITCH} or {@link Bytecodes#LOOKUPSWITCH} instructions.
         */
        static final int BRANCH = 0x00000004;

        /**
         * Denotes an instruction that reads the value of a static or instance field.
         */
        static final int FIELD_READ = 0x00000008;

        /**
         * Denotes an instruction that writes the value of a static or instance field.
         */
        static final int FIELD_WRITE = 0x00000010;

        /**
         * Denotes an instruction that is not defined in the JVM specification.
         */
        static final int EXTENSION = 0x00000020;

        /**
         * Denotes an instruction that can cause a trap.
         */
        static final int TRAP        = 0x00000080;
        /**
         * Denotes an instruction that is commutative.
         */
        static final int COMMUTATIVE = 0x00000100;
        /**
         * Denotes an instruction that is associative.
         */
        static final int ASSOCIATIVE = 0x00000200;
        /**
         * Denotes an instruction that loads an operand.
         */
        static final int LOAD        = 0x00000400;
        /**
         * Denotes an instruction that stores an operand.
         */
        static final int STORE       = 0x00000800;
        /**
         * Denotes the 4 INVOKE* instructions.
         */
        static final int INVOKE       = 0x00001000;
    }

    // Performs a sanity check that none of the flags overlap.
    static {
        int allFlags = 0;
        try {
            for (Field field : Flags.class.getDeclaredFields()) {
                int flagsFilter = Modifier.FINAL | Modifier.STATIC;
                if ((field.getModifiers() & flagsFilter) == flagsFilter && !field.isSynthetic()) {
                    assert field.getType() == int.class : "Field is not int : " + field;
                    final int flag = field.getInt(null);
                    assert flag != 0;
                    assert (flag & allFlags) == 0 : field.getName() + " has a value conflicting with another flag";
                    allFlags |= flag;
                }
            }
        } catch (Exception e) {
            throw new InternalError(e.toString());
        }
    }

    /**
     * An array that maps from a bytecode value to a {@link String} for the corresponding instruction mnemonic.
     * This will include the root instruction for the three-byte extended instructions.
     */
    private static final String[] nameArray = new String[256];

    /**
     * An array that maps from a bytecode value to the set of {@link Flags} for the corresponding instruction.
     */
    private static final int[] flagsArray = new int[256];

    /**
     * An array that maps from a bytecode value to the length in bytes for the corresponding instruction.
     */
    private static final int[] lengthArray = new int[256];

    /**
     * An array that maps from a bytecode value to the estimated complexity of the bytecode in terms of generated machine code.
     */
    private static final int[] compilationComplexityArray = new int[256];

    // Checkstyle: stop
    static {
        def(NOP                 , "nop"             , "b"    , 0);
        def(ACONST_NULL         , "aconst_null"     , "b"    , 0);
        def(ICONST_M1           , "iconst_m1"       , "b"    , 0);
        def(ICONST_0            , "iconst_0"        , "b"    , 0);
        def(ICONST_1            , "iconst_1"        , "b"    , 0);
        def(ICONST_2            , "iconst_2"        , "b"    , 0);
        def(ICONST_3            , "iconst_3"        , "b"    , 0);
        def(ICONST_4            , "iconst_4"        , "b"    , 0);
        def(ICONST_5            , "iconst_5"        , "b"    , 0);
        def(LCONST_0            , "lconst_0"        , "b"    , 0);
        def(LCONST_1            , "lconst_1"        , "b"    , 0);
        def(FCONST_0            , "fconst_0"        , "b"    , 0);
        def(FCONST_1            , "fconst_1"        , "b"    , 0);
        def(FCONST_2            , "fconst_2"        , "b"    , 0);
        def(DCONST_0            , "dconst_0"        , "b"    , 0);
        def(DCONST_1            , "dconst_1"        , "b"    , 0);
        def(BIPUSH              , "bipush"          , "bc"   , 0);
        def(SIPUSH              , "sipush"          , "bcc"  , 0);
        def(LDC                 , "ldc"             , "bi"   , 0, TRAP);
        def(LDC_W               , "ldc_w"           , "bii"  , 0, TRAP);
        def(LDC2_W              , "ldc2_w"          , "bii"  , 0, TRAP);
        def(ILOAD               , "iload"           , "bi"   , 0, LOAD);
        def(LLOAD               , "lload"           , "bi"   , 0, LOAD);
        def(FLOAD               , "fload"           , "bi"   , 0, LOAD);
        def(DLOAD               , "dload"           , "bi"   , 0, LOAD);
        def(ALOAD               , "aload"           , "bi"   , 0, LOAD);
        def(ILOAD_0             , "iload_0"         , "b"    , 0, LOAD);
        def(ILOAD_1             , "iload_1"         , "b"    , 0, LOAD);
        def(ILOAD_2             , "iload_2"         , "b"    , 0, LOAD);
        def(ILOAD_3             , "iload_3"         , "b"    , 0, LOAD);
        def(LLOAD_0             , "lload_0"         , "b"    , 0, LOAD);
        def(LLOAD_1             , "lload_1"         , "b"    , 0, LOAD);
        def(LLOAD_2             , "lload_2"         , "b"    , 0, LOAD);
        def(LLOAD_3             , "lload_3"         , "b"    , 0, LOAD);
        def(FLOAD_0             , "fload_0"         , "b"    , 0, LOAD);
        def(FLOAD_1             , "fload_1"         , "b"    , 0, LOAD);
        def(FLOAD_2             , "fload_2"         , "b"    , 0, LOAD);
        def(FLOAD_3             , "fload_3"         , "b"    , 0, LOAD);
        def(DLOAD_0             , "dload_0"         , "b"    , 0, LOAD);
        def(DLOAD_1             , "dload_1"         , "b"    , 0, LOAD);
        def(DLOAD_2             , "dload_2"         , "b"    , 0, LOAD);
        def(DLOAD_3             , "dload_3"         , "b"    , 0, LOAD);
        def(ALOAD_0             , "aload_0"         , "b"    , 0, LOAD);
        def(ALOAD_1             , "aload_1"         , "b"    , 0, LOAD);
        def(ALOAD_2             , "aload_2"         , "b"    , 0, LOAD);
        def(ALOAD_3             , "aload_3"         , "b"    , 0, LOAD);
        def(IALOAD              , "iaload"          , "b"    , 0, TRAP);
        def(LALOAD              , "laload"          , "b"    , 0, TRAP);
        def(FALOAD              , "faload"          , "b"    , 0, TRAP);
        def(DALOAD              , "daload"          , "b"    , 0, TRAP);
        def(AALOAD              , "aaload"          , "b"    , 0, TRAP);
        def(BALOAD              , "baload"          , "b"    , 0, TRAP);
        def(CALOAD              , "caload"          , "b"    , 0, TRAP);
        def(SALOAD              , "saload"          , "b"    , 0, TRAP);
        def(ISTORE              , "istore"          , "bi"   , 0, STORE);
        def(LSTORE              , "lstore"          , "bi"   , 0, STORE);
        def(FSTORE              , "fstore"          , "bi"   , 0, STORE);
        def(DSTORE              , "dstore"          , "bi"   , 0, STORE);
        def(ASTORE              , "astore"          , "bi"   , 0, STORE);
        def(ISTORE_0            , "istore_0"        , "b"    , 0, STORE);
        def(ISTORE_1            , "istore_1"        , "b"    , 0, STORE);
        def(ISTORE_2            , "istore_2"        , "b"    , 0, STORE);
        def(ISTORE_3            , "istore_3"        , "b"    , 0, STORE);
        def(LSTORE_0            , "lstore_0"        , "b"    , 0, STORE);
        def(LSTORE_1            , "lstore_1"        , "b"    , 0, STORE);
        def(LSTORE_2            , "lstore_2"        , "b"    , 0, STORE);
        def(LSTORE_3            , "lstore_3"        , "b"    , 0, STORE);
        def(FSTORE_0            , "fstore_0"        , "b"    , 0, STORE);
        def(FSTORE_1            , "fstore_1"        , "b"    , 0, STORE);
        def(FSTORE_2            , "fstore_2"        , "b"    , 0, STORE);
        def(FSTORE_3            , "fstore_3"        , "b"    , 0, STORE);
        def(DSTORE_0            , "dstore_0"        , "b"    , 0, STORE);
        def(DSTORE_1            , "dstore_1"        , "b"    , 0, STORE);
        def(DSTORE_2            , "dstore_2"        , "b"    , 0, STORE);
        def(DSTORE_3            , "dstore_3"        , "b"    , 0, STORE);
        def(ASTORE_0            , "astore_0"        , "b"    , 0, STORE);
        def(ASTORE_1            , "astore_1"        , "b"    , 0, STORE);
        def(ASTORE_2            , "astore_2"        , "b"    , 0, STORE);
        def(ASTORE_3            , "astore_3"        , "b"    , 0, STORE);
        def(IASTORE             , "iastore"         , "b"    , 3, TRAP);
        def(LASTORE             , "lastore"         , "b"    , 3, TRAP);
        def(FASTORE             , "fastore"         , "b"    , 3, TRAP);
        def(DASTORE             , "dastore"         , "b"    , 3, TRAP);
        def(AASTORE             , "aastore"         , "b"    , 4, TRAP);
        def(BASTORE             , "bastore"         , "b"    , 3, TRAP);
        def(CASTORE             , "castore"         , "b"    , 3, TRAP);
        def(SASTORE             , "sastore"         , "b"    , 3, TRAP);
        def(POP                 , "pop"             , "b"    , 0);
        def(POP2                , "pop2"            , "b"    , 0);
        def(DUP                 , "dup"             , "b"    , 0);
        def(DUP_X1              , "dup_x1"          , "b"    , 0);
        def(DUP_X2              , "dup_x2"          , "b"    , 0);
        def(DUP2                , "dup2"            , "b"    , 0);
        def(DUP2_X1             , "dup2_x1"         , "b"    , 0);
        def(DUP2_X2             , "dup2_x2"         , "b"    , 0);
        def(SWAP                , "swap"            , "b"    , 0);
        def(IADD                , "iadd"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(LADD                , "ladd"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(FADD                , "fadd"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(DADD                , "dadd"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(ISUB                , "isub"            , "b"    , 1);
        def(LSUB                , "lsub"            , "b"    , 1);
        def(FSUB                , "fsub"            , "b"    , 1);
        def(DSUB                , "dsub"            , "b"    , 1);
        def(IMUL                , "imul"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(LMUL                , "lmul"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(FMUL                , "fmul"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(DMUL                , "dmul"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(IDIV                , "idiv"            , "b"    , 1, TRAP);
        def(LDIV                , "ldiv"            , "b"    , 1, TRAP);
        def(FDIV                , "fdiv"            , "b"    , 1);
        def(DDIV                , "ddiv"            , "b"    , 1);
        def(IREM                , "irem"            , "b"    , 1, TRAP);
        def(LREM                , "lrem"            , "b"    , 1, TRAP);
        def(FREM                , "frem"            , "b"    , 1);
        def(DREM                , "drem"            , "b"    , 1);
        def(INEG                , "ineg"            , "b"    , 1);
        def(LNEG                , "lneg"            , "b"    , 1);
        def(FNEG                , "fneg"            , "b"    , 1);
        def(DNEG                , "dneg"            , "b"    , 1);
        def(ISHL                , "ishl"            , "b"    , 1);
        def(LSHL                , "lshl"            , "b"    , 1);
        def(ISHR                , "ishr"            , "b"    , 1);
        def(LSHR                , "lshr"            , "b"    , 1);
        def(IUSHR               , "iushr"           , "b"    , 1);
        def(LUSHR               , "lushr"           , "b"    , 1);
        def(IAND                , "iand"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(LAND                , "land"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(IOR                 , "ior"             , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(LOR                 , "lor"             , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(IXOR                , "ixor"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(LXOR                , "lxor"            , "b"    , 1, COMMUTATIVE | ASSOCIATIVE);
        def(IINC                , "iinc"            , "bic"  , 1, LOAD | STORE);
        def(I2L                 , "i2l"             , "b"    , 1);
        def(I2F                 , "i2f"             , "b"    , 1);
        def(I2D                 , "i2d"             , "b"    , 1);
        def(L2I                 , "l2i"             , "b"    , 1);
        def(L2F                 , "l2f"             , "b"    , 1);
        def(L2D                 , "l2d"             , "b"    , 1);
        def(F2I                 , "f2i"             , "b"    , 1);
        def(F2L                 , "f2l"             , "b"    , 1);
        def(F2D                 , "f2d"             , "b"    , 1);
        def(D2I                 , "d2i"             , "b"    , 1);
        def(D2L                 , "d2l"             , "b"    , 1);
        def(D2F                 , "d2f"             , "b"    , 1);
        def(I2B                 , "i2b"             , "b"    , 1);
        def(I2C                 , "i2c"             , "b"    , 1);
        def(I2S                 , "i2s"             , "b"    , 1);
        def(LCMP                , "lcmp"            , "b"    , 1);
        def(FCMPL               , "fcmpl"           , "b"    , 1);
        def(FCMPG               , "fcmpg"           , "b"    , 1);
        def(DCMPL               , "dcmpl"           , "b"    , 1);
        def(DCMPG               , "dcmpg"           , "b"    , 1);
        def(IFEQ                , "ifeq"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFNE                , "ifne"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFLT                , "iflt"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFGE                , "ifge"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFGT                , "ifgt"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFLE                , "ifle"            , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IF_ICMPEQ           , "if_icmpeq"       , "boo"  , 2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ICMPNE           , "if_icmpne"       , "boo"  , 2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ICMPLT           , "if_icmplt"       , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IF_ICMPGE           , "if_icmpge"       , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IF_ICMPGT           , "if_icmpgt"       , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IF_ICMPLE           , "if_icmple"       , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IF_ACMPEQ           , "if_acmpeq"       , "boo"  , 2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ACMPNE           , "if_acmpne"       , "boo"  , 2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(GOTO                , "goto"            , "boo"  , 1, STOP | BRANCH);
        def(JSR                 , "jsr"             , "boo"  , 0, STOP | BRANCH);
        def(RET                 , "ret"             , "bi"   , 0, STOP);
        def(TABLESWITCH         , "tableswitch"     , ""     , 4, STOP);
        def(LOOKUPSWITCH        , "lookupswitch"    , ""     , 4, STOP);
        def(IRETURN             , "ireturn"         , "b"    , 1, TRAP | STOP);
        def(LRETURN             , "lreturn"         , "b"    , 1, TRAP | STOP);
        def(FRETURN             , "freturn"         , "b"    , 1, TRAP | STOP);
        def(DRETURN             , "dreturn"         , "b"    , 1, TRAP | STOP);
        def(ARETURN             , "areturn"         , "b"    , 1, TRAP | STOP);
        def(RETURN              , "return"          , "b"    , 1, TRAP | STOP);
        def(GETSTATIC           , "getstatic"       , "bjj"  , 2, TRAP | FIELD_READ);
        def(PUTSTATIC           , "putstatic"       , "bjj"  , 2, TRAP | FIELD_WRITE);
        def(GETFIELD            , "getfield"        , "bjj"  , 2, TRAP | FIELD_READ);
        def(PUTFIELD            , "putfield"        , "bjj"  , 2, TRAP | FIELD_WRITE);
        def(INVOKEVIRTUAL       , "invokevirtual"   , "bjj"  , 7, TRAP | INVOKE);
        def(INVOKESPECIAL       , "invokespecial"   , "bjj"  , 5, TRAP | INVOKE);
        def(INVOKESTATIC        , "invokestatic"    , "bjj"  , 5, TRAP | INVOKE);
        def(INVOKEINTERFACE     , "invokeinterface" , "bjja_", 7, TRAP | INVOKE);
        def(XXXUNUSEDXXX        , "xxxunusedxxx"    , ""     , 0);
        def(NEW                 , "new"             , "bii"  , 6, TRAP);
        def(NEWARRAY            , "newarray"        , "bc"   , 6, TRAP);
        def(ANEWARRAY           , "anewarray"       , "bii"  , 6, TRAP);
        def(ARRAYLENGTH         , "arraylength"     , "b"    , 2, TRAP);
        def(ATHROW              , "athrow"          , "b"    , 5, TRAP | STOP);
        def(CHECKCAST           , "checkcast"       , "bii"  , 3, TRAP);
        def(INSTANCEOF          , "instanceof"      , "bii"  , 4, TRAP);
        def(MONITORENTER        , "monitorenter"    , "b"    , 5, TRAP);
        def(MONITOREXIT         , "monitorexit"     , "b"    , 5, TRAP);
        def(WIDE                , "wide"            , ""     , 0);
        def(MULTIANEWARRAY      , "multianewarray"  , "biic" , 6, TRAP);
        def(IFNULL              , "ifnull"          , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(IFNONNULL           , "ifnonnull"       , "boo"  , 2, FALL_THROUGH | BRANCH);
        def(GOTO_W              , "goto_w"          , "boooo", 1, STOP | BRANCH);
        def(JSR_W               , "jsr_w"           , "boooo", 0, STOP | BRANCH);
        def(BREAKPOINT          , "breakpoint"      , "b"    , 0, TRAP);
    }
    // Checkstyle: resume

    /**
     * Determines if an opcode is commutative.
     * @param opcode the opcode to check
     * @return {@code true} iff commutative
     */
    public static boolean isCommutative(int opcode) {
        return (flagsArray[opcode & 0xff] & COMMUTATIVE) != 0;
    }

    /**
     * Gets the length of an instruction denoted by a given opcode.
     *
     * @param opcode an instruction opcode
     * @return the length of the instruction denoted by {@code opcode}. If {@code opcode} is an illegal instruction or denotes a
     *         variable length instruction (e.g. {@link #TABLESWITCH}), then 0 is returned.
     */
    public static int lengthOf(int opcode) {
        return lengthArray[opcode & 0xff];
    }

    /**
     * Gets the length of an instruction at a given position in a given bytecode array.
     * This methods handles variable length and {@linkplain #WIDE widened} instructions.
     *
     * @param code an array of bytecode
     * @param bci the position in {@code code} of an instruction's opcode
     * @return the length of the instruction at position {@code bci} in {@code code}
     */
    public static int lengthOf(byte[] code, int bci) {
        int opcode = Bytes.beU1(code, bci);
        int length = Bytecodes.lengthArray[opcode & 0xff];
        if (length == 0) {
            switch (opcode) {
                case TABLESWITCH: {
                    return new BytecodeTableSwitch(code, bci).size();
                }
                case LOOKUPSWITCH: {
                    return new BytecodeLookupSwitch(code, bci).size();
                }
                case WIDE: {
                    int opc = Bytes.beU1(code, bci + 1);
                    if (opc == RET) {
                        return 4;
                    } else if (opc == IINC) {
                        return 6;
                    } else {
                        return 4; // a load or store bytecode
                    }
                }
                default:
                    throw new Error("unknown variable-length bytecode: " + opcode);
            }
        }
        return length;
    }

    /**
     * Gets the compilation complexity for a given opcode.
     * @param opcode an opcode
     * @return a value >= 0
     */
    public static int compilationComplexity(int opcode) {
        return compilationComplexityArray[opcode & 0xff];
    }

    /**
     * Gets the lower-case mnemonic for a given opcode.
     *
     * @param opcode an opcode
     * @return the mnemonic for {@code opcode} or {@code "<illegal opcode: " + opcode + ">"} if {@code opcode} is not a legal opcode
     */
    public static String nameOf(int opcode) throws IllegalArgumentException {
        String name = nameArray[opcode & 0xff];
        if (name == null) {
            return "<illegal opcode: " + opcode + ">";
        }
        return name;
    }

    /**
     * Allocation-free version of {@linkplain #nameOf(int)}.
     * @param opcode an opcode.
     * @return the mnemonic for {@code opcode} or {@code "<illegal opcode>"} if {@code opcode} is not a legal opcode.
     */
    public static String baseNameOf(int opcode) {
        String name = nameArray[opcode & 0xff];
        if (name == null) {
            return "<illegal opcode>";
        }
        return name;
    }

    /**
     * Gets the opcode corresponding to a given mnemonic.
     *
     * @param name an opcode mnemonic
     * @return the opcode corresponding to {@code mnemonic}
     * @throws IllegalArgumentException if {@code name} does not denote a valid opcode
     */
    public static int valueOf(String name) {
        for (int opcode = 0; opcode < nameArray.length; ++opcode) {
            if (name.equalsIgnoreCase(nameArray[opcode])) {
                return opcode;
            }
        }
        throw new IllegalArgumentException("No opcode for " + name);
    }

    /**
     * Determines if a given opcode denotes an instruction that can cause an implicit exception.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} can cause an implicit exception, {@code false} otherwise
     */
    public static boolean canTrap(int opcode) {
        return (flagsArray[opcode & 0xff] & TRAP) != 0;
    }

    /**
     * Determines if a given opcode denotes an instruction that loads a local variable to the operand stack.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} loads a local variable to the operand stack, {@code false} otherwise
     */
    public static boolean isLoad(int opcode) {
        return (flagsArray[opcode & 0xff] & LOAD) != 0;
    }

    /**
     * Determines if a given opcode denotes an instruction that ends a basic block and does not let control flow fall
     * through to its lexical successor.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} properly ends a basic block
     */
    public static boolean isStop(int opcode) {
        return (flagsArray[opcode & 0xff] & STOP) != 0;
    }

    /**
     * Determines if a given opcode denotes an instruction that stores a value to a local variable
     * after popping it from the operand stack.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} stores a value to a local variable, {@code false} otherwise
     */
    public static boolean isInvoke(int opcode) {
        return (flagsArray[opcode & 0xff] & INVOKE) != 0;
    }

    /**
     * Determines if a given opcode denotes an instruction that stores a value to a local variable
     * after popping it from the operand stack.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} stores a value to a local variable, {@code false} otherwise
     */
    public static boolean isStore(int opcode) {
        return (flagsArray[opcode & 0xff] & STORE) != 0;
    }

    /**
     * Determines if a given opcode is an instruction that delimits a basic block.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} delimits a basic block
     */
    public static boolean isBlockEnd(int opcode) {
        return (flagsArray[opcode & 0xff] & (STOP | FALL_THROUGH)) != 0;
    }

    /**
     * Determines if a given opcode is an instruction that has a 2 or 4 byte operand that is an offset to another
     * instruction in the same method. This does not include the {@linkplain #TABLESWITCH switch} instructions.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} is a branch instruction with a single operand
     */
    public static boolean isBranch(int opcode) {
        return (flagsArray[opcode & 0xff] & BRANCH) != 0;
    }

    /**
     * Determines if a given opcode denotes a conditional branch.
     * @param opcode
     * @return {@code true} iff {@code opcode} is a conditional branch
     */
    public static boolean isConditionalBranch(int opcode) {
        return (flagsArray[opcode & 0xff] & FALL_THROUGH) != 0;
    }

    /**
     * Determines if a given opcode denotes a standard bytecode. A standard bytecode is
     * defined in the JVM specification.
     *
     * @param opcode an opcode to test
     * @return {@code true} iff {@code opcode} is a standard bytecode
     */
    public static boolean isStandard(int opcode) {
        return (flagsArray[opcode & 0xff] & EXTENSION) == 0;
    }

    /**
     * Determines if a given opcode denotes an extended bytecode.
     *
     * @param opcode an opcode to test
     * @return {@code true} if {@code opcode} is an extended bytecode
     */
    public static boolean isExtended(int opcode) {
        return (flagsArray[opcode & 0xff] & EXTENSION) != 0;
    }

    /**
     * Determines if a given opcode is a three-byte extended bytecode.
     *
     * @param opcode an opcode to test
     * @return {@code true} if {@code (opcode & ~0xff) != 0}
     */
    public static boolean isThreeByteExtended(int opcode) {
        return (opcode & ~0xff) != 0;
    }

    /**
     * Gets the arithmetic operator name for a given opcode. If {@code opcode} does not denote an
     * arithmetic instruction, then the {@linkplain #nameOf(int) name} of the opcode is returned
     * instead.
     *
     * @param op an opcode
     * @return the arithmetic operator name
     */
    public static String operator(int op) {
        // Checkstyle: stop
        switch (op) {
            // arithmetic ops
            case IADD : // fall through
            case LADD : // fall through
            case FADD : // fall through
            case DADD : return "+";
            case ISUB : // fall through
            case LSUB : // fall through
            case FSUB : // fall through
            case DSUB : return "-";
            case IMUL : // fall through
            case LMUL : // fall through
            case FMUL : // fall through
            case DMUL : return "*";
            case IDIV : // fall through
            case LDIV : // fall through
            case FDIV : // fall through
            case DDIV : return "/";
            case IREM : // fall through
            case LREM : // fall through
            case FREM : // fall through
            case DREM : return "%";
            // shift ops
            case ISHL : // fall through
            case LSHL : return "<<";
            case ISHR : // fall through
            case LSHR : return ">>";
            case IUSHR: // fall through
            case LUSHR: return ">>>";
            // logic ops
            case IAND : // fall through
            case LAND : return "&";
            case IOR  : // fall through
            case LOR  : return "|";
            case IXOR : // fall through
            case LXOR : return "^";
        }
        // Checkstyle: resume
        return nameOf(op);
    }

    /**
     * Defines a bytecode by entering it into the arrays that record its name, length and flags.
     *
     * @param name instruction name (should be lower case)
     * @param format encodes the length of the instruction
     * @param flagsArray the set of {@link Flags} associated with the instruction
     */
    private static void def(int opcode, String name, String format, int compilationComplexity) {
        def(opcode, name, format, compilationComplexity, 0);
    }

    /**
     * Defines a bytecode by entering it into the arrays that record its name, length and flags.
     *
     * @param name instruction name (lower case)
     * @param format encodes the length of the instruction
     * @param flags the set of {@link Flags} associated with the instruction
     */
    private static void def(int opcode, String name, String format, int compilationComplexity, int flags) {
        assert nameArray[opcode] == null : "opcode " + opcode + " is already bound to name " + nameArray[opcode];
        nameArray[opcode] = name;
        int instructionLength = format.length();
        lengthArray[opcode] = instructionLength;
        compilationComplexityArray[opcode] = compilationComplexity;
        Bytecodes.flagsArray[opcode] = flags;

        assert !isConditionalBranch(opcode) || isBranch(opcode) : "a conditional branch must also be a branch";
    }

    /**
     * Utility for ensuring that the extended opcodes are contiguous and follow on directly
     * from the standard JVM opcodes. If these conditions do not hold for the input source
     * file, then it is modified 'in situ' to fix the problem.
     *
     * @param args {@code args[0]} is the path to this source file
     */
    public static void main(String[] args) throws Exception {
        Method findWorkspaceDirectory = Class.forName("com.sun.max.ide.JavaProject").getDeclaredMethod("findWorkspaceDirectory");
        File base = new File((File) findWorkspaceDirectory.invoke(null), "com.oracle.max.cri/src");
        File file = new File(base, Bytecodes.class.getName().replace('.', File.separatorChar) + ".java").getAbsoluteFile();

        Pattern opcodeDecl = Pattern.compile("(\\s*public static final int )(\\w+)(\\s*=\\s*)(\\d+)(;.*)");

        BufferedReader br = new BufferedReader(new FileReader(file));
        CharArrayWriter buffer = new CharArrayWriter((int) file.length());
        PrintWriter out = new PrintWriter(buffer);
        String line;
        int lastExtendedOpcode = BREAKPOINT;
        boolean modified = false;
        int section = 0;
        while ((line = br.readLine()) != null) {
            if (section == 0) {
                if (line.equals("    // Start extended bytecodes")) {
                    section = 1;
                }
            } else if (section == 1) {
                if (line.equals("    // End extended bytecodes")) {
                    section = 2;
                } else {
                    Matcher matcher = opcodeDecl.matcher(line);
                    if (matcher.matches()) {
                        String name = matcher.group(2);
                        String value = matcher.group(4);
                        int opcode = Integer.parseInt(value);
                        if (nameArray[opcode] == null || !nameArray[opcode].equalsIgnoreCase(name)) {
                            throw new RuntimeException("Missing definition of name and flags for " + opcode + ":" + name + " -- " + nameArray[opcode]);
                        }
                        if (opcode != lastExtendedOpcode + 1) {
                            System.err.println("Fixed declaration of opcode " + name + " to be " + (lastExtendedOpcode + 1) + " (was " + value + ")");
                            opcode = lastExtendedOpcode + 1;
                            line = line.substring(0, matcher.start(4)) + opcode + line.substring(matcher.end(4));
                            modified = true;
                        }

                        if (opcode >= 256) {
                            throw new RuntimeException("Exceeded maximum opcode value with " + name);
                        }

                        lastExtendedOpcode = opcode;
                    }
                }
            }

            out.println(line);
        }
        if (section == 0) {
            throw new RuntimeException("Did not find line starting extended bytecode declarations:\n\n    // Start extended bytecodes");
        } else if (section == 1) {
            throw new RuntimeException("Did not find line ending extended bytecode declarations:\n\n    // End extended bytecodes");
        }

        if (modified) {
            out.flush();
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(buffer.toCharArray());
            fileWriter.close();

            System.out.println("Modified: " + file);
        }


        // Uncomment to print out visitor method declarations:
//        for (int opcode = 0; opcode < flags.length; ++opcode) {
//            if (isExtension(opcode)) {
//                String visitorParams = length(opcode) == 1 ? "" : "int index";
//                System.out.println("@Override");
//                System.out.println("protected void " + name(opcode) + "(" + visitorParams + ") {");
//                System.out.println("}");
//                System.out.println();
//            }
//        }

        // Uncomment to print out visitor method declarations:
//        for (int opcode = 0; opcode < flags.length; ++opcode) {
//            if (isExtension(opcode)) {
//                System.out.println("case " + name(opcode).toUpperCase() + ": {");
//                String arg = "";
//                int length = length(opcode);
//                if (length == 2) {
//                    arg = "readUnsigned1()";
//                } else if (length == 3) {
//                    arg = "readUnsigned2()";
//                }
//                System.out.println("    bytecodeVisitor." + name(opcode) + "(" + arg + ");");
//                System.out.println("    break;");
//                System.out.println("}");
//            }
//        }

    }
}
