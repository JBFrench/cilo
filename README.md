# Cilo
__Cilo__ stands for Continuous Integration LOcally and is a decentralized continuous delivery build platform with a focus on fast feedback, minimal setup, and secure credentials management. 

## Goals
The goal of __Cilo__ is to give developers the ability to build and deploy applications from their own machines while still allowing organizations to enforce quality gates and restrict direct access to secure information like passwords and other credentials.

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
  
## Use
### Run
`

NAME
    Cilo -  CI (Continuous Integration) Local
         Pronounced \ˈsīlō/

USAGE
    cilo [OPTIONS] run <pipeline>
    cilo [OPTIONS] help [command]
    cilo [OPTIONS] version
    cilo [OPTIONS] shell
    cilo [OPTIONS] secret

DESCRIPTION
      Cilo is a local and decentralized CI/CD build tool. 
    It can be used to either build software projects or 
    deploy application infrasturcture without having to worry about 
    the underlying operating system. Most CI/CD tools take a 
    centralized approach to deployments; this is problematic because 
    deployments must be centrally managed adding to cost (money/time) and 
    reducing the developer's visibilty into deployments.
      Cilo uses a local docker container to isolate builds and deployments. 
    All while loading secrets from a cilo server credential store or 
    the local host machine. Logs and build information can also be 
    transmitted to a centrallized cilo server for CO (continuous operation).

OPTIONS
    -i|--image <image-name>
            Name of docker image used to build.
            DEFAULT:   cilo
            NOTE:      Must inherit from cilo's image.

    -h|--help|--usage
            Show usage

    -l|--library <cilo-library-path>
              Will load additional build libraries with the 
            ".cilo" extension from this path.
            DEFAULT:   cilib/
            NOTE:      Must be relative to project with no leading slash and must have a trailing slash.

    --log
            Automatically logs in local directory
            DEFAULT:   log/

    --log-directory <dir>
            Write logs to a directory other than log/
            NOTE:      Must be relative to project with no leading slash and must have a trailing slash.

    --pull
            Rebuilds or pulls latest docker image.

    -s|--server <cilo-server-url>
           URL of your cilo server.

    -u|--url-library <shared-library-url>
           Download and use a shared library from a url. 

    -v|--verbose
              Writes verbose output to stdout and 
            logs when relevent.
            Url libraries must be cilo files normally with 
            the ".cilo" extension.

    -vvv
            Extra verbose...to the point of debug.

DETAILS
      Cilo operates on what's called a cilo "run". Each run has a corrisponding cilo file.
    This file is written in a language called "cilo" as a seamless combination between Bash and
    a Groovy DSL.
    Here is a sample script called "depoy.cilo":

        def tag = "${PROJECT_NAME}-${GIT_COMMIT}"

        def checkStatus(stdOut, stdErr, exitCode) {
          if (exitCode != 0) {
             fail "${stdErr}"
          }
        }                   

        step("build") {
          println """Building [${PROJECT_NAME}] from git branch [${GIT_BRANCH}] and commit [${GIT_COMMIT}]"""
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

`

### Secrets
`
 
 NAME
      Cilo Local Secrets

 USAGE
      cilo [OPTIONS] secret create <name> (string <string> | file <file> | input)
      cilo [OPTIONS] secret read   <name>
      cilo [OPTIONS] secret update <name> (string <string> | file <file> | input)
      cilo [OPTIONS] secret delete <name>

 DESCRIPTION
        Cilo has the ability to manage two types of secrets.
      Local secrets and organization secrets.
      Local secrets are managed exlusivly by these usage options which
      corrispond with typical CRUD operations:
      (create, read, update and delete).
        These types of secrets are for personal use or for a small number of
      individuals. They are stored locally on your machine in an encrypted form
      and are encrypted again using a random key that is used for only one cilo run.
      A secret is only unencrypted in memory (or file based on secret file usage) for
      the durration of a cilo secret block:

          secret("secret-name") {...}

        When the secret block exits that particular unencrypted version of 
      a secret is lost. Once the docker container exits the randomly generated 
      key is release from memory. All secrets are masked out (*******) from
      local and remote logging.

 COMMAND SUMMARY
      list
              List all local secrets by name.

      create <name> string <string>
      create <name> file <file>
      create <name> input
              Creates a local secret. Which can be taken from:
                     Command Line Argument
                     Input Filename
                     Standard Input

      read <name>
              Writes the unencrypted secret from <name> to StdOut.

      update <name> string <string>
      update <name> file <file>
      update <name> input
              Updates a local secret. Which can be taken from:
                     Command Line Argument
                     Input Filename
                     Standard Input

      delete <name>
              Deletes a secret by name
`
