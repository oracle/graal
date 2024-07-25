package jdk.graal.compiler.phases.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;

public class InitMemoryVerificationPhase extends BasePhase<CoreProviders> {
    private record Data(EconomicMap<AbstractNewObjectNode, AllocData> allocData) {}

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifApplied(this, GraphState.StageFlag.LOW_TIER_LOWERING, graphState);
    }

    private static final class AllocData {
        private AllocData() {
            liveWrites = new ArrayList<>();
            wasPublished = false;
        }

        private AllocData(AllocData copy) {
            liveWrites = new ArrayList<>(copy.liveWrites);
            wasPublished = copy.wasPublished;
        }

        private final List<MemoryKill> liveWrites;
        private boolean wasPublished;

        private boolean hasNoLiveKills() {
            return liveWrites.isEmpty();
        }

        private void publish() {
            liveWrites.clear();
            wasPublished = true;
        }
    }

    private static class InitWriteTestDataClosure extends ReentrantNodeIterator.NodeIteratorClosure<Data> {
        private void processCheckpoint(SingleMemoryKill checkpoint, Data state) {
            processIdentity(checkpoint.getKilledLocationIdentity(), checkpoint, state);
        }

        private void processCheckpoint(MultiMemoryKill checkpoint, Data state) {
            for (LocationIdentity identity : checkpoint.getKilledLocationIdentities()) {
                processIdentity(identity, checkpoint, state);
            }
        }

        private void processIdentity(LocationIdentity identity, MemoryKill checkpoint, Data state) {
            if (!(checkpoint instanceof AddressableMemoryAccess)) {
                return;
            }
            ValueNode addressBase = ((AddressableMemoryAccess) checkpoint).getAddress().getBase();
            if (!(addressBase instanceof AbstractNewObjectNode newObject)) {
                return;
            }

            AllocData data = state.allocData.get(newObject);
            assert data != null : "Trying to write to non-registered object";
            assert data.wasPublished || newObject.emitMemoryBarrier() || identity.equals(LocationIdentity.init()) : "Unpublished write " + checkpoint + " to new object " + newObject + " must use init memory";
            if (identity.equals(LocationIdentity.init())) {
                data.liveWrites.add(checkpoint);
            }
        }

        private void processPublish(PublishWritesNode publish, Data currentState) {
            if (publish.allocation() instanceof AbstractNewObjectNode newObject) {
                AllocData data = currentState.allocData.get(newObject);
                assert data != null : "trying to publish non registered alloc: " + publish;
                data.publish();
            }
        }

        private boolean checkNoLiveKills(Data state) {
            var cursor = state.allocData.getEntries();
            while (cursor.advance()) {
                AllocData data = cursor.getValue();
                assert data.liveWrites.isEmpty() : cursor.getKey() + " has unpublished writes";
            }
            return true;
        }

        private boolean checkNoLiveKills(Data state, AbstractNewObjectNode target) {
            AllocData data = state.allocData.get(target);
            assert data != null : "memory access to non-registered target node " + target;
            assert data.hasNoLiveKills() : target + " has unpublished writes";
            assert !data.wasPublished : " cannot read directly from published new object node";
            return true;
        }

        private void processNonKillAccess(AddressableMemoryAccess access, Data currentState) {
            ValueNode base = access.getAddress().getBase();
            if (base instanceof AbstractNewObjectNode newObjectNode) {
                assert checkNoLiveKills(currentState, newObjectNode);
            }
        }

        @Override
        protected Data processNode(FixedNode node, Data currentState) {
            if (node instanceof AbstractNewObjectNode newObjectNode) {
                currentState.allocData.put(newObjectNode, new AllocData());
            } else if (MemoryKill.isSingleMemoryKill(node)) {
                processCheckpoint(MemoryKill.asSingleMemoryKill(node), currentState);
            } else if (MemoryKill.isMultiMemoryKill(node)) {
                processCheckpoint(MemoryKill.asMultiMemoryKill(node), currentState);
            } else if (node instanceof AddressableMemoryAccess access) {
                processNonKillAccess(access, currentState);
            } else if (node instanceof PublishWritesNode anchorNode) {
                processPublish(anchorNode, currentState);
            } else if (node instanceof ReturnNode) {
                assert checkNoLiveKills(currentState);
            }
            return currentState;
        }

        @Override
        protected Data merge(AbstractMergeNode merge, List<Data> states) {
            EconomicMap<AbstractNewObjectNode, AllocData> merged = EconomicMap.create();
            for (Data state : states) {
                merged.putAll(state.allocData);
            }
            return new Data(merged);
        }

        @Override
        protected Data afterSplit(AbstractBeginNode node, Data oldState) {
            EconomicMap<AbstractNewObjectNode, AllocData> newData = EconomicMap.create();
            var cursor = oldState.allocData.getEntries();
            while (cursor.advance()) {
                AllocData oldAlloc = cursor.getValue();
                newData.put(cursor.getKey(), new AllocData(oldAlloc));
            }
            return new Data(newData);
        }

        @Override
        protected EconomicMap<LoopExitNode, Data> processLoop(LoopBeginNode loop, Data initialState) {
            ReentrantNodeIterator.LoopInfo<Data> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);
            return loopInfo.exitStates;
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        ReentrantNodeIterator.apply(new InitWriteTestDataClosure(), graph.start(), new Data(EconomicMap.create()));
    }

}
