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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface InvokeDynamicConstant extends BootstrapMethodConstant {

    static InvokeDynamicConstant create(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        return new Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.INVOKEDYNAMIC;
    }

    default boolean isResolved() {
        return false;
    }

    CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex);

    final class Indexes extends BootstrapMethodConstant.Indexes implements InvokeDynamicConstant, Resolvable.ResolvedConstant, Resolvable {
        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        @Override
        public CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex) {
            CompilerAsserts.neverPartOfCompilation();
            Meta meta = accessingKlass.getMeta();

            // Indy constant resolving.
            BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) ((ObjectKlass) accessingKlass).getAttribute(BootstrapMethodsAttribute.NAME);

            assert (bms != null);
            // TODO(garcia) cache bootstrap method resolution
            // Bootstrap method resolution
            try {
                BootstrapMethodsAttribute.Entry bsEntry = bms.at(getBootstrapMethodAttrIndex());

                StaticObject bootstrapmethodMethodHandle = bsEntry.getMethodHandle(accessingKlass, pool);
                StaticObject[] args = bsEntry.getStaticArguments(accessingKlass, pool);

                // Preparing Bootstrap call.
                StaticObject name = meta.toGuestString(getName(pool));
                Symbol<Signature> invokeSignature = getSignature(pool);
                Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
                StaticObject methodType = MethodTypeConstant.signatureToMethodType(parsedInvokeSignature, accessingKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
                StaticObject appendix = StaticObject.createArray(meta.java_lang_Object_array, new StaticObject[1]);
                StaticObject memberName;
                if (meta.getJavaVersion().varHandlesEnabled()) {
                    memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    thisIndex,
                                    bootstrapmethodMethodHandle,
                                    name, methodType,
                                    StaticObject.createArray(meta.java_lang_Object_array, args),
                                    appendix);
                } else {
                    memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    bootstrapmethodMethodHandle,
                                    name, methodType,
                                    StaticObject.createArray(meta.java_lang_Object_array, args),
                                    appendix);
                }
                StaticObject unboxedAppendix = appendix.get(0);

                return new CallSiteLink(memberName, unboxedAppendix, parsedInvokeSignature);
            } catch (EspressoException e) {
                throw new CallSiteLinkingFailure(e);
            }
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(bootstrapMethodAttrIndex);
            buf.putChar(nameAndTypeIndex);
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return this;
        }

        @Override
        public Object value() {
            throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
        }

        @Override
        public String toString(ConstantPool pool) {
            return "bsmIndex:" + getBootstrapMethodAttrIndex() + " " + getSignature(pool);
        }
    }

    final class CallSiteLink {
        final StaticObject memberName;
        final StaticObject unboxedAppendix;
        @CompilerDirectives.CompilationFinal(dimensions = 1) final Symbol<Type>[] parsedSignature;

        public CallSiteLink(StaticObject memberName, StaticObject unboxedAppendix, Symbol<Type>[] parsedSignature) {
            this.memberName = memberName;
            this.unboxedAppendix = unboxedAppendix;
            this.parsedSignature = parsedSignature;
        }

        public StaticObject getMemberName() {
            return memberName;
        }

        public StaticObject getUnboxedAppendix() {
            return unboxedAppendix;
        }

        public Symbol<Type>[] getParsedSignature() {
            return parsedSignature;
        }
    }

    final class CallSiteLinkingFailure extends RuntimeException {
        private static final long serialVersionUID = 2567495832103023693L;

        public EspressoException cause;

        public CallSiteLinkingFailure(EspressoException cause) {
            this.cause = cause;
        }

        public Fail failConstant() {
            return new Fail(cause);
        }

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    final class Fail implements InvokeDynamicConstant, Resolvable.ResolvedConstant {
        private final EspressoException failure;

        public Fail(EspressoException failure) {
            this.failure = failure;
        }

        @Override
        public CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex) {
            throw failure;
        }

        @Override
        public Object value() {
            throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
        }

        @Override
        public int getBootstrapMethodAttrIndex() {
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public Symbol<Symbol.Name> getName(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public void dump(ByteBuffer buf) {
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }
    }
}
