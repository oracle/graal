
int run() {
  int i;
  int number = 600000;
  int primes[number + 1];

  for (i = 2; i <= number; i++) {
    primes[i] = i;
  }

  i = 2;
  while ((i * i) <= number) {
    if (primes[i] != 0) {
      for(int j = 2; j < number; j++) {
        if (primes[i] * j > number)
          break;
        else
          primes[primes[i] * j] = 0;
      }
    }
    i++;
  }

  return primes[number];
}

int main() {
  return run();
}

