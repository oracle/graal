/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.impl;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.graalvm.nativeimage.ImageInfo;
import sun.misc.Unsafe;

import javax.management.MBeanServer;

/**
 * Default implementation of the Truffle runtime if the virtual machine does not provide a better
 * performing alternative.
 * <p>
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link Truffle#getRuntime()} to retrieve the current {@link TruffleRuntime}.
 */
public final class DefaultTruffleRuntime implements TruffleRuntime {

    private final ThreadLocal<DefaultFrameInstance> stackTraces = new ThreadLocal<>();
    private final DefaultTVMCI tvmci = new DefaultTVMCI();
    /**
     * Contains a reason why the default fallback engine was selected.
     */
    private final String fallbackReason;

    private final boolean explicitlyRequested;

    private final TVMCI.Test<Closeable, CallTarget> testTvmci = new TVMCI.Test<>() {

        @Override
        protected Closeable createTestContext(String testName) {
            return null;
        }

        @Override
        public CallTarget createTestCallTarget(Closeable testContext, RootNode testNode) {
            return testNode.getCallTarget();
        }

        @Override
        public void finishWarmup(Closeable testContext, CallTarget callTarget) {
            // do nothing if we have no compiler
        }
    };

    public DefaultTruffleRuntime() {
        this(null, false);
    }

    public DefaultTruffleRuntime(String fallbackReason) {
        this(fallbackReason, false);
    }

    public DefaultTruffleRuntime(String fallbackReason, boolean explicitlyRequested) {
        this.fallbackReason = fallbackReason;
        this.explicitlyRequested = explicitlyRequested;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public boolean isExplicitlyRequested() {
        return explicitlyRequested;
    }

    /**
     * Utility method that casts the singleton {@link TruffleRuntime}.
     */
    static DefaultTruffleRuntime getRuntime() {
        return (DefaultTruffleRuntime) Truffle.getRuntime();
    }

    public DefaultTVMCI getTvmci() {
        return tvmci;
    }

    @Override
    public String getName() {
        return "Interpreted";
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new DefaultAssumption(name);
    }

    @Override
    public <T> T iterateFrames(FrameInstanceVisitor<T> visitor) {
        return iterateFrames(visitor, 0);
    }

    public <T> T iterateFrames(FrameInstanceVisitor<T> visitor, int skipFrames) {
        if (skipFrames < 0) {
            throw new IllegalArgumentException("The skipFrames parameter must be >= 0.");
        }
        T result = null;
        DefaultFrameInstance frameInstance = getThreadLocalStackTrace();
        int skipCounter = skipFrames;
        while (frameInstance != null) {
            if (skipCounter <= 0) {
                result = visitor.visitFrame(frameInstance);
                if (result != null) {
                    return result;
                }
            }
            frameInstance = frameInstance.callerFrame;
            skipCounter--;
        }
        return result;
    }

    private DefaultFrameInstance getThreadLocalStackTrace() {
        return stackTraces.get();
    }

    private void setThreadLocalStackTrace(DefaultFrameInstance topFrame) {
        stackTraces.set(topFrame);
    }

    DefaultFrameInstance pushFrame(VirtualFrame frame, CallTarget target) {
        DefaultFrameInstance callerFrame = getThreadLocalStackTrace();
        setThreadLocalStackTrace(new DefaultFrameInstance(frame, target, null, callerFrame));
        return callerFrame;
    }

    DefaultFrameInstance pushFrame(VirtualFrame frame, CallTarget target, Node parentCallNode) {
        DefaultFrameInstance callerFrame = getThreadLocalStackTrace();
        // we need to ensure that frame instances are immutable so we need to recreate the parent
        // frame
        DefaultFrameInstance callerFrameWithCallNode = callerFrame != null ? callerFrame.withCallNode(parentCallNode) : callerFrame;
        setThreadLocalStackTrace(new DefaultFrameInstance(frame, target, null, callerFrameWithCallNode));
        return callerFrame;
    }

    void popFrame(DefaultFrameInstance callerFrame) {
        setThreadLocalStackTrace(callerFrame);
    }

    @Override
    public <T> T getCapability(Class<T> capability) {
        if (capability == TVMCI.Test.class) {
            return capability.cast(testTvmci);
        } else if (capability == TVMCI.class) {
            return capability.cast(tvmci);
        }

        final Iterator<T> it = Loader.load(capability).iterator();
        try {
            return it.hasNext() ? it.next() : null;
        } catch (ServiceConfigurationError e) {
            return null;
        }
    }

    private static final class Loader {
        @SuppressWarnings("unchecked")
        static <S> Iterable<S> load(Class<S> service) {
            Module truffleModule = DefaultTruffleRuntime.class.getModule();
            if (!truffleModule.canUse(service)) {
                truffleModule.addUses(service);
            }
            ModuleLayer moduleLayer = truffleModule.getLayer();
            Iterable<S> services;
            if (moduleLayer != null) {
                services = ServiceLoader.load(moduleLayer, service);
            } else {
                services = ServiceLoader.load(service, DefaultTruffleRuntime.class.getClassLoader());
            }
            if (!services.iterator().hasNext()) {
                services = ServiceLoader.load(service);
            }
            return services;
        }
    }

    public void notifyTransferToInterpreter() {
    }

    public LoopNode createLoopNode(RepeatingNode repeating) {
        if (!(repeating instanceof Node)) {
            throw new IllegalArgumentException("Repeating node must be of type Node.");
        }
        return new DefaultLoopNode(repeating);
    }

    public boolean isProfilingEnabled() {
        return false;
    }

    static final class DefaultFrameInstance implements FrameInstance {

        private final CallTarget target;
        private final VirtualFrame frame;
        private final Node callNode;
        private final DefaultFrameInstance callerFrame;

        DefaultFrameInstance(VirtualFrame frame, CallTarget target, Node callNode, DefaultFrameInstance callerFrame) {
            this.target = target;
            this.frame = frame;
            this.callNode = callNode;
            this.callerFrame = callerFrame;
        }

        public Frame getFrame(FrameAccess access) {
            Frame localFrame = this.frame;
            switch (access) {
                case READ_ONLY:
                    /* Verify that it is really used read only. */
                    return new ReadOnlyFrame(localFrame);
                case READ_WRITE:
                    return localFrame;
                case MATERIALIZE:
                    return localFrame.materialize();
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public boolean isVirtualFrame() {
            return false;
        }

        public CallTarget getCallTarget() {
            return target;
        }

        public Node getCallNode() {
            return callNode;
        }

        DefaultFrameInstance withCallNode(Node otherCallNode) {
            return new DefaultFrameInstance(frame, target, otherCallNode, callerFrame);
        }

    }

    public void markFrameMaterializeCalled(@SuppressWarnings("unused") FrameDescriptor descriptor) {
        // empty
    }

    static <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
        if (ImageInfo.inImageRuntimeCode()) {
            throw new AssertionError("Must not be called in native-image execution time");
        }
        Objects.requireNonNull(initialValue, "initialValue must be non null.");
        Objects.requireNonNull(onThreadTermination, "onThreadTermination must be non null.");
        return DefaultRuntimeAccessor.ENGINE.getModulesAccessor().getJavaLangSupport().createTerminatingThreadLocal(initialValue, onThreadTermination);
    }

    static long getStackOverflowLimit() {
        if (ImageInfo.inImageRuntimeCode()) {
            throw new AssertionError("Must not be called in native-image execution time");
        }
        long platformStackEnd = getPlatformStackEnd0();
        if (platformStackEnd == 0L) {
            throw new UnsupportedOperationException("Unable to determine platform stack end for the current thread.");
        }
        HotSpotStackConfig config = HotSpotStackConfig.INSTANCE;
        long red = alignUp(config.redZoneSize(), config.pageSize());
        long yellow = alignUp(config.yellowZoneSize(), config.pageSize());
        long reserved = alignUp(config.reservedZoneSize(), config.pageSize());
        long shadow = alignUp(config.shadowZoneSize(), config.pageSize());
        long guardZone = red + yellow + reserved;
        return platformStackEnd + config.transitionSafetyMargin() + Math.max(guardZone, shadow);
    }

    private static long alignUp(long x, long a) {
        return ((x + a - 1) / a) * a;
    }

    private record HotSpotStackConfig(long redZoneSize, long yellowZoneSize, long reservedZoneSize,
                    long shadowZoneSize, long transitionSafetyMargin, long pageSize) {

        HotSpotStackConfig {
            assert transitionSafetyMargin % pageSize == 0 : "transitionSafetyMargin must be a multiple of pageSize";
        }

        private static final HotSpotStackConfig INSTANCE = init();

        private static HotSpotStackConfig init() {
            if (ImageInfo.inImageCode()) {
                return null;
            }
            long pageSize = getUnsafe().pageSize();
            /*
             * On Linux, add one page of extra safety margin to compensate for libc/pthread
             * stack-bound reporting differences (notably older glibc guard-page behavior). The
             * fallback stack overflow limit is only required to be conservative, not identical to
             * HotSpot's exact limit.
             */
            long safetyMargin = InternalResource.OS.getCurrent() == InternalResource.OS.LINUX ? pageSize : 0L;
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            try {
                HotSpotDiagnosticMXBean bean = ManagementFactory.newPlatformMXBeanProxy(platformMBeanServer, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
                return new HotSpotStackConfig(
                                // uses 4KB units
                                Long.parseLong(bean.getVMOption("StackRedPages").getValue()) * 4096L,
                                // uses 4KB units
                                Long.parseLong(bean.getVMOption("StackYellowPages").getValue()) * 4096L,
                                // uses 4KB units
                                Long.parseLong(bean.getVMOption("StackReservedPages").getValue()) * 4096L,
                                // uses 4KB units
                                Long.parseLong(bean.getVMOption("StackShadowPages").getValue()) * 4096L,
                                // size in bytes
                                safetyMargin,
                                // OS page size in bytes
                                pageSize);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        private static Unsafe getUnsafe() {
            try {
                // Fast path when we are trusted.
                return Unsafe.getUnsafe();
            } catch (SecurityException se) {
                // Slow path when we are not trusted.
                try {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(Unsafe.class);
                } catch (Exception e) {
                    throw new RuntimeException("exception while trying to get Unsafe", e);
                }
            }
        }
    }

    private static native long getPlatformStackEnd0();
}
