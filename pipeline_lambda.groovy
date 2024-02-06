def IMAGE_VERSION = 'latest'
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                credentialsId: 'GITHUB',
                url: 'https://github.com/CES4RFL/LambdaReadCSV.git'
            }
        }
        
        stage('Configure') {
            steps {
                withCredentials([file(credentialsId: 'database_config', variable: 'secretFile')]) {
                    writeFile file: 'database_conf.py', text: readFile(secretFile)
                }

                  sh """cat ${WORKSPACE}/database_conf.py"""
            }
        }
        
        stage('Test') {
            steps {
                sh 'ls -l'
            }
        }
        
        stage('Build') {
            steps {
                
                sh """  docker logout 982673117069.dkr.ecr.us-east-1.amazonaws.com
                        docker logout public.ecr.aws
                        docker build -f Dockerfile -t 982673117069.dkr.ecr.us-east-1.amazonaws.com/lambda:${params.Version} .
                        docker images
                        docker system prune -a
                    """
            }
        }
        
        stage('Setup credentials') {
            steps {

                withCredentials([
                   string(credentialsId: 'aws_access_key_id', variable: 'aws_access_key_id'),
                   string(credentialsId: 'aws_secret_access_key', variable: 'aws_secret_access_key')
                ]){
                    sh """
                        #!/bin/bash
                        aws configure set aws_access_key_id $aws_access_key_id
                        aws configure set aws_secret_access_key $aws_secret_access_key
                        aws configure set default.region us-east-1 --output json --profile dev
                        aws configure list
                        aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 982673117069.dkr.ecr.us-east-1.amazonaws.com
                    """
                }
            }
        } 
        
        stage('Docker push') {
            steps {
                sh """
         	      docker push 982673117069.dkr.ecr.us-east-1.amazonaws.com/lambda:${params.Version}
                """
            }
        }
        
        stage('Deploy Image') {
            steps {
                sh """
         	     aws lambda update-function-code \
                    --function-name csv \
                    --image-uri 982673117069.dkr.ecr.us-east-1.amazonaws.com/lambda:${params.Version} \
                    
                """
            }
        }
    } 
    post { 
        always { 
            deleteDir()
            sh 'rm -r ~/.aws'
            script{
                def exitCode = sh(script:"docker rmi -f \$(docker images -aq)", returnStatus: true)
                if (exitCode != 0) {
                    echo "exit 0"
                }
            }
        }
    }
}