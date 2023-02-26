function Natural() {
    this.x = 2;
};
Natural.prototype.next = function() {
    return this.x++;
};
let acceptAndAdd = function(n, filter) {
  var sqrt = Math.sqrt(n);
  for (let i = 0; i < filter.length; i++) {
      if (n % filter[i] === 0) {
          return false;
      }
      if (filter[i] > sqrt) {
          break;
      }
  }
  filter.push(n);
  return true;
};

function Primes(natural) {
    this.natural = natural;
    this.filter = [];
}
Primes.prototype.next = function() {
    for (;;) {
        var n = this.natural.next();
        if (acceptAndAdd(n, this.filter)) {
            return n;
        }
    }
};

function measure(prntCnt, upto) {
    var primes = new Primes(new Natural());

    var start = new Date().getTime();
    var cnt = 0;
    var res = -1;
    for (;;) {
        res = primes.next();
        cnt++;
        if (cnt % prntCnt === 0) {
            log("Computed " + cnt + " primes in " + (new Date().getTime() - start) + " ms. Last one is " + res);
            prntCnt *= 2;
        }
        if (upto && cnt >= upto) {
            break;
        }
    }
    return new Date().getTime() - start;
}

var log = typeof console !== 'undefined' ? console.log : (
    typeof println !== 'undefined' ? println : print
);
if (typeof count === 'undefined') {
    var count = 256 * 256;
    if (typeof process !== 'undefined' && process.argv.length === 3) {
      count = new Number(process.argv[2]);
    }
}

log("Hundred thousand prime numbers in " + measure(97, 100000) + " ms");
