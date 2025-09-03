/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public final class CodeAttribute extends Attribute {
    public static final Symbol<Name> NAME = ParserNames.Code;

    private final char maxStack;
    private final char maxLocals;

    @CompilationFinal(dimensions = 1) //
    private final byte[] originalCode; // no bytecode patching

    @CompilationFinal(dimensions = 1) //
    private final ExceptionHandler[] exceptionHandlerEntries;

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public CodeAttribute(Symbol<Name> name, int maxStack, int maxLocals, byte[] code, ExceptionHandler[] exceptionHandlerEntries, Attribute[] attributes) {
        assert name == NAME;
        this.maxStack = (char) maxStack;
        this.maxLocals = (char) maxLocals;
        this.originalCode = code;
        this.exceptionHandlerEntries = exceptionHandlerEntries;
        this.attributes = attributes;
    }

    public CodeAttribute(CodeAttribute copy) {
        this(copy.getName(), copy.getMaxStack(), copy.getMaxLocals(), copy.getOriginalCode(), copy.getExceptionHandlers(), copy.attributes);
    }

    public int getMaxStack() {
        return maxStack;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public byte[] getOriginalCode() {
        return originalCode;
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlerEntries;
    }

    public StackMapTableAttribute getStackMapFrame() {
        for (Attribute attr : attributes) {
            if (attr.getName() == ParserNames.StackMapTable) {
                return (StackMapTableAttribute) attr;
            }
        }
        return null;
    }

    public LineNumberTableAttribute getLineNumberTableAttribute() {
        for (Attribute attr : attributes) {
            if (attr.getName() == ParserNames.LineNumberTable) {
                return (LineNumberTableAttribute) attr;
            }
        }
        return LineNumberTableAttribute.EMPTY;
    }

    public LocalVariableTable getLocalvariableTable() {
        for (Attribute attr : attributes) {
            if (attr.getName() == ParserNames.LocalVariableTable) {
                return (LocalVariableTable) attr;
            }
        }
        return LocalVariableTable.EMPTY_LVT;
    }

    public LocalVariableTable getLocalvariableTypeTable() {
        for (Attribute attr : attributes) {
            if (attr.getName() == ParserNames.LocalVariableTypeTable) {
                return (LocalVariableTable) attr;
            }
        }
        return LocalVariableTable.EMPTY_LVTT;
    }

    public int bciToLineNumber(int bci) {
        LineNumberTableAttribute lnt = getLineNumberTableAttribute();
        if (lnt == LineNumberTableAttribute.EMPTY) {
            return -1;
        }
        return lnt.getLineNumber(bci);
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }
}
