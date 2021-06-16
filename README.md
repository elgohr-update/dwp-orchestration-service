# orchestration-service
Orchestration service is a Kotlin Spring API that handles provisioning and deprovisioning of Analytical Tooling Environments. 

### User Tooling Environment

Orchestration Service currently deploys the following tooling environments:
* [JupyterHub](https://jupyter.org/) - [Docker Image](https://github.com/dwp/docker-jupyterhub)
* [Rstudio Open Source](https://www.rstudio.com/products/rstudio/#rstudio-server) - [Docker Image](https://github.com/dwp/docker-rstudio-oss)
* [Hue](https://docs.cloudera.com/documentation/enterprise/6/6.3/topics/hue.html) -  [Docker Image](https://github.com/dwp/dataworks-hardened-images/tree/master/hue)

It also provides access to the following services:

* Private GitHub
* User [Azkaban](https://azkaban.github.io/) - [GitHub Repo](https://github.com/dwp/aws-azkaban)

The tooling environment uses the following supporting containers:
 
 * Headless Chromium - [Docker Image](https://github.com/dwp/docker-headless-chrome) - Chromium container to access user interfaces for the services mentioned above
 * S3FS - [Docker Image](https://github.com/dwp/dataworks-hardened-images/tree/master/s3fs) - Sidecar container with elevated privileges that creates FUSE mounts to user and team S3 locations, which are also mounted into the tooling environment images
 * Guacamole Web Application - [Docker Image](https://github.com/dwp/cognito-guacamole-extension/tree/master/docker) - Tomcat app that serves the guacamole web interface and provides the third   authentication layer for the analytical environment
 * Guacd - [Docker Image](https://github.com/dwp/dataworks-hardened-images/tree/master/guacd) - Server side proxy that handles remote desktop connections for the Guacamole Web Application

### Endpoint ot submit request for user containers
 Requests using JWT to be submitted as a post request to `/connect`  
 - The JWT should be sent as a string in the header under the key `Authorisation` 
 - The JWT should contain:
    * `cognito:username` or `username` (provided by AWS Cognito)
    * `cognito:groups` - an array of the groups that the user has access to

Optional inputs for body for the request are:
  - `screenWidth` - Desired resolutiun width for the Remote Desktop window - default : 1920
  - `screenHeight`- Desired resolutiun height for the Remote Desktop window - default : 1080
 
 Test requests that omit JWT to be submitted as a post request to `/debug/deploy`. To enable this endpoint the `orchestrationService.debug` environment variable must be set to `true`.
 
 Header of request must contain the following:
  - `Authorisation`*
  - `cognito:groups` - can be an empty string
     

  
\* `Authorisation` field in request header is for JWT String with `/connect` endpoint or a test username (as a String) with `/debug/deploy` endpoint.
     

### Monitoring
 This application uses Spring Actuator to provide metrics and healthchecks, which can
 be found on the following endpoints:
  - Prometheus metrics: `/metrics`
  - Healthchecks:
     * all: `/health`
     * self: `/health/ping`
     * AWS Service connections: `/health/aws`

![Image of Orchestration Service](OrchestrationService.png)
