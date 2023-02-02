/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.TransferToInterpreter;
import static org.graalvm.compiler.hotspot.meta.HotSpotExceptionDispatchPlugin.Options.HotSpotPostOnExceptions;

import java.util.function.Supplier;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.meta.JavaKind;
import sun.misc.Unsafe;

/**
 * This plugin does HotSpot-specific instrumentation of exception dispatch to check the
 * {@code JavaThread.should_post_on_exceptions} flag to see exceptions must be reported for the
 * current thread.
 */
public final class HotSpotExceptionDispatchPlugin implements NodePlugin {
    public static class Options {
        @Option(help = "Testing only option that forces deopts for exception throws", type = OptionType.Expert)//
        public static final OptionKey<Boolean> HotSpotPostOnExceptions = new OptionKey<>(false);
    }

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    private final GraalHotSpotVMConfig config;
    private final JavaKind wordKind;

    public HotSpotExceptionDispatchPlugin(GraalHotSpotVMConfig config, JavaKind wordKind) {
        this.config = config;
        this.wordKind = wordKind;
    }

    @Override
    public FixedWithNextNode instrumentExceptionDispatch(StructuredGraph graph, FixedWithNextNode afterExceptionLoaded, Supplier<FrameState> frameStateFunction) {
        CompilationIdentifier id = graph.compilationId();
        if (id instanceof HotSpotCompilationIdentifier &&
                        config.jvmciCompileStateCanPostOnExceptionsOffset != Integer.MIN_VALUE &&
                        config.javaThreadShouldPostOnExceptionsFlagOffset != Integer.MIN_VALUE) {
            boolean canPostOnExceptions = HotSpotPostOnExceptions.getValue(graph.getOptions());
            HotSpotCompilationRequest request = ((HotSpotCompilationIdentifier) id).getRequest();
            if (request != null) {
                long compileState = request.getJvmciEnv();
                if (compileState != 0) {
                    long canPostOnExceptionsOffset = compileState + config.jvmciCompileStateCanPostOnExceptionsOffset;
                    canPostOnExceptions = UNSAFE.getByte(canPostOnExceptionsOffset) != 0;
                }
            }
            if (canPostOnExceptions) {
                // If the exception capability is set, then generate code
                // to check the JavaThread.should_post_on_exceptions flag to see
                // if we actually need to report exception events for the current
                // thread. If not, take the fast path otherwise deoptimize.
                CurrentJavaThreadNode thread = graph.unique(new CurrentJavaThreadNode(wordKind));
                ValueNode offset = graph.unique(ConstantNode.forLong(config.javaThreadShouldPostOnExceptionsFlagOffset));
                AddressNode address = graph.unique(new OffsetAddressNode(thread, offset));
                ReadNode shouldPostException = graph.add(new ReadNode(address, JAVA_THREAD_SHOULD_POST_ON_EXCEPTIONS_FLAG_LOCATION, StampFactory.intValue(), BarrierType.NONE, MemoryOrderMode.PLAIN));
                afterExceptionLoaded.setNext(shouldPostException);
                ValueNode zero = graph.unique(ConstantNode.forInt(0));
                LogicNode cond = graph.unique(new IntegerEqualsNode(shouldPostException, zero));
                FixedGuardNode check = graph.add(new FixedGuardNode(cond, TransferToInterpreter, None, false));
                FrameState fs = frameStateFunction.get();
                assert fs.stackSize() == 1 && fs.rethrowException() : "expected rethrow exception FrameState";
                check.setStateBefore(fs);
                shouldPostException.setNext(check);
                return check;
            }
        }
        return afterExceptionLoaded;
    }

    private static final LocationIdentity JAVA_THREAD_SHOULD_POST_ON_EXCEPTIONS_FLAG_LOCATION = NamedLocationIdentity.mutable("JavaThread::_should_post_on_exceptions_flag");
}
