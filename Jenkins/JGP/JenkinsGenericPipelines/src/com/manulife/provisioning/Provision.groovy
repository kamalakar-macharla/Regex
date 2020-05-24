package com.manulife.provisioning

/**
  *
  * This class represents one pipeline execution audit trail entry.
  *
  */
class Provision implements Serializable {
    String id                   // Transaction ID for the REST API
    String foundation           // PCF foundation provided by user
    String appName              // PCF application name provided by user
    String team                 // PCF team provided by user
    String org                  // PCF org provided by user
    String space                // PCF space provided by user
    String manifestFileName     // File manifest name that will be used in the deployment
}
