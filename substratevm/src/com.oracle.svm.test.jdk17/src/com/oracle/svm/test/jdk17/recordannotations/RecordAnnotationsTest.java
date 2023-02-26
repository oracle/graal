/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.jdk17.recordannotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class RecordAnnotationsTest {

    @Test
    public void verifyRecordAnnotations() {
        F x = new F("x", 1);

        RecordComponent[] recordComponents = x.getClass().getRecordComponents();
        Assert.assertEquals(2, recordComponents.length);

        // Avoid relying on order of returned record components
        RecordComponent intComp = null;
        RecordComponent stringComp = null;
        for (RecordComponent c : recordComponents) {
            if ("name".equals(c.getName())) {
                stringComp = c;
            }
            if ("i".equals(c.getName())) {
                intComp = c;
            }
        }
        Assert.assertNotNull(stringComp);
        Assert.assertNotNull(intComp);

        // Verify String component annotations
        //
        // Checkstyle: stop
        List<Annotation> stringCompAnnot = Arrays.asList(stringComp.getAnnotations());
        // Checkstyle: resume
        Assert.assertEquals("expected 2 record annotations for string component", 2, stringCompAnnot.size());
        Assert.assertTrue("expected RCA component annotation", stringCompAnnot.stream().anyMatch(a -> a.annotationType().getName().equals("com.oracle.svm.test.jdk17.recordannotations.RCA")));
        Assert.assertTrue("expected RCA2 component annotation", stringCompAnnot.stream().anyMatch(a -> a.annotationType().getName().equals("com.oracle.svm.test.jdk17.recordannotations.RCA2")));
        Assert.assertEquals(String.class.getTypeName(), stringComp.getAnnotatedType().getType().getTypeName());
        // Checkstyle: stop
        Annotation rcaAnnotation = stringComp.getAnnotation(RCA.class);
        // Checkstyle: resume
        Assert.assertNotNull("expected RCA annotation to be present", rcaAnnotation);
        Assert.assertEquals(String.class.getTypeName(), stringComp.getGenericType().getTypeName());

        // Verify int component annotations
        //
        // Checkstyle: stop
        List<Annotation> intCompAnnot = Arrays.asList(intComp.getAnnotations());
        // Checkstyle: resume
        Assert.assertEquals("expected 1 record annotation for int component", 1, intCompAnnot.size());
        Assert.assertTrue("expected RCA2 component annotation", intCompAnnot.stream().anyMatch(a -> a.annotationType().getName().equals("com.oracle.svm.test.jdk17.recordannotations.RCA2")));
        Assert.assertEquals(int.class.getTypeName(), intComp.getAnnotatedType().getType().getTypeName());
        // Checkstyle: stop
        Assert.assertNull(intComp.getAnnotation(RCA.class));
        Assert.assertNotNull(intComp.getAnnotation(RCA2.class));
        // Checkstyle: resume
        Assert.assertEquals(int.class.getTypeName(), intComp.getGenericType().getTypeName());

        Assert.assertEquals(1, x.i());
        Assert.assertEquals("x", x.name());
    }

}
