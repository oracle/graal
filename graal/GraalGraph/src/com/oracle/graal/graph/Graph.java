package com.oracle.graal.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gilles Duboscq
 *
 */
public class Graph {
	private final ArrayList<Node> nodes;
	private int nextId;
	
	public Graph() {
		nodes = new ArrayList<Node>();
	}

	public synchronized int nextId(Node node) {
		int id = nextId++;
		nodes.add(id, node);
		return id;
	}
	
	public Collection<Node> getNodes(){
		return Collections.unmodifiableCollection(nodes);
	}
	
	public Node local(Node node) {
		if(node.getGraph() == this)
			return node;
		return node.cloneNode(this);
	}
}
