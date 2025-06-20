package jdk.graal.compiler.hotspot.meta.Bubo;

import java.lang.Thread;

/**
 * BuboCache thread buffer, this is called viva a Forgien call
 * see Custom Instrumentation Phase to see where its called.
 */

 public class BuboCache extends Thread {

        public static long[] TimeBuffer; // stores all recorded cycles from RDTSC comp units
        public static long[] ActivationCountBuffer; // stores activaation of Comp units
        public static long[] CyclesBuffer; // stores all recorded cycles for est comp units
        public static int pointer;
        //public static long[] Buffer; // dead never used
        public static long[] CallSiteBuffer; // store all of the time spent calling out for a comp unit

        public BuboCache() {
                //Buffer = new long[200_000];
                TimeBuffer = new long[200_000];
                ActivationCountBuffer = new long[200_000];
                CyclesBuffer = new long[200_000];
                CallSiteBuffer = new long [200_000];
                pointer = 1;
        }

        public static void TestPrint(char Unused) {
                System.out.print("Test Print From Static Class");
        }


}