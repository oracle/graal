/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.objects;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.lookup.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Represents the Ruby {@code BasicObject} class - the root of the Ruby class hierarchy.
 */
public class RubyBasicObject {

    @CompilationFinal protected RubyClass rubyClass;
    protected RubyClass rubySingletonClass;

    protected LookupNode lookupNode;

    protected long objectID = -1;

    public boolean hasPrivateLayout = false;
    private ObjectLayout objectLayout;

    public static final int PRIMITIVE_STORAGE_LOCATIONS_COUNT = 14;
    protected int primitiveStorageLocation01;
    protected int primitiveStorageLocation02;
    protected int primitiveStorageLocation03;
    protected int primitiveStorageLocation04;
    protected int primitiveStorageLocation05;
    protected int primitiveStorageLocation06;
    protected int primitiveStorageLocation07;
    protected int primitiveStorageLocation08;
    protected int primitiveStorageLocation09;
    protected int primitiveStorageLocation10;
    protected int primitiveStorageLocation11;
    protected int primitiveStorageLocation12;
    protected int primitiveStorageLocation13;
    protected int primitiveStorageLocation14;

    // A bit map to indicate which primitives are set, so that they can be Nil
    protected int primitiveSetMap;

    protected Object[] objectStorageLocations;

    public RubyBasicObject(RubyClass rubyClass) {
        if (rubyClass != null) {
            unsafeSetRubyClass(rubyClass);

            if (rubyClass.getContext().getConfiguration().getFullObjectSpace()) {
                rubyClass.getContext().getObjectSpaceManager().add(this);
            }
        }
    }

    public void initialize() {
    }

    public LookupNode getLookupNode() {
        return lookupNode;
    }

    public RubyClass getRubyClass() {
        assert rubyClass != null;
        return rubyClass;
    }

    public boolean hasPrivateLayout() {
        return hasPrivateLayout;
    }

    public ObjectLayout getObjectLayout() {
        return objectLayout;
    }

    public ObjectLayout getUpdatedObjectLayout() {
        updateLayout();
        return objectLayout;
    }

    /**
     * Does this object have an instance variable defined?
     */
    public boolean isInstanceVariableDefined(String name) {
        if (!hasPrivateLayout && objectLayout != rubyClass.getObjectLayoutForInstances()) {
            updateLayout();
        }

        return objectLayout.findStorageLocation(name) != null;
    }

    /**
     * Set an instance variable to be a value. Slow path.
     */
    public void setInstanceVariable(String name, Object value) {
        CompilerAsserts.neverPartOfCompilation();

        // If the object's layout doesn't match the class, update

        if (!hasPrivateLayout && objectLayout != rubyClass.getObjectLayoutForInstances()) {
            updateLayout();
        }

        // Find the storage location

        StorageLocation storageLocation = objectLayout.findStorageLocation(name);

        if (storageLocation == null) {
            /*
             * It doesn't exist, so create a new layout for the class that includes it and update
             * the layout of this object.
             */

            rubyClass.setObjectLayoutForInstances(rubyClass.getObjectLayoutForInstances().withNewVariable(name, value.getClass()));
            updateLayout();

            storageLocation = objectLayout.findStorageLocation(name);
        }

        // Try to write to that storage location

        try {
            storageLocation.write(this, value);
        } catch (GeneralizeStorageLocationException e) {
            /*
             * It might not be able to store the type that we passed, if not generalize the class's
             * layout and update the layout of this object.
             */

            rubyClass.setObjectLayoutForInstances(rubyClass.getObjectLayoutForInstances().withGeneralisedVariable(name));
            updateLayout();

            storageLocation = objectLayout.findStorageLocation(name);

            // Try to write to the generalized storage location

            try {
                storageLocation.write(this, value);
            } catch (GeneralizeStorageLocationException e1) {
                // We know that we just generalized it, so this should not happen
                throw new RuntimeException("Generalised an instance variable, but it still rejected the value");
            }
        }
    }

    /**
     * Get the value of an instance variable, or Nil if it isn't defined. Slow path.
     */
    public Object getInstanceVariable(String name) {
        CompilerAsserts.neverPartOfCompilation();

        // If the object's layout doesn't match the class, update

        if (!hasPrivateLayout && objectLayout != rubyClass.getObjectLayoutForInstances()) {
            updateLayout();
        }

        // Find the storage location

        final StorageLocation storageLocation = objectLayout.findStorageLocation(name);

        // Get the value

        if (storageLocation == null) {
            return NilPlaceholder.INSTANCE;
        }

        return storageLocation.read(this, true);
    }

    public String[] getInstanceVariableNames() {
        final Set<String> instanceVariableNames = getInstanceVariables().keySet();
        return instanceVariableNames.toArray(new String[instanceVariableNames.size()]);
    }

    public RubyClass getSingletonClass() {
        if (rubySingletonClass == null) {
            /*
             * The object a of class A has a singleton class a' of class Class, with name
             * #<Class:#<A:objectid>>, and with superclass that is A.
             * 
             * irb(main):001:0> class A; end
             * 
             * => nil
             * 
             * irb(main):002:0> a = A.new
             * 
             * => #<A:0x007ff612a631e0>
             * 
             * irb(main):003:0> a.singleton_class
             * 
             * => #<Class:#<A:0x007ff612a631e0>>
             * 
             * irb(main):004:0> a.singleton_class.class
             * 
             * => Class
             * 
             * irb(main):005:0> a.singleton_class.superclass
             * 
             * => A
             */

            rubySingletonClass = new RubyClass(rubyClass.getParentModule(), rubyClass, String.format("#<Class:#<%s:%d>>", rubyClass.getName(), getObjectID()), true);

            lookupNode = new LookupFork(rubySingletonClass, rubyClass);
        }

        return rubySingletonClass;
    }

    public long getObjectID() {
        if (objectID == -1) {
            objectID = rubyClass.getContext().getNextObjectID();
        }

        return objectID;
    }

    public String inspect() {
        return toString();
    }

    /**
     * Get a map of all instance variables.
     */
    protected Map<String, Object> getInstanceVariables() {
        if (objectLayout == null) {
            return Collections.emptyMap();
        }

        final Map<String, Object> instanceVariableMap = new HashMap<>();

        for (Entry<String, StorageLocation> entry : objectLayout.getAllStorageLocations().entrySet()) {
            final String name = entry.getKey();
            final StorageLocation storageLocation = entry.getValue();

            if (storageLocation.isSet(this)) {
                instanceVariableMap.put(name, storageLocation.read(this, true));
            }
        }

        return instanceVariableMap;
    }

    /**
     * Set instance variables from a map.
     */
    protected void setInstanceVariables(Map<String, Object> instanceVariables) {
        assert instanceVariables != null;

        if (objectLayout == null) {
            updateLayout();
        }

        for (Entry<String, Object> entry : instanceVariables.entrySet()) {
            final StorageLocation storageLocation = objectLayout.findStorageLocation(entry.getKey());
            assert storageLocation != null;

            try {
                storageLocation.write(this, entry.getValue());
            } catch (GeneralizeStorageLocationException e) {
                throw new RuntimeException("Should not have to be generalising when setting instance variables - " + entry.getValue().getClass().getName() + ", " +
                                storageLocation.getStoredClass().getName());
            }
        }
    }

    /**
     * Update the layout of this object to match that of its class.
     */
    @CompilerDirectives.SlowPath
    public void updateLayout() {
        // Get the current values of instance variables

        final Map<String, Object> instanceVariableMap = getInstanceVariables();

        // Use the layout of the class

        objectLayout = rubyClass.getObjectLayoutForInstances();

        // Make all primitives as unset

        primitiveSetMap = 0;

        // Create a new array for objects

        allocateObjectStorageLocations();

        // Restore values

        setInstanceVariables(instanceVariableMap);
    }

    private void allocateObjectStorageLocations() {
        final int objectStorageLocationsUsed = objectLayout.getObjectStorageLocationsUsed();

        if (objectStorageLocationsUsed == 0) {
            objectStorageLocations = null;
        } else {
            objectStorageLocations = new Object[objectStorageLocationsUsed];
        }
    }

    public void switchToPrivateLayout() {
        final Map<String, Object> instanceVariables = getInstanceVariables();

        hasPrivateLayout = true;
        objectLayout = ObjectLayout.EMPTY;

        for (Entry<String, Object> entry : instanceVariables.entrySet()) {
            objectLayout = objectLayout.withNewVariable(entry.getKey(), entry.getValue().getClass());
        }

        setInstanceVariables(instanceVariables);
    }

    public void extend(RubyModule module) {
        getSingletonClass().include(module);
    }

    @Override
    public String toString() {
        return "#<" + rubyClass.getName() + ":0x" + Long.toHexString(getObjectID()) + ">";
    }

    public boolean hasSingletonClass() {
        return rubySingletonClass != null;
    }

    public Object send(String name, RubyProc block, Object... args) {
        final RubyMethod method = getLookupNode().lookupMethod(name);

        if (method == null || method.isUndefined()) {
            throw new RaiseException(getRubyClass().getContext().getCoreLibrary().noMethodError(name, toString()));
        }

        return method.call(null, this, block, args);
    }

    public void unsafeSetRubyClass(RubyClass newRubyClass) {
        assert rubyClass == null;

        rubyClass = newRubyClass;
        lookupNode = rubyClass;
    }

}
