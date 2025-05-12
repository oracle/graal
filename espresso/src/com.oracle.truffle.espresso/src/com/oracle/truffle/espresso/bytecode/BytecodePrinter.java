/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.bytecode;

import java.io.PrintStream;
import java.util.Arrays;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;

public class BytecodePrinter {

    public static void printBytecode(Klass klass, PrintStream out, byte[] code) {
        BytecodeStream stream = new BytecodeStream(code);
        try {
            ConstantPool pool = klass.getConstantPool();
            int bci = 0;
            int nextBCI = 0;
            StringBuilder str = new StringBuilder();
            while (nextBCI < stream.endBCI()) {
                str.setLength(0);
                bci = nextBCI;
                int opcode = stream.currentBC(bci);
                str.append(bci).append(": ").append(Bytecodes.nameOf(opcode)).append(" ");
                nextBCI = stream.nextBCI(bci);
                if (Bytecodes.isBranch(opcode)) {
                    // {bci}: {branch bytecode} {target}
                    str.append(stream.readBranchDest(bci));
                } else if (opcode == Bytecodes.NEW) {
                    // {bci}: new {class name}
                    int cpi = stream.readCPI(bci);
                    Symbol<Name> className = pool.className(cpi);
                    str.append(className);
                } else if (opcode == Bytecodes.INVOKEDYNAMIC) {
                    // {bci}: #{bootstrap method index} -> {name}:{signature}
                    int cpi = stream.readCPI(bci);
                    str.append(pool.toString(cpi));
                } else if (Bytecodes.isInvoke(opcode)) {
                    // {bci}: invoke{} {class}.{method name}:{method signature}
                    int cpi = stream.readCPI(bci);
                    Symbol<Name> holderClassName = pool.memberClassName(cpi);
                    Symbol<Name> methodName = pool.methodName(cpi);
                    Symbol<Signature> methodSignature = pool.methodSignature(cpi);
                    str.append(holderClassName).append(".").append(methodName).append(":").append(methodSignature);
                } else if (opcode == Bytecodes.TABLESWITCH) {
                    // @formatter:off
                    // checkstyle: stop

                    // {bci}: tableswitch
                    //      {key1}: {target1}
                    //      ...
                    //      {keyN}: {targetN}

                    // @formatter:on
                    // Checkstyle: resume
                    str.append('\n');
                    BytecodeTableSwitch helper = BytecodeTableSwitch.INSTANCE;
                    int low = helper.lowKey(stream, bci);
                    int high = helper.highKey(stream, bci);
                    for (int i = low; i != high + 1; i++) {
                        str.append('\t').append(i).append(": ").append(helper.targetAt(stream, bci, i)).append('\n');
                    }
                    str.append("\tdefault: ").append(helper.defaultTarget(stream, bci));
                } else if (opcode == Bytecodes.LOOKUPSWITCH) {
                    // @formatter:off
                    // checkstyle: stop

                    // {bci}: lookupswitch
                    //      {key1}: {target1}
                    //      ...
                    //      {keyN}: {targetN}

                    // @formatter:on
                    // Checkstyle: resume
                    str.append('\n');
                    BytecodeLookupSwitch helper = BytecodeLookupSwitch.INSTANCE;
                    int low = 0;
                    int high = helper.numberOfCases(stream, bci) - 1;
                    for (int i = low; i <= high; i++) {
                        str.append('\t').append(helper.keyAt(stream, bci, i)).append(": ").append(helper.targetAt(stream, bci, i));
                    }
                    str.append("\tdefault: ").append(helper.defaultTarget(stream, bci));
                } else if (opcode == Bytecodes.IINC) {
                    str.append(" ").append(stream.readLocalIndex(bci)).append(" ").append(stream.readIncrement(bci));
                } else {
                    // {bci}: {opcode} {corresponding value}
                    if (nextBCI - bci == 2) {
                        str.append(stream.readUByte(bci + 1));
                    }
                    if (nextBCI - bci == 3) {
                        str.append(stream.readShort(bci));
                    }
                    if (nextBCI - bci == 5) {
                        str.append(stream.readInt(bci + 1));
                    }
                }
                out.println(str.toString());
            }
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere("Exception thrown during bytecode printing, aborting...", e);
        }
    }

    public static void printRawBytecode(PrintStream out, byte[] code) {
        out.println(Arrays.toString(code));
    }

    public static void print(Klass klass, byte[] code, PrintStream out) {
        try {
            printBytecode(klass, out, code);
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere("Throw during printing. Aborting...", e);
        }
    }
}
