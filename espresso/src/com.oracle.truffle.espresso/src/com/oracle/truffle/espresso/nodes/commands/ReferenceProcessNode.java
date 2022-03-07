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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Node for triggering reference processing in single threaded contexts.
 */
public class ReferenceProcessNode extends RootNode {
    public static final String EVAL_NAME = "<ProcessReferences>";

    private final EspressoContext context;
    private final DirectCallNode processPendingReferences;
    private final StaticObject finalizerQueue;
    private final DirectCallNode queuePoll;
    private final DirectCallNode runFinalizer;
    private final StaticObject jla;

    public ReferenceProcessNode(EspressoLanguage lang) {
        super(lang);
        this.context = EspressoContext.get(this);

        Method processPendingReferenceMethod = context.getMeta().java_lang_ref_Reference.lookupDeclaredMethod(Symbol.Name.processPendingReferences, Symbol.Signature._void,
                        Klass.LookupMode.STATIC_ONLY);
        this.processPendingReferences = DirectCallNode.create(processPendingReferenceMethod.getCallTargetForceInit());

        Field queue = context.getMeta().java_lang_ref_Finalizer.lookupDeclaredField(Symbol.Name.queue, Symbol.Type.java_lang_ref_ReferenceQueue);
        this.finalizerQueue = queue.getObject(context.getMeta().java_lang_ref_Finalizer.tryInitializeAndGetStatics());

        Method poll = finalizerQueue.getKlass().lookupMethod(Symbol.Name.poll, Symbol.Signature.Reference);
        this.queuePoll = DirectCallNode.create(poll.getCallTargetForceInit());

        Method runFinalizerMethod = context.getMeta().java_lang_ref_Finalizer.lookupDeclaredMethod(Symbol.Name.runFinalizer, Symbol.Signature._void_JavaLangAccess, Klass.LookupMode.INSTANCE_ONLY);
        this.runFinalizer = DirectCallNode.create(runFinalizerMethod.getCallTargetForceInit());

        Field javaLangAccess = context.getMeta().jdk_internal_access_SharedSecrets.lookupDeclaredField(Symbol.Name.javaLangAccess, Symbol.Type.jdk_internal_access_JavaLangAccess);
        jla = javaLangAccess.getObject(context.getMeta().jdk_internal_access_SharedSecrets.tryInitializeAndGetStatics());
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (context.multiThreadingEnabled()) {
            throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_IllegalStateException,
                            "Manual reference processing was requested, but the context is not in single-threaded mode.");
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
        processPendingReferences.call();
    }

    /*
     * Process loop of `FinalizerThread.run()`
     */
    private void processFinalizers() {
        StaticObject finalizer = (StaticObject) queuePoll.call(finalizerQueue);
        while (!StaticObject.isNull(finalizer)) {
            runFinalizer.call(finalizer, jla);
            finalizer = (StaticObject) queuePoll.call(finalizerQueue);
        }
    }
}
