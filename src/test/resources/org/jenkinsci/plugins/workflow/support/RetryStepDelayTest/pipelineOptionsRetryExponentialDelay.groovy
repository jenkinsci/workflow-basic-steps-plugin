pipeline {
    agent any
    options {
        retry(count: 6, delay: randomExponential(max: 10, multiplier: 2), useRetryDelay: true, unit: 'SECONDS')
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