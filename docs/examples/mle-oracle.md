---
layout: docs
toc_group: examples
link_title: Oracle Database Example
permalink: /examples/mle-oracle/
---

# Oracle Database Multilingual Engine (MLE) Based on JavaScript module

This repository contains instructions on how to run the Oracle Database Multilingual Engine (MLE),
based on the JavaScript module, with functions stored as procedures in the database.

## Preparation

Download the Docker container of the Oracle database with experimental
support for the Multilingual Engine from [Oracle Database MLE](https://labs.oracle.com/pls/apex/f?p=LABS:project_details:0:15).

2&#46; Load the Docker image:
```shell
docker load --input mle-docker-0.2.7.tar.gz
```

3&#46; Run the container (note that you can also choose to configure non-default credentials):
```shell
docker run mle-docker-0.2.7
```

4&#46; From another console window, run the `docker ps` command to show only running containers and find a necessary container ID.

5&#46; Shell into the docker container:
```shell
docker exec -ti <container_id> bash -li
```
Wait for the database to start. It may take time for
the first run. Next runs are faster.

6&#46; To verify the database has started, run the `sqlplus` from a new command shell:
```shell
sqlplus scott/tiger@localhost:1521/ORCLCDB
```

Note: `scott/tiger` is the default login/password.
`ORCLCDB` is a site identifier (SID). There can be more than one database on
the same Oracle_HOME, which is why SID is required to identify them.
If you have changed the default login/password, change the command respectively.
If `sqlplus` works, the database is ready. Exit `sqlplus`.

7&#46; Create a directory, initialize an empty node package, install the validator
module from NPM, and install the TypeScript types for the validator module:
```shell
mkdir crazyawesome
cd crazyawesome
echo "{}" > package.json
npm install validator
npm install @types/validator
```

8&#46; Deploy the validator module to the database. In the following command,
`validator` is the module name:
```shell
dbjs deploy -u scott -p tiger -c localhost:1521/ORCLCDB validator
```

9&#46; Start `sqlplus` again:
```shell
sqlplus scott/tiger@localhost:1521/ORCLCDB
```

10&#46; Use the validator module functions as the stored procedures. Make sure to put a semicolon after the query:
```shell
select validator.isEmail('oleg.selaev@oracle.com') from dual;
select validator.isEmail('oleg.selaev') from dual;
```
