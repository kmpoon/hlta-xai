# HLTA for XAI

Hierarchical latent tree analysis (HLTA) has been used for hierarchical topic detection based on co-occurrence of words in documents.  We applied the same technique to find a hierarchy of clusters of labels based on co-occurrence.  The label clusters can be used for explaining the behaviour of classifiers after further processing.

# Running

## Building Models

To build a hierarchical latent tree model (HLTM), run the following command:

```java -cp HLTA-XAI.jar xai.hlta.HLTA data_file output_name```

In the above, `data_file` is the name of the data file, `output_name` is the name of the output such that the resulting model will be `output_name.bif` and `HLTA-XAI.jar` is the jar library of the HLTA-XAI package.

For example:

```java -Xmx8G -cp HLTA-XAI.jar xai.hlta.HLTA test.sparse.txt.gz outmodel```

The resulting model will be named `outmodel.bif`.  The `-Xmx8G` specifies that 8GB of memory will be allocated for the Java runtime.

There are two options that may influence the structural learning.

1. `--struct-batch-size  <num>` indicates the sample size used for calculating the BIC score in the UD-test.  A larger size may lead to smaller label clusters in the model.  The number of samples used in XAI may be too large compared to the sample size that is used to derive the BIC score, hence there may be a need to specify a certain number.  The default value is 5000.  This means that when the BIC score is calculated, the BIC scores for batches of 5000 samples are calculated and then the average of the BIC scores is used in the UD-test.  It is suggested to try a range of values from 1,000 to 10,000.

2. `--struct-learn-size  <arg>` indicates the number of samples to be used in structural learning.  A larger number means more memory and CPU time will be needed.  If a number smaller than the original sample size is specified, a subset of samples will be randomly selected from the original data set.


## Extracting Trees

After building the model, a topic tree displayed in a webpage can be built by the following command:

```java -cp HLTA-XAI.jar xai.hlta.ExtractTopicTree output_name model_file```

In the above, `output_name` is the name of the output tree, `output_name` is the name of the output such that the resulting topic tree can be opened from the file `output_name.html` and `HLTA-XAI.jar` is the jar library of the HLTA-XAI package.



# HLTA for Topic Detection

If you are looking for the code for hierarchical latent tree analysis for topic detection, please go to the original [HLTA repository](https://github.com/kmpoon/hlta).


