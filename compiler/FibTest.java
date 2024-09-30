public class FibTest {
    
    public static long fibonacciHot(int n) {
        if (n <= 1) {
            return n;
        }
        
        long fib = 1;
        long prevFib = 1;
        
        for (int i = 2; i < n; i++) {
            long temp = fib;
            fib += prevFib;
            prevFib = temp;
        }
        
        return fib;
    }
    
    public static void main(String[] args) {
        int n = 45;  // A sufficiently large number to make the method "hot"
        long startTime = System.nanoTime();
        
        // Call the method multiple times to ensure it becomes hot
        for (int i = 0; i < 10000000; i++) {
            fibonacciHot(n);
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 500000;  // Convert to milliseconds
        
        // System.out.println("Fibonacci(" + n + ") = " + fibonacciHot(n));
        System.out.println("Time taken: " + duration + " ms");
    }
}