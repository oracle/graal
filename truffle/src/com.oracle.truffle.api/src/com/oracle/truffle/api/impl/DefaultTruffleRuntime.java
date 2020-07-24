/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

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

    private final TVMCI.Test<Closeable, CallTarget> testTvmci = new TVMCI.Test<Closeable, CallTarget>() {

        @Override
        protected Closeable createTestContext(String testName) {
            return null;
        }

        @Override
        public CallTarget createTestCallTarget(Closeable testContext, RootNode testNode) {
            return createCallTarget(testNode);
        }

        @Override
        public void finishWarmup(Closeable testContext, CallTarget callTarget) {
            // do nothing if we have no compiler
        }
    };

    public DefaultTruffleRuntime() {
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

    @SuppressWarnings("deprecation")
    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        DefaultCallTarget target = new DefaultCallTarget(rootNode);
        DefaultRuntimeAccessor.INSTRUMENT.onLoad(target.getRootNode());
        return target;
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
        Objects.requireNonNull(target);
        return new DefaultDirectCallNode(target);
    }

    @Override
    public IndirectCallNode createIndirectCallNode() {
        return new DefaultIndirectCallNode();
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new DefaultVirtualFrame(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new DefaultMaterializedFrame(new DefaultVirtualFrame(frameDescriptor, arguments));
    }

    @Override
    public CompilerOptions createCompilerOptions() {
        return new DefaultCompilerOptions();
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
        T result = null;
        DefaultFrameInstance frameInstance = getThreadLocalStackTrace();
        while (frameInstance != null) {
            result = visitor.visitFrame(frameInstance);
            if (result != null) {
                return result;
            }
            frameInstance = frameInstance.callerFrame;
        }
        return result;
    }

    @Override
    public FrameInstance getCallerFrame() {
        DefaultFrameInstance currentFrame = getThreadLocalStackTrace();
        if (currentFrame != null) {
            return currentFrame.callerFrame;
        } else {
            return null;
        }
    }

    @Override
    public FrameInstance getCurrentFrame() {
        return getThreadLocalStackTrace();
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
        private static final Method LOAD_METHOD;
        static {
            Method loadMethod = null;
            try {
                Class<?> servicesClass = Class.forName("jdk.vm.ci.services.Services");
                loadMethod = servicesClass.getMethod("load", Class.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // Services.load is not available
            }
            if (loadMethod != null) {
                try {
                    try {
                        loadMethod.invoke(null, (Object) null);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                } catch (NullPointerException npe) {
                    // Services.load is accessible
                } catch (IllegalAccessException iae) {
                    // Services.load is not accessible. This happens on JDK 9+ when EnableJVMCI is
                    // true (either explicitly or by default) which causes the jdk.internal.vm.ci
                    // module to be resolved.
                    loadMethod = null;
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
            LOAD_METHOD = loadMethod;
        }

        @SuppressWarnings("unchecked")
        static <S> Iterable<S> load(Class<S> service) {
            TruffleJDKServices.addUses(service);
            if (LOAD_METHOD != null) {
                try {
                    return (Iterable<S>) LOAD_METHOD.invoke(null, service);
                } catch (Exception e) {
                    throw new InternalError(e);
                }
            } else {
                return ServiceLoader.load(service);
            }
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

}
