/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.clearPendingException;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static com.oracle.graal.hotspot.replacements.HotSpotSymbolConstant.vmSymbol;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.StubForeignCallNode;
import com.oracle.graal.hotspot.word.SymbolPointer;
import com.oracle.graal.word.Word;

import jdk.vm.ci.code.Register;

/**
 * Base class for stubs that create a runtime exception.
 */
public class CreateExceptionStub extends SnippetStub {

    protected CreateExceptionStub(String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetMethodName, providers, linkage);
    }

    public static final ForeignCallDescriptor THROW_AND_POST_JVMTI_EXCEPTION = new ForeignCallDescriptor("throw_and_post_jvmti_exception", void.class, Word.class, SymbolPointer.class, Word.class);

    @NodeIntrinsic(StubForeignCallNode.class)
    static native void throwAndPostJvmtiException(@ConstantNodeParameter ForeignCallDescriptor desc, Word thread, SymbolPointer type, Word message);

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, Word message) {
        Word thread = registerAsWord(threadRegister);
        throwAndPostJvmtiException(THROW_AND_POST_JVMTI_EXCEPTION, thread, vmSymbol(exception), message);
        return clearPendingException(thread);
    }
}
