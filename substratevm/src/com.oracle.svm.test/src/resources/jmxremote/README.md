## Remote JMX SSL and Authentication Supporting Files
In order for the remote JMX password authentication and SSL to work a few files need to be created. 

### Password Authentication 
Create the file `jmxremote.access`. In it add the line: `myrole readwrite`. This specifies a role with the read and write permissions. 

Next, create the file `jmxremote.password`. In it add the line: `myrole MYP@SSWORD`. This specifies a password for the previously created role. This password will automatically be hashed and updated in the file once the first connection is made. No further action is needed.

### SSL
The remote JMX unit test automatically generates the necessary files in a temporary directory. 
However, these are the steps to generate them manually:

Make the client keystore and client key. 
```
keytool -genkeypair -keystore clientkeystore -alias clientkey -validity 99999 -storepass clientpass -keypass clientpass -keyalg rsa
```

Create the client certificate file so that we can put in the server's truststore later.
```
keytool -exportcert -keystore clientkeystore -alias clientkey -storepass clientpass -file client.cer
```

Put the client's certificate in the server's truststore (also creating the truststore).
```
keytool -importcert -file client.cer -keystore servertruststore -storepass servertrustpass
```

