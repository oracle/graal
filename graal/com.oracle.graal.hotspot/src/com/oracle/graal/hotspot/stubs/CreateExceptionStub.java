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

import static com.oracle.graal.compiler.common.LocationIdentity.any;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_REGISTERS;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.SAFEPOINT;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.REEXECUTABLE;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.clearPendingException;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.HotSpotVMConfig;
import com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.StubForeignCallNode;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.replacements.nodes.CStringConstant;
import com.oracle.graal.word.Word;

import jdk.vm.ci.code.Register;

/**
 * Base class for stubs that create a runtime exception.
 */
public class CreateExceptionStub extends SnippetStub {

    protected CreateExceptionStub(String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetMethodName, providers, linkage);
    }

    @Fold
    static String getInternalClassName(Class<?> cls) {
        return cls.getName().replace('.', '/');
    }

    private static Word classAsCString(Class<?> cls) {
        return CStringConstant.cstring(getInternalClassName(cls));
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception) {
        Word message = null;
        return createException(threadRegister, exception, message);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, Word message) {
        Word thread = registerAsWord(threadRegister);
        throwAndPostJvmtiException(THROW_AND_POST_JVMTI_EXCEPTION, thread, classAsCString(exception), message);
        return clearPendingException(thread);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, KlassPointer klass) {
        Word thread = registerAsWord(threadRegister);
        throwKlassExternalNameException(THROW_KLASS_EXTERNAL_NAME_EXCEPTION, thread, classAsCString(exception), klass);
        return clearPendingException(thread);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, KlassPointer objKlass, KlassPointer targetKlass) {
        Word thread = registerAsWord(threadRegister);
        throwClassCastException(THROW_CLASS_CAST_EXCEPTION, thread, classAsCString(exception), objKlass, targetKlass);
        return clearPendingException(thread);
    }

    private static final ForeignCallDescriptor THROW_AND_POST_JVMTI_EXCEPTION = new ForeignCallDescriptor("throw_and_post_jvmti_exception", void.class, Word.class, Word.class, Word.class);
    private static final ForeignCallDescriptor THROW_KLASS_EXTERNAL_NAME_EXCEPTION = new ForeignCallDescriptor("throw_klass_external_name_exception", void.class, Word.class, Word.class,
                    KlassPointer.class);
    private static final ForeignCallDescriptor THROW_CLASS_CAST_EXCEPTION = new ForeignCallDescriptor("throw_class_cast_exception", void.class, Word.class, Word.class, KlassPointer.class,
                    KlassPointer.class);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native void throwAndPostJvmtiException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, Word message);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native void throwKlassExternalNameException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, KlassPointer klass);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native void throwClassCastException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, KlassPointer objKlass, KlassPointer targetKlass);

    public static void registerForeignCalls(HotSpotVMConfig c, HotSpotForeignCallsProviderImpl foreignCalls) {
        foreignCalls.registerForeignCall(THROW_AND_POST_JVMTI_EXCEPTION, c.throwAndPostJvmtiExceptionAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        foreignCalls.registerForeignCall(THROW_KLASS_EXTERNAL_NAME_EXCEPTION, c.throwKlassExternalNameExceptionAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        foreignCalls.registerForeignCall(THROW_CLASS_CAST_EXCEPTION, c.throwClassCastExceptionAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
    }
}
