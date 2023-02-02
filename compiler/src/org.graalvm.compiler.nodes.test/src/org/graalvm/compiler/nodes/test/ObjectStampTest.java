/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;

public class ObjectStampTest extends AbstractObjectStampTest {
    @Test
    public void testInterfaceTrust0() {
        Stamp notTrusted = StampFactory.object(TypeReference.createWithoutAssumptions(getMetaAccess().lookupJavaType(I.class)));
        Assert.assertEquals(StampFactory.object(), notTrusted);
    }

    private interface TrustedI {

    }

    @Test
    public void testInterfaceTrust1() {
        Stamp trusted = StampFactory.object(getType(TrustedI.class));
        Assert.assertNotEquals(StampFactory.object(), trusted);
        Assert.assertTrue("Should be an AbstractObjectStamp", trusted instanceof AbstractObjectStamp);
        AbstractObjectStamp trustedObjectStamp = (AbstractObjectStamp) trusted;
        Assert.assertNotNull(trustedObjectStamp.type());
        Assert.assertTrue("Should be an interface", trustedObjectStamp.type().isInterface());
    }

    @Test
    public void testEquals() {
        Stamp empty = StampFactory.empty(JavaKind.Object);
        Stamp object = StampFactory.objectNonNull();

        Stamp exactObject = new ObjectStamp(getMetaAccess().lookupJavaType(java.lang.Object.class), true, true, false, false);
        ObjectStamp exactObject2 = new ObjectStamp(getMetaAccess().lookupJavaType(Object.class), true, true, false, false);
        Assert.assertFalse(empty.equals(exactObject));
        Assert.assertFalse(exactObject.equals(empty));
        Assert.assertTrue(exactObject.equals(exactObject2));
        Assert.assertFalse(exactObject.equals(object));

        Stamp inexactObject = new ObjectStamp(getMetaAccess().lookupJavaType(java.lang.Object.class), false, true, false, false);
        ObjectStamp inexactObject2 = new ObjectStamp(getMetaAccess().lookupJavaType(Object.class), false, true, false, false);
        Assert.assertFalse(empty.equals(inexactObject));
        Assert.assertFalse(inexactObject.equals(empty));
        Assert.assertTrue(inexactObject.equals(inexactObject2));
        Assert.assertTrue(inexactObject.equals(object));

        /* type==null and exact==true means "empty". */
        Stamp exactNull = new ObjectStamp(null, true, true, false, false);
        ObjectStamp exactNull2 = new ObjectStamp(null, true, true, false, false);
        Assert.assertTrue(empty.equals(exactNull));
        Assert.assertTrue(exactNull.equals(empty));
        Assert.assertTrue(exactNull.equals(exactNull2));
        Assert.assertFalse(exactNull.equals(object));
        Assert.assertFalse(exactObject.equals(exactNull));

        /* type==null and exact==false means "java.lang.Object". */
        Stamp inexactNull = new ObjectStamp(null, false, true, false, false);
        ObjectStamp inexactNull2 = new ObjectStamp(null, false, true, false, false);
        Assert.assertFalse(empty.equals(inexactNull));
        Assert.assertFalse(inexactNull.equals(empty));
        Assert.assertTrue(inexactNull.equals(inexactNull2));
        Assert.assertTrue(inexactNull.equals(object));
        Assert.assertTrue(inexactObject.equals(inexactNull));

        /*
         * For all assertions that equals==true, we also check the hashCode. Ideally, equals==false
         * would also mean that the hashCode is different, but we cannot guarantee it because we do
         * not control the hashCode of a ResolvedJavaType.
         */
        Assert.assertTrue(exactObject.hashCode() == exactObject2.hashCode());
        Assert.assertTrue(inexactObject.hashCode() == inexactObject2.hashCode());
        Assert.assertTrue(inexactObject.hashCode() == object.hashCode());
        Assert.assertTrue(empty.hashCode() == exactNull.hashCode());
        Assert.assertTrue(exactNull.hashCode() == empty.hashCode());
        Assert.assertTrue(exactNull.hashCode() == exactNull2.hashCode());
        Assert.assertTrue(inexactNull.hashCode() == inexactNull2.hashCode());
        Assert.assertTrue(inexactNull.hashCode() == object.hashCode());
        Assert.assertTrue(inexactObject.hashCode() == inexactNull.hashCode());
    }
}
