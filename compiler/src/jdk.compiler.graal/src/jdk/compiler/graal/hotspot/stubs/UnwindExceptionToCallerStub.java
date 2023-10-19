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
package jdk.compiler.graal.hotspot.stubs;

import static jdk.compiler.graal.hotspot.stubs.ExceptionHandlerStub.checkExceptionNotNull;
import static jdk.compiler.graal.hotspot.stubs.ExceptionHandlerStub.checkNoExceptionInThread;
import static jdk.compiler.graal.hotspot.stubs.StubUtil.cAssertionsEnabled;
import static jdk.compiler.graal.hotspot.stubs.StubUtil.decipher;
import static jdk.compiler.graal.hotspot.stubs.StubUtil.newDescriptor;
import static jdk.compiler.graal.hotspot.stubs.StubUtil.printf;
import static org.graalvm.word.LocationIdentity.any;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.api.replacements.Fold.InjectedParameter;
import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.debug.Assertions;
import jdk.compiler.graal.graph.Node.ConstantNodeParameter;
import jdk.compiler.graal.graph.Node.NodeIntrinsic;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotForeignCallLinkage;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.nodes.JumpToExceptionHandlerInCallerNode;
import jdk.compiler.graal.hotspot.nodes.StubForeignCallNode;
import jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.compiler.graal.nodes.UnwindNode;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.word.Word;
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
        if (logging(GraalHotSpotVMConfig.INJECTED_OPTIONVALUES)) {
            printf("unwinding exception %p (", exceptionOop.rawValue());
            decipher(exceptionOop.rawValue());
            printf(") at %p (", returnAddress.rawValue());
            decipher(returnAddress.rawValue());
            printf(")\n");
        }
        Word thread = HotSpotReplacementsUtil.registerAsWord(threadRegister);
        checkNoExceptionInThread(thread, assertionsEnabled(GraalHotSpotVMConfig.INJECTED_VMCONFIG));
        checkExceptionNotNull(assertionsEnabled(GraalHotSpotVMConfig.INJECTED_VMCONFIG), exception);

        Word handlerInCallerPc = exceptionHandlerForReturnAddress(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, thread, returnAddress);

        if (logging(GraalHotSpotVMConfig.INJECTED_OPTIONVALUES)) {
            printf("handler for exception %p at return address %p is at %p (", exceptionOop.rawValue(), returnAddress.rawValue(), handlerInCallerPc.rawValue());
            decipher(handlerInCallerPc.rawValue());
            printf(")\n");
        }

        JumpToExceptionHandlerInCallerNode.jumpToExceptionHandlerInCaller(handlerInCallerPc, exception, returnAddress);
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

    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER_FOR_RETURN_ADDRESS = newDescriptor(HotSpotForeignCallDescriptor.Transition.SAFEPOINT,
                    HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, any(), UnwindExceptionToCallerStub.class,
                    "exceptionHandlerForReturnAddress", Word.class, Word.class, Word.class);

    @NodeIntrinsic(value = StubForeignCallNode.class)
    public static native Word exceptionHandlerForReturnAddress(@ConstantNodeParameter ForeignCallDescriptor exceptionHandlerForReturnAddress, Word thread, Word returnAddress);
}
