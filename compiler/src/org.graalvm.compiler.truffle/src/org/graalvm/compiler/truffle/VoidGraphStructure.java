package org.graalvm.compiler.truffle;

import java.util.Collection;
import java.util.Map;
import org.graalvm.graphio.GraphStructure;

final class VoidGraphStructure implements GraphStructure<Void, Void, Void, Void> {
    static final GraphStructure<Void, Void, Void, Void> INSTANCE = new VoidGraphStructure();

    private VoidGraphStructure() {
    }

    @Override
    public Void graph(Void currentGraph, Object obj) {
        return null;
    }

    @Override
    public Iterable<? extends Void> nodes(Void graph) {
        return null;
    }

    @Override
    public int nodesCount(Void graph) {
        return 0;
    }

    @Override
    public int nodeId(Void node) {
        return 0;
    }

    @Override
    public boolean nodeHasPredecessor(Void node) {
        return false;
    }

    @Override
    public void nodeProperties(Void graph, Void node, Map<String, ? super Object> properties) {
    }

    @Override
    public Void node(Object obj) {
        return null;
    }

    @Override
    public Void nodeClass(Object obj) {
        return null;
    }

    @Override
    public Void classForNode(Void node) {
        return null;
    }

    @Override
    public String nameTemplate(Void nodeClass) {
        return null;
    }

    @Override
    public Object nodeClassType(Void nodeClass) {
        return null;
    }

    @Override
    public Void portInputs(Void nodeClass) {
        return null;
    }

    @Override
    public Void portOutputs(Void nodeClass) {
        return null;
    }

    @Override
    public int portSize(Void port) {
        return 0;
    }

    @Override
    public boolean edgeDirect(Void port, int index) {
        return false;
    }

    @Override
    public String edgeName(Void port, int index) {
        return null;
    }

    @Override
    public Object edgeType(Void port, int index) {
        return null;
    }

    @Override
    public Collection<? extends Void> edgeNodes(Void graph, Void node, Void port, int index) {
        return null;
    }

}
