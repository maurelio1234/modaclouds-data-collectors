Modaclouds-DataCollectors
=========================
## Requirements

Please download and install the following pieces of software before using the Data Collectors component:
* JRE 1.7 or higher from http://www.java.com/
* Collectl 3.6.7 or higher from http://collectl.sourceforge.net/
* Sigar 1.6.4 or higher from http://sourceforge.net/projects/sigar/

## Installation
First, Compile the code or download the latest release from [https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases](https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases).
Then run it as: 
```java
java -Djava.library.path=<path>/dcs/hyperic-sigar-1.6.4/sigar-bin/lib/ -jar dcs.jar <parameters>
```
The parameters contain the name of the data collectors that need to run. So it currently supports the following ones:
* jmx
* collectl
* sigar
* ofbiz
* apache
* mysql
* cloudwatch
* flexiant
* ec2-spotPrice
* startupTime
* cost
* availability
* detailedCost

You can instantiate multiple data collectors at the same time.
