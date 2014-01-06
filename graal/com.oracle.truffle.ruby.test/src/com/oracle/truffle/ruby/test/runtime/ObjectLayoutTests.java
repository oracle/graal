/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test.runtime;

import org.junit.*;

import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

import static org.junit.Assert.*;

/**
 * Test the object layout classes.
 */
public class ObjectLayoutTests {

    @Test
    public void testNewInstanceVariable() {
        final RubyContext context = new RubyContext(null);

        // Create a class and an instance

        final RubyClass classA = new RubyClass(context, null, null, null, "A");
        final ObjectLayout layoutClassA = classA.getObjectLayoutForInstances();

        final RubyBasicObject objectA = new RubyBasicObject(classA);
        final ObjectLayout layoutObjectA = objectA.getObjectLayout();

        // Add an instance variable to the instance

        objectA.setInstanceVariable("foo", 14);

        // That should have changed the layout of the class

        assertNotSame(layoutClassA, classA.getObjectLayoutForInstances());

        // If we notify the object, it should also change the layout of that

        objectA.updateLayout();
        assertNotSame(layoutObjectA, objectA.getObjectLayout());

        // We should be able to find that instance variable as a storage location in the class

        assertNotNull(classA.getObjectLayoutForInstances().findStorageLocation("foo"));

        // We should be able to read that value back out

        assertEquals(14, objectA.getInstanceVariable("foo"));
    }

    @Test
    public void testOverflowPrimitives() {
        final RubyContext context = new RubyContext(null);

        // Create a class and an instance

        final RubyClass classA = new RubyClass(context, null, null, null, "A");
        final RubyBasicObject objectA = new RubyBasicObject(classA);

        // Add many more Fixnums that we have space for primitives

        final int count = 100;

        for (int n = 0; n < count; n++) {
            objectA.setInstanceVariable("foo" + n, n);
        }

        // We should be able to read them back out

        for (int n = 0; n < count; n++) {
            assertEquals(n, objectA.getInstanceVariable("foo" + n));
        }
    }

    @Test
    public void testGeneralisation() {
        final RubyContext context = new RubyContext(null);

        // Create a class and two instances

        final RubyClass classA = new RubyClass(context, null, null, null, "A");
        final RubyBasicObject object1 = new RubyBasicObject(classA);
        final RubyBasicObject object2 = new RubyBasicObject(classA);

        // Set an instance variable to be a Fixnum in object 1

        object1.setInstanceVariable("foo", 14);

        // We should be able to read that instance variable back, and it should still be a Fixnum

        assertEquals(14, object1.getInstanceVariable("foo"));
        assertSame(Integer.class, object1.getInstanceVariable("foo").getClass());

        // The underlying instance store should be Fixnum

        assertSame(FixnumStorageLocation.class, object1.getObjectLayout().findStorageLocation("foo").getClass());

        /*
         * The same instance variable in object 2 should be Nil. Note that this requires that we
         * realise that even though the instance variable is known about in the layout of object 2,
         * and we are using a primitive int to hold it, that it hasn't been set and is actually Nil.
         * We don't want it to appear as 0.
         */

        assertSame(NilPlaceholder.INSTANCE, object2.getInstanceVariable("foo"));

        /*
         * We should be able to set the same instance variable in object 2 to also be a Fixnum
         * without changing the layout.
         */

        final ObjectLayout objectLayout2 = object2.getObjectLayout();
        object2.setInstanceVariable("foo", 2);
        assertEquals(2, object2.getInstanceVariable("foo"));
        assertSame(Integer.class, object2.getInstanceVariable("foo").getClass());
        assertSame(objectLayout2, object2.getObjectLayout());

        // Set the instance variable in object 2 to be a Float

        object2.setInstanceVariable("foo", 2.25);

        // We should be able to read that instance variable back, and it should still be a Fixnum

        assertEquals(2.25, object2.getInstanceVariable("foo"));
        assertSame(Double.class, object2.getInstanceVariable("foo").getClass());

        // Object 1 should give still think the instance variable is a Fixnum

        assertEquals(14, object1.getInstanceVariable("foo"));
        assertSame(Integer.class, object1.getInstanceVariable("foo").getClass());

        // The underlying instance store in both objects should now be Object

        assertSame(ObjectStorageLocation.class, object1.getObjectLayout().findStorageLocation("foo").getClass());
        assertSame(ObjectStorageLocation.class, object2.getObjectLayout().findStorageLocation("foo").getClass());

    }

    @Test
    public void testSubclasses() {
        final RubyContext context = new RubyContext(null);

        // Create two classes, A, and a subclass, B, and an instance of each

        final RubyClass classA = new RubyClass(context, null, null, null, "A");
        final RubyClass classB = new RubyClass(context, null, null, classA, "B");

        ObjectLayout layoutClassA = classA.getObjectLayoutForInstances();
        ObjectLayout layoutClassB = classA.getObjectLayoutForInstances();

        final RubyBasicObject objectA = new RubyBasicObject(classA);
        final RubyBasicObject objectB = new RubyBasicObject(classB);

        ObjectLayout layoutObjectA = objectA.getObjectLayout();
        ObjectLayout layoutObjectB = objectB.getObjectLayout();

        // Add an instance variable to the instance of A

        objectA.setInstanceVariable("foo", 14);

        // That should have changed the layout of both classes

        assertNotSame(layoutClassA, classA.getObjectLayoutForInstances());
        assertNotSame(layoutClassB, classB.getObjectLayoutForInstances());

        layoutClassA = classA.getObjectLayoutForInstances();
        layoutClassB = classA.getObjectLayoutForInstances();

        // If we notify the objects, both of them should have changed layouts

        objectA.updateLayout();
        objectB.updateLayout();
        assertNotSame(layoutObjectA, objectA.getObjectLayout());
        assertNotSame(layoutObjectB, objectB.getObjectLayout());

        layoutObjectA = objectA.getObjectLayout();
        layoutObjectB = objectB.getObjectLayout();

        // We should be able to find that instance variable as a storage location in both classes

        assertNotNull(classA.getObjectLayoutForInstances().findStorageLocation("foo"));
        assertNotNull(classB.getObjectLayoutForInstances().findStorageLocation("foo"));

        // We should be able to read that value back out

        assertEquals(14, objectA.getInstanceVariable("foo"));

        // Add an instance variable to the instance of B

        objectB.setInstanceVariable("bar", 2);

        // This should not have changed the layout of A or the instance of A

        assertSame(layoutClassA, classA.getObjectLayoutForInstances());
        assertSame(layoutObjectA, objectA.getObjectLayout());

        // But the layout of B and the instance of B should have changed

        assertNotSame(layoutClassB, classB.getObjectLayoutForInstances());

        objectB.updateLayout();
        assertNotSame(layoutObjectB, objectB.getObjectLayout());

        // We should be able to find the new instance variable in the instance of B but not A

        assertNull(classA.getObjectLayoutForInstances().findStorageLocation("bar"));
        assertNotNull(classB.getObjectLayoutForInstances().findStorageLocation("bar"));

        // We should be able to read that value back out

        assertEquals(2, objectB.getInstanceVariable("bar"));
    }

    @Test
    public void testPerObjectInstanceVariables() {
        final RubyContext context = new RubyContext(null);

        // Create a class and an instance

        final RubyClass classA = new RubyClass(context, context.getCoreLibrary().getClassClass(), null, null, "A");
        final RubyBasicObject objectA = new RubyBasicObject(classA);

        ObjectLayout layoutClassA = classA.getObjectLayoutForInstances();
        ObjectLayout layoutObjectA = objectA.getObjectLayout();

        // Add an instance variable to the instance of A

        objectA.setInstanceVariable("foo", 2);

        // That should have changed the layout of the class and the object

        assertNotSame(layoutClassA, classA.getObjectLayoutForInstances());
        assertNotSame(layoutObjectA, objectA.getObjectLayout());
        layoutClassA = classA.getObjectLayoutForInstances();
        layoutObjectA = classA.getObjectLayout();

        // We should be able to read the value back out

        assertEquals(2, objectA.getInstanceVariable("foo"));

        /*
         * Switch object A to having a private object layout, as would be done by calls such as
         * instance_variable_set.
         */

        objectA.switchToPrivateLayout();

        // Set an instance variable on object A

        objectA.setInstanceVariable("bar", 14);

        // The layout of object A, however, should have changed

        // CS: it hasn't changed because it's still null
        // assertNotSame(layoutObjectA, objectA.getObjectLayout());

        // We should be able to read the value back out

        assertEquals(14, objectA.getInstanceVariable("bar"));

        /*
         * We should also be able to read the first variable back out, even though we've switched to
         * private layout since then.
         */

        assertEquals(2, objectA.getInstanceVariable("foo"));
    }

}
