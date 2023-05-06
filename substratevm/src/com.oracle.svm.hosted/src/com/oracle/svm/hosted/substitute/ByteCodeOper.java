/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.hosted.substitute;

import com.oracle.svm.core.util.UserError;
import jdk.vm.ci.meta.JavaKind;

import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.DCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.DLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.DRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.DSTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.FCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.FLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.FRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.FSTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.IRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ISTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.LSTORE;

public abstract class ByteCodeOper {

    private static final LoadOper LOAD_OPER = new LoadOper();
    private static final StoreOper STORE_OPER = new StoreOper();
    private static final ReturnOper RETURN_OPER = new ReturnOper();
    private static final DefaultOper DEFAULT_OPER = new DefaultOper();

    public static int getLoadOp(Class<?> type) {
        return LOAD_OPER.getOpCodeByType(type);
    }

    public static int getStoreOp(Class<?> type) {
        return STORE_OPER.getOpCodeByType(type);
    }

    public static int getReturnOp(Class<?> type) {
        return RETURN_OPER.getOpCodeByType(type);
    }

    public static int getDefaultOp(Class<?> type) {
        return DEFAULT_OPER.getOpCodeByType(type);
    }

    public int getOpCodeByType(Class<?> type) {
        int opCode;
        switch (JavaKind.fromJavaClass(type)) {
            case Byte:
            case Char:
            case Boolean:
            case Short:
            case Int:
                opCode = getIntOp();
                break;
            case Long:
                opCode = getLongOp();
                break;
            case Float:
                opCode = getFloatOp();
                break;
            case Double:
                opCode = getDoubleOp();
                break;
            case Object:
                opCode = getObjectOp();
                break;
            default:
                throw UserError.abort("Can't operate on void type");
        }
        return opCode;
    }

    protected abstract int getIntOp();

    protected abstract int getLongOp();

    protected abstract int getFloatOp();

    protected abstract int getDoubleOp();

    protected abstract int getObjectOp();

    public static class LoadOper extends ByteCodeOper {

        @Override
        protected int getIntOp() {
            return ILOAD;
        }

        @Override
        protected int getLongOp() {
            return LLOAD;
        }

        @Override
        protected int getFloatOp() {
            return FLOAD;
        }

        @Override
        protected int getDoubleOp() {
            return DLOAD;
        }

        @Override
        protected int getObjectOp() {
            return ALOAD;
        }
    }

    public static class StoreOper extends ByteCodeOper {

        @Override
        protected int getIntOp() {
            return ISTORE;
        }

        @Override
        protected int getLongOp() {
            return LSTORE;
        }

        @Override
        protected int getFloatOp() {
            return FSTORE;
        }

        @Override
        protected int getDoubleOp() {
            return DSTORE;
        }

        @Override
        protected int getObjectOp() {
            return ASTORE;
        }
    }

    public static class ReturnOper extends ByteCodeOper {

        @Override
        protected int getIntOp() {
            return IRETURN;
        }

        @Override
        protected int getLongOp() {
            return LRETURN;
        }

        @Override
        protected int getFloatOp() {
            return FRETURN;
        }

        @Override
        protected int getDoubleOp() {
            return DRETURN;
        }

        @Override
        protected int getObjectOp() {
            return ARETURN;
        }
    }

    public static class DefaultOper extends ByteCodeOper {

        @Override
        protected int getIntOp() {
            return ICONST_0;
        }

        @Override
        protected int getLongOp() {
            return LCONST_0;
        }

        @Override
        protected int getFloatOp() {
            return FCONST_0;
        }

        @Override
        protected int getDoubleOp() {
            return DCONST_0;
        }

        @Override
        protected int getObjectOp() {
            return ACONST_NULL;
        }
    }
}
