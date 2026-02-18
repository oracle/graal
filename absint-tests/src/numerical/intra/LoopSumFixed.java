/*
 Expected invariants:
 - loop index i in [0,4]
 - sum s in [0, 10] if add 0..2 each iteration (example uses res[i] constant 1)
*/
public class LoopSumFixed {
    public static int main(String[] args) {
        int s = 0;
        int[] res = new int[] {1,1,1,1,1};
        for (int i = 0; i < 5; i++) {
            s = s + res[i];
        }
        return s;
    }
}

