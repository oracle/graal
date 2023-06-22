/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_OPTIONVALUES;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.clearPendingException;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.DeoptimizeWithExceptionInCallerNode;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.CStringConstant;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.Register;

/**
 * Base class for stubs that create a runtime exception.
 */
public class CreateExceptionStub extends SnippetStub {

    public static class Options {
        @Option(help = "Testing only option that forces deopts for exception throws", type = OptionType.Expert)//
        public static final OptionKey<Boolean> HotSpotDeoptExplicitExceptions = new OptionKey<>(false);
    }

    protected CreateExceptionStub(String snippetMethodName, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetMethodName, options, providers, linkage);
    }

    @Fold
    static boolean reportsDeoptimization(@Fold.InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptBlobUnpackWithExceptionInTLS != 0;
    }

    @Fold
    static boolean alwayDeoptimize(@Fold.InjectedParameter OptionValues options) {
        return Options.HotSpotDeoptExplicitExceptions.getValue(options);
    }

    @Fold
    static String getInternalClassName(Class<?> cls) {
        return cls.getName().replace('.', '/');
    }

    private static Word classAsCString(Class<?> cls) {
        return CStringConstant.cstring(getInternalClassName(cls));
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception) {
        Word message = WordFactory.zero();
        return createException(threadRegister, exception, message);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, Word message) {
        Word thread = registerAsWord(threadRegister);
        int deoptimized = throwAndPostJvmtiException(THROW_AND_POST_JVMTI_EXCEPTION, thread, classAsCString(exception), message);
        return handleExceptionReturn(thread, deoptimized);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, KlassPointer klass) {
        Word thread = registerAsWord(threadRegister);
        int deoptimized = throwKlassExternalNameException(THROW_KLASS_EXTERNAL_NAME_EXCEPTION, thread, classAsCString(exception), klass);
        return handleExceptionReturn(thread, deoptimized);
    }

    protected static Object createException(Register threadRegister, Class<? extends Throwable> exception, KlassPointer objKlass, KlassPointer targetKlass) {
        Word thread = registerAsWord(threadRegister);
        int deoptimized = throwClassCastException(THROW_CLASS_CAST_EXCEPTION, thread, classAsCString(exception), objKlass, targetKlass);
        return handleExceptionReturn(thread, deoptimized);
    }

    private static Object handleExceptionReturn(Word thread, int deoptimized) {
        Object clearPendingException = clearPendingException(thread);
        // alwayDeoptimize is a testing option to force a deopt here but the code pattern should
        // keep both the deopt and return paths, so include a test against the exception which we
        // know should always succeed.
        if ((alwayDeoptimize(INJECTED_OPTIONVALUES) && clearPendingException != null) ||
                        (reportsDeoptimization(GraalHotSpotVMConfig.INJECTED_VMCONFIG) && deoptimized != 0)) {
            DeoptimizeWithExceptionInCallerNode.deopt(clearPendingException);
        }
        return clearPendingException;
    }

    private static final HotSpotForeignCallDescriptor THROW_AND_POST_JVMTI_EXCEPTION = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "throw_and_post_jvmti_exception", int.class,
                    Word.class, Word.class, Word.class);
    private static final HotSpotForeignCallDescriptor THROW_KLASS_EXTERNAL_NAME_EXCEPTION = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "throw_klass_external_name_exception",
                    int.class, Word.class, Word.class, KlassPointer.class);
    private static final HotSpotForeignCallDescriptor THROW_CLASS_CAST_EXCEPTION = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "throw_class_cast_exception", int.class, Word.class,
                    Word.class, KlassPointer.class, KlassPointer.class);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native int throwAndPostJvmtiException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, Word message);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native int throwKlassExternalNameException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, KlassPointer klass);

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native int throwClassCastException(@ConstantNodeParameter ForeignCallDescriptor d, Word thread, Word type, KlassPointer objKlass, KlassPointer targetKlass);

    public static void registerForeignCalls(GraalHotSpotVMConfig c, HotSpotForeignCallsProviderImpl foreignCalls) {
        foreignCalls.registerForeignCall(THROW_AND_POST_JVMTI_EXCEPTION, c.throwAndPostJvmtiExceptionAddress, NativeCall);
        foreignCalls.registerForeignCall(THROW_KLASS_EXTERNAL_NAME_EXCEPTION, c.throwKlassExternalNameExceptionAddress, NativeCall);
        foreignCalls.registerForeignCall(THROW_CLASS_CAST_EXCEPTION, c.throwClassCastExceptionAddress, NativeCall);
    }
}
