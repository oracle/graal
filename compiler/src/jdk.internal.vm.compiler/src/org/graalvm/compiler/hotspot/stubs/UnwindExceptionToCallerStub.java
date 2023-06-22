/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_OPTIONVALUES;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.nodes.JumpToExceptionHandlerInCallerNode.jumpToExceptionHandlerInCaller;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub.checkExceptionNotNull;
import static org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub.checkNoExceptionInThread;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.cAssertionsEnabled;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.decipher;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.newDescriptor;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.printf;
import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.code.Register;

/**
 * Stub called by an {@link UnwindNode}. This stub executes in the frame of the method throwing an
 * exception and completes by jumping to the exception handler in the calling frame.
 */
public class UnwindExceptionToCallerStub extends SnippetStub {

    public UnwindExceptionToCallerStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("unwindExceptionToCaller", options, providers, linkage);
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        if (index == 2) {
            return providers.getRegisters().getThreadRegister();
        }
        throw new InternalError();
    }

    @Snippet
    private static void unwindExceptionToCaller(Object exception, Word returnAddress, @ConstantParameter Register threadRegister) {
        Pointer exceptionOop = Word.objectToTrackedPointer(exception);
        if (logging(INJECTED_OPTIONVALUES)) {
            printf("unwinding exception %p (", exceptionOop.rawValue());
            decipher(exceptionOop.rawValue());
            printf(") at %p (", returnAddress.rawValue());
            decipher(returnAddress.rawValue());
            printf(")\n");
        }
        Word thread = registerAsWord(threadRegister);
        checkNoExceptionInThread(thread, assertionsEnabled(INJECTED_VMCONFIG));
        checkExceptionNotNull(assertionsEnabled(INJECTED_VMCONFIG), exception);

        Word handlerInCallerPc = exceptionHandlerForReturnAddress(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, thread, returnAddress);

        if (logging(INJECTED_OPTIONVALUES)) {
            printf("handler for exception %p at return address %p is at %p (", exceptionOop.rawValue(), returnAddress.rawValue(), handlerInCallerPc.rawValue());
            decipher(handlerInCallerPc.rawValue());
            printf(")\n");
        }

        jumpToExceptionHandlerInCaller(handlerInCallerPc, exception, returnAddress);
    }

    @Fold
    static boolean logging(@Fold.InjectedParameter OptionValues options) {
        return StubOptions.TraceUnwindStub.getValue(options);
    }

    /**
     * Determines if either Java assertions are enabled for Graal or if this is a HotSpot build
     * where the ASSERT mechanism is enabled.
     */
    @Fold
    @SuppressWarnings("all")
    static boolean assertionsEnabled(@InjectedParameter GraalHotSpotVMConfig config) {
        return Assertions.assertionsEnabled() || cAssertionsEnabled(config);
    }

    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER_FOR_RETURN_ADDRESS = newDescriptor(SAFEPOINT, REEXECUTABLE, any(), UnwindExceptionToCallerStub.class,
                    "exceptionHandlerForReturnAddress", Word.class, Word.class, Word.class);

    @NodeIntrinsic(value = StubForeignCallNode.class)
    public static native Word exceptionHandlerForReturnAddress(@ConstantNodeParameter ForeignCallDescriptor exceptionHandlerForReturnAddress, Word thread, Word returnAddress);
}
