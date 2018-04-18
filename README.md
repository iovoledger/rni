# Welcome to IOVO!
----

This is the main IOVO Reference Node Implementation repository. 

----
## What is IOVO?
The Internet of Value Omniledger is next generation Blockchain (DAG) decentralised database ecosystem. It is dedicated to giving data ownership and power over it's monetisation to those, whose lives generate it - the people.

## Disclaimer
IOVO is at an early stage of development. Please be advice that the code available here has not been intensively tested and may contain many bugs. 

## Installation & run
To generate jar

`mvn clean compile package`

To run

`java -jar target/node-0.1.jar`

WebServer works on port `12345`

## API

### Get node state

Returns list of all active nodes

`GET /` 

Request:

``` { "command": "getState" } ```

Response:
```
{
    "version": "0.0.1",
    "time": 1522174453,
    "lastBlock": "2680262203532249785",
    "numberOfBlocks": 1,
    "numberOfTransactions": 2,
    "numberOfAccounts": 3,
    "duration": 1
}
```

## Configuration

Configuration file location:
`java/resources/config.properties`

## License
```
GPL-3.0
```

