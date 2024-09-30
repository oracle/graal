package jdk.graal.compiler.hotspot.meta.joonhwan;

import java.io.FileWriter;

// public class BuboCache {
//         // Define a suitable buffer size
//         private static final int BUFFER_SIZE = 1024;
    
//         // Thread-local buffer to store timing data
//         public static final ThreadLocal<long[]> threadLocalBuffer = ThreadLocal.withInitial(() -> new long[BUFFER_SIZE]);
    
//         // Thread-local pointer to keep track of buffer index
//         public static final ThreadLocal<Integer> threadLocalPointer = ThreadLocal.withInitial(() -> 0);

//         static {
//                 System.out.println("Cache initialiation called");
//         }

//         public static void rotateBuffer() {
//         }

//         public static void add(long[] item){}

//         public static void print(){
//                 String fileName = "bubo_cache_output.csv";
//                 try (FileWriter writer = new FileWriter(fileName)) {
//                         writer.append("CompID,Start\n"); // CSV header
//                         writer.append(String.format("pointer %d\n", threadLocalPointer.get()));
//                         for (int i = 0; i < threadLocalPointer.get() ; i += 2) {
//                                 writer.append(String.format("%d,%d\n", threadLocalBuffer.get()[i], threadLocalBuffer.get()[i + 1]));
//                         }
//                         System.out.println("CSV file '" + fileName + "' has been created successfully.");
//                 } catch (Exception e) {
//                         System.err.println("An error occurred while writing to the CSV file: " + e.getMessage());
//                 }
//         }
// }

    

 public class BuboCache {
        
        public static long[] Buffer;
        public static int pointer;
        public static int BufferPointer;
        public static long[][] BufferArray;

        public static volatile long sampleCounter;

        static {
                System.out.println("CACHE INITIALIZATION");
                // BufferArray = new long[5][250_000];
                BufferPointer = 0;
                pointer = 0;
                Buffer = new long[9_000_000];
                sampleCounter = 0;
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
                String fileName = "bubo_cache_output.csv";
                try (FileWriter writer = new FileWriter(fileName)) {
                writer.append("CompID,Start\n"); // CSV header
                writer.append(String.format("pointer %d\n", pointer));
                for (int i = 0; i < pointer; i += 2) {
                        writer.append(String.format("%d,%d\n", Buffer[i], Buffer[i + 1]));
                }
                System.out.println(String.format("samplecounter is: %d\n", sampleCounter));
                System.out.println("CSV file '" + fileName + "' has been created successfully.");
                } catch (Exception e) {
                System.err.println("An error occurred while writing to the CSV file: " + e.getMessage());
                }
        }


}