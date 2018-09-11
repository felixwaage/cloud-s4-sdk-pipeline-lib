def call(Map parameters) {
    def stageName = 'initS4sdkPipeline'
    def script = parameters.script

    loadPiper script: script

    /*
    In order to avoid the trust issues between the build server and the git server in a distributed setup,
    the init stage always executes on the master node. The underlying assumption here is that, Jenkins
    server has a ssh key and it has been added to the git server. This is necessary if Jenkins has to push
    code changes to the git server.
    */
    runAsStage(stageName: stageName, script: script, node: 'master') {
        checkout scm

        loadAdditionalLibraries script: script

        initS4SdkPipelineLibrary script: script
        initStashConfiguration script: script

        def mavenLocalRepository = new File(script.s4SdkGlobals.m2Directory)
        def reportsDirectory = new File(script.s4SdkGlobals.reportsDirectory)

        mavenLocalRepository.mkdirs()
        reportsDirectory.mkdirs()
        if (!fileExists(mavenLocalRepository.absolutePath) || !fileExists(reportsDirectory.absolutePath)) {
            errorWhenCurrentBuildResultIsWorseOrEqualTo(
                script: script,
                errorCurrentBuildStatus: 'FAILURE',
                errorMessage: "Please check if the user can create report directory."
            )
        }

        Map generalConfiguration = script.commonPipelineEnvironment.configuration.general

        if (!generalConfiguration) {
            generalConfiguration = [:]
            script.commonPipelineEnvironment.configuration.general = generalConfiguration
        }

        def isMtaProject = fileExists('mta.yaml')
        if (isMtaProject) {
            setupMtaProject(script: script, generalConfiguration: generalConfiguration)
        } else if (fileExists('pom.xml')) {
            if (!generalConfiguration.projectName?.trim()) {
                pom = readMavenPom file: 'pom.xml'
                generalConfiguration.projectName = pom.artifactId
            }
        } else {
            throw new Exception("No pom.xml or mta.yaml has been found in the root of the project. Currently the pipeline only supports Maven and Mta projects.")
        }

        Map configWithDefault = loadEffectiveGeneralConfiguration script: script

        if (isProductiveBranch(script: script) && configWithDefault.automaticVersioning) {
            artifactSetVersion script: script, buildTool: isMtaProject ? 'mta' : 'maven', filePath: isMtaProject ? 'mta.yaml' : 'pom.xml'
        }
        generalConfiguration.gitCommitId = getGitCommitId()

        String prefix = generalConfiguration.projectName

        if (Boolean.valueOf(env.ON_K8S)) {
            initContainersMap script: script
        }

        script.commonPipelineEnvironment.configuration.currentBuildResultLock = "${prefix}/currentBuildResult"
        script.commonPipelineEnvironment.configuration.performanceTestLock = "${prefix}/performanceTest"
        script.commonPipelineEnvironment.configuration.endToEndTestLock = "${prefix}/endToEndTest"
        script.commonPipelineEnvironment.configuration.productionDeploymentLock = "${prefix}/productionDeployment"
        script.commonPipelineEnvironment.configuration.stashFiles = "${prefix}/stashFiles"

        String extensionRepository = generalConfiguration.extensionsRepository

        if (extensionRepository != null) {
            try {
                sh "git clone ${extensionRepository} ${s4SdkGlobals.repositoryExtensionsDirectory}"
            } catch (Exception e) {
                error("Error while executing git clone when accessing repository ${extensionRepository}.")
            }
        }

        initStageSkipConfiguration script: script
    }
}


