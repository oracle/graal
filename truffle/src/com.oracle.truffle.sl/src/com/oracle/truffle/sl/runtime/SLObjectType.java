/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.sl.SLLanguage;

/**
 * This class defines operations that can be performed on SL Objects. While we could define all
 * these operations as individual AST nodes, We opted to define those operations by using
 * {@link com.oracle.truffle.api.library.Library a Truffle library}, or more concretely the
 * {@link InteropLibrary}. This has several advantages, but the primary one is that it allows SL
 * objects to be used in the interoperability message protocol, i.e. It allows other languages and
 * tools to operate on SL objects without necessarily knowing they are SL objects.
 *
 * SL Objects are essentially instances of {@link DynamicObject} (objects whose members can be
 * dynamically added and removed). Our {@link SLObjectType} class thus extends {@link ObjectType} an
 * extensible object type descriptor for {@link DynamicObject}s. We also annotate the class with
 * {@link ExportLibrary} with value {@link InteropLibrary InteropLibrary.class} and receiverType
 * {@link DynamicObject DynamicObject.class}. This essentially ensures that the build system and
 * runtime know that this class specifies the interop messages (i.e. operations) that SL can do on
 * {@link DynamicObject} instances.
 *
 * {@see ExportLibrary} {@see ExportMessage} {@see InteropLibrary}
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = DynamicObject.class)
public final class SLObjectType extends ObjectType {

    protected static final int CACHE_LIMIT = 3;
    public static final ObjectType SINGLETON = new SLObjectType();

    private SLObjectType() {
    }

    @Override
    public Class<?> dispatch() {
        return SLObjectType.class;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasLanguage(DynamicObject receiver) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Class<? extends TruffleLanguage<?>> getLanguage(DynamicObject receiver) {
        return SLLanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doSLObject(DynamicObject receiver, DynamicObject other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(DynamicObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(DynamicObject receiver) {
        return System.identityHashCode(receiver);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasMetaObject(DynamicObject receiver) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Object getMetaObject(DynamicObject receiver) {
        return SLType.OBJECT;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("unused")
    static Object toDisplayString(DynamicObject receiver, @SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasMembers(DynamicObject receiver) {
        return true;
    }

    @ExportMessage
    static boolean removeMember(DynamicObject receiver, String member) throws UnknownIdentifierException {
        if (receiver.containsKey(member)) {
            return receiver.delete(member);
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static class GetMembers {

        @Specialization(guards = "receiver.getShape() == cachedShape")
        static Keys doCached(DynamicObject receiver, boolean includeInternal, //
                        @Cached("receiver.getShape()") Shape cachedShape, //
                        @Cached(value = "doGeneric(receiver, includeInternal)", allowUncached = true) Keys cachedKeys) {
            return cachedKeys;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Keys doGeneric(DynamicObject receiver, boolean includeInternal) {
            return new Keys(receiver.getShape().getKeyList().toArray());
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    @SuppressWarnings("unused")
    static class ExistsMember {

        @Specialization(guards = {"receiver.getShape() == cachedShape", "cachedMember.equals(member)"})
        static boolean doCached(DynamicObject receiver, String member,
                        @Cached("receiver.getShape()") Shape cachedShape,
                        @Cached("member") String cachedMember,
                        @Cached("doGeneric(receiver, member)") boolean cachedResult) {
            assert cachedResult == doGeneric(receiver, member);
            return cachedResult;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static boolean doGeneric(DynamicObject receiver, String member) {
            return receiver.getShape().getProperty(member) != null;
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isMemberInsertable(DynamicObject receiver, String member,
                    @CachedLibrary("receiver") InteropLibrary receivers) {
        return !receivers.isMemberExisting(receiver, member);
    }

    static boolean shapeCheck(Shape shape, DynamicObject receiver) {
        return shape != null && shape.check(receiver);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        private final Object[] keys;

        Keys(Object[] keys) {
            this.keys = keys;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return keys[(int) index];
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }
    }

    /**
     * Since reading a member is potentially expensive (results in a truffle boundary call) if the
     * node turns megamorphic (i.e. cache limit is exceeded) we annotate it with
     * {@ReportPolymorphism}. This ensures that the runtime is notified when this node turns
     * polymorphic. This, in turn, may, under certain conditions, cause the runtime to attempt to
     * make node monomorphic again by duplicating the entire AST containing that node and
     * specialising it for a particular call site.
     *
     * {@see ReportPolymorphism}
     */
    @GenerateUncached
    @ExportMessage
    @ReportPolymorphism
    abstract static class ReadMember {

        /**
         * Polymorphic inline cache for a limited number of distinct property names and shapes.
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "receiver.getShape() == cachedShape",
                                        "cachedName.equals(name)"
                        }, //
                        assumptions = "cachedShape.getValidAssumption()")
        static Object readCached(DynamicObject receiver, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @Cached("receiver.getShape()") Shape cachedShape,
                        @Cached("lookupLocation(cachedShape, name)") Location location) {
            return location.get(receiver, cachedShape);
        }

        static Location lookupLocation(Shape shape, String name) throws UnknownIdentifierException {
            /* Initialization of cached values always happens in a slow path. */
            CompilerAsserts.neverPartOfCompilation();

            Property property = shape.getProperty(name);
            if (property == null) {
                /* Property does not exist. */
                throw UnknownIdentifierException.create(name);
            }

            return property.getLocation();
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(replaces = {"readCached"}, guards = "receiver.getShape().isValid()")
        static Object readUncached(DynamicObject receiver, String name) throws UnknownIdentifierException {
            Object result = receiver.get(name);
            if (result == null) {
                /* Property does not exist. */
                throw UnknownIdentifierException.create(name);
            }
            return result;
        }

        @Specialization(guards = "!receiver.getShape().isValid()")
        static Object updateShape(DynamicObject receiver, String name) throws UnknownIdentifierException {
            CompilerDirectives.transferToInterpreter();
            receiver.updateShape();
            return readUncached(receiver, name);
        }
    }

    /**
     * Since writing a member is potentially expensive (results in a truffle boundary call) if the
     * node turns megamorphic (i.e. cache limit is exceeded) we annotate it with
     * {@ReportPolymorphism}. This ensures that the runtime is notified when this node turns
     * polymorphic. This, in turn, may, under certain conditions, cause the runtime to attempt to
     * make node monomorphic again by duplicating the entire AST containing that node and
     * specialising it for a particular call site.
     *
     * {@see ReportPolymorphism}
     */
    @GenerateUncached
    @ExportMessage
    @ReportPolymorphism
    abstract static class WriteMember {

        /**
         * Polymorphic inline cache for writing a property that already exists (no shape change is
         * necessary).
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, receiver)",
                                        "location != null",
                                        "canSet(location, value)"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        static void writeExistingPropertyCached(DynamicObject receiver, @SuppressWarnings("unused") String name, Object value,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @Cached("receiver.getShape()") Shape shape,
                        @Cached("lookupLocation(shape, name, value)") Location location) {
            try {
                location.set(receiver, value, shape);

            } catch (IncompatibleLocationException | FinalLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /**
         * Polymorphic inline cache for writing a property that does not exist yet (shape change is
         * necessary).
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "receiver.getShape() == oldShape",
                                        "oldLocation == null",
                                        "canStore(newLocation, value)"
                        }, //
                        assumptions = {
                                        "oldShape.getValidAssumption()",
                                        "newShape.getValidAssumption()"
                        })
        @SuppressWarnings("unused")
        static void writeNewPropertyCached(DynamicObject receiver, String name, Object value,
                        @Cached("name") String cachedName,
                        @Cached("receiver.getShape()") Shape oldShape,
                        @Cached("lookupLocation(oldShape, name, value)") Location oldLocation,
                        @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                        @Cached("lookupLocation(newShape, name)") Location newLocation) {
            try {
                newLocation.set(receiver, value, oldShape, newShape);

            } catch (IncompatibleLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /** Try to find the given property in the shape. */
        static Location lookupLocation(Shape shape, String name) {
            CompilerAsserts.neverPartOfCompilation();

            Property property = shape.getProperty(name);
            if (property == null) {
                /* Property does not exist yet, so a shape change is necessary. */
                return null;
            }

            return property.getLocation();
        }

        /**
         * Try to find the given property in the shape. Also returns null when the value cannot be
         * store into the location.
         */
        static Location lookupLocation(Shape shape, String name, Object value) {
            Location location = lookupLocation(shape, name);
            if (location == null || !location.canSet(value)) {
                /* Existing property has an incompatible type, so a shape change is necessary. */
                return null;
            }

            return location;
        }

        static Shape defineProperty(Shape oldShape, String name, Object value) {
            return oldShape.defineProperty(name, value, 0);
        }

        /**
         * There is a subtle difference between {@link Location#canSet} and
         * {@link Location#canStore}. We need {@link Location#canSet} for the guard of
         * {@link #writeExistingPropertyCached} because there we call {@link Location#set}. We use
         * the more relaxed {@link Location#canStore} for the guard of
         * {@link SLWritePropertyCacheNode#writeNewPropertyCached} because there we perform a shape
         * transition, i.e., we are not actually setting the value of the new location - we only
         * transition to this location as part of the shape change.
         */
        static boolean canSet(Location location, Object value) {
            return location.canSet(value);
        }

        /** See {@link #canSet} for the difference between the two methods. */
        static boolean canStore(Location location, Object value) {
            return location.canStore(value);
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(replaces = {"writeExistingPropertyCached", "writeNewPropertyCached"}, guards = {"receiver.getShape().isValid()"})
        static void writeUncached(DynamicObject receiver, String name, Object value) {
            receiver.define(name, value);
        }

        @TruffleBoundary
        @Specialization(guards = {"!receiver.getShape().isValid()"})
        static void updateShape(DynamicObject receiver, String name, Object value) {
            /*
             * Slow path that we do not handle in compiled code. But no need to invalidate compiled
             * code.
             */
            CompilerDirectives.transferToInterpreter();
            receiver.updateShape();
            writeUncached(receiver, name, value);
        }

    }

}
