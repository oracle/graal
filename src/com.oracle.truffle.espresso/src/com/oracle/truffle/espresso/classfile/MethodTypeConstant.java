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

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.UTF8;
import static com.oracle.truffle.espresso.descriptors.Symbol.Signature;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface MethodTypeConstant extends PoolConstant {

    default Tag tag() {
        return Tag.METHODTYPE;
    }

    /**
     * Gets the signature of this method type constant.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<Signature> getSignature(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return getSignature(pool).toString();
    }

    final class Index implements MethodTypeConstant, Resolvable {
        private final char descriptorIndex;

        Index(int descriptorIndex) {
            this.descriptorIndex = PoolConstant.u2(descriptorIndex);
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            // TODO(peterssen): Assert valid signature.
            return pool.utf8At(descriptorIndex);
        }

        public Resolved resolve(RuntimeConstantPool pool, int index, Klass accessingKlass) {
            Symbol<Signature> sig = getSignature(pool);
            Meta meta = accessingKlass.getContext().getMeta();
            return new Resolved(BytecodeNode.signatureToMethodType(meta.getSignatures().parsed(sig), accessingKlass, meta));
        }

        @Override
        public void checkValidity(ConstantPool pool) {
            if (pool.at(descriptorIndex).tag() != UTF8) {
                throw new VerifyError("Invalid pool constant: " + tag());
            }
            pool.at(descriptorIndex).checkValidity(pool);
        }
    }

    final class Resolved implements MethodTypeConstant, Resolvable.ResolvedConstant {
        private final StaticObject resolved;

        Resolved(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            // TODO(peterssen): Assert valid signature.
            throw EspressoError.shouldNotReachHere("Method type already resolved !");
        }

        public Object value() {
            return resolved;
        }
    }
}
