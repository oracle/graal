package org.graalvm.tools.lsp.server.utils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public class CoverageEventNode extends ExecutionEventNode {

    private final URI coverageUri;
    private final Node instrumentedNode;
    private final SourceSection instrumentedSection;
    private final Function<URI, TextDocumentSurrogate> surrogateProvider;
    private Node child;
    @CompilationFinal private boolean entered = false;
    private final long creatorThreadId;

    public CoverageEventNode(SourceSection instrumentedSection, Node instrumentedNode, URI coverageUri, Function<URI, TextDocumentSurrogate> func, long creatorThreadId) {
        this.instrumentedSection = instrumentedSection;
        this.instrumentedNode = instrumentedNode;
        this.coverageUri = coverageUri;
        this.surrogateProvider = func;
        this.creatorThreadId = creatorThreadId;
    }

    public Node getInstrumentedNode() {
        return instrumentedNode;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        if (entered || creatorThreadId != Thread.currentThread().getId()) {
            // We had problems with a finalizer thread, so we filter only for the thread which
            // created the node
            return;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        entered = true;
        putSection2Uri(frame.materialize());
    }

    @TruffleBoundary
    private void putSection2Uri(MaterializedFrame frame) {
        URI sourceUri = instrumentedSection.getSource().getURI();
        if (!sourceUri.getScheme().equals("file")) {
            String name = instrumentedSection.getSource().getName();
            Path pathFromName = null;
            try {
                if (name != null) {
                    pathFromName = Paths.get(name);
                }
            } catch (InvalidPathException e) {
            }
            if (pathFromName == null || !Files.exists(pathFromName)) {
                return;
            }

            sourceUri = pathFromName.toUri();
        }

        MaterializedFrame frameCopy = copyFrame(frame);
        TextDocumentSurrogate surrogate = surrogateProvider.apply(sourceUri);
        surrogate.addLocationCoverage(SourceSectionReference.from(instrumentedSection), new CoverageData(coverageUri, frameCopy, this));
    }

    /**
     * Copies the frame's {@link FrameDescriptor} and creates a new {@link MaterializedFrame} with
     * this descriptor to freeze the current frame state (locals, slots, etc.) at a specific source
     * section. This is useful for code completion based on coverage data. So, completion can be
     * based on the frame state at the completion's source section.
     *
     * The frame copying does not perform a deep copy of objects in frame slots, so that we cannot
     * freeze the state of these objects.
     *
     * @param frame to copy
     * @return the copy
     */
    private static MaterializedFrame copyFrame(MaterializedFrame frame) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        FrameDescriptor descriptorCopy = frameDescriptor.copy();
        for (FrameSlot slotCopy : descriptorCopy.getSlots()) {
            FrameSlotKind frameSlotKind = frameDescriptor.getFrameSlotKind(frameDescriptor.findFrameSlot(slotCopy.getIdentifier()));
            descriptorCopy.setFrameSlotKind(slotCopy, frameSlotKind);
        }
        Object[] arguments = frame.getArguments();
        MaterializedFrame frameCopy = Truffle.getRuntime().createMaterializedFrame(Arrays.copyOf(arguments, arguments.length), descriptorCopy);
        try {
            for (FrameSlot slot : frameDescriptor.getSlots()) {
                FrameSlotKind slotKind = frameDescriptor.getFrameSlotKind(slot);
                FrameSlot id = descriptorCopy.findFrameSlot(slot.getIdentifier());

                switch (slotKind) {
                    case Illegal:
                        break;
                    case Object:
                        if (frame.isObject(slot)) {
                            frameCopy.setObject(id, frame.getObject(slot));
                        }
                        break;
                    case Boolean:
                        if (frame.isBoolean(slot)) {
                            frameCopy.setBoolean(id, frame.getBoolean(slot));
                        }
                        break;
                    case Int:
                        if (frame.isInt(slot)) {
                            frameCopy.setInt(id, frame.getInt(slot));
                        }
                        break;
                    case Byte:
                        if (frame.isByte(slot)) {
                            frameCopy.setByte(id, frame.getByte(slot));
                        }
                        break;
                    case Long:
                        if (frame.isLong(slot)) {
                            frameCopy.setLong(id, frame.getLong(slot));
                        }
                        break;
                    case Double:
                        if (frame.isDouble(slot)) {
                            frameCopy.setDouble(id, frame.getDouble(slot));
                        }
                        break;
                    case Float:
                        if (frame.isFloat(slot)) {
                            frameCopy.setFloat(id, frame.getFloat(slot));
                        }
                        break;
                }
            }
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
        return frameCopy;
    }

    public void insertOrReplaceChild(Node node) {
        if (child != null) {
            child.replace(node);
        } else {
            child = node;
            insert(node);
        }
    }

    public void clearChild() {
        child.replace(new ExecutionEventNode() {
        });
    }
}
