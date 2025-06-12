/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.commands;

import java.util.List;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.ThreadState;
import com.oracle.truffle.espresso.threads.Transition;

public final class ReferenceProcessCache extends EspressoNode {
    /*
     * Note: different implementations of java 11 have different package names for these classes
     * (j.i.misc vs j.i.access). Since we cannot select a type according to the version, we try all
     * known names here.
     */
    private static final List<Symbol<Type>> SHARED_SECRETS_TYPES = List.of(Types.jdk_internal_access_SharedSecrets, Types.sun_misc_SharedSecrets,
                    Types.jdk_internal_misc_SharedSecrets);
    private static final List<Symbol<Type>> JAVA_LANG_ACCESS_TYPES = List.of(Types.jdk_internal_access_JavaLangAccess, Types.sun_misc_JavaLangAccess,
                    Types.jdk_internal_misc_JavaLangAccess);
    private static final List<Symbol<Signature>> RUN_FINALIZER_SIGNATURES = List.of(Signatures._void_jdk_internal_access_JavaLangAccess, Signatures._void_sun_misc_JavaLangAccess,
                    Signatures._void_jdk_internal_misc_JavaLangAccess);

    private final EspressoContext context;
    private final DirectCallNode processPendingReferences;
    private final StaticObject finalizerQueue;
    private final DirectCallNode queuePoll;
    private final DirectCallNode runFinalizer;
    private final StaticObject jla;

    public ReferenceProcessCache(EspressoContext context) {
        this.context = context;

        Method processPendingReferenceMethod = findProcessPendingReferences(context);
        this.processPendingReferences = DirectCallNode.create(processPendingReferenceMethod.getCallTargetForceInit());

        Field queue = context.getMeta().java_lang_ref_Finalizer.lookupDeclaredField(Names.queue, Types.java_lang_ref_ReferenceQueue);
        this.finalizerQueue = queue.getObject(context.getMeta().java_lang_ref_Finalizer.tryInitializeAndGetStatics());

        Method poll = finalizerQueue.getKlass().lookupMethod(Names.poll, Signatures.Reference);
        this.queuePoll = DirectCallNode.create(poll.getCallTargetForceInit());

        Klass sharedSecrets = findSharedSecrets(context);
        Field jlaField = findJlaField(sharedSecrets);

        jla = jlaField.getObject(sharedSecrets.tryInitializeAndGetStatics());

        this.runFinalizer = DirectCallNode.create(findRunFinalizer(context).getCallTargetForceInit());
    }

    public void execute() {
        if (context.multiThreadingEnabled()) {
            throw throwIllegalStateException("Manual reference processing was requested, but the context is not in single-threaded mode.");
        }
        Transition transition = Transition.transition(ThreadState.IN_ESPRESSO, this);
        try {
            context.triggerDrain();
            processPendingReferences();
            processFinalizers();
        } finally {
            transition.restore(this);
        }
    }

    private void processPendingReferences() {
        if (!context.hasReferencePendingList()) {
            // no pending references, can skip.
            return;
        }
        if (context.getJavaVersion().java8OrEarlier()) {
            processPendingReferences.call(false /* waitForNotify */);
        } else {
            processPendingReferences.call();
        }
    }

    /*
     * Process loop of `FinalizerThread.run()`.
     */
    private void processFinalizers() {
        StaticObject finalizer = (StaticObject) queuePoll.call(finalizerQueue);
        while (!StaticObject.isNull(finalizer)) {
            runFinalizer.call(finalizer, jla);
            finalizer = (StaticObject) queuePoll.call(finalizerQueue);
        }
    }

    private EspressoException throwIllegalStateException(String message) {
        return context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_IllegalStateException, message);
    }

    private static Klass findSharedSecrets(EspressoContext context) {
        for (Symbol<Type> type : SHARED_SECRETS_TYPES) {
            Klass k = context.getMeta().loadKlassOrNull(type, StaticObject.NULL, StaticObject.NULL);
            if (k != null) {
                return k;
            }
        }
        throw EspressoError.shouldNotReachHere("Could not find SharedSecrets for reference processing.");
    }

    private static Field findJlaField(Klass sharedSecrets) {
        for (Symbol<Type> type : JAVA_LANG_ACCESS_TYPES) {
            Field f = sharedSecrets.lookupField(Names.javaLangAccess, type, Klass.LookupMode.STATIC_ONLY);
            if (f != null) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere("Could not find SharedSecrets#javaLangAccess field for reference processing.");
    }

    private static Method findRunFinalizer(EspressoContext context) {
        for (Symbol<Signature> signature : RUN_FINALIZER_SIGNATURES) {
            Method m = context.getMeta().java_lang_ref_Finalizer.lookupMethod(Names.runFinalizer, signature, Klass.LookupMode.INSTANCE_ONLY);
            if (m != null) {
                return m;
            }
        }
        throw EspressoError.shouldNotReachHere("Could not find Finalizer.runFinalizer method for reference processing.");
    }

    private static Method findProcessPendingReferences(EspressoContext context) {
        Method processPendingReferenceMethod;
        if (context.getJavaVersion().java8OrEarlier()) {
            processPendingReferenceMethod = context.getMeta().java_lang_ref_Reference.lookupDeclaredMethod(Names.tryHandlePending, Signatures._boolean_boolean,
                            Klass.LookupMode.STATIC_ONLY);
        } else {
            processPendingReferenceMethod = context.getMeta().java_lang_ref_Reference.lookupDeclaredMethod(Names.processPendingReferences, Signatures._void, Klass.LookupMode.STATIC_ONLY);
        }
        if (processPendingReferenceMethod == null) {
            throw EspressoError.shouldNotReachHere("Could not find pending reference processing method.");
        }
        return processPendingReferenceMethod;
    }
}
