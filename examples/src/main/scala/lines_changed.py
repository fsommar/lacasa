import difflib
import os
import re
import itertools

def partition(pred, iterable):
    'Use a predicate to partition entries into false entries and true entries'
    # partition(is_odd, range(10)) --> 0 2 4 6 8   and  1 3 5 7 9
    t1, t2 = itertools.tee(iterable)
    return list(itertools.filterfalse(pred, t1)), list(filter(pred, t2))

def filter_imports(lines):
    return list(
        filter(
            lambda x: not re.match(r'^import|^package|^\s+$', x),
            lines))

cases = {
    "BankingAsk": "BTX",
    "BitonicSort": "BSORT",
    "Chameneos": "CHAM",
    "NQueens": "NQN",
    "NQueensBox": "NQN[box]",
    "Philosopher": "PHIL",
    "ProdConsBoundedBuffer": "PCBB",
    "Sieve": "SIEVE"
}

dir_path = os.path.dirname(os.path.realpath(__file__))

s = ""
for case,keyword in cases.items():
    s += keyword + " & "
    filename1 = os.path.join(dir_path, 'akka', case.replace('Box', '') + '.scala')
    filename2 = os.path.join(dir_path, 'lacasa', case + '.scala')
    with open(filename1, 'r') as f1, open(filename2, 'r') as f2:
        linesOriginal = filter_imports(f1.readlines())
        analysis = difflib.unified_diff(
            linesOriginal,
            filter_imports(f2.readlines()), 
            n=0)
        plus, minus = partition(
            lambda x: x[0] == '+',
            filter(
                lambda x: x.startswith('+ ') or x.startswith('- '),
                analysis))
        print(''.join(plus))
        print(''.join(minus))
        s += str(max(len(plus), len(minus)) / sum(1 for _ in linesOriginal))
        s += r" \\ " + "\n"

print(s)