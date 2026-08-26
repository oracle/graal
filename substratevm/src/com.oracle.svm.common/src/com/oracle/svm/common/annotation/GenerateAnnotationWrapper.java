/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.common.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/// Generates guest-side representations of annotations in the annotated package. This annotation
/// is intended for use on a `package-info.java` file:
///
/// ```java
/// @GenerateAnnotationWrapper({ExampleAnnotation.class})
/// package com.example;
/// ```
///
/// For an `ExampleAnnotation` with `String name()` and `Class<?> target()` members, the annotation
/// processor generates a record with the following shape:
///
/// ```java
/// public record ExampleAnnotationGuestValue(String name, ResolvedJavaType target) {
///     public static ExampleAnnotationGuestValue get(Annotated element) {
///         // Looks up the annotation through GuestAnnotationAccess and delegates to from.
///     }
///
///     public static ExampleAnnotationGuestValue from(
///                     AnnotationValue annotationValue) {
///         // ...
///     }
/// }
/// ```
///
/// The generated `get` method is the concise path for ordinary guest annotation lookup. The `from`
/// method remains available for specialized consumers that already have an `AnnotationValue`, such
/// as declared-only or repeatable annotation processing.
///
/// Primitive and [String] members retain their declared type, [Class] members become
/// `ResolvedJavaType`, and enum members retain their declared enum type. `String[]` members become
/// `List<String>`, `Class<?>[]` members become `List<ResolvedJavaType>`, and other array members
/// become `List<?>`. The generated source is compiled as part of the project containing the
/// annotated package.
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PACKAGE)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface GenerateAnnotationWrapper {
    Class<? extends Annotation>[] value();
}
