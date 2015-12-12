from guineapig import *
from sets import Set
import sys
import re
import math

# supporting routines can go here
def countToken(line):
	tok = line.split("\t")
	labels = tok[1].split(",")
	words = tok[2].split(" ")

	for word in words:
		word = re.sub('\W', "", word)
		if len(word) > 1:
			for label in labels:
				yield (word,label)
				yield ('*',label)

	for label in labels:
		yield '+',label
		yield '+','*'


def tokens(line): 
    tok = line.split("\t")
    words = tok[2].split(" ")

    for word in words:
    	word = re.sub('\W', "", word)
    	if len(word) > 1:
    		yield (word,tok[0])
    yield ('*',tok[0])
    yield ('+', tok[0])

def test(predictAug2):
	docid = predictAug2[0][0]
	wordDict = predictAug2[0][1]
	wordSize = predictAug2[1][1]-2

	# convert label to map
	labelMap = dict()
	labels = []
	labelIndex = dict()
	index = 0

	starLabelMap = dict()
	for word in wordDict:
		if word[0] == '*':
			labelCount = word[1]
			for label in labelCount:
				starLabelMap[label[0]] = label[1]

		if word[0] == '+':
			labelCount = word[1]
			for label in labelCount:
				labelMap[label[0]] = label[1]
				if label[0] != '*':
					labels.append(label[0])
					labelIndex[label[0]] = index
					index = index + 1

	likelihood = []
	docLength = len(wordDict)-2
	for label in labels:
		likelihood.append(math.log((1.0*labelMap[label])/labelMap['*'])-docLength*math.log(1.0*starLabelMap[label]+wordSize))	

	length = len(labels)
	for word in wordDict:
		if word[0] != '*' and word[0] != '+':
			labelCount = word[1]
			for label in labelCount:
				# yield label[0],label[1],starLabelMap[label[0]],labelIndex[label[0]],wordSize
				likelihood[labelIndex[label[0]]] += math.log(1.0*label[1]+1.0)

	predictProb = likelihood[0]
	# yield likelihood[0]
	predictLabel = labels[0]
	for i in range(1, length):
		# yield likelihood[i]
		label = labels[i]
		if (likelihood[i] > predictProb):
			predictLabel = label
			predictProb = likelihood[i]

	yield (docid,predictLabel,predictProb)

#always subclass Planner
class NB(Planner):
	# training
	params = GPig.getArgvParams()
	trainFile = ReadLines(params['trainFile'])
	wordLabelCount = Flatten(trainFile,by=countToken)
	trainDict = Group(wordLabelCount, by=lambda x:x, reducingTo=ReduceToCount()) | ReplaceEach(by=lambda ((word,count),n):[word,[count,n]]) | Group(by=lambda x:x[0], retaining=lambda x:x[1])

	testFile = ReadLines(params['testFile'])
	testDict = Flatten(testFile,by=tokens)

	testWordCount = Join(Jin(trainDict,by=lambda x:x[0]), Jin(testDict,by=lambda x:x[0])) | Group(by=lambda x:x[1][1], retaining=lambda x:x[0])

	dictSize = Group(trainDict, by=lambda x:'ANY', reducingTo=ReduceToCount())
	predictAug2 = Augment(testWordCount, sideviews=[dictSize], loadedBy=lambda v1:GPig.onlyRowOf(v1))
	output = Flatten(predictAug2, by=test)

# always end like this
if __name__ == "__main__":
    NB().main(sys.argv)

# supporting routines can go here
