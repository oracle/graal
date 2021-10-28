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

import static com.oracle.truffle.espresso.descriptors.Symbol.Signature;

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface MethodTypeConstant extends PoolConstant {

    static MethodTypeConstant create(int descriptorIndex) {
        return new Index(descriptorIndex);
    }

    @Override
    default Tag tag() {
        return Tag.METHODTYPE;
    }

    static StaticObject signatureToMethodType(Symbol<Symbol.Type>[] signature, Klass accessingKlass, boolean failWithBME, Meta meta) {
        Symbol<Symbol.Type> rt = Signatures.returnType(signature);
        int pcount = Signatures.parameterCount(signature, false);

        StaticObject[] ptypes = new StaticObject[pcount];
        StaticObject rtype;
        try {
            for (int i = 0; i < pcount; i++) {
                Symbol<Symbol.Type> paramType = Signatures.parameterType(signature, i);
                ptypes[i] = meta.resolveSymbolAndAccessCheck(paramType, accessingKlass).mirror();
            }
        } catch (EspressoException e) {
            if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, e.getGuestMessage());
            }
            throw e;
        }
        try {
            rtype = meta.resolveSymbolAndAccessCheck(rt, accessingKlass).mirror();
        } catch (EspressoException e) {
            EspressoException rethrow = e;
            if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                rethrow = EspressoException.wrap(Meta.initExceptionWithMessage(meta.java_lang_NoClassDefFoundError, e.getGuestMessage()), meta);
            }
            if (failWithBME) {
                rethrow = EspressoException.wrap(Meta.initExceptionWithCause(meta.java_lang_BootstrapMethodError, rethrow.getExceptionObject()), meta);
            }
            throw rethrow;
        }

        return (StaticObject) meta.java_lang_invoke_MethodHandleNatives_findMethodHandleType.invokeDirect(null, rtype, StaticObject.createArray(meta.java_lang_Class_array, ptypes));
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
            return pool.symbolAt(descriptorIndex);
        }

        @Override
        public Resolved resolve(RuntimeConstantPool pool, int index, Klass accessingKlass) {
            Symbol<Signature> sig = getSignature(pool);
            Meta meta = accessingKlass.getContext().getMeta();
            return new Resolved(signatureToMethodType(meta.getSignatures().parsed(sig), accessingKlass, false, meta));
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.utf8At(descriptorIndex).validateSignature();
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(descriptorIndex);
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

        @Override
        public Object value() {
            return resolved;
        }
    }
}
