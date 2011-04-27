package com.oracle.graal.graph;

/**
 * @author Gilles Duboscq
 *
 */
public class NullNode extends Node {

	public NullNode(int inputs, int successors, Graph graph) {
		super(inputs, successors, graph);
	}

	@Override
	public NullNode cloneNode(Graph into) {
		return new NullNode(0, 0, into);
	}

}
