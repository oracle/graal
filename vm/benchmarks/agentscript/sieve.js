function Natural() {
    this.x = 2;
};
Natural.prototype.next = function() {
    return this.x++;
};

function Filter(number) {
    this.number = number;
    this.next = null;
    this.last = this;
}
Filter.prototype.acceptAndAdd = function(n) {
  var filter = this;
  var sqrt = Math.sqrt(n);
  for (;;) {
      if (n % filter.number === 0) {
          return false;
      }
      if (filter.number > sqrt) {
          break;
      }
      filter = filter.next;
  }
  var newFilter = new Filter(n);
  this.last.next = newFilter;
  this.last = newFilter;
  return true;
};

function Primes(natural) {
    this.natural = natural;
    this.filter = null;
}
Primes.prototype.next = function() {
    for (;;) {
        var n = this.natural.next();
        if (this.filter === null) {
            this.filter = new Filter(n);
            return n;
        }
        if (this.filter.acceptAndAdd(n)) {
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
if (typeof setTimeout === 'undefined') {
    var pending = [];
    setTimeout = function(r) {
        var process = pending.length === 0;
        pending.push(r);
        if (process) {
            while (pending.length > 0) {
                pending[0]();
                pending.shift();
            }
        }
    };
}

function oneRound() {
    log("Hundred thousand prime numbers in " + measure(97, 100000) + " ms");
    if (count-- > 0) {
        setTimeout(oneRound, 50);
    }
}
setTimeout(oneRound, 50);
