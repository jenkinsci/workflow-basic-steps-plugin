pipeline {
    agent any
    stages {
        stage('x') {
            steps {
                retry(count: 3, timeDelay: 10, unit: 'SECONDS', useTimeDelay: true) {
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