/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public interface DynamicConstant extends PoolConstant {

    static DynamicConstant create(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        return new Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.DYNAMIC;
    }

    Symbol<Type> getTypeSymbol(ConstantPool pool);

    final class Indexes extends BootstrapMethodConstant.Indexes implements DynamicConstant, Resolvable {
        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        @Override
        public Symbol<Type> getTypeSymbol(ConstantPool pool) {
            return Types.fromSymbol(pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool));
        }

        private static Resolved makeResolved(Klass type, StaticObject result) {
            switch (type.getJavaKind()) {
                case Boolean:
                case Byte:
                case Short:
                case Char: {
                    int value = (int) MHLinkToNode.rebasic(type.getMeta().unboxGuest(result), type.getJavaKind());
                    return new ResolvedInt(value);
                }
                case Int: {
                    int value = type.getMeta().unboxInteger(result);
                    return new ResolvedInt(value);
                }
                case Float: {
                    float value = type.getMeta().unboxFloat(result);
                    return new ResolvedFloat(value);
                }
                case Long: {
                    long value = type.getMeta().unboxLong(result);
                    return new ResolvedLong(value);
                }
                case Double: {
                    double value = type.getMeta().unboxDouble(result);
                    return new ResolvedDouble(value);
                }
                case Object:
                    return new ResolvedObject(result);
            }
            throw EspressoError.shouldNotReachHere();
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.nameAndTypeAt(nameAndTypeIndex).validateField(pool);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(bootstrapMethodAttrIndex);
            buf.putChar(nameAndTypeIndex);
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, ObjectKlass accessingKlass) {
            Meta meta = accessingKlass.getMeta();

            // Condy constant resolving.
            BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) accessingKlass.getAttribute(BootstrapMethodsAttribute.NAME);

            assert (bms != null);
            // TODO(garcia) cache bootstrap method resolution
            // Bootstrap method resolution
            try {
                BootstrapMethodsAttribute.Entry bsEntry = bms.at(getBootstrapMethodAttrIndex());

                StaticObject bootstrapmethodMethodHandle = bsEntry.getMethodHandle(accessingKlass, pool);
                StaticObject[] args = bsEntry.getStaticArguments(accessingKlass, pool);

                StaticObject fieldName = meta.toGuestString(getName(pool));
                Klass fieldType = meta.resolveSymbolOrFail(getTypeSymbol(pool),
                                accessingKlass.getDefiningClassLoader(),
                                accessingKlass.protectionDomain());

                Object result = null;
                if (!meta.getJavaVersion().java19OrLater()) {
                    result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    thisIndex,
                                    bootstrapmethodMethodHandle,
                                    fieldName, fieldType.mirror(),
                                    StaticObject.wrap(args, meta));
                } else {
                    result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirect(
                                    null,
                                    accessingKlass.mirror(),
                                    bootstrapmethodMethodHandle,
                                    fieldName, fieldType.mirror(),
                                    StaticObject.wrap(args, meta));
                }
                try {
                    return makeResolved(fieldType, (StaticObject) result);
                } catch (ClassCastException | NullPointerException e) {
                    throw meta.throwException(meta.java_lang_BootstrapMethodError);
                } catch (EspressoException e) {
                    if (meta.java_lang_NullPointerException.isAssignableFrom(e.getGuestException().getKlass()) ||
                                    meta.java_lang_ClassCastException.isAssignableFrom(e.getGuestException().getKlass())) {
                        throw meta.throwExceptionWithCause(meta.java_lang_BootstrapMethodError, e.getGuestException());
                    }
                    throw e;
                }
            } catch (EspressoException e) {
                return new ResolvedFail(e);
            }
        }
    }

    interface Resolved extends DynamicConstant, Resolvable.ResolvedConstant {
        void putResolved(VirtualFrame frame, int top, BytecodeNode node);

        @Override
        default Symbol<Type> getTypeSymbol(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Getting type symbol of a resolved dynamic constant");
        }

        default StaticObject guestBoxedValue(Meta meta) {
            Object value = value();
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            return Meta.box(meta, value);
        }

        default void checkFail() {
        }
    }

    final class ResolvedObject implements Resolved {
        final StaticObject resolved;

        public ResolvedObject(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            EspressoFrame.putObject(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedInt implements Resolved {
        final int resolved;

        public ResolvedInt(int resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            EspressoFrame.putInt(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedLong implements Resolved {
        final long resolved;

        public ResolvedLong(long resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            EspressoFrame.putLong(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedDouble implements Resolved {
        final double resolved;

        public ResolvedDouble(double resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            EspressoFrame.putDouble(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedFloat implements Resolved {
        final float resolved;

        public ResolvedFloat(float resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            EspressoFrame.putFloat(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedFail implements Resolved {
        final EspressoException failure;

        public ResolvedFail(EspressoException failure) {
            this.failure = failure;
        }

        @Override
        public void checkFail() {
            throw failure;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Failure should have arose earlier.");
        }

        @Override
        public Object value() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Failure should have arose earlier.");
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + failure + ")";
        }
    }
}
