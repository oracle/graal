public class UnguardedOOB {
    public static void main(String[] args) {
        int[] arr = new int[5];
        int idx = 5;
        if (idx < arr.length) {
            System.out.println("UNREACHABLE: idx<arr.length");
        } else {
            System.out.println("ALWAYS_TRUE: idx>=arr.length");
        }
        // Intentional unsafe access to model OOB
        // Do not execute arr[idx] to avoid runtime error; just show branch
    }
}

