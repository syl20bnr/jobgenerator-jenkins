Job Generator Plugin [![Build Status](https://buildhive.cloudbees.com/job/jenkinsci/job/jobgenerator-plugin/badge/icon)](https://buildhive.cloudbees.com/job/jenkinsci/job/jobgenerator-plugin/)
====================

A simple plugin to generate projects based on a custom set of template
parameters.

Why this plugin ?
-----------------
This plugin is a good fit for generating pipelines based on a template with parameters. Typical parameter is the branch name or base directory where to execute a job.

It is possible to do it with the built-in build parameters of Jenkins but you have to manage input/output parameters for each job (i.e. duplicate the branch name parameter) if you want to be able to execute a job manually in the pipeline. Moreover some plugins don't expand variables which can be a show-stopper till it gets fixed. Last but not least you may want to avoid to pollute the job history with different parameters which make hard to follow trends (i.e. different branch locations) and you need history isolation with different explicit jobs pre-configured with each parameter value.

Of course you don't want to do this by hand as the number of jobs can grow quickly if you have a big pipeline. A lot of team work around this by developing external tools to duplicate jobs, the Job Generator plugin proposes a solution integrated into Jenkins which does not need any restart of the server.

Features
--------
- Job generators can be parametrized with a new build parameter type called Template Parameter.
- Support for downstream job generators (can generate a whole pipeline by triggering the top most job generator).
- Template Parameters of the top most job generator are available in the downstream job generators (no need to duplicate the parameters).
- Support for custom display name (does not overlap the display name of the job generator itself).
- Support for Delete operation (can be used to delete a whole pipeline by triggering the top most generator).
- Try to be as safer as possible by requiring explicit action from the user for critical operations (overwrite and delete).
- Support for all plugins available in Free-Style jobs.
- Hot creation/deletion of jobs. No need to reload from disk and restart jenkins.

Usage
-----
You define your whole pipeline with Job Generators which are exactly like Free-Style Build Software jobs but with some additional configuration items which are wiped out in the generated job configurations. In the top most Job Generator you declare your Template Parameters (for instance BRANCH_NAME). All Template Parameters declared in the top most Job are automatically made available to the downstream Jobs so you can start generating the pipeline at any level.

To let the display name parameter available to the Job Generator, a special configuration item is in the Advanced Project Options section in order to define the display name for the generated jobs.

To generate the pipeline, trigger a build on the top most Job Generator job and define the template parameters, set the options and hit Generate. The jobs are generated and directly usable, no need to restart the Jenkins instance.

A good practice is to prefix Job Generator names and put them in a special view with a matching regular expression.

