
long A;
int B;
short C;
char D;
long double E;
double F;
float G;
__int128_t H;
static long *I;

__attribute__((constructor)) int test() {
    A = 1;
    B = 2;
    C = 3;
    D = 4;
    E = 5.6;
    F = 7.8;
    G = 9.10;
    H = 11;
    I = &A;
    return 0;
}
