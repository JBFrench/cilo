# Cilo
__Cilo__ stands for Continuous Integration LOcally and is a decentralized CI/CD build platform with a focus on fast feedback, minimal setup, and secure credentials management. 

## Goal
The goal of __Cilo__ is to give developers the ability to build and deploy applications from their own machines while still allowing organizations to enforce quality gates and restrict direct access to secure information like passwords and other credentials.

## Run
Cilo operates on what's called a cilo "run". Each run has a corrisponding cilo file.
This file is written in a language called "cilo" as a seamless combination between Bash and
a Groovy DSL.
Here is a sample script called "depoy.cilo":
```
def tag = "${PROJECT_NAME}-${git.commitHash}"
build
def checkStatus(stdOut, stdErr, exitCode) {
      if (exitCode != 0) {
            fail "${stdErr}"
      }
}                   

step("build") {
      println """Building [${PROJECT_NAME}] from git branch [${git.branchName}] and commit [${git.commitHash}]"""
      $ ./gradlew clean assemble
      checkStatus stdOut, stdErr, exitCode
      def environment = ["TAG":"${tag}"]
      env(environment) {
            $ docker build -t "$TAG" . 
            checkStatus stdOut, stdErr, exitCode
      }
}

step("deploy") {
      secret("awsAutomationToken") {
            def imageName = awsSendToECR(awsAutomationToken, "${PROJECT_NAME}", "${BUILD_NUMBER}")
            deployToECS(awsAutomationToken, imageName)
      }
}
```
  This run revolves arount a built-in function called step. Above there are two steps,
one called "build" and one called "deploy".

  Notice bash and groovy are integrated together in cilo. A bash command or script can 
be run by prefixing a line with a dollar sign. Groovy variables can be passed as script 
variables by using the built-in function called "env". Each set of bash commands returns
a variable for their stdOut, stdErr and last exitCode.

  Secrets can be made available to a certain scope by using the built-in "secret" function.
In the case of the deploy step, it has access to a secret by the name of awsAutomationToken.
Inside of this scope their are three new variables: awsAutomationToken, awsAutomationTokenBytes and
awsAutomationTokenFile. Having a secret in a string is nice; but other forms are for when there
is a need to have binary data accessible also. These variables are availible in groovy and bash.

## Secrets
  Cilo has the ability to manage two types of secrets.
Local secrets and organization secrets.

  Local secrets are managed exlusivly by these usage options which
corrispond with typical CRUD operations: (create, read, update and delete).

  These types of secrets are for personal use or for a small number of
individuals. They are stored locally on your machine in an encrypted form
and are encrypted again using a random key that is used for only one cilo run.
A secret is only unencrypted in memory (or file based on secret file usage) for
the durration of a cilo secret block:
```
secret("secret-name") {
      println ${secret-name}
}
```
  When the secret block exits that particular unencrypted version of 
a secret is lost. Once the docker container exits the randomly generated 
key is release from memory. All secrets are masked out (\*\*\*\*\*\*\*) from
local and remote logging. So if the value of the secret "secret-name" above was "hello, i am a secret" the output from loggig it anywhere would be (\*\*\*\*\*\*\*).

## Installation
### Mac
  `
  git clone git@github.com:cirrosoft/cilo.git
  `
  
### Linux
  `
  git clone git@github.com:cirrosoft/cilo.git
  `
  
### Windows (Currently not supported)
  Currently, there is no native Windows implementation, but you can download it into a linux docker container and try to run there. __WARNING__ this has not been tested and we can not garuntee its success or safety.
