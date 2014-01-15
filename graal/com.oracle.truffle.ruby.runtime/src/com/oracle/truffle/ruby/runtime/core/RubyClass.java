/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.lookup.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Class} class. Note that most of the functionality you might associate
 * with {@code Class} is actually in {@code Module}, implemented by {@link RubyModule}.
 */
public class RubyClass extends RubyModule {

    /**
     * The class from which we create the object that is {@code Class}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyClass} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyClassClass extends RubyClass {

        public RubyClassClass(RubyContext context) {
            super(context, null, null, null, "Class");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyClass(null, getContext().getCoreLibrary().getObjectClass(), "(unnamed class)");
        }

    }

    @CompilationFinal private RubyClass superclass;

    // We maintain a list of subclasses so we can notify them when they need to update their layout.
    private final Set<RubyClass> subClasses = Collections.newSetFromMap(new WeakHashMap<RubyClass, Boolean>());

    /*
     * The layout to use for instances of this class - do not confuse with objectLayout, which is
     * the layout for this object - the class.
     */
    private ObjectLayout objectLayoutForInstances = null;

    public RubyClass(RubyModule parentModule, RubyClass rubySuperclass, String name) {
        this(parentModule, rubySuperclass, name, false);
    }

    public RubyClass(RubyModule parentModule, RubyClass rubySuperclass, String name, boolean isSingleton) {
        this(rubySuperclass.getContext(), rubySuperclass.getContext().getCoreLibrary().getClassClass(), parentModule, rubySuperclass, name);

        if (!isSingleton) {
            getSingletonClass();
        }
    }

    /**
     * This constructor supports initialization and solves boot-order problems and should not
     * normally be used from outside this class.
     */
    public RubyClass(RubyContext context, RubyClass classClass, RubyModule parentModule, RubyClass superclass, String name) {
        super(context, classClass, parentModule, name);

        if (superclass == null) {
            objectLayoutForInstances = ObjectLayout.EMPTY;
        } else {
            unsafeSetSuperclass(superclass);
        }
    }

    public RubyClass getSuperclass() {
        assert superclass != null;
        return superclass;
    }

    @Override
    public RubyClass getSingletonClass() {
        if (rubySingletonClass == null) {
            RubyClass singletonSuperclass;

            if (superclass == null) {
                singletonSuperclass = getRubyClass();
            } else {
                singletonSuperclass = superclass.getSingletonClass();
            }

            rubySingletonClass = new RubyClass(getParentModule(), singletonSuperclass, String.format("#<Class:%s>", getName()), true);

            lookupNode = new LookupFork(rubySingletonClass, lookupNode);
        }

        return rubySingletonClass;
    }

    /**
     * This method supports initialization and solves boot-order problems and should not normally be
     * used.
     */
    public void unsafeSetSuperclass(RubyClass newSuperclass) {
        assert superclass == null;

        superclass = newSuperclass;
        superclass.addDependent(this);
        superclass.subClasses.add(this);

        include(superclass);

        objectLayoutForInstances = new ObjectLayout(getName(), superclass.objectLayoutForInstances);
    }

    public RubyBasicObject newInstance() {
        return new RubyObject(this);
    }

    /**
     * Is an instance of this class assignable to some location expecting some other class?
     */
    public boolean assignableTo(RubyClass otherClass) {
        if (this == otherClass) {
            return true;
        }

        if (superclass == null) {
            return false;
        }

        return superclass.assignableTo(otherClass);
    }

    /**
     * Returns the object layout that objects of this class should use. Do not confuse with
     * {@link #getObjectLayout}, which for {@link RubyClass} will return the layout of the class
     * object itself.
     */
    public ObjectLayout getObjectLayoutForInstances() {
        return objectLayoutForInstances;
    }

    /**
     * Change the layout to be used for instances of this object.
     */
    public void setObjectLayoutForInstances(ObjectLayout newObjectLayoutForInstances) {
        objectLayoutForInstances = newObjectLayoutForInstances;

        for (RubyClass subClass : subClasses) {
            subClass.renewObjectLayoutForInstances();
        }
    }

    private void renewObjectLayoutForInstances() {
        objectLayoutForInstances = objectLayoutForInstances.renew(superclass.objectLayoutForInstances);

        for (RubyClass subClass : subClasses) {
            subClass.renewObjectLayoutForInstances();
        }
    }

}
