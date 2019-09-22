pipeline {
    agent any
    stages {
        stage('x') {
            options {
                retry(count: 3, timeDelay: 10, unit: 'SECONDS', useTimeDelay: true)
            }
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