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

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import sun.misc.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.*;

/**
 * Maps names of instance variables to storage locations, which are either the offset of a primitive
 * field in {@code RubyBasicObject}, or an index into an object array in {@code RubyBasicObject}.
 * Object layouts are chained, with each having zero or one parents.
 * <p>
 * Object layouts are immutable, with the methods for adding new instance variables of generalizing
 * the type of existing instance variables returning new object layouts.
 */
public class ObjectLayout {

    public static final ObjectLayout EMPTY = new ObjectLayout("(empty)");

    private final String originHint;

    private final ObjectLayout parent;

    private final Map<String, StorageLocation> storageLocations = new HashMap<>();

    private final int primitiveStorageLocationsUsed;
    private final int objectStorageLocationsUsed;

    public static final long FIRST_OFFSET = getFirstOffset();

    private ObjectLayout(String originHint) {
        this.originHint = originHint;
        this.parent = null;
        primitiveStorageLocationsUsed = 0;
        objectStorageLocationsUsed = 0;
    }

    public ObjectLayout(String originHint, ObjectLayout parent) {
        this(originHint, parent, new HashMap<String, Class>());
    }

    public ObjectLayout(String originHint, ObjectLayout parent, Map<String, Class> storageTypes) {
        this.originHint = originHint;
        this.parent = parent;

        // Start our offsets from where the parent ends

        int primitiveStorageLocationIndex;
        int objectStorageLocationIndex;

        if (parent == null) {
            primitiveStorageLocationIndex = 0;
            objectStorageLocationIndex = 0;
        } else {
            primitiveStorageLocationIndex = parent.primitiveStorageLocationsUsed;
            objectStorageLocationIndex = parent.objectStorageLocationsUsed;
        }

        // Go through the variables we've been asked to store

        for (Entry<String, Class> entry : storageTypes.entrySet()) {
            // TODO(cs): what if parent has it, but we need a more general type?

            final String name = entry.getKey();
            final Class type = entry.getValue();

            if (parent == null || parent.findStorageLocation(name) == null) {
                boolean canStoreInPrimitive = false;
                int primitivesNeeded = 0;

                if (type == Integer.class) {
                    canStoreInPrimitive = true;
                    primitivesNeeded = 1;
                } else if (type == Double.class) {
                    canStoreInPrimitive = true;
                    primitivesNeeded = 2;
                }

                if (canStoreInPrimitive && primitiveStorageLocationIndex + primitivesNeeded <= RubyBasicObject.PRIMITIVE_STORAGE_LOCATIONS_COUNT) {
                    final long offset = FIRST_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * primitiveStorageLocationIndex;
                    final int mask = 1 << primitiveStorageLocationIndex;

                    StorageLocation newStorageLocation = null;

                    if (type == Integer.class) {
                        newStorageLocation = new FixnumStorageLocation(this, offset, mask);
                    } else if (type == Double.class) {
                        newStorageLocation = new FloatStorageLocation(this, offset, mask);
                    }

                    storageLocations.put(entry.getKey(), newStorageLocation);
                    primitiveStorageLocationIndex += primitivesNeeded;
                } else {
                    final ObjectStorageLocation newStorageLocation = new ObjectStorageLocation(this, objectStorageLocationIndex);
                    storageLocations.put(entry.getKey(), newStorageLocation);
                    objectStorageLocationIndex++;
                }
            }
        }

        primitiveStorageLocationsUsed = primitiveStorageLocationIndex;
        objectStorageLocationsUsed = objectStorageLocationIndex;
    }

    /**
     * Create a new version of this layout, but with a different parent. The new parent probably
     * comes from the same Ruby class as it did, but it's a new layout because layouts are
     * immutable, so modifications to the superclass yields a new layout.
     */
    public ObjectLayout renew(ObjectLayout newParent) {
        return new ObjectLayout(originHint + ".renewed", newParent, getStorageTypes());
    }

    /**
     * Create a new version of this layout but with a new variable.
     */
    public ObjectLayout withNewVariable(String name, Class type) {
        final Map<String, Class> storageTypes = getStorageTypes();
        storageTypes.put(name, type);
        return new ObjectLayout(originHint + ".withnew", parent, storageTypes);
    }

    /**
     * Create a new version of this layout but with an existing variable generalized to support any
     * type.
     */
    public ObjectLayout withGeneralisedVariable(String name) {
        return withNewVariable(name, Object.class);
    }

    /**
     * Get a map of instance variable names to the type that they store.
     */
    public Map<String, Class> getStorageTypes() {
        Map<String, Class> storageTypes = new HashMap<>();

        for (Entry<String, StorageLocation> entry : storageLocations.entrySet()) {
            final String name = entry.getKey();
            final StorageLocation storageLocation = entry.getValue();

            if (storageLocation.getStoredClass() != null) {
                storageTypes.put(name, storageLocation.getStoredClass());
            }
        }

        return storageTypes;
    }

    /**
     * Get a map of instance variable names to the type that they store, but including both this
     * layout and all parent layouts.
     */
    public Map<String, StorageLocation> getAllStorageLocations() {
        final Map<String, StorageLocation> allStorageLocations = new HashMap<>();

        allStorageLocations.putAll(storageLocations);

        if (parent != null) {
            allStorageLocations.putAll(parent.getAllStorageLocations());
        }

        return allStorageLocations;
    }

    /**
     * Find a storage location from a name, including in parents.
     */
    public StorageLocation findStorageLocation(String name) {
        final StorageLocation storageLocation = storageLocations.get(name);

        if (storageLocation != null) {
            return storageLocation;
        }

        if (parent == null) {
            return null;
        }

        return parent.findStorageLocation(name);
    }

    public int getObjectStorageLocationsUsed() {
        return objectStorageLocationsUsed;
    }

    /**
     * Does this layout include another layout? That is, is that other layout somewhere in the chain
     * of parents? We say 'include' because all of the variables in a parent layout are available in
     * your layout as well.
     */
    public boolean contains(ObjectLayout other) {
        ObjectLayout layout = this;

        do {
            if (other == layout) {
                return true;
            }

            layout = layout.parent;
        } while (layout != null);

        return false;
    }

    public String getOriginHint() {
        return originHint;
    }

    private static long getFirstOffset() {
        try {
            final Field fieldOffsetProviderField = NodeUtil.class.getDeclaredField("unsafeFieldOffsetProvider");
            fieldOffsetProviderField.setAccessible(true);
            final FieldOffsetProvider fieldOffsetProvider = (FieldOffsetProvider) fieldOffsetProviderField.get(null);

            final Field firstPrimitiveField = RubyBasicObject.class.getDeclaredField("primitiveStorageLocation01");
            return fieldOffsetProvider.objectFieldOffset(firstPrimitiveField);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
