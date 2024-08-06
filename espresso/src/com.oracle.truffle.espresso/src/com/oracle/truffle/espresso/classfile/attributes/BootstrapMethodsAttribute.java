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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class BootstrapMethodsAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.BootstrapMethods;

    public Entry[] getEntries() {
        return entries;
    }

    public static final class Entry {
        final char bootstrapMethodRef;
        @CompilerDirectives.CompilationFinal(dimensions = 1) final char[] bootstrapArguments;

        public int numBootstrapArguments() {
            return bootstrapArguments.length;
        }

        public Entry(char bootstrapMethodRef, char[] bootstrapArguments) {
            this.bootstrapMethodRef = bootstrapMethodRef;
            this.bootstrapArguments = bootstrapArguments;
        }

        public char argAt(int index) {
            return bootstrapArguments[index];
        }

        public char getBootstrapMethodRef() {
            return bootstrapMethodRef;
        }

        public StaticObject getMethodHandle(ObjectKlass accessingKlass, RuntimeConstantPool pool) {
            return pool.resolvedMethodHandleAt(accessingKlass, getBootstrapMethodRef());
        }

        public StaticObject[] getStaticArguments(ObjectKlass accessingKlass, RuntimeConstantPool pool) {
            Meta meta = accessingKlass.getMeta();
            StaticObject[] args = new StaticObject[numBootstrapArguments()];
            // @formatter:off
            for (int i = 0; i < numBootstrapArguments(); i++) {
                PoolConstant pc = pool.at(argAt(i));
                switch (pc.tag()) {
                    case METHODHANDLE:
                        args[i] = pool.resolvedMethodHandleAt(accessingKlass, argAt(i));
                        break;
                    case METHODTYPE:
                        args[i] = pool.resolvedMethodTypeAt(accessingKlass, argAt(i));
                        break;
                    case DYNAMIC:
                        args[i] = pool.resolvedDynamicConstantAt(accessingKlass, argAt(i)).guestBoxedValue(meta);
                        break;
                    case CLASS:
                        args[i] = pool.resolvedKlassAt(accessingKlass, argAt(i)).mirror();
                        break;
                    case STRING:
                        args[i] = pool.resolvedStringAt(argAt(i));
                        break;
                    case INTEGER:
                        args[i] = meta.boxInteger(pool.intAt(argAt(i)));
                        break;
                    case LONG:
                        args[i] = meta.boxLong(pool.longAt(argAt(i)));
                        break;
                    case DOUBLE:
                        args[i] = meta.boxDouble(pool.doubleAt(argAt(i)));
                        break;
                    case FLOAT:
                        args[i] = meta.boxFloat(pool.floatAt(argAt(i)));
                        break;
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.shouldNotReachHere();
                }
            }
            return args;
            // @formatter:on
        }
    }

    private final Entry[] entries;

    public BootstrapMethodsAttribute(Symbol<Name> name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }

    public Entry at(int index) {
        return entries[index];
    }
}
