---
layout: docs
toc_group: examples
link_title: Oracle Database Example
permalink: /examples/mle-oracle/
---

# Oracle Database Multilingual Engine (MLE): GraalVM in the Database

This page contains instructions on how to run the Oracle Database
Multilingual Engine (MLE) which is the integration of GraalVM in the Oracle
Database and allows dynamic execution of dynamic JavaScript code in Oracle
Database 21c.

## Preparation

Get a [Free-Tier Oracle Cloud account](https://www.oracle.com/cloud/free) and
choose a home region that offers Oracle 21c (Ashburn, Phoenix, Frankfurt,
London), provision an Autonomous Transaction Processing instance and start
your favourite SQL shell, e.g. one of the following:
- SQL Developer: go to Tools -> Open Database Actions -> SQL
- APEX' SQL Workshop

More details can be found in the blog post by Salim Hlayel entitled
[MLE and the Future of Server-Side Programming in Oracle APEX](https://blogs.oracle.com/apex/mle-and-the-future-of-server-side-programming-in-oracle-apex).

## First Steps

1&#46; Run the following piece of PL/SQL code which creates a context and evaluates one line of JavaScript code in it, producing `Hello World!` as console output:
```sql
SET SERVEROUTPUT ON;
DECLARE
  ctx dbms_mle.context_handle_t;
  user_code clob := q'~
    console.log('Hello World!');
  ~';
BEGIN
  ctx := dbms_mle.create_context();
  dbms_mle.eval(ctx, 'JAVASCRIPT', user_code);
  dbms_mle.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

Note: There is the `EXCEPTION` clause which makes sure that the context gets dropped either way. This is important to avoid resource leakage.

2&#46; Now here is an example that shows how values can be passed between PL/SQL and MLE. As expected, the output is `49`:
```sql
SET SERVEROUTPUT ON;
DECLARE
  ctx dbms_mle.context_handle_t;
  result NUMBER;
  user_code clob := q'~
    let bindings = require('mle-js-bindings');
    let val = bindings.importValue('val');
    bindings.exportValue('res', val+7);
  ~';
BEGIN
  ctx := dbms_mle.create_context();
  dbms_mle.export_to_mle(ctx, 'val', 42);
  dbms_mle.eval(ctx, 'JAVASCRIPT', user_code);
  dbms_mle.import_from_mle(ctx, 'res', result);
  dbms_mle.drop_context(ctx);
  dbms_output.put_line(result);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

3&#46; The next example shows how to use the MLE SQL Driver (`mle-js-oracledb`) to execute a SQL query and process the results:
```sql
SET SERVEROUTPUT ON;
DECLARE
  ctx dbms_mle.context_handle_t;
  user_code clob := q'~
    const oracledb = require('mle-js-oracledb');
    const conn = oracledb.defaultConnection();
    const query = 'select empno, ename from emp where sal <:1';
    const res = conn.execute(query, [3000]);
    for (let row of res.rows) {
      console.log('empno: ' + row[0] + ' name: ' + row[1]);
    }
  ~';
BEGIN
  ctx := dbms_mle.create_context();
  dbms_mle.eval(ctx, 'JAVASCRIPT', user_code);
  dbms_mle.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/

```
Under the assumption that the default employee / department schema is loaded, this produces:
```shell
empno: 7369 name: SMITH
empno: 7499 name: ALLEN
empno: 7521 name: WARD
empno: 7566 name: JONES
empno: 7654 name: MARTIN
empno: 7698 name: BLAKE
empno: 7782 name: CLARK
empno: 7844 name: TURNER
empno: 7876 name: ADAMS
empno: 7900 name: JAMES
empno: 7934 name: MILLER
```

4&#46; Finally, this example illustrates a SQL query that retrieves an Oracle TIMESTAMP object and (default) converts this to a JavaScript Date object and produces something like `[["2021-07-09T12:02:57.949Z"]]`:
```sql
SET SERVEROUTPUT ON;
DECLARE
   ctx DBMS_MLE.context_handle_t := DBMS_MLE.create_context();
   user_code clob := q'~
     const oracledb = require('mle-js-oracledb');
     const conn = oracledb.defaultConnection();
     const query = 'SELECT SYSTIMESTAMP as ts FROM dual';
     const result = conn.execute(query);
     console.log(JSON.stringify(result.rows));
   ~';
BEGIN
   DBMS_MLE.eval(ctx, 'JAVASCRIPT', user_code);
   DBMS_MLE.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

## Type Conversions

Let us now have a closer look at conversions from Oracle types (as retrieved by SQL queries) to JavaScript types.

By default, Oracle data types get automatically converted to regular, native JavaScript types, which can lead to a loss of precision.
In the following example, the object retrieved by the SQL driver is an Oracle NUMBER (`9007199254740993`) which gets converted to a JavaScript number.
JavaScript numbers can have different underlying representations; in the concrete examples it's an IEEE double precision float which cannot express that precise value and hence rounds to `9007199254740992`:
```sql
-- Despite selecting 9007199254740993, we get 9007199254740992
SET SERVEROUTPUT ON;
DECLARE
   ctx DBMS_MLE.context_handle_t := DBMS_MLE.create_context();
   user_code clob := q'~
     const oracledb = require('mle-js-oracledb');
     const conn = oracledb.defaultConnection();
     const query = "SELECT 9007199254740993 AS n FROM dual";
     const result = conn.execute(query);
     console.log(result.rows[0][0].toString());
   ~';
BEGIN
   DBMS_MLE.eval(ctx, 'JAVASCRIPT', user_code);
   DBMS_MLE.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

For applications where exact values matter, `mle-js-oracledb` provides an option to change the default conversion and use a so-called PL/SQL wrapper type, i.e. `OracleNumber`.
Those wrapper types eliminate the need for conversion to a native JavaScript type and avoid any loss of precision.
Hence we get the precise value, i.e. `9007199254740993`:
```sql
-- This time, we get the expected result, 9007199254740993.
SET SERVEROUTPUT ON;
DECLARE
   ctx DBMS_MLE.context_handle_t := DBMS_MLE.create_context();
   user_code clob := q'~
     const oracledb = require('mle-js-oracledb');
     const conn = oracledb.defaultConnection();
     const query = "SELECT 9007199254740993 AS n FROM dual";
     const options = { fetchInfo: { N: { type: oracledb.ORACLE_NUMBER } } };
     const result = conn.execute(query, [], options);
     console.log(result.rows[0][0].toString());
   ~';
BEGIN
   DBMS_MLE.eval(ctx, 'JAVASCRIPT', user_code);
   DBMS_MLE.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

If we want to add a JavaScript number to an `OracleNumber` with the `+` operator, we first have to convert the latter to a JavaScript number by calling `toNumber()`.
Again, the result suffers from a loss of precision; we get `9007199254741000`, while the precise result of adding `9007199254740992` and `7` would be `9007199254740999`:
```sql
-- Adding ORACLE NUMBER and float leads to loss of precision.
SET SERVEROUTPUT ON;
DECLARE
   ctx DBMS_MLE.context_handle_t := DBMS_MLE.create_context();
   user_code clob := q'~
     const oracledb = require('mle-js-oracledb');
     const conn = oracledb.defaultConnection();
     const query = "SELECT 9007199254740992 AS n FROM dual";
     const options = { fetchInfo: { N: { type: oracledb.ORACLE_NUMBER } } };
     const result = conn.execute(query, [], options);
     console.log(result.rows[0][0].toNumber() + 7);
   ~';
BEGIN
   DBMS_MLE.eval(ctx, 'JAVASCRIPT', user_code);
   DBMS_MLE.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

In order to get precise Oracle NUMBER arithmetics, we need to make sure that both summands have the Oracle NUMBER type and use Oracle NUMBER arithmetics for the addition.
We achieve this by constructing the second summand from a String and using the `OracleNumber.add` function to add it to the first summand.
What we obtain is the precise result i.e. `9007199254740992 + 7 = 9007199254740999`:
```sql
-- Adding two ORACLE NUMBER objects leads to an exact result.
SET SERVEROUTPUT ON;
DECLARE
   ctx DBMS_MLE.context_handle_t := DBMS_MLE.create_context();
   user_code clob := q'~
     const oracledb = require('mle-js-oracledb');
     const OracleNumber = require('mle-js-plsqltypes').OracleNumber;
     const conn = oracledb.defaultConnection();
     const query = "SELECT 9007199254740992 AS n FROM dual";
     const options = { fetchInfo: { N: { type: oracledb.ORACLE_NUMBER } } };
     const result = conn.execute(query, [], options);
     console.log(result.rows[0][0].add(OracleNumber.fromString('7')));
   ~';
BEGIN
   DBMS_MLE.eval(ctx, 'JAVASCRIPT', user_code);
   DBMS_MLE.drop_context(ctx);
EXCEPTION
  WHEN others THEN
    dbms_mle.drop_context(ctx);
    RAISE;
END;
/
```

## Further Reading

Here is a set of resources that we recommend for further reading about MLE:
- [Multilingual Engine: Executing JavaScript in Oracle Database](https://medium.com/graalvm/mle-executing-javascript-in-oracle-database-c545feb1a010) provides further insight into the MLE architecture based on GraalVM.
- [MLE and the Future of Server-Side Programming in Oracle APEX](https://blogs.oracle.com/apex/mle-and-the-future-of-server-side-programming-in-oracle-apex) shows step-by-step how to set up a free Oracle Cloud account, provision a database instance and run some JavaScript code in Oracle APEX (powered by MLE).
- [JavaScript as a Server-Side Language in Oracle APEX 20.2](https://medium.com/graalvm/javascript-as-a-server-side-language-in-oracle-apex-20-2-457e073ca4ca) shows more in-depth JavaScript-in-APEX examples, including how to use the GraalVM Polyglot interface to load popular JavaScript modules (like [validator](https://www.npmjs.com/package/validator)) from database tables and use them in an APEX app.
- [APEX + Server-side JavaScript = Awesome!](https://www.youtube.com/watch?v=voolgTBoPyE) is a talk about the MLE and how it's best used in APEX, including a life demo with many awesome examples of running JavaScript modules in the database.
- https://github.com/stefandobre/apex-mle-demo contains the sample APEX app that was used in aforementioned life demo and articles, ready to be deployed and run on a free Oracle Cloud account.
