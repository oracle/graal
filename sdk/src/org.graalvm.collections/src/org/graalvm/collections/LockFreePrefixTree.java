package org.graalvm.collections;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class LockFreePrefixTree {
   public static class Node extends AtomicLong {

       private static class LinearChildren extends  AtomicReferenceArray<Node>{
           LinearChildren(int length){
               super(length);
           }

       }

       private static class HashChildren extends AtomicReferenceArray<Node>{
           HashChildren(int length){
               super(length);
           }
       }

       private static class FrozenNode extends Node{
            FrozenNode(){
                super(0);
            }
       }


       //Requires: INITIAL_HASH_NODE_SIZE >= MAX_LINEAR_NODE_SIZE -> otherwise we have an endless loop
       private static final int INITIAL_LINEAR_NODE_SIZE = 3;
       private static final int INITIAL_HASH_NODE_SIZE = 20;
       private static final int MAX_LINEAR_NODE_SIZE = 6;
       private  static final int MAX_HASH_SKIPS = 10;



       private static final AtomicReferenceFieldUpdater<Node,AtomicReferenceArray>childrenUpdater=AtomicReferenceFieldUpdater.newUpdater(Node.class, AtomicReferenceArray .class, "children");

       private  final long key;

       private volatile AtomicReferenceArray<Node>  children;

//       public static final ThreadLocal<Integer> count = ThreadLocal.withInitial(() -> new Integer(0));
//
//       public static final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();


       public Node(long key) {
           this.key = key;
       }

       public long value() {
            return get();
        }

        public long getKey(){
           return this.key;
        }

        public void setValue(long value) {
            set(value);
        }
        public long incValue() {
//           count.set(count.get() + 1);
//           queue.add(Thread.currentThread().getName() + " -> " + this.key + " " + System.identityHashCode(this) );
            return incrementAndGet();
        }



       @SuppressWarnings("unchecked")
       public Node at(long key){
           ensureChildren();

           while(true){
               AtomicReferenceArray<Node>  children0 = readChildren();
               if(children0 instanceof LinearChildren) {
                   // Find first empty slot.
                   Node newChild = tryAppendChild(key, children0);
                   if (newChild != null) {
                       return newChild;
                   }else{
                       //Children array is full, we need to  resize.
                        tryResizeLinear(children0);
                   }

               }else{
                   //Children0 instanceof HashChildren.
                    Node newChild = tryAddChildToHash(key,children0);
                    if(newChild != null){
                        return newChild;
                    }else{
                        growHash();
                    }
               }
           }
       }

       //Precondition: childrenArray is full.
       private void tryResizeLinear(AtomicReferenceArray<Node> childrenArray) {

           AtomicReferenceArray<Node> newChildrenArray;
           if(childrenArray.length() <  MAX_LINEAR_NODE_SIZE){
               newChildrenArray = new LinearChildren(2 * childrenArray.length());
               for (int i = 0; i < childrenArray.length(); i++) {
                   newChildrenArray.set(i, childrenArray.get(i));
               }
           }
           else{
               newChildrenArray = new HashChildren(INITIAL_HASH_NODE_SIZE);
               for(int i = 0; i < childrenArray.length();++i){
                   addChildToFreshHash(childrenArray.get(i),newChildrenArray);
               }
           }

           childrenUpdater.compareAndSet(this,childrenArray,newChildrenArray);
       }

       private Node tryAddChildToHash(long key, AtomicReferenceArray<Node> hash) {
           int index = hash(key) % hash.length();
           int skips = 0;

           while(true){
               if(hash.get(index) == null){
                   Node newNode = new Node(key);
                   if(cas(hash,index,null, newNode)) {
                       return newNode;
                   }else{
                       //Rechecks same index spot if the node has been inserted by other thread.
                       continue;
                   }
               }
               else if(hash.get(index).getKey() == key){
                   return hash.get(index);
               }

               index++;
               skips++;
               if(index >= hash.length() || skips > MAX_HASH_SKIPS){
                   //Two cases for growth: (1) We have to wrap around the array, (2) the MAX_HASH_SKIPS have been exceeded.
                   //Returning null automatically triggers a hash growth.
                   return null;
               }
           }
       }


       //This method can only get called in the grow hash function, or when converting from linear to hash, meaning it is only exposed to a SINGLE thread
       //Precondition: hash is empty, reachable from exactly  one thread
       private void addChildToFreshHash(Node node,AtomicReferenceArray<Node> hash){
           int index = hash(node.key) % hash.length();
            while(hash.get(index) != null){
                index = (index + 1) % hash.length() ;
            }
            hash.set(index, node);
       }


       private void growHash() {
           AtomicReferenceArray<Node> childrenHash = (AtomicReferenceArray<Node>) children;
           freezeHash(childrenHash);
           AtomicReferenceArray<Node> newChildrenHash = new HashChildren(2 * childrenHash.length());
           for (int i = 0; i < childrenHash.length(); ++i) {
               Node toCopy = childrenHash.get(i);
               assert(toCopy != null);

               if(!(toCopy instanceof  FrozenNode) ){
                    addChildToFreshHash(toCopy,newChildrenHash);
               }

           }
           casChildren(childrenHash,newChildrenHash);
       }


       private void freezeHash(AtomicReferenceArray<Node> childrenHash) {
           for (int i = 0; i < childrenHash.length() ; ++i) {
                cas(childrenHash,i,null,new FrozenNode());
           }
       }

       private Node tryAppendChild(long key, AtomicReferenceArray<Node> childrenArray) {
           for (int i = 0; i < childrenArray.length(); i++) {
               Node child = read(childrenArray,i);
               if(child == null){
                   Node newChild = new Node(key);
                   if(cas(childrenArray,i,null,newChild)){
                       return newChild;
                   }
                   else{
                       // We need to check if the failed CAS was due to another thread inserting this key.
                       i--;
                       continue;
                   }
               }
               else if (child.getKey() == key){
                   return child;
               }
           }
           // Array is full, triggers resize.
           return null;
       }

       private boolean cas(AtomicReferenceArray<Node> childrenArray, int i, Node expected, Node updated) {
           return childrenArray.compareAndSet(i,expected,updated);
       }

       private Node read(AtomicReferenceArray<Node> childrenArray, int i) {
           return childrenArray.get(i);
       }

       private void ensureChildren() {
           if(readChildren() == null){
               AtomicReferenceArray<Node> newChildren = new LinearChildren(INITIAL_LINEAR_NODE_SIZE);
               casChildren(null, newChildren);
           }

       }

       private boolean casChildren(AtomicReferenceArray<Node>  expected, AtomicReferenceArray<Node>  updated) {
           return childrenUpdater.compareAndSet(this, expected, updated);
       }

       private AtomicReferenceArray<Node>  readChildren() {
           return children;
       }

        private static int hash(long key) {
            long v = key * 0x9e3775cd9e3775cdL;
            v = Long.reverseBytes(v);
            v = v * 0x9e3775cd9e3775cdL;
            return 0x7fff_ffff & (int) (v ^ (v >> 32));
        }

       @Override
        public String toString() {
            return "Node<" + value() + ">";
        }
   }



   private Node root;

   public LockFreePrefixTree(){
       this.root = new Node(0);
   }
   public Node root() {
       return root;
   }
}

