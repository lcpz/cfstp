# CFSTP

| **Author** | **License** |
|---|---|
| [Luca Capezzuto](https://lcpz.gitlab.io) | [MIT](https://opensource.org/licenses/MIT) |

Algorithms for solving the Coalition Formation with Spatial and Temporal
constraints Problem ([CFSTP](https://eprints.soton.ac.uk/268497/)).

## Dependencies

- Java 11+
- [Eclipse IDE](https://www.eclipse.org/eclipseide)
- [Apache Maven](https://maven.apache.org)
- [Apache Commons Lang 3](https://commons.apache.org/proper/commons-lang)
- [Apache Commons Math 3](https://commons.apache.org/proper/commons-math)

## Usage

1. Import the project in Eclipse as a Maven project
2. Update it to download the library dependencies
3. Run `src/test/java/solvers/SolversTest`

## To-do list

- [ ] Test with [MASPlanes](https://github.com/MASPlanes/MASPlanes)
- [ ] Integrate the [XCSP parser](https://github.com/xcsp3team/XCSP3-Java-Tools) (for DCOPs)
- [ ] Use [BURLAP](https://github.com/jmacglashan/burlap)'s [`GridWorld`](https://github.com/jmacglashan/burlap_examples/blob/master/src/main/java/edu/brown/cs/burlap/tutorials/HelloGridWorld.java) for GUI representation (see this [tutorial](http://burlap.cs.brown.edu/tutorials/hgw/p1.html))
- [ ] Write a [microbenchmark](https://stackoverflow.com/a/513259) to measure
  computation time accurately (or use [Stopwatch](https://www.javarticles.com/2016/02/junit-stopwatch-rule-example.html))