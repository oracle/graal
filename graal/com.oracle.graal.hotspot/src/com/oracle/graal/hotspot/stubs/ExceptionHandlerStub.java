/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.hotspot.nodes.PatchReturnAddressNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.stubs.StubUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.*;
import com.oracle.graal.word.*;

/**
 * Stub called by the {@linkplain Marks#MARK_EXCEPTION_HANDLER_ENTRY exception handler entry point}
 * in a compiled method. This entry point is used when returning to a method to handle an exception
 * thrown by a callee. It is not used for routing implicit exceptions. Therefore, it does not need
 * to save any registers as HotSpot uses a caller save convention.
 * <p>
 * The descriptor for a call to this stub is {@link HotSpotBackend#EXCEPTION_HANDLER}.
 */
public class ExceptionHandlerStub extends SnippetStub {

    public ExceptionHandlerStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(providers, target, linkage);
    }

    /**
     * This stub is called when returning to a method to handle an exception thrown by a callee. It
     * is not used for routing implicit exceptions. Therefore, it does not need to save any
     * registers as HotSpot uses a caller save convention.
     */
    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        assert index == 2;
        return providers.getRegisters().getThreadRegister();
    }

    @Snippet
    private static void exceptionHandler(Object exception, Word exceptionPc, @ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        checkNoExceptionInThread(thread, assertionsEnabled());
        checkExceptionNotNull(assertionsEnabled(), exception);
        writeExceptionOop(thread, exception);
        writeExceptionPc(thread, exceptionPc);
        if (logging()) {
            printf("handling exception %p (", Word.fromObject(exception).rawValue());
            decipher(Word.fromObject(exception).rawValue());
            printf(") at %p (", exceptionPc.rawValue());
            decipher(exceptionPc.rawValue());
            printf(")\n");
        }

        // patch throwing pc into return address so that deoptimization finds the right debug info
        patchReturnAddress(exceptionPc);

        Word handlerPc = exceptionHandlerForPc(EXCEPTION_HANDLER_FOR_PC, thread);

        if (logging()) {
            printf("handler for exception %p at %p is at %p (", Word.fromObject(exception).rawValue(), exceptionPc.rawValue(), handlerPc.rawValue());
            decipher(handlerPc.rawValue());
            printf(")\n");
        }

        // patch the return address so that this stub returns to the exception handler
        patchReturnAddress(handlerPc);
    }

    static void checkNoExceptionInThread(Word thread, boolean enabled) {
        if (enabled) {
            Object currentException = readExceptionOop(thread);
            if (currentException != null) {
                fatal("exception object in thread must be null, not %p", Word.fromObject(currentException).rawValue());
            }
            if (cAssertionsEnabled()) {
                // This thread-local is only cleared in DEBUG builds of the VM
                // (see OptoRuntime::generate_exception_blob)
                Word currentExceptionPc = readExceptionPc(thread);
                if (currentExceptionPc.notEqual(Word.zero())) {
                    fatal("exception PC in thread must be zero, not %p", currentExceptionPc.rawValue());
                }
            }
        }
    }

    static void checkExceptionNotNull(boolean enabled, Object exception) {
        if (enabled && exception == null) {
            fatal("exception must not be null");
        }
    }

    @Fold
    private static boolean logging() {
        return Boolean.getBoolean("graal.logExceptionHandlerStub");
    }

    /**
     * Determines if either Java assertions are enabled for {@link ExceptionHandlerStub} or if this
     * is a HotSpot build where the ASSERT mechanism is enabled.
     * <p>
     * This first check relies on the per-class assertion status which is why this method must be in
     * this class.
     */
    @Fold
    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled || cAssertionsEnabled();
    }

    public static final ForeignCallDescriptor EXCEPTION_HANDLER_FOR_PC = descriptorFor(ExceptionHandlerStub.class, "exceptionHandlerForPc");

    @NodeIntrinsic(value = StubForeignCallNode.class, setStampFromReturnType = true)
    public static native Word exceptionHandlerForPc(@ConstantNodeParameter ForeignCallDescriptor exceptionHandlerForPc, Word thread);
}
