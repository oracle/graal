package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, Integer.MAX_VALUE, null, null);
    // Must be a power of 2 (polling uses bit masks)
    public static final int OSR_POLL_INTERVAL = 1024;

    private final BytecodeOSRNode osrNode;
    private final Map<Integer, OptimizedCallTarget> osrCompilations;
    private final int osrThreshold;
    private int backEdgeCount;
    private volatile boolean compilationFailed;

    // Used for OSR state transfer.
    @CompilationFinal(dimensions = 1) private final FrameSlot[] frameSlots;
    @CompilationFinal(dimensions = 1) private final byte[] frameTags;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, int osrThreshold, FrameSlot[] frameSlots, byte[] frameTags) {
        this.osrNode = osrNode;
        this.osrCompilations = new ConcurrentHashMap<>();
        this.osrThreshold = osrThreshold;
        this.backEdgeCount = 0;
        this.compilationFailed = false;
        this.frameSlots = frameSlots;
        this.frameTags = frameTags;
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
            if (!osrTarget.isValid()) {
                invalidateOSRTarget(target, "OSR compilation failed or cancelled");
                return null;
            }
            if (parentFrame.getFrameDescriptor().canMaterialize()) {
                throw new AssertionError("Frame passed to OSR should not be materializeable.");
            }
            return osrTarget.callOSR(parentFrame);
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

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state into (or
     * out of) an OSR frame.
     */
    @ExplodeLoop
    public void executeTransfer(FrameWithoutBoxing source, FrameWithoutBoxing target) {
        byte[] currentSourceTags = source.getTags();
        byte[] currentTargetTags = target.getTags();

        if (currentSourceTags.length != frameTags.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError("Source frame contains an unexpected number of slots.");
        } else if (currentTargetTags.length != frameTags.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError("Target frame contains an unexpected number of slots.");
        }

        for (int i = 0; i < frameSlots.length; i++) {
            FrameSlot slot = frameSlots[i];

            byte expectedTag = frameTags[i];
            byte actualTag = currentSourceTags[i];

            boolean tagsCondition = expectedTag == actualTag;
            if (!tagsCondition) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frameTags[i] = actualTag;
                expectedTag = actualTag;
            }
            switch (expectedTag) {
                case FrameWithoutBoxing.BOOLEAN_TAG:
                    target.setBoolean(slot, source.getBooleanUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.BYTE_TAG:
                    target.setByte(slot, source.getByteUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.DOUBLE_TAG:
                    target.setDouble(slot, source.getDoubleUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.FLOAT_TAG:
                    target.setFloat(slot, source.getFloatUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.INT_TAG:
                    target.setInt(slot, source.getIntUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.LONG_TAG:
                    target.setLong(slot, source.getLongUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.OBJECT_TAG:
                    target.setObject(slot, source.getObjectUnsafe(i, slot, tagsCondition));
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Defined frame slot " + slot + " is illegal. Please initialize frame slot with a FrameSlotKind.");
            }
        }
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
