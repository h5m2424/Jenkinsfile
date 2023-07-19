pipeline {
    agent any
    environment {
        NAMESPACE = "my_namespace"
        PROJECT_NAME = "my_project"
        GIT_URL = "http://x.x.x.x:3000/xx/${PROJECT_NAME}.git"
        //KUBE_CONFIG = "/var/lib/jenkins/devconfig"
        KUBE_CONFIG = "/var/lib/jenkins/testconfig"
        //REPO_NAME="xxx"
        REPO_NAME="yyy"
        ECR_URL = "xxx.ecr.cn-northwest-1.amazonaws.com.cn"
        REPO_URL = "${ECR_URL}/${REPO_NAME}"
        AWS_REGION = "cn-northwest-1"
    }
   
    stages {
        
        stage('Checkout Code from Git') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${Branch_or_Tag}"]],
                    userRemoteConfigs: [[
                        credentialsId: 'awsjenkins',
                        url: "${GIT_URL}"]]
                ])
            }
        }

        stage('Java Build') {
            steps {
                withMaven(
                    globalMavenSettingsConfig: '5b91a83d-3b55-4912-9a90-9473cafeeb0f',
                    jdk: 'jdk-11.0.2',
                    maven: 'Maven3.3.9',
                    mavenLocalRepo: '/var/lib/jenkins/mvn_repository',
                    mavenOpts: '-Xms256m -Xmx768m -XX:PermSize=128m -XX:MaxPermSize=256M',
                    mavenSettingsConfig: 'e91ba7dc-3fbe-467c-9414-8cd0b9f049de',
                    options: [
                        pipelineGraphPublisher(
                            ignoreUpstreamTriggers: true,
                            skipDownstreamTriggers: true
                        )
                    ],
                    publisherStrategy: 'EXPLICIT',
                    tempBinDir: '/var/lib/jenkins/temp'
                ) {
                    script {
                        sh 'mvn --version'
                        sh 'mvn clean package -DskipTests -U'
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps{
                script {
                    def dockerfilePath = './Dockerfile'
                    def dockerImageTag = "${env.REPO_URL}:${env.PROJECT_NAME}_${env.BUILD_ID}"

                    echo "Running ${env.BUILDNUMBER} on ${env.JENKINS_URL}"

                    if (fileExists(dockerfilePath)) {
                        docker.build(dockerImageTag, "-f ${dockerfilePath} .")
                    } else {
                        error("No Dockerfile found at either path: ${dockerfilePath}")
                    }
                }
            }
        }
        
        stage('Logging into AWS ECR') {
            steps {
                script {
                    sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_URL}"
                }
                 
            }
        }
   
        stage('Pushing to AWS ECR') {
            steps{  
                script {
                    sh "docker push ${REPO_URL}:${env.PROJECT_NAME}_${env.BUILD_ID}"
                    sh "docker rmi ${REPO_URL}:${env.PROJECT_NAME}_${env.BUILD_ID}"
                }
            }
        }
        
        stage('Deploy to EKS') {
            steps {
                script {
                    sh "kubectl --kubeconfig ${env.KUBE_CONFIG} -n ${env.NAMESPACE} set image deployment/${env.PROJECT_NAME} ${env.PROJECT_NAME}=${REPO_URL}:${env.PROJECT_NAME}_${env.BUILD_ID}"
                    sh "kubectl --kubeconfig ${env.KUBE_CONFIG} -n ${env.NAMESPACE} rollout restart deployment/${env.PROJECT_NAME}"
                }                
            }
        }
    }
}
