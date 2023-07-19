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

        stage('Web Build') {
            steps {
                //nodejs('NodeJS14_17_0'){
                nodejs('NodeJS16_17_0'){
                    script {
                        sh "npm config set registry http://registry.npm.taobao.org"
                        //sh "npm cache clean -f"
                        //sh "npm init -y"
                        //sh "npm install --legacy-peer-deps"
                        //sh "npm ci"
                        sh "npm install"
                        //sh "npm run test-build"
                        sh "npm run build:test"
                    }   
                }
            }
        }

        stage('Build Docker Image') {
            steps{
                script {
                    echo "Running ${env.BUILDNUMBER} on ${env.JENKINS_URL}"
                    sh "docker build -t ${env.REPO_URL}:${env.PROJECT_NAME}_${env.BUILD_ID} ."
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
