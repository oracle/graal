/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.cfg.model;

import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


/**
 * This is the container class for the data model,
 * it prepares creates nodes and edges for the CfgScene
 * from a ControlFlowGraph of the Compilation Model
 */
public class CfgEnv {
    private ControlFlowGraph cfg;    
    private Map<CfgNode, LoopInfo> loopMap;//maps: LoopHeader --> LoopInfo
    private CfgNodeImpl[] nodeArr;
    private CfgEdgeImpl[] edgeArr;
       
    public CfgEnv(ControlFlowGraph cfg) {
        this.cfg = cfg;        
        int blockCount = cfg.getBasicBlocks().size();
        CfgNodeImpl[] nodes = new CfgNodeImpl[blockCount];
        Map<BasicBlock, CfgNodeImpl> block2nodeMap = new HashMap<BasicBlock, CfgNodeImpl>();
        Map<CfgNodeImpl, Set<CfgEdgeImpl>> inputMap = new HashMap<CfgNodeImpl, Set<CfgEdgeImpl>>();
        ArrayList<CfgEdgeImpl> allEdges = new ArrayList<CfgEdgeImpl>();
        List<BasicBlock> blocks = cfg.getBasicBlocks();           
        //create nodes
        for(int idx=0 ; idx < blockCount ; idx++) {
            BasicBlock b = blocks.get(idx);
            
            String description = "Name: " + b.getName() + "\n";
            description += "BCI: [" + b.getFromBci() + "," + b.getToBci() + "]\n";
            if (b.getLoopDepth() > 0) {
                description += "Loop " + b.getLoopIndex() + " Depth " + b.getLoopDepth() + "\n";
            }
            description += "Predecessors: " + getBlockList(b.getPredecessors()) + "\n";
            description += "Successors: " + getBlockList(b.getSuccessors()) + "\n";
            description += "XHandlers: " + getBlockList(b.getXhandlers());
            if (b.getDominator() != null) {
                description += "\nDominator: " + b.getDominator().getName();
            } 
            nodes[idx] = new CfgNodeImpl(b, idx, description);
            block2nodeMap.put(b, nodes[idx]);
        }
        
        
        //create edges
        Set<String> cache = new HashSet<String>();//avoids identical edges with same source and same target
        for(int i = 0 ; i < blockCount ; i++) {
            BasicBlock b = blocks.get(i);       
            List<CfgEdgeImpl> outputEdges = new ArrayList<CfgEdgeImpl>();           
            
            Set<BasicBlock> successors = new HashSet<BasicBlock>();
            successors.addAll(b.getSuccessors());
            successors.addAll(b.getXhandlers());
            for(BasicBlock sb : successors) {  
                CfgNodeImpl succNode = block2nodeMap.get(sb);
                CfgEdgeImpl edge = new CfgEdgeImpl(nodes[i], succNode);                
                if(cache.contains(edge.toString())) 
                    continue;
                cache.add(edge.toString());
                //check for symtric edges              
                if(sb.getXhandlers().contains(b) || sb.getSuccessors().contains(b)) 
                    edge.setSymmetric(true);
                outputEdges.add(edge);                         
            }
            allEdges.addAll(outputEdges);
            nodes[i].setOutputEdges(outputEdges.toArray(new CfgEdgeImpl[outputEdges.size()]));    
        }

        for(CfgEdgeImpl e: allEdges) {
            //CfgNodeImpl src = (CfgNodeImpl) e.getSourceNode();
            CfgNodeImpl tar = (CfgNodeImpl) e.getTargetNode();
            Set<CfgEdgeImpl> set = inputMap.get(tar);
            if( set == null) {
                set = new HashSet<CfgEdgeImpl>();      
                set.add(e);
                inputMap.put(tar, set);
            }
            set.add(e);    
        }
        for(CfgNodeImpl n : nodes){
            Set<CfgEdgeImpl> inputEdges = inputMap.get(n);
            if(inputEdges == null) continue;
            n.setInputEdges(inputEdges.toArray(new CfgEdgeImpl[inputEdges.size()]));
        }    
        CfgEdgeImpl[] edges = allEdges.toArray(new CfgEdgeImpl[allEdges.size()]);
        this.edgeArr=edges;
        this.nodeArr=nodes;       
        CfgNodeImpl rootNode = nodeArr[0];                   
        setNodeLevels(rootNode);       
        indexLoops(rootNode); 
                          
    }
    
    
    private String getBlockList(List<BasicBlock> blocks) {
        if (blocks.size() == 0) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for (BasicBlock b : blocks) {
            sb.append(prefix).append(b.getName());
            prefix = ", ";
        }
        return sb.toString();
    }
 
    
    public CfgNode[] getNodes(){
        return this.nodeArr;
    }
    
    public CfgEdge[] getEdges(){
        return this.edgeArr;
    }

    public Map<CfgNode, LoopInfo> getLoopMap() {
        return loopMap;
    }

    public void setLoopMap(Map<CfgNode, LoopInfo> loopMap) {
        this.loopMap = loopMap;
    }
 
    private void indexLoops(CfgNodeImpl rootNode){
         LoopEnv env = new LoopEnv(Arrays.asList(nodeArr));
         loopDetection(env, rootNode);
         calcLoopDepth(env);      
                
         int loopIndex=1;
          
         for(LoopInfo info : env.loopMap.values()) {
             info.setLoopIndex(loopIndex++);
             info.setLoopDepth(info.getHeader().getLoopDepth());
             for(CfgNode n : info.getMembers()){
                if(n.getLoopDepth()>info.getLoopDepth()) continue;
                CfgNodeImpl ni = (CfgNodeImpl) n;  
                ni.setLoopDepth(info.getLoopDepth());
                ni.setLoopIndex(info.getLoopIndex());
             }
         }
         
         for(LoopInfo info : env.loopMap.values()) {          
            HashSet<CfgNode> members =  new HashSet<CfgNode>(info.getMembers());         
            members.remove(info.getHeader());//remove own header
            for(CfgNode n: members){              
                if(n.isLoopHeader()) {                   
                    LoopInfo memberInfo = env.loopMap.get(n);
                    if (info.getLoopDepth() == memberInfo.getLoopDepth()-1)
                        memberInfo.setParent(info);
                }                    
            }
         }
         this.loopMap = env.loopMap; 
    }

    
    private class LoopEnv {   
        Set<CfgNodeImpl> allNodes;
        Set<CfgNodeImpl> activeNodes; 
        Set<CfgNodeImpl> visitedNodes;       
        Map<CfgNode, LoopInfo> loopMap;
        private int loopIndex=0;
        
        public LoopEnv(Collection<CfgNodeImpl> nodes){
            allNodes = new HashSet<CfgNodeImpl>(nodes); 
            activeNodes = new HashSet<CfgNodeImpl>(2 * allNodes.size());
            visitedNodes = new HashSet<CfgNodeImpl>(2 * allNodes.size());   
            loopMap = new HashMap<CfgNode, LoopInfo>();
        }  
        
        public int getLoopIndex(){         
            return ++loopIndex;           
        }    
    }
      
  
    private void loopDetection(LoopEnv env, CfgNodeImpl root) {  
        for (CfgNodeImpl n : env.allNodes) {
            n.setLoopHeader(false);
            for (CfgEdge e : n.getInputEdges()) {
                CfgEdgeImpl ei = (CfgEdgeImpl) e;        
                ei.setBackEdge(false);       
            }
        }
        visit(env, root, null);
    }
   

    
    private void visit(LoopEnv env, CfgNodeImpl n, CfgEdgeImpl e) {
        if (env.activeNodes.contains(n)) {
            // This node is b loop header!
            n.setLoopHeader(true);
            e.setBackEdge(true);
        } else if (!env.visitedNodes.contains(n)) {
            env.visitedNodes.add(n);
            env.activeNodes.add(n);
            
            for (CfgEdge edge : n.getOutputEdges()) {               
                if(!edge.getTargetNode().isOSR()) {
                    CfgEdgeImpl ei = (CfgEdgeImpl) edge;
                    CfgNodeImpl ni = (CfgNodeImpl) edge.getTargetNode();
                    visit(env, ni, ei);
                }
            }
            env.activeNodes.remove(n);
        }
    }
    
    
    
    private void calcLoopDepth(LoopEnv env) {
        for (CfgNodeImpl n : env.allNodes) {
            env.visitedNodes.clear();

            if (n.isLoopHeader()) {
                LoopInfo loop = new LoopInfo();
                loop.setHeader(n);
                n.setLoopIndex(env.getLoopIndex());
                HashSet<CfgNode> members = new HashSet<CfgNode>();
                loop.setMembers(members);
                members.add(n);               
                env.loopMap.put(loop.getHeader(), loop);
                int loopDepth = n.getLoopDepth() + 1;
                loop.setLoopDepth(loopDepth);
                n.setLoopDepth(loopDepth);
                for (CfgEdge e : n.getInputEdges())  {
                    if (e.isBackEdge() && !e.getSourceNode().isOSR()) {
                        CfgNodeImpl src = (CfgNodeImpl) e.getSourceNode();
                        backwardIteration(env, n, src, loop);               
                        loop.getBackEdges().add(e);
                    }
                }
            }
        }
    }
  

    private void backwardIteration(LoopEnv env, CfgNodeImpl endNode, CfgNodeImpl n, LoopInfo loop) {
        if (endNode != n && !env.visitedNodes.contains(n)) {
            env.visitedNodes.add(n);

            for (CfgEdge e : n.getInputEdges()) {
                if (!e.getSourceNode().isOSR()) {
                    CfgNodeImpl src = (CfgNodeImpl) e.getSourceNode();
                    backwardIteration(env, endNode, src, loop);
                }
            }
            loop.getMembers().add(n); 
            n.setLoopDepth(n.getLoopDepth() + 1);
        }
    }
    
    private void setNodeLevels(CfgNode rootNode){
        Set<CfgNode> cache = new HashSet<CfgNode>();
        Queue<CfgNode> queue = new LinkedList<CfgNode>();
        queue.add(rootNode);
        cache.add(rootNode);
        int level=0;
        int[] nodeCount = new int[2];
        nodeCount[0]=1;
        while(!queue.isEmpty()){           
            CfgNodeImpl curNode = (CfgNodeImpl) queue.poll();
            curNode.setLevel(level);                 
            nodeCount[0]--;       
            for(CfgEdge outEdge : curNode.getOutputEdges()) {
                CfgNode succNode = outEdge.getTargetNode();
                if(cache.contains(succNode)) continue;
                cache.add(succNode);
                queue.add(succNode);
                nodeCount[1]++;
            }
            if(nodeCount[0]==0){
                nodeCount[0]=nodeCount[1];
                nodeCount[1]=0;
                level++;
            }
        }               
    }

    public ControlFlowGraph getCfg() {
        return cfg;
    }

}
