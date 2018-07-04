__attribute__((constructor)) int test() {
    char a = '0';
    short b = 1;
    int c = 2;
    long d = 3;
    __int128_t g = 8;
    float e = 4.5;
    double f = 6.7;
    long double h = 9.10;
    long double result = a + b + c + d + e + f + ((long) g) + h;
    return 0;
}

int main() {
    return 0;
}