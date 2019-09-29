pipeline {
    agent any
    options {
        retry(count: 4, delay: exponential(max: 20, min: 1, multiplier: 2, unit: 'SECONDS'), useRetryDelay: true)
    }
    stages {
        stage('x') {
            steps {
                echo 'Trying!'
                error('oops')
            }
        }
    }
    post {
        always {
            echo 'Done!'
        }
    }
}