
function run() {
  let i;
  let number = 600000;
  let primes = new Array(number + 1);

  for (i = 2; i <= number; i++) {
    primes[i] = i;
  }

  i = 2;
  while ((i * i) <= number) {
    if (primes[i] != 0) {
      for (let j = 2; j < number; j++) {
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

