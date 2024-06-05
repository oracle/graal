/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.jdk23.test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.util.ArraysSupport;

@AddExports({"java.base/jdk.internal.util"})
public class VectorizedHashCodeTest extends GraalCompilerTest {

    private static int getField(String name) {
        try {
            var arraysSupport = Class.forName("jdk.internal.util.ArraysSupport");
            Field f = arraysSupport.getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testJDKConstantValue() {
        Assert.assertEquals(getField("T_BOOLEAN"), VectorizedHashCodeInvocationPlugin.T_BOOLEAN);
        Assert.assertEquals(getField("T_CHAR"), VectorizedHashCodeInvocationPlugin.T_CHAR);
        Assert.assertEquals(getField("T_BYTE"), VectorizedHashCodeInvocationPlugin.T_BYTE);
        Assert.assertEquals(getField("T_SHORT"), VectorizedHashCodeInvocationPlugin.T_SHORT);
        Assert.assertEquals(getField("T_INT"), VectorizedHashCodeInvocationPlugin.T_INT);
    }

    // @formatter:off
    private static String[] tests = {"", " ", "a", "abcdefg",
            "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way- in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.  -- Charles Dickens, Tale of Two Cities",
            "C'était le meilleur des temps, c'était le pire des temps, c'était l'âge de la sagesse, c'était l'âge de la folie, c'était l'époque de la croyance, c'était l'époque de l'incrédulité, c'était la saison de la Lumière, c'était C'était la saison des Ténèbres, c'était le printemps de l'espoir, c'était l'hiver du désespoir, nous avions tout devant nous, nous n'avions rien devant nous, nous allions tous directement au Ciel, nous allions tous directement dans l'autre sens bref, la période ressemblait tellement à la période actuelle, que certaines de ses autorités les plus bruyantes ont insisté pour qu'elle soit reçue, pour le bien ou pour le mal, au degré superlatif de la comparaison seulement. -- Charles Dickens, Tale of Two Cities (in French)",
            "禅道修行を志した雲水は、一般に参禅のしきたりを踏んだうえで一人の師につき、各地にある専門道場と呼ばれる養成寺院に入門し、与えられた公案に取り組むことになる。公案は、師家（老師）から雲水が悟りの境地へと進んで行くために手助けとして課す問題であり、悟りの境地に達していない人には容易に理解し難い難問だが、屁理屈や詭弁が述べられているわけではなく、頓知や謎かけとも異なる。"
    };
    // @formatter:on

    private static int[] initialValues = {0, 1, 0xDEADBEEF};

    private void testHash(String method, Function<byte[], Object> f, Function<byte[], Integer> getLength) {
        for (String test : tests) {
            byte[] baseArray = test.getBytes(StandardCharsets.UTF_8);
            Object array = f.apply(baseArray);
            int len = getLength.apply(baseArray);

            Set<Integer> intValues = new HashSet<>();
            intValues.add(0);
            intValues.add(1);
            intValues.add(len / 2);
            intValues.add(len - 1);
            intValues.add(len);

            for (int index : intValues) {
                if (index < 0) {
                    continue;
                }
                for (int length : intValues) {
                    if (length < 0 || index + length > len) {
                        continue;
                    }
                    for (int initialValue : initialValues) {
                        test(method, array, index, length, initialValue);
                    }
                }
            }
        }
    }

    public static int hashByteArray(byte[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashByteArray() {
        testHash("hashByteArray", a -> a, a -> a.length);
    }

    public static int hashByteArrayCharElement(byte[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashByteArrayCharElement() {
        testHash("hashByteArrayCharElement", a -> a, a -> a.length / 2);
    }

    public static int hashBooleanArray(byte[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashBooleanArray() {
        testHash("hashBooleanArray", a -> {
            byte[] array = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                array[i] = (byte) (a[i] % 2);
            }
            return array;
        }, a -> a.length);
        // non-boolean element
        testHash("hashBooleanArray", a -> a, a -> a.length);
    }

    public static int hashCharArray(char[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashCharArray() {
        testHash("hashCharArray", a -> {
            char[] array = new char[a.length];
            for (int i = 0; i < a.length; i++) {
                array[i] = (char) a[i];
            }
            return array;
        }, a -> a.length);
    }

    public static int hashShortArray(short[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashShortArray() {
        testHash("hashShortArray", a -> {
            short[] array = new short[a.length];
            for (int i = 0; i < a.length; i++) {
                array[i] = a[i];
            }
            return array;
        }, a -> a.length);
    }

    public static int hashIntArray(int[] array, int fromIndex, int length, int initialValue) {
        return ArraysSupport.hashCode(array, fromIndex, length, initialValue);
    }

    @Test
    public void testHashIntArray() {
        testHash("hashIntArray", a -> {
            int[] array = new int[a.length];
            for (int i = 0; i < a.length; i++) {
                array[i] = a[i];
            }
            return array;
        }, a -> a.length);
    }
}
