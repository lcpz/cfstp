# CFSTP

| **Author** | **License** |
|---|---|
| [Luca Capezzuto](https://lcpz.gitlab.io) | [MIT](https://opensource.org/licenses/MIT) |

Distributed and dynamic algorithms for solving the Coalition Formation with Spatial and
Temporal constraints Problem ([CFSTP](https://link.springer.com/chapter/10.1007/978-3-030-66412-1_38)).

## Usage

With Java 11 or above, run:

```shell
java -jar lfb-dcop-benchmark.jar --help
```

Valid problem classes:

```shell
AGENT_BASED
NDCS
C_AGENT_BASED
C_NDCS
U_AGENT_BASED
U_NDCS
UC_AGENT_BASED
UC_NDCS
```

Valid algorithms:

```shell
CTS
FMS-ADVP
DSA-SDP
```

If an option has multiple entries, these have to be separated by spaces. For
example:

```shell
java -jar lfb-dcop-benchmark.jar -p AGENT_BASED NDCS -a CTS DSA-SDP -n
path/to/nodes -s path/to/stations
```

## Building instructions

1. Import the project in Eclipse as a Maven project.
2. Update it to download the Maven dependencies.
3. Set a JRE System Library with version 11 or above.
4. Right click on the project and select `Run As -> Maven install`.

## To-do list

- Increase the data set by also considering records with duplicate IDs
- Use [BURLAP](https://github.com/jmacglashan/burlap)'s [`GridWorld`](https://github.com/jmacglashan/burlap_examples/blob/master/src/main/java/edu/brown/cs/burlap/tutorials/HelloGridWorld.java) for GUI representation (see this [tutorial](http://burlap.cs.brown.edu/tutorials/hgw/p1.html))
- Write a [microbenchmark](https://stackoverflow.com/a/513259) to measure CPU times accurately
- Integrate the [XCSP parser](https://github.com/xcsp3team/XCSP3-Java-Tools) (for DCOPs)
- Check [MASPlanes](https://github.com/MASPlanes/MASPlanes)
