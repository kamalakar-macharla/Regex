package com.manulife.provisioning

/**
 *
 * Represents the result of the Provisioning API and the artifacts metadata
 *
 **/
class Request implements Serializable {
    String id
    String language
    String status
    String fileName
    String action
    String serviceId
    String serviceStatus
    String buildpack
    String applicationName
}