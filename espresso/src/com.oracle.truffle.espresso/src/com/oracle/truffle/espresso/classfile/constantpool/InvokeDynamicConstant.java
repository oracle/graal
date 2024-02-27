/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public interface InvokeDynamicConstant extends BootstrapMethodConstant {

    static InvokeDynamicConstant create(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        return new Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.INVOKEDYNAMIC;
    }

    Symbol<Signature> getSignature(ConstantPool pool);

    default boolean isResolved() {
        return false;
    }

    CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex);

    final class Indexes extends BootstrapMethodConstant.Indexes implements InvokeDynamicConstant, Resolvable {
        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(bootstrapMethodAttrIndex);
            buf.putChar(nameAndTypeIndex);
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.nameAndTypeAt(nameAndTypeIndex).validateMethod(pool, false);
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            return Signatures.check(pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool));
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            CompilerAsserts.neverPartOfCompilation();
            BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) ((ObjectKlass) accessingKlass).getAttribute(BootstrapMethodsAttribute.NAME);
            BootstrapMethodsAttribute.Entry bsEntry = bms.at(getBootstrapMethodAttrIndex());

            Meta meta = accessingKlass.getMeta();
            Symbol<Signature> invokeSignature = getSignature(pool);
            Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
            return new Resolved(bsEntry, parsedInvokeSignature, getName(pool));
        }

        @Override
        public String toString(ConstantPool pool) {
            return "bsmIndex:" + getBootstrapMethodAttrIndex() + " " + getSignature(pool);
        }

        @Override
        public CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex) {
            throw EspressoError.shouldNotReachHere("Not resolved yet");
        }
    }

    final class Resolved implements InvokeDynamicConstant, Resolvable.ResolvedConstant {
        private final BootstrapMethodsAttribute.Entry bootstrapMethod;
        private final Symbol<Type>[] parsedInvokeSignature;
        private final Symbol<Name> nameSymbol;

        public Resolved(BootstrapMethodsAttribute.Entry bootstrapMethod, Symbol<Type>[] parsedInvokeSignature, Symbol<Name> name) {
            this.bootstrapMethod = bootstrapMethod;
            this.parsedInvokeSignature = parsedInvokeSignature;
            this.nameSymbol = name;
        }

        @Override
        public Object value() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
        }

        @Override
        public boolean isResolved() {
            return true;
        }

        /**
         * The call site linking operation must be executed once per {@code invokedynamic}
         * instruction, rather than once per {@code invokedynamic_constant}.
         * <p>
         * Furthermore, a previously failed call site linking from the constant pool must
         * immediately fail again on subsequent linking operations, independently of the
         * invokedynamic instruction involved.
         * <p>
         * For example:
         * 
         * <pre>
         * method1:
         *     0: invokedynamic #0 // calls MHN.linkCallSite successfully
         *     4: invokedynamic #0 // Also calls MHM.linkCallSite, resulting in a *different* CallSite
         *     
         * method2:
         *     0: invokedynamic #1 // Fails during MHN.linkCallSite upcall
         *     4: invokedynamic #1 // Immediately fails without calling MHN.linkCallSite
         * </pre>
         * 
         * @see RuntimeConstantPool#linkInvokeDynamic(Klass, int)
         */
        @Override
        public CallSiteLink link(RuntimeConstantPool pool, Klass accessingKlass, int thisIndex) {
            // Per-callsite linking
            CompilerAsserts.neverPartOfCompilation();
            Meta meta = accessingKlass.getMeta();
            try {
                StaticObject bootstrapmethodMethodHandle = bootstrapMethod.getMethodHandle(accessingKlass, pool);
                StaticObject[] args = bootstrapMethod.getStaticArguments(accessingKlass, pool);

                StaticObject name = meta.toGuestString(nameSymbol);
                StaticObject methodType = MethodTypeConstant.signatureToMethodType(parsedInvokeSignature, accessingKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
                /*
                 * the 4 objects resolved above are not actually call-site specific. We don't cache
                 * them to minimize footprint since most indy constant are only used by one
                 * call-site
                 */

                StaticObject appendix = StaticObject.createArray(meta.java_lang_Object_array, new StaticObject[1], meta.getContext());
                StaticObject memberName;
                if (meta.getJavaVersion().varHandlesEnabled() && !meta.getJavaVersion().java19OrLater()) {
                    memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    thisIndex,
                                    bootstrapmethodMethodHandle,
                                    name, methodType,
                                    StaticObject.createArray(meta.java_lang_Object_array, args.clone(), meta.getContext()),
                                    appendix);
                } else {
                    memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    bootstrapmethodMethodHandle,
                                    name, methodType,
                                    StaticObject.createArray(meta.java_lang_Object_array, args.clone(), meta.getContext()),
                                    appendix);
                }
                StaticObject unboxedAppendix = appendix.get(meta.getLanguage(), 0);

                return new CallSiteLink(memberName, unboxedAppendix, parsedInvokeSignature);
            } catch (EspressoException e) {
                throw new CallSiteLinkingFailure(e);
            }
        }

        @Override
        public int getBootstrapMethodAttrIndex() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("String already resolved");
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("String already resolved");
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("String already resolved");
        }

        @Override
        public NameAndTypeConstant getNameAndType(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("String already resolved");
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
        }

        @Override
        public int getBootstrapMethodAttrIndex() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public NameAndTypeConstant getNameAndType(ConstantPool pool) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public void dump(ByteBuffer buf) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Invoke dynamic already resolved.");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }
}
