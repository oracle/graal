package com.oracle.svm.core.jfr.utils;

import jdk.internal.misc.Unsafe;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import com.oracle.svm.core.locks.VMMutex;

/** This class is essentially the same as JfrPRNG in jdk/src/hotspot/shar/jfr/utilities/jfrRandom.inline.hpp in the OpenJDK
 *  Commit hash: 1100dbc6b2a1f2d5c431c6f5c6eb0b9092aee817 */
public class JfrRandom {
    private static final long prngMult = 25214903917L;
    private static final long prngAdd = 11;
    private static final long prngModPower = 48;
    private static final long modMask = (1L << prngModPower) - 1;
    private volatile long random = 0;

    private static com.oracle.svm.core.locks.VMMutex mutex;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrRandom(){
        mutex = new VMMutex("asdf");
    }

    /** This is the formula for RAND48 used in unix systems (linear congruential generator). This is also what JFR in hotspot uses.*/
    @Uninterruptible(reason = "Locking with no transition.")
    private long nextRandom(){
        // Should be atomic to avoid repeated values
        mutex.lockNoTransition();
        try {
            if(random == 0){
                random = System.currentTimeMillis(); //TODO reset in startup hook
            }
            long next = (prngMult * random + prngAdd) & modMask;
            random = next;
            com.oracle.svm.core.util.VMError.guarantee(random >0);
            return next;
        } finally {
            mutex.unlock();
        }
    }

    public void resetSeed(){
        mutex.lock();
        try {
            random = 0;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public double nextUniform(){
        long next = nextRandom();
        // Take the top 26 bits
        long masked = next >> (prngModPower - 26);
        // Normalize between 0 and 1
        return masked / (double) (1L << 26);
    }
}
