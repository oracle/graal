package jdk.graal.compiler.hotspot.meta.joonhwan;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;


 public class BuboCache extends Thread {

        public static long[] Buffer = new long[9_000_000];
        public static AtomicLong bufferIndex = new AtomicLong(0);
        public static AtomicLong sampleCounter = new AtomicLong(0);
        public static final int SAMPLE_RATE = 100;


        static {
                System.out.println("CACHE INITIALIZATION");
        }

        //ThreadLocalFields impl
        public static void sampleTime(long id){
                if(sampleCounter.getAndIncrement() % SAMPLE_RATE == 0){
                        long index = bufferIndex.getAndAdd(2);
                        if (index + 1 < Buffer.length) {
                                Buffer[(int) index] = id;
                                Buffer[(int) index + 1] = System.nanoTime();
                        } else {
                        // TODO Handle buffer overflow,
                        }
                }
        }

        public static void test(){
                System.out.println("CACHE TEST");
        }


        public static void print() {
                String fileName = "bubo_cache_output.csv";
                try (FileWriter writer = new FileWriter(fileName)) {
                        writer.append("CompID,Start\n"); 
                        long currentIndex = bufferIndex.get();
                
                        if (currentIndex % 2 != 0) {
                                currentIndex--; // Adjust to the last complete pair
                        }
                
                        writer.append(String.format("pointer %d\n", currentIndex));
                
                        for (int i = 0; i < currentIndex; i += 2) {
                                long compID = Buffer[i];
                                long startTime = Buffer[i + 1];
                                writer.append(String.format("%d,%d\n", compID, startTime));
                        }
                
                        System.out.println("CSV file '" + fileName + "' has been created successfully.");
                } catch (IOException e) {
                        System.err.println("An error occurred while writing to the CSV file: " + e.getMessage());
                }
        }
        
}