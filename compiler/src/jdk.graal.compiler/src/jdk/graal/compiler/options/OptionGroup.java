/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes the attributes of an option whose {@link OptionKey value} is in a static field
 * annotated by this annotation type.
 *
 * @see OptionDescriptor
 */
/*
 * Needs to be runtime retention in order to filter groups that should be registered as service in
 * native-image runtime options.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OptionGroup {

    /**
     * Prefix string to be used for option names. For example setting it to <code>"compiler."</code>
     * will make every specified option use that prefix. By default options directly use the field
     * name and do not use a prefix.
     */
    String prefix() default "";

    /**
     * By default, generated classes that specify an {@link Option} annotation are loaded as a
     * service. If this attribute is set to <code>false</code>, the generated options in this class
     * are not loaded by the service mechanism. This allows manually specifying where the option
     * descriptors come from.
     * <p>
     * Since option services are registered by name using the "_OptionDescriptors" class name
     * suffix, the generated class also does not end with "_OptionDescriptors" but just with
     * "OptionDescriptors". This is hopefully obsolete after GR-46195 is implemented.
     */
    boolean registerAsService() default true;

}
