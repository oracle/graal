/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.lang.annotation.*;

import com.oracle.truffle.ruby.runtime.methods.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoreMethod {

    String[] names();

    boolean isModuleMethod() default false;

    boolean needsSelf() default true;

    boolean isSplatted() default false;

    boolean needsBlock() default false;

    boolean appendCallNode() default false;

    int minArgs() default Arity.NO_MINIMUM;

    int maxArgs() default Arity.NO_MAXIMUM;

}
