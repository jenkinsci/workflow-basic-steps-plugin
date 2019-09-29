pipeline {
    agent any
    stages {
        stage('x') {
            steps {
                retry(count: 4, delay: incremental(increment: 2, max: 10, min: 1, unit: 'SECONDS'), useRetryDelay: true) {
                    echo 'Trying!'
                    error('oops')
                }
            }
        }
    }
    post {
        always {
            echo 'Done!'
        }
    }
}