/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.FinalizationSupport;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_lang_ref_Reference {

    static {
        // Ensure PublicFinalReference is injected in the host VM.
        FinalizationSupport.ensureInitialized();
    }

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                    @JavaType(Object.class) StaticObject referent, @JavaType(ReferenceQueue.class) StaticObject queue,
                    @Inject Meta meta) {
        // Guest referent field is ignored for weak/soft/final/phantom references.
        EspressoReference<StaticObject> ref = null;
        if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_WeakReference)) {
            ref = new EspressoWeakReference(self, referent, meta.getContext().getReferenceQueue());
        } else if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_SoftReference)) {
            ref = new EspressoSoftReference(self, referent, meta.getContext().getReferenceQueue());
        } else if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_FinalReference)) {
            ref = new EspressoFinalReference(self, referent, meta.getContext().getReferenceQueue());
        } else if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_PhantomReference)) {
            ref = new EspressoPhantomReference(self, referent, meta.getContext().getReferenceQueue());
        }
        if (ref != null) {
            // Weak/Soft/Final/Phantom reference.
            meta.HIDDEN_HOST_REFERENCE.setHiddenObject(self, ref);
        } else {
            // Strong reference.
            meta.java_lang_ref_Reference_referent.set(self, referent);
        }

        if (StaticObject.isNull(queue)) {
            meta.java_lang_ref_Reference_queue.set(self,
                            meta.java_lang_ref_ReferenceQueue_NULL.get(meta.java_lang_ref_ReferenceQueue.tryInitializeAndGetStatics()));
        } else {
            meta.java_lang_ref_Reference_queue.set(self, queue);
        }
    }

    @SuppressWarnings("rawtypes")
    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject get(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                    @Inject Meta meta) {
        assert !InterpreterToVM.instanceOf(self, meta.java_lang_ref_PhantomReference) : "Cannot call Reference.get on PhantomReference";
        if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_WeakReference) //
                        || InterpreterToVM.instanceOf(self, meta.java_lang_ref_SoftReference) //
                        || InterpreterToVM.instanceOf(self, meta.java_lang_ref_FinalReference)) {
            // Ignore guest referent field.
            EspressoReference ref = (EspressoReference) meta.HIDDEN_HOST_REFERENCE.getHiddenObject(self);
            if (ref == null) {
                return StaticObject.NULL;
            }
            assert ref instanceof Reference;
            StaticObject obj = (StaticObject) ref.get();
            return obj == null ? StaticObject.NULL : obj;
        } else {
            return meta.java_lang_ref_Reference_referent.getObject(self);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetFromInactiveFinalReference extends SubstitutionNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(java.lang.ref.Reference.class) StaticObject self);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_lang_ref_Reference_getFromInactiveFinalReference.getCallTargetNoSubstitution())") DirectCallNode original) {
            // Call original to possibly trigger guest assertion.
            original.call(self);
            // Ignore result, and return the actual result.
            return get(self, context.getMeta());
        }
    }

    @SuppressWarnings("rawtypes")
    @Substitution(hasReceiver = true)
    public static void clear(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                    @Inject Meta meta) {
        if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_WeakReference) //
                        || InterpreterToVM.instanceOf(self, meta.java_lang_ref_SoftReference) //
                        || InterpreterToVM.instanceOf(self, meta.java_lang_ref_PhantomReference) //
                        || InterpreterToVM.instanceOf(self, meta.java_lang_ref_FinalReference)) {
            EspressoReference ref = (EspressoReference) meta.HIDDEN_HOST_REFERENCE.getHiddenObject(self);
            if (ref != null) {
                assert ref instanceof Reference;
                ref.clear();
                // Also remove host reference.
                meta.HIDDEN_HOST_REFERENCE.setHiddenObject(self, null);
            }
        } else {
            meta.java_lang_ref_Reference_referent.set(self, StaticObject.NULL);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class ClearInactiveFinalReference extends SubstitutionNode {

        abstract void execute(@JavaType(java.lang.ref.Reference.class) StaticObject self);

        @Specialization
        void doCached(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_lang_ref_Reference_clearInactiveFinalReference.getCallTargetNoSubstitution())") DirectCallNode original) {
            // Call original to possibly trigger guest assertion.
            original.call(self);
            // Ignore result, and actually clear.
            clear(self, context.getMeta());
        }
    }

    @SuppressWarnings("rawtypes")
    @Substitution(hasReceiver = true)
    abstract static class Enqueue extends SubstitutionNode {

        abstract boolean execute(@JavaType(java.lang.ref.Reference.class) StaticObject self);

        @Specialization
        boolean doCached(@JavaType(java.lang.ref.Reference.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_lang_ref_Reference_enqueue.getCallTargetNoSubstitution())") DirectCallNode originalEnqueue) {
            if (context.getJavaVersion().java9OrLater()) {
                /*
                 * In java 9 or later, the referent field is cleared. We must replicate this
                 * behavior on our own implementation.
                 */
                Meta meta = context.getMeta();
                if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_WeakReference) //
                                || InterpreterToVM.instanceOf(self, meta.java_lang_ref_SoftReference) //
                                || InterpreterToVM.instanceOf(self, meta.java_lang_ref_PhantomReference) //
                                || InterpreterToVM.instanceOf(self, meta.java_lang_ref_FinalReference)) {
                    EspressoReference ref = (EspressoReference) meta.HIDDEN_HOST_REFERENCE.getHiddenObject(self);
                    if (ref != null) {
                        ref.clear();
                    }
                }
            }
            return (boolean) originalEnqueue.call(self);
        }

    }

}
