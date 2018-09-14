import pygount
import os

def count_imports(filename):
    i = 0
    with open(filename) as f:
        i = sum(1 for _ in filter(lambda x: x.startswith("import"), f))
    return i

cases = {
    "BankingAsk": "BTX",
    "BitonicSort": "BSORT",
    "Chameneos": "CHAM",
    "NQueens": "NQN",
    "Philosopher": "PHIL",
    "ProdConsBoundedBuffer": "PCBB",
    "Sieve": "SIEVE"
}

dir_path = os.path.dirname(os.path.realpath(__file__))

s = ""
for case,keyword in cases.items():
    s += keyword + " "
    for dir in ['akka', 'lacasa']:
        s += " & "
        filename = os.path.join(dir_path, dir, case + '.scala')
        analysis = pygount.source_analysis(filename, group=case)
        lines = analysis.code - count_imports(filename)
        print(keyword, dir, lines)
        s += str(lines)
    s += r" \\ " + "\n"

print(s)