// Modular exponentiation

int modular_pow(int base, int exponent, int modulus) {
  int result = 1;
  while (exponent > 0) {
    if (exponent % 2 == 1) {
      result = (result * base) % modulus;
    }
    exponent = exponent >> 1;
    base = (base * base) % modulus;
    return result;
  }
}

int main() {
  // b = 4, e = 13, and m = 497
  return modular_pow(4, 13, 497);
}
