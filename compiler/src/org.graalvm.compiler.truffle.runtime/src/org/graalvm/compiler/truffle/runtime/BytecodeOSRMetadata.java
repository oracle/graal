package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, Integer.MAX_VALUE);
    // Must be a power of 2 (polling uses bit masks)
    public static final int OSR_POLL_INTERVAL = 1024;

    private final BytecodeOSRNode osrNode;
    private final Map<Integer, OptimizedCallTarget> osrCompilations;
    private final int osrThreshold;
    private int backEdgeCount;
    private volatile boolean compilationFailed;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, int osrThreshold) {
        this.osrNode = osrNode;
        this.osrCompilations = new ConcurrentHashMap<>();
        this.osrThreshold = osrThreshold;
        this.backEdgeCount = 0;
        this.compilationFailed = false;
    }

    final Object onOSRBackEdge(VirtualFrame parentFrame, int target, TruffleLanguage<?> language) {
        if (!incrementAndPoll() || compilationFailed) {
            // note: incur volatile read of compilationFailed only if poll succeeds
            return null;
        }
        if (parentFrame.getFrameDescriptor().canMaterialize()) {
            // If the frame is materializeable, give up on OSR. State could become inconsistent.
            osrNode.setOSRMetadata(DISABLED);
            return null;
        }

        OptimizedCallTarget osrTarget = osrCompilations.get(target);
        if (osrTarget == null) {
            synchronized (this) {
                osrTarget = osrCompilations.get(target);
                if (osrTarget == null) {
                    osrTarget = requestOSR(target, language, parentFrame.getFrameDescriptor());
                    if (osrTarget != null) {
                        osrCompilations.put(target, osrTarget);
                    }
                }
            }
        }
        if (osrTarget != null && !osrTarget.isCompiling()) {
            if (osrTarget.isValid()) {
                // Quick check, in case the frame somehow changed after compilation.
                assert !parentFrame.getFrameDescriptor().canMaterialize();
                return osrTarget.callOSR(parentFrame);
            }
            invalidateOSRTarget(target, "OSR compilation failed or cancelled");
        }
        return null;
    }

    /**
     * Increment back edge count and return whether compilation should be polled.
     *
     * When the OSR threshold is reached, this method will return true after every OSR_POLL_INTERVAL
     * back-edges. This method is thread-safe, but could under-count.
     */
    private boolean incrementAndPoll() {
        int newBackEdgeCount = ++backEdgeCount; // Omit overflow check; OSR should trigger long
                                                // before overflow happens
        return (newBackEdgeCount >= osrThreshold && (newBackEdgeCount & (OSR_POLL_INTERVAL - 1)) == 0);
    }

    private synchronized OptimizedCallTarget requestOSR(int target, TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        assert !osrCompilations.containsKey(target);
        OptimizedCallTarget callTarget = GraalTruffleRuntime.getRuntime().createOSRCallTarget(new BytecodeOSRRootNode(osrNode, target, language, frameDescriptor));
        callTarget.compile(false);
        if (callTarget.isCompilationFailed()) {
            markCompilationFailed();
            return null;
        }
        osrCompilations.put(target, callTarget);
        return callTarget;
    }

    private synchronized void invalidateOSRTarget(int target, CharSequence reason) {
        OptimizedCallTarget callTarget = osrCompilations.remove(target);
        if (callTarget != null) {
            if (callTarget.isCompilationFailed()) {
                markCompilationFailed();
            }
            callTarget.invalidate(reason);
        }
    }

    synchronized void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        for (OptimizedCallTarget callTarget : osrCompilations.values()) {
            if (callTarget != null) {
                if (callTarget.isCompilationFailed()) {
                    markCompilationFailed();
                }
                callTarget.nodeReplaced(oldNode, newNode, reason);
            }
        }
        osrCompilations.clear();
    }

    private void markCompilationFailed() {
        /*
         * Replace this object with the DISABLED marker object. Another thread may already have a
         * handle to this object, so also mark the failure internally so we can avoid recompilation.
         */
        compilationFailed = true;
        osrNode.setOSRMetadata(DISABLED);
    }

    // for testing
    public Map<Integer, OptimizedCallTarget> getOSRCompilations() {
        return osrCompilations;
    }

    public int getBackEdgeCount() {
        return backEdgeCount;
    }
}
