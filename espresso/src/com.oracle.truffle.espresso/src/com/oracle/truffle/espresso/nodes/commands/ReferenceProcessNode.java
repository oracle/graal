/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Node for triggering reference processing in single threaded contexts.
 */
public class ReferenceProcessNode extends RootNode {
    public static final String EVAL_NAME = "<ProcessReferences>";

    /*
     * Note: different implementations of java 11 have different package names for these classes
     * (j.i.misc vs j.i.access). Since we cannot select a type according to the version, we try all
     * known names here.
     */
    private static final List<Symbol<Type>> SHARED_SECRETS_TYPES = List.of(Type.jdk_internal_access_SharedSecrets, Type.sun_misc_SharedSecrets, Type.jdk_internal_misc_SharedSecrets);
    private static final List<Symbol<Type>> JAVA_LANG_ACCESS_TYPES = List.of(Type.jdk_internal_access_JavaLangAccess, Type.sun_misc_JavaLangAccess, Type.jdk_internal_misc_JavaLangAccess);
    private static final List<Symbol<Signature>> RUN_FINALIZER_SIGNATURES = List.of(Signature._void_jdk_internal_access_JavaLangAccess, Signature._void_sun_misc_JavaLangAccess,
                    Signature._void_jdk_internal_misc_JavaLangAccess);

    private final EspressoContext context;
    private final DirectCallNode processPendingReferences;
    private final StaticObject finalizerQueue;
    private final DirectCallNode queuePoll;
    private final DirectCallNode runFinalizer;
    private final StaticObject jla;

    @Override
    public String getName() {
        return EVAL_NAME;
    }

    private Klass findSharedSecrets() {
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
            Field f = sharedSecrets.lookupField(Name.javaLangAccess, type, Klass.LookupMode.STATIC_ONLY);
            if (f != null) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere("Could not find SharedSecrets#javaLangAccess field for reference processing.");
    }

    private Method findRunFinalizer() {
        for (Symbol<Signature> signature : RUN_FINALIZER_SIGNATURES) {
            Method m = context.getMeta().java_lang_ref_Finalizer.lookupMethod(Name.runFinalizer, signature, Klass.LookupMode.INSTANCE_ONLY);
            if (m != null) {
                return m;
            }
        }
        throw EspressoError.shouldNotReachHere("Could not find Finalizer.runFinalizer method for reference processing.");

    }

    private Method findProcessPendingReferences() {
        Method processPendingReferenceMethod;
        if (context.getJavaVersion().java8OrEarlier()) {
            processPendingReferenceMethod = context.getMeta().java_lang_ref_Reference.lookupDeclaredMethod(Name.tryHandlePending, Signature._boolean_boolean, Klass.LookupMode.STATIC_ONLY);
        } else {
            processPendingReferenceMethod = context.getMeta().java_lang_ref_Reference.lookupDeclaredMethod(Name.processPendingReferences, Signature._void, Klass.LookupMode.STATIC_ONLY);
        }
        if (processPendingReferenceMethod == null) {
            throw EspressoError.shouldNotReachHere("Could not find pending reference processing method.");
        }
        return processPendingReferenceMethod;
    }

    public ReferenceProcessNode(EspressoLanguage lang) {
        super(lang);
        this.context = EspressoContext.get(this);

        Method processPendingReferenceMethod = findProcessPendingReferences();
        this.processPendingReferences = DirectCallNode.create(processPendingReferenceMethod.getCallTargetForceInit());

        Field queue = context.getMeta().java_lang_ref_Finalizer.lookupDeclaredField(Name.queue, Type.java_lang_ref_ReferenceQueue);
        this.finalizerQueue = queue.getObject(context.getMeta().java_lang_ref_Finalizer.tryInitializeAndGetStatics());

        Method poll = finalizerQueue.getKlass().lookupMethod(Name.poll, Signature.Reference);
        this.queuePoll = DirectCallNode.create(poll.getCallTargetForceInit());

        Klass sharedSecrets = findSharedSecrets();
        Field jlaField = findJlaField(sharedSecrets);

        jla = jlaField.getObject(sharedSecrets.tryInitializeAndGetStatics());

        this.runFinalizer = DirectCallNode.create(findRunFinalizer().getCallTargetForceInit());
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (context.multiThreadingEnabled()) {
            throw throwIllegalStateException("Manual reference processing was requested, but the context is not in single-threaded mode.");
        }
        context.triggerDrain();
        processPendingReferences();
        processFinalizers();
        return StaticObject.NULL;
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
}
