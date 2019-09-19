def call(Map parameters = [:]) {
    handleStepErrors(stepName: 'stashFiles', stepParameters: parameters) {
        def script = parameters.script
        def stage = parameters.stage

        Map stashConfig = script.commonPipelineEnvironment.configuration.s4SdkStashConfiguration

        for (def stash : stashConfig[stage].stashes) {
            def name = stash.name
            def include = stash.includes
            def exclude = stash.excludes


            if (stash?.merge == true) {
                String lockName = "${script.commonPipelineEnvironment.configuration.stashFiles}/${stash.name}"
                lock(lockName) {
                    unstash stash.name
                    steps.stash name: name, includes: include, excludes: exclude, allowEmpty: true
                }
            } else {
                steps.stash name: name, includes: include, excludes: exclude, allowEmpty: true
            }
        }
        //FIXME We do not delete the directory because it failed on JaaS because of infrastructure issues.
        // DeleteDir is not required in pods, but would be nice to have the same behaviour and leave a clean fileSystem.
        if(isNotInsidePod(script)) {
            deleteDir()
        }
    }
}

private boolean isNotInsidePod(script){
    return !script.env.POD_NAME
}
