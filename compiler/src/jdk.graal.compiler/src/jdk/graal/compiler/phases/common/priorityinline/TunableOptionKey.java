/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * A {@code TunableOptionKey} is a macro option used to tune the value of one or more other options.
 * An illuminating example is when some options are known to trade compilation time for potential
 * throughput gain. The default value for the given tuned options should keep the compilation time
 * within reasonable bounds while the {@code maxValue} will have a potentially significant cost,
 * impacting also warmup time. On the other hand, {@code minValue} will reduce the compilation time
 * below what would give the default configuration.
 *
 * The tuning option must have a default value of 0 which leaves the tuned options to their defaults
 * and can vary between -1 and 1 mapping linearly the value between {@code minValue} and the default
 * value when it is negative and between the default and maxValue when it is positive. Values out of
 * the [-1, 1] bounds are clamped.
 *
 * For instance, a costly phase could introduce two flags, {@code firstFlag} and {@code secondFlag}
 * that depend on the value of a third one, {@code TuneCostlyPhaseExploration}:
 *
 * <pre>
 * public static final OptionKey<Double> TuneCostlyPhaseExploration = new OptionKey<>(0.0);
 * public static final OptionKey<Integer> firstFlag = new TunableOptionKey<>(TuneCostlyPhaseExploration, 10, true, 0, 20);
 * public static final OptionKey<Double> secondFlag = new TunableOptionKey<>(TuneCostlyPhaseExploration, 0.5, false, 0.2, 1.0);
 * </pre>
 *
 * Setting {@code TuneCostlyPhaseExploration} at runtime to 1 (or anything above) would result in:
 *
 * <pre>
 *     FirstFlag = 20
 *     SecondFlag = 0.2
 * </pre>
 *
 * Because {@code higherIsMoreExpensive} is true for {@code FirstFlag} and false for
 * {@code SecondFlag}, so the maximum, respectively minimum, values are used.
 *
 * If {@code TuneCostlyPhaseExploration} would be set to -0.5, the returned values would be:
 *
 * <pre>
 *     FirstFlag = 5
 *     SecondFlag = 0.75
 * </pre>
 */
public class TunableOptionKey<T extends Number> extends OptionKey<T> {

    private OptionKey<Double> tuningOption;
    private boolean higherIsMoreExpensive;
    private double minValue;
    private double maxValue;

    /**
     * Creates an option which value can be modified at runtime depending on the value of the
     * {@code tuningOption} option. The allowed tuning bounds are given by {@code minValue} and
     * {@code maxValue}.
     *
     * @param tuningOption the tuning option
     * @param defaultValue default value for the option being created
     * @param higherIsMoreExpensive if a value greater than the default one increases compilation
     *            time or not
     * @param minValue the minimum value that could be dynamically set by the tuning option
     * @param maxValue the maximum value that could be dynamically set by the tuning option
     */
    public TunableOptionKey(OptionKey<Double> tuningOption, T defaultValue, boolean higherIsMoreExpensive, double minValue, double maxValue) {
        super(defaultValue);
        this.tuningOption = tuningOption;
        this.higherIsMoreExpensive = higherIsMoreExpensive;
        this.minValue = minValue;
        this.maxValue = maxValue;
        GraalError.guarantee(tuningOption.getDefaultValue() == 0, "Tuning option must have a default value of 0");
        GraalError.guarantee(minValue <= defaultValue.doubleValue(), "Tunable option minValue must be smaller than or equal to the default value");
        GraalError.guarantee(maxValue >= defaultValue.doubleValue(), "Tunable option maxValue must be equal to or bigger than the default value");
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getValue(OptionValues options) {
        if (!this.hasBeenSet(options) && tuningOption.getValue(options) != 0) {
            if (getDefaultValue() instanceof Integer) {
                return (T) (Integer.valueOf((int) Math.round(getTunedValue(options))));
            }
            return (T) getTunedValue(options);
        }
        return super.getValue(options);
    }

    private Double getTunedValue(OptionValues options) {
        return getTunedValue(tuningOption.getValue(options), minValue, getDefaultValue().doubleValue(), maxValue, higherIsMoreExpensive);
    }

    /**
     * Provides the same functionality as the {@link TunableOptionKey} with the provided arguments
     * (see {@link TunableOptionKey}), i.e. scale a value between minValue and maxValue depending on
     * the tuningOptionValue and higherIsMoreExpensive.
     *
     * @param tuningOptionValue How much to scale the default value. Should be between -1.0 and 1.0.
     * @param minValue What is the minimum acceptable return value after scaling.
     * @param defaultValue What is the default value to be returned if tuningOptionValue is 0.
     * @param maxValue What is the maximum acceptable return value after scaling.
     * @param higherIsMoreExpensive If true, negative values of tuningOptionValue will tend towards
     *            minValue and positive values of tuningOptionValue will result in values tending
     *            towards maxValue. Other way around if false.
     * @return Given defaultValue if the tuningOptionValue is 0. If higherIsMoreExpensive and if
     *         tuningOptionValue is negative, the return value will be scaled between minValue and
     *         defaultValue, and if tuningOptionValue is positive the return value will be scaled
     *         between defaultValue and maxValue. If not higherIsMoreExpensive, the return value
     *         scaling is inverted - positive between minValue and defaultValue, negative between
     *         defaultValue and maxValue.
     */
    public static double getTunedValue(double tuningOptionValue, double minValue, double defaultValue, double maxValue, boolean higherIsMoreExpensive) {
        double clampedTuningOptionValue = Math.clamp(tuningOptionValue, -1d, 1d);

        double rangeWidth;
        if ((clampedTuningOptionValue > 0.0 && higherIsMoreExpensive) || (clampedTuningOptionValue < 0.0 && !higherIsMoreExpensive)) {
            rangeWidth = maxValue - defaultValue;
        } else {
            rangeWidth = defaultValue - minValue;
        }
        assert NumUtil.assertNonNegativeDouble(rangeWidth);
        double deviationFromDefault = rangeWidth * clampedTuningOptionValue;

        if (!higherIsMoreExpensive) {
            deviationFromDefault *= -1.0;
        }
        return defaultValue + deviationFromDefault;
    }
}
