/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.constantpool;

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface StringConstant extends PoolConstant {

    static StringConstant create(int utf8Index) {
        return new Index(utf8Index);
    }

    static StringConstant preResolved(StaticObject resolved) {
        return new PreResolved(resolved);
    }

    @Override
    default Tag tag() {
        return Tag.STRING;
    }

    @Override
    default String toString(ConstantPool pool) {
        return getSymbol(pool).toString();
    }

    /**
     * Gets the name of this name+descriptor pair constant.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<ModifiedUTF8> getSymbol(ConstantPool pool);

    final class Index implements StringConstant, Resolvable {
        private final char utf8Index;

        @Override
        public Symbol<ModifiedUTF8> getSymbol(ConstantPool pool) {
            return pool.symbolAt(utf8Index);
        }

        Index(int utf8Index) {
            this.utf8Index = PoolConstant.u2(utf8Index);
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return new Resolved(pool.getContext().getStrings().intern(getSymbol(pool)));
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.utf8At(utf8Index).validateUTF8();
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(utf8Index);
        }
    }

    final class Resolved implements StringConstant, Resolvable.ResolvedConstant {

        private final StaticObject resolved;

        Resolved(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public StaticObject value() {
            return resolved;
        }

        @Override
        public Symbol<ModifiedUTF8> getSymbol(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("String already resolved");
        }
    }

    final class PreResolved implements StringConstant, Resolvable {
        private final StaticObject resolved;

        @Override
        public Symbol<ModifiedUTF8> getSymbol(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("String already Pre-Resolved");
        }

        PreResolved(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return new Resolved(resolved);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar((char) 0);
        }
    }
}
