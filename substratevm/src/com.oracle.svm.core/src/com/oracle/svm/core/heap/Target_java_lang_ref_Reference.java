/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.heap;

//Checkstyle: allow reflection

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.reflect.Field;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.ExcludeFromReferenceMap;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/* @formatter:off */
/**
 *
 * This class is the plumbing under java.lang.ref.Reference. Instances of this class are discovered
 * by the collector and put on a list. The list can then be inspected to implement
 * java.lang.ref.Reference-like operations.
 *
 * Instances are treated specially by the collector.
 * <ul>
 * <li>The "referent" field will *not* be followed during live object discovery. It is a Pointer,
 * not an Object.</li>
 * <li>During live object discovery, each DiscoverableReference will be added to the "discovered"
 * list.</li>
 * <li>If the object referenced by the referent field is not live at the end of a collection, the
 * "referent" field will be set to null.</li>
 * <li>If the object referenced by the referent field is live at the end of a collection, the
 * referent field is updated if the object moved.</li>
 * <li>If the object referenced by the referent field is live at the end of a collection, the
 * DiscoverableReference will be removed from the discovered list.</li>
 * </ul>
 * After a collection, anyone who is interested can run down the discovered list and do something
 * with each DiscoverableReference.
 *
 * On top of this, I think you could build PhantomReference and WeakReference classes by adding a
 * getReferent() method that returned the referent as an Object. See
 * {@linkplain Target_java_lang_ref_Reference}, {@linkplain Target_java_lang_ref_ReferenceQueue}.
 * Reference classes that subsequently promote their referent (SoftReference and FinalReference)
 * would be more complicated.
 *
 * All the state that is needed to build lists, etc., is allocated in the DiscoverableReference
 * instances, because space can not be allocated for lists during a collection.

 * This is missing the notification methodsof java.lang.ref.Reference, but has list methods.
 *
 * Fields for putting an instance on a list is here in the instance, because space can't be
 * allocated during a collection. But they should only be used by list-manipulating classes.
 *
 * <p>
 * The lifecycle of a FeebleReference:
 * <ul>
 *   <li>If the FeebleReference does not have a list:
 *     <table>
 *       <tr>  <th align=left>Stage</th>  <th>.referent</th>      <th>.list</th>  <th>.next</th>  </tr>
 *       <tr>  <td>At construction</td>   <td>referent</td>       <td>null</td>   <td>this</td>   </tr>
 *       <tr>  <td>At discovery</td>      <td><em>null</em></td>  <td>null</td>   <td>this</td>   </tr>
 *     </table>
 *   </li>
 *   <li>If the FeebleReference does have a list:</li>
 *     <table>
 *       <tr>  <th align=left>Stage</th>  <th>.referent</th>      <th>.list</th>           <th>.next</th>           </tr>
 *       <tr>  <td>At construction</td>   <td>referent</td>       <td>list</td>            <td>this</td>            </tr>
 *       <tr>  <td>At discovery</td>      <td><em>null</em></td>  <td>list</td>            <td>this</td>            </tr>
 *       <tr>  <td>Before pushing</td>    <td>null</td>           <td><em>null</em></td>   <td>this</td>            </tr>
 *       <tr>  <td>After pushing</td>     <td>null</td>           <td>null</td>            <td><em>next</em></td>   </tr>
 *       <tr>  <td>After popping</td>     <td>null</td>           <td>null</td>            <td><em>this</em></td>   </tr>
 *     </table>
 *     Note that after being pushed and popped a FeebleReference with a list
 *     is in the same state as a discovered FeebleReference without a list.
 *   </li>
 * </ul>
 */
/* @formatter:on */
@UnknownClass
@TargetClass(java.lang.ref.Reference.class)
@Substitute
public final class Target_java_lang_ref_Reference<T> {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "rawReferent", declClass = Target_java_lang_ref_Reference.class) //
    private static long rawReferentFieldOffset;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "nextDiscovered", declClass = Target_java_lang_ref_Reference.class) //
    private static long nextDiscoveredFieldOffset;

    /**
     * The list to which this FeebleReference is added when the referent is unreachable. This is
     * initialized and then becomes null when the FeebleReference is put on its list.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeQueueValue.class) //
    protected final UninterruptibleUtils.AtomicReference<Target_java_lang_ref_ReferenceQueue<? super T>> list;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeTrue.class) //
    protected final boolean initialized;

    /**
     * The referent. The declared type must be {@link Object}, so that the static analysis can track
     * reads and writes. However, the field must not be in the regular reference map since we do all
     * the garbage collection support manually. The garbage collector performs Pointer-level access
     * to the field. This is fine from the point of view of the static analysis, since field stores
     * by the garbage collector do not change the type of the referent.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeReferenceValue.class) //
    @ExcludeFromReferenceMap("Field is manually processed by the garbage collector.") //
    protected T rawReferent;

    /**
     * Is this DiscoverableReference on a list?
     * <p>
     * DiscoverableReference does not use the self-link secret of the ancients that FeebleReference
     * uses, because the discovery happens during blackening, so if the DiscoverableReference has
     * been promoted, but the next field has not yet been updated, then this == next fails.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    protected boolean isDiscovered;

    /** The next element in whichever list of DiscoverableReferences. */
    @SuppressWarnings("unused") //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    protected Target_java_lang_ref_Reference<?> nextDiscovered;

    /**
     * The next element in the FeebleReferenceList or null if this FeebleReference is not on a list.
     * <p>
     * If this field points to this instance, then this instance is not on any list.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeReceiverValue.class) //
    private Target_java_lang_ref_Reference<?> nextInList;

    @Substitute
    Target_java_lang_ref_Reference(T referent) {
        this(referent, (Target_java_lang_ref_ReferenceQueue<? super T>) null);
    }

    @Substitute
    Target_java_lang_ref_Reference(T referent, Target_java_lang_ref_ReferenceQueue<? super T> queue) {
        this(referent, new UninterruptibleUtils.AtomicReference<>(queue));
    }

    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    private Target_java_lang_ref_Reference(T referent, UninterruptibleUtils.AtomicReference<Target_java_lang_ref_ReferenceQueue<? super T>> queue) {
        this.rawReferent = referent;
        this.nextDiscovered = null;
        this.isDiscovered = false;
        this.list = queue;
        Target_java_lang_ref_ReferenceQueue.clean(this);
        this.initialized = true;
    }

    @Substitute
    public T get() {
        return rawReferent;
    }

    @Substitute
    public void clear() {
        doClear();
    }

    public void doClear() {
        rawReferent = null;
    }

    @Substitute
    public boolean enqueue() {
        final Target_java_lang_ref_ReferenceQueue<? super T> frList = getList();
        if (frList != null) {
            return frList.push(this);
        }
        return false;
    }

    @Substitute
    public boolean isEnqueued() {
        return isOnList();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOnList() {
        return (nextInList != this);
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings("unused")
    private static boolean tryHandlePending(boolean waitForNotify) {
        throw VMError.unimplemented();
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings("unused")
    private static boolean waitForReferenceProcessing() {
        throw VMError.unimplemented();
    }

    @Override
    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    protected native Object clone() throws CloneNotSupportedException;

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    // @ForceInline
    @SuppressWarnings("unused")
    public static void reachabilityFence(Object ref) {
        GraalDirectives.blackhole(ref);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasList() {
        return (list != null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Target_java_lang_ref_ReferenceQueue<? super T> getList() {
        return list.get();
    }

    /** Clears the list, returning the previous value, which might be null. */
    public Target_java_lang_ref_ReferenceQueue<? super T> clearList() {
        return list.getAndSet(null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Target_java_lang_ref_Reference<?> listGetNext() {
        return nextInList;
    }

    void listPrepend(Target_java_lang_ref_Reference<?> newNext) {
        assert newNext != this : "Creating self-loop.";
        nextInList = newNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void listRemove() {
        nextInList = this;
    }

    public Target_java_lang_ref_Reference<T> prependToDiscoveredReference(Target_java_lang_ref_Reference<?> newNext) {
        setNextDiscoveredReference(newNext, true);
        return this;
    }

    /**
     * Is this instance initialized?
     *
     * This seems like a funny method to have, because by the time I could see an instance class
     * from ordinary Java code it would be initialized. But the collector might see a reference to
     * an instance between when it is allocated and when it is initialized, and so must be able to
     * detect if the fields of the instance are safe to access. The constructor is
     * unininterruptible, so the collector either sees an uninitialized instance or fully
     * initialized instance.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Read access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public Pointer getReferentPointer() {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(this, WordFactory.signed(rawReferentFieldOffset)));
    }

    /**
     * Write access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public void setReferentPointer(Pointer value) {
        ObjectAccess.writeObject(this, WordFactory.signed(rawReferentFieldOffset), value.toObject());
    }

    /**
     * Read access to the next field. Must use ObjectAccess to read the field because it is written
     * with ObjectAccess only.
     */
    public Target_java_lang_ref_Reference<?> getNextDiscoveredReference() {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(this, WordFactory.signed(nextDiscoveredFieldOffset)), Target_java_lang_ref_Reference.class);
    }

    /**
     * Write access to the next field. Must use ObjectAccess to bypass the write barrier.
     */
    private void setNextDiscoveredReference(Target_java_lang_ref_Reference<?> newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(this, WordFactory.signed(nextDiscoveredFieldOffset), newNext);
        isDiscovered = newIsDiscovered;
    }

    public boolean getIsDiscovered() {
        return isDiscovered;
    }

    public void cleanDiscovered() {
        setNextDiscoveredReference(null, false);
    }

    /**
     * Read access to the next field, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public Pointer getNextDiscoveredRefPointer() {
        return Word.objectToUntrackedPointer(this).add(WordFactory.signed(nextDiscoveredFieldOffset));
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        @NeverInline("Prevent the access from moving around")
        public static Pointer getReferentPointer(Reference<?> that) {
            return SubstrateUtil.cast(that, Target_java_lang_ref_Reference.class).getReferentPointer();
        }

        public static Reference<?> getNextDiscoveredReference(Reference<?> that) {
            Target_java_lang_ref_Reference<?> cast = SubstrateUtil.cast(that, Target_java_lang_ref_Reference.class);
            Target_java_lang_ref_Reference<?> next = cast.getNextDiscoveredReference();
            return SubstrateUtil.cast(next, Reference.class);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeReferenceValue implements CustomFieldValueComputer {

    private static final Field REFERENT_FIELD = ReflectionUtil.lookupField(Reference.class, "referent");

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        if (receiver instanceof PhantomReference) {
            /*
             * PhantomReference does not allow access to its object, so it is mostly useless to have
             * a PhantomReference on the image heap. But some JDK code uses it, e.g., for marker
             * values, so we cannot disallow PhantomReference for the image heap.
             */
            return null;
        }
        try {
            /*
             * Some subclasses of Reference overwrite Reference.get() to throw an error. Therefore,
             * we need to access the field directly using reflection.
             */
            return REFERENT_FIELD.get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeQueueValue implements CustomFieldValueComputer {

    private static final Field QUEUE_FIELD = ReflectionUtil.lookupField(Reference.class, "queue");

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        try {
            return new UninterruptibleUtils.AtomicReference<>(QUEUE_FIELD.get(receiver));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeReceiverValue implements CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return receiver;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeTrue implements CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return true;
    }
}
