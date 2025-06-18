package jdk.graal.compiler.hotspot.meta.Bubo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class BuboDataReader {

    public static void main(String[] args) {
        String csvFile = "output.csv";
        HashMap<Integer, Long> dataMap = readData(csvFile);

        // Print the data in the HashMap
        for (Integer id : dataMap.keySet()) {
            System.out.println("ID: " + id + ", CPU Time: " + dataMap.get(id));
        }
    }

    public static void DumpToFile(long[][] data, int pointer, int bufferPointer, String filename) {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i <= bufferPointer -1; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    writer.write(data[i][j] + " ");
                    writer.newLine();
                }
            }
            for (int j = 0; j <= pointer; j++) {
                writer.write(data[bufferPointer][j] + " ");
                writer.newLine();
            }

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    public static HashMap<Integer, Long> convertToHashMap(long[][] data, int pointer, int bufferPointer) {
        HashMap<Integer, Long> LargehashMap = new HashMap<>();
        HashMap<Integer, Long> temp = new HashMap<>();
        for (int i = 0; i < bufferPointer - 1; i++) {
            temp = convertToHashMap(data[i], 250_000_000);
            for (int key : temp.keySet()) {
                if (LargehashMap.containsKey(key)) {
                    LargehashMap.put(key, LargehashMap.get(key) + temp.get(key));
                }
                else{
                    LargehashMap.put(key, temp.get(key));
                }
            }
        }
        temp = convertToHashMap(data[bufferPointer], pointer);
        for (int key : temp.keySet()) {
            if (LargehashMap.containsKey(key)) {
                LargehashMap.put(key, LargehashMap.get(key) + temp.get(key));
            }
            else{
                LargehashMap.put(key, temp.get(key));
            }
        }

        return LargehashMap;

    }

    public static HashMap<Integer, Long> convertToHashMap(long[] data, int pointer) {
        // Check if the length of the array is even (pairs of ID and number)
        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid input array length. It should contain pairs of ID and number.");
        }

        // Create a HashMap to store ID-number pairs
        HashMap<Integer, Long> hashMap = new HashMap<>();

        // Iterate through the array in pairs and put them into the HashMap
        for (int i = 0; i < pointer; i += 2) {
            int id = (int) data[i];
            long number = data[i + 1];
            if (hashMap.containsKey(id)) {
                hashMap.put(id, hashMap.get(id) + number);
            }
            else{
                hashMap.put(id, number);
            }

        }

        return hashMap;
    }

    public static HashMap<Integer, Long> readData(String csvFile) {
        HashMap<Integer, Long> dataMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0].trim());
                long cpuTime = Long.parseLong(parts[1].trim());

                // Ensure CPU time is positive
                if (cpuTime < 0) {
                    // if the cpu time is neagative ignore it
                    continue;
                }

                if (dataMap.containsKey(id)) {
                    dataMap.put(id, dataMap.get(id) + cpuTime);
                }
                else
                {
                dataMap.put(id, cpuTime);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dataMap;
    }
}