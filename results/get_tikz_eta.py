#!/usr/bin/env python3

# plots performance difference of CTS wrt DSA-SDP

import re, sys

labels = { # hard-coded, but quick
    0 : 'messagesSent',
    1 : 'networkLoad',
    2 : 'NCCCs',
    3 : 'percentageOfVisitedNodes',
    4 : 'CPU time'
}

def main():
    summary_file = 'summary.txt'

    if len(sys.argv) > 1:
        summary_file = sys.argv[1] # the input summary file
    else:
        return

    d = {}

    with open(summary_file) as f:
        for line in f:
            cr = re.match(r'^.*-\d+', line).group(0).split('-') # class-ratio

            if cr[0] not in d:
                d[cr[0]] = {}

            solver = re.findall('(\w+-*\w*) \[', line)[0]

            i = 0
            for entry in re.findall('\d+.?\d* \+\- \[\d+.?\d* \d+.?\d*\]', line):
                e = entry.split(' +- ')

                median = float(e[0])
                ci = e[1].replace('[', '').replace(']', '')
                key = labels[i]

                if key not in d[cr[0]]:
                    d[cr[0]][key] = {}

                if solver not in d[cr[0]][key]:
                    d[cr[0]][key][solver] = {}

                # problem-type, metric, solver, ratio
                d[cr[0]][key][solver][float(cr[1])] = '{} {}'.format(median, ci)

                i += 1

    for problem_class, keys in d.items():
        print(problem_class)

        for key, value in keys.items(): # metric
            print(key)

            median = {}
            median_plus = {}
            median_minus = {}

            for k, v in value.items(): # solver
                xarr = list(v.keys())
                yarr = []
                yarr_plus = []
                yarr_minus = []

                for x, s in v.items(): # values
                    arr = s.split(' ') # [y, CI_plus, CI_minus]
                    yarr.append(float(arr[0]))
                    yarr_plus.append(float(arr[0]) + float(arr[1]))
                    yarr_minus.append(float(arr[0]) - float(arr[2]))

                median[k] = {}
                median_plus[k] = {}
                median_minus[k] = {}

                for i,_ in enumerate(yarr):
                    median[k][int(xarr[i])] = yarr[i]
                    median_plus[k][int(xarr[i])] = yarr_plus[i]
                    median_minus[k][int(xarr[i])] = yarr_minus[i]

            print('median')
            for k in median['CTS'].keys():
                if median['CTS'][k] != 0:
                    p = median['DSA-SDP'][k] / median['CTS'][k]
                    print('({}, {:.2f})'.format(k, p))

            print('confidence interval upper bound')
            for k in median_plus['CTS'].keys():
                if median_plus['CTS'][k] != 0:
                    p = median_plus['DSA-SDP'][k] / median_plus['CTS'][k]
                    print('({}, {:.2f})'.format(k, p))

            print('confidence interval lower bound')
            for k in median_minus['CTS'].keys():
                if median_minus['CTS'][k] != 0:
                    p = median_minus['DSA-SDP'][k] / median_minus['CTS'][k]
                    print('({}, {:.2f})'.format(k, p))

if __name__ == "__main__":
    main()
