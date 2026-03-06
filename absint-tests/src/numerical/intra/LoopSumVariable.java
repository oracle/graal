/*
 Expected invariants:
 - loop index i in [0,n-1]
 - sum s in [0, n*5] if res[i] in [0,5]
*/
public class LoopSumVariable {
    public static int main(String[] args) {
        int s = 0;
        int n = 10;
        int[] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = 1; // or unknown value
            s += res[i];
        }
        return s;
    }
}

