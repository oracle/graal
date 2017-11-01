function ack(m,n) {
  if (m == 0) {
    return n + 1;
  }
  if (n == 0) {
      n = 1;
  } else {
      n = ack(m, n - 1);
  }
  return ack(m - 1, n);
}

function execute() {
    ack(2,3);
}

function main() {
  return execute;
}

