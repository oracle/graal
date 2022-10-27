package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.Uninterruptible;
import org.graalvm.word.WordFactory;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;

public class JfrBufferNodeLinkedList {
    private JfrThreadLocal.JfrBufferNode head;
    JfrThreadLocal.JfrBufferNode lock; // TODO: remember to clean this up
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public boolean isAcquired() {
        return isAcquired(lock);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public boolean acquire() {
//        return lock.logicCompareAndSwapWord(0, WordFactory.nullPointer(), WordFactory.pointer(1), NamedLocationIdentity.OFF_HEAP_LOCATION);
        return acquire(lock);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    public void release() {
//        com.oracle.svm.core.util.VMError.guarantee(!acquire(), "^^^13");//assert !acquire();
        com.oracle.svm.core.util.VMError.guarantee(lock.getAcquired()==1, "^^^26");
        release(lock);
//        boolean result = lock.logicCompareAndSwapWord(0, WordFactory.pointer(1), WordFactory.nullPointer(), NamedLocationIdentity.OFF_HEAP_LOCATION);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public JfrThreadLocal.JfrBufferNode getHead() {
        com.oracle.svm.core.util.VMError.guarantee(lock.getAcquired()==1|| com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^12");//assert !acquire();
        return head;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setHead(JfrThreadLocal.JfrBufferNode node) {
        com.oracle.svm.core.util.VMError.guarantee(lock.getAcquired()==1 || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^11");//assert !acquire();
        head = node;
    }

    private static org.graalvm.word.UnsignedWord getHeaderSize() {
//        return com.oracle.svm.core.util.UnsignedUtils.roundUp(WordFactory.unsigned(1), WordFactory.unsigned(com.oracle.svm.core.config.ConfigurationValues.getTarget().wordSize));
        return com.oracle.svm.core.util.UnsignedUtils.roundUp(org.graalvm.nativeimage.c.struct.SizeOf.unsigned(JfrThreadLocal.JfrBufferNode.class), WordFactory.unsigned(com.oracle.svm.core.config.ConfigurationValues.getTarget().wordSize));
    }
    public JfrBufferNodeLinkedList(){
        head = WordFactory.nullPointer();
        lock = org.graalvm.nativeimage.ImageSingletons.lookup(org.graalvm.nativeimage.impl.UnmanagedMemorySupport.class).malloc(getHeaderSize());
//        lock =  ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(getHeaderSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void removeNode(JfrThreadLocal.JfrBufferNode prev, JfrThreadLocal.JfrBufferNode node){

        JfrThreadLocal.JfrBufferNode next = node.getNext(); // next could be null if node is tail
        com.oracle.svm.core.util.VMError.guarantee(head.isNonNull(), "^^^8");//assert head.isNonNull();
        if (node == head) {
            com.oracle.svm.core.util.VMError.guarantee(lock.getAcquired()==1 || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^22");//assert !acquire();
            assert prev.isNull();
            setHead(next); // head could now be null if there was only one node in the list
        }  else {
            prev.setNext(next); // prev could now be "tail" if current was tail
        }

        // Free LL node holding buffer
        com.oracle.svm.core.util.VMError.guarantee(node.getValue().isNonNull(), "^^^9");//assert node.getValue().isNonNull();
        JfrBufferAccess.free(node.getValue());
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void addNode(JfrThreadLocal.JfrBufferNode node){
        int count =0;
        while(!acquire()) { // *** need infinite tries.
            count++;
            com.oracle.svm.core.util.VMError.guarantee(count < 1000, "^^^23");
        }
            if (head.isNull()){
                node.setNext(WordFactory.nullPointer());
                head = node;
                release();
                return;
            }
        node.setNext(head);
        head = node;
        release();
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static boolean acquire(JfrThreadLocal.JfrBufferNode buffer) {
        return ((org.graalvm.word.Pointer) buffer).logicCompareAndSwapInt(JfrThreadLocal.JfrBufferNode.offsetOfAcquired(), 0, 1, org.graalvm.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION);
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static void release(JfrThreadLocal.JfrBufferNode buffer) {
        com.oracle.svm.core.util.VMError.guarantee(buffer.getAcquired() == 1, "^^^10");//assert buffer.getAcquired() == 1;
        buffer.setAcquired(0);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAcquired(JfrThreadLocal.JfrBufferNode buffer) {
        return buffer.getAcquired() == 1;
    }
}