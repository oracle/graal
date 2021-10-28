/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.stubs.StubUtil.printNumber;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.AllocaNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;

/**
 * Stub to allocate a {@link NegativeArraySizeException} thrown by a bytecode when the length of an
 * array allocation is negative.
 */
public class NegativeArraySizeExceptionStub extends CreateExceptionStub {
    public NegativeArraySizeExceptionStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("createNegativeArraySizeException", options, providers, linkage);
    }

    private static final boolean PRINT_LENGTH_IN_EXCEPTION = JavaVersionUtil.JAVA_SPEC >= 11;

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        switch (index) {
            case 1:
                return providers.getRegisters().getThreadRegister();
            case 2:
                // required bytes for maximum length + nullbyte
                return OutOfBoundsExceptionStub.MAX_INT_STRING_SIZE + 1;
            case 3:
                return PRINT_LENGTH_IN_EXCEPTION;
            default:
                throw GraalError.shouldNotReachHere("unknown parameter " + name + " at index " + index);
        }
    }

    @Snippet
    private static Object createNegativeArraySizeException(int length, @ConstantParameter Register threadRegister, @ConstantParameter int bufferSizeInBytes,
                    @ConstantParameter boolean printLengthInException) {
        if (printLengthInException) {
            Word buffer = AllocaNode.alloca(bufferSizeInBytes, HotSpotReplacementsUtil.wordSize());
            Word ptr = printNumber(buffer, length);
            ptr.writeByte(0, (byte) 0);
            return createException(threadRegister, NegativeArraySizeException.class, buffer);
        } else {
            return createException(threadRegister, NegativeArraySizeException.class);
        }
    }
}
