/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.utilities;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.utilities.MathUtils;

public class MathUtilsTest {

    @Test
    public void asinhTest() {
        for (var pair : List.of(
                        Map.entry(1e-15, 1e-15),
                        Map.entry(3.7252902984619136e-9, 3.7252902984619136e-9),
                        Map.entry(0x1.0p-28, 0x1.0p-28),
                        Map.entry(3.725290298461915e-9, 3.725290298461915e-9),
                        Map.entry(1e-5, 9.999999999833334e-6),
                        Map.entry(0.5, 0.48121182505960347),
                        Map.entry(0.9999999999999999, 0.8813735870195429),
                        Map.entry(1.0, 0.881373587019543),
                        Map.entry(1.0000000000000002, 0.8813735870195432),
                        Map.entry(1.5, 1.1947632172871094),
                        Map.entry(1.9999999999999996, 1.44363547517881),
                        Map.entry(1.9999999999999998, 1.4436354751788103),
                        Map.entry(2.0, 1.4436354751788103),
                        Map.entry(2.0000000000000004, 1.4436354751788105),
                        Map.entry(5.0, 2.3124383412727525),
                        Map.entry(1e5, 12.206072645555174),
                        Map.entry(Math.PI, 1.8622957433108482),
                        Map.entry(11.548739357257748, Math.PI),
                        Map.entry(267.74489404101644, 2 * Math.PI),
                        Map.entry(0x1.0p+28, 20.101268236238415),
                        Map.entry(268435456.00000006, 20.101268236238415), // GR-54495
                        Map.entry(0x1.0p+29, 20.79441541679836),
                        Map.entry(0x1.0p+30, 21.487562597358302),
                        Map.entry(1e15, 35.23192357547063),
                        Map.entry(1e300, 691.4686750787736),
                        Map.entry(2.2250738585072014e-308, 2.2250738585072014e-308),
                        Map.entry(5.244519529722009e+307, 709.2439543619927),
                        Map.entry(1e308, 709.889355822726),
                        Map.entry(Double.MAX_VALUE, 710.4758600739439),
                        Map.entry(Double.MIN_VALUE, Double.MIN_VALUE),
                        Map.entry(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                        Map.entry(Double.NaN, Double.NaN))) {
            double x = pair.getKey();
            double r = pair.getValue();
            Assert.assertEquals("asinh(" + x + ")", r, MathUtils.asinh(x), 0.0);
            Assert.assertEquals("asinh(" + -x + ")", -r, MathUtils.asinh(-x), 0.0);
        }
    }

    @Test
    public void acoshTest() {
        for (var pair : List.of(
                        // x < 1 := NaN
                        Map.entry(0.9999999999999999, Double.NaN),
                        Map.entry(0x1.0p-28, Double.NaN),
                        Map.entry(1e-15, Double.NaN),
                        Map.entry(Double.MIN_VALUE, Double.MIN_VALUE),
                        // x >= 1
                        Map.entry(1.0, 0.0),
                        Map.entry(1.0000000000000002, 2.1073424255447017E-8),
                        Map.entry(1.1, 0.4435682543851154),
                        Map.entry(1.5, 0.9624236501192069),
                        Map.entry(2.0, 1.3169578969248166),
                        Map.entry(5.0, 2.2924316695611777),
                        Map.entry(1e5, 12.206072645505174),
                        Map.entry(Math.PI, 1.811526272460853),
                        Map.entry(11.591953275521519, Math.PI),
                        Map.entry(267.7467614837482, 2 * Math.PI),
                        Map.entry(0x1.0p+28, 20.10126823623841),
                        Map.entry(0x1.0p+29, 20.79441541679836),
                        Map.entry(0x1.0p+30, 21.487562597358302),
                        Map.entry(1e15, 35.23192357547063),
                        Map.entry(1.5243074119957227e267, 615.904907160801),
                        Map.entry(1e300, 691.4686750787736),
                        Map.entry(2.2250738585072014e-308, 2.2250738585072014e-308),
                        Map.entry(5.244519529722009e+307, 709.2439543619927),
                        Map.entry(1e308, 709.889355822726),
                        Map.entry(Double.MAX_VALUE, 710.4758600739439),
                        Map.entry(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                        Map.entry(Double.NaN, Double.NaN))) {
            double x = pair.getKey();
            double r = pair.getValue();
            Assert.assertEquals("acosh(" + x + ")", x >= 1 ? r : Double.NaN, MathUtils.acosh(x), 0.0);
            Assert.assertEquals("acosh(" + -x + ")", Double.NaN, MathUtils.acosh(-x), 0.0);
        }
    }

    @Test
    public void atanhTest() {
        for (var pair : List.of(
                        // |x| > 1 := NaN
                        Map.entry(1.0000000000000002, Double.NaN),
                        Map.entry(Double.POSITIVE_INFINITY, Double.NaN),
                        // |x| == 1 := +/-inf
                        Map.entry(1.0, Double.POSITIVE_INFINITY),
                        // |x| < 1
                        Map.entry(0.9999999999999999, 18.714973875118524),
                        Map.entry(0.9999930253396107, 6.283185307182609),
                        Map.entry(0.99627207622075, 3.141592653589798),
                        Map.entry(0.9, 1.4722194895832204),
                        Map.entry(0.8, 1.0986122886681098),
                        Map.entry(0.7, 0.8673005276940531),
                        Map.entry(0.6, 0.6931471805599453),
                        Map.entry(0.5, 0.5493061443340548),
                        Map.entry(0.4, 0.42364893019360184),
                        Map.entry(0.3, 0.30951960420311175),
                        Map.entry(0.2, 0.2027325540540822),
                        Map.entry(0.1, 0.10033534773107558),
                        Map.entry(1e-5, 1.0000000000333335e-5),
                        Map.entry(1e-15, 1e-15),
                        Map.entry(0.0, 0.0),
                        Map.entry(3.7252902984619136e-9, 3.7252902984619136e-9),
                        Map.entry(0x1.0p-28, 0x1.0p-28),
                        Map.entry(3.725290298461915e-9, 3.725290298461915e-9),
                        Map.entry(0x1.0p-29, 0x1.0p-29),
                        Map.entry(Double.MIN_VALUE, Double.MIN_VALUE),
                        Map.entry(Double.NaN, Double.NaN))) {
            double x = pair.getKey();
            double r = pair.getValue();
            Assert.assertEquals("atanh(" + x + ")", r, MathUtils.atanh(x), 0.0);
            Assert.assertEquals("atanh(" + -x + ")", -r, MathUtils.atanh(-x), 0.0);
        }
    }
}
