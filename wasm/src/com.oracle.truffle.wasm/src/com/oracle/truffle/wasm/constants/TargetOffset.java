package com.oracle.truffle.wasm.constants;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class TargetOffset {
    @CompilationFinal public final int value;

    public TargetOffset(int value) {
        this.value = value;
    }

    public boolean isGreaterThanZero() {
        return value > 0;
    }

    public boolean isMinusOne() {
        return value == -1;
    }

    public TargetOffset decrement() {
        final int resultValue = value - 1;
        return createOrCached(resultValue);
    }

    public static TargetOffset createOrCached(int value) {
        // The cache index starts with value -1, so we need a +1 offset.
        final int resultCacheIndex = value + 1;
        if (resultCacheIndex < CACHE.length) {
            return CACHE[resultCacheIndex];
        }
        return new TargetOffset(value);
    }

    @CompilationFinal public static final TargetOffset MINUS_ONE = new TargetOffset(-1);

    @CompilationFinal public static final TargetOffset ZERO = new TargetOffset(0);

    @CompilationFinal(dimensions = 1) private static final TargetOffset[] CACHE = new TargetOffset[]{
                    MINUS_ONE,
                    ZERO,
                    new TargetOffset(1),
                    new TargetOffset(2),
                    new TargetOffset(3),
                    new TargetOffset(4),
                    new TargetOffset(5),
                    new TargetOffset(6),
                    new TargetOffset(7),
                    new TargetOffset(8),
                    new TargetOffset(9),
                    new TargetOffset(10),
                    new TargetOffset(11),
                    new TargetOffset(12),
                    new TargetOffset(13),
                    new TargetOffset(14),
                    new TargetOffset(15),
                    new TargetOffset(16),
                    new TargetOffset(17),
                    new TargetOffset(18),
                    new TargetOffset(19),
                    new TargetOffset(20),
                    new TargetOffset(21),
                    new TargetOffset(22),
                    new TargetOffset(23),
                    new TargetOffset(24),
                    new TargetOffset(25),
                    new TargetOffset(26),
                    new TargetOffset(27),
                    new TargetOffset(28),
                    new TargetOffset(29),
                    new TargetOffset(30),
                    new TargetOffset(31),
                    new TargetOffset(32)
    };
}
