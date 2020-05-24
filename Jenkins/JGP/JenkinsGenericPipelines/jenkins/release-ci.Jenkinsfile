pipelineJavaMavenContinuousIntegration(
  [
    propertiesFileName:'release-ci.properties',
    jenkinsJobInitialAgent: 'multi-platform-general',
    jenkinsJobTimeOutInMinutes: 60,
    jenkinsJobTriggerOnPush: true,
    jenkinsJobTriggerOnMergeRequest: true,
    jenkinsJobRegEx: '^release.*',            
    jenkinsJobSecretToken: 'JGP',
  ]
)
