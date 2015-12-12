import org.petuum.jbosen.PsApplication;
import org.petuum.jbosen.PsTableGroup;
import org.petuum.jbosen.table.IntTable;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import org.apache.commons.math3.special.*;

public class LdaApp extends PsApplication {

  private static final int TOPIC_TABLE = 0;
  private static final int WORD_TOPIC_TABLE = 1;
  
  private String outputDir;
  private int numWords;
  private int numTopics;
  private double alpha;
  private double beta;
  private int numIterations;
  private int numClocksPerIteration;
  private int staleness;
  private DataLoader dataLoader;
  private Random random;

  public LdaApp(String dataFile, String outputDir, int numWords, int numTopics,
                double alpha, double beta, int numIterations,
                int numClocksPerIteration, int staleness) {
    this.outputDir = outputDir;
    this.numWords = numWords;
    this.numTopics = numTopics;
    this.alpha = alpha;
    this.beta = beta;
    this.numIterations = numIterations;
    this.numClocksPerIteration = numClocksPerIteration;
    this.staleness = staleness;
    this.dataLoader = new DataLoader(dataFile);
    this.random = new Random();
  }

  public double logDirichlet(double[] alpha) {
		double sumLogGamma=0.0;
		double logSumGamma=0.0;
    
		for (double value : alpha){
			sumLogGamma += Gamma.logGamma(value);
      if (sumLogGamma != sumLogGamma) {
        System.out.print(value+" ");
        break;
      }
			logSumGamma += value;
		}
    // System.out.println();
		return sumLogGamma - Gamma.logGamma(logSumGamma);
	}

	public double logDirichlet(double alpha, int k) {
    return k * Gamma.logGamma(alpha) - Gamma.logGamma(k*alpha);
	}
	
	public double[] getRows(IntTable matrix, int columnId) {
		double[] rows = new double[this.numWords];
		for (int i = 0; i < this.numWords; i ++){
			rows[i] = (double) matrix.get(i, columnId);
		}
		return rows;
	}

  public double[] getTableRows(IntTable matrix, int rowId) {
    double[] rows = new double[this.numTopics];
    for (int i = 0; i < this.numTopics; i ++){
      rows[i] = (double) matrix.get(rowId, i);
    }
    return rows;
  }
	
	public double[] getColumns(int[][] matrix, int rowId){
		double[] cols = new double[this.numTopics];
		for (int i = 0; i < this.numTopics; i ++){
			cols[i] = (double) matrix[rowId][i];
		}
		return cols;
	}

	public double getLogLikelihood(IntTable wordTopicTable,
                                 int[][] docTopicTable) {
	  double lik = 0.0;
	  for (int k = 0; k < this.numTopics; k ++) {
		  double[] temp = this.getRows(wordTopicTable, k);
		  for (int w = 0; w < this.numWords; w ++) {
				 temp[w] += this.alpha;
		  }
		  lik += this.logDirichlet(temp);
		  lik -= this.logDirichlet(this.beta, this.numWords);
	  }
	  for (int d = 0; d < docTopicTable.length; d ++) {
		  double[] temp = this.getColumns(docTopicTable, d);
		  for (int k = 0; k < this.numTopics; k ++) {
			 temp[k] += this.alpha;
		  }
		  lik += this.logDirichlet(temp);
		  lik -= this.logDirichlet(this.alpha, this.numTopics);
	  }

	  return lik;
  }
  
  @Override
  public void initialize() {
    // Create global topic count table. This table only has one row, which
    // contains counts for all topics.
    PsTableGroup.createDenseIntTable(TOPIC_TABLE, staleness, numTopics);
    // Create global word-topic table. This table contains numWords rows, each
    // of which has numTopics columns.
    PsTableGroup.createDenseIntTable(WORD_TOPIC_TABLE, staleness, numTopics);
  }

  @Override
  public void runWorkerThread(int threadId) {
    int clientId = PsTableGroup.getClientId();

    // Load data for this thread.
    System.out.println("Client " + clientId + " thread " + threadId +
                       " loading data...");
    int part = PsTableGroup.getNumLocalWorkerThreads() * clientId + threadId;
    int numParts = PsTableGroup.getNumTotalWorkerThreads();
    int[][] w = this.dataLoader.load(part, numParts);

    // Get global tables.
    IntTable topicTable = PsTableGroup.getIntTable(TOPIC_TABLE);
    IntTable wordTopicTable = PsTableGroup.getIntTable(WORD_TOPIC_TABLE);
    
    // Initialize LDA variables.
    System.out.println("Client " + clientId + " thread " + threadId +
                       " initializing variables...");
    int[][] docTopicTable = new int[w.length][this.numTopics];
    int[][] docWordTopic = new int[w.length][];

    // initilization
    for (int i = 0; i<w.length; i++) {
      docWordTopic[i] = new int[w[i].length];
      for (int j = 0; j<w[i].length; j++) {
        int topic = this.random.nextInt(this.numTopics);
        docTopicTable[i][topic] ++;
        topicTable.inc(0, topic, 1);
        wordTopicTable.inc(w[i][j], topic, 1);
        docWordTopic[i][j] = topic;
      }
    }

    // Global barrier to synchronize word-topic table.
    PsTableGroup.globalBarrier();

    // Do LDA Gibbs sampling.
    System.out.println("Client " + clientId + " thread " + threadId +
                       " starting gibbs sampling...");
    double[] llh = new double[this.numIterations];
    double[] sec = new double[this.numIterations];
    double totalSec = 0.0;
    for (int iter = 0; iter < this.numIterations; iter ++) {
      long startTime = System.currentTimeMillis();
      // Each iteration consists of a number of batches, and we clock
      // between each to communicate parameters according to SSP.
      for (int batch = 0; batch < this.numClocksPerIteration; batch ++) {
        int begin = w.length * batch / this.numClocksPerIteration;
        int end = w.length * (batch + 1) / this.numClocksPerIteration;
        // Loop through each document in the current batch.
        for (int d = begin; d < end; d ++) {
          for (int i = 0; i<w[d].length; i++) {
            int t = docWordTopic[d][i];
            docTopicTable[d][t] --;
            topicTable.inc(0, t, -1);
            wordTopicTable.inc(w[d][i], t, -1);

            // sample new topic
            double[] Ndk = getColumns(docTopicTable, d);
            double[] distribution = new double[this.numTopics];
            double[] Nkv = getTableRows(wordTopicTable, w[d][i]);
            double[] Nk = getTableRows(topicTable, 0);
            double sum = 0.0;
            for (int topic = 0; topic<this.numTopics; topic ++) {
                distribution[topic] = (Nkv[topic]+this.beta)/(Nk[topic]+this.beta*this.numWords)*(Ndk[topic]+this.alpha);
                sum += distribution[topic];
            }
            for (int topic = 0; topic<this.numTopics; topic ++) {
              distribution[topic] = distribution[topic]/sum;
            }

            double nextDouble = this.random.nextDouble();
            int newT = 0;
            for (; newT < this.numTopics; newT++) {
              if (distribution[newT] > nextDouble)
                break;
              else
                nextDouble -= distribution[newT];
            }
            // System.out.println("new topic: "+newT); 
            docWordTopic[d][i] = newT;
            docTopicTable[d][newT] ++;
            topicTable.inc(0, newT, 1);
            wordTopicTable.inc(w[d][i], newT, 1);
          }
        }
        // Call clock() to indicate an SSP boundary.
        PsTableGroup.clock();
      }
      // Calculate likelihood and elapsed time.
      totalSec += (double) (System.currentTimeMillis() - startTime) / 1000; 
      sec[iter] = totalSec;
      llh[iter] = this.getLogLikelihood(wordTopicTable, docTopicTable);
      System.out.println("Client " + clientId + " thread " + threadId +
                         " completed iteration " + (iter + 1) +
                         "\n    Elapsed seconds: " + sec[iter] +
                         "\n    Log-likelihood: " + llh[iter]);
    }

    PsTableGroup.globalBarrier();

    // Output likelihood.
    System.out.println("Client " + clientId + " thread " + threadId +
                       " writing likelihood to file...");
    try {
      PrintWriter out = new PrintWriter(this.outputDir + "/likelihood_" +
                                        clientId + "-" + threadId + ".csv");
      for (int i = 0; i < this.numIterations; i ++) {
        out.println((i + 1) + "," + sec[i] + "," + llh[i]);
      }
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    
    PsTableGroup.globalBarrier();
    
    // Output tables.
    if (clientId == 0 && threadId == 0) {
      System.out.println("Client " + clientId + " thread " + threadId +
                         " writing word-topic table to file...");
      try {
        PrintWriter out = new PrintWriter(this.outputDir + "/word-topic.csv");
        for (int i = 0; i < numWords; i ++) {
          out.print(wordTopicTable.get(i, 0));
          for (int k = 1; k < numTopics; k ++) {
            out.print("," + wordTopicTable.get(i, k));
          }
          out.println();
        }
        out.close();
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    PsTableGroup.globalBarrier();

    System.out.println("Client " + clientId + " thread " + threadId +
                       " exited.");
  }

}
