package com.oracle.graal.graph;

import static org.junit.Assert.*;

import org.junit.Test;

public class NodeTest {

	@Test
	public void testReplace() {
		DummyNode n1 = new DummyNode(2, 1, null);
		
		Graph g1 = new Graph();
		
		DummyNode n2 = new DummyNode(1, 1, g1);
	}

	private static class DummyNode extends Node{
		
		public DummyNode(int inputs, int successors, Graph graph) {
			super(inputs, successors, graph);
		}

		public DummyNode(Node[] inputs, Node[] successors, Graph graph) {
			super(inputs, successors, graph);
		}

		@Override
		public Node cloneNode(Graph into) {
			return new DummyNode(this.getInputs().asArray(), this.getSuccessors().asArray(), into);
		}
		
	}
}
