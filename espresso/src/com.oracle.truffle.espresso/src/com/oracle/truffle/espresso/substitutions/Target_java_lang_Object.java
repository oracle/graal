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

package com.oracle.truffle.espresso.substitutions;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_java_lang_Object {
    @Substitution(hasReceiver = true, isTrivial = true)
    public static int hashCode(@JavaType(Object.class) StaticObject self, @Inject EspressoLanguage lang) {
        return VM.JVM_IHashCode(self, lang);
    }

    @Substitution(hasReceiver = true, isTrivial = true)
    public static @JavaType(Class.class) StaticObject getClass(@JavaType(Object.class) StaticObject self) {
        return self.getKlass().mirror();
    }

    public static final class InitGuard implements InlinedMethodPredicate {
        public static final InlinedMethodPredicate INSTANCE = new InitGuard();

        @Override
        public boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, InlinedMethodNode node) {
            StaticObject receiver = node.peekReceiver(frame);
            return !Init.hasFinalizer(receiver, context);
        }
    }

    // TODO: Allow inlining this in bytecode (see GR-42697)
    // @InlineInBytecode(guard = InitGuard.class)
    @Substitution(hasReceiver = true, methodName = "<init>", isTrivial = true)
    abstract static class Init extends SubstitutionNode {

        abstract void execute(@JavaType(Object.class) StaticObject self);

        static boolean hasFinalizer(StaticObject self, EspressoContext context) {
            return ((ObjectKlass) self.getKlass()).hasFinalizer(context);
        }

        @Specialization(guards = "!hasFinalizer(self, getContext())")
        void noFinalizer(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject self) {
            // nop
        }

        @TruffleBoundary
        @Fallback
        void registerFinalizer(@JavaType(Object.class) StaticObject self,
                        @Cached("getMeta().java_lang_ref_Finalizer_register.getCallTarget()") CallTarget register,
                        @Cached IndirectCallNode indirectCallNode) {
            indirectCallNode.call(register, self);
        }
    }

    @Substitution(hasReceiver = true)
    @Throws(CloneNotSupportedException.class)
    public static @JavaType(Object.class) StaticObject clone(@JavaType(Object.class) StaticObject self,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return VM.JVM_Clone(self, language, meta, profiler);
    }

    /* As of JDK 14+, these are no longer linked in libjava. */

    @Substitution(hasReceiver = true)
    public static void wait(@JavaType(Object.class) StaticObject self, long time,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        meta.getVM().JVM_MonitorWait(self, time, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static void notify(@JavaType(Object.class) StaticObject self,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        meta.getVM().JVM_MonitorNotify(self, profiler);
    }

    @Substitution(hasReceiver = true)
    public static void notifyAll(@JavaType(Object.class) StaticObject self,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        meta.getVM().JVM_MonitorNotifyAll(self, profiler);
    }
}
