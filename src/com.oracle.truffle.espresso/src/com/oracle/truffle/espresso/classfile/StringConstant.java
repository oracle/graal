/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Constant;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface StringConstant extends PoolConstant {

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
    Symbol<Constant> getSymbol(ConstantPool pool);

    final class Index implements StringConstant, Resolvable {
        private final int utf8Index;

        @Override
        public Symbol<Constant> getSymbol(ConstantPool pool) {
            return pool.utf8At(utf8Index);
        }

        Index(int utf8Index) {
            this.utf8Index = utf8Index;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return new Resolved(pool.getContext().getStrings().intern(getSymbol(pool)));
        }

        public boolean checkValidity(ConstantPool pool) {
            return pool.at(utf8Index).tag() == Tag.UTF8;
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
        public Symbol<Constant> getSymbol(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("String already resolved");
        }
    }

    final class PreResolved implements StringConstant, Resolvable {
        private final StaticObject resolved;

        @Override
        public Symbol<Constant> getSymbol(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("String already Pre-Resolved");
        }

        PreResolved(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return new Resolved(resolved);
        }
    }
}
