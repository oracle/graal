package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.Uninterruptible;
import org.graalvm.word.WordFactory;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.SizeOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import org.graalvm.nativeimage.IsolateThread;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.UnsignedUtils;
public class JfrBufferNodeLinkedList {
    @RawStructure
    public interface JfrBufferNode extends UninterruptibleEntry {
        @RawField
        JfrBuffer getValue();
        @RawField
        void setValue(JfrBuffer value);

        @RawField
        IsolateThread getThread();
        @RawField
        void setThread(IsolateThread thread);

        @RawField
        boolean getAlive();
        @RawField
        void setAlive(boolean alive);
        @RawField
        int getAcquired();

        @RawField
        void setAcquired(int value);

        @RawFieldOffset
        static int offsetOfAcquired() {
            throw VMError.unimplemented(); // replaced
        }
        @RawField
        <T extends UninterruptibleEntry> T getPrev();

        @RawField
        void setPrev(UninterruptibleEntry value);
    }
    private volatile JfrBufferNode head;
    private  JfrBufferNode tail; // this never gets deleted

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public JfrBufferNode getAndLockTail() {
        VMError.guarantee(tail.isNonNull(), "^^116");
        if (tryAcquire(tail)) {
            return tail;
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean tryAcquire(JfrBufferNode node) {
        for (int retry = 0; retry < 10000; retry++){
            if (node.isNull() || acquire(node)) {
                return true;
            }
        }
        VMError.guarantee(false, "^^^111");
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isTail(JfrBufferNode node) {
        return node == tail;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isHead(JfrBufferNode node) {
        return node == head;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setHead(JfrBufferNode node) {
        VMError.guarantee(isAcquired(head) || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^11");//assert !acquire();
        head = node;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static org.graalvm.word.UnsignedWord getHeaderSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(JfrBufferNode.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static JfrBufferNode createNode(JfrBuffer buffer, IsolateThread thread){
        JfrBufferNode node = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(getHeaderSize());
        node.setAlive(true);
        node.setValue(buffer);
        node.setThread(thread);
        node.setPrev(WordFactory.nullPointer());
        node.setNext(WordFactory.nullPointer());
        node.setAcquired(0);
        return node;
    }
    public JfrBufferNodeLinkedList(){
        tail = createNode(WordFactory.nullPointer(), WordFactory.nullPointer());
        head = tail;
    }

    public void teardown(){
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(tail);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean lockSection(JfrBufferNode target) {
        VMError.guarantee(target.isNonNull(), "^^^84");
        // acquire target and adjacent nodes
        if(acquire(target)){
            if (target.getPrev().isNull() || acquire(target.getPrev())) {
                if (target.getNext().isNull() || acquire(target.getNext())) {
                    return true;
                }
                // couldn't acquire all three locks. So release all of them.
                if (target.getPrev().isNonNull()) {
                    release(target.getPrev());
                }
            }
            release(target);
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean lockAdjacent(JfrBufferNode target) {
        VMError.guarantee(target.isNonNull(), "^^^85");
        // acquire target and adjacent nodes
        if (target.getPrev().isNull() || acquire(target.getPrev())) {
            if (target.getNext().isNull() || acquire(target.getNext())) {
                return true;
            }
            // couldn't acquire all three locks. So release all of them.
            if (target.getPrev().isNonNull()) {
                release(target.getPrev());
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean removeNode(JfrBufferNode node, boolean flushing){
        JfrBufferNode next = node.getNext(); // next can never be null
        JfrBufferNode prev = node.getPrev();
        VMError.guarantee(next.isNonNull(), "^^^89"); //tail must always exist until torn down
        VMError.guarantee(head.isNonNull(), "^^^8");

        // make one attempt to get all the locks. If flushing, only target nodes already acquired.
        if (flushing && !com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() && !lockAdjacent(node)) {
            return false;
        }
        VMError.guarantee((isAcquired(node) && isAcquired(next)) || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^22");//assert !acquire();
        if (isHead(node)) {
            VMError.guarantee(prev.isNull(), "^^^96");
            setHead(next); // head could now be tail if there was only one node in the list
            VMError.guarantee(isAcquired(head) || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint(), "^^^97");
            head.setPrev(WordFactory.nullPointer());
        }  else {
            VMError.guarantee( isAcquired(prev) || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^90");//assert !acquire();
            prev.setNext(next);
            next.setPrev(prev);
        }
        VMError.guarantee(prev != next, "^^^92");

        // Free LL node holding buffer
        VMError.guarantee(node.getValue().isNonNull(), "^^^9");
        JfrBufferAccess.free(node.getValue());
        release(node); //is this necessary?
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);

        // release existing locks
        release(next);
        if (prev.isNonNull()) {
            release(prev);
        }
        return true;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void addNode(JfrBufferNode node){
        VMError.guarantee(!com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint(), "^^^127");
        int count =0;
        while(!acquire(head)) { // *** need infinite tries.
            count++;
            VMError.guarantee(count < 100000, "^^^23");
        }
        JfrBufferNode oldHead = head;
        node.setPrev(WordFactory.nullPointer());

        VMError.guarantee(head.getPrev().isNull(), "^^^83");
        node.setNext(head);
        head.setPrev(node);
        head = node;
        VMError.guarantee(oldHead == head.getNext(), "^^^114");
        release(head.getNext());
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static boolean acquire(JfrBufferNode node) {
        if (com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint()) {
            VMError.guarantee(!isAcquired(node), "^^^100");
            return true;
        }
        return ((org.graalvm.word.Pointer) node).logicCompareAndSwapInt(JfrBufferNode.offsetOfAcquired(), 0, 1, org.graalvm.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION);
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static void release(JfrBufferNode node) {
        if (com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint()) {
            VMError.guarantee(!isAcquired(node), "^^^101");
            return;
        }
        VMError.guarantee(isAcquired(node), "^^^10");
        node.setAcquired(0);
    }
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAcquired(JfrBufferNode node) {
        return node.getAcquired() == 1;
    }
}