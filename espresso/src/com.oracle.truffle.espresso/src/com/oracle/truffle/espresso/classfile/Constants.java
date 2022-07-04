/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

public final class Constants {

    /* Access Flags */
    // @formatter:off
    public static final int ACC_PUBLIC               = 0x00000001;
    public static final int ACC_PRIVATE              = 0x00000002;
    public static final int ACC_PROTECTED            = 0x00000004;
    public static final int ACC_STATIC               = 0x00000008;
    public static final int ACC_FINAL                = 0x00000010;
    public static final int ACC_SYNCHRONIZED         = 0x00000020;
    public static final int ACC_SUPER                = 0x00000020;
    public static final int ACC_VOLATILE             = 0x00000040;
    public static final int ACC_TRANSIENT            = 0x00000080;
    public static final int ACC_NATIVE               = 0x00000100;
    public static final int ACC_INTERFACE            = 0x00000200;
    public static final int ACC_ABSTRACT             = 0x00000400;
    public static final int ACC_STRICT               = 0x00000800;
    public static final int ACC_EXPLICIT             = 0x00001000;

    public static final int ACC_BRIDGE               = 0x00000040;
    public static final int ACC_VARARGS              = 0x00000080;
    public static final int ACC_SYNTHETIC            = 0x00001000;
    public static final int ACC_ANNOTATION           = 0x00002000;
    public static final int ACC_ENUM                 = 0x00004000;
    public static final int ACC_MANDATED             = 0x00008000;
    public static final int ACC_MODULE               = 0x00008000;

    // Not part of the spec, used internally by the VM.
    public static final int ACC_FINALIZER            = 0x00010000;
    public static final int ACC_FORCE_INLINE         = 0x00020000;
    public static final int ACC_LAMBDA_FORM_COMPILED = 0x00040000;
    public static final int ACC_CALLER_SENSITIVE     = 0x00080000;
    public static final int ACC_HIDDEN               = 0x00100000;
    public static final int ACC_IS_HIDDEN_CLASS      = 0x04000000;

    public static final int FIELD_ID_TYPE            = 0x01000000;
    public static final int FIELD_ID_OBFUSCATE       = 0x02000000;

    public static final int JVM_ACC_WRITTEN_FLAGS    = 0x00007FFF;
    // @formatter:on

    // Table 4.1-A. Class access and property modifiers.
    public static final int JVM_RECOGNIZED_CLASS_MODIFIERS = ACC_PUBLIC |
                    ACC_FINAL |
                    ACC_SUPER | // Only very old compilers.
                    ACC_INTERFACE |
                    ACC_ABSTRACT |
                    ACC_ANNOTATION |
                    ACC_ENUM |
                    ACC_SYNTHETIC;

    // Inner classes can be static, private or protected (classic VM does this)
    public static final int RECOGNIZED_INNER_CLASS_MODIFIERS = (JVM_RECOGNIZED_CLASS_MODIFIERS | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC);

    // Table 4.5-A. Field access and property flags.
    public static final int JVM_RECOGNIZED_FIELD_MODIFIERS = ACC_PUBLIC |
                    ACC_PRIVATE |
                    ACC_PROTECTED |
                    ACC_STATIC |
                    ACC_FINAL |
                    ACC_VOLATILE |
                    ACC_TRANSIENT |
                    ACC_ENUM |
                    ACC_SYNTHETIC;

    // Table 4.6-A. Method access and property flags.
    public static final int JVM_RECOGNIZED_METHOD_MODIFIERS = ACC_PUBLIC |
                    ACC_PRIVATE |
                    ACC_PROTECTED |
                    ACC_STATIC |
                    ACC_FINAL |
                    ACC_SYNCHRONIZED |
                    ACC_BRIDGE |
                    ACC_VARARGS |
                    ACC_NATIVE |
                    ACC_ABSTRACT |
                    ACC_STRICT |
                    ACC_SYNTHETIC;

    /* Type codes for StackMap attribute */
    public static final int ITEM_Bogus = 0; // an unknown or uninitialized value
    public static final int ITEM_Integer = 1; // a 32-bit integer
    public static final int ITEM_Float = 2; // not used
    public static final int ITEM_Double = 3; // not used
    public static final int ITEM_Long = 4; // a 64-bit integer
    public static final int ITEM_Null = 5; // the type of null
    public static final int ITEM_InitObject = 6; // "this" in constructor
    public static final int ITEM_Object = 7; // followed by 2-byte index of class name
    public static final int ITEM_NewObject = 8; // followed by 2-byte ref to "new"

    /* Constants used in StackMapTable attribute */
    public static final int SAME_FRAME_BOUND = 64;
    public static final int SAME_LOCALS_1_STACK_ITEM_BOUND = 128;
    public static final int SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247;
    public static final int CHOP_BOUND = 251;
    public static final int SAME_FRAME_EXTENDED = 251;
    public static final int APPEND_FRAME_BOUND = 255;
    public static final int FULL_FRAME = 255;

    public static final int MAX_ARRAY_DIMENSIONS = 255;

    //@formatter:off

    /**
     * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
     */
    public static final byte REF_NONE = 0; // null value
    public static final byte REF_getField = 1;
    public static final byte REF_getStatic = 2;
    public static final byte REF_putField = 3;
    public static final byte REF_putStatic = 4;
    public static final byte REF_invokeVirtual = 5;
    public static final byte REF_invokeStatic = 6;
    public static final byte REF_invokeSpecial = 7;
    public static final byte REF_newInvokeSpecial = 8;
    public static final byte REF_invokeInterface = 9;
    public static final byte REF_LIMIT = 10;


    public static final String[] mnemonics = new String[]{
        /*0:   */"nop",
        /*1:   */"aconst_null",
        /*2:   */"iconst_m1",
        /*3:   */"iconst_0",
        /*4:   */"iconst_1",
        /*5:   */"iconst_2",
        /*6:   */"iconst_3",
        /*7:   */"iconst_4",
        /*8:   */"iconst_5",
        /*9:   */"lconst_0",
        /*10:  */"lconst_1",
        /*11:  */"fconst_0",
        /*12:  */"fconst_1",
        /*13:  */"fconst_2",
        /*14:  */"dconst_0",
        /*15:  */"dconst_1",
        /*16:  */"bipush",
        /*17:  */"sipush",
        /*18:  */"ldc",
        /*19:  */"ldc_w",
        /*20:  */"ldc2_w",
        /*21:  */"iload",
        /*22:  */"lload",
        /*23:  */"fload",
        /*24:  */"dload",
        /*25:  */"aload",
        /*26:  */"iload_0",
        /*27:  */"iload_1",
        /*28:  */"iload_2",
        /*29:  */"iload_3",
        /*30:  */"lload_0",
        /*31:  */"lload_1",
        /*32:  */"lload_2",
        /*33:  */"lload_3",
        /*34:  */"fload_0",
        /*35:  */"fload_1",
        /*36:  */"fload_2",
        /*37:  */"fload_3",
        /*38:  */"dload_0",
        /*39:  */"dload_1",
        /*40:  */"dload_2",
        /*41:  */"dload_3",
        /*42:  */"aload_0",
        /*43:  */"aload_1",
        /*44:  */"aload_2",
        /*45:  */"aload_3",
        /*46:  */"iaload",
        /*47:  */"laload",
        /*48:  */"faload",
        /*49:  */"daload",
        /*50:  */"aaload",
        /*51:  */"baload",
        /*52:  */"caload",
        /*53:  */"saload",
        /*54:  */"istore",
        /*55:  */"lstore",
        /*56:  */"fstore",
        /*57:  */"dstore",
        /*58:  */"astore",
        /*59:  */"istore_0",
        /*60:  */"istore_1",
        /*61:  */"istore_2",
        /*62:  */"istore_3",
        /*63:  */"lstore_0",
        /*64:  */"lstore_1",
        /*65:  */"lstore_2",
        /*66:  */"lstore_3",
        /*67:  */"fstore_0",
        /*68:  */"fstore_1",
        /*69:  */"fstore_2",
        /*70:  */"fstore_3",
        /*71:  */"dstore_0",
        /*72:  */"dstore_1",
        /*73:  */"dstore_2",
        /*74:  */"dstore_3",
        /*75:  */"astore_0",
        /*76:  */"astore_1",
        /*77:  */"astore_2",
        /*78:  */"astore_3",
        /*79:  */"iastore",
        /*80:  */"lastore",
        /*81:  */"fastore",
        /*82:  */"dastore",
        /*83:  */"aastore",
        /*84:  */"bastore",
        /*85:  */"castore",
        /*86:  */"sastore",
        /*87:  */"pop",
        /*88:  */"pop2",
        /*89:  */"dup",
        /*90:  */"dup_x1",
        /*91:  */"dup_x2",
        /*92:  */"dup2",
        /*93:  */"dup2_x1",
        /*94:  */"dup2_x2",
        /*95:  */"swap",
        /*96:  */"iadd",
        /*97:  */"ladd",
        /*98:  */"fadd",
        /*99:  */"dadd",
        /*100: */"isub",
        /*101: */"lsub",
        /*102: */"fsub",
        /*103: */"dsub",
        /*104: */"imul",
        /*105: */"lmul",
        /*106: */"fmul",
        /*107: */"dmul",
        /*108: */"idiv",
        /*109: */"ldiv",
        /*110: */"fdiv",
        /*111: */"ddiv",
        /*112: */"irem",
        /*113: */"lrem",
        /*114: */"frem",
        /*115: */"drem",
        /*116: */"ineg",
        /*117: */"lneg",
        /*118: */"fneg",
        /*119: */"dneg",
        /*120: */"ishl",
        /*121: */"lshl",
        /*122: */"ishr",
        /*123: */"lshr",
        /*124: */"iushr",
        /*125: */"lushr",
        /*126: */"iand",
        /*127: */"land",
        /*128: */"ior",
        /*129: */"lor",
        /*130: */"ixor",
        /*131: */"lxor",
        /*132: */"iinc",
        /*133: */"i2l",
        /*134: */"i2f",
        /*135: */"i2d",
        /*136: */"l2i",
        /*137: */"l2f",
        /*138: */"l2d",
        /*139: */"f2i",
        /*140: */"f2l",
        /*141: */"f2d",
        /*142: */"d2i",
        /*143: */"d2l",
        /*144: */"d2f",
        /*145: */"i2b",
        /*146: */"i2c",
        /*147: */"i2s",
        /*148: */"lcmp",
        /*149: */"fcmpl",
        /*150: */"fcmpg",
        /*151: */"dcmpl",
        /*152: */"dcmpg",
        /*153: */"ifeq",
        /*154: */"ifne",
        /*155: */"iflt",
        /*156: */"ifge",
        /*157: */"ifgt",
        /*158: */"ifle",
        /*159: */"if_icmpeq",
        /*160: */"if_icmpne",
        /*161: */"if_icmplt",
        /*162: */"if_icmpge",
        /*163: */"if_icmpgt",
        /*164: */"if_icmple",
        /*165: */"if_acmpeq",
        /*166: */"if_acmpne",
        /*167: */"goto",
        /*168: */"jsr",
        /*169: */"ret",
        /*170: */"tableswitch",
        /*171: */"lookupswitch",
        /*172: */"ireturn",
        /*173: */"lreturn",
        /*174: */"freturn",
        /*175: */"dreturn",
        /*176: */"areturn",
        /*177: */"return",
        /*178: */"getstatic",
        /*179: */"putstatic",
        /*180: */"getfield",
        /*181: */"putfield",
        /*182: */"invokevirtual",
        /*183: */"invokespecial",
        /*184: */"invokestatic",
        /*185: */"invokeinterface",
        /*186: */"invokedynamic",
        /*187: */"new",
        /*188: */"newarray",
        /*189: */"anewarray",
        /*190: */"arraylength",
        /*191: */"athrow",
        /*192: */"checkcast",
        /*193: */"instanceof",
        /*194: */"monitorenter",
        /*195: */"monitorexit",
        /*196: */"wide",
        /*197: */"multianewarray",
        /*198: */"ifnull",
        /*199: */"ifnonnull",
        /*200: */"goto_w",
        /*201: */"jsr_w"};
    //@formatter:on

    /* Opcodes */
    public static final int opc_dead = -2;
    public static final int opc_label = -1;
    public static final int opc_nop = 0;
    public static final int opc_aconst_null = 1;
    public static final int opc_iconst_m1 = 2;
    public static final int opc_iconst_0 = 3;
    public static final int opc_iconst_1 = 4;
    public static final int opc_iconst_2 = 5;
    public static final int opc_iconst_3 = 6;
    public static final int opc_iconst_4 = 7;
    public static final int opc_iconst_5 = 8;
    public static final int opc_lconst_0 = 9;
    public static final int opc_lconst_1 = 10;
    public static final int opc_fconst_0 = 11;
    public static final int opc_fconst_1 = 12;
    public static final int opc_fconst_2 = 13;
    public static final int opc_dconst_0 = 14;
    public static final int opc_dconst_1 = 15;
    public static final int opc_bipush = 16;
    public static final int opc_sipush = 17;
    public static final int opc_ldc = 18;
    public static final int opc_ldc_w = 19;
    public static final int opc_ldc2_w = 20;
    public static final int opc_iload = 21;
    public static final int opc_lload = 22;
    public static final int opc_fload = 23;
    public static final int opc_dload = 24;
    public static final int opc_aload = 25;
    public static final int opc_iload_0 = 26;
    public static final int opc_iload_1 = 27;
    public static final int opc_iload_2 = 28;
    public static final int opc_iload_3 = 29;
    public static final int opc_lload_0 = 30;
    public static final int opc_lload_1 = 31;
    public static final int opc_lload_2 = 32;
    public static final int opc_lload_3 = 33;
    public static final int opc_fload_0 = 34;
    public static final int opc_fload_1 = 35;
    public static final int opc_fload_2 = 36;
    public static final int opc_fload_3 = 37;
    public static final int opc_dload_0 = 38;
    public static final int opc_dload_1 = 39;
    public static final int opc_dload_2 = 40;
    public static final int opc_dload_3 = 41;
    public static final int opc_aload_0 = 42;
    public static final int opc_aload_1 = 43;
    public static final int opc_aload_2 = 44;
    public static final int opc_aload_3 = 45;
    public static final int opc_iaload = 46;
    public static final int opc_laload = 47;
    public static final int opc_faload = 48;
    public static final int opc_daload = 49;
    public static final int opc_aaload = 50;
    public static final int opc_baload = 51;
    public static final int opc_caload = 52;
    public static final int opc_saload = 53;
    public static final int opc_istore = 54;
    public static final int opc_lstore = 55;
    public static final int opc_fstore = 56;
    public static final int opc_dstore = 57;
    public static final int opc_astore = 58;
    public static final int opc_istore_0 = 59;
    public static final int opc_istore_1 = 60;
    public static final int opc_istore_2 = 61;
    public static final int opc_istore_3 = 62;
    public static final int opc_lstore_0 = 63;
    public static final int opc_lstore_1 = 64;
    public static final int opc_lstore_2 = 65;
    public static final int opc_lstore_3 = 66;
    public static final int opc_fstore_0 = 67;
    public static final int opc_fstore_1 = 68;
    public static final int opc_fstore_2 = 69;
    public static final int opc_fstore_3 = 70;
    public static final int opc_dstore_0 = 71;
    public static final int opc_dstore_1 = 72;
    public static final int opc_dstore_2 = 73;
    public static final int opc_dstore_3 = 74;
    public static final int opc_astore_0 = 75;
    public static final int opc_astore_1 = 76;
    public static final int opc_astore_2 = 77;
    public static final int opc_astore_3 = 78;
    public static final int opc_iastore = 79;
    public static final int opc_lastore = 80;
    public static final int opc_fastore = 81;
    public static final int opc_dastore = 82;
    public static final int opc_aastore = 83;
    public static final int opc_bastore = 84;
    public static final int opc_castore = 85;
    public static final int opc_sastore = 86;
    public static final int opc_pop = 87;
    public static final int opc_pop2 = 88;
    public static final int opc_dup = 89;
    public static final int opc_dup_x1 = 90;
    public static final int opc_dup_x2 = 91;
    public static final int opc_dup2 = 92;
    public static final int opc_dup2_x1 = 93;
    public static final int opc_dup2_x2 = 94;
    public static final int opc_swap = 95;
    public static final int opc_iadd = 96;
    public static final int opc_ladd = 97;
    public static final int opc_fadd = 98;
    public static final int opc_dadd = 99;
    public static final int opc_isub = 100;
    public static final int opc_lsub = 101;
    public static final int opc_fsub = 102;
    public static final int opc_dsub = 103;
    public static final int opc_imul = 104;
    public static final int opc_lmul = 105;
    public static final int opc_fmul = 106;
    public static final int opc_dmul = 107;
    public static final int opc_idiv = 108;
    public static final int opc_ldiv = 109;
    public static final int opc_fdiv = 110;
    public static final int opc_ddiv = 111;
    public static final int opc_irem = 112;
    public static final int opc_lrem = 113;
    public static final int opc_frem = 114;
    public static final int opc_drem = 115;
    public static final int opc_ineg = 116;
    public static final int opc_lneg = 117;
    public static final int opc_fneg = 118;
    public static final int opc_dneg = 119;
    public static final int opc_ishl = 120;
    public static final int opc_lshl = 121;
    public static final int opc_ishr = 122;
    public static final int opc_lshr = 123;
    public static final int opc_iushr = 124;
    public static final int opc_lushr = 125;
    public static final int opc_iand = 126;
    public static final int opc_land = 127;
    public static final int opc_ior = 128;
    public static final int opc_lor = 129;
    public static final int opc_ixor = 130;
    public static final int opc_lxor = 131;
    public static final int opc_iinc = 132;
    public static final int opc_i2l = 133;
    public static final int opc_i2f = 134;
    public static final int opc_i2d = 135;
    public static final int opc_l2i = 136;
    public static final int opc_l2f = 137;
    public static final int opc_l2d = 138;
    public static final int opc_f2i = 139;
    public static final int opc_f2l = 140;
    public static final int opc_f2d = 141;
    public static final int opc_d2i = 142;
    public static final int opc_d2l = 143;
    public static final int opc_d2f = 144;
    public static final int opc_i2b = 145;
    public static final int opc_int2byte = 145;
    public static final int opc_i2c = 146;
    public static final int opc_int2char = 146;
    public static final int opc_i2s = 147;
    public static final int opc_int2short = 147;
    public static final int opc_lcmp = 148;
    public static final int opc_fcmpl = 149;
    public static final int opc_fcmpg = 150;
    public static final int opc_dcmpl = 151;
    public static final int opc_dcmpg = 152;
    public static final int opc_ifeq = 153;
    public static final int opc_ifne = 154;
    public static final int opc_iflt = 155;
    public static final int opc_ifge = 156;
    public static final int opc_ifgt = 157;
    public static final int opc_ifle = 158;
    public static final int opc_if_icmpeq = 159;
    public static final int opc_if_icmpne = 160;
    public static final int opc_if_icmplt = 161;
    public static final int opc_if_icmpge = 162;
    public static final int opc_if_icmpgt = 163;
    public static final int opc_if_icmple = 164;
    public static final int opc_if_acmpeq = 165;
    public static final int opc_if_acmpne = 166;
    public static final int opc_goto = 167;
    public static final int opc_jsr = 168;
    public static final int opc_ret = 169;
    public static final int opc_tableswitch = 170;
    public static final int opc_lookupswitch = 171;
    public static final int opc_ireturn = 172;
    public static final int opc_lreturn = 173;
    public static final int opc_freturn = 174;
    public static final int opc_dreturn = 175;
    public static final int opc_areturn = 176;
    public static final int opc_return = 177;
    public static final int opc_getstatic = 178;
    public static final int opc_putstatic = 179;
    public static final int opc_getfield = 180;
    public static final int opc_putfield = 181;
    public static final int opc_invokevirtual = 182;
    public static final int opc_invokenonvirtual = 183;
    public static final int opc_invokespecial = 183;
    public static final int opc_invokestatic = 184;
    public static final int opc_invokeinterface = 185;
    public static final int opc_invokedynamic = 186;
    public static final int opc_new = 187;
    public static final int opc_newarray = 188;
    public static final int opc_anewarray = 189;
    public static final int opc_arraylength = 190;
    public static final int opc_athrow = 191;
    public static final int opc_checkcast = 192;
    public static final int opc_instanceof = 193;
    public static final int opc_monitorenter = 194;
    public static final int opc_monitorexit = 195;
    public static final int opc_wide = 196;
    public static final int opc_multianewarray = 197;
    public static final int opc_ifnull = 198;
    public static final int opc_ifnonnull = 199;
    public static final int opc_goto_w = 200;
    public static final int opc_jsr_w = 201;

    /* Pseudo-instructions */
    public static final int opc_bytecode = 203;
    public static final int opc_try = 204;
    public static final int opc_endtry = 205;
    public static final int opc_catch = 206;
    public static final int opc_var = 207;
    public static final int opc_endvar = 208;
    public static final int opc_localsmap = 209;
    public static final int opc_stackmap = 210;

    /* PicoJava prefixes */
    public static final int opc_nonpriv = 254;
    public static final int opc_priv = 255;

    /* ArrayType constants */

    public static final int JVM_ArrayType_Boolean = 4;
    public static final int JVM_ArrayType_Char = 5;
    public static final int JVM_ArrayType_Float = 6;
    public static final int JVM_ArrayType_Double = 7;
    public static final int JVM_ArrayType_Byte = 8;
    public static final int JVM_ArrayType_Short = 9;
    public static final int JVM_ArrayType_Int = 10;
    public static final int JVM_ArrayType_Long = 11;
    public static final int JVM_ArrayType_Object = 12;
    public static final int JVM_ArrayType_Void = 14;
    public static final int JVM_ArrayType_ReturnAddress = 98;
    public static final int JVM_ArrayType_Illegal = 99;

    public static byte atype(final Class<?> clazz) {
        if (clazz == boolean.class) {
            return JVM_ArrayType_Boolean;
        } else if (clazz == char.class) {
            return JVM_ArrayType_Char;
        } else if (clazz == float.class) {
            return JVM_ArrayType_Float;
        } else if (clazz == double.class) {
            return JVM_ArrayType_Double;
        } else if (clazz == byte.class) {
            return JVM_ArrayType_Byte;
        } else if (clazz == short.class) {
            return JVM_ArrayType_Short;
        } else if (clazz == int.class) {
            return JVM_ArrayType_Int;
        } else if (clazz == long.class) {
            return JVM_ArrayType_Long;
        }

        throw new IllegalArgumentException();
    }

    public static Class<?> arrayType(byte atype) {
        switch (atype) {
            case JVM_ArrayType_Boolean:
                return boolean.class;
            case JVM_ArrayType_Char:
                return char.class;
            case JVM_ArrayType_Float:
                return float.class;
            case JVM_ArrayType_Double:
                return double.class;
            case JVM_ArrayType_Byte:
                return byte.class;
            case JVM_ArrayType_Short:
                return short.class;
            case JVM_ArrayType_Int:
                return int.class;
            case JVM_ArrayType_Long:
                return long.class;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     *
     * @param bytecode the instruction
     * @return the size of the instruction in bytes
     */
    public static int getInstructionSize(int bytecode) {
        switch (bytecode) {
            case opc_tableswitch:
            case opc_lookupswitch:
            case opc_wide:
                throw new RuntimeException("not implemented");
            case opc_aload:
            case opc_astore:
            case opc_bipush:
            case opc_dload:
            case opc_fload:
            case opc_fstore:
            case opc_istore:
            case opc_ldc:
            case opc_lload:
            case opc_lstore:
            case opc_newarray:
            case opc_ret:
                return 2;
            case opc_anewarray:
            case opc_checkcast:
            case opc_getfield:
            case opc_getstatic:
            case opc_goto:
            case opc_if_acmpeq:
            case opc_if_acmpne:
            case opc_if_icmpeq:
            case opc_if_icmpge:
            case opc_if_icmpgt:
            case opc_if_icmple:
            case opc_if_icmplt:
            case opc_if_icmpne:
            case opc_ifeq:
            case opc_ifne:
            case opc_ifge:
            case opc_ifle:
            case opc_ifgt:
            case opc_iflt:
            case opc_ifnonnull:
            case opc_ifnull:
            case opc_iinc:
            case opc_iload:
            case opc_instanceof:
            case opc_invokespecial:
            case opc_invokestatic:
            case opc_invokevirtual:
            case opc_jsr:
            case opc_ldc_w:
            case opc_ldc2_w:
            case opc_new:
            case opc_putfield:
            case opc_putstatic:
            case opc_sipush:
                return 3;
            case opc_multianewarray:
                return 4;
            case opc_goto_w:
            case opc_invokedynamic:
            case opc_invokeinterface:
            case opc_jsr_w:
                return 5;

            default:
                // all other instructions are size 1
                return 1;
        }
    }
}
