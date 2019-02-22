liferay-dependencies
================

Utility provides maven dependencies for Liferay Portal as well as generator to get all these dependencies 
based on installed Liferay portal instance.

Each pom file should be installed using Maven:
'''mvn -f pom-7.1.2.ga3.xml clean install'''

Then in your project pom you can use installed artifacts:
'''    
<properties>
    <liferay.version>7.1.2.ga3</liferay.version>
</properties>
<dependency>
    <groupId>com.yurishchev.liferay</groupId>
    <artifactId>liferay-dependencies</artifactId>
    <version>${liferay.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
'''
