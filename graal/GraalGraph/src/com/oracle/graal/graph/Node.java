/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public abstract class Node {
	private final Graph graph;
	private final int id;
	private final NodeArray inputs;
	private final NodeArray successors;
	private final ArrayList<Node> usages;
	private final ArrayList<Node> predecessors;
	
	public Node(Node[] inputs, Node[] successors, Graph graph) {
		this.graph = graph;
		if(graph != null) {
			this.id = graph.nextId(this); //this pointer escaping in a constructor..
		}else {
			this.id = -1;
		}
		this.inputs = new NodeArray(inputs);
		this.successors = new NodeArray(successors);
		this.predecessors = new ArrayList<Node>();
		this.usages = new ArrayList<Node>();
	}
	
	public Node(int inputs, int successors, Graph graph) {
		this(nullNodes(inputs, graph), nullNodes(successors, graph), graph);
	}
	
	public class NodeArray implements Iterable<Node>{
		private final Node[] nodes;
		
		public NodeArray(Node[] nodes) {
			this.nodes = nodes;
		}
		
		@Override
		public Iterator<Node> iterator() {
			return Arrays.asList(this.nodes).iterator();
		}
		
		public Node set(int index, Node node) {
			if(node.graph != Node.this.graph) {
				// fail ?
			}
			Node old = nodes[index];
			nodes[index] = node;
			if(Node.this.inputs == this) { // :-/
				old.usages.remove(Node.this);
				node.usages.add(Node.this);
			}else /*if(Node.this.successors == this)*/{
				old.predecessors.remove(Node.this);
				node.predecessors.add(Node.this);
			}
			
			return old;
		}
		
		public boolean contains(Node n) {
			for(int i = 0; i < nodes.length; i++)
				if(nodes[i] == n) //equals?
					return true;
			return false;
		}
		
		public boolean replace(Node toReplace, Node replacement) {
			for(int i = 0; i < nodes.length; i++) {
				if(nodes[i] == toReplace) { // equals?
					this.set(i, replacement);
					return true; //replace only one occurrence
				}
			}
			return false;
		}
		
		public Node[] asArray() {
			Node[] copy = new Node[nodes.length];
			System.arraycopy(nodes, 0, copy, 0, nodes.length);
			return copy;
		}
	}
	
	public Collection<Node> getPredecessors() {
		return Collections.unmodifiableCollection(predecessors);
	}
	
	public Collection<Node> getUsages() {
		return Collections.unmodifiableCollection(usages);
	}
	
	public NodeArray getInputs() {
		return inputs;
	}
	
	public NodeArray getSuccessors() {
		return successors;
	}
	
	public int getId() {
		return id;
	}
	
	public Graph getGraph() {
		return graph;
	}
	
	public void replace(Node other) {
		if(other.graph != this.graph) {
			other = other.cloneNode(this.graph);
		}
		Node[] myInputs = inputs.nodes;
		for(int i = 0; i < myInputs.length; i++) {
			other.inputs.set(i, myInputs[i]);
		}
		for(Node usage : usages) {
			usage.inputs.replace(this, other);
		}
		
		Node[] mySuccessors = successors.nodes;
		for(int i = 0; i < mySuccessors.length; i++) {
			other.successors.set(i, mySuccessors[i]);
		}
		for(Node predecessor : predecessors) {
			predecessor.successors.replace(this, other);
		}
	}
	
	public abstract Node cloneNode(Graph into);

	@Override
	public boolean equals(Object obj) {
		if(obj == this)
			return true;
		if(obj.getClass() == this.getClass()) {
			Node other  = (Node)obj;
			if(other.id == this.id && other.graph == this.graph)
				return true;
		}
		return false;
	}
	
	protected Node getInput(int index) {
		return this.inputs.nodes[index];
	}
	
	protected Node getSuccessor(int index) {
		return this.successors.nodes[index];
	}

	private static Node[] nullNodes(int number, Graph graph) {
		Node[] nodes = new Node[number];
		for(int i = 0; i < number; i++)
			nodes[i] = new NullNode(0, 0, graph);
		return nodes;
	}
}
