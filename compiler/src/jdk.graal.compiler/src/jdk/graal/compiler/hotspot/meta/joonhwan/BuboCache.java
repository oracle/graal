package jdk.graal.compiler.hotspot.meta.joonhwan;

/**
 * BuboCache thread buffer, this is called viva a Forgien call
 * see Custom Instrumentation Phase to see where its called.
 */

 public class BuboCache extends Thread {
        
        public static long[] Buffer;
        public static int pointer;
        public static int BufferPointer;
        public static long[][] BufferArray;

        // public BuboCache() {
        //         BufferArray = new long[2][250_000];
        //         BufferPointer = 0;
        //         pointer = 0;
        //         Buffer = BufferArray[BufferPointer];
        // }

        static {
                System.out.println("CACHE INITIALIZATION");
                BufferArray = new long[5][250_000];
                BufferPointer = 0;
                pointer = 0;
                Buffer = BufferArray[BufferPointer];
        }

        public static void rotateBuffer() {
                BufferPointer++;
                if (BufferPointer >= 5) {
                        throw new ArrayIndexOutOfBoundsException("We have reached the max of the buffer");
                }
                Buffer = BufferArray[BufferPointer];
                pointer = 0;
        }

        public static void incPointer()
        {
                pointer++;
        }

        public static void test(){
                System.out.println("CACHE TEST");
        }

        public static void add(long[] item)
        {
                Buffer[pointer] = item[0];
                pointer++;
                Buffer[pointer] = item[1];
                pointer++;

                if (pointer > 250_000_000) {
                        rotateBuffer();   
                }

        }

        public static void print(){
                // if (pointer == 0) {
                //         System.out.println("Buffer is empty! :(");
                // }
                for (int i = 0; i < 2; i++) {
                        System.out.println(Buffer[i] + "," + Buffer[i+1]);
                        i++;
                }
        }


}