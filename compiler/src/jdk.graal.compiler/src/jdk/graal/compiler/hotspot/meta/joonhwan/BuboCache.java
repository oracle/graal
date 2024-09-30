package jdk.graal.compiler.hotspot.meta.joonhwan;

import java.io.FileWriter;
import java.io.IOException;

 public class BuboCache extends Thread {
        
        public static long[] Buffer;
        public static int bufferIndex;

        public static long sampleCounter;

        public static final int SAMPLE_RATE = 100;

        static {
                System.out.println("CACHE INITIALIZATION");
                bufferIndex = 0;
                Buffer = new long[9_000_000];
                sampleCounter = 0;
        }

        public static void sampleTime(long id){
                if(sampleCounter % SAMPLE_RATE == 0){
                        Buffer[bufferIndex++] = id;
                        Buffer[bufferIndex++] = System.currentTimeMillis();
                }
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
                System.out.println("CSV file '" + fileName + "' has been created successfully.");
                } catch (IOException e) {
                System.err.println("An error occurred while writing to the CSV file: " + e.getMessage());
                }
        }


}