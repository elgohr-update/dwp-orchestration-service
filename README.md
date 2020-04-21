# orchestration-service
The service orchestrator for providing remote access into the analytical environment

###Endpoint ot submit request for user containers 
 Requests using JWT to be submitted as a post request to `/connect`  
 
 Test requests that omit JWT to be submitted as a post request to `/deployusercontainers` 
 
 Header of request must contain the following:
  - `Authorisation`*
     
  Optional inputs for body of request are:
  - `containerPorts`        - default : 443
  - `jupyterCpu`            - default : 512
  - `jupyterMemory `        - default : 512
  - `additionalPermissions`** - default : null
  
     \* `Authorisation` field in request header is for JWT String with `/connect` endpoint or a test username (as a String) with `/deployusercontainers` endpoint.
     
     \** `additionalPermissions` should be an array eg. `["s3:List*", "s3:Get*"]`

![Image of Orchestration Service](OrchestrationService.png)
