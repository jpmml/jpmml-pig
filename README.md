JPMML-Pig [![Build Status](https://travis-ci.org/jpmml/jpmml-pig.png?branch=master)](https://travis-ci.org/jpmml/jpmml-pig)
=========

PMML evaluator library for the Apache Pig platform (http://pig.apache.org/).

# Features #

* Full support for PMML specification versions 3.0 through 4.2. The evaluation is handled by the [JPMML-Evaluator] (https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Apache Pig version 0.8.0 or newer.

# Overview #

A working JPMML-Pig setup consists of a library JAR file and a number of model JAR files. The library JAR is centered around the utility class `org.jpmml.pig.PMMLUtil`, which provides Pig compliant utility methods for handling most common PMML evaluation scenarios. A model JAR file contains one or more model launcher classes and a PMML resource.

The main responsibility of a model launcher class is to formalize the "public interface" of a PMML resource. A model launcher class must extend abstract Pig user-defined function (UDF) class `org.apache.pig.EvalFunc` and provide concrete implementations for the following methods:

* `#exec(Tuple)`. Handled either by the method `PMMLUtil#evaluateSimple(Class, Tuple)` or `PMMLUtil#evaluateComplex(Class, Tuple)`.
* `#outputSchema(Schema)`. Handled by the method `PMMLUtil#getResultType(Class)`.

All in all, a typical model launcher class can be implemented in 5 to 10 lines of boilerplate-esque Java source code.

The example model JAR file contains a DecisionTree model for the "iris" dataset. This model is exposed in two ways. First, the model launcher class `org.jpmml.pig.DecisionTreeIrisSimple` defines a custom function that returns the PMML target field ("Species") as a string. Second, the model launcher class `org.jpmml.pig.DecisionTreeIrisComplex` defines a custom function that returns the PMML target field ("Species") together with four output fields ("Predicted_Species", "Probability_setosa", "Probability_versicolor", "Probability_virginica") as a tuple.

# Installation #

Enter the project root directory and build using [Apache Maven] (http://maven.apache.org/):
```
mvn clean install
```

The build produces two JAR files:
* `pmml-pig/target/pmml-pig-runtime-1.0-SNAPSHOT.jar` - Library uber-JAR file. It contains the classes of the library JAR file `pmml-pig/target/pmml-pig-1.0-SNAPSHOT.jar`, plus all the classes of its transitive dependencies.
* `pmml-pig-example/target/pmml-pig-example-1.0-SNAPSHOT.jar` - Example model JAR file.

# Usage #

### Library ###

##### Installation #####

Add the library uber-JAR file to Pig classpath:
```
REGISTER /tmp/pmml-pig-runtime-1.0-SNAPSHOT.jar;
```

### Example model ###

##### Installation #####

Add the example model JAR file to Pig classpath:
```
REGISTER /tmp/pmml-pig-example-1.0-SNAPSHOT.jar;
```

##### Usage #####

Loading the `iris` data set:
```
data = LOAD '/tmp/iris.csv' USING PigStorage(',')
	AS (Sepal_Length:double, Sepal_Width:double, Petal_Length:double, Petal_Width:double);
```

Evaluating this data set using the simple UDF:
```
simple_result = FOREACH data GENERATE org.jpmml.pig.DecisionTreeIrisSimple(Sepal_Length, Sepal_Width, Petal_Length, Petal_Width);
DESCRIBE simple_result;
DUMP simple_result;
```

Evaluating this data set using the complex UDF:
```
complex_result = FOREACH data GENERATE org.jpmml.pig.DecisionTreeIrisComplex(Sepal_Length, Sepal_Width, Petal_Length, Petal_Width);
DESCRIBE complex_result;
DUMP complex_result;
```

# License #

JPMML-Pig is dual-licensed under the [GNU Affero General Public License (AGPL) version 3.0] (http://www.gnu.org/licenses/agpl-3.0.html) and a commercial license.

# Additional information #

Please contact [info@openscoring.io] (mailto:info@openscoring.io)
