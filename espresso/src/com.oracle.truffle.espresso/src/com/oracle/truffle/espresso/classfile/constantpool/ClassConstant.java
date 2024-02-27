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
import java.util.Objects;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;

/**
 * Interface denoting a class entry in a constant pool.
 */
public interface ClassConstant extends PoolConstant {

    /* static final */ DebugCounter CLASS_RESOLVE_COUNT = DebugCounter.create("ClassConstant.resolve calls");

    static ClassConstant create(int classNameIndex) {
        return new Index(classNameIndex);
    }

    static ClassConstant preResolved(Klass klass) {
        return new PreResolved(klass);
    }

    static ClassConstant withString(Symbol<Name> name) {
        return new WithString(name);
    }

    static Resolvable.ResolvedConstant resolved(Klass klass) {
        return new Resolved(klass);
    }

    @Override
    default Tag tag() {
        return Tag.CLASS;
    }

    /**
     * Gets the type descriptor of the class represented by this constant.
     *
     * @param pool container of this constant
     */
    Symbol<Name> getName(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return getName(pool).toString();
    }

    final class Index implements ClassConstant, Resolvable {
        private final char classNameIndex;

        Index(int classNameIndex) {
            this.classNameIndex = PoolConstant.u2(classNameIndex);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return pool.symbolAt(classNameIndex);
        }

        /**
         * <h3>5.4.3.1. Class and Interface Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to a class or interface C denoted by
         * N, the following steps are performed:
         * <ol>
         * <li>The defining class loader of D is used to create a class or interface denoted by N.
         * This class or interface is C. The details of the process are given in &sect;5.3. <b>Any
         * exception that can be thrown as a result of failure of class or interface creation can
         * thus be thrown as a result of failure of class and interface resolution.</b>
         * <li>If C is an array class and its element type is a reference type, then a symbolic
         * reference to the class or interface representing the element type is resolved by invoking
         * the algorithm in &sect;5.4.3.1 recursively.
         * <li>Finally, access permissions to C are checked.
         * <ul>
         * <li><b>If C is not accessible (&sect;5.4.4) to D, class or interface resolution throws an
         * IllegalAccessError.</b> This condition can occur, for example, if C is a class that was
         * originally declared to be public but was changed to be non-public after D was compiled.
         * </ul>
         * </ol>
         * If steps 1 and 2 succeed but step 3 fails, C is still valid and usable. Nevertheless,
         * resolution fails, and D is prohibited from accessing C.
         */
        @SuppressWarnings("try")
        @Override
        public Resolved resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            try (EspressoLanguage.DisableSingleStepping ignored = accessingKlass.getLanguage().disableStepping()) {
                CLASS_RESOLVE_COUNT.inc();
                assert accessingKlass != null;
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Symbol<Name> klassName = getName(pool);
                try {
                    EspressoContext context = pool.getContext();
                    Symbol<Symbol.Type> type = context.getTypes().fromName(klassName);
                    Klass klass = context.getMeta().resolveSymbolOrFail(type, accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
                    if (!Klass.checkAccess(klass.getElementalType(), accessingKlass, false)) {
                        Meta meta = context.getMeta();
                        context.getLogger().log(Level.FINE,
                                        "Access check of: " + klass.getType() + " from " + accessingKlass.getType() + " throws IllegalAccessError");
                        throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, meta.toGuestString(klassName));
                    }

                    return new Resolved(klass);

                } catch (EspressoException e) {
                    CompilerDirectives.transferToInterpreter();
                    Meta meta = pool.getContext().getMeta();
                    if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getGuestException().getKlass())) {
                        throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, meta.toGuestString(klassName));
                    }
                    throw e;
                } catch (VirtualMachineError e) {
                    // Comment from Hotspot:
                    // Just throw the exception and don't prevent these classes from
                    // being loaded for virtual machine errors like StackOverflow
                    // and OutOfMemoryError, etc.
                    // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
                    throw e;
                }
            }
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.utf8At(classNameIndex).validateClassName();
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(classNameIndex);
        }
    }

    final class Resolved implements ClassConstant, Resolvable.ResolvedConstant {
        private final Klass resolved;

        Resolved(Klass resolved) {
            this.resolved = Objects.requireNonNull(resolved);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getName();
        }

        @Override
        public Klass value() {
            return resolved;
        }
    }

    final class WithString implements ClassConstant, Resolvable {
        private final Symbol<Name> name;

        WithString(Symbol<Name> name) {
            this.name = name;
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return name;
        }

        @Override
        public Resolved resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            CLASS_RESOLVE_COUNT.inc();
            assert accessingKlass != null;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Symbol<Name> klassName = getName(pool);
            try {
                EspressoContext context = pool.getContext();
                Meta meta = context.getMeta();
                Klass klass = meta.resolveSymbolOrFail(context.getTypes().fromName(klassName), accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
                if (!Klass.checkAccess(klass.getElementalType(), accessingKlass, false)) {
                    context.getLogger().log(Level.FINE,
                                    "Access check of: " + klass.getType() + " from " + accessingKlass.getType() + " throws IllegalAccessError");
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, meta.toGuestString(klassName));
                }

                return new Resolved(klass);

            } catch (VirtualMachineError e) {
                // Comment from Hotspot:
                // Just throw the exception and don't prevent these classes from
                // being loaded for virtual machine errors like StackOverflow
                // and OutOfMemoryError, etc.
                // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
                throw e;
            }
        }

        @Override
        public void validate(ConstantPool pool) {
            // No UTF8 entry: cannot cache validation.
            if (!Validation.validModifiedUTF8(name) || !Validation.validClassNameEntry(name)) {
                throw ConstantPool.classFormatError("Invalid class name entry: " + name);
            }
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar((char) 0);
        }
    }

    /**
     * Constant Pool patching inserts already resolved constants in the constant pool. However, at
     * the time of patching, we do not have a Runtime CP. Therefore, we help the CP by inserting a
     * Pre-Resolved constant.
     *
     * This is also used to Pre-resolve anonymous classes.
     */
    final class PreResolved implements ClassConstant, Resolvable {
        private final Klass resolved;

        PreResolved(Klass resolved) {
            this.resolved = Objects.requireNonNull(resolved);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getName();
        }

        @Override
        public Resolved resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            return new Resolved(resolved);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar((char) 0);
        }
    }
}
