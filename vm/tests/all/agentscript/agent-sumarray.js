function plus(a, b) {
  return a + b;
}

var sum = 0;
[1, 2, 3, 4, 5, 6, 7, 8, 9].forEach((n) => sum = plus(sum, n));
print(sum);
