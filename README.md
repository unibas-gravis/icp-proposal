# A Closest Point Proposal for MCMC-based Probabilistic Surface Registration

This repository contains all the code to reproduce our results from our recent ECCV2020 publication:
- Dennis Madsen, Andreas Morel-Forster, Patrick Kahr, Dana Rahbani, Thomas Vetter and Marcel Lüthi ["A Closest Point Proposal for MCMC-based Probabilistic Surface Registration"](Pre-print https://arxiv.org/abs/1907.01414) 

[Video presentation @ ECCV2020](https://www.youtube.com/watch?v=ge4LYNAVB2c)

BibTex:
```bibtex
@InProceedings{Madsen_2020_ECCV,
author = {Madsen, Dennis and Morel-Forster, Andreas and Kahr, Patrick and Rahbani, Dana and Vetter, Thomas and L{\"u}thi, Marcel},
title = {A Closest Point Proposal for MCMC-based Probabilistic Surface Registratio},
booktitle = {Proceedings of the European Conference on Computer Vision (ECCV)},
month = {August},
year = {2020}
}
```

Markov Chain Monte Carlo for shape registration with examples using [Scalismo](https://github.com/unibas-gravis/scalismo).


## Overview


## Femur experiments
The experiments are found under *apps/femur*. The repository already contains Gaussian Process Morphable Models (GPMMs) of the femur - approximated with 50 and 100 basis functions. 
A synthetic target is also provided. 
Without downloading the full test dataset from SMIR, the registration can be performed on the target mesh.

- **IcpRegistration**: Perform a normal ICP registration.
- **IcpProposalRegistration**: Performs a probabilistic registration using *our* method described in the paper.
- **GreateGPmodel**: Create a new GPMM with a user defined number of basis functions.

The log file for the target registration with 100.000 samples is avilable, the following scripts can be used for replay and visualisation:

- **ReplayFittingFromLog**: Replay a fitting from a log file
- **PosteriorVariabilityToMeshColor**: Create a color map on the MAP solution to visualise the registration uncertainty

### Data preparation
The test dataset we use is the same as used in the [Statistical Shape Modelling course on futurelearn](https://www.futurelearn.com/courses/statistical-shape-modelling) and can be downloaded from SMIR:
[comment]: <> (Registration procedure from the SSM course <https://www.futurelearn.com/courses/statistical-shape-modelling/0/steps/16884>)

- Go to the [SMIR registraiton page](https://www.smir.ch/Account/Register).
- Fill in your details, and select **SSM.FUTURELEARN.COM** as a research unit.
- *This will send a request to an administrator to authorize your account creation. Please bare in mind that this might take **up to 24h**. You will be informed by Email once your account creation is authorized.*
- Follow the [instructions on Sicas Medical Image Repository (SMIR)](https://www.smir.ch/courses/FutureLearnSSM/2016) to download the required femur surfaces and corresponding landmarks.
- We only use the data from **Step 2** of the project
- Extract the folder under **data/femur/SMIR**, such that the mesh **0.stl** can be found under **data/femur/SMIR/step2/meshes**
- Align all the test meshes to the model by running the **apps/femur/AlignShapes** script

To compare the Markov-Chain random-walk to our ICP-proposal, run the script: **apps/femur/RunMHrandomInitComparson**. This script will start 5 fittings in parallel with different initial starting points for the femur GPMM.

To compare the standard ICP fitting to our ICP-proposal either with Euclidean average evaluator or Hausdorff evaluator, run the script: **apps/femur/StdIcpVsChainICPrandomInitComparsonAll**. This will perform registration on all the femurs from SMIR and do so 100 times for each mesh, each registration having a different initial starting point for the femur GPMM.


## BFM experiments

### Data preparation
Download the Basel Face Model (BFM) 2017 <https://faces.dmi.unibas.ch/bfm/bfm2017.html>. 
For the experiments we use the cropped model: **model2017-1_face12_nomouth.h5**, which is cropped to the face region.
Place the model such that it is located: **data/bfm/model2017-1_face12_nomouth.h5** (same place where our provided **bfm.json** landmark file is located).

The test face scans are available here: <https://faces.dmi.unibas.ch/bfm/main.php?nav=1-2&id=downloads>

- **Download the 3D face scans & renderings of ten individuals (94 MB)**.
- Unpack the scans in the bfm/initial folder such that the .ply meshes are to be found under **data/bfm/initial/PublicMM1/03_scans_ply**.
- Then run the **apps/bfm/AlignShapes** script to scale and align all faces to the face model as well as creating partial target meshes.
- Then run the **apps/bfm/CreateGPModel** to create an analytically defined GPMM using the face template from the BFM.

To run the face registration, run the script: **apps/bfm/BfmFitting**.
As with the femur, the face registration can be replayed and the posterior can be visualised with the similar scripts found under **apps/bfm/**.
