import collections
import os
import sys
import numpy as np
import math

from numpy.random import rand
from pyspark import SparkContext
import time
import pyspark

MIN_EPS = 0.005
TINY_EPS = 0.00001
PLEASE_CODE_ME = -1

# map the line to a triple (word id, doc id, tfidf)
def map_line(line):
    items = line.split(",")
    return int(items[0]), int(items[1]), float(items[2]) 

def compute_max_id(word_id, doc_id):
    current_word = -1
    current_doc = -1

    for word in word_id.collect():
        current_word += 1

    for doc in doc_id.collect():
        current_doc += 1

    return current_word, current_doc

def calculate_loss(pred_matrix, true_matrix):
    # TODO: calculate the loss and RMSE here, and print to stdout
    # Note: pred_matrix is a matrix of num_docs x num_words
    # Note: true_matrix is the result of ratings.collect()
    error = 0.0
    num_nonzero_entries = 1

    # TODO: calculate RMSE from error and num_nonzero_entries
    for tuple in true_matrix:
        for t in tuple[1]:
            # print t
            if (t[2] != 0):
                # print tuple[1][0],tuple[1][1],tuple[1][2]
                tmp = pred_matrix[t[0]][t[1]]-t[2]
                error += tmp * tmp
                num_nonzero_entries += 1

    rmse = math.sqrt(error/num_nonzero_entries)
    print('loss: %f, RMSE: %f' % (error, rmse))


def get_worker_id_for_position(word_id, doc_id):
    # TODO: code this. You should be able to determine the worker id from the word id or doc id,
    # or a combination of both. You might also need strata_row_size and/or strata_col_size
    # Suggestion is to return either a column block index or a row block index
    # Assign columns or rows to specific workers
    row = word_id / strata_row_size_bc.value

    return row


def blockify_matrix(worker_id, partition):
    blocks = collections.defaultdict(list)
    # Note: partition is a list of (worker_id, (word_id, doc_id, tf_idf))
    # This function should aggregate all elements which belong to the same block
    # Basically, it should return a single tuple for each block in the partition.
    # Each of these tuples should have the format:
    # ( (col_block_index, row_block_index), list of (word_id, doc_id, tf_df))

    # You can use the col_block_index and row_block_index of each of these tuples
    # to determine which of the blocks should be processed on each iteration.

    # Output the blocks. Output should be several of
    # ( (row block index, col block index), list of (word_id, doc_id, tf_idf) )
    # note that worker id is probably one of row block index or col block index
    for p in partition:
        row = p[1][0] / strata_row_size_bc.value
        col = p[1][1] / strata_col_size_bc.value
        blocks[(row, col)].append(p[1])

    for item in blocks.items():
        yield item


def filter_block_for_iteration(num_iteration, col_block_index, row_block_index):
    
    return ((col_block_index-row_block_index) % strata_num_workers_bc.value) == (num_iteration % strata_num_workers_bc.value)


def perform_sgd(block):
    (row_block, col_block), tuples = block
    # print 'col_block', col_block, 'row_block', row_block
    srs = strata_row_size_bc.value
    scs = strata_col_size_bc.value
    # print 'srs', srs, 'scs', scs

    # TODO: determine row_start and col_start based on row_block, col_block and
    # strata_row_size and strata_col_size.
    row_start = row_block * srs # use col_block and row_block to transform to real row and col indexes
    col_start = col_block * scs # use col_block and row_block to transform to real row and col indexes
    w_mat_block = w_mat_bc.value[row_start:row_start + srs, :]
    h_mat_block = h_mat_bc.value[col_start:col_start + scs, :]
    # Note: you need to use block indexes for w_mat_block and h_mat_block to access w_mat_block and h_mat_block

    num_updated = 0
    for word_id, doc_id, tfidf_score in tuples:
        # print word_id, row_start, doc_id, col_start
        # update w_mat_block, h_mat_block
        i = word_id-row_start
        j = doc_id-col_start
        w_i = w_mat_block[i]
        h_j = h_mat_block[j]
        WH = 0.0
        learning_rate = math.pow((100+num_old_updates_bc.value+num_updated), -strata_beta_value_bc.value)
        if learning_rate < MIN_EPS:
            learning_rate = MIN_EPS

        for k in range(strata_num_factors_bc.value):
            WH += w_i[k]*h_j[k]

        const = learning_rate*2*(tfidf_score-WH)
        w_i_ = np.multiply(h_j, const)
        h_j_ = np.multiply(w_i, const)
        w_mat_block[i] += w_i_
        h_mat_block[j] += h_j_

        # update num_updated
        num_updated += 1  

        # Note: you might need strata_row_size and strata_col_size to
        # map real word/doc ids to block matrix indexes so you can update
        # w_mat_block and h_mat_block

        # Note: Use MIN_EPS as the min value for the learning rate, in case
        # your learning rate is too small

        # Note: You can use num_old_updates here

    return row_block, col_block, w_mat_block, h_mat_block, num_updated

def toCSVFile(line):
  return ','.join(str(l) for l in line)

if __name__ == '__main__':
    # read command line arguments
    num_factors = int(sys.argv[1])
    num_workers = int(sys.argv[2])
    num_iterations = int(sys.argv[3])
    beta_value = float(sys.argv[4])
    inputV_filepath, outputW_filepath, outputH_filepath = sys.argv[5:]

    # create spark context
    conf = pyspark.SparkConf().setAppName("SGD").setMaster("local[{0}]".format(num_workers))
    sc = pyspark.SparkContext(conf=conf)

    # TODO: measure time starting here
    start_time = time.clock()

    # get tfidf_scores RDD from data
    # Note: you need to implement the function 'map_line' above.
    if os.path.isfile(inputV_filepath):
        # local file
        tfidf_scores = sc.textFile(inputV_filepath).map(map_line)
    else:
        # directory, or on HDFS
        rating_files = sc.wholeTextFiles(inputV_filepath)
        tfidf_scores = rating_files.flatMap(
            lambda pair: map_line(pair[1]))


    # get the max_word_id and max_doc_id.
    word_id = tfidf_scores.map(lambda x : x[0]).distinct().sortBy(lambda x:x)
    doc_id = tfidf_scores.map(lambda x : x[1]).distinct().sortBy(lambda x:x)
    max_word_id, max_doc_id = compute_max_id(word_id, doc_id)
    # print 'max_word_id', max_word_id, 'max_doc_id', max_doc_id

    # build W and H as numpy matrices, initialized randomly with ]0,1] values
    w_mat = rand(max_word_id + 1, num_factors) + TINY_EPS
    h_mat = rand(max_doc_id + 1, num_factors) + TINY_EPS
    w_mat = w_mat.astype(np.float32, copy=False)
    h_mat = h_mat.astype(np.float32, copy=False)

    # TODO: determine strata block size.
    strata_row_size = max_word_id / num_workers
    strata_row_size += 1

    strata_col_size = max_doc_id / num_workers
    strata_col_size += 1

    strata_col_size_bc = sc.broadcast(strata_col_size)
    strata_row_size_bc = sc.broadcast(strata_row_size)
    strata_num_workers_bc = sc.broadcast(num_workers)
    strata_num_factors_bc = sc.broadcast(num_factors)
    strata_beta_value_bc = sc.broadcast(beta_value)

    # TODO: map scores to (worker id, (word id, doc id, tf idf score) (implement get_worker_id_for_position)
    # Here we are assigning each cell of the matrix to a worker
    tfidf_scores = tfidf_scores.map(lambda score: (
        get_worker_id_for_position(score[0], score[1]),
        (
            score[0], # word id
            score[1], # doc id
            score[2]  # tf idf score
        )
        # partitionBy num_workers, by doing this we are distributing the
        # partitions of the RDD to all of the workers. Each worker gets one partition.
        # Lastly, we do a mapPartitionsWithIndex so each worker can group together
        # all cells that belong to the same block.
        # Make sure we preserve partitioning for correctness and parallelism efficiency
    )).partitionBy(num_workers) \
      .mapPartitionsWithIndex(blockify_matrix, preservesPartitioning=True) \
      .cache()

    # finally, run sgd. Each iteration should update one strata.
    num_old_updates = 0
    for current_iteration in range(num_iterations):
        # perform updates for one strata in parallel

        # broadcast factor matrices to workers
        w_mat_bc = sc.broadcast(w_mat)
        h_mat_bc = sc.broadcast(h_mat)
        num_old_updates_bc = sc.broadcast(num_old_updates)

        # perform_sgd should return a tuple consisting of:
        # (row block index, col block index, updated w block, updated h block, number of updates done)
        # s[0][0] is the col or row block index, s[0][1] is the col or row block index
        updated = tfidf_scores \
            .filter(lambda s: filter_block_for_iteration(current_iteration, s[0][0], s[0][1])) \
            .map(perform_sgd, preservesPartitioning=True) \
            .collect()

        # unpersist outdated old factor matrices
        w_mat_bc.unpersist()
        h_mat_bc.unpersist()
        num_old_updates_bc.unpersist()


        srs = strata_row_size_bc.value
        scs = strata_col_size_bc.value
        # aggregate the updates, update the local w_mat and h_mat
        for block_row, block_col, updated_w, updated_h, num_updates in updated:
            # update w_mat and h_mat matrices

            # map block_row block_col to real indexes (words and doc ids)
            w_update_start = block_row * srs
            w_update_end = w_update_start + srs
            w_mat[w_update_start:w_update_end, :] = updated_w

            h_update_start = block_col * scs
            h_update_end = h_update_start + scs
            h_mat[h_update_start:h_update_end, :] = updated_h

            num_old_updates += num_updates

        calculate_loss(np.dot(w_mat, h_mat.transpose()), tfidf_scores.collect())

    # print running time
    print time.clock()-start_time

    np.savetxt(outputW_filepath, w_mat, delimiter=',')
    np.savetxt(outputH_filepath, h_mat.transpose(), delimiter=',')

    # Stop spark
    sc.stop()

    # TODO: print w_mat and h_mat to outputW_filepath and outputH_filepath
