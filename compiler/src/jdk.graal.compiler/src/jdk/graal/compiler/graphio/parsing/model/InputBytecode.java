/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class InputBytecode {

    private final int bci;
    private final int length;
    private final byte[] code;

    private final String name;
    private String operands;
    private final String comment;

    private InputMethod inlined;

    private InputBytecode(int bci, byte[] code, int opcode, int length) {
        this.bci = bci;
        this.length = length;
        this.code = code;

        this.name = OPCODE[opcode];
        this.operands = null;
        this.comment = null;
    }

    public InputBytecode(int bci, String name, String operands, String comment) {
        this.bci = bci;
        this.length = -1;
        this.code = null;

        this.name = name;
        this.operands = operands;
        this.comment = comment;
    }

    public InputMethod getInlined() {
        return inlined;
    }

    public void setInlined(InputMethod inlined) {
        this.inlined = inlined;
    }

    public int getBci() {
        return bci;
    }

    public String getName() {
        return name;
    }

    public String getOperands() {
        if (operands == null) {
            if (code == null) {
                operands = "";
            } else {
                operands = readOperands(code, bci, length, toUnsignedInt(code[bci]));
            }
        }
        return operands;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + this.bci;
        hash = 83 * hash + Objects.hashCode(this.name);
        hash = 83 * hash + (code == null ? Objects.hashCode(operands) : hashFromRange(code, bci, bci + length));
        hash = 83 * hash + Objects.hashCode(this.comment);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof InputBytecode)) {
            return false;
        }
        final InputBytecode other = (InputBytecode) obj;
        return this.bci == other.bci && Objects.equals(this.name, other.name) && (code == null ? Objects.equals(operands, other.operands)
                        : equalsInRanges(code, bci, bci + length, other.code, other.bci, other.bci + other.length)) && Objects.equals(this.comment, other.comment) &&
                        Objects.equals(this.inlined, other.inlined);
    }

    public static List<InputBytecode> parseCode(byte[] code, List<InputBytecode> initialBytecodes, List<InputMethod> inlinedMethods) {
        assert code != null && code.length > 0;
        List<InputBytecode> bytecodes = initialBytecodes;
        if (bytecodes == null) {
            bytecodes = new ArrayList<>();
        }
        List<InputMethod> inlined = inlinedMethods;
        if (inlined == null) {
            inlined = Collections.emptyList();
        }
        int toSkip;
        int opcode;
        InputBytecode bc;
        for (int i = 0; i < code.length; i += toSkip + 1) {
            opcode = toUnsignedInt(code[i]);
            assert opcode >= 0 && opcode <= 201 : "Opcode \"" + opcode + "\" isn't in expected range: <0-201>";
            toSkip = resolveLength(code, i, opcode);
            bc = new InputBytecode(i, code, opcode, toSkip);
            bytecodes.add(bc);
            for (InputMethod m : inlined) {
                if (m.getBci() == i) {
                    bc.setInlined(m);
                    break;
                }
            }
        }
        return bytecodes;
    }

    private static String readOperands(byte[] code, int startPos, int length, int opcode) {
        int pos = startPos + 1;
        int toRead = length;
        StringBuilder bld = new StringBuilder();
        int size = OPCODE_AFTER_SIZE[opcode];
        if (opcode == 196) {
            bld.append(OPCODE[toUnsignedInt(code[pos])]).append(", ");
            ++pos;
            --toRead;
        } else if (opcode == 170 || opcode == 171) {
            int padding = pos % 4;
            padding = padding == 0 ? 0 : 4 - padding;
            pos += padding;
            toRead -= padding;
        }
        for (int i = pos; i < pos + toRead; i += size) {
            bld.append(intFromRange(code, i, i + size)).append(", ");
        }
        if (bld.length() > 0) {
            bld.setLength(bld.length() - 2);
        }
        return bld.toString();
    }

    private static int resolveLength(byte[] code, int startPos, int opcode) {
        int position = startPos + 1;
        switch (opcode) {
            case 196:
                if (toUnsignedInt(code[position]) == 132) {
                    return 5;
                }
                return 3;
            case 170:
            case 171:
                int padding = position % 4;
                padding = padding == 0 ? 0 : 4 - padding;
                position += padding;
                int l = intFromRange(code, position + 4, position + 8);
                if (opcode == 171) {
                    return 8 + padding + 8 * (l);
                }
                int h = intFromRange(code, position + 8, position + 12);
                return 12 + padding + 4 * (h - l + 1);
            default:
                return OPCODE_AFTER_BYTES[opcode];
        }
    }

    private static int intFromRange(byte[] bytes, int from, int to) {
        assert from >= 0 && to - from <= 4;
        int val = 0;
        for (int i = from; i < to; ++i) {
            val = val << 8;
            val = val | (bytes[i] & 0xFF);
        }
        return val;
    }

    private static int hashFromRange(byte[] source, int from, int to) {
        int hash = 23;
        for (int i = from; i < Math.min(source.length, to); ++i) {
            hash = hash * 7 + source[i];
        }
        return hash;
    }

    private static boolean equalsInRanges(byte[] source1, int start1, int end1, byte[] source2, int start2, int end2) {
        int from1 = toRange(start1, source1.length, 0);
        int from2 = toRange(start2, source2.length, 0);
        int to1 = toRange(end1, source1.length, 0);
        int to2 = toRange(end2, source2.length, 0);
        if (to1 - from1 != to2 - from2) {
            return false;
        }
        for (int i = 0; i < to1 - from1; ++i) {
            if (source1[from1 + i] != source2[from2 + i]) {
                return false;
            }
        }
        return true;
    }

    private static int toRange(int value, int max, int min) {
        return Math.max(min, Math.min(max, value));
    }

    private static final String[] OPCODE = {
                    "nop", // 0
                    "aconst_null", // 1
                    "iconst_m1", // 2
                    "iconst_0", // 3
                    "iconst_1", // 4
                    "iconst_2", // 5
                    "iconst_3", // 6
                    "iconst_4", // 7
                    "iconst_5", // 8
                    "lconst_0", // 9
                    "lconst_1", // 10
                    "fconst_0", // 11
                    "fconst_1", // 12
                    "fconst_2", // 13
                    "dconst_0", // 14
                    "dconst_1", // 15
                    "bipush", // 16
                    "sipush", // 17
                    "ldc", // 18
                    "ldc_w", // 19
                    "ldc2_w", // 20
                    "iload", // 21
                    "lload", // 22
                    "fload", // 23
                    "dload", // 24
                    "aload", // 25
                    "iload_0", // 26
                    "iload_1", // 27
                    "iload_2", // 28
                    "iload_3", // 29
                    "lload_0", // 30
                    "lload_1", // 31
                    "lload_2", // 32
                    "lload_3", // 33
                    "fload_0", // 34
                    "fload_1", // 35
                    "fload_2", // 36
                    "fload_3", // 37
                    "dload_0", // 38
                    "dload_1", // 39
                    "dload_2", // 40
                    "dload_3", // 41
                    "aload_0", // 42
                    "aload_1", // 43
                    "aload_2", // 44
                    "aload_3", // 45
                    "iaload", // 46
                    "laload", // 47
                    "faload", // 48
                    "daload", // 49
                    "aaload", // 50
                    "baload", // 51
                    "caload", // 52
                    "saload", // 53
                    "istore", // 54
                    "lstore", // 55
                    "fstore", // 56
                    "dstore", // 57
                    "astore", // 58
                    "istore_0", // 59
                    "istore_1", // 60
                    "istore_2", // 61
                    "istore_3", // 62
                    "lstore_0", // 63
                    "lstore_1", // 64
                    "lstore_2", // 65
                    "lstore_3", // 66
                    "fstore_0", // 67
                    "fstore_1", // 68
                    "fstore_2", // 69
                    "fstore_3", // 70
                    "dstore_0", // 71
                    "dstore_1", // 72
                    "dstore_2", // 73
                    "dstore_3", // 74
                    "astore_0", // 75
                    "astore_1", // 76
                    "astore_2", // 77
                    "astore_3", // 78
                    "iastore", // 79
                    "lastore", // 80
                    "fastore", // 81
                    "dastore", // 82
                    "aastore", // 83
                    "bastore", // 84
                    "castore", // 85
                    "sastore", // 86
                    "pop", // 87
                    "pop2", // 88
                    "dup", // 89
                    "dup_x1", // 90
                    "dup_x2", // 91
                    "dup2", // 92
                    "dup2_x1", // 93
                    "dup2_x2", // 94
                    "swap", // 95
                    "iadd", // 96
                    "ladd", // 97
                    "fadd", // 98
                    "dadd", // 99
                    "isub", // 100
                    "lsub", // 101
                    "fsub", // 102
                    "dsub", // 103
                    "imul", // 104
                    "lmul", // 105
                    "fmul", // 106
                    "dmul", // 107
                    "idiv", // 108
                    "ldiv", // 109
                    "fdiv", // 110
                    "ddiv", // 111
                    "irem", // 112
                    "lrem", // 113
                    "frem", // 114
                    "drem", // 115
                    "ineg", // 116
                    "lneg", // 117
                    "fneg", // 118
                    "dneg", // 119
                    "ishl", // 120
                    "lshl", // 121
                    "ishr", // 122
                    "lshr", // 123
                    "iushr", // 124
                    "lushr", // 125
                    "iand", // 126
                    "land", // 127
                    "ior", // 128
                    "lor", // 129
                    "ixor", // 130
                    "lxor", // 131
                    "iinc", // 132
                    "i2l", // 133
                    "i2f", // 134
                    "i2d", // 135
                    "l2i", // 136
                    "l2f", // 137
                    "l2d", // 138
                    "f2i", // 139
                    "f2l", // 140
                    "f2d", // 141
                    "d2i", // 142
                    "d2l", // 143
                    "d2f", // 144
                    "i2b", // 145
                    "i2c", // 146
                    "i2s", // 147
                    "lcmp", // 148
                    "fcmpl", // 149
                    "fcmpg", // 150
                    "dcmpl", // 151
                    "dcmpg", // 152
                    "ifeq", // 153
                    "ifne", // 154
                    "iflt", // 155
                    "ifge", // 156
                    "ifgt", // 157
                    "ifle", // 158
                    "if_icmpeq", // 159
                    "if_icmpne", // 160
                    "if_icmplt", // 161
                    "if_icmpge", // 162
                    "if_icmpgt", // 163
                    "if_icmple", // 164
                    "if_acmpeq", // 165
                    "if_acmpne", // 166
                    "goto", // 167
                    "jsr", // 168
                    "ret", // 169
                    "tableswitch", // 170
                    "lookupswitch", // 171
                    "ireturn", // 172
                    "lreturn", // 173
                    "freturn", // 174
                    "dreturn", // 175
                    "areturn", // 176
                    "return", // 177
                    "getstatic", // 178
                    "putstatic", // 179
                    "getfield", // 180
                    "putfield", // 181
                    "invokevirtual", // 182
                    "invokespecial", // 183
                    "invokestatic", // 184
                    "invokeinterface", // 185
                    "invokedynamic", // 186
                    "new", // 187
                    "newarray", // 188
                    "anewarray", // 189
                    "arraylength", // 190
                    "athrow", // 191
                    "checkcast", // 192
                    "instanceof", // 193
                    "monitorenter", // 194
                    "monitorexit", // 195
                    "wide", // 196
                    "multianewarray", // 197
                    "ifnull", // 198
                    "ifnonnull", // 199
                    "goto_w", // 200
                    "jsr_w" // 201
    };

    private static final int[] OPCODE_AFTER_BYTES = {
                    0, // 0
                    0, // 1
                    0, // 2
                    0, // 3
                    0, // 4
                    0, // 5
                    0, // 6
                    0, // 7
                    0, // 8
                    0, // 9
                    0, // 10
                    0, // 11
                    0, // 12
                    0, // 13
                    0, // 14
                    0, // 15
                    1, // 16
                    2, // 17
                    1, // 18
                    2, // 19
                    2, // 20
                    1, // 21
                    1, // 22
                    1, // 23
                    1, // 24
                    1, // 25
                    0, // 26
                    0, // 27
                    0, // 28
                    0, // 29
                    0, // 30
                    0, // 31
                    0, // 32
                    0, // 33
                    0, // 34
                    0, // 35
                    0, // 36
                    0, // 37
                    0, // 38
                    0, // 39
                    0, // 40
                    0, // 41
                    0, // 42
                    0, // 43
                    0, // 44
                    0, // 45
                    0, // 46
                    0, // 47
                    0, // 48
                    0, // 49
                    0, // 50
                    0, // 51
                    0, // 52
                    0, // 53
                    1, // 54
                    1, // 55
                    1, // 56
                    1, // 57
                    1, // 58
                    0, // 59
                    0, // 60
                    0, // 61
                    0, // 62
                    0, // 63
                    0, // 64
                    0, // 65
                    0, // 66
                    0, // 67
                    0, // 68
                    0, // 69
                    0, // 70
                    0, // 71
                    0, // 72
                    0, // 73
                    0, // 74
                    0, // 75
                    0, // 76
                    0, // 77
                    0, // 78
                    0, // 79
                    0, // 80
                    0, // 81
                    0, // 82
                    0, // 83
                    0, // 84
                    0, // 85
                    0, // 86
                    0, // 87
                    0, // 88
                    0, // 89
                    0, // 90
                    0, // 91
                    0, // 92
                    0, // 93
                    0, // 94
                    0, // 95
                    0, // 96
                    0, // 97
                    0, // 98
                    0, // 99
                    0, // 100
                    0, // 101
                    0, // 102
                    0, // 103
                    0, // 104
                    0, // 105
                    0, // 106
                    0, // 107
                    0, // 108
                    0, // 109
                    0, // 110
                    0, // 111
                    0, // 112
                    0, // 113
                    0, // 114
                    0, // 115
                    0, // 116
                    0, // 117
                    0, // 118
                    0, // 119
                    0, // 120
                    0, // 121
                    0, // 122
                    0, // 123
                    0, // 124
                    0, // 125
                    0, // 126
                    0, // 127
                    0, // 128
                    0, // 129
                    0, // 130
                    0, // 131
                    2, // 132
                    0, // 133
                    0, // 134
                    0, // 135
                    0, // 136
                    0, // 137
                    0, // 138
                    0, // 139
                    0, // 140
                    0, // 141
                    0, // 142
                    0, // 143
                    0, // 144
                    0, // 145
                    0, // 146
                    0, // 147
                    0, // 148
                    0, // 149
                    0, // 150
                    0, // 151
                    0, // 152
                    2, // 153
                    2, // 154
                    2, // 155
                    2, // 156
                    2, // 157
                    2, // 158
                    2, // 159
                    2, // 160
                    2, // 161
                    2, // 162
                    2, // 163
                    2, // 164
                    2, // 165
                    2, // 166
                    2, // 167
                    2, // 168
                    1, // 169
                    -1, // 170
                    -1, // 171
                    0, // 172
                    0, // 173
                    0, // 174
                    0, // 175
                    0, // 176
                    0, // 177
                    2, // 178
                    2, // 179
                    2, // 180
                    2, // 181
                    2, // 182
                    2, // 183
                    2, // 184
                    4, // 185
                    4, // 186
                    2, // 187
                    1, // 188
                    2, // 189
                    0, // 190
                    0, // 191
                    2, // 192
                    2, // 193
                    0, // 194
                    0, // 195
                    -1, // 196
                    3, // 197
                    2, // 198
                    2, // 199
                    4, // 200
                    4 // 201
    };
    private static final int[] OPCODE_AFTER_SIZE = {
                    0, // 0
                    0, // 1
                    0, // 2
                    0, // 3
                    0, // 4
                    0, // 5
                    0, // 6
                    0, // 7
                    0, // 8
                    0, // 9
                    0, // 10
                    0, // 11
                    0, // 12
                    0, // 13
                    0, // 14
                    0, // 15
                    1, // 16
                    2, // 17
                    1, // 18
                    2, // 19
                    2, // 20
                    1, // 21
                    1, // 22
                    1, // 23
                    1, // 24
                    1, // 25
                    0, // 26
                    0, // 27
                    0, // 28
                    0, // 29
                    0, // 30
                    0, // 31
                    0, // 32
                    0, // 33
                    0, // 34
                    0, // 35
                    0, // 36
                    0, // 37
                    0, // 38
                    0, // 39
                    0, // 40
                    0, // 41
                    0, // 42
                    0, // 43
                    0, // 44
                    0, // 45
                    0, // 46
                    0, // 47
                    0, // 48
                    0, // 49
                    0, // 50
                    0, // 51
                    0, // 52
                    0, // 53
                    1, // 54
                    1, // 55
                    1, // 56
                    1, // 57
                    1, // 58
                    0, // 59
                    0, // 60
                    0, // 61
                    0, // 62
                    0, // 63
                    0, // 64
                    0, // 65
                    0, // 66
                    0, // 67
                    0, // 68
                    0, // 69
                    0, // 70
                    0, // 71
                    0, // 72
                    0, // 73
                    0, // 74
                    0, // 75
                    0, // 76
                    0, // 77
                    0, // 78
                    0, // 79
                    0, // 80
                    0, // 81
                    0, // 82
                    0, // 83
                    0, // 84
                    0, // 85
                    0, // 86
                    0, // 87
                    0, // 88
                    0, // 89
                    0, // 90
                    0, // 91
                    0, // 92
                    0, // 93
                    0, // 94
                    0, // 95
                    0, // 96
                    0, // 97
                    0, // 98
                    0, // 99
                    0, // 100
                    0, // 101
                    0, // 102
                    0, // 103
                    0, // 104
                    0, // 105
                    0, // 106
                    0, // 107
                    0, // 108
                    0, // 109
                    0, // 110
                    0, // 111
                    0, // 112
                    0, // 113
                    0, // 114
                    0, // 115
                    0, // 116
                    0, // 117
                    0, // 118
                    0, // 119
                    0, // 120
                    0, // 121
                    0, // 122
                    0, // 123
                    0, // 124
                    0, // 125
                    0, // 126
                    0, // 127
                    0, // 128
                    0, // 129
                    0, // 130
                    0, // 131
                    1, // 132
                    0, // 133
                    0, // 134
                    0, // 135
                    0, // 136
                    0, // 137
                    0, // 138
                    0, // 139
                    0, // 140
                    0, // 141
                    0, // 142
                    0, // 143
                    0, // 144
                    0, // 145
                    0, // 146
                    0, // 147
                    0, // 148
                    0, // 149
                    0, // 150
                    0, // 151
                    0, // 152
                    2, // 153
                    2, // 154
                    2, // 155
                    2, // 156
                    2, // 157
                    2, // 158
                    2, // 159
                    2, // 160
                    2, // 161
                    2, // 162
                    2, // 163
                    2, // 164
                    2, // 165
                    2, // 166
                    2, // 167
                    2, // 168
                    1, // 169
                    4, // 170
                    4, // 171
                    0, // 172
                    0, // 173
                    0, // 174
                    0, // 175
                    0, // 176
                    0, // 177
                    2, // 178
                    2, // 179
                    2, // 180
                    2, // 181
                    2, // 182
                    2, // 183
                    2, // 184
                    2, // 185?
                    2, // 186?
                    2, // 187
                    1, // 188
                    2, // 189
                    0, // 190
                    0, // 191
                    2, // 192
                    2, // 193
                    0, // 194
                    0, // 195
                    2, // 196
                    2, // 197
                    2, // 198
                    2, // 199
                    4, // 200
                    4 // 201
    };

    private static int toUnsignedInt(byte b) {
        return b & 0xff;
    }
}
