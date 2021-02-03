#!/usr/bin/env python3

# workload distribution

import sys

def main():
    if len(sys.argv) > 1:
        d = {}
        with open(sys.argv[1]) as f:
            for line in f:
                t = int(line)
                if t not in d:
                    d[t] = 1
                else:
                    d[t] += 1
        for k, v in dict(sorted(d.items())).items():
            print('{} {}'.format(k, v))

if __name__ == "__main__":
    main()
